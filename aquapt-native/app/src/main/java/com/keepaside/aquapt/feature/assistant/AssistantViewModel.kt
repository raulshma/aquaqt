package com.keepaside.aquapt.feature.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.keepaside.aquapt.core.assistant.AssistantGateway
import com.keepaside.aquapt.core.assistant.AssistantGatewayMessage
import com.keepaside.aquapt.core.assistant.AssistantGatewayRequest
import com.keepaside.aquapt.core.model.AssistantChatMessage
import com.keepaside.aquapt.core.model.AssistantConversation
import com.keepaside.aquapt.core.model.AssistantMessageRole
import com.keepaside.aquapt.core.repository.AssistantConversationsStore
import com.keepaside.aquapt.core.repository.AppSettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

private const val assistantDefaultConversationTitle = "New Chat"
private const val assistantDefaultStatusMessage =
    "Ask about tanks, tasks, issues, and quick next steps."

private data class AssistantTransientState(
    val activeId: String?,
    val composer: String,
    val status: String,
    val error: String?,
    val sending: Boolean,
    val streamingId: String?
)

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
    val assistantError: String? = null,
    val conversationItems: List<AssistantConversationItem> = emptyList(),
    val activeConversationId: String? = null,
    val activeConversationTitle: String = assistantDefaultConversationTitle,
    val activeConversationPinned: Boolean = false,
    val messages: List<AssistantChatMessage> = emptyList(),
    val composerText: String = "",
    val canSend: Boolean = false,
    val isSending: Boolean = false,
    val activeStreamingMessageId: String? = null,
    val canStopGeneration: Boolean = false
)

