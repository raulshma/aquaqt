package com.keepaside.aquapt.feature.entity

import com.keepaside.aquapt.core.model.Aquarium
import com.keepaside.aquapt.core.model.ReminderGroup
import com.keepaside.aquapt.core.model.TaskCategory
import com.keepaside.aquapt.core.model.TaskExecution
import com.keepaside.aquapt.core.model.TaskFrequency
import com.keepaside.aquapt.core.model.TaskTemplate
import com.keepaside.aquapt.core.model.WaterType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class EntityEditViewModelTest {

    @Test
    fun `task template edit state assembles selected options`() {
        val template = TaskTemplate(
            id = "task-feed",
            title = "Feed fish",
            description = "Morning + evening",
            category = TaskCategory.FEEDING,
            frequency = TaskFrequency.DAILY,
            aquariumIds = listOf("a-1"),
            timesPerDay = 2,
            startDate = "2026-04-01",
            reminderHours = listOf(8, 19)
        )

        val state = assembleEntityEditUiState(
            kind = EntityEditKind.TASK_TEMPLATE,
            entityId = template.id,
            aquariums = listOf(
                Aquarium(
                    id = "a-2",
                    name = "Zulu",
                    volumeLiters = 120.0,
                    waterType = WaterType.FRESHWATER
                ),
                Aquarium(
                    id = "a-1",
                    name = "Alpha",
                    volumeLiters = 80.0,
                    waterType = WaterType.FRESHWATER
                )
            ),
            taskTemplates = listOf(template),
            taskExecutions = emptyList(),
            reminderGroups = listOf(
                ReminderGroup(
                    id = "rg-1",
                    name = "Morning",
                    hours = listOf(8)
                )
            ),
            editedTaskTemplateDraft = null,
            editedTaskExecutionDraft = null,
            isSaving = false,
            statusMessage = null,
            zoneId = ZoneOffset.UTC
        )

        assertFalse(state.isNotFound)
        assertEquals("Edit task template", state.headline)
        assertEquals("Feed fish", state.taskTemplateDraft?.title)
        assertEquals("2", state.taskTemplateDraft?.timesPerDay)
        assertEquals("8, 19", state.taskTemplateDraft?.reminderHours)
        assertEquals("a-1", state.aquariumOptions.first().id)
        assertTrue(state.aquariumOptions.first().isSelected)
        assertTrue(state.canSave)
    }

    @Test
    fun `task execution edit state resolves task and aquarium context`() {
        val state = assembleEntityEditUiState(
            kind = EntityEditKind.TASK_EXECUTION,
            entityId = "exec-1",
            aquariums = listOf(
                Aquarium(
                    id = "a-1",
                    name = "Display",
                    volumeLiters = 100.0,
                    waterType = WaterType.FRESHWATER
                )
            ),
            taskTemplates = listOf(
                TaskTemplate(
                    id = "task-1",
                    title = "Trim stems",
                    category = TaskCategory.MAINTENANCE,
                    frequency = TaskFrequency.WEEKLY,
                    aquariumIds = listOf("a-1")
                )
            ),
            taskExecutions = listOf(
                TaskExecution(
                    id = "exec-1",
                    taskTemplateId = "task-1",
                    aquariumId = "a-1",
                    completedAt = "2026-04-11T10:00:00Z",
                    note = "Done before lights out"
                )
            ),
            reminderGroups = emptyList(),
            editedTaskTemplateDraft = null,
            editedTaskExecutionDraft = null,
            isSaving = false,
            statusMessage = null,
            zoneId = ZoneOffset.UTC
        )

        assertFalse(state.isNotFound)
        assertEquals("Edit task execution", state.headline)
        assertEquals("Trim stems", state.taskExecutionDraft?.taskTitle)
        assertEquals("Display", state.taskExecutionDraft?.aquariumName)
        assertEquals("2026-04-11 10:00", state.taskExecutionDraft?.completedAtInput)
        assertTrue(state.canSave)
    }

    @Test
    fun `task template validation catches custom-day and reminder errors`() {
        val options = listOf(
            EntityEditAquariumOption(
                id = "a-1",
                name = "Display",
                isSelected = true
            )
        )

        val invalidCustomDays = validateEntityEditTaskTemplateDraft(
            draft = EntityEditTaskTemplateDraft(
                id = "task-1",
                title = "Dose trace",
                aquariumIds = setOf("a-1"),
                frequency = TaskFrequency.custom(1),
                customDays = "0"
            ),
            aquariumOptions = options,
            reminderGroupOptions = emptyList()
        )

        val invalidReminderHours = validateEntityEditTaskTemplateDraft(
            draft = EntityEditTaskTemplateDraft(
                id = "task-1",
                title = "Dose trace",
                aquariumIds = setOf("a-1"),
                frequency = TaskFrequency.WEEKLY,
                reminderHours = "8, 26"
            ),
            aquariumOptions = options,
            reminderGroupOptions = emptyList()
        )

        assertEquals("Custom frequency needs at least 1 day.", invalidCustomDays)
        assertEquals("Reminder hours must be between 0 and 23.", invalidReminderHours)
    }

    @Test
    fun `reminder hour parser normalizes distinct sorted values`() {
        val hours = parseEntityEditReminderHoursInput("18, 8; 8 21")

        assertEquals(listOf(8, 18, 21), hours)
    }

    @Test
    fun `entity edit date parser accepts friendly local input`() {
        val parsed = parseEntityEditDateTimeInput("2026-04-11 18:30", ZoneOffset.UTC)

        assertEquals(Instant.parse("2026-04-11T18:30:00Z"), parsed)
    }
}