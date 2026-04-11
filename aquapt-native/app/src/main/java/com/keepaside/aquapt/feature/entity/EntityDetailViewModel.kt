package com.keepaside.aquapt.feature.entity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.keepaside.aquapt.core.model.Asset
import com.keepaside.aquapt.core.model.AssetCategory
import com.keepaside.aquapt.core.model.Aquarium
import com.keepaside.aquapt.core.model.Consumable
import com.keepaside.aquapt.core.model.ConsumableUnit
import com.keepaside.aquapt.core.model.DosingLog
import com.keepaside.aquapt.core.model.EntityKind
import com.keepaside.aquapt.core.model.EntityRef
import com.keepaside.aquapt.core.model.Issue
import com.keepaside.aquapt.core.model.IssueStatus
import com.keepaside.aquapt.core.model.Livestock
import com.keepaside.aquapt.core.model.LivestockStatus
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
import kotlinx.coroutines.flow.first
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

data class EntityRelatedPhotoItem(
    val id: String,
    val uri: String,
    val title: String,
    val supportingText: String
)

data class EntityTaskExecutionItem(
    val id: String,
    val aquariumId: String,
    val aquariumName: String,
    val completedAtLabel: String,
    val note: String?
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

data class EntityAssetEditorState(
    val id: String,
    val aquariumId: String,
    val category: AssetCategory,
    val brandModel: String,
    val purchasedAtInput: String,
    val priceInput: String,
    val photoUri: String?
)

data class EntityConsumableEditorState(
    val id: String,
    val aquariumId: String,
    val name: String,
    val unit: ConsumableUnit,
    val remainingInput: String,
    val reorderAtInput: String,
    val photoUri: String?
)

data class EntityLivestockEditorState(
    val id: String,
    val aquariumId: String,
    val name: String,
    val species: String,
    val quantityInput: String,
    val status: LivestockStatus,
    val dietaryNotes: String,
    val photoUri: String?
)

private data class EntityLivestockDeleteImpact(
    val childUpdates: List<Livestock>,
    val taskTemplateUpdates: List<TaskTemplate>,
    val orphanedOffspringCount: Int,
    val detachedTaskCount: Int
)

data class EntityDetailUiState(
    val isLoading: Boolean = true,
    val isActionInProgress: Boolean = false,
    val isNotFound: Boolean = false,
    val headline: String = "Loading entity details...",
    val kindLabel: String = "Entity",
    val entityId: String = "",
    val aquariumId: String? = null,
    val title: String = "",
    val subtitle: String? = null,
    val aquariumName: String? = null,
    val photoUri: String? = null,
    val metrics: List<EntityDetailMetric> = emptyList(),
    val fields: List<EntityDetailField> = emptyList(),
    val taskExecutionHistory: List<EntityTaskExecutionItem> = emptyList(),
    val relatedPhotos: List<EntityRelatedPhotoItem> = emptyList(),
    val relatedEvents: List<EntityRelatedEventItem> = emptyList(),
    val livestockEditor: EntityLivestockEditorState? = null,
    val assetEditor: EntityAssetEditorState? = null,
    val consumableEditor: EntityConsumableEditorState? = null,
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
    val taskExecutionHistory: List<EntityTaskExecutionItem> = emptyList(),
    val livestockEditor: EntityLivestockEditorState? = null,
    val assetEditor: EntityAssetEditorState? = null,
    val consumableEditor: EntityConsumableEditorState? = null,
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

    fun saveAssetDetails(
        category: AssetCategory,
        brandModelInput: String,
        purchasedAtInput: String,
        priceInput: String
    ) {
        if (kind != EntityKind.ASSET) {
            setActionStatus("Asset actions are only available for asset details.")
            return
        }

        val normalizedBrandModel = brandModelInput.trim()
        val normalizedPurchasedAt = purchasedAtInput.trim()
        val normalizedPriceInput = priceInput.trim()

        val purchasedAt = if (normalizedPurchasedAt.isEmpty()) {
            null
        } else {
            parseToInstant(normalizedPurchasedAt, zoneId)?.toString()
        }

        if (normalizedPurchasedAt.isNotEmpty() && purchasedAt == null) {
            setActionStatus("Use a valid purchase date like 2026-04-11 or 2026-04-11 14:30.")
            return
        }

        val price = if (normalizedPriceInput.isEmpty()) {
            null
        } else {
            parseNonNegativeAmountInput(normalizedPriceInput)
        }

        if (normalizedPriceInput.isNotEmpty() && price == null) {
            setActionStatus("Price must be a number greater than or equal to 0.")
            return
        }

        withAction(
            errorFallback = "Unable to save asset changes."
        ) {
            val asset = assetRepository.getById(entityId.trim())
                ?: error("Asset no longer exists.")

            val updatedAsset = asset.copy(
                category = category,
                brandModel = normalizedBrandModel,
                purchasedAt = purchasedAt,
                price = price
            )

            if (!hasAssetChanges(asset, updatedAsset)) {
                setActionStatus("No asset changes to save.")
                return@withAction
            }

            assetRepository.upsert(updatedAsset)

            timelineEventRepository.upsert(
                TimelineEvent(
                    id = idProvider(),
                    aquariumId = updatedAsset.aquariumId,
                    type = TimelineEventType.ASSET,
                    createdAt = nowProvider().toString(),
                    title = "Asset updated",
                    description = buildAssetUpdateDescription(asset, updatedAsset),
                    photoUri = updatedAsset.photoUri,
                    source = EntityRef(EntityKind.ASSET, updatedAsset.id, updatedAsset.aquariumId),
                    related = aquariumRelatedRefs(updatedAsset.aquariumId)
                )
            )

            setActionStatus("Asset updated.")
        }
    }

    fun saveConsumableDetails(
        nameInput: String,
        unit: ConsumableUnit,
        remainingInput: String,
        reorderAtInput: String
    ) {
        if (kind != EntityKind.CONSUMABLE) {
            setActionStatus("Consumable actions are only available for consumable details.")
            return
        }

        val name = nameInput.trim()
        if (name.isEmpty()) {
            setActionStatus("Name the consumable before saving.")
            return
        }

        val normalizedRemainingInput = remainingInput.trim()
        val remaining = parseNonNegativeAmountInput(normalizedRemainingInput)
        if (remaining == null) {
            setActionStatus("Remaining amount must be a number greater than or equal to 0.")
            return
        }

        val normalizedReorderInput = reorderAtInput.trim()
        val reorderAt = if (normalizedReorderInput.isEmpty()) {
            null
        } else {
            parseNonNegativeAmountInput(normalizedReorderInput)
        }

        if (normalizedReorderInput.isNotEmpty() && reorderAt == null) {
            setActionStatus("Reorder threshold must be a number greater than or equal to 0.")
            return
        }

        withAction(
            errorFallback = "Unable to save consumable changes."
        ) {
            val consumable = consumableRepository.getById(entityId.trim())
                ?: error("Consumable no longer exists.")

            val updatedConsumable = consumable.copy(
                name = name,
                unit = unit,
                remaining = remaining,
                reorderAt = reorderAt,
                updatedAt = nowProvider().toString()
            )

            if (!hasConsumableChanges(consumable, updatedConsumable)) {
                setActionStatus("No consumable changes to save.")
                return@withAction
            }

            consumableRepository.upsert(updatedConsumable)

            timelineEventRepository.upsert(
                TimelineEvent(
                    id = idProvider(),
                    aquariumId = updatedConsumable.aquariumId,
                    type = TimelineEventType.CONSUMABLE,
                    createdAt = nowProvider().toString(),
                    title = "Consumable updated",
                    description = buildConsumableUpdateDescription(consumable, updatedConsumable),
                    photoUri = updatedConsumable.photoUri,
                    source = EntityRef(EntityKind.CONSUMABLE, updatedConsumable.id, updatedConsumable.aquariumId),
                    related = aquariumRelatedRefs(updatedConsumable.aquariumId)
                )
            )

            setActionStatus("Consumable inventory updated.")
        }
    }

    fun saveLivestockDetails(
        nameInput: String,
        speciesInput: String,
        quantityInput: String,
        status: LivestockStatus,
        dietaryNotesInput: String
    ) {
        if (kind != EntityKind.LIVESTOCK) {
            setActionStatus("Livestock actions are only available for resident details.")
            return
        }

        val name = nameInput.trim()
        if (name.isBlank()) {
            setActionStatus("Name the resident before saving.")
            return
        }

        val quantity = quantityInput.trim().toIntOrNull()?.takeIf { it >= 1 }
        if (quantity == null) {
            setActionStatus("Quantity must be at least 1.")
            return
        }

        val species = speciesInput.trim()
        val dietaryNotes = dietaryNotesInput.trim().ifBlank { null }

        withAction(
            errorFallback = "Unable to save resident changes."
        ) {
            val resident = livestockRepository.getById(entityId.trim())
                ?: error("Resident no longer exists.")

            val updatedResident = resident.copy(
                name = name,
                species = species,
                quantity = quantity,
                status = status,
                dietaryNotes = dietaryNotes
            )

            if (!hasLivestockChanges(resident, updatedResident)) {
                setActionStatus("No resident changes to save.")
                return@withAction
            }

            livestockRepository.upsert(updatedResident)

            timelineEventRepository.upsert(
                TimelineEvent(
                    id = idProvider(),
                    aquariumId = updatedResident.aquariumId,
                    type = TimelineEventType.LIVESTOCK,
                    createdAt = nowProvider().toString(),
                    title = "Resident updated",
                    description = buildLivestockUpdateDescription(resident, updatedResident),
                    photoUri = updatedResident.photoUri,
                    source = EntityRef(EntityKind.LIVESTOCK, updatedResident.id, updatedResident.aquariumId),
                    related = aquariumRelatedRefs(updatedResident.aquariumId)
                )
            )

            setActionStatus("${updatedResident.name} profile updated.")
        }
    }

    fun archiveLivestock() {
        if (kind != EntityKind.LIVESTOCK) {
            setActionStatus("Livestock actions are only available for resident details.")
            return
        }

        withAction(
            errorFallback = "Unable to archive resident."
        ) {
            val resident = livestockRepository.getById(entityId.trim())
                ?: error("Resident no longer exists.")

            if (resident.status == LivestockStatus.DECEASED) {
                setActionStatus("${resident.name} is already archived.")
                return@withAction
            }

            val archived = resident.copy(status = LivestockStatus.DECEASED)
            livestockRepository.upsert(archived)

            timelineEventRepository.upsert(
                TimelineEvent(
                    id = idProvider(),
                    aquariumId = archived.aquariumId,
                    type = TimelineEventType.LIVESTOCK,
                    createdAt = nowProvider().toString(),
                    title = "Archived ${archived.name}",
                    description = "Resident moved to archived/deceased status.",
                    photoUri = archived.photoUri,
                    source = EntityRef(EntityKind.LIVESTOCK, archived.id, archived.aquariumId),
                    related = aquariumRelatedRefs(archived.aquariumId)
                )
            )

            setActionStatus("${archived.name} archived.")
        }
    }

    fun deleteCurrentEntity() {
        when (kind) {
            EntityKind.LIVESTOCK -> deleteLivestock()
            EntityKind.ISSUE -> deleteIssue()
            EntityKind.MEMO -> deleteMemo()
            EntityKind.ASSET -> deleteAsset()
            EntityKind.CONSUMABLE -> deleteConsumable()
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

    private fun deleteLivestock() {
        withAction(
            errorFallback = "Unable to delete resident."
        ) {
            val resident = livestockRepository.getById(entityId.trim())
                ?: error("Resident no longer exists.")

            val allLivestock = livestockRepository.getAll().first()
            val allTaskTemplates = taskTemplateRepository.getAll().first()
            val impact = computeEntityLivestockDeleteImpact(
                livestock = allLivestock,
                taskTemplates = allTaskTemplates,
                deletedLivestockId = resident.id
            )

            impact.childUpdates.forEach { offspring ->
                livestockRepository.upsert(offspring)
            }

            impact.taskTemplateUpdates.forEach { template ->
                val primaryAquariumId = template.aquariumIds.firstOrNull() ?: resident.aquariumId
                taskTemplateRepository.upsert(template, primaryAquariumId)
            }

            livestockRepository.deleteById(resident.id)

            timelineEventRepository.upsert(
                TimelineEvent(
                    id = idProvider(),
                    aquariumId = resident.aquariumId,
                    type = TimelineEventType.LIVESTOCK,
                    createdAt = nowProvider().toString(),
                    title = "Deleted ${resident.name}",
                    description = buildEntityLivestockDeleteDescription(impact),
                    photoUri = resident.photoUri,
                    source = EntityRef(EntityKind.LIVESTOCK, resident.id, resident.aquariumId),
                    related = aquariumRelatedRefs(resident.aquariumId)
                )
            )

            setActionStatus(buildEntityLivestockDeleteStatusMessage(resident.name, impact))
        }
    }

    private fun deleteAsset() {
        withAction(
            errorFallback = "Unable to delete asset."
        ) {
            val asset = assetRepository.getById(entityId.trim())
                ?: error("Asset no longer exists.")

            assetRepository.deleteById(asset.id)

            timelineEventRepository.upsert(
                TimelineEvent(
                    id = idProvider(),
                    aquariumId = asset.aquariumId,
                    type = TimelineEventType.ASSET,
                    createdAt = nowProvider().toString(),
                    title = "Deleted asset",
                    description = asset.brandModel.ifBlank { asset.category.label() },
                    photoUri = asset.photoUri,
                    source = EntityRef(EntityKind.ASSET, asset.id, asset.aquariumId),
                    related = aquariumRelatedRefs(asset.aquariumId)
                )
            )

            setActionStatus("Asset deleted.")
        }
    }

    private fun deleteConsumable() {
        withAction(
            errorFallback = "Unable to delete consumable."
        ) {
            val consumable = consumableRepository.getById(entityId.trim())
                ?: error("Consumable no longer exists.")

            consumableRepository.deleteById(consumable.id)

            timelineEventRepository.upsert(
                TimelineEvent(
                    id = idProvider(),
                    aquariumId = consumable.aquariumId,
                    type = TimelineEventType.CONSUMABLE,
                    createdAt = nowProvider().toString(),
                    title = "Deleted consumable",
                    description = buildString {
                        append(consumable.name)
                        append(" (")
                        append(formatAmount(consumable.remaining))
                        append(' ')
                        append(consumable.unit.name.lowercase())
                        append(')')
                    },
                    photoUri = consumable.photoUri,
                    source = EntityRef(EntityKind.CONSUMABLE, consumable.id, consumable.aquariumId),
                    related = aquariumRelatedRefs(consumable.aquariumId)
                )
            )

            setActionStatus("Consumable deleted.")
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
            aquariumId = routeAquariumId,
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

            val taskExecutionHistory = taskExecutionsForTemplate
                .sortedWith(compareByDescending<TaskExecution> {
                    parseToInstant(it.completedAt, zoneId)?.toEpochMilli() ?: Long.MIN_VALUE
                }.thenByDescending { it.completedAt })
                .map { execution ->
                    EntityTaskExecutionItem(
                        id = execution.id,
                        aquariumId = execution.aquariumId,
                        aquariumName = aquariumNameById[execution.aquariumId] ?: "Unknown tank",
                        completedAtLabel = formatDateTime(execution.completedAt, zoneId),
                        note = execution.note
                    )
                }

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
                },
                taskExecutionHistory = taskExecutionHistory
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
                        add(EntityDetailField("Acquired", formatDateTime(it, zoneId)))
                    }
                    resident.dietaryNotes?.takeIf { it.isNotBlank() }?.let {
                        add(EntityDetailField("Dietary notes", it))
                    }
                    resident.parentId
                        ?.let { livestockById[it] }
                        ?.let { parent ->
                            add(EntityDetailField("Parent", parent.name.ifBlank { parent.species.ifBlank { "Resident" } }))
                        }
                },
                livestockEditor = EntityLivestockEditorState(
                    id = resident.id,
                    aquariumId = resident.aquariumId,
                    name = resident.name,
                    species = resident.species,
                    quantityInput = resident.quantity.toString(),
                    status = resident.status,
                    dietaryNotes = resident.dietaryNotes.orEmpty(),
                    photoUri = resident.photoUri
                )
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
                        add(EntityDetailField("Purchased", formatDateTime(it, zoneId)))
                    }
                    asset.price?.let {
                        add(EntityDetailField("Price", formatAmount(it)))
                    }
                },
                assetEditor = EntityAssetEditorState(
                    id = asset.id,
                    aquariumId = asset.aquariumId,
                    category = asset.category,
                    brandModel = asset.brandModel,
                    purchasedAtInput = asset.purchasedAt?.let { formatDateTime(it, zoneId) }.orEmpty(),
                    priceInput = asset.price?.let(::formatAmount).orEmpty(),
                    photoUri = asset.photoUri
                )
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
                },
                consumableEditor = EntityConsumableEditorState(
                    id = consumable.id,
                    aquariumId = consumable.aquariumId,
                    name = consumable.name,
                    unit = consumable.unit,
                    remainingInput = formatAmount(consumable.remaining),
                    reorderAtInput = consumable.reorderAt?.let(::formatAmount).orEmpty(),
                    photoUri = consumable.photoUri
                )
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

    val sortedMatchingEvents = matchingEvents
        .sortedWith(compareByDescending<TimelineEvent> {
            parseToInstant(it.createdAt, zoneId)?.toEpochMilli() ?: Long.MIN_VALUE
        }.thenByDescending { it.createdAt })

    val relatedEvents = sortedMatchingEvents
        .take(8)
        .map { event ->
            val eventAquariumName = aquariumNameById[event.aquariumId] ?: "Unknown tank"
            EntityRelatedEventItem(
                id = event.id,
                title = event.title,
                supportingText = "${event.type.label()} • ${formatDateTime(event.createdAt, zoneId)} • $eventAquariumName"
            )
        }

    val relatedPhotos = buildEntityRelatedPhotos(
        kind = kind,
        entityId = trimmedEntityId,
        entityTitle = resolved.title,
        resolvedPhotoUri = resolved.photoUri,
        matchingEvents = sortedMatchingEvents,
        aquariums = aquariums,
        livestock = livestock,
        assets = assets,
        consumables = consumables,
        memos = memos,
        aquariumNameById = aquariumNameById,
        zoneId = zoneId
    )

    return EntityDetailUiState(
        isNotFound = false,
        headline = "${kind.label()} details",
        kindLabel = kind.label(),
        entityId = trimmedEntityId,
        aquariumId = aquariumId,
        title = resolved.title,
        subtitle = resolved.subtitle,
        aquariumName = aquariumName,
        photoUri = resolved.photoUri,
        metrics = resolved.metrics + EntityDetailMetric("Linked events", matchingEvents.size.toString()),
        fields = resolved.fields,
        taskExecutionHistory = resolved.taskExecutionHistory,
        relatedPhotos = relatedPhotos,
        relatedEvents = relatedEvents,
        livestockEditor = resolved.livestockEditor,
        assetEditor = resolved.assetEditor,
        consumableEditor = resolved.consumableEditor,
        issueEditor = resolved.issueEditor,
        memoEditor = resolved.memoEditor,
        statusMessage = if (matchingEvents.isEmpty()) {
            "No linked timeline entries yet."
        } else {
            null
        }
    )
}

