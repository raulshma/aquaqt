package com.keepaside.aquapt.feature.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.keepaside.aquapt.core.model.Aquarium
import com.keepaside.aquapt.core.model.Memo
import com.keepaside.aquapt.core.model.TimelineEvent
import com.keepaside.aquapt.core.model.TimelineEventType
import com.keepaside.aquapt.core.repository.AquariumRepository
import com.keepaside.aquapt.core.repository.MemoRepository
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

data class TimelineSummaryMetrics(
    val eventCount: Int = 0,
    val visibleEventCount: Int = 0,
    val aquariumCount: Int = 0,
    val memoCount: Int = 0,
    val issueCount: Int = 0,
    val taskCount: Int = 0
)

data class TimelineAquariumFilter(
    val aquariumId: String,
    val aquariumName: String
)

data class TimelineEventTypeFilter(
    val type: TimelineEventType,
    val label: String
)

data class TimelineEventItem(
    val id: String,
    val aquariumId: String,
    val aquariumName: String,
    val type: TimelineEventType,
    val typeLabel: String,
    val title: String,
    val description: String?,
    val createdAtLabel: String,
    val dateLabel: String,
    val photoUri: String?,
    val relatedCount: Int
)

data class TimelineDayGroup(
    val dateLabel: String,
    val events: List<TimelineEventItem>
)

data class TimelineQuickMemoDraft(
    val aquariumId: String? = null,
    val content: String = "",
    val createdAtInput: String = "",
    val photoUri: String? = null
)

data class TimelineUiState(
    val isLoading: Boolean = true,
    val isEmpty: Boolean = false,
    val headline: String = "Loading timeline...",
    val selectedAquariumId: String? = null,
    val selectedType: TimelineEventType? = null,
    val summary: TimelineSummaryMetrics = TimelineSummaryMetrics(),
    val aquariumFilters: List<TimelineAquariumFilter> = emptyList(),
    val typeFilters: List<TimelineEventTypeFilter> = TimelineEventType.entries.map {
        TimelineEventTypeFilter(type = it, label = it.label())
    },
    val dayGroups: List<TimelineDayGroup> = emptyList(),
    val quickMemoDraft: TimelineQuickMemoDraft = TimelineQuickMemoDraft(),
    val statusMessage: String? = null
)

