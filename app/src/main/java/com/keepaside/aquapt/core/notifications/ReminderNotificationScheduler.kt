package com.keepaside.aquapt.core.notifications

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.keepaside.aquapt.core.model.AppSettings
import com.keepaside.aquapt.core.model.ReminderGroup
import com.keepaside.aquapt.core.model.TaskExecution
import com.keepaside.aquapt.core.model.TaskTemplate
import com.keepaside.aquapt.core.repository.AppSettingsStore
import com.keepaside.aquapt.core.repository.ReminderGroupRepository
import com.keepaside.aquapt.core.repository.TaskExecutionRepository
import com.keepaside.aquapt.core.repository.TaskTemplateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class ReminderNotificationScheduler(
    appContext: Context,
    private val appSettingsStore: AppSettingsStore,
    private val taskTemplateRepository: TaskTemplateRepository,
    private val taskExecutionRepository: TaskExecutionRepository,
    private val reminderGroupRepository: ReminderGroupRepository,
    private val externalScope: CoroutineScope? = null,
    private val workManager: WorkManager = WorkManager.getInstance(appContext)
) {

    private val schedulerScope: CoroutineScope =
        externalScope ?: CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var observerJob: Job? = null
    private var lastScheduledHours: Set<Int>? = null

    fun start() {
        if (observerJob?.isActive == true) return

        observerJob = schedulerScope.launch {
            combine(
                appSettingsStore.settings,
                taskTemplateRepository.getAll(),
                taskExecutionRepository.getAll(),
                reminderGroupRepository.getAll()
            ) { settings, taskTemplates, taskExecutions, reminderGroups ->
                ReminderScheduleSnapshot(
                    settings = settings,
                    taskTemplates = taskTemplates,
                    taskExecutions = taskExecutions,
                    reminderGroups = reminderGroups
                )
            }.collect { snapshot ->
                syncReminderSchedule(snapshot)
            }
        }
    }

    private fun syncReminderSchedule(snapshot: ReminderScheduleSnapshot) {
        if (!snapshot.settings.notificationsEnabled) {
            cancelAllReminderWork()
            lastScheduledHours = emptySet()
            return
        }

        val dueTasksByHour = collectNormalizedDueTasksByHour(
            settings = snapshot.settings,
            taskTemplates = snapshot.taskTemplates,
            taskExecutions = snapshot.taskExecutions,
            reminderGroups = snapshot.reminderGroups
        )
        val targetHours = dueTasksByHour.keys

        if (lastScheduledHours == null) {
            cancelAllReminderWork()
        }

        if (targetHours == lastScheduledHours) {
            return
        }

        val previousHours = lastScheduledHours.orEmpty()
        val removedHours = previousHours - targetHours
        removedHours.forEach { hour ->
            workManager.cancelUniqueWork(reminderWorkerUniqueName(hour))
        }

        targetHours.forEach { hour ->
            scheduleReminderForHour(hour)
        }

        lastScheduledHours = targetHours
    }

    private fun scheduleReminderForHour(hour: Int) {
        val normalizedHour = hour.coerceIn(0, 23)
        val request = PeriodicWorkRequestBuilder<ReminderNotificationWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(
                calculateNextReminderInitialDelayMillis(normalizedHour),
                TimeUnit.MILLISECONDS
            )
            .addTag(reminderWorkerTag)
            .setInputData(
                workDataOf(
                    reminderWorkerInputHourKey to normalizedHour
                )
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            reminderWorkerUniqueName(normalizedHour),
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    private fun cancelAllReminderWork() {
        workManager.cancelAllWorkByTag(reminderWorkerTag)
    }

    private data class ReminderScheduleSnapshot(
        val settings: AppSettings,
        val taskTemplates: List<TaskTemplate>,
        val taskExecutions: List<TaskExecution>,
        val reminderGroups: List<ReminderGroup>
    )
}
