package com.keepaside.aquapt.feature.assistant

import com.keepaside.aquapt.core.assistant.AssistantActionReviewService
import com.keepaside.aquapt.core.assistant.AssistantDictationController
import com.keepaside.aquapt.core.assistant.AssistantGateway
import com.keepaside.aquapt.core.assistant.AssistantGatewayResponse
import com.keepaside.aquapt.core.assistant.AssistantGatewayTelemetry
import com.keepaside.aquapt.core.assistant.AssistantGatewayUsage
import com.keepaside.aquapt.core.assistant.AssistantGatewayRequest
import com.keepaside.aquapt.core.model.AssistantActionExecutionResult
import com.keepaside.aquapt.core.model.AssistantActionExecutionItemResult
import com.keepaside.aquapt.core.model.AssistantActionExtractionResult
import com.keepaside.aquapt.core.model.AssistantActionTypes
import com.keepaside.aquapt.core.model.AssistantDetectedAction
import com.keepaside.aquapt.core.model.AssistantMemoryCompactionPreview
import com.keepaside.aquapt.core.model.AssistantMemorySnippet
import com.keepaside.aquapt.core.model.AppSettings
import com.keepaside.aquapt.core.model.AssistantConversation
import com.keepaside.aquapt.core.model.AssistantChatMessage
import com.keepaside.aquapt.core.model.AssistantMessageRole
import com.keepaside.aquapt.core.repository.AssistantConversationsStore
import com.keepaside.aquapt.core.repository.AssistantMemoryStore
import com.keepaside.aquapt.core.repository.AppSettingsStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class AssistantViewModelTest {

    @Test
    fun `bootstrap creates initial conversation when store is empty`() = runTest {
        val store = FakeAssistantConversationsStore()
        val settingsStore = FakeAppSettingsStore()
        val gateway = FakeAssistantGateway()
        val actionReviewService = FakeAssistantActionReviewService()
        val viewModel = AssistantViewModel(
            assistantConversationsStore = store,
            appSettingsStore = settingsStore,
            assistantGateway = gateway,
            assistantActionReviewService = actionReviewService,
            externalScope = this,
            nowProvider = { Instant.parse("2026-04-11T10:00:00Z") },
            idProvider = { prefix -> "$prefix-1" }
        )

        try {
            advanceUntilIdle()
            val state = viewModel.uiState.value

            assertFalse(state.isLoading)
            assertEquals(1, state.conversationItems.size)
            assertNotNull(state.activeConversationId)
            assertEquals("New Chat", state.activeConversationTitle)
            assertEquals(1, store.conversations.value.size)
        } finally {
            viewModel.disposeForTests()
        }
    }

    @Test
    fun `send message streams assistant reply and appends messages`() = runTest {
        val store = FakeAssistantConversationsStore()
        val settingsStore = FakeAppSettingsStore(
            AppSettings(
                openRouterApiKey = "test-key",
                aiModel = "openai/gpt-4o-mini"
            )
        )
        val gateway = FakeAssistantGateway(
            snapshots = listOf("Echo", "Echo: done"),
            finalReply = "Echo: done"
        )
        val actionReviewService = FakeAssistantActionReviewService()
        val viewModel = AssistantViewModel(
            assistantConversationsStore = store,
            appSettingsStore = settingsStore,
            assistantGateway = gateway,
            assistantActionReviewService = actionReviewService,
            externalScope = this,
            nowProvider = { Instant.parse("2026-04-11T11:00:00Z") },
            idProvider = { prefix -> "$prefix-${store.nextId()}" }
        )

        try {
            advanceUntilIdle()

            viewModel.onComposerTextChanged("Check nitrate trend for display tank")
            viewModel.sendMessage()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("", state.composerText)
            assertEquals(2, state.messages.size)
            assertEquals(AssistantMessageRole.USER, state.messages[0].role)
            assertEquals(AssistantMessageRole.ASSISTANT, state.messages[1].role)
            assertEquals("Echo: done", state.messages[1].content)
            assertTrue(state.activeConversationTitle.startsWith("Check nitrate trend"))
            assertEquals(1, gateway.requests.size)
        } finally {
            viewModel.disposeForTests()
        }
    }

    @Test
    fun `dictation appends transcript to composer and exits listening on final result`() = runTest {
        val store = FakeAssistantConversationsStore()
        val dictationController = FakeAssistantDictationController(isAvailable = true)
        val viewModel = AssistantViewModel(
            assistantConversationsStore = store,
            appSettingsStore = FakeAppSettingsStore(),
            assistantGateway = FakeAssistantGateway(),
            assistantActionReviewService = FakeAssistantActionReviewService(),
            assistantDictationController = dictationController,
            externalScope = this,
            nowProvider = { Instant.parse("2026-04-11T11:00:00Z") },
            idProvider = { prefix -> "$prefix-${store.nextId()}" }
        )

        try {
            advanceUntilIdle()

            viewModel.onComposerTextChanged("Review")
            viewModel.startDictation()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.isDictating)
            assertEquals("Listening… tap Stop when finished.", viewModel.uiState.value.statusMessage)

            dictationController.emitPartial("nitrate")
            advanceUntilIdle()
            assertEquals("Review nitrate", viewModel.uiState.value.composerText)

            dictationController.emitFinal("nitrate trends today")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isDictating)
            assertEquals("Review nitrate trends today", state.composerText)
            assertTrue(state.statusMessage.contains("Dictation captured"))
        } finally {
            viewModel.disposeForTests()
        }
    }

    @Test
    fun `dictation unavailable surfaces status feedback`() = runTest {
        val store = FakeAssistantConversationsStore()
        val dictationController = FakeAssistantDictationController(isAvailable = false)
        val viewModel = AssistantViewModel(
            assistantConversationsStore = store,
            appSettingsStore = FakeAppSettingsStore(),
            assistantGateway = FakeAssistantGateway(),
            assistantActionReviewService = FakeAssistantActionReviewService(),
            assistantDictationController = dictationController,
            externalScope = this,
            nowProvider = { Instant.parse("2026-04-11T11:05:00Z") },
            idProvider = { prefix -> "$prefix-${store.nextId()}" }
        )

        try {
            advanceUntilIdle()

            viewModel.startDictation()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isDictating)
            assertEquals("Dictation is unavailable on this device.", state.statusMessage)
        } finally {
            viewModel.disposeForTests()
        }
    }

    @Test
    fun `reuse message loads content into composer`() = runTest {
        val existing = listOf(
            AssistantConversation(
                id = "conv-reuse",
                title = "Reuse",
                messages = listOf(
                    AssistantChatMessage(
                        id = "msg-user",
                        role = AssistantMessageRole.USER,
                        content = "Original prompt",
                        createdAt = "2026-04-11T10:59:00Z"
                    ),
                    AssistantChatMessage(
                        id = "msg-assistant",
                        role = AssistantMessageRole.ASSISTANT,
                        content = "Reuse this answer",
                        createdAt = "2026-04-11T11:00:00Z"
                    )
                ),
                createdAt = "2026-04-11T10:59:00Z",
                updatedAt = "2026-04-11T11:00:00Z"
            )
        )

        val store = FakeAssistantConversationsStore(existing)
        val viewModel = AssistantViewModel(
            assistantConversationsStore = store,
            appSettingsStore = FakeAppSettingsStore(),
            assistantGateway = FakeAssistantGateway(),
            assistantActionReviewService = FakeAssistantActionReviewService(),
            externalScope = this,
            nowProvider = { Instant.parse("2026-04-11T11:01:00Z") },
            idProvider = { prefix -> "$prefix-${store.nextId()}" }
        )

        try {
            advanceUntilIdle()
            viewModel.selectConversation("conv-reuse")

            viewModel.reuseMessageAsPrompt("msg-assistant")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("Reuse this answer", state.composerText)
            assertTrue(state.canSend)
            assertEquals("Loaded assistant reply into the composer.", state.statusMessage)
        } finally {
            viewModel.disposeForTests()
        }
    }

    @Test
    fun `reuse message rejects empty content`() = runTest {
        val existing = listOf(
            AssistantConversation(
                id = "conv-empty-reuse",
                title = "Reuse empty",
                messages = listOf(
                    AssistantChatMessage(
                        id = "msg-empty",
                        role = AssistantMessageRole.ASSISTANT,
                        content = "   ",
                        createdAt = "2026-04-11T11:00:00Z"
                    )
                ),
                createdAt = "2026-04-11T11:00:00Z",
                updatedAt = "2026-04-11T11:00:00Z"
            )
        )

        val store = FakeAssistantConversationsStore(existing)
        val viewModel = AssistantViewModel(
            assistantConversationsStore = store,
            appSettingsStore = FakeAppSettingsStore(),
            assistantGateway = FakeAssistantGateway(),
            assistantActionReviewService = FakeAssistantActionReviewService(),
            externalScope = this,
            nowProvider = { Instant.parse("2026-04-11T11:02:00Z") },
            idProvider = { prefix -> "$prefix-${store.nextId()}" }
        )

        try {
            advanceUntilIdle()
            viewModel.selectConversation("conv-empty-reuse")

            viewModel.reuseMessageAsPrompt("msg-empty")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("", state.composerText)
            assertEquals("Cannot reuse an empty message.", state.statusMessage)
            assertFalse(state.canSend)
        } finally {
            viewModel.disposeForTests()
        }
    }

    @Test
    fun `conversation search filters by title and latest preview`() = runTest {
        val existing = listOf(
            AssistantConversation(
                id = "conv-water",
                title = "Water planning",
                messages = listOf(
                    AssistantChatMessage(
                        id = "msg-water-1",
                        role = AssistantMessageRole.USER,
                        content = "How often should I change water?",
                        createdAt = "2026-04-11T09:00:00Z"
                    )
                ),
                createdAt = "2026-04-11T09:00:00Z",
                updatedAt = "2026-04-11T09:00:00Z"
            ),
            AssistantConversation(
                id = "conv-feeding",
                title = "Feeding notes",
                messages = listOf(
                    AssistantChatMessage(
                        id = "msg-feed-1",
                        role = AssistantMessageRole.ASSISTANT,
                        content = "Nitrate alert trend looks stable.",
                        createdAt = "2026-04-11T10:00:00Z"
                    )
                ),
                createdAt = "2026-04-11T10:00:00Z",
                updatedAt = "2026-04-11T10:00:00Z"
            )
        )

        val store = FakeAssistantConversationsStore(existing)
        val viewModel = AssistantViewModel(
            assistantConversationsStore = store,
            appSettingsStore = FakeAppSettingsStore(),
            assistantGateway = FakeAssistantGateway(),
            assistantActionReviewService = FakeAssistantActionReviewService(),
            externalScope = this,
            nowProvider = { Instant.parse("2026-04-11T11:03:00Z") },
            idProvider = { prefix -> "$prefix-${store.nextId()}" }
        )

        try {
            advanceUntilIdle()

            viewModel.onConversationSearchQueryChanged("feeding")
            advanceUntilIdle()

            val byTitle = viewModel.uiState.value
            assertEquals(2, byTitle.totalConversationCount)
            assertEquals(1, byTitle.visibleConversationCount)
            assertEquals("conv-feeding", byTitle.conversationItems.first().id)

            viewModel.onConversationSearchQueryChanged("nitrate")
            advanceUntilIdle()

            val byPreview = viewModel.uiState.value
            assertEquals(1, byPreview.visibleConversationCount)
            assertEquals("conv-feeding", byPreview.conversationItems.first().id)
        } finally {
            viewModel.disposeForTests()
        }
    }

    @Test
    fun `clear conversation search restores full conversation list`() = runTest {
        val existing = listOf(
            AssistantConversation(
                id = "conv-a",
                title = "Alpha",
                createdAt = "2026-04-11T09:00:00Z",
                updatedAt = "2026-04-11T09:00:00Z"
            ),
            AssistantConversation(
                id = "conv-b",
                title = "Beta",
                createdAt = "2026-04-11T10:00:00Z",
                updatedAt = "2026-04-11T10:00:00Z"
            )
        )

        val store = FakeAssistantConversationsStore(existing)
        val viewModel = AssistantViewModel(
            assistantConversationsStore = store,
            appSettingsStore = FakeAppSettingsStore(),
            assistantGateway = FakeAssistantGateway(),
            assistantActionReviewService = FakeAssistantActionReviewService(),
            externalScope = this,
            nowProvider = { Instant.parse("2026-04-11T11:04:00Z") },
            idProvider = { prefix -> "$prefix-${store.nextId()}" }
        )

        try {
            advanceUntilIdle()

            viewModel.onConversationSearchQueryChanged("alpha")
            advanceUntilIdle()
            assertEquals(1, viewModel.uiState.value.visibleConversationCount)

            viewModel.clearConversationSearchQuery()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("", state.conversationSearchQuery)
            assertEquals(2, state.visibleConversationCount)
            assertEquals(2, state.conversationItems.size)
        } finally {
            viewModel.disposeForTests()
        }
    }

    @Test
    fun `send message attaches assistant telemetry metadata`() = runTest {
        val store = FakeAssistantConversationsStore()
        val settingsStore = FakeAppSettingsStore(
            AppSettings(
                openRouterApiKey = "test-key",
                aiModel = "openai/gpt-4o-mini"
            )
        )
        val gateway = FakeAssistantGateway(
            snapshots = listOf("Telemetry reply"),
            finalReply = "Telemetry reply",
            telemetry = AssistantGatewayTelemetry(
                generationId = "gen-123",
                providerName = "OpenAI",
                router = "openrouter",
                model = "openai/gpt-4o-mini",
                usage = AssistantGatewayUsage(
                    promptTokens = 123,
                    completionTokens = 45,
                    totalTokens = 168,
                    cost = 0.0012
                ),
                latencyMs = 320,
                generationTimeMs = 180,
                finishReason = "stop",
                nativeFinishReason = "stop",
                streamed = true
            )
        )
        val actionReviewService = FakeAssistantActionReviewService()
        val viewModel = AssistantViewModel(
            assistantConversationsStore = store,
            appSettingsStore = settingsStore,
            assistantGateway = gateway,
            assistantActionReviewService = actionReviewService,
            externalScope = this,
            nowProvider = { Instant.parse("2026-04-11T11:02:00Z") },
            idProvider = { prefix -> "$prefix-${store.nextId()}" }
        )

        try {
            advanceUntilIdle()

            viewModel.onComposerTextChanged("Give me latest telemetry")
            viewModel.sendMessage()
            advanceUntilIdle()

            val assistantMessage = viewModel.uiState.value.messages
                .last { it.role == AssistantMessageRole.ASSISTANT }

            assertEquals("gen-123", assistantMessage.responseTelemetry?.generationId)
            assertEquals("OpenAI", assistantMessage.responseTelemetry?.providerName)
            assertEquals("openrouter", assistantMessage.responseTelemetry?.router)
            assertEquals(168, assistantMessage.responseTelemetry?.totalTokens)
            assertEquals(320L, assistantMessage.responseTelemetry?.latencyMs)
            assertEquals("stop", assistantMessage.responseTelemetry?.finishReason)
            assertTrue((assistantMessage.responseTelemetry?.throughputCharsPerSecond ?: 0.0) >= 0.0)
        } finally {
            viewModel.disposeForTests()
        }
    }

    @Test
    fun `assistant memory controls remember and forget manual snippets`() = runTest {
        val store = FakeAssistantConversationsStore()
        val settingsStore = FakeAppSettingsStore(
            AppSettings(
                openRouterApiKey = "test-key",
                aiModel = "openai/gpt-4o-mini",
                assistantMemoryEnabled = true
            )
        )
        val memoryStore = FakeAssistantMemoryStore()
        val gateway = FakeAssistantGateway(
            snapshots = listOf("Memory reply"),
            finalReply = "Memory reply"
        )
        val actionReviewService = FakeAssistantActionReviewService()

        val viewModel = AssistantViewModel(
            assistantConversationsStore = store,
            appSettingsStore = settingsStore,
            assistantMemoryStore = memoryStore,
            assistantGateway = gateway,
            assistantActionReviewService = actionReviewService,
            externalScope = this,
            nowProvider = { Instant.parse("2026-04-11T11:03:00Z") },
            idProvider = { prefix -> "$prefix-${store.nextId()}" }
        )

        try {
            advanceUntilIdle()

            viewModel.onComposerTextChanged("Remember this answer")
            viewModel.sendMessage()
            advanceUntilIdle()

            val assistantMessage = viewModel.uiState.value.messages
                .last { it.role == AssistantMessageRole.ASSISTANT }

            viewModel.rememberAssistantMessage(assistantMessage.id)
            advanceUntilIdle()

            assertTrue(
                viewModel.uiState.value.rememberedAssistantMessageIds.contains(assistantMessage.id)
            )

            viewModel.forgetAssistantMessageMemory(assistantMessage.id)
            advanceUntilIdle()

            assertFalse(
                viewModel.uiState.value.rememberedAssistantMessageIds.contains(assistantMessage.id)
            )
        } finally {
            viewModel.disposeForTests()
        }
    }

    @Test
    fun `memory snippets are injected into assistant request context`() = runTest {
        val store = FakeAssistantConversationsStore()
        val settingsStore = FakeAppSettingsStore(
            AppSettings(
                openRouterApiKey = "test-key",
                aiModel = "openai/gpt-4o-mini",
                assistantMemoryEnabled = true
            )
        )
        val memoryStore = FakeAssistantMemoryStore(
            initialSnippets = listOf(
                AssistantMemorySnippet(
                    id = "manual:conv:a:msg:b",
                    content = "User prefers Sunday maintenance windows.",
                    category = "manual",
                    sourceConversationId = "conv-a",
                    sourceMessageId = "msg-b",
                    createdAt = "2026-04-11T10:00:00Z"
                )
            )
        ).apply {
            queryResponse = initialSnippets
        }

        val gateway = FakeAssistantGateway(
            snapshots = listOf("Done"),
            finalReply = "Done"
        )
        val actionReviewService = FakeAssistantActionReviewService()

        val viewModel = AssistantViewModel(
            assistantConversationsStore = store,
            appSettingsStore = settingsStore,
            assistantMemoryStore = memoryStore,
            assistantGateway = gateway,
            assistantActionReviewService = actionReviewService,
            externalScope = this,
            nowProvider = { Instant.parse("2026-04-11T11:04:00Z") },
            idProvider = { prefix -> "$prefix-${store.nextId()}" }
        )

        try {
            advanceUntilIdle()

            viewModel.onComposerTextChanged("What should I do this weekend?")
            viewModel.sendMessage()
            advanceUntilIdle()

            val request = gateway.requests.first()
            assertTrue(
                request.messages.any { message ->
                    message.role == AssistantMessageRole.SYSTEM &&
                        message.content.contains("Long-term memory snippets")
                }
            )
        } finally {
            viewModel.disposeForTests()
        }
    }

    @Test
    fun `memory compaction preview updates assistant ui state`() = runTest {
        val store = FakeAssistantConversationsStore()
        val settingsStore = FakeAppSettingsStore(
            AppSettings(
                assistantMemoryEnabled = true
            )
        )
        val memoryStore = FakeAssistantMemoryStore(
            initialSnippets = listOf(
                AssistantMemorySnippet(
                    id = "snippet-1",
                    content = "Memory facts: User prefers evening reminders.",
                    category = "conversation_turn",
                    createdAt = "2026-04-11T10:00:00Z"
                ),
                AssistantMemorySnippet(
                    id = "snippet-2",
                    content = "Monitor nitrate trend weekly.",
                    category = "conversation_turn",
                    createdAt = "2026-04-11T10:10:00Z"
                )
            )
        ).apply {
            preview = AssistantMemoryCompactionPreview(
                beforeCount = 2,
                afterCount = 1,
                facts = listOf("User prefers evening reminders and weekly nitrate checks.")
            )
        }

        val viewModel = AssistantViewModel(
            assistantConversationsStore = store,
            appSettingsStore = settingsStore,
            assistantMemoryStore = memoryStore,
            assistantGateway = FakeAssistantGateway(),
            assistantActionReviewService = FakeAssistantActionReviewService(),
            externalScope = this,
            nowProvider = { Instant.parse("2026-04-11T11:05:00Z") },
            idProvider = { prefix -> "$prefix-${store.nextId()}" }
        )

        try {
            advanceUntilIdle()

            viewModel.previewMemoryCompaction()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(2, state.memoryCompactionBeforeCount)
            assertEquals(1, state.memoryCompactionAfterCount)
            assertEquals(1, state.memoryCompactionFacts.size)
        } finally {
            viewModel.disposeForTests()
        }
    }

    @Test
    fun `apply memory compaction uses preview facts and refreshes snippet count`() = runTest {
        val store = FakeAssistantConversationsStore()
        val settingsStore = FakeAppSettingsStore(
            AppSettings(
                assistantMemoryEnabled = true
            )
        )
        val memoryStore = FakeAssistantMemoryStore(
            initialSnippets = listOf(
                AssistantMemorySnippet(
                    id = "snippet-a",
                    content = "User likes evening reminders.",
                    category = "conversation_turn",
                    createdAt = "2026-04-11T10:00:00Z"
                ),
                AssistantMemorySnippet(
                    id = "snippet-b",
                    content = "Weekly nitrate checks are preferred.",
                    category = "conversation_turn",
                    createdAt = "2026-04-11T10:05:00Z"
                )
            )
        ).apply {
            preview = AssistantMemoryCompactionPreview(
                beforeCount = 2,
                afterCount = 1,
                facts = listOf("Prefers evening reminders with weekly nitrate checks.")
            )
        }

        val viewModel = AssistantViewModel(
            assistantConversationsStore = store,
            appSettingsStore = settingsStore,
            assistantMemoryStore = memoryStore,
            assistantGateway = FakeAssistantGateway(),
            assistantActionReviewService = FakeAssistantActionReviewService(),
            externalScope = this,
            nowProvider = { Instant.parse("2026-04-11T11:06:00Z") },
            idProvider = { prefix -> "$prefix-${store.nextId()}" }
        )

        try {
            advanceUntilIdle()

            viewModel.previewMemoryCompaction()
            advanceUntilIdle()
            viewModel.applyMemoryCompaction()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(1, memoryStore.applyCalls)
            assertEquals(
                listOf("Prefers evening reminders with weekly nitrate checks."),
                memoryStore.lastAppliedFacts
            )
            assertEquals(1, state.assistantMemorySnippetCount)
            assertFalse(state.isApplyingMemoryCompaction)
            assertTrue(state.statusMessage.contains("Applied memory compaction"))
        } finally {
            viewModel.disposeForTests()
        }
    }

    @Test
    fun `dismiss memory compaction preview clears preview fields`() = runTest {
        val store = FakeAssistantConversationsStore()
        val settingsStore = FakeAppSettingsStore(
            AppSettings(
                assistantMemoryEnabled = true
            )
        )
        val memoryStore = FakeAssistantMemoryStore().apply {
            preview = AssistantMemoryCompactionPreview(
                beforeCount = 3,
                afterCount = 2,
                facts = listOf("Fact A", "Fact B")
            )
        }

        val viewModel = AssistantViewModel(
            assistantConversationsStore = store,
            appSettingsStore = settingsStore,
            assistantMemoryStore = memoryStore,
            assistantGateway = FakeAssistantGateway(),
            assistantActionReviewService = FakeAssistantActionReviewService(),
            externalScope = this,
            nowProvider = { Instant.parse("2026-04-11T11:07:00Z") },
            idProvider = { prefix -> "$prefix-${store.nextId()}" }
        )

        try {
            advanceUntilIdle()

            viewModel.previewMemoryCompaction()
            advanceUntilIdle()
            viewModel.dismissMemoryCompactionPreview()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(null, state.memoryCompactionBeforeCount)
            assertEquals(null, state.memoryCompactionAfterCount)
            assertTrue(state.memoryCompactionFacts.isEmpty())
            assertFalse(state.canApplyMemoryCompaction)
        } finally {
            viewModel.disposeForTests()
        }
    }

    @Test
    fun `missing credentials block send and surface assistant error`() = runTest {
        val store = FakeAssistantConversationsStore()
        val settingsStore = FakeAppSettingsStore(AppSettings())
        val gateway = FakeAssistantGateway()
        val actionReviewService = FakeAssistantActionReviewService()
        val viewModel = AssistantViewModel(
            assistantConversationsStore = store,
            appSettingsStore = settingsStore,
            assistantGateway = gateway,
            assistantActionReviewService = actionReviewService,
            externalScope = this,
            nowProvider = { Instant.parse("2026-04-11T11:10:00Z") },
            idProvider = { prefix -> "$prefix-${store.nextId()}" }
        )

        try {
            advanceUntilIdle()

            viewModel.onComposerTextChanged("Any update?")
            viewModel.sendMessage()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.assistantError?.contains("Missing OpenRouter") == true)
            assertEquals(0, gateway.requests.size)
            assertEquals(0, viewModel.uiState.value.messages.size)
        } finally {
            viewModel.disposeForTests()
        }
    }

    @Test
    fun `retry failed message clears failure and appends assistant reply`() = runTest {
        val failedUser = AssistantChatMessage(
            id = "msg-user",
            role = AssistantMessageRole.USER,
            content = "Retry this",
            createdAt = "2026-04-11T09:00:00Z",
            requestFailed = true,
            requestError = "Network issue"
        )
        val existing = listOf(
            AssistantConversation(
                id = "conv-retry",
                title = "Retry chat",
                messages = listOf(failedUser),
                createdAt = "2026-04-11T09:00:00Z",
                updatedAt = "2026-04-11T09:00:00Z"
            )
        )

        val store = FakeAssistantConversationsStore(existing)
        val settingsStore = FakeAppSettingsStore(
            AppSettings(openRouterApiKey = "key", aiModel = "openai/gpt-4o-mini")
        )
        val gateway = FakeAssistantGateway(
            snapshots = listOf("Retried"),
            finalReply = "Retried"
        )
        val actionReviewService = FakeAssistantActionReviewService()

        val viewModel = AssistantViewModel(
            assistantConversationsStore = store,
            appSettingsStore = settingsStore,
            assistantGateway = gateway,
            assistantActionReviewService = actionReviewService,
            externalScope = this,
            nowProvider = { Instant.parse("2026-04-11T11:15:00Z") },
            idProvider = { prefix -> "$prefix-${store.nextId()}" }
        )

        try {
            advanceUntilIdle()
            viewModel.selectConversation("conv-retry")

            viewModel.retryFailedMessage("msg-user")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(2, state.messages.size)
            assertEquals(false, state.messages[0].requestFailed)
            assertEquals(null, state.messages[0].requestError)
            assertEquals(AssistantMessageRole.ASSISTANT, state.messages[1].role)
            assertEquals("Retried", state.messages[1].content)
        } finally {
            viewModel.disposeForTests()
        }
    }

    @Test
    fun `regenerate replaces assistant reply for previous user prompt`() = runTest {
        val existing = listOf(
            AssistantConversation(
                id = "conv-regenerate",
                title = "Regenerate chat",
                messages = listOf(
                    AssistantChatMessage(
                        id = "msg-user",
                        role = AssistantMessageRole.USER,
                        content = "Original question",
                        createdAt = "2026-04-11T09:00:00Z"
                    ),
                    AssistantChatMessage(
                        id = "msg-assistant-old",
                        role = AssistantMessageRole.ASSISTANT,
                        content = "Old answer",
                        createdAt = "2026-04-11T09:00:10Z"
                    )
                ),
                createdAt = "2026-04-11T09:00:00Z",
                updatedAt = "2026-04-11T09:00:10Z"
            )
        )

        val store = FakeAssistantConversationsStore(existing)
        val settingsStore = FakeAppSettingsStore(
            AppSettings(openRouterApiKey = "key", aiModel = "openai/gpt-4o-mini")
        )
        val gateway = FakeAssistantGateway(
            snapshots = listOf("New answer"),
            finalReply = "New answer"
        )
        val actionReviewService = FakeAssistantActionReviewService()

        val viewModel = AssistantViewModel(
            assistantConversationsStore = store,
            appSettingsStore = settingsStore,
            assistantGateway = gateway,
            assistantActionReviewService = actionReviewService,
            externalScope = this,
            nowProvider = { Instant.parse("2026-04-11T11:20:00Z") },
            idProvider = { prefix -> "$prefix-${store.nextId()}" }
        )

        try {
            advanceUntilIdle()
            viewModel.selectConversation("conv-regenerate")

            viewModel.regenerateReply("msg-assistant-old")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(2, state.messages.size)
            assertEquals("msg-user", state.messages[0].id)
            assertEquals(AssistantMessageRole.ASSISTANT, state.messages[1].role)
            assertEquals("New answer", state.messages[1].content)
            assertFalse(state.messages.any { it.id == "msg-assistant-old" })
        } finally {
            viewModel.disposeForTests()
        }
    }

    @Test
    fun `pinning conversation reorders list and delete switches active`() = runTest {
        val existing = listOf(
            AssistantConversation(
                id = "conv-a",
                title = "A",
                pinned = false,
                createdAt = "2026-04-10T00:00:00Z",
                updatedAt = "2026-04-10T00:00:00Z"
            ),
            AssistantConversation(
                id = "conv-b",
                title = "B",
                pinned = false,
                createdAt = "2026-04-11T00:00:00Z",
                updatedAt = "2026-04-11T00:00:00Z"
            )
        )
        val store = FakeAssistantConversationsStore(existing)
        val settingsStore = FakeAppSettingsStore(
            AppSettings(openRouterApiKey = "key", aiModel = "openai/gpt-4o-mini")
        )
        val gateway = FakeAssistantGateway()
        val actionReviewService = FakeAssistantActionReviewService()
        val viewModel = AssistantViewModel(
            assistantConversationsStore = store,
            appSettingsStore = settingsStore,
            assistantGateway = gateway,
            assistantActionReviewService = actionReviewService,
            externalScope = this,
            nowProvider = { Instant.parse("2026-04-11T12:00:00Z") },
            idProvider = { prefix -> "$prefix-1" }
        )

        try {
            advanceUntilIdle()

            viewModel.selectConversation("conv-a")
            viewModel.togglePinConversation("conv-a")
            advanceUntilIdle()

            val afterPin = viewModel.uiState.value
            assertEquals("conv-a", afterPin.conversationItems.first().id)
            assertTrue(afterPin.conversationItems.first().isPinned)

            viewModel.deleteConversation("conv-a")
            advanceUntilIdle()

            val afterDelete = viewModel.uiState.value
            assertEquals(1, afterDelete.conversationItems.size)
            assertEquals("conv-b", afterDelete.activeConversationId)
        } finally {
            viewModel.disposeForTests()
        }
    }

    @Test
    fun `blank rename is rejected`() = runTest {
        val store = FakeAssistantConversationsStore()
        val settingsStore = FakeAppSettingsStore()
        val gateway = FakeAssistantGateway()
        val actionReviewService = FakeAssistantActionReviewService()
        val viewModel = AssistantViewModel(
            assistantConversationsStore = store,
            appSettingsStore = settingsStore,
            assistantGateway = gateway,
            assistantActionReviewService = actionReviewService,
            externalScope = this,
            nowProvider = { Instant.parse("2026-04-11T13:00:00Z") },
            idProvider = { prefix -> "$prefix-1" }
        )

        try {
            advanceUntilIdle()
            val activeId = viewModel.uiState.value.activeConversationId ?: ""

            viewModel.renameConversation(activeId, "   ")
            advanceUntilIdle()

            assertEquals(
                "Conversation title cannot be empty.",
                viewModel.uiState.value.statusMessage
            )
        } finally {
            viewModel.disposeForTests()
        }
    }

    @Test
    fun `assistant reply with extracted actions updates action review state`() = runTest {
        val store = FakeAssistantConversationsStore()
        val settingsStore = FakeAppSettingsStore(
            AppSettings(openRouterApiKey = "key", aiModel = "openai/gpt-4o-mini")
        )
        val gateway = FakeAssistantGateway(
            snapshots = listOf("done"),
            finalReply = "done"
        )
        val actionReviewService = FakeAssistantActionReviewService().apply {
            parseResponder = { _, _, sourceMessageId ->
                AssistantActionExtractionResult(
                    actions = listOf(
                        AssistantDetectedAction(
                            id = "action-1",
                            type = AssistantActionTypes.CREATE_TASK_TEMPLATE,
                            title = "Weekly water change",
                            frequency = "weekly",
                            validationErrors = emptyList(),
                            sourceMessageId = sourceMessageId
                        )
                    ),
                    warnings = emptyList(),
                    raw = "done"
                )
            }
        }

        val viewModel = AssistantViewModel(
            assistantConversationsStore = store,
            appSettingsStore = settingsStore,
            assistantGateway = gateway,
            assistantActionReviewService = actionReviewService,
            externalScope = this,
            nowProvider = { Instant.parse("2026-04-11T13:10:00Z") },
            idProvider = { prefix -> "$prefix-${store.nextId()}" }
        )

        try {
            advanceUntilIdle()

            viewModel.onComposerTextChanged("Set up a weekly water change task")
            viewModel.sendMessage()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state.hasDetectedActions)
            assertEquals(1, state.detectedActions.size)
            assertEquals("Weekly water change", state.detectedActions[0].title)
            assertEquals(1, state.messages[1].detectedActionIds.size)
            assertEquals("action-1", state.messages[1].detectedActionIds[0])
        } finally {
            viewModel.disposeForTests()
        }
    }

    @Test
    fun `rich action type add aquarium is surfaced and approvable`() = runTest {
        val store = FakeAssistantConversationsStore()
        val settingsStore = FakeAppSettingsStore(
            AppSettings(openRouterApiKey = "key", aiModel = "openai/gpt-4o-mini")
        )
        val gateway = FakeAssistantGateway(
            snapshots = listOf("ok"),
            finalReply = "ok"
        )
        val actionReviewService = FakeAssistantActionReviewService().apply {
            parseResponder = { _, _, sourceMessageId ->
                AssistantActionExtractionResult(
                    actions = listOf(
                        AssistantDetectedAction(
                            id = "action-aquarium",
                            type = AssistantActionTypes.ADD_AQUARIUM,
                            title = "Nano Reef",
                            aquariumName = "Nano Reef",
                            validationErrors = emptyList(),
                            sourceMessageId = sourceMessageId
                        )
                    ),
                    warnings = emptyList(),
                    raw = "ok"
                )
            }
        }

        val viewModel = AssistantViewModel(
            assistantConversationsStore = store,
            appSettingsStore = settingsStore,
            assistantGateway = gateway,
            assistantActionReviewService = actionReviewService,
            externalScope = this,
            nowProvider = { Instant.parse("2026-04-11T13:20:00Z") },
            idProvider = { prefix -> "$prefix-${store.nextId()}" }
        )

        try {
            advanceUntilIdle()

            viewModel.onComposerTextChanged("Add my nano reef tank")
            viewModel.sendMessage()
            advanceUntilIdle()

            val initial = viewModel.uiState.value
            assertTrue(initial.hasDetectedActions)
            assertEquals("Nano Reef", initial.detectedActions.first().title)
            assertEquals(0, initial.approvedActionCount)

            viewModel.toggleActionApproval("action-aquarium", true)
            advanceUntilIdle()

            val afterApproval = viewModel.uiState.value
            assertEquals(1, afterApproval.approvedActionCount)
            assertTrue(afterApproval.canExecuteApprovedActions)
        } finally {
            viewModel.disposeForTests()
        }
    }

    @Test
    fun `execute approved actions runs review service and appends system summary`() = runTest {
        val existing = listOf(
            AssistantConversation(
                id = "conv-actions",
                title = "Actions",
                messages = listOf(
                    AssistantChatMessage(
                        id = "msg-user",
                        role = AssistantMessageRole.USER,
                        content = "Create a task",
                        createdAt = "2026-04-11T14:00:00Z"
                    ),
                    AssistantChatMessage(
                        id = "msg-assistant",
                        role = AssistantMessageRole.ASSISTANT,
                        content = "I can do that.",
                        createdAt = "2026-04-11T14:00:10Z",
                        detectedActionIds = listOf("action-valid", "action-invalid")
                    )
                ),
                detectedActions = listOf(
                    AssistantDetectedAction(
                        id = "action-valid",
                        type = AssistantActionTypes.ADD_MEMO,
                        memoContent = "Top off reminder",
                        approved = true,
                        validationErrors = emptyList(),
                        sourceMessageId = "msg-assistant"
                    ),
                    AssistantDetectedAction(
                        id = "action-invalid",
                        type = AssistantActionTypes.LOG_DOSING,
                        product = "Calcium",
                        amountMl = -1.0,
                        approved = true,
                        validationErrors = listOf("Invalid amount"),
                        sourceMessageId = "msg-assistant"
                    )
                ),
                createdAt = "2026-04-11T14:00:00Z",
                updatedAt = "2026-04-11T14:00:10Z"
            )
        )

        val store = FakeAssistantConversationsStore(existing)
        val settingsStore = FakeAppSettingsStore()
        val gateway = FakeAssistantGateway()
        val actionReviewService = FakeAssistantActionReviewService().apply {
            executionResult = AssistantActionExecutionResult(
                createdCount = 1,
                skippedCount = 0,
                results = listOf(
                    AssistantActionExecutionItemResult(
                        actionId = "action-valid",
                        actionType = AssistantActionTypes.ADD_MEMO,
                        created = true,
                        summary = "Memo added"
                    )
                )
            )
        }

        val viewModel = AssistantViewModel(
            assistantConversationsStore = store,
            appSettingsStore = settingsStore,
            assistantGateway = gateway,
            assistantActionReviewService = actionReviewService,
            externalScope = this,
            nowProvider = { Instant.parse("2026-04-11T14:10:00Z") },
            idProvider = { prefix -> "$prefix-${store.nextId()}" }
        )

        try {
            advanceUntilIdle()
            viewModel.selectConversation("conv-actions")

            viewModel.executeApprovedActions()
            advanceUntilIdle()

            assertEquals(1, actionReviewService.executedActions.size)
            assertEquals("action-valid", actionReviewService.executedActions.first().id)

            val state = viewModel.uiState.value
            assertEquals(3, state.messages.size)
            assertEquals(AssistantMessageRole.SYSTEM, state.messages.last().role)
            assertTrue(state.messages.last().content.contains("Executed 1 action"))

            val validAction = state.detectedActions.first { it.id == "action-valid" }
            assertFalse(validAction.approved)
        } finally {
            viewModel.disposeForTests()
        }
    }
}

