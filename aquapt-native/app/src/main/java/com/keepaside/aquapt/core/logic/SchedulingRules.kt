package com.keepaside.aquapt.core.logic

import com.keepaside.aquapt.core.model.ReminderGroup
import com.keepaside.aquapt.core.model.TaskExecution
import com.keepaside.aquapt.core.model.TaskFrequency
import com.keepaside.aquapt.core.model.TaskFrequencyKind
import com.keepaside.aquapt.core.model.TaskTemplate
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L

private fun getFrequencyDays(frequency: TaskFrequency): Long =
    when (frequency.kind) {
        TaskFrequencyKind.DAILY -> 1L
        TaskFrequencyKind.WEEKLY -> 7L
        TaskFrequencyKind.BI_WEEKLY -> 14L
        TaskFrequencyKind.MONTHLY -> 30L
        TaskFrequencyKind.CUSTOM -> (frequency.customDays ?: 1).coerceAtLeast(1).toLong()
    }

fun toIsoDate(date: LocalDate): String = date.toString()

fun getExecutionsForTask(
    taskExecutions: List<TaskExecution>,
    taskTemplateId: String,
    aquariumId: String
): List<TaskExecution> =
    taskExecutions.filter { it.taskTemplateId == taskTemplateId && it.aquariumId == aquariumId }

fun getExecutionsForDay(
    executions: List<TaskExecution>,
    date: LocalDate,
    zoneId: ZoneId = ZoneId.systemDefault()
): List<TaskExecution> =
    executions.filter { execution ->
        parseToInstant(execution.completedAt, zoneId)
            ?.atZone(zoneId)
            ?.toLocalDate() == date
    }

fun getLatestExecutionIso(
    taskExecutions: List<TaskExecution>,
    taskTemplateId: String,
    aquariumId: String,
    zoneId: ZoneId = ZoneId.systemDefault()
): String? {
    val executions = getExecutionsForTask(taskExecutions, taskTemplateId, aquariumId)
    if (executions.isEmpty()) return null

    var latestIso: String? = null
    var latestTs = Long.MIN_VALUE

    for (execution in executions) {
        val ts = parseToInstant(execution.completedAt, zoneId)?.toEpochMilli() ?: continue
        if (ts <= latestTs) continue
        latestTs = ts
        latestIso = execution.completedAt
    }

    return latestIso
}

fun isTaskDue(
    task: TaskTemplate,
    aquariumId: String,
    taskExecutions: List<TaskExecution>,
    now: Instant = Instant.now(),
    zoneId: ZoneId = ZoneId.systemDefault()
): Boolean {
    if (task.startDate != null) {
        val startDate = parseToLocalDate(task.startDate, zoneId)
        if (startDate != null && now.atZone(zoneId).toLocalDate().isBefore(startDate)) {
            return false
        }
    }

    val executions = getExecutionsForTask(taskExecutions, task.id, aquariumId)
    val timesPerDay = task.timesPerDay ?: 1

    if (task.frequency.kind == TaskFrequencyKind.DAILY && timesPerDay > 1) {
        val today = now.atZone(zoneId).toLocalDate()
        val todayExecutions = getExecutionsForDay(executions, today, zoneId)
        return todayExecutions.size < timesPerDay
    }

    val lastDoneIso = getLatestExecutionIso(taskExecutions, task.id, aquariumId, zoneId)
        ?: return true

    val lastDone = parseToInstant(lastDoneIso, zoneId) ?: return true
    val elapsedMs = now.toEpochMilli() - lastDone.toEpochMilli()
    val days = getFrequencyDays(task.frequency)

    return elapsedMs >= days * MILLIS_PER_DAY
}

fun getCompletionsToday(
    task: TaskTemplate,
    aquariumId: String,
    taskExecutions: List<TaskExecution>,
    now: Instant = Instant.now(),
    zoneId: ZoneId = ZoneId.systemDefault()
): Int {
    val executions = getExecutionsForTask(taskExecutions, task.id, aquariumId)
    val today = now.atZone(zoneId).toLocalDate()
    return getExecutionsForDay(executions, today, zoneId).size
}

fun countDueTasks(
    taskTemplates: List<TaskTemplate>,
    taskExecutions: List<TaskExecution>,
    now: Instant = Instant.now(),
    zoneId: ZoneId = ZoneId.systemDefault()
): Int =
    taskTemplates.sumOf { task ->
        task.aquariumIds.count { aquariumId ->
            isTaskDue(task, aquariumId, taskExecutions, now, zoneId)
        }
    }

fun resolveEffectiveReminderHours(
    task: TaskTemplate,
    reminderGroups: List<ReminderGroup>,
    globalHours: List<Int>
): List<Int> {
    if (task.reminderHours.isNotEmpty()) {
        return task.reminderHours
    }

    if (!task.reminderGroupId.isNullOrBlank()) {
        val group = reminderGroups.firstOrNull { it.id == task.reminderGroupId }
        if (group != null && group.hours.isNotEmpty()) {
            return group.hours
        }
    }

    return globalHours
}

fun collectDueTasksByHour(
    taskTemplates: List<TaskTemplate>,
    taskExecutions: List<TaskExecution>,
    reminderGroups: List<ReminderGroup>,
    globalHours: List<Int>,
    now: Instant = Instant.now(),
    zoneId: ZoneId = ZoneId.systemDefault()
): Map<Int, List<TaskTemplate>> {
    val byHour = mutableMapOf<Int, MutableList<TaskTemplate>>()

    for (task in taskTemplates) {
        val due = task.aquariumIds.any { aquariumId ->
            isTaskDue(task, aquariumId, taskExecutions, now, zoneId)
        }
        if (!due) continue

        val hours = resolveEffectiveReminderHours(task, reminderGroups, globalHours)
        for (hour in hours) {
            byHour.getOrPut(hour) { mutableListOf() }.add(task)
        }
    }

    return byHour
}

private fun parseToLocalDate(raw: String, zoneId: ZoneId): LocalDate? =
    runCatching { LocalDate.parse(raw.trim()) }.getOrNull()
        ?: parseToInstant(raw, zoneId)?.atZone(zoneId)?.toLocalDate()

private fun parseToInstant(raw: String, zoneId: ZoneId): Instant? {
    val value = raw.trim()
    if (value.isEmpty()) return null

    return runCatching { Instant.parse(value) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()
        ?: runCatching { LocalDateTime.parse(value).atZone(zoneId).toInstant() }.getOrNull()
        ?: runCatching { LocalDate.parse(value).atStartOfDay(zoneId).toInstant() }.getOrNull()
}
