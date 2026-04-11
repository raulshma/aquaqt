package com.keepaside.aquapt.feature.entity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.keepaside.aquapt.core.model.Asset
import com.keepaside.aquapt.core.model.Aquarium
import com.keepaside.aquapt.core.model.Consumable
import com.keepaside.aquapt.core.model.DosingLog
import com.keepaside.aquapt.core.model.EntityKind
import com.keepaside.aquapt.core.model.EntityRef
import com.keepaside.aquapt.core.model.Issue
import com.keepaside.aquapt.core.model.IssueStatus
import com.keepaside.aquapt.core.model.Livestock
import com.keepaside.aquapt.core.model.Memo
import com.keepaside.aquapt.core.model.TaskExecution
import com.keepaside.aquapt.core.model.TaskTemplate
import com.keepaside.aquapt.core.model.TimelineEvent
import com.keepaside.aquapt.core.model.TimelineEventType
import com.keepaside.aquapt.core.model.WaterParameterLog
import com.keepaside.aquapt.core.model.WaterParameters
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

data class EntityDetailField(
    val label: String,
    val value: String
)

data class EntityDetailMetric(
    val label: String,
    val value: String
)

data class EntityRelatedEventItem(
    val id: String,
    val title: String,
    val supportingText: String
)

data class EntityIssueEditorState(
    val id: String,
    val title: String,
    val aquariumId: String,
    val status: IssueStatus,
    val resolutionNote: String?
)

data class EntityMemoEditorState(
    val id: String,
    val aquariumId: String,
    val content: String,
    val photoUri: String?
)

data class EntityDetailUiState(
    val isLoading: Boolean = true,
    val isActionInProgress: Boolean = false,
    val isNotFound: Boolean = false,
    val headline: String = "Loading entity details...",
    val kindLabel: String = "Entity",
    val entityId: String = "",
    val title: String = "",
    val subtitle: String? = null,
    val aquariumName: String? = null,
    val photoUri: String? = null,
    val metrics: List<EntityDetailMetric> = emptyList(),
    val fields: List<EntityDetailField> = emptyList(),
    val relatedEvents: List<EntityRelatedEventItem> = emptyList(),
    val issueEditor: EntityIssueEditorState? = null,
    val memoEditor: EntityMemoEditorState? = null,
    val statusMessage: String? = null
)

private data class ResolvedEntityDetail(
    val title: String,
    val subtitle: String? = null,
    val aquariumId: String? = null,
    val photoUri: String? = null,
    val metrics: List<EntityDetailMetric> = emptyList(),
    val fields: List<EntityDetailField> = emptyList(),
    val issueEditor: EntityIssueEditorState? = null,
    val memoEditor: EntityMemoEditorState? = null
)