private data class EntityPhotoKey(
    val kind: EntityKind,
    val id: String
)

private data class EntityPhotoCandidate(
    val uri: String,
    val title: String,
    val supportingText: String
)

private fun buildEntityRelatedPhotos(
    kind: EntityKind,
    entityId: String,
    entityTitle: String,
    resolvedPhotoUri: String?,
    matchingEvents: List<TimelineEvent>,
    aquariums: List<Aquarium>,
    livestock: List<Livestock>,
    assets: List<Asset>,
    consumables: List<Consumable>,
    memos: List<Memo>,
    aquariumNameById: Map<String, String>,
    zoneId: ZoneId
): List<EntityRelatedPhotoItem> {
    val photoUriByEntity = mutableMapOf<EntityPhotoKey, String>()
    val photoLabelByEntity = mutableMapOf<EntityPhotoKey, String>()

    fun registerPhoto(kind: EntityKind, id: String, uri: String?, label: String) {
        val normalizedUri = normalizePhotoUri(uri) ?: return
        val key = EntityPhotoKey(kind = kind, id = id)
        photoUriByEntity[key] = normalizedUri
        photoLabelByEntity[key] = label
    }

    aquariums.forEach { aquarium ->
        registerPhoto(
            kind = EntityKind.AQUARIUM,
            id = aquarium.id,
            uri = aquarium.photoUri,
            label = aquarium.name.ifBlank { "Aquarium" }
        )
    }

    livestock.forEach { resident ->
        registerPhoto(
            kind = EntityKind.LIVESTOCK,
            id = resident.id,
            uri = resident.photoUri,
            label = resident.name.ifBlank { resident.species.ifBlank { "Resident" } }
        )
    }

    assets.forEach { asset ->
        registerPhoto(
            kind = EntityKind.ASSET,
            id = asset.id,
            uri = asset.photoUri,
            label = asset.brandModel.ifBlank { "${asset.category.label()} asset" }
        )
    }

    consumables.forEach { consumable ->
        registerPhoto(
            kind = EntityKind.CONSUMABLE,
            id = consumable.id,
            uri = consumable.photoUri,
            label = consumable.name
        )
    }

    memos.forEach { memo ->
        val snippet = memo.content.trim()
            .take(48)
            .trim()
            .ifBlank { "Memo" }

        registerPhoto(
            kind = EntityKind.MEMO,
            id = memo.id,
            uri = memo.photoUri,
            label = snippet
        )
    }

    val candidates = mutableListOf<EntityPhotoCandidate>()

    normalizePhotoUri(resolvedPhotoUri)?.let { uri ->
        candidates += EntityPhotoCandidate(
            uri = uri,
            title = "${kind.label()} • ${entityTitle.ifBlank { entityId }}",
            supportingText = "Current entity"
        )
    }

    matchingEvents.forEach { event ->
        val eventDateLabel = formatDateTime(event.createdAt, zoneId)
        normalizePhotoUri(event.photoUri)?.let { uri ->
            candidates += EntityPhotoCandidate(
                uri = uri,
                title = event.title.ifBlank { "${event.type.label()} event" },
                supportingText = "${event.type.label()} • $eventDateLabel"
            )
        }

        buildList {
            event.source?.let { add(it) }
            addAll(event.related)
        }.forEach { reference ->
            val key = EntityPhotoKey(kind = reference.kind, id = reference.id)
            val linkedUri = photoUriByEntity[key] ?: return@forEach
            val linkedTitle = photoLabelByEntity[key] ?: reference.kind.label()
            val aquariumLabel = reference.aquariumId
                ?.let { aquariumNameById[it] ?: "Unknown tank" }
                ?: "Unknown tank"

            candidates += EntityPhotoCandidate(
                uri = linkedUri,
                title = "${reference.kind.label()} • $linkedTitle",
                supportingText = "$aquariumLabel • Linked entity"
            )
        }
    }

    val seenUris = mutableSetOf<String>()
    return candidates
        .asSequence()
        .filter { candidate -> seenUris.add(candidate.uri) }
        .take(18)
        .mapIndexed { index, candidate ->
            EntityRelatedPhotoItem(
                id = "photo-${index + 1}-${candidate.uri.hashCode()}",
                uri = candidate.uri,
                title = candidate.title,
                supportingText = candidate.supportingText
            )
        }
        .toList()
}

