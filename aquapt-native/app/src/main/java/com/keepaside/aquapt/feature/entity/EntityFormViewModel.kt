package com.keepaside.aquapt.feature.entity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.keepaside.aquapt.core.model.Aquarium
import com.keepaside.aquapt.core.model.EntityKind
import com.keepaside.aquapt.core.model.EntityRef
import com.keepaside.aquapt.core.model.Issue
import com.keepaside.aquapt.core.model.Memo
import com.keepaside.aquapt.core.model.TimelineEvent
import com.keepaside.aquapt.core.model.TimelineEventType
import com.keepaside.aquapt.core.repository.AquariumRepository
import com.keepaside.aquapt.core.repository.IssueRepository
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
    val memoPhotoUri: String = ""
)

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

                    else -> error("This form is not available for this entity type yet.")
                }
            }.onSuccess { successPrefix ->
                val aquariumName = state.aquariumName ?: "tank"
                statusMessage.value = "$successPrefix to $aquariumName."
                draftState.update { draft ->
                    draft.copy(
                        createdAtInput = formatEntityFormDateTimeInput(nowProvider(), zoneId),
                        issueTitle = "",
                        memoContent = "",
                        memoPhotoUri = "",
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
            "Capture notes and optional photo URI for this tank.",
            "Save memo"
        )

        else -> Triple(
            "New activity",
            "This route currently supports native issue and memo forms.",
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
        else -> "This form is not available for this entity type yet."
    }
}

internal fun isEntityFormSupported(kind: EntityKind?): Boolean =
    kind == EntityKind.ISSUE || kind == EntityKind.MEMO

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

private fun EntityKind?.label(): String =
    this?.name
        ?.lowercase()
        ?.replace('_', ' ')
        ?.replaceFirstChar { it.uppercaseChar() }
        ?: "Entity"

private fun aquariumRelatedRefs(aquariumId: String, vararg extras: EntityRef): List<EntityRef> =
    listOf(EntityRef(EntityKind.AQUARIUM, aquariumId, aquariumId)) + extras
