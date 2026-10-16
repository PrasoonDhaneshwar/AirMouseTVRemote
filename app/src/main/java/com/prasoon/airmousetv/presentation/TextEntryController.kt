package com.prasoon.airmousetv.presentation

import android.util.Log
import com.prasoon.airmousetv.data.api.mismatchSize
import com.prasoon.airmousetv.data.model.ImeField
import com.prasoon.airmousetv.data.model.TvKey
import com.prasoon.airmousetv.data.model.reportedMismatch
import com.prasoon.airmousetv.data.repository.RemoteRepository
import com.prasoon.airmousetv.data.repository.RemoteSessionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "TextEntry"

/** How long to wait for the TV to report its text after an edit; it normally does within a few hundred ms. */
internal const val TV_REPORT_TIMEOUT_MS = 1000L
/** Corrective edits in a row before the TV's text is accepted as it is. */
internal const val MAX_TEXT_CORRECTIONS = 4
/** How long typing must pause before the TV's text is checked once more for changes it made by itself. */
internal const val TEXT_SETTLE_DELAY_MS = 1500L

/** What the text entry needs from the TV connection; a fake in tests, the repository and session in the app. */
internal interface TvTextPort {
    /** The text field focused on the TV, as last reported by it. */
    val imeField: StateFlow<ImeField?>

    /** True when the TV has an open text input session that accepts text edits. */
    val hasTextSession: Boolean

    /** Waits until the TV can take another edit. */
    suspend fun awaitEditSlot()

    /** Makes the TV's field go from [previous] to [current]; returns the characters that couldn't be typed. */
    suspend fun typeEdit(previous: String, current: String): List<Char>

    suspend fun sendEnter()

    /** Records [event] in the diagnostics a user can copy into a bug report. */
    fun note(event: String)
}

/** The app's [TvTextPort]: edits go through the repository, everything else through the session. */
internal class SessionTvTextPort(
    private val repository: RemoteRepository,
    private val session: RemoteSessionManager,
) : TvTextPort {
    override val imeField: StateFlow<ImeField?> get() = session.imeField
    override val hasTextSession: Boolean get() = session.hasTextSession
    override suspend fun awaitEditSlot() = repository.awaitEditSlot()
    override suspend fun typeEdit(previous: String, current: String) = repository.typeEdit(previous, current)
    override suspend fun sendEnter() = repository.sendKey(TvKey.ENTER)
    override fun note(event: String) = session.note(event)
}

/** Something to do to the TV's text field, applied in order. */
private sealed interface TextAction {
    /** A new text entry session; the TV's field currently shows [initial]. */
    data class Begin(val initial: String) : TextAction

    /** The field should show [text]. */
    data class Set(val text: String) : TextAction

    /** Press Enter. */
    object Enter : TextAction
}

/**
 * Makes the TV's focused text field follow the text the user types on the phone.
 *
 * Calls queue up and a worker applies them one at a time, in order. Consecutive sets are merged
 * into the latest one: swipe typing makes the phone keyboard rewrite the word many times a second,
 * and only the final text matters, so intermediate states are never sent. The merging happens
 * after waiting for the edit slot, so slow sends merge more. Characters the remote can't type, and
 * failures, are passed to [onError].
 *
 * The TV doesn't always apply an edit as planned, and it reports its field's text back after each
 * one. So after an edit through the text input session the worker compares that report with what
 * it meant to send. If they differ, the TV's own text becomes the baseline for the next edit and
 * the latest wanted text is sent again, up to [MAX_TEXT_CORRECTIONS] times in a row and only while
 * that brings the TV closer, so a TV that rewrites the text (or can never match it) doesn't cause
 * an endless loop. The TV can also change its text on its own a moment later, so once typing has
 * paused for [TEXT_SETTLE_DELAY_MS] the worker looks at what the TV has reported since the edit
 * (a TV that reported nothing is left alone).
 */