private class FakeAssistantGateway(
    private val snapshots: List<String> = listOf("ok"),
    private val finalReply: String = snapshots.lastOrNull().orEmpty(),
    private val telemetry: AssistantGatewayTelemetry? = null,
    private val error: Throwable? = null
) : AssistantGateway {
    val requests = mutableListOf<AssistantGatewayRequest>()

    override suspend fun requestStreamingReply(
        request: AssistantGatewayRequest,
        onSnapshot: suspend (String) -> Unit
    ): AssistantGatewayResponse {
        requests += request
        error?.let { throw it }
        snapshots.forEach { snapshot -> onSnapshot(snapshot) }
        return AssistantGatewayResponse(
            text = finalReply,
            telemetry = telemetry
        )
    }
}

private class FakeAssistantDictationController(
    override val isAvailable: Boolean
) : AssistantDictationController {
    private var onPartialTranscript: ((String) -> Unit)? = null
    private var onFinalTranscript: ((String) -> Unit)? = null
    private var onError: ((String) -> Unit)? = null

    override fun startListening(
        onPartialTranscript: (String) -> Unit,
        onFinalTranscript: (String) -> Unit,
        onError: (String) -> Unit
    ): Boolean {
        if (!isAvailable) {
            return false
        }

        this.onPartialTranscript = onPartialTranscript
        this.onFinalTranscript = onFinalTranscript
        this.onError = onError
        return true
    }

    override fun stopListening() = Unit

    override fun cancelListening() = Unit

    override fun release() {
        onPartialTranscript = null
        onFinalTranscript = null
        onError = null
    }

    fun emitPartial(value: String) {
        onPartialTranscript?.invoke(value)
    }

    fun emitFinal(value: String) {
        onFinalTranscript?.invoke(value)
    }

    fun emitError(value: String) {
        onError?.invoke(value)
    }
}

