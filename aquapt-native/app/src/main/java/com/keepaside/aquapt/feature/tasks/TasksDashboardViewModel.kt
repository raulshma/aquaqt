package com.keepaside.aquapt.feature.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.keepaside.aquapt.core.logic.getCompletionsToday
import com.keepaside.aquapt.core.logic.isTaskDue
import com.keepaside.aquapt.core.model.Aquarium
import com.keepaside.aquapt.core.model.DosingLog
import com.keepaside.aquapt.core.model.TaskExecution
import com.keepaside.aquapt.core.model.TaskTemplate
import com.keepaside.aquapt.core.repository.AquariumRepository
import com.keepaside.aquapt.core.repository.DosingLogRepository
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

data class TasksDashboardUiState(
    val isLoading: Boolean = true,
    val isEmpty: Boolean = false,
    val headline: String = "Loading tasks…",
    val summary: TasksSummaryMetrics = TasksSummaryMetrics(),
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
        viewModelScope.launch {
            combine(
                aquariumRepository.getAll(),
                taskTemplateRepository.getAll(),
                taskExecutionRepository.getAll(),
                dosingLogRepository.getAll(),
                _statusMessage
            ) { aquariums, taskTemplates, taskExecutions, dosingLogs, statusMessage ->
                assembleTasksDashboardUiState(
                    aquariums = aquariums,
                    taskTemplates = taskTemplates,
                    taskExecutions = taskExecutions,
                    dosingLogs = dosingLogs,
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

    companion object {
        fun factory(
            aquariumRepository: AquariumRepository,
            taskTemplateRepository: TaskTemplateRepository,
            taskExecutionRepository: TaskExecutionRepository,
            dosingLogRepository: DosingLogRepository
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(TasksDashboardViewModel::class.java)) {
                        return TasksDashboardViewModel(
                            aquariumRepository = aquariumRepository,
                            taskTemplateRepository = taskTemplateRepository,
                            taskExecutionRepository = taskExecutionRepository,
                            dosingLogRepository = dosingLogRepository
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
                dueTaskCount = 0,
                recentExecutionCount = taskExecutions.size,
                dosingLogCount = dosingLogs.size
            ),
            statusMessage = statusMessage
        )
    }

    val aquariumNameById = aquariums.associate { it.id to it.name }
    val taskTitleById = taskTemplates.associate { it.id to it.title }

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
            dueTaskCount = dueTasks.size,
            recentExecutionCount = recentExecutions.size,
            dosingLogCount = dosingLogs.size
        ),
        dueTasks = dueTasks,
        dosingSnapshots = dosingSnapshots,
        recentExecutions = recentExecutions,
        statusMessage = statusMessage
    )
}

internal const val dateTimeErrorMessage =
    "Use a valid completion time like 2026-04-11 18:30."

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
