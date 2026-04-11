package com.keepaside.aquapt.feature.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.keepaside.aquapt.core.model.AssistantChatMessage
import com.keepaside.aquapt.core.model.AssistantConversation
import com.keepaside.aquapt.core.model.AssistantMessageRole
import com.keepaside.aquapt.core.repository.AssistantConversationsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

private const val assistantDefaultConversationTitle = "New Chat"
private const val assistantDefaultStatusMessage =
    "Ask about tanks, tasks, issues, and quick next steps."

data class AssistantConversationItem(
    val id: String,
    val title: String,
    val isPinned: Boolean,
    val updatedAtLabel: String,
    val messageCount: Int,
    val lastMessagePreview: String?
)

data class AssistantUiState(
    val isLoading: Boolean = true,
    val statusMessage: String = assistantDefaultStatusMessage,
    val conversationItems: List<AssistantConversationItem> = emptyList(),
    val activeConversationId: String? = null,
    val activeConversationTitle: String = assistantDefaultConversationTitle,
    val activeConversationPinned: Boolean = false,
    val messages: List<AssistantChatMessage> = emptyList(),
    val composerText: String = "",
    val canSend: Boolean = false
)

class AssistantViewModel(
    private val assistantConversationsStore: AssistantConversationsStore,
    private val externalScope: CoroutineScope? = null,
    private val nowProvider: () -> Instant = { Instant.now() },
    private val idProvider: (String) -> String = { prefix -> "$prefix-${UUID.randomUUID()}" },
    private val replyBuilder: (String) -> String = { prompt ->
        "Got it — I captured: $prompt"
    },
    private val zoneId: ZoneId = ZoneId.systemDefault()
) : ViewModel() {

    private val activeConversationId = MutableStateFlow<String?>(null)
    private val composerText = MutableStateFlow("")
    private val statusMessage = MutableStateFlow(assistantDefaultStatusMessage)

    private val _uiState = MutableStateFlow(AssistantUiState())
    val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()

    private var observerJob: Job? = null

    init {
        bootstrapConversationsIfNeeded()
        observerJob = observeUiState()
    }

    fun onComposerTextChanged(value: String) {
        composerText.update { value }
    }

    fun createConversation() {
        launchWork {
            val now = nowIso()
            val conversation = AssistantConversation(
                id = idProvider("conv"),
                title = assistantDefaultConversationTitle,
                pinned = false,
                messages = emptyList(),
                warnings = emptyList(),
                createdAt = now,
                updatedAt = now
            )

            val existing = assistantConversationsStore.conversations.value
            assistantConversationsStore.setConversations(
                listOf(conversation) + existing
            )
            activeConversationId.update { conversation.id }
            statusMessage.update { "Created a new conversation." }
        }
    }

    fun selectConversation(conversationId: String) {
        activeConversationId.update { conversationId }
        statusMessage.update { "Switched conversation." }
    }

    fun renameConversation(conversationId: String, title: String) {
        val normalizedTitle = title.trim()
        if (normalizedTitle.isBlank()) {
            statusMessage.update { "Conversation title cannot be empty." }
            return
        }

        launchWork {
            val updated = assistantConversationsStore.conversations.value.map { conversation ->
                if (conversation.id == conversationId) {
                    conversation.copy(
                        title = normalizedTitle,
                        updatedAt = nowIso()
                    )
                } else {
                    conversation
                }
            }

            assistantConversationsStore.setConversations(updated)
            statusMessage.update { "Conversation renamed." }
        }
    }

    fun togglePinConversation(conversationId: String) {
        launchWork {
            val updated = assistantConversationsStore.conversations.value.map { conversation ->
                if (conversation.id == conversationId) {
                    conversation.copy(
                        pinned = !conversation.pinned,
                        updatedAt = nowIso()
                    )
                } else {
                    conversation
                }
            }

            assistantConversationsStore.setConversations(updated)
            statusMessage.update { "Updated pin preference." }
        }
    }

    fun deleteConversation(conversationId: String) {
        launchWork {
            val remaining = assistantConversationsStore.conversations.value
                .filterNot { conversation -> conversation.id == conversationId }

            if (remaining.isEmpty()) {
                val now = nowIso()
                val replacement = AssistantConversation(
                    id = idProvider("conv"),
                    title = assistantDefaultConversationTitle,
                    pinned = false,
                    messages = emptyList(),
                    warnings = emptyList(),
                    createdAt = now,
                    updatedAt = now
                )
                assistantConversationsStore.setConversations(listOf(replacement))
                activeConversationId.update { replacement.id }
                statusMessage.update { "Deleted conversation and created a new one." }
                return@launchWork
            }

            assistantConversationsStore.setConversations(remaining)

            val activeId = activeConversationId.value
            if (activeId == conversationId) {
                activeConversationId.update { remaining.first().id }
            }

            statusMessage.update { "Conversation deleted." }
        }
    }

    fun sendMessage() {
        val prompt = composerText.value.trim()
        if (prompt.isBlank()) return

        launchWork {
            val conversations = assistantConversationsStore.conversations.value
            val resolvedActiveConversation = resolveActiveConversation(conversations)
            if (resolvedActiveConversation == null) {
                statusMessage.update { "No conversation available." }
                return@launchWork
            }

            val now = nowIso()
            val userMessage = AssistantChatMessage(
                id = idProvider("msg"),
                role = AssistantMessageRole.USER,
                content = prompt,
                createdAt = now
            )
            val assistantMessage = AssistantChatMessage(
                id = idProvider("msg"),
                role = AssistantMessageRole.ASSISTANT,
                content = replyBuilder(prompt).trim().ifBlank {
                    "I heard you. Native streaming responses are coming next."
                },
                createdAt = now
            )

            val updatedConversations = conversations.map { conversation ->
                if (conversation.id == resolvedActiveConversation.id) {
                    val firstUserPromptTitle =
                        conversation.title == assistantDefaultConversationTitle &&
                            conversation.messages.none { it.role == AssistantMessageRole.USER }

                    conversation.copy(
                        title = if (firstUserPromptTitle) {
                            prompt.take(40).ifBlank { assistantDefaultConversationTitle }
                        } else {
                            conversation.title
                        },
                        messages = conversation.messages + userMessage + assistantMessage,
                        updatedAt = now
                    )
                } else {
                    conversation
                }
            }

            assistantConversationsStore.setConversations(updatedConversations)
            composerText.update { "" }
            statusMessage.update { "Message sent." }
        }
    }

    private fun bootstrapConversationsIfNeeded() {
        launchWork {
            if (assistantConversationsStore.conversations.value.isNotEmpty()) {
                return@launchWork
            }

            val now = nowIso()
            val seed = AssistantConversation(
                id = idProvider("conv"),
                title = assistantDefaultConversationTitle,
                pinned = false,
                messages = emptyList(),
                warnings = emptyList(),
                createdAt = now,
                updatedAt = now
            )
            assistantConversationsStore.setConversations(listOf(seed))
            activeConversationId.update { seed.id }
        }
    }

    private fun observeUiState(): Job =
        launchWork {
            combine(
                assistantConversationsStore.conversations,
                activeConversationId,
                composerText,
                statusMessage
            ) { conversations, activeId, composer, status ->
                val sorted = conversations.sortedWith(
                    compareByDescending<AssistantConversation> { it.pinned }
                        .thenByDescending { it.updatedAt }
                )
                val activeConversation =
                    sorted.firstOrNull { it.id == activeId } ?: sorted.firstOrNull()

                val items = sorted.map { conversation ->
                    AssistantConversationItem(
                        id = conversation.id,
                        title = conversation.title,
                        isPinned = conversation.pinned,
                        updatedAtLabel = formatDateTime(conversation.updatedAt),
                        messageCount = conversation.messages.size,
                        lastMessagePreview = conversation.messages.lastOrNull()
                            ?.content
                            ?.trim()
                            ?.takeIf { it.isNotEmpty() }
                            ?.take(72)
                    )
                }

                AssistantUiState(
                    isLoading = false,
                    statusMessage = status,
                    conversationItems = items,
                    activeConversationId = activeConversation?.id,
                    activeConversationTitle = activeConversation?.title ?: assistantDefaultConversationTitle,
                    activeConversationPinned = activeConversation?.pinned ?: false,
                    messages = activeConversation?.messages.orEmpty(),
                    composerText = composer,
                    canSend = composer.trim().isNotEmpty() && activeConversation != null
                )
            }.collect { next ->
                _uiState.update { next }
                if (_uiState.value.activeConversationId != null) {
                    activeConversationId.update { it ?: _uiState.value.activeConversationId }
                }
            }
        }

    private fun resolveActiveConversation(
        conversations: List<AssistantConversation>
    ): AssistantConversation? {
        val current = conversations.firstOrNull { it.id == activeConversationId.value }
        return current ?: conversations.firstOrNull()
    }

    private fun nowIso(): String = nowProvider().toString()

    private fun formatDateTime(value: String): String {
        val instant = runCatching { Instant.parse(value) }.getOrNull() ?: return value
        return dateTimeFormatter.format(instant.atZone(zoneId))
    }

    private fun launchWork(block: suspend () -> Unit): Job =
        (externalScope ?: viewModelScope).launch {
            block()
        }

    internal fun disposeForTests() {
        observerJob?.cancel()
    }

    companion object {
        private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

        fun factory(
            assistantConversationsStore: AssistantConversationsStore
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(AssistantViewModel::class.java)) {
                        return AssistantViewModel(assistantConversationsStore) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
    }
}