private class FakeAppSettingsStore(
    initial: AppSettings = AppSettings()
) : AppSettingsStore {
    private val flow = MutableStateFlow(initial)

    override val settings: StateFlow<AppSettings> = flow.asStateFlow()

    override suspend fun setSettings(settings: AppSettings) {
        flow.value = settings
    }
}

private class FakeAssistantMemoryStore(
    val initialSnippets: List<AssistantMemorySnippet> = emptyList()
) : AssistantMemoryStore {
    private val flow = MutableStateFlow(initialSnippets)

    override val snippets: StateFlow<List<AssistantMemorySnippet>> = flow.asStateFlow()

    var queryResponse: List<AssistantMemorySnippet> = emptyList()
    var preview: AssistantMemoryCompactionPreview = AssistantMemoryCompactionPreview(
        beforeCount = 0,
        afterCount = 0,
        facts = emptyList()
    )
    var applyCalls: Int = 0
    var lastAppliedFacts: List<String> = emptyList()

    override suspend fun rememberTurn(
        conversationId: String,
        userMessageId: String,
        userPrompt: String,
        assistantText: String
    ) {
        val snippetId = "conv:$conversationId:msg:$userMessageId"
        val snippet = AssistantMemorySnippet(
            id = snippetId,
            content = assistantText,
            category = "conversation_turn",
            createdAt = Instant.now().toString(),
            sourceConversationId = conversationId,
            sourceMessageId = userMessageId
        )
        flow.value = (flow.value.filterNot { it.id == snippetId } + snippet)
            .sortedByDescending { it.createdAt.orEmpty() }
    }

    override suspend fun rememberManualSnippet(
        conversationId: String,
        sourceMessageId: String,
        content: String
    ): String {
        val snippetId = "manual:conv:$conversationId:msg:$sourceMessageId"
        val snippet = AssistantMemorySnippet(
            id = snippetId,
            content = content,
            category = "manual",
            createdAt = Instant.now().toString(),
            sourceConversationId = conversationId,
            sourceMessageId = sourceMessageId
        )

        flow.value = (flow.value.filterNot { it.id == snippetId } + snippet)
            .sortedByDescending { it.createdAt.orEmpty() }

        return snippetId
    }

    override suspend fun forgetManualSnippet(
        conversationId: String,
        sourceMessageId: String
    ) {
        val snippetId = "manual:conv:$conversationId:msg:$sourceMessageId"
        flow.value = flow.value.filterNot { it.id == snippetId }
    }

    override suspend fun forgetSnippet(id: String) {
        flow.value = flow.value.filterNot { it.id == id }
    }

    override suspend fun queryRelevantSnippets(
        prompt: String,
        limit: Int
    ): List<AssistantMemorySnippet> {
        val source = queryResponse.ifEmpty { flow.value }
        return source.take(limit)
    }

    override suspend fun previewCompaction(maxFacts: Int): AssistantMemoryCompactionPreview =
        preview

    override suspend fun applyCompaction(
        precomputedFacts: List<String>,
        maxFacts: Int
    ): AssistantMemoryCompactionPreview {
        applyCalls += 1
        lastAppliedFacts = precomputedFacts

        val effectiveFacts = precomputedFacts
            .map { fact -> fact.trim() }
            .filter { fact -> fact.isNotEmpty() }
            .distinct()
            .take(maxFacts.coerceIn(1, 20))

        val result = AssistantMemoryCompactionPreview(
            beforeCount = flow.value.size,
            afterCount = effectiveFacts.size,
            facts = effectiveFacts
        )

        val now = Instant.now().toString()
        flow.value = effectiveFacts.mapIndexed { index, fact ->
            AssistantMemorySnippet(
                id = "compact-$index",
                content = fact,
                category = "compacted_fact",
                createdAt = now
            )
        }

        return result
    }
}

