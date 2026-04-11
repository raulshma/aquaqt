package com.keepaside.aquapt.feature.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.keepaside.aquapt.core.backup.BackupCompatibilityGateway
import com.keepaside.aquapt.core.backup.BackupCloudSyncGateway
import com.keepaside.aquapt.core.model.AppThemePreference
import com.keepaside.aquapt.core.model.RegionalPreferencesMode
import com.keepaside.aquapt.core.repository.AppSettingsStore
import com.keepaside.aquapt.core.repository.BackupSecretsStore
import com.keepaside.aquapt.core.repository.ReminderGroupRepository
import com.keepaside.aquapt.core.repository.TaskTemplateRepository
import org.koin.java.KoinJavaComponent

@Composable
fun SettingsBackupScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(24.dp)
) {
    val context = LocalContext.current
    val backupGateway: BackupCompatibilityGateway = remember {
        KoinJavaComponent.get(BackupCompatibilityGateway::class.java)
    }
    val appSettingsStore: AppSettingsStore = remember {
        KoinJavaComponent.get(AppSettingsStore::class.java)
    }
    val backupSecretsStore: BackupSecretsStore = remember {
        KoinJavaComponent.get(BackupSecretsStore::class.java)
    }
    val backupCloudSyncGateway: BackupCloudSyncGateway = remember {
        KoinJavaComponent.get(BackupCloudSyncGateway::class.java)
    }
    val viewModel: SettingsBackupViewModel = viewModel(
        factory = remember(backupGateway, appSettingsStore, backupSecretsStore, backupCloudSyncGateway) {
            SettingsBackupViewModel.factory(
                backupGateway = backupGateway,
                appSettingsStore = appSettingsStore,
                backupSecretsStore = backupSecretsStore,
                backupCloudSyncGateway = backupCloudSyncGateway
            )
        }
    )
    val uiState by viewModel.uiState.collectAsState()
    val selectedCloudObject = uiState.cloudBackups.firstOrNull { cloudObject ->
        cloudObject.objectKey == uiState.selectedCloudObjectKey
    }

    val settingsPreferencesViewModel: SettingsPreferencesViewModel = viewModel(
        factory = remember(appSettingsStore, backupSecretsStore) {
            SettingsPreferencesViewModel.factory(
                appSettingsStore = appSettingsStore,
                backupSecretsStore = backupSecretsStore
            )
        }
    )
    val settingsPreferencesUiState by settingsPreferencesViewModel.uiState.collectAsState()

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

    var notificationPermissionStatus by remember {
        mutableStateOf(resolveNotificationPermissionStatus(context))
    }
    val refreshNotificationPermissionStatus = remember(context) {
        {
            notificationPermissionStatus = resolveNotificationPermissionStatus(context)
        }
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        refreshNotificationPermissionStatus()
    }
    val notificationSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        refreshNotificationPermissionStatus()
    }

    var showRestoreSelectedConfirmation by remember { mutableStateOf(false) }
    var showRestoreLatestConfirmation by remember { mutableStateOf(false) }
    var showDeleteSelectedConfirmation by remember { mutableStateOf(false) }

    LaunchedEffect(context) {
        refreshNotificationPermissionStatus()
    }


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
            SettingsPreferencesSection(
                uiState = settingsPreferencesUiState,
                onOpenRouterApiKeyChanged = settingsPreferencesViewModel::onOpenRouterApiKeyChanged,
                onAiModelChanged = settingsPreferencesViewModel::onAiModelChanged,
                onAssistantMemoryModelChanged = settingsPreferencesViewModel::onAssistantMemoryModelChanged,
                onAssistantMemoryEnabledChanged = settingsPreferencesViewModel::onAssistantMemoryEnabledChanged,
                onThemePreferenceChanged = settingsPreferencesViewModel::onThemePreferenceChanged,
                onRegionalModeChanged = settingsPreferencesViewModel::onRegionalPreferencesModeChanged,
                onNotificationsEnabledChanged = settingsPreferencesViewModel::onNotificationsEnabledChanged,
                onBackupSyncEnabledChanged = settingsPreferencesViewModel::onBackupSyncEnabledChanged,
                onBackupSyncHourChanged = settingsPreferencesViewModel::onBackupSyncHourChanged,
                onBackupS3EndpointChanged = settingsPreferencesViewModel::onBackupS3EndpointChanged,
                onBackupS3RegionChanged = settingsPreferencesViewModel::onBackupS3RegionChanged,
                onBackupS3BucketChanged = settingsPreferencesViewModel::onBackupS3BucketChanged,
                onBackupS3ObjectKeyChanged = settingsPreferencesViewModel::onBackupS3ObjectKeyChanged,
                onBackupS3ForcePathStyleChanged = settingsPreferencesViewModel::onBackupS3ForcePathStyleChanged,
                onBackupUseVersionedKeysChanged = settingsPreferencesViewModel::onBackupUseVersionedKeysChanged,
                onBackupRetentionDaysChanged = settingsPreferencesViewModel::onBackupRetentionDaysChanged,
                onBackupMasterKeyInputChanged = settingsPreferencesViewModel::onBackupMasterKeyInputChanged,
                onBackupS3AccessKeyIdInputChanged = settingsPreferencesViewModel::onBackupS3AccessKeyIdInputChanged,
                onBackupS3SecretAccessKeyInputChanged = settingsPreferencesViewModel::onBackupS3SecretAccessKeyInputChanged,
                onClearBackupMasterKey = settingsPreferencesViewModel::clearBackupMasterKey,
                onClearBackupS3Credentials = settingsPreferencesViewModel::clearBackupS3Credentials,
                notificationPermissionStatus = notificationPermissionStatus,
                onRequestNotificationPermission = {
                    if (
                        notificationPermissionStatus.runtimePermissionRequired &&
                        !notificationPermissionStatus.runtimePermissionGranted
                    ) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                onOpenSystemNotificationSettings = {
                    notificationSettingsLauncher.launch(createNotificationSettingsIntent(context))
                },
                onReminderHoursChanged = settingsPreferencesViewModel::onReminderHoursChanged,
                onDefaultLocaleChanged = settingsPreferencesViewModel::onDefaultLocaleChanged,
                onDefaultTimezoneChanged = settingsPreferencesViewModel::onDefaultTimezoneChanged,
                onDefaultCountryCodeChanged = settingsPreferencesViewModel::onDefaultCountryCodeChanged,
                onDefaultCountryNameChanged = settingsPreferencesViewModel::onDefaultCountryNameChanged,
                onDefaultCurrencyChanged = settingsPreferencesViewModel::onDefaultCurrencyChanged,
                onSave = settingsPreferencesViewModel::savePreferences,
                onReset = settingsPreferencesViewModel::resetDraftToSaved
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

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

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Text(
                text = "Cloud backup",
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = "Uses configured S3 destination and secure credentials from App preferences.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = viewModel::syncToCloud,
                    enabled = !uiState.isBusy
                ) {
                    Text("Sync to cloud")
                }

                OutlinedButton(
                    onClick = viewModel::loadCloudBackups,
                    enabled = !uiState.isBusy
                ) {
                    Text("Load cloud list")
                }
            }

            if (uiState.cloudBackups.isEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text = "No cloud objects loaded. Use \"Load cloud list\" or \"Sync to cloud\".",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Text(
                    text = "Restore source object",
                    style = MaterialTheme.typography.titleSmall
                )

                uiState.cloudBackups.forEach { cloudObject ->
                    val metadata = buildString {
                        if (cloudObject.isLatestObject) {
                            append("latest")
                        }
                        if (!cloudObject.lastModified.isNullOrBlank()) {
                            if (isNotEmpty()) append(" • ")
                            append(cloudObject.lastModified)
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (uiState.selectedCloudObjectKey == cloudObject.objectKey) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Checkbox(
                                checked = uiState.selectedCloudObjectKey == cloudObject.objectKey,
                                onCheckedChange = {
                                    viewModel.onSelectedCloudObjectChanged(cloudObject.objectKey)
                                },
                                enabled = !uiState.isBusy
                            )

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = cloudObject.objectKey,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (metadata.isNotBlank()) {
                                    Text(
                                        text = metadata,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            selectedCloudObject?.let { cloudObject ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Restore preview",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = cloudObject.objectKey,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = if (cloudObject.lastModified.isNullOrBlank()) {
                                if (cloudObject.isLatestObject) "Marked as latest" else "Last modified unknown"
                            } else {
                                "Last modified: ${cloudObject.lastModified}" +
                                    if (cloudObject.isLatestObject) " • latest" else ""
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = if (uiState.replaceExisting) {
                                "Restore mode: replace existing local state."
                            } else {
                                "Restore mode: merge with existing local state."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        if (uiState.selectedCloudObjectKey.isBlank()) {
                            viewModel.restoreSelectedCloudBackup()
                        } else {
                            showRestoreSelectedConfirmation = true
                        }
                    },
                    enabled = !uiState.isBusy
                ) {
                    Text("Restore selected")
                }

                OutlinedButton(
                    onClick = {
                        showRestoreLatestConfirmation = true
                    },
                    enabled = !uiState.isBusy
                ) {
                    Text("Restore latest")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        if (uiState.selectedCloudObjectKey.isBlank()) {
                            viewModel.deleteSelectedCloudBackupObject()
                        } else {
                            showDeleteSelectedConfirmation = true
                        }
                    },
                    enabled = !uiState.isBusy
                ) {
                    Text("Delete selected")
                }

                OutlinedButton(
                    onClick = viewModel::pruneCloudBackupHistory,
                    enabled = !uiState.isBusy
                ) {
                    Text("Prune history")
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

        if (showRestoreSelectedConfirmation) {
            AlertDialog(
                onDismissRequest = {
                    showRestoreSelectedConfirmation = false
                },
                title = {
                    Text("Restore selected backup?")
                },
                text = {
                    Text(
                        "Restore from ${uiState.selectedCloudObjectKey}. " +
                            if (uiState.replaceExisting) {
                                "This will replace existing local state before import."
                            } else {
                                "This will merge imported records with local state."
                            }
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showRestoreSelectedConfirmation = false
                            viewModel.restoreSelectedCloudBackup()
                        }
                    ) {
                        Text("Restore")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showRestoreSelectedConfirmation = false
                        }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showRestoreLatestConfirmation) {
            AlertDialog(
                onDismissRequest = {
                    showRestoreLatestConfirmation = false
                },
                title = {
                    Text("Restore latest backup?")
                },
                text = {
                    Text(
                        "Restore from the latest cloud object for this destination. " +
                            if (uiState.replaceExisting) {
                                "This will replace existing local state before import."
                            } else {
                                "This will merge imported records with local state."
                            }
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showRestoreLatestConfirmation = false
                            viewModel.restoreLatestCloudBackup()
                        }
                    ) {
                        Text("Restore")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showRestoreLatestConfirmation = false
                        }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showDeleteSelectedConfirmation) {
            AlertDialog(
                onDismissRequest = {
                    showDeleteSelectedConfirmation = false
                },
                title = {
                    Text("Delete selected cloud object?")
                },
                text = {
                    Text(
                        "Delete ${uiState.selectedCloudObjectKey}. This cannot be undone." +
                            if (selectedCloudObject?.isLatestObject == true) {
                                " This object is marked as latest. Sync again to recreate the latest pointer after deletion."
                            } else {
                                ""
                            }
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteSelectedConfirmation = false
                            viewModel.deleteSelectedCloudBackupObject()
                        }
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showDeleteSelectedConfirmation = false
                        }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun SettingsPreferencesSection(
    uiState: SettingsPreferencesUiState,
    onOpenRouterApiKeyChanged: (String) -> Unit,
    onAiModelChanged: (String) -> Unit,
    onAssistantMemoryModelChanged: (String) -> Unit,
    onAssistantMemoryEnabledChanged: (Boolean) -> Unit,
    onThemePreferenceChanged: (AppThemePreference) -> Unit,
    onRegionalModeChanged: (RegionalPreferencesMode) -> Unit,
    onNotificationsEnabledChanged: (Boolean) -> Unit,
    onBackupSyncEnabledChanged: (Boolean) -> Unit,
    onBackupSyncHourChanged: (String) -> Unit,
    onBackupS3EndpointChanged: (String) -> Unit,
    onBackupS3RegionChanged: (String) -> Unit,
    onBackupS3BucketChanged: (String) -> Unit,
    onBackupS3ObjectKeyChanged: (String) -> Unit,
    onBackupS3ForcePathStyleChanged: (Boolean) -> Unit,
    onBackupUseVersionedKeysChanged: (Boolean) -> Unit,
    onBackupRetentionDaysChanged: (String) -> Unit,
    onBackupMasterKeyInputChanged: (String) -> Unit,
    onBackupS3AccessKeyIdInputChanged: (String) -> Unit,
    onBackupS3SecretAccessKeyInputChanged: (String) -> Unit,
    onClearBackupMasterKey: () -> Unit,
    onClearBackupS3Credentials: () -> Unit,
    notificationPermissionStatus: NotificationPermissionStatus,
    onRequestNotificationPermission: () -> Unit,
    onOpenSystemNotificationSettings: () -> Unit,
    onReminderHoursChanged: (String) -> Unit,
    onDefaultLocaleChanged: (String) -> Unit,
    onDefaultTimezoneChanged: (String) -> Unit,
    onDefaultCountryCodeChanged: (String) -> Unit,
    onDefaultCountryNameChanged: (String) -> Unit,
    onDefaultCurrencyChanged: (String) -> Unit,
    onSave: () -> Unit,
    onReset: () -> Unit
) {
    Text(
        text = "App preferences",
        style = MaterialTheme.typography.headlineSmall
    )

    Text(
        text = uiState.statusMessage,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Assistant runtime",
                style = MaterialTheme.typography.titleMedium
            )

            OutlinedTextField(
                value = uiState.draft.openRouterApiKey,
                onValueChange = onOpenRouterApiKeyChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("OpenRouter API key") },
                supportingText = { Text("Required for streaming assistant replies.") },
                enabled = !uiState.isSaving,
                singleLine = true
            )

            OutlinedTextField(
                value = uiState.draft.aiModel,
                onValueChange = onAiModelChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("OpenRouter model") },
                supportingText = { Text("Example: openai/gpt-4o-mini") },
                enabled = !uiState.isSaving,
                singleLine = true
            )

            OutlinedTextField(
                value = uiState.draft.assistantMemoryModel,
                onValueChange = onAssistantMemoryModelChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Assistant memory model") },
                supportingText = { Text("Optional. Used for future memory summarization/compaction passes.") },
                enabled = !uiState.isSaving,
                singleLine = true
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Checkbox(
                    checked = uiState.draft.assistantMemoryEnabled,
                    onCheckedChange = { value -> onAssistantMemoryEnabledChanged(value) },
                    enabled = !uiState.isSaving
                )
                Text(
                    text = "Enable assistant memory",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Text(
                text = "Appearance",
                style = MaterialTheme.typography.titleMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppThemePreference.entries.forEach { preference ->
                    FilterChip(
                        selected = uiState.draft.themePreference == preference,
                        onClick = { onThemePreferenceChanged(preference) },
                        enabled = !uiState.isSaving,
                        label = { Text(preference.toReadableLabel()) }
                    )
                }
            }

            Text(
                text = "Regional defaults",
                style = MaterialTheme.typography.titleMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RegionalPreferencesMode.entries.forEach { mode ->
                    FilterChip(
                        selected = uiState.draft.regionalPreferencesMode == mode,
                        onClick = { onRegionalModeChanged(mode) },
                        enabled = !uiState.isSaving,
                        label = { Text(mode.toReadableLabel()) }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Checkbox(
                    checked = uiState.draft.notificationsEnabled,
                    onCheckedChange = { value -> onNotificationsEnabledChanged(value) },
                    enabled = !uiState.isSaving
                )
                Text(
                    text = "Enable notifications",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (
                uiState.draft.notificationsEnabled &&
                !notificationPermissionStatus.canPostNotifications
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Reminder notifications need Android permission and app-level notification access.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )

                        if (
                            notificationPermissionStatus.runtimePermissionRequired &&
                            !notificationPermissionStatus.runtimePermissionGranted
                        ) {
                            OutlinedButton(
                                onClick = onRequestNotificationPermission,
                                enabled = !uiState.isSaving
                            ) {
                                Text("Grant notification permission")
                            }
                        }

                        if (!notificationPermissionStatus.appNotificationsEnabled) {
                            OutlinedButton(
                                onClick = onOpenSystemNotificationSettings,
                                enabled = !uiState.isSaving
                            ) {
                                Text("Open notification settings")
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = uiState.draft.reminderHoursInput,
                onValueChange = onReminderHoursChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Reminder hours") },
                supportingText = {
                    Text("Optional. Use 24h values like 8, 18.")
                },
                enabled = !uiState.isSaving
            )

            Text(
                text = "Backup automation",
                style = MaterialTheme.typography.titleMedium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Checkbox(
                    checked = uiState.draft.backupSyncEnabled,
                    onCheckedChange = { value -> onBackupSyncEnabledChanged(value) },
                    enabled = !uiState.isSaving
                )
                Text(
                    text = "Enable automatic backup sync",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            OutlinedTextField(
                value = uiState.draft.backupSyncHourInput,
                onValueChange = onBackupSyncHourChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Backup sync hour") },
                supportingText = {
                    Text("Runs once per day at/after this local 24h hour (default 3).")
                },
                enabled = !uiState.isSaving
            )

            OutlinedTextField(
                value = uiState.draft.backupS3Endpoint,
                onValueChange = onBackupS3EndpointChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("S3 endpoint") },
                supportingText = { Text("Example: https://s3.amazonaws.com") },
                enabled = !uiState.isSaving,
                singleLine = true
            )

            OutlinedTextField(
                value = uiState.draft.backupS3Region,
                onValueChange = onBackupS3RegionChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("S3 region") },
                supportingText = { Text("Example: us-east-1") },
                enabled = !uiState.isSaving,
                singleLine = true
            )

            OutlinedTextField(
                value = uiState.draft.backupS3Bucket,
                onValueChange = onBackupS3BucketChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("S3 bucket") },
                enabled = !uiState.isSaving,
                singleLine = true
            )

            OutlinedTextField(
                value = uiState.draft.backupS3ObjectKey,
                onValueChange = onBackupS3ObjectKeyChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("S3 object key") },
                supportingText = { Text("Example: aquapt/backups/latest.enc.json") },
                enabled = !uiState.isSaving,
                singleLine = true
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Checkbox(
                    checked = uiState.draft.backupS3ForcePathStyle,
                    onCheckedChange = { value -> onBackupS3ForcePathStyleChanged(value) },
                    enabled = !uiState.isSaving
                )
                Text(
                    text = "Force path-style S3 URLs",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Checkbox(
                    checked = uiState.draft.backupUseVersionedKeys,
                    onCheckedChange = { value -> onBackupUseVersionedKeysChanged(value) },
                    enabled = !uiState.isSaving
                )
                Text(
                    text = "Upload daily versioned backups",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            OutlinedTextField(
                value = uiState.draft.backupRetentionDaysInput,
                onValueChange = onBackupRetentionDaysChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Backup retention days") },
                supportingText = { Text("Optional. Applies to versioned backups (1-3650).") },
                enabled = !uiState.isSaving,
                singleLine = true
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
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Secure backup credentials",
                        style = MaterialTheme.typography.titleSmall
                    )

                    Text(
                        text = "Master key: ${if (uiState.draft.backupMasterKeyConfigured) "Configured" else "Not configured"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = "S3 credentials: ${if (uiState.draft.backupS3CredentialsConfigured) "Configured" else "Not configured"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = uiState.draft.backupMasterKeyInput,
                        onValueChange = onBackupMasterKeyInputChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Update backup master key") },
                        supportingText = {
                            Text("Leave blank to keep current key. Saves to encrypted device storage.")
                        },
                        enabled = !uiState.isSaving,
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )

                    OutlinedTextField(
                        value = uiState.draft.backupS3AccessKeyIdInput,
                        onValueChange = onBackupS3AccessKeyIdInputChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Update S3 access key ID") },
                        supportingText = { Text("Leave blank to keep current credentials.") },
                        enabled = !uiState.isSaving,
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = uiState.draft.backupS3SecretAccessKeyInput,
                        onValueChange = onBackupS3SecretAccessKeyInputChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Update S3 secret access key") },
                        supportingText = { Text("Provide together with access key ID.") },
                        enabled = !uiState.isSaving,
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onClearBackupMasterKey,
                            enabled = !uiState.isSaving
                        ) {
                            Text("Clear master key")
                        }

                        OutlinedButton(
                            onClick = onClearBackupS3Credentials,
                            enabled = !uiState.isSaving
                        ) {
                            Text("Clear S3 credentials")
                        }
                    }
                }
            }

            Text(
                text = "Last auto-sync date: ${uiState.draft.backupLastAutoSyncDate.ifBlank { "Never" }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "Last successful sync: ${uiState.draft.backupLastSyncedAt.ifBlank { "Never" }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (uiState.draft.backupLastError.isNotBlank()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = uiState.draft.backupLastError,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            if (uiState.draft.regionalPreferencesMode == RegionalPreferencesMode.MANUAL) {
                OutlinedTextField(
                    value = uiState.draft.defaultLocale,
                    onValueChange = onDefaultLocaleChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Default locale") },
                    supportingText = { Text("Example: en-US") },
                    enabled = !uiState.isSaving
                )

                OutlinedTextField(
                    value = uiState.draft.defaultTimezone,
                    onValueChange = onDefaultTimezoneChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Default timezone") },
                    supportingText = { Text("Example: America/New_York") },
                    enabled = !uiState.isSaving
                )

                OutlinedTextField(
                    value = uiState.draft.defaultCountryCode,
                    onValueChange = onDefaultCountryCodeChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Country code") },
                    supportingText = { Text("Example: US") },
                    enabled = !uiState.isSaving
                )

                OutlinedTextField(
                    value = uiState.draft.defaultCountryName,
                    onValueChange = onDefaultCountryNameChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Country name") },
                    supportingText = { Text("Example: United States") },
                    enabled = !uiState.isSaving
                )

                OutlinedTextField(
                    value = uiState.draft.defaultCurrency,
                    onValueChange = onDefaultCurrencyChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Currency") },
                    supportingText = { Text("Example: USD") },
                    enabled = !uiState.isSaving
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onSave,
                    enabled = !uiState.isLoading && !uiState.isSaving
                ) {
                    Text("Save preferences")
                }

                OutlinedButton(
                    onClick = onReset,
                    enabled = !uiState.isLoading && !uiState.isSaving
                ) {
                    Text("Reset")
                }
            }
        }
    }
}

private fun AppThemePreference.toReadableLabel(): String = when (this) {
    AppThemePreference.SYSTEM -> "System"
    AppThemePreference.LIGHT -> "Light"
    AppThemePreference.DARK -> "Dark"
}

private fun RegionalPreferencesMode.toReadableLabel(): String = when (this) {
    RegionalPreferencesMode.AUTO -> "Auto"
    RegionalPreferencesMode.MANUAL -> "Manual"
}

private data class NotificationPermissionStatus(
    val runtimePermissionRequired: Boolean,
    val runtimePermissionGranted: Boolean,
    val appNotificationsEnabled: Boolean
) {
    val canPostNotifications: Boolean
        get() = runtimePermissionGranted && appNotificationsEnabled
}

private fun resolveNotificationPermissionStatus(
    context: Context,
    sdkInt: Int = Build.VERSION.SDK_INT
): NotificationPermissionStatus {
    val runtimePermissionRequired = sdkInt >= Build.VERSION_CODES.TIRAMISU
    val runtimePermissionGranted = !runtimePermissionRequired ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    val appNotificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()

    return NotificationPermissionStatus(
        runtimePermissionRequired = runtimePermissionRequired,
        runtimePermissionGranted = runtimePermissionGranted,
        appNotificationsEnabled = appNotificationsEnabled
    )
}

private fun createNotificationSettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    }
