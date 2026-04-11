package com.keepaside.aquapt.feature.entity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.keepaside.aquapt.core.model.Aquarium
import com.keepaside.aquapt.core.model.ReminderGroup
import com.keepaside.aquapt.core.model.TaskCategory
import com.keepaside.aquapt.core.model.TaskExecution
import com.keepaside.aquapt.core.model.TaskFrequency
import com.keepaside.aquapt.core.model.TaskFrequencyKind
import com.keepaside.aquapt.core.model.TaskTemplate
import com.keepaside.aquapt.core.repository.AquariumRepository
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

enum class EntityEditKind(val routeToken: String) {
    TASK_TEMPLATE("task-template"),
    TASK_EXECUTION("task-execution");

    companion object {
        fun fromRouteToken(value: String?): EntityEditKind? {
            val token = value?.trim()?.lowercase().orEmpty()
            return entries.firstOrNull { it.routeToken == token }
        }
    }
}

data class EntityEditAquariumOption(
    val id: String,
    val name: String,
    val isSelected: Boolean
)

data class EntityEditReminderGroupOption(
    val id: String,
    val name: String,
    val hours: List<Int>,
    val hoursLabel: String
)

data class EntityEditTaskTemplateDraft(
    val id: String,
    val title: String = "",
    val description: String = "",
    val category: TaskCategory? = TaskCategory.MAINTENANCE,
    val livestockId: String? = null,
    val aquariumIds: Set<String> = emptySet(),
    val frequency: TaskFrequency = TaskFrequency.DAILY,
    val customDays: String = "1",
    val startDate: String = "",
    val timesPerDay: String = "1",
    val reminderHours: String = "",
    val reminderGroupId: String? = null
)

data class EntityEditTaskExecutionDraft(
    val id: String,
    val taskTemplateId: String,
    val taskTitle: String,
    val aquariumId: String,
    val aquariumName: String,
    val completedAtInput: String,
    val note: String = ""
)

data class EntityEditUiState(
    val isLoading: Boolean = true,
    val isNotFound: Boolean = false,
    val kind: EntityEditKind? = null,
    val entityId: String = "",
    val headline: String = "Loading edit form…",
    val supportingText: String = "",
    val aquariumOptions: List<EntityEditAquariumOption> = emptyList(),
    val reminderGroupOptions: List<EntityEditReminderGroupOption> = emptyList(),
    val taskTemplateDraft: EntityEditTaskTemplateDraft? = null,
    val taskExecutionDraft: EntityEditTaskExecutionDraft? = null,
    val isSaving: Boolean = false,
    val canSave: Boolean = false,
    val statusMessage: String? = null
)

