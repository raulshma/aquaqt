package com.keepaside.aquapt.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.keepaside.aquapt.core.model.AssistantMemoryCompactionPreview
import com.keepaside.aquapt.core.model.AssistantMemorySnippet
import com.keepaside.aquapt.core.repository.AppSettingsStore
import com.keepaside.aquapt.core.repository.AssistantMemoryStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val settingsMemoryDefaultStatus = "Review and manage assistant memory snippets."
private const val settingsMemoryMaxVisibleSnippets = 30

data class SettingsMemorySnippetItem(
    val id: String,
    val content: String,
    val categoryLabel: String,
    val createdAtLabel: String,
    val sourceLabel: String?
)

data class SettingsMemoryUiState(
    val isLoading: Boolean = true,
    val isBusy: Boolean = false,
    val isPreviewing: Boolean = false,
    val isApplying: Boolean = false,
    val memoryEnabled: Boolean = false,
    val statusMessage: String = settingsMemoryDefaultStatus,
    val snippets: List<SettingsMemorySnippetItem> = emptyList(),
    val preview: AssistantMemoryCompactionPreview? = null
) {
    val canRefresh: Boolean
        get() = !isBusy && !isPreviewing && !isApplying

    val canClearAll: Boolean
        get() = memoryEnabled && snippets.isNotEmpty() && !isBusy && !isPreviewing && !isApplying

    val canPreviewCompaction: Boolean
        get() = memoryEnabled && snippets.size >= 2 && !isBusy && !isPreviewing && !isApplying

    val canApplyCompaction: Boolean
        get() = memoryEnabled &&
            !isBusy &&
            !isPreviewing &&
            !isApplying &&
            preview != null &&
            preview.facts.isNotEmpty()
}