private fun normalizePhotoUri(rawUri: String?): String? =
    rawUri?.trim()?.takeIf { it.isNotEmpty() }

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
        aquariumId = routeAquariumId,
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

internal fun buildAssetUpdateDescription(previous: Asset, updated: Asset): String {
    val parts = mutableListOf<String>()
    if (previous.category != updated.category) {
        parts += "Category ${previous.category.label()} → ${updated.category.label()}"
    }
    if (previous.brandModel != updated.brandModel) {
        parts += if (updated.brandModel.isBlank()) {
            "Brand/model cleared"
        } else {
            "Brand/model updated"
        }
    }
    if (previous.purchasedAt != updated.purchasedAt) {
        parts += if (updated.purchasedAt.isNullOrBlank()) {
            "Purchase date cleared"
        } else {
            "Purchase date updated"
        }
    }
    if (!areSameNullableDouble(previous.price, updated.price)) {
        parts += if (updated.price == null) {
            "Price cleared"
        } else {
            "Price updated"
        }
    }

    return parts.joinToString(" • ").ifBlank { "Asset updated" }
}

internal fun buildConsumableUpdateDescription(previous: Consumable, updated: Consumable): String {
    val parts = mutableListOf<String>()
    if (previous.name != updated.name) {
        parts += "Name updated"
    }
    if (previous.unit != updated.unit) {
        parts += "Unit ${previous.unit.name.lowercase()} → ${updated.unit.name.lowercase()}"
    }
    if (!areSameNullableDouble(previous.remaining, updated.remaining)) {
        parts += "Remaining ${formatAmount(previous.remaining)} → ${formatAmount(updated.remaining)}"
    }
    if (!areSameNullableDouble(previous.reorderAt, updated.reorderAt)) {
        parts += if (updated.reorderAt == null) {
            "Reorder threshold cleared"
        } else {
            "Reorder threshold updated"
        }
    }

    return parts.joinToString(" • ").ifBlank { "Consumable updated" }
}

