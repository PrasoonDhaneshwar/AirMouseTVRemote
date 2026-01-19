package com.prasoon.airmousetv.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prasoon.airmousetv.data.model.DiscoveredTv
import com.prasoon.airmousetv.data.repository.RemoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RemoteUiState(
    val isDiscovering: Boolean = false,
    val discoveredTvs: List<DiscoveredTv> = emptyList(),
    val error: String? = null,
    val isRefreshing: Boolean = false
)

@HiltViewModel
class RemoteViewModel @Inject constructor(
    private val repository: RemoteRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RemoteUiState())
    val uiState: StateFlow<RemoteUiState> = _uiState.asStateFlow()
    private var tvCollectorJob: Job? = null  // Track the collector

    fun startDiscovery() {
        if (_uiState.value.isDiscovering) return

        _uiState.value = _uiState.value.copy(isDiscovering = true, error = null)

        // Cancel old collector, start new one
        tvCollectorJob?.cancel()
        tvCollectorJob = viewModelScope.launch {
            repository.discoveredTvs.collect { tvs ->
                _uiState.value = _uiState.value.copy(discoveredTvs = tvs)
            }
        }

        repository.startDiscovery()
    }

    fun retryDiscovery() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true, error = null)

            // Force repository clean restart
            repository.stopDiscovery()
            delay(800)  // Let NSD fully reset

            repository.startDiscovery()

            // Watch for timeout
            delay(5000)
            if (repository.discoveredTvs.value.isEmpty()) {
                _uiState.value = _uiState.value.copy(
                    isRefreshing = false,
                    error = "No TVs found. Try again or check Wi-Fi."
                )
            }
        }
    }

    fun stopDiscovery() {
        repository.stopDiscovery()
        _uiState.value = _uiState.value.copy(
            isDiscovering = false
        )
    }

    fun selectTv(tv: DiscoveredTv) {
        // TODO: Trigger pairing/selection use case
        // Navigate to RemoteScreen with selected TV
        _uiState.value = _uiState.value.copy(
            error = "Selected: ${tv.name} (${tv.host}:${tv.port})"
        )
    }

    override fun onCleared() {
        super.onCleared()
        tvCollectorJob?.cancel()
        repository.stopDiscovery()
        repository.releaseResources()
    }

    fun clearTvCache() {
        viewModelScope.launch {
            repository.clearPortCache()
            _uiState.value = _uiState.value.copy(error = "✅ TV cache cleared!")
        }
    }
}