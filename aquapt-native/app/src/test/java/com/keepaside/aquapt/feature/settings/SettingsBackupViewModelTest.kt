package com.keepaside.aquapt.feature.settings

import com.keepaside.aquapt.core.backup.AppStateImportResult
import com.keepaside.aquapt.core.backup.BackupCloudObject
import com.keepaside.aquapt.core.backup.BackupCloudSyncGateway
import com.keepaside.aquapt.core.backup.BackupCompatibilityGateway
import com.keepaside.aquapt.core.backup.BackupRestoreOutcome
import com.keepaside.aquapt.core.backup.BackupSyncOutcome
import com.keepaside.aquapt.core.backup.PersistedAppStateSnapshot
import com.keepaside.aquapt.core.model.AppSettings
import com.keepaside.aquapt.core.model.AppThemePreference
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

    @Test
    fun `cloud backup list loads and selects latest object`() = runTest {
        val fakeGateway = FakeBackupGateway()
        val fakeCloudGateway = FakeBackupCloudGateway(
            cloudObjects = listOf(
                BackupCloudObject(
                    objectKey = "aquapt/backups/latest.enc.json",
                    lastModified = "2026-04-11T03:00:00Z",
                    isLatestObject = true
                ),
                BackupCloudObject(
                    objectKey = "aquapt/backups/history/2026-04-10.enc.json",
                    lastModified = "2026-04-10T03:00:00Z"
                )
            )
        )
        val fakeStore = FakeAppSettingsStore(
            AppSettings(
                backupS3Endpoint = "https://s3.example.com",
                backupS3Bucket = "aquapt-backups",
                backupS3ObjectKey = "aquapt/backups/latest.enc.json"
            )
        )
        val fakeSecretsStore = FakeBackupSecretsStoreForBackupViewModel(
            masterKey = "valid-master-key-123",
            credentials = BackupS3Credentials("AKIA123", "secret")
        )
        val viewModel = SettingsBackupViewModel(
            backupGateway = fakeGateway,
            appSettingsStore = fakeStore,
            backupSecretsStore = fakeSecretsStore,
            backupCloudSyncGateway = fakeCloudGateway,
            externalScope = this
        )

        viewModel.loadCloudBackups()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.cloudBackups.size)
        assertEquals("aquapt/backups/latest.enc.json", state.selectedCloudObjectKey)
        assertEquals(1, fakeCloudGateway.listCalls)
    }

    @Test
    fun `manual cloud sync updates settings metadata and status`() = runTest {
        val fakeGateway = FakeBackupGateway()
        val fakeCloudGateway = FakeBackupCloudGateway(
            cloudObjects = listOf(
                BackupCloudObject(
                    objectKey = "aquapt/backups/latest.enc.json",
                    lastModified = "2026-04-11T03:00:00Z",
                    isLatestObject = true
                )
            ),
            syncOutcome = BackupSyncOutcome(
                uploadedAt = "2026-04-11T03:00:00Z",
                objectUrl = "https://s3.example.com/aquapt/backups/latest.enc.json",
                payloadBytes = 1234,
                versionedObjectKey = "aquapt/backups/history/2026-04-11.enc.json"
            )
        )
        val fakeStore = FakeAppSettingsStore(
            AppSettings(
                backupS3Endpoint = "https://s3.example.com",
                backupS3Bucket = "aquapt-backups",
                backupS3ObjectKey = "aquapt/backups/latest.enc.json"
            )
        )
        val fakeSecretsStore = FakeBackupSecretsStoreForBackupViewModel(
            masterKey = "valid-master-key-123",
            credentials = BackupS3Credentials("AKIA123", "secret")
        )
        val viewModel = SettingsBackupViewModel(
            backupGateway = fakeGateway,
            appSettingsStore = fakeStore,
            backupSecretsStore = fakeSecretsStore,
            backupCloudSyncGateway = fakeCloudGateway,
            externalScope = this
        )

        viewModel.syncToCloud()
        advanceUntilIdle()

        assertEquals(1, fakeCloudGateway.syncCalls)
        assertEquals("2026-04-11T03:00:00Z", fakeStore.settings.value.backupLastSyncedAt)
        assertTrue(fakeStore.settings.value.backupMasterKeySet)
        assertTrue(fakeStore.settings.value.backupS3CredentialsSet)
        assertTrue(viewModel.uiState.value.statusMessage.contains("Cloud sync completed"))
    }

    @Test
    fun `restore selected cloud backup applies restored settings`() = runTest {
        val fakeGateway = FakeBackupGateway()
        val fakeCloudGateway = FakeBackupCloudGateway(
            restoreOutcome = BackupRestoreOutcome(
                restoredAt = "2026-04-11T04:00:00Z",
                sourceObjectKey = "aquapt/backups/history/2026-04-10.enc.json",
                exportedAt = "2026-04-10T03:00:00Z",
                restoredSettings = AppSettings(
                    themePreference = AppThemePreference.DARK,
                    defaultCurrency = "USD"
                ),
                skippedCounts = mapOf("timeline" to 2)
            )
        )
        val fakeStore = FakeAppSettingsStore(
            AppSettings(
                backupS3Endpoint = "https://s3.example.com",
                backupS3Bucket = "aquapt-backups",
                backupS3ObjectKey = "aquapt/backups/latest.enc.json"
            )
        )
        val fakeSecretsStore = FakeBackupSecretsStoreForBackupViewModel(
            masterKey = "valid-master-key-123",
            credentials = BackupS3Credentials("AKIA123", "secret")
        )
        val viewModel = SettingsBackupViewModel(
            backupGateway = fakeGateway,
            appSettingsStore = fakeStore,
            backupSecretsStore = fakeSecretsStore,
            backupCloudSyncGateway = fakeCloudGateway,
            externalScope = this
        )

        viewModel.onSelectedCloudObjectChanged("aquapt/backups/history/2026-04-10.enc.json")
        viewModel.restoreSelectedCloudBackup()
        advanceUntilIdle()

        assertEquals(1, fakeCloudGateway.restoreObjectCalls)
        assertEquals("aquapt/backups/history/2026-04-10.enc.json", fakeCloudGateway.lastRestoreObjectKey)
        assertEquals(AppThemePreference.DARK, fakeStore.settings.value.themePreference)
        assertEquals("USD", fakeStore.settings.value.defaultCurrency)
        assertEquals("2026-04-11T04:00:00Z", fakeStore.settings.value.backupLastRestoredAt)
        assertTrue(viewModel.uiState.value.statusMessage.contains("Cloud restore completed"))
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

private class FakeBackupSecretsStoreForBackupViewModel(
    private var masterKey: String = "",
    private var credentials: BackupS3Credentials? = null
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
        credentials = BackupS3Credentials(accessKeyId.trim(), secretAccessKey.trim())
    }

    override suspend fun loadBackupS3Credentials(): BackupS3Credentials? = credentials

    override suspend fun hasBackupS3Credentials(): Boolean = credentials != null

    override suspend fun clearBackupS3Credentials() {
        credentials = null
    }
}