class EntityEditViewModel(
    private val kind: EntityEditKind?,
    private val entityId: String,
    private val aquariumRepository: AquariumRepository,
    private val taskTemplateRepository: TaskTemplateRepository,
    private val taskExecutionRepository: TaskExecutionRepository,
    private val reminderGroupRepository: ReminderGroupRepository,
    private val zoneId: ZoneId = ZoneId.systemDefault()
) : ViewModel() {

    private data class EntityEditBaseData(
        val aquariums: List<Aquarium>,
        val taskTemplates: List<TaskTemplate>,
        val taskExecutions: List<TaskExecution>,
        val reminderGroups: List<ReminderGroup>
    )

    private val editedTemplateDraft = MutableStateFlow<EntityEditTaskTemplateDraft?>(null)
    private val editedExecutionDraft = MutableStateFlow<EntityEditTaskExecutionDraft?>(null)
    private val statusMessage = MutableStateFlow<String?>(null)
    private val isSaving = MutableStateFlow(false)

    private val _uiState = MutableStateFlow(EntityEditUiState())
    val uiState: StateFlow<EntityEditUiState> = _uiState.asStateFlow()

    init {
        observeEditState()
    }

    fun onTemplateTitleChanged(input: String) {
        editedTemplateDraft.updateTemplateDraft { copy(title = input) }
    }

    fun onTemplateDescriptionChanged(input: String) {
        editedTemplateDraft.updateTemplateDraft { copy(description = input) }
    }

    fun onTemplateCategoryChanged(category: TaskCategory?) {
        editedTemplateDraft.updateTemplateDraft { copy(category = category) }
    }

    fun onTemplateFrequencyChanged(frequency: TaskFrequency) {
        editedTemplateDraft.updateTemplateDraft { copy(frequency = frequency) }
    }

    fun onTemplateCustomDaysChanged(input: String) {
        editedTemplateDraft.updateTemplateDraft { copy(customDays = input) }
    }

    fun onTemplateStartDateChanged(input: String) {
        editedTemplateDraft.updateTemplateDraft { copy(startDate = input) }
    }

    fun onTemplateTimesPerDayChanged(input: String) {
        editedTemplateDraft.updateTemplateDraft { copy(timesPerDay = input) }
    }

    fun onTemplateReminderHoursChanged(input: String) {
        editedTemplateDraft.updateTemplateDraft { copy(reminderHours = input) }
    }

    fun onTemplateReminderGroupChanged(reminderGroupId: String?) {
        editedTemplateDraft.updateTemplateDraft { copy(reminderGroupId = reminderGroupId) }
    }

    fun toggleTemplateAquariumSelection(aquariumId: String) {
        editedTemplateDraft.updateTemplateDraft {
            val nextAquariumIds = if (aquariumId in aquariumIds) {
                aquariumIds - aquariumId
            } else {
                aquariumIds + aquariumId
            }

            copy(aquariumIds = nextAquariumIds)
        }
    }

    fun onExecutionCompletedAtChanged(input: String) {
        editedExecutionDraft.updateExecutionDraft { copy(completedAtInput = input) }
    }

    fun onExecutionNoteChanged(input: String) {
        editedExecutionDraft.updateExecutionDraft { copy(note = input) }
    }

    fun save(onSaved: (() -> Unit)? = null) {
        when (kind) {
            EntityEditKind.TASK_TEMPLATE -> saveTaskTemplate(onSaved)
            EntityEditKind.TASK_EXECUTION -> saveTaskExecution(onSaved)
            null -> setStatus("The selected edit type is not available.")
        }
    }

    fun deleteCurrentEntity(onDeleted: (() -> Unit)? = null) {
        when (kind) {
            EntityEditKind.TASK_TEMPLATE -> deleteTaskTemplate(onDeleted)
            EntityEditKind.TASK_EXECUTION -> deleteTaskExecution(onDeleted)
            null -> setStatus("The selected edit type is not available.")
        }
    }

    private fun saveTaskTemplate(onSaved: (() -> Unit)? = null) {
        val state = _uiState.value
        val draft = state.taskTemplateDraft
        if (draft == null) {
            setStatus("Task template no longer exists.")
            return
        }

        validateEntityEditTaskTemplateDraft(
            draft = draft,
            aquariumOptions = state.aquariumOptions,
            reminderGroupOptions = state.reminderGroupOptions
        )?.let { message ->
            setStatus(message)
            return
        }

        val selectedAquariumIds = state.aquariumOptions
            .filter { option -> option.id in draft.aquariumIds }
            .map { it.id }

        val frequency = resolveEntityEditTaskTemplateFrequency(draft)
            ?: run {
                setStatus("Custom frequency needs at least 1 day.")
                return
            }

        val reminderHours = parseEntityEditReminderHoursInput(draft.reminderHours)
            ?: run {
                setStatus("Reminder hours must be between 0 and 23.")
                return
            }

        val timesPerDay = if (frequency.kind == TaskFrequencyKind.DAILY) {
            draft.timesPerDay.trim().ifBlank { "1" }.toInt().coerceAtLeast(1)
        } else {
            null
        }

        viewModelScope.launch {
            isSaving.update { true }

            runCatching {
                taskTemplateRepository.upsert(
                    template = TaskTemplate(
                        id = draft.id,
                        title = draft.title.trim(),
                        description = draft.description.trim().ifBlank { null },
                        category = draft.category,
                        livestockId = draft.livestockId,
                        frequency = frequency,
                        aquariumIds = selectedAquariumIds,
                        startDate = draft.startDate.trim().ifBlank { null },
                        timesPerDay = timesPerDay,
                        reminderHours = reminderHours,
                        reminderGroupId = draft.reminderGroupId
                    ),
                    primaryAquariumId = selectedAquariumIds.first()
                )
            }.onSuccess {
                setStatus("Task template updated.")
                onSaved?.invoke()
            }.onFailure { error ->
                setStatus(error.message ?: "Unable to update task template.")
            }

            isSaving.update { false }
        }
    }

    private fun saveTaskExecution(onSaved: (() -> Unit)? = null) {
        val draft = _uiState.value.taskExecutionDraft
        if (draft == null) {
            setStatus("Task execution no longer exists.")
            return
        }

        validateEntityEditTaskExecutionDraft(draft, zoneId)?.let { message ->
            setStatus(message)
            return
        }

        val completedAt = parseEntityEditDateTimeInput(draft.completedAtInput, zoneId)
            ?: run {
                setStatus(entityEditDateTimeErrorMessage)
                return
            }

        viewModelScope.launch {
            isSaving.update { true }

            runCatching {
                taskExecutionRepository.upsert(
                    TaskExecution(
                        id = draft.id,
                        taskTemplateId = draft.taskTemplateId,
                        aquariumId = draft.aquariumId,
                        completedAt = completedAt.toString(),
                        note = draft.note.trim().takeIf { it.isNotEmpty() }
                    )
                )
            }.onSuccess {
                setStatus("Task execution updated.")
                onSaved?.invoke()
            }.onFailure { error ->
                setStatus(error.message ?: "Unable to update task execution.")
            }

            isSaving.update { false }
        }
    }

    private fun deleteTaskTemplate(onDeleted: (() -> Unit)? = null) {
        viewModelScope.launch {
            isSaving.update { true }

            runCatching {
                taskTemplateRepository.deleteById(entityId)
            }.onSuccess {
                setStatus("Task template deleted with linked history.")
                onDeleted?.invoke()
            }.onFailure { error ->
                setStatus(error.message ?: "Unable to delete task template.")
            }

            isSaving.update { false }
        }
    }

    private fun deleteTaskExecution(onDeleted: (() -> Unit)? = null) {
        viewModelScope.launch {
            isSaving.update { true }

            runCatching {
                taskExecutionRepository.deleteById(entityId)
            }.onSuccess {
                setStatus("Task execution deleted.")
                onDeleted?.invoke()
            }.onFailure { error ->
                setStatus(error.message ?: "Unable to delete task execution.")
            }

            isSaving.update { false }
        }
    }

    private fun observeEditState() {
        val baseDataFlow = combine(
            aquariumRepository.getAll(),
            taskTemplateRepository.getAll(),
            taskExecutionRepository.getAll(),
            reminderGroupRepository.getAll()
        ) { aquariums, taskTemplates, taskExecutions, reminderGroups ->
            EntityEditBaseData(
                aquariums = aquariums,
                taskTemplates = taskTemplates,
                taskExecutions = taskExecutions,
                reminderGroups = reminderGroups
            )
        }

        viewModelScope.launch {
            combine(
                baseDataFlow,
                editedTemplateDraft,
                editedExecutionDraft,
                isSaving,
                statusMessage
            ) {
                    base,
                    editedTemplate,
                    editedExecution,
                    saving,
                    status ->
                assembleEntityEditUiState(
                    kind = kind,
                    entityId = entityId,
                    aquariums = base.aquariums,
                    taskTemplates = base.taskTemplates,
                    taskExecutions = base.taskExecutions,
                    reminderGroups = base.reminderGroups,
                    editedTaskTemplateDraft = editedTemplate,
                    editedTaskExecutionDraft = editedExecution,
                    isSaving = saving,
                    statusMessage = status,
                    zoneId = zoneId
                )
            }.collect { next ->
                _uiState.update { next.copy(isLoading = false) }
            }
        }
    }

    private fun setStatus(message: String) {
        statusMessage.update { message }
    }

    companion object {
        fun factory(
            kind: EntityEditKind?,
            entityId: String,
            aquariumRepository: AquariumRepository,
            taskTemplateRepository: TaskTemplateRepository,
            taskExecutionRepository: TaskExecutionRepository,
            reminderGroupRepository: ReminderGroupRepository
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(EntityEditViewModel::class.java)) {
                        return EntityEditViewModel(
                            kind = kind,
                            entityId = entityId,
                            aquariumRepository = aquariumRepository,
                            taskTemplateRepository = taskTemplateRepository,
                            taskExecutionRepository = taskExecutionRepository,
                            reminderGroupRepository = reminderGroupRepository
                        ) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
    }
}

internal const val entityEditDateTimeErrorMessage =
    "Use a valid completion time like 2026-04-11 18:30."

internal fun assembleEntityEditUiState(
    kind: EntityEditKind?,
    entityId: String,
    aquariums: List<Aquarium>,
    taskTemplates: List<TaskTemplate>,
    taskExecutions: List<TaskExecution>,
    reminderGroups: List<ReminderGroup>,
    editedTaskTemplateDraft: EntityEditTaskTemplateDraft?,
    editedTaskExecutionDraft: EntityEditTaskExecutionDraft?,
    isSaving: Boolean,
    statusMessage: String?,
    zoneId: ZoneId
): EntityEditUiState {
    val trimmedEntityId = entityId.trim()
    if (kind == null || trimmedEntityId.isBlank()) {
        return EntityEditUiState(
            isNotFound = true,
            kind = kind,
            entityId = trimmedEntityId,
            headline = "Invalid edit action",
            supportingText = "The selected edit target could not be resolved.",
            isSaving = isSaving,
            statusMessage = statusMessage ?: "Open an edit route from task details to continue."
        )
    }

    return when (kind) {
        EntityEditKind.TASK_TEMPLATE -> {
            val template = taskTemplates.firstOrNull { it.id == trimmedEntityId }
                ?: return EntityEditUiState(
                    isNotFound = true,
                    kind = kind,
                    entityId = trimmedEntityId,
                    headline = "Task template not found",
                    supportingText = "The task template may have been deleted.",
                    isSaving = isSaving,
                    statusMessage = statusMessage
                )

            val workingDraft = editedTaskTemplateDraft ?: template.toEntityEditDraft()
            val aquariumOptions = aquariums
                .sortedBy { it.name.lowercase() }
                .map { aquarium ->
                    EntityEditAquariumOption(
                        id = aquarium.id,
                        name = aquarium.name,
                        isSelected = aquarium.id in workingDraft.aquariumIds
                    )
                }
            val reminderGroupOptions = reminderGroups
                .sortedBy { it.name.lowercase() }
                .map { group ->
                    EntityEditReminderGroupOption(
                        id = group.id,
                        name = group.name,
                        hours = group.hours,
                        hoursLabel = group.hours.joinToString(", ")
                    )
                }

            val validationError = validateEntityEditTaskTemplateDraft(
                draft = workingDraft,
                aquariumOptions = aquariumOptions,
                reminderGroupOptions = reminderGroupOptions
            )

            EntityEditUiState(
                kind = kind,
                entityId = trimmedEntityId,
                headline = "Edit task template",
                supportingText = template.title,
                aquariumOptions = aquariumOptions,
                reminderGroupOptions = reminderGroupOptions,
                taskTemplateDraft = workingDraft,
                isSaving = isSaving,
                canSave = !isSaving && validationError == null,
                statusMessage = statusMessage
            )
        }

        EntityEditKind.TASK_EXECUTION -> {
            val execution = taskExecutions.firstOrNull { it.id == trimmedEntityId }
                ?: return EntityEditUiState(
                    isNotFound = true,
                    kind = kind,
                    entityId = trimmedEntityId,
                    headline = "Task execution not found",
                    supportingText = "The task execution may have been deleted.",
                    isSaving = isSaving,
                    statusMessage = statusMessage
                )

            val taskTitle = taskTemplates
                .firstOrNull { it.id == execution.taskTemplateId }
                ?.title
                ?: "Unknown task"

            val aquariumName = aquariums
                .firstOrNull { it.id == execution.aquariumId }
                ?.name
                ?: "Unknown tank"

            val workingDraft = editedTaskExecutionDraft
                ?: execution.toEntityEditDraft(
                    taskTitle = taskTitle,
                    aquariumName = aquariumName,
                    zoneId = zoneId
                )

            val validationError = validateEntityEditTaskExecutionDraft(workingDraft, zoneId)

            EntityEditUiState(
                kind = kind,
                entityId = trimmedEntityId,
                headline = "Edit task execution",
                supportingText = "${workingDraft.taskTitle} • ${workingDraft.aquariumName}",
                taskExecutionDraft = workingDraft,
                isSaving = isSaving,
                canSave = !isSaving && validationError == null,
                statusMessage = statusMessage
            )
        }
    }
}

internal fun validateEntityEditTaskTemplateDraft(
    draft: EntityEditTaskTemplateDraft,
    aquariumOptions: List<EntityEditAquariumOption>,
    reminderGroupOptions: List<EntityEditReminderGroupOption>
): String? {
    if (draft.title.trim().isBlank()) return "Name the task before saving."
    if (draft.aquariumIds.none { id -> aquariumOptions.any { it.id == id } }) {
        return "Choose at least one tank for this task."
    }
    if (draft.reminderGroupId != null && reminderGroupOptions.none { it.id == draft.reminderGroupId }) {
        return "Choose a valid reminder group."
    }
    if (resolveEntityEditTaskTemplateFrequency(draft) == null) {
        return "Custom frequency needs at least 1 day."
    }
    if (draft.startDate.isNotBlank() && runCatching { LocalDate.parse(draft.startDate.trim()) }.isFailure) {
        return "Start date must use yyyy-MM-dd."
    }
    if (draft.frequency.kind == TaskFrequencyKind.DAILY) {
        val timesPerDay = draft.timesPerDay.trim().ifBlank { "1" }.toIntOrNull()
        if (timesPerDay == null || timesPerDay < 1) return "Times per day must be at least 1."
    }
    if (parseEntityEditReminderHoursInput(draft.reminderHours) == null) {
        return "Reminder hours must be between 0 and 23."
    }
    return null
}

internal fun validateEntityEditTaskExecutionDraft(
    draft: EntityEditTaskExecutionDraft,
    zoneId: ZoneId
): String? {
    if (parseEntityEditDateTimeInput(draft.completedAtInput, zoneId) == null) {
        return entityEditDateTimeErrorMessage
    }

    return null
}

internal fun resolveEntityEditTaskTemplateFrequency(draft: EntityEditTaskTemplateDraft): TaskFrequency? =
    if (draft.frequency.kind == TaskFrequencyKind.CUSTOM) {
        draft.customDays.trim().toIntOrNull()?.takeIf { it >= 1 }?.let { TaskFrequency.custom(it) }
    } else {
        draft.frequency
    }

internal fun parseEntityEditReminderHoursInput(raw: String): List<Int>? {
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

internal fun parseEntityEditDateTimeInput(raw: String, zoneId: ZoneId): Instant? {
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

internal fun formatEntityEditDateTimeInput(instant: Instant, zoneId: ZoneId): String {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    return formatter.format(instant.atZone(zoneId))
}

private fun MutableStateFlow<EntityEditTaskTemplateDraft?>.updateTemplateDraft(
    transform: EntityEditTaskTemplateDraft.() -> EntityEditTaskTemplateDraft
) {
    update { current ->
        current?.transform()
    }
}

private fun MutableStateFlow<EntityEditTaskExecutionDraft?>.updateExecutionDraft(
    transform: EntityEditTaskExecutionDraft.() -> EntityEditTaskExecutionDraft
) {
    update { current ->
        current?.transform()
    }
}

private fun TaskTemplate.toEntityEditDraft(): EntityEditTaskTemplateDraft =
    EntityEditTaskTemplateDraft(
        id = id,
        title = title,
        description = description.orEmpty(),
        category = category,
        livestockId = livestockId,
        aquariumIds = aquariumIds.toSet(),
        frequency = frequency,
        customDays = (frequency.customDays ?: 1).toString(),
        startDate = startDate.orEmpty(),
        timesPerDay = (timesPerDay ?: 1).toString(),
        reminderHours = reminderHours.joinToString(", "),
        reminderGroupId = reminderGroupId
    )

private fun TaskExecution.toEntityEditDraft(
    taskTitle: String,
    aquariumName: String,
    zoneId: ZoneId
): EntityEditTaskExecutionDraft {
    val completedAtInput = parseEntityEditDateTimeInput(completedAt, zoneId)
        ?.let { instant -> formatEntityEditDateTimeInput(instant, zoneId) }
        ?: completedAt

    return EntityEditTaskExecutionDraft(
        id = id,
        taskTemplateId = taskTemplateId,
        taskTitle = taskTitle,
        aquariumId = aquariumId,
        aquariumName = aquariumName,
        completedAtInput = completedAtInput,
        note = note.orEmpty()
    )
}