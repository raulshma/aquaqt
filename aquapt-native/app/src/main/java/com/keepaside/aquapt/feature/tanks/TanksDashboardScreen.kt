package com.keepaside.aquapt.feature.tanks

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
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
import com.keepaside.aquapt.core.repository.MemoRepository
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
    val memoRepository: MemoRepository = remember {
        KoinJavaComponent.get(MemoRepository::class.java)
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
            timelineEventRepository,
            memoRepository
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
                timelineEventRepository = timelineEventRepository,
                memoRepository = memoRepository
            )
        }
    )

    val uiState by viewModel.uiState.collectAsState()
    var aquariumDraft by remember { mutableStateOf<TanksAquariumDraft?>(null) }
    var livestockDraft by remember { mutableStateOf<TanksLivestockDraft?>(null) }
    var assetDraft by remember { mutableStateOf<TanksAssetDraft?>(null) }
    var consumableDraft by remember { mutableStateOf<TanksConsumableDraft?>(null) }
    var showQuickLog by remember { mutableStateOf(false) }

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

            if (uiState.aquariums.isNotEmpty()) {
                item {
                    ParameterAnalyticsCard(
                        chartState = uiState.parameterChart,
                        onSelectMetric = { viewModel.selectChartMetric(it) },
                        onSelectAquarium = { viewModel.selectChartAquarium(it) }
                    )
                }
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

        if (uiState.aquariums.isNotEmpty()) {
            FloatingActionButton(
                onClick = {
                    viewModel.prepareQuickLog()
                    showQuickLog = true
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 16.dp, end = 16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Quick log")
            }
        }

        if (showQuickLog) {
            TanksQuickLogBottomSheet(
                draft = uiState.quickLogDraft,
                aquariums = uiState.aquariums,
                dueTaskOptions = uiState.quickLogDueTaskOptions,
                onDismiss = { showQuickLog = false },
                onTypeSelected = { viewModel.onQuickLogTypeSelected(it) },
                onAquariumSelected = { viewModel.onQuickLogAquariumSelected(it) },
                onTaskTemplateSelected = { viewModel.onQuickLogTaskTemplateSelected(it) },
                onTaskNoteChanged = { viewModel.onQuickLogTaskNoteChanged(it) },
                onMemoContentChanged = { viewModel.onQuickLogMemoContentChanged(it) },
                onIssueTitleChanged = { viewModel.onQuickLogIssueTitleChanged(it) },
                onDosingProductChanged = { viewModel.onQuickLogDosingProductChanged(it) },
                onDosingAmountChanged = { viewModel.onQuickLogDosingAmountChanged(it) },
                onParameterChanged = { field, value -> viewModel.onQuickLogParameterChanged(field, value) },
                onSave = {
                    viewModel.saveQuickLog()
                    showQuickLog = false
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TanksQuickLogBottomSheet(
    draft: TanksQuickLogDraft,
    aquariums: List<AquariumDashboardCard>,
    dueTaskOptions: List<TanksQuickLogDueTaskOption>,
    onDismiss: () -> Unit,
    onTypeSelected: (TanksQuickLogType) -> Unit,
    onAquariumSelected: (String) -> Unit,
    onTaskTemplateSelected: (String) -> Unit,
    onTaskNoteChanged: (String) -> Unit,
    onMemoContentChanged: (String) -> Unit,
    onIssueTitleChanged: (String) -> Unit,
    onDosingProductChanged: (String) -> Unit,
    onDosingAmountChanged: (String) -> Unit,
    onParameterChanged: (AnalyticMetric, String) -> Unit,
    onSave: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Quick log",
                style = MaterialTheme.typography.titleMedium
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(aquariums, key = { it.aquariumId }) { aquarium ->
                    FilterChip(
                        selected = draft.aquariumId == aquarium.aquariumId,
                        onClick = { onAquariumSelected(aquarium.aquariumId) },
                        label = { Text(aquarium.aquariumName, maxLines = 1) }
                    )
                }
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(TanksQuickLogType.entries, key = { it.name }) { type ->
                    FilterChip(
                        selected = draft.type == type,
                        onClick = { onTypeSelected(type) },
                        label = { Text(type.label) }
                    )
                }
            }

            when (draft.type) {
                TanksQuickLogType.TASK -> {
                    if (dueTaskOptions.isEmpty()) {
                        Text(
                            text = "No due tasks for this aquarium.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(dueTaskOptions, key = { it.taskTemplateId }) { option ->
                                FilterChip(
                                    selected = draft.taskTemplateId == option.taskTemplateId,
                                    onClick = { onTaskTemplateSelected(option.taskTemplateId) },
                                    label = { Text(option.title, maxLines = 1) }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = draft.taskNote,
                        onValueChange = onTaskNoteChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Note") },
                        supportingText = { Text("Optional") },
                        maxLines = 2
                    )
                }

                TanksQuickLogType.PARAMETER -> {
                    val parameterFields = listOf(
                        AnalyticMetric.AMMONIA to "Ammonia (ppm)",
                        AnalyticMetric.NITRITE to "Nitrite (ppm)",
                        AnalyticMetric.NITRATE to "Nitrate (ppm)",
                        AnalyticMetric.PH to "pH",
                        AnalyticMetric.TEMPERATURE to "Temperature C",
                        AnalyticMetric.GH to "GH",
                        AnalyticMetric.KH to "KH",
                        AnalyticMetric.SALINITY to "Salinity",
                        AnalyticMetric.CALCIUM to "Calcium (ppm)",
                        AnalyticMetric.ALKALINITY to "Alkalinity (dKH)"
                    )
                    parameterFields.forEach { (metric, label) ->
                        val fieldValue = when (metric) {
                            AnalyticMetric.AMMONIA -> draft.ammonia
                            AnalyticMetric.NITRITE -> draft.nitrite
                            AnalyticMetric.NITRATE -> draft.nitrate
                            AnalyticMetric.PH -> draft.ph
                            AnalyticMetric.TEMPERATURE -> draft.temperatureC
                            AnalyticMetric.GH -> draft.gh
                            AnalyticMetric.KH -> draft.kh
                            AnalyticMetric.SALINITY -> draft.salinity
                            AnalyticMetric.CALCIUM -> draft.calcium
                            AnalyticMetric.ALKALINITY -> draft.alkalinity
                        }
                        OutlinedTextField(
                            value = fieldValue,
                            onValueChange = { onParameterChanged(metric, it) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(label) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )
                    }
                }

                TanksQuickLogType.MEMO -> {
                    OutlinedTextField(
                        value = draft.memoContent,
                        onValueChange = onMemoContentChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Memo") },
                        minLines = 3,
                        maxLines = 4
                    )
                }

                TanksQuickLogType.ISSUE -> {
                    OutlinedTextField(
                        value = draft.issueTitle,
                        onValueChange = onIssueTitleChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Issue title") },
                        singleLine = true
                    )
                }

                TanksQuickLogType.DOSING -> {
                    OutlinedTextField(
                        value = draft.dosingProduct,
                        onValueChange = onDosingProductChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Product") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = draft.dosingAmountMl,
                        onValueChange = onDosingAmountChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Amount (ml)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                }
            }

            FilledTonalButton(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth(),
                enabled = draft.aquariumId != null && draft.canAttemptSave()
            ) {
                Text("Save")
            }
        }
    }
}

private fun WaterType.label(): String =
    name.lowercase().replaceFirstChar { it.uppercaseChar() }

private fun LivestockKind.label(): String =
    name.lowercase().replaceFirstChar { it.uppercaseChar() }

private fun AssetCategory.label(): String =
    name.lowercase().replaceFirstChar { it.uppercaseChar() }

private val ANALYTIC_METRIC_COLORS: Map<AnalyticMetric, Color> = mapOf(
    AnalyticMetric.AMMONIA to Color(0xFFEF4444),
    AnalyticMetric.NITRITE to Color(0xFFF97316),
    AnalyticMetric.NITRATE to Color(0xFF22C55E),
    AnalyticMetric.PH to Color(0xFF0EA5E9),
    AnalyticMetric.TEMPERATURE to Color(0xFF8B5CF6),
    AnalyticMetric.GH to Color(0xFF14B8A6),
    AnalyticMetric.KH to Color(0xFF06B6D4),
    AnalyticMetric.SALINITY to Color(0xFF0D9488),
    AnalyticMetric.CALCIUM to Color(0xFF2563EB),
    AnalyticMetric.ALKALINITY to Color(0xFF9333EA)
)

@Composable
private fun ParameterAnalyticsCard(
    chartState: ParameterChartState,
    onSelectMetric: (AnalyticMetric) -> Unit,
    onSelectAquarium: (String?) -> Unit
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Parameter analytics",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Track recent chemistry shifts and compare trends across tanks.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AssistChip(
                            onClick = {},
                            label = {
                                Text("${chartState.selectedMetric.label} trend")
                            }
                        )
                        AssistChip(
                            onClick = {},
                            label = {
                                Text("Last ${chartState.chartData.size} logs")
                            }
                        )
                    }

                    if (chartState.availableAquariums.size > 1) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(chartState.availableAquariums, key = { it.first }) { (id, name) ->
                                FilterChip(
                                    selected = chartState.selectedAquariumId == id,
                                    onClick = { onSelectAquarium(id) },
                                    label = { Text(name, maxLines = 1) }
                                )
                            }
                        }
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(AnalyticMetric.entries, key = { it.name }) { metric ->
                            FilterChip(
                                selected = chartState.selectedMetric == metric,
                                onClick = { onSelectMetric(metric) },
                                label = { Text(metric.label) }
                            )
                        }
                    }

                    if (chartState.chartData.size >= 2) {
                        ParameterTrendChart(
                            data = chartState.chartData,
                            lineColor = ANALYTIC_METRIC_COLORS[chartState.selectedMetric]
                                ?: MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Unit: ${chartState.selectedMetric.unit.ifBlank { "value" }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "Need at least 2 ${chartState.selectedMetric.label} logs for charting.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ParameterTrendChart(
    data: List<ParameterChartDataPoint>,
    lineColor: Color,
    modifier: Modifier = Modifier
) {
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
    val surfaceColor = MaterialTheme.colorScheme.surface

    val values = data.map { it.value }
    val minValue = values.minOrNull() ?: 0.0
    val maxValue = values.maxOrNull() ?: 0.0
    val valueRange = if (maxValue == minValue) 1.0 else maxValue - minValue
    val padding = valueRange * 0.1
    val chartMin = minValue - padding
    val chartMax = maxValue + padding
    val chartRange = chartMax - chartMin

    val textStyle = android.graphics.Paint().apply {
        color = textColor.hashCode()
        textSize = 28f
        textAlign = android.graphics.Paint.Align.CENTER
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
    ) {
        val leftPadding = 40.dp.toPx()
        val bottomPadding = 28.dp.toPx()
        val topPadding = 8.dp.toPx()
        val rightPadding = 8.dp.toPx()
        val chartWidth = size.width - leftPadding - rightPadding
        val chartHeight = size.height - bottomPadding - topPadding

        val ySteps = 4
        for (i in 0..ySteps) {
            val fraction = i.toFloat() / ySteps
            val y = topPadding + chartHeight * (1f - fraction)
            drawLine(
                color = gridColor,
                start = Offset(leftPadding, y),
                end = Offset(size.width - rightPadding, y),
                strokeWidth = 1.dp.toPx()
            )
            val yValue = chartMin + chartRange * fraction
            drawContext.canvas.nativeCanvas.drawText(
                String.format("%.1f", yValue),
                leftPadding / 2f,
                y + 4.dp.toPx(),
                textStyle
            )
        }

        val xStep = if (data.size > 1) chartWidth / (data.size - 1) else chartWidth
        for (i in data.indices) {
            val x = leftPadding + i * xStep
            drawContext.canvas.nativeCanvas.drawText(
                data[i].dayLabel,
                x,
                size.height - 4.dp.toPx(),
                textStyle
            )
        }

        val points = data.mapIndexed { index, point ->
            val x = leftPadding + index * xStep
            val normalizedValue = ((point.value - chartMin) / chartRange).toFloat()
                .coerceIn(0f, 1f)
            val y = topPadding + chartHeight * (1f - normalizedValue)
            Offset(x, y)
        }

        val areaPath = Path().apply {
            moveTo(points.first().x, topPadding + chartHeight)
            points.forEach { offset ->
                lineTo(offset.x, offset.y)
            }
            lineTo(points.last().x, topPadding + chartHeight)
            close()
        }
        drawPath(
            path = areaPath,
            color = lineColor.copy(alpha = 0.12f)
        )

        val linePath = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (i in 1 until points.size) {
                lineTo(points[i].x, points[i].y)
            }
        }
        drawPath(
            path = linePath,
            color = lineColor,
            style = Stroke(width = 2.5.dp.toPx())
        )

        points.forEach { point ->
            drawRoundRect(
                color = lineColor,
                topLeft = Offset(point.x - 3.dp.toPx(), point.y - 3.dp.toPx()),
                size = Size(6.dp.toPx(), 6.dp.toPx()),
                cornerRadius = CornerRadius(2.dp.toPx())
            )
        }
    }
}