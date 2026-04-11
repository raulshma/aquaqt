package com.keepaside.aquapt.feature.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.keepaside.aquapt.core.assistant.AssistantActionReviewService
import com.keepaside.aquapt.core.assistant.AssistantDictationController
import com.keepaside.aquapt.core.assistant.AssistantGateway
import com.keepaside.aquapt.core.assistant.AssistantGatewayMessage
import com.keepaside.aquapt.core.assistant.AssistantGatewayRequest
import com.keepaside.aquapt.core.assistant.NoOpAssistantDictationController
import com.keepaside.aquapt.core.model.AssistantActionTypes
import com.keepaside.aquapt.core.model.AssistantChatMessage
import com.keepaside.aquapt.core.model.AssistantConversation
import com.keepaside.aquapt.core.model.AssistantDetectedAction
import com.keepaside.aquapt.core.model.AssistantMemoryCompactionPreview
import com.keepaside.aquapt.core.model.AssistantMemorySnippet
import com.keepaside.aquapt.core.model.AssistantMessageRole
import com.keepaside.aquapt.core.model.AssistantResponseTelemetry
import com.keepaside.aquapt.core.model.AppSettings
import com.keepaside.aquapt.core.repository.AssistantConversationsStore
import com.keepaside.aquapt.core.repository.AssistantMemoryStore
import com.keepaside.aquapt.core.repository.AppSettingsStore
import kotlinx.coroutines.CancellationException
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
private const val assistantMemoryCompactionSnippetLimit = 24
private const val assistantMemoryCompactionSnippetCharLimit = 320
private const val assistantMemoryCompactionFactCharLimit = 220

val QUICK_PROMPT_SUGGESTIONS = listOf(
    "What should I do for my tanks today?",
    "Review my open issues and suggest priorities.",
    "Plan this week's maintenance tasks.",
    "Any dosing or parameter checks due today?"
)

private object NoOpAssistantMemoryStore : AssistantMemoryStore {
    private val empty = MutableStateFlow<List<AssistantMemorySnippet>>(emptyList())

    override val snippets: StateFlow<List<AssistantMemorySnippet>> = empty.asStateFlow()

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

    override suspend fun forgetSnippet(id: String) = Unit

    override suspend fun clearAllSnippets() = Unit

    override suspend fun queryRelevantSnippets(
        prompt: String,
        limit: Int,
        conversationId: String?
    ): List<AssistantMemorySnippet> = emptyList()

    override suspend fun previewCompaction(maxFacts: Int): AssistantMemoryCompactionPreview =
        AssistantMemoryCompactionPreview(
            beforeCount = 0,
            afterCount = 0,
            facts = emptyList()
        )

    override suspend fun applyCompaction(
        precomputedFacts: List<String>,
        maxFacts: Int
    ): AssistantMemoryCompactionPreview =
        AssistantMemoryCompactionPreview(
            beforeCount = 0,
            afterCount = 0,
            facts = emptyList()
        )
}

private data class AssistantTransientState(
    val activeId: String?,
    val conversationSearch: String,
    val composer: String,
    val status: String,
    val error: String?,
    val sending: Boolean,
    val streamingId: String?,
    val executingActions: Boolean,
    val dictating: Boolean,
    val memoryBusyMessageIds: Set<String>,
    val memoryPreviewing: Boolean,
    val memoryApplying: Boolean,
    val memoryPreview: AssistantMemoryCompactionPreview?
)

data class AssistantConversationItem(
    val id: String,
    val title: String,
    val isPinned: Boolean,
    val updatedAtLabel: String,
    val messageCount: Int,
    val lastMessagePreview: String?
)

data class AssistantDetectedActionItem(
    val id: String,
    val type: String,
    val title: String,
    val subtitle: String,
    val approved: Boolean,
    val confidence: Double,
    val validationErrors: List<String>
)

data class AssistantUiState(
    val isLoading: Boolean = true,
    val statusMessage: String = assistantDefaultStatusMessage,
    val assistantError: String? = null,
    val conversationItems: List<AssistantConversationItem> = emptyList(),
    val conversationSearchQuery: String = "",
    val totalConversationCount: Int = 0,
    val visibleConversationCount: Int = 0,
    val activeConversationId: String? = null,
    val activeConversationTitle: String = assistantDefaultConversationTitle,
    val activeConversationPinned: Boolean = false,
    val messages: List<AssistantChatMessage> = emptyList(),
    val detectedActions: List<AssistantDetectedActionItem> = emptyList(),
    val actionWarnings: List<String> = emptyList(),
    val composerText: String = "",
    val canSend: Boolean = false,
    val isSending: Boolean = false,
    val activeStreamingMessageId: String? = null,
    val canStopGeneration: Boolean = false,
    val dictationSupported: Boolean = false,
    val isDictating: Boolean = false,
    val isExecutingActions: Boolean = false,
    val approvedActionCount: Int = 0,
    val canExecuteApprovedActions: Boolean = false,
    val hasDetectedActions: Boolean = false,
    val assistantMemoryEnabled: Boolean = false,
    val assistantMemoryModel: String? = null,
    val assistantMemorySnippetCount: Int = 0,
    val rememberedAssistantMessageIds: Set<String> = emptySet(),
    val memoryActionBusyMessageIds: Set<String> = emptySet(),
    val isMemoryPreviewing: Boolean = false,
    val isApplyingMemoryCompaction: Boolean = false,
    val canApplyMemoryCompaction: Boolean = false,
    val memoryCompactionBeforeCount: Int? = null,
    val memoryCompactionAfterCount: Int? = null,
    val memoryCompactionFacts: List<String> = emptyList()
)

