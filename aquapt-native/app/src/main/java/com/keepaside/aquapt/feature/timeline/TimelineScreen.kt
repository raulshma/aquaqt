package com.keepaside.aquapt.feature.timeline

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.keepaside.aquapt.core.model.EntityKind
import com.keepaside.aquapt.core.repository.AssetRepository
import com.keepaside.aquapt.core.model.TimelineEventType
import com.keepaside.aquapt.core.repository.AquariumRepository
import com.keepaside.aquapt.core.repository.ConsumableRepository
import com.keepaside.aquapt.core.repository.DosingLogRepository
import com.keepaside.aquapt.core.repository.IssueRepository
import com.keepaside.aquapt.core.repository.LivestockRepository
import com.keepaside.aquapt.core.repository.MemoRepository
import com.keepaside.aquapt.core.repository.TaskExecutionRepository
import com.keepaside.aquapt.core.repository.TaskTemplateRepository
import com.keepaside.aquapt.core.repository.TimelineEventRepository
import com.keepaside.aquapt.core.repository.WaterParameterLogRepository
import org.koin.java.KoinJavaComponent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    onOpenEntityDeepLink: (EntityKind, String, String?) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp)
) {
    val aquariumRepository: AquariumRepository = remember {
        KoinJavaComponent.get(AquariumRepository::class.java)
    }
    val livestockRepository: LivestockRepository = remember {
        KoinJavaComponent.get(LivestockRepository::class.java)
    }
    val timelineEventRepository: TimelineEventRepository = remember {
        KoinJavaComponent.get(TimelineEventRepository::class.java)
    }
    val taskTemplateRepository: TaskTemplateRepository = remember {
        KoinJavaComponent.get(TaskTemplateRepository::class.java)
    }
    val taskExecutionRepository: TaskExecutionRepository = remember {
        KoinJavaComponent.get(TaskExecutionRepository::class.java)
    }
    val memoRepository: MemoRepository = remember {
        KoinJavaComponent.get(MemoRepository::class.java)
    }
    val issueRepository: IssueRepository = remember {
        KoinJavaComponent.get(IssueRepository::class.java)
    }
    val dosingLogRepository: DosingLogRepository = remember {
        KoinJavaComponent.get(DosingLogRepository::class.java)
    }
    val waterParameterLogRepository: WaterParameterLogRepository = remember {
        KoinJavaComponent.get(WaterParameterLogRepository::class.java)
    }
    val assetRepository: AssetRepository = remember {
        KoinJavaComponent.get(AssetRepository::class.java)
    }
    val consumableRepository: ConsumableRepository = remember {
        KoinJavaComponent.get(ConsumableRepository::class.java)
    }

    val viewModel: TimelineViewModel = viewModel(
        factory = remember(
            aquariumRepository,
            livestockRepository,
            timelineEventRepository,
            taskTemplateRepository,
            taskExecutionRepository,
            memoRepository,
            issueRepository,
            dosingLogRepository,
            waterParameterLogRepository,
            assetRepository,
            consumableRepository
        ) {
            TimelineViewModel.factory(
                aquariumRepository = aquariumRepository,
                livestockRepository = livestockRepository,
                taskTemplateRepository = taskTemplateRepository,
                taskExecutionRepository = taskExecutionRepository,
                timelineEventRepository = timelineEventRepository,
                memoRepository = memoRepository,
                issueRepository = issueRepository,
                dosingLogRepository = dosingLogRepository,
                waterParameterLogRepository = waterParameterLogRepository,
                assetRepository = assetRepository,
                consumableRepository = consumableRepository
            )
        }
    )

    val uiState by viewModel.uiState.collectAsState()
    var showQuickLogSheet by rememberSaveable { mutableStateOf(false) }
    var selectedEventId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedEvent = uiState.dayGroups
        .asSequence()
        .flatMap { group -> group.events.asSequence() }
        .firstOrNull { event -> event.id == selectedEventId }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        viewModel.onQuickLogPhotoUriChanged(uri?.toString())
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
                                viewModel.prepareQuickLog()
                                showQuickLogSheet = true
                            },
                            enabled = uiState.aquariumFilters.isNotEmpty()
                        ) {
                            Text("Quick log")
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
                                "No timeline activity yet. Imported events and quick logs will appear here."
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
                    TimelineDayGroupCard(
                        group = group,
                        onEventSelected = { eventId ->
                            selectedEventId = eventId
                        }
                    )
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

        if (showQuickLogSheet) {
            ModalBottomSheet(
                onDismissRequest = { showQuickLogSheet = false }
            ) {
                QuickLogSheet(
                    uiState = uiState,
                    onTypeSelected = viewModel::onQuickLogTypeSelected,
                    onAquariumSelected = viewModel::onQuickLogAquariumSelected,
                    onMemoContentChanged = viewModel::onQuickLogMemoContentChanged,
                    onIssueTitleChanged = viewModel::onQuickLogIssueTitleChanged,
                    onDosingProductChanged = viewModel::onQuickLogDosingProductChanged,
                    onDosingAmountChanged = viewModel::onQuickLogDosingAmountChanged,
                    onDosingNoteChanged = viewModel::onQuickLogDosingNoteChanged,
                    onTaskTemplateSelected = viewModel::onQuickLogTaskTemplateSelected,
                    onTaskNoteChanged = viewModel::onQuickLogTaskNoteChanged,
                    onParameterValueChanged = viewModel::onQuickLogParameterValueChanged,
                    onCreatedAtChanged = viewModel::onQuickLogCreatedAtChanged,
                    onPickPhoto = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest.Builder()
                                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                .build()
                        )
                    },
                    onRemovePhoto = { viewModel.onQuickLogPhotoUriChanged(null) },
                    onSave = {
                        viewModel.saveQuickLog()
                        showQuickLogSheet = false
                    },
                    onCancel = { showQuickLogSheet = false }
                )
            }
        }

        selectedEvent?.let { event ->
            ModalBottomSheet(
                onDismissRequest = { selectedEventId = null }
            ) {
                EventDetailSheet(
                    event = event,
                    onOpenEntity = { preview ->
                        onOpenEntityDeepLink(preview.kind, preview.id, preview.aquariumId)
                        selectedEventId = null
                    },
                    onShowTank = {
                        viewModel.onAquariumFilterSelected(event.aquariumId)
                        selectedEventId = null
                    },
                    onShowType = {
                        viewModel.onTypeFilterSelected(event.type)
                        selectedEventId = null
                    },
                    onDismiss = { selectedEventId = null }
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
private fun TimelineDayGroupCard(
    group: TimelineDayGroup,
    onEventSelected: (String) -> Unit
) {
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
                TimelineEventRow(
                    event = event,
                    onClick = { onEventSelected(event.id) }
                )
            }
        }
    }
}

