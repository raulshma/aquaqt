package com.keepaside.aquapt.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.keepaside.aquapt.core.backup.backupAutoSyncDefaultHour
import com.keepaside.aquapt.core.localization.convertCurrencyAmount
import com.keepaside.aquapt.core.localization.defaultRegionalCurrencyCode
import com.keepaside.aquapt.core.localization.defaultRegionalLocale
import com.keepaside.aquapt.core.localization.formatCurrencyAmount
import com.keepaside.aquapt.core.localization.ManualRegionalSettingsInput
import com.keepaside.aquapt.core.localization.ManualRegionalSettingsResult
import com.keepaside.aquapt.core.localization.normalizeCurrencyCode
import com.keepaside.aquapt.core.localization.applyRegionalDefaults
import com.keepaside.aquapt.core.localization.resolveManualRegionalSettings
import com.keepaside.aquapt.core.localization.resolveRegionalDefaults
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

internal const val settingsRegionalPreviewAmountErrorMessage =
    "Enter a valid amount for conversion preview."

internal const val settingsRegionalPreviewCurrencyErrorMessage =
    "Select valid base and target currency codes for conversion preview."

fun interface RegionalCurrencyPreviewConverter {
    suspend fun convert(value: Double, fromCurrency: String, toCurrency: String): Double
}

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
    val statusMessage: String = settingsPreferencesDefaultStatus,
    val regionalConversionAmountInput: String = "100",
    val regionalConversionBaseCurrency: String = defaultRegionalCurrencyCode,
    val regionalConversionPreviewLabel: String = "",
    val regionalConversionErrorMessage: String? = null,
    val isRegionalConversionLoading: Boolean = false
)

