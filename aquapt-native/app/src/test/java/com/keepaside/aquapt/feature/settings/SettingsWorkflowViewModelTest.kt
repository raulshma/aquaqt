package com.keepaside.aquapt.feature.settings

import com.keepaside.aquapt.core.assistant.AssistantGateway
import com.keepaside.aquapt.core.assistant.AssistantGatewayMessage
import com.keepaside.aquapt.core.assistant.AssistantGatewayRequest
import com.keepaside.aquapt.core.assistant.AssistantGatewayResponse
import com.keepaside.aquapt.core.model.AppThemePreference
import com.keepaside.aquapt.core.model.LivestockKind
import com.keepaside.aquapt.core.model.RegionalPreferencesMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsWorkflowViewModelTest {

    @Test
    fun `LivestockKind toWorkflowLabel returns readable names`() {
        assertEquals("Fish", LivestockKind.FISH.toWorkflowLabel())
        assertEquals("Shrimp", LivestockKind.SHRIMP.toWorkflowLabel())
        assertEquals("Snail", LivestockKind.SNAIL.toWorkflowLabel())
        assertEquals("Coral", LivestockKind.CORAL.toWorkflowLabel())
        assertEquals("Plant", LivestockKind.PLANT.toWorkflowLabel())
        assertEquals("Other", LivestockKind.OTHER.toWorkflowLabel())
    }

    @Test
    fun `mode prompts cover all workflow modes`() {
        assertTrue(modePrompts.containsKey(WorkflowAssistantMode.GENERAL))
        assertTrue(modePrompts.containsKey(WorkflowAssistantMode.DIAGNOSTIC))
        assertTrue(modePrompts.containsKey(WorkflowAssistantMode.COMPATIBILITY))
        assertTrue(modePrompts.containsKey(WorkflowAssistantMode.TASK_SUGGESTION))
        modePrompts.values.forEach { prompt ->
            assertTrue(prompt.isNotEmpty())
        }
    }

    @Test
    fun `question presets cover three expected categories`() {
        assertEquals(3, workflowQuestionPresets.size)
        assertTrue(workflowQuestionPresets.any { it.mode == WorkflowAssistantMode.DIAGNOSTIC })
        assertTrue(workflowQuestionPresets.any { it.mode == WorkflowAssistantMode.COMPATIBILITY })
        assertTrue(workflowQuestionPresets.any { it.mode == WorkflowAssistantMode.TASK_SUGGESTION })
        workflowQuestionPresets.forEach { preset ->
            assertTrue(preset.label.isNotEmpty())
            assertTrue(preset.question.isNotEmpty())
        }
    }

    @Test
    fun `WorkflowQA draft state management`() {
        var draft = WorkflowQADraft()
        assertEquals(WorkflowAssistantMode.GENERAL, draft.mode)
        assertEquals("", draft.question)

        draft = draft.copy(mode = WorkflowAssistantMode.DIAGNOSTIC, question = "Why are my shrimp struggling?")
        assertEquals(WorkflowAssistantMode.DIAGNOSTIC, draft.mode)
        assertEquals("Why are my shrimp struggling?", draft.question)
    }

    @Test
    fun `WorkflowDiagnostic draft defaults`() {
        val draft = WorkflowDiagnosticDraft()
        assertEquals("", draft.aquariumId)
        assertEquals("14", draft.windowDays)
        assertEquals("", draft.symptoms)
    }

    @Test
    fun `WorkflowCompatibility draft defaults`() {
        val draft = WorkflowCompatibilityDraft()
        assertEquals("", draft.aquariumId)
        assertEquals("", draft.species)
        assertEquals(LivestockKind.SHRIMP, draft.kind)
        assertEquals("1", draft.quantity)
        assertEquals("", draft.notes)
    }

    @Test
    fun `WorkflowAppContext serialization roundtrip`() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val context = WorkflowAppContext(
            aquariumSummary = listOf(
                WorkflowAquariumContext(
                    name = "Display",
                    waterType = "freshwater",
                    openIssues = listOf("Algae bloom")
                )
            ),
            userLocale = WorkflowUserLocale(
                locale = "en-US",
                timezone = "America/New_York",
                country = "United States",
                currency = "USD"
            ),
            livestockCount = 12,
            recentParameterLogCount = 30,
            openIssueCount = 2,
            taskTemplateCount = 5,
            recentTaskExecutionCount = 20
        )

        val encoded = json.encodeToString(WorkflowAppContext.serializer(), context)
        val decoded = json.decodeFromString(WorkflowAppContext.serializer(), encoded)

        assertEquals(1, decoded.aquariumSummary.size)
        assertEquals("Display", decoded.aquariumSummary[0].name)
        assertEquals("freshwater", decoded.aquariumSummary[0].waterType)
        assertEquals(listOf("Algae bloom"), decoded.aquariumSummary[0].openIssues)
        assertEquals("en-US", decoded.userLocale.locale)
        assertEquals("USD", decoded.userLocale.currency)
        assertEquals(12, decoded.livestockCount)
        assertEquals(2, decoded.openIssueCount)
        assertEquals(5, decoded.taskTemplateCount)
    }

    @Test
    fun `WorkflowAppContext empty serialization roundtrip`() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val context = WorkflowAppContext()

        val encoded = json.encodeToString(WorkflowAppContext.serializer(), context)
        val decoded = json.decodeFromString(WorkflowAppContext.serializer(), encoded)

        assertTrue(decoded.aquariumSummary.isEmpty())
        assertEquals(0, decoded.livestockCount)
        assertEquals(0, decoded.openIssueCount)
    }

    @Test
    fun `SettingsWorkflowUiState initial defaults`() {
        val state = SettingsWorkflowUiState()
        assertTrue(state.isLoading)
        assertFalse(state.isRequesting)
        assertEquals("", state.currentModel)
        assertFalse(state.hasApiKey)
        assertEquals("", state.qaAnswer)
        assertNull(state.qaError)
        assertEquals("", state.diagnosticAnswer)
        assertNull(state.diagnosticError)
        assertEquals("", state.compatibilityAnswer)
        assertNull(state.compatibilityError)
        assertTrue(state.aquariumOptions.isEmpty())
    }

    @Test
    fun `WorkflowAssistantMode enum covers all four modes`() {
        assertEquals(4, WorkflowAssistantMode.entries.size)
        assertEquals(WorkflowAssistantMode.GENERAL, WorkflowAssistantMode.valueOf("GENERAL"))
        assertEquals(WorkflowAssistantMode.DIAGNOSTIC, WorkflowAssistantMode.valueOf("DIAGNOSTIC"))
        assertEquals(WorkflowAssistantMode.COMPATIBILITY, WorkflowAssistantMode.valueOf("COMPATIBILITY"))
        assertEquals(WorkflowAssistantMode.TASK_SUGGESTION, WorkflowAssistantMode.valueOf("TASK_SUGGESTION"))
    }
}
