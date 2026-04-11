package com.keepaside.aquapt.core.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.keepaside.aquapt.core.repository.BackupSecretsStore
import com.keepaside.aquapt.core.repository.AppSettingsStore
import kotlinx.coroutines.CancellationException
import org.koin.java.KoinJavaComponent
import java.time.Instant

class BackupAutoSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val requestedHour = inputData.getInt(backupAutoSyncWorkerInputHourKey, -1)
            .takeIf { hour -> hour in 0..23 }

        val appSettingsStore: AppSettingsStore = KoinJavaComponent.get(AppSettingsStore::class.java)
        val backupSecretsStore: BackupSecretsStore =
            KoinJavaComponent.get(BackupSecretsStore::class.java)
        val backupCloudSyncService: BackupCloudSyncService =
            KoinJavaComponent.get(BackupCloudSyncService::class.java)

        var settings = appSettingsStore.settings.value

        val hasMasterKey = backupSecretsStore.hasBackupMasterKey()
        val hasS3Credentials = backupSecretsStore.hasBackupS3Credentials()
        if (
            settings.backupMasterKeySet != hasMasterKey ||
            settings.backupS3CredentialsSet != hasS3Credentials
        ) {
            appSettingsStore.setSettings(
                settings.copy(
                    backupMasterKeySet = hasMasterKey,
                    backupS3CredentialsSet = hasS3Credentials
                )
            )
            settings = appSettingsStore.settings.value
        }

        val targetHour = resolveBackupAutoSyncHour(settings) ?: return Result.success()

        if (requestedHour != null && requestedHour != targetHour) {
            return Result.success()
        }

        if (!canRunBackupAutoSyncNow(targetHour)) {
            return Result.success()
        }

        if (!shouldRunBackupAutoSyncToday(settings.backupLastAutoSyncDate)) {
            return Result.success()
        }

        val prerequisiteError = resolveBackupAutoSyncPrerequisiteError(settings)
        if (prerequisiteError != null) {
            setBackupErrorIfChanged(appSettingsStore, prerequisiteError)
            return Result.success()
        }

        val masterKey = backupSecretsStore.loadBackupMasterKey()
        val s3Credentials = backupSecretsStore.loadBackupS3Credentials()
        if (masterKey.isBlank() || s3Credentials == null) {
            appSettingsStore.setSettings(
                appSettingsStore.settings.value.copy(
                    backupMasterKeySet = masterKey.isNotBlank(),
                    backupS3CredentialsSet = s3Credentials != null,
                    backupLastError =
                        "Auto backup sync skipped: backup key or S3 credentials are not configured."
                )
            )
            return Result.success()
        }

        return try {
            val syncOutcome = backupCloudSyncService.syncCurrentStateToCloud(
                settings = settings,
                masterKey = masterKey,
                credentials = s3Credentials
            )

            val now = Instant.now()
            appSettingsStore.setSettings(
                appSettingsStore.settings.value.copy(
                    backupLastSyncedAt = syncOutcome.uploadedAt,
                    backupLastAutoSyncDate = backupAutoSyncDateStamp(now = now),
                    backupLastError = null,
                    backupMasterKeySet = true,
                    backupS3CredentialsSet = true
                )
            )

            Result.success()
        } catch (error: Throwable) {
            if (error is CancellationException) {
                throw error
            }

            setBackupErrorIfChanged(
                appSettingsStore = appSettingsStore,
                message = error.message ?: "Auto backup sync failed."
            )
            Result.success()
        }
    }

    private suspend fun setBackupErrorIfChanged(
        appSettingsStore: AppSettingsStore,
        message: String
    ) {
        val current = appSettingsStore.settings.value
        if (current.backupLastError == message) return

        appSettingsStore.setSettings(
            current.copy(
                backupLastError = message
            )
        )
    }
}
