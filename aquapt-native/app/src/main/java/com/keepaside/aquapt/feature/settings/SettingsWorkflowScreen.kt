package com.keepaside.aquapt.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.keepaside.aquapt.core.model.LivestockKind
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsWorkflowScreen(
    onBack: () -> Unit = {}
) {
    val viewModel: SettingsWorkflowViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            TextButton(onClick = onBack) {
                androidx.compose.material3.Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Settings")
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "AI workflows",
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Contextual assistant, diagnostics, and compatibility checks.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(8.dp))
                FilterChip(
                    selected = false,
                    onClick = {},
                    label = { Text(state.currentModel.ifEmpty { "No model selected" }) }
                )
            }
        }

        AskAquaptAICard(
            state = state,
            onModeChanged = viewModel::onQAModeChanged,
            onQuestionChanged = viewModel::onQAQuestionChanged,
            onAsk = viewModel::askAssistant,
            onPresetSelected = viewModel::selectPreset
        )

        DiagnosticWorkflowCard(
            state = state,
            onAquariumChanged = viewModel::onDiagnosticAquariumChanged,
            onWindowDaysChanged = viewModel::onDiagnosticWindowDaysChanged,
            onSymptomsChanged = viewModel::onDiagnosticSymptomsChanged,
            onRun = viewModel::runDiagnosticWorkflow
        )

        CompatibilityWorkflowCard(
            state = state,
            onAquariumChanged = viewModel::onCompatibilityAquariumChanged,
            onSpeciesChanged = viewModel::onCompatibilitySpeciesChanged,
            onKindChanged = viewModel::onCompatibilityKindChanged,
            onQuantityChanged = viewModel::onCompatibilityQuantityChanged,
            onNotesChanged = viewModel::onCompatibilityNotesChanged,
            onRun = viewModel::runCompatibilityWorkflow
        )

        if (state.statusMessage.isNotEmpty()) {
            Text(
                text = state.statusMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AskAquaptAICard(
    state: SettingsWorkflowUiState,
    onModeChanged: (WorkflowAssistantMode) -> Unit,
    onQuestionChanged: (String) -> Unit,
    onAsk: () -> Unit,
    onPresetSelected: (WorkflowQuestionPreset) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Ask Aquapt AI",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Uses your OpenRouter key and current app context.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "General" to WorkflowAssistantMode.GENERAL,
                    "Diagnostic" to WorkflowAssistantMode.DIAGNOSTIC,
                    "Compatibility" to WorkflowAssistantMode.COMPATIBILITY,
                    "Task Suggest" to WorkflowAssistantMode.TASK_SUGGESTION
                ).forEach { (label, mode) ->
                    FilterChip(
                        selected = state.qaDraft.mode == mode,
                        onClick = { onModeChanged(mode) },
                        label = { Text(label) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                workflowQuestionPresets.forEach { preset ->
                    FilterChip(
                        selected = false,
                        onClick = { onPresetSelected(preset) },
                        label = { Text(preset.label) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = state.qaDraft.question,
                onValueChange = onQuestionChanged,
                label = { Text("Ask Aquapt AI") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5
            )

            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onAsk,
                enabled = !state.isRequesting && state.hasApiKey && state.qaDraft.question.trim().isNotEmpty()
            ) {
                Text("Ask assistant")
            }

            if (state.isRequesting) {
                Spacer(modifier = Modifier.height(12.dp))
                CircularProgressIndicator()
            }

            state.qaError?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            if (state.qaAnswer.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                ResponseCard(text = state.qaAnswer)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DiagnosticWorkflowCard(
    state: SettingsWorkflowUiState,
    onAquariumChanged: (String) -> Unit,
    onWindowDaysChanged: (String) -> Unit,
    onSymptomsChanged: (String) -> Unit,
    onRun: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Diagnostic workflow",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Guided root-cause analysis based on your aquarium context.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.aquariumOptions.forEach { option ->
                    FilterChip(
                        selected = state.diagnosticDraft.aquariumId == option.id,
                        onClick = { onAquariumChanged(option.id) },
                        label = { Text(option.name) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = state.diagnosticDraft.windowDays,
                onValueChange = onWindowDaysChanged,
                label = { Text("Review window (days)") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = state.diagnosticDraft.symptoms,
                onValueChange = onSymptomsChanged,
                label = { Text("Symptoms observed") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5
            )

            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onRun,
                enabled = !state.isRequesting &&
                    state.diagnosticDraft.aquariumId.isNotEmpty() &&
                    state.diagnosticDraft.symptoms.trim().isNotEmpty()
            ) {
                Text("Run diagnostic analysis")
            }

            state.diagnosticError?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            if (state.diagnosticAnswer.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                ResponseCard(text = state.diagnosticAnswer)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompatibilityWorkflowCard(
    state: SettingsWorkflowUiState,
    onAquariumChanged: (String) -> Unit,
    onSpeciesChanged: (String) -> Unit,
    onKindChanged: (LivestockKind) -> Unit,
    onQuantityChanged: (String) -> Unit,
    onNotesChanged: (String) -> Unit,
    onRun: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Compatibility workflow",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Evaluate additions against current tank constraints and risks.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.aquariumOptions.forEach { option ->
                    FilterChip(
                        selected = state.compatibilityDraft.aquariumId == option.id,
                        onClick = { onAquariumChanged(option.id) },
                        label = { Text(option.name) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = state.compatibilityDraft.species,
                    onValueChange = onSpeciesChanged,
                    label = { Text("Species") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = state.compatibilityDraft.quantity,
                    onValueChange = onQuantityChanged,
                    label = { Text("Qty") },
                    modifier = Modifier.width(90.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LivestockKind.entries.forEach { kind ->
                    FilterChip(
                        selected = state.compatibilityDraft.kind == kind,
                        onClick = { onKindChanged(kind) },
                        label = { Text(kind.toWorkflowLabel()) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = state.compatibilityDraft.notes,
                onValueChange = onNotesChanged,
                label = { Text("Notes (optional)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4
            )

            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onRun,
                enabled = !state.isRequesting &&
                    state.compatibilityDraft.aquariumId.isNotEmpty() &&
                    state.compatibilityDraft.species.trim().isNotEmpty()
            ) {
                Text("Run compatibility check")
            }

            state.compatibilityError?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            if (state.compatibilityAnswer.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                ResponseCard(text = state.compatibilityAnswer)
            }
        }
    }
}

@Composable
private fun ResponseCard(text: String) {
    val clipboardManager = LocalClipboardManager.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = { clipboardManager.setText(AnnotatedString(text)) }
            ) {
                Text("Copy response")
            }
        }
    }
}
