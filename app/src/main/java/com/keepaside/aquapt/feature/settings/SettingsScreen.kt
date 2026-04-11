package com.keepaside.aquapt.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.Assistant
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.keepaside.aquapt.core.model.AppThemePreference
import com.keepaside.aquapt.core.repository.AppSettingsStore
import com.keepaside.aquapt.ui.theme.NeoHeroContainer
import com.keepaside.aquapt.ui.theme.NeoHeroOnContainer
import org.koin.java.KoinJavaComponent

private data class SettingsGroupUiModel(
    val title: String,
    val items: List<SettingsDestinationUiModel>
)

private data class SettingsDestinationUiModel(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val onOpen: () -> Unit
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onOpenCoreSettings: () -> Unit = {},
    onOpenWorkflows: () -> Unit = {},
    onOpenMemoryTools: () -> Unit = {},
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(24.dp)
) {
    val appSettingsStore: AppSettingsStore = remember {
        KoinJavaComponent.get(AppSettingsStore::class.java)
    }
    val appSettings by appSettingsStore.settings.collectAsState()

    val groupedDestinations = remember(onOpenCoreSettings, onOpenWorkflows, onOpenMemoryTools) {
        listOf(
            SettingsGroupUiModel(
                title = "Core",
                items = listOf(
                    SettingsDestinationUiModel(
                        title = "General preferences",
                        description = "Appearance, regional defaults, reminders, and runtime options.",
                        icon = Icons.Outlined.Settings,
                        onOpen = onOpenCoreSettings
                    )
                )
            ),
            SettingsGroupUiModel(
                title = "Assistant",
                items = listOf(
                    SettingsDestinationUiModel(
                        title = "AI workflows",
                        description = "Contextual assistant, diagnostics, and compatibility checks.",
                        icon = Icons.Rounded.Analytics,
                        onOpen = onOpenWorkflows
                    ),
                    SettingsDestinationUiModel(
                        title = "Assistant memory",
                        description = "Review snippets, compact facts, and clear stale memory.",
                        icon = Icons.Rounded.Assistant,
                        onOpen = onOpenMemoryTools
                    )
                )
            ),
            SettingsGroupUiModel(
                title = "Storage",
                items = listOf(
                    SettingsDestinationUiModel(
                        title = "Backup & restore",
                        description = "Encrypted S3 sync, cloud restore previews, and JSON import/export.",
                        icon = Icons.Rounded.TaskAlt,
                        onOpen = onOpenCoreSettings
                    )
                )
            )
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = NeoHeroContainer,
                contentColor = NeoHeroOnContainer
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Take control",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = NeoHeroOnContainer
                )
                Text(
                    text = "Your settings are split into focused groups. Open the section you need and tweak without the clutter.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NeoHeroOnContainer.copy(alpha = 0.76f)
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SettingsStatusPill(
                        label = "Theme",
                        value = appSettings.themePreference.toReadableLabel()
                    )
                    SettingsStatusPill(
                        label = "Memory",
                        value = if (appSettings.assistantMemoryEnabled) "Enabled" else "Off"
                    )
                    SettingsStatusPill(
                        label = "Backup",
                        value = if (appSettings.backupSyncEnabled) "Auto" else "Manual"
                    )
                }
            }
        }

        groupedDestinations.forEach { group ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = group.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )

                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                    )
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        group.items.forEachIndexed { index, destination ->
                            SettingsDestinationRow(destination = destination)

                            if (index < group.items.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 18.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
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
private fun SettingsDestinationRow(destination: SettingsDestinationUiModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = destination.onOpen)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            modifier = Modifier.size(42.dp),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = destination.icon,
                    contentDescription = null
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = destination.title,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = destination.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = "Open ${destination.title}",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsStatusPill(
    label: String,
    value: String
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

private fun AppThemePreference.toReadableLabel(): String = when (this) {
    AppThemePreference.SYSTEM -> "System"
    AppThemePreference.LIGHT -> "Light"
    AppThemePreference.DARK -> "Dark"
}