class EntityDetailViewModel(
    private val kind: EntityKind?,
    private val entityId: String,
    private val routeAquariumId: String?,
    private val aquariumRepository: AquariumRepository,
    private val taskTemplateRepository: TaskTemplateRepository,
    private val taskExecutionRepository: TaskExecutionRepository,
    private val livestockRepository: LivestockRepository,
    private val assetRepository: AssetRepository,
    private val consumableRepository: ConsumableRepository,
    private val issueRepository: IssueRepository,
    private val memoRepository: MemoRepository,
    private val dosingLogRepository: DosingLogRepository,
    private val waterParameterLogRepository: WaterParameterLogRepository,
    private val timelineEventRepository: TimelineEventRepository,
    private val nowProvider: () -> Instant = { Instant.now() },
    private val idProvider: () -> String = { UUID.randomUUID().toString() },
    private val zoneId: ZoneId = ZoneId.systemDefault()
) : ViewModel() {

    private data class EntityCoreData(
        val aquariums: List<Aquarium>,
        val taskTemplates: List<TaskTemplate>,
        val taskExecutions: List<TaskExecution>
    )

    private data class EntityInventoryData(
        val livestock: List<Livestock>,
        val assets: List<Asset>,
        val consumables: List<Consumable>
    )

    private data class EntityLogData(
        val issues: List<Issue>,
        val memos: List<Memo>,
        val dosingLogs: List<DosingLog>,
        val parameterLogs: List<WaterParameterLog>,
        val timelineEvents: List<TimelineEvent>
    )

    private data class EntityActionState(
        val isBusy: Boolean = false,
        val statusMessage: String? = null
    )

    private val actionState = MutableStateFlow(EntityActionState())

    private val _uiState = MutableStateFlow(EntityDetailUiState())
    val uiState: StateFlow<EntityDetailUiState> = _uiState.asStateFlow()

    init {
        observeEntityDetails()
    }

    private fun observeEntityDetails() {
        val coreDataFlow = combine(
            aquariumRepository.getAll(),
            taskTemplateRepository.getAll(),
            taskExecutionRepository.getAll()
        ) { aquariums, taskTemplates, taskExecutions ->
            EntityCoreData(
                aquariums = aquariums,
                taskTemplates = taskTemplates,
                taskExecutions = taskExecutions
            )
        }

        val inventoryDataFlow = combine(
            livestockRepository.getAll(),
            assetRepository.getAll(),
            consumableRepository.getAll()
        ) { livestock, assets, consumables ->
            EntityInventoryData(
                livestock = livestock,
                assets = assets,
                consumables = consumables
            )
        }

        val logDataFlow = combine(
            issueRepository.getAll(),
            memoRepository.getAll(),
            dosingLogRepository.getAll(),
            waterParameterLogRepository.getAll(),
            timelineEventRepository.getAll()
        ) { issues, memos, dosingLogs, parameterLogs, timelineEvents ->
            EntityLogData(
                issues = issues,
                memos = memos,
                dosingLogs = dosingLogs,
                parameterLogs = parameterLogs,
                timelineEvents = timelineEvents
            )
        }

        viewModelScope.launch {
            combine(
                coreDataFlow,
                inventoryDataFlow,
                logDataFlow,
                actionState
            ) { coreData, inventoryData, logData, action ->
                val assembled = assembleEntityDetailUiState(
                    kind = kind,
                    entityId = entityId,
                    routeAquariumId = routeAquariumId,
                    aquariums = coreData.aquariums,
                    taskTemplates = coreData.taskTemplates,
                    taskExecutions = coreData.taskExecutions,
                    livestock = inventoryData.livestock,
                    assets = inventoryData.assets,
                    consumables = inventoryData.consumables,
                    issues = logData.issues,
                    memos = logData.memos,
                    dosingLogs = logData.dosingLogs,
                    parameterLogs = logData.parameterLogs,
                    timelineEvents = logData.timelineEvents,
                    zoneId = zoneId
                )

                assembled.copy(
                    isActionInProgress = action.isBusy,
                    statusMessage = action.statusMessage ?: assembled.statusMessage
                )
            }.collect { next ->
                _uiState.update { next.copy(isLoading = false) }
            }
        }
    }

    fun saveIssueUpdate(status: IssueStatus, resolutionNoteInput: String) {
        if (kind != EntityKind.ISSUE) {
            setActionStatus("Issue actions are only available for issue details.")
            return
        }

        val normalizedNote = resolutionNoteInput.trim().ifBlank { null }
        withAction(
            errorFallback = "Unable to save issue changes."
        ) {
            val issue = issueRepository.getById(entityId.trim())
                ?: error("Issue no longer exists.")

            val updatedIssue = issue.copy(
                status = status,
                resolutionNote = normalizedNote
            )

            if (updatedIssue == issue) {
                setActionStatus("No issue changes to save.")
                return@withAction
            }

            issueRepository.upsert(updatedIssue)

            timelineEventRepository.upsert(
                TimelineEvent(
                    id = idProvider(),
                    aquariumId = updatedIssue.aquariumId,
                    type = TimelineEventType.ISSUE,
                    createdAt = nowProvider().toString(),
                    title = updatedIssue.title,
                    description = buildIssueUpdateDescription(issue, updatedIssue),
                    source = EntityRef(EntityKind.ISSUE, updatedIssue.id, updatedIssue.aquariumId),
                    related = aquariumRelatedRefs(updatedIssue.aquariumId)
                )
            )

            setActionStatus("Issue updated: ${updatedIssue.status.label()}.")
        }
    }

    fun saveMemoContent(contentInput: String) {
        if (kind != EntityKind.MEMO) {
            setActionStatus("Memo actions are only available for memo details.")
            return
        }

        val content = contentInput.trim()
        if (content.isBlank()) {
            setActionStatus("Write memo content before saving.")
            return
        }

        withAction(
            errorFallback = "Unable to save memo changes."
        ) {
            val memo = memoRepository.getById(entityId.trim())
                ?: error("Memo no longer exists.")

            if (memo.content.trim() == content) {
                setActionStatus("No memo content changes to save.")
                return@withAction
            }

            val updatedMemo = memo.copy(content = content)
            memoRepository.upsert(updatedMemo)

            timelineEventRepository.upsert(
                TimelineEvent(
                    id = idProvider(),
                    aquariumId = updatedMemo.aquariumId,
                    type = TimelineEventType.MEMO,
                    createdAt = nowProvider().toString(),
                    title = "Memo updated",
                    description = content,
                    photoUri = updatedMemo.photoUri,
                    source = EntityRef(EntityKind.MEMO, updatedMemo.id, updatedMemo.aquariumId),
                    related = aquariumRelatedRefs(updatedMemo.aquariumId)
                )
            )

            setActionStatus("Memo updated.")
        }
    }

    fun deleteCurrentEntity() {
        when (kind) {
            EntityKind.ISSUE -> deleteIssue()
            EntityKind.MEMO -> deleteMemo()
            else -> setActionStatus("Delete is not available for this entity type yet.")
        }
    }

    private fun deleteIssue() {
        withAction(
            errorFallback = "Unable to delete issue."
        ) {
            val issue = issueRepository.getById(entityId.trim())
                ?: error("Issue no longer exists.")

            issueRepository.deleteById(issue.id)

            timelineEventRepository.upsert(
                TimelineEvent(
                    id = idProvider(),
                    aquariumId = issue.aquariumId,
                    type = TimelineEventType.ISSUE,
                    createdAt = nowProvider().toString(),
                    title = "Deleted issue",
                    description = issue.title,
                    source = EntityRef(EntityKind.ISSUE, issue.id, issue.aquariumId),
                    related = aquariumRelatedRefs(issue.aquariumId)
                )
            )

            setActionStatus("Issue deleted.")
        }
    }

    private fun deleteMemo() {
        withAction(
            errorFallback = "Unable to delete memo."
        ) {
            val memo = memoRepository.getById(entityId.trim())
                ?: error("Memo no longer exists.")

            memoRepository.deleteById(memo.id)

            timelineEventRepository.upsert(
                TimelineEvent(
                    id = idProvider(),
                    aquariumId = memo.aquariumId,
                    type = TimelineEventType.MEMO,
                    createdAt = nowProvider().toString(),
                    title = "Deleted memo",
                    description = memo.content.trim().take(160).ifBlank { null },
                    photoUri = memo.photoUri,
                    source = EntityRef(EntityKind.MEMO, memo.id, memo.aquariumId),
                    related = aquariumRelatedRefs(memo.aquariumId)
                )
            )

            setActionStatus("Memo deleted.")
        }
    }

    private fun withAction(
        errorFallback: String,
        action: suspend () -> Unit
    ) {
        viewModelScope.launch {
            actionState.update { it.copy(isBusy = true) }

            runCatching {
                action()
            }.onFailure { error ->
                setActionStatus(error.message ?: errorFallback)
            }

            actionState.update { it.copy(isBusy = false) }
        }
    }

    private fun setActionStatus(message: String) {
        actionState.update { it.copy(statusMessage = message) }
    }

    companion object {
        fun factory(
            kind: EntityKind?,
            entityId: String,
            aquariumId: String?,
            aquariumRepository: AquariumRepository,
            taskTemplateRepository: TaskTemplateRepository,
            taskExecutionRepository: TaskExecutionRepository,
            livestockRepository: LivestockRepository,
            assetRepository: AssetRepository,
            consumableRepository: ConsumableRepository,
            issueRepository: IssueRepository,
            memoRepository: MemoRepository,
            dosingLogRepository: DosingLogRepository,
            waterParameterLogRepository: WaterParameterLogRepository,
            timelineEventRepository: TimelineEventRepository
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(EntityDetailViewModel::class.java)) {
                        return EntityDetailViewModel(
                            kind = kind,
                            entityId = entityId,
                            routeAquariumId = aquariumId,
                            aquariumRepository = aquariumRepository,
                            taskTemplateRepository = taskTemplateRepository,
                            taskExecutionRepository = taskExecutionRepository,
                            livestockRepository = livestockRepository,
                            assetRepository = assetRepository,
                            consumableRepository = consumableRepository,
                            issueRepository = issueRepository,
                            memoRepository = memoRepository,
                            dosingLogRepository = dosingLogRepository,
                            waterParameterLogRepository = waterParameterLogRepository,
                            timelineEventRepository = timelineEventRepository
                        ) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
    }
}

internal fun assembleEntityDetailUiState(
    kind: EntityKind?,
    entityId: String,
    routeAquariumId: String?,
    aquariums: List<Aquarium>,
    taskTemplates: List<TaskTemplate>,
    taskExecutions: List<TaskExecution>,
    livestock: List<Livestock>,
    assets: List<Asset>,
    consumables: List<Consumable>,
    issues: List<Issue>,
    memos: List<Memo>,
    dosingLogs: List<DosingLog>,
    parameterLogs: List<WaterParameterLog>,
    timelineEvents: List<TimelineEvent>,
    zoneId: ZoneId
): EntityDetailUiState {
    val trimmedEntityId = entityId.trim()
    if (kind == null || trimmedEntityId.isBlank()) {
        return EntityDetailUiState(
            isNotFound = true,
            headline = "This deep link is missing entity details.",
            kindLabel = kind?.label() ?: "Entity",
            entityId = trimmedEntityId,
            statusMessage = "Open an entity from the timeline details to continue."
        )
    }

    val aquariumNameById = aquariums.associate { it.id to it.name }
    val livestockById = livestock.associateBy { it.id }

    val resolved = when (kind) {
        EntityKind.AQUARIUM -> {
            val aquarium = aquariums.firstOrNull { it.id == trimmedEntityId } ?: return missingEntityState(
                kind = kind,
                entityId = trimmedEntityId,
                routeAquariumId = routeAquariumId,
                aquariumNameById = aquariumNameById
            )

            val residentCount = livestock.count { it.aquariumId == aquarium.id }
            val taskCount = taskTemplates.count { task -> task.aquariumIds.contains(aquarium.id) }
            val openIssueCount = issues.count {
                it.aquariumId == aquarium.id && it.status != IssueStatus.RESOLVED
            }

            ResolvedEntityDetail(
                title = aquarium.name,
                subtitle = aquarium.waterType.label(),
                aquariumId = aquarium.id,
                photoUri = aquarium.photoUri,
                metrics = listOf(
                    EntityDetailMetric("Residents", residentCount.toString()),
                    EntityDetailMetric("Tasks", taskCount.toString()),
                    EntityDetailMetric("Open issues", openIssueCount.toString())
                ),
                fields = buildList {
                    add(EntityDetailField("Volume", "${formatAmount(aquarium.volumeLiters)} L"))
                    aquarium.dimensions.trim().takeIf { it.isNotEmpty() }?.let {
                        add(EntityDetailField("Dimensions", it))
                    }
                    aquarium.setupDate.trim().takeIf { it.isNotEmpty() }?.let {
                        add(EntityDetailField("Setup date", it))
                    }
                    aquarium.investmentCost?.let {
                        add(EntityDetailField("Investment", formatAmount(it)))
                    }
                }
            )
        }

        EntityKind.TASK -> {
            val task = taskTemplates.firstOrNull { it.id == trimmedEntityId } ?: return missingEntityState(
                kind = kind,
                entityId = trimmedEntityId,
                routeAquariumId = routeAquariumId,
                aquariumNameById = aquariumNameById
            )

            val taskExecutionsForTemplate = taskExecutions
                .filter { it.taskTemplateId == task.id }

            val latestCompletion = taskExecutionsForTemplate
                .maxByOrNull { parseToInstant(it.completedAt, zoneId)?.toEpochMilli() ?: Long.MIN_VALUE }
                ?.let { formatDateTime(it.completedAt, zoneId) }

            val assignedAquariumNames = task.aquariumIds
                .mapNotNull { aquariumNameById[it] }
                .ifEmpty {
                    routeAquariumId
                        ?.let { aquariumNameById[it] ?: "Unknown tank" }
                        ?.let { listOf(it) }
                        .orEmpty()
                }

            ResolvedEntityDetail(
                title = task.title,
                subtitle = task.frequency.getLabel(),
                aquariumId = task.aquariumIds.firstOrNull() ?: routeAquariumId,
                metrics = listOf(
                    EntityDetailMetric("Completions", taskExecutionsForTemplate.size.toString()),
                    EntityDetailMetric("Assigned tanks", task.aquariumIds.size.toString())
                ),
                fields = buildList {
                    task.category?.let {
                        add(EntityDetailField("Category", it.label()))
                    }
                    add(EntityDetailField("Frequency", task.frequency.getLabel()))
                    task.startDate?.takeIf { it.isNotBlank() }?.let {
                        add(EntityDetailField("Start date", it))
                    }
                    task.timesPerDay?.let {
                        add(EntityDetailField("Times per day", it.toString()))
                    }
                    task.reminderHours.takeIf { it.isNotEmpty() }?.let {
                        add(EntityDetailField("Reminder hours", it.joinToString(", ")))
                    }
                    task.reminderGroupId?.takeIf { it.isNotBlank() }?.let {
                        add(EntityDetailField("Reminder group", it))
                    }
                    task.description?.takeIf { it.isNotBlank() }?.let {
                        add(EntityDetailField("Description", it))
                    }
                    task.livestockId
                        ?.let { livestockById[it] }
                        ?.let { resident ->
                            add(EntityDetailField("Target resident", resident.name.ifBlank { resident.species.ifBlank { "Resident" } }))
                        }
                    assignedAquariumNames.takeIf { it.isNotEmpty() }?.let {
                        add(EntityDetailField("Tanks", it.joinToString(", ")))
                    }
                    latestCompletion?.let {
                        add(EntityDetailField("Latest completion", it))
                    }
                }
            )
        }

        EntityKind.LIVESTOCK -> {
            val resident = livestock.firstOrNull { it.id == trimmedEntityId } ?: return missingEntityState(
                kind = kind,
                entityId = trimmedEntityId,
                routeAquariumId = routeAquariumId,
                aquariumNameById = aquariumNameById
            )

            val offspringCount = livestock.count { it.parentId == resident.id }
            val feedingTaskCount = taskTemplates.count { it.livestockId == resident.id }

            ResolvedEntityDetail(
                title = resident.name.ifBlank { resident.species.ifBlank { "Resident" } },
                subtitle = resident.kind.label(),
                aquariumId = resident.aquariumId,
                photoUri = resident.photoUri,
                metrics = listOf(
                    EntityDetailMetric("Quantity", resident.quantity.toString()),
                    EntityDetailMetric("Offspring", offspringCount.toString()),
                    EntityDetailMetric("Feeding tasks", feedingTaskCount.toString())
                ),
                fields = buildList {
                    resident.species.takeIf { it.isNotBlank() }?.let {
                        add(EntityDetailField("Species", it))
                    }
                    add(EntityDetailField("Status", resident.status.label()))
                    resident.acquiredAt.takeIf { it.isNotBlank() }?.let {
                        add(EntityDetailField("Acquired", it))
                    }
                    resident.dietaryNotes?.takeIf { it.isNotBlank() }?.let {
                        add(EntityDetailField("Dietary notes", it))
                    }
                    resident.parentId
                        ?.let { livestockById[it] }
                        ?.let { parent ->
                            add(EntityDetailField("Parent", parent.name.ifBlank { parent.species.ifBlank { "Resident" } }))
                        }
                }
            )
        }

        EntityKind.ASSET -> {
            val asset = assets.firstOrNull { it.id == trimmedEntityId } ?: return missingEntityState(
                kind = kind,
                entityId = trimmedEntityId,
                routeAquariumId = routeAquariumId,
                aquariumNameById = aquariumNameById
            )

            ResolvedEntityDetail(
                title = asset.brandModel.ifBlank { "${asset.category.label()} asset" },
                subtitle = asset.category.label(),
                aquariumId = asset.aquariumId,
                photoUri = asset.photoUri,
                metrics = listOf(
                    EntityDetailMetric("Maintenance links", asset.maintenanceTaskTemplateIds.size.toString())
                ),
                fields = buildList {
                    asset.purchasedAt?.takeIf { it.isNotBlank() }?.let {
                        add(EntityDetailField("Purchased", it))
                    }
                    asset.price?.let {
                        add(EntityDetailField("Price", formatAmount(it)))
                    }
                }
            )
        }

        EntityKind.CONSUMABLE -> {
            val consumable = consumables.firstOrNull { it.id == trimmedEntityId } ?: return missingEntityState(
                kind = kind,
                entityId = trimmedEntityId,
                routeAquariumId = routeAquariumId,
                aquariumNameById = aquariumNameById
            )

            ResolvedEntityDetail(
                title = consumable.name,
                subtitle = consumable.unit.label(),
                aquariumId = consumable.aquariumId,
                photoUri = consumable.photoUri,
                metrics = listOf(
                    EntityDetailMetric(
                        "Remaining",
                        "${formatAmount(consumable.remaining)} ${consumable.unit.name.lowercase()}"
                    )
                ),
                fields = buildList {
                    consumable.reorderAt?.let {
                        add(EntityDetailField("Reorder at", "${formatAmount(it)} ${consumable.unit.name.lowercase()}"))
                    }
                    consumable.updatedAt.takeIf { it.isNotBlank() }?.let {
                        add(EntityDetailField("Updated", formatDateTime(it, zoneId)))
                    }
                }
            )
        }

        EntityKind.ISSUE -> {
            val issue = issues.firstOrNull { it.id == trimmedEntityId } ?: return missingEntityState(
                kind = kind,
                entityId = trimmedEntityId,
                routeAquariumId = routeAquariumId,
                aquariumNameById = aquariumNameById
            )

            ResolvedEntityDetail(
                title = issue.title,
                subtitle = issue.status.label(),
                aquariumId = issue.aquariumId,
                fields = buildList {
                    add(EntityDetailField("Created", formatDateTime(issue.createdAt, zoneId)))
                    issue.resolutionNote?.takeIf { it.isNotBlank() }?.let {
                        add(EntityDetailField("Resolution note", it))
                    }
                },
                issueEditor = EntityIssueEditorState(
                    id = issue.id,
                    title = issue.title,
                    aquariumId = issue.aquariumId,
                    status = issue.status,
                    resolutionNote = issue.resolutionNote
                )
            )
        }

        EntityKind.MEMO -> {
            val memo = memos.firstOrNull { it.id == trimmedEntityId } ?: return missingEntityState(
                kind = kind,
                entityId = trimmedEntityId,
                routeAquariumId = routeAquariumId,
                aquariumNameById = aquariumNameById
            )

            val content = memo.content.trim()

            ResolvedEntityDetail(
                title = content.takeIf { it.isNotBlank() }?.take(56)?.trimEnd()?.let {
                    if (content.length > 56) "$it…" else it
                } ?: "Memo",
                subtitle = "Memo",
                aquariumId = memo.aquariumId,
                photoUri = memo.photoUri,
                metrics = listOf(
                    EntityDetailMetric("Characters", content.length.toString())
                ),
                fields = buildList {
                    add(EntityDetailField("Created", formatDateTime(memo.createdAt, zoneId)))
                    if (content.isNotBlank()) {
                        add(EntityDetailField("Content", content))
                    }
                },
                memoEditor = EntityMemoEditorState(
                    id = memo.id,
                    aquariumId = memo.aquariumId,
                    content = memo.content,
                    photoUri = memo.photoUri
                )
            )
        }

        EntityKind.DOSING -> {
            val dosing = dosingLogs.firstOrNull { it.id == trimmedEntityId } ?: return missingEntityState(
                kind = kind,
                entityId = trimmedEntityId,
                routeAquariumId = routeAquariumId,
                aquariumNameById = aquariumNameById
            )

            ResolvedEntityDetail(
                title = dosing.product,
                subtitle = "${formatAmount(dosing.amountMl)} ml",
                aquariumId = dosing.aquariumId,
                fields = buildList {
                    add(EntityDetailField("Logged", formatDateTime(dosing.createdAt, zoneId)))
                    dosing.note?.takeIf { it.isNotBlank() }?.let {
                        add(EntityDetailField("Note", it))
                    }
                }
            )
        }

        EntityKind.PARAMETER_LOG -> {
            val parameterLog = parameterLogs.firstOrNull { it.id == trimmedEntityId }
                ?: return missingEntityState(
                    kind = kind,
                    entityId = trimmedEntityId,
                    routeAquariumId = routeAquariumId,
                    aquariumNameById = aquariumNameById
                )

            val parameterFields = parameterLog.values.toFieldList()

            ResolvedEntityDetail(
                title = "Water parameters",
                subtitle = formatDateTime(parameterLog.createdAt, zoneId),
                aquariumId = parameterLog.aquariumId,
                metrics = listOf(
                    EntityDetailMetric("Values", parameterFields.size.toString())
                ),
                fields = parameterFields
            )
        }
    }

    val matchingEvents = timelineEvents
        .filter { event ->
            if (kind == EntityKind.AQUARIUM) {
                event.aquariumId == trimmedEntityId
            } else {
                event.source.matchesEntity(kind, trimmedEntityId) ||
                    event.related.any { it.matchesEntity(kind, trimmedEntityId) }
            }
        }

    val aquariumId = resolved.aquariumId ?: routeAquariumId
    val aquariumName = aquariumId?.let { aquariumNameById[it] ?: "Unknown tank" }

    val relatedEvents = matchingEvents
        .sortedWith(compareByDescending<TimelineEvent> {
            parseToInstant(it.createdAt, zoneId)?.toEpochMilli() ?: Long.MIN_VALUE
        }.thenByDescending { it.createdAt })
        .take(8)
        .map { event ->
            val eventAquariumName = aquariumNameById[event.aquariumId] ?: "Unknown tank"
            EntityRelatedEventItem(
                id = event.id,
                title = event.title,
                supportingText = "${event.type.label()} • ${formatDateTime(event.createdAt, zoneId)} • $eventAquariumName"
            )
        }

    return EntityDetailUiState(
        isNotFound = false,
        headline = "${kind.label()} details",
        kindLabel = kind.label(),
        entityId = trimmedEntityId,
        title = resolved.title,
        subtitle = resolved.subtitle,
        aquariumName = aquariumName,
        photoUri = resolved.photoUri,
        metrics = resolved.metrics + EntityDetailMetric("Linked events", matchingEvents.size.toString()),
        fields = resolved.fields,
        relatedEvents = relatedEvents,
        issueEditor = resolved.issueEditor,
        memoEditor = resolved.memoEditor,
        statusMessage = if (matchingEvents.isEmpty()) {
            "No linked timeline entries yet."
        } else {
            null
        }
    )
}

private fun missingEntityState(
    kind: EntityKind,
    entityId: String,
    routeAquariumId: String?,
    aquariumNameById: Map<String, String>
): EntityDetailUiState {
    val aquariumName = routeAquariumId?.let { aquariumNameById[it] ?: "Unknown tank" }
    return EntityDetailUiState(
        isNotFound = true,
        headline = "${kind.label()} not found.",
        kindLabel = kind.label(),
        entityId = entityId,
        aquariumName = aquariumName,
        statusMessage = "The linked ${kind.label().lowercase()} may have been deleted."
    )
}

private fun EntityRef?.matchesEntity(kind: EntityKind, id: String): Boolean {
    return this?.kind == kind && this.id == id
}

private fun WaterParameters.toFieldList(): List<EntityDetailField> = buildList {
    ammonia?.let { add(EntityDetailField("Ammonia", formatAmount(it))) }
    nitrite?.let { add(EntityDetailField("Nitrite", formatAmount(it))) }
    nitrate?.let { add(EntityDetailField("Nitrate", formatAmount(it))) }
    ph?.let { add(EntityDetailField("pH", formatAmount(it))) }
    temperatureC?.let { add(EntityDetailField("Temperature C", formatAmount(it))) }
    gh?.let { add(EntityDetailField("GH", formatAmount(it))) }
    kh?.let { add(EntityDetailField("KH", formatAmount(it))) }
    salinity?.let { add(EntityDetailField("Salinity", formatAmount(it))) }
    calcium?.let { add(EntityDetailField("Calcium", formatAmount(it))) }
    alkalinity?.let { add(EntityDetailField("Alkalinity", formatAmount(it))) }
}

private fun EntityKind.label(): String =
    name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercaseChar() }

