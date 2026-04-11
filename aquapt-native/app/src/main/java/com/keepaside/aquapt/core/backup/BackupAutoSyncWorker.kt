package com.keepaside.aquapt.core.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
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
        val backupGateway: BackupCompatibilityGateway =
            KoinJavaComponent.get(BackupCompatibilityGateway::class.java)

        val settings = appSettingsStore.settings.value
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

        return try {
            backupGateway.exportCurrentStateJson(
                settings = settings,
                pretty = false
            )

            val now = Instant.now()
            appSettingsStore.setSettings(
                appSettingsStore.settings.value.copy(
                    backupLastSyncedAt = now.toString(),
                    backupLastAutoSyncDate = backupAutoSyncDateStamp(now = now),
                    backupLastError = null
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