private class FakeBackupCloudGateway(
    private val cloudObjects: List<BackupCloudObject> = emptyList(),
    private val syncOutcome: BackupSyncOutcome = BackupSyncOutcome(
        uploadedAt = "2026-04-11T03:00:00Z",
        objectUrl = "https://s3.example.com/aquapt/backups/latest.enc.json",
        payloadBytes = 123
    ),
    private val restoreOutcome: BackupRestoreOutcome = BackupRestoreOutcome(
        restoredAt = "2026-04-11T04:00:00Z",
        sourceObjectKey = "aquapt/backups/latest.enc.json",
        exportedAt = "2026-04-11T03:00:00Z",
        restoredSettings = AppSettings()
    )
) : BackupCloudSyncGateway {

    var syncCalls: Int = 0
    var listCalls: Int = 0
    var restoreObjectCalls: Int = 0
    var restoreLatestCalls: Int = 0
    var lastRestoreObjectKey: String? = null

    override suspend fun syncCurrentStateToCloud(
        settings: AppSettings,
        masterKey: String,
        credentials: BackupS3Credentials
    ): BackupSyncOutcome {
        syncCalls += 1
        return syncOutcome
    }

    override suspend fun listAvailableCloudBackups(
        settings: AppSettings,
        credentials: BackupS3Credentials
    ): List<BackupCloudObject> {
        listCalls += 1
        return cloudObjects
    }

    override suspend fun restoreFromCloudObject(
        settings: AppSettings,
        masterKey: String,
        credentials: BackupS3Credentials,
        objectKey: String,
        replaceExisting: Boolean
    ): BackupRestoreOutcome {
        restoreObjectCalls += 1
        lastRestoreObjectKey = objectKey
        return restoreOutcome
    }

    override suspend fun restoreLatestFromCloud(
        settings: AppSettings,
        masterKey: String,
        credentials: BackupS3Credentials,
        replaceExisting: Boolean
    ): BackupRestoreOutcome {
        restoreLatestCalls += 1
        return restoreOutcome
    }
}
