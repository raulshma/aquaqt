package com.keepaside.aquapt.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.keepaside.aquapt.core.backup.backupAutoSyncDefaultHour
import com.keepaside.aquapt.core.model.AppSettings
import com.keepaside.aquapt.core.model.AppThemePreference
import com.keepaside.aquapt.core.model.RegionalPreferencesMode
import com.keepaside.aquapt.core.repository.AppSettingsStore
import com.keepaside.aquapt.core.repository.BackupSecretsStore
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

internal const val settingsBackupSyncHourErrorMessage =
    "Backup sync hour must be between 0 and 23."

internal const val settingsBackupCredentialsErrorMessage =
    "Both S3 access key ID and secret access key are required when updating credentials."

internal const val settingsBackupRetentionDaysErrorMessage =
    "Backup retention days must be between 1 and 3650."

data class SettingsPreferencesDraft(
    val openRouterApiKey: String = "",
    val aiModel: String = "",
    val assistantMemoryModel: String = "",
    val assistantMemoryEnabled: Boolean = false,
    val themePreference: AppThemePreference = AppThemePreference.SYSTEM,
    val regionalPreferencesMode: RegionalPreferencesMode = RegionalPreferencesMode.AUTO,
    val notificationsEnabled: Boolean = false,
    val backupSyncEnabled: Boolean = false,
    val backupSyncHourInput: String = "",
    val backupS3Endpoint: String = "",
    val backupS3Region: String = "",
    val backupS3Bucket: String = "",
    val backupS3ObjectKey: String = "",
    val backupS3ForcePathStyle: Boolean = false,
    val backupUseVersionedKeys: Boolean = false,
    val backupRetentionDaysInput: String = "",
    val backupMasterKeyInput: String = "",
    val backupS3AccessKeyIdInput: String = "",
    val backupS3SecretAccessKeyInput: String = "",
    val backupMasterKeyConfigured: Boolean = false,
    val backupS3CredentialsConfigured: Boolean = false,
    val backupLastSyncedAt: String = "",
    val backupLastAutoSyncDate: String = "",
    val backupLastError: String = "",
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
    private val backupSecretsStore: BackupSecretsStore? = null,
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

    fun onAssistantMemoryModelChanged(value: String) {
        _uiState.update { state ->
            state.copy(draft = state.draft.copy(assistantMemoryModel = value))
        }
    }

    fun onAssistantMemoryEnabledChanged(value: Boolean) {
        _uiState.update { state ->
            state.copy(draft = state.draft.copy(assistantMemoryEnabled = value))
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

    fun onBackupSyncEnabledChanged(value: Boolean) {
        _uiState.update { state ->
            state.copy(draft = state.draft.copy(backupSyncEnabled = value))
        }
    }

    fun onBackupSyncHourChanged(value: String) {
        _uiState.update { state ->
            state.copy(draft = state.draft.copy(backupSyncHourInput = value))
        }
    }

    fun onBackupS3EndpointChanged(value: String) {
        _uiState.update { state ->
            state.copy(draft = state.draft.copy(backupS3Endpoint = value))
        }
    }

    fun onBackupS3RegionChanged(value: String) {
        _uiState.update { state ->
            state.copy(draft = state.draft.copy(backupS3Region = value))
        }
    }

    fun onBackupS3BucketChanged(value: String) {
        _uiState.update { state ->
            state.copy(draft = state.draft.copy(backupS3Bucket = value))
        }
    }

    fun onBackupS3ObjectKeyChanged(value: String) {
        _uiState.update { state ->
            state.copy(draft = state.draft.copy(backupS3ObjectKey = value))
        }
    }

    fun onBackupS3ForcePathStyleChanged(value: Boolean) {
        _uiState.update { state ->
            state.copy(draft = state.draft.copy(backupS3ForcePathStyle = value))
        }
    }

    fun onBackupUseVersionedKeysChanged(value: Boolean) {
        _uiState.update { state ->
            state.copy(draft = state.draft.copy(backupUseVersionedKeys = value))
        }
    }

    fun onBackupRetentionDaysChanged(value: String) {
        _uiState.update { state ->
            state.copy(draft = state.draft.copy(backupRetentionDaysInput = value))
        }
    }

    fun onBackupMasterKeyInputChanged(value: String) {
        _uiState.update { state ->
            state.copy(draft = state.draft.copy(backupMasterKeyInput = value))
        }
    }

    fun onBackupS3AccessKeyIdInputChanged(value: String) {
        _uiState.update { state ->
            state.copy(draft = state.draft.copy(backupS3AccessKeyIdInput = value))
        }
    }

    fun onBackupS3SecretAccessKeyInputChanged(value: String) {
        _uiState.update { state ->
            state.copy(draft = state.draft.copy(backupS3SecretAccessKeyInput = value))
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

        val backupSyncHour = parseSettingsBackupSyncHourInput(current.draft.backupSyncHourInput)
        if (
            backupSyncHour == null &&
            current.draft.backupSyncHourInput.trim().isNotEmpty()
        ) {
            _uiState.update { it.copy(statusMessage = settingsBackupSyncHourErrorMessage) }
            return
        }

        val backupRetentionDays = parseSettingsBackupRetentionDaysInput(
            current.draft.backupRetentionDaysInput
        )
        if (
            backupRetentionDays == null &&
            current.draft.backupRetentionDaysInput.trim().isNotEmpty()
        ) {
            _uiState.update { it.copy(statusMessage = settingsBackupRetentionDaysErrorMessage) }
            return
        }

        val backupAccessKeyId = current.draft.backupS3AccessKeyIdInput.trim()
        val backupSecretAccessKey = current.draft.backupS3SecretAccessKeyInput.trim()
        if ((backupAccessKeyId.isEmpty()) != (backupSecretAccessKey.isEmpty())) {
            _uiState.update { it.copy(statusMessage = settingsBackupCredentialsErrorMessage) }
            return
        }

        val backupMasterKeyInput = current.draft.backupMasterKeyInput.trim()

        launchWork {
            _uiState.update { it.copy(isSaving = true) }

            runCatching {
                val existing = appSettingsStore.settings.value
                val isManualMode = current.draft.regionalPreferencesMode == RegionalPreferencesMode.MANUAL
                val normalizedBackupHour = backupSyncHour
                    ?: existing.backupSyncHour
                    ?: backupAutoSyncDefaultHour

                if (backupSecretsStore != null) {
                    if (backupMasterKeyInput.isNotEmpty()) {
                        backupSecretsStore.saveBackupMasterKey(backupMasterKeyInput)
                    }

                    if (backupAccessKeyId.isNotEmpty()) {
                        backupSecretsStore.saveBackupS3Credentials(
                            accessKeyId = backupAccessKeyId,
                            secretAccessKey = backupSecretAccessKey
                        )
                    }
                }

                val hasMasterKey = backupSecretsStore?.hasBackupMasterKey()
                    ?: existing.backupMasterKeySet
                val hasS3Credentials = backupSecretsStore?.hasBackupS3Credentials()
                    ?: existing.backupS3CredentialsSet

                appSettingsStore.setSettings(
                    existing.copy(
                        openRouterApiKey = current.draft.openRouterApiKey.trim(),
                        aiModel = current.draft.aiModel.trim(),
                        assistantMemoryModel = current.draft.assistantMemoryModel
                            .trim()
                            .takeIf { it.isNotEmpty() },
                        assistantMemoryEnabled = current.draft.assistantMemoryEnabled,
                        themePreference = current.draft.themePreference,
                        regionalPreferencesMode = current.draft.regionalPreferencesMode,
                        notificationsEnabled = current.draft.notificationsEnabled,
                        backupSyncEnabled = current.draft.backupSyncEnabled,
                        backupSyncHour = normalizedBackupHour,
                        backupS3Endpoint = current.draft.backupS3Endpoint.trim().takeIf { it.isNotEmpty() },
                        backupS3Region = current.draft.backupS3Region.trim().takeIf { it.isNotEmpty() },
                        backupS3Bucket = current.draft.backupS3Bucket.trim().takeIf { it.isNotEmpty() },
                        backupS3ObjectKey = current.draft.backupS3ObjectKey.trim()
                            .takeIf { it.isNotEmpty() },
                        backupS3ForcePathStyle = current.draft.backupS3ForcePathStyle,
                        backupUseVersionedKeys = current.draft.backupUseVersionedKeys,
                        backupRetentionDays = backupRetentionDays,
                        backupMasterKeySet = hasMasterKey,
                        backupS3CredentialsSet = hasS3Credentials,
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

                hasMasterKey to hasS3Credentials
            }.onSuccess { (hasMasterKey, hasS3Credentials) ->
                _uiState.update { state ->
                    state.copy(
                        isSaving = false,
                        draft = state.draft.copy(
                            backupMasterKeyInput = "",
                            backupS3AccessKeyIdInput = "",
                            backupS3SecretAccessKeyInput = "",
                            backupMasterKeyConfigured = hasMasterKey,
                            backupS3CredentialsConfigured = hasS3Credentials
                        ),
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

    fun clearBackupMasterKey() {
        if (_uiState.value.isSaving) return

        val secretsStore = backupSecretsStore ?: run {
            _uiState.update {
                it.copy(statusMessage = "Backup secure store is unavailable on this build.")
            }
            return
        }

        launchWork {
            _uiState.update { it.copy(isSaving = true) }

            runCatching {
                secretsStore.clearBackupMasterKey()

                val current = appSettingsStore.settings.value
                appSettingsStore.setSettings(
                    current.copy(
                        backupMasterKeySet = false
                    )
                )
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        draft = it.draft.copy(
                            backupMasterKeyInput = "",
                            backupMasterKeyConfigured = false
                        ),
                        statusMessage = "Stored backup master key cleared."
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        statusMessage = error.message ?: "Unable to clear backup master key."
                    )
                }
            }
        }
    }

    fun clearBackupS3Credentials() {
        if (_uiState.value.isSaving) return

        val secretsStore = backupSecretsStore ?: run {
            _uiState.update {
                it.copy(statusMessage = "Backup secure store is unavailable on this build.")
            }
            return
        }

        launchWork {
            _uiState.update { it.copy(isSaving = true) }

            runCatching {
                secretsStore.clearBackupS3Credentials()

                val current = appSettingsStore.settings.value
                appSettingsStore.setSettings(
                    current.copy(
                        backupS3CredentialsSet = false
                    )
                )
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        draft = it.draft.copy(
                            backupS3AccessKeyIdInput = "",
                            backupS3SecretAccessKeyInput = "",
                            backupS3CredentialsConfigured = false
                        ),
                        statusMessage = "Stored S3 credentials cleared."
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        statusMessage = error.message ?: "Unable to clear S3 credentials."
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
        fun factory(
            appSettingsStore: AppSettingsStore,
            backupSecretsStore: BackupSecretsStore? = null
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(SettingsPreferencesViewModel::class.java)) {
                        return SettingsPreferencesViewModel(
                            appSettingsStore = appSettingsStore,
                            backupSecretsStore = backupSecretsStore
                        ) as T
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

internal fun parseSettingsBackupSyncHourInput(raw: String): Int? {
    val value = raw.trim()
    if (value.isEmpty()) return null

    return value.toIntOrNull()?.takeIf { it in 0..23 }
}

internal fun parseSettingsBackupRetentionDaysInput(raw: String): Int? {
    val value = raw.trim()
    if (value.isEmpty()) return null

    return value.toIntOrNull()?.takeIf { it in 1..3650 }
}

private fun AppSettings.toDraft(): SettingsPreferencesDraft = SettingsPreferencesDraft(
    openRouterApiKey = openRouterApiKey,
    aiModel = aiModel,
    assistantMemoryModel = assistantMemoryModel.orEmpty(),
    assistantMemoryEnabled = assistantMemoryEnabled,
    themePreference = themePreference,
    regionalPreferencesMode = regionalPreferencesMode,
    notificationsEnabled = notificationsEnabled,
    backupSyncEnabled = backupSyncEnabled,
    backupSyncHourInput = (backupSyncHour ?: backupAutoSyncDefaultHour).toString(),
    backupS3Endpoint = backupS3Endpoint.orEmpty(),
    backupS3Region = backupS3Region.orEmpty(),
    backupS3Bucket = backupS3Bucket.orEmpty(),
    backupS3ObjectKey = backupS3ObjectKey.orEmpty(),
    backupS3ForcePathStyle = backupS3ForcePathStyle,
    backupUseVersionedKeys = backupUseVersionedKeys,
    backupRetentionDaysInput = backupRetentionDays?.toString().orEmpty(),
    backupMasterKeyConfigured = backupMasterKeySet,
    backupS3CredentialsConfigured = backupS3CredentialsSet,
    backupLastSyncedAt = backupLastSyncedAt.orEmpty(),
    backupLastAutoSyncDate = backupLastAutoSyncDate.orEmpty(),
    backupLastError = backupLastError.orEmpty(),
    reminderHoursInput = reminderHours.joinToString(", "),
    defaultLocale = defaultLocale.orEmpty(),
    defaultTimezone = defaultTimezone.orEmpty(),
    defaultCountryCode = defaultCountryCode.orEmpty(),
    defaultCountryName = defaultCountryName.orEmpty(),
    defaultCurrency = defaultCurrency.orEmpty()
)