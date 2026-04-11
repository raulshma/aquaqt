package com.keepaside.aquapt.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.keepaside.aquapt.core.assistant.AssistantGateway
import com.keepaside.aquapt.core.assistant.AssistantGatewayMessage
import com.keepaside.aquapt.core.assistant.AssistantGatewayRequest
import com.keepaside.aquapt.core.model.AppSettings
import com.keepaside.aquapt.core.model.AssistantMessageRole
import com.keepaside.aquapt.core.model.IssueStatus
import com.keepaside.aquapt.core.model.LivestockKind
import com.keepaside.aquapt.core.model.WaterParameters
import com.keepaside.aquapt.core.repository.AquariumRepository
import com.keepaside.aquapt.core.repository.AppSettingsStore
import com.keepaside.aquapt.core.repository.IssueRepository
import com.keepaside.aquapt.core.repository.LivestockRepository
import com.keepaside.aquapt.core.repository.TaskExecutionRepository
import com.keepaside.aquapt.core.repository.TaskTemplateRepository
import com.keepaside.aquapt.core.repository.WaterParameterLogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class WorkflowAssistantMode {
    GENERAL, DIAGNOSTIC, COMPATIBILITY, TASK_SUGGESTION
}

@Serializable
data class WorkflowAquariumContext(
    val name: String,
    val waterType: String,
    val latestParams: WaterParameters? = null,
    val openIssues: List<String> = emptyList()
)

@Serializable
data class WorkflowAppContext(
    val aquariumSummary: List<WorkflowAquariumContext> = emptyList(),
    val userLocale: WorkflowUserLocale = WorkflowUserLocale(),
    val livestockCount: Int = 0,
    val recentParameterLogCount: Int = 0,
    val openIssueCount: Int = 0,
    val taskTemplateCount: Int = 0,
    val recentTaskExecutionCount: Int = 0
)

@Serializable
data class WorkflowUserLocale(
    val locale: String = "",
    val timezone: String = "",
    val country: String = "",
    val currency: String = ""
)

data class WorkflowQuestionPreset(
    val label: String,
    val mode: WorkflowAssistantMode,
    val question: String
)

data class WorkflowDiagnosticDraft(
    val aquariumId: String = "",
    val windowDays: String = "14",
    val symptoms: String = ""
)

data class WorkflowCompatibilityDraft(
    val aquariumId: String = "",
    val species: String = "",
    val kind: LivestockKind = LivestockKind.SHRIMP,
    val quantity: String = "1",
    val notes: String = ""
)

data class WorkflowQADraft(
    val mode: WorkflowAssistantMode = WorkflowAssistantMode.GENERAL,
    val question: String = ""
)

data class WorkflowAquariumOption(
    val id: String,
    val name: String
)

data class SettingsWorkflowUiState(
    val isLoading: Boolean = true,
    val isRequesting: Boolean = false,
    val currentModel: String = "",
    val hasApiKey: Boolean = false,
    val qaDraft: WorkflowQADraft = WorkflowQADraft(),
    val qaAnswer: String = "",
    val qaError: String? = null,
    val diagnosticDraft: WorkflowDiagnosticDraft = WorkflowDiagnosticDraft(),
    val diagnosticAnswer: String = "",
    val diagnosticError: String? = null,
    val compatibilityDraft: WorkflowCompatibilityDraft = WorkflowCompatibilityDraft(),
    val compatibilityAnswer: String = "",
    val compatibilityError: String? = null,
    val aquariumOptions: List<WorkflowAquariumOption> = emptyList(),
    val statusMessage: String = ""
)

private const val systemPrompt =
    "You are Aquapt assistant. Give concise, practical aquarium advice based on provided context. If uncertain, say so. Prioritize actionable steps with safety-first guidance."

internal val modePrompts = mapOf(
    WorkflowAssistantMode.GENERAL to
        "Answer clearly and concisely. Provide practical aquarium-safe recommendations and include brief rationale.",
    WorkflowAssistantMode.DIAGNOSTIC to
        "Prioritize diagnosis from trends. List likely causes ranked by confidence, then immediate safe actions, then monitoring checks for the next 7 days.",
    WorkflowAssistantMode.COMPATIBILITY to
        "Evaluate species compatibility using current livestock, water parameters, and water type. Highlight conflicts and provide safer alternatives if needed.",
    WorkflowAssistantMode.TASK_SUGGESTION to
        "Suggest actionable maintenance/task adjustments based on open issues and recent logs. Provide a simple schedule with frequency and expected outcome."
)

