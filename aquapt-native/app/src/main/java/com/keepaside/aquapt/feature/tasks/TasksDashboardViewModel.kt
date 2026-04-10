package com.keepaside.aquapt.feature.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.keepaside.aquapt.core.logic.getCompletionsToday
import com.keepaside.aquapt.core.logic.isTaskDue
import com.keepaside.aquapt.core.model.Aquarium
import com.keepaside.aquapt.core.model.DosingLog
import com.keepaside.aquapt.core.model.ReminderGroup
import com.keepaside.aquapt.core.model.TaskCategory
import com.keepaside.aquapt.core.model.TaskExecution
import com.keepaside.aquapt.core.model.TaskFrequency
import com.keepaside.aquapt.core.model.TaskFrequencyKind
import com.keepaside.aquapt.core.model.TaskTemplate
import com.keepaside.aquapt.core.repository.AquariumRepository
import com.keepaside.aquapt.core.repository.DosingLogRepository
import com.keepaside.aquapt.core.repository.ReminderGroupRepository
import com.keepaside.aquapt.core.repository.TaskExecutionRepository
import com.keepaside.aquapt.core.repository.TaskTemplateRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

data class TasksSummaryMetrics(
    val aquariumCount: Int = 0,
    val taskTemplateCount: Int = 0,
    val dueTaskCount: Int = 0,
    val recentExecutionCount: Int = 0,
    val dosingLogCount: Int = 0
)

data class DueTaskMatrixItem(
    val taskId: String,
    val taskTitle: String,
    val aquariumId: String,
    val aquariumName: String,
    val frequencyLabel: String,
    val completionsToday: Int,
    val timesPerDay: Int
)

data class RecentTaskExecutionItem(
    val executionId: String,
    val taskTitle: String,
    val aquariumName: String,
    val completedAtLabel: String,
    val completedAtInput: String,
    val note: String?
)

data class AquariumDosingSnapshotItem(
    val aquariumId: String,
    val aquariumName: String,
    val count: Int,
    val latestProduct: String?,
    val latestAmountMl: Double?,
    val latestAtLabel: String?
)

data class TaskTemplateAquariumOption(
    val aquariumId: String,
    val aquariumName: String
)

data class ReminderGroupOption(
    val id: String,
    val name: String,
    val hours: List<Int>,
    val hoursLabel: String
)

data class TaskTemplateListItem(
    val id: String,
    val title: String,
    val description: String?,
    val category: TaskCategory?,
    val categoryLabel: String,
    val aquariumIds: List<String>,
    val aquariumNames: List<String>,
    val frequency: TaskFrequency,
    val frequencyLabel: String,
    val customDaysInput: String,
    val startDate: String,
    val timesPerDayInput: String,
    val reminderHoursInput: String,
    val reminderGroupId: String?,
    val reminderGroupLabel: String?
)

data class TaskTemplateDraft(
    val id: String? = null,
    val title: String = "",
    val description: String = "",
    val category: TaskCategory? = TaskCategory.MAINTENANCE,
    val aquariumIds: Set<String> = emptySet(),
    val frequency: TaskFrequency = TaskFrequency.DAILY,
    val customDays: String = "1",
    val startDate: String = "",
    val timesPerDay: String = "1",
    val reminderHours: String = "",
    val reminderGroupId: String? = null
)

data class TasksDashboardUiState(
    val isLoading: Boolean = true,
    val isEmpty: Boolean = false,
    val headline: String = "Loading tasks…",
    val summary: TasksSummaryMetrics = TasksSummaryMetrics(),
    val aquariumOptions: List<TaskTemplateAquariumOption> = emptyList(),
    val reminderGroupOptions: List<ReminderGroupOption> = emptyList(),
    val taskTemplates: List<TaskTemplateListItem> = emptyList(),
    val dueTasks: List<DueTaskMatrixItem> = emptyList(),
    val dosingSnapshots: List<AquariumDosingSnapshotItem> = emptyList(),
    val recentExecutions: List<RecentTaskExecutionItem> = emptyList(),
    val statusMessage: String? = null
)