@Composable
private fun TimelineEventRow(
    event: TimelineEventItem,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
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
private fun QuickLogSheet(
    uiState: TimelineUiState,
    onTypeSelected: (TimelineQuickLogType) -> Unit,
    onAquariumSelected: (String) -> Unit,
    onMemoContentChanged: (String) -> Unit,
    onIssueTitleChanged: (String) -> Unit,
    onDosingProductChanged: (String) -> Unit,
    onDosingAmountChanged: (String) -> Unit,
    onDosingNoteChanged: (String) -> Unit,
    onTaskTemplateSelected: (String) -> Unit,
    onTaskNoteChanged: (String) -> Unit,
    onParameterValueChanged: (TimelineParameterField, String) -> Unit,
    onCreatedAtChanged: (String) -> Unit,
    onPickPhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Quick log",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(TimelineQuickLogType.entries, key = { it.name }) { type ->
                FilterChip(
                    selected = uiState.quickLogDraft.type == type,
                    onClick = { onTypeSelected(type) },
                    label = { Text(type.label) }
                )
            }
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(uiState.aquariumFilters, key = { it.aquariumId }) { aquarium ->
                FilterChip(
                    selected = uiState.quickLogDraft.aquariumId == aquarium.aquariumId,
                    onClick = { onAquariumSelected(aquarium.aquariumId) },
                    label = { Text(aquarium.aquariumName, maxLines = 1) }
                )
            }
        }

        when (uiState.quickLogDraft.type) {
            TimelineQuickLogType.TASK -> TaskQuickLogFields(
                uiState = uiState,
                onTaskTemplateSelected = onTaskTemplateSelected,
                onTaskNoteChanged = onTaskNoteChanged
            )
            TimelineQuickLogType.MEMO -> MemoQuickLogFields(
                uiState = uiState,
                onMemoContentChanged = onMemoContentChanged,
                onPickPhoto = onPickPhoto,
                onRemovePhoto = onRemovePhoto
            )
            TimelineQuickLogType.ISSUE -> IssueQuickLogFields(
                uiState = uiState,
                onIssueTitleChanged = onIssueTitleChanged
            )
            TimelineQuickLogType.PARAMETER -> ParameterQuickLogFields(
                uiState = uiState,
                onParameterValueChanged = onParameterValueChanged
            )
            TimelineQuickLogType.DOSING -> DosingQuickLogFields(
                uiState = uiState,
                onDosingProductChanged = onDosingProductChanged,
                onDosingAmountChanged = onDosingAmountChanged,
                onDosingNoteChanged = onDosingNoteChanged
            )
        }

        OutlinedTextField(
            value = uiState.quickLogDraft.createdAtInput,
            onValueChange = onCreatedAtChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("${uiState.quickLogDraft.type.label} time") },
            supportingText = { Text("Use yyyy-MM-dd HH:mm") },
            singleLine = true
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
        ) {
            OutlinedButton(onClick = onCancel) {
                Text("Cancel")
            }
            FilledTonalButton(
                onClick = onSave,
                enabled = uiState.quickLogDraft.canAttemptSave()
            ) {
                Text("Save")
            }
        }
    }
}

