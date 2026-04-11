package com.keepaside.aquapt.feature.assistant

import com.keepaside.aquapt.core.assistant.AssistantActionReviewService
import com.keepaside.aquapt.core.assistant.AssistantGateway
import com.keepaside.aquapt.core.assistant.AssistantGatewayRequest
import com.keepaside.aquapt.core.model.AssistantActionExecutionResult
import com.keepaside.aquapt.core.model.AssistantActionExecutionItemResult
import com.keepaside.aquapt.core.model.AssistantActionExtractionResult
import com.keepaside.aquapt.core.model.AssistantActionTypes
import com.keepaside.aquapt.core.model.AssistantDetectedAction
import com.keepaside.aquapt.core.model.AppSettings
import com.keepaside.aquapt.core.model.AssistantConversation
import com.keepaside.aquapt.core.model.AssistantChatMessage
import com.keepaside.aquapt.core.model.AssistantMessageRole
import com.keepaside.aquapt.core.repository.AssistantConversationsStore
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
    private val error: Throwable? = null
) : AssistantGateway {
    val requests = mutableListOf<AssistantGatewayRequest>()

    override suspend fun requestStreamingReply(
        request: AssistantGatewayRequest,
        onSnapshot: suspend (String) -> Unit
    ): String {
        requests += request
        error?.let { throw it }
        snapshots.forEach { snapshot -> onSnapshot(snapshot) }
        return finalReply
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