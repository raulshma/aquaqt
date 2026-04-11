package com.keepaside.aquapt.core.backup

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.keepaside.aquapt.core.model.AppSettings
import com.keepaside.aquapt.core.repository.AppSettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class BackupAutoSyncScheduler(
    appContext: Context,
    private val appSettingsStore: AppSettingsStore,
    private val externalScope: CoroutineScope? = null,
    private val workManager: WorkManager = WorkManager.getInstance(appContext)
) {

    private val schedulerScope: CoroutineScope =
        externalScope ?: CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var observerJob: Job? = null
    private var lastScheduledHour: Int? = null

    fun start() {
        if (observerJob?.isActive == true) return

        observerJob = schedulerScope.launch {
            appSettingsStore.settings.collect { settings ->
                syncBackupSchedule(settings)
            }
        }
    }

    private fun syncBackupSchedule(settings: AppSettings) {
        val targetHour = resolveBackupAutoSyncHour(settings)

        if (targetHour == null) {
            cancelBackupWork()
            lastScheduledHour = null
            return
        }

        if (targetHour == lastScheduledHour) {
            return
        }

        scheduleBackupAtHour(targetHour)
        lastScheduledHour = targetHour
    }

    private fun scheduleBackupAtHour(hour: Int) {
        val normalizedHour = hour.coerceIn(0, 23)
        val request = PeriodicWorkRequestBuilder<BackupAutoSyncWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(
                calculateNextBackupAutoSyncInitialDelayMillis(normalizedHour),
                TimeUnit.MILLISECONDS
            )
            .addTag(backupAutoSyncWorkerTag)
            .setInputData(
                workDataOf(
                    backupAutoSyncWorkerInputHourKey to normalizedHour
                )
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            backupAutoSyncWorkerUniqueName,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    private fun cancelBackupWork() {
        workManager.cancelAllWorkByTag(backupAutoSyncWorkerTag)
    }
}
