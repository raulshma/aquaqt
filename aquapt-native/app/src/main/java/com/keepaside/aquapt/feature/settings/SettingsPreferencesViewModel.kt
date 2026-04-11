package com.keepaside.aquapt.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.keepaside.aquapt.core.model.AppSettings
import com.keepaside.aquapt.core.model.AppThemePreference
import com.keepaside.aquapt.core.model.RegionalPreferencesMode
import com.keepaside.aquapt.core.repository.AppSettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal const val settingsPreferencesDefaultStatus =
    "Configure appearance, regional defaults, and assistant runtime settings for your native AquaPT experience."

internal const val settingsReminderHoursErrorMessage =
    "Reminder hours must be between 0 and 23."

data class SettingsPreferencesDraft(
    val openRouterApiKey: String = "",
    val aiModel: String = "",
    val themePreference: AppThemePreference = AppThemePreference.SYSTEM,
    val regionalPreferencesMode: RegionalPreferencesMode = RegionalPreferencesMode.AUTO,
    val notificationsEnabled: Boolean = false,
    val reminderHoursInput: String = "",
    val defaultLocale: String = "",
    val defaultTimezone: String = "",
    val defaultCountryCode: String = "",
    val defaultCountryName: String = "",
    val defaultCurrency: String = ""
)

data class SettingsPreferencesUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val draft: SettingsPreferencesDraft = SettingsPreferencesDraft(),
    val statusMessage: String = settingsPreferencesDefaultStatus
)

class SettingsPreferencesViewModel(
    private val appSettingsStore: AppSettingsStore,
    private val externalScope: CoroutineScope? = null
) : ViewModel() {

    private var observerJob: Job? = null

    private val _uiState = MutableStateFlow(SettingsPreferencesUiState())
    val uiState: StateFlow<SettingsPreferencesUiState> = _uiState.asStateFlow()

    init {
        observerJob = observePreferences()
    }

    fun onOpenRouterApiKeyChanged(value: String) {
        _uiState.update { state ->
            state.copy(draft = state.draft.copy(openRouterApiKey = value))
        }
    }

    fun onAiModelChanged(value: String) {
        _uiState.update { state ->
            state.copy(draft = state.draft.copy(aiModel = value))
        }
    }

    fun onThemePreferenceChanged(value: AppThemePreference) {
        _uiState.update { state ->
            state.copy(draft = state.draft.copy(themePreference = value))
        }
    }

    fun onRegionalPreferencesModeChanged(value: RegionalPreferencesMode) {
        _uiState.update { state ->
            state.copy(draft = state.draft.copy(regionalPreferencesMode = value))
        }
    }

    fun onNotificationsEnabledChanged(value: Boolean) {
        _uiState.update { state ->
            state.copy(draft = state.draft.copy(notificationsEnabled = value))
        }
    }

    fun onReminderHoursChanged(value: String) {
        _uiState.update { state ->
            state.copy(draft = state.draft.copy(reminderHoursInput = value))
        }
    }

    fun onDefaultLocaleChanged(value: String) {
        _uiState.update { state ->
            state.copy(draft = state.draft.copy(defaultLocale = value))
        }
    }

    fun onDefaultTimezoneChanged(value: String) {
        _uiState.update { state ->
            state.copy(draft = state.draft.copy(defaultTimezone = value))
        }
    }

    fun onDefaultCountryCodeChanged(value: String) {
        _uiState.update { state ->
            state.copy(draft = state.draft.copy(defaultCountryCode = value))
        }
    }

    fun onDefaultCountryNameChanged(value: String) {
        _uiState.update { state ->
            state.copy(draft = state.draft.copy(defaultCountryName = value))
        }
    }

    fun onDefaultCurrencyChanged(value: String) {
        _uiState.update { state ->
            state.copy(draft = state.draft.copy(defaultCurrency = value))
        }
    }

    fun savePreferences() {
        val current = _uiState.value
        if (current.isSaving) return

        val reminderHours = parseSettingsReminderHoursInput(current.draft.reminderHoursInput)
        if (reminderHours == null) {
            _uiState.update { it.copy(statusMessage = settingsReminderHoursErrorMessage) }
            return
        }

        launchWork {
            _uiState.update { it.copy(isSaving = true) }

            runCatching {
                val existing = appSettingsStore.settings.value
                val isManualMode = current.draft.regionalPreferencesMode == RegionalPreferencesMode.MANUAL

                appSettingsStore.setSettings(
                    existing.copy(
                        openRouterApiKey = current.draft.openRouterApiKey.trim(),
                        aiModel = current.draft.aiModel.trim(),
                        themePreference = current.draft.themePreference,
                        regionalPreferencesMode = current.draft.regionalPreferencesMode,
                        notificationsEnabled = current.draft.notificationsEnabled,
                        reminderHours = reminderHours,
                        defaultLocale = if (isManualMode) {
                            current.draft.defaultLocale.trim().takeIf { it.isNotEmpty() }
                        } else {
                            null
                        },
                        defaultTimezone = if (isManualMode) {
                            current.draft.defaultTimezone.trim().takeIf { it.isNotEmpty() }
                        } else {
                            null
                        },
                        defaultCountryCode = if (isManualMode) {
                            current.draft.defaultCountryCode
                                .trim()
                                .uppercase()
                                .takeIf { it.isNotEmpty() }
                        } else {
                            null
                        },
                        defaultCountryName = if (isManualMode) {
                            current.draft.defaultCountryName.trim().takeIf { it.isNotEmpty() }
                        } else {
                            null
                        },
                        defaultCurrency = if (isManualMode) {
                            current.draft.defaultCurrency
                                .trim()
                                .uppercase()
                                .takeIf { it.isNotEmpty() }
                        } else {
                            null
                        }
                    )
                )
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        statusMessage = "Settings saved."
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        statusMessage = error.message ?: "Unable to save settings."
                    )
                }
            }
        }
    }

    fun resetDraftToSaved() {
        if (_uiState.value.isSaving) return

        _uiState.update {
            it.copy(
                draft = appSettingsStore.settings.value.toDraft(),
                statusMessage = "Reverted unsaved changes."
            )
        }
    }

    private fun observePreferences(): Job = launchWork {
        appSettingsStore.settings.collect { settings ->
            _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    draft = settings.toDraft()
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
        fun factory(appSettingsStore: AppSettingsStore): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(SettingsPreferencesViewModel::class.java)) {
                        return SettingsPreferencesViewModel(appSettingsStore) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
    }
}

internal fun parseSettingsReminderHoursInput(raw: String): List<Int>? {
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

private fun AppSettings.toDraft(): SettingsPreferencesDraft = SettingsPreferencesDraft(
    openRouterApiKey = openRouterApiKey,
    aiModel = aiModel,
    themePreference = themePreference,
    regionalPreferencesMode = regionalPreferencesMode,
    notificationsEnabled = notificationsEnabled,
    reminderHoursInput = reminderHours.joinToString(", "),
    defaultLocale = defaultLocale.orEmpty(),
    defaultTimezone = defaultTimezone.orEmpty(),
    defaultCountryCode = defaultCountryCode.orEmpty(),
    defaultCountryName = defaultCountryName.orEmpty(),
    defaultCurrency = defaultCurrency.orEmpty()
)