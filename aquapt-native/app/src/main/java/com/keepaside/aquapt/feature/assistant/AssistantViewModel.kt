package com.keepaside.aquapt.feature.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.keepaside.aquapt.core.assistant.AssistantActionReviewService
import com.keepaside.aquapt.core.assistant.AssistantGateway
import com.keepaside.aquapt.core.assistant.AssistantGatewayMessage
import com.keepaside.aquapt.core.assistant.AssistantGatewayRequest
import com.keepaside.aquapt.core.model.AssistantActionTypes
import com.keepaside.aquapt.core.model.AssistantChatMessage
import com.keepaside.aquapt.core.model.AssistantConversation
import com.keepaside.aquapt.core.model.AssistantDetectedAction
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
    val streamingId: String?,
    val executingActions: Boolean
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
    val isExecutingActions: Boolean = false,
    val approvedActionCount: Int = 0,
    val canExecuteApprovedActions: Boolean = false,
    val hasDetectedActions: Boolean = false
)

class AssistantViewModel(
    private val assistantConversationsStore: AssistantConversationsStore,
    private val appSettingsStore: AppSettingsStore,
    private val assistantGateway: AssistantGateway,
    private val assistantActionReviewService: AssistantActionReviewService,
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
    private val isExecutingActions = MutableStateFlow(false)

    private val _uiState = MutableStateFlow(AssistantUiState())
    val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()

    private var observerJob: Job? = null
    private var generationJob: Job? = null

    private fun isBusy(): Boolean = isSending.value || isExecutingActions.value

    init {
        bootstrapConversationsIfNeeded()
        observerJob = observeUiState()
    }

    fun onComposerTextChanged(value: String) {
        if (isBusy()) return
        composerText.update { value }
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

                val extraction = assistantActionReviewService.parseAssistantActionExtraction(
                    responseContent = finalText,
                    transcript = normalizedPrompt,
                    sourceMessageId = assistantDraftId
                )

                mutateConversation(activeConversation.id) { conversation ->
                    conversation.copy(
                        messages = conversation.messages.map { message ->
                            if (message.id == assistantDraftId) {
                                val current = message.content.trim()
                                val next = finalText.ifBlank { current }
                                message.copy(
                                    content = next,
                                    detectedActionIds = extraction.actions.map { action -> action.id }
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
                activeStreamingMessageId,
                isExecutingActions
            ) { values ->
                @Suppress("UNCHECKED_CAST")
                AssistantTransientState(
                    activeId = values[0] as String?,
                    composer = values[1] as String,
                    status = values[2] as String,
                    error = values[3] as String?,
                    sending = values[4] as Boolean,
                    streamingId = values[5] as String?,
                    executingActions = values[6] as Boolean
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

                AssistantUiState(
                    isLoading = false,
                    statusMessage = transient.status,
                    assistantError = transient.error,
                    conversationItems = items,
                    activeConversationId = activeConversation?.id,
                    activeConversationTitle = activeConversation?.title ?: assistantDefaultConversationTitle,
                    activeConversationPinned = activeConversation?.pinned ?: false,
                    messages = activeConversation?.messages.orEmpty(),
                    detectedActions = detectedActions,
                    actionWarnings = activeConversation?.warnings.orEmpty(),
                    composerText = transient.composer,
                    canSend = transient.composer.trim().isNotEmpty() &&
                        activeConversation != null &&
                        !transient.sending &&
                        !transient.executingActions,
                    isSending = transient.sending,
                    activeStreamingMessageId = transient.streamingId,
                    canStopGeneration = transient.sending && !transient.streamingId.isNullOrBlank(),
                    isExecutingActions = transient.executingActions,
                    approvedActionCount = approvedActionCount,
                    canExecuteApprovedActions = approvedActionCount > 0 && !transient.sending,
                    hasDetectedActions = detectedActions.isNotEmpty()
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
            else -> title?.ifBlank { null } ?: type
        }

        val subtitleParts = buildList {
            aquariumName?.takeIf { it.isNotBlank() }?.let { add("Tank: $it") }
            frequency?.takeIf { it.isNotBlank() }?.let { add("Freq: $it") }
            amountMl?.takeIf { it > 0.0 }?.let { add("Amount: ${it}ml") }
            reminderHour?.let { add("Hour: $it") }
            if (reminderHours.isNotEmpty()) {
                add("Hours: ${reminderHours.joinToString(",")}")
            }
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
            assistantGateway: AssistantGateway,
            assistantActionReviewService: AssistantActionReviewService
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(AssistantViewModel::class.java)) {
                        return AssistantViewModel(
                            assistantConversationsStore = assistantConversationsStore,
                            appSettingsStore = appSettingsStore,
                            assistantGateway = assistantGateway,
                            assistantActionReviewService = assistantActionReviewService
                        ) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
    }
}