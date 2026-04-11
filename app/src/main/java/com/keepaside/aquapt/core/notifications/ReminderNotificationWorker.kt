package com.keepaside.aquapt.core.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.keepaside.aquapt.MainActivity
import com.keepaside.aquapt.core.repository.AppSettingsStore
import com.keepaside.aquapt.core.repository.ReminderGroupRepository
import com.keepaside.aquapt.core.repository.TaskExecutionRepository
import com.keepaside.aquapt.core.repository.TaskTemplateRepository
import kotlinx.coroutines.flow.first
import org.koin.java.KoinJavaComponent

class ReminderNotificationWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val targetHour = inputData.getInt(reminderWorkerInputHourKey, -1)
            .takeIf { it in 0..23 }
            ?: return Result.success()

        if (!canPostNotifications(applicationContext)) {
            return Result.success()
        }

        val appSettingsStore: AppSettingsStore = KoinJavaComponent.get(AppSettingsStore::class.java)
        val taskTemplateRepository: TaskTemplateRepository =
            KoinJavaComponent.get(TaskTemplateRepository::class.java)
        val taskExecutionRepository: TaskExecutionRepository =
            KoinJavaComponent.get(TaskExecutionRepository::class.java)
        val reminderGroupRepository: ReminderGroupRepository =
            KoinJavaComponent.get(ReminderGroupRepository::class.java)

        val settings = appSettingsStore.settings.value
        if (!settings.notificationsEnabled) {
            return Result.success()
        }

        val dueTasksByHour = collectNormalizedDueTasksByHour(
            settings = settings,
            taskTemplates = taskTemplateRepository.getAll().first(),
            taskExecutions = taskExecutionRepository.getAll().first(),
            reminderGroups = reminderGroupRepository.getAll().first()
        )

        val dueTasks = dueTasksByHour[targetHour].orEmpty()
        val reminderBody = buildReminderNotificationBody(dueTasks)
            ?: return Result.success()

        ensureReminderChannel(applicationContext)

        val openTasksIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(reminderNotificationRouteExtraKey, reminderNotificationRouteTasks)
        }
        val contentIntent = PendingIntent.getActivity(
            applicationContext,
            targetHour,
            openTasksIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, reminderNotificationChannelId)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("AquaPT task reminder")
            .setContentText(reminderBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reminderBody))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        NotificationManagerCompat.from(applicationContext)
            .notify(reminderNotificationIdForHour(targetHour), notification)

        return Result.success()
    }
}

private fun reminderNotificationIdForHour(hour: Int): Int = 41000 + hour.coerceIn(0, 23)

private fun ensureReminderChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (manager.getNotificationChannel(reminderNotificationChannelId) != null) return

    val channel = NotificationChannel(
        reminderNotificationChannelId,
        reminderNotificationChannelName,
        NotificationManager.IMPORTANCE_DEFAULT
    ).apply {
        description = reminderNotificationChannelDescription
    }

    manager.createNotificationChannel(channel)
}

private fun canPostNotifications(context: Context): Boolean {
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
        return false
    }

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return true
    }

    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED
}
