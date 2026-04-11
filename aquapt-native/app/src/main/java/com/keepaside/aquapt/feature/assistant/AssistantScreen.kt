package com.keepaside.aquapt.feature.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.keepaside.aquapt.core.assistant.AssistantActionReviewService
import com.keepaside.aquapt.core.assistant.AssistantGateway
import com.keepaside.aquapt.core.model.AssistantMessageRole
import com.keepaside.aquapt.core.repository.AssistantConversationsStore
import com.keepaside.aquapt.core.repository.AssistantMemoryStore
import com.keepaside.aquapt.core.repository.AppSettingsStore
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent

@Composable
fun AssistantScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp)
) {
    val assistantStore: AssistantConversationsStore = remember {
        KoinJavaComponent.get(AssistantConversationsStore::class.java)
    }
    val appSettingsStore: AppSettingsStore = remember {
        KoinJavaComponent.get(AppSettingsStore::class.java)
    }
    val assistantMemoryStore: AssistantMemoryStore = remember {
        KoinJavaComponent.get(AssistantMemoryStore::class.java)
    }
    val assistantGateway: AssistantGateway = remember {
        KoinJavaComponent.get(AssistantGateway::class.java)
    }
    val assistantActionReviewService: AssistantActionReviewService = remember {
        KoinJavaComponent.get(AssistantActionReviewService::class.java)
    }

    val viewModel: AssistantViewModel = viewModel(
        factory = remember(
            assistantStore,
            appSettingsStore,
            assistantMemoryStore,
            assistantGateway,
            assistantActionReviewService
        ) {
            AssistantViewModel.factory(
                assistantConversationsStore = assistantStore,
                appSettingsStore = appSettingsStore,
                assistantMemoryStore = assistantMemoryStore,
                assistantGateway = assistantGateway,
                assistantActionReviewService = assistantActionReviewService
            )
        }
    )
    val uiState by viewModel.uiState.collectAsState()
    val assistantError = uiState.assistantError
    val controlsEnabled =
        !uiState.isSending && !uiState.isExecutingActions && !uiState.isApplyingMemoryCompaction
    val clipboardManager = LocalClipboardManager.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val drawerScope = rememberCoroutineScope()

    var renameInput by remember(uiState.activeConversationId, uiState.activeConversationTitle) {
        mutableStateOf(uiState.activeConversationTitle)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Conversations",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    OutlinedTextField(
                        value = uiState.conversationSearchQuery,
                        onValueChange = viewModel::onConversationSearchQueryChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Search conversations") },
                        singleLine = true,
                        enabled = controlsEnabled
                    )

                    if (uiState.conversationSearchQuery.isNotBlank()) {
                        OutlinedButton(
                            onClick = viewModel::clearConversationSearchQuery,
                            enabled = controlsEnabled
                        ) {
                            Text("Clear search")
                        }
                    }

                    Text(
                        text = "${uiState.visibleConversationCount} of ${uiState.totalConversationCount} shown",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (uiState.conversationItems.isEmpty()) {
                        Text(
                            text = "No conversations match your search.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(uiState.conversationItems, key = { it.id }) { conversation ->
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    FilterChip(
                                        selected = uiState.activeConversationId == conversation.id,
                                        onClick = {
                                            viewModel.selectConversation(conversation.id)
                                            drawerScope.launch { drawerState.close() }
                                        },
                                        enabled = controlsEnabled,
                                        label = {
                                            Text(
                                                text = buildString {
                                                    if (conversation.isPinned) {
                                                        append("📌 ")
                                                    }
                                                    append(conversation.title)
                                                    append(" • ")
                                                    append(conversation.messageCount)
                                                    append(" msg")
                                                },
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    )

                                    conversation.lastMessagePreview
                                        ?.takeIf { it.isNotBlank() }
                                        ?.let { preview ->
                                            Text(
                                                text = preview,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                }
                            }
                        }
                    }
                }
            }
        }
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(contentPadding)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                return@Box
            }

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
            Text(
                text = "Assistant",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = uiState.statusMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!assistantError.isNullOrBlank()) {
                Text(
                    text = assistantError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { drawerScope.launch { drawerState.open() } },
                    enabled = controlsEnabled
                ) {
                    Text("Browse")
                }

                Button(
                    onClick = viewModel::createConversation,
                    enabled = controlsEnabled
                ) {
                    Text("New chat")
                }

                OutlinedButton(
                    onClick = {
                        uiState.activeConversationId?.let(viewModel::togglePinConversation)
                    },
                    enabled = uiState.activeConversationId != null && controlsEnabled
                ) {
                    Text(if (uiState.activeConversationPinned) "Unpin" else "Pin")
                }

                OutlinedButton(
                    onClick = {
                        uiState.activeConversationId?.let(viewModel::deleteConversation)
                    },
                    enabled = uiState.activeConversationId != null && controlsEnabled
                ) {
                    Text("Delete")
                }
            }

            OutlinedTextField(
                value = renameInput,
                onValueChange = { renameInput = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Conversation title") },
                singleLine = true,
                enabled = controlsEnabled
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        uiState.activeConversationId?.let { conversationId ->
                            viewModel.renameConversation(conversationId, renameInput)
                        }
                    },
                    enabled = uiState.activeConversationId != null && controlsEnabled
                ) {
                    Text("Rename")
                }

                OutlinedButton(
                    onClick = { renameInput = uiState.activeConversationTitle },
                    enabled = uiState.activeConversationId != null && controlsEnabled
                ) {
                    Text("Reset")
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Text(
                    text = "${uiState.messages.size} messages in this conversation",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            if (uiState.hasDetectedActions || uiState.actionWarnings.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Action review",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )

                        uiState.actionWarnings.forEach { warning ->
                            Text(
                                text = warning,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        if (!uiState.hasDetectedActions) {
                            Text(
                                text = "No detected actions in the active thread yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        uiState.detectedActions.forEach { action ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Checkbox(
                                        checked = action.approved,
                                        enabled = controlsEnabled && action.validationErrors.isEmpty(),
                                        onCheckedChange = { checked ->
                                            viewModel.toggleActionApproval(action.id, checked)
                                        }
                                    )

                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = action.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )

                                        Text(
                                            text = "${action.type} • ${(action.confidence * 100).toInt()}% confidence",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        if (action.subtitle.isNotBlank()) {
                                            Text(
                                                text = action.subtitle,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        action.validationErrors.forEach { validationError ->
                                            Text(
                                                text = validationError,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = viewModel::approveAllValidActions,
                                enabled = controlsEnabled && uiState.hasDetectedActions
                            ) {
                                Text("Approve valid")
                            }

                            Button(
                                onClick = viewModel::executeApprovedActions,
                                enabled = controlsEnabled && uiState.canExecuteApprovedActions
                            ) {
                                Text("Execute (${uiState.approvedActionCount})")
                            }
                        }

                        if (uiState.isExecutingActions) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.padding(2.dp))
                                Text(
                                    text = "Executing approved actions…",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Assistant memory",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        text = if (uiState.assistantMemoryEnabled) {
                            "${uiState.assistantMemorySnippetCount} snippet(s) saved${uiState.assistantMemoryModel?.takeIf { it.isNotBlank() }?.let { " • model $it" } ?: ""}"
                        } else {
                            "Memory is disabled in Settings preferences."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (uiState.assistantMemoryEnabled) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.previewMemoryCompaction() },
                                enabled = controlsEnabled && !uiState.isMemoryPreviewing
                            ) {
                                Text("Preview compaction")
                            }

                            Button(
                                onClick = { viewModel.applyMemoryCompaction() },
                                enabled = controlsEnabled && uiState.canApplyMemoryCompaction
                            ) {
                                Text("Apply")
                            }

                            if (uiState.memoryCompactionBeforeCount != null) {
                                OutlinedButton(
                                    onClick = viewModel::dismissMemoryCompactionPreview,
                                    enabled = controlsEnabled && !uiState.isMemoryPreviewing
                                ) {
                                    Text("Dismiss")
                                }
                            }
                        }

                        if (uiState.isMemoryPreviewing) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.padding(2.dp))
                                Text(
                                    text = "Building memory compaction preview…",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        if (uiState.isApplyingMemoryCompaction) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.padding(2.dp))
                                Text(
                                    text = "Applying memory compaction…",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        if (
                            uiState.memoryCompactionBeforeCount != null &&
                            uiState.memoryCompactionAfterCount != null
                        ) {
                            Text(
                                text = "Preview: ${uiState.memoryCompactionBeforeCount} → ${uiState.memoryCompactionAfterCount} facts",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            uiState.memoryCompactionFacts.take(5).forEach { fact ->
                                Text(
                                    text = "• $fact",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            if (uiState.messages.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text = "Start by asking for a tank review, task plan, or issue triage.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.messages, key = { it.id }) { message ->
                    val isUser = message.role == AssistantMessageRole.USER
                    val isRemembered = message.id in uiState.rememberedAssistantMessageIds
                    val isMemoryBusy = message.id in uiState.memoryActionBusyMessageIds
                    val containerColor = when (message.role) {
                        AssistantMessageRole.USER -> MaterialTheme.colorScheme.primaryContainer
                        AssistantMessageRole.ASSISTANT -> MaterialTheme.colorScheme.tertiaryContainer
                        AssistantMessageRole.SYSTEM -> MaterialTheme.colorScheme.surfaceVariant
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(0.86f),
                            colors = CardDefaults.cardColors(containerColor = containerColor)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = when (message.role) {
                                        AssistantMessageRole.USER -> "You"
                                        AssistantMessageRole.ASSISTANT -> "Assistant"
                                        AssistantMessageRole.SYSTEM -> "System"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )

                                Text(
                                    text = message.content,
                                    style = MaterialTheme.typography.bodyMedium
                                )

                                if (message.requestFailed && !message.requestError.isNullOrBlank()) {
                                    Text(
                                        text = message.requestError,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }

                                if (
                                    message.role == AssistantMessageRole.ASSISTANT &&
                                    message.responseTelemetry != null
                                ) {
                                    val telemetry = message.responseTelemetry

                                    val lineOneParts = buildList {
                                        telemetry?.model?.takeIf { it.isNotBlank() }?.let { add(it) }
                                        telemetry?.providerName?.takeIf { it.isNotBlank() }?.let { add("via $it") }
                                        telemetry?.router?.takeIf { it.isNotBlank() }?.let { add(it) }
                                        telemetry?.totalTokens?.let { add("$it tok") }
                                        telemetry?.cost?.let { add("$${"%.4f".format(it)}") }
                                    }

                                    val lineTwoParts = buildList {
                                        telemetry?.latencyMs?.let { add("lat ${it}ms") }
                                            ?: telemetry?.elapsedMs?.let { add("${it}ms") }
                                        telemetry?.generationTimeMs?.let { add("gen ${it}ms") }
                                        telemetry?.throughputCharsPerSecond?.let {
                                            add("${"%.1f".format(it)} ch/s")
                                        }
                                        telemetry?.throughputTokensPerSecond?.let {
                                            add("${"%.1f".format(it)} tok/s")
                                        }
                                        telemetry?.finishReason
                                            ?.takeIf { it.isNotBlank() }
                                            ?.let { add(it) }
                                    }

                                    if (lineOneParts.isNotEmpty()) {
                                        Text(
                                            text = lineOneParts.joinToString(" • "),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    if (lineTwoParts.isNotEmpty()) {
                                        Text(
                                            text = lineTwoParts.joinToString(" • "),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Text(
                                    text = message.createdAt,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (message.content.isNotBlank()) {
                                        OutlinedButton(
                                            onClick = {
                                                clipboardManager.setText(AnnotatedString(message.content))
                                                viewModel.onMessageCopied(message.role)
                                            },
                                            enabled = controlsEnabled
                                        ) {
                                            Text("Copy")
                                        }

                                        OutlinedButton(
                                            onClick = { viewModel.reuseMessageAsPrompt(message.id) },
                                            enabled = controlsEnabled
                                        ) {
                                            Text("Reuse")
                                        }
                                    }

                                    if (message.role == AssistantMessageRole.USER && message.requestFailed) {
                                        OutlinedButton(
                                            onClick = { viewModel.retryFailedMessage(message.id) },
                                            enabled = controlsEnabled
                                        ) {
                                            Text("Retry")
                                        }
                                    }

                                    if (message.role == AssistantMessageRole.ASSISTANT) {
                                        OutlinedButton(
                                            onClick = { viewModel.regenerateReply(message.id) },
                                            enabled = controlsEnabled
                                        ) {
                                            Text("Regenerate")
                                        }

                                        if (uiState.assistantMemoryEnabled) {
                                            OutlinedButton(
                                                onClick = {
                                                    if (isRemembered) {
                                                        viewModel.forgetAssistantMessageMemory(message.id)
                                                    } else {
                                                        viewModel.rememberAssistantMessage(message.id)
                                                    }
                                                },
                                                enabled = controlsEnabled && !isMemoryBusy
                                            ) {
                                                Text(if (isRemembered) "Forget" else "Remember")
                                            }
                                        }
                                    }

                                    if (uiState.isSending && uiState.activeStreamingMessageId == message.id) {
                                        Text(
                                            text = "Streaming…",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(top = 10.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    OutlinedTextField(
                        value = uiState.composerText,
                        onValueChange = viewModel::onComposerTextChanged,
                        modifier = Modifier.weight(1f),
                        label = { Text("Message AquaPT assistant") },
                        minLines = 2,
                        maxLines = 5,
                        enabled = controlsEnabled
                    )

                    if (uiState.canStopGeneration) {
                        OutlinedButton(onClick = viewModel::stopGeneration) {
                            Text("Stop")
                        }
                    } else {
                        Button(
                            onClick = viewModel::sendMessage,
                            enabled = uiState.canSend && controlsEnabled
                        ) {
                            Text("Send")
                        }
                    }
                }
            }
        }
    }
}