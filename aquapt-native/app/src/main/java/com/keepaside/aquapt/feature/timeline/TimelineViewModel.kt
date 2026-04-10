package com.keepaside.aquapt.feature.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.keepaside.aquapt.core.model.Aquarium
import com.keepaside.aquapt.core.model.DosingLog
import com.keepaside.aquapt.core.model.EntityKind
import com.keepaside.aquapt.core.model.EntityRef
import com.keepaside.aquapt.core.model.Issue
import com.keepaside.aquapt.core.model.IssueStatus
import com.keepaside.aquapt.core.model.Memo
import com.keepaside.aquapt.core.model.TimelineEvent
import com.keepaside.aquapt.core.model.TimelineEventType
import com.keepaside.aquapt.core.model.WaterParameterLog
import com.keepaside.aquapt.core.model.WaterParameters
import com.keepaside.aquapt.core.repository.AquariumRepository
import com.keepaside.aquapt.core.repository.DosingLogRepository
import com.keepaside.aquapt.core.repository.IssueRepository
import com.keepaside.aquapt.core.repository.MemoRepository
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

enum class TimelineQuickLogType(val label: String) {
    MEMO("Memo"),
    ISSUE("Issue"),
    PARAMETER("Parameters"),
    DOSING("Dosing")
}

enum class TimelineParameterField(val label: String) {
    AMMONIA("Ammonia"),
    NITRITE("Nitrite"),
    NITRATE("Nitrate"),
    PH("pH"),
    TEMPERATURE_C("Temperature C"),
    GH("GH"),
    KH("KH"),
    SALINITY("Salinity"),
    CALCIUM("Calcium"),
    ALKALINITY("Alkalinity")
}

data class TimelineQuickLogDraft(
    val type: TimelineQuickLogType = TimelineQuickLogType.MEMO,
    val aquariumId: String? = null,
    val createdAtInput: String = "",
    val memoContent: String = "",
    val photoUri: String? = null,
    val issueTitle: String = "",
    val dosingProduct: String = "",
    val dosingAmountMl: String = "",
    val dosingNote: String = "",
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
    val quickLogDraft: TimelineQuickLogDraft = TimelineQuickLogDraft(),
    val statusMessage: String? = null
)