class AssistantViewModel(
    private val assistantConversationsStore: AssistantConversationsStore,
    private val appSettingsStore: AppSettingsStore,
    private val assistantGateway: AssistantGateway,
    private val externalScope: CoroutineScope? = null,
    private val nowProvider: () -> Instant = { Instant.now() },
    private val idProvider: (String) -> String = { prefix -> "$prefix-${UUID.randomUUID()}" },
    private val zoneId: ZoneId = ZoneId.systemDefault()
) : ViewModel() {

    private val activeConversationId = MutableStateFlow<String?>(null)
    private val composerText = MutableStateFlow("")
    private val statusMessage = MutableStateFlow(assistantDefaultStatusMessage)
    private val assistantError = MutableStateFlow<String?>(null)
    private val isSending = MutableStateFlow(false)
    private val activeStreamingMessageId = MutableStateFlow<String?>(null)

    private val _uiState = MutableStateFlow(AssistantUiState())
    val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()

    private var observerJob: Job? = null
    private var generationJob: Job? = null

    init {
        bootstrapConversationsIfNeeded()
        observerJob = observeUiState()
    }

    fun onComposerTextChanged(value: String) {
        if (isSending.value) return
        composerText.update { value }
    }

    fun createConversation() {
        if (isSending.value) return

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
        if (isSending.value) return
        activeConversationId.update { conversationId }
        statusMessage.update { "Switched conversation." }
    }

    fun renameConversation(conversationId: String, title: String) {
        if (isSending.value) return

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
        if (isSending.value) return

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
        if (isSending.value) return

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
        if (isSending.value) return

        requestAssistantReply(
            prompt = composerText.value,
            retryUserMessageId = null,
            replaceAssistantMessageId = null
        )
    }

    fun retryFailedMessage(userMessageId: String) {
        if (isSending.value) return

        val activeConversation = resolveActiveConversation(assistantConversationsStore.conversations.value)
            ?: return

        val target = activeConversation.messages.firstOrNull { message ->
            message.id == userMessageId && message.role == AssistantMessageRole.USER
        } ?: return

        requestAssistantReply(
            prompt = target.content,
            retryUserMessageId = userMessageId,
            replaceAssistantMessageId = null
        )
    }

    fun regenerateReply(assistantMessageId: String) {
        if (isSending.value) return

        val activeConversation = resolveActiveConversation(assistantConversationsStore.conversations.value)
            ?: return

        val assistantIndex = activeConversation.messages.indexOfFirst { message ->
            message.id == assistantMessageId && message.role == AssistantMessageRole.ASSISTANT
        }

        if (assistantIndex <= 0) {
            statusMessage.update { "No message available to regenerate." }
            return
        }

        val previousUser = activeConversation.messages
            .subList(0, assistantIndex)
            .lastOrNull { message -> message.role == AssistantMessageRole.USER }

        if (previousUser == null) {
            statusMessage.update { "Could not locate the paired user prompt." }
            return
        }

        requestAssistantReply(
            prompt = previousUser.content,
            retryUserMessageId = previousUser.id,
            replaceAssistantMessageId = assistantMessageId
        )
    }

    fun stopGeneration() {
        generationJob?.cancel()
    }

    private fun requestAssistantReply(
        prompt: String,
        retryUserMessageId: String?,
        replaceAssistantMessageId: String?
    ) {
        val normalizedPrompt = prompt.trim()
        if (normalizedPrompt.isBlank()) return
        if (isSending.value) return

        generationJob = launchWork {
            val conversations = assistantConversationsStore.conversations.value
            val activeConversation = resolveActiveConversation(conversations)

            if (activeConversation == null) {
                statusMessage.update { "No conversation available." }
                return@launchWork
            }

            val settings = appSettingsStore.settings.value
            val apiKey = settings.openRouterApiKey.trim()
            val model = settings.aiModel.trim()

            if (apiKey.isEmpty() || model.isEmpty()) {
                val missingMessage = when {
                    apiKey.isEmpty() && model.isEmpty() -> {
                        "Missing OpenRouter API key and model. Configure both in Settings."
                    }

                    apiKey.isEmpty() -> {
                        "Missing OpenRouter API key. Configure it in Settings."
                    }

                    else -> {
                        "Missing OpenRouter model. Configure it in Settings."
                    }
                }

                assistantError.update { missingMessage }
                statusMessage.update { missingMessage }
                return@launchWork
            }

            val now = nowIso()
            val userMessageId = retryUserMessageId ?: idProvider("msg")
            val newUserMessage = if (retryUserMessageId == null) {
                AssistantChatMessage(
                    id = userMessageId,
                    role = AssistantMessageRole.USER,
                    content = normalizedPrompt,
                    createdAt = now
                )
            } else {
                null
            }

            val assistantDraftId = idProvider("msg")
            val assistantDraft = AssistantChatMessage(
                id = assistantDraftId,
                role = AssistantMessageRole.ASSISTANT,
                content = "",
                createdAt = now
            )

            val updatedConversations = conversations.map { conversation ->
                if (conversation.id != activeConversation.id) {
                    conversation
                } else {
                    var messages = conversation.messages

                    if (replaceAssistantMessageId != null) {
                        messages = messages.filterNot { message ->
                            message.id == replaceAssistantMessageId &&
                                message.role == AssistantMessageRole.ASSISTANT
                        }
                    }

                    messages = if (retryUserMessageId != null) {
                        messages.map { message ->
                            if (message.id == retryUserMessageId && message.role == AssistantMessageRole.USER) {
                                message.copy(
                                    requestFailed = false,
                                    requestError = null
                                )
                            } else {
                                message
                            }
                        }
                    } else {
                        messages + checkNotNull(newUserMessage)
                    }

                    val firstPromptTitle =
                        retryUserMessageId == null &&
                            conversation.title == assistantDefaultConversationTitle &&
                            conversation.messages.none { it.role == AssistantMessageRole.USER }

                    conversation.copy(
                        title = if (firstPromptTitle) {
                            normalizedPrompt.take(40).ifBlank { assistantDefaultConversationTitle }
                        } else {
                            conversation.title
                        },
                        messages = messages + assistantDraft,
                        updatedAt = now
                    )
                }
            }

            assistantConversationsStore.setConversations(updatedConversations)

            if (retryUserMessageId == null) {
                composerText.update { "" }
            }

            val requestMessages = updatedConversations
                .firstOrNull { it.id == activeConversation.id }
                ?.messages
                ?.filterNot { it.id == assistantDraftId }
                .orEmpty()
                .toGatewayMessages()

            if (requestMessages.none { it.role == AssistantMessageRole.USER }) {
                statusMessage.update { "No user prompt available for assistant request." }
                return@launchWork
            }

            isSending.update { true }
            activeStreamingMessageId.update { assistantDraftId }
            assistantError.update { null }

            try {
                val finalText = assistantGateway.requestStreamingReply(
                    request = AssistantGatewayRequest(
                        apiKey = apiKey,
                        model = model,
                        messages = requestMessages
                    ),
                    onSnapshot = { snapshot ->
                        mutateConversation(activeConversation.id) { conversation ->
                            conversation.copy(
                                messages = conversation.messages.map { message ->
                                    if (message.id == assistantDraftId) {
                                        message.copy(content = snapshot)
                                    } else {
                                        message
                                    }
                                },
                                updatedAt = nowIso()
                            )
                        }
                    }
                ).trim()

                mutateConversation(activeConversation.id) { conversation ->
                    conversation.copy(
                        messages = conversation.messages.map { message ->
                            if (message.id == assistantDraftId) {
                                val current = message.content.trim()
                                val next = finalText.ifBlank { current }
                                message.copy(content = next)
                            } else {
                                message
                            }
                        },
                        updatedAt = nowIso()
                    )
                }

                statusMessage.update { "Assistant response received." }
            } catch (_: CancellationException) {
                val hasPartialContent = assistantConversationsStore.conversations.value
                    .firstOrNull { it.id == activeConversation.id }
                    ?.messages
                    ?.firstOrNull { it.id == assistantDraftId }
                    ?.content
                    ?.isNotBlank()
                    ?: false

                if (!hasPartialContent) {
                    mutateConversation(activeConversation.id) { conversation ->
                        conversation.copy(
                            messages = conversation.messages.filterNot { it.id == assistantDraftId },
                            updatedAt = nowIso()
                        )
                    }
                }

                statusMessage.update { "Generation stopped." }
            } catch (error: Throwable) {
                val failureMessage =
                    error.message?.takeIf { message -> message.isNotBlank() }
                        ?: "Assistant request failed."

                assistantError.update { failureMessage }
                statusMessage.update { failureMessage }

                mutateConversation(activeConversation.id) { conversation ->
                    val cleanedMessages = conversation.messages
                        .filterNot { it.id == assistantDraftId }
                        .map { message ->
                            if (message.id == userMessageId && message.role == AssistantMessageRole.USER) {
                                message.copy(
                                    requestFailed = true,
                                    requestError = failureMessage
                                )
                            } else {
                                message
                            }
                        }

                    conversation.copy(
                        messages = cleanedMessages,
                        updatedAt = nowIso()
                    )
                }
            } finally {
                isSending.update { false }
                activeStreamingMessageId.update { null }
                generationJob = null
            }
        }
    }

    private suspend fun mutateConversation(
        conversationId: String,
        transform: (AssistantConversation) -> AssistantConversation
    ) {
        val updated = assistantConversationsStore.conversations.value.map { conversation ->
            if (conversation.id == conversationId) {
                transform(conversation)
            } else {
                conversation
            }
        }

        assistantConversationsStore.setConversations(updated)
    }

    private fun List<AssistantChatMessage>.toGatewayMessages(): List<AssistantGatewayMessage> =
        mapNotNull { message ->
            val content = message.content.trim()
            if (content.isBlank()) {
                null
            } else {
                AssistantGatewayMessage(
                    role = message.role,
                    content = content
                )
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
            val transientState = combine(
                activeConversationId,
                composerText,
                statusMessage,
                assistantError,
                isSending,
                activeStreamingMessageId
            ) { values ->
                @Suppress("UNCHECKED_CAST")
                AssistantTransientState(
                    activeId = values[0] as String?,
                    composer = values[1] as String,
                    status = values[2] as String,
                    error = values[3] as String?,
                    sending = values[4] as Boolean,
                    streamingId = values[5] as String?
                )
            }

            combine(
                assistantConversationsStore.conversations,
                transientState
            ) { conversations, transient ->
                val sorted = conversations.sortedWith(
                    compareByDescending<AssistantConversation> { it.pinned }
                        .thenByDescending { it.updatedAt }
                )
                val activeConversation =
                    sorted.firstOrNull { it.id == transient.activeId } ?: sorted.firstOrNull()

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
                    statusMessage = transient.status,
                    assistantError = transient.error,
                    conversationItems = items,
                    activeConversationId = activeConversation?.id,
                    activeConversationTitle = activeConversation?.title ?: assistantDefaultConversationTitle,
                    activeConversationPinned = activeConversation?.pinned ?: false,
                    messages = activeConversation?.messages.orEmpty(),
                    composerText = transient.composer,
                    canSend = transient.composer.trim().isNotEmpty() &&
                        activeConversation != null &&
                        !transient.sending,
                    isSending = transient.sending,
                    activeStreamingMessageId = transient.streamingId,
                    canStopGeneration = transient.sending && !transient.streamingId.isNullOrBlank()
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
        generationJob?.cancel()
        generationJob = null
    }

    companion object {
        private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

        fun factory(
            assistantConversationsStore: AssistantConversationsStore,
            appSettingsStore: AppSettingsStore,
            assistantGateway: AssistantGateway
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(AssistantViewModel::class.java)) {
                        return AssistantViewModel(
                            assistantConversationsStore = assistantConversationsStore,
                            appSettingsStore = appSettingsStore,
                            assistantGateway = assistantGateway
                        ) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
    }
}