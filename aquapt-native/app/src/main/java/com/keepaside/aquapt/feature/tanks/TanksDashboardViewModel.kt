package com.keepaside.aquapt.feature.tanks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.keepaside.aquapt.core.logic.ParameterAlert
import com.keepaside.aquapt.core.logic.evaluateParameterAlerts
import com.keepaside.aquapt.core.logic.isTaskDue
import com.keepaside.aquapt.core.model.Aquarium
import com.keepaside.aquapt.core.model.Issue
import com.keepaside.aquapt.core.model.IssueStatus
import com.keepaside.aquapt.core.model.Livestock
import com.keepaside.aquapt.core.model.TaskExecution
import com.keepaside.aquapt.core.model.TaskTemplate
import com.keepaside.aquapt.core.model.WaterParameterLog
import com.keepaside.aquapt.core.repository.AquariumRepository
import com.keepaside.aquapt.core.repository.DosingLogRepository
import com.keepaside.aquapt.core.repository.IssueRepository
import com.keepaside.aquapt.core.repository.LivestockRepository
import com.keepaside.aquapt.core.repository.TaskExecutionRepository
import com.keepaside.aquapt.core.repository.TaskTemplateRepository
import com.keepaside.aquapt.core.repository.WaterParameterLogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlin.math.abs

data class TanksSummaryMetrics(
    val aquariumCount: Int = 0,
    val residentCount: Int = 0,
    val dueTaskCount: Int = 0,
    val openIssueCount: Int = 0,
    val parameterAlertCount: Int = 0,
    val dosingLogCount: Int = 0,
    val parameterLogCount: Int = 0
)

data class DueTaskItem(
    val taskId: String,
    val taskTitle: String,
    val aquariumId: String,
    val aquariumName: String
)

data class AquariumAlertItem(
    val aquariumId: String,
    val aquariumName: String,
    val key: String,
    val label: String,
    val value: Double,
    val unit: String,
    val status: String
)

data class AquariumDashboardCard(
    val aquariumId: String,
    val aquariumName: String,
    val waterTypeLabel: String,
    val volumeLiters: Double,
    val setupDate: String,
    val latestParameterSummary: String,
    val nitrateTrend: String,
    val residentCount: Int,
    val openIssueCount: Int,
    val dueTaskCount: Int,
    val activeAlertCount: Int
)

data class TanksDashboardUiState(
    val isLoading: Boolean = true,
    val isEmpty: Boolean = false,
    val headline: String = "Loading dashboard…",
    val summary: TanksSummaryMetrics = TanksSummaryMetrics(),
    val alerts: List<AquariumAlertItem> = emptyList(),
    val dueTasks: List<DueTaskItem> = emptyList(),
    val aquariums: List<AquariumDashboardCard> = emptyList()
)

