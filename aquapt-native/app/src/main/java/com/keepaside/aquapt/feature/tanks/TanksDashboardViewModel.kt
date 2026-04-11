package com.keepaside.aquapt.feature.tanks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.keepaside.aquapt.core.model.Asset
import com.keepaside.aquapt.core.model.AssetCategory
import com.keepaside.aquapt.core.logic.ParameterAlert
import com.keepaside.aquapt.core.logic.evaluateParameterAlerts
import com.keepaside.aquapt.core.logic.isTaskDue
import com.keepaside.aquapt.core.model.Aquarium
import com.keepaside.aquapt.core.model.Consumable
import com.keepaside.aquapt.core.model.ConsumableUnit
import com.keepaside.aquapt.core.model.EntityKind
import com.keepaside.aquapt.core.model.EntityRef
import com.keepaside.aquapt.core.model.DosingLog
import com.keepaside.aquapt.core.model.Issue
import com.keepaside.aquapt.core.model.IssueStatus
import com.keepaside.aquapt.core.model.Livestock
import com.keepaside.aquapt.core.model.LivestockKind
import com.keepaside.aquapt.core.model.Memo
import com.keepaside.aquapt.core.model.TaskExecution
import com.keepaside.aquapt.core.model.TaskTemplate
import com.keepaside.aquapt.core.model.TimelineEvent
import com.keepaside.aquapt.core.model.TimelineEventType
import com.keepaside.aquapt.core.model.WaterParameterLog
import com.keepaside.aquapt.core.model.WaterParameters
import com.keepaside.aquapt.core.model.WaterType
import com.keepaside.aquapt.core.repository.AssetRepository
import com.keepaside.aquapt.core.repository.AquariumRepository
import com.keepaside.aquapt.core.repository.ConsumableRepository
import com.keepaside.aquapt.core.repository.DosingLogRepository
import com.keepaside.aquapt.core.repository.IssueRepository
import com.keepaside.aquapt.core.repository.LivestockRepository
import com.keepaside.aquapt.core.repository.MemoRepository
import com.keepaside.aquapt.core.repository.TaskExecutionRepository
import com.keepaside.aquapt.core.repository.TaskTemplateRepository
import com.keepaside.aquapt.core.repository.TimelineEventRepository
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
import java.time.format.DateTimeFormatter
import java.util.UUID
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

enum class AnalyticMetric(val label: String, val unit: String) {
    AMMONIA("NH3", "ppm"),
    NITRITE("NO2", "ppm"),
    NITRATE("NO3", "ppm"),
    PH("pH", ""),
    TEMPERATURE("Temp", "°C"),
    GH("GH", ""),
    KH("KH", ""),
    SALINITY("Sal", ""),
    CALCIUM("Ca", "ppm"),
    ALKALINITY("Alk", "dKH");

    fun extractFrom(params: WaterParameters): Double? = when (this) {
        AMMONIA -> params.ammonia
        NITRITE -> params.nitrite
        NITRATE -> params.nitrate
        PH -> params.ph
        TEMPERATURE -> params.temperatureC
        GH -> params.gh
        KH -> params.kh
        SALINITY -> params.salinity
        CALCIUM -> params.calcium
        ALKALINITY -> params.alkalinity
    }
}

data class ParameterChartDataPoint(
    val value: Double,
    val dayLabel: String
)

data class ParameterChartState(
    val selectedMetric: AnalyticMetric = AnalyticMetric.NITRATE,
    val selectedAquariumId: String? = null,
    val chartData: List<ParameterChartDataPoint> = emptyList(),
    val availableAquariums: List<Pair<String, String>> = emptyList()
)

data class TanksDashboardUiState(
    val isLoading: Boolean = true,
    val isEmpty: Boolean = false,
    val headline: String = "Loading dashboard…",
    val summary: TanksSummaryMetrics = TanksSummaryMetrics(),
    val alerts: List<AquariumAlertItem> = emptyList(),
    val dueTasks: List<DueTaskItem> = emptyList(),
    val aquariums: List<AquariumDashboardCard> = emptyList(),
    val parameterChart: ParameterChartState = ParameterChartState(),
    val quickLogDraft: TanksQuickLogDraft = TanksQuickLogDraft(),
    val quickLogDueTaskOptions: List<TanksQuickLogDueTaskOption> = emptyList(),
    val statusMessage: String? = null
)

data class TanksAquariumDraft(
    val name: String = "",
    val volumeLitersInput: String = "",
    val dimensions: String = "",
    val waterType: WaterType = WaterType.FRESHWATER,
    val setupDateInput: String = "",
    val investmentCostInput: String = ""
)

data class TanksLivestockDraft(
    val aquariumId: String? = null,
    val name: String = "",
    val species: String = "",
    val quantityInput: String = "1",
    val kind: LivestockKind = LivestockKind.FISH,
    val acquiredAtInput: String = ""
)

