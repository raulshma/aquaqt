package com.keepaside.aquapt.core.assistant

import com.keepaside.aquapt.core.model.AssistantActionExecutionItemResult
import com.keepaside.aquapt.core.model.AssistantActionExecutionResult
import com.keepaside.aquapt.core.model.AssistantActionExtractionResult
import com.keepaside.aquapt.core.model.AssistantActionTypes
import com.keepaside.aquapt.core.model.AssistantDetectedAction
import com.keepaside.aquapt.core.model.DosingLog
import com.keepaside.aquapt.core.model.EntityKind
import com.keepaside.aquapt.core.model.EntityRef
import com.keepaside.aquapt.core.model.Issue
import com.keepaside.aquapt.core.model.IssueStatus
import com.keepaside.aquapt.core.model.Memo
import com.keepaside.aquapt.core.model.TaskCategory
import com.keepaside.aquapt.core.model.TaskExecution
import com.keepaside.aquapt.core.model.TaskFrequency
import com.keepaside.aquapt.core.model.TaskTemplate
import com.keepaside.aquapt.core.model.TimelineEvent
import com.keepaside.aquapt.core.model.TimelineEventType
import com.keepaside.aquapt.core.model.WaterParameterLog
import com.keepaside.aquapt.core.model.WaterParameters
import com.keepaside.aquapt.core.repository.AppSettingsStore
import com.keepaside.aquapt.core.repository.AquariumRepository
import com.keepaside.aquapt.core.repository.DosingLogRepository
import com.keepaside.aquapt.core.repository.IssueRepository
import com.keepaside.aquapt.core.repository.MemoRepository
import com.keepaside.aquapt.core.repository.TaskExecutionRepository
import com.keepaside.aquapt.core.repository.TaskTemplateRepository
import com.keepaside.aquapt.core.repository.TimelineEventRepository
import com.keepaside.aquapt.core.repository.WaterParameterLogRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.util.UUID

interface AssistantActionReviewService {
    fun parseAssistantActionExtraction(
        responseContent: String,
        transcript: String,
        sourceMessageId: String
    ): AssistantActionExtractionResult

    suspend fun executeApprovedActions(
        actions: List<AssistantDetectedAction>
    ): AssistantActionExecutionResult
}