class SettingsPreferencesViewModel(
    private val appSettingsStore: AppSettingsStore,
    private val backupSecretsStore: BackupSecretsStore? = null,
    private val externalScope: CoroutineScope? = null,
    private val regionalCurrencyPreviewConverter: RegionalCurrencyPreviewConverter =
        RegionalCurrencyPreviewConverter { value, fromCurrency, toCurrency ->
            convertCurrencyAmount(
                value = value,
                fromCurrency = fromCurrency,
                toCurrency = toCurrency
            )
        }
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
            state.copy(
                draft = state.draft.copy(regionalPreferencesMode = value),
                regionalConversionPreviewLabel = if (value == RegionalPreferencesMode.MANUAL) {
                    state.regionalConversionPreviewLabel
                } else {
                    ""
                },
                regionalConversionErrorMessage = null
            )
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
            state.copy(
                draft = state.draft.copy(defaultLocale = value),
                regionalConversionPreviewLabel = "",
                regionalConversionErrorMessage = null
            )
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
            state.copy(
                draft = state.draft.copy(defaultCurrency = value),
                regionalConversionPreviewLabel = "",
                regionalConversionErrorMessage = null
            )
        }
    }

    fun onRegionalConversionAmountChanged(value: String) {
        _uiState.update { state ->
            state.copy(
                regionalConversionAmountInput = value,
                regionalConversionPreviewLabel = "",
                regionalConversionErrorMessage = null
            )
        }
    }

    fun onRegionalConversionBaseCurrencyChanged(value: String) {
        _uiState.update { state ->
            state.copy(
                regionalConversionBaseCurrency = value,
                regionalConversionPreviewLabel = "",
                regionalConversionErrorMessage = null
            )
        }
    }

    fun refreshRegionalConversionPreview() {
        val current = _uiState.value
        if (current.isRegionalConversionLoading || current.isSaving) return

        val amount = parseSettingsRegionalPreviewAmountInput(current.regionalConversionAmountInput)
        if (amount == null) {
            _uiState.update {
                it.copy(
                    regionalConversionPreviewLabel = "",
                    regionalConversionErrorMessage = settingsRegionalPreviewAmountErrorMessage
                )
            }
            return
        }

        val fromCurrency = normalizeCurrencyCode(current.regionalConversionBaseCurrency)
        val toCurrency = normalizeCurrencyCode(current.draft.defaultCurrency)
        if (fromCurrency == null || toCurrency == null) {
            _uiState.update {
                it.copy(
                    regionalConversionPreviewLabel = "",
                    regionalConversionErrorMessage = settingsRegionalPreviewCurrencyErrorMessage
                )
            }
            return
        }

        val localeTag = current.draft.defaultLocale
            .trim()
            .takeIf { value -> value.isNotEmpty() }
            ?: defaultRegionalLocale

        launchWork {
            _uiState.update {
                it.copy(
                    isRegionalConversionLoading = true,
                    regionalConversionErrorMessage = null
                )
            }

            runCatching {
                val convertedAmount = regionalCurrencyPreviewConverter.convert(
                    value = amount,
                    fromCurrency = fromCurrency,
                    toCurrency = toCurrency
                )
                val sourceLabel = formatCurrencyAmount(
                    value = amount,
                    currencyCode = fromCurrency,
                    localeTag = localeTag,
                    maximumFractionDigits = 2
                )
                val targetLabel = formatCurrencyAmount(
                    value = convertedAmount,
                    currencyCode = toCurrency,
                    localeTag = localeTag,
                    maximumFractionDigits = 2
                )
                "$sourceLabel ≈ $targetLabel"
            }.onSuccess { previewLabel ->
                _uiState.update {
                    it.copy(
                        isRegionalConversionLoading = false,
                        regionalConversionPreviewLabel = previewLabel,
                        regionalConversionErrorMessage = null
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isRegionalConversionLoading = false,
                        regionalConversionPreviewLabel = "",
                        regionalConversionErrorMessage =
                            error.message ?: "Unable to load conversion preview right now."
                    )
                }
            }
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
        val regionalDefaults = resolveRegionalDefaults()
        val manualRegionalResolution = if (
            current.draft.regionalPreferencesMode == RegionalPreferencesMode.MANUAL
        ) {
            val countryInput = current.draft.defaultCountryCode
                .trim()
                .ifEmpty { current.draft.defaultCountryName.trim() }
            resolveManualRegionalSettings(
                input = ManualRegionalSettingsInput(
                    country = countryInput,
                    currency = current.draft.defaultCurrency,
                    fallbackCountryCode = regionalDefaults.defaultCountryCode
                ),
                detectedDefaults = regionalDefaults
            )
        } else {
            null
        }
        if (manualRegionalResolution is ManualRegionalSettingsResult.Error) {
            _uiState.update { it.copy(statusMessage = manualRegionalResolution.message) }
            return
        }

        launchWork {
            _uiState.update { it.copy(isSaving = true) }

            runCatching {
                val existing = appSettingsStore.settings.value
                val isManualMode = current.draft.regionalPreferencesMode == RegionalPreferencesMode.MANUAL
                val manualRegionalValue =
                    (manualRegionalResolution as? ManualRegionalSettingsResult.Success)?.value
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

                val normalizedSettings = applyRegionalDefaults(
                    settings = existing.copy(
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
                            manualRegionalValue?.defaultCountryCode
                        } else {
                            null
                        },
                        defaultCountryName = if (isManualMode) {
                            manualRegionalValue?.defaultCountryName
                        } else {
                            null
                        },
                        defaultCurrency = if (isManualMode) {
                            manualRegionalValue?.defaultCurrency
                        } else {
                            null
                        }
                    ),
                    detectedDefaults = regionalDefaults
                )

                appSettingsStore.setSettings(normalizedSettings)

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
                regionalConversionPreviewLabel = "",
                regionalConversionErrorMessage = null,
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
            backupSecretsStore: BackupSecretsStore? = null,
            regionalCurrencyPreviewConverter: RegionalCurrencyPreviewConverter =
                RegionalCurrencyPreviewConverter { value, fromCurrency, toCurrency ->
                    convertCurrencyAmount(
                        value = value,
                        fromCurrency = fromCurrency,
                        toCurrency = toCurrency
                    )
                }
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(SettingsPreferencesViewModel::class.java)) {
                        return SettingsPreferencesViewModel(
                            appSettingsStore = appSettingsStore,
                            backupSecretsStore = backupSecretsStore,
                            regionalCurrencyPreviewConverter = regionalCurrencyPreviewConverter
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

internal fun parseSettingsRegionalPreviewAmountInput(raw: String): Double? {
    val parsed = raw.trim().toDoubleOrNull() ?: return null
    if (!parsed.isFinite() || parsed < 0) {
        return null
    }

    return parsed
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