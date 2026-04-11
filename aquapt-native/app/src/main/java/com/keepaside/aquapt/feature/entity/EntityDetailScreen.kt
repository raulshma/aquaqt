package com.keepaside.aquapt.feature.entity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.keepaside.aquapt.core.model.AssetCategory
import com.keepaside.aquapt.core.model.ConsumableUnit
import com.keepaside.aquapt.core.model.EntityKind
import com.keepaside.aquapt.core.model.IssueStatus
import com.keepaside.aquapt.core.model.LivestockStatus
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
import coil.compose.AsyncImage
import org.koin.java.KoinJavaComponent

@Composable
fun EntityDetailScreen(
    kind: EntityKind?,
    entityId: String,
    aquariumId: String?,
    onOpenEntityForm: (EntityKind, String?, String?) -> Unit = { _, _, _ -> },
    onOpenEntityEdit: (EntityEditKind, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp)
) {
    val aquariumRepository: AquariumRepository = remember {
        KoinJavaComponent.get(AquariumRepository::class.java)
    }
    val taskTemplateRepository: TaskTemplateRepository = remember {
        KoinJavaComponent.get(TaskTemplateRepository::class.java)
    }
    val taskExecutionRepository: TaskExecutionRepository = remember {
        KoinJavaComponent.get(TaskExecutionRepository::class.java)
    }
    val livestockRepository: LivestockRepository = remember {
        KoinJavaComponent.get(LivestockRepository::class.java)
    }
    val assetRepository: AssetRepository = remember {
        KoinJavaComponent.get(AssetRepository::class.java)
    }
    val consumableRepository: ConsumableRepository = remember {
        KoinJavaComponent.get(ConsumableRepository::class.java)
    }
    val issueRepository: IssueRepository = remember {
        KoinJavaComponent.get(IssueRepository::class.java)
    }
    val memoRepository: MemoRepository = remember {
        KoinJavaComponent.get(MemoRepository::class.java)
    }
    val dosingLogRepository: DosingLogRepository = remember {
        KoinJavaComponent.get(DosingLogRepository::class.java)
    }
    val waterParameterLogRepository: WaterParameterLogRepository = remember {
        KoinJavaComponent.get(WaterParameterLogRepository::class.java)
    }
    val timelineEventRepository: TimelineEventRepository = remember {
        KoinJavaComponent.get(TimelineEventRepository::class.java)
    }

    val detailViewModel: EntityDetailViewModel = viewModel(
        factory = remember(
            kind,
            entityId,
            aquariumId,
            aquariumRepository,
            taskTemplateRepository,
            taskExecutionRepository,
            livestockRepository,
            assetRepository,
            consumableRepository,
            issueRepository,
            memoRepository,
            dosingLogRepository,
            waterParameterLogRepository,
            timelineEventRepository
        ) {
            EntityDetailViewModel.factory(
                kind = kind,
                entityId = entityId,
                aquariumId = aquariumId,
                aquariumRepository = aquariumRepository,
                taskTemplateRepository = taskTemplateRepository,
                taskExecutionRepository = taskExecutionRepository,
                livestockRepository = livestockRepository,
                assetRepository = assetRepository,
                consumableRepository = consumableRepository,
                issueRepository = issueRepository,
                memoRepository = memoRepository,
                dosingLogRepository = dosingLogRepository,
                waterParameterLogRepository = waterParameterLogRepository,
                timelineEventRepository = timelineEventRepository
            )
        }
    )

    val uiState by detailViewModel.uiState.collectAsState()
    val livestockEditor = uiState.livestockEditor
    val assetEditor = uiState.assetEditor
    val consumableEditor = uiState.consumableEditor
    val issueEditor = uiState.issueEditor
    val memoEditor = uiState.memoEditor

    var livestockNameDraft by remember(livestockEditor?.id, livestockEditor?.name) {
        mutableStateOf(livestockEditor?.name.orEmpty())
    }
    var livestockSpeciesDraft by remember(livestockEditor?.id, livestockEditor?.species) {
        mutableStateOf(livestockEditor?.species.orEmpty())
    }
    var livestockQuantityDraft by remember(livestockEditor?.id, livestockEditor?.quantityInput) {
        mutableStateOf(livestockEditor?.quantityInput.orEmpty())
    }
    var livestockStatusDraft by remember(livestockEditor?.id, livestockEditor?.status) {
        mutableStateOf(livestockEditor?.status ?: LivestockStatus.ACTIVE)
    }
    var livestockDietaryNotesDraft by remember(livestockEditor?.id, livestockEditor?.dietaryNotes) {
        mutableStateOf(livestockEditor?.dietaryNotes.orEmpty())
    }

    var assetCategoryDraft by remember(assetEditor?.id, assetEditor?.category) {
        mutableStateOf(assetEditor?.category ?: AssetCategory.OTHER)
    }
    var assetBrandModelDraft by remember(assetEditor?.id, assetEditor?.brandModel) {
        mutableStateOf(assetEditor?.brandModel.orEmpty())
    }
    var assetPurchasedAtDraft by remember(assetEditor?.id, assetEditor?.purchasedAtInput) {
        mutableStateOf(assetEditor?.purchasedAtInput.orEmpty())
    }
    var assetPriceDraft by remember(assetEditor?.id, assetEditor?.priceInput) {
        mutableStateOf(assetEditor?.priceInput.orEmpty())
    }

    var consumableNameDraft by remember(consumableEditor?.id, consumableEditor?.name) {
        mutableStateOf(consumableEditor?.name.orEmpty())
    }
    var consumableUnitDraft by remember(consumableEditor?.id, consumableEditor?.unit) {
        mutableStateOf(consumableEditor?.unit ?: ConsumableUnit.ML)
    }
    var consumableRemainingDraft by remember(consumableEditor?.id, consumableEditor?.remainingInput) {
        mutableStateOf(consumableEditor?.remainingInput.orEmpty())
    }
    var consumableReorderAtDraft by remember(consumableEditor?.id, consumableEditor?.reorderAtInput) {
        mutableStateOf(consumableEditor?.reorderAtInput.orEmpty())
    }

    var issueStatusDraft by remember(issueEditor?.id, issueEditor?.status) {
        mutableStateOf(issueEditor?.status ?: IssueStatus.OPEN)
    }
    var issueResolutionDraft by remember(issueEditor?.id, issueEditor?.resolutionNote) {
        mutableStateOf(issueEditor?.resolutionNote.orEmpty())
    }

    var memoContentDraft by remember(memoEditor?.id, memoEditor?.content) {
        mutableStateOf(memoEditor?.content.orEmpty())
    }

    var showDeleteDialog by remember(uiState.entityId, uiState.kindLabel) {
        mutableStateOf(false)
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
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = uiState.headline,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        uiState.title.takeIf { it.isNotBlank() }?.let { title ->
                            Text(
                                text = title,
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        uiState.subtitle?.takeIf { it.isNotBlank() }?.let { subtitle ->
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        uiState.aquariumName?.let { aquariumName ->
                            Text(
                                text = "Tank: $aquariumName",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Text(
                            text = "${uiState.kindLabel} ID: ${uiState.entityId}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        if (uiState.photoUri != null) {
                            Text(
                                text = "Photo attached",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }

            if (!uiState.isNotFound && uiState.metrics.isNotEmpty()) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(uiState.metrics, key = { it.label }) { metric ->
                            MetricCard(metric = metric)
                        }
                    }
                }
            }

            if (!uiState.isNotFound && uiState.fields.isNotEmpty()) {
                item {
                    Card {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Details",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )

                            uiState.fields.forEach { field ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = field.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = field.value,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1.2f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (!uiState.isNotFound && uiState.aquariumId != null) {
                item {
                    EntityCreateActionsCard(
                        isBusy = uiState.isActionInProgress,
                        onCreateIssue = {
                            onOpenEntityForm(EntityKind.ISSUE, uiState.aquariumId, null)
                        },
                        onCreateMemo = {
                            onOpenEntityForm(EntityKind.MEMO, uiState.aquariumId, null)
                        },
                        onCreateDosing = {
                            onOpenEntityForm(EntityKind.DOSING, uiState.aquariumId, null)
                        },
                        onCreateParameters = {
                            onOpenEntityForm(EntityKind.PARAMETER_LOG, uiState.aquariumId, null)
                        },
                        onCreateConsumable = {
                            onOpenEntityForm(EntityKind.CONSUMABLE, uiState.aquariumId, null)
                        }
                    )
                }
            }

            if (!uiState.isNotFound && kind == EntityKind.CONSUMABLE && uiState.aquariumId != null) {
                item {
                    ConsumableActionsCard(
                        isBusy = uiState.isActionInProgress,
                        onUseConsumable = {
                            onOpenEntityForm(
                                EntityKind.CONSUMABLE,
                                uiState.aquariumId,
                                uiState.entityId
                            )
                        }
                    )
                }
            }

            if (!uiState.isNotFound && kind == EntityKind.TASK) {
                item {
                    TaskActionsCard(
                        isBusy = uiState.isActionInProgress,
                        executionHistory = uiState.taskExecutionHistory,
                        onEditTemplate = {
                            onOpenEntityEdit(EntityEditKind.TASK_TEMPLATE, uiState.entityId)
                        },
                        onEditExecution = { executionId ->
                            onOpenEntityEdit(EntityEditKind.TASK_EXECUTION, executionId)
                        }
                    )
                }
            }

            if (!uiState.isNotFound && livestockEditor != null) {
                item {
                    LivestockActionsCard(
                        name = livestockNameDraft,
                        species = livestockSpeciesDraft,
                        quantity = livestockQuantityDraft,
                        status = livestockStatusDraft,
                        dietaryNotes = livestockDietaryNotesDraft,
                        isBusy = uiState.isActionInProgress,
                        onNameChanged = { livestockNameDraft = it },
                        onSpeciesChanged = { livestockSpeciesDraft = it },
                        onQuantityChanged = { livestockQuantityDraft = it },
                        onStatusChanged = { livestockStatusDraft = it },
                        onDietaryNotesChanged = { livestockDietaryNotesDraft = it },
                        onSave = {
                            detailViewModel.saveLivestockDetails(
                                nameInput = livestockNameDraft,
                                speciesInput = livestockSpeciesDraft,
                                quantityInput = livestockQuantityDraft,
                                status = livestockStatusDraft,
                                dietaryNotesInput = livestockDietaryNotesDraft
                            )
                        },
                        onArchive = {
                            detailViewModel.archiveLivestock()
                        },
                        onDelete = { showDeleteDialog = true }
                    )
                }
            }

            if (!uiState.isNotFound && assetEditor != null) {
                item {
                    AssetActionsCard(
                        category = assetCategoryDraft,
                        brandModel = assetBrandModelDraft,
                        purchasedAt = assetPurchasedAtDraft,
                        price = assetPriceDraft,
                        isBusy = uiState.isActionInProgress,
                        onCategoryChanged = { assetCategoryDraft = it },
                        onBrandModelChanged = { assetBrandModelDraft = it },
                        onPurchasedAtChanged = { assetPurchasedAtDraft = it },
                        onPriceChanged = { assetPriceDraft = it },
                        onSave = {
                            detailViewModel.saveAssetDetails(
                                category = assetCategoryDraft,
                                brandModelInput = assetBrandModelDraft,
                                purchasedAtInput = assetPurchasedAtDraft,
                                priceInput = assetPriceDraft
                            )
                        },
                        onDelete = { showDeleteDialog = true }
                    )
                }
            }

            if (!uiState.isNotFound && consumableEditor != null) {
                item {
                    ConsumableInventoryActionsCard(
                        name = consumableNameDraft,
                        unit = consumableUnitDraft,
                        remaining = consumableRemainingDraft,
                        reorderAt = consumableReorderAtDraft,
                        isBusy = uiState.isActionInProgress,
                        onNameChanged = { consumableNameDraft = it },
                        onUnitChanged = { consumableUnitDraft = it },
                        onRemainingChanged = { consumableRemainingDraft = it },
                        onReorderAtChanged = { consumableReorderAtDraft = it },
                        onSave = {
                            detailViewModel.saveConsumableDetails(
                                nameInput = consumableNameDraft,
                                unit = consumableUnitDraft,
                                remainingInput = consumableRemainingDraft,
                                reorderAtInput = consumableReorderAtDraft
                            )
                        },
                        onDelete = { showDeleteDialog = true }
                    )
                }
            }

            if (!uiState.isNotFound && issueEditor != null) {
                item {
                    IssueActionsCard(
                        status = issueStatusDraft,
                        resolutionNote = issueResolutionDraft,
                        isBusy = uiState.isActionInProgress,
                        onStatusChanged = { issueStatusDraft = it },
                        onResolutionNoteChanged = { issueResolutionDraft = it },
                        onSave = {
                            detailViewModel.saveIssueUpdate(
                                status = issueStatusDraft,
                                resolutionNoteInput = issueResolutionDraft
                            )
                        },
                        onDelete = { showDeleteDialog = true }
                    )
                }
            }

            if (!uiState.isNotFound && memoEditor != null) {
                item {
                    MemoActionsCard(
                        content = memoContentDraft,
                        isBusy = uiState.isActionInProgress,
                        onContentChanged = { memoContentDraft = it },
                        onSave = {
                            detailViewModel.saveMemoContent(memoContentDraft)
                        },
                        onDelete = { showDeleteDialog = true }
                    )
                }
            }

            if (!uiState.isNotFound && uiState.relatedPhotos.isNotEmpty()) {
                item {
                    RelatedPhotoGalleryCard(photos = uiState.relatedPhotos)
                }
            }

            if (!uiState.isNotFound && uiState.relatedEvents.isNotEmpty()) {
                item {
                    Card {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Linked timeline activity",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )

                            uiState.relatedEvents.forEach { event ->
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = event.title,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = event.supportingText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
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

        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = {
                    Text(text = "Delete ${uiState.kindLabel.lowercase()}?")
                },
                text = {
                    Text(
                        text = "This removes the ${uiState.kindLabel.lowercase()} record. " +
                            "A timeline entry will still be added for activity history."
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteDialog = false
                            detailViewModel.deleteCurrentEntity()
                        }
                    ) {
                        Text(text = "Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text(text = "Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun RelatedPhotoGalleryCard(photos: List<EntityRelatedPhotoItem>) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Photo gallery",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(photos, key = { it.id }) { photo ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.width(220.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            AsyncImage(
                                model = photo.uri,
                                contentDescription = photo.title,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(128.dp),
                                contentScale = ContentScale.Crop
                            )

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = photo.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = photo.supportingText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
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

@Composable
private fun IssueActionsCard(
    status: IssueStatus,
    resolutionNote: String,
    isBusy: Boolean,
    onStatusChanged: (IssueStatus) -> Unit,
    onResolutionNoteChanged: (String) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Issue actions",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(IssueStatus.entries, key = { it.name }) { option ->
                    FilterChip(
                        selected = option == status,
                        onClick = { onStatusChanged(option) },
                        enabled = !isBusy,
                        label = { Text(option.label()) }
                    )
                }
            }

            OutlinedTextField(
                value = resolutionNote,
                onValueChange = onResolutionNoteChanged,
                label = { Text("Resolution note") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isBusy,
                minLines = 2
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onSave,
                    enabled = !isBusy
                ) {
                    Text("Save issue")
                }

                OutlinedButton(
                    onClick = onDelete,
                    enabled = !isBusy
                ) {
                    Text("Delete")
                }
            }
        }
    }
}

@Composable
private fun EntityCreateActionsCard(
    isBusy: Boolean,
    onCreateIssue: () -> Unit,
    onCreateMemo: () -> Unit,
    onCreateDosing: () -> Unit,
    onCreateParameters: () -> Unit,
    onCreateConsumable: () -> Unit
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Create linked activity",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = "Open native forms to add issue, memo, dosing, parameter, or consumable activity for this tank.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onCreateIssue,
                    enabled = !isBusy
                ) {
                    Text("New issue")
                }

                OutlinedButton(
                    onClick = onCreateMemo,
                    enabled = !isBusy
                ) {
                    Text("New memo")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onCreateDosing,
                    enabled = !isBusy
                ) {
                    Text("New dosing")
                }

                OutlinedButton(
                    onClick = onCreateParameters,
                    enabled = !isBusy
                ) {
                    Text("New parameters")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onCreateConsumable,
                    enabled = !isBusy
                ) {
                    Text("New consumable")
                }
            }
        }
    }
}

@Composable
private fun ConsumableActionsCard(
    isBusy: Boolean,
    onUseConsumable: () -> Unit
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Consumable actions",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = "Log usage and update remaining stock for this consumable.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onUseConsumable,
                    enabled = !isBusy
                ) {
                    Text("Use consumable")
                }
            }
        }
    }
}

@Composable
private fun TaskActionsCard(
    isBusy: Boolean,
    executionHistory: List<EntityTaskExecutionItem>,
    onEditTemplate: () -> Unit,
    onEditExecution: (String) -> Unit
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Task actions",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = "Edit this recurring template or adjust completion history records.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onEditTemplate,
                    enabled = !isBusy
                ) {
                    Text("Edit template")
                }
            }

            Text(
                text = "Recent completions",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (executionHistory.isEmpty()) {
                Text(
                    text = "No completion history yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                executionHistory.take(8).forEach { execution ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "${execution.completedAtLabel} • ${execution.aquariumName}",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        execution.note?.takeIf { it.isNotBlank() }?.let { note ->
                            Text(
                                text = note,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        OutlinedButton(
                            onClick = { onEditExecution(execution.id) },
                            enabled = !isBusy
                        ) {
                            Text("Edit execution")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LivestockActionsCard(
    name: String,
    species: String,
    quantity: String,
    status: LivestockStatus,
    dietaryNotes: String,
    isBusy: Boolean,
    onNameChanged: (String) -> Unit,
    onSpeciesChanged: (String) -> Unit,
    onQuantityChanged: (String) -> Unit,
    onStatusChanged: (LivestockStatus) -> Unit,
    onDietaryNotesChanged: (String) -> Unit,
    onSave: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Resident actions",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = "Adjust profile details, archive the resident, or remove with cleanup safeguards.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = name,
                onValueChange = onNameChanged,
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isBusy
            )

            OutlinedTextField(
                value = species,
                onValueChange = onSpeciesChanged,
                label = { Text("Species") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isBusy
            )

            OutlinedTextField(
                value = quantity,
                onValueChange = onQuantityChanged,
                label = { Text("Quantity") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isBusy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(LivestockStatus.entries, key = { it.name }) { option ->
                    FilterChip(
                        selected = option == status,
                        onClick = { onStatusChanged(option) },
                        enabled = !isBusy,
                        label = { Text(option.label()) }
                    )
                }
            }

            OutlinedTextField(
                value = dietaryNotes,
                onValueChange = onDietaryNotesChanged,
                label = { Text("Dietary notes") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isBusy,
                minLines = 2
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onSave,
                    enabled = !isBusy
                ) {
                    Text("Save profile")
                }

                OutlinedButton(
                    onClick = onArchive,
                    enabled = !isBusy && status != LivestockStatus.DECEASED
                ) {
                    Text(
                        if (status == LivestockStatus.DECEASED) {
                            "Archived"
                        } else {
                            "Archive"
                        }
                    )
                }

                OutlinedButton(
                    onClick = onDelete,
                    enabled = !isBusy
                ) {
                    Text("Delete")
                }
            }
        }
    }
}

@Composable
private fun AssetActionsCard(
    category: AssetCategory,
    brandModel: String,
    purchasedAt: String,
    price: String,
    isBusy: Boolean,
    onCategoryChanged: (AssetCategory) -> Unit,
    onBrandModelChanged: (String) -> Unit,
    onPurchasedAtChanged: (String) -> Unit,
    onPriceChanged: (String) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Asset actions",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = "Update category, purchase metadata, or remove this asset.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(AssetCategory.entries, key = { it.name }) { option ->
                    FilterChip(
                        selected = option == category,
                        onClick = { onCategoryChanged(option) },
                        enabled = !isBusy,
                        label = { Text(option.label()) }
                    )
                }
            }

            OutlinedTextField(
                value = brandModel,
                onValueChange = onBrandModelChanged,
                label = { Text("Brand/model") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isBusy
            )

            OutlinedTextField(
                value = purchasedAt,
                onValueChange = onPurchasedAtChanged,
                label = { Text("Purchased at") },
                supportingText = { Text("Optional • yyyy-MM-dd or yyyy-MM-dd HH:mm") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isBusy
            )

            OutlinedTextField(
                value = price,
                onValueChange = onPriceChanged,
                label = { Text("Price") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isBusy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onSave,
                    enabled = !isBusy
                ) {
                    Text("Save asset")
                }

                OutlinedButton(
                    onClick = onDelete,
                    enabled = !isBusy
                ) {
                    Text("Delete")
                }
            }
        }
    }
}

@Composable
private fun ConsumableInventoryActionsCard(
    name: String,
    unit: ConsumableUnit,
    remaining: String,
    reorderAt: String,
    isBusy: Boolean,
    onNameChanged: (String) -> Unit,
    onUnitChanged: (ConsumableUnit) -> Unit,
    onRemainingChanged: (String) -> Unit,
    onReorderAtChanged: (String) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Consumable inventory",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = "Adjust stock metadata and reorder thresholds for this consumable.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = name,
                onValueChange = onNameChanged,
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isBusy
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ConsumableUnit.entries, key = { it.name }) { option ->
                    FilterChip(
                        selected = option == unit,
                        onClick = { onUnitChanged(option) },
                        enabled = !isBusy,
                        label = { Text(option.name.lowercase()) }
                    )
                }
            }

            OutlinedTextField(
                value = remaining,
                onValueChange = onRemainingChanged,
                label = { Text("Remaining") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isBusy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )

            OutlinedTextField(
                value = reorderAt,
                onValueChange = onReorderAtChanged,
                label = { Text("Reorder threshold") },
                supportingText = { Text("Optional") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isBusy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onSave,
                    enabled = !isBusy
                ) {
                    Text("Save inventory")
                }

                OutlinedButton(
                    onClick = onDelete,
                    enabled = !isBusy
                ) {
                    Text("Delete")
                }
            }
        }
    }
}

@Composable
private fun MemoActionsCard(
    content: String,
    isBusy: Boolean,
    onContentChanged: (String) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Memo actions",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            OutlinedTextField(
                value = content,
                onValueChange = onContentChanged,
                label = { Text("Memo content") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isBusy,
                minLines = 3
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onSave,
                    enabled = !isBusy
                ) {
                    Text("Save memo")
                }

                OutlinedButton(
                    onClick = onDelete,
                    enabled = !isBusy
                ) {
                    Text("Delete")
                }
            }
        }
    }
}

private fun IssueStatus.label(): String =
    name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercaseChar() }

private fun AssetCategory.label(): String =
    name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercaseChar() }

private fun LivestockStatus.label(): String =
    name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercaseChar() }

@Composable
private fun MetricCard(metric: EntityDetailMetric) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = metric.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = metric.value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}