package com.keepaside.aquapt.feature.tanks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.keepaside.aquapt.core.model.AssetCategory
import com.keepaside.aquapt.core.model.ConsumableUnit
import com.keepaside.aquapt.core.model.LivestockKind
import com.keepaside.aquapt.core.model.WaterType
import com.keepaside.aquapt.core.repository.AssetRepository
import com.keepaside.aquapt.core.repository.AquariumRepository
import com.keepaside.aquapt.core.repository.ConsumableRepository
import com.keepaside.aquapt.core.repository.DosingLogRepository
import com.keepaside.aquapt.core.repository.IssueRepository
import com.keepaside.aquapt.core.repository.LivestockRepository
import com.keepaside.aquapt.core.repository.TaskExecutionRepository
import com.keepaside.aquapt.core.repository.TaskTemplateRepository
import com.keepaside.aquapt.core.repository.TimelineEventRepository
import com.keepaside.aquapt.core.repository.WaterParameterLogRepository
import org.koin.java.KoinJavaComponent

@Composable
fun TanksDashboardScreen(
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
    val taskExecutionRepository: TaskExecutionRepository = remember {
        KoinJavaComponent.get(TaskExecutionRepository::class.java)
    }
    val issueRepository: IssueRepository = remember {
        KoinJavaComponent.get(IssueRepository::class.java)
    }
    val waterParameterLogRepository: WaterParameterLogRepository = remember {
        KoinJavaComponent.get(WaterParameterLogRepository::class.java)
    }
    val dosingLogRepository: DosingLogRepository = remember {
        KoinJavaComponent.get(DosingLogRepository::class.java)
    }
    val assetRepository: AssetRepository = remember {
        KoinJavaComponent.get(AssetRepository::class.java)
    }
    val consumableRepository: ConsumableRepository = remember {
        KoinJavaComponent.get(ConsumableRepository::class.java)
    }
    val timelineEventRepository: TimelineEventRepository = remember {
        KoinJavaComponent.get(TimelineEventRepository::class.java)
    }

    val viewModel: TanksDashboardViewModel = viewModel(
        factory = remember(
            aquariumRepository,
            livestockRepository,
            taskTemplateRepository,
            taskExecutionRepository,
            issueRepository,
            waterParameterLogRepository,
            dosingLogRepository,
            assetRepository,
            consumableRepository,
            timelineEventRepository
        ) {
            TanksDashboardViewModel.factory(
                aquariumRepository = aquariumRepository,
                livestockRepository = livestockRepository,
                taskTemplateRepository = taskTemplateRepository,
                taskExecutionRepository = taskExecutionRepository,
                issueRepository = issueRepository,
                waterParameterLogRepository = waterParameterLogRepository,
                dosingLogRepository = dosingLogRepository,
                assetRepository = assetRepository,
                consumableRepository = consumableRepository,
                timelineEventRepository = timelineEventRepository
            )
        }
    )

    val uiState by viewModel.uiState.collectAsState()
    var aquariumDraft by remember { mutableStateOf<TanksAquariumDraft?>(null) }
    var livestockDraft by remember { mutableStateOf<TanksLivestockDraft?>(null) }
    var assetDraft by remember { mutableStateOf<TanksAssetDraft?>(null) }
    var consumableDraft by remember { mutableStateOf<TanksConsumableDraft?>(null) }

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
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Today at a glance",
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
                            MetricCard(
                                modifier = Modifier.weight(1f),
                                title = "Tanks",
                                value = uiState.summary.aquariumCount.toString()
                            )
                            MetricCard(
                                modifier = Modifier.weight(1f),
                                title = "Residents",
                                value = uiState.summary.residentCount.toString()
                            )
                            MetricCard(
                                modifier = Modifier.weight(1f),
                                title = "Due",
                                value = uiState.summary.dueTaskCount.toString()
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MetricCard(
                                modifier = Modifier.weight(1f),
                                title = "Open issues",
                                value = uiState.summary.openIssueCount.toString()
                            )
                            MetricCard(
                                modifier = Modifier.weight(1f),
                                title = "Alerts",
                                value = uiState.summary.parameterAlertCount.toString()
                            )
                            MetricCard(
                                modifier = Modifier.weight(1f),
                                title = "Dosing logs",
                                value = uiState.summary.dosingLogCount.toString()
                            )
                        }
                    }
                }
            }

            item {
                QuickCreateActionsCard(
                    hasAquariums = uiState.aquariums.isNotEmpty(),
                    onCreateAquarium = { aquariumDraft = viewModel.newAquariumDraft() },
                    onCreateLivestock = { livestockDraft = viewModel.newLivestockDraft() },
                    onCreateAsset = { assetDraft = viewModel.newAssetDraft() },
                    onCreateConsumable = { consumableDraft = viewModel.newConsumableDraft() }
                )
            }

            if (uiState.isEmpty) {
                item {
                    Card {
                        Text(
                            text = "No aquariums yet. Import a backup in Settings or add your first tank with the quick action above.",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

            if (uiState.alerts.isNotEmpty()) {
                item {
                    Card {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Water alerts",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            uiState.alerts.take(5).forEach { alert ->
                                Text(
                                    text = "${alert.aquariumName}: ${alert.label} ${alert.status} at ${formatAlertValue(alert.value, alert.unit)}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }

            if (uiState.dueTasks.isNotEmpty()) {
                item {
                    Card {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Due tasks",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            uiState.dueTasks.take(6).forEach { task ->
                                Text(
                                    text = "${task.taskTitle} • ${task.aquariumName}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Aquariums",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                )
            }

            items(uiState.aquariums, key = { it.aquariumId }) { aquarium ->
                Card {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = aquarium.aquariumName,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f)
                            )
                            AssistChip(
                                onClick = {},
                                label = {
                                    Text("${aquarium.volumeLiters.toInt()}L")
                                }
                            )
                        }

                        Text(
                            text = "${aquarium.waterTypeLabel} • Setup ${aquarium.setupDate.ifBlank { "unknown" }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text(
                            text = aquarium.latestParameterSummary,
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Residents ${aquarium.residentCount}",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                text = "Issues ${aquarium.openIssueCount}",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                text = "Due ${aquarium.dueTaskCount}",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                text = "Alerts ${aquarium.activeAlertCount}",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }

                        Text(
                            text = "NO3 trend: ${aquarium.nitrateTrend}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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

        aquariumDraft?.let { draft ->
            AquariumDraftDialog(
                draft = draft,
                onDraftChange = { aquariumDraft = it },
                onDismiss = { aquariumDraft = null },
                onConfirm = {
                    viewModel.saveAquariumDraft(draft)
                    aquariumDraft = null
                }
            )
        }

        livestockDraft?.let { draft ->
            LivestockDraftDialog(
                draft = draft,
                aquariums = uiState.aquariums,
                onDraftChange = { livestockDraft = it },
                onDismiss = { livestockDraft = null },
                onConfirm = {
                    viewModel.saveLivestockDraft(draft)
                    livestockDraft = null
                }
            )
        }

        assetDraft?.let { draft ->
            AssetDraftDialog(
                draft = draft,
                aquariums = uiState.aquariums,
                onDraftChange = { assetDraft = it },
                onDismiss = { assetDraft = null },
                onConfirm = {
                    viewModel.saveAssetDraft(draft)
                    assetDraft = null
                }
            )
        }

        consumableDraft?.let { draft ->
            ConsumableDraftDialog(
                draft = draft,
                aquariums = uiState.aquariums,
                onDraftChange = { consumableDraft = it },
                onDismiss = { consumableDraft = null },
                onConfirm = {
                    viewModel.saveConsumableDraft(draft)
                    consumableDraft = null
                }
            )
        }
    }
}

@Composable
private fun QuickCreateActionsCard(
    hasAquariums: Boolean,
    onCreateAquarium: () -> Unit,
    onCreateLivestock: () -> Unit,
    onCreateAsset: () -> Unit,
    onCreateConsumable: () -> Unit
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Quick actions",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Create tanks, residents, equipment, and consumables without leaving the dashboard.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onCreateAquarium,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Add tank")
                }
                FilledTonalButton(
                    onClick = onCreateLivestock,
                    enabled = hasAquariums,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Add resident")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onCreateAsset,
                    enabled = hasAquariums,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Add asset")
                }
                FilledTonalButton(
                    onClick = onCreateConsumable,
                    enabled = hasAquariums,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Add consumable")
                }
            }
        }
    }
}

@Composable
private fun AquariumDraftDialog(
    draft: TanksAquariumDraft,
    onDraftChange: (TanksAquariumDraft) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create tank") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { onDraftChange(draft.copy(name = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Tank name") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = draft.volumeLitersInput,
                    onValueChange = { onDraftChange(draft.copy(volumeLitersInput = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Volume (L)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                OutlinedTextField(
                    value = draft.dimensions,
                    onValueChange = { onDraftChange(draft.copy(dimensions = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Dimensions") },
                    supportingText = { Text("Optional") },
                    singleLine = true
                )

                Text(
                    text = "Water type",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(WaterType.entries, key = { it.name }) { waterType ->
                        FilterChip(
                            selected = draft.waterType == waterType,
                            onClick = { onDraftChange(draft.copy(waterType = waterType)) },
                            label = { Text(waterType.label()) }
                        )
                    }
                }

                OutlinedTextField(
                    value = draft.setupDateInput,
                    onValueChange = { onDraftChange(draft.copy(setupDateInput = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Setup date/time") },
                    supportingText = { Text("Use yyyy-MM-dd or yyyy-MM-dd HH:mm") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = draft.investmentCostInput,
                    onValueChange = { onDraftChange(draft.copy(investmentCostInput = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Investment cost") },
                    supportingText = { Text("Optional") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = onConfirm,
                enabled = draft.name.isNotBlank() && draft.volumeLitersInput.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun LivestockDraftDialog(
    draft: TanksLivestockDraft,
    aquariums: List<AquariumDashboardCard>,
    onDraftChange: (TanksLivestockDraft) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add resident") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Tank",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(aquariums, key = { it.aquariumId }) { aquarium ->
                        FilterChip(
                            selected = draft.aquariumId == aquarium.aquariumId,
                            onClick = { onDraftChange(draft.copy(aquariumId = aquarium.aquariumId)) },
                            label = { Text(aquarium.aquariumName, maxLines = 1) }
                        )
                    }
                }

                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { onDraftChange(draft.copy(name = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Name") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = draft.species,
                    onValueChange = { onDraftChange(draft.copy(species = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Species") },
                    supportingText = { Text("Optional") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = draft.quantityInput,
                    onValueChange = { onDraftChange(draft.copy(quantityInput = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Quantity") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )

                Text(
                    text = "Kind",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(LivestockKind.entries, key = { it.name }) { kind ->
                        FilterChip(
                            selected = draft.kind == kind,
                            onClick = { onDraftChange(draft.copy(kind = kind)) },
                            label = { Text(kind.label()) }
                        )
                    }
                }

                OutlinedTextField(
                    value = draft.acquiredAtInput,
                    onValueChange = { onDraftChange(draft.copy(acquiredAtInput = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Acquired at") },
                    supportingText = { Text("Use yyyy-MM-dd or yyyy-MM-dd HH:mm") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = onConfirm,
                enabled = draft.aquariumId != null && draft.name.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun AssetDraftDialog(
    draft: TanksAssetDraft,
    aquariums: List<AquariumDashboardCard>,
    onDraftChange: (TanksAssetDraft) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add asset") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Tank",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(aquariums, key = { it.aquariumId }) { aquarium ->
                        FilterChip(
                            selected = draft.aquariumId == aquarium.aquariumId,
                            onClick = { onDraftChange(draft.copy(aquariumId = aquarium.aquariumId)) },
                            label = { Text(aquarium.aquariumName, maxLines = 1) }
                        )
                    }
                }

                Text(
                    text = "Category",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(AssetCategory.entries, key = { it.name }) { category ->
                        FilterChip(
                            selected = draft.category == category,
                            onClick = { onDraftChange(draft.copy(category = category)) },
                            label = { Text(category.label()) }
                        )
                    }
                }

                OutlinedTextField(
                    value = draft.brandModel,
                    onValueChange = { onDraftChange(draft.copy(brandModel = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Brand / model") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = draft.purchasedAtInput,
                    onValueChange = { onDraftChange(draft.copy(purchasedAtInput = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Purchased at") },
                    supportingText = { Text("Optional • yyyy-MM-dd or yyyy-MM-dd HH:mm") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = draft.priceInput,
                    onValueChange = { onDraftChange(draft.copy(priceInput = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Price") },
                    supportingText = { Text("Optional") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = onConfirm,
                enabled = draft.aquariumId != null && draft.brandModel.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ConsumableDraftDialog(
    draft: TanksConsumableDraft,
    aquariums: List<AquariumDashboardCard>,
    onDraftChange: (TanksConsumableDraft) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add consumable") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Tank",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(aquariums, key = { it.aquariumId }) { aquarium ->
                        FilterChip(
                            selected = draft.aquariumId == aquarium.aquariumId,
                            onClick = { onDraftChange(draft.copy(aquariumId = aquarium.aquariumId)) },
                            label = { Text(aquarium.aquariumName, maxLines = 1) }
                        )
                    }
                }

                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { onDraftChange(draft.copy(name = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Name") },
                    singleLine = true
                )

                Text(
                    text = "Unit",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ConsumableUnit.entries, key = { it.name }) { unit ->
                        FilterChip(
                            selected = draft.unit == unit,
                            onClick = { onDraftChange(draft.copy(unit = unit)) },
                            label = { Text(unit.name) }
                        )
                    }
                }

                OutlinedTextField(
                    value = draft.remainingInput,
                    onValueChange = { onDraftChange(draft.copy(remainingInput = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Remaining") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                OutlinedTextField(
                    value = draft.reorderAtInput,
                    onValueChange = { onDraftChange(draft.copy(reorderAtInput = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Reorder threshold") },
                    supportingText = { Text("Optional") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                OutlinedTextField(
                    value = draft.updatedAtInput,
                    onValueChange = { onDraftChange(draft.copy(updatedAtInput = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Last updated") },
                    supportingText = { Text("Optional • yyyy-MM-dd or yyyy-MM-dd HH:mm") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = onConfirm,
                enabled = draft.aquariumId != null && draft.name.isNotBlank() && draft.remainingInput.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun MetricCard(
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

private fun formatAlertValue(value: Double, unit: String): String {
    val rendered = if (value == value.toInt().toDouble()) {
        value.toInt().toString()
    } else {
        String.format("%.2f", value)
    }
    return if (unit.isBlank()) rendered else "$rendered $unit"
}

private fun WaterType.label(): String =
    name.lowercase().replaceFirstChar { it.uppercaseChar() }

private fun LivestockKind.label(): String =
    name.lowercase().replaceFirstChar { it.uppercaseChar() }

private fun AssetCategory.label(): String =
    name.lowercase().replaceFirstChar { it.uppercaseChar() }