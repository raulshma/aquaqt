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
import androidx.compose.material3.FilterChip
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
import com.keepaside.aquapt.core.model.AppThemePreference
import com.keepaside.aquapt.core.model.RegionalPreferencesMode
import com.keepaside.aquapt.core.repository.AppSettingsStore
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
    val appSettingsStore: AppSettingsStore = remember {
        KoinJavaComponent.get(AppSettingsStore::class.java)
    }
    val viewModel: SettingsBackupViewModel = viewModel(
        factory = remember(backupGateway, appSettingsStore) {
            SettingsBackupViewModel.factory(
                backupGateway = backupGateway,
                appSettingsStore = appSettingsStore
            )
        }
    )
    val uiState by viewModel.uiState.collectAsState()

    val settingsPreferencesViewModel: SettingsPreferencesViewModel = viewModel(
        factory = remember(appSettingsStore) {
            SettingsPreferencesViewModel.factory(appSettingsStore)
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
                onThemePreferenceChanged = settingsPreferencesViewModel::onThemePreferenceChanged,
                onRegionalModeChanged = settingsPreferencesViewModel::onRegionalPreferencesModeChanged,
                onNotificationsEnabledChanged = settingsPreferencesViewModel::onNotificationsEnabledChanged,
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

@Composable
private fun SettingsPreferencesSection(
    uiState: SettingsPreferencesUiState,
    onOpenRouterApiKeyChanged: (String) -> Unit,
    onAiModelChanged: (String) -> Unit,
    onThemePreferenceChanged: (AppThemePreference) -> Unit,
    onRegionalModeChanged: (RegionalPreferencesMode) -> Unit,
    onNotificationsEnabledChanged: (Boolean) -> Unit,
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
