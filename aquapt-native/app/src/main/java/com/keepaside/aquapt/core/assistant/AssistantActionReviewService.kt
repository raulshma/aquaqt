package com.keepaside.aquapt.core.assistant

import com.keepaside.aquapt.core.model.AssistantActionExecutionItemResult
import com.keepaside.aquapt.core.model.AssistantActionExecutionResult
import com.keepaside.aquapt.core.model.AssistantActionExtractionResult
import com.keepaside.aquapt.core.model.AssistantActionTypes
import com.keepaside.aquapt.core.model.AssistantDetectedAction
import com.keepaside.aquapt.core.model.Asset
import com.keepaside.aquapt.core.model.AssetCategory
import com.keepaside.aquapt.core.model.Aquarium
import com.keepaside.aquapt.core.model.Consumable
import com.keepaside.aquapt.core.model.ConsumableUnit
import com.keepaside.aquapt.core.model.DosingLog
import com.keepaside.aquapt.core.model.EntityKind
import com.keepaside.aquapt.core.model.EntityRef
import com.keepaside.aquapt.core.model.Issue
import com.keepaside.aquapt.core.model.IssueStatus
import com.keepaside.aquapt.core.model.Livestock
import com.keepaside.aquapt.core.model.LivestockKind
import com.keepaside.aquapt.core.model.LivestockStatus
import com.keepaside.aquapt.core.model.Memo
import com.keepaside.aquapt.core.model.TaskCategory
import com.keepaside.aquapt.core.model.TaskExecution
import com.keepaside.aquapt.core.model.TaskFrequency
import com.keepaside.aquapt.core.model.TaskTemplate
import com.keepaside.aquapt.core.model.TimelineEvent
import com.keepaside.aquapt.core.model.TimelineEventType
import com.keepaside.aquapt.core.model.WaterType
import com.keepaside.aquapt.core.model.WaterParameterLog
import com.keepaside.aquapt.core.model.WaterParameters
import com.keepaside.aquapt.core.repository.AssetRepository
import com.keepaside.aquapt.core.repository.AppSettingsStore
import com.keepaside.aquapt.core.repository.AquariumRepository
import com.keepaside.aquapt.core.repository.ConsumableRepository
import com.keepaside.aquapt.core.repository.DosingLogRepository
import com.keepaside.aquapt.core.repository.IssueRepository
import com.keepaside.aquapt.core.repository.LivestockRepository
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
    private val livestockRepository: LivestockRepository,
    private val taskTemplateRepository: TaskTemplateRepository,
    private val taskExecutionRepository: TaskExecutionRepository,
    private val assetRepository: AssetRepository,
    private val consumableRepository: ConsumableRepository,
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
        val livestock = livestockRepository.getAll().first()
        val templates = taskTemplateRepository.getAll().first()
        val consumables = consumableRepository.getAll().first()
        val issues = issueRepository.getAll().first()

        val mutableAquariums = aquariums.toMutableList()
        val mutableLivestock = livestock.toMutableList()
        val mutableTemplates = templates.toMutableList()
        val mutableConsumables = consumables.toMutableList()
        val mutableIssues = issues.toMutableList()

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

            val requiresExistingAquarium = action.type !in setOf(
                AssistantActionTypes.ADD_AQUARIUM,
                AssistantActionTypes.SET_ISSUE_STATUS,
                AssistantActionTypes.CONSUME_CONSUMABLE
            )
            val aquariumId = if (requiresExistingAquarium) {
                resolveAquariumId(action, mutableAquariums)
            } else {
                ""
            }

            if (requiresExistingAquarium && aquariumId.isBlank()) {
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

                    val duplicate = mutableTemplates.any { template ->
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
                    mutableTemplates += taskTemplate
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
                    val taskTemplateId = resolveTaskTemplateId(action, aquariumId, mutableTemplates)
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
                    mutableIssues += issue

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

                AssistantActionTypes.ADD_AQUARIUM -> {
                    val name = action.title?.trim().orEmpty()
                    val waterType = action.waterType
                    val volume = action.volumeLiters
                    val dimensions = action.dimensions?.trim().orEmpty()

                    if (name.isBlank() || waterType == null || volume == null || volume <= 0.0 || dimensions.isBlank()) {
                        executionItems += AssistantActionExecutionItemResult(
                            actionId = action.id,
                            actionType = action.type,
                            created = false,
                            reason = "Aquarium fields are incomplete."
                        )
                        continue
                    }

                    val aquarium = Aquarium(
                        id = idProvider("aquarium"),
                        name = name,
                        volumeLiters = volume,
                        dimensions = dimensions,
                        waterType = waterType,
                        setupDate = action.setupDate?.trim()?.takeIf { it.isNotBlank() } ?: nowIso(),
                        investmentCost = action.investmentCost
                    )

                    aquariumRepository.upsert(aquarium)
                    mutableAquariums += aquarium

                    createdCount += 1
                    executionItems += AssistantActionExecutionItemResult(
                        actionId = action.id,
                        actionType = action.type,
                        created = true,
                        summary = "Aquarium added"
                    )
                }

                AssistantActionTypes.ADD_LIVESTOCK -> {
                    val livestockName = action.livestockName?.trim().orEmpty()
                    val species = action.species?.trim().orEmpty()
                    val kind = action.livestockKind
                    val quantity = action.quantity

                    if (livestockName.isBlank() || species.isBlank() || kind == null || quantity == null || quantity <= 0) {
                        executionItems += AssistantActionExecutionItemResult(
                            actionId = action.id,
                            actionType = action.type,
                            created = false,
                            reason = "Livestock fields are incomplete."
                        )
                        continue
                    }

                    val item = Livestock(
                        id = idProvider("livestock"),
                        aquariumId = aquariumId,
                        kind = kind,
                        name = livestockName,
                        species = species,
                        quantity = quantity,
                        acquiredAt = action.setupDate?.trim()?.takeIf { it.isNotBlank() } ?: nowIso(),
                        purchasePrice = action.price,
                        dietaryNotes = action.description,
                        status = action.livestockStatus ?: LivestockStatus.ACTIVE
                    )

                    livestockRepository.upsert(item)
                    mutableLivestock += item

                    timelineEventRepository.upsert(
                        TimelineEvent(
                            aquariumId = aquariumId,
                            type = TimelineEventType.LIVESTOCK,
                            createdAt = nowIso(),
                            title = "Assistant added livestock: ${item.name}",
                            description = item.species,
                            source = EntityRef(
                                kind = EntityKind.LIVESTOCK,
                                id = item.id,
                                aquariumId = aquariumId
                            )
                        )
                    )

                    createdCount += 1
                    executionItems += AssistantActionExecutionItemResult(
                        actionId = action.id,
                        actionType = action.type,
                        created = true,
                        summary = "Livestock added"
                    )
                }

                AssistantActionTypes.ADD_ASSET -> {
                    val category = action.assetCategory
                    val brandModel = action.brandModel?.trim().orEmpty()

                    if (category == null || brandModel.isBlank()) {
                        executionItems += AssistantActionExecutionItemResult(
                            actionId = action.id,
                            actionType = action.type,
                            created = false,
                            reason = "Asset fields are incomplete."
                        )
                        continue
                    }

                    val asset = Asset(
                        id = idProvider("asset"),
                        aquariumId = aquariumId,
                        category = category,
                        brandModel = brandModel,
                        purchasedAt = action.purchasedAt?.trim()?.takeIf { it.isNotBlank() },
                        price = action.price
                    )

                    assetRepository.upsert(asset)

                    timelineEventRepository.upsert(
                        TimelineEvent(
                            aquariumId = aquariumId,
                            type = TimelineEventType.ASSET,
                            createdAt = nowIso(),
                            title = "Assistant added asset: ${asset.brandModel}",
                            source = EntityRef(
                                kind = EntityKind.ASSET,
                                id = asset.id,
                                aquariumId = aquariumId
                            )
                        )
                    )

                    createdCount += 1
                    executionItems += AssistantActionExecutionItemResult(
                        actionId = action.id,
                        actionType = action.type,
                        created = true,
                        summary = "Asset added"
                    )
                }

                AssistantActionTypes.ADD_CONSUMABLE -> {
                    val name = action.consumableName?.trim().orEmpty()
                    val unit = action.consumableUnit
                    val remaining = action.remaining

                    if (name.isBlank() || unit == null || remaining == null || remaining < 0.0) {
                        executionItems += AssistantActionExecutionItemResult(
                            actionId = action.id,
                            actionType = action.type,
                            created = false,
                            reason = "Consumable fields are incomplete."
                        )
                        continue
                    }

                    val consumable = Consumable(
                        id = idProvider("consumable"),
                        aquariumId = aquariumId,
                        name = name,
                        unit = unit,
                        remaining = remaining,
                        reorderAt = action.reorderAt,
                        updatedAt = nowIso()
                    )

                    consumableRepository.upsert(consumable)
                    mutableConsumables += consumable

                    timelineEventRepository.upsert(
                        TimelineEvent(
                            aquariumId = aquariumId,
                            type = TimelineEventType.CONSUMABLE,
                            createdAt = nowIso(),
                            title = "Assistant added consumable: ${consumable.name}",
                            source = EntityRef(
                                kind = EntityKind.CONSUMABLE,
                                id = consumable.id,
                                aquariumId = aquariumId
                            )
                        )
                    )

                    createdCount += 1
                    executionItems += AssistantActionExecutionItemResult(
                        actionId = action.id,
                        actionType = action.type,
                        created = true,
                        summary = "Consumable added"
                    )
                }

                AssistantActionTypes.CONSUME_CONSUMABLE -> {
                    val amountUsed = action.amountUsed
                    if (amountUsed == null || amountUsed <= 0.0) {
                        executionItems += AssistantActionExecutionItemResult(
                            actionId = action.id,
                            actionType = action.type,
                            created = false,
                            reason = "Consumable usage amount is invalid."
                        )
                        continue
                    }

                    val consumable = resolveConsumable(
                        action = action,
                        aquariumId = aquariumId.takeIf { it.isNotBlank() },
                        consumables = mutableConsumables
                    )
                    if (consumable == null) {
                        executionItems += AssistantActionExecutionItemResult(
                            actionId = action.id,
                            actionType = action.type,
                            created = false,
                            reason = "Consumable not found."
                        )
                        continue
                    }

                    val updated = consumable.copy(
                        remaining = (consumable.remaining - amountUsed).coerceAtLeast(0.0),
                        updatedAt = nowIso()
                    )
                    consumableRepository.upsert(updated)
                    mutableConsumables.replaceAll { item ->
                        if (item.id == updated.id) updated else item
                    }

                    timelineEventRepository.upsert(
                        TimelineEvent(
                            aquariumId = consumable.aquariumId,
                            type = TimelineEventType.CONSUMABLE,
                            createdAt = nowIso(),
                            title = "Assistant logged consumable usage: ${updated.name}",
                            description = "Used $amountUsed ${updated.unit.name.lowercase()}",
                            source = EntityRef(
                                kind = EntityKind.CONSUMABLE,
                                id = updated.id,
                                aquariumId = consumable.aquariumId
                            )
                        )
                    )

                    createdCount += 1
                    executionItems += AssistantActionExecutionItemResult(
                        actionId = action.id,
                        actionType = action.type,
                        created = true,
                        summary = "Consumable usage logged"
                    )
                }

                AssistantActionTypes.SET_ISSUE_STATUS -> {
                    val issue = resolveIssue(
                        action = action,
                        aquariumId = aquariumId.takeIf { it.isNotBlank() },
                        issues = mutableIssues
                    )
                    val nextStatus = action.issueStatus

                    if (issue == null || nextStatus == null) {
                        executionItems += AssistantActionExecutionItemResult(
                            actionId = action.id,
                            actionType = action.type,
                            created = false,
                            reason = "Issue or target status could not be resolved."
                        )
                        continue
                    }

                    val updatedIssue = issue.copy(
                        status = nextStatus,
                        resolutionNote = action.resolutionNote?.trim()?.takeIf { it.isNotBlank() }
                            ?: action.note?.trim()?.takeIf { it.isNotBlank() }
                    )

                    issueRepository.upsert(updatedIssue)
                    mutableIssues.replaceAll { item ->
                        if (item.id == updatedIssue.id) updatedIssue else item
                    }

                    timelineEventRepository.upsert(
                        TimelineEvent(
                            aquariumId = issue.aquariumId,
                            type = TimelineEventType.ISSUE,
                            createdAt = nowIso(),
                            title = "Assistant updated issue status: ${updatedIssue.title}",
                            description = "Status ${updatedIssue.status.name.lowercase()}",
                            source = EntityRef(
                                kind = EntityKind.ISSUE,
                                id = updatedIssue.id,
                                aquariumId = issue.aquariumId
                            )
                        )
                    )

                    createdCount += 1
                    executionItems += AssistantActionExecutionItemResult(
                        actionId = action.id,
                        actionType = action.type,
                        created = true,
                        summary = "Issue status updated"
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
        val issueId = raw.stringValue("issueId")
        val issueStatus = raw.issueStatusValue("issueStatus")
        val resolutionNote = raw.stringValue("resolutionNote")
        val reminderHour = raw.intValue("reminderHour")
        val reminderHours = raw.intListValue("reminderHours")
        val parameters = raw.waterParametersValue("parameters")
        val waterType = raw.waterTypeValue("waterType")
        val volumeLiters = raw.numberValue("volumeLiters")
        val dimensions = raw.stringValue("dimensions")
        val setupDate = raw.stringValue("setupDate")
        val investmentCost = raw.numberValue("investmentCost")
        val livestockName = raw.stringValue("livestockName") ?: title
        val livestockId = raw.stringValue("livestockId")
        val species = raw.stringValue("species")
        val quantity = raw.intValue("quantity")
        val livestockKind = raw.livestockKindValue("livestockKind")
        val livestockStatus = raw.livestockStatusValue("livestockStatus")
        val assetCategory = raw.assetCategoryValue("assetCategory")
        val brandModel = raw.stringValue("brandModel")
        val purchasedAt = raw.stringValue("purchasedAt")
        val price = raw.numberValue("price")
        val consumableId = raw.stringValue("consumableId")
        val consumableName = raw.stringValue("consumableName")
        val consumableUnit = raw.consumableUnitValue("consumableUnit")
        val remaining = raw.numberValue("remaining")
        val reorderAt = raw.numberValue("reorderAt")
        val amountUsed = raw.numberValue("amountUsed")

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

            AssistantActionTypes.ADD_AQUARIUM -> {
                if (title.isNullOrBlank()) {
                    validationErrors += "Missing aquarium name"
                }
                if (volumeLiters == null || volumeLiters <= 0.0) {
                    validationErrors += "Missing or invalid volumeLiters"
                }
                if (waterType == null) {
                    validationErrors += "Missing or invalid waterType"
                }
                if (dimensions.isNullOrBlank()) {
                    validationErrors += "Missing aquarium dimensions"
                }
            }

            AssistantActionTypes.ADD_LIVESTOCK -> {
                if (livestockName.isNullOrBlank()) {
                    validationErrors += "Missing livestock name"
                }
                if (species.isNullOrBlank()) {
                    validationErrors += "Missing livestock species"
                }
                if (quantity == null || quantity <= 0) {
                    validationErrors += "Missing or invalid livestock quantity"
                }
                if (livestockKind == null) {
                    validationErrors += "Missing or invalid livestock kind"
                }
            }

            AssistantActionTypes.ADD_ASSET -> {
                if (assetCategory == null) {
                    validationErrors += "Missing or invalid asset category"
                }
                if (brandModel.isNullOrBlank()) {
                    validationErrors += "Missing asset brandModel"
                }
            }

            AssistantActionTypes.ADD_CONSUMABLE -> {
                if (consumableName.isNullOrBlank()) {
                    validationErrors += "Missing consumable name"
                }
                if (consumableUnit == null) {
                    validationErrors += "Missing or invalid consumable unit"
                }
                if (remaining == null || remaining < 0.0) {
                    validationErrors += "Missing or invalid consumable remaining amount"
                }
            }

            AssistantActionTypes.CONSUME_CONSUMABLE -> {
                if (consumableId.isNullOrBlank() && consumableName.isNullOrBlank()) {
                    validationErrors += "Missing consumableId or consumableName"
                }
                if (amountUsed == null || amountUsed <= 0.0) {
                    validationErrors += "Missing or invalid amountUsed"
                }
            }

            AssistantActionTypes.SET_ISSUE_STATUS -> {
                if (issueId.isNullOrBlank() && issueTitle.isNullOrBlank()) {
                    validationErrors += "Missing issueId or issueTitle"
                }
                if (issueStatus == null) {
                    validationErrors += "Missing or invalid issueStatus"
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
            waterType = waterType,
            volumeLiters = volumeLiters,
            dimensions = dimensions,
            setupDate = setupDate,
            investmentCost = investmentCost,
            livestockId = livestockId,
            livestockName = livestockName,
            species = species,
            quantity = quantity,
            livestockKind = livestockKind,
            livestockStatus = livestockStatus,
            issueId = issueId,
            issueStatus = issueStatus,
            resolutionNote = resolutionNote,
            assetCategory = assetCategory,
            brandModel = brandModel,
            purchasedAt = purchasedAt,
            price = price,
            consumableId = consumableId,
            consumableName = consumableName,
            consumableUnit = consumableUnit,
            remaining = remaining,
            reorderAt = reorderAt,
            amountUsed = amountUsed,
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

    private fun resolveIssue(
        action: AssistantDetectedAction,
        aquariumId: String?,
        issues: List<Issue>
    ): Issue? {
        if (!action.issueId.isNullOrBlank()) {
            issues.firstOrNull { issue -> issue.id == action.issueId }?.let { return it }
        }

        val normalizedTitle = action.issueTitle?.trim()?.lowercase() ?: return null
        return issues.firstOrNull { issue ->
            (aquariumId == null || issue.aquariumId == aquariumId) &&
                issue.title.trim().lowercase() == normalizedTitle
        }
    }

    private fun resolveConsumable(
        action: AssistantDetectedAction,
        aquariumId: String?,
        consumables: List<Consumable>
    ): Consumable? {
        if (!action.consumableId.isNullOrBlank()) {
            consumables.firstOrNull { consumable -> consumable.id == action.consumableId }
                ?.let { return it }
        }

        val normalizedName = action.consumableName?.trim()?.lowercase() ?: return null
        return consumables.firstOrNull { consumable ->
            (aquariumId == null || consumable.aquariumId == aquariumId) &&
                consumable.name.trim().lowercase() == normalizedName
        }
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

    private fun JsonObject.waterTypeValue(key: String): WaterType? {
        val normalized = stringValue(key)?.lowercase() ?: return null
        return when (normalized) {
            "freshwater" -> WaterType.FRESHWATER
            "marine" -> WaterType.MARINE
            "brackish" -> WaterType.BRACKISH
            else -> null
        }
    }

    private fun JsonObject.livestockKindValue(key: String): LivestockKind? {
        val normalized = stringValue(key)?.lowercase() ?: return null
        return when (normalized) {
            "fish" -> LivestockKind.FISH
            "shrimp" -> LivestockKind.SHRIMP
            "snail" -> LivestockKind.SNAIL
            "coral" -> LivestockKind.CORAL
            "plant" -> LivestockKind.PLANT
            "other" -> LivestockKind.OTHER
            else -> null
        }
    }

    private fun JsonObject.livestockStatusValue(key: String): LivestockStatus? {
        val normalized = stringValue(key)?.lowercase() ?: return null
        return when (normalized) {
            "active" -> LivestockStatus.ACTIVE
            "ill" -> LivestockStatus.ILL
            "deceased" -> LivestockStatus.DECEASED
            else -> null
        }
    }

    private fun JsonObject.issueStatusValue(key: String): IssueStatus? {
        val normalized = stringValue(key)?.lowercase() ?: return null
        return when (normalized) {
            "open" -> IssueStatus.OPEN
            "monitoring" -> IssueStatus.MONITORING
            "resolved" -> IssueStatus.RESOLVED
            else -> null
        }
    }

    private fun JsonObject.assetCategoryValue(key: String): AssetCategory? {
        val normalized = stringValue(key)?.lowercase() ?: return null
        return when (normalized) {
            "filter" -> AssetCategory.FILTER
            "heater" -> AssetCategory.HEATER
            "light" -> AssetCategory.LIGHT
            "co2" -> AssetCategory.CO2
            "other" -> AssetCategory.OTHER
            else -> null
        }
    }

    private fun JsonObject.consumableUnitValue(key: String): ConsumableUnit? {
        val normalized = stringValue(key)?.lowercase() ?: return null
        return when (normalized) {
            "g" -> ConsumableUnit.G
            "ml" -> ConsumableUnit.ML
            "pcs" -> ConsumableUnit.PCS
            else -> null
        }
    }

    private fun JsonObject.jsonArrayValue(key: String): JsonArray? =
        this[key]?.let { element -> runCatching { element.jsonArray }.getOrNull() }

    private fun nowIso(): String = nowProvider().toString()
}
