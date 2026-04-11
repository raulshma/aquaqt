package com.keepaside.aquapt.feature.tasks

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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
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
import com.keepaside.aquapt.core.repository.DosingLogRepository
import com.keepaside.aquapt.core.repository.ReminderGroupRepository
import com.keepaside.aquapt.core.repository.TaskExecutionRepository
import com.keepaside.aquapt.core.repository.TaskTemplateRepository
import com.keepaside.aquapt.ui.theme.NeoHeroContainer
import com.keepaside.aquapt.ui.theme.NeoHeroOnContainer
import org.koin.java.KoinJavaComponent
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksDashboardScreen(
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
    val dosingLogRepository: DosingLogRepository = remember {
        KoinJavaComponent.get(DosingLogRepository::class.java)
    }
    val reminderGroupRepository: ReminderGroupRepository = remember {
        KoinJavaComponent.get(ReminderGroupRepository::class.java)
    }

    val viewModel: TasksDashboardViewModel = viewModel(
        factory = remember(
            aquariumRepository,
            taskTemplateRepository,
            taskExecutionRepository,
            dosingLogRepository,
            reminderGroupRepository
        ) {
            TasksDashboardViewModel.factory(
                aquariumRepository = aquariumRepository,
                taskTemplateRepository = taskTemplateRepository,
                taskExecutionRepository = taskExecutionRepository,
                dosingLogRepository = dosingLogRepository,
                reminderGroupRepository = reminderGroupRepository
            )
        }
    )

    val uiState by viewModel.uiState.collectAsState()
    var templateDialogState by remember { mutableStateOf<TaskTemplateDraft?>(null) }
    var completionDialogState by remember { mutableStateOf<CompletionDialogState?>(null) }
    var editDialogState by remember { mutableStateOf<EditExecutionDialogState?>(null) }
    var taskDetailsTemplateId by remember { mutableStateOf<String?>(null) }
    val selectedTemplate = uiState.taskTemplates.firstOrNull { it.id == taskDetailsTemplateId }
    val selectedTemplateHistory = selectedTemplate
        ?.let { template -> uiState.executionHistoryByTaskId[template.id].orEmpty() }
        .orEmpty()

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
                        containerColor = NeoHeroContainer,
                        contentColor = NeoHeroOnContainer
                    ),
                    shape = MaterialTheme.shapes.large,
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Tasks overview",
                            style = MaterialTheme.typography.titleMedium,
                            color = NeoHeroOnContainer
                        )
                        Text(
                            text = uiState.headline,
                            style = MaterialTheme.typography.bodyMedium,
                            color = NeoHeroOnContainer.copy(alpha = 0.78f)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SummaryTile(
                                modifier = Modifier.weight(1f),
                                title = "Templates",
                                value = uiState.summary.taskTemplateCount.toString()
                            )
                            SummaryTile(
                                modifier = Modifier.weight(1f),
                                title = "Due",
                                value = uiState.summary.dueTaskCount.toString()
                            )
                            SummaryTile(
                                modifier = Modifier.weight(1f),
                                title = "Recent",
                                value = uiState.summary.recentExecutionCount.toString()
                            )
                            SummaryTile(
                                modifier = Modifier.weight(1f),
                                title = "Dosing",
                                value = uiState.summary.dosingLogCount.toString()
                            )
                        }
                    }
                }
            }

            if (uiState.isEmpty) {
                item {
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Text(
                            text = "No task data yet. Create a care template here or import data in Settings.",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

            item {
                TaskTemplateManagementCard(
                    uiState = uiState,
                    onCreate = { templateDialogState = viewModel.newTaskTemplateDraft() },
                    onEdit = { templateId ->
                        templateDialogState = viewModel.draftForTaskTemplate(templateId)
                    },
                    onViewDetails = { templateId ->
                        taskDetailsTemplateId = templateId
                    },
                    onDelete = viewModel::deleteTaskTemplate
                )
            }

            if (uiState.dueTasks.isNotEmpty()) {
                item {
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Due task matrix",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )

                            uiState.dueTasks.take(10).forEach { due ->
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = due.taskTitle,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${due.aquariumName} • ${due.frequencyLabel} • ${due.completionsToday}/${due.timesPerDay} completed today",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        FilledTonalButton(
                                            onClick = { viewModel.completeTaskNow(due) },
                                            colors = ButtonDefaults.filledTonalButtonColors(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            )
                                        ) {
                                            Text("Complete now")
                                        }
                                        OutlinedButton(onClick = { viewModel.backdateTaskByDays(due, days = 1) }) {
                                            Text("Backdate 1 day")
                                        }
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            completionDialogState = CompletionDialogState(
                                                dueTask = due,
                                                completedAtInput = viewModel.currentCompletionDateTimeInput()
                                            )
                                        }
                                    ) {
                                        Text("Add note/time")
                                    }
                                    TextButton(onClick = { taskDetailsTemplateId = due.taskId }) {
                                        Text("View details")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (uiState.dosingSnapshots.any { it.count > 0 }) {
                item {
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Dosing snapshot",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            uiState.dosingSnapshots
                                .filter { it.count > 0 }
                                .take(8)
                                .forEach { snapshot ->
                                    val amount = snapshot.latestAmountMl?.let { value ->
                                        if (value == value.toInt().toDouble()) {
                                            "${value.toInt()} ml"
                                        } else {
                                            String.format(Locale.US, "%.2f ml", value)
                                        }
                                    } ?: "-"

                                    Text(
                                        text = "${snapshot.aquariumName}: ${snapshot.count} logs • latest ${snapshot.latestProduct ?: "-"} ($amount)",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                        }
                    }
                }
            }

            if (uiState.recentExecutions.isNotEmpty()) {
                item {
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Recent completions",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            uiState.recentExecutions.take(8).forEach { execution ->
                                RecentExecutionRow(
                                    execution = execution,
                                    onEdit = {
                                        editDialogState = EditExecutionDialogState(
                                            executionId = execution.executionId,
                                            taskTitle = execution.taskTitle,
                                            completedAtInput = execution.completedAtInput,
                                            note = execution.note.orEmpty()
                                        )
                                    },
                                    onDelete = {
                                        viewModel.deleteExecution(execution.executionId)
                                    },
                                    onViewTaskDetails = {
                                        taskDetailsTemplateId = execution.taskId
                                    }
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
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Text(
                            text = message,
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }

        templateDialogState?.let { dialogState ->
            TaskTemplateDialog(
                draft = dialogState,
                aquariumOptions = uiState.aquariumOptions,
                reminderGroupOptions = uiState.reminderGroupOptions,
                onDraftChange = { next -> templateDialogState = next },
                onDismiss = { templateDialogState = null },
                onConfirm = {
                    viewModel.saveTaskTemplate(dialogState)
                    templateDialogState = null
                }
            )
        }

        completionDialogState?.let { dialogState ->
            TaskCompletionDialog(
                title = "Complete ${dialogState.dueTask.taskTitle}",
                supportingText = dialogState.dueTask.aquariumName,
                completedAtInput = dialogState.completedAtInput,
                note = dialogState.note,
                confirmLabel = "Save completion",
                onCompletedAtChange = { value ->
                    completionDialogState = dialogState.copy(completedAtInput = value)
                },
                onNoteChange = { value ->
                    completionDialogState = dialogState.copy(note = value)
                },
                onDismiss = { completionDialogState = null },
                onConfirm = {
                    viewModel.completeTaskAt(
                        item = dialogState.dueTask,
                        completedAtInput = dialogState.completedAtInput,
                        note = dialogState.note
                    )
                    completionDialogState = null
                }
            )
        }

        editDialogState?.let { dialogState ->
            TaskCompletionDialog(
                title = "Edit completion",
                supportingText = dialogState.taskTitle,
                completedAtInput = dialogState.completedAtInput,
                note = dialogState.note,
                confirmLabel = "Update",
                onCompletedAtChange = { value ->
                    editDialogState = dialogState.copy(completedAtInput = value)
                },
                onNoteChange = { value ->
                    editDialogState = dialogState.copy(note = value)
                },
                onDismiss = { editDialogState = null },
                onConfirm = {
                    viewModel.updateExecution(
                        executionId = dialogState.executionId,
                        completedAtInput = dialogState.completedAtInput,
                        note = dialogState.note
                    )
                    editDialogState = null
                }
            )
        }

        selectedTemplate?.let { template ->
            ModalBottomSheet(onDismissRequest = { taskDetailsTemplateId = null }) {
                TaskTemplateDetailSheet(
                    template = template,
                    history = selectedTemplateHistory,
                    onEditTemplate = {
                        templateDialogState = viewModel.draftForTaskTemplate(template.id)
                        taskDetailsTemplateId = null
                    },
                    onEditExecution = { execution ->
                        editDialogState = EditExecutionDialogState(
                            executionId = execution.executionId,
                            taskTitle = execution.taskTitle,
                            completedAtInput = execution.completedAtInput,
                            note = execution.note.orEmpty()
                        )
                    },
                    onDeleteExecution = viewModel::deleteExecution,
                    onDismiss = { taskDetailsTemplateId = null }
                )
            }
        }
    }
}

@Composable
private fun TaskTemplateManagementCard(
    uiState: TasksDashboardUiState,
    onCreate: () -> Unit,
    onEdit: (String) -> Unit,
    onViewDetails: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "Task templates",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Recurring routines for one or more tanks.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FilledTonalButton(
                    onClick = onCreate,
                    enabled = uiState.aquariumOptions.isNotEmpty(),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text("Create")
                }
            }

            if (uiState.aquariumOptions.isEmpty()) {
                Text(
                    text = "Add a tank before creating recurring care templates.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }

            if (uiState.taskTemplates.isEmpty()) {
                Text(
                    text = "No templates yet. Start with feeding, water changes, testing, or dosing checks.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            uiState.taskTemplates.take(12).forEach { template ->
                TaskTemplateRow(
                    template = template,
                    onEdit = { onEdit(template.id) },
                    onViewDetails = { onViewDetails(template.id) },
                    onDelete = { onDelete(template.id) }
                )
            }
        }
    }
}

@Composable
private fun TaskTemplateRow(
    template: TaskTemplateListItem,
    onEdit: () -> Unit,
    onViewDetails: () -> Unit,
    onDelete: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = template.title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = buildList {
                add(template.categoryLabel)
                add(template.frequencyLabel)
                if (template.timesPerDayInput != "1" && template.frequency.kind == TaskFrequencyKind.DAILY) {
                    add("${template.timesPerDayInput}/day")
                }
                if (template.startDate.isNotBlank()) add("Starts ${template.startDate}")
                if (template.reminderHoursInput.isNotBlank()) add("Reminders ${template.reminderHoursInput}")
                if (!template.reminderGroupLabel.isNullOrBlank()) add("Group ${template.reminderGroupLabel}")
            }.joinToString(" - "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = template.aquariumNames.joinToString(", ").ifBlank { "No tanks assigned" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = if (template.completionCount == 0) {
                "No completions yet"
            } else {
                "${template.completionCount} completion${if (template.completionCount == 1) "" else "s"} • latest ${template.latestCompletionLabel}"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onEdit) {
                Text("Edit template")
            }
            OutlinedButton(onClick = onViewDetails) {
                Text("Details")
            }
            TextButton(onClick = onDelete) {
                Text("Delete")
            }
        }
    }
}

@Composable
private fun RecentExecutionRow(
    execution: RecentTaskExecutionItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onViewTaskDetails: (() -> Unit)? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "${execution.completedAtLabel} • ${execution.taskTitle} • ${execution.aquariumName}",
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

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onEdit) {
                Text("Edit")
            }
            onViewTaskDetails?.let {
                OutlinedButton(onClick = it) {
                    Text("Task details")
                }
            }
            TextButton(onClick = onDelete) {
                Text("Delete")
            }
        }
    }
}

@Composable
private fun TaskTemplateDialog(
    draft: TaskTemplateDraft,
    aquariumOptions: List<TaskTemplateAquariumOption>,
    reminderGroupOptions: List<ReminderGroupOption>,
    onDraftChange: (TaskTemplateDraft) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (draft.id == null) "Create task template" else "Edit task template") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = draft.title,
                    onValueChange = { value -> onDraftChange(draft.copy(title = value)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Task name") },
                    singleLine = true
                )

                OutlinedTextField(
                    value = draft.description,
                    onValueChange = { value -> onDraftChange(draft.copy(description = value)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Description") },
                    minLines = 2
                )

                Text(
                    text = "Tanks",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(aquariumOptions, key = { it.aquariumId }) { aquarium ->
                        val selected = aquarium.aquariumId in draft.aquariumIds
                        FilterChip(
                            selected = selected,
                            onClick = {
                                val nextIds = if (selected) {
                                    draft.aquariumIds - aquarium.aquariumId
                                } else {
                                    draft.aquariumIds + aquarium.aquariumId
                                }
                                onDraftChange(draft.copy(aquariumIds = nextIds))
                            },
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
                    items(categoryOptions, key = { it.first }) { option ->
                        FilterChip(
                            selected = draft.category == option.second,
                            onClick = { onDraftChange(draft.copy(category = option.second)) },
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
                        val frequency = option.second
                        FilterChip(
                            selected = draft.frequency.kind == frequency.kind,
                            onClick = { onDraftChange(draft.copy(frequency = frequency)) },
                            label = { Text(option.first) }
                        )
                    }
                }

                if (draft.frequency.kind == TaskFrequencyKind.CUSTOM) {
                    OutlinedTextField(
                        value = draft.customDays,
                        onValueChange = { value -> onDraftChange(draft.copy(customDays = value)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Every N days") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }

                if (draft.frequency.kind == TaskFrequencyKind.DAILY) {
                    OutlinedTextField(
                        value = draft.timesPerDay,
                        onValueChange = { value -> onDraftChange(draft.copy(timesPerDay = value)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Times per day") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = draft.startDate,
                    onValueChange = { value -> onDraftChange(draft.copy(startDate = value)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Start date") },
                    supportingText = { Text("Use yyyy-MM-dd or leave blank") },
                    singleLine = true
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
                            onClick = { onDraftChange(draft.copy(reminderGroupId = null)) },
                            label = { Text("None") }
                        )
                    }
                    items(reminderGroupOptions, key = { it.id }) { group ->
                        FilterChip(
                            selected = draft.reminderGroupId == group.id,
                            onClick = { onDraftChange(draft.copy(reminderGroupId = group.id)) },
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
                    onValueChange = { value -> onDraftChange(draft.copy(reminderHours = value)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Reminder hours") },
                    supportingText = {
                        Text("Optional, for example 8, 18. Overrides reminder group when set.")
                    },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = onConfirm,
                enabled = draft.title.isNotBlank() && draft.aquariumIds.isNotEmpty()
            ) {
                Text(if (draft.id == null) "Create" else "Update")
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
private fun TaskCompletionDialog(
    title: String,
    supportingText: String,
    completedAtInput: String,
    note: String,
    confirmLabel: String,
    onCompletedAtChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = completedAtInput,
                    onValueChange = onCompletedAtChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Completed at") },
                    supportingText = { Text("Use yyyy-MM-dd HH:mm") },
                    singleLine = true
                )
                OutlinedButton(
                    onClick = {
                        openNativeTaskDateTimePicker(
                            context = context,
                            initialInput = completedAtInput,
                            onSelected = onCompletedAtChange
                        )
                    }
                ) {
                    Text("Pick date & time")
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = onNoteChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Note") },
                    minLines = 3
                )
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = onConfirm,
                enabled = completedAtInput.isNotBlank()
            ) {
                Text(confirmLabel)
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
private fun SummaryTile(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.72f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

@Composable
private fun TaskTemplateDetailSheet(
    template: TaskTemplateListItem,
    history: List<RecentTaskExecutionItem>,
    onEditTemplate: () -> Unit,
    onEditExecution: (RecentTaskExecutionItem) -> Unit,
    onDeleteExecution: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Task details",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        Text(
            text = template.title,
            style = MaterialTheme.typography.titleSmall
        )

        Text(
            text = buildList {
                add(template.categoryLabel)
                add(template.frequencyLabel)
                if (template.timesPerDayInput != "1" && template.frequency.kind == TaskFrequencyKind.DAILY) {
                    add("${template.timesPerDayInput}/day")
                }
                if (template.startDate.isNotBlank()) add("Starts ${template.startDate}")
                if (!template.reminderGroupLabel.isNullOrBlank()) add("Group ${template.reminderGroupLabel}")
                if (template.reminderHoursInput.isNotBlank()) add("Reminders ${template.reminderHoursInput}")
            }.joinToString(" • "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = "Tanks: ${template.aquariumNames.joinToString(", ").ifBlank { "None" }}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedButton(onClick = onEditTemplate) {
            Text("Edit template")
        }

        Text(
            text = "Completion history",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )

        if (history.isEmpty()) {
            Text(
                text = "No completions yet for this task.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            history.take(20).forEach { execution ->
                RecentExecutionRow(
                    execution = execution,
                    onEdit = { onEditExecution(execution) },
                    onDelete = { onDeleteExecution(execution.executionId) }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    }
}

private fun openNativeTaskDateTimePicker(
    context: Context,
    initialInput: String,
    onSelected: (String) -> Unit
) {
    val zoneId = ZoneId.systemDefault()
    val initialDateTime = parseTaskDateTimeInput(initialInput, zoneId)
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
                    onSelected(formatDateTimeInput(selectedInstant, zoneId))
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

private data class CompletionDialogState(
    val dueTask: DueTaskMatrixItem,
    val completedAtInput: String,
    val note: String = ""
)

private data class EditExecutionDialogState(
    val executionId: String,
    val taskTitle: String,
    val completedAtInput: String,
    val note: String
)
