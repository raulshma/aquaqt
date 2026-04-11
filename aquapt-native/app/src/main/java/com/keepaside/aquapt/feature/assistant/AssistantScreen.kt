package com.keepaside.aquapt.feature.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.keepaside.aquapt.core.model.AssistantMessageRole
import com.keepaside.aquapt.core.repository.AssistantConversationsStore
import org.koin.java.KoinJavaComponent

@Composable
fun AssistantScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp)
) {
    val assistantStore: AssistantConversationsStore = remember {
        KoinJavaComponent.get(AssistantConversationsStore::class.java)
    }
    val viewModel: AssistantViewModel = viewModel(
        factory = remember(assistantStore) {
            AssistantViewModel.factory(assistantStore)
        }
    )
    val uiState by viewModel.uiState.collectAsState()

    var renameInput by remember(uiState.activeConversationId, uiState.activeConversationTitle) {
        mutableStateOf(uiState.activeConversationTitle)
    }

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

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = viewModel::createConversation) {
                    Text("New chat")
                }

                OutlinedButton(
                    onClick = {
                        uiState.activeConversationId?.let(viewModel::togglePinConversation)
                    },
                    enabled = uiState.activeConversationId != null
                ) {
                    Text(if (uiState.activeConversationPinned) "Unpin" else "Pin")
                }

                OutlinedButton(
                    onClick = {
                        uiState.activeConversationId?.let(viewModel::deleteConversation)
                    },
                    enabled = uiState.activeConversationId != null
                ) {
                    Text("Delete")
                }
            }

            OutlinedTextField(
                value = renameInput,
                onValueChange = { renameInput = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Conversation title") },
                singleLine = true
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
                    enabled = uiState.activeConversationId != null
                ) {
                    Text("Rename")
                }

                OutlinedButton(
                    onClick = { renameInput = uiState.activeConversationTitle },
                    enabled = uiState.activeConversationId != null
                ) {
                    Text("Reset")
                }
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(uiState.conversationItems, key = { it.id }) { conversation ->
                    FilterChip(
                        selected = uiState.activeConversationId == conversation.id,
                        onClick = { viewModel.selectConversation(conversation.id) },
                        label = {
                            Text(
                                text = if (conversation.isPinned) "📌 ${conversation.title}" else conversation.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
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

                                Text(
                                    text = message.createdAt,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
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
                    maxLines = 5
                )

                Button(
                    onClick = viewModel::sendMessage,
                    enabled = uiState.canSend
                ) {
                    Text("Send")
                }
            }
        }
    }
}