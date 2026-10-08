package com.prasoon.airmousetv.data.model

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
    /** Code typed by the user: up to 6 hex digits. */
    val pairingCode: String = "",
    /** True once the TV is showing its code, so the screen swaps the spinner for the code field. */
    val awaitingCode: Boolean = false,
    val error: String? = null,
)
