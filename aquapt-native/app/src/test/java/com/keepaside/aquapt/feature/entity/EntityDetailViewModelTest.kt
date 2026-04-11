package com.keepaside.aquapt.feature.entity

import com.keepaside.aquapt.core.model.Aquarium
import com.keepaside.aquapt.core.model.EntityKind
import com.keepaside.aquapt.core.model.EntityRef
import com.keepaside.aquapt.core.model.Issue
import com.keepaside.aquapt.core.model.IssueStatus
import com.keepaside.aquapt.core.model.Livestock
import com.keepaside.aquapt.core.model.TaskCategory
import com.keepaside.aquapt.core.model.TaskExecution
import com.keepaside.aquapt.core.model.TaskFrequency
import com.keepaside.aquapt.core.model.TaskTemplate
import com.keepaside.aquapt.core.model.TimelineEvent
import com.keepaside.aquapt.core.model.TimelineEventType
import com.keepaside.aquapt.core.model.WaterType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class EntityDetailViewModelTest {

    @Test
    fun `invalid deep link returns missing-state guidance`() {
        val state = assembleEntityDetailUiState(
            kind = null,
            entityId = "",
            routeAquariumId = null,
            aquariums = emptyList(),
            taskTemplates = emptyList(),
            taskExecutions = emptyList(),
            livestock = emptyList(),
            assets = emptyList(),
            consumables = emptyList(),
            issues = emptyList(),
            memos = emptyList(),
            dosingLogs = emptyList(),
            parameterLogs = emptyList(),
            timelineEvents = emptyList(),
            zoneId = ZoneOffset.UTC
        )

        assertTrue(state.isNotFound)
        assertEquals("This deep link is missing entity details.", state.headline)
        assertEquals("Entity", state.kindLabel)
    }

    @Test
    fun `task detail includes completion and linked-event summary`() {
        val aquarium = Aquarium(
            id = "a-display",
            name = "Display",
            volumeLiters = 180.0,
            waterType = WaterType.FRESHWATER
        )
        val task = TaskTemplate(
            id = "task-trim",
            title = "Trim stems",
            category = TaskCategory.MAINTENANCE,
            frequency = TaskFrequency.WEEKLY,
            aquariumIds = listOf(aquarium.id),
            startDate = "2026-04-01",
            timesPerDay = 1
        )
        val executions = listOf(
            TaskExecution(
                id = "exec-old",
                taskTemplateId = task.id,
                aquariumId = aquarium.id,
                completedAt = "2026-04-10T08:00:00Z"
            ),
            TaskExecution(
                id = "exec-new",
                taskTemplateId = task.id,
                aquariumId = aquarium.id,
                completedAt = "2026-04-11T09:30:00Z"
            )
        )
        val timelineEvents = listOf(
            TimelineEvent(
                id = "event-1",
                aquariumId = aquarium.id,
                type = TimelineEventType.TASK,
                createdAt = "2026-04-11T10:00:00Z",
                title = "Trim complete",
                source = EntityRef(EntityKind.TASK, task.id, aquarium.id)
            ),
            TimelineEvent(
                id = "event-2",
                aquariumId = aquarium.id,
                type = TimelineEventType.MEMO,
                createdAt = "2026-04-11T11:00:00Z",
                title = "Unrelated memo"
            )
        )

        val state = assembleEntityDetailUiState(
            kind = EntityKind.TASK,
            entityId = task.id,
            routeAquariumId = aquarium.id,
            aquariums = listOf(aquarium),
            taskTemplates = listOf(task),
            taskExecutions = executions,
            livestock = emptyList(),
            assets = emptyList(),
            consumables = emptyList(),
            issues = emptyList(),
            memos = emptyList(),
            dosingLogs = emptyList(),
            parameterLogs = emptyList(),
            timelineEvents = timelineEvents,
            zoneId = ZoneOffset.UTC
        )

        val metricByLabel = state.metrics.associate { it.label to it.value }
        val fieldByLabel = state.fields.associate { it.label to it.value }

        assertEquals("Task details", state.headline)
        assertEquals("Trim stems", state.title)
        assertEquals("Display", state.aquariumName)
        assertEquals("2", metricByLabel["Completions"])
        assertEquals("1", metricByLabel["Assigned tanks"])
        assertEquals("1", metricByLabel["Linked events"])
        assertEquals("Maintenance", fieldByLabel["Category"])
        assertEquals("Weekly", fieldByLabel["Frequency"])
        assertEquals("2026-04-11 09:30", fieldByLabel["Latest completion"])
        assertEquals(1, state.relatedEvents.size)
    }

    @Test
    fun `aquarium detail surfaces aggregate metrics`() {
        val aquarium = Aquarium(
            id = "a-community",
            name = "Community",
            volumeLiters = 240.0,
            waterType = WaterType.FRESHWATER,
            setupDate = "2026-01-10"
        )
        val residents = listOf(
            Livestock(id = "l-1", aquariumId = aquarium.id, name = "Ember"),
            Livestock(id = "l-2", aquariumId = aquarium.id, name = "Scout")
        )
        val tasks = listOf(
            TaskTemplate(
                id = "task-feed",
                title = "Feed fish",
                frequency = TaskFrequency.DAILY,
                aquariumIds = listOf(aquarium.id)
            )
        )
        val issues = listOf(
            Issue(
                id = "issue-open",
                aquariumId = aquarium.id,
                title = "Cloudy water",
                status = IssueStatus.OPEN,
                createdAt = "2026-04-11T08:00:00Z"
            ),
            Issue(
                id = "issue-closed",
                aquariumId = aquarium.id,
                title = "Old algae bloom",
                status = IssueStatus.RESOLVED,
                createdAt = "2026-03-01T08:00:00Z"
            )
        )
        val events = listOf(
            TimelineEvent(
                id = "event-a",
                aquariumId = aquarium.id,
                type = TimelineEventType.MEMO,
                createdAt = "2026-04-11T12:00:00Z",
                title = "Observation"
            )
        )

        val state = assembleEntityDetailUiState(
            kind = EntityKind.AQUARIUM,
            entityId = aquarium.id,
            routeAquariumId = null,
            aquariums = listOf(aquarium),
            taskTemplates = tasks,
            taskExecutions = emptyList(),
            livestock = residents,
            assets = emptyList(),
            consumables = emptyList(),
            issues = issues,
            memos = emptyList(),
            dosingLogs = emptyList(),
            parameterLogs = emptyList(),
            timelineEvents = events,
            zoneId = ZoneOffset.UTC
        )

        val metricByLabel = state.metrics.associate { it.label to it.value }
        val fieldByLabel = state.fields.associate { it.label to it.value }

        assertEquals("Community", state.title)
        assertEquals("Freshwater", state.subtitle)
        assertEquals("2", metricByLabel["Residents"])
        assertEquals("1", metricByLabel["Tasks"])
        assertEquals("1", metricByLabel["Open issues"])
        assertEquals("1", metricByLabel["Linked events"])
        assertEquals("240 L", fieldByLabel["Volume"])
        assertEquals("2026-01-10", fieldByLabel["Setup date"])
    }

    @Test
    fun `missing entity kind returns not found state with tank context`() {
        val aquarium = Aquarium(
            id = "a-quarantine",
            name = "Quarantine",
            volumeLiters = 30.0,
            waterType = WaterType.FRESHWATER
        )

        val state = assembleEntityDetailUiState(
            kind = EntityKind.ISSUE,
            entityId = "issue-missing",
            routeAquariumId = aquarium.id,
            aquariums = listOf(aquarium),
            taskTemplates = emptyList(),
            taskExecutions = emptyList(),
            livestock = emptyList(),
            assets = emptyList(),
            consumables = emptyList(),
            issues = emptyList(),
            memos = emptyList(),
            dosingLogs = emptyList(),
            parameterLogs = emptyList(),
            timelineEvents = emptyList(),
            zoneId = ZoneOffset.UTC
        )

        assertTrue(state.isNotFound)
        assertEquals("Issue not found.", state.headline)
        assertEquals("Quarantine", state.aquariumName)
        assertNull(state.subtitle)
    }
}