package com.keepaside.aquapt.feature.settings

import com.keepaside.aquapt.core.model.AppSettings
import com.keepaside.aquapt.core.model.AppThemePreference
import com.keepaside.aquapt.core.model.RegionalPreferencesMode
import com.keepaside.aquapt.core.repository.AppSettingsStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsPreferencesViewModelTest {

    @Test
    fun `save preferences persists normalized manual values`() = runTest {
        val fakeStore = FakePreferencesAppSettingsStore()
        val viewModel = SettingsPreferencesViewModel(fakeStore, this)

        try {
            advanceUntilIdle()

            viewModel.onThemePreferenceChanged(AppThemePreference.DARK)
            viewModel.onRegionalPreferencesModeChanged(RegionalPreferencesMode.MANUAL)
            viewModel.onNotificationsEnabledChanged(true)
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
        val viewModel = SettingsPreferencesViewModel(fakeStore, this)

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
    fun `auto regional mode clears manual overrides on save`() = runTest {
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
        val viewModel = SettingsPreferencesViewModel(fakeStore, this)

        try {
            advanceUntilIdle()

            viewModel.onRegionalPreferencesModeChanged(RegionalPreferencesMode.AUTO)
            viewModel.savePreferences()
            advanceUntilIdle()

            val saved = fakeStore.settings.value
            assertEquals(RegionalPreferencesMode.AUTO, saved.regionalPreferencesMode)
            assertNull(saved.defaultLocale)
            assertNull(saved.defaultTimezone)
            assertNull(saved.defaultCountryCode)
            assertNull(saved.defaultCountryName)
            assertNull(saved.defaultCurrency)
        } finally {
            viewModel.disposeForTests()
        }
    }

    @Test
    fun `reminder hour parser normalizes and validates values`() {
        assertEquals(listOf(8, 18, 21), parseSettingsReminderHoursInput("18, 8; 21 8"))
        assertEquals(emptyList<Int>(), parseSettingsReminderHoursInput("   "))
        assertEquals(null, parseSettingsReminderHoursInput("9, 99"))
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