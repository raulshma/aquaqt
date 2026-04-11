package com.keepaside.aquapt.feature.settings

import com.keepaside.aquapt.core.localization.resolveRegionalDefaults
import com.keepaside.aquapt.core.model.AppSettings
import com.keepaside.aquapt.core.model.AppThemePreference
import com.keepaside.aquapt.core.model.RegionalPreferencesMode
import com.keepaside.aquapt.core.repository.AppSettingsStore
import com.keepaside.aquapt.core.repository.BackupS3Credentials
import com.keepaside.aquapt.core.repository.BackupSecretsStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsPreferencesViewModelTest {

    @Test
    fun `regional conversion preview uses converter and publishes formatted label`() = runTest {
        val fakeStore = FakePreferencesAppSettingsStore(
            AppSettings(
                regionalPreferencesMode = RegionalPreferencesMode.MANUAL,
                defaultLocale = "en-US",
                defaultTimezone = "America/New_York",
                defaultCountryCode = "US",
                defaultCountryName = "United States",
                defaultCurrency = "USD"
            )
        )
        val converter = FakeRegionalCurrencyPreviewConverter(convertedAmount = 9162.5)
        val viewModel = SettingsPreferencesViewModel(
            appSettingsStore = fakeStore,
            externalScope = this,
            regionalCurrencyPreviewConverter = converter
        )

        try {
            advanceUntilIdle()

            viewModel.onDefaultCurrencyChanged("INR")
            viewModel.onRegionalConversionBaseCurrencyChanged("USD")
            viewModel.onRegionalConversionAmountChanged("100")
            viewModel.refreshRegionalConversionPreview()
            advanceUntilIdle()

            assertEquals(1, converter.callCount)
            assertEquals(100.0, converter.lastValue, 0.00001)
            assertEquals("USD", converter.lastFromCurrency)
            assertEquals("INR", converter.lastToCurrency)
            assertTrue(viewModel.uiState.value.regionalConversionPreviewLabel.contains("≈"))
            assertEquals(null, viewModel.uiState.value.regionalConversionErrorMessage)
            assertFalse(viewModel.uiState.value.isRegionalConversionLoading)
        } finally {
            viewModel.disposeForTests()
        }
    }

    @Test
    fun `regional conversion preview rejects invalid amount input`() = runTest {
        val fakeStore = FakePreferencesAppSettingsStore(
            AppSettings(
                regionalPreferencesMode = RegionalPreferencesMode.MANUAL,
                defaultCurrency = "USD"
            )
        )
        val converter = FakeRegionalCurrencyPreviewConverter(convertedAmount = 1.0)
        val viewModel = SettingsPreferencesViewModel(
            appSettingsStore = fakeStore,
            externalScope = this,
            regionalCurrencyPreviewConverter = converter
        )

        try {
            advanceUntilIdle()

            viewModel.onDefaultCurrencyChanged("EUR")
            viewModel.onRegionalConversionBaseCurrencyChanged("USD")
            viewModel.onRegionalConversionAmountChanged("abc")
            viewModel.refreshRegionalConversionPreview()
            advanceUntilIdle()

            assertEquals(0, converter.callCount)
            assertEquals(
                settingsRegionalPreviewAmountErrorMessage,
                viewModel.uiState.value.regionalConversionErrorMessage
            )
            assertEquals("", viewModel.uiState.value.regionalConversionPreviewLabel)
        } finally {
            viewModel.disposeForTests()
        }
    }

    @Test
    fun `save preferences persists normalized manual values`() = runTest {
        val fakeStore = FakePreferencesAppSettingsStore()
        val viewModel = SettingsPreferencesViewModel(
            appSettingsStore = fakeStore,
            externalScope = this
        )

        try {
            advanceUntilIdle()

            viewModel.onThemePreferenceChanged(AppThemePreference.DARK)
            viewModel.onRegionalPreferencesModeChanged(RegionalPreferencesMode.MANUAL)
            viewModel.onNotificationsEnabledChanged(true)
            viewModel.onBackupSyncEnabledChanged(true)
            viewModel.onBackupSyncHourChanged("5")
            viewModel.onAssistantMemoryEnabledChanged(true)
            viewModel.onAssistantMemoryModelChanged("openai/gpt-4o-mini")
            viewModel.onReminderHoursChanged("18, 8; 18")
            viewModel.onDefaultLocaleChanged(" en-US ")
            viewModel.onDefaultTimezoneChanged(" America/New_York ")
            viewModel.onDefaultCountryCodeChanged(" us ")
            viewModel.onDefaultCountryNameChanged(" United States ")
            viewModel.onDefaultCurrencyChanged(" usd ")

            viewModel.savePreferences()
            advanceUntilIdle()

            val saved = fakeStore.settings.value
            assertEquals(AppThemePreference.DARK, saved.themePreference)
            assertEquals(RegionalPreferencesMode.MANUAL, saved.regionalPreferencesMode)
            assertEquals(true, saved.notificationsEnabled)
            assertEquals(true, saved.backupSyncEnabled)
            assertEquals(5, saved.backupSyncHour)
            assertEquals(true, saved.assistantMemoryEnabled)
            assertEquals("openai/gpt-4o-mini", saved.assistantMemoryModel)
            assertEquals(listOf(8, 18), saved.reminderHours)
            assertEquals("en-US", saved.defaultLocale)
            assertEquals("America/New_York", saved.defaultTimezone)
            assertEquals("US", saved.defaultCountryCode)
            assertEquals("United States", saved.defaultCountryName)
            assertEquals("USD", saved.defaultCurrency)
            assertEquals("Settings saved.", viewModel.uiState.value.statusMessage)
        } finally {
            viewModel.disposeForTests()
        }
    }

    @Test
    fun `invalid reminder hours block save`() = runTest {
        val fakeStore = FakePreferencesAppSettingsStore()
        val viewModel = SettingsPreferencesViewModel(
            appSettingsStore = fakeStore,
            externalScope = this
        )

        try {
            advanceUntilIdle()

            viewModel.onReminderHoursChanged("8, 24")
            viewModel.savePreferences()
            advanceUntilIdle()

            assertEquals(settingsReminderHoursErrorMessage, viewModel.uiState.value.statusMessage)
            assertEquals(0, fakeStore.setCalls)
        } finally {
            viewModel.disposeForTests()
        }
    }

    @Test
    fun `invalid backup sync hour blocks save`() = runTest {
        val fakeStore = FakePreferencesAppSettingsStore()
        val viewModel = SettingsPreferencesViewModel(
            appSettingsStore = fakeStore,
            externalScope = this
        )

        try {
            advanceUntilIdle()

            viewModel.onBackupSyncHourChanged("99")
            viewModel.savePreferences()
            advanceUntilIdle()

            assertEquals(settingsBackupSyncHourErrorMessage, viewModel.uiState.value.statusMessage)
            assertEquals(0, fakeStore.setCalls)
        } finally {
            viewModel.disposeForTests()
        }
    }

    @Test
    fun `auto regional mode reapplies detected defaults on save`() = runTest {
        val fakeStore = FakePreferencesAppSettingsStore(
            AppSettings(
                regionalPreferencesMode = RegionalPreferencesMode.MANUAL,
                defaultLocale = "en-US",
                defaultTimezone = "America/New_York",
                defaultCountryCode = "US",
                defaultCountryName = "United States",
                defaultCurrency = "USD"
            )
        )
        val viewModel = SettingsPreferencesViewModel(
            appSettingsStore = fakeStore,
            externalScope = this
        )

        try {
            advanceUntilIdle()

            viewModel.onRegionalPreferencesModeChanged(RegionalPreferencesMode.AUTO)
            viewModel.savePreferences()
            advanceUntilIdle()

            val saved = fakeStore.settings.value
            val detectedDefaults = resolveRegionalDefaults()
            assertEquals(RegionalPreferencesMode.AUTO, saved.regionalPreferencesMode)
            assertEquals(detectedDefaults.defaultLocale, saved.defaultLocale)
            assertEquals(detectedDefaults.defaultTimezone, saved.defaultTimezone)
            assertEquals(detectedDefaults.defaultCountryCode, saved.defaultCountryCode)
            assertEquals(detectedDefaults.defaultCountryName, saved.defaultCountryName)
            assertEquals(detectedDefaults.defaultCurrency, saved.defaultCurrency)
        } finally {
            viewModel.disposeForTests()
        }
    }

    @Test
    fun `invalid manual country blocks save`() = runTest {
        val fakeStore = FakePreferencesAppSettingsStore()
        val viewModel = SettingsPreferencesViewModel(
            appSettingsStore = fakeStore,
            externalScope = this
        )

        try {
            advanceUntilIdle()

            viewModel.onRegionalPreferencesModeChanged(RegionalPreferencesMode.MANUAL)
            viewModel.onDefaultCountryCodeChanged("Atlantis")
            viewModel.savePreferences()
            advanceUntilIdle()

            assertEquals(
                "Enter a valid country name or 2-letter country code.",
                viewModel.uiState.value.statusMessage
            )
            assertEquals(0, fakeStore.setCalls)
        } finally {
            viewModel.disposeForTests()
        }
    }

    @Test
    fun `invalid manual currency blocks save`() = runTest {
        val fakeStore = FakePreferencesAppSettingsStore()
        val viewModel = SettingsPreferencesViewModel(
            appSettingsStore = fakeStore,
            externalScope = this
        )

        try {
            advanceUntilIdle()

            viewModel.onRegionalPreferencesModeChanged(RegionalPreferencesMode.MANUAL)
            viewModel.onDefaultCountryCodeChanged("IN")
            viewModel.onDefaultCurrencyChanged("rupees")
            viewModel.savePreferences()
            advanceUntilIdle()

            assertEquals(
                "Enter a valid 3-letter currency code.",
                viewModel.uiState.value.statusMessage
            )
            assertEquals(0, fakeStore.setCalls)
        } finally {
            viewModel.disposeForTests()
        }
    }

    @Test
    fun `reminder hour parser normalizes and validates values`() {
        assertEquals(listOf(8, 18, 21), parseSettingsReminderHoursInput("18, 8; 21 8"))
        assertEquals(emptyList<Int>(), parseSettingsReminderHoursInput("   "))
        assertEquals(null, parseSettingsReminderHoursInput("9, 99"))

        assertEquals(7, parseSettingsBackupSyncHourInput(" 7 "))
        assertEquals(null, parseSettingsBackupSyncHourInput("24"))
        assertEquals(null, parseSettingsBackupSyncHourInput(""))

        assertEquals(30, parseSettingsBackupRetentionDaysInput("30"))
        assertEquals(null, parseSettingsBackupRetentionDaysInput("0"))
        assertEquals(null, parseSettingsBackupRetentionDaysInput(""))

        assertEquals(42.5, parseSettingsRegionalPreviewAmountInput("42.5"))
        assertEquals(0.0, parseSettingsRegionalPreviewAmountInput("0"))
        assertEquals(null, parseSettingsRegionalPreviewAmountInput("-1"))
        assertEquals(null, parseSettingsRegionalPreviewAmountInput("abc"))
    }

    @Test
    fun `save preferences persists backup destination and secure secret status`() = runTest {
        val fakeStore = FakePreferencesAppSettingsStore()
        val fakeSecretsStore = FakeBackupSecretsStore()
        val viewModel = SettingsPreferencesViewModel(
            appSettingsStore = fakeStore,
            backupSecretsStore = fakeSecretsStore,
            externalScope = this
        )

        try {
            advanceUntilIdle()

            viewModel.onBackupSyncEnabledChanged(true)
            viewModel.onBackupSyncHourChanged("4")
            viewModel.onBackupS3EndpointChanged(" https://s3.example.com ")
            viewModel.onBackupS3RegionChanged(" us-east-1 ")
            viewModel.onBackupS3BucketChanged(" aquapt-backups ")
            viewModel.onBackupS3ObjectKeyChanged(" aquapt/backups/latest.enc.json ")
            viewModel.onBackupS3ForcePathStyleChanged(true)
            viewModel.onBackupUseVersionedKeysChanged(true)
            viewModel.onBackupRetentionDaysChanged("45")
            viewModel.onBackupMasterKeyInputChanged(" super-secret-master-key ")
            viewModel.onBackupS3AccessKeyIdInputChanged(" AKIA123 ")
            viewModel.onBackupS3SecretAccessKeyInputChanged(" secret-xyz ")

            viewModel.savePreferences()
            advanceUntilIdle()

            val saved = fakeStore.settings.value
            assertEquals(true, saved.backupSyncEnabled)
            assertEquals(4, saved.backupSyncHour)
            assertEquals("https://s3.example.com", saved.backupS3Endpoint)
            assertEquals("us-east-1", saved.backupS3Region)
            assertEquals("aquapt-backups", saved.backupS3Bucket)
            assertEquals("aquapt/backups/latest.enc.json", saved.backupS3ObjectKey)
            assertEquals(true, saved.backupS3ForcePathStyle)
            assertEquals(true, saved.backupUseVersionedKeys)
            assertEquals(45, saved.backupRetentionDays)
            assertEquals(true, saved.backupMasterKeySet)
            assertEquals(true, saved.backupS3CredentialsSet)

            assertEquals("super-secret-master-key", fakeSecretsStore.masterKey)
            assertEquals("AKIA123", fakeSecretsStore.credentials?.accessKeyId)
            assertEquals("secret-xyz", fakeSecretsStore.credentials?.secretAccessKey)
            assertEquals("Settings saved.", viewModel.uiState.value.statusMessage)
        } finally {
            viewModel.disposeForTests()
        }
    }

    @Test
    fun `partial backup credentials block save`() = runTest {
        val fakeStore = FakePreferencesAppSettingsStore()
        val fakeSecretsStore = FakeBackupSecretsStore()
        val viewModel = SettingsPreferencesViewModel(
            appSettingsStore = fakeStore,
            backupSecretsStore = fakeSecretsStore,
            externalScope = this
        )

        try {
            advanceUntilIdle()

            viewModel.onBackupS3AccessKeyIdInputChanged("AKIA123")
            viewModel.savePreferences()
            advanceUntilIdle()

            assertEquals(settingsBackupCredentialsErrorMessage, viewModel.uiState.value.statusMessage)
            assertEquals(0, fakeStore.setCalls)
        } finally {
            viewModel.disposeForTests()
        }
    }

    @Test
    fun `clear backup secrets updates configured status`() = runTest {
        val fakeStore = FakePreferencesAppSettingsStore(
            AppSettings(
                backupMasterKeySet = true,
                backupS3CredentialsSet = true
            )
        )
        val fakeSecretsStore = FakeBackupSecretsStore(
            masterKey = "already-set",
            credentials = BackupS3Credentials("AKIA123", "secret-xyz")
        )
        val viewModel = SettingsPreferencesViewModel(
            appSettingsStore = fakeStore,
            backupSecretsStore = fakeSecretsStore,
            externalScope = this
        )

        try {
            advanceUntilIdle()

            viewModel.clearBackupMasterKey()
            viewModel.clearBackupS3Credentials()
            advanceUntilIdle()

            val saved = fakeStore.settings.value
            assertFalse(saved.backupMasterKeySet)
            assertFalse(saved.backupS3CredentialsSet)
            assertTrue(fakeSecretsStore.masterKey.isEmpty())
            assertTrue(fakeSecretsStore.credentials == null)
        } finally {
            viewModel.disposeForTests()
        }
    }
}