internal fun buildLivestockUpdateDescription(previous: Livestock, updated: Livestock): String {
    val parts = mutableListOf<String>()
    if (previous.name != updated.name) {
        parts += "Name updated"
    }
    if (previous.species != updated.species) {
        parts += if (updated.species.isBlank()) {
            "Species cleared"
        } else {
            "Species updated"
        }
    }
    if (previous.quantity != updated.quantity) {
        parts += "Quantity ${previous.quantity} → ${updated.quantity}"
    }
    if (previous.status != updated.status) {
        parts += "Status ${previous.status.label()} → ${updated.status.label()}"
    }
    if (previous.dietaryNotes != updated.dietaryNotes) {
        parts += if (updated.dietaryNotes.isNullOrBlank()) {
            "Dietary notes cleared"
        } else {
            "Dietary notes updated"
        }
    }

    return parts.joinToString(" • ").ifBlank { "Resident updated" }
}

private fun computeEntityLivestockDeleteImpact(
    livestock: List<Livestock>,
    taskTemplates: List<TaskTemplate>,
    deletedLivestockId: String
): EntityLivestockDeleteImpact {
    val childUpdates = livestock
        .filter { item -> item.parentId == deletedLivestockId }
        .map { item -> item.copy(parentId = null) }

    val taskTemplateUpdates = taskTemplates
        .filter { template -> template.livestockId == deletedLivestockId }
        .map { template -> template.copy(livestockId = null) }

    return EntityLivestockDeleteImpact(
        childUpdates = childUpdates,
        taskTemplateUpdates = taskTemplateUpdates,
        orphanedOffspringCount = childUpdates.size,
        detachedTaskCount = taskTemplateUpdates.size
    )
}

