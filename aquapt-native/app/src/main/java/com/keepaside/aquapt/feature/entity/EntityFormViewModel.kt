package com.keepaside.aquapt.feature.entity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.keepaside.aquapt.core.model.Aquarium
import com.keepaside.aquapt.core.model.DosingLog
import com.keepaside.aquapt.core.model.EntityKind
import com.keepaside.aquapt.core.model.EntityRef
import com.keepaside.aquapt.core.model.Issue
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

data class EntityFormAquariumOption(
    val id: String,
    val name: String,
    val isSelected: Boolean
)

data class EntityFormDraft(
    val aquariumId: String? = null,
    val createdAtInput: String = "",
    val issueTitle: String = "",
    val memoContent: String = "",
    val memoPhotoUri: String = "",
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

enum class EntityFormParameterField(val label: String) {
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

data class EntityFormUiState(
    val isLoading: Boolean = true,
    val kind: EntityKind? = null,
    val kindLabel: String = "Entity",
    val headline: String = "New activity",
    val supportingText: String = "",
    val saveButtonLabel: String = "Save",
    val aquariumId: String? = null,
    val aquariumName: String? = null,
    val aquariumOptions: List<EntityFormAquariumOption> = emptyList(),
    val draft: EntityFormDraft = EntityFormDraft(),
    val isUnsupportedKind: Boolean = false,
    val isSaving: Boolean = false,
    val canSave: Boolean = false,
    val statusMessage: String? = null
)

class EntityFormViewModel(
    private val kind: EntityKind?,
    aquariumId: String?,
    private val aquariumRepository: AquariumRepository,
    private val issueRepository: IssueRepository,
    private val memoRepository: MemoRepository,
    private val dosingLogRepository: DosingLogRepository,
    private val waterParameterLogRepository: WaterParameterLogRepository,
    private val timelineEventRepository: TimelineEventRepository,
    private val nowProvider: () -> Instant = { Instant.now() },
    private val idProvider: () -> String = { UUID.randomUUID().toString() },
    private val zoneId: ZoneId = ZoneId.systemDefault()
) : ViewModel() {

    private val draftState = MutableStateFlow(
        EntityFormDraft(
            aquariumId = aquariumId,
            createdAtInput = formatEntityFormDateTimeInput(nowProvider(), zoneId)
        )
    )
    private val statusMessage = MutableStateFlow<String?>(null)
    private val isSaving = MutableStateFlow(false)

    private val _uiState = MutableStateFlow(EntityFormUiState())
    val uiState: StateFlow<EntityFormUiState> = _uiState.asStateFlow()

    init {
        observeFormState()
    }

    fun onAquariumSelected(aquariumId: String) {
        draftState.update { draft -> draft.copy(aquariumId = aquariumId) }
    }

    fun onCreatedAtInputChanged(input: String) {
        draftState.update { draft -> draft.copy(createdAtInput = input) }
    }

    fun onIssueTitleChanged(input: String) {
        draftState.update { draft -> draft.copy(issueTitle = input) }
    }

    fun onMemoContentChanged(input: String) {
        draftState.update { draft -> draft.copy(memoContent = input) }
    }

    fun onMemoPhotoUriChanged(input: String) {
        draftState.update { draft -> draft.copy(memoPhotoUri = input) }
    }

    fun onDosingProductChanged(input: String) {
        draftState.update { draft -> draft.copy(dosingProduct = input) }
    }

    fun onDosingAmountMlChanged(input: String) {
        draftState.update { draft -> draft.copy(dosingAmountMl = input) }
    }

    fun onDosingNoteChanged(input: String) {
        draftState.update { draft -> draft.copy(dosingNote = input) }
    }

    fun onParameterValueChanged(field: EntityFormParameterField, value: String) {
        draftState.update { draft ->
            when (field) {
                EntityFormParameterField.AMMONIA -> draft.copy(ammonia = value)
                EntityFormParameterField.NITRITE -> draft.copy(nitrite = value)
                EntityFormParameterField.NITRATE -> draft.copy(nitrate = value)
                EntityFormParameterField.PH -> draft.copy(ph = value)
                EntityFormParameterField.TEMPERATURE_C -> draft.copy(temperatureC = value)
                EntityFormParameterField.GH -> draft.copy(gh = value)
                EntityFormParameterField.KH -> draft.copy(kh = value)
                EntityFormParameterField.SALINITY -> draft.copy(salinity = value)
                EntityFormParameterField.CALCIUM -> draft.copy(calcium = value)
                EntityFormParameterField.ALKALINITY -> draft.copy(alkalinity = value)
            }
        }
    }

    fun save() {
        val state = _uiState.value
        val aquariumId = state.aquariumId

        val validationError = validateEntityFormDraft(
            kind = kind,
            draft = state.draft,
            aquariumId = aquariumId,
            zoneId = zoneId
        )

        if (validationError != null) {
            statusMessage.value = validationError
            return
        }

        val createdAt = parseEntityFormDateTimeInput(state.draft.createdAtInput, zoneId)
        if (createdAt == null) {
            statusMessage.value = entityFormDateTimeErrorMessage
            return
        }

        viewModelScope.launch {
            isSaving.update { true }

            runCatching {
                when (kind) {
                    EntityKind.ISSUE -> {
                        val issueTitle = state.draft.issueTitle.trim()
                        saveIssue(
                            aquariumId = aquariumId ?: error("Choose a tank before saving."),
                            title = issueTitle,
                            createdAtIso = createdAt.toString()
                        )
                        "Issue added"
                    }

                    EntityKind.MEMO -> {
                        val memoContent = state.draft.memoContent.trim()
                        val photoUri = normalizeEntityFormPhotoUri(state.draft.memoPhotoUri)
                        saveMemo(
                            aquariumId = aquariumId ?: error("Choose a tank before saving."),
                            content = memoContent,
                            photoUri = photoUri,
                            createdAtIso = createdAt.toString()
                        )
                        "Memo added"
                    }

                    EntityKind.DOSING -> {
                        val product = state.draft.dosingProduct.trim()
                        val amountMl = parseEntityFormPositiveAmountMl(state.draft.dosingAmountMl)
                            ?: error(entityFormDosingAmountErrorMessage)
                        val note = state.draft.dosingNote.trim().takeIf { it.isNotEmpty() }
                        saveDosingLog(
                            aquariumId = aquariumId ?: error("Choose a tank before saving."),
                            product = product,
                            amountMl = amountMl,
                            note = note,
                            createdAtIso = createdAt.toString()
                        )
                        "Dosing log added"
                    }

                    EntityKind.PARAMETER_LOG -> {
                        val values = state.draft.toWaterParameters()
                            ?: error(entityFormParameterErrorMessage)
                        saveParameterLog(
                            aquariumId = aquariumId ?: error("Choose a tank before saving."),
                            values = values,
                            createdAtIso = createdAt.toString()
                        )
                        "Parameter log added"
                    }

                    else -> error("This form is not available for this entity type yet.")
                }
            }.onSuccess { successPrefix ->
                val aquariumName = state.aquariumName ?: "tank"
                statusMessage.value = "$successPrefix to $aquariumName."
                draftState.update { draft ->
                    draft.clearedAfterSave(
                        createdAtInput = formatEntityFormDateTimeInput(nowProvider(), zoneId),
                        aquariumId = aquariumId
                    )
                }
            }.onFailure { error ->
                statusMessage.value = error.message ?: "Unable to save activity."
            }

            isSaving.update { false }
        }
    }

    private suspend fun saveIssue(
        aquariumId: String,
        title: String,
        createdAtIso: String
    ) {
        val issueId = idProvider()
        issueRepository.upsert(
            Issue(
                id = issueId,
                aquariumId = aquariumId,
                title = title,
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
                source = EntityRef(EntityKind.ISSUE, issueId, aquariumId),
                related = aquariumRelatedRefs(aquariumId)
            )
        )
    }

    private suspend fun saveMemo(
        aquariumId: String,
        content: String,
        photoUri: String?,
        createdAtIso: String
    ) {
        val memoId = idProvider()
        memoRepository.upsert(
            Memo(
                id = memoId,
                aquariumId = aquariumId,
                content = content,
                createdAt = createdAtIso,
                photoUri = photoUri
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
                photoUri = photoUri,
                source = EntityRef(EntityKind.MEMO, memoId, aquariumId),
                related = aquariumRelatedRefs(aquariumId)
            )
        )
    }

    private suspend fun saveDosingLog(
        aquariumId: String,
        product: String,
        amountMl: Double,
        note: String?,
        createdAtIso: String
    ) {
        val dosingId = idProvider()
        dosingLogRepository.upsert(
            DosingLog(
                id = dosingId,
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
                    add("${formatEntityAmount(amountMl)} ml")
                    note?.let { add(it) }
                }.joinToString(" - "),
                source = EntityRef(EntityKind.DOSING, dosingId, aquariumId),
                related = aquariumRelatedRefs(aquariumId)
            )
        )
    }

    private suspend fun saveParameterLog(
        aquariumId: String,
        values: WaterParameters,
        createdAtIso: String
    ) {
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
                source = EntityRef(EntityKind.PARAMETER_LOG, parameterLogId, aquariumId),
                related = aquariumRelatedRefs(aquariumId)
            )
        )
    }

    private fun observeFormState() {
        viewModelScope.launch {
            combine(
                aquariumRepository.getAll(),
                draftState,
                statusMessage,
                isSaving
            ) { aquariums, draft, status, saving ->
                assembleEntityFormUiState(
                    kind = kind,
                    draft = draft,
                    aquariums = aquariums,
                    isSaving = saving,
                    statusMessage = status,
                    zoneId = zoneId
                )
            }.collect { next ->
                _uiState.update { next.copy(isLoading = false) }
            }
        }
    }

    companion object {
        fun factory(
            kind: EntityKind?,
            aquariumId: String?,
            aquariumRepository: AquariumRepository,
            issueRepository: IssueRepository,
            memoRepository: MemoRepository,
            dosingLogRepository: DosingLogRepository,
            waterParameterLogRepository: WaterParameterLogRepository,
            timelineEventRepository: TimelineEventRepository
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(EntityFormViewModel::class.java)) {
                        return EntityFormViewModel(
                            kind = kind,
                            aquariumId = aquariumId,
                            aquariumRepository = aquariumRepository,
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

internal const val entityFormDateTimeErrorMessage =
    "Use a valid date/time like 2026-04-11 18:30."

internal const val entityFormParameterErrorMessage =
    "Enter at least one valid parameter value."

internal const val entityFormDosingAmountErrorMessage =
    "Enter a dosing amount greater than 0 ml."

internal fun assembleEntityFormUiState(
    kind: EntityKind?,
    draft: EntityFormDraft,
    aquariums: List<Aquarium>,
    isSaving: Boolean,
    statusMessage: String?,
    zoneId: ZoneId
): EntityFormUiState {
    val sortedAquariums = aquariums.sortedBy { it.name.lowercase() }
    val requestedAquariumId = draft.aquariumId?.takeIf { id -> sortedAquariums.any { it.id == id } }
    val aquariumId = requestedAquariumId ?: sortedAquariums.firstOrNull()?.id
    val aquariumName = sortedAquariums.firstOrNull { it.id == aquariumId }?.name

    val supported = isEntityFormSupported(kind)
    val validationError = validateEntityFormDraft(kind, draft, aquariumId, zoneId)

    val (headline, supportingText, saveLabel) = when (kind) {
        EntityKind.ISSUE -> Triple(
            "New issue",
            "Capture an issue for this tank and add it to the timeline.",
            "Save issue"
        )

        EntityKind.MEMO -> Triple(
            "New memo",
            "Capture notes and optional photo for this tank.",
            "Save memo"
        )

        EntityKind.DOSING -> Triple(
            "New dosing log",
            "Track dosing amount, product, and optional notes.",
            "Save dosing"
        )

        EntityKind.PARAMETER_LOG -> Triple(
            "New parameter log",
            "Capture one or more water-test values for this tank.",
            "Save parameters"
        )

        else -> Triple(
            "New activity",
            "This route currently supports issue, memo, dosing, and parameter forms.",
            "Save"
        )
    }

    val fallbackStatus = when {
        sortedAquariums.isEmpty() -> "Add a tank before creating activity."
        !supported -> "This form is not available for this entity type yet."
        else -> null
    }

    return EntityFormUiState(
        kind = kind,
        kindLabel = kind.label(),
        headline = headline,
        supportingText = supportingText,
        saveButtonLabel = saveLabel,
        aquariumId = aquariumId,
        aquariumName = aquariumName,
        aquariumOptions = sortedAquariums.map { aquarium ->
            EntityFormAquariumOption(
                id = aquarium.id,
                name = aquarium.name,
                isSelected = aquarium.id == aquariumId
            )
        },
        draft = draft,
        isUnsupportedKind = !supported,
        isSaving = isSaving,
        canSave = !isSaving && validationError == null,
        statusMessage = statusMessage ?: fallbackStatus
    )
}

internal fun validateEntityFormDraft(
    kind: EntityKind?,
    draft: EntityFormDraft,
    aquariumId: String?,
    zoneId: ZoneId
): String? {
    if (!isEntityFormSupported(kind)) {
        return "This form is not available for this entity type yet."
    }

    if (aquariumId.isNullOrBlank()) {
        return "Choose a tank before saving."
    }

    if (parseEntityFormDateTimeInput(draft.createdAtInput, zoneId) == null) {
        return entityFormDateTimeErrorMessage
    }

    return when (kind) {
        EntityKind.ISSUE -> if (draft.issueTitle.trim().isBlank()) "Name the issue before saving." else null
        EntityKind.MEMO -> if (draft.memoContent.trim().isBlank()) "Write a memo before saving." else null
        EntityKind.DOSING -> when {
            draft.dosingProduct.trim().isBlank() -> "Name the dosing product before saving."
            parseEntityFormPositiveAmountMl(draft.dosingAmountMl) == null -> entityFormDosingAmountErrorMessage
            else -> null
        }
        EntityKind.PARAMETER_LOG -> if (draft.toWaterParameters() == null) entityFormParameterErrorMessage else null
        else -> "This form is not available for this entity type yet."
    }
}

internal fun isEntityFormSupported(kind: EntityKind?): Boolean =
    kind == EntityKind.ISSUE ||
        kind == EntityKind.MEMO ||
        kind == EntityKind.DOSING ||
        kind == EntityKind.PARAMETER_LOG

internal fun normalizeEntityFormPhotoUri(raw: String?): String? =
    raw?.trim()?.takeIf { it.isNotEmpty() }

internal fun parseEntityFormDateTimeInput(raw: String, zoneId: ZoneId): Instant? {
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

internal fun formatEntityFormDateTimeInput(instant: Instant, zoneId: ZoneId): String {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    return formatter.format(instant.atZone(zoneId))
}

internal fun EntityFormDraft.parameterValue(field: EntityFormParameterField): String =
    when (field) {
        EntityFormParameterField.AMMONIA -> ammonia
        EntityFormParameterField.NITRITE -> nitrite
        EntityFormParameterField.NITRATE -> nitrate
        EntityFormParameterField.PH -> ph
        EntityFormParameterField.TEMPERATURE_C -> temperatureC
        EntityFormParameterField.GH -> gh
        EntityFormParameterField.KH -> kh
        EntityFormParameterField.SALINITY -> salinity
        EntityFormParameterField.CALCIUM -> calcium
        EntityFormParameterField.ALKALINITY -> alkalinity
    }

private fun EntityFormDraft.hasAnyParameterInput(): Boolean =
    EntityFormParameterField.entries.any { field -> parameterValue(field).isNotBlank() }

internal fun EntityFormDraft.toWaterParameters(): WaterParameters? {
    if (!hasAnyParameterInput()) return null

    fun value(field: EntityFormParameterField): Double? {
        val raw = parameterValue(field)
        return if (raw.isBlank()) null else parseFiniteEntityFormDouble(raw)
    }

    val parsedValues = EntityFormParameterField.entries.associateWith { field -> value(field) }
    if (parsedValues.any { (field, value) -> parameterValue(field).isNotBlank() && value == null }) {
        return null
    }

    return WaterParameters(
        ammonia = parsedValues[EntityFormParameterField.AMMONIA],
        nitrite = parsedValues[EntityFormParameterField.NITRITE],
        nitrate = parsedValues[EntityFormParameterField.NITRATE],
        ph = parsedValues[EntityFormParameterField.PH],
        temperatureC = parsedValues[EntityFormParameterField.TEMPERATURE_C],
        gh = parsedValues[EntityFormParameterField.GH],
        kh = parsedValues[EntityFormParameterField.KH],
        salinity = parsedValues[EntityFormParameterField.SALINITY],
        calcium = parsedValues[EntityFormParameterField.CALCIUM],
        alkalinity = parsedValues[EntityFormParameterField.ALKALINITY]
    )
}

internal fun parseEntityFormPositiveAmountMl(raw: String): Double? =
    parseFiniteEntityFormDouble(raw)?.takeIf { it > 0.0 }

private fun parseFiniteEntityFormDouble(raw: String): Double? {
    val number = raw.trim().toDoubleOrNull() ?: return null
    return number.takeIf { !it.isNaN() && !it.isInfinite() }
}

private fun WaterParameters.summaryLabel(): String =
    listOfNotNull(
        ammonia?.let { "Ammonia ${formatEntityAmount(it)}" },
        nitrite?.let { "Nitrite ${formatEntityAmount(it)}" },
        nitrate?.let { "Nitrate ${formatEntityAmount(it)}" },
        ph?.let { "pH ${formatEntityAmount(it)}" },
        temperatureC?.let { "Temp ${formatEntityAmount(it)} C" },
        gh?.let { "GH ${formatEntityAmount(it)}" },
        kh?.let { "KH ${formatEntityAmount(it)}" },
        salinity?.let { "Salinity ${formatEntityAmount(it)}" },
        calcium?.let { "Calcium ${formatEntityAmount(it)}" },
        alkalinity?.let { "Alkalinity ${formatEntityAmount(it)}" }
    ).joinToString(", ")

private fun EntityFormDraft.clearedAfterSave(
    createdAtInput: String,
    aquariumId: String?
): EntityFormDraft =
    copy(
        createdAtInput = createdAtInput,
        aquariumId = aquariumId,
        issueTitle = "",
        memoContent = "",
        memoPhotoUri = "",
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

private fun formatEntityAmount(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

private fun EntityKind?.label(): String =
    this?.name
        ?.lowercase()
        ?.replace('_', ' ')
        ?.replaceFirstChar { it.uppercaseChar() }
        ?: "Entity"

private fun aquariumRelatedRefs(aquariumId: String, vararg extras: EntityRef): List<EntityRef> =
    listOf(EntityRef(EntityKind.AQUARIUM, aquariumId, aquariumId)) + extras
