package com.keepaside.aquapt.core.notifications

import com.keepaside.aquapt.core.logic.collectDueTasksByHour
import com.keepaside.aquapt.core.model.AppSettings
import com.keepaside.aquapt.core.model.ReminderGroup
import com.keepaside.aquapt.core.model.TaskExecution
import com.keepaside.aquapt.core.model.TaskTemplate
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

internal fun collectNormalizedDueTasksByHour(
    settings: AppSettings,
    taskTemplates: List<TaskTemplate>,
    taskExecutions: List<TaskExecution>,
    reminderGroups: List<ReminderGroup>,
    now: Instant = Instant.now(),
    zoneId: ZoneId = ZoneId.systemDefault()
): Map<Int, List<TaskTemplate>> {
    if (!settings.notificationsEnabled) {
        return emptyMap()
    }

    val dueTasksByHour = collectDueTasksByHour(
        taskTemplates = taskTemplates,
        taskExecutions = taskExecutions,
        reminderGroups = reminderGroups,
        globalHours = settings.reminderHours,
        now = now,
        zoneId = zoneId
    )

    if (dueTasksByHour.isEmpty()) {
        return emptyMap()
    }

    val normalized = mutableMapOf<Int, MutableList<TaskTemplate>>()
    for ((hour, tasks) in dueTasksByHour) {
        if (tasks.isEmpty()) continue
        val normalizedHour = hour.coerceIn(0, 23)
        normalized.getOrPut(normalizedHour) { mutableListOf() }
            .addAll(tasks)
    }

    return normalized
        .toSortedMap()
        .mapValues { (_, tasks) -> tasks.distinctBy { it.id } }
}

internal fun buildReminderNotificationBody(tasks: List<TaskTemplate>): String? {
    if (tasks.isEmpty()) return null

    val titles = tasks.map { template -> template.title.trim() }
        .filter { title -> title.isNotEmpty() }

    if (titles.isEmpty()) return null

    if (titles.size == 1) {
        return "${titles.first()} is due."
    }

    val preview = titles.take(3).joinToString(", ")
    val suffix = if (titles.size > 3) "…" else ""
    return "${titles.size} tasks due: $preview$suffix"
}

internal fun calculateNextReminderInitialDelayMillis(
    hour: Int,
    now: ZonedDateTime = ZonedDateTime.now()
): Long {
    val normalizedHour = hour.coerceIn(0, 23)
    val nextTrigger = now
        .withHour(normalizedHour)
        .withMinute(0)
        .withSecond(0)
        .withNano(0)
        .let { candidate ->
            if (candidate.isAfter(now)) candidate else candidate.plusDays(1)
        }

    return Duration.between(now, nextTrigger)
        .toMillis()
        .coerceAtLeast(0L)
}