class TasksDashboardViewModel(
    private val aquariumRepository: AquariumRepository,
    private val taskTemplateRepository: TaskTemplateRepository,
    private val taskExecutionRepository: TaskExecutionRepository,
    private val dosingLogRepository: DosingLogRepository,
    private val reminderGroupRepository: ReminderGroupRepository,
    private val nowProvider: () -> Instant = { Instant.now() },
    private val idProvider: () -> String = { UUID.randomUUID().toString() },
    private val zoneId: ZoneId = ZoneId.systemDefault()
) : ViewModel() {

    private val _statusMessage = MutableStateFlow<String?>(null)
    private val _uiState = MutableStateFlow(TasksDashboardUiState())
    val uiState: StateFlow<TasksDashboardUiState> = _uiState.asStateFlow()

    init {
        observeTasksDashboard()
    }

    fun completeTaskNow(item: DueTaskMatrixItem, note: String? = null) {
        completeTask(
            item = item,
            completedAt = nowProvider(),
            note = note,
            successMessage = "Marked '${item.taskTitle}' complete for ${item.aquariumName}."
        )
    }

    fun backdateTaskByDays(item: DueTaskMatrixItem, days: Int = 1, note: String? = null) {
        val safeDays = days.coerceAtLeast(1)
        completeTask(
            item = item,
            completedAt = nowProvider().minusSeconds(24L * 60L * 60L * safeDays),
            note = note,
            successMessage = "Backdated '${item.taskTitle}' by $safeDays day${if (safeDays == 1) "" else "s"}."
        )
    }

    fun currentCompletionDateTimeInput(): String =
        formatDateTimeInput(nowProvider(), zoneId)

    fun newTaskTemplateDraft(): TaskTemplateDraft =
        TaskTemplateDraft(
            aquariumIds = _uiState.value.aquariumOptions.firstOrNull()
                ?.let { setOf(it.aquariumId) }
                .orEmpty(),
            startDate = todayIso(nowProvider(), zoneId)
        )

    fun draftForTaskTemplate(templateId: String): TaskTemplateDraft? =
        _uiState.value.taskTemplates.firstOrNull { it.id == templateId }?.toDraft()

    fun saveTaskTemplate(draft: TaskTemplateDraft) {
        val aquariumOptions = _uiState.value.aquariumOptions
        val reminderGroupOptions = _uiState.value.reminderGroupOptions
        validateTaskTemplateDraft(draft, aquariumOptions, reminderGroupOptions)?.let { message ->
            _statusMessage.value = message
            return
        }

        val title = draft.title.trim()
        val selectedAquariumIds = draft.aquariumIds
            .filter { aquariumId -> aquariumOptions.any { it.aquariumId == aquariumId } }
        val frequency = resolveTaskTemplateFrequency(draft)
            ?: return _statusMessage.update { "Custom frequency needs at least 1 day." }
        val startDate = draft.startDate.trim().ifBlank { null }
        val reminderHours = parseReminderHoursInput(draft.reminderHours)
            ?: return _statusMessage.update { "Reminder hours must be between 0 and 23." }
        val reminderGroupId = draft.reminderGroupId
            ?.takeIf { selectedId -> reminderGroupOptions.any { it.id == selectedId } }
        val timesPerDay = if (frequency.kind == TaskFrequencyKind.DAILY) {
            draft.timesPerDay.trim().ifBlank { "1" }.toInt().coerceAtLeast(1)
        } else {
            null
        }

        viewModelScope.launch {
            runCatching {
                val existing = draft.id?.let { taskTemplateRepository.getById(it) }
                val template = TaskTemplate(
                    id = existing?.id ?: draft.id ?: idProvider(),
                    title = title,
                    description = draft.description.trim().ifBlank { null },
                    category = draft.category,
                    livestockId = existing?.livestockId,
                    frequency = frequency,
                    aquariumIds = selectedAquariumIds,
                    startDate = startDate,
                    timesPerDay = timesPerDay,
                    reminderHours = reminderHours,
                    reminderGroupId = reminderGroupId
                )

                taskTemplateRepository.upsert(
                    template = template,
                    primaryAquariumId = selectedAquariumIds.first()
                )
            }.onSuccess {
                _statusMessage.value = if (draft.id == null) {
                    "Task template created."
                } else {
                    "Task template updated."
                }
            }.onFailure { error ->
                _statusMessage.value = error.message ?: "Unable to save task template."
            }
        }
    }

    fun deleteTaskTemplate(templateId: String) {
        viewModelScope.launch {
            runCatching {
                taskTemplateRepository.deleteById(templateId)
            }.onSuccess {
                _statusMessage.value = "Task template deleted."
            }.onFailure { error ->
                _statusMessage.value = error.message ?: "Unable to delete task template."
            }
        }
    }

    fun completeTaskAt(item: DueTaskMatrixItem, completedAtInput: String, note: String? = null) {
        val completedAt = parseTaskDateTimeInput(completedAtInput, zoneId)
        if (completedAt == null) {
            _statusMessage.value = dateTimeErrorMessage
            return
        }

        completeTask(
            item = item,
            completedAt = completedAt,
            note = note,
            successMessage = "Saved '${item.taskTitle}' completion for ${item.aquariumName}."
        )
    }

    fun updateExecution(executionId: String, completedAtInput: String, note: String? = null) {
        val completedAt = parseTaskDateTimeInput(completedAtInput, zoneId)
        if (completedAt == null) {
            _statusMessage.value = dateTimeErrorMessage
            return
        }

        viewModelScope.launch {
            runCatching {
                val existing = taskExecutionRepository.getById(executionId)
                    ?: error("Task execution was not found.")
                taskExecutionRepository.upsert(
                    existing.copy(
                        completedAt = completedAt.toString(),
                        note = note.normalizeNote()
                    )
                )
            }.onSuccess {
                _statusMessage.value = "Updated task completion."
            }.onFailure { error ->
                _statusMessage.value = error.message ?: "Unable to update task completion."
            }
        }
    }

    fun deleteExecution(executionId: String) {
        viewModelScope.launch {
            runCatching {
                taskExecutionRepository.deleteById(executionId)
            }.onSuccess {
                _statusMessage.value = "Deleted task completion."
            }.onFailure { error ->
                _statusMessage.value = error.message ?: "Unable to delete task completion."
            }
        }
    }

    private fun completeTask(
        item: DueTaskMatrixItem,
        completedAt: Instant,
        note: String?,
        successMessage: String
    ) {
        viewModelScope.launch {
            runCatching {
                taskExecutionRepository.upsert(
                    TaskExecution(
                        id = idProvider(),
                        taskTemplateId = item.taskId,
                        aquariumId = item.aquariumId,
                        completedAt = completedAt.toString(),
                        note = note.normalizeNote()
                    )
                )
            }.onSuccess {
                _statusMessage.value = successMessage
            }.onFailure { error ->
                _statusMessage.value = error.message ?: "Unable to save task execution."
            }
        }
    }

    private fun observeTasksDashboard() {
        val dashboardDataFlow = combine(
            aquariumRepository.getAll(),
            taskTemplateRepository.getAll(),
            taskExecutionRepository.getAll(),
            dosingLogRepository.getAll(),
            reminderGroupRepository.getAll()
        ) { aquariums, taskTemplates, taskExecutions, dosingLogs, reminderGroups ->
            TasksDashboardBaseData(
                aquariums = aquariums,
                taskTemplates = taskTemplates,
                taskExecutions = taskExecutions,
                dosingLogs = dosingLogs,
                reminderGroups = reminderGroups
            )
        }

        viewModelScope.launch {
            combine(
                dashboardDataFlow,
                _statusMessage
            ) { base, statusMessage ->
                assembleTasksDashboardUiState(
                    aquariums = base.aquariums,
                    taskTemplates = base.taskTemplates,
                    taskExecutions = base.taskExecutions,
                    dosingLogs = base.dosingLogs,
                    reminderGroups = base.reminderGroups,
                    now = nowProvider(),
                    zoneId = zoneId,
                    statusMessage = statusMessage
                )
            }.collect { next ->
                _uiState.update {
                    next.copy(isLoading = false)
                }
            }
        }
    }

    private data class TasksDashboardBaseData(
        val aquariums: List<Aquarium>,
        val taskTemplates: List<TaskTemplate>,
        val taskExecutions: List<TaskExecution>,
        val dosingLogs: List<DosingLog>,
        val reminderGroups: List<ReminderGroup>
    )

    companion object {
        fun factory(
            aquariumRepository: AquariumRepository,
            taskTemplateRepository: TaskTemplateRepository,
            taskExecutionRepository: TaskExecutionRepository,
            dosingLogRepository: DosingLogRepository,
            reminderGroupRepository: ReminderGroupRepository
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(TasksDashboardViewModel::class.java)) {
                        return TasksDashboardViewModel(
                            aquariumRepository = aquariumRepository,
                            taskTemplateRepository = taskTemplateRepository,
                            taskExecutionRepository = taskExecutionRepository,
                            dosingLogRepository = dosingLogRepository,
                            reminderGroupRepository = reminderGroupRepository
                        ) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
    }
}

internal fun assembleTasksDashboardUiState(
    aquariums: List<Aquarium>,
    taskTemplates: List<TaskTemplate>,
    taskExecutions: List<TaskExecution>,
    dosingLogs: List<DosingLog>,
    reminderGroups: List<ReminderGroup> = emptyList(),
    now: Instant,
    zoneId: ZoneId,
    statusMessage: String?
): TasksDashboardUiState {
    if (aquariums.isEmpty()) {
        return TasksDashboardUiState(
            isEmpty = true,
            headline = "Add your first tank to unlock task planning and completion tracking.",
            summary = TasksSummaryMetrics(
                aquariumCount = 0,
                taskTemplateCount = taskTemplates.size,
                dueTaskCount = 0,
                recentExecutionCount = taskExecutions.size,
                dosingLogCount = dosingLogs.size
            ),
            statusMessage = statusMessage
        )
    }

    val aquariumNameById = aquariums.associate { it.id to it.name }
    val reminderGroupById = reminderGroups.associateBy { it.id }
    val taskTitleById = taskTemplates.associate { it.id to it.title }
    val aquariumOptions = aquariums
        .sortedBy { it.name.lowercase() }
        .map { TaskTemplateAquariumOption(it.id, it.name) }
    val reminderGroupOptions = reminderGroups
        .sortedBy { it.name.lowercase() }
        .map { group ->
            ReminderGroupOption(
                id = group.id,
                name = group.name,
                hours = group.hours,
                hoursLabel = group.hours.joinToString(", ")
            )
        }
    val taskTemplateItems = taskTemplates
        .sortedWith(compareBy<TaskTemplate> { it.title.lowercase() }.thenBy { it.id })
        .map { template ->
            val aquariumIds = template.aquariumIds
            val reminderGroupLabel = template.reminderGroupId
                ?.let { groupId ->
                    val group = reminderGroupById[groupId]
                    if (group == null) {
                        "Unknown group"
                    } else if (group.hours.isEmpty()) {
                        group.name
                    } else {
                        "${group.name} (${group.hours.joinToString(", ")})"
                    }
                }
            TaskTemplateListItem(
                id = template.id,
                title = template.title,
                description = template.description,
                category = template.category,
                categoryLabel = template.category.label(),
                aquariumIds = aquariumIds,
                aquariumNames = aquariumIds.map { aquariumId ->
                    aquariumNameById[aquariumId] ?: "Unknown tank"
                },
                frequency = template.frequency,
                frequencyLabel = template.frequency.getLabel(),
                customDaysInput = (template.frequency.customDays ?: 1).toString(),
                startDate = template.startDate.orEmpty(),
                timesPerDayInput = (template.timesPerDay ?: 1).toString(),
                reminderHoursInput = template.reminderHours.joinToString(", "),
                reminderGroupId = template.reminderGroupId,
                reminderGroupLabel = reminderGroupLabel
            )
        }

    val dueTasks = taskTemplates
        .flatMap { task ->
            val timesPerDay = (task.timesPerDay ?: 1).coerceAtLeast(1)
            task.aquariumIds.mapNotNull { aquariumId ->
                if (!isTaskDue(task, aquariumId, taskExecutions, now, zoneId)) {
                    return@mapNotNull null
                }

                DueTaskMatrixItem(
                    taskId = task.id,
                    taskTitle = task.title,
                    aquariumId = aquariumId,
                    aquariumName = aquariumNameById[aquariumId] ?: "Unknown tank",
                    frequencyLabel = task.frequency.getLabel(),
                    completionsToday = getCompletionsToday(task, aquariumId, taskExecutions, now, zoneId),
                    timesPerDay = timesPerDay
                )
            }
        }
        .sortedWith(compareBy<DueTaskMatrixItem> { it.aquariumName }.thenBy { it.taskTitle })

    val recentExecutions = taskExecutions
        .sortedByDescending { parseToInstant(it.completedAt, zoneId)?.toEpochMilli() ?: Long.MIN_VALUE }
        .take(20)
        .map { execution ->
            RecentTaskExecutionItem(
                executionId = execution.id,
                taskTitle = taskTitleById[execution.taskTemplateId] ?: "Unknown task",
                aquariumName = aquariumNameById[execution.aquariumId] ?: "Unknown tank",
                completedAtLabel = formatDateTime(execution.completedAt, zoneId),
                completedAtInput = formatDateTimeInput(execution.completedAt, zoneId),
                note = execution.note
            )
        }

    val dosingByAquarium = dosingLogs.groupBy { it.aquariumId }
    val dosingSnapshots = aquariums.map { aquarium ->
        val logs = dosingByAquarium[aquarium.id].orEmpty()
        val latest = logs.maxByOrNull { parseToInstant(it.createdAt, zoneId)?.toEpochMilli() ?: Long.MIN_VALUE }

        AquariumDosingSnapshotItem(
            aquariumId = aquarium.id,
            aquariumName = aquarium.name,
            count = logs.size,
            latestProduct = latest?.product,
            latestAmountMl = latest?.amountMl,
            latestAtLabel = latest?.let { formatDateTime(it.createdAt, zoneId) }
        )
    }

    val headline = when {
        dueTasks.isNotEmpty() -> "${dueTasks.size} task${if (dueTasks.size == 1) "" else "s"} due across ${aquariums.size} tank${if (aquariums.size == 1) "" else "s"}."
        recentExecutions.isNotEmpty() -> "No tasks due right now. Great consistency so far."
        else -> "Create your first template to start scheduling recurring care routines."
    }

    return TasksDashboardUiState(
        isEmpty = taskTemplates.isEmpty() && taskExecutions.isEmpty() && dosingLogs.isEmpty(),
        headline = headline,
        summary = TasksSummaryMetrics(
            aquariumCount = aquariums.size,
            taskTemplateCount = taskTemplates.size,
            dueTaskCount = dueTasks.size,
            recentExecutionCount = recentExecutions.size,
            dosingLogCount = dosingLogs.size
        ),
        aquariumOptions = aquariumOptions,
        reminderGroupOptions = reminderGroupOptions,
        taskTemplates = taskTemplateItems,
        dueTasks = dueTasks,
        dosingSnapshots = dosingSnapshots,
        recentExecutions = recentExecutions,
        statusMessage = statusMessage
    )
}

internal const val dateTimeErrorMessage =
    "Use a valid completion time like 2026-04-11 18:30."

internal fun validateTaskTemplateDraft(
    draft: TaskTemplateDraft,
    aquariumOptions: List<TaskTemplateAquariumOption>,
    reminderGroupOptions: List<ReminderGroupOption> = emptyList()
): String? {
    if (draft.title.isBlank()) return "Name the task before saving."
    if (draft.aquariumIds.none { aquariumId -> aquariumOptions.any { it.aquariumId == aquariumId } }) {
        return "Choose at least one tank for this task."
    }
    if (draft.reminderGroupId != null && reminderGroupOptions.none { it.id == draft.reminderGroupId }) {
        return "Choose a valid reminder group."
    }
    if (resolveTaskTemplateFrequency(draft) == null) return "Custom frequency needs at least 1 day."
    if (draft.startDate.isNotBlank() && runCatching { LocalDate.parse(draft.startDate.trim()) }.isFailure) {
        return "Start date must use yyyy-MM-dd."
    }
    if (draft.frequency.kind == TaskFrequencyKind.DAILY) {
        val timesPerDay = draft.timesPerDay.trim().ifBlank { "1" }.toIntOrNull()
        if (timesPerDay == null || timesPerDay < 1) return "Times per day must be at least 1."
    }
    if (parseReminderHoursInput(draft.reminderHours) == null) {
        return "Reminder hours must be between 0 and 23."
    }
    return null
}

internal fun resolveTaskTemplateFrequency(draft: TaskTemplateDraft): TaskFrequency? =
    if (draft.frequency.kind == TaskFrequencyKind.CUSTOM) {
        draft.customDays.trim().toIntOrNull()?.takeIf { it >= 1 }?.let { TaskFrequency.custom(it) }
    } else {
        draft.frequency
    }

internal fun parseReminderHoursInput(raw: String): List<Int>? {
    val value = raw.trim()
    if (value.isEmpty()) return emptyList()

    return value
        .split(",", ";", " ")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { part -> part.toIntOrNull()?.takeIf { it in 0..23 } ?: return null }
        .distinct()
        .sorted()
}

internal fun parseTaskDateTimeInput(raw: String, zoneId: ZoneId): Instant? {
    val value = raw.trim()
    if (value.isEmpty()) return null

    val localDateTimeFormatters = listOf(
        DateTimeFormatter.ISO_LOCAL_DATE_TIME,
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    )

    return runCatching { Instant.parse(value) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()
        ?: localDateTimeFormatters.firstNotNullOfOrNull { formatter ->
            runCatching { LocalDateTime.parse(value, formatter).atZone(zoneId).toInstant() }.getOrNull()
        }
        ?: runCatching { LocalDate.parse(value).atStartOfDay(zoneId).toInstant() }.getOrNull()
}

internal fun formatDateTimeInput(instant: Instant, zoneId: ZoneId): String {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    return formatter.format(instant.atZone(zoneId))
}

private fun formatDateTime(raw: String, zoneId: ZoneId): String {
    val instant = parseToInstant(raw, zoneId) ?: return raw
    return formatDateTimeInput(instant, zoneId)
}

private fun formatDateTimeInput(raw: String, zoneId: ZoneId): String {
    val instant = parseToInstant(raw, zoneId) ?: return raw
    return formatDateTimeInput(instant, zoneId)
}

private fun parseToInstant(raw: String, zoneId: ZoneId): Instant? {
    val value = raw.trim()
    if (value.isEmpty()) return null

    return runCatching { Instant.parse(value) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()
        ?: runCatching { LocalDateTime.parse(value).atZone(zoneId).toInstant() }.getOrNull()
        ?: runCatching { LocalDate.parse(value).atStartOfDay(zoneId).toInstant() }.getOrNull()
}

private fun String?.normalizeNote(): String? =
    this?.trim()?.takeIf { it.isNotEmpty() }

private fun TaskTemplateListItem.toDraft(): TaskTemplateDraft =
    TaskTemplateDraft(
        id = id,
        title = title,
        description = description.orEmpty(),
        category = category,
        aquariumIds = aquariumIds.toSet(),
        frequency = frequency,
        customDays = customDaysInput,
        startDate = startDate,
        timesPerDay = timesPerDayInput,
        reminderHours = reminderHoursInput,
        reminderGroupId = reminderGroupId
    )

private fun TaskCategory?.label(): String =
    this?.name
        ?.lowercase()
        ?.replaceFirstChar { it.uppercaseChar() }
        ?: "General"

private fun todayIso(now: Instant, zoneId: ZoneId): String =
    DateTimeFormatter.ISO_LOCAL_DATE.format(now.atZone(zoneId))
