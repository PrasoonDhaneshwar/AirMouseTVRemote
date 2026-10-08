package com.prasoon.airmousetv.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prasoon.airmousetv.data.model.ConnectionState
import com.prasoon.airmousetv.data.model.DiscoveredTv
import com.prasoon.airmousetv.data.model.RemoteMode
import com.prasoon.airmousetv.data.model.RemoteUiState
import com.prasoon.airmousetv.data.model.TvKey
import com.prasoon.airmousetv.data.repository.RemoteRepository
import com.prasoon.airmousetv.data.repository.RemoteSessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "RemoteViewModel"

/**
 * Drives the whole UI flow: discovery -> connect/pair -> remote.
 * Discovery goes through [RemoteRepository]; the connection itself through [RemoteSessionManager].
 */
@HiltViewModel
class RemoteViewModel @Inject constructor(
    private val repository: RemoteRepository,
    private val remoteSession: RemoteSessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(RemoteUiState())
    val uiState: StateFlow<RemoteUiState> = _uiState.asStateFlow()
    /** Collects the repository's TV list into [uiState]; replaced on each discovery restart. */
    private var tvCollectorJob: Job? = null  // Track the collector

    init {
        observeConnectionState()
    }

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

    /** Translates [ConnectionState] changes from the session into UI state. */
    private fun observeConnectionState() {
        viewModelScope.launch {
            remoteSession.connectionState.collect { state ->
                when (state) {
                    ConnectionState.Connected -> {
                        val tv = (_uiState.value.mode as? RemoteMode.Pairing)?.tv
                        if (tv != null) {
                            _uiState.value = _uiState.value.copy(
                                mode = RemoteMode.Connected(tv)
                            )
                        }
                    }
                    ConnectionState.AwaitingCode -> {
                        _uiState.value = _uiState.value.copy(awaitingCode = true)
                    }
                    is ConnectionState.Error -> {
                        _uiState.value = _uiState.value.copy(error = state.message)
                    }
                    ConnectionState.Disconnected -> {
                        if (_uiState.value.mode is RemoteMode.Connected) {
                            _uiState.value = _uiState.value.copy(
                                mode = RemoteMode.Discovery,
                                isDiscovering = false,
                                awaitingCode = false,
                                pairingCode = "",
                                error = "Connection to TV lost"
                            )
                        }
                    }
                    else -> Unit
                }
    }
    }
    }


    /**
     * Starts connecting to [tv]. If the TV already trusts this app the remote opens directly;
     * otherwise the TV shows a code and [awaitingCode][RemoteUiState.awaitingCode] turns true.
     */
    fun selectTv(tv: DiscoveredTv) {
        Log.i(TAG, "📺 TV selected: ${tv.displayName}")

        _uiState.value = _uiState.value.copy(
            mode = RemoteMode.Pairing(tv),
            awaitingCode = false,
            pairingCode = "",
            error = null
        )

        viewModelScope.launch {
            try {
                // Reconnects straight away if the TV already trusts us, otherwise starts pairing
                remoteSession.connect(tv.host, "AirMouseTV")
    } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                    error = "Connection failed: ${e.message}"
                )
    }
    }
    }

    /** Keeps only hex digits, upper-cased and capped at 6, and clears any shown error. */
    fun updatePairingCode(code: String) {
        // Pairing codes are 6 hex digits
        val hex = code.uppercase().filter { it in '0'..'9' || it in 'A'..'F' }.take(6)
        _uiState.value = _uiState.value.copy(pairingCode = hex, error = null)
    }

    /** Sends the typed code to the TV; ignored until all 6 digits are entered. */
    fun submitPairingCode() {
        val code = _uiState.value.pairingCode
        if (code.length != 6) return  // 6-digit hex code

        viewModelScope.launch {
            remoteSession.submitPairingCode(code)
            Log.d(TAG, "Submitted pairing code: $code")
    }
    }


    /** Abandons the connection attempt and returns to discovery. */
    fun cancelPairing() {
        remoteSession.close()
        _uiState.value = _uiState.value.copy(
            mode = RemoteMode.Discovery,
            isDiscovering = false,
            awaitingCode = false,
            pairingCode = "",
            error = null
        )
    }

    /** Sends one key tap; failures are surfaced through [RemoteUiState.error]. */
    fun sendKey(key: TvKey) {
        viewModelScope.launch {
            try {
                repository.sendKey(key)
            } catch (e: Exception) {
                Log.e(TAG, "sendKey failed", e)
                _uiState.value = _uiState.value.copy(error = "Send failed: ${e.message}")
            }
        }
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