@Composable
private fun TaskQuickLogFields(
    uiState: TimelineUiState,
    onTaskTemplateSelected: (String) -> Unit,
    onTaskNoteChanged: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (uiState.dueTaskOptions.isEmpty()) {
            Text(
                text = "No due tasks for this tank right now.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(uiState.dueTaskOptions, key = { it.taskTemplateId }) { task ->
                    FilterChip(
                        selected = uiState.quickLogDraft.taskTemplateId == task.taskTemplateId,
                        onClick = { onTaskTemplateSelected(task.taskTemplateId) },
                        label = {
                            Text(
                                text = task.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }
            }

            uiState.dueTaskOptions
                .firstOrNull { it.taskTemplateId == uiState.quickLogDraft.taskTemplateId }
                ?.let { selectedTask ->
                    Text(
                        text = "${selectedTask.frequencyLabel} • ${selectedTask.completionLabel}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
        }

        OutlinedTextField(
            value = uiState.quickLogDraft.taskNote,
            onValueChange = onTaskNoteChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Completion note (optional)") },
            minLines = 2
        )
    }
}

@Composable
private fun MemoQuickLogFields(
    uiState: TimelineUiState,
    onMemoContentChanged: (String) -> Unit,
    onPickPhoto: () -> Unit,
    onRemovePhoto: () -> Unit
) {
    OutlinedTextField(
        value = uiState.quickLogDraft.memoContent,
        onValueChange = onMemoContentChanged,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Memo") },
        minLines = 3
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(onClick = onPickPhoto) {
            Text(if (uiState.quickLogDraft.photoUri == null) "Attach photo" else "Change photo")
        }
        uiState.quickLogDraft.photoUri?.let {
            TextButton(onClick = onRemovePhoto) {
                Text("Remove")
            }
        }
    }

    uiState.quickLogDraft.photoUri?.let {
        Text(
            text = "Photo selected",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun IssueQuickLogFields(
    uiState: TimelineUiState,
    onIssueTitleChanged: (String) -> Unit
) {
    OutlinedTextField(
        value = uiState.quickLogDraft.issueTitle,
        onValueChange = onIssueTitleChanged,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Issue") },
        supportingText = { Text("Saved as an open issue") },
        singleLine = true
    )
}

@Composable
private fun DosingQuickLogFields(
    uiState: TimelineUiState,
    onDosingProductChanged: (String) -> Unit,
    onDosingAmountChanged: (String) -> Unit,
    onDosingNoteChanged: (String) -> Unit
) {
    OutlinedTextField(
        value = uiState.quickLogDraft.dosingProduct,
        onValueChange = onDosingProductChanged,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Product") },
        singleLine = true
    )
    OutlinedTextField(
        value = uiState.quickLogDraft.dosingAmountMl,
        onValueChange = onDosingAmountChanged,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Amount ml") },
        singleLine = true
    )
    OutlinedTextField(
        value = uiState.quickLogDraft.dosingNote,
        onValueChange = onDosingNoteChanged,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Note") },
        minLines = 2
    )
}

@Composable
private fun ParameterQuickLogFields(
    uiState: TimelineUiState,
    onParameterValueChanged: (TimelineParameterField, String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TimelineParameterField.entries.forEach { field ->
            OutlinedTextField(
                value = uiState.quickLogDraft.parameterValue(field),
                onValueChange = { onParameterValueChanged(field, it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(field.label) },
                singleLine = true
            )
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

@Composable
private fun EventDetailSheet(
    event: TimelineEventItem,
    onOpenEntity: (TimelineEntityPreview) -> Unit,
    onShowTank: () -> Unit,
    onShowType: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Event details",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        Text(
            text = event.title,
            style = MaterialTheme.typography.titleSmall
        )

        Text(
            text = "${event.typeLabel} • ${event.createdAtLabel}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = event.aquariumName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        event.description?.takeIf { it.isNotBlank() }?.let { description ->
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        if (event.photoUri != null) {
            Text(
                text = "Photo attached",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        event.sourcePreview?.let { source ->
            EntityPreviewSection(
                title = "Source",
                previews = listOf(source),
                onOpenEntity = onOpenEntity
            )
        }

        if (event.relatedPreviews.isNotEmpty()) {
            EntityPreviewSection(
                title = "Linked items",
                previews = event.relatedPreviews,
                onOpenEntity = onOpenEntity
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
        ) {
            OutlinedButton(onClick = onShowType) {
                Text("Show type")
            }
            FilledTonalButton(onClick = onShowTank) {
                Text("Show tank")
            }
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    }
}

@Composable
private fun EntityPreviewSection(
    title: String,
    previews: List<TimelineEntityPreview>,
    onOpenEntity: (TimelineEntityPreview) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        previews.forEach { preview ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                AssistChip(
                    onClick = { onOpenEntity(preview) },
                    label = {
                        Text(
                            text = "${preview.kind.label()} • ${preview.title}",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
                preview.supportingText?.takeIf { it.isNotBlank() }?.let { supportingText ->
                    Text(
                        text = supportingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun EntityKind.label(): String =
    name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercaseChar() }
