package com.keepaside.aquapt.feature.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.keepaside.aquapt.core.logic.evaluateParameterAlerts
import com.keepaside.aquapt.core.logic.isTaskDue
import com.keepaside.aquapt.core.model.Aquarium
import com.keepaside.aquapt.core.model.Issue
import com.keepaside.aquapt.core.model.IssueStatus
import com.keepaside.aquapt.core.model.TaskExecution
import com.keepaside.aquapt.core.model.TaskTemplate
import com.keepaside.aquapt.core.model.WaterParameterLog
import com.keepaside.aquapt.core.repository.AquariumRepository
import com.keepaside.aquapt.core.repository.IssueRepository
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

data class GlobalInsightsSummary(
    val aquariumCount: Int = 0,
    val dueTaskCount: Int = 0,
    val activeIssueCount: Int = 0,
    val safetyAlertCount: Int = 0
)

data class InsightRecommendationItem(
    val id: String,
    val message: String,
    val isHighlighted: Boolean
)

data class AquariumFocusItem(
    val aquariumId: String,
    val aquariumName: String,
    val dueTaskCount: Int,
    val activeIssueCount: Int,
    val safetyAlertCount: Int,
    val focusScore: Int
)

data class GlobalInsightsUiState(
    val isLoading: Boolean = true,
    val isEmpty: Boolean = false,
    val headline: String = "Loading global insights…",
    val summary: GlobalInsightsSummary = GlobalInsightsSummary(),
    val recommendations: List<InsightRecommendationItem> = emptyList(),
    val aquariumFocus: List<AquariumFocusItem> = emptyList()
)

