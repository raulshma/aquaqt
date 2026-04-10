package com.keepaside.aquapt.feature.tanks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.keepaside.aquapt.core.repository.AquariumRepository
import com.keepaside.aquapt.core.repository.DosingLogRepository
import com.keepaside.aquapt.core.repository.IssueRepository
import com.keepaside.aquapt.core.repository.LivestockRepository
import com.keepaside.aquapt.core.repository.TaskExecutionRepository
import com.keepaside.aquapt.core.repository.TaskTemplateRepository
import com.keepaside.aquapt.core.repository.WaterParameterLogRepository
import org.koin.java.KoinJavaComponent

@Composable
fun TanksDashboardScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp)
) {
    val aquariumRepository: AquariumRepository = remember {
        KoinJavaComponent.get(AquariumRepository::class.java)
    }
    val livestockRepository: LivestockRepository = remember {
        KoinJavaComponent.get(LivestockRepository::class.java)
    }
    val taskTemplateRepository: TaskTemplateRepository = remember {
        KoinJavaComponent.get(TaskTemplateRepository::class.java)
    }
    val taskExecutionRepository: TaskExecutionRepository = remember {
        KoinJavaComponent.get(TaskExecutionRepository::class.java)
    }
    val issueRepository: IssueRepository = remember {
        KoinJavaComponent.get(IssueRepository::class.java)
    }
    val waterParameterLogRepository: WaterParameterLogRepository = remember {
        KoinJavaComponent.get(WaterParameterLogRepository::class.java)
    }
    val dosingLogRepository: DosingLogRepository = remember {
        KoinJavaComponent.get(DosingLogRepository::class.java)
    }

    val viewModel: TanksDashboardViewModel = viewModel(
        factory = remember(
            aquariumRepository,
            livestockRepository,
            taskTemplateRepository,
            taskExecutionRepository,
            issueRepository,
            waterParameterLogRepository,
            dosingLogRepository
        ) {
            TanksDashboardViewModel.factory(
                aquariumRepository = aquariumRepository,
                livestockRepository = livestockRepository,
                taskTemplateRepository = taskTemplateRepository,
                taskExecutionRepository = taskExecutionRepository,
                issueRepository = issueRepository,
                waterParameterLogRepository = waterParameterLogRepository,
                dosingLogRepository = dosingLogRepository
            )
        }
    )

    val uiState by viewModel.uiState.collectAsState()

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
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Today at a glance",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = uiState.headline,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MetricCard(
                                modifier = Modifier.weight(1f),
                                title = "Tanks",
                                value = uiState.summary.aquariumCount.toString()
                            )
                            MetricCard(
                                modifier = Modifier.weight(1f),
                                title = "Residents",
                                value = uiState.summary.residentCount.toString()
                            )
                            MetricCard(
                                modifier = Modifier.weight(1f),
                                title = "Due",
                                value = uiState.summary.dueTaskCount.toString()
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MetricCard(
                                modifier = Modifier.weight(1f),
                                title = "Open issues",
                                value = uiState.summary.openIssueCount.toString()
                            )
                            MetricCard(
                                modifier = Modifier.weight(1f),
                                title = "Alerts",
                                value = uiState.summary.parameterAlertCount.toString()
                            )
                            MetricCard(
                                modifier = Modifier.weight(1f),
                                title = "Dosing logs",
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
                            text = "No aquariums yet. Import a backup in Settings or add your first tank in upcoming creation flows.",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

            if (uiState.alerts.isNotEmpty()) {
                item {
                    Card {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Water alerts",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            uiState.alerts.take(5).forEach { alert ->
                                Text(
                                    text = "${alert.aquariumName}: ${alert.label} ${alert.status} at ${formatAlertValue(alert.value, alert.unit)}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }

            if (uiState.dueTasks.isNotEmpty()) {
                item {
                    Card {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Due tasks",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            uiState.dueTasks.take(6).forEach { task ->
                                Text(
                                    text = "${task.taskTitle} • ${task.aquariumName}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Aquariums",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                )
            }

            items(uiState.aquariums, key = { it.aquariumId }) { aquarium ->
                Card {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = aquarium.aquariumName,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f)
                            )
                            AssistChip(
                                onClick = {},
                                label = {
                                    Text("${aquarium.volumeLiters.toInt()}L")
                                }
                            )
                        }

                        Text(
                            text = "${aquarium.waterTypeLabel} • Setup ${aquarium.setupDate.ifBlank { "unknown" }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text(
                            text = aquarium.latestParameterSummary,
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Residents ${aquarium.residentCount}",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                text = "Issues ${aquarium.openIssueCount}",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                text = "Due ${aquarium.dueTaskCount}",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                text = "Alerts ${aquarium.activeAlertCount}",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }

                        Text(
                            text = "NO3 trend: ${aquarium.nitrateTrend}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricCard(
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

private fun formatAlertValue(value: Double, unit: String): String {
    val rendered = if (value == value.toInt().toDouble()) {
        value.toInt().toString()
    } else {
        String.format("%.2f", value)
    }
    return if (unit.isBlank()) rendered else "$rendered $unit"
}