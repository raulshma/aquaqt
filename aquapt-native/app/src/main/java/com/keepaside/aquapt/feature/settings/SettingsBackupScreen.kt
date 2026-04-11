package com.keepaside.aquapt.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.keepaside.aquapt.core.backup.BackupCompatibilityGateway
import com.keepaside.aquapt.core.repository.ReminderGroupRepository
import com.keepaside.aquapt.core.repository.TaskTemplateRepository
import org.koin.java.KoinJavaComponent

@Composable
fun SettingsBackupScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(24.dp)
) {
    val backupGateway: BackupCompatibilityGateway = remember {
        KoinJavaComponent.get(BackupCompatibilityGateway::class.java)
    }
    val viewModel: SettingsBackupViewModel = viewModel(
        factory = remember(backupGateway) { SettingsBackupViewModel.factory(backupGateway) }
    )
    val uiState by viewModel.uiState.collectAsState()
    val reminderGroupRepository: ReminderGroupRepository = remember {
        KoinJavaComponent.get(ReminderGroupRepository::class.java)
    }
    val taskTemplateRepository: TaskTemplateRepository = remember {
        KoinJavaComponent.get(TaskTemplateRepository::class.java)
    }
    val reminderGroupsViewModel: SettingsReminderGroupsViewModel = viewModel(
        factory = remember(reminderGroupRepository, taskTemplateRepository) {
            SettingsReminderGroupsViewModel.factory(
                reminderGroupRepository = reminderGroupRepository,
                taskTemplateRepository = taskTemplateRepository
            )
        }
    )
    val reminderGroupsUiState by reminderGroupsViewModel.uiState.collectAsState()


    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Backup compatibility",
                style = MaterialTheme.typography.headlineSmall
            )

            Text(
                text = uiState.statusMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Checkbox(
                    checked = uiState.replaceExisting,
                    onCheckedChange = viewModel::onReplaceExistingChanged,
                    enabled = !uiState.isBusy
                )
                Text(
                    text = "Replace existing local state on import",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = viewModel::exportJson,
                    enabled = !uiState.isBusy
                ) {
                    Text("Export JSON")
                }

                Button(
                    onClick = viewModel::importJson,
                    enabled = !uiState.isBusy
                ) {
                    Text("Import JSON")
                }
            }

            OutlinedTextField(
                value = uiState.payload,
                onValueChange = viewModel::onPayloadChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                label = { Text("Backup JSON payload") },
                minLines = 14,
                maxLines = 24,
                enabled = !uiState.isBusy
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text(
                text = "Reminder groups",
                style = MaterialTheme.typography.headlineSmall
            )

            Text(
                text = reminderGroupsUiState.statusMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (reminderGroupsUiState.isLoading) {
                CircularProgressIndicator()
            }

            if (!reminderGroupsUiState.isLoading && reminderGroupsUiState.groups.isEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text = "No reminder groups yet. Create one to reuse hour presets across recurring tasks.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            reminderGroupsUiState.groups.forEach { group ->
                Card {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = group.name,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Hours: ${group.hoursLabel}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Assigned templates: ${group.assignedTaskCount}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { reminderGroupsViewModel.startEditDraft(group.id) },
                                enabled = !reminderGroupsUiState.isBusy
                            ) {
                                Text("Edit")
                            }
                            OutlinedButton(
                                onClick = { reminderGroupsViewModel.deleteGroup(group.id) },
                                enabled = !reminderGroupsUiState.isBusy
                            ) {
                                Text("Delete")
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = reminderGroupsUiState.draft.name,
                onValueChange = reminderGroupsViewModel::onDraftNameChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Group name") },
                enabled = !reminderGroupsUiState.isBusy
            )

            OutlinedTextField(
                value = reminderGroupsUiState.draft.hoursInput,
                onValueChange = reminderGroupsViewModel::onDraftHoursChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Default reminder hours") },
                supportingText = {
                    Text("Optional. Use 24h integers (0-23), separated by commas, spaces, or semicolons.")
                },
                enabled = !reminderGroupsUiState.isBusy
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = reminderGroupsViewModel::saveDraft,
                    enabled = !reminderGroupsUiState.isBusy
                ) {
                    Text(
                        if (reminderGroupsUiState.draft.id == null) {
                            "Save group"
                        } else {
                            "Update group"
                        }
                    )
                }

                OutlinedButton(
                    onClick = {
                        if (reminderGroupsUiState.draft.id == null &&
                            reminderGroupsUiState.draft.name.isBlank() &&
                            reminderGroupsUiState.draft.hoursInput.isBlank()
                        ) {
                            reminderGroupsViewModel.startCreateDraft()
                        } else {
                            reminderGroupsViewModel.clearDraft()
                        }
                    },
                    enabled = !reminderGroupsUiState.isBusy
                ) {
                    Text(
                        if (reminderGroupsUiState.draft.id == null) {
                            "New draft"
                        } else {
                            "Cancel edit"
                        }
                    )
                }
            }
        }
    }
}