private class FakePreferencesAppSettingsStore(
    initial: AppSettings = AppSettings()
) : AppSettingsStore {
    private val flow = MutableStateFlow(initial)

    var setCalls: Int = 0

    override val settings: StateFlow<AppSettings> = flow.asStateFlow()

    override suspend fun setSettings(settings: AppSettings) {
        setCalls += 1
        flow.value = settings
    }
}

private class FakeBackupSecretsStore(
    var masterKey: String = "",
    var credentials: BackupS3Credentials? = null
) : BackupSecretsStore {
    override suspend fun saveBackupMasterKey(masterKey: String) {
        this.masterKey = masterKey.trim()
    }

    override suspend fun loadBackupMasterKey(): String = masterKey

    override suspend fun hasBackupMasterKey(): Boolean = masterKey.isNotEmpty()

    override suspend fun clearBackupMasterKey() {
        masterKey = ""
    }

    override suspend fun saveBackupS3Credentials(accessKeyId: String, secretAccessKey: String) {
        credentials = BackupS3Credentials(
            accessKeyId = accessKeyId.trim(),
            secretAccessKey = secretAccessKey.trim()
        )
    }

    override suspend fun loadBackupS3Credentials(): BackupS3Credentials? = credentials

    override suspend fun hasBackupS3Credentials(): Boolean = credentials != null

    override suspend fun clearBackupS3Credentials() {
        credentials = null
    }
}

private class FakeRegionalCurrencyPreviewConverter(
    private val convertedAmount: Double
) : RegionalCurrencyPreviewConverter {
    var callCount: Int = 0
    var lastValue: Double = 0.0
    var lastFromCurrency: String = ""
    var lastToCurrency: String = ""

    override suspend fun convert(value: Double, fromCurrency: String, toCurrency: String): Double {
        callCount += 1
        lastValue = value
        lastFromCurrency = fromCurrency
        lastToCurrency = toCurrency
        return convertedAmount
    }
}