class SettingsMemoryViewModel(
    private val appSettingsStore: AppSettingsStore,
    private val assistantMemoryStore: AssistantMemoryStore,
    private val externalScope: CoroutineScope? = null,
    private val zoneId: ZoneId = ZoneId.systemDefault()
) : ViewModel() {

    private val statusMessage = MutableStateFlow(settingsMemoryDefaultStatus)
    private val isBusy = MutableStateFlow(false)
    private val isPreviewing = MutableStateFlow(false)
    private val isApplying = MutableStateFlow(false)
    private val compactionPreview = MutableStateFlow<AssistantMemoryCompactionPreview?>(null)

    private val _uiState = MutableStateFlow(SettingsMemoryUiState())
    val uiState: StateFlow<SettingsMemoryUiState> = _uiState.asStateFlow()

    private var observerJob: Job? = null

    init {
        observerJob = observeUiState()
    }

    fun refreshSnippets() {
        if (!uiState.value.canRefresh) return
        compactionPreview.update { null }
        statusMessage.update { "Memory refreshed." }
    }

    fun setMemoryEnabled(enabled: Boolean) {
        if (isBusy.value) return

        launchWork {
            val currentSettings = appSettingsStore.settings.value
            if (currentSettings.assistantMemoryEnabled == enabled) {
                statusMessage.update {
                    if (enabled) {
                        "Assistant memory is already enabled."
                    } else {
                        "Assistant memory is already disabled."
                    }
                }
                return@launchWork
            }

            isBusy.update { true }
            try {
                appSettingsStore.setSettings(
                    currentSettings.copy(
                        assistantMemoryEnabled = enabled
                    )
                )

                if (!enabled) {
                    compactionPreview.update { null }
                }

                statusMessage.update {
                    if (enabled) {
                        "Assistant memory enabled."
                    } else {
                        "Assistant memory disabled."
                    }
                }
            } catch (error: Throwable) {
                statusMessage.update {
                    error.message?.takeIf { message -> message.isNotBlank() }
                        ?: "Could not update assistant memory setting."
                }
            } finally {
                isBusy.update { false }
            }
        }
    }

    fun forgetSnippet(snippetId: String) {
        if (isBusy.value) return

        val normalizedId = snippetId.trim()
        if (normalizedId.isEmpty()) {
            statusMessage.update { "Snippet id is missing." }
            return
        }

        launchWork {
            isBusy.update { true }
            try {
                assistantMemoryStore.forgetSnippet(normalizedId)
                compactionPreview.update { null }
                statusMessage.update { "Snippet removed from memory." }
            } catch (error: Throwable) {
                statusMessage.update {
                    error.message?.takeIf { message -> message.isNotBlank() }
                        ?: "Could not remove snippet."
                }
            } finally {
                isBusy.update { false }
            }
        }
    }

    fun clearAllSnippets() {
        if (!uiState.value.canClearAll) return

        launchWork {
            isBusy.update { true }
            try {
                assistantMemoryStore.clearAllSnippets()
                compactionPreview.update { null }
                statusMessage.update { "Assistant memory cleared." }
            } catch (error: Throwable) {
                statusMessage.update {
                    error.message?.takeIf { message -> message.isNotBlank() }
                        ?: "Could not clear assistant memory."
                }
            } finally {
                isBusy.update { false }
            }
        }
    }

    fun previewCompaction(maxFacts: Int = 10) {
        if (isBusy.value || isPreviewing.value || isApplying.value) return

        if (!appSettingsStore.settings.value.assistantMemoryEnabled) {
            statusMessage.update { "Enable assistant memory before previewing compaction." }
            return
        }

        launchWork {
            isPreviewing.update { true }
            try {
                val preview = assistantMemoryStore.previewCompaction(maxFacts = maxFacts)
                compactionPreview.update { preview }
                statusMessage.update {
                    when {
                        preview.beforeCount <= 0 -> "No memory snippets found to compact."
                        preview.afterCount <= 0 -> "No durable facts found in current snippets."
                        else -> "Preview ready: ${preview.beforeCount} → ${preview.afterCount} fact(s)."
                    }
                }
            } catch (error: Throwable) {
                statusMessage.update {
                    error.message?.takeIf { message -> message.isNotBlank() }
                        ?: "Could not preview memory compaction."
                }
            } finally {
                isPreviewing.update { false }
            }
        }
    }

    fun applyCompaction(maxFacts: Int = 10) {
        if (isBusy.value || isPreviewing.value || isApplying.value) return

        if (!appSettingsStore.settings.value.assistantMemoryEnabled) {
            statusMessage.update { "Enable assistant memory before applying compaction." }
            return
        }

        launchWork {
            isApplying.update { true }
            try {
                val precomputedFacts = compactionPreview.value?.facts.orEmpty()
                val result = assistantMemoryStore.applyCompaction(
                    precomputedFacts = precomputedFacts,
                    maxFacts = maxFacts
                )

                compactionPreview.update { result }
                statusMessage.update {
                    when {
                        result.beforeCount <= 0 -> "No memory snippets found to compact."
                        result.afterCount <= 0 -> "Compaction completed with no durable facts."
                        else -> "Compacted ${result.beforeCount} snippet(s) into ${result.afterCount} fact(s)."
                    }
                }
            } catch (error: Throwable) {
                statusMessage.update {
                    error.message?.takeIf { message -> message.isNotBlank() }
                        ?: "Could not apply memory compaction."
                }
            } finally {
                isApplying.update { false }
            }
        }
    }

    fun cancelCompactionPreview() {
        if (isBusy.value || isApplying.value) return
        compactionPreview.update { null }
        statusMessage.update { "Compaction preview canceled." }
    }

    private fun observeUiState(): Job = launchWork {
        combine(
            appSettingsStore.settings,
            assistantMemoryStore.snippets,
            statusMessage,
            isBusy,
            isPreviewing,
            isApplying,
            compactionPreview
        ) { values ->
            @Suppress("UNCHECKED_CAST")
            val settings = values[0] as com.keepaside.aquapt.core.model.AppSettings
            val snippets = values[1] as List<AssistantMemorySnippet>
            val status = values[2] as String
            val busy = values[3] as Boolean
            val previewing = values[4] as Boolean
            val applying = values[5] as Boolean
            val preview = values[6] as AssistantMemoryCompactionPreview?

            SettingsMemoryUiState(
                isLoading = false,
                isBusy = busy,
                isPreviewing = previewing,
                isApplying = applying,
                memoryEnabled = settings.assistantMemoryEnabled,
                statusMessage = status,
                snippets = snippets
                    .take(settingsMemoryMaxVisibleSnippets)
                    .map { snippet -> snippet.toUiItem(zoneId) },
                preview = preview
            )
        }.collect { next ->
            _uiState.update { next }
        }
    }

    private fun AssistantMemorySnippet.toUiItem(zoneId: ZoneId): SettingsMemorySnippetItem =
        SettingsMemorySnippetItem(
            id = id,
            content = content,
            categoryLabel = toCategoryLabel(category),
            createdAtLabel = formatCreatedAt(createdAt, zoneId),
            sourceLabel = buildSourceLabel(sourceConversationId, sourceMessageId)
        )

    private fun toCategoryLabel(category: String?): String {
        return when (category?.trim()?.lowercase()) {
            "manual" -> "Manual"
            "conversation_turn" -> "Conversation"
            "compacted_fact" -> "Compacted"
            else -> "Memory"
        }
    }

    private fun buildSourceLabel(
        conversationId: String?,
        messageId: String?
    ): String? {
        val normalizedConversationId = conversationId?.trim().orEmpty()
        val normalizedMessageId = messageId?.trim().orEmpty()
        if (normalizedConversationId.isEmpty() && normalizedMessageId.isEmpty()) {
            return null
        }

        val parts = mutableListOf<String>()
        if (normalizedConversationId.isNotEmpty()) {
            parts += "Conv ${normalizedConversationId.take(14)}"
        }
        if (normalizedMessageId.isNotEmpty()) {
            parts += "Msg ${normalizedMessageId.take(14)}"
        }
        return parts.joinToString(" • ")
    }

    private fun formatCreatedAt(value: String?, zoneId: ZoneId): String {
        val instant = runCatching { value?.let(Instant::parse) }.getOrNull()
            ?: return "Saved: -"
        return "Saved: ${settingsMemoryDateFormatter.format(instant.atZone(zoneId))}"
    }

    private fun launchWork(block: suspend () -> Unit): Job =
        (externalScope ?: viewModelScope).launch {
            block()
        }

    internal fun disposeForTests() {
        observerJob?.cancel()
    }

    companion object {
        private val settingsMemoryDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

        fun factory(
            appSettingsStore: AppSettingsStore,
            assistantMemoryStore: AssistantMemoryStore
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(SettingsMemoryViewModel::class.java)) {
                        return SettingsMemoryViewModel(
                            appSettingsStore = appSettingsStore,
                            assistantMemoryStore = assistantMemoryStore
                        ) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
    }
}
