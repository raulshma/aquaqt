package com.keepaside.aquapt.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.keepaside.aquapt.core.assistant.OpenRouterModel
import com.keepaside.aquapt.core.assistant.OpenRouterModelListingGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal const val MAX_MODELS_PER_GROUP = 40

enum class ModelBrowserSort {
    NAME,
    CREATED,
    CONTEXT
}

enum class ModelBrowserTarget {
    ASSISTANT,
    MEMORY
}

data class ModelBrowserGroupedModels(
    val free: List<OpenRouterModel> = emptyList(),
    val paid: List<OpenRouterModel> = emptyList()
)

data class ModelBrowserUiState(
    val isLoading: Boolean = false,
    val models: List<OpenRouterModel> = emptyList(),
    val query: String = "",
    val sort: ModelBrowserSort = ModelBrowserSort.NAME,
    val target: ModelBrowserTarget = ModelBrowserTarget.ASSISTANT,
    val errorMessage: String? = null,
    val selectedModelId: String? = null
) {
    val groupedModels: ModelBrowserGroupedModels
        get() {
            val filtered = filterModels(models, query)
            val sorted = sortModels(filtered, sort)
            val (free, paid) = partitionByPricing(sorted)
            return ModelBrowserGroupedModels(
                free = free.take(MAX_MODELS_PER_GROUP),
                paid = paid.take(MAX_MODELS_PER_GROUP)
            )
        }

    val summaryLabel: String
        get() {
            val grouped = groupedModels
            val total = grouped.free.size + grouped.paid.size
            val sortLabel = when (sort) {
                ModelBrowserSort.NAME -> "name"
                ModelBrowserSort.CREATED -> "created"
                ModelBrowserSort.CONTEXT -> "context"
            }
            return "$total models shown (${grouped.free.size} free, ${grouped.paid.size} paid, sort: $sortLabel)"
        }

    val isTruncated: Boolean
        get() {
            val grouped = groupedModels
            return grouped.free.size >= MAX_MODELS_PER_GROUP ||
                grouped.paid.size >= MAX_MODELS_PER_GROUP
        }
}

class SettingsModelBrowserViewModel(
    private val modelListingGateway: OpenRouterModelListingGateway,
    private val externalScope: CoroutineScope? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(ModelBrowserUiState())
    val uiState: StateFlow<ModelBrowserUiState> = _uiState.asStateFlow()

    fun loadModels() {
        if (_uiState.value.isLoading) return

        launchWork {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            runCatching {
                modelListingGateway.fetchModels()
            }.onSuccess { models ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        models = models.filter { m -> m.id.isNotEmpty() }
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Unable to load models."
                    )
                }
            }
        }
    }

    fun refreshModels() {
        launchWork {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            runCatching {
                modelListingGateway.fetchModels()
            }.onSuccess { models ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        models = models.filter { m -> m.id.isNotEmpty() }
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Unable to load models."
                    )
                }
            }
        }
    }

    fun onQueryChanged(value: String) {
        _uiState.update { it.copy(query = value) }
    }

    fun onSortChanged(value: ModelBrowserSort) {
        _uiState.update { it.copy(sort = value) }
    }

    fun onTargetChanged(value: ModelBrowserTarget) {
        _uiState.update { it.copy(target = value) }
    }

    fun onModelSelected(modelId: String) {
        _uiState.update { it.copy(selectedModelId = modelId) }
    }

    private fun launchWork(block: suspend () -> Unit): Job =
        (externalScope ?: viewModelScope).launch {
            block()
        }

    companion object {
        fun factory(
            modelListingGateway: OpenRouterModelListingGateway
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(SettingsModelBrowserViewModel::class.java)) {
                        return SettingsModelBrowserViewModel(
                            modelListingGateway = modelListingGateway
                        ) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
    }
}

internal fun filterModels(models: List<OpenRouterModel>, query: String): List<OpenRouterModel> {
    val trimmedQuery = query.trim().lowercase()
    if (trimmedQuery.isEmpty()) return models

    return models.filter { candidate ->
        "${candidate.id} ${candidate.name.orEmpty()}".lowercase().contains(trimmedQuery)
    }
}

internal fun sortModels(models: List<OpenRouterModel>, sort: ModelBrowserSort): List<OpenRouterModel> =
    when (sort) {
        ModelBrowserSort.NAME -> models.sortedBy { (it.name ?: it.id).lowercase() }
        ModelBrowserSort.CREATED -> models.sortedByDescending { it.created ?: 0L }
        ModelBrowserSort.CONTEXT -> models.sortedByDescending { it.contextLength ?: 0L }
    }

internal fun isFreeModel(model: OpenRouterModel): Boolean {
    if (model.id.lowercase().contains(":free")) return true
    val promptPrice = model.promptPrice
    val completionPrice = model.completionPrice
    if (promptPrice == null || completionPrice == null) return false
    return promptPrice == 0.0 && completionPrice == 0.0
}

internal fun partitionByPricing(models: List<OpenRouterModel>): Pair<List<OpenRouterModel>, List<OpenRouterModel>> {
    val free = mutableListOf<OpenRouterModel>()
    val paid = mutableListOf<OpenRouterModel>()
    for (model in models) {
        if (isFreeModel(model)) {
            free.add(model)
        } else {
            paid.add(model)
        }
    }
    return free to paid
}

internal fun formatModelCreatedDate(created: Long?): String {
    if (created == null) return "-"
    val epochSeconds = if (created > 1_000_000_000_000) created / 1000 else created
    return runCatching {
        val instant = java.time.Instant.ofEpochSecond(epochSeconds)
        java.time.format.DateTimeFormatter
            .ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)
            .withZone(java.time.ZoneId.systemDefault())
            .format(instant)
    }.getOrNull() ?: "-"
}
