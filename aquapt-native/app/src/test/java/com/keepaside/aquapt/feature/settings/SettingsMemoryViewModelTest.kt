package com.keepaside.aquapt.feature.settings

import com.keepaside.aquapt.core.model.AppSettings
import com.keepaside.aquapt.core.model.AssistantMemoryCompactionPreview
import com.keepaside.aquapt.core.model.AssistantMemorySnippet
import com.keepaside.aquapt.core.repository.AppSettingsStore
import com.keepaside.aquapt.core.repository.AssistantMemoryStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsMemoryViewModelTest {

    @Test
    fun `toggle memory enabled updates persisted settings`() = runTest {
        val appSettingsStore = FakeMemoryAppSettingsStore(
            AppSettings(assistantMemoryEnabled = false)
        )
        val memoryStore = FakeSettingsAssistantMemoryStore()

        val viewModel = SettingsMemoryViewModel(
            appSettingsStore = appSettingsStore,
            assistantMemoryStore = memoryStore,
            externalScope = this
        )

        try {
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.memoryEnabled)

            viewModel.setMemoryEnabled(true)
            advanceUntilIdle()

            assertTrue(appSettingsStore.settings.value.assistantMemoryEnabled)
            assertTrue(viewModel.uiState.value.memoryEnabled)
            assertEquals("Assistant memory enabled.", viewModel.uiState.value.statusMessage)
        } finally {
            viewModel.disposeForTests()
        }
    }

    @Test
    fun `forget snippet removes it from visible list`() = runTest {
        val appSettingsStore = FakeMemoryAppSettingsStore(
            AppSettings(assistantMemoryEnabled = true)
        )
        val memoryStore = FakeSettingsAssistantMemoryStore(
            initialSnippets = listOf(
                AssistantMemorySnippet(
                    id = "snippet-1",
                    content = "First snippet",
                    category = "manual",
                    createdAt = "2026-04-12T01:00:00Z"
                ),
                AssistantMemorySnippet(
                    id = "snippet-2",
                    content = "Second snippet",
                    category = "conversation_turn",
                    createdAt = "2026-04-12T02:00:00Z"
                )
            )
        )

        val viewModel = SettingsMemoryViewModel(
            appSettingsStore = appSettingsStore,
            assistantMemoryStore = memoryStore,
            externalScope = this
        )

        try {
            advanceUntilIdle()
            assertEquals(2, viewModel.uiState.value.snippets.size)

            viewModel.forgetSnippet("snippet-1")
            advanceUntilIdle()

            assertEquals(1, viewModel.uiState.value.snippets.size)
            assertEquals("snippet-2", viewModel.uiState.value.snippets.first().id)
            assertEquals(1, memoryStore.forgetCalls)
        } finally {
            viewModel.disposeForTests()
        }
    }

    @Test
    fun `clear all snippets empties memory list`() = runTest {
        val appSettingsStore = FakeMemoryAppSettingsStore(
            AppSettings(assistantMemoryEnabled = true)
        )
        val memoryStore = FakeSettingsAssistantMemoryStore(
            initialSnippets = listOf(
                AssistantMemorySnippet(
                    id = "snippet-1",
                    content = "Snippet one",
                    category = "manual",
                    createdAt = "2026-04-12T01:00:00Z"
                ),
                AssistantMemorySnippet(
                    id = "snippet-2",
                    content = "Snippet two",
                    category = "conversation_turn",
                    createdAt = "2026-04-12T02:00:00Z"
                )
            )
        )

        val viewModel = SettingsMemoryViewModel(
            appSettingsStore = appSettingsStore,
            assistantMemoryStore = memoryStore,
            externalScope = this
        )

        try {
            advanceUntilIdle()
            assertEquals(2, viewModel.uiState.value.snippets.size)

            viewModel.clearAllSnippets()
            advanceUntilIdle()

            assertEquals(0, viewModel.uiState.value.snippets.size)
            assertEquals(1, memoryStore.clearAllCalls)
            assertEquals("Assistant memory cleared.", viewModel.uiState.value.statusMessage)
        } finally {
            viewModel.disposeForTests()
        }
    }

    @Test
    fun `preview and apply compaction uses preview facts`() = runTest {
        val appSettingsStore = FakeMemoryAppSettingsStore(
            AppSettings(assistantMemoryEnabled = true)
        )
        val memoryStore = FakeSettingsAssistantMemoryStore(
            initialSnippets = listOf(
                AssistantMemorySnippet(
                    id = "snippet-1",
                    content = "Prefers weekly Sunday maintenance.",
                    category = "manual",
                    createdAt = "2026-04-12T01:00:00Z"
                ),
                AssistantMemorySnippet(
                    id = "snippet-2",
                    content = "Track nitrate trend every week.",
                    category = "conversation_turn",
                    createdAt = "2026-04-12T02:00:00Z"
                )
            ),
            preview = AssistantMemoryCompactionPreview(
                beforeCount = 2,
                afterCount = 1,
                facts = listOf("Weekly Sunday maintenance with nitrate trend checks.")
            )
        )

        val viewModel = SettingsMemoryViewModel(
            appSettingsStore = appSettingsStore,
            assistantMemoryStore = memoryStore,
            externalScope = this
        )

        try {
            advanceUntilIdle()

            viewModel.previewCompaction()
            advanceUntilIdle()

            val preview = viewModel.uiState.value.preview
            requireNotNull(preview)
            assertEquals(2, preview.beforeCount)
            assertEquals(1, preview.afterCount)
            assertEquals(1, preview.facts.size)

            viewModel.applyCompaction()
            advanceUntilIdle()

            assertEquals(1, memoryStore.applyCalls)
            assertEquals(
                listOf("Weekly Sunday maintenance with nitrate trend checks."),
                memoryStore.lastApplyPrecomputedFacts
            )
            assertTrue(viewModel.uiState.value.statusMessage.contains("Compacted"))
            assertEquals(1, viewModel.uiState.value.snippets.size)
        } finally {
            viewModel.disposeForTests()
        }
    }
}

