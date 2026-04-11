package com.keepaside.aquapt.core.notifications

internal const val reminderNotificationChannelId = "reminders"
internal const val reminderNotificationChannelName = "Reminders"
internal const val reminderNotificationChannelDescription =
    "Daily aquarium task reminders"

internal const val reminderNotificationRouteTasks = "/(tabs)/tasks"
const val reminderNotificationRouteExtraKey = "aquapt_notification_route"

internal const val reminderWorkerInputHourKey = "reminder_hour"
internal const val reminderWorkerTag = "aquapt-daily-reminder"

internal fun reminderWorkerUniqueName(hour: Int): String =
    "aquapt-daily-reminder-hour-${hour.coerceIn(0, 23)}"
