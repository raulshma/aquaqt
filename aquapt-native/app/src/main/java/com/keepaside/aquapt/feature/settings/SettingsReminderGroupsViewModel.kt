package com.keepaside.aquapt.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.keepaside.aquapt.core.model.ReminderGroup
import com.keepaside.aquapt.core.repository.ReminderGroupRepository
import com.keepaside.aquapt.core.repository.TaskTemplateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

private const val DEFAULT_STATUS =
    "Create reusable reminder groups and assign them to recurring task templates."

data class SettingsReminderGroupItem(
    val id: String,
    val name: String,
    val hours: List<Int>,
    val hoursLabel: String,
    val assignedTaskCount: Int
)

data class ReminderGroupDraft(
    val id: String? = null,
    val name: String = "",
    val hoursInput: String = ""
)

data class SettingsReminderGroupsUiState(
    val isLoading: Boolean = true,
    val isBusy: Boolean = false,
    val groups: List<SettingsReminderGroupItem> = emptyList(),
    val draft: ReminderGroupDraft = ReminderGroupDraft(),
    val statusMessage: String = DEFAULT_STATUS
)

class SettingsReminderGroupsViewModel(
    private val reminderGroupRepository: ReminderGroupRepository,
    private val taskTemplateRepository: TaskTemplateRepository,
    private val externalScope: CoroutineScope? = null,
    private val idProvider: () -> String = { UUID.randomUUID().toString() }
) : ViewModel() {

    private var observerJob: Job? = null
    private val _uiState = MutableStateFlow(SettingsReminderGroupsUiState())
    val uiState: StateFlow<SettingsReminderGroupsUiState> = _uiState.asStateFlow()

    init {
        observerJob = observeReminderGroups()
    }

    fun onDraftNameChanged(value: String) {
        _uiState.update { state ->
            state.copy(draft = state.draft.copy(name = value))
        }
    }

    fun onDraftHoursChanged(value: String) {
        _uiState.update { state ->
            state.copy(draft = state.draft.copy(hoursInput = value))
        }
    }

    fun startCreateDraft() {
        if (_uiState.value.isBusy) return

        _uiState.update {
            it.copy(
                draft = ReminderGroupDraft(),
                statusMessage = "Create a reminder group with optional default reminder hours."
            )
        }
    }

    fun startEditDraft(groupId: String) {
        if (_uiState.value.isBusy) return

        val existing = _uiState.value.groups.firstOrNull { it.id == groupId }
        if (existing == null) {
            _uiState.update { it.copy(statusMessage = "Reminder group not found.") }
            return
        }

        _uiState.update {
            it.copy(
                draft = ReminderGroupDraft(
                    id = existing.id,
                    name = existing.name,
                    hoursInput = existing.hours.joinToString(", ")
                ),
                statusMessage = "Editing '${existing.name}'."
            )
        }
    }

    fun clearDraft() {
        if (_uiState.value.isBusy) return

        _uiState.update {
            it.copy(
                draft = ReminderGroupDraft(),
                statusMessage = "Draft cleared."
            )
        }
    }

    fun saveDraft() {
        val currentState = _uiState.value
        if (currentState.isBusy) return

        val snapshot = currentState.draft
        val name = snapshot.name.trim()
        if (name.isBlank()) {
            _uiState.update { it.copy(statusMessage = reminderGroupNameErrorMessage) }
            return
        }

        val hours = parseReminderGroupHoursInput(snapshot.hoursInput)
        if (hours == null) {
            _uiState.update { it.copy(statusMessage = reminderGroupHoursErrorMessage) }
            return
        }

        launchWork {
            _uiState.update { it.copy(isBusy = true) }

            runCatching {
                reminderGroupRepository.upsert(
                    ReminderGroup(
                        id = snapshot.id ?: idProvider(),
                        name = name,
                        hours = hours
                    )
                )
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        draft = ReminderGroupDraft(),
                        statusMessage = if (snapshot.id == null) {
                            "Reminder group created."
                        } else {
                            "Reminder group updated."
                        }
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        statusMessage = error.message ?: "Unable to save reminder group."
                    )
                }
            }
        }
    }

    fun deleteGroup(groupId: String) {
        if (_uiState.value.isBusy) return

        launchWork {
            _uiState.update { it.copy(isBusy = true) }

            runCatching {
                val detachedTemplateCount = taskTemplateRepository.clearReminderGroup(groupId)
                reminderGroupRepository.deleteById(groupId)
                detachedTemplateCount
            }.onSuccess { detachedTemplateCount ->
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        draft = if (it.draft.id == groupId) ReminderGroupDraft() else it.draft,
                        statusMessage = if (detachedTemplateCount > 0) {
                            "Reminder group deleted. Unassigned $detachedTemplateCount task template${if (detachedTemplateCount == 1) "" else "s"}."
                        } else {
                            "Reminder group deleted."
                        }
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        statusMessage = error.message ?: "Unable to delete reminder group."
                    )
                }
            }
        }
    }

    private fun observeReminderGroups(): Job =
        launchWork {
            combine(
                reminderGroupRepository.getAll(),
                taskTemplateRepository.getAll()
            ) { groups, taskTemplates ->
                val assignedTemplateCountByGroupId = taskTemplates
                    .mapNotNull { template -> template.reminderGroupId }
                    .groupingBy { it }
                    .eachCount()

                groups
                    .sortedBy { it.name.lowercase() }
                    .map { group ->
                        val normalizedHours = group.hours.distinct().sorted()
                        SettingsReminderGroupItem(
                            id = group.id,
                            name = group.name,
                            hours = normalizedHours,
                            hoursLabel = normalizedHours.joinToString(", ").ifBlank {
                                "No default hours"
                            },
                            assignedTaskCount = assignedTemplateCountByGroupId[group.id] ?: 0
                        )
                    }
            }.collect { next ->
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        groups = next
                    )
                }
            }
        }

    private fun launchWork(block: suspend () -> Unit): Job =
        (externalScope ?: viewModelScope).launch {
            block()
        }

    internal fun disposeForTests() {
        observerJob?.cancel()
    }

    companion object {
        fun factory(
            reminderGroupRepository: ReminderGroupRepository,
            taskTemplateRepository: TaskTemplateRepository
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(SettingsReminderGroupsViewModel::class.java)) {
                        return SettingsReminderGroupsViewModel(
                            reminderGroupRepository = reminderGroupRepository,
                            taskTemplateRepository = taskTemplateRepository
                        ) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
    }
}

internal const val reminderGroupNameErrorMessage =
    "Name the reminder group before saving."

internal const val reminderGroupHoursErrorMessage =
    "Reminder hours must be between 0 and 23."

internal fun parseReminderGroupHoursInput(raw: String): List<Int>? {
    val value = raw.trim()
    if (value.isEmpty()) return emptyList()

    return value
        .split(",", ";", " ")
        .map { token -> token.trim() }
        .filter { token -> token.isNotEmpty() }
        .map { token -> token.toIntOrNull()?.takeIf { it in 0..23 } ?: return null }
        .distinct()
        .sorted()
}
