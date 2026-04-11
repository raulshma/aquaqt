package com.keepaside.aquapt.feature.insights

import com.keepaside.aquapt.core.model.Aquarium
import com.keepaside.aquapt.core.model.Issue
import com.keepaside.aquapt.core.model.IssueStatus
import com.keepaside.aquapt.core.model.TaskExecution
import com.keepaside.aquapt.core.model.TaskFrequency
import com.keepaside.aquapt.core.model.TaskTemplate
import com.keepaside.aquapt.core.model.WaterParameterLog
import com.keepaside.aquapt.core.model.WaterParameters
import com.keepaside.aquapt.core.model.WaterType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class GlobalInsightsViewModelTest {

    private val now: Instant = Instant.parse("2026-04-11T12:00:00Z")

    @Test
    fun `empty aquarium state surfaces onboarding headline`() {
        val state = assembleGlobalInsightsUiState(
            aquariums = emptyList(),
            taskTemplates = emptyList(),
            taskExecutions = emptyList(),
            issues = emptyList(),
            parameterLogs = emptyList(),
            now = now,
            zoneId = ZoneOffset.UTC
        )

        assertTrue(state.isEmpty)
        assertEquals("Add your first tank to unlock portfolio-level insights.", state.headline)
        assertEquals(0, state.summary.aquariumCount)
        assertTrue(state.aquariumFocus.isEmpty())
    }

    @Test
    fun `summary and tank focus aggregate due tasks issues and alerts`() {
        val display = Aquarium(
            id = "a-display",
            name = "Display",
            volumeLiters = 180.0,
            waterType = WaterType.FRESHWATER,
            setupDate = "2026-01-01"
        )
        val nano = Aquarium(
            id = "a-nano",
            name = "Nano",
            volumeLiters = 38.0,
            waterType = WaterType.FRESHWATER,
            setupDate = "2026-02-01"
        )

        val dueTask = TaskTemplate(
            id = "task-due",
            title = "Water change",
            frequency = TaskFrequency.DAILY,
            aquariumIds = listOf(display.id)
        )
        val completedTask = TaskTemplate(
            id = "task-done",
            title = "Feed fish",
            frequency = TaskFrequency.DAILY,
            aquariumIds = listOf(nano.id)
        )

        val execution = TaskExecution(
            id = "exec-1",
            taskTemplateId = completedTask.id,
            aquariumId = nano.id,
            completedAt = "2026-04-11T08:00:00Z"
        )

        val issue = Issue(
            id = "issue-1",
            aquariumId = nano.id,
            title = "Cloudy water",
            status = IssueStatus.OPEN,
            createdAt = "2026-04-11T07:00:00Z"
        )

        val parameterLogs = listOf(
            WaterParameterLog(
                id = "log-display",
                aquariumId = display.id,
                createdAt = "2026-04-11T09:00:00Z",
                values = WaterParameters(nitrate = 12.0, ph = 7.2, temperatureC = 25.0)
            ),
            WaterParameterLog(
                id = "log-nano",
                aquariumId = nano.id,
                createdAt = "2026-04-11T09:30:00Z",
                values = WaterParameters(nitrate = 45.0, ph = 7.4, temperatureC = 26.0)
            )
        )

        val state = assembleGlobalInsightsUiState(
            aquariums = listOf(display, nano),
            taskTemplates = listOf(dueTask, completedTask),
            taskExecutions = listOf(execution),
            issues = listOf(issue),
            parameterLogs = parameterLogs,
            now = now,
            zoneId = ZoneOffset.UTC
        )

        assertEquals(2, state.summary.aquariumCount)
        assertEquals(1, state.summary.dueTaskCount)
        assertEquals(1, state.summary.activeIssueCount)
        assertEquals(1, state.summary.safetyAlertCount)
        assertEquals(nano.id, state.aquariumFocus.first().aquariumId)
        assertTrue(state.headline.contains("safety alert"))
    }

    @Test
    fun `steady recommendation appears when nothing urgent`() {
        val aquarium = Aquarium(
            id = "a-steady",
            name = "Steady",
            volumeLiters = 100.0,
            waterType = WaterType.FRESHWATER,
            setupDate = "2026-03-10"
        )

        val state = assembleGlobalInsightsUiState(
            aquariums = listOf(aquarium),
            taskTemplates = emptyList(),
            taskExecutions = emptyList(),
            issues = emptyList(),
            parameterLogs = emptyList(),
            now = now,
            zoneId = ZoneOffset.UTC
        )

        assertTrue(state.recommendations.any { recommendation ->
            recommendation.id == "steady" && recommendation.isHighlighted
        })
        assertEquals(
            "Portfolio health looks steady across 1 tank.",
            state.headline
        )
    }
}
