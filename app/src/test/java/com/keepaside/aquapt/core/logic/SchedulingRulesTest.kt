package com.keepaside.aquapt.core.logic

import com.keepaside.aquapt.core.model.ReminderGroup
import com.keepaside.aquapt.core.model.TaskExecution
import com.keepaside.aquapt.core.model.TaskFrequency
import com.keepaside.aquapt.core.model.TaskTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class SchedulingRulesTest {

    private val zone: ZoneId = ZoneOffset.UTC
    private val now: Instant = Instant.parse("2026-04-11T12:00:00Z")

    @Test
    fun `task with no executions is due`() {
        val task = baseTask(id = "t-1", frequency = TaskFrequency.DAILY)

        val due = isTaskDue(task, aquariumId = "a-1", taskExecutions = emptyList(), now = now, zoneId = zone)

        assertTrue(due)
    }

    @Test
    fun `daily multi-completion task is due until required completions reached`() {
        val task = baseTask(id = "t-2", frequency = TaskFrequency.DAILY, timesPerDay = 2)
        val oneCompletion = listOf(
            TaskExecution(
                id = "e-1",
                taskTemplateId = "t-2",
                aquariumId = "a-1",
                completedAt = "2026-04-11T08:00:00Z"
            )
        )
        val twoCompletions = oneCompletion + TaskExecution(
            id = "e-2",
            taskTemplateId = "t-2",
            aquariumId = "a-1",
            completedAt = "2026-04-11T10:00:00Z"
        )

        assertTrue(isTaskDue(task, "a-1", oneCompletion, now, zone))
        assertFalse(isTaskDue(task, "a-1", twoCompletions, now, zone))
        assertEquals(2, getCompletionsToday(task, "a-1", twoCompletions, now, zone))
    }

    @Test
    fun `weekly task becomes due after seven days`() {
        val task = baseTask(id = "t-3", frequency = TaskFrequency.WEEKLY)
        val sixDaysAgo = TaskExecution(
            id = "e-3",
            taskTemplateId = "t-3",
            aquariumId = "a-1",
            completedAt = "2026-04-05T12:00:01Z"
        )
        val exactlySevenDaysAgo = sixDaysAgo.copy(completedAt = "2026-04-04T12:00:00Z")

        assertFalse(isTaskDue(task, "a-1", listOf(sixDaysAgo), now, zone))
        assertTrue(isTaskDue(task, "a-1", listOf(exactlySevenDaysAgo), now, zone))
    }

    @Test
    fun `task is not due before start date`() {
        val task = baseTask(id = "t-4", startDate = "2026-04-12")

        val due = isTaskDue(task, aquariumId = "a-1", taskExecutions = emptyList(), now = now, zoneId = zone)

        assertFalse(due)
    }

    @Test
    fun `reminder hours resolve with task then group then global precedence`() {
        val group = ReminderGroup(id = "g-1", name = "Morning", hours = listOf(9, 18))
        val global = listOf(12)

        val taskHours = baseTask(id = "t-hours", reminderHours = listOf(7, 20), reminderGroupId = "g-1")
        val taskGroup = baseTask(id = "t-group", reminderHours = emptyList(), reminderGroupId = "g-1")
        val taskGlobal = baseTask(id = "t-global", reminderHours = emptyList(), reminderGroupId = "missing")

        assertEquals(listOf(7, 20), resolveEffectiveReminderHours(taskHours, listOf(group), global))
        assertEquals(listOf(9, 18), resolveEffectiveReminderHours(taskGroup, listOf(group), global))
        assertEquals(global, resolveEffectiveReminderHours(taskGlobal, listOf(group), global))
    }

    @Test
    fun `collect due tasks groups by effective hour`() {
        val group = ReminderGroup(id = "g-2", name = "Group", hours = listOf(9))

        val taskA = baseTask(id = "task-a", reminderHours = listOf(8, 20))
        val taskB = baseTask(id = "task-b", reminderHours = emptyList(), reminderGroupId = "g-2")
        val taskC = baseTask(id = "task-c", reminderHours = emptyList(), reminderGroupId = null)
        val notDueTask = baseTask(id = "task-d", frequency = TaskFrequency.WEEKLY)

        val executions = listOf(
            TaskExecution(
                id = "e-4",
                taskTemplateId = "task-d",
                aquariumId = "a-1",
                completedAt = "2026-04-10T12:00:00Z"
            )
        )

        val byHour = collectDueTasksByHour(
            taskTemplates = listOf(taskA, taskB, taskC, notDueTask),
            taskExecutions = executions,
            reminderGroups = listOf(group),
            globalHours = listOf(6),
            now = now,
            zoneId = zone
        )

        assertEquals(listOf(taskA), byHour[8])
        assertEquals(listOf(taskA), byHour[20])
        assertEquals(listOf(taskB), byHour[9])
        assertEquals(listOf(taskC), byHour[6])
        assertEquals(4, byHour.size)
    }

    @Test
    fun `iso date helper uses yyyy-mm-dd`() {
        assertEquals("2026-04-11", toIsoDate(LocalDate.of(2026, 4, 11)))
    }

    private fun baseTask(
        id: String,
        frequency: TaskFrequency = TaskFrequency.DAILY,
        startDate: String? = null,
        timesPerDay: Int? = null,
        reminderHours: List<Int> = emptyList(),
        reminderGroupId: String? = null
    ) = TaskTemplate(
        id = id,
        title = "Task $id",
        frequency = frequency,
        aquariumIds = listOf("a-1"),
        startDate = startDate,
        timesPerDay = timesPerDay,
        reminderHours = reminderHours,
        reminderGroupId = reminderGroupId
    )
}
