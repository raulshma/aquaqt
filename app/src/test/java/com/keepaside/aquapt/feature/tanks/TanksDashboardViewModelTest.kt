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
import org.junit.Assert.assertFalse
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

    @Test
    fun `parameter chart assembles data for selected metric and aquarium`() {
        val aquarium1 = Aquarium(
            id = "a-1",
            name = "Display",
            volumeLiters = 180.0,
            waterType = WaterType.FRESHWATER,
            setupDate = "2026-01-01"
        )
        val aquarium2 = Aquarium(
            id = "a-2",
            name = "Shrimp",
            volumeLiters = 35.0,
            waterType = WaterType.FRESHWATER,
            setupDate = "2026-02-01"
        )
        val logs = listOf(
            WaterParameterLog(
                id = "w-1",
                aquariumId = "a-1",
                createdAt = "2026-04-09T09:00:00Z",
                values = WaterParameters(nitrate = 10.0, ph = 7.0)
            ),
            WaterParameterLog(
                id = "w-2",
                aquariumId = "a-1",
                createdAt = "2026-04-10T09:00:00Z",
                values = WaterParameters(nitrate = 20.0, ph = 7.2)
            ),
            WaterParameterLog(
                id = "w-3",
                aquariumId = "a-1",
                createdAt = "2026-04-11T09:00:00Z",
                values = WaterParameters(nitrate = 15.0, ph = 7.5)
            ),
            WaterParameterLog(
                id = "w-4",
                aquariumId = "a-2",
                createdAt = "2026-04-11T09:00:00Z",
                values = WaterParameters(nitrate = 5.0, ph = 6.8)
            )
        )

        val state = assembleTanksDashboardUiState(
            aquariums = listOf(aquarium1, aquarium2),
            livestock = emptyList(),
            taskTemplates = emptyList(),
            taskExecutions = emptyList(),
            issues = emptyList(),
            parameterLogs = logs,
            dosingLogCount = 0,
            now = now,
            zoneId = ZoneOffset.UTC,
            selectedMetric = AnalyticMetric.NITRATE,
            selectedChartAquariumId = "a-1"
        )

        val chart = state.parameterChart
        assertEquals(AnalyticMetric.NITRATE, chart.selectedMetric)
        assertEquals("a-1", chart.selectedAquariumId)
        assertEquals(3, chart.chartData.size)
        assertEquals(10.0, chart.chartData[0].value, 0.01)
        assertEquals(20.0, chart.chartData[1].value, 0.01)
        assertEquals(15.0, chart.chartData[2].value, 0.01)
        assertEquals(2, chart.availableAquariums.size)
    }

    @Test
    fun `parameter chart filters by metric field`() {
        val aquarium = Aquarium(
            id = "a-1",
            name = "Display",
            volumeLiters = 180.0,
            waterType = WaterType.FRESHWATER,
            setupDate = "2026-01-01"
        )
        val logs = listOf(
            WaterParameterLog(
                id = "w-1",
                aquariumId = "a-1",
                createdAt = "2026-04-09T09:00:00Z",
                values = WaterParameters(ph = 7.0, nitrate = 10.0)
            ),
            WaterParameterLog(
                id = "w-2",
                aquariumId = "a-1",
                createdAt = "2026-04-10T09:00:00Z",
                values = WaterParameters(ph = 7.5, nitrate = 15.0)
            )
        )

        val phState = assembleTanksDashboardUiState(
            aquariums = listOf(aquarium),
            livestock = emptyList(),
            taskTemplates = emptyList(),
            taskExecutions = emptyList(),
            issues = emptyList(),
            parameterLogs = logs,
            dosingLogCount = 0,
            now = now,
            zoneId = ZoneOffset.UTC,
            selectedMetric = AnalyticMetric.PH,
            selectedChartAquariumId = "a-1"
        )

        assertEquals(2, phState.parameterChart.chartData.size)
        assertEquals(7.0, phState.parameterChart.chartData[0].value, 0.01)
        assertEquals(7.5, phState.parameterChart.chartData[1].value, 0.01)
    }

    @Test
    fun `parameter chart skips logs missing selected metric`() {
        val aquarium = Aquarium(
            id = "a-1",
            name = "Display",
            volumeLiters = 180.0,
            waterType = WaterType.FRESHWATER,
            setupDate = "2026-01-01"
        )
        val logs = listOf(
            WaterParameterLog(
                id = "w-1",
                aquariumId = "a-1",
                createdAt = "2026-04-09T09:00:00Z",
                values = WaterParameters(nitrate = 10.0)
            ),
            WaterParameterLog(
                id = "w-2",
                aquariumId = "a-1",
                createdAt = "2026-04-10T09:00:00Z",
                values = WaterParameters(ph = 7.5)
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
            zoneId = ZoneOffset.UTC,
            selectedMetric = AnalyticMetric.NITRATE,
            selectedChartAquariumId = "a-1"
        )

        assertEquals(1, state.parameterChart.chartData.size)
        assertEquals(10.0, state.parameterChart.chartData[0].value, 0.01)
    }

    @Test
    fun `parameter chart takes last 8 data points`() {
        val aquarium = Aquarium(
            id = "a-1",
            name = "Display",
            volumeLiters = 180.0,
            waterType = WaterType.FRESHWATER,
            setupDate = "2026-01-01"
        )
        val logs = (1..12).map { i ->
            WaterParameterLog(
                id = "w-$i",
                aquariumId = "a-1",
                createdAt = "2026-04-${String.format("%02d", i)}T09:00:00Z",
                values = WaterParameters(nitrate = i.toDouble())
            )
        }

        val state = assembleTanksDashboardUiState(
            aquariums = listOf(aquarium),
            livestock = emptyList(),
            taskTemplates = emptyList(),
            taskExecutions = emptyList(),
            issues = emptyList(),
            parameterLogs = logs,
            dosingLogCount = 0,
            now = now,
            zoneId = ZoneOffset.UTC,
            selectedMetric = AnalyticMetric.NITRATE,
            selectedChartAquariumId = "a-1"
        )

        assertEquals(8, state.parameterChart.chartData.size)
        assertEquals(5.0, state.parameterChart.chartData.first().value, 0.01)
        assertEquals(12.0, state.parameterChart.chartData.last().value, 0.01)
    }

    @Test
    fun `parameter chart defaults to first aquarium when none selected`() {
        val aquarium1 = Aquarium(
            id = "a-1",
            name = "Display",
            volumeLiters = 180.0,
            waterType = WaterType.FRESHWATER,
            setupDate = "2026-01-01"
        )
        val aquarium2 = Aquarium(
            id = "a-2",
            name = "Shrimp",
            volumeLiters = 35.0,
            waterType = WaterType.FRESHWATER,
            setupDate = "2026-02-01"
        )
        val logs = listOf(
            WaterParameterLog(
                id = "w-1",
                aquariumId = "a-1",
                createdAt = "2026-04-09T09:00:00Z",
                values = WaterParameters(nitrate = 10.0)
            ),
            WaterParameterLog(
                id = "w-2",
                aquariumId = "a-2",
                createdAt = "2026-04-10T09:00:00Z",
                values = WaterParameters(nitrate = 50.0)
            )
        )

        val state = assembleTanksDashboardUiState(
            aquariums = listOf(aquarium1, aquarium2),
            livestock = emptyList(),
            taskTemplates = emptyList(),
            taskExecutions = emptyList(),
            issues = emptyList(),
            parameterLogs = logs,
            dosingLogCount = 0,
            now = now,
            zoneId = ZoneOffset.UTC,
            selectedMetric = AnalyticMetric.NITRATE,
            selectedChartAquariumId = null
        )

        assertEquals("a-1", state.parameterChart.selectedAquariumId)
        assertEquals(1, state.parameterChart.chartData.size)
        assertEquals(10.0, state.parameterChart.chartData[0].value, 0.01)
    }

    @Test
    fun `analytic metric extractFrom matches water parameters fields`() {
        val params = WaterParameters(
            ammonia = 0.25,
            nitrite = 0.1,
            nitrate = 15.0,
            ph = 7.2,
            temperatureC = 25.5,
            gh = 8.0,
            kh = 4.0,
            salinity = 1.025,
            calcium = 400.0,
            alkalinity = 8.0
        )

        assertEquals(0.25, AnalyticMetric.AMMONIA.extractFrom(params)!!, 0.001)
        assertEquals(0.1, AnalyticMetric.NITRITE.extractFrom(params)!!, 0.001)
        assertEquals(15.0, AnalyticMetric.NITRATE.extractFrom(params)!!, 0.001)
        assertEquals(7.2, AnalyticMetric.PH.extractFrom(params)!!, 0.001)
        assertEquals(25.5, AnalyticMetric.TEMPERATURE.extractFrom(params)!!, 0.001)
        assertEquals(8.0, AnalyticMetric.GH.extractFrom(params)!!, 0.001)
        assertEquals(4.0, AnalyticMetric.KH.extractFrom(params)!!, 0.001)
        assertEquals(1.025, AnalyticMetric.SALINITY.extractFrom(params)!!, 0.001)
        assertEquals(400.0, AnalyticMetric.CALCIUM.extractFrom(params)!!, 0.001)
        assertEquals(8.0, AnalyticMetric.ALKALINITY.extractFrom(params)!!, 0.001)
    }

    @Test
    fun `analytic metric extractFrom returns null for missing fields`() {
        val params = WaterParameters()
        assertNull(AnalyticMetric.AMMONIA.extractFrom(params))
        assertNull(AnalyticMetric.NITRATE.extractFrom(params))
        assertNull(AnalyticMetric.PH.extractFrom(params))

        val partial = WaterParameters(nitrate = 10.0)
        assertNull(AnalyticMetric.AMMONIA.extractFrom(partial))
        assertEquals(10.0, AnalyticMetric.NITRATE.extractFrom(partial)!!, 0.001)
    }

    @Test
    fun `quick log due task options filters by selected aquarium and due status`() {
        val aquarium = Aquarium(id = "a-1", name = "Tank", volumeLiters = 100.0, waterType = WaterType.FRESHWATER, setupDate = "2026-01-01")
        val task = TaskTemplate(id = "t-1", title = "Feed", frequency = TaskFrequency.DAILY, aquariumIds = listOf("a-1"))

        val options = buildQuickLogDueTaskOptions(
            selectedAquariumId = "a-1",
            taskTemplates = listOf(task),
            taskExecutions = emptyList(),
            aquariums = listOf(aquarium),
            now = now,
            zoneId = ZoneOffset.UTC
        )

        assertEquals(1, options.size)
        assertEquals("t-1", options[0].taskTemplateId)
        assertEquals("Feed", options[0].title)
    }

    @Test
    fun `quick log due task options returns empty when no aquarium selected`() {
        val task = TaskTemplate(id = "t-1", title = "Feed", frequency = TaskFrequency.DAILY, aquariumIds = listOf("a-1"))

        val options = buildQuickLogDueTaskOptions(
            selectedAquariumId = null,
            taskTemplates = listOf(task),
            taskExecutions = emptyList(),
            aquariums = emptyList(),
            now = now,
            zoneId = ZoneOffset.UTC
        )

        assertTrue(options.isEmpty())
    }

    @Test
    fun `quick log validation rejects empty task template`() {
        val draft = TanksQuickLogDraft(type = TanksQuickLogType.TASK, aquariumId = "a-1", taskTemplateId = "")
        assertEquals("Choose a due task before saving.", validateTanksQuickLogDraft(draft))
    }

    @Test
    fun `quick log validation rejects empty memo content`() {
        val draft = TanksQuickLogDraft(type = TanksQuickLogType.MEMO, aquariumId = "a-1", memoContent = "")
        assertEquals("Write a memo before saving.", validateTanksQuickLogDraft(draft))
    }

    @Test
    fun `quick log validation rejects empty issue title`() {
        val draft = TanksQuickLogDraft(type = TanksQuickLogType.ISSUE, aquariumId = "a-1", issueTitle = "")
        assertEquals("Name the issue before saving.", validateTanksQuickLogDraft(draft))
    }

    @Test
    fun `quick log validation rejects empty parameter input`() {
        val draft = TanksQuickLogDraft(type = TanksQuickLogType.PARAMETER, aquariumId = "a-1")
        assertEquals("Enter at least one parameter value.", validateTanksQuickLogDraft(draft))
    }

    @Test
    fun `quick log validation rejects empty dosing product`() {
        val draft = TanksQuickLogDraft(type = TanksQuickLogType.DOSING, aquariumId = "a-1", dosingProduct = "", dosingAmountMl = "5")
        assertEquals("Name the dosing product before saving.", validateTanksQuickLogDraft(draft))
    }

    @Test
    fun `quick log validation rejects non-positive dosing amount`() {
        val draft = TanksQuickLogDraft(type = TanksQuickLogType.DOSING, aquariumId = "a-1", dosingProduct = "Prime", dosingAmountMl = "0")
        assertEquals("Amount must be a positive number.", validateTanksQuickLogDraft(draft))
    }

    @Test
    fun `quick log validation accepts valid drafts`() {
        assertNull(validateTanksQuickLogDraft(TanksQuickLogDraft(type = TanksQuickLogType.TASK, taskTemplateId = "t-1")))
        assertNull(validateTanksQuickLogDraft(TanksQuickLogDraft(type = TanksQuickLogType.MEMO, memoContent = "test")))
        assertNull(validateTanksQuickLogDraft(TanksQuickLogDraft(type = TanksQuickLogType.ISSUE, issueTitle = "Cloudy")))
        assertNull(validateTanksQuickLogDraft(TanksQuickLogDraft(type = TanksQuickLogType.PARAMETER, nitrate = "10.0")))
        assertNull(validateTanksQuickLogDraft(TanksQuickLogDraft(type = TanksQuickLogType.DOSING, dosingProduct = "Prime", dosingAmountMl = "2.5")))
    }

    @Test
    fun `quick log draft hasAnyParameterInput detects non-blank fields`() {
        assertTrue(TanksQuickLogDraft(nitrate = "10.0").hasAnyParameterInput())
        assertFalse(TanksQuickLogDraft().hasAnyParameterInput())
    }

    @Test
    fun `quick log draft toWaterParameters parses valid values`() {
        val draft = TanksQuickLogDraft(nitrate = "15.0", ph = "7.2", temperatureC = "25")
        val params = draft.toWaterParameters()
        assertEquals(15.0, params.nitrate!!, 0.001)
        assertEquals(7.2, params.ph!!, 0.001)
        assertEquals(25.0, params.temperatureC!!, 0.001)
        assertNull(params.ammonia)
    }

    @Test
    fun `quick log draft toWaterParameters ignores non-finite values`() {
        val params = TanksQuickLogDraft(nitrate = "abc").toWaterParameters()
        assertNull(params.nitrate)
    }

    @Test
    fun `quick log draft canAttemptSave checks required fields`() {
        assertFalse(TanksQuickLogDraft(type = TanksQuickLogType.TASK).canAttemptSave())
        assertTrue(TanksQuickLogDraft(type = TanksQuickLogType.TASK, aquariumId = "a-1", taskTemplateId = "t-1").canAttemptSave())
        assertFalse(TanksQuickLogDraft(type = TanksQuickLogType.MEMO, aquariumId = "a-1", memoContent = "").canAttemptSave())
        assertTrue(TanksQuickLogDraft(type = TanksQuickLogType.MEMO, aquariumId = "a-1", memoContent = "text").canAttemptSave())
    }

    @Test
    fun `parse tanks quick positive amount rejects non-positive`() {
        assertNull(parseTanksQuickPositiveAmount(""))
        assertNull(parseTanksQuickPositiveAmount("0"))
        assertNull(parseTanksQuickPositiveAmount("-1"))
        assertEquals(2.5, parseTanksQuickPositiveAmount("2.5")!!, 0.001)
    }
}