data class TanksAssetDraft(
    val aquariumId: String? = null,
    val category: AssetCategory = AssetCategory.OTHER,
    val brandModel: String = "",
    val purchasedAtInput: String = "",
    val priceInput: String = ""
)

data class TanksConsumableDraft(
    val aquariumId: String? = null,
    val name: String = "",
    val unit: ConsumableUnit = ConsumableUnit.ML,
    val remainingInput: String = "0",
    val reorderAtInput: String = "",
    val updatedAtInput: String = ""
)

enum class TanksQuickLogType(val label: String) {
    TASK("Task"),
    PARAMETER("Parameters"),
    MEMO("Memo"),
    ISSUE("Issue"),
    DOSING("Dosing")
}

data class TanksQuickLogDueTaskOption(
    val taskTemplateId: String,
    val title: String,
    val aquariumName: String
)

data class TanksQuickLogDraft(
    val type: TanksQuickLogType = TanksQuickLogType.PARAMETER,
    val aquariumId: String? = null,
    val taskTemplateId: String = "",
    val taskNote: String = "",
    val memoContent: String = "",
    val photoUri: String? = null,
    val issueTitle: String = "",
    val dosingProduct: String = "",
    val dosingAmountMl: String = "",
    val ammonia: String = "",
    val nitrite: String = "",
    val nitrate: String = "",
    val ph: String = "",
    val temperatureC: String = "",
    val gh: String = "",
    val kh: String = "",
    val salinity: String = "",
    val calcium: String = "",
    val alkalinity: String = ""
)

private data class TanksInteractionState(
    val statusMessage: String? = null,
    val selectedMetric: AnalyticMetric = AnalyticMetric.NITRATE,
    val selectedChartAquariumId: String? = null,
    val quickLogDraft: TanksQuickLogDraft = TanksQuickLogDraft()
)

