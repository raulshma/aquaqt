package com.keepaside.aquapt.feature.livestock

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.keepaside.aquapt.core.model.LivestockStatus
import com.keepaside.aquapt.core.model.TaskFrequency
import com.keepaside.aquapt.core.model.TaskFrequencyKind
import com.keepaside.aquapt.core.repository.AquariumRepository
import com.keepaside.aquapt.core.repository.LivestockRepository
import com.keepaside.aquapt.core.repository.TaskTemplateRepository
import com.keepaside.aquapt.core.repository.TimelineEventRepository
import org.koin.java.KoinJavaComponent

@Composable
fun LivestockScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp)
) {
    val aquariumRepository: AquariumRepository = remember {
        KoinJavaComponent.get(AquariumRepository::class.java)
    }
    val livestockRepository: LivestockRepository = remember {
        KoinJavaComponent.get(LivestockRepository::class.java)
    }
    val taskTemplateRepository: TaskTemplateRepository = remember {
        KoinJavaComponent.get(TaskTemplateRepository::class.java)
    }
    val timelineEventRepository: TimelineEventRepository = remember {
        KoinJavaComponent.get(TimelineEventRepository::class.java)
    }

    val viewModel: LivestockViewModel = viewModel(
        factory = remember(
            aquariumRepository,
            livestockRepository,
            taskTemplateRepository,
            timelineEventRepository
        ) {
            LivestockViewModel.factory(
                aquariumRepository = aquariumRepository,
                livestockRepository = livestockRepository,
                taskTemplateRepository = taskTemplateRepository,
                timelineEventRepository = timelineEventRepository
            )
        }
    )

    val uiState by viewModel.uiState.collectAsState()

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
                LivestockOverviewCard(uiState)
            }

            item {
                LivestockFilters(
                    uiState = uiState,
                    onAquariumSelected = viewModel::onAquariumFilterSelected
                )
            }

            if (uiState.isEmpty) {
                item {
                    Card {
                        Text(
                            text = if (uiState.aquariumFilters.isEmpty()) {
                                "No tanks yet. Import a backup in Settings or add a tank in an upcoming creation flow."
                            } else {
                                "No residents yet. Imported livestock and future tank actions will appear here."
                            },
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

            if (uiState.residents.isEmpty() && !uiState.isEmpty) {
                item {
                    Card {
                        Text(
                            text = "No residents match this tank filter.",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

            if (uiState.residents.isNotEmpty()) {
                item {
                    ResidentSelector(
                        residents = uiState.residents,
                        onResidentSelected = viewModel::onResidentSelected
                    )
                }
            }

            uiState.selectedResident?.let { resident ->
                item {
                    LivestockDetailCard(
                        uiState = uiState,
                        resident = resident,
                        onFeedingNoteChanged = viewModel::onFeedingNoteChanged,
                        onSaveFeedingNotes = viewModel::saveFeedingNotes,
                        onStatusSelected = viewModel::onStatusSelected,
                        onStatusNoteChanged = viewModel::onStatusNoteChanged,
                        onSaveStatus = viewModel::saveStatus,
                        onTransferTargetSelected = viewModel::onTransferTargetSelected,
                        onTransferNoteChanged = viewModel::onTransferNoteChanged,
                        onTransfer = viewModel::transferSelectedResident,
                        onOffspringNameChanged = viewModel::onOffspringNameChanged,
                        onOffspringSpeciesChanged = viewModel::onOffspringSpeciesChanged,
                        onOffspringQuantityChanged = viewModel::onOffspringQuantityChanged,
                        onAddOffspring = viewModel::addOffspring,
                        onFeedingTaskTitleChanged = viewModel::onFeedingTaskTitleChanged,
                        onFeedingTaskFrequencySelected = viewModel::onFeedingTaskFrequencySelected,
                        onFeedingTaskCustomDaysChanged = viewModel::onFeedingTaskCustomDaysChanged,
                        onFeedingTaskStartDateChanged = viewModel::onFeedingTaskStartDateChanged,
                        onFeedingTaskTimesPerDayChanged = viewModel::onFeedingTaskTimesPerDayChanged,
                        onCreateFeedingTask = viewModel::createFeedingTask
                    )
                }
            } ?: run {
                if (uiState.residents.isNotEmpty()) {
                    item {
                        Card {
                            Text(
                                text = "Select a resident to update feeding notes, health, transfer, family links, or feeding tasks.",
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
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
    }
}

@Composable
private fun LivestockOverviewCard(uiState: LivestockUiState) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Livestock",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = uiState.headline,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SummaryTile(
                    modifier = Modifier.weight(1f),
                    title = "Active",
                    value = uiState.summary.activeCount.toString()
                )
                SummaryTile(
                    modifier = Modifier.weight(1f),
                    title = "Ill",
                    value = uiState.summary.illCount.toString()
                )
                SummaryTile(
                    modifier = Modifier.weight(1f),
                    title = "Feeding",
                    value = uiState.summary.feedingTaskCount.toString()
                )
            }
        }
    }
}

@Composable
private fun LivestockFilters(
    uiState: LivestockUiState,
    onAquariumSelected: (String?) -> Unit
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Tank filter",
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
        }
    }
}

@Composable
private fun ResidentSelector(
    residents: List<LivestockResidentItem>,
    onResidentSelected: (String) -> Unit
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Residents",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(residents, key = { it.id }) { resident ->
                    ResidentCard(
                        resident = resident,
                        onClick = { onResidentSelected(resident.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ResidentCard(
    resident: LivestockResidentItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (resident.isSelected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = resident.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${resident.kindLabel} - ${resident.quantity} - ${resident.aquariumName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            AssistChip(
                onClick = onClick,
                label = { Text(resident.statusLabel) }
            )
        }
    }
}

@Composable
private fun LivestockDetailCard(
    uiState: LivestockUiState,
    resident: LivestockDetailItem,
    onFeedingNoteChanged: (String) -> Unit,
    onSaveFeedingNotes: () -> Unit,
    onStatusSelected: (LivestockStatus) -> Unit,
    onStatusNoteChanged: (String) -> Unit,
    onSaveStatus: () -> Unit,
    onTransferTargetSelected: (String) -> Unit,
    onTransferNoteChanged: (String) -> Unit,
    onTransfer: () -> Unit,
    onOffspringNameChanged: (String) -> Unit,
    onOffspringSpeciesChanged: (String) -> Unit,
    onOffspringQuantityChanged: (String) -> Unit,
    onAddOffspring: () -> Unit,
    onFeedingTaskTitleChanged: (String) -> Unit,
    onFeedingTaskFrequencySelected: (TaskFrequency) -> Unit,
    onFeedingTaskCustomDaysChanged: (String) -> Unit,
    onFeedingTaskStartDateChanged: (String) -> Unit,
    onFeedingTaskTimesPerDayChanged: (String) -> Unit,
    onCreateFeedingTask: () -> Unit
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = resident.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = buildList {
                        add(resident.species.ifBlank { "Unspecified species" })
                        add(resident.kindLabel)
                        add("${resident.quantity} total")
                        add(resident.ageLabel)
                        add(resident.aquariumName)
                    }.joinToString(" - "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            FamilyAndTaskChips(resident)

            DetailSection(title = "Feeding notes") {
                OutlinedTextField(
                    value = uiState.feedingNoteDraft,
                    onValueChange = onFeedingNoteChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Dietary notes") },
                    minLines = 3
                )
                FilledTonalButton(onClick = onSaveFeedingNotes) {
                    Text("Save notes")
                }
            }

            DetailSection(title = "Health status") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(LivestockStatus.entries, key = { it.name }) { status ->
                        FilterChip(
                            selected = uiState.statusDraft == status,
                            onClick = { onStatusSelected(status) },
                            label = { Text(status.name.lowercase().replaceFirstChar { it.uppercaseChar() }) }
                        )
                    }
                }
                OutlinedTextField(
                    value = uiState.statusNoteDraft,
                    onValueChange = onStatusNoteChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Status note") }
                )
                FilledTonalButton(onClick = onSaveStatus) {
                    Text("Update status")
                }
            }

            DetailSection(title = "Transfer") {
                if (resident.transferTargets.isEmpty()) {
                    Text(
                        text = "Add another tank before transferring this resident.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(resident.transferTargets, key = { it.aquariumId }) { aquarium ->
                            FilterChip(
                                selected = uiState.transferDraft.targetAquariumId == aquarium.aquariumId,
                                onClick = { onTransferTargetSelected(aquarium.aquariumId) },
                                label = { Text(aquarium.aquariumName, maxLines = 1) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = uiState.transferDraft.note,
                        onValueChange = onTransferNoteChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Transfer note") }
                    )
                    OutlinedButton(
                        onClick = onTransfer,
                        enabled = uiState.transferDraft.targetAquariumId != null
                    ) {
                        Text("Transfer resident")
                    }
                }
            }

            DetailSection(title = "Offspring") {
                OutlinedTextField(
                    value = uiState.offspringDraft.name,
                    onValueChange = onOffspringNameChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Name") }
                )
                OutlinedTextField(
                    value = uiState.offspringDraft.species,
                    onValueChange = onOffspringSpeciesChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Species") }
                )
                OutlinedTextField(
                    value = uiState.offspringDraft.quantity,
                    onValueChange = onOffspringQuantityChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Quantity") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                FilledTonalButton(
                    onClick = onAddOffspring,
                    enabled = uiState.offspringDraft.name.isNotBlank()
                ) {
                    Text("Link offspring")
                }
            }

            FeedingTaskSection(
                uiState = uiState,
                onTitleChanged = onFeedingTaskTitleChanged,
                onFrequencySelected = onFeedingTaskFrequencySelected,
                onCustomDaysChanged = onFeedingTaskCustomDaysChanged,
                onStartDateChanged = onFeedingTaskStartDateChanged,
                onTimesPerDayChanged = onFeedingTaskTimesPerDayChanged,
                onCreateFeedingTask = onCreateFeedingTask
            )
        }
    }
}

@Composable
private fun FamilyAndTaskChips(resident: LivestockDetailItem) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                AssistChip(onClick = {}, label = { Text(resident.statusLabel) })
            }
            resident.parent?.let { parent ->
                item {
                    AssistChip(onClick = {}, label = { Text("Parent: ${parent.name}") })
                }
            }
            items(resident.offspring, key = { it.id }) { offspring ->
                AssistChip(onClick = {}, label = { Text("Offspring: ${offspring.name}") })
            }
            items(resident.feedingTasks, key = { it.id }) { task ->
                AssistChip(onClick = {}, label = { Text(task.title) })
            }
        }

        if (resident.feedingTasks.isNotEmpty()) {
            resident.feedingTasks.take(3).forEach { task ->
                Text(
                    text = "${task.title} - ${task.frequencyLabel}${task.timesPerDay?.let { " - $it/day" } ?: ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DetailSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        content()
    }
}

@Composable
private fun FeedingTaskSection(
    uiState: LivestockUiState,
    onTitleChanged: (String) -> Unit,
    onFrequencySelected: (TaskFrequency) -> Unit,
    onCustomDaysChanged: (String) -> Unit,
    onStartDateChanged: (String) -> Unit,
    onTimesPerDayChanged: (String) -> Unit,
    onCreateFeedingTask: () -> Unit
) {
    val draft = uiState.feedingTaskDraft
    val options = listOf(
        "Daily" to TaskFrequency.DAILY,
        "Weekly" to TaskFrequency.WEEKLY,
        "Bi-weekly" to TaskFrequency.BI_WEEKLY,
        "Monthly" to TaskFrequency.MONTHLY,
        "Custom" to TaskFrequency.custom(1)
    )

    DetailSection(title = "Feeding task") {
        OutlinedTextField(
            value = draft.title,
            onValueChange = onTitleChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Task title") }
        )

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(options, key = { it.first }) { option ->
                val frequency = option.second
                FilterChip(
                    selected = draft.frequency.kind == frequency.kind,
                    onClick = { onFrequencySelected(frequency) },
                    label = { Text(option.first) }
                )
            }
        }

        if (draft.frequency.kind == TaskFrequencyKind.CUSTOM) {
            OutlinedTextField(
                value = draft.customDays,
                onValueChange = onCustomDaysChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Every N days") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }

        if (draft.frequency.kind == TaskFrequencyKind.DAILY) {
            OutlinedTextField(
                value = draft.timesPerDay,
                onValueChange = onTimesPerDayChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Times per day") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }

        OutlinedTextField(
            value = draft.startDate,
            onValueChange = onStartDateChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Start date (yyyy-mm-dd)") }
        )

        FilledTonalButton(
            onClick = onCreateFeedingTask,
            enabled = draft.frequency.kind != TaskFrequencyKind.CUSTOM ||
                (draft.customDays.toIntOrNull() ?: 0) >= 1
        ) {
            Text("Create feeding task")
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