internal class TextEntryController(
    scope: CoroutineScope,
    private val tv: TvTextPort,
    private val onError: (String) -> Unit,
) {
    private val actions = Channel<TextAction>(Channel.UNLIMITED)

    /** The text the user wants on the TV right now, the newest one given to [type] or [begin]. */
    @Volatile
    private var wantedText = ""

    init {
        scope.launch { work() }
    }

    /** Starts a text entry session whose field already shows [initial] on the TV. */
    fun begin(initial: String) {
        wantedText = initial
        actions.trySend(TextAction.Begin(initial))
    }

    /** Makes the TV's focused text field show [text]; edits made in quick succession are merged. */
    fun type(text: String) {
        wantedText = text
        actions.trySend(TextAction.Set(text))
    }

    /** Presses Enter on the TV once all text typed so far has been sent. */
    fun submit() {
        actions.trySend(TextAction.Enter)
    }

    private suspend fun work() {
        var tvText = ""
        var corrections = 0
        var lastGap = 0  // how far the TV's text was from the wanted text before the last correction
        var settleCheckDue = false
        var settleFocuses = 0  // how many fields had taken focus when the last edit was made
        var settleReports = 0  // how many reports the TV had made when the last edit was checked
        var carried: TextAction? = null

        /** The TV shows [actual] instead of what was sent: trust it as the baseline and, if it isn't what the user wants, send that again. */
        fun onTvTextDiffers(actual: String) {
            Log.w(TAG, "TV text differs from what was sent: ${actual.length} chars, expected ${tvText.length}")
            tv.note("TV text differs (${actual.length} chars, expected ${tvText.length})")
            tvText = actual
            // A correction must bring the TV closer to the wanted text; if it didn't, the TV isn't
            // taking the edits and more of them would only pile more text on it
            val gap = mismatchSize(actual, wantedText)
            val improving = corrections == 0 || gap < lastGap
            lastGap = gap
            if (improving && corrections++ < MAX_TEXT_CORRECTIONS && gap > 0) {
                actions.trySend(TextAction.Set(wantedText))
            } else {
                corrections = 0
            }
        }

        while (true) {
            val first = carried
                ?: if (settleCheckDue) withTimeoutOrNull(TEXT_SETTLE_DELAY_MS) { actions.receive() } else actions.receive()
            if (first == null) {
                // Typing paused: has the TV changed its text since the last edit, without being asked?
                // Not if another field has taken focus since, whose text has nothing to do with ours.
                settleCheckDue = false
                val reported = tv.imeField.value
                when {
                    !tv.hasTextSession || reported == null ->
                        Log.d(TAG, "Settle check skipped: no text session")
                    reported.focuses != settleFocuses ->
                        Log.d(TAG, "Settle check skipped: another field took focus")
                    // A TV that reported nothing since the edit gives us nothing to compare: its old value is not its text now
                    reported.reports <= settleReports ->
                        Log.d(TAG, "Settle check skipped: the TV reported nothing new")
                    reported.value != tvText -> onTvTextDiffers(reported.value)
                }
                continue
            }
            var action: TextAction = first
            carried = null
            if (action is TextAction.Set) {
                // Edits can't be sent faster than the TV copes with, so let more pile up first
                tv.awaitEditSlot()
                while (true) {
                    val next = actions.tryReceive().getOrNull() ?: break
                    if (next is TextAction.Set) action = next else { carried = next; break }
                }
            }
            try {
                when (action) {
                    is TextAction.Begin -> {
                        tvText = action.initial
                        corrections = 0
                        settleCheckDue = false
                    }
                    is TextAction.Set -> {
                        val reportsBefore = tv.imeField.value?.reports ?: 0
                        val skipped = tv.typeEdit(tvText, action.text)
                        tvText = action.text
                        if (skipped.isNotEmpty()) {
                            onError("Can't type: ${skipped.distinct().joinToString(" ")}")
                        }
                        if (tv.hasTextSession) {
                            settleCheckDue = true
                            settleFocuses = tv.imeField.value?.focuses ?: 0
                            val actual = awaitReportedMismatch(action.text, reportsBefore)
                            settleReports = tv.imeField.value?.reports ?: 0
                            if (actual == null) corrections = 0 else onTvTextDiffers(actual)
                        }
                    }
                    TextAction.Enter -> {
                        settleCheckDue = false  // the TV moves on after Enter; its next text isn't ours to correct
                        tv.sendEnter()
                    }
                }
            } catch (e: CancellationException) {
                throw e  // the owner is going away; not a send failure
            } catch (e: Exception) {
                Log.e(TAG, "text action failed", e)
                onError("Send failed: ${e.message}")
            }
        }
    }

    /**
     * Waits briefly for the TV to report its field's text as [expected] after an edit. Returns the
     * text it reports instead if that differs, or null if it matches or the TV reported nothing.
     */
    private suspend fun awaitReportedMismatch(expected: String, reportsBefore: Int): String? {
        withTimeoutOrNull(TV_REPORT_TIMEOUT_MS) { tv.imeField.first { it?.value == expected } }
        return tv.imeField.value.reportedMismatch(expected, reportsBefore)
    }
}
