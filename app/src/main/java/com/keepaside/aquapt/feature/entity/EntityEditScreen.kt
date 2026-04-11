package com.keepaside.aquapt.feature.entity

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.keepaside.aquapt.core.model.TaskCategory
import com.keepaside.aquapt.core.model.TaskFrequency
import com.keepaside.aquapt.core.model.TaskFrequencyKind
import com.keepaside.aquapt.core.repository.AquariumRepository
import com.keepaside.aquapt.core.repository.ReminderGroupRepository
import com.keepaside.aquapt.core.repository.TaskExecutionRepository
import com.keepaside.aquapt.core.repository.TaskTemplateRepository
import org.koin.java.KoinJavaComponent
import java.time.LocalDateTime
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntityEditScreen(
    kind: EntityEditKind?,
    entityId: String,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp)
) {
    val context = LocalContext.current
    val aquariumRepository: AquariumRepository = remember {
        KoinJavaComponent.get(AquariumRepository::class.java)
    }
    val taskTemplateRepository: TaskTemplateRepository = remember {
        KoinJavaComponent.get(TaskTemplateRepository::class.java)
    }
    val taskExecutionRepository: TaskExecutionRepository = remember {
        KoinJavaComponent.get(TaskExecutionRepository::class.java)
    }
    val reminderGroupRepository: ReminderGroupRepository = remember {
        KoinJavaComponent.get(ReminderGroupRepository::class.java)
    }

    val editViewModel: EntityEditViewModel = viewModel(
        factory = remember(
            kind,
            entityId,
            aquariumRepository,
            taskTemplateRepository,
            taskExecutionRepository,
            reminderGroupRepository
        ) {
            EntityEditViewModel.factory(
                kind = kind,
                entityId = entityId,
                aquariumRepository = aquariumRepository,
                taskTemplateRepository = taskTemplateRepository,
                taskExecutionRepository = taskExecutionRepository,
                reminderGroupRepository = reminderGroupRepository
            )
        }
    )

    val uiState by editViewModel.uiState.collectAsState()
    var showDeleteDialog by remember(uiState.kind, uiState.entityId) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding)
    ) {
        if (uiState.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            return@Box
        }

        if (uiState.isNotFound) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = uiState.headline,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = uiState.supportingText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    TextButton(onClick = onDone) {
                        Text("Back")
                    }
                }
            }
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
                    }
                }
            }

            uiState.taskTemplateDraft?.let { draft ->
                item {
                    TaskTemplateEditCard(
                        draft = draft,
                        aquariumOptions = uiState.aquariumOptions,
                        reminderGroupOptions = uiState.reminderGroupOptions,
                        isSaving = uiState.isSaving,
                        onTitleChanged = editViewModel::onTemplateTitleChanged,
                        onDescriptionChanged = editViewModel::onTemplateDescriptionChanged,
                        onCategoryChanged = editViewModel::onTemplateCategoryChanged,
                        onFrequencyChanged = editViewModel::onTemplateFrequencyChanged,
                        onCustomDaysChanged = editViewModel::onTemplateCustomDaysChanged,
                        onStartDateChanged = editViewModel::onTemplateStartDateChanged,
                        onTimesPerDayChanged = editViewModel::onTemplateTimesPerDayChanged,
                        onReminderHoursChanged = editViewModel::onTemplateReminderHoursChanged,
                        onReminderGroupChanged = editViewModel::onTemplateReminderGroupChanged,
                        onToggleAquarium = editViewModel::toggleTemplateAquariumSelection
                    )
                }
            }

            uiState.taskExecutionDraft?.let { draft ->
                item {
                    TaskExecutionEditCard(
                        draft = draft,
                        isSaving = uiState.isSaving,
                        onCompletedAtChanged = editViewModel::onExecutionCompletedAtChanged,
                        onNoteChanged = editViewModel::onExecutionNoteChanged,
                        onOpenDateTimePicker = {
                            openNativeEntityEditDateTimePicker(
                                context = context,
                                initialInput = draft.completedAtInput,
                                onSelected = editViewModel::onExecutionCompletedAtChanged
                            )
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

            item {
                Card {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { editViewModel.save(onSaved = onDone) },
                            enabled = uiState.canSave,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Save")
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

            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Danger zone",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = if (uiState.kind == EntityEditKind.TASK_TEMPLATE) {
                                "Delete this task template and linked execution history."
                            } else {
                                "Delete this execution record."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        OutlinedButton(
                            onClick = { showDeleteDialog = true },
                            enabled = !uiState.isSaving
                        ) {
                            Text("Delete")
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = {
                Text(
                    if (uiState.kind == EntityEditKind.TASK_TEMPLATE) {
                        "Delete task template?"
                    } else {
                        "Delete task execution?"
                    }
                )
            },
            text = {
                Text(
                    if (uiState.kind == EntityEditKind.TASK_TEMPLATE) {
                        "This permanently removes the template and linked completion history."
                    } else {
                        "This permanently removes the selected completion record."
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        editViewModel.deleteCurrentEntity(onDeleted = onDone)
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun TaskTemplateEditCard(
    draft: EntityEditTaskTemplateDraft,
    aquariumOptions: List<EntityEditAquariumOption>,
    reminderGroupOptions: List<EntityEditReminderGroupOption>,
    isSaving: Boolean,
    onTitleChanged: (String) -> Unit,
    onDescriptionChanged: (String) -> Unit,
    onCategoryChanged: (TaskCategory?) -> Unit,
    onFrequencyChanged: (TaskFrequency) -> Unit,
    onCustomDaysChanged: (String) -> Unit,
    onStartDateChanged: (String) -> Unit,
    onTimesPerDayChanged: (String) -> Unit,
    onReminderHoursChanged: (String) -> Unit,
    onReminderGroupChanged: (String?) -> Unit,
    onToggleAquarium: (String) -> Unit
) {
    val frequencyOptions = listOf(
        "Daily" to TaskFrequency.DAILY,
        "Weekly" to TaskFrequency.WEEKLY,
        "Bi-weekly" to TaskFrequency.BI_WEEKLY,
        "Monthly" to TaskFrequency.MONTHLY,
        "Custom" to TaskFrequency.custom(1)
    )
    val categoryOptions = listOf(
        "General" to null,
        "Maintenance" to TaskCategory.MAINTENANCE,
        "Feeding" to TaskCategory.FEEDING
    )

    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Task template",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            OutlinedTextField(
                value = draft.title,
                onValueChange = onTitleChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Task name") },
                singleLine = true,
                enabled = !isSaving
            )

            OutlinedTextField(
                value = draft.description,
                onValueChange = onDescriptionChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Description") },
                minLines = 2,
                enabled = !isSaving
            )

            Text(
                text = "Tanks",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(aquariumOptions, key = { it.id }) { option ->
                    FilterChip(
                        selected = option.isSelected,
                        onClick = { onToggleAquarium(option.id) },
                        enabled = !isSaving,
                        label = {
                            Text(
                                text = option.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }
            }

            Text(
                text = "Category",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categoryOptions, key = { it.first }) { option ->
                    FilterChip(
                        selected = draft.category == option.second,
                        onClick = { onCategoryChanged(option.second) },
                        enabled = !isSaving,
                        label = { Text(option.first) }
                    )
                }
            }

            Text(
                text = "Frequency",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(frequencyOptions, key = { it.first }) { option ->
                    FilterChip(
                        selected = draft.frequency.kind == option.second.kind,
                        onClick = { onFrequencyChanged(option.second) },
                        enabled = !isSaving,
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
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    enabled = !isSaving
                )
            }

            if (draft.frequency.kind == TaskFrequencyKind.DAILY) {
                OutlinedTextField(
                    value = draft.timesPerDay,
                    onValueChange = onTimesPerDayChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Times per day") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    enabled = !isSaving
                )
            }

            OutlinedTextField(
                value = draft.startDate,
                onValueChange = onStartDateChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Start date") },
                supportingText = { Text("Use yyyy-MM-dd or leave blank") },
                singleLine = true,
                enabled = !isSaving
            )

            Text(
                text = "Reminder group",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = draft.reminderGroupId == null,
                        onClick = { onReminderGroupChanged(null) },
                        enabled = !isSaving,
                        label = { Text("None") }
                    )
                }
                items(reminderGroupOptions, key = { it.id }) { group ->
                    FilterChip(
                        selected = draft.reminderGroupId == group.id,
                        onClick = { onReminderGroupChanged(group.id) },
                        enabled = !isSaving,
                        label = {
                            Text(
                                text = group.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }
            }

            reminderGroupOptions.firstOrNull { it.id == draft.reminderGroupId }?.let { selectedGroup ->
                val groupHours = if (selectedGroup.hours.isEmpty()) {
                    "No default hours"
                } else {
                    "Group hours: ${selectedGroup.hoursLabel}"
                }
                Text(
                    text = groupHours,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedTextField(
                value = draft.reminderHours,
                onValueChange = onReminderHoursChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Reminder hours") },
                supportingText = {
                    Text("Optional, for example 8, 18. Overrides reminder group when set.")
                },
                singleLine = true,
                enabled = !isSaving
            )
        }
    }
}

@Composable
private fun TaskExecutionEditCard(
    draft: EntityEditTaskExecutionDraft,
    isSaving: Boolean,
    onCompletedAtChanged: (String) -> Unit,
    onNoteChanged: (String) -> Unit,
    onOpenDateTimePicker: () -> Unit
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Task execution",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = "${draft.taskTitle} • ${draft.aquariumName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = draft.completedAtInput,
                onValueChange = onCompletedAtChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Completed at") },
                supportingText = { Text("Use yyyy-MM-dd HH:mm") },
                enabled = !isSaving,
                singleLine = true
            )

            OutlinedButton(
                onClick = onOpenDateTimePicker,
                enabled = !isSaving
            ) {
                Text("Pick date & time")
            }

            OutlinedTextField(
                value = draft.note,
                onValueChange = onNoteChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Note") },
                minLines = 3,
                enabled = !isSaving
            )
        }
    }
}

private fun openNativeEntityEditDateTimePicker(
    context: Context,
    initialInput: String,
    onSelected: (String) -> Unit
) {
    val zoneId = ZoneId.systemDefault()
    val initialDateTime = parseEntityEditDateTimeInput(initialInput, zoneId)
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
                    onSelected(formatEntityEditDateTimeInput(selectedInstant, zoneId))
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