class TimelineViewModel(
    private val aquariumRepository: AquariumRepository,
    private val timelineEventRepository: TimelineEventRepository,
    private val memoRepository: MemoRepository,
    private val issueRepository: IssueRepository,
    private val dosingLogRepository: DosingLogRepository,
    private val waterParameterLogRepository: WaterParameterLogRepository,
    private val nowProvider: () -> Instant = { Instant.now() },
    private val idProvider: () -> String = { UUID.randomUUID().toString() },
    private val zoneId: ZoneId = ZoneId.systemDefault()
) : ViewModel() {

    private val selectedAquariumId = MutableStateFlow<String?>(null)
    private val selectedType = MutableStateFlow<TimelineEventType?>(null)
    private val quickLogDraft = MutableStateFlow(TimelineQuickLogDraft())
    private val statusMessage = MutableStateFlow<String?>(null)
    private val _uiState = MutableStateFlow(TimelineUiState())
    val uiState: StateFlow<TimelineUiState> = _uiState.asStateFlow()

    init {
        observeTimeline()
    }

    fun onAquariumFilterSelected(aquariumId: String?) {
        selectedAquariumId.value = aquariumId
        quickLogDraft.update { draft ->
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

    fun prepareQuickLog() {
        val state = _uiState.value
        val preferredAquariumId = state.quickLogDraft.aquariumId
            ?: state.selectedAquariumId
            ?: state.aquariumFilters.firstOrNull()?.aquariumId

        quickLogDraft.update { draft ->
            draft.copy(
                aquariumId = preferredAquariumId,
                createdAtInput = draft.createdAtInput.ifBlank {
                    formatDateTimeInput(nowProvider(), zoneId)
                }
            )
        }
    }

    fun onQuickLogTypeSelected(type: TimelineQuickLogType) {
        quickLogDraft.update { draft -> draft.copy(type = type) }
    }

    fun onQuickLogAquariumSelected(aquariumId: String) {
        quickLogDraft.update { draft -> draft.copy(aquariumId = aquariumId) }
    }

    fun onQuickLogMemoContentChanged(content: String) {
        quickLogDraft.update { draft -> draft.copy(memoContent = content) }
    }

    fun onQuickLogIssueTitleChanged(title: String) {
        quickLogDraft.update { draft -> draft.copy(issueTitle = title) }
    }

    fun onQuickLogDosingProductChanged(product: String) {
        quickLogDraft.update { draft -> draft.copy(dosingProduct = product) }
    }

    fun onQuickLogDosingAmountChanged(amountMl: String) {
        quickLogDraft.update { draft -> draft.copy(dosingAmountMl = amountMl) }
    }

    fun onQuickLogDosingNoteChanged(note: String) {
        quickLogDraft.update { draft -> draft.copy(dosingNote = note) }
    }

    fun onQuickLogParameterValueChanged(field: TimelineParameterField, value: String) {
        quickLogDraft.update { draft ->
            when (field) {
                TimelineParameterField.AMMONIA -> draft.copy(ammonia = value)
                TimelineParameterField.NITRITE -> draft.copy(nitrite = value)
                TimelineParameterField.NITRATE -> draft.copy(nitrate = value)
                TimelineParameterField.PH -> draft.copy(ph = value)
                TimelineParameterField.TEMPERATURE_C -> draft.copy(temperatureC = value)
                TimelineParameterField.GH -> draft.copy(gh = value)
                TimelineParameterField.KH -> draft.copy(kh = value)
                TimelineParameterField.SALINITY -> draft.copy(salinity = value)
                TimelineParameterField.CALCIUM -> draft.copy(calcium = value)
                TimelineParameterField.ALKALINITY -> draft.copy(alkalinity = value)
            }
        }
    }

    fun onQuickLogCreatedAtChanged(createdAtInput: String) {
        quickLogDraft.update { draft -> draft.copy(createdAtInput = createdAtInput) }
    }

    fun onQuickLogPhotoUriChanged(photoUri: String?) {
        quickLogDraft.update { draft ->
            draft.copy(photoUri = photoUri?.trim()?.takeIf { it.isNotEmpty() })
        }
    }

    fun saveQuickLog() {
        val draft = quickLogDraft.value
        val aquariumId = draft.aquariumId
        val createdAt = parseTimelineDateTimeInput(draft.createdAtInput, zoneId)

        if (aquariumId == null) {
            statusMessage.value = "Add a tank before logging activity."
            return
        }

        if (createdAt == null) {
            statusMessage.value = timelineDateTimeErrorMessage(draft.type)
            return
        }

        val validationError = validateQuickLogDraft(draft)
        if (validationError != null) {
            statusMessage.value = validationError
            return
        }

        viewModelScope.launch {
            runCatching {
                val createdAtIso = createdAt.toString()
                saveQuickLogRecord(
                    draft = draft,
                    aquariumId = aquariumId,
                    createdAtIso = createdAtIso
                )
            }.onSuccess {
                quickLogDraft.update {
                    it.clearedAfterSave(formatDateTimeInput(nowProvider(), zoneId))
                }
                val aquariumName = _uiState.value.aquariumFilters
                    .firstOrNull { it.aquariumId == aquariumId }
                    ?.aquariumName
                    ?: "tank"
                statusMessage.value = "${draft.type.label} added to $aquariumName."
            }.onFailure { error ->
                statusMessage.value = error.message ?: "Unable to save activity."
            }
        }
    }

    private suspend fun saveQuickLogRecord(
        draft: TimelineQuickLogDraft,
        aquariumId: String,
        createdAtIso: String
    ) {
        when (draft.type) {
            TimelineQuickLogType.MEMO -> saveMemoLog(draft, aquariumId, createdAtIso)
            TimelineQuickLogType.ISSUE -> saveIssueLog(draft, aquariumId, createdAtIso)
            TimelineQuickLogType.PARAMETER -> saveParameterLog(draft, aquariumId, createdAtIso)
            TimelineQuickLogType.DOSING -> saveDosingLog(draft, aquariumId, createdAtIso)
        }
    }

    private suspend fun saveMemoLog(
        draft: TimelineQuickLogDraft,
        aquariumId: String,
        createdAtIso: String
    ) {
        val content = draft.memoContent.trim()
        val memoId = idProvider()

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
                id = idProvider(),
                aquariumId = aquariumId,
                type = TimelineEventType.MEMO,
                createdAt = createdAtIso,
                title = "Memo",
                description = content,
                photoUri = draft.photoUri,
                source = EntityRef(EntityKind.MEMO, memoId, aquariumId)
            )
        )
    }

    private suspend fun saveIssueLog(
        draft: TimelineQuickLogDraft,
        aquariumId: String,
        createdAtIso: String
    ) {
        val title = draft.issueTitle.trim()
        val issueId = idProvider()

        issueRepository.upsert(
            Issue(
                id = issueId,
                aquariumId = aquariumId,
                title = title,
                status = IssueStatus.OPEN,
                createdAt = createdAtIso
            )
        )
        timelineEventRepository.upsert(
            TimelineEvent(
                id = idProvider(),
                aquariumId = aquariumId,
                type = TimelineEventType.ISSUE,
                createdAt = createdAtIso,
                title = title,
                description = "Open issue",
                source = EntityRef(EntityKind.ISSUE, issueId, aquariumId)
            )
        )
    }

    private suspend fun saveParameterLog(
        draft: TimelineQuickLogDraft,
        aquariumId: String,
        createdAtIso: String
    ) {
        val values = draft.toWaterParameters() ?: error(parameterLogErrorMessage)
        val parameterLogId = idProvider()

        waterParameterLogRepository.upsert(
            WaterParameterLog(
                id = parameterLogId,
                aquariumId = aquariumId,
                createdAt = createdAtIso,
                values = values
            )
        )
        timelineEventRepository.upsert(
            TimelineEvent(
                id = idProvider(),
                aquariumId = aquariumId,
                type = TimelineEventType.PARAMETER,
                createdAt = createdAtIso,
                title = "Water parameters",
                description = values.summaryLabel(),
                source = EntityRef(EntityKind.PARAMETER_LOG, parameterLogId, aquariumId)
            )
        )
    }

    private suspend fun saveDosingLog(
        draft: TimelineQuickLogDraft,
        aquariumId: String,
        createdAtIso: String
    ) {
        val amountMl = parsePositiveAmountMl(draft.dosingAmountMl) ?: error(dosingAmountErrorMessage)
        val product = draft.dosingProduct.trim()
        val note = draft.dosingNote.trim().takeIf { it.isNotEmpty() }
        val dosingLogId = idProvider()

        dosingLogRepository.upsert(
            DosingLog(
                id = dosingLogId,
                aquariumId = aquariumId,
                product = product,
                amountMl = amountMl,
                createdAt = createdAtIso,
                note = note
            )
        )
        timelineEventRepository.upsert(
            TimelineEvent(
                id = idProvider(),
                aquariumId = aquariumId,
                type = TimelineEventType.DOSING,
                createdAt = createdAtIso,
                title = "Dosed $product",
                description = buildList {
                    add("${formatAmount(amountMl)} ml")
                    note?.let { add(it) }
                }.joinToString(" - "),
                source = EntityRef(EntityKind.DOSING, dosingLogId, aquariumId)
            )
        )
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
                quickLogDraft,
                statusMessage
            ) { base, aquariumId, type, draft, status ->
                assembleTimelineUiState(
                    aquariums = base.aquariums,
                    events = base.events,
                    selectedAquariumId = aquariumId,
                    selectedType = type,
                    quickLogDraft = draft,
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
            memoRepository: MemoRepository,
            issueRepository: IssueRepository,
            dosingLogRepository: DosingLogRepository,
            waterParameterLogRepository: WaterParameterLogRepository
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(TimelineViewModel::class.java)) {
                        return TimelineViewModel(
                            aquariumRepository = aquariumRepository,
                            timelineEventRepository = timelineEventRepository,
                            memoRepository = memoRepository,
                            issueRepository = issueRepository,
                            dosingLogRepository = dosingLogRepository,
                            waterParameterLogRepository = waterParameterLogRepository
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
    quickLogDraft: TimelineQuickLogDraft,
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
        quickLogDraft = quickLogDraft,
        statusMessage = statusMessage
    )
}

private fun TimelineEventType.label(): String =
    name.lowercase().replaceFirstChar { it.uppercaseChar() }

private fun Int.plural(): String = if (this == 1) "" else "s"

internal fun timelineDateTimeErrorMessage(type: TimelineQuickLogType): String =
    "Use a valid ${type.label.lowercase()} time like 2026-04-11 18:30."

internal const val parameterLogErrorMessage =
    "Enter at least one valid parameter value."

internal const val dosingAmountErrorMessage =
    "Enter a dosing amount greater than 0 ml."

internal fun validateQuickLogDraft(draft: TimelineQuickLogDraft): String? =
    when (draft.type) {
        TimelineQuickLogType.MEMO ->
            if (draft.memoContent.isBlank()) "Write a memo before saving." else null
        TimelineQuickLogType.ISSUE ->
            if (draft.issueTitle.isBlank()) "Name the issue before saving." else null
        TimelineQuickLogType.PARAMETER ->
            if (draft.toWaterParameters() == null) parameterLogErrorMessage else null
        TimelineQuickLogType.DOSING -> when {
            draft.dosingProduct.isBlank() -> "Name the dosing product before saving."
            parsePositiveAmountMl(draft.dosingAmountMl) == null -> dosingAmountErrorMessage
            else -> null
        }
    }

internal fun TimelineQuickLogDraft.parameterValue(field: TimelineParameterField): String =
    when (field) {
        TimelineParameterField.AMMONIA -> ammonia
        TimelineParameterField.NITRITE -> nitrite
        TimelineParameterField.NITRATE -> nitrate
        TimelineParameterField.PH -> ph
        TimelineParameterField.TEMPERATURE_C -> temperatureC
        TimelineParameterField.GH -> gh
        TimelineParameterField.KH -> kh
        TimelineParameterField.SALINITY -> salinity
        TimelineParameterField.CALCIUM -> calcium
        TimelineParameterField.ALKALINITY -> alkalinity
    }

internal fun TimelineQuickLogDraft.hasAnyParameterInput(): Boolean =
    TimelineParameterField.entries.any { field -> parameterValue(field).isNotBlank() }

internal fun TimelineQuickLogDraft.toWaterParameters(): WaterParameters? {
    if (!hasAnyParameterInput()) return null

    fun value(field: TimelineParameterField): Double? {
        val raw = parameterValue(field)
        return if (raw.isBlank()) null else parseFiniteDouble(raw)
    }

    val parsedValues = TimelineParameterField.entries.associateWith { field -> value(field) }
    if (parsedValues.any { (field, value) -> parameterValue(field).isNotBlank() && value == null }) {
        return null
    }

    return WaterParameters(
        ammonia = parsedValues[TimelineParameterField.AMMONIA],
        nitrite = parsedValues[TimelineParameterField.NITRITE],
        nitrate = parsedValues[TimelineParameterField.NITRATE],
        ph = parsedValues[TimelineParameterField.PH],
        temperatureC = parsedValues[TimelineParameterField.TEMPERATURE_C],
        gh = parsedValues[TimelineParameterField.GH],
        kh = parsedValues[TimelineParameterField.KH],
        salinity = parsedValues[TimelineParameterField.SALINITY],
        calcium = parsedValues[TimelineParameterField.CALCIUM],
        alkalinity = parsedValues[TimelineParameterField.ALKALINITY]
    )
}

internal fun TimelineQuickLogDraft.canAttemptSave(): Boolean =
    aquariumId != null && createdAtInput.isNotBlank() && when (type) {
        TimelineQuickLogType.MEMO -> memoContent.isNotBlank()
        TimelineQuickLogType.ISSUE -> issueTitle.isNotBlank()
        TimelineQuickLogType.PARAMETER -> hasAnyParameterInput()
        TimelineQuickLogType.DOSING -> dosingProduct.isNotBlank() && dosingAmountMl.isNotBlank()
    }

private fun TimelineQuickLogDraft.clearedAfterSave(createdAtInput: String): TimelineQuickLogDraft =
    copy(
        createdAtInput = createdAtInput,
        memoContent = "",
        photoUri = null,
        issueTitle = "",
        dosingProduct = "",
        dosingAmountMl = "",
        dosingNote = "",
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

private fun parseFiniteDouble(raw: String): Double? {
    val number = raw.trim().toDoubleOrNull() ?: return null
    return number.takeIf { !it.isNaN() && !it.isInfinite() }
}

internal fun parsePositiveAmountMl(raw: String): Double? =
    parseFiniteDouble(raw)?.takeIf { it > 0.0 }

private fun WaterParameters.summaryLabel(): String =
    listOfNotNull(
        ammonia?.let { "Ammonia ${formatAmount(it)}" },
        nitrite?.let { "Nitrite ${formatAmount(it)}" },
        nitrate?.let { "Nitrate ${formatAmount(it)}" },
        ph?.let { "pH ${formatAmount(it)}" },
        temperatureC?.let { "Temp ${formatAmount(it)} C" },
        gh?.let { "GH ${formatAmount(it)}" },
        kh?.let { "KH ${formatAmount(it)}" },
        salinity?.let { "Salinity ${formatAmount(it)}" },
        calcium?.let { "Calcium ${formatAmount(it)}" },
        alkalinity?.let { "Alkalinity ${formatAmount(it)}" }
    ).joinToString(", ")

private fun formatAmount(value: Double): String =
    if (value % 1.0 == 0.0) {
        value.toLong().toString()
    } else {
        value.toString()
    }

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
