package com.keepaside.aquapt.core.backup

import com.keepaside.aquapt.core.model.AppThemePreference
import com.keepaside.aquapt.core.model.Aquarium
import com.keepaside.aquapt.core.model.EntityKind
import com.keepaside.aquapt.core.model.EntityRef
import com.keepaside.aquapt.core.model.TaskCategory
import com.keepaside.aquapt.core.model.TaskFrequency
import com.keepaside.aquapt.core.model.TaskFrequencyKind
import com.keepaside.aquapt.core.model.TaskTemplate
import com.keepaside.aquapt.core.model.TimelineEvent
import com.keepaside.aquapt.core.model.TimelineEventType
import com.keepaside.aquapt.core.model.WaterType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppStateJsonCompatibilityTest {

    @Test
    fun `decode handles legacy reminderHour and string enums`() {
        val payload =
            """
            {
              "aquariums": [{
                "id": "a-1",
                "name": "Main",
                "volumeLiters": 120,
                "dimensions": "90x45x45",
                "waterType": "brackish",
                "setupDate": "2025-01-01"
              }],
              "taskTemplates": [{
                "id": "t-1",
                "title": "Feed",
                "category": "feeding",
                "frequency": "custom-3",
                "aquariumIds": ["a-1"],
                "timesPerDay": 2
              }],
              "livestock": [],
              "taskExecutions": [],
              "dosingLogs": [],
              "assets": [],
              "consumables": [],
              "parameterLogs": [],
              "issues": [],
              "memos": [],
              "timeline": [],
              "reminderGroups": [],
              "settings": {
                "openRouterApiKey": "abc",
                "aiModel": "model-x",
                "reminderHour": 9,
                "themePreference": "dark"
              }
            }
            """.trimIndent()

        val snapshot = AppStateJsonCompatibility.decode(payload)

        assertEquals(1, snapshot.aquariums.size)
        assertEquals(WaterType.BRACKISH, snapshot.aquariums.first().waterType)
        assertEquals(1, snapshot.taskTemplates.size)
        assertEquals(TaskCategory.FEEDING, snapshot.taskTemplates.first().category)
        assertEquals(TaskFrequencyKind.CUSTOM, snapshot.taskTemplates.first().frequency.kind)
        assertEquals(3, snapshot.taskTemplates.first().frequency.customDays)
        assertEquals(listOf(9), snapshot.settings.reminderHours)
        assertEquals(AppThemePreference.DARK, snapshot.settings.themePreference)
    }

    @Test
    fun `encode and decode roundtrip keeps key fields`() {
        val original = PersistedAppStateSnapshot(
            aquariums = listOf(
                Aquarium(
                    id = "aq-1",
                    name = "Display",
                    volumeLiters = 240.0,
                    dimensions = "120x40x50",
                    waterType = WaterType.FRESHWATER,
                    setupDate = "2024-04-01"
                )
            ),
            taskTemplates = listOf(
                TaskTemplate(
                    id = "task-1",
                    title = "Dose fertilizer",
                    category = TaskCategory.MAINTENANCE,
                    frequency = TaskFrequency.custom(2),
                    aquariumIds = listOf("aq-1"),
                    reminderHours = listOf(8, 20)
                )
            ),
            timeline = listOf(
                TimelineEvent(
                    id = "ev-1",
                    aquariumId = "aq-1",
                    type = TimelineEventType.TASK,
                    createdAt = "2026-04-11T12:00:00Z",
                    title = "Done",
                    source = EntityRef(kind = EntityKind.TASK, id = "task-1", aquariumId = "aq-1")
                )
            )
        )

        val json = AppStateJsonCompatibility.encode(original, pretty = true)
        val decoded = AppStateJsonCompatibility.decode(json)

        assertTrue(json.contains("\"taskTemplates\""))
        assertEquals(1, decoded.aquariums.size)
        assertEquals("Display", decoded.aquariums.first().name)
        assertEquals(TaskFrequencyKind.CUSTOM, decoded.taskTemplates.first().frequency.kind)
        assertEquals(2, decoded.taskTemplates.first().frequency.customDays)
        assertEquals("task-1", decoded.timeline.first().source?.id)
        assertEquals(EntityKind.TASK, decoded.timeline.first().source?.kind)
    }
}