class TimelineViewModel(
    private val aquariumRepository: AquariumRepository,
    private val timelineEventRepository: TimelineEventRepository,
    private val memoRepository: MemoRepository,
    private val nowProvider: () -> Instant = { Instant.now() },
    private val idProvider: () -> String = { UUID.randomUUID().toString() },
    private val zoneId: ZoneId = ZoneId.systemDefault()
) : ViewModel() {

    private val selectedAquariumId = MutableStateFlow<String?>(null)
    private val selectedType = MutableStateFlow<TimelineEventType?>(null)
    private val quickMemoDraft = MutableStateFlow(TimelineQuickMemoDraft())
    private val statusMessage = MutableStateFlow<String?>(null)
    private val _uiState = MutableStateFlow(TimelineUiState())
    val uiState: StateFlow<TimelineUiState> = _uiState.asStateFlow()

    init {
        observeTimeline()
    }

    fun onAquariumFilterSelected(aquariumId: String?) {
        selectedAquariumId.value = aquariumId
        quickMemoDraft.update { draft ->
            if (aquariumId == null) {
                draft
            } else {
                draft.copy(aquariumId = aquariumId)
            }
        }
    }

    fun onTypeFilterSelected(type: TimelineEventType?) {
        selectedType.value = type
    }

    fun prepareQuickMemo() {
        val state = _uiState.value
        val preferredAquariumId = state.quickMemoDraft.aquariumId
            ?: state.selectedAquariumId
            ?: state.aquariumFilters.firstOrNull()?.aquariumId

        quickMemoDraft.update { draft ->
            draft.copy(
                aquariumId = preferredAquariumId,
                createdAtInput = draft.createdAtInput.ifBlank {
                    formatDateTimeInput(nowProvider(), zoneId)
                }
            )
        }
    }

    fun onQuickMemoAquariumSelected(aquariumId: String) {
        quickMemoDraft.update { draft -> draft.copy(aquariumId = aquariumId) }
    }

    fun onQuickMemoContentChanged(content: String) {
        quickMemoDraft.update { draft -> draft.copy(content = content) }
    }

    fun onQuickMemoCreatedAtChanged(createdAtInput: String) {
        quickMemoDraft.update { draft -> draft.copy(createdAtInput = createdAtInput) }
    }

    fun onQuickMemoPhotoUriChanged(photoUri: String?) {
        quickMemoDraft.update { draft ->
            draft.copy(photoUri = photoUri?.trim()?.takeIf { it.isNotEmpty() })
        }
    }

    fun saveQuickMemo() {
        val draft = quickMemoDraft.value
        val aquariumId = draft.aquariumId
        val content = draft.content.trim()
        val createdAt = parseTimelineDateTimeInput(draft.createdAtInput, zoneId)

        if (aquariumId == null) {
            statusMessage.value = "Add a tank before logging a memo."
            return
        }

        if (content.isBlank()) {
            statusMessage.value = "Write a memo before saving."
            return
        }

        if (createdAt == null) {
            statusMessage.value = timelineDateTimeErrorMessage
            return
        }

        viewModelScope.launch {
            runCatching {
                val memoId = idProvider()
                val eventId = idProvider()
                val createdAtIso = createdAt.toString()

                memoRepository.upsert(
                    Memo(
                        id = memoId,
                        aquariumId = aquariumId,
                        content = content,
                        createdAt = createdAtIso,
                        photoUri = draft.photoUri
                    )
                )
                timelineEventRepository.upsert(
                    TimelineEvent(
                        id = eventId,
                        aquariumId = aquariumId,
                        type = TimelineEventType.MEMO,
                        createdAt = createdAtIso,
                        title = "Memo",
                        description = content,
                        photoUri = draft.photoUri
                    )
                )
            }.onSuccess {
                quickMemoDraft.update {
                    it.copy(
                        content = "",
                        createdAtInput = formatDateTimeInput(nowProvider(), zoneId),
                        photoUri = null
                    )
                }
                val aquariumName = _uiState.value.aquariumFilters
                    .firstOrNull { it.aquariumId == aquariumId }
                    ?.aquariumName
                    ?: "tank"
                statusMessage.value = "Memo added to $aquariumName."
            }.onFailure { error ->
                statusMessage.value = error.message ?: "Unable to save memo."
            }
        }
    }

    private fun observeTimeline() {
        val baseDataFlow = combine(
            aquariumRepository.getAll(),
            timelineEventRepository.getAll()
        ) { aquariums, events ->
            TimelineBaseData(
                aquariums = aquariums,
                events = events
            )
        }

        viewModelScope.launch {
            combine(
                baseDataFlow,
                selectedAquariumId,
                selectedType,
                quickMemoDraft,
                statusMessage
            ) { base, aquariumId, type, draft, status ->
                assembleTimelineUiState(
                    aquariums = base.aquariums,
                    events = base.events,
                    selectedAquariumId = aquariumId,
                    selectedType = type,
                    quickMemoDraft = draft,
                    zoneId = zoneId,
                    statusMessage = status
                )
            }.collect { next ->
                _uiState.update { next.copy(isLoading = false) }
            }
        }
    }

    private data class TimelineBaseData(
        val aquariums: List<Aquarium>,
        val events: List<TimelineEvent>
    )

    companion object {
        fun factory(
            aquariumRepository: AquariumRepository,
            timelineEventRepository: TimelineEventRepository,
            memoRepository: MemoRepository
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(TimelineViewModel::class.java)) {
                        return TimelineViewModel(
                            aquariumRepository = aquariumRepository,
                            timelineEventRepository = timelineEventRepository,
                            memoRepository = memoRepository
                        ) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
    }
}

internal fun assembleTimelineUiState(
    aquariums: List<Aquarium>,
    events: List<TimelineEvent>,
    selectedAquariumId: String?,
    selectedType: TimelineEventType?,
    quickMemoDraft: TimelineQuickMemoDraft,
    zoneId: ZoneId,
    statusMessage: String?
): TimelineUiState {
    val aquariumFilters = aquariums
        .sortedBy { it.name.lowercase() }
        .map { TimelineAquariumFilter(it.id, it.name) }

    val aquariumNameById = aquariums.associate { it.id to it.name }
    val visibleEvents = events
        .asSequence()
        .filter { event -> selectedAquariumId == null || event.aquariumId == selectedAquariumId }
        .filter { event -> selectedType == null || event.type == selectedType }
        .sortedWith(compareByDescending<TimelineEvent> {
            parseToInstant(it.createdAt, zoneId)?.toEpochMilli() ?: Long.MIN_VALUE
        }.thenByDescending { it.createdAt })
        .map { event ->
            val dateLabel = formatDate(event.createdAt, zoneId)
            TimelineEventItem(
                id = event.id,
                aquariumId = event.aquariumId,
                aquariumName = aquariumNameById[event.aquariumId] ?: "Unknown tank",
                type = event.type,
                typeLabel = event.type.label(),
                title = event.title,
                description = event.description,
                createdAtLabel = formatDateTime(event.createdAt, zoneId),
                dateLabel = dateLabel,
                photoUri = event.photoUri,
                relatedCount = event.related.size + if (event.source == null) 0 else 1
            )
        }
        .toList()

    val dayGroups = visibleEvents
        .groupBy { it.dateLabel }
        .map { (dateLabel, items) ->
            TimelineDayGroup(dateLabel = dateLabel, events = items)
        }

    val headline = when {
        aquariums.isEmpty() -> "Add your first tank to start building a care history."
        events.isEmpty() -> "Your timeline is ready for imported activity and quick memos."
        visibleEvents.isEmpty() -> "No timeline entries match the current filters."
        selectedAquariumId != null || selectedType != null -> "${visibleEvents.size} event${visibleEvents.size.plural()} match the current filters."
        else -> "${visibleEvents.size} event${visibleEvents.size.plural()} across ${aquariums.size} tank${aquariums.size.plural()}."
    }

    return TimelineUiState(
        isEmpty = aquariums.isEmpty() || events.isEmpty(),
        headline = headline,
        selectedAquariumId = selectedAquariumId,
        selectedType = selectedType,
        summary = TimelineSummaryMetrics(
            eventCount = events.size,
            visibleEventCount = visibleEvents.size,
            aquariumCount = aquariums.size,
            memoCount = events.count { it.type == TimelineEventType.MEMO },
            issueCount = events.count { it.type == TimelineEventType.ISSUE },
            taskCount = events.count { it.type == TimelineEventType.TASK }
        ),
        aquariumFilters = aquariumFilters,
        dayGroups = dayGroups,
        quickMemoDraft = quickMemoDraft,
        statusMessage = statusMessage
    )
}

private fun TimelineEventType.label(): String =
    name.lowercase().replaceFirstChar { it.uppercaseChar() }

private fun Int.plural(): String = if (this == 1) "" else "s"

internal const val timelineDateTimeErrorMessage =
    "Use a valid memo time like 2026-04-11 18:30."

internal fun parseTimelineDateTimeInput(raw: String, zoneId: ZoneId): Instant? {
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

internal fun formatDateTimeInput(instant: Instant, zoneId: ZoneId): String {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    return formatter.format(instant.atZone(zoneId))
}

private fun formatDateTime(raw: String, zoneId: ZoneId): String {
    val instant = parseToInstant(raw, zoneId) ?: return raw
    return formatDateTimeInput(instant, zoneId)
}

private fun formatDate(raw: String, zoneId: ZoneId): String {
    val instant = parseToInstant(raw, zoneId) ?: return "Unknown date"
    return DateTimeFormatter.ISO_LOCAL_DATE.format(instant.atZone(zoneId))
}

private fun parseToInstant(raw: String, zoneId: ZoneId): Instant? {
    val value = raw.trim()
    if (value.isEmpty()) return null

    return runCatching { Instant.parse(value) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()
        ?: runCatching { LocalDateTime.parse(value).atZone(zoneId).toInstant() }.getOrNull()
        ?: runCatching { LocalDate.parse(value).atStartOfDay(zoneId).toInstant() }.getOrNull()
}