class TanksDashboardViewModel(
    private val aquariumRepository: AquariumRepository,
    private val livestockRepository: LivestockRepository,
    private val taskTemplateRepository: TaskTemplateRepository,
    private val taskExecutionRepository: TaskExecutionRepository,
    private val issueRepository: IssueRepository,
    private val waterParameterLogRepository: WaterParameterLogRepository,
    private val dosingLogRepository: DosingLogRepository,
    private val assetRepository: AssetRepository,
    private val consumableRepository: ConsumableRepository,
    private val timelineEventRepository: TimelineEventRepository,
    private val memoRepository: MemoRepository,
    private val nowProvider: () -> Instant = { Instant.now() },
    private val idProvider: () -> String = { UUID.randomUUID().toString() },
    private val zoneId: ZoneId = ZoneId.systemDefault()
) : ViewModel() {

    private val interactionState = MutableStateFlow(TanksInteractionState())
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
                dosingLogRepository.getAll(),
                interactionState
            ) { base, issues, parameterLogs, dosingLogs, interaction ->
                val quickLogDraft = interaction.quickLogDraft
                val dueTaskOptions = buildQuickLogDueTaskOptions(
                    quickLogDraft.aquariumId,
                    base.taskTemplates,
                    base.taskExecutions,
                    base.aquariums,
                    nowProvider(),
                    zoneId
                )
                assembleTanksDashboardUiState(
                    aquariums = base.aquariums,
                    livestock = base.livestock,
                    taskTemplates = base.taskTemplates,
                    taskExecutions = base.taskExecutions,
                    issues = issues,
                    parameterLogs = parameterLogs,
                    dosingLogCount = dosingLogs.size,
                    now = nowProvider(),
                    zoneId = zoneId,
                    selectedMetric = interaction.selectedMetric,
                    selectedChartAquariumId = interaction.selectedChartAquariumId
                ).copy(
                    statusMessage = interaction.statusMessage,
                    quickLogDraft = quickLogDraft,
                    quickLogDueTaskOptions = dueTaskOptions
                )
            }.collect { next ->
                _uiState.update {
                    next.copy(isLoading = false)
                }
            }
        }
    }

    fun newAquariumDraft(): TanksAquariumDraft =
        TanksAquariumDraft(
            setupDateInput = DateTimeFormatter.ISO_LOCAL_DATE.format(nowProvider().atZone(zoneId))
        )

    fun newLivestockDraft(): TanksLivestockDraft? {
        val aquariumId = _uiState.value.aquariums.firstOrNull()?.aquariumId
        if (aquariumId == null) {
            setStatus("Add a tank before adding residents.")
            return null
        }

        return TanksLivestockDraft(
            aquariumId = aquariumId,
            acquiredAtInput = formatTanksDateTimeInput(nowProvider(), zoneId)
        )
    }

    fun newAssetDraft(): TanksAssetDraft? {
        val aquariumId = _uiState.value.aquariums.firstOrNull()?.aquariumId
        if (aquariumId == null) {
            setStatus("Add a tank before adding assets.")
            return null
        }

        return TanksAssetDraft(
            aquariumId = aquariumId,
            purchasedAtInput = formatTanksDateTimeInput(nowProvider(), zoneId)
        )
    }

    fun newConsumableDraft(): TanksConsumableDraft? {
        val aquariumId = _uiState.value.aquariums.firstOrNull()?.aquariumId
        if (aquariumId == null) {
            setStatus("Add a tank before adding consumables.")
            return null
        }

        return TanksConsumableDraft(
            aquariumId = aquariumId,
            updatedAtInput = formatTanksDateTimeInput(nowProvider(), zoneId)
        )
    }

    fun saveAquariumDraft(draft: TanksAquariumDraft) {
        val validationError = validateTanksAquariumDraft(draft)
        if (validationError != null) {
            setStatus(validationError)
            return
        }

        val volumeLiters = parseTanksPositiveDouble(draft.volumeLitersInput)
            ?: return setStatus("Volume must be a positive number.")
        val investmentCost = parseTanksNonNegativeDouble(draft.investmentCostInput)

        viewModelScope.launch {
            runCatching {
                val aquarium = Aquarium(
                    id = idProvider(),
                    name = draft.name.trim(),
                    volumeLiters = volumeLiters,
                    dimensions = draft.dimensions.trim(),
                    waterType = draft.waterType,
                    setupDate = draft.setupDateInput.trim(),
                    investmentCost = investmentCost
                )
                aquariumRepository.upsert(aquarium)
                aquarium
            }.onSuccess { aquarium ->
                setStatus("${aquarium.name} tank created.")
            }.onFailure { error ->
                setStatus(error.message ?: "Unable to save tank.")
            }
        }
    }

    fun saveLivestockDraft(draft: TanksLivestockDraft) {
        val validationError = validateTanksLivestockDraft(draft, _uiState.value.aquariums.map { it.aquariumId }, zoneId)
        if (validationError != null) {
            setStatus(validationError)
            return
        }

        val aquariumId = draft.aquariumId ?: return setStatus("Choose a tank for this resident.")
        val quantity = parseTanksPositiveInt(draft.quantityInput)
            ?: return setStatus("Quantity must be at least 1.")

        val acquiredAtInstant = parseTanksDateTimeInput(draft.acquiredAtInput, zoneId)
            ?: nowProvider()

        viewModelScope.launch {
            runCatching {
                val aquarium = aquariumRepository.getById(aquariumId)
                    ?: error("Selected tank no longer exists.")

                val livestock = Livestock(
                    id = idProvider(),
                    aquariumId = aquarium.id,
                    kind = draft.kind,
                    name = draft.name.trim(),
                    species = draft.species.trim(),
                    quantity = quantity,
                    acquiredAt = acquiredAtInstant.toString()
                )

                livestockRepository.upsert(livestock)
                timelineEventRepository.upsert(
                    TimelineEvent(
                        id = idProvider(),
                        aquariumId = aquarium.id,
                        type = TimelineEventType.LIVESTOCK,
                        createdAt = nowProvider().toString(),
                        title = "Added ${livestock.name}",
                        description = listOfNotNull(
                            livestock.kind.label(),
                            livestock.species.takeIf { it.isNotBlank() },
                            "Qty ${livestock.quantity}"
                        ).joinToString(" • ").ifBlank { null },
                        source = EntityRef(EntityKind.LIVESTOCK, livestock.id, aquarium.id),
                        related = aquariumRelatedRefs(aquarium.id)
                    )
                )

                livestock to aquarium.name
            }.onSuccess { (livestock, aquariumName) ->
                setStatus("${livestock.name} added to $aquariumName.")
            }.onFailure { error ->
                setStatus(error.message ?: "Unable to add resident.")
            }
        }
    }

    fun saveAssetDraft(draft: TanksAssetDraft) {
        val validationError = validateTanksAssetDraft(draft, _uiState.value.aquariums.map { it.aquariumId }, zoneId)
        if (validationError != null) {
            setStatus(validationError)
            return
        }

        val aquariumId = draft.aquariumId ?: return setStatus("Choose a tank for this asset.")
        val purchasedAt = parseTanksDateTimeInput(draft.purchasedAtInput, zoneId)?.toString()
        val price = parseTanksNonNegativeDouble(draft.priceInput)

        viewModelScope.launch {
            runCatching {
                val aquarium = aquariumRepository.getById(aquariumId)
                    ?: error("Selected tank no longer exists.")

                val asset = Asset(
                    id = idProvider(),
                    aquariumId = aquarium.id,
                    category = draft.category,
                    brandModel = draft.brandModel.trim(),
                    purchasedAt = purchasedAt,
                    price = price
                )

                assetRepository.upsert(asset)
                timelineEventRepository.upsert(
                    TimelineEvent(
                        id = idProvider(),
                        aquariumId = aquarium.id,
                        type = TimelineEventType.ASSET,
                        createdAt = nowProvider().toString(),
                        title = "Added asset",
                        description = listOfNotNull(
                            draft.category.label(),
                            asset.brandModel.takeIf { it.isNotBlank() },
                            asset.price?.let { "${formatMoneyValue(it)}" }
                        ).joinToString(" • ").ifBlank { null },
                        source = EntityRef(EntityKind.ASSET, asset.id, aquarium.id),
                        related = aquariumRelatedRefs(aquarium.id)
                    )
                )

                aquarium.name
            }.onSuccess { aquariumName ->
                setStatus("Asset added to $aquariumName.")
            }.onFailure { error ->
                setStatus(error.message ?: "Unable to add asset.")
            }
        }
    }

    fun saveConsumableDraft(draft: TanksConsumableDraft) {
        val validationError = validateTanksConsumableDraft(draft, _uiState.value.aquariums.map { it.aquariumId }, zoneId)
        if (validationError != null) {
            setStatus(validationError)
            return
        }

        val aquariumId = draft.aquariumId ?: return setStatus("Choose a tank for this consumable.")
        val remaining = parseTanksNonNegativeDouble(draft.remainingInput)
            ?: return setStatus("Remaining amount must be a valid non-negative number.")
        val reorderAt = parseTanksNonNegativeDouble(draft.reorderAtInput)
        val updatedAt = parseTanksDateTimeInput(draft.updatedAtInput, zoneId)
            ?: nowProvider()

        viewModelScope.launch {
            runCatching {
                val aquarium = aquariumRepository.getById(aquariumId)
                    ?: error("Selected tank no longer exists.")

                val consumable = Consumable(
                    id = idProvider(),
                    aquariumId = aquarium.id,
                    name = draft.name.trim(),
                    unit = draft.unit,
                    remaining = remaining,
                    reorderAt = reorderAt,
                    updatedAt = updatedAt.toString()
                )

                consumableRepository.upsert(consumable)
                timelineEventRepository.upsert(
                    TimelineEvent(
                        id = idProvider(),
                        aquariumId = aquarium.id,
                        type = TimelineEventType.CONSUMABLE,
                        createdAt = nowProvider().toString(),
                        title = "Added consumable",
                        description = listOfNotNull(
                            consumable.name,
                            "${formatMeasurementValue(consumable.remaining)} ${consumable.unit.name.lowercase()}"
                        ).joinToString(" • ").ifBlank { null },
                        source = EntityRef(EntityKind.CONSUMABLE, consumable.id, aquarium.id),
                        related = aquariumRelatedRefs(aquarium.id)
                    )
                )

                aquarium.name
            }.onSuccess { aquariumName ->
                setStatus("Consumable added to $aquariumName.")
            }.onFailure { error ->
                setStatus(error.message ?: "Unable to add consumable.")
            }
        }
    }

    private fun setStatus(message: String) {
        interactionState.update { it.copy(statusMessage = message) }
    }

    fun selectChartMetric(metric: AnalyticMetric) {
        interactionState.update { it.copy(selectedMetric = metric) }
    }

    fun selectChartAquarium(aquariumId: String?) {
        interactionState.update { it.copy(selectedChartAquariumId = aquariumId) }
    }

    fun prepareQuickLog() {
        val state = _uiState.value
        val preferredAquariumId = state.quickLogDraft.aquariumId
            ?: state.aquariums.firstOrNull()?.aquariumId

        interactionState.update { it.copy(quickLogDraft = TanksQuickLogDraft(aquariumId = preferredAquariumId)) }
    }

    fun onQuickLogTypeSelected(type: TanksQuickLogType) {
        interactionState.update { it.copy(quickLogDraft = it.quickLogDraft.copy(type = type)) }
    }

    fun onQuickLogAquariumSelected(aquariumId: String) {
        interactionState.update {
            it.copy(quickLogDraft = it.quickLogDraft.copy(aquariumId = aquariumId, taskTemplateId = ""))
        }
    }

    fun onQuickLogTaskTemplateSelected(taskTemplateId: String) {
        interactionState.update { it.copy(quickLogDraft = it.quickLogDraft.copy(taskTemplateId = taskTemplateId)) }
    }

    fun onQuickLogTaskNoteChanged(taskNote: String) {
        interactionState.update { it.copy(quickLogDraft = it.quickLogDraft.copy(taskNote = taskNote)) }
    }

    fun onQuickLogMemoContentChanged(content: String) {
        interactionState.update { it.copy(quickLogDraft = it.quickLogDraft.copy(memoContent = content)) }
    }

    fun onQuickLogPhotoUriChanged(photoUri: String?) {
        interactionState.update {
            it.copy(quickLogDraft = it.quickLogDraft.copy(photoUri = photoUri?.trim()?.takeIf { s -> s.isNotEmpty() }))
        }
    }

    fun onQuickLogIssueTitleChanged(title: String) {
        interactionState.update { it.copy(quickLogDraft = it.quickLogDraft.copy(issueTitle = title)) }
    }

    fun onQuickLogDosingProductChanged(product: String) {
        interactionState.update { it.copy(quickLogDraft = it.quickLogDraft.copy(dosingProduct = product)) }
    }

    fun onQuickLogDosingAmountChanged(amountMl: String) {
        interactionState.update { it.copy(quickLogDraft = it.quickLogDraft.copy(dosingAmountMl = amountMl)) }
    }

    fun onQuickLogParameterChanged(field: AnalyticMetric, value: String) {
        interactionState.update {
            it.copy(quickLogDraft = it.quickLogDraft.copyParameterValue(field, value))
        }
    }

    fun saveQuickLog() {
        val draft = interactionState.value.quickLogDraft
        val aquariumId = draft.aquariumId

        if (aquariumId == null) {
            setStatus("Add a tank before logging activity.")
            return
        }

        val validationError = validateTanksQuickLogDraft(draft)
        if (validationError != null) {
            setStatus(validationError)
            return
        }

        viewModelScope.launch {
            runCatching {
                saveQuickLogRecord(draft, aquariumId)
            }.onSuccess {
                interactionState.update { state ->
                    state.copy(quickLogDraft = state.quickLogDraft.clearedAfterSave())
                }
                val aquariumName = _uiState.value.aquariums
                    .firstOrNull { it.aquariumId == aquariumId }?.aquariumName ?: "tank"
                setStatus("${draft.type.label} added to $aquariumName.")
            }.onFailure { error ->
                setStatus(error.message ?: "Unable to save activity.")
            }
        }
    }

    private suspend fun saveQuickLogRecord(draft: TanksQuickLogDraft, aquariumId: String) {
        val now = nowProvider()
        when (draft.type) {
            TanksQuickLogType.TASK -> saveQuickTaskLog(draft, aquariumId, now)
            TanksQuickLogType.PARAMETER -> saveQuickParameterLog(draft, aquariumId, now)
            TanksQuickLogType.MEMO -> saveQuickMemoLog(draft, aquariumId, now)
            TanksQuickLogType.ISSUE -> saveQuickIssueLog(draft, aquariumId, now)
            TanksQuickLogType.DOSING -> saveQuickDosingLog(draft, aquariumId, now)
        }
    }

    private suspend fun saveQuickTaskLog(draft: TanksQuickLogDraft, aquariumId: String, now: Instant) {
        val taskTemplateId = draft.taskTemplateId.trim()
        val taskTemplate = taskTemplateRepository.getById(taskTemplateId)
            ?: error("Selected task could not be found.")

        val note = draft.taskNote.trim().takeIf { it.isNotEmpty() }
        val executionId = idProvider()

        taskExecutionRepository.upsert(
            TaskExecution(
                id = executionId,
                taskTemplateId = taskTemplate.id,
                aquariumId = aquariumId,
                completedAt = now.toString(),
                note = note
            )
        )
        timelineEventRepository.upsert(
            TimelineEvent(
                id = idProvider(),
                aquariumId = aquariumId,
                type = TimelineEventType.TASK,
                createdAt = now.toString(),
                title = "${taskTemplate.title} completed",
                description = note,
                source = EntityRef(EntityKind.TASK, taskTemplate.id, aquariumId),
                related = buildList {
                    taskTemplate.livestockId?.let { lid ->
                        add(EntityRef(EntityKind.LIVESTOCK, lid, aquariumId))
                    }
                }
            )
        )
    }

    private suspend fun saveQuickParameterLog(draft: TanksQuickLogDraft, aquariumId: String, now: Instant) {
        val values = draft.toWaterParameters()
        val parameterLogId = idProvider()

        waterParameterLogRepository.upsert(
            WaterParameterLog(
                id = parameterLogId,
                aquariumId = aquariumId,
                createdAt = now.toString(),
                values = values
            )
        )
        timelineEventRepository.upsert(
            TimelineEvent(
                id = idProvider(),
                aquariumId = aquariumId,
                type = TimelineEventType.PARAMETER,
                createdAt = now.toString(),
                title = "Water parameters",
                description = values.summaryLabel(),
                source = EntityRef(EntityKind.PARAMETER_LOG, parameterLogId, aquariumId)
            )
        )
    }

    private suspend fun saveQuickMemoLog(draft: TanksQuickLogDraft, aquariumId: String, now: Instant) {
        val content = draft.memoContent.trim()
        val memoId = idProvider()

        memoRepository.upsert(
            Memo(
                id = memoId,
                aquariumId = aquariumId,
                content = content,
                createdAt = now.toString(),
                photoUri = draft.photoUri
            )
        )
        timelineEventRepository.upsert(
            TimelineEvent(
                id = idProvider(),
                aquariumId = aquariumId,
                type = TimelineEventType.MEMO,
                createdAt = now.toString(),
                title = "Memo",
                description = content,
                photoUri = draft.photoUri,
                source = EntityRef(EntityKind.MEMO, memoId, aquariumId)
            )
        )
    }

    private suspend fun saveQuickIssueLog(draft: TanksQuickLogDraft, aquariumId: String, now: Instant) {
        val title = draft.issueTitle.trim()
        val issueId = idProvider()

        issueRepository.upsert(
            Issue(
                id = issueId,
                aquariumId = aquariumId,
                title = title,
                status = IssueStatus.OPEN,
                createdAt = now.toString()
            )
        )
        timelineEventRepository.upsert(
            TimelineEvent(
                id = idProvider(),
                aquariumId = aquariumId,
                type = TimelineEventType.ISSUE,
                createdAt = now.toString(),
                title = title,
                description = "Open issue",
                source = EntityRef(EntityKind.ISSUE, issueId, aquariumId)
            )
        )
    }

    private suspend fun saveQuickDosingLog(draft: TanksQuickLogDraft, aquariumId: String, now: Instant) {
        val amountMl = parseTanksQuickPositiveAmount(draft.dosingAmountMl)
            ?: error("Amount must be a positive number.")
        val product = draft.dosingProduct.trim()
        val dosingLogId = idProvider()

        dosingLogRepository.upsert(
            DosingLog(
                id = dosingLogId,
                aquariumId = aquariumId,
                product = product,
                amountMl = amountMl,
                createdAt = now.toString()
            )
        )
        timelineEventRepository.upsert(
            TimelineEvent(
                id = idProvider(),
                aquariumId = aquariumId,
                type = TimelineEventType.DOSING,
                createdAt = now.toString(),
                title = "Dosed $product",
                description = "${formatQuickAmount(amountMl)} ml",
                source = EntityRef(EntityKind.DOSING, dosingLogId, aquariumId)
            )
        )
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
            dosingLogRepository: DosingLogRepository,
            assetRepository: AssetRepository,
            consumableRepository: ConsumableRepository,
            timelineEventRepository: TimelineEventRepository,
            memoRepository: MemoRepository
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
                            dosingLogRepository = dosingLogRepository,
                            assetRepository = assetRepository,
                            consumableRepository = consumableRepository,
                            timelineEventRepository = timelineEventRepository,
                            memoRepository = memoRepository
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
    zoneId: ZoneId,
    selectedMetric: AnalyticMetric = AnalyticMetric.NITRATE,
    selectedChartAquariumId: String? = null
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
            ),
            parameterChart = ParameterChartState(
                selectedMetric = selectedMetric,
                chartData = emptyList(),
                availableAquariums = emptyList()
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

    val chartAquariumId = selectedChartAquariumId ?: aquariums.firstOrNull()?.id
    val chartData = if (chartAquariumId != null) {
        parameterLogs
            .filter { it.aquariumId == chartAquariumId }
            .sortedBy { parseToInstant(it.createdAt, zoneId)?.toEpochMilli() ?: Long.MIN_VALUE }
            .mapNotNull { log ->
                selectedMetric.extractFrom(log.values)?.let { value ->
                    ParameterChartDataPoint(
                        value = value,
                        dayLabel = parseToInstant(log.createdAt, zoneId)
                            ?.atZone(zoneId)?.dayOfMonth?.toString() ?: "?"
                    )
                }
            }
            .takeLast(8)
    } else {
        emptyList()
    }

    val parameterChart = ParameterChartState(
        selectedMetric = selectedMetric,
        selectedAquariumId = chartAquariumId,
        chartData = chartData,
        availableAquariums = aquariums.map { it.id to it.name }
    )

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
        aquariums = aquariumCards,
        parameterChart = parameterChart
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

private fun aquariumRelatedRefs(aquariumId: String, vararg extras: EntityRef): List<EntityRef> =
    listOf(EntityRef(EntityKind.AQUARIUM, aquariumId, aquariumId)) + extras

internal fun validateTanksAquariumDraft(draft: TanksAquariumDraft): String? {
    if (draft.name.trim().isBlank()) return "Name the tank before saving."
    if (parseTanksPositiveDouble(draft.volumeLitersInput) == null) return "Volume must be a positive number."
    if (draft.setupDateInput.trim().isNotBlank() && parseTanksDateTimeInput(draft.setupDateInput, ZoneId.systemDefault()) == null) {
        return "Use a valid setup date/time (for example 2026-04-11 or 2026-04-11 18:30)."
    }
    if (draft.investmentCostInput.isNotBlank() && parseTanksNonNegativeDouble(draft.investmentCostInput) == null) {
        return "Investment cost must be a valid non-negative number."
    }
    return null
}

internal fun validateTanksLivestockDraft(
    draft: TanksLivestockDraft,
    aquariumIds: List<String>,
    zoneId: ZoneId
): String? {
    val aquariumId = draft.aquariumId
    if (aquariumId == null || aquariumIds.none { it == aquariumId }) {
        return "Choose a valid tank for this resident."
    }
    if (draft.name.trim().isBlank()) return "Name the resident before saving."
    if (parseTanksPositiveInt(draft.quantityInput) == null) return "Quantity must be at least 1."
    if (draft.acquiredAtInput.isNotBlank() && parseTanksDateTimeInput(draft.acquiredAtInput, zoneId) == null) {
        return "Use a valid acquired date/time like 2026-04-11 18:30."
    }
    return null
}

internal fun validateTanksAssetDraft(
    draft: TanksAssetDraft,
    aquariumIds: List<String>,
    zoneId: ZoneId
): String? {
    val aquariumId = draft.aquariumId
    if (aquariumId == null || aquariumIds.none { it == aquariumId }) {
        return "Choose a valid tank for this asset."
    }
    if (draft.brandModel.trim().isBlank()) return "Add a brand/model description."
    if (draft.purchasedAtInput.isNotBlank() && parseTanksDateTimeInput(draft.purchasedAtInput, zoneId) == null) {
        return "Use a valid purchase date/time like 2026-04-11 18:30."
    }
    if (draft.priceInput.isNotBlank() && parseTanksNonNegativeDouble(draft.priceInput) == null) {
        return "Price must be a valid non-negative number."
    }
    return null
}

internal fun validateTanksConsumableDraft(
    draft: TanksConsumableDraft,
    aquariumIds: List<String>,
    zoneId: ZoneId
): String? {
    val aquariumId = draft.aquariumId
    if (aquariumId == null || aquariumIds.none { it == aquariumId }) {
        return "Choose a valid tank for this consumable."
    }
    if (draft.name.trim().isBlank()) return "Name the consumable before saving."
    if (parseTanksNonNegativeDouble(draft.remainingInput) == null) {
        return "Remaining amount must be a valid non-negative number."
    }
    if (draft.reorderAtInput.isNotBlank() && parseTanksNonNegativeDouble(draft.reorderAtInput) == null) {
        return "Reorder threshold must be a valid non-negative number."
    }
    if (draft.updatedAtInput.isNotBlank() && parseTanksDateTimeInput(draft.updatedAtInput, zoneId) == null) {
        return "Use a valid last-updated date/time like 2026-04-11 18:30."
    }
    return null
}

internal fun parseTanksPositiveInt(raw: String): Int? {
    val parsed = raw.trim().toIntOrNull() ?: return null
    return parsed.takeIf { it >= 1 }
}

internal fun parseTanksPositiveDouble(raw: String): Double? {
    val parsed = raw.trim().toDoubleOrNull() ?: return null
    return parsed.takeIf { it.isFinite() && it > 0.0 }
}

internal fun parseTanksNonNegativeDouble(raw: String): Double? {
    val value = raw.trim()
    if (value.isEmpty()) return null

    val parsed = value.toDoubleOrNull() ?: return null
    return parsed.takeIf { it.isFinite() && it >= 0.0 }
}

internal fun parseTanksDateTimeInput(raw: String, zoneId: ZoneId): Instant? {
    val value = raw.trim()
    if (value.isEmpty()) return null

    val localDateTimeFormatters = listOf(
        DateTimeFormatter.ISO_LOCAL_DATE_TIME,
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    )

    return runCatching { Instant.parse(value) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()
        ?: localDateTimeFormatters.firstNotNullOfOrNull { formatter ->
            runCatching { LocalDateTime.parse(value, formatter).atZone(zoneId).toInstant() }.getOrNull()
        }
        ?: runCatching { LocalDate.parse(value).atStartOfDay(zoneId).toInstant() }.getOrNull()
}

internal fun formatTanksDateTimeInput(instant: Instant, zoneId: ZoneId): String {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    return formatter.format(instant.atZone(zoneId))
}

private fun LivestockKind.label(): String =
    name.lowercase().replaceFirstChar { it.uppercaseChar() }

private fun AssetCategory.label(): String =
    name.lowercase().replaceFirstChar { it.uppercaseChar() }

private fun formatMeasurementValue(value: Double): String =
    if (value == value.toInt().toDouble()) {
        value.toInt().toString()
    } else {
        String.format("%.2f", value)
    }

private fun formatMoneyValue(value: Double): String =
    if (value == value.toInt().toDouble()) {
        "$${value.toInt()}"
    } else {
        String.format("$%.2f", value)
    }

private fun formatQuickAmount(value: Double): String =
    if (value % 1.0 == 0.0) {
        value.toLong().toString()
    } else {
        value.toString()
    }

internal fun buildQuickLogDueTaskOptions(
    selectedAquariumId: String?,
    taskTemplates: List<TaskTemplate>,
    taskExecutions: List<TaskExecution>,
    aquariums: List<Aquarium>,
    now: Instant,
    zoneId: ZoneId
): List<TanksQuickLogDueTaskOption> {
    val aquariumNameById = aquariums.associate { it.id to it.name }
    val targetAquariumId = selectedAquariumId ?: return emptyList()

    return taskTemplates
        .filter { it.aquariumIds.contains(targetAquariumId) }
        .filter { isTaskDue(it, targetAquariumId, taskExecutions, now, zoneId) }
        .map { task ->
            TanksQuickLogDueTaskOption(
                taskTemplateId = task.id,
                title = task.title,
                aquariumName = aquariumNameById[targetAquariumId] ?: "Unknown tank"
            )
        }
}

internal fun validateTanksQuickLogDraft(draft: TanksQuickLogDraft): String? = when (draft.type) {
    TanksQuickLogType.TASK ->
        if (draft.taskTemplateId.isBlank()) "Choose a due task before saving." else null
    TanksQuickLogType.MEMO ->
        if (draft.memoContent.isBlank()) "Write a memo before saving." else null
    TanksQuickLogType.ISSUE ->
        if (draft.issueTitle.isBlank()) "Name the issue before saving." else null
    TanksQuickLogType.PARAMETER ->
        if (!draft.hasAnyParameterInput()) "Enter at least one parameter value." else null
    TanksQuickLogType.DOSING -> when {
        draft.dosingProduct.isBlank() -> "Name the dosing product before saving."
        parseTanksQuickPositiveAmount(draft.dosingAmountMl) == null -> "Amount must be a positive number."
        else -> null
    }
}

internal fun parseTanksQuickPositiveAmount(raw: String): Double? {
    val number = raw.trim().toDoubleOrNull() ?: return null
    return number.takeIf { it.isFinite() && it > 0.0 }
}

internal fun TanksQuickLogDraft.hasAnyParameterInput(): Boolean =
    listOf(ammonia, nitrite, nitrate, ph, temperatureC, gh, kh, salinity, calcium, alkalinity)
        .any { it.isNotBlank() }

internal fun TanksQuickLogDraft.toWaterParameters(): WaterParameters {
    fun value(raw: String): Double? {
        if (raw.isBlank()) return null
        val number = raw.trim().toDoubleOrNull() ?: return null
        return number.takeIf { it.isFinite() }
    }

    return WaterParameters(
        ammonia = value(ammonia),
        nitrite = value(nitrite),
        nitrate = value(nitrate),
        ph = value(ph),
        temperatureC = value(temperatureC),
        gh = value(gh),
        kh = value(kh),
        salinity = value(salinity),
        calcium = value(calcium),
        alkalinity = value(alkalinity)
    )
}

private fun WaterParameters.summaryLabel(): String =
    listOfNotNull(
        ammonia?.let { "Ammonia ${formatQuickAmount(it)}" },
        nitrite?.let { "Nitrite ${formatQuickAmount(it)}" },
        nitrate?.let { "Nitrate ${formatQuickAmount(it)}" },
        ph?.let { "pH ${formatQuickAmount(it)}" },
        temperatureC?.let { "Temp ${formatQuickAmount(it)} C" },
        gh?.let { "GH ${formatQuickAmount(it)}" },
        kh?.let { "KH ${formatQuickAmount(it)}" },
        salinity?.let { "Salinity ${formatQuickAmount(it)}" },
        calcium?.let { "Calcium ${formatQuickAmount(it)}" },
        alkalinity?.let { "Alkalinity ${formatQuickAmount(it)}" }
    ).joinToString(", ")

internal fun TanksQuickLogDraft.canAttemptSave(): Boolean =
    aquariumId != null && when (type) {
        TanksQuickLogType.TASK -> taskTemplateId.isNotBlank()
        TanksQuickLogType.MEMO -> memoContent.isNotBlank()
        TanksQuickLogType.ISSUE -> issueTitle.isNotBlank()
        TanksQuickLogType.PARAMETER -> hasAnyParameterInput()
        TanksQuickLogType.DOSING -> dosingProduct.isNotBlank() && dosingAmountMl.isNotBlank()
    }

private fun TanksQuickLogDraft.clearedAfterSave(): TanksQuickLogDraft =
    copy(
        taskTemplateId = "",
        taskNote = "",
        memoContent = "",
        photoUri = null,
        issueTitle = "",
        dosingProduct = "",
        dosingAmountMl = "",
        ammonia = "",
        nitrite = "",
        nitrate = "",
        ph = "",
        temperatureC = "",
        gh = "",
        kh = "",
        salinity = "",
        calcium = "",
        alkalinity = ""
    )

private fun TanksQuickLogDraft.copyParameterValue(field: AnalyticMetric, value: String): TanksQuickLogDraft =
    when (field) {
        AnalyticMetric.AMMONIA -> copy(ammonia = value)
        AnalyticMetric.NITRITE -> copy(nitrite = value)
        AnalyticMetric.NITRATE -> copy(nitrate = value)
        AnalyticMetric.PH -> copy(ph = value)
        AnalyticMetric.TEMPERATURE -> copy(temperatureC = value)
        AnalyticMetric.GH -> copy(gh = value)
        AnalyticMetric.KH -> copy(kh = value)
        AnalyticMetric.SALINITY -> copy(salinity = value)
        AnalyticMetric.CALCIUM -> copy(calcium = value)
        AnalyticMetric.ALKALINITY -> copy(alkalinity = value)
    }