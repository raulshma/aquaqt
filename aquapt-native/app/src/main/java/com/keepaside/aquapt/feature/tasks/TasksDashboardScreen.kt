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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
                                Text(
                                    text = "${execution.completedAtLabel} • ${execution.taskTitle} • ${execution.aquariumName}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
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
    }
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