val workflowQuestionPresets = listOf(
    WorkflowQuestionPreset(
        label = "Shrimp issue",
        mode = WorkflowAssistantMode.DIAGNOSTIC,
        question = "Why are my shrimp struggling lately? Please analyze my recent trends and suggest next actions."
    ),
    WorkflowQuestionPreset(
        label = "Stocking check",
        mode = WorkflowAssistantMode.COMPATIBILITY,
        question = "Can I add Cherry Shrimp to my current tank safely? Explain compatibility and parameter constraints."
    ),
    WorkflowQuestionPreset(
        label = "Algae plan",
        mode = WorkflowAssistantMode.TASK_SUGGESTION,
        question = "I keep getting algae reports. What maintenance and dosing schedule should I follow for the next 2 weeks?"
    )
)

class SettingsWorkflowViewModel(
    private val aquariumRepository: AquariumRepository,
    private val livestockRepository: LivestockRepository,
    private val issueRepository: IssueRepository,
    private val parameterLogRepository: WaterParameterLogRepository,
    private val taskTemplateRepository: TaskTemplateRepository,
    private val taskExecutionRepository: TaskExecutionRepository,
    private val appSettingsStore: AppSettingsStore,
    private val assistantGateway: AssistantGateway,
    private val externalScope: CoroutineScope? = null
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _uiState = MutableStateFlow(SettingsWorkflowUiState())
    val uiState: StateFlow<SettingsWorkflowUiState> = _uiState.asStateFlow()

    private var observerJob: Job? = null

    init {
        observerJob = observeState()
    }

    fun onQAModeChanged(mode: WorkflowAssistantMode) {
        _uiState.update { it.copy(qaDraft = it.qaDraft.copy(mode = mode)) }
    }

    fun onQAQuestionChanged(question: String) {
        _uiState.update { it.copy(qaDraft = it.qaDraft.copy(question = question)) }
    }

    fun onDiagnosticAquariumChanged(aquariumId: String) {
        _uiState.update {
            it.copy(diagnosticDraft = it.diagnosticDraft.copy(aquariumId = aquariumId))
        }
    }

    fun onDiagnosticWindowDaysChanged(days: String) {
        _uiState.update {
            it.copy(diagnosticDraft = it.diagnosticDraft.copy(windowDays = days))
        }
    }

    fun onDiagnosticSymptomsChanged(symptoms: String) {
        _uiState.update {
            it.copy(diagnosticDraft = it.diagnosticDraft.copy(symptoms = symptoms))
        }
    }

    fun onCompatibilityAquariumChanged(aquariumId: String) {
        _uiState.update {
            it.copy(compatibilityDraft = it.compatibilityDraft.copy(aquariumId = aquariumId))
        }
    }

    fun onCompatibilitySpeciesChanged(species: String) {
        _uiState.update {
            it.copy(compatibilityDraft = it.compatibilityDraft.copy(species = species))
        }
    }

    fun onCompatibilityKindChanged(kind: LivestockKind) {
        _uiState.update {
            it.copy(compatibilityDraft = it.compatibilityDraft.copy(kind = kind))
        }
    }

    fun onCompatibilityQuantityChanged(quantity: String) {
        _uiState.update {
            it.copy(compatibilityDraft = it.compatibilityDraft.copy(quantity = quantity))
        }
    }

    fun onCompatibilityNotesChanged(notes: String) {
        _uiState.update {
            it.copy(compatibilityDraft = it.compatibilityDraft.copy(notes = notes))
        }
    }

    fun selectPreset(preset: WorkflowQuestionPreset) {
        _uiState.update {
            it.copy(
                qaDraft = WorkflowQADraft(mode = preset.mode, question = preset.question)
            )
        }
    }

    fun askAssistant() {
        val current = _uiState.value
        if (current.isRequesting) return
        val apiKey = appSettingsStore.settings.value.openRouterApiKey.trim()
        val model = appSettingsStore.settings.value.aiModel.trim()
        val question = current.qaDraft.question.trim()
        if (apiKey.isEmpty() || question.isEmpty()) {
            _uiState.update { it.copy(statusMessage = "API key and question are required.") }
            return
        }

        val mode = current.qaDraft.mode
        launchWork {
            _uiState.update { it.copy(isRequesting = true, qaError = null, statusMessage = "") }
            runCatching {
                val context = buildAppContext()
                requestWorkflowCompletion(apiKey, model, mode, question, context)
            }.onSuccess { answer ->
                _uiState.update {
                    it.copy(isRequesting = false, qaAnswer = answer, statusMessage = "")
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isRequesting = false,
                        qaError = error.message ?: "Assistant request failed.",
                        statusMessage = ""
                    )
                }
            }
        }
    }

    fun runDiagnosticWorkflow() {
        val current = _uiState.value
        if (current.isRequesting) return
        val apiKey = appSettingsStore.settings.value.openRouterApiKey.trim()
        val model = appSettingsStore.settings.value.aiModel.trim()
        val aquariumId = current.diagnosticDraft.aquariumId
        val symptoms = current.diagnosticDraft.symptoms.trim()
        if (apiKey.isEmpty() || aquariumId.isEmpty() || symptoms.isEmpty()) {
            _uiState.update {
                it.copy(statusMessage = "API key, aquarium selection, and symptoms are required.")
            }
            return
        }

        val aquariumName = current.aquariumOptions.find { it.id == aquariumId }?.name ?: "Unknown"
        val daysParsed = current.diagnosticDraft.windowDays.trim().toIntOrNull()
        val windowDays = if (daysParsed != null && daysParsed > 0) daysParsed else 14

        val prompt = listOf(
            "Perform a focused diagnostic review for aquarium \"$aquariumName\" over the last $windowDays days.",
            "Observed symptoms: $symptoms",
            "Please output:\n1) Most likely root causes ranked\n2) Immediate safe actions (today)\n3) Monitoring checklist for next 7 days\n4) Red flags that require urgent intervention"
        ).joinToString("\n\n")

        launchWork {
            _uiState.update {
                it.copy(isRequesting = true, diagnosticError = null, diagnosticAnswer = "", statusMessage = "")
            }
            runCatching {
                val context = buildAppContext()
                requestWorkflowCompletion(apiKey, model, WorkflowAssistantMode.DIAGNOSTIC, prompt, context)
            }.onSuccess { answer ->
                _uiState.update {
                    it.copy(isRequesting = false, diagnosticAnswer = answer, statusMessage = "")
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isRequesting = false,
                        diagnosticError = error.message ?: "Diagnostic request failed.",
                        statusMessage = ""
                    )
                }
            }
        }
    }

    fun runCompatibilityWorkflow() {
        val current = _uiState.value
        if (current.isRequesting) return
        val apiKey = appSettingsStore.settings.value.openRouterApiKey.trim()
        val model = appSettingsStore.settings.value.aiModel.trim()
        val aquariumId = current.compatibilityDraft.aquariumId
        val species = current.compatibilityDraft.species.trim()
        if (apiKey.isEmpty() || aquariumId.isEmpty() || species.isEmpty()) {
            _uiState.update {
                it.copy(statusMessage = "API key, aquarium selection, and species name are required.")
            }
            return
        }

        val aquariumOption = current.aquariumOptions.find { it.id == aquariumId }
        val aquariumName = aquariumOption?.name ?: "Unknown"
        val quantityParsed = current.compatibilityDraft.quantity.trim().toIntOrNull()
        val quantity = if (quantityParsed != null && quantityParsed > 0) quantityParsed else 1
        val kind = current.compatibilityDraft.kind.name.lowercase()
        val notes = current.compatibilityDraft.notes.trim()

        launchWork {
            _uiState.update {
                it.copy(isRequesting = true, compatibilityError = null, compatibilityAnswer = "", statusMessage = "")
            }
            runCatching {
                val aquariumsSnapshot = aquariumRepository.getAll().first()
                val waterType = aquariumsSnapshot
                    .find { it.id == aquariumId }?.waterType?.name?.lowercase() ?: "unknown"
                val promptParts = mutableListOf(
                    "Compatibility check for aquarium \"$aquariumName\" ($waterType).",
                    "Candidate addition: $quantity x $species ($kind)."
                )
                if (notes.isNotEmpty()) {
                    promptParts.add("Extra notes: $notes")
                }
                promptParts.add(
                    "Please output:\n1) Compatibility verdict (Safe / Caution / Not recommended)\n2) Main conflict risks\n3) Parameter gaps to fix before adding\n4) Safer alternatives if needed"
                )
                val context = buildAppContext()
                requestWorkflowCompletion(apiKey, model, WorkflowAssistantMode.COMPATIBILITY, promptParts.joinToString("\n\n"), context)
            }.onSuccess { answer ->
                _uiState.update {
                    it.copy(isRequesting = false, compatibilityAnswer = answer, statusMessage = "")
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isRequesting = false,
                        compatibilityError = error.message ?: "Compatibility request failed.",
                        statusMessage = ""
                    )
                }
            }
        }
    }

    private suspend fun requestWorkflowCompletion(
        apiKey: String,
        model: String,
        mode: WorkflowAssistantMode,
        userQuestion: String,
        appContext: WorkflowAppContext
    ): String {
        val contextJson = json.encodeToString(WorkflowAppContext.serializer(), appContext)
        val messages = listOf(
            AssistantGatewayMessage(
                role = AssistantMessageRole.SYSTEM,
                content = systemPrompt
            ),
            AssistantGatewayMessage(
                role = AssistantMessageRole.SYSTEM,
                content = "Assistant mode: ${mode.name.lowercase().replace('_', '-')}"
            ),
            AssistantGatewayMessage(
                role = AssistantMessageRole.SYSTEM,
                content = modePrompts[mode] ?: ""
            ),
            AssistantGatewayMessage(
                role = AssistantMessageRole.SYSTEM,
                content = "App context: $contextJson"
            ),
            AssistantGatewayMessage(
                role = AssistantMessageRole.USER,
                content = userQuestion
            )
        )

        val response = assistantGateway.requestStreamingReply(
            request = AssistantGatewayRequest(
                apiKey = apiKey,
                model = model,
                messages = messages
            ),
            onSnapshot = {}
        )

        return response.text.ifEmpty { "No response." }
    }

    private suspend fun buildAppContext(): WorkflowAppContext {
        val settings = appSettingsStore.settings.value
        val aquariums = aquariumRepository.getAll().first().take(8)
        val allLivestock = livestockRepository.getAll().first()
        val allParameterLogs = parameterLogRepository.getAll().first()
        val allIssues = issueRepository.getAll().first()
        val allTaskTemplates = taskTemplateRepository.getAll().first()
        val allTaskExecutions = taskExecutionRepository.getAll().first()

        val aquariumSummaries = aquariums.map { aq ->
            val latestParams = allParameterLogs
                .filter { it.aquariumId == aq.id }
                .firstOrNull()?.values
            val openIssues = allIssues
                .filter { it.aquariumId == aq.id && it.status != IssueStatus.RESOLVED }
                .map { it.title }

            WorkflowAquariumContext(
                name = aq.name,
                waterType = aq.waterType.name.lowercase(),
                latestParams = latestParams,
                openIssues = openIssues
            )
        }

        return WorkflowAppContext(
            aquariumSummary = aquariumSummaries,
            userLocale = WorkflowUserLocale(
                locale = settings.defaultLocale.orEmpty(),
                timezone = settings.defaultTimezone.orEmpty(),
                country = settings.defaultCountryName.orEmpty(),
                currency = settings.defaultCurrency.orEmpty()
            ),
            livestockCount = allLivestock.take(40).size,
            recentParameterLogCount = allParameterLogs.take(60).size,
            openIssueCount = allIssues.count { it.status != IssueStatus.RESOLVED },
            taskTemplateCount = allTaskTemplates.size,
            recentTaskExecutionCount = allTaskExecutions.take(80).size
        )
    }

    private fun observeState(): Job = launchWork {
        combine(
            aquariumRepository.getAll(),
            appSettingsStore.settings
        ) { aquariums, settings ->
            val options = aquariums.map { WorkflowAquariumOption(id = it.id, name = it.name) }
            val firstAquariumId = options.firstOrNull()?.id.orEmpty()
            _uiState.update { state ->
                val updatedAquariumId = if (state.diagnosticDraft.aquariumId.isEmpty()) {
                    firstAquariumId
                } else {
                    state.diagnosticDraft.aquariumId
                }
                val updatedCompatAquariumId = if (state.compatibilityDraft.aquariumId.isEmpty()) {
                    firstAquariumId
                } else {
                    state.compatibilityDraft.aquariumId
                }
                state.copy(
                    isLoading = false,
                    currentModel = settings.aiModel,
                    hasApiKey = settings.openRouterApiKey.trim().isNotEmpty(),
                    aquariumOptions = options,
                    diagnosticDraft = state.diagnosticDraft.copy(aquariumId = updatedAquariumId),
                    compatibilityDraft = state.compatibilityDraft.copy(aquariumId = updatedCompatAquariumId)
                )
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, Unit)
    }

    private fun launchWork(block: suspend () -> Unit): Job =
        (externalScope ?: viewModelScope).launch {
            block()
        }

    internal fun disposeForTests() {
        observerJob?.cancel()
    }

    companion object {
        fun factory(
            aquariumRepository: AquariumRepository,
            livestockRepository: LivestockRepository,
            issueRepository: IssueRepository,
            parameterLogRepository: WaterParameterLogRepository,
            taskTemplateRepository: TaskTemplateRepository,
            taskExecutionRepository: TaskExecutionRepository,
            appSettingsStore: AppSettingsStore,
            assistantGateway: AssistantGateway
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(SettingsWorkflowViewModel::class.java)) {
                        return SettingsWorkflowViewModel(
                            aquariumRepository = aquariumRepository,
                            livestockRepository = livestockRepository,
                            issueRepository = issueRepository,
                            parameterLogRepository = parameterLogRepository,
                            taskTemplateRepository = taskTemplateRepository,
                            taskExecutionRepository = taskExecutionRepository,
                            appSettingsStore = appSettingsStore,
                            assistantGateway = assistantGateway
                        ) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
    }
}

internal fun LivestockKind.toWorkflowLabel(): String = when (this) {
    LivestockKind.FISH -> "Fish"
    LivestockKind.SHRIMP -> "Shrimp"
    LivestockKind.SNAIL -> "Snail"
    LivestockKind.CORAL -> "Coral"
    LivestockKind.PLANT -> "Plant"
    LivestockKind.OTHER -> "Other"
}
