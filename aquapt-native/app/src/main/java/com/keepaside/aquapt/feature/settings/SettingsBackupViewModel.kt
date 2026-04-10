package com.keepaside.aquapt.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.keepaside.aquapt.core.backup.BackupCompatibilityGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val DEFAULT_STATUS =
    "Export your current Room state to RN-compatible JSON, or import a previous backup payload."

data class SettingsBackupUiState(
    val payload: String = "",
    val replaceExisting: Boolean = true,
    val isBusy: Boolean = false,
    val statusMessage: String = DEFAULT_STATUS
)

class SettingsBackupViewModel(
    private val backupGateway: BackupCompatibilityGateway,
    private val externalScope: CoroutineScope? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsBackupUiState())
    val uiState: StateFlow<SettingsBackupUiState> = _uiState.asStateFlow()

    fun onPayloadChanged(value: String) {
        _uiState.update { it.copy(payload = value) }
    }

    fun onReplaceExistingChanged(value: Boolean) {
        _uiState.update { it.copy(replaceExisting = value) }
    }

    fun exportJson() {
        if (_uiState.value.isBusy) return

        launchWork {
            _uiState.update { it.copy(isBusy = true) }

            runCatching {
                backupGateway.exportCurrentStateJson(pretty = true)
            }.onSuccess { exported ->
                _uiState.update {
                    it.copy(
                        payload = exported,
                        statusMessage = "Export completed. JSON payload loaded into the editor below."
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(statusMessage = error.message ?: "Export failed.")
                }
            }

            _uiState.update { it.copy(isBusy = false) }
        }
    }

    fun importJson() {
        val current = _uiState.value
        if (current.isBusy) return

        if (current.payload.isBlank()) {
            _uiState.update {
                it.copy(statusMessage = "Import payload is empty. Paste a backup JSON first.")
            }
            return
        }

        launchWork {
            _uiState.update { it.copy(isBusy = true) }

            runCatching {
                backupGateway.importFromJson(
                    payload = _uiState.value.payload,
                    replaceExisting = _uiState.value.replaceExisting
                )
            }.onSuccess { result ->
                val skippedSummary = if (result.skippedCounts.isEmpty()) {
                    "No skipped records."
                } else {
                    result.skippedCounts.entries.joinToString(
                        prefix = "Skipped -> ",
                        separator = ", "
                    ) { (kind, count) -> "$kind: $count" }
                }

                _uiState.update {
                    it.copy(statusMessage = "Import completed. $skippedSummary")
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(statusMessage = error.message ?: "Import failed.")
                }
            }

            _uiState.update { it.copy(isBusy = false) }
        }
    }

    private fun launchWork(block: suspend () -> Unit) {
        (externalScope ?: viewModelScope).launch {
            block()
        }
    }

    companion object {
        fun factory(backupGateway: BackupCompatibilityGateway): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(SettingsBackupViewModel::class.java)) {
                        return SettingsBackupViewModel(backupGateway) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
    }
}
