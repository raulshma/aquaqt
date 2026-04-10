package com.keepaside.aquapt.feature.timeline

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.keepaside.aquapt.core.model.TimelineEventType
import com.keepaside.aquapt.core.repository.AquariumRepository
import com.keepaside.aquapt.core.repository.MemoRepository
import com.keepaside.aquapt.core.repository.TimelineEventRepository
import org.koin.java.KoinJavaComponent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp)
) {
    val aquariumRepository: AquariumRepository = remember {
        KoinJavaComponent.get(AquariumRepository::class.java)
    }
    val timelineEventRepository: TimelineEventRepository = remember {
        KoinJavaComponent.get(TimelineEventRepository::class.java)
    }
    val memoRepository: MemoRepository = remember {
        KoinJavaComponent.get(MemoRepository::class.java)
    }

    val viewModel: TimelineViewModel = viewModel(
        factory = remember(aquariumRepository, timelineEventRepository, memoRepository) {
            TimelineViewModel.factory(
                aquariumRepository = aquariumRepository,
                timelineEventRepository = timelineEventRepository,
                memoRepository = memoRepository
            )
        }
    )

    val uiState by viewModel.uiState.collectAsState()
    var showQuickMemoSheet by rememberSaveable { mutableStateOf(false) }
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        viewModel.onQuickMemoPhotoUriChanged(uri?.toString())
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

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Activity timeline",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = uiState.headline,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SummaryTile(
                                modifier = Modifier.weight(1f),
                                title = "Visible",
                                value = uiState.summary.visibleEventCount.toString()
                            )
                            SummaryTile(
                                modifier = Modifier.weight(1f),
                                title = "Memos",
                                value = uiState.summary.memoCount.toString()
                            )
                            SummaryTile(
                                modifier = Modifier.weight(1f),
                                title = "Issues",
                                value = uiState.summary.issueCount.toString()
                            )
                        }

                        FilledTonalButton(
                            onClick = {
                                viewModel.prepareQuickMemo()
                                showQuickMemoSheet = true
                            },
                            enabled = uiState.aquariumFilters.isNotEmpty()
                        ) {
                            Text("Quick memo")
                        }
                    }
                }
            }

            item {
                TimelineFilters(
                    uiState = uiState,
                    onAquariumSelected = viewModel::onAquariumFilterSelected,
                    onTypeSelected = viewModel::onTypeFilterSelected
                )
            }

            if (uiState.isEmpty) {
                item {
                    Card {
                        Text(
                            text = if (uiState.aquariumFilters.isEmpty()) {
                                "No tanks yet. Import a backup in Settings or add a tank in an upcoming creation flow."
                            } else {
                                "No timeline activity yet. Imported events and quick memos will appear here."
                            },
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

            if (uiState.dayGroups.isEmpty() && !uiState.isEmpty) {
                item {
                    Card {
                        Text(
                            text = "No events match these filters.",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

            uiState.dayGroups.forEach { group ->
                item(key = group.dateLabel) {
                    TimelineDayGroupCard(group)
                }
            }

            uiState.statusMessage?.let { message ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Text(
                            text = message,
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
        }

        if (showQuickMemoSheet) {
            ModalBottomSheet(
                onDismissRequest = { showQuickMemoSheet = false }
            ) {
                QuickMemoSheet(
                    uiState = uiState,
                    onAquariumSelected = viewModel::onQuickMemoAquariumSelected,
                    onContentChanged = viewModel::onQuickMemoContentChanged,
                    onCreatedAtChanged = viewModel::onQuickMemoCreatedAtChanged,
                    onPickPhoto = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest.Builder()
                                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                .build()
                        )
                    },
                    onRemovePhoto = { viewModel.onQuickMemoPhotoUriChanged(null) },
                    onSave = {
                        viewModel.saveQuickMemo()
                        showQuickMemoSheet = false
                    },
                    onCancel = { showQuickMemoSheet = false }
                )
            }
        }
    }
}

@Composable
private fun TimelineFilters(
    uiState: TimelineUiState,
    onAquariumSelected: (String?) -> Unit,
    onTypeSelected: (TimelineEventType?) -> Unit
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Filters",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = uiState.selectedAquariumId == null,
                        onClick = { onAquariumSelected(null) },
                        label = { Text("All tanks") }
                    )
                }
                items(uiState.aquariumFilters, key = { it.aquariumId }) { aquarium ->
                    FilterChip(
                        selected = uiState.selectedAquariumId == aquarium.aquariumId,
                        onClick = { onAquariumSelected(aquarium.aquariumId) },
                        label = { Text(aquarium.aquariumName, maxLines = 1) }
                    )
                }
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = uiState.selectedType == null,
                        onClick = { onTypeSelected(null) },
                        label = { Text("All types") }
                    )
                }
                items(uiState.typeFilters, key = { it.type.name }) { filter ->
                    FilterChip(
                        selected = uiState.selectedType == filter.type,
                        onClick = { onTypeSelected(filter.type) },
                        label = { Text(filter.label) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineDayGroupCard(group: TimelineDayGroup) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = group.dateLabel,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            group.events.forEach { event ->
                TimelineEventRow(event)
            }
        }
    }
}

@Composable
private fun TimelineEventRow(event: TimelineEventItem) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = event.title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            AssistChip(
                onClick = {},
                label = { Text(event.typeLabel) }
            )
        }

        Text(
            text = "${event.createdAtLabel} - ${event.aquariumName}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        event.description?.takeIf { it.isNotBlank() }?.let { description ->
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (event.photoUri != null || event.relatedCount > 0) {
            Text(
                text = buildList {
                    if (event.photoUri != null) add("Photo attached")
                    if (event.relatedCount > 0) add("${event.relatedCount} linked item${if (event.relatedCount == 1) "" else "s"}")
                }.joinToString(" - "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun QuickMemoSheet(
    uiState: TimelineUiState,
    onAquariumSelected: (String) -> Unit,
    onContentChanged: (String) -> Unit,
    onCreatedAtChanged: (String) -> Unit,
    onPickPhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Quick memo",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(uiState.aquariumFilters, key = { it.aquariumId }) { aquarium ->
                FilterChip(
                    selected = uiState.quickMemoDraft.aquariumId == aquarium.aquariumId,
                    onClick = { onAquariumSelected(aquarium.aquariumId) },
                    label = { Text(aquarium.aquariumName, maxLines = 1) }
                )
            }
        }

        OutlinedTextField(
            value = uiState.quickMemoDraft.content,
            onValueChange = onContentChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Memo") },
            minLines = 3
        )

        OutlinedTextField(
            value = uiState.quickMemoDraft.createdAtInput,
            onValueChange = onCreatedAtChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Memo time") },
            supportingText = { Text("Use yyyy-MM-dd HH:mm") },
            singleLine = true
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = onPickPhoto) {
                Text(if (uiState.quickMemoDraft.photoUri == null) "Attach photo" else "Change photo")
            }
            uiState.quickMemoDraft.photoUri?.let {
                TextButton(onClick = onRemovePhoto) {
                    Text("Remove")
                }
            }
        }

        uiState.quickMemoDraft.photoUri?.let {
            Text(
                text = "Photo selected",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
        ) {
            OutlinedButton(onClick = onCancel) {
                Text("Cancel")
            }
            FilledTonalButton(
                onClick = onSave,
                enabled = uiState.quickMemoDraft.aquariumId != null &&
                    uiState.quickMemoDraft.content.isNotBlank() &&
                    uiState.quickMemoDraft.createdAtInput.isNotBlank()
            ) {
                Text("Save")
            }
        }
    }
}

@Composable
private fun SummaryTile(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
