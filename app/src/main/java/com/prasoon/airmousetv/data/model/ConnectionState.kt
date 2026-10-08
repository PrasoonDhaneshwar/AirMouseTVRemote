package com.prasoon.airmousetv.data.model

/** Where the connection to the TV currently is, from first contact through pairing to an active remote. */
sealed class ConnectionState {
    /** Nothing in progress. */
    object Idle : ConnectionState()

    /** TLS to the pairing port (6467) is up. */
    object TcpConnected : ConnectionState()

    /** PairingRequest sent; waiting for the TV to answer. */
    object PairingRequested : ConnectionState()

    /** The TV is displaying a 6-digit hex code and waiting for the user to enter it. */
    object AwaitingCode : ConnectionState()

    /** Remote session (port 6466) is active; keys can be sent. */
    object Connected : ConnectionState()

    /** Session closed or lost. */
    object Disconnected : ConnectionState()

    /**
     * A failure the UI should show.
     *
     * [id] makes two identical failures distinct values, so a StateFlow doesn't conflate a
     * repeat (e.g. entering a wrong code twice) into a single emission.
     */
    data class Error(val message: String, val id: Long = System.nanoTime()) : ConnectionState()
}