private class FakeMemoryAppSettingsStore(
    initial: AppSettings = AppSettings()
) : AppSettingsStore {
    private val flow = MutableStateFlow(initial)

    override val settings: StateFlow<AppSettings> = flow.asStateFlow()

    override suspend fun setSettings(settings: AppSettings) {
        flow.value = settings
    }
}

private class FakeSettingsAssistantMemoryStore(
    initialSnippets: List<AssistantMemorySnippet> = emptyList(),
    private val preview: AssistantMemoryCompactionPreview = AssistantMemoryCompactionPreview(
        beforeCount = 0,
        afterCount = 0,
        facts = emptyList()
    )
) : AssistantMemoryStore {
    private val flow = MutableStateFlow(initialSnippets)

    override val snippets: StateFlow<List<AssistantMemorySnippet>> = flow.asStateFlow()

    var forgetCalls: Int = 0
    var clearAllCalls: Int = 0
    var applyCalls: Int = 0
    var lastApplyPrecomputedFacts: List<String> = emptyList()

    override suspend fun rememberTurn(
        conversationId: String,
        userMessageId: String,
        userPrompt: String,
        assistantText: String
    ) = Unit

    override suspend fun rememberManualSnippet(
        conversationId: String,
        sourceMessageId: String,
        content: String
    ): String? = null

    override suspend fun forgetManualSnippet(
        conversationId: String,
        sourceMessageId: String
    ) = Unit

    override suspend fun forgetSnippet(id: String) {
        forgetCalls += 1
        flow.value = flow.value.filterNot { snippet -> snippet.id == id }
    }

    override suspend fun clearAllSnippets() {
        clearAllCalls += 1
        flow.value = emptyList()
    }

    override suspend fun queryRelevantSnippets(
        prompt: String,
        limit: Int,
        conversationId: String?
    ): List<AssistantMemorySnippet> = flow.value.take(limit)

    override suspend fun previewCompaction(maxFacts: Int): AssistantMemoryCompactionPreview =
        preview

    override suspend fun applyCompaction(
        precomputedFacts: List<String>,
        maxFacts: Int
    ): AssistantMemoryCompactionPreview {
        applyCalls += 1
        lastApplyPrecomputedFacts = precomputedFacts

        val compactedFacts = precomputedFacts
            .map { fact -> fact.trim() }
            .filter { fact -> fact.isNotEmpty() }
            .distinct()
            .take(maxFacts.coerceIn(1, 20))

        val now = "2026-04-12T04:00:00Z"
        flow.value = compactedFacts.mapIndexed { index, fact ->
            AssistantMemorySnippet(
                id = "compact-$index",
                content = fact,
                category = "compacted_fact",
                createdAt = now
            )
        }

        return AssistantMemoryCompactionPreview(
            beforeCount = preview.beforeCount,
            afterCount = compactedFacts.size,
            facts = compactedFacts
        )
    }
}
