package com.keepaside.aquapt.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsModelBrowserScreen(
    initialTarget: ModelBrowserTarget = ModelBrowserTarget.ASSISTANT,
    initialModelId: String? = null,
    onModelSelected: (ModelBrowserTarget, String) -> Unit = { _, _ -> },
    onBack: () -> Unit = {}
) {
    val viewModel: SettingsModelBrowserViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.onTargetChanged(initialTarget)
        viewModel.onModelSelected(initialModelId ?: "")
        viewModel.loadModels()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Browse models",
                style = MaterialTheme.typography.headlineSmall
            )
        }

        Text(
            text = state.summaryLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ModelBrowserTarget.entries.forEach { target ->
                FilterChip(
                    selected = state.target == target,
                    onClick = { viewModel.onTargetChanged(target) },
                    label = {
                        Text(
                            when (target) {
                                ModelBrowserTarget.ASSISTANT -> "Assistant"
                                ModelBrowserTarget.MEMORY -> "Memory"
                            }
                        )
                    }
                )
            }
        }

        OutlinedTextField(
            value = state.query,
            onValueChange = { viewModel.onQueryChanged(it) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Filter by model name or ID") },
            singleLine = true
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ModelBrowserSort.entries.forEach { sort ->
                FilterChip(
                    selected = state.sort == sort,
                    onClick = { viewModel.onSortChanged(sort) },
                    label = {
                        Text(
                            when (sort) {
                                ModelBrowserSort.NAME -> "Name"
                                ModelBrowserSort.CREATED -> "Created"
                                ModelBrowserSort.CONTEXT -> "Context"
                            }
                        )
                    }
                )
            }

            IconButton(
                onClick = { viewModel.refreshModels() },
                enabled = !state.isLoading
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Refresh models"
                )
            }
        }

        if (state.isLoading) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
            }
        }

        state.errorMessage?.let { error ->
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }

        if (state.isTruncated) {
            Text(
                text = "Showing the first $MAX_MODELS_PER_GROUP models per group.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        val grouped = state.groupedModels

        if (grouped.free.isNotEmpty()) {
            Text(
                text = "Free models (${grouped.free.size})",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            grouped.free.forEach { model ->
                ModelItemCard(
                    model = model,
                    isSelected = state.selectedModelId == model.id,
                    onSelect = {
                        viewModel.onModelSelected(model.id)
                        onModelSelected(state.target, model.id)
                    }
                )
            }
        }

        if (grouped.paid.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Paid models (${grouped.paid.size})",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            grouped.paid.forEach { model ->
                ModelItemCard(
                    model = model,
                    isSelected = state.selectedModelId == model.id,
                    onSelect = {
                        viewModel.onModelSelected(model.id)
                        onModelSelected(state.target, model.id)
                    }
                )
            }
        }

        if (!state.isLoading && grouped.free.isEmpty() && grouped.paid.isEmpty() && state.errorMessage == null) {
            Text(
                text = if (state.models.isEmpty()) "No models loaded. Tap refresh to load available models."
                else "No models match your filter.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ModelItemCard(
    model: com.keepaside.aquapt.core.assistant.OpenRouterModel,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val displayName = model.name ?: model.id
    val contextLabel = model.contextLength?.toString() ?: "-"
    val dateLabel = formatModelCreatedDate(model.created)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable(onClick = onSelect),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text(
                text = displayName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = model.id,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "$contextLabel ctx",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = dateLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
