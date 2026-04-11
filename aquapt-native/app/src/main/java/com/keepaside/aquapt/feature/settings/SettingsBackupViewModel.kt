package com.keepaside.aquapt.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.keepaside.aquapt.core.backup.AppStateJsonCompatibility
import com.keepaside.aquapt.core.backup.BackupCloudObject
import com.keepaside.aquapt.core.backup.BackupCloudSyncGateway
import com.keepaside.aquapt.core.backup.BackupCompatibilityGateway
import com.keepaside.aquapt.core.backup.BackupSnapshotSummary
import com.keepaside.aquapt.core.backup.buildBackupSnapshotSummary
import com.keepaside.aquapt.core.model.AppSettings
import com.keepaside.aquapt.core.repository.AppSettingsStore
import com.keepaside.aquapt.core.repository.BackupS3Credentials
import com.keepaside.aquapt.core.repository.BackupSecretsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val DEFAULT_STATUS =
    "Export your current Room state to RN-compatible JSON, or import a previous backup payload."

private const val cloudPrerequisiteMessage =
    "Cloud backup requires configured S3 destination and secure credentials in App preferences."

data class BackupCollectionDiff(
    val label: String,
    val currentCount: Int,
    val incomingCount: Int
) {
    val delta: Int
        get() = incomingCount - currentCount
}

data class CloudRestorePreviewUiState(
    val sourceObjectKey: String,
    val exportedAt: String,
    val collectionDiffs: List<BackupCollectionDiff>,
    val changedSettingLabels: List<String>
) {
    val changedCollectionCount: Int
        get() = collectionDiffs.count { diff -> diff.delta != 0 }
}

data class SettingsBackupUiState(
    val payload: String = "",
    val replaceExisting: Boolean = true,
    val isBusy: Boolean = false,
    val isRestorePreviewLoading: Boolean = false,
    val cloudBackups: List<BackupCloudObject> = emptyList(),
    val selectedCloudObjectKey: String = "",
    val restorePreview: CloudRestorePreviewUiState? = null,
    val statusMessage: String = DEFAULT_STATUS
)

