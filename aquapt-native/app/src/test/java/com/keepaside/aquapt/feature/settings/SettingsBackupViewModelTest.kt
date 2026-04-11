package com.keepaside.aquapt.feature.settings

import com.keepaside.aquapt.core.backup.AppStateImportResult
import com.keepaside.aquapt.core.backup.BackupCompatibilityGateway
import com.keepaside.aquapt.core.backup.PersistedAppStateSnapshot
import com.keepaside.aquapt.core.model.AppSettings
import com.keepaside.aquapt.core.model.AppThemePreference
import com.keepaside.aquapt.core.repository.AppSettingsStore
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
class SettingsBackupViewModelTest {

    @Test
    fun `export updates payload and status on success`() = runTest {
        val fake = FakeBackupGateway(
            exportJson = "{\"aquariums\":[]}",
            exportError = null
        )
        val viewModel = SettingsBackupViewModel(
            backupGateway = fake,
            externalScope = this
        )

        viewModel.exportJson()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("{\"aquariums\":[]}", state.payload)
        assertEquals(
            "Export completed. JSON payload loaded into the editor below.",
            state.statusMessage
        )
        assertFalse(state.isBusy)
        assertEquals(1, fake.exportCalls)
    }

    @Test
    fun `import reports empty payload without gateway call`() = runTest {
        val fake = FakeBackupGateway()
        val viewModel = SettingsBackupViewModel(
            backupGateway = fake,
            externalScope = this
        )

        viewModel.importJson()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Import payload is empty. Paste a backup JSON first.", state.statusMessage)
        assertEquals(0, fake.importCalls)
    }

    @Test
    fun `import success includes skipped summary`() = runTest {
        val fake = FakeBackupGateway(
            importResult = AppStateImportResult(
                snapshot = PersistedAppStateSnapshot(),
                skippedCounts = mapOf("taskExecutions" to 2, "timeline" to 1)
            )
        )
        val viewModel = SettingsBackupViewModel(
            backupGateway = fake,
            externalScope = this
        )
        viewModel.onPayloadChanged("{\"aquariums\":[]}")
        viewModel.onReplaceExistingChanged(false)

        viewModel.importJson()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.statusMessage.startsWith("Import completed. Skipped ->"))
        assertTrue(state.statusMessage.contains("taskExecutions: 2"))
        assertTrue(state.statusMessage.contains("timeline: 1"))
        assertEquals(1, fake.importCalls)
        assertFalse(fake.lastReplaceExisting)
    }

    @Test
    fun `gateway errors are surfaced in status messages`() = runTest {
        val fake = FakeBackupGateway(
            exportError = IllegalStateException("No data"),
            importError = IllegalArgumentException("Invalid payload")
        )
        val viewModel = SettingsBackupViewModel(
            backupGateway = fake,
            externalScope = this
        )

        viewModel.exportJson()
        advanceUntilIdle()
        assertEquals("No data", viewModel.uiState.value.statusMessage)

        viewModel.onPayloadChanged("{bad}")
        viewModel.importJson()
        advanceUntilIdle()
        assertEquals("Invalid payload", viewModel.uiState.value.statusMessage)
    }

    @Test
    fun `export uses persisted settings and import restores snapshot settings`() = runTest {
        val importedSettings = AppSettings(
            themePreference = AppThemePreference.DARK,
            defaultCurrency = "USD"
        )
        val fakeGateway = FakeBackupGateway(
            importResult = AppStateImportResult(
                snapshot = PersistedAppStateSnapshot(settings = importedSettings)
            )
        )
        val fakeStore = FakeAppSettingsStore(
            AppSettings(themePreference = AppThemePreference.LIGHT)
        )
        val viewModel = SettingsBackupViewModel(
            backupGateway = fakeGateway,
            appSettingsStore = fakeStore,
            externalScope = this
        )

        viewModel.exportJson()
        advanceUntilIdle()
        assertEquals(AppThemePreference.LIGHT, fakeGateway.lastExportSettings?.themePreference)

        viewModel.onPayloadChanged("{\"aquariums\":[]}")
        viewModel.importJson()
        advanceUntilIdle()

        assertEquals(AppThemePreference.DARK, fakeStore.settings.value.themePreference)
        assertEquals("USD", fakeStore.settings.value.defaultCurrency)
    }
}

private class FakeBackupGateway(
    private val exportJson: String = "{}",
    private val importResult: AppStateImportResult = AppStateImportResult(PersistedAppStateSnapshot()),
    private val exportError: Throwable? = null,
    private val importError: Throwable? = null
) : BackupCompatibilityGateway {
    var exportCalls: Int = 0
    var importCalls: Int = 0
    var lastReplaceExisting: Boolean = true
    var lastExportSettings: AppSettings? = null

    override suspend fun exportCurrentStateJson(settings: com.keepaside.aquapt.core.model.AppSettings, pretty: Boolean): String {
        exportCalls += 1
        lastExportSettings = settings
        exportError?.let { throw it }
        return exportJson
    }

    override suspend fun importFromJson(payload: String, replaceExisting: Boolean): AppStateImportResult {
        importCalls += 1
        lastReplaceExisting = replaceExisting
        importError?.let { throw it }
        return importResult
    }
}

private class FakeAppSettingsStore(
    initial: AppSettings = AppSettings()
) : AppSettingsStore {
    private val flow = MutableStateFlow(initial)

    override val settings: StateFlow<AppSettings> = flow.asStateFlow()

    override suspend fun setSettings(settings: AppSettings) {
        flow.value = settings
    }
}