private fun Enum<*>.label(): String =
    name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercaseChar() }

internal fun buildIssueUpdateDescription(previous: Issue, updated: Issue): String {
    val parts = mutableListOf<String>()
    if (previous.status != updated.status) {
        parts += "Status ${previous.status.label()} → ${updated.status.label()}"
    }
    if (previous.resolutionNote != updated.resolutionNote) {
        if (updated.resolutionNote.isNullOrBlank()) {
            parts += "Resolution note cleared"
        } else {
            parts += "Resolution note updated"
        }
    }

    return parts.joinToString(" • ").ifBlank { "Issue updated" }
}

private fun aquariumRelatedRefs(aquariumId: String, vararg extras: EntityRef): List<EntityRef> =
    listOf(EntityRef(EntityKind.AQUARIUM, aquariumId, aquariumId)) + extras

private fun formatAmount(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

private fun formatDateTime(raw: String, zoneId: ZoneId): String {
    val instant = parseToInstant(raw, zoneId) ?: return raw
    return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(instant.atZone(zoneId))
}

private fun parseToInstant(raw: String, zoneId: ZoneId): Instant? {
    val value = raw.trim()
    if (value.isEmpty()) return null

    return runCatching { Instant.parse(value) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()
        ?: runCatching { LocalDateTime.parse(value).atZone(zoneId).toInstant() }.getOrNull()
        ?: runCatching { LocalDate.parse(value).atStartOfDay(zoneId).toInstant() }.getOrNull()
}