private fun buildEntityLivestockDeleteDescription(impact: EntityLivestockDeleteImpact): String? {
    val parts = mutableListOf<String>()
    if (impact.orphanedOffspringCount > 0) {
        parts += "${impact.orphanedOffspringCount} offspring link${impact.orphanedOffspringCount.plural()} removed"
    }
    if (impact.detachedTaskCount > 0) {
        parts += "${impact.detachedTaskCount} feeding task link${impact.detachedTaskCount.plural()} detached"
    }
    return parts.joinToString(" • ").ifBlank { null }
}

private fun buildEntityLivestockDeleteStatusMessage(
    residentName: String,
    impact: EntityLivestockDeleteImpact
): String {
    val details = buildEntityLivestockDeleteDescription(impact)
    return if (details.isNullOrBlank()) {
        "$residentName removed."
    } else {
        "$residentName removed. $details."
    }
}

private fun hasAssetChanges(previous: Asset, updated: Asset): Boolean =
    previous.category != updated.category ||
        previous.brandModel != updated.brandModel ||
        previous.purchasedAt != updated.purchasedAt ||
        !areSameNullableDouble(previous.price, updated.price)

private fun hasLivestockChanges(previous: Livestock, updated: Livestock): Boolean =
    previous.name != updated.name ||
        previous.species != updated.species ||
        previous.quantity != updated.quantity ||
        previous.status != updated.status ||
        previous.dietaryNotes != updated.dietaryNotes

private fun hasConsumableChanges(previous: Consumable, updated: Consumable): Boolean =
    previous.name != updated.name ||
        previous.unit != updated.unit ||
        !areSameNullableDouble(previous.remaining, updated.remaining) ||
        !areSameNullableDouble(previous.reorderAt, updated.reorderAt)

private fun Int.plural(): String = if (this == 1) "" else "s"

private fun parseNonNegativeAmountInput(raw: String): Double? {
    val value = raw.trim().toDoubleOrNull() ?: return null
    if (value.isNaN() || value.isInfinite()) return null
    return value.takeIf { it >= 0.0 }
}

private fun areSameNullableDouble(first: Double?, second: Double?): Boolean {
    if (first == null || second == null) return first == second
    return kotlin.math.abs(first - second) < 1e-9
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