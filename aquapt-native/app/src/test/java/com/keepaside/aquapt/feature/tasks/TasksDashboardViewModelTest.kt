package com.keepaside.aquapt.feature.tasks

import com.keepaside.aquapt.core.model.Aquarium
import com.keepaside.aquapt.core.model.DosingLog
import com.keepaside.aquapt.core.model.TaskExecution
import com.keepaside.aquapt.core.model.TaskFrequency
import com.keepaside.aquapt.core.model.TaskTemplate
import com.keepaside.aquapt.core.model.WaterType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class TasksDashboardViewModelTest {

    private val now: Instant = Instant.parse("2026-04-11T12:00:00Z")

    @Test
    fun `empty aquarium state is surfaced`() {
        val state = assembleTasksDashboardUiState(
            aquariums = emptyList(),
            taskTemplates = emptyList(),
            taskExecutions = emptyList(),
            dosingLogs = emptyList(),
            now = now,
            zoneId = ZoneOffset.UTC,
            statusMessage = null
        )

        assertTrue(state.isEmpty)
        assertEquals("Add your first tank to unlock task planning and completion tracking.", state.headline)
        assertEquals(0, state.summary.aquariumCount)
    }

    @Test
    fun `due matrix includes daily multi-completion progress`() {
        val aquarium = Aquarium(
            id = "a-1",
            name = "Display",
            volumeLiters = 120.0,
            waterType = WaterType.FRESHWATER,
            setupDate = "2026-01-01"
        )
        val task = TaskTemplate(
            id = "t-1",
            title = "Feed fish",
            frequency = TaskFrequency.DAILY,
            aquariumIds = listOf(aquarium.id),
            timesPerDay = 2
        )
        val execution = TaskExecution(
            id = "e-1",
            taskTemplateId = task.id,
            aquariumId = aquarium.id,
            completedAt = "2026-04-11T08:00:00Z"
        )

        val state = assembleTasksDashboardUiState(
            aquariums = listOf(aquarium),
            taskTemplates = listOf(task),
            taskExecutions = listOf(execution),
            dosingLogs = emptyList(),
            now = now,
            zoneId = ZoneOffset.UTC,
            statusMessage = null
        )

        assertEquals(1, state.dueTasks.size)
        assertEquals("Feed fish", state.dueTasks.first().taskTitle)
        assertEquals(1, state.dueTasks.first().completionsToday)
        assertEquals(2, state.dueTasks.first().timesPerDay)
        assertEquals(1, state.summary.dueTaskCount)
    }

    @Test
    fun `recent execution list is ordered by latest timestamp`() {
        val aquarium = Aquarium(
            id = "a-2",
            name = "Marine",
            volumeLiters = 300.0,
            waterType = WaterType.MARINE,
            setupDate = "2025-11-12"
        )
        val templateA = TaskTemplate(
            id = "t-a",
            title = "Check salinity",
            frequency = TaskFrequency.DAILY,
            aquariumIds = listOf(aquarium.id)
        )
        val templateB = TaskTemplate(
            id = "t-b",
            title = "Swap floss",
            frequency = TaskFrequency.WEEKLY,
            aquariumIds = listOf(aquarium.id)
        )
        val older = TaskExecution(
            id = "e-old",
            taskTemplateId = templateA.id,
            aquariumId = aquarium.id,
            completedAt = "2026-04-10T09:00:00Z"
        )
        val newer = TaskExecution(
            id = "e-new",
            taskTemplateId = templateB.id,
            aquariumId = aquarium.id,
            completedAt = "2026-04-11T10:00:00Z"
        )

        val state = assembleTasksDashboardUiState(
            aquariums = listOf(aquarium),
            taskTemplates = listOf(templateA, templateB),
            taskExecutions = listOf(older, newer),
            dosingLogs = emptyList(),
            now = now,
            zoneId = ZoneOffset.UTC,
            statusMessage = null
        )

        assertEquals(2, state.recentExecutions.size)
        assertEquals("Swap floss", state.recentExecutions.first().taskTitle)
        assertEquals("Check salinity", state.recentExecutions.last().taskTitle)
        assertEquals("2026-04-11 10:00", state.recentExecutions.first().completedAtInput)
    }

    @Test
    fun `dosing snapshot reports count and latest product per aquarium`() {
        val aquarium = Aquarium(
            id = "a-3",
            name = "Nano",
            volumeLiters = 45.0,
            waterType = WaterType.FRESHWATER,
            setupDate = "2026-02-01"
        )
        val logs = listOf(
            DosingLog(
                id = "d-1",
                aquariumId = aquarium.id,
                product = "Fertilizer A",
                amountMl = 2.5,
                createdAt = "2026-04-09T08:00:00Z"
            ),
            DosingLog(
                id = "d-2",
                aquariumId = aquarium.id,
                product = "Fertilizer B",
                amountMl = 1.0,
                createdAt = "2026-04-11T08:00:00Z"
            )
        )

        val state = assembleTasksDashboardUiState(
            aquariums = listOf(aquarium),
            taskTemplates = emptyList(),
            taskExecutions = emptyList(),
            dosingLogs = logs,
            now = now,
            zoneId = ZoneOffset.UTC,
            statusMessage = null
        )

        assertEquals(1, state.dosingSnapshots.size)
        assertEquals(2, state.dosingSnapshots.first().count)
        assertEquals("Fertilizer B", state.dosingSnapshots.first().latestProduct)
    }

    @Test
    fun `task date time input accepts friendly local value`() {
        val parsed = parseTaskDateTimeInput("2026-04-11 18:30", ZoneOffset.UTC)

        assertEquals(Instant.parse("2026-04-11T18:30:00Z"), parsed)
    }

    @Test
    fun `task date time input rejects invalid value`() {
        val parsed = parseTaskDateTimeInput("next water change", ZoneOffset.UTC)

        assertEquals(null, parsed)
    }
}