class AssistantActionReviewServiceImpl(
    private val aquariumRepository: AquariumRepository,
    private val taskTemplateRepository: TaskTemplateRepository,
    private val taskExecutionRepository: TaskExecutionRepository,
    private val dosingLogRepository: DosingLogRepository,
    private val waterParameterLogRepository: WaterParameterLogRepository,
    private val issueRepository: IssueRepository,
    private val memoRepository: MemoRepository,
    private val appSettingsStore: AppSettingsStore,
    private val timelineEventRepository: TimelineEventRepository,
    private val nowProvider: () -> Instant = { Instant.now() },
    private val idProvider: (String) -> String = { prefix -> "$prefix-${UUID.randomUUID()}" },
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
) : AssistantActionReviewService {

    override fun parseAssistantActionExtraction(
        responseContent: String,
        transcript: String,
        sourceMessageId: String
    ): AssistantActionExtractionResult {
        val jsonBlock = extractJsonBlock(responseContent)
        val payload = runCatching {
            json.parseToJsonElement(jsonBlock).jsonObject
        }.getOrNull()

        if (payload == null) {
            return AssistantActionExtractionResult(
                actions = emptyList(),
                warnings = listOf(
                    "Assistant response did not include a valid JSON action block."
                ),
                raw = responseContent
            )
        }

        val warnings = payload.stringListValue("warnings")

        val actions = payload.jsonArrayValue("actions")
            ?.mapNotNull { element -> runCatching { element.jsonObject }.getOrNull() }
            ?.map { rawAction ->
                normalizeAction(
                    raw = rawAction,
                    transcript = transcript,
                    sourceMessageId = sourceMessageId
                )
            }
            .orEmpty()

        return AssistantActionExtractionResult(
            actions = actions,
            warnings = warnings,
            raw = responseContent
        )
    }

    override suspend fun executeApprovedActions(
        actions: List<AssistantDetectedAction>
    ): AssistantActionExecutionResult {
        val approvedActions = actions.filter { action -> action.approved }
        if (approvedActions.isEmpty()) {
            return AssistantActionExecutionResult(
                createdCount = 0,
                skippedCount = 0,
                results = emptyList()
            )
        }

        val aquariums = aquariumRepository.getAll().first()
        val templates = taskTemplateRepository.getAll().first()
        val issues = issueRepository.getAll().first()

        val executionItems = mutableListOf<AssistantActionExecutionItemResult>()
        var createdCount = 0

        for (action in approvedActions) {
            if (action.validationErrors.isNotEmpty()) {
                executionItems += AssistantActionExecutionItemResult(
                    actionId = action.id,
                    actionType = action.type,
                    created = false,
                    reason = action.validationErrors.joinToString(", ")
                )
                continue
            }

            if (action.type == AssistantActionTypes.SAVE_REMINDER_SETTINGS) {
                val normalizedHours = action.reminderHours
                    .ifEmpty { listOfNotNull(action.reminderHour) }
                    .mapNotNull { hour -> hour.takeIf { it in 0..23 } }
                    .distinct()
                    .sorted()

                val existingSettings = appSettingsStore.settings.value
                appSettingsStore.setSettings(
                    existingSettings.copy(
                        notificationsEnabled = action.reminderEnabled
                            ?: existingSettings.notificationsEnabled,
                        reminderHours = normalizedHours
                    )
                )

                createdCount += 1
                executionItems += AssistantActionExecutionItemResult(
                    actionId = action.id,
                    actionType = action.type,
                    created = true,
                    summary = "Reminder settings updated"
                )
                continue
            }

            val aquariumId = resolveAquariumId(action, aquariums)
            if (aquariumId.isBlank()) {
                executionItems += AssistantActionExecutionItemResult(
                    actionId = action.id,
                    actionType = action.type,
                    created = false,
                    reason = "No aquarium found for this action."
                )
                continue
            }

            when (action.type) {
                AssistantActionTypes.CREATE_TASK_TEMPLATE -> {
                    val title = action.title?.trim().orEmpty()
                    val frequency = toTaskFrequency(action.frequency)

                    if (title.isBlank() || frequency == null) {
                        executionItems += AssistantActionExecutionItemResult(
                            actionId = action.id,
                            actionType = action.type,
                            created = false,
                            reason = "Task title or frequency is invalid."
                        )
                        continue
                    }

                    val duplicate = templates.any { template ->
                        template.title.equals(title, ignoreCase = true) &&
                            template.frequency.serialize() == frequency.serialize() &&
                            template.aquariumIds.contains(aquariumId)
                    }

                    if (duplicate) {
                        executionItems += AssistantActionExecutionItemResult(
                            actionId = action.id,
                            actionType = action.type,
                            created = false,
                            reason = "Skipped duplicate task template."
                        )
                        continue
                    }

                    val taskTemplate = TaskTemplate(
                        id = idProvider("task"),
                        title = title,
                        description = action.description?.trim()?.takeIf { it.isNotBlank() },
                        category = TaskCategory.MAINTENANCE,
                        frequency = frequency,
                        aquariumIds = listOf(aquariumId),
                        startDate = nowIso().take(10),
                        reminderHours = action.reminderHours
                            .ifEmpty { listOfNotNull(action.reminderHour) }
                            .filter { it in 0..23 }
                    )

                    taskTemplateRepository.upsert(taskTemplate, aquariumId)
                    timelineEventRepository.upsert(
                        TimelineEvent(
                            aquariumId = aquariumId,
                            type = TimelineEventType.TASK,
                            createdAt = nowIso(),
                            title = "Assistant created task: ${taskTemplate.title}",
                            description = taskTemplate.description,
                            source = EntityRef(
                                kind = EntityKind.TASK,
                                id = taskTemplate.id,
                                aquariumId = aquariumId
                            )
                        )
                    )

                    createdCount += 1
                    executionItems += AssistantActionExecutionItemResult(
                        actionId = action.id,
                        actionType = action.type,
                        created = true,
                        summary = "Task created"
                    )
                }

                AssistantActionTypes.COMPLETE_TASK -> {
                    val taskTemplateId = resolveTaskTemplateId(action, aquariumId, templates)
                    if (taskTemplateId.isBlank()) {
                        executionItems += AssistantActionExecutionItemResult(
                            actionId = action.id,
                            actionType = action.type,
                            created = false,
                            reason = "Task template not found for completion."
                        )
                        continue
                    }

                    val execution = TaskExecution(
                        id = idProvider("exec"),
                        taskTemplateId = taskTemplateId,
                        aquariumId = aquariumId,
                        completedAt = nowIso(),
                        note = action.note?.trim()?.takeIf { it.isNotBlank() }
                            ?: action.description?.trim()?.takeIf { it.isNotBlank() }
                    )
                    taskExecutionRepository.upsert(execution)

                    timelineEventRepository.upsert(
                        TimelineEvent(
                            aquariumId = aquariumId,
                            type = TimelineEventType.TASK,
                            createdAt = execution.completedAt,
                            title = "Assistant completed task",
                            description = execution.note,
                            source = EntityRef(
                                kind = EntityKind.TASK,
                                id = taskTemplateId,
                                aquariumId = aquariumId
                            )
                        )
                    )

                    createdCount += 1
                    executionItems += AssistantActionExecutionItemResult(
                        actionId = action.id,
                        actionType = action.type,
                        created = true,
                        summary = "Task completion logged"
                    )
                }

                AssistantActionTypes.LOG_DOSING -> {
                    val product = action.product?.trim().orEmpty()
                    val amount = action.amountMl

                    if (product.isBlank() || amount == null || amount <= 0.0) {
                        executionItems += AssistantActionExecutionItemResult(
                            actionId = action.id,
                            actionType = action.type,
                            created = false,
                            reason = "Dosing product or amount is invalid."
                        )
                        continue
                    }

                    val log = DosingLog(
                        id = idProvider("dosing"),
                        aquariumId = aquariumId,
                        product = product,
                        amountMl = amount,
                        createdAt = nowIso(),
                        note = action.note?.trim()?.takeIf { it.isNotBlank() }
                    )
                    dosingLogRepository.upsert(log)

                    timelineEventRepository.upsert(
                        TimelineEvent(
                            aquariumId = aquariumId,
                            type = TimelineEventType.DOSING,
                            createdAt = log.createdAt,
                            title = "Assistant logged dosing: ${log.product}",
                            description = "${log.amountMl} ml",
                            source = EntityRef(
                                kind = EntityKind.DOSING,
                                id = log.id,
                                aquariumId = aquariumId
                            )
                        )
                    )

                    createdCount += 1
                    executionItems += AssistantActionExecutionItemResult(
                        actionId = action.id,
                        actionType = action.type,
                        created = true,
                        summary = "Dosing logged"
                    )
                }

                AssistantActionTypes.LOG_PARAMETERS -> {
                    val parameters = action.parameters
                    if (parameters == null || !parameters.hasAnyValue()) {
                        executionItems += AssistantActionExecutionItemResult(
                            actionId = action.id,
                            actionType = action.type,
                            created = false,
                            reason = "No water parameter values were provided."
                        )
                        continue
                    }

                    val log = WaterParameterLog(
                        id = idProvider("params"),
                        aquariumId = aquariumId,
                        createdAt = nowIso(),
                        values = parameters
                    )
                    waterParameterLogRepository.upsert(log)

                    timelineEventRepository.upsert(
                        TimelineEvent(
                            aquariumId = aquariumId,
                            type = TimelineEventType.PARAMETER,
                            createdAt = log.createdAt,
                            title = "Assistant logged water parameters",
                            description = parameterSummary(parameters),
                            source = EntityRef(
                                kind = EntityKind.PARAMETER_LOG,
                                id = log.id,
                                aquariumId = aquariumId
                            )
                        )
                    )

                    createdCount += 1
                    executionItems += AssistantActionExecutionItemResult(
                        actionId = action.id,
                        actionType = action.type,
                        created = true,
                        summary = "Parameters logged"
                    )
                }

                AssistantActionTypes.ADD_ISSUE -> {
                    val issueTitle = action.issueTitle?.trim()?.takeIf { it.isNotBlank() }
                        ?: action.title?.trim()?.takeIf { it.isNotBlank() }

                    if (issueTitle.isNullOrBlank()) {
                        executionItems += AssistantActionExecutionItemResult(
                            actionId = action.id,
                            actionType = action.type,
                            created = false,
                            reason = "Issue title is missing."
                        )
                        continue
                    }

                    val issue = Issue(
                        id = idProvider("issue"),
                        aquariumId = aquariumId,
                        title = issueTitle,
                        status = IssueStatus.OPEN,
                        createdAt = nowIso()
                    )
                    issueRepository.upsert(issue)

                    timelineEventRepository.upsert(
                        TimelineEvent(
                            aquariumId = aquariumId,
                            type = TimelineEventType.ISSUE,
                            createdAt = issue.createdAt,
                            title = "Assistant created issue: ${issue.title}",
                            source = EntityRef(
                                kind = EntityKind.ISSUE,
                                id = issue.id,
                                aquariumId = aquariumId
                            )
                        )
                    )

                    createdCount += 1
                    executionItems += AssistantActionExecutionItemResult(
                        actionId = action.id,
                        actionType = action.type,
                        created = true,
                        summary = "Issue created"
                    )
                }

                AssistantActionTypes.ADD_MEMO -> {
                    val memoContent = action.memoContent?.trim()?.takeIf { it.isNotBlank() }
                        ?: action.description?.trim()?.takeIf { it.isNotBlank() }

                    if (memoContent.isNullOrBlank()) {
                        executionItems += AssistantActionExecutionItemResult(
                            actionId = action.id,
                            actionType = action.type,
                            created = false,
                            reason = "Memo content is missing."
                        )
                        continue
                    }

                    val memo = Memo(
                        id = idProvider("memo"),
                        aquariumId = aquariumId,
                        content = memoContent,
                        createdAt = nowIso()
                    )
                    memoRepository.upsert(memo)

                    timelineEventRepository.upsert(
                        TimelineEvent(
                            aquariumId = aquariumId,
                            type = TimelineEventType.MEMO,
                            createdAt = memo.createdAt,
                            title = "Assistant added memo",
                            description = memo.content.take(120),
                            source = EntityRef(
                                kind = EntityKind.MEMO,
                                id = memo.id,
                                aquariumId = aquariumId
                            )
                        )
                    )

                    createdCount += 1
                    executionItems += AssistantActionExecutionItemResult(
                        actionId = action.id,
                        actionType = action.type,
                        created = true,
                        summary = "Memo added"
                    )
                }

                else -> {
                    executionItems += AssistantActionExecutionItemResult(
                        actionId = action.id,
                        actionType = action.type,
                        created = false,
                        reason = "Unsupported action type."
                    )
                }
            }
        }

        return AssistantActionExecutionResult(
            createdCount = createdCount,
            skippedCount = executionItems.count { item -> !item.created },
            results = executionItems
        )
    }

    private fun normalizeAction(
        raw: JsonObject,
        transcript: String,
        sourceMessageId: String
    ): AssistantDetectedAction {
        val rawType = raw.stringValue("type")
        val normalizedType = rawType
            ?.takeIf { type -> type in AssistantActionTypes.supported }
            ?: AssistantActionTypes.CREATE_TASK_TEMPLATE

        val validationErrors = mutableListOf<String>()
        if (!rawType.isNullOrBlank() && rawType !in AssistantActionTypes.supported) {
            validationErrors += "Unsupported action type: $rawType"
        }

        val normalizedFrequency = toNormalizedFrequency(raw.stringValue("frequency"))
        val title = raw.stringValue("title")
        val taskTitle = raw.stringValue("taskTitle")
        val product = raw.stringValue("product")
        val amountMl = raw.numberValue("amountMl")
        val note = raw.stringValue("note")
        val memoContent = raw.stringValue("memoContent")
        val description = raw.stringValue("description")
        val issueTitle = raw.stringValue("issueTitle")
        val reminderHour = raw.intValue("reminderHour")
        val reminderHours = raw.intListValue("reminderHours")
        val parameters = raw.waterParametersValue("parameters")

        when (normalizedType) {
            AssistantActionTypes.CREATE_TASK_TEMPLATE -> {
                if (title.isNullOrBlank()) {
                    validationErrors += "Missing task title"
                }
                if (normalizedFrequency == null) {
                    validationErrors += "Missing or invalid task frequency"
                }
            }

            AssistantActionTypes.COMPLETE_TASK -> {
                if (raw.stringValue("taskTemplateId").isNullOrBlank() && taskTitle.isNullOrBlank()) {
                    validationErrors += "Missing taskTemplateId or taskTitle"
                }
            }

            AssistantActionTypes.LOG_DOSING -> {
                if (product.isNullOrBlank()) {
                    validationErrors += "Missing dosing product"
                }
                if (amountMl == null || amountMl <= 0.0) {
                    validationErrors += "Missing or invalid dosing amount"
                }
            }

            AssistantActionTypes.LOG_PARAMETERS -> {
                if (parameters == null || !parameters.hasAnyValue()) {
                    validationErrors += "Missing water parameters"
                }
            }

            AssistantActionTypes.ADD_ISSUE -> {
                if (issueTitle.isNullOrBlank() && title.isNullOrBlank()) {
                    validationErrors += "Missing issue title"
                }
            }

            AssistantActionTypes.ADD_MEMO -> {
                if (memoContent.isNullOrBlank() && description.isNullOrBlank()) {
                    validationErrors += "Missing memo content"
                }
            }

            AssistantActionTypes.SAVE_REMINDER_SETTINGS -> {
                val enabled = raw.booleanValue("reminderEnabled")
                if (enabled == null) {
                    validationErrors += "Missing reminderEnabled"
                }

                if (enabled == true) {
                    val hasHour = reminderHour != null || reminderHours.isNotEmpty()
                    if (!hasHour) {
                        validationErrors += "Missing reminder hour(s)"
                    }
                }
            }
        }

        val confidence = raw.numberValue("confidence")
            ?.coerceIn(0.0, 1.0)
            ?: 0.6

        return AssistantDetectedAction(
            id = idProvider("action"),
            type = normalizedType,
            title = title,
            frequency = normalizedFrequency,
            aquariumId = raw.stringValue("aquariumId"),
            aquariumName = raw.stringValue("aquariumName"),
            description = description,
            taskTemplateId = raw.stringValue("taskTemplateId"),
            taskTitle = taskTitle,
            product = product,
            amountMl = amountMl,
            note = note,
            parameters = parameters,
            issueTitle = issueTitle,
            memoContent = memoContent,
            reminderEnabled = raw.booleanValue("reminderEnabled"),
            reminderHour = reminderHour,
            reminderHours = reminderHours,
            confidence = confidence,
            approved = false,
            validationErrors = validationErrors,
            sourceTranscript = transcript,
            sourceMessageId = sourceMessageId
        )
    }

    private fun resolveAquariumId(
        action: AssistantDetectedAction,
        aquariums: List<com.keepaside.aquapt.core.model.Aquarium>
    ): String {
        if (!action.aquariumId.isNullOrBlank() &&
            aquariums.any { aquarium -> aquarium.id == action.aquariumId }
        ) {
            return action.aquariumId.orEmpty()
        }

        val byName = action.aquariumName
            ?.trim()
            ?.lowercase()
            ?.let { normalized ->
                aquariums.firstOrNull { aquarium ->
                    aquarium.name.trim().lowercase() == normalized
                }
            }

        if (byName != null) {
            return byName.id
        }

        return if (aquariums.size == 1) aquariums.first().id else ""
    }

    private fun resolveTaskTemplateId(
        action: AssistantDetectedAction,
        aquariumId: String,
        templates: List<TaskTemplate>
    ): String {
        if (!action.taskTemplateId.isNullOrBlank() &&
            templates.any { template -> template.id == action.taskTemplateId }
        ) {
            return action.taskTemplateId.orEmpty()
        }

        val normalizedTitle = action.taskTitle?.trim()?.lowercase() ?: return ""
        return templates.firstOrNull { template ->
            template.title.trim().lowercase() == normalizedTitle &&
                template.aquariumIds.contains(aquariumId)
        }?.id.orEmpty()
    }

    private fun toTaskFrequency(value: String?): TaskFrequency? {
        val normalized = toNormalizedFrequency(value) ?: return null
        return TaskFrequency.parse(normalized)
    }

    private fun toNormalizedFrequency(value: String?): String? {
        val normalized = value
            ?.trim()
            ?.lowercase()
            ?.replace(" ", "_")
            ?: return null

        val customMatch = Regex("every_?(\\d+)_?day").matchEntire(normalized)
        if (customMatch != null) {
            val days = customMatch.groupValues[1].toIntOrNull()?.coerceAtLeast(1) ?: 1
            return "custom-$days"
        }

        return when (normalized) {
            "daily", "every_day" -> "daily"
            "weekly", "every_week" -> "weekly"
            "biweekly", "bi-weekly", "fortnightly" -> "bi-weekly"
            "monthly" -> "monthly"
            else -> if (normalized.startsWith("custom-")) {
                val days = normalized.removePrefix("custom-").toIntOrNull()?.coerceAtLeast(1)
                days?.let { "custom-$it" }
            } else {
                null
            }
        }
    }

    private fun extractJsonBlock(content: String): String {
        val fencedMatch = Regex("```json\\s*([\\s\\S]*?)\\s*```", RegexOption.IGNORE_CASE)
            .find(content)
        if (fencedMatch?.groupValues?.getOrNull(1)?.isNotBlank() == true) {
            return fencedMatch.groupValues[1]
        }

        val firstBrace = content.indexOf('{')
        val lastBrace = content.lastIndexOf('}')
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return content.substring(firstBrace, lastBrace + 1)
        }

        return content
    }

    private fun WaterParameters.hasAnyValue(): Boolean =
        ammonia != null ||
            nitrite != null ||
            nitrate != null ||
            ph != null ||
            temperatureC != null ||
            gh != null ||
            kh != null ||
            salinity != null ||
            calcium != null ||
            alkalinity != null

    private fun parameterSummary(values: WaterParameters): String {
        val parts = buildList {
            values.ammonia?.let { add("Ammonia $it") }
            values.nitrite?.let { add("Nitrite $it") }
            values.nitrate?.let { add("Nitrate $it") }
            values.ph?.let { add("pH $it") }
            values.temperatureC?.let { add("Temp ${it}°C") }
            values.gh?.let { add("GH $it") }
            values.kh?.let { add("KH $it") }
            values.salinity?.let { add("Salinity $it") }
            values.calcium?.let { add("Calcium $it") }
            values.alkalinity?.let { add("Alk $it") }
        }

        return if (parts.isEmpty()) {
            "No parameter summary"
        } else {
            parts.joinToString(" • ")
        }
    }

    private fun JsonObject.stringValue(key: String): String? =
        this[key]
            ?.let { element -> runCatching { element.jsonPrimitive.contentOrNull }.getOrNull() }
            ?.trim()
            ?.takeIf { value -> value.isNotBlank() }

    private fun JsonObject.booleanValue(key: String): Boolean? {
        val primitive = this[key]?.let { element -> runCatching { element.jsonPrimitive }.getOrNull() }
            ?: return null

        return primitive.booleanOrNull
            ?: primitive.contentOrNull?.trim()?.lowercase()?.let { value ->
                when (value) {
                    "true" -> true
                    "false" -> false
                    else -> null
                }
            }
    }

    private fun JsonObject.numberValue(key: String): Double? {
        val primitive = this[key]?.let { element -> runCatching { element.jsonPrimitive }.getOrNull() }
            ?: return null

        return primitive.doubleOrNull
            ?: primitive.contentOrNull?.trim()?.toDoubleOrNull()
    }

    private fun JsonObject.intValue(key: String): Int? {
        val primitive = this[key]?.let { element -> runCatching { element.jsonPrimitive }.getOrNull() }
            ?: return null

        return primitive.intOrNull
            ?: primitive.contentOrNull?.trim()?.toIntOrNull()
    }

    private fun JsonObject.intListValue(key: String): List<Int> {
        val array = this.jsonArrayValue(key) ?: return emptyList()
        return array.mapNotNull { element ->
            val primitive = runCatching { element.jsonPrimitive }.getOrNull() ?: return@mapNotNull null
            primitive.intOrNull ?: primitive.contentOrNull?.trim()?.toIntOrNull()
        }.filter { value -> value in 0..23 }
            .distinct()
            .sorted()
    }

    private fun JsonObject.stringListValue(key: String): List<String> {
        val array = this.jsonArrayValue(key) ?: return emptyList()
        return array.mapNotNull { element ->
            runCatching { element.jsonPrimitive.contentOrNull }.getOrNull()
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        }
    }

    private fun JsonObject.waterParametersValue(key: String): WaterParameters? {
        val obj = this[key]
            ?.let { element -> runCatching { element.jsonObject }.getOrNull() }
            ?: return null

        val params = WaterParameters(
            ammonia = obj.numberValue("ammonia"),
            nitrite = obj.numberValue("nitrite"),
            nitrate = obj.numberValue("nitrate"),
            ph = obj.numberValue("ph"),
            temperatureC = obj.numberValue("temperatureC"),
            gh = obj.numberValue("gh"),
            kh = obj.numberValue("kh"),
            salinity = obj.numberValue("salinity"),
            calcium = obj.numberValue("calcium"),
            alkalinity = obj.numberValue("alkalinity")
        )

        return params.takeIf { it.hasAnyValue() }
    }

    private fun JsonObject.jsonArrayValue(key: String): JsonArray? =
        this[key]?.let { element -> runCatching { element.jsonArray }.getOrNull() }

    private fun nowIso(): String = nowProvider().toString()
}