class AssistantViewModel(
    private val assistantConversationsStore: AssistantConversationsStore,
    private val appSettingsStore: AppSettingsStore,
    private val assistantMemoryStore: AssistantMemoryStore = NoOpAssistantMemoryStore,
    private val assistantGateway: AssistantGateway,
    private val assistantActionReviewService: AssistantActionReviewService,
    private val assistantDictationController: AssistantDictationController =
        NoOpAssistantDictationController,
    private val externalScope: CoroutineScope? = null,
    private val nowProvider: () -> Instant = { Instant.now() },
    private val idProvider: (String) -> String = { prefix -> "$prefix-${UUID.randomUUID()}" },
    private val zoneId: ZoneId = ZoneId.systemDefault()
) : ViewModel() {

    private val activeConversationId = MutableStateFlow<String?>(null)
    private val conversationSearchQuery = MutableStateFlow("")
    private val composerText = MutableStateFlow("")
    private val statusMessage = MutableStateFlow(assistantDefaultStatusMessage)
    private val assistantError = MutableStateFlow<String?>(null)
    private val isSending = MutableStateFlow(false)
    private val activeStreamingMessageId = MutableStateFlow<String?>(null)
    private val isExecutingActions = MutableStateFlow(false)
    private val isDictating = MutableStateFlow(false)
    private val memoryActionBusyMessageIds = MutableStateFlow<Set<String>>(emptySet())
    private val isPreviewingMemoryCompaction = MutableStateFlow(false)
    private val isApplyingMemoryCompaction = MutableStateFlow(false)
    private val memoryCompactionPreview = MutableStateFlow<AssistantMemoryCompactionPreview?>(null)

    private var dictationBaseComposerText: String = ""

    private val _uiState = MutableStateFlow(AssistantUiState())
    val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()

    private var observerJob: Job? = null
    private var generationJob: Job? = null

    private fun isBusy(): Boolean =
        isSending.value ||
            isExecutingActions.value ||
            isApplyingMemoryCompaction.value ||
            isDictating.value

    init {
        bootstrapConversationsIfNeeded()
        observerJob = observeUiState()
    }

    fun onComposerTextChanged(value: String) {
        if (isBusy()) return
        composerText.update { value }
    }

    fun applyQuickPrompt(prompt: String) {
        if (isBusy()) return
        composerText.update { prompt }
    }

    fun onConversationSearchQueryChanged(value: String) {
        conversationSearchQuery.update { value }
    }

    fun clearConversationSearchQuery() {
        conversationSearchQuery.update { "" }
    }

    fun createConversation() {
        if (isBusy()) return

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
        if (isBusy()) return
        activeConversationId.update { conversationId }
        statusMessage.update { "Switched conversation." }
    }

    fun renameConversation(conversationId: String, title: String) {
        if (isBusy()) return

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
        if (isBusy()) return

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
        if (isBusy()) return

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
        if (isBusy()) return

        requestAssistantReply(
            prompt = composerText.value,
            retryUserMessageId = null,
            replaceAssistantMessageId = null
        )
    }

    fun startDictation() {
        if (isBusy()) return

        if (!assistantDictationController.isAvailable) {
            statusMessage.update { "Dictation is unavailable on this device." }
            return
        }

        dictationBaseComposerText = composerText.value.trim()
        assistantError.update { null }

        val started = assistantDictationController.startListening(
            onPartialTranscript = { partial ->
                if (!isDictating.value) {
                    return@startListening
                }

                val merged = mergeDictationIntoComposer(partial)
                composerText.update { merged }
            },
            onFinalTranscript = { final ->
                val merged = mergeDictationIntoComposer(final)
                composerText.update { merged }
                dictationBaseComposerText = merged
                isDictating.update { false }
                statusMessage.update {
                    if (merged.isBlank()) {
                        "No speech detected. Try dictation again."
                    } else {
                        "Dictation captured. Review and send when ready."
                    }
                }
            },
            onError = { error ->
                isDictating.update { false }
                statusMessage.update {
                    error.trim().ifBlank { "Dictation failed. Please try again." }
                }
            }
        )

        if (started) {
            isDictating.update { true }
            statusMessage.update { "Listening… tap Stop when finished." }
        } else {
            statusMessage.update { "Could not start dictation." }
        }
    }

    fun stopDictation() {
        if (!isDictating.value) return

        assistantDictationController.stopListening()
        isDictating.update { false }
        dictationBaseComposerText = composerText.value.trim()
        statusMessage.update { "Dictation stopped." }
    }

    fun onDictationPermissionDenied() {
        if (isBusy()) return
        statusMessage.update { "Microphone permission is required for dictation." }
    }

    fun reuseMessageAsPrompt(messageId: String) {
        if (isBusy()) return

        val activeConversation = resolveActiveConversation(assistantConversationsStore.conversations.value)
        if (activeConversation == null) {
            statusMessage.update { "No conversation available." }
            return
        }

        val message = activeConversation.messages.firstOrNull { candidate ->
            candidate.id == messageId
        }

        if (message == null) {
            statusMessage.update { "Message is no longer available." }
            return
        }

        val content = message.content.trim()
        if (content.isEmpty()) {
            statusMessage.update { "Cannot reuse an empty message." }
            return
        }

        composerText.update { content }
        statusMessage.update {
            when (message.role) {
                AssistantMessageRole.USER -> "Loaded your previous prompt into the composer."
                AssistantMessageRole.ASSISTANT -> "Loaded assistant reply into the composer."
                AssistantMessageRole.SYSTEM -> "Loaded system message into the composer."
            }
        }
    }

    fun onMessageCopied(messageRole: AssistantMessageRole) {
        if (isBusy()) return

        statusMessage.update {
            when (messageRole) {
                AssistantMessageRole.USER -> "Copied your message to clipboard."
                AssistantMessageRole.ASSISTANT -> "Copied assistant reply to clipboard."
                AssistantMessageRole.SYSTEM -> "Copied system message to clipboard."
            }
        }
    }

    fun retryFailedMessage(userMessageId: String) {
        if (isBusy()) return

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
        if (isBusy()) return

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

    fun toggleActionApproval(actionId: String, approved: Boolean) {
        if (isBusy()) return

        launchWork {
            val activeConversation = resolveActiveConversation(assistantConversationsStore.conversations.value)
                ?: return@launchWork

            mutateConversation(activeConversation.id) { conversation ->
                conversation.copy(
                    detectedActions = conversation.detectedActions.map { action ->
                        if (action.id == actionId) {
                            action.copy(approved = approved)
                        } else {
                            action
                        }
                    },
                    updatedAt = nowIso()
                )
            }

            statusMessage.update {
                if (approved) {
                    "Action approved."
                } else {
                    "Action unapproved."
                }
            }
        }
    }

    fun approveAllValidActions() {
        if (isBusy()) return

        launchWork {
            val activeConversation = resolveActiveConversation(assistantConversationsStore.conversations.value)
                ?: return@launchWork

            mutateConversation(activeConversation.id) { conversation ->
                conversation.copy(
                    detectedActions = conversation.detectedActions.map { action ->
                        if (action.validationErrors.isEmpty()) {
                            action.copy(approved = true)
                        } else {
                            action
                        }
                    },
                    updatedAt = nowIso()
                )
            }

            statusMessage.update { "Approved all valid actions." }
        }
    }

    fun executeApprovedActions() {
        if (isBusy()) return

        launchWork {
            val activeConversation = resolveActiveConversation(assistantConversationsStore.conversations.value)
            if (activeConversation == null) {
                statusMessage.update { "No conversation available." }
                return@launchWork
            }

            val approvedActions = activeConversation.detectedActions.filter { action ->
                action.approved && action.validationErrors.isEmpty()
            }

            if (approvedActions.isEmpty()) {
                statusMessage.update { "No approved valid actions to execute." }
                return@launchWork
            }

            isExecutingActions.update { true }

            try {
                val executionResult = assistantActionReviewService.executeApprovedActions(approvedActions)
                val approvedIds = approvedActions.map { action -> action.id }.toSet()

                val summaryHeader = buildString {
                    append("Executed ${executionResult.createdCount} action")
                    if (executionResult.createdCount != 1) {
                        append("s")
                    }

                    if (executionResult.skippedCount > 0) {
                        append(" • Skipped ${executionResult.skippedCount}")
                    }
                }

                val details = executionResult.results
                    .take(5)
                    .joinToString(separator = "\n") { item ->
                        val prefix = if (item.created) "✓" else "•"
                        val message = item.summary ?: item.reason ?: "No details"
                        "$prefix ${item.actionType}: $message"
                    }

                val systemMessage = AssistantChatMessage(
                    id = idProvider("msg"),
                    role = AssistantMessageRole.SYSTEM,
                    content = listOf(summaryHeader, details)
                        .filter { it.isNotBlank() }
                        .joinToString(separator = "\n"),
                    createdAt = nowIso()
                )

                mutateConversation(activeConversation.id) { conversation ->
                    conversation.copy(
                        detectedActions = conversation.detectedActions.map { action ->
                            if (action.id in approvedIds) {
                                action.copy(approved = false)
                            } else {
                                action
                            }
                        },
                        messages = conversation.messages + systemMessage,
                        warnings = (conversation.warnings + executionResult.results
                            .filter { item -> !item.created }
                            .mapNotNull { item -> item.reason })
                            .takeLast(8),
                        updatedAt = nowIso()
                    )
                }

                statusMessage.update { summaryHeader }
            } catch (error: Throwable) {
                val message = error.message?.takeIf { it.isNotBlank() }
                    ?: "Failed to execute assistant actions."
                assistantError.update { message }
                statusMessage.update { message }
            } finally {
                isExecutingActions.update { false }
            }
        }
    }

    fun rememberAssistantMessage(messageId: String) {
        if (isBusy()) return

        val activeConversation = resolveActiveConversation(assistantConversationsStore.conversations.value)
        if (activeConversation == null) {
            statusMessage.update { "No conversation available." }
            return
        }

        val settings = appSettingsStore.settings.value
        if (!settings.assistantMemoryEnabled) {
            statusMessage.update { "Assistant memory is disabled in Settings." }
            return
        }

        val message = activeConversation.messages.firstOrNull { candidate ->
            candidate.id == messageId && candidate.role == AssistantMessageRole.ASSISTANT
        }

        val content = message?.content?.trim().orEmpty()
        if (content.isEmpty()) {
            statusMessage.update { "Cannot remember an empty assistant message." }
            return
        }

        launchWork {
            setMemoryBusy(messageId, busy = true)

            try {
                assistantMemoryStore.rememberManualSnippet(
                    conversationId = activeConversation.id,
                    sourceMessageId = messageId,
                    content = content
                )
                statusMessage.update { "Saved assistant reply to memory." }
            } catch (error: Throwable) {
                val messageText = error.message?.takeIf { it.isNotBlank() }
                    ?: "Could not remember assistant reply."
                assistantError.update { messageText }
                statusMessage.update { messageText }
            } finally {
                setMemoryBusy(messageId, busy = false)
            }
        }
    }

    fun forgetAssistantMessageMemory(messageId: String) {
        if (isBusy()) return

        val activeConversation = resolveActiveConversation(assistantConversationsStore.conversations.value)
        if (activeConversation == null) {
            statusMessage.update { "No conversation available." }
            return
        }

        launchWork {
            setMemoryBusy(messageId, busy = true)

            try {
                assistantMemoryStore.forgetManualSnippet(
                    conversationId = activeConversation.id,
                    sourceMessageId = messageId
                )
                statusMessage.update { "Removed assistant memory snippet." }
            } catch (error: Throwable) {
                val messageText = error.message?.takeIf { it.isNotBlank() }
                    ?: "Could not forget assistant memory snippet."
                assistantError.update { messageText }
                statusMessage.update { messageText }
            } finally {
                setMemoryBusy(messageId, busy = false)
            }
        }
    }

    fun previewMemoryCompaction(maxFacts: Int = 10) {
        if (isBusy()) return
        if (isPreviewingMemoryCompaction.value) return

        val settings = appSettingsStore.settings.value
        if (!settings.assistantMemoryEnabled) {
            statusMessage.update { "Assistant memory is disabled in Settings." }
            return
        }

        launchWork {
            isPreviewingMemoryCompaction.update { true }

            try {
                val preview = buildMemoryCompactionPreview(
                    settings = settings,
                    maxFacts = maxFacts
                )
                memoryCompactionPreview.update { preview }

                statusMessage.update {
                    if (preview.beforeCount <= 0) {
                        "No memory snippets available for compaction preview."
                    } else {
                        "Compaction preview: ${preview.beforeCount} → ${preview.afterCount} fact(s)."
                    }
                }
            } catch (error: Throwable) {
                val messageText = error.message?.takeIf { it.isNotBlank() }
                    ?: "Could not preview memory compaction."
                assistantError.update { messageText }
                statusMessage.update { messageText }
            } finally {
                isPreviewingMemoryCompaction.update { false }
            }
        }
    }

    fun applyMemoryCompaction(maxFacts: Int = 10) {
        if (isBusy()) return
        if (isApplyingMemoryCompaction.value) return

        val settings = appSettingsStore.settings.value
        if (!settings.assistantMemoryEnabled) {
            statusMessage.update { "Assistant memory is disabled in Settings." }
            return
        }

        launchWork {
            isApplyingMemoryCompaction.update { true }

            try {
                val previewFacts = memoryCompactionPreview.value?.facts.orEmpty()
                val result = assistantMemoryStore.applyCompaction(
                    precomputedFacts = previewFacts,
                    maxFacts = maxFacts
                )
                memoryCompactionPreview.update { result }

                statusMessage.update {
                    if (result.beforeCount <= 0) {
                        "No memory snippets available to compact."
                    } else {
                        "Applied memory compaction: ${result.beforeCount} → ${result.afterCount} fact(s)."
                    }
                }
            } catch (error: Throwable) {
                val messageText = error.message?.takeIf { it.isNotBlank() }
                    ?: "Could not apply memory compaction."
                assistantError.update { messageText }
                statusMessage.update { messageText }
            } finally {
                isApplyingMemoryCompaction.update { false }
            }
        }
    }

    fun dismissMemoryCompactionPreview() {
        if (isBusy()) return
        memoryCompactionPreview.update { null }
        statusMessage.update { "Dismissed memory compaction preview." }
    }

    private fun requestAssistantReply(
        prompt: String,
        retryUserMessageId: String?,
        replaceAssistantMessageId: String?
    ) {
        val normalizedPrompt = prompt.trim()
        if (normalizedPrompt.isBlank()) return
        if (isBusy()) return

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
                    var detectedActions = conversation.detectedActions

                    if (replaceAssistantMessageId != null) {
                        messages = messages.filterNot { message ->
                            message.id == replaceAssistantMessageId &&
                                message.role == AssistantMessageRole.ASSISTANT
                        }

                        detectedActions = detectedActions.filterNot { action ->
                            action.sourceMessageId == replaceAssistantMessageId
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
                        detectedActions = detectedActions,
                        updatedAt = now
                    )
                }
            }

            assistantConversationsStore.setConversations(updatedConversations)

            if (retryUserMessageId == null) {
                composerText.update { "" }
            }

            val requestMessagesWithoutMemory = updatedConversations
                .firstOrNull { it.id == activeConversation.id }
                ?.messages
                ?.filterNot { it.id == assistantDraftId }
                .orEmpty()
                .toGatewayMessages()

            val memorySnippets = if (settings.assistantMemoryEnabled) {
                runCatching {
                    assistantMemoryStore.queryRelevantSnippets(
                        prompt = normalizedPrompt,
                        limit = 4,
                        conversationId = activeConversation.id
                    )
                }.getOrDefault(emptyList())
            } else {
                emptyList()
            }

            val memoryPrompt = buildMemorySystemPrompt(memorySnippets)

            val requestMessages = if (!memoryPrompt.isNullOrBlank()) {
                listOf(
                    AssistantGatewayMessage(
                        role = AssistantMessageRole.SYSTEM,
                        content = memoryPrompt
                    )
                ) + requestMessagesWithoutMemory
            } else {
                requestMessagesWithoutMemory
            }

            if (requestMessages.none { it.role == AssistantMessageRole.USER }) {
                statusMessage.update { "No user prompt available for assistant request." }
                return@launchWork
            }

            isSending.update { true }
            activeStreamingMessageId.update { assistantDraftId }
            assistantError.update { null }

            try {
                val requestStartedAtMs = System.currentTimeMillis()
                val gatewayResponse = assistantGateway.requestStreamingReply(
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
                )

                val finalText = gatewayResponse.text.trim()
                val streamedText = assistantConversationsStore.conversations.value
                    .firstOrNull { it.id == activeConversation.id }
                    ?.messages
                    ?.firstOrNull { it.id == assistantDraftId }
                    ?.content
                    ?.trim()
                    .orEmpty()

                val resolvedReplyText = finalText.ifBlank { streamedText }
                val elapsedMs = (System.currentTimeMillis() - requestStartedAtMs)
                    .coerceAtLeast(1)
                    .toLong()

                val completionTokens = gatewayResponse.telemetry?.usage?.completionTokens
                val throughputTokensPerSecond = if (completionTokens != null) {
                    completionTokens / (elapsedMs / 1000.0)
                } else {
                    null
                }

                val throughputCharsPerSecond = if (resolvedReplyText.isNotBlank()) {
                    resolvedReplyText.length / (elapsedMs / 1000.0)
                } else {
                    null
                }

                val responseTelemetry = AssistantResponseTelemetry(
                    generationId = gatewayResponse.telemetry?.generationId,
                    providerName = gatewayResponse.telemetry?.providerName,
                    router = gatewayResponse.telemetry?.router,
                    model = gatewayResponse.telemetry?.model ?: model,
                    promptTokens = gatewayResponse.telemetry?.usage?.promptTokens,
                    completionTokens = completionTokens,
                    totalTokens = gatewayResponse.telemetry?.usage?.totalTokens,
                    cost = gatewayResponse.telemetry?.cost ?: gatewayResponse.telemetry?.usage?.cost,
                    elapsedMs = elapsedMs,
                    latencyMs = gatewayResponse.telemetry?.latencyMs ?: elapsedMs,
                    generationTimeMs = gatewayResponse.telemetry?.generationTimeMs,
                    throughputCharsPerSecond = throughputCharsPerSecond,
                    throughputTokensPerSecond = throughputTokensPerSecond,
                    finishReason = gatewayResponse.telemetry?.finishReason,
                    nativeFinishReason = gatewayResponse.telemetry?.nativeFinishReason,
                    streamed = gatewayResponse.telemetry?.streamed ?: true
                )

                val extraction = assistantActionReviewService.parseAssistantActionExtraction(
                    responseContent = resolvedReplyText,
                    transcript = normalizedPrompt,
                    sourceMessageId = assistantDraftId
                )

                mutateConversation(activeConversation.id) { conversation ->
                    conversation.copy(
                        messages = conversation.messages.map { message ->
                            if (message.id == assistantDraftId) {
                                val current = message.content.trim()
                                val next = resolvedReplyText.ifBlank { current }
                                message.copy(
                                    content = next,
                                    detectedActionIds = extraction.actions.map { action -> action.id },
                                    responseTelemetry = responseTelemetry
                                )
                            } else {
                                message
                            }
                        },
                        detectedActions = conversation.detectedActions
                            .filterNot { action -> action.sourceMessageId == assistantDraftId } +
                            extraction.actions,
                        warnings = (conversation.warnings + extraction.warnings).takeLast(8),
                        updatedAt = nowIso()
                    )
                }

                if (settings.assistantMemoryEnabled && resolvedReplyText.isNotBlank()) {
                    runCatching {
                        assistantMemoryStore.rememberTurn(
                            conversationId = activeConversation.id,
                            userMessageId = userMessageId,
                            userPrompt = normalizedPrompt,
                            assistantText = resolvedReplyText
                        )
                    }
                }

                statusMessage.update {
                    if (extraction.actions.isNotEmpty()) {
                        "Assistant response received with ${extraction.actions.size} detected action(s)."
                    } else {
                        "Assistant response received."
                    }
                }
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
                            detectedActions = conversation.detectedActions.filterNot { action ->
                                action.sourceMessageId == assistantDraftId
                            },
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
                        detectedActions = conversation.detectedActions.filterNot { action ->
                            action.sourceMessageId == assistantDraftId
                        },
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

    private fun buildMemorySystemPrompt(snippets: List<AssistantMemorySnippet>): String? {
        if (snippets.isEmpty()) {
            return null
        }

        val lines = snippets.mapNotNull { snippet ->
            snippet.content
                .replace(Regex("\\s+"), " ")
                .trim()
                .takeIf { content -> content.isNotEmpty() }
                ?.let { content -> "- $content" }
        }

        if (lines.isEmpty()) {
            return null
        }

        return listOf(
            "Long-term memory snippets from previous chats (may be outdated; verify before acting):",
            *lines.toTypedArray()
        ).joinToString(separator = "\n")
    }

    private suspend fun buildMemoryCompactionPreview(
        settings: AppSettings,
        maxFacts: Int
    ): AssistantMemoryCompactionPreview {
        val fallbackPreview = assistantMemoryStore.previewCompaction(maxFacts = maxFacts)
        if (fallbackPreview.beforeCount <= 0) {
            return fallbackPreview
        }

        val aiFacts = runCatching {
            generateAiCompactionFacts(
                settings = settings,
                maxFacts = maxFacts
            )
        }.getOrDefault(emptyList())

        return if (aiFacts.isNotEmpty()) {
            fallbackPreview.copy(
                afterCount = aiFacts.size,
                facts = aiFacts
            )
        } else {
            fallbackPreview
        }
    }

    private suspend fun generateAiCompactionFacts(
        settings: AppSettings,
        maxFacts: Int
    ): List<String> {
        val apiKey = settings.openRouterApiKey.trim()
        val memoryModel = settings.assistantMemoryModel?.trim().orEmpty()
        val fallbackModel = settings.aiModel.trim()
        val model = memoryModel.ifEmpty { fallbackModel }

        if (apiKey.isEmpty() || model.isEmpty()) {
            return emptyList()
        }

        val snippetLines = assistantMemoryStore.snippets.value
            .sortedByDescending { snippet -> snippet.createdAt.orEmpty() }
            .mapNotNull { snippet ->
                val normalizedContent = normalizeCompactionText(snippet.content)
                    .take(assistantMemoryCompactionSnippetCharLimit)

                if (normalizedContent.isEmpty()) {
                    null
                } else {
                    val category = snippet.category
                        ?.trim()
                        ?.takeIf { value -> value.isNotEmpty() }
                        ?: "memory"
                    "- [$category] $normalizedContent"
                }
            }
            .take(assistantMemoryCompactionSnippetLimit)

        if (snippetLines.isEmpty()) {
            return emptyList()
        }

        val effectiveMaxFacts = maxFacts.coerceIn(1, 20)

        val response = assistantGateway.requestStreamingReply(
            request = AssistantGatewayRequest(
                apiKey = apiKey,
                model = model,
                messages = listOf(
                    AssistantGatewayMessage(
                        role = AssistantMessageRole.SYSTEM,
                        content = buildString {
                            appendLine("You compact aquarium assistant memory into durable facts.")
                            appendLine("Return ONLY bullet points with no heading.")
                            appendLine("Rules:")
                            appendLine("- Up to $effectiveMaxFacts bullets")
                            appendLine("- One fact per bullet")
                            appendLine("- Keep each fact under 200 characters")
                            appendLine("- Prefer stable user preferences, constraints, and recurring care routines")
                            append("- Remove duplicates and contradictions")
                        }
                    ),
                    AssistantGatewayMessage(
                        role = AssistantMessageRole.USER,
                        content = buildString {
                            appendLine(
                                "Compact the following memory snippets into up to $effectiveMaxFacts durable facts:"
                            )
                            appendLine()
                            append(snippetLines.joinToString(separator = "\n"))
                        }
                    )
                )
            ),
            onSnapshot = { }
        )

        return parseCompactionFacts(
            raw = response.text,
            maxFacts = effectiveMaxFacts
        )
    }

    private fun parseCompactionFacts(
        raw: String,
        maxFacts: Int
    ): List<String> {
        val effectiveMaxFacts = maxFacts.coerceIn(1, 20)

        val fromLines = raw
            .lineSequence()
            .map(::normalizeCompactionText)
            .map { line ->
                line.replace(Regex("^[-*•]\\s*"), "")
                    .replace(Regex("^\\d+[.)]\\s*"), "")
                    .trim()
            }
            .filter { line ->
                line.isNotEmpty() &&
                    !line.endsWith(":") &&
                    !line.equals("facts", ignoreCase = true)
            }
            .filter { line -> line.length >= 12 }
            .map { line -> line.take(assistantMemoryCompactionFactCharLimit) }
            .distinct()
            .take(effectiveMaxFacts)
            .toList()

        if (fromLines.isNotEmpty()) {
            return fromLines
        }

        return raw
            .split(Regex("(?<=[.!?])\\s+"))
            .map(::normalizeCompactionText)
            .filter { sentence -> sentence.length >= 12 }
            .map { sentence -> sentence.take(assistantMemoryCompactionFactCharLimit) }
            .distinct()
            .take(effectiveMaxFacts)
    }

    private fun normalizeCompactionText(value: String): String =
        value
            .replace(Regex("[\\u0000-\\u001F]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

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
                conversationSearchQuery,
                composerText,
                statusMessage,
                assistantError,
                isSending,
                activeStreamingMessageId,
                isExecutingActions,
                isDictating,
                memoryActionBusyMessageIds,
                isPreviewingMemoryCompaction,
                isApplyingMemoryCompaction,
                memoryCompactionPreview
            ) { values ->
                @Suppress("UNCHECKED_CAST")
                AssistantTransientState(
                    activeId = values[0] as String?,
                    conversationSearch = values[1] as String,
                    composer = values[2] as String,
                    status = values[3] as String,
                    error = values[4] as String?,
                    sending = values[5] as Boolean,
                    streamingId = values[6] as String?,
                    executingActions = values[7] as Boolean,
                    dictating = values[8] as Boolean,
                    memoryBusyMessageIds = values[9] as Set<String>,
                    memoryPreviewing = values[10] as Boolean,
                    memoryApplying = values[11] as Boolean,
                    memoryPreview = values[12] as AssistantMemoryCompactionPreview?
                )
            }

            combine(
                assistantConversationsStore.conversations,
                transientState,
                appSettingsStore.settings,
                assistantMemoryStore.snippets
            ) { conversations, transient, settings, snippets ->
                val sorted = conversations.sortedWith(
                    compareByDescending<AssistantConversation> { it.pinned }
                        .thenByDescending { it.updatedAt }
                )

                val normalizedSearch = transient.conversationSearch.trim()
                val visibleConversations = if (normalizedSearch.isEmpty()) {
                    sorted
                } else {
                    sorted.filter { conversation ->
                        val latestPreviewText = assistantMarkdownPreviewText(
                            markdown = conversation.messages.lastOrNull()?.content.orEmpty(),
                            maxLength = 240
                        )

                        conversation.title.contains(normalizedSearch, ignoreCase = true) ||
                            latestPreviewText.contains(normalizedSearch, ignoreCase = true)
                    }
                }

                val activeConversation =
                    sorted.firstOrNull { it.id == transient.activeId } ?: sorted.firstOrNull()

                val rememberedAssistantMessageIds = snippets
                    .asSequence()
                    .filter { snippet ->
                        snippet.category.equals("manual", ignoreCase = true) &&
                            snippet.sourceConversationId == activeConversation?.id
                    }
                    .mapNotNull { snippet -> snippet.sourceMessageId }
                    .toSet()

                val items = visibleConversations.map { conversation ->
                    AssistantConversationItem(
                        id = conversation.id,
                        title = conversation.title,
                        isPinned = conversation.pinned,
                        updatedAtLabel = formatDateTime(conversation.updatedAt),
                        messageCount = conversation.messages.size,
                        lastMessagePreview = assistantMarkdownPreviewText(
                            markdown = conversation.messages.lastOrNull()?.content.orEmpty(),
                            maxLength = 72
                        ).takeIf { it.isNotBlank() }
                    )
                }

                val detectedActions = activeConversation
                    ?.detectedActions
                    .orEmpty()
                    .map { action -> action.toActionItem() }

                val approvedActionCount = activeConversation
                    ?.detectedActions
                    ?.count { action ->
                        action.approved && action.validationErrors.isEmpty()
                    }
                    ?: 0

                val canApplyMemoryCompaction = settings.assistantMemoryEnabled &&
                    transient.memoryPreview != null &&
                    (transient.memoryPreview.beforeCount > 0 || transient.memoryPreview.afterCount > 0) &&
                    !transient.memoryPreviewing &&
                    !transient.memoryApplying &&
                    !transient.dictating &&
                    !transient.sending &&
                    !transient.executingActions

                AssistantUiState(
                    isLoading = false,
                    statusMessage = transient.status,
                    assistantError = transient.error,
                    conversationItems = items,
                    conversationSearchQuery = transient.conversationSearch,
                    totalConversationCount = sorted.size,
                    visibleConversationCount = items.size,
                    activeConversationId = activeConversation?.id,
                    activeConversationTitle = activeConversation?.title ?: assistantDefaultConversationTitle,
                    activeConversationPinned = activeConversation?.pinned ?: false,
                    messages = activeConversation?.messages.orEmpty(),
                    detectedActions = detectedActions,
                    actionWarnings = activeConversation?.warnings.orEmpty(),
                    composerText = transient.composer,
                    canSend = transient.composer.trim().isNotEmpty() &&
                        activeConversation != null &&
                        !transient.dictating &&
                        !transient.sending &&
                        !transient.executingActions &&
                        !transient.memoryApplying,
                    isSending = transient.sending,
                    activeStreamingMessageId = transient.streamingId,
                    canStopGeneration = transient.sending && !transient.streamingId.isNullOrBlank(),
                    dictationSupported = assistantDictationController.isAvailable,
                    isDictating = transient.dictating,
                    isExecutingActions = transient.executingActions,
                    approvedActionCount = approvedActionCount,
                    canExecuteApprovedActions = approvedActionCount > 0 &&
                        !transient.dictating &&
                        !transient.sending &&
                        !transient.memoryApplying,
                    hasDetectedActions = detectedActions.isNotEmpty(),
                    assistantMemoryEnabled = settings.assistantMemoryEnabled,
                    assistantMemoryModel = settings.assistantMemoryModel,
                    assistantMemorySnippetCount = snippets.size,
                    rememberedAssistantMessageIds = rememberedAssistantMessageIds,
                    memoryActionBusyMessageIds = transient.memoryBusyMessageIds,
                    isMemoryPreviewing = transient.memoryPreviewing,
                    isApplyingMemoryCompaction = transient.memoryApplying,
                    canApplyMemoryCompaction = canApplyMemoryCompaction,
                    memoryCompactionBeforeCount = transient.memoryPreview?.beforeCount,
                    memoryCompactionAfterCount = transient.memoryPreview?.afterCount,
                    memoryCompactionFacts = transient.memoryPreview?.facts.orEmpty()
                )
            }.collect { next ->
                _uiState.update { next }
                if (_uiState.value.activeConversationId != null) {
                    activeConversationId.update { it ?: _uiState.value.activeConversationId }
                }
            }
        }

    private fun AssistantDetectedAction.toActionItem(): AssistantDetectedActionItem {
        val titleValue = when (type) {
            AssistantActionTypes.CREATE_TASK_TEMPLATE -> {
                title?.ifBlank { null } ?: "Create task template"
            }

            AssistantActionTypes.COMPLETE_TASK -> {
                taskTitle?.ifBlank { null } ?: title?.ifBlank { null } ?: "Complete task"
            }

            AssistantActionTypes.LOG_DOSING -> {
                product?.ifBlank { null }?.let { "Log dosing: $it" } ?: "Log dosing"
            }

            AssistantActionTypes.LOG_PARAMETERS -> "Log water parameters"
            AssistantActionTypes.ADD_ISSUE -> issueTitle?.ifBlank { null } ?: "Add issue"
            AssistantActionTypes.ADD_MEMO -> memoContent?.ifBlank { null }?.take(48) ?: "Add memo"
            AssistantActionTypes.SAVE_REMINDER_SETTINGS -> "Update reminder settings"
            AssistantActionTypes.ADD_AQUARIUM -> title?.ifBlank { null } ?: "Add aquarium"
            AssistantActionTypes.ADD_LIVESTOCK -> livestockName?.ifBlank { null } ?: "Add livestock"
            AssistantActionTypes.ADD_ASSET -> brandModel?.ifBlank { null } ?: "Add asset"
            AssistantActionTypes.ADD_CONSUMABLE -> consumableName?.ifBlank { null } ?: "Add consumable"
            AssistantActionTypes.CONSUME_CONSUMABLE -> consumableName?.ifBlank { null } ?: "Consume consumable"
            AssistantActionTypes.SET_ISSUE_STATUS -> issueTitle?.ifBlank { null } ?: "Set issue status"
            else -> title?.ifBlank { null } ?: type
        }

        val subtitleParts = buildList {
            aquariumName?.takeIf { it.isNotBlank() }?.let { add("Tank: $it") }
            frequency?.takeIf { it.isNotBlank() }?.let { add("Freq: $it") }
            amountMl?.takeIf { it > 0.0 }?.let { add("Amount: ${it}ml") }
            amountUsed?.takeIf { it > 0.0 }?.let { add("Used: ${it}") }
            quantity?.takeIf { it > 0 }?.let { add("Qty: $it") }
            reminderHour?.let { add("Hour: $it") }
            if (reminderHours.isNotEmpty()) {
                add("Hours: ${reminderHours.joinToString(",")}")
            }
            issueStatus?.let { add("Status: ${it.name.lowercase()}") }
            if (validationErrors.isNotEmpty()) {
                add("Needs fixes")
            }
        }

        return AssistantDetectedActionItem(
            id = id,
            type = type,
            title = titleValue,
            subtitle = subtitleParts.joinToString(" • "),
            approved = approved,
            confidence = confidence,
            validationErrors = validationErrors
        )
    }

    private fun resolveActiveConversation(
        conversations: List<AssistantConversation>
    ): AssistantConversation? {
        val current = conversations.firstOrNull { it.id == activeConversationId.value }
        return current ?: conversations.firstOrNull()
    }

    private fun nowIso(): String = nowProvider().toString()

    private fun mergeDictationIntoComposer(transcript: String): String {
        val cleanedTranscript = transcript
            .replace(Regex("\\s+"), " ")
            .trim()

        if (cleanedTranscript.isBlank()) {
            return dictationBaseComposerText
        }

        return listOf(dictationBaseComposerText, cleanedTranscript)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .trim()
    }

    private fun formatDateTime(value: String): String {
        val instant = runCatching { Instant.parse(value) }.getOrNull() ?: return value
        return dateTimeFormatter.format(instant.atZone(zoneId))
    }

    private fun launchWork(block: suspend () -> Unit): Job =
        (externalScope ?: viewModelScope).launch {
            block()
        }

    private fun setMemoryBusy(messageId: String, busy: Boolean) {
        memoryActionBusyMessageIds.update { current ->
            if (busy) {
                current + messageId
            } else {
                current - messageId
            }
        }
    }

    internal fun disposeForTests() {
        observerJob?.cancel()
        generationJob?.cancel()
        assistantDictationController.release()
        generationJob = null
    }

    override fun onCleared() {
        super.onCleared()
        assistantDictationController.release()
    }

    companion object {
        private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

        fun factory(
            assistantConversationsStore: AssistantConversationsStore,
            appSettingsStore: AppSettingsStore,
            assistantMemoryStore: AssistantMemoryStore,
            assistantGateway: AssistantGateway,
            assistantActionReviewService: AssistantActionReviewService,
            assistantDictationController: AssistantDictationController =
                NoOpAssistantDictationController
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(AssistantViewModel::class.java)) {
                        return AssistantViewModel(
                            assistantConversationsStore = assistantConversationsStore,
                            appSettingsStore = appSettingsStore,
                            assistantMemoryStore = assistantMemoryStore,
                            assistantGateway = assistantGateway,
                            assistantActionReviewService = assistantActionReviewService,
                            assistantDictationController = assistantDictationController
                        ) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
    }
}