class TanksDashboardViewModel(
    private val aquariumRepository: AquariumRepository,
    private val livestockRepository: LivestockRepository,
    private val taskTemplateRepository: TaskTemplateRepository,
    private val taskExecutionRepository: TaskExecutionRepository,
    private val issueRepository: IssueRepository,
    private val waterParameterLogRepository: WaterParameterLogRepository,
    private val dosingLogRepository: DosingLogRepository,
    private val nowProvider: () -> Instant = { Instant.now() },
    private val zoneId: ZoneId = ZoneId.systemDefault()
) : ViewModel() {

    private val _uiState = MutableStateFlow(TanksDashboardUiState())
    val uiState: StateFlow<TanksDashboardUiState> = _uiState.asStateFlow()

    init {
        observeDashboard()
    }

    private fun observeDashboard() {
        val baseDataFlow = combine(
            aquariumRepository.getAll(),
            livestockRepository.getAll(),
            taskTemplateRepository.getAll(),
            taskExecutionRepository.getAll()
        ) { aquariums, livestock, taskTemplates, taskExecutions ->
            BaseDashboardData(
                aquariums = aquariums,
                livestock = livestock,
                taskTemplates = taskTemplates,
                taskExecutions = taskExecutions
            )
        }

        viewModelScope.launch {
            combine(
                baseDataFlow,
                issueRepository.getAll(),
                waterParameterLogRepository.getAll(),
                dosingLogRepository.getAll()
            ) { base, issues, parameterLogs, dosingLogs ->
                assembleTanksDashboardUiState(
                    aquariums = base.aquariums,
                    livestock = base.livestock,
                    taskTemplates = base.taskTemplates,
                    taskExecutions = base.taskExecutions,
                    issues = issues,
                    parameterLogs = parameterLogs,
                    dosingLogCount = dosingLogs.size,
                    now = nowProvider(),
                    zoneId = zoneId
                )
            }.collect { next ->
                _uiState.update {
                    next.copy(isLoading = false)
                }
            }
        }
    }

    private data class BaseDashboardData(
        val aquariums: List<Aquarium>,
        val livestock: List<Livestock>,
        val taskTemplates: List<TaskTemplate>,
        val taskExecutions: List<TaskExecution>
    )

    companion object {
        fun factory(
            aquariumRepository: AquariumRepository,
            livestockRepository: LivestockRepository,
            taskTemplateRepository: TaskTemplateRepository,
            taskExecutionRepository: TaskExecutionRepository,
            issueRepository: IssueRepository,
            waterParameterLogRepository: WaterParameterLogRepository,
            dosingLogRepository: DosingLogRepository
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(TanksDashboardViewModel::class.java)) {
                        return TanksDashboardViewModel(
                            aquariumRepository = aquariumRepository,
                            livestockRepository = livestockRepository,
                            taskTemplateRepository = taskTemplateRepository,
                            taskExecutionRepository = taskExecutionRepository,
                            issueRepository = issueRepository,
                            waterParameterLogRepository = waterParameterLogRepository,
                            dosingLogRepository = dosingLogRepository
                        ) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
    }
}

internal fun assembleTanksDashboardUiState(
    aquariums: List<Aquarium>,
    livestock: List<Livestock>,
    taskTemplates: List<TaskTemplate>,
    taskExecutions: List<TaskExecution>,
    issues: List<Issue>,
    parameterLogs: List<WaterParameterLog>,
    dosingLogCount: Int,
    now: Instant,
    zoneId: ZoneId
): TanksDashboardUiState {
    if (aquariums.isEmpty()) {
        return TanksDashboardUiState(
            isEmpty = true,
            headline = "Add your first tank to begin tracking care routines.",
            summary = TanksSummaryMetrics(
                aquariumCount = 0,
                residentCount = livestock.sumOf { it.quantity.coerceAtLeast(0) },
                dueTaskCount = 0,
                openIssueCount = issues.count { it.status != IssueStatus.RESOLVED },
                parameterAlertCount = 0,
                dosingLogCount = dosingLogCount,
                parameterLogCount = parameterLogs.size
            )
        )
    }

    val aquariumNameById = aquariums.associate { it.id to it.name }
    val livestockByAquarium = livestock.groupBy { it.aquariumId }
    val openIssuesByAquarium = issues
        .filter { it.status != IssueStatus.RESOLVED }
        .groupBy { it.aquariumId }

    val dueTaskItems = taskTemplates.flatMap { task ->
        task.aquariumIds.mapNotNull { aquariumId ->
            if (!isTaskDue(task, aquariumId, taskExecutions, now, zoneId)) {
                return@mapNotNull null
            }

            DueTaskItem(
                taskId = task.id,
                taskTitle = task.title,
                aquariumId = aquariumId,
                aquariumName = aquariumNameById[aquariumId] ?: "Unknown tank"
            )
        }
    }

    val dueTaskCountByAquarium = dueTaskItems.groupingBy { it.aquariumId }.eachCount()
    val latestLogByAquarium = parameterLogs
        .groupBy { it.aquariumId }
        .mapValues { (_, logs) ->
            logs.maxByOrNull { parseToInstant(it.createdAt, zoneId)?.toEpochMilli() ?: Long.MIN_VALUE }
        }

    val alertsByAquarium = mutableMapOf<String, List<ParameterAlert>>()
    val alertItems = mutableListOf<AquariumAlertItem>()

    for (aquarium in aquariums) {
        val latestLog = latestLogByAquarium[aquarium.id]
        val alerts = latestLog?.let { evaluateParameterAlerts(aquarium, it.values) }.orEmpty()
        alertsByAquarium[aquarium.id] = alerts

        alertItems += alerts.map { alert ->
            AquariumAlertItem(
                aquariumId = aquarium.id,
                aquariumName = aquarium.name,
                key = alert.key,
                label = alert.label,
                value = alert.value,
                unit = alert.unit,
                status = alert.status
            )
        }
    }

    val aquariumCards = aquariums.map { aquarium ->
        val latestLog = latestLogByAquarium[aquarium.id]
        val nitrateTrend = computeNitrateTrend(
            logs = parameterLogs.filter { it.aquariumId == aquarium.id },
            zoneId = zoneId
        )

        AquariumDashboardCard(
            aquariumId = aquarium.id,
            aquariumName = aquarium.name,
            waterTypeLabel = aquarium.waterType.name.lowercase().replaceFirstChar { it.uppercaseChar() },
            volumeLiters = aquarium.volumeLiters,
            setupDate = aquarium.setupDate,
            latestParameterSummary = latestLog?.let { log ->
                "NO3 ${prettyNumber(log.values.nitrate)} • pH ${prettyNumber(log.values.ph)} • ${prettyNumber(log.values.temperatureC)}°C"
            } ?: "No measurements logged yet",
            nitrateTrend = nitrateTrend,
            residentCount = livestockByAquarium[aquarium.id].orEmpty().sumOf { it.quantity.coerceAtLeast(0) },
            openIssueCount = openIssuesByAquarium[aquarium.id].orEmpty().size,
            dueTaskCount = dueTaskCountByAquarium[aquarium.id] ?: 0,
            activeAlertCount = alertsByAquarium[aquarium.id].orEmpty().size
        )
    }

    val alertCount = alertItems.size
    val headline = when {
        alertCount > 0 -> "$alertCount water alerts need attention across ${aquariums.size} tank${if (aquariums.size == 1) "" else "s"}."
        dueTaskItems.isNotEmpty() -> "${dueTaskItems.size} task${if (dueTaskItems.size == 1) "" else "s"} are due today."
        else -> "Everything looks steady across ${aquariums.size} tank${if (aquariums.size == 1) "" else "s"}."
    }

    return TanksDashboardUiState(
        isEmpty = false,
        headline = headline,
        summary = TanksSummaryMetrics(
            aquariumCount = aquariums.size,
            residentCount = livestock.sumOf { it.quantity.coerceAtLeast(0) },
            dueTaskCount = dueTaskItems.size,
            openIssueCount = openIssuesByAquarium.values.sumOf { it.size },
            parameterAlertCount = alertCount,
            dosingLogCount = dosingLogCount,
            parameterLogCount = parameterLogs.size
        ),
        alerts = alertItems.sortedWith(
            compareByDescending<AquariumAlertItem> { it.status == "high" }
                .thenBy { abs(it.value) }
        ),
        dueTasks = dueTaskItems,
        aquariums = aquariumCards
    )
}

private fun computeNitrateTrend(
    logs: List<WaterParameterLog>,
    zoneId: ZoneId
): String {
    val nitratePoints = logs
        .sortedBy { parseToInstant(it.createdAt, zoneId)?.toEpochMilli() ?: Long.MIN_VALUE }
        .mapNotNull { it.values.nitrate }
        .takeLast(5)

    if (nitratePoints.size < 2) {
        return "Not enough data yet"
    }

    val first = nitratePoints.first()
    val last = nitratePoints.last()
    val delta = (last - first)
    val rounded = String.format("%.2f", delta)
    val direction = when {
        delta > 0 -> "↑"
        delta < 0 -> "↓"
        else -> "→"
    }

    return "$direction ${if (delta >= 0) "+" else ""}$rounded ppm"
}

private fun prettyNumber(value: Double?): String =
    when (value) {
        null -> "-"
        else -> {
            val roundedToInt = value.toInt().toDouble()
            if (value == roundedToInt) {
                roundedToInt.toInt().toString()
            } else {
                String.format("%.2f", value)
            }
        }
    }

private fun parseToInstant(raw: String, zoneId: ZoneId): Instant? {
    val value = raw.trim()
    if (value.isEmpty()) return null

    return runCatching { Instant.parse(value) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()
        ?: runCatching { LocalDateTime.parse(value).atZone(zoneId).toInstant() }.getOrNull()
        ?: runCatching { LocalDate.parse(value).atStartOfDay(zoneId).toInstant() }.getOrNull()
}