private class FakeAssistantActionReviewService : AssistantActionReviewService {
    var parseResponder: (String, String, String) -> AssistantActionExtractionResult = { _, _, _ ->
        AssistantActionExtractionResult(
            actions = emptyList(),
            warnings = emptyList(),
            raw = ""
        )
    }

    var executionResult: AssistantActionExecutionResult = AssistantActionExecutionResult(
        createdCount = 0,
        skippedCount = 0,
        results = emptyList()
    )

    var executedActions: List<AssistantDetectedAction> = emptyList()

    override fun parseAssistantActionExtraction(
        responseContent: String,
        transcript: String,
        sourceMessageId: String
    ): AssistantActionExtractionResult = parseResponder(responseContent, transcript, sourceMessageId)

    override suspend fun executeApprovedActions(
        actions: List<AssistantDetectedAction>
    ): AssistantActionExecutionResult {
        executedActions = actions
        return executionResult
    }
}

private class FakeAssistantConversationsStore(
    initial: List<AssistantConversation> = emptyList()
) : AssistantConversationsStore {
    private val flow = MutableStateFlow(initial)
    private var incrementingId = 100

    override val conversations: StateFlow<List<AssistantConversation>> = flow.asStateFlow()

    override suspend fun setConversations(conversations: List<AssistantConversation>) {
        flow.value = conversations
    }

    fun nextId(): Int {
        incrementingId += 1
        return incrementingId
    }
}