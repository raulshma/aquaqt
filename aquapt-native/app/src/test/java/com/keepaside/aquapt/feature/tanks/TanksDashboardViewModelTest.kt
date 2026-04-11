package com.keepaside.aquapt.feature.tanks

import com.keepaside.aquapt.core.model.Aquarium
import com.keepaside.aquapt.core.model.Issue
import com.keepaside.aquapt.core.model.IssueStatus
import com.keepaside.aquapt.core.model.Livestock
import com.keepaside.aquapt.core.model.TaskExecution
import com.keepaside.aquapt.core.model.TaskFrequency
import com.keepaside.aquapt.core.model.TaskTemplate
import com.keepaside.aquapt.core.model.WaterParameterLog
import com.keepaside.aquapt.core.model.WaterParameters
import com.keepaside.aquapt.core.model.WaterType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class TanksDashboardViewModelTest {

    private val now: Instant = Instant.parse("2026-04-11T12:00:00Z")

    @Test
    fun `empty aquariums returns empty dashboard state`() {
        val state = assembleTanksDashboardUiState(
            aquariums = emptyList(),
            livestock = emptyList(),
            taskTemplates = emptyList(),
            taskExecutions = emptyList(),
            issues = emptyList(),
            parameterLogs = emptyList(),
            dosingLogCount = 0,
            now = now,
            zoneId = ZoneOffset.UTC
        )

        assertTrue(state.isEmpty)
        assertEquals("Add your first tank to begin tracking care routines.", state.headline)
        assertEquals(0, state.summary.aquariumCount)
        assertEquals(0, state.summary.dueTaskCount)
    }

    @Test
    fun `dashboard aggregates due tasks open issues and alerts`() {
        val aquarium = Aquarium(
            id = "a-1",
            name = "Display",
            volumeLiters = 180.0,
            waterType = WaterType.FRESHWATER,
            setupDate = "2026-01-01"
        )
        val task = TaskTemplate(
            id = "t-1",
            title = "Water change",
            frequency = TaskFrequency.DAILY,
            aquariumIds = listOf(aquarium.id)
        )
        val issueOpen = Issue(
            id = "i-1",
            aquariumId = aquarium.id,
            title = "Cloudy water",
            status = IssueStatus.OPEN,
            createdAt = "2026-04-10T08:00:00Z"
        )
        val issueResolved = issueOpen.copy(id = "i-2", status = IssueStatus.RESOLVED)
        val livestock = listOf(
            Livestock(
                id = "l-1",
                aquariumId = aquarium.id,
                name = "Neon tetra",
                quantity = 7
            )
        )
        val logs = listOf(
            WaterParameterLog(
                id = "w-1",
                aquariumId = aquarium.id,
                createdAt = "2026-04-10T09:00:00Z",
                values = WaterParameters(nitrate = 10.0, ph = 7.1, temperatureC = 25.0)
            ),
            WaterParameterLog(
                id = "w-2",
                aquariumId = aquarium.id,
                createdAt = "2026-04-11T09:00:00Z",
                values = WaterParameters(nitrate = 35.0, ph = 7.3, temperatureC = 26.0)
            )
        )

        val state = assembleTanksDashboardUiState(
            aquariums = listOf(aquarium),
            livestock = livestock,
            taskTemplates = listOf(task),
            taskExecutions = emptyList(),
            issues = listOf(issueOpen, issueResolved),
            parameterLogs = logs,
            dosingLogCount = 3,
            now = now,
            zoneId = ZoneOffset.UTC
        )

        assertEquals(1, state.summary.aquariumCount)
        assertEquals(7, state.summary.residentCount)
        assertEquals(1, state.summary.dueTaskCount)
        assertEquals(1, state.summary.openIssueCount)
        assertEquals(3, state.summary.dosingLogCount)
        assertTrue(state.summary.parameterAlertCount >= 1)
        assertEquals(1, state.dueTasks.size)
        assertTrue(state.headline.contains("water alerts") || state.headline.contains("due"))
        assertEquals("Display", state.aquariums.first().aquariumName)
    }

    @Test
    fun `nitrate trend shows directional delta`() {
        val aquarium = Aquarium(
            id = "a-2",
            name = "Shrimp tank",
            volumeLiters = 35.0,
            waterType = WaterType.FRESHWATER,
            setupDate = "2026-03-01"
        )
        val logs = listOf(
            WaterParameterLog(
                id = "w-a",
                aquariumId = aquarium.id,
                createdAt = "2026-04-09T12:00:00Z",
                values = WaterParameters(nitrate = 5.0)
            ),
            WaterParameterLog(
                id = "w-b",
                aquariumId = aquarium.id,
                createdAt = "2026-04-11T12:00:00Z",
                values = WaterParameters(nitrate = 9.0)
            )
        )

        val state = assembleTanksDashboardUiState(
            aquariums = listOf(aquarium),
            livestock = emptyList(),
            taskTemplates = emptyList(),
            taskExecutions = emptyList(),
            issues = emptyList(),
            parameterLogs = logs,
            dosingLogCount = 0,
            now = now,
            zoneId = ZoneOffset.UTC
        )

        assertTrue(state.aquariums.first().nitrateTrend.startsWith("↑"))
        assertTrue(state.aquariums.first().nitrateTrend.contains("+4.00"))
    }

    @Test
    fun `recent execution removes due status for daily task`() {
        val aquarium = Aquarium(
            id = "a-3",
            name = "Marine",
            volumeLiters = 260.0,
            waterType = WaterType.MARINE,
            setupDate = "2026-02-15"
        )
        val task = TaskTemplate(
            id = "t-3",
            title = "Check skimmer",
            frequency = TaskFrequency.DAILY,
            aquariumIds = listOf(aquarium.id)
        )
        val executions = listOf(
            TaskExecution(
                id = "e-1",
                taskTemplateId = task.id,
                aquariumId = aquarium.id,
                completedAt = "2026-04-11T08:00:00Z"
            )
        )

        val state = assembleTanksDashboardUiState(
            aquariums = listOf(aquarium),
            livestock = emptyList(),
            taskTemplates = listOf(task),
            taskExecutions = executions,
            issues = emptyList(),
            parameterLogs = emptyList(),
            dosingLogCount = 0,
            now = now,
            zoneId = ZoneOffset.UTC
        )

        assertEquals(0, state.summary.dueTaskCount)
        assertTrue(state.dueTasks.isEmpty())
    }

    @Test
    fun `aquarium draft validation rejects invalid numeric inputs`() {
        val invalidVolume = validateTanksAquariumDraft(
            TanksAquariumDraft(
                name = "Display",
                volumeLitersInput = "0"
            )
        )
        assertEquals("Volume must be a positive number.", invalidVolume)

        val invalidCost = validateTanksAquariumDraft(
            TanksAquariumDraft(
                name = "Display",
                volumeLitersInput = "120",
                investmentCostInput = "-5"
            )
        )
        assertEquals("Investment cost must be a valid non-negative number.", invalidCost)
    }

    @Test
    fun `livestock draft validation checks aquarium quantity and datetime`() {
        val invalidAquarium = validateTanksLivestockDraft(
            draft = TanksLivestockDraft(
                aquariumId = "missing",
                name = "Neon tetra",
                quantityInput = "3"
            ),
            aquariumIds = listOf("a-1"),
            zoneId = ZoneOffset.UTC
        )
        assertEquals("Choose a valid tank for this resident.", invalidAquarium)

        val invalidQuantity = validateTanksLivestockDraft(
            draft = TanksLivestockDraft(
                aquariumId = "a-1",
                name = "Neon tetra",
                quantityInput = "0"
            ),
            aquariumIds = listOf("a-1"),
            zoneId = ZoneOffset.UTC
        )
        assertEquals("Quantity must be at least 1.", invalidQuantity)

        val invalidDateTime = validateTanksLivestockDraft(
            draft = TanksLivestockDraft(
                aquariumId = "a-1",
                name = "Neon tetra",
                quantityInput = "2",
                acquiredAtInput = "not-a-date"
            ),
            aquariumIds = listOf("a-1"),
            zoneId = ZoneOffset.UTC
        )
        assertEquals("Use a valid acquired date/time like 2026-04-11 18:30.", invalidDateTime)
    }

    @Test
    fun `tanks datetime and numeric parsers handle supported formats`() {
        val parsedLocalDateTime = parseTanksDateTimeInput("2026-04-11 18:30", ZoneOffset.UTC)
        assertEquals(Instant.parse("2026-04-11T18:30:00Z"), parsedLocalDateTime)

        val parsedDate = parseTanksDateTimeInput("2026-04-11", ZoneOffset.UTC)
        assertEquals(Instant.parse("2026-04-11T00:00:00Z"), parsedDate)

        assertEquals(1.25, parseTanksNonNegativeDouble("1.25") ?: 0.0, 0.0)
        assertNull(parseTanksNonNegativeDouble(""))
        assertNull(parseTanksPositiveDouble("0"))
    }
}