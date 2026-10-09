package com.prasoon.airmousetv.data.model

/**
 * The text field focused on the TV, as last reported by it.
 *
 * [imeCounter] comes from the TV's own ImeBatchEdit messages: it is 0 when no text input session
 * is open and above 0 while one is. [showRequests] goes up each time a field takes focus, so the
 * UI can open text entry on demand rather than on every state change.
 */
data class ImeField(
    val label: String = "",
    val value: String = "",
    val imeCounter: Int = 0,
    val showRequests: Int = 0,
) {
    /** True when the TV has an open text input session that accepts text edits. */
    val acceptsText: Boolean get() = imeCounter > 0
}
