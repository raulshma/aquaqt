package com.keepaside.aquapt.feature.entity

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.keepaside.aquapt.core.model.ConsumableUnit
import com.keepaside.aquapt.core.model.EntityKind
import com.keepaside.aquapt.core.repository.AquariumRepository
import com.keepaside.aquapt.core.repository.ConsumableRepository
import com.keepaside.aquapt.core.repository.DosingLogRepository
import com.keepaside.aquapt.core.repository.IssueRepository
import com.keepaside.aquapt.core.repository.MemoRepository
import com.keepaside.aquapt.core.repository.TimelineEventRepository
import com.keepaside.aquapt.core.repository.WaterParameterLogRepository
import org.koin.java.KoinJavaComponent
import java.time.LocalDateTime
import java.time.ZoneId

@Composable
fun EntityFormScreen(
    kind: EntityKind?,
    aquariumId: String?,
    targetEntityId: String?,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp)
) {
    val context = LocalContext.current
    val aquariumRepository: AquariumRepository = remember {
        KoinJavaComponent.get(AquariumRepository::class.java)
    }
    val issueRepository: IssueRepository = remember {
        KoinJavaComponent.get(IssueRepository::class.java)
    }
    val memoRepository: MemoRepository = remember {
        KoinJavaComponent.get(MemoRepository::class.java)
    }
    val consumableRepository: ConsumableRepository = remember {
        KoinJavaComponent.get(ConsumableRepository::class.java)
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

    val formViewModel: EntityFormViewModel = viewModel(
        factory = remember(
            kind,
            aquariumId,
            targetEntityId,
            aquariumRepository,
            consumableRepository,
            issueRepository,
            memoRepository,
            dosingLogRepository,
            waterParameterLogRepository,
            timelineEventRepository
        ) {
            EntityFormViewModel.factory(
                kind = kind,
                aquariumId = aquariumId,
                targetEntityId = targetEntityId,
                aquariumRepository = aquariumRepository,
                consumableRepository = consumableRepository,
                issueRepository = issueRepository,
                memoRepository = memoRepository,
                dosingLogRepository = dosingLogRepository,
                waterParameterLogRepository = waterParameterLogRepository,
                timelineEventRepository = timelineEventRepository
            )
        }
    )

    val uiState by formViewModel.uiState.collectAsState()
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        when (uiState.kind) {
            EntityKind.MEMO -> formViewModel.onMemoPhotoUriChanged(uri?.toString().orEmpty())
            EntityKind.CONSUMABLE -> formViewModel.onConsumablePhotoUriChanged(uri?.toString().orEmpty())
            else -> Unit
        }
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
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = uiState.supportingText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        uiState.aquariumName?.let { aquariumName ->
                            Text(
                                text = "Tank: $aquariumName",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }

            if (uiState.aquariumOptions.isNotEmpty()) {
                item {
                    Card {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Tank",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )

                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(uiState.aquariumOptions, key = { it.id }) { option ->
                                    FilterChip(
                                        selected = option.isSelected,
                                        onClick = { formViewModel.onAquariumSelected(option.id) },
                                        enabled = !uiState.isSaving,
                                        label = { Text(option.name) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Card {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = uiState.draft.createdAtInput,
                            onValueChange = formViewModel::onCreatedAtInputChanged,
                            label = { Text("When") },
                            supportingText = {
                                Text("Format: yyyy-MM-dd HH:mm")
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !uiState.isSaving
                        )
                        OutlinedButton(
                            onClick = {
                                openNativeEntityFormDateTimePicker(
                                    context = context,
                                    initialInput = uiState.draft.createdAtInput,
                                    onSelected = formViewModel::onCreatedAtInputChanged
                                )
                            },
                            enabled = !uiState.isSaving
                        ) {
                            Text("Pick date & time")
                        }

                        when (uiState.kind) {
                            EntityKind.ISSUE -> {
                                OutlinedTextField(
                                    value = uiState.draft.issueTitle,
                                    onValueChange = formViewModel::onIssueTitleChanged,
                                    label = { Text("Issue title") },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !uiState.isSaving
                                )
                            }

                            EntityKind.MEMO -> {
                                OutlinedTextField(
                                    value = uiState.draft.memoContent,
                                    onValueChange = formViewModel::onMemoContentChanged,
                                    label = { Text("Memo content") },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !uiState.isSaving,
                                    minLines = 3
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            photoPickerLauncher.launch(
                                                PickVisualMediaRequest.Builder()
                                                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                    .build()
                                            )
                                        },
                                        enabled = !uiState.isSaving
                                    ) {
                                        Text(
                                            if (uiState.draft.memoPhotoUri.isBlank()) "Attach photo" else "Change photo"
                                        )
                                    }

                                    if (uiState.draft.memoPhotoUri.isNotBlank()) {
                                        TextButton(
                                            onClick = { formViewModel.onMemoPhotoUriChanged("") },
                                            enabled = !uiState.isSaving
                                        ) {
                                            Text("Remove")
                                        }
                                    }
                                }

                                if (uiState.draft.memoPhotoUri.isNotBlank()) {
                                    Text(
                                        text = "Photo selected",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            EntityKind.CONSUMABLE -> {
                                val targetConsumable = uiState.targetConsumable

                                if (targetConsumable != null) {
                                    Text(
                                        text = "Consumable: ${targetConsumable.name}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Current stock: ${targetConsumable.remainingLabel}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    OutlinedTextField(
                                        value = uiState.draft.consumableAmountUsed,
                                        onValueChange = formViewModel::onConsumableAmountUsedChanged,
                                        label = { Text("Amount used") },
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = !uiState.isSaving,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                                    )

                                    OutlinedTextField(
                                        value = uiState.draft.consumableUseNote,
                                        onValueChange = formViewModel::onConsumableUseNoteChanged,
                                        label = { Text("Note (optional)") },
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = !uiState.isSaving,
                                        minLines = 2
                                    )
                                } else {
                                    OutlinedTextField(
                                        value = uiState.draft.consumableName,
                                        onValueChange = formViewModel::onConsumableNameChanged,
                                        label = { Text("Name") },
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = !uiState.isSaving
                                    )

                                    Text(
                                        text = "Unit",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(ConsumableUnit.entries, key = { it.name }) { unit ->
                                            FilterChip(
                                                selected = uiState.draft.consumableUnit == unit.name,
                                                onClick = { formViewModel.onConsumableUnitChanged(unit) },
                                                enabled = !uiState.isSaving,
                                                label = {
                                                    Text(unit.name.lowercase())
                                                }
                                            )
                                        }
                                    }

                                    OutlinedTextField(
                                        value = uiState.draft.consumableRemaining,
                                        onValueChange = formViewModel::onConsumableRemainingChanged,
                                        label = { Text("Remaining") },
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = !uiState.isSaving,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                                    )

                                    OutlinedTextField(
                                        value = uiState.draft.consumableReorderAt,
                                        onValueChange = formViewModel::onConsumableReorderAtChanged,
                                        label = { Text("Reorder threshold (optional)") },
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = !uiState.isSaving,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                                    )

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                photoPickerLauncher.launch(
                                                    PickVisualMediaRequest.Builder()
                                                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                        .build()
                                                )
                                            },
                                            enabled = !uiState.isSaving
                                        ) {
                                            Text(
                                                if (uiState.draft.consumablePhotoUri.isBlank()) {
                                                    "Attach photo"
                                                } else {
                                                    "Change photo"
                                                }
                                            )
                                        }

                                        if (uiState.draft.consumablePhotoUri.isNotBlank()) {
                                            TextButton(
                                                onClick = { formViewModel.onConsumablePhotoUriChanged("") },
                                                enabled = !uiState.isSaving
                                            ) {
                                                Text("Remove")
                                            }
                                        }
                                    }

                                    if (uiState.draft.consumablePhotoUri.isNotBlank()) {
                                        Text(
                                            text = "Photo selected",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            EntityKind.DOSING -> {
                                OutlinedTextField(
                                    value = uiState.draft.dosingProduct,
                                    onValueChange = formViewModel::onDosingProductChanged,
                                    label = { Text("Product") },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !uiState.isSaving
                                )
                                OutlinedTextField(
                                    value = uiState.draft.dosingAmountMl,
                                    onValueChange = formViewModel::onDosingAmountMlChanged,
                                    label = { Text("Amount ml") },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !uiState.isSaving,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )
                                OutlinedTextField(
                                    value = uiState.draft.dosingNote,
                                    onValueChange = formViewModel::onDosingNoteChanged,
                                    label = { Text("Note (optional)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !uiState.isSaving,
                                    minLines = 2
                                )
                            }

                            EntityKind.PARAMETER_LOG -> {
                                EntityFormParameterField.entries.forEach { field ->
                                    OutlinedTextField(
                                        value = uiState.draft.parameterValue(field),
                                        onValueChange = { value ->
                                            formViewModel.onParameterValueChanged(field, value)
                                        },
                                        label = { Text(field.label) },
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = !uiState.isSaving,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                    )
                                }
                            }

                            else -> {
                                Text(
                                    text = "This form kind is not supported yet.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
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

            item {
                Card {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = formViewModel::save,
                            enabled = uiState.canSave,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(uiState.saveButtonLabel)
                        }
                        OutlinedButton(
                            onClick = onDone,
                            enabled = !uiState.isSaving,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Back")
                        }
                    }
                }
            }
        }
    }
}

private fun openNativeEntityFormDateTimePicker(
    context: Context,
    initialInput: String,
    onSelected: (String) -> Unit
) {
    val zoneId = ZoneId.systemDefault()
    val initialDateTime = parseEntityFormDateTimeInput(initialInput, zoneId)
        ?.atZone(zoneId)
        ?.toLocalDateTime()
        ?: LocalDateTime.now(zoneId)

    DatePickerDialog(
        context,
        { _, year, monthOfYear, dayOfMonth ->
            TimePickerDialog(
                context,
                { _, hourOfDay, minute ->
                    val dateTime = LocalDateTime.of(year, monthOfYear + 1, dayOfMonth, hourOfDay, minute)
                    val selectedInstant = dateTime.atZone(zoneId).toInstant()
                    onSelected(formatEntityFormDateTimeInput(selectedInstant, zoneId))
                },
                initialDateTime.hour,
                initialDateTime.minute,
                true
            ).show()
        },
        initialDateTime.year,
        initialDateTime.monthValue - 1,
        initialDateTime.dayOfMonth
    ).show()
}
