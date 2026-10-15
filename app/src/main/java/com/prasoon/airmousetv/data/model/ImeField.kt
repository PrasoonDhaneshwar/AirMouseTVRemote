package com.prasoon.airmousetv.data.model

/**
 * The text field focused on the TV, as last reported by it.
 *
 * [imeCounter] comes from the TV's own ImeBatchEdit messages: it is 0 when no text input session
 * is open and above 0 while one is. [showRequests] goes up each time a field takes focus, so the
 * UI can open text entry on demand rather than on every state change.
 *
 * [reports] counts the times the TV has told us the field's text, so a caller can tell a report
 * made after its own edit from one that was already there. [focuses] counts the times a text
 * field took focus on the TV, so a caller can tell the same field from a different one.
 */
data class ImeField(
    val label: String = "",
    val value: String = "",
    val imeCounter: Int = 0,
    val showRequests: Int = 0,
    val reports: Int = 0,
    val focuses: Int = 0,
) {
    /** True when the TV has an open text input session that accepts text edits. */
    val acceptsText: Boolean get() = imeCounter > 0
}

/**
 * The text the TV reports for its field when it differs from [expected], or null if there is
 * nothing to correct: the text matches, or the TV has reported nothing since [reportsBefore] and
 * so can't be compared with.
 */
fun ImeField?.reportedMismatch(expected: String, reportsBefore: Int): String? {
    if (this == null || reports <= reportsBefore) return null
    return value.takeIf { it != expected }
}