class SettingsBackupViewModel(
    private val backupGateway: BackupCompatibilityGateway,
    private val appSettingsStore: AppSettingsStore? = null,
    private val backupSecretsStore: BackupSecretsStore? = null,
    private val backupCloudSyncGateway: BackupCloudSyncGateway? = null,
    private val externalScope: CoroutineScope? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsBackupUiState())
    val uiState: StateFlow<SettingsBackupUiState> = _uiState.asStateFlow()

    fun onPayloadChanged(value: String) {
        _uiState.update { it.copy(payload = value) }
    }

    fun onReplaceExistingChanged(value: Boolean) {
        _uiState.update { it.copy(replaceExisting = value) }
    }

    fun onSelectedCloudObjectChanged(objectKey: String) {
        _uiState.update {
            it.copy(
                selectedCloudObjectKey = objectKey.trim(),
                restorePreview = null
            )
        }
    }

    fun exportJson() {
        if (_uiState.value.isBusy) return

        launchWork {
            _uiState.update { it.copy(isBusy = true) }

            runCatching {
                backupGateway.exportCurrentStateJson(
                    settings = appSettingsStore?.settings?.value ?: AppSettings(),
                    pretty = true
                )
            }.onSuccess { exported ->
                _uiState.update {
                    it.copy(
                        payload = exported,
                        statusMessage = "Export completed. JSON payload loaded into the editor below."
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(statusMessage = error.message ?: "Export failed.")
                }
            }

            _uiState.update { it.copy(isBusy = false) }
        }
    }

    fun importJson() {
        val current = _uiState.value
        if (current.isBusy) return

        if (current.payload.isBlank()) {
            _uiState.update {
                it.copy(statusMessage = "Import payload is empty. Paste a backup JSON first.")
            }
            return
        }

        launchWork {
            _uiState.update { it.copy(isBusy = true) }

            runCatching {
                val result = backupGateway.importFromJson(
                    payload = _uiState.value.payload,
                    replaceExisting = _uiState.value.replaceExisting
                )
                appSettingsStore?.setSettings(result.snapshot.settings)
                result
            }.onSuccess { result ->
                val skippedSummary = if (result.skippedCounts.isEmpty()) {
                    "No skipped records."
                } else {
                    result.skippedCounts.entries.joinToString(
                        prefix = "Skipped -> ",
                        separator = ", "
                    ) { (kind, count) -> "$kind: $count" }
                }

                _uiState.update {
                    it.copy(statusMessage = "Import completed. $skippedSummary")
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(statusMessage = error.message ?: "Import failed.")
                }
            }

            _uiState.update { it.copy(isBusy = false) }
        }
    }

    fun syncToCloud() {
        val current = _uiState.value
        if (current.isBusy) return

        launchWork {
            _uiState.update { it.copy(isBusy = true) }

            runCatching {
                val prereq = resolveCloudRestorePrerequisites()
                val syncOutcome = prereq.cloudGateway.syncCurrentStateToCloud(
                    settings = prereq.settings,
                    masterKey = prereq.masterKey,
                    credentials = prereq.credentials
                )

                prereq.settingsStore.setSettings(
                    prereq.settingsStore.settings.value.copy(
                        backupLastSyncedAt = syncOutcome.uploadedAt,
                        backupLastError = null,
                        backupMasterKeySet = true,
                        backupS3CredentialsSet = true
                    )
                )

                val refreshedSettings = prereq.settingsStore.settings.value
                val cloudObjects = prereq.cloudGateway.listAvailableCloudBackups(
                    settings = refreshedSettings,
                    credentials = prereq.credentials
                )

                val selected = resolveSelectedCloudObjectKey(
                    objects = cloudObjects,
                    preferredObjectKey = refreshedSettings.backupS3ObjectKey,
                    currentSelectedObjectKey = _uiState.value.selectedCloudObjectKey
                )

                Triple(syncOutcome, cloudObjects, selected)
            }.onSuccess { (syncOutcome, cloudObjects, selectedObjectKey) ->
                val historySummary = buildString {
                    append("Cloud sync completed")
                    if (syncOutcome.versionedObjectKey != null) {
                        append(". Versioned backup: ${syncOutcome.versionedObjectKey}")
                    }
                    if (syncOutcome.deletedVersionedKeys.isNotEmpty()) {
                        append(". Cleaned ${syncOutcome.deletedVersionedKeys.size} old versioned backups")
                    }
                    append('.')
                }

                _uiState.update {
                    it.copy(
                        cloudBackups = cloudObjects,
                        selectedCloudObjectKey = selectedObjectKey,
                        statusMessage = historySummary
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(statusMessage = error.message ?: "Cloud sync failed.")
                }
            }

            _uiState.update { it.copy(isBusy = false) }
        }
    }

    fun loadCloudBackups() {
        val current = _uiState.value
        if (current.isBusy) return

        launchWork {
            _uiState.update { it.copy(isBusy = true) }

            runCatching {
                val prereq = resolveCloudListPrerequisites()
                val cloudObjects = prereq.cloudGateway.listAvailableCloudBackups(
                    settings = prereq.settings,
                    credentials = prereq.credentials
                )

                val selected = resolveSelectedCloudObjectKey(
                    objects = cloudObjects,
                    preferredObjectKey = prereq.settings.backupS3ObjectKey,
                    currentSelectedObjectKey = _uiState.value.selectedCloudObjectKey
                )

                cloudObjects to selected
            }.onSuccess { (cloudObjects, selectedObjectKey) ->
                val status = if (cloudObjects.isEmpty()) {
                    "No cloud backups found for the configured destination."
                } else {
                    "Loaded ${cloudObjects.size} cloud backup object(s)."
                }

                _uiState.update {
                    it.copy(
                        cloudBackups = cloudObjects,
                        selectedCloudObjectKey = selectedObjectKey,
                        restorePreview = null,
                        statusMessage = status
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(statusMessage = error.message ?: "Unable to load cloud backups.")
                }
            }

            _uiState.update { it.copy(isBusy = false) }
        }
    }

    fun loadSelectedCloudBackupPreview() {
        val current = _uiState.value
        if (current.isBusy) return

        val selectedObjectKey = current.selectedCloudObjectKey.trim()
        if (selectedObjectKey.isEmpty()) {
            _uiState.update {
                it.copy(statusMessage = "Select a cloud backup object first, then load its restore preview.")
            }
            return
        }

        launchWork {
            _uiState.update {
                it.copy(
                    isBusy = true,
                    isRestorePreviewLoading = true,
                    restorePreview = null
                )
            }

            runCatching {
                val prereq = resolveCloudRestorePrerequisites()
                val currentSettings = prereq.settingsStore.settings.value
                val remotePreview = prereq.cloudGateway.previewCloudRestoreObject(
                    settings = prereq.settings,
                    masterKey = prereq.masterKey,
                    credentials = prereq.credentials,
                    objectKey = selectedObjectKey
                )

                val currentSummary = exportCurrentSnapshotSummary(currentSettings)

                CloudRestorePreviewUiState(
                    sourceObjectKey = remotePreview.sourceObjectKey,
                    exportedAt = remotePreview.exportedAt,
                    collectionDiffs = buildCollectionDiffs(
                        current = currentSummary,
                        incoming = remotePreview.snapshotSummary
                    ),
                    changedSettingLabels = buildSettingDiffLabels(
                        current = currentSettings,
                        incoming = remotePreview.restoredSettings
                    )
                )
            }.onSuccess { preview ->
                _uiState.update {
                    it.copy(
                        restorePreview = preview,
                        statusMessage =
                            "Restore preview loaded for ${preview.sourceObjectKey} " +
                                "(${preview.changedCollectionCount} collection change(s), " +
                                "${preview.changedSettingLabels.size} app setting change(s))."
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(statusMessage = error.message ?: "Restore preview failed.")
                }
            }

            _uiState.update {
                it.copy(
                    isBusy = false,
                    isRestorePreviewLoading = false
                )
            }
        }
    }

    fun restoreSelectedCloudBackup() {
        val current = _uiState.value
        if (current.isBusy) return

        val selectedObjectKey = current.selectedCloudObjectKey.trim()
        if (selectedObjectKey.isEmpty()) {
            _uiState.update {
                it.copy(statusMessage = "Select a cloud backup object first, then restore.")
            }
            return
        }

        launchWork {
            _uiState.update { it.copy(isBusy = true) }

            runCatching {
                val prereq = resolveCloudRestorePrerequisites()
                val restoreOutcome = prereq.cloudGateway.restoreFromCloudObject(
                    settings = prereq.settings,
                    masterKey = prereq.masterKey,
                    credentials = prereq.credentials,
                    objectKey = selectedObjectKey,
                    replaceExisting = _uiState.value.replaceExisting
                )

                prereq.settingsStore.setSettings(
                    restoreOutcome.restoredSettings.copy(
                        backupLastRestoredAt = restoreOutcome.restoredAt,
                        backupLastError = null,
                        backupMasterKeySet = true,
                        backupS3CredentialsSet = true
                    )
                )

                restoreOutcome
            }.onSuccess { restoreOutcome ->
                val skippedSummary = if (restoreOutcome.skippedCounts.isEmpty()) {
                    "No skipped records."
                } else {
                    restoreOutcome.skippedCounts.entries.joinToString(
                        prefix = "Skipped -> ",
                        separator = ", "
                    ) { (kind, count) -> "$kind: $count" }
                }

                _uiState.update {
                    it.copy(
                        restorePreview = null,
                        statusMessage =
                            "Cloud restore completed from ${restoreOutcome.sourceObjectKey}. $skippedSummary"
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(statusMessage = error.message ?: "Cloud restore failed.")
                }
            }

            _uiState.update { it.copy(isBusy = false) }
        }
    }

    fun restoreLatestCloudBackup() {
        val current = _uiState.value
        if (current.isBusy) return

        launchWork {
            _uiState.update { it.copy(isBusy = true) }

            runCatching {
                val prereq = resolveCloudRestorePrerequisites()
                val restoreOutcome = prereq.cloudGateway.restoreLatestFromCloud(
                    settings = prereq.settings,
                    masterKey = prereq.masterKey,
                    credentials = prereq.credentials,
                    replaceExisting = _uiState.value.replaceExisting
                )

                prereq.settingsStore.setSettings(
                    restoreOutcome.restoredSettings.copy(
                        backupLastRestoredAt = restoreOutcome.restoredAt,
                        backupLastError = null,
                        backupMasterKeySet = true,
                        backupS3CredentialsSet = true
                    )
                )

                val refreshedSettings = prereq.settingsStore.settings.value
                val cloudObjects = prereq.cloudGateway.listAvailableCloudBackups(
                    settings = refreshedSettings,
                    credentials = prereq.credentials
                )

                val selected = resolveSelectedCloudObjectKey(
                    objects = cloudObjects,
                    preferredObjectKey = restoreOutcome.sourceObjectKey,
                    currentSelectedObjectKey = _uiState.value.selectedCloudObjectKey
                )

                Triple(restoreOutcome, cloudObjects, selected)
            }.onSuccess { (restoreOutcome, cloudObjects, selectedObjectKey) ->
                val skippedSummary = if (restoreOutcome.skippedCounts.isEmpty()) {
                    "No skipped records."
                } else {
                    restoreOutcome.skippedCounts.entries.joinToString(
                        prefix = "Skipped -> ",
                        separator = ", "
                    ) { (kind, count) -> "$kind: $count" }
                }

                _uiState.update {
                    it.copy(
                        cloudBackups = cloudObjects,
                        selectedCloudObjectKey = selectedObjectKey,
                        restorePreview = null,
                        statusMessage =
                            "Latest cloud restore completed from ${restoreOutcome.sourceObjectKey}. $skippedSummary"
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(statusMessage = error.message ?: "Latest cloud restore failed.")
                }
            }

            _uiState.update { it.copy(isBusy = false) }
        }
    }

    fun deleteSelectedCloudBackupObject() {
        val current = _uiState.value
        if (current.isBusy) return

        val selectedObjectKey = current.selectedCloudObjectKey.trim()
        if (selectedObjectKey.isEmpty()) {
            _uiState.update {
                it.copy(statusMessage = "Select a cloud backup object first, then delete it.")
            }
            return
        }

        launchWork {
            _uiState.update { it.copy(isBusy = true) }

            runCatching {
                val prereq = resolveCloudListPrerequisites()
                val deleteOutcome = prereq.cloudGateway.deleteCloudBackupObject(
                    settings = prereq.settings,
                    credentials = prereq.credentials,
                    objectKey = selectedObjectKey
                )

                val refreshedSettings = prereq.settingsStore.settings.value
                val cloudObjects = prereq.cloudGateway.listAvailableCloudBackups(
                    settings = refreshedSettings,
                    credentials = prereq.credentials
                )

                val selected = resolveSelectedCloudObjectKey(
                    objects = cloudObjects,
                    preferredObjectKey = refreshedSettings.backupS3ObjectKey,
                    currentSelectedObjectKey = if (selectedObjectKey == deleteOutcome.deletedObjectKey) {
                        ""
                    } else {
                        _uiState.value.selectedCloudObjectKey
                    }
                )

                Triple(deleteOutcome, cloudObjects, selected)
            }.onSuccess { (deleteOutcome, cloudObjects, selectedObjectKeyAfterDelete) ->
                val statusMessage = buildString {
                    append("Deleted cloud object ${deleteOutcome.deletedObjectKey}.")
                    if (deleteOutcome.wasLatestObject) {
                        append(" The latest backup pointer was removed; run Sync to cloud to recreate it.")
                    }
                }

                _uiState.update {
                    it.copy(
                        cloudBackups = cloudObjects,
                        selectedCloudObjectKey = selectedObjectKeyAfterDelete,
                        restorePreview = null,
                        statusMessage = statusMessage
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(statusMessage = error.message ?: "Cloud delete failed.")
                }
            }

            _uiState.update { it.copy(isBusy = false) }
        }
    }

    fun pruneCloudBackupHistory() {
        val current = _uiState.value
        if (current.isBusy) return

        launchWork {
            _uiState.update { it.copy(isBusy = true) }

            runCatching {
                val prereq = resolveCloudListPrerequisites()
                val pruneOutcome = prereq.cloudGateway.pruneCloudBackupHistory(
                    settings = prereq.settings,
                    credentials = prereq.credentials
                )

                val refreshedSettings = prereq.settingsStore.settings.value
                val cloudObjects = prereq.cloudGateway.listAvailableCloudBackups(
                    settings = refreshedSettings,
                    credentials = prereq.credentials
                )

                val selected = resolveSelectedCloudObjectKey(
                    objects = cloudObjects,
                    preferredObjectKey = refreshedSettings.backupS3ObjectKey,
                    currentSelectedObjectKey = _uiState.value.selectedCloudObjectKey
                )

                Triple(pruneOutcome, cloudObjects, selected)
            }.onSuccess { (pruneOutcome, cloudObjects, selectedObjectKey) ->
                val statusMessage = if (pruneOutcome.deletedKeys.isEmpty()) {
                    "Prune complete. No old versioned backups matched the retention policy."
                } else {
                    "Prune complete. Deleted ${pruneOutcome.deletedKeys.size} old versioned backup(s)."
                }

                _uiState.update {
                    it.copy(
                        cloudBackups = cloudObjects,
                        selectedCloudObjectKey = selectedObjectKey,
                        restorePreview = null,
                        statusMessage = statusMessage
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(statusMessage = error.message ?: "Cloud prune failed.")
                }
            }

            _uiState.update { it.copy(isBusy = false) }
        }
    }

    private fun resolveSelectedCloudObjectKey(
        objects: List<BackupCloudObject>,
        preferredObjectKey: String?,
        currentSelectedObjectKey: String
    ): String {
        if (objects.isEmpty()) return ""

        val normalizedCurrent = currentSelectedObjectKey.trim()
        if (objects.any { it.objectKey == normalizedCurrent }) {
            return normalizedCurrent
        }

        val normalizedPreferred = preferredObjectKey
            ?.trim()
            .orEmpty()
        if (objects.any { it.objectKey == normalizedPreferred }) {
            return normalizedPreferred
        }

        return objects.firstOrNull { it.isLatestObject }?.objectKey
            ?: objects.first().objectKey
    }

    private suspend fun resolveCloudListPrerequisites(): CloudListPrerequisites {
        val settingsStore = appSettingsStore
            ?: throw IllegalStateException(cloudPrerequisiteMessage)
        val secretsStore = backupSecretsStore
            ?: throw IllegalStateException(cloudPrerequisiteMessage)
        val cloudGateway = backupCloudSyncGateway
            ?: throw IllegalStateException(cloudPrerequisiteMessage)

        val settings = settingsStore.settings.value
        val credentials = secretsStore.loadBackupS3Credentials()
            ?: throw IllegalStateException(
                "Cloud backup requires stored S3 credentials. Configure them in App preferences."
            )

        return CloudListPrerequisites(
            settingsStore = settingsStore,
            settings = settings,
            credentials = credentials,
            cloudGateway = cloudGateway
        )
    }

    private suspend fun resolveCloudRestorePrerequisites(): CloudRestorePrerequisites {
        val listPrereq = resolveCloudListPrerequisites()
        val masterKey = backupSecretsStore
            ?.loadBackupMasterKey()
            .orEmpty()
            .trim()

        if (masterKey.isEmpty()) {
            throw IllegalStateException(
                "Cloud restore/sync requires a stored backup master key. Configure it in App preferences."
            )
        }

        return CloudRestorePrerequisites(
            settingsStore = listPrereq.settingsStore,
            settings = listPrereq.settings,
            credentials = listPrereq.credentials,
            cloudGateway = listPrereq.cloudGateway,
            masterKey = masterKey
        )
    }

    private suspend fun exportCurrentSnapshotSummary(settings: AppSettings): BackupSnapshotSummary {
        val payload = backupGateway.exportCurrentStateJson(
            settings = settings,
            pretty = false
        )
        val snapshot = AppStateJsonCompatibility.decode(payload)
        return buildBackupSnapshotSummary(snapshot)
    }

    private fun buildCollectionDiffs(
        current: BackupSnapshotSummary,
        incoming: BackupSnapshotSummary
    ): List<BackupCollectionDiff> {
        val currentByLabel = current.toNamedCounts().toMap()
        return incoming.toNamedCounts().map { (label, incomingCount) ->
            BackupCollectionDiff(
                label = label,
                currentCount = currentByLabel[label] ?: 0,
                incomingCount = incomingCount
            )
        }
    }

    private fun buildSettingDiffLabels(
        current: AppSettings,
        incoming: AppSettings
    ): List<String> {
        val labels = mutableListOf<String>()

        fun addIfChanged(label: String, changed: Boolean) {
            if (changed) {
                labels += label
            }
        }

        addIfChanged("Theme preference", current.themePreference != incoming.themePreference)
        addIfChanged("Regional mode", current.regionalPreferencesMode != incoming.regionalPreferencesMode)
        addIfChanged("Notifications enabled", current.notificationsEnabled != incoming.notificationsEnabled)
        addIfChanged("Reminder hours", current.reminderHours != incoming.reminderHours)
        addIfChanged("Assistant model", current.aiModel != incoming.aiModel)
        addIfChanged(
            "Assistant memory model",
            normalizeOptional(current.assistantMemoryModel) != normalizeOptional(incoming.assistantMemoryModel)
        )
        addIfChanged("Assistant memory enabled", current.assistantMemoryEnabled != incoming.assistantMemoryEnabled)
        addIfChanged(
            "OpenRouter API key",
            normalizeOptional(current.openRouterApiKey) != normalizeOptional(incoming.openRouterApiKey)
        )
        addIfChanged("Backup auto-sync", current.backupSyncEnabled != incoming.backupSyncEnabled)
        addIfChanged("Backup sync hour", current.backupSyncHour != incoming.backupSyncHour)
        addIfChanged(
            "Backup S3 endpoint",
            normalizeOptional(current.backupS3Endpoint) != normalizeOptional(incoming.backupS3Endpoint)
        )
        addIfChanged(
            "Backup S3 region",
            normalizeOptional(current.backupS3Region) != normalizeOptional(incoming.backupS3Region)
        )
        addIfChanged(
            "Backup S3 bucket",
            normalizeOptional(current.backupS3Bucket) != normalizeOptional(incoming.backupS3Bucket)
        )
        addIfChanged(
            "Backup object key",
            normalizeOptional(current.backupS3ObjectKey) != normalizeOptional(incoming.backupS3ObjectKey)
        )
        addIfChanged("Backup path-style mode", current.backupS3ForcePathStyle != incoming.backupS3ForcePathStyle)
        addIfChanged("Versioned backups", current.backupUseVersionedKeys != incoming.backupUseVersionedKeys)
        addIfChanged("Backup retention days", current.backupRetentionDays != incoming.backupRetentionDays)
        addIfChanged("Default locale", normalizeOptional(current.defaultLocale) != normalizeOptional(incoming.defaultLocale))
        addIfChanged(
            "Default timezone",
            normalizeOptional(current.defaultTimezone) != normalizeOptional(incoming.defaultTimezone)
        )
        addIfChanged(
            "Default country code",
            normalizeOptional(current.defaultCountryCode) != normalizeOptional(incoming.defaultCountryCode)
        )
        addIfChanged(
            "Default country name",
            normalizeOptional(current.defaultCountryName) != normalizeOptional(incoming.defaultCountryName)
        )
        addIfChanged(
            "Default currency",
            normalizeOptional(current.defaultCurrency) != normalizeOptional(incoming.defaultCurrency)
        )

        return labels
    }

    private fun normalizeOptional(value: String?): String? = value
        ?.trim()
        ?.takeIf { text -> text.isNotEmpty() }

    private fun launchWork(block: suspend () -> Unit) {
        (externalScope ?: viewModelScope).launch {
            block()
        }
    }

    companion object {
        fun factory(
            backupGateway: BackupCompatibilityGateway,
            appSettingsStore: AppSettingsStore? = null,
            backupSecretsStore: BackupSecretsStore? = null,
            backupCloudSyncGateway: BackupCloudSyncGateway? = null
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(SettingsBackupViewModel::class.java)) {
                        return SettingsBackupViewModel(
                            backupGateway = backupGateway,
                            appSettingsStore = appSettingsStore,
                            backupSecretsStore = backupSecretsStore,
                            backupCloudSyncGateway = backupCloudSyncGateway
                        ) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
    }

    private data class CloudListPrerequisites(
        val settingsStore: AppSettingsStore,
        val settings: AppSettings,
        val credentials: BackupS3Credentials,
        val cloudGateway: BackupCloudSyncGateway
    )

    private data class CloudRestorePrerequisites(
        val settingsStore: AppSettingsStore,
        val settings: AppSettings,
        val credentials: BackupS3Credentials,
        val cloudGateway: BackupCloudSyncGateway,
        val masterKey: String
    )
}
