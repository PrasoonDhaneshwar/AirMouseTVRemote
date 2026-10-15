package com.prasoon.airmousetv.data.api

/**
 * What it takes to turn the TV's text field from one text into another, with the TV's cursor at
 * the end: delete [deleteCount] characters, then add [newChars].
 *
 * [replacement] is what to send through the TV's text input session, where an edit replaces the
 * word at the cursor: that word followed by [newChars]. Example: "why" to "why " is sent as
 * "why ", and "why " to "why h" as just "h".
 */
internal data class TextEditPlan(
    val deleteCount: Int,
    val newChars: String,
    val replacement: String,
) {
    /**
     * How many characters to delete through the text input session. Once something has been
     * deleted, the TV doesn't always replace the word left under its cursor with [replacement]; it
     * sometimes inserts the text after it, doubling that word. So when text is deleted and then
     * sent, the part of that word before the cursor is deleted too ([replacement] holds it again),
     * which leaves the cursor on a word boundary with nothing to replace.
     */
    val sessionDeleteCount: Int
        get() = if (deleteCount > 0 && newChars.isNotEmpty()) deleteCount + (replacement.length - newChars.length) else deleteCount
}

/** Plans the edit from [previous] to [current]; only the end of the text is ever changed. */
internal fun planTextEdit(previous: String, current: String): TextEditPlan {
    val common = previous.commonPrefixWith(current).length
    val newChars = current.substring(common)
    val replacement = if (newChars.isEmpty()) "" else current.substring(wordStart(current.substring(0, common)))
    return TextEditPlan(deleteCount = previous.length - common, newChars = newChars, replacement = replacement)
}

/**
 * The same edit as key presses, for a TV with no text input session: [deleteCount] backspaces,
 * then [strokes]. Characters the remote can't type are left out of both texts before planning, so
 * the plan matches what the TV's field really holds; [skipped] lists the ones new in this edit.
 */
internal data class KeyPressPlan(
    val deleteCount: Int,
    val strokes: List<TextKeyMapper.Stroke>,
    val skipped: List<Char>,
)

/** Plans the key presses that take the TV's field from [previous] to [current], with the cursor at the end. */
internal fun planKeyPressEdit(previous: String, current: String): KeyPressPlan {
    fun typeable(text: String) = text.filter { TextKeyMapper.toStroke(it) != null }

    val plan = planTextEdit(typeable(previous), typeable(current))
    val skipped = planTextEdit(previous, current).newChars.filter { TextKeyMapper.toStroke(it) == null }
    return KeyPressPlan(
        deleteCount = plan.deleteCount,
        strokes = plan.newChars.mapNotNull { TextKeyMapper.toStroke(it) },
        skipped = skipped.toList(),
    )
}

/**
 * How much of two texts differs: the length of the longer one's middle part once their common
 * start and common end are left out. 0 means equal; a smaller value means closer.
 */
internal fun mismatchSize(a: String, b: String): Int {
    val prefix = a.commonPrefixWith(b).length
    val suffix = a.substring(prefix).commonSuffixWith(b.substring(prefix)).length
    return maxOf(a.length, b.length) - prefix - suffix
}

/** Index where the last whitespace-delimited word of [text] starts. */
private fun wordStart(text: String): Int = text.indexOfLast { it.isWhitespace() } + 1
