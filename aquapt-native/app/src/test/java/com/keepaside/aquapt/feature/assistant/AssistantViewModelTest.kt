package com.keepaside.aquapt.feature.assistant

import com.keepaside.aquapt.core.model.AssistantConversation
import com.keepaside.aquapt.core.model.AssistantMessageRole
import com.keepaside.aquapt.core.repository.AssistantConversationsStore
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
        val viewModel = AssistantViewModel(
            assistantConversationsStore = store,
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
    fun `send message appends user and assistant replies`() = runTest {
        val store = FakeAssistantConversationsStore()
        val viewModel = AssistantViewModel(
            assistantConversationsStore = store,
            externalScope = this,
            nowProvider = { Instant.parse("2026-04-11T11:00:00Z") },
            idProvider = { prefix -> "$prefix-${store.nextId()}" },
            replyBuilder = { prompt -> "Echo: $prompt" }
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
            assertTrue(state.messages[1].content.startsWith("Echo:"))
            assertTrue(state.activeConversationTitle.startsWith("Check nitrate trend"))
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
        val viewModel = AssistantViewModel(
            assistantConversationsStore = store,
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
        val viewModel = AssistantViewModel(
            assistantConversationsStore = store,
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