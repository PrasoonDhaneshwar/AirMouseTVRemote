package com.prasoon.airmousetv.data.model

import com.prasoon.airmousetv.airmouse.model.AirMouseUiState

/** Which top-level screen is showing. */
sealed interface RemoteMode {
    /** Looking for TVs on the network. */
    object Discovery : RemoteMode

    /** Connecting to [tv], then pairing with it if it doesn't already trust this app. */
    data class Pairing(val tv: DiscoveredTv) : RemoteMode

    /** Remote session with [tv] is active. */
    data class Connected(val tv: DiscoveredTv) : RemoteMode
}

/** Everything the UI renders, held in [com.prasoon.airmousetv.presentation.RemoteViewModel]. */
data class RemoteUiState(
    val mode: RemoteMode = RemoteMode.Discovery,
    val discoveredTvs: List<DiscoveredTv> = emptyList(),
    val isDiscovering: Boolean = false,
    val isRefreshing: Boolean = false,
    /** True while the selected TV is being checked for reachability, before the pairing screen opens. */
    val isCheckingTv: Boolean = false,
    /** True while the saved TV answers on the network but its remote service isn't up yet (a TV still starting). */
    val savedTvStarting: Boolean = false,
    /** True once the saved TV has not answered at all for a couple of minutes: it may be on another network or band. */
    val savedTvOffNetwork: Boolean = false,
    /** Code typed by the user: up to 6 hex digits. */
    val pairingCode: String = "",
    /** True once the TV is showing its code, so the screen swaps the spinner for the code field. */
    val awaitingCode: Boolean = false,
    /** True for a moment after the TV turned the typed code down: the boxes show it, then empty themselves. */
    val codeRejected: Boolean = false,
    /** True for a moment after a code was accepted, while the pairing screen shows its tick before the remote opens. */
    val paired: Boolean = false,
    /** True while the Paired screen offers the air mouse and waits for an answer instead of opening the remote by itself. */
    val pairedOffer: Boolean = false,
    val error: String? = null,
    /** True while the app is re-establishing a dropped session; the remote stays on screen, disabled. */
    val isReconnecting: Boolean = false,
    /** The text field focused on the TV, or null if it has not reported one. */
    val imeField: ImeField? = null,
    /** Package of the app in front on the TV, or null if it has not reported one. */
    val foregroundApp: String? = null,
    /** True when the connected TV is known to leave the network once switched off, so the app can't turn it back on. */
    val powerOffIsFinal: Boolean = false,
    /** The pointer for the connected TV; all defaults while no TV is connected. */
    val airMouse: AirMouseUiState = AirMouseUiState(),
)
