package com.keepaside.aquapt.feature.livestock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.keepaside.aquapt.core.model.Aquarium
import com.keepaside.aquapt.core.model.EntityKind
import com.keepaside.aquapt.core.model.EntityRef
import com.keepaside.aquapt.core.model.Livestock
import com.keepaside.aquapt.core.model.LivestockKind
import com.keepaside.aquapt.core.model.LivestockStatus
import com.keepaside.aquapt.core.model.TaskCategory
import com.keepaside.aquapt.core.model.TaskFrequency
import com.keepaside.aquapt.core.model.TaskFrequencyKind
import com.keepaside.aquapt.core.model.TaskTemplate
import com.keepaside.aquapt.core.model.TimelineEvent
import com.keepaside.aquapt.core.model.TimelineEventType
import com.keepaside.aquapt.core.repository.AquariumRepository
import com.keepaside.aquapt.core.repository.LivestockRepository
import com.keepaside.aquapt.core.repository.TaskTemplateRepository
import com.keepaside.aquapt.core.repository.TimelineEventRepository
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
import kotlin.math.floor

data class LivestockSummaryMetrics(
    val aquariumCount: Int = 0,
    val residentCount: Int = 0,
    val filteredResidentCount: Int = 0,
    val activeCount: Int = 0,
    val illCount: Int = 0,
    val deceasedCount: Int = 0,
    val feedingTaskCount: Int = 0
)

data class LivestockAquariumFilter(
    val aquariumId: String,
    val aquariumName: String
)

data class LivestockResidentItem(
    val id: String,
    val aquariumId: String,
    val aquariumName: String,
    val name: String,
    val species: String,
    val quantity: Int,
    val kind: LivestockKind,
    val kindLabel: String,
    val status: LivestockStatus,
    val statusLabel: String,
    val dietaryNotes: String?,
    val isSelected: Boolean
)

data class LivestockFamilyLink(
    val id: String,
    val name: String,
    val aquariumId: String,
    val aquariumName: String
)

data class LivestockFeedingTaskItem(
    val id: String,
    val title: String,
    val frequencyLabel: String,
    val timesPerDay: Int?
)

data class LivestockDetailItem(
    val id: String,
    val aquariumId: String,
    val aquariumName: String,
    val name: String,
    val species: String,
    val quantity: Int,
    val kind: LivestockKind,
    val kindLabel: String,
    val status: LivestockStatus,
    val statusLabel: String,
    val ageLabel: String,
    val dietaryNotes: String?,
    val parent: LivestockFamilyLink?,
    val offspring: List<LivestockFamilyLink>,
    val feedingTasks: List<LivestockFeedingTaskItem>,
    val transferTargets: List<LivestockAquariumFilter>
)

data class LivestockTransferDraft(
    val targetAquariumId: String? = null,
    val note: String = ""
)

data class LivestockOffspringDraft(
    val name: String = "",
    val species: String = "",
    val quantity: String = "1"
)

data class LivestockFeedingTaskDraft(
    val title: String = "",
    val frequency: TaskFrequency = TaskFrequency.DAILY,
    val customDays: String = "1",
    val startDate: String = "",
    val timesPerDay: String = "1"
)

data class LivestockUiState(
    val isLoading: Boolean = true,
    val isEmpty: Boolean = false,
    val headline: String = "Loading livestock...",
    val selectedAquariumId: String? = null,
    val selectedLivestockId: String? = null,
    val summary: LivestockSummaryMetrics = LivestockSummaryMetrics(),
    val aquariumFilters: List<LivestockAquariumFilter> = emptyList(),
    val residents: List<LivestockResidentItem> = emptyList(),
    val selectedResident: LivestockDetailItem? = null,
    val feedingNoteDraft: String = "",
    val statusDraft: LivestockStatus = LivestockStatus.ACTIVE,
    val statusNoteDraft: String = "",
    val transferDraft: LivestockTransferDraft = LivestockTransferDraft(),
    val offspringDraft: LivestockOffspringDraft = LivestockOffspringDraft(),
    val feedingTaskDraft: LivestockFeedingTaskDraft = LivestockFeedingTaskDraft(),
    val statusMessage: String? = null
)

internal data class LivestockInteractionState(
    val selectedAquariumId: String? = null,
    val selectedLivestockId: String? = null,
    val feedingNoteDraft: String = "",
    val statusDraft: LivestockStatus = LivestockStatus.ACTIVE,
    val statusNoteDraft: String = "",
    val transferTargetAquariumId: String? = null,
    val transferNoteDraft: String = "",
    val offspringDraft: LivestockOffspringDraft = LivestockOffspringDraft(),
    val feedingTaskDraft: LivestockFeedingTaskDraft = LivestockFeedingTaskDraft(),
    val statusMessage: String? = null
)

class LivestockViewModel(
    private val aquariumRepository: AquariumRepository,
    private val livestockRepository: LivestockRepository,
    private val taskTemplateRepository: TaskTemplateRepository,
    private val timelineEventRepository: TimelineEventRepository,
    private val nowProvider: () -> Instant = { Instant.now() },
    private val idProvider: () -> String = { UUID.randomUUID().toString() },
    private val zoneId: ZoneId = ZoneId.systemDefault()
) : ViewModel() {

    private val interactionState = MutableStateFlow(LivestockInteractionState())
    private val _uiState = MutableStateFlow(LivestockUiState())
    val uiState: StateFlow<LivestockUiState> = _uiState.asStateFlow()

    init {
        observeLivestock()
    }

    fun onAquariumFilterSelected(aquariumId: String?) {
        interactionState.update { it.copy(selectedAquariumId = aquariumId) }
    }

    fun onResidentSelected(livestockId: String) {
        val current = _uiState.value
        val selected = current.residents.firstOrNull { it.id == livestockId }
        val isCollapsing = current.selectedLivestockId == livestockId

        interactionState.update { previous ->
            if (isCollapsing) {
                previous.copy(selectedLivestockId = null)
            } else {
                previous.copy(
                    selectedLivestockId = livestockId,
                    feedingNoteDraft = selected?.dietaryNotes.orEmpty(),
                    statusDraft = selected?.status ?: LivestockStatus.ACTIVE,
                    statusNoteDraft = "",
                    transferTargetAquariumId = current.aquariumFilters
                        .firstOrNull { it.aquariumId != selected?.aquariumId }
                        ?.aquariumId,
                    transferNoteDraft = "",
                    offspringDraft = LivestockOffspringDraft(),
                    feedingTaskDraft = LivestockFeedingTaskDraft()
                )
            }
        }
    }

    fun onFeedingNoteChanged(note: String) {
        interactionState.update { it.copy(feedingNoteDraft = note) }
    }

    fun saveFeedingNotes() {
        val selected = _uiState.value.selectedResident ?: return setStatus("Select a resident first.")
        viewModelScope.launch {
            runCatching {
                val current = livestockRepository.getById(selected.id)
                    ?: error("Resident no longer exists.")
                livestockRepository.upsert(current.copy(dietaryNotes = interactionState.value.feedingNoteDraft.trim()))
            }.onSuccess {
                setStatus("Feeding notes saved for ${selected.name}.")
            }.onFailure { error ->
                setStatus(error.message ?: "Unable to save feeding notes.")
            }
        }
    }

    fun onStatusSelected(status: LivestockStatus) {
        interactionState.update { it.copy(statusDraft = status) }
    }

    fun onStatusNoteChanged(note: String) {
        interactionState.update { it.copy(statusNoteDraft = note) }
    }

    fun saveStatus() {
        val selected = _uiState.value.selectedResident ?: return setStatus("Select a resident first.")
        val status = interactionState.value.statusDraft
        val note = interactionState.value.statusNoteDraft.trim().ifBlank { null }
        val createdAt = nowProvider().toString()

        viewModelScope.launch {
            runCatching {
                val current = livestockRepository.getById(selected.id)
                    ?: error("Resident no longer exists.")
                livestockRepository.upsert(current.copy(status = status))
                timelineEventRepository.upsert(
                    TimelineEvent(
                        id = idProvider(),
                        aquariumId = current.aquariumId,
                        type = TimelineEventType.LIVESTOCK,
                        createdAt = createdAt,
                        title = "${current.name} status: ${status.label()}",
                        description = note,
                        photoUri = current.photoUri,
                        source = EntityRef(EntityKind.LIVESTOCK, current.id, current.aquariumId),
                        related = aquariumRelatedRefs(current.aquariumId)
                    )
                )
            }.onSuccess {
                interactionState.update { it.copy(statusNoteDraft = "") }
                setStatus("${selected.name} marked ${status.label()}.")
            }.onFailure { error ->
                setStatus(error.message ?: "Unable to update status.")
            }
        }
    }

    fun onTransferTargetSelected(aquariumId: String) {
        interactionState.update { it.copy(transferTargetAquariumId = aquariumId) }
    }

    fun onTransferNoteChanged(note: String) {
        interactionState.update { it.copy(transferNoteDraft = note) }
    }

    fun transferSelectedResident() {
        val selected = _uiState.value.selectedResident ?: return setStatus("Select a resident first.")
        val targetAquariumId = _uiState.value.transferDraft.targetAquariumId
            ?: return setStatus("Add another tank before transferring livestock.")
        val note = interactionState.value.transferNoteDraft.trim().ifBlank { null }
        val createdAt = nowProvider().toString()

        if (targetAquariumId == selected.aquariumId) {
            setStatus("Choose a different target tank.")
            return
        }

        viewModelScope.launch {
            runCatching {
                val current = livestockRepository.getById(selected.id)
                    ?: error("Resident no longer exists.")
                val sourceAquariumId = current.aquariumId
                val targetName = _uiState.value.aquariumFilters
                    .firstOrNull { it.aquariumId == targetAquariumId }
                    ?.aquariumName
                    ?: "target tank"
                val sourceName = _uiState.value.aquariumFilters
                    .firstOrNull { it.aquariumId == sourceAquariumId }
                    ?.aquariumName
                    ?: "source tank"

                livestockRepository.upsert(current.copy(aquariumId = targetAquariumId))
                timelineEventRepository.upsert(
                    TimelineEvent(
                        id = idProvider(),
                        aquariumId = sourceAquariumId,
                        type = TimelineEventType.LIVESTOCK,
                        createdAt = createdAt,
                        title = "Transferred out ${current.name}",
                        description = buildTransferDescription("Moved to $targetName", note),
                        photoUri = current.photoUri,
                        source = EntityRef(EntityKind.LIVESTOCK, current.id, sourceAquariumId),
                        related = aquariumRelatedRefs(
                            sourceAquariumId,
                            EntityRef(EntityKind.AQUARIUM, targetAquariumId, targetAquariumId)
                        )
                    )
                )
                timelineEventRepository.upsert(
                    TimelineEvent(
                        id = idProvider(),
                        aquariumId = targetAquariumId,
                        type = TimelineEventType.LIVESTOCK,
                        createdAt = createdAt,
                        title = "Transferred ${current.name}",
                        description = buildTransferDescription("From $sourceName", note),
                        photoUri = current.photoUri,
                        source = EntityRef(EntityKind.LIVESTOCK, current.id, targetAquariumId),
                        related = aquariumRelatedRefs(
                            targetAquariumId,
                            EntityRef(EntityKind.AQUARIUM, sourceAquariumId, sourceAquariumId)
                        )
                    )
                )
            }.onSuccess {
                interactionState.update { state ->
                    state.copy(
                        transferTargetAquariumId = _uiState.value.aquariumFilters
                            .firstOrNull { it.aquariumId != targetAquariumId }
                            ?.aquariumId,
                        transferNoteDraft = ""
                    )
                }
                setStatus("${selected.name} transferred.")
            }.onFailure { error ->
                setStatus(error.message ?: "Unable to transfer resident.")
            }
        }
    }

    fun onOffspringNameChanged(name: String) {
        interactionState.update { it.copy(offspringDraft = it.offspringDraft.copy(name = name)) }
    }

    fun onOffspringSpeciesChanged(species: String) {
        interactionState.update { it.copy(offspringDraft = it.offspringDraft.copy(species = species)) }
    }

    fun onOffspringQuantityChanged(quantity: String) {
        interactionState.update { it.copy(offspringDraft = it.offspringDraft.copy(quantity = quantity)) }
    }

    fun addOffspring() {
        val selected = _uiState.value.selectedResident ?: return setStatus("Select a resident first.")
        val draft = interactionState.value.offspringDraft
        val name = draft.name.trim()
        val quantity = draft.quantity.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val createdAt = nowProvider().toString()

        if (name.isBlank()) {
            setStatus("Name the offspring before saving.")
            return
        }

        viewModelScope.launch {
            runCatching {
                val parent = livestockRepository.getById(selected.id)
                    ?: error("Parent resident no longer exists.")
                val offspring = Livestock(
                    id = idProvider(),
                    aquariumId = parent.aquariumId,
                    kind = parent.kind,
                    name = name,
                    species = draft.species.trim(),
                    quantity = quantity,
                    acquiredAt = createdAt,
                    parentId = parent.id,
                    status = LivestockStatus.ACTIVE
                )

                livestockRepository.upsert(offspring)
                timelineEventRepository.upsert(
                    TimelineEvent(
                        id = idProvider(),
                        aquariumId = offspring.aquariumId,
                        type = TimelineEventType.LIVESTOCK,
                        createdAt = createdAt,
                        title = "Offspring linked",
                        description = "${offspring.name} linked to ${parent.name}",
                        photoUri = offspring.photoUri,
                        source = EntityRef(EntityKind.LIVESTOCK, offspring.id, offspring.aquariumId),
                        related = aquariumRelatedRefs(
                            offspring.aquariumId,
                            EntityRef(EntityKind.LIVESTOCK, parent.id, parent.aquariumId)
                        )
                    )
                )
            }.onSuccess {
                interactionState.update { it.copy(offspringDraft = LivestockOffspringDraft()) }
                setStatus("$name linked as offspring.")
            }.onFailure { error ->
                setStatus(error.message ?: "Unable to add offspring.")
            }
        }
    }

    fun onFeedingTaskTitleChanged(title: String) {
        interactionState.update { it.copy(feedingTaskDraft = it.feedingTaskDraft.copy(title = title)) }
    }

    fun onFeedingTaskFrequencySelected(frequency: TaskFrequency) {
        interactionState.update { it.copy(feedingTaskDraft = it.feedingTaskDraft.copy(frequency = frequency)) }
    }

    fun onFeedingTaskCustomDaysChanged(days: String) {
        interactionState.update { it.copy(feedingTaskDraft = it.feedingTaskDraft.copy(customDays = days)) }
    }

    fun onFeedingTaskStartDateChanged(date: String) {
        interactionState.update { it.copy(feedingTaskDraft = it.feedingTaskDraft.copy(startDate = date)) }
    }

    fun onFeedingTaskTimesPerDayChanged(times: String) {
        interactionState.update { it.copy(feedingTaskDraft = it.feedingTaskDraft.copy(timesPerDay = times)) }
    }

    fun createFeedingTask() {
        val selected = _uiState.value.selectedResident ?: return setStatus("Select a resident first.")
        val draft = interactionState.value.feedingTaskDraft
        val frequency = resolveFeedingTaskFrequency(draft)
            ?: return setStatus("Custom frequency needs at least 1 day.")
        val timesPerDay = draft.timesPerDay.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val startDate = draft.startDate.trim().ifBlank { todayIso(nowProvider(), zoneId) }
        val title = draft.title.trim().ifBlank { "Feed ${selected.name}" }

        viewModelScope.launch {
            runCatching {
                taskTemplateRepository.upsert(
                    TaskTemplate(
                        id = idProvider(),
                        title = title,
                        description = selected.dietaryNotes ?: "Targeted feeding regimen for ${selected.name}",
                        category = TaskCategory.FEEDING,
                        livestockId = selected.id,
                        frequency = frequency,
                        aquariumIds = listOf(selected.aquariumId),
                        startDate = startDate,
                        timesPerDay = if (frequency.kind == TaskFrequencyKind.DAILY) timesPerDay else null
                    ),
                    primaryAquariumId = selected.aquariumId
                )
            }.onSuccess {
                interactionState.update { it.copy(feedingTaskDraft = LivestockFeedingTaskDraft()) }
                setStatus("Feeding task created for ${selected.name}.")
            }.onFailure { error ->
                setStatus(error.message ?: "Unable to create feeding task.")
            }
        }
    }

    private fun observeLivestock() {
        val baseDataFlow = combine(
            aquariumRepository.getAll(),
            livestockRepository.getAll(),
            taskTemplateRepository.getAll()
        ) { aquariums, livestock, taskTemplates ->
            LivestockBaseData(
                aquariums = aquariums,
                livestock = livestock,
                taskTemplates = taskTemplates
            )
        }

        viewModelScope.launch {
            combine(baseDataFlow, interactionState) { base, interaction ->
                assembleLivestockUiState(
                    aquariums = base.aquariums,
                    livestock = base.livestock,
                    taskTemplates = base.taskTemplates,
                    interaction = interaction,
                    now = nowProvider(),
                    zoneId = zoneId
                )
            }.collect { next ->
                _uiState.update { next.copy(isLoading = false) }
            }
        }
    }

    private fun setStatus(message: String) {
        interactionState.update { it.copy(statusMessage = message) }
    }

    private data class LivestockBaseData(
        val aquariums: List<Aquarium>,
        val livestock: List<Livestock>,
        val taskTemplates: List<TaskTemplate>
    )

    companion object {
        fun factory(
            aquariumRepository: AquariumRepository,
            livestockRepository: LivestockRepository,
            taskTemplateRepository: TaskTemplateRepository,
            timelineEventRepository: TimelineEventRepository
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(LivestockViewModel::class.java)) {
                        return LivestockViewModel(
                            aquariumRepository = aquariumRepository,
                            livestockRepository = livestockRepository,
                            taskTemplateRepository = taskTemplateRepository,
                            timelineEventRepository = timelineEventRepository
                        ) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
    }
}

internal fun assembleLivestockUiState(
    aquariums: List<Aquarium>,
    livestock: List<Livestock>,
    taskTemplates: List<TaskTemplate>,
    interaction: LivestockInteractionState,
    now: Instant,
    zoneId: ZoneId
): LivestockUiState {
    val aquariumFilters = aquariums
        .sortedBy { it.name.lowercase() }
        .map { LivestockAquariumFilter(it.id, it.name) }
    val aquariumNameById = aquariums.associate { it.id to it.name }
    val selectedAquariumId = interaction.selectedAquariumId

    val filteredLivestock = livestock
        .filter { selectedAquariumId == null || it.aquariumId == selectedAquariumId }
        .sortedWith(compareBy<Livestock> { aquariumNameById[it.aquariumId] ?: "Unknown tank" }
            .thenBy { it.name.lowercase() })

    val residents = filteredLivestock.map { item ->
        LivestockResidentItem(
            id = item.id,
            aquariumId = item.aquariumId,
            aquariumName = aquariumNameById[item.aquariumId] ?: "Unknown tank",
            name = item.name,
            species = item.species,
            quantity = item.quantity,
            kind = item.kind,
            kindLabel = item.kind.label(),
            status = item.status,
            statusLabel = item.status.label(),
            dietaryNotes = item.dietaryNotes,
            isSelected = item.id == interaction.selectedLivestockId
        )
    }

    val selectedLivestock = livestock.firstOrNull { it.id == interaction.selectedLivestockId }
    val selectedResident = selectedLivestock?.let { item ->
        val feedingTasks = taskTemplates
            .filter { it.livestockId == item.id }
            .sortedBy { it.title.lowercase() }
            .map {
                LivestockFeedingTaskItem(
                    id = it.id,
                    title = it.title,
                    frequencyLabel = it.frequency.getLabel(),
                    timesPerDay = it.timesPerDay
                )
            }
        val parent = item.parentId?.let { parentId ->
            livestock.firstOrNull { it.id == parentId }?.toFamilyLink(aquariumNameById)
        }
        val offspring = livestock
            .filter { it.parentId == item.id }
            .sortedBy { it.name.lowercase() }
            .map { it.toFamilyLink(aquariumNameById) }
        val transferTargets = aquariumFilters.filter { it.aquariumId != item.aquariumId }

        LivestockDetailItem(
            id = item.id,
            aquariumId = item.aquariumId,
            aquariumName = aquariumNameById[item.aquariumId] ?: "Unknown tank",
            name = item.name,
            species = item.species,
            quantity = item.quantity,
            kind = item.kind,
            kindLabel = item.kind.label(),
            status = item.status,
            statusLabel = item.status.label(),
            ageLabel = formatAge(item.acquiredAt, now, zoneId),
            dietaryNotes = item.dietaryNotes,
            parent = parent,
            offspring = offspring,
            feedingTasks = feedingTasks,
            transferTargets = transferTargets
        )
    }

    val transferTargetAquariumId = interaction.transferTargetAquariumId
        ?.takeIf { target -> selectedResident?.transferTargets?.any { it.aquariumId == target } == true }
        ?: selectedResident?.transferTargets?.firstOrNull()?.aquariumId

    val headline = when {
        aquariums.isEmpty() -> "Add your first tank before tracking residents."
        livestock.isEmpty() -> "Add residents from the tank flow or import an existing backup."
        residents.isEmpty() -> "No residents match the current tank filter."
        interaction.selectedLivestockId != null && selectedResident == null -> "That resident is no longer available."
        else -> "${residents.size} resident${residents.size.plural()} in view across ${aquariums.size} tank${aquariums.size.plural()}."
    }

    return LivestockUiState(
        isEmpty = aquariums.isEmpty() || livestock.isEmpty(),
        headline = headline,
        selectedAquariumId = selectedAquariumId,
        selectedLivestockId = selectedResident?.id,
        summary = LivestockSummaryMetrics(
            aquariumCount = aquariums.size,
            residentCount = livestock.size,
            filteredResidentCount = residents.size,
            activeCount = livestock.count { it.status == LivestockStatus.ACTIVE },
            illCount = livestock.count { it.status == LivestockStatus.ILL },
            deceasedCount = livestock.count { it.status == LivestockStatus.DECEASED },
            feedingTaskCount = taskTemplates.count { it.category == TaskCategory.FEEDING && it.livestockId != null }
        ),
        aquariumFilters = aquariumFilters,
        residents = residents,
        selectedResident = selectedResident,
        feedingNoteDraft = interaction.feedingNoteDraft,
        statusDraft = interaction.statusDraft,
        statusNoteDraft = interaction.statusNoteDraft,
        transferDraft = LivestockTransferDraft(
            targetAquariumId = transferTargetAquariumId,
            note = interaction.transferNoteDraft
        ),
        offspringDraft = interaction.offspringDraft,
        feedingTaskDraft = interaction.feedingTaskDraft,
        statusMessage = interaction.statusMessage
    )
}

private fun Livestock.toFamilyLink(aquariumNameById: Map<String, String>): LivestockFamilyLink =
    LivestockFamilyLink(
        id = id,
        name = name,
        aquariumId = aquariumId,
        aquariumName = aquariumNameById[aquariumId] ?: "Unknown tank"
    )

private fun LivestockKind.label(): String =
    name.lowercase().replaceFirstChar { it.uppercaseChar() }

private fun LivestockStatus.label(): String =
    name.lowercase().replaceFirstChar { it.uppercaseChar() }

private fun Int.plural(): String = if (this == 1) "" else "s"

private fun formatAge(raw: String, now: Instant, zoneId: ZoneId): String {
    val acquired = parseToInstant(raw, zoneId) ?: return "Unknown age"
    if (acquired.isAfter(now)) return "Unknown age"
    val days = floor((now.toEpochMilli() - acquired.toEpochMilli()).toDouble() / 86_400_000.0).toLong()

    return when {
        days < 1L -> "Today"
        days == 1L -> "1 day"
        days < 14L -> "$days days"
        days < 30L -> {
            val weeks = days / 7L
            "$weeks week${if (weeks == 1L) "" else "s"}"
        }
        else -> {
            val acquiredDate = acquired.atZone(zoneId).toLocalDate()
            val nowDate = now.atZone(zoneId).toLocalDate()
            var years = nowDate.year - acquiredDate.year
            var months = nowDate.monthValue - acquiredDate.monthValue
            var dayOfMonth = nowDate.dayOfMonth - acquiredDate.dayOfMonth

            if (dayOfMonth < 0) {
                months -= 1
                dayOfMonth += nowDate.minusMonths(1).lengthOfMonth()
            }
            if (months < 0) {
                years -= 1
                months += 12
            }

            buildList {
                if (years > 0) add("$years year${if (years == 1) "" else "s"}")
                if (months > 0) add("$months month${if (months == 1) "" else "s"}")
                if (years == 0 && dayOfMonth > 0) add("$dayOfMonth day${if (dayOfMonth == 1) "" else "s"}")
            }.joinToString(", ").ifBlank { "$days days" }
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

private fun resolveFeedingTaskFrequency(draft: LivestockFeedingTaskDraft): TaskFrequency? =
    if (draft.frequency.kind == TaskFrequencyKind.CUSTOM) {
        draft.customDays.toIntOrNull()?.takeIf { it >= 1 }?.let { TaskFrequency.custom(it) }
    } else {
        draft.frequency
    }

private fun todayIso(now: Instant, zoneId: ZoneId): String =
    DateTimeFormatter.ISO_LOCAL_DATE.format(now.atZone(zoneId))

private fun aquariumRelatedRefs(aquariumId: String, vararg extras: EntityRef): List<EntityRef> =
    listOf(EntityRef(EntityKind.AQUARIUM, aquariumId, aquariumId)) + extras

private fun buildTransferDescription(prefix: String, note: String?): String =
    if (note.isNullOrBlank()) prefix else "$prefix - $note"
