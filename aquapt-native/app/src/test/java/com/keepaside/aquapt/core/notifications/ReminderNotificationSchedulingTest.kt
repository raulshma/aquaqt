package com.keepaside.aquapt.core.notifications

import com.keepaside.aquapt.core.model.AppSettings
import com.keepaside.aquapt.core.model.ReminderGroup
import com.keepaside.aquapt.core.model.TaskFrequency
import com.keepaside.aquapt.core.model.TaskTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime

class ReminderNotificationSchedulingTest {

    private val now = Instant.parse("2026-04-11T12:00:00Z")

    @Test
    fun `notifications disabled yields no scheduled reminder hours`() {
        val settings = AppSettings(
            notificationsEnabled = false,
            reminderHours = listOf(8)
        )
        val tasks = listOf(
            baseTask(
                id = "task-1",
                reminderHours = listOf(9)
            )
        )

        val dueByHour = collectNormalizedDueTasksByHour(
            settings = settings,
            taskTemplates = tasks,
            taskExecutions = emptyList(),
            reminderGroups = emptyList(),
            now = now,
            zoneId = ZoneOffset.UTC
        )

        assertTrue(dueByHour.isEmpty())
    }

    @Test
    fun `due tasks are grouped by task, group, and global reminder hours`() {
        val settings = AppSettings(
            notificationsEnabled = true,
            reminderHours = listOf(6)
        )
        val groups = listOf(
            ReminderGroup(id = "g-1", name = "Morning", hours = listOf(9))
        )

        val taskWithOwnHours = baseTask(id = "task-own", reminderHours = listOf(8, 20))
        val taskWithGroup = baseTask(id = "task-group", reminderGroupId = "g-1")
        val taskWithGlobal = baseTask(id = "task-global")

        val dueByHour = collectNormalizedDueTasksByHour(
            settings = settings,
            taskTemplates = listOf(taskWithOwnHours, taskWithGroup, taskWithGlobal),
            taskExecutions = emptyList(),
            reminderGroups = groups,
            now = now,
            zoneId = ZoneOffset.UTC
        )

        assertEquals(listOf(6, 8, 9, 20), dueByHour.keys.toList())
        assertEquals(listOf(taskWithGlobal), dueByHour[6])
        assertEquals(listOf(taskWithOwnHours), dueByHour[8])
        assertEquals(listOf(taskWithGroup), dueByHour[9])
        assertEquals(listOf(taskWithOwnHours), dueByHour[20])
    }

    @Test
    fun `notification body adapts for one or many due tasks`() {
        val single = buildReminderNotificationBody(
            listOf(baseTask(id = "single", title = "Water change"))
        )
        val many = buildReminderNotificationBody(
            listOf(
                baseTask(id = "t-1", title = "Water change"),
                baseTask(id = "t-2", title = "Dose nitrate"),
                baseTask(id = "t-3", title = "Trim plants"),
                baseTask(id = "t-4", title = "Inspect filter")
            )
        )

        assertEquals("Water change is due.", single)
        assertEquals("4 tasks due: Water change, Dose nitrate, Trim plants…", many)
    }

    @Test
    fun `initial delay targets the next occurrence of requested hour`() {
        val baseTime = ZonedDateTime.parse("2026-04-11T09:30:00Z")

        val halfHourDelay = calculateNextReminderInitialDelayMillis(hour = 10, now = baseTime)
        val nextDayDelay = calculateNextReminderInitialDelayMillis(hour = 9, now = baseTime)

        assertEquals(30L * 60L * 1000L, halfHourDelay)
        assertEquals(23L * 60L * 60L * 1000L + 30L * 60L * 1000L, nextDayDelay)
    }

    private fun baseTask(
        id: String,
        title: String = "Task $id",
        reminderHours: List<Int> = emptyList(),
        reminderGroupId: String? = null
    ) = TaskTemplate(
        id = id,
        title = title,
        frequency = TaskFrequency.DAILY,
        aquariumIds = listOf("a-1"),
        reminderHours = reminderHours,
        reminderGroupId = reminderGroupId
    )
}
