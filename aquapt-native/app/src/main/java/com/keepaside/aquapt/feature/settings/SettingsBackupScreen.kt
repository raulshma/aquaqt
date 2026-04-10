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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
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
        }
    }
}
