package com.keepaside.aquapt.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.keepaside.aquapt.core.repository.AppSettingsStore
import com.keepaside.aquapt.core.repository.AssistantMemoryStore
import org.koin.java.KoinJavaComponent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsMemoryScreen(
    onBack: () -> Unit = {}
) {
    val appSettingsStore: AppSettingsStore = remember {
        KoinJavaComponent.get(AppSettingsStore::class.java)
    }
    val assistantMemoryStore: AssistantMemoryStore = remember {
        KoinJavaComponent.get(AssistantMemoryStore::class.java)
    }

    val viewModel: SettingsMemoryViewModel = viewModel(
        factory = remember(appSettingsStore, assistantMemoryStore) {
            SettingsMemoryViewModel.factory(
                appSettingsStore = appSettingsStore,
                assistantMemoryStore = assistantMemoryStore
            )
        }
    )

    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            TextButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Settings")
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Assistant memory",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "Review snippets, clear stale memory, and compact durable facts.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = uiState.memoryEnabled,
                onClick = { viewModel.setMemoryEnabled(true) },
                enabled = !uiState.isBusy,
                label = { Text("Memory ON") }
            )
            FilterChip(
                selected = !uiState.memoryEnabled,
                onClick = { viewModel.setMemoryEnabled(false) },
                enabled = !uiState.isBusy,
                label = { Text("Memory OFF") }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = viewModel::refreshSnippets,
                enabled = uiState.canRefresh
            ) {
                Text("Refresh")
            }

            OutlinedButton(
                onClick = viewModel::clearAllSnippets,
                enabled = uiState.canClearAll
            ) {
                Text("Clear all")
            }

            OutlinedButton(
                onClick = viewModel::previewCompaction,
                enabled = uiState.canPreviewCompaction
            ) {
                Text("Preview compact")
            }
        }

        if (uiState.isBusy || uiState.isPreviewing || uiState.isApplying) {
            CircularProgressIndicator()
        }

        Text(
            text = uiState.statusMessage,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        uiState.preview?.let { preview ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Compaction preview",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "${preview.beforeCount} snippets → ${preview.afterCount} durable fact(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    preview.facts.forEachIndexed { index, fact ->
                        Text(
                            text = "${index + 1}. $fact",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = viewModel::applyCompaction,
                            enabled = uiState.canApplyCompaction
                        ) {
                            Text("Apply")
                        }

                        OutlinedButton(
                            onClick = viewModel::cancelCompactionPreview,
                            enabled = !uiState.isApplying
                        ) {
                            Text("Cancel")
                        }
                    }
                }
            }
        }

        if (!uiState.memoryEnabled) {
            Text(
                text = "Enable assistant memory to store and review snippets.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else if (uiState.snippets.isEmpty() && !uiState.isLoading) {
            Text(
                text = "No memory snippets saved yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        uiState.snippets.forEach { snippet ->
            Card(
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
                        text = snippet.content,
                        style = MaterialTheme.typography.bodySmall
                    )

                    Text(
                        text = "${snippet.categoryLabel} • ${snippet.createdAtLabel}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    snippet.sourceLabel?.let { source ->
                        Text(
                            text = source,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    TextButton(
                        onClick = { viewModel.forgetSnippet(snippet.id) },
                        enabled = !uiState.isBusy && !uiState.isPreviewing && !uiState.isApplying
                    ) {
                        Text("Forget")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}