class GlobalInsightsViewModel(
    private val aquariumRepository: AquariumRepository,
    private val taskTemplateRepository: TaskTemplateRepository,
    private val taskExecutionRepository: TaskExecutionRepository,
    private val issueRepository: IssueRepository,
    private val waterParameterLogRepository: WaterParameterLogRepository,
    private val nowProvider: () -> Instant = { Instant.now() },
    private val zoneId: ZoneId = ZoneId.systemDefault()
) : ViewModel() {

    private val _uiState = MutableStateFlow(GlobalInsightsUiState())
    val uiState: StateFlow<GlobalInsightsUiState> = _uiState.asStateFlow()

    init {
        observeInsights()
    }

    private fun observeInsights() {
        viewModelScope.launch {
            combine(
                aquariumRepository.getAll(),
                taskTemplateRepository.getAll(),
                taskExecutionRepository.getAll(),
                issueRepository.getAll(),
                waterParameterLogRepository.getAll()
            ) { aquariums, taskTemplates, taskExecutions, issues, parameterLogs ->
                assembleGlobalInsightsUiState(
                    aquariums = aquariums,
                    taskTemplates = taskTemplates,
                    taskExecutions = taskExecutions,
                    issues = issues,
                    parameterLogs = parameterLogs,
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

    companion object {
        fun factory(
            aquariumRepository: AquariumRepository,
            taskTemplateRepository: TaskTemplateRepository,
            taskExecutionRepository: TaskExecutionRepository,
            issueRepository: IssueRepository,
            waterParameterLogRepository: WaterParameterLogRepository
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(GlobalInsightsViewModel::class.java)) {
                        return GlobalInsightsViewModel(
                            aquariumRepository = aquariumRepository,
                            taskTemplateRepository = taskTemplateRepository,
                            taskExecutionRepository = taskExecutionRepository,
                            issueRepository = issueRepository,
                            waterParameterLogRepository = waterParameterLogRepository
                        ) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
    }
}

internal fun assembleGlobalInsightsUiState(
    aquariums: List<Aquarium>,
    taskTemplates: List<TaskTemplate>,
    taskExecutions: List<TaskExecution>,
    issues: List<Issue>,
    parameterLogs: List<WaterParameterLog>,
    now: Instant,
    zoneId: ZoneId
): GlobalInsightsUiState {
    val dueTaskCountByAquarium = mutableMapOf<String, Int>()
    taskTemplates.forEach { template ->
        template.aquariumIds.forEach { aquariumId ->
            if (isTaskDue(template, aquariumId, taskExecutions, now, zoneId)) {
                dueTaskCountByAquarium[aquariumId] = (dueTaskCountByAquarium[aquariumId] ?: 0) + 1
            }
        }
    }

    val activeIssuesByAquarium = issues
        .filter { it.status != IssueStatus.RESOLVED }
        .groupBy { it.aquariumId }

    val latestParameterLogByAquarium = parameterLogs
        .groupBy { it.aquariumId }
        .mapValues { (_, logs) ->
            logs.maxByOrNull { parseToInstant(it.createdAt, zoneId)?.toEpochMilli() ?: Long.MIN_VALUE }
        }

    val safetyAlertCountByAquarium = aquariums.associate { aquarium ->
        val latestLog = latestParameterLogByAquarium[aquarium.id]
        val count = latestLog
            ?.let { evaluateParameterAlerts(aquarium, it.values).size }
            ?: 0
        aquarium.id to count
    }

    val summary = GlobalInsightsSummary(
        aquariumCount = aquariums.size,
        dueTaskCount = dueTaskCountByAquarium.values.sum(),
        activeIssueCount = activeIssuesByAquarium.values.sumOf { it.size },
        safetyAlertCount = safetyAlertCountByAquarium.values.sum()
    )

    if (aquariums.isEmpty()) {
        return GlobalInsightsUiState(
            isEmpty = true,
            headline = "Add your first tank to unlock portfolio-level insights.",
            summary = summary,
            recommendations = buildRecommendations(summary)
        )
    }

    val aquariumFocus = aquariums
        .map { aquarium ->
            val due = dueTaskCountByAquarium[aquarium.id] ?: 0
            val issuesCount = activeIssuesByAquarium[aquarium.id].orEmpty().size
            val alerts = safetyAlertCountByAquarium[aquarium.id] ?: 0
            val score = (alerts * 3) + (issuesCount * 2) + due

            AquariumFocusItem(
                aquariumId = aquarium.id,
                aquariumName = aquarium.name,
                dueTaskCount = due,
                activeIssueCount = issuesCount,
                safetyAlertCount = alerts,
                focusScore = score
            )
        }
        .filter { it.focusScore > 0 }
        .sortedWith(
            compareByDescending<AquariumFocusItem> { it.focusScore }
                .thenByDescending { it.safetyAlertCount }
                .thenByDescending { it.activeIssueCount }
                .thenByDescending { it.dueTaskCount }
                .thenBy { it.aquariumName }
        )

    return GlobalInsightsUiState(
        isEmpty = false,
        headline = buildHeadline(summary),
        summary = summary,
        recommendations = buildRecommendations(summary),
        aquariumFocus = aquariumFocus
    )
}

private fun buildHeadline(summary: GlobalInsightsSummary): String = when {
    summary.safetyAlertCount > 0 -> {
        "${summary.safetyAlertCount} safety alert${plural(summary.safetyAlertCount)} need attention across ${summary.aquariumCount} tank${plural(summary.aquariumCount)}."
    }
    summary.dueTaskCount > 0 -> {
        "${summary.dueTaskCount} due task${plural(summary.dueTaskCount)} should be completed today."
    }
    summary.activeIssueCount > 0 -> {
        "${summary.activeIssueCount} active issue${plural(summary.activeIssueCount)} ${if (summary.activeIssueCount == 1) "is" else "are"} being tracked."
    }
    else -> {
        "Portfolio health looks steady across ${summary.aquariumCount} tank${plural(summary.aquariumCount)}."
    }
}

private fun buildRecommendations(summary: GlobalInsightsSummary): List<InsightRecommendationItem> {
    val recommendations = listOf(
        InsightRecommendationItem(
            id = "due",
            message = "Complete due tasks first to keep schedule drift low.",
            isHighlighted = summary.dueTaskCount > 0
        ),
        InsightRecommendationItem(
            id = "alerts",
            message = "Resolve safety alerts before adding livestock.",
            isHighlighted = summary.safetyAlertCount > 0
        ),
        InsightRecommendationItem(
            id = "issues",
            message = "Close open issues with resolution notes for better diagnostics.",
            isHighlighted = summary.activeIssueCount > 0
        )
    )

    return if (recommendations.any { it.isHighlighted }) {
        recommendations
    } else {
        recommendations + InsightRecommendationItem(
            id = "steady",
            message = "No urgent blockers detected. Keep routine checks and regular logging.",
            isHighlighted = true
        )
    }
}

private fun plural(value: Int): String = if (value == 1) "" else "s"

private fun parseToInstant(raw: String, zoneId: ZoneId): Instant? {
    val value = raw.trim()
    if (value.isEmpty()) return null

    return runCatching { Instant.parse(value) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()
        ?: runCatching { LocalDateTime.parse(value).atZone(zoneId).toInstant() }.getOrNull()
        ?: runCatching { LocalDate.parse(value).atStartOfDay(zoneId).toInstant() }.getOrNull()
}
