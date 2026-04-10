package com.keepaside.aquapt.feature.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.keepaside.aquapt.core.repository.AquariumRepository
import com.keepaside.aquapt.core.repository.DosingLogRepository
import com.keepaside.aquapt.core.repository.TaskExecutionRepository
import com.keepaside.aquapt.core.repository.TaskTemplateRepository
import org.koin.java.KoinJavaComponent
import java.util.Locale

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

    val viewModel: TasksDashboardViewModel = viewModel(
        factory = remember(
            aquariumRepository,
            taskTemplateRepository,
            taskExecutionRepository,
            dosingLogRepository
        ) {
            TasksDashboardViewModel.factory(
                aquariumRepository = aquariumRepository,
                taskTemplateRepository = taskTemplateRepository,
                taskExecutionRepository = taskExecutionRepository,
                dosingLogRepository = dosingLogRepository
            )
        }
    )

    val uiState by viewModel.uiState.collectAsState()
    var completionDialogState by remember { mutableStateOf<CompletionDialogState?>(null) }
    var editDialogState by remember { mutableStateOf<EditExecutionDialogState?>(null) }

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
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
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
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = uiState.headline,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
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
                    Card {
                        Text(
                            text = "No task data yet. Create task templates (next slice) or import data in Settings.",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

            if (uiState.dueTasks.isNotEmpty()) {
                item {
                    Card {
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
                                        FilledTonalButton(onClick = { viewModel.completeTaskNow(due) }) {
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
                                }
                            }
                        }
                    }
                }
            }

            if (uiState.dosingSnapshots.any { it.count > 0 }) {
                item {
                    Card {
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
                    Card {
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
    }
}

@Composable
private fun RecentExecutionRow(
    execution: RecentTaskExecutionItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit
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
            TextButton(onClick = onDelete) {
                Text("Delete")
            }
        }
    }
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
