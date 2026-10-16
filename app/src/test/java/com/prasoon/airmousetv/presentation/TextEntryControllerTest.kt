package com.prasoon.airmousetv.presentation

import com.prasoon.airmousetv.data.model.ImeField
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TextEntryControllerTest {

    /**
     * A TV text field. Every edit the controller asks for is recorded in [log] and handed to
     * [behaviour], which decides what the field ends up showing; by default it shows exactly the
     * requested text and reports it, like a well-behaved TV.
     */
    private class FakeTv : TvTextPort {
        override val imeField = MutableStateFlow<ImeField?>(ImeField(imeCounter = 1))
        override var hasTextSession = true
        val log = mutableListOf<String>()
        val notes = mutableListOf<String>()
        var behaviour: suspend FakeTv.(previous: String, current: String) -> List<Char> =
            { _, current -> report(current); emptyList() }

        /** The TV tells the phone what its field shows now. */
        fun report(text: String) {
            imeField.update { (it ?: ImeField()).copy(value = text, reports = (it?.reports ?: 0) + 1) }
        }

        override suspend fun awaitEditSlot() = delay(0)

        override suspend fun typeEdit(previous: String, current: String): List<Char> {
            log += "edit '$previous' -> '$current'"
            return behaviour(this, previous, current)
        }

        override suspend fun sendEnter() {
            log += "enter"
        }

        override fun note(event: String) {
            notes += event
        }
    }

    private class Rig(val tv: FakeTv, val errors: MutableList<String>, val controller: TextEntryController)

    private fun TestScope.rig(configure: FakeTv.() -> Unit = {}): Rig {
        val tv = FakeTv().apply(configure)
        val errors = mutableListOf<String>()
        return Rig(tv, errors, TextEntryController(backgroundScope, tv) { errors += it })
    }

    /** Lets the controller run for [ms] of virtual time. */
    private fun TestScope.pass(ms: Long = 10_000) {
        runCurrent()
        advanceTimeBy(ms)
        runCurrent()
    }

    @Test
    fun typing_sendsTheEditOnce() = runTest {
        val r = rig()
        r.controller.begin("")
        r.controller.type("a")
        pass()

        assertEquals(listOf("edit '' -> 'a'"), r.tv.log)
        assertTrue(r.errors.isEmpty())
        assertTrue(r.tv.notes.isEmpty())
    }

    @Test
    fun quickEdits_areMergedIntoTheLatest() = runTest {
        val r = rig()
        r.controller.begin("")
        r.controller.type("h")
        r.controller.type("he")
        r.controller.type("hel")
        pass()

        assertEquals(listOf("edit '' -> 'hel'"), r.tv.log)
    }

    @Test
    fun enter_keepsItsPlaceBetweenEdits() = runTest {
        val r = rig()
        r.controller.begin("")
        r.controller.type("a")
        r.controller.type("ab")
        r.controller.submit()
        r.controller.type("abc")
        pass()

        assertEquals(listOf("edit '' -> 'ab'", "enter", "edit 'ab' -> 'abc'"), r.tv.log)
    }

    @Test
    fun begin_setsWhatTheTvAlreadyShows() = runTest {
        val r = rig()
        r.controller.begin("abc")
        r.controller.type("abcd")
        pass()

        assertEquals(listOf("edit 'abc' -> 'abcd'"), r.tv.log)
    }

    @Test
    fun charactersTheRemoteCantType_areReported() = runTest {
        val r = rig { behaviour = { _, current -> report(current); listOf('é', 'é', 'ñ') } }
        r.controller.begin("")
        r.controller.type("aéñ")
        pass()

        assertEquals(listOf("Can't type: é ñ"), r.errors)
    }

    @Test
    fun aFailedEdit_isReported_andTheNextOneStillGoesThrough() = runTest {
        var first = true
        val r = rig {
            behaviour = { _, current ->
                if (first) {
                    first = false
                    throw IllegalStateException("boom")
                }
                report(current)
                emptyList()
            }
        }
        r.controller.begin("")
        r.controller.type("a")
        pass(100)
        r.controller.type("ab")
        pass()

        assertEquals(listOf("Send failed: boom"), r.errors)
        // The failed edit didn't move the baseline, so the next one starts from the same text
        assertEquals(listOf("edit '' -> 'a'", "edit '' -> 'ab'"), r.tv.log)
    }

    @Test
    fun cancellingTheOwner_isNotASendFailure() = runTest {
        val tv = FakeTv().apply { behaviour = { _, _ -> awaitCancellation() } }
        val errors = mutableListOf<String>()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val controller = TextEntryController(scope, tv) { errors += it }
        controller.begin("")
        controller.type("a")
        runCurrent()
        scope.cancel()
        runCurrent()

        assertTrue(errors.isEmpty())
    }

    @Test
    fun aTvThatDoublesTheText_isCorrected() = runTest {
        var mangled = false
        val r = rig {
            behaviour = { _, current ->
                if (!mangled) { mangled = true; report(current + current) } else report(current)
                emptyList()
            }
        }
        r.controller.begin("")
        r.controller.type("ab")
        pass()

        // The second edit starts from what the TV really shows, and brings it to the wanted text
        assertEquals(listOf("edit '' -> 'ab'", "edit 'abab' -> 'ab'"), r.tv.log)
        assertEquals("ab", r.tv.imeField.value?.value)
        assertEquals(1, r.tv.notes.size)
        assertTrue(r.errors.isEmpty())
    }

    @Test
    fun correctionsStop_whenTheTvDoesNotGetCloser() = runTest {
        // The TV always adds an "x": a correction makes no difference, so only one is tried
        val r = rig { behaviour = { _, current -> report(current + "x"); emptyList() } }
        r.controller.begin("")
        r.controller.type("ab")
        pass()

        assertEquals(2, r.tv.log.size)
    }

    @Test
    fun correctionsStop_afterTheLimit_evenWhileGettingCloser() = runTest {
        // Each attempt leaves one extra character fewer than the one before, but never reaches zero
        var attempt = 0
        val r = rig { behaviour = { _, current -> report(current + "z".repeat(6 - attempt++)); emptyList() } }
        r.controller.begin("")
        r.controller.type("abcdefgh")
        pass()

        assertEquals(1 + MAX_TEXT_CORRECTIONS, r.tv.log.size)
    }

    @Test
    fun aTvThatNeverReports_isLeftAlone() = runTest {
        val r = rig { behaviour = { _, _ -> emptyList() } }
        r.controller.begin("")
        r.controller.type("ab")
        pass(60_000)

        // Its stored value (empty) is not what its field shows, so nothing may be sent to "fix" it
        assertEquals(listOf("edit '' -> 'ab'"), r.tv.log)
        assertTrue(r.tv.notes.isEmpty())
    }

    @Test
    fun withoutATextSession_nothingIsCheckedAgainstTheTv() = runTest {
        val r = rig { hasTextSession = false }
        r.controller.begin("")
        r.controller.type("ab")
        pass(60_000)

        assertEquals(listOf("edit '' -> 'ab'"), r.tv.log)
    }

    @Test
    fun aChangeTheTvMakesByItself_isCorrectedOncePausedTyping() = runTest {
        val r = rig()
        r.controller.begin("")
        r.controller.type("ab")
        runCurrent()
        advanceTimeBy(100)
        r.tv.report("abab")  // the TV changes its own text after confirming ours
        pass()

        assertEquals(listOf("edit '' -> 'ab'", "edit 'abab' -> 'ab'"), r.tv.log)
    }

    @Test
    fun aDifferentFieldTakingFocus_isNotCorrected() = runTest {
        val r = rig()
        r.controller.begin("")
        r.controller.type("ab")
        runCurrent()
        advanceTimeBy(100)
        // Another field with other text took focus: that text has nothing to do with ours
        r.tv.imeField.update { it!!.copy(value = "other", reports = it.reports + 1, focuses = it.focuses + 1) }
        pass()

        assertEquals(listOf("edit '' -> 'ab'"), r.tv.log)
    }

    @Test
    fun pressingEnter_endsTheWatchForChanges() = runTest {
        val r = rig()
        r.controller.begin("")
        r.controller.type("ab")
        runCurrent()
        advanceTimeBy(100)
        r.controller.submit()
        runCurrent()
        r.tv.report("something else on the next screen")
        pass()

        assertEquals(listOf("edit '' -> 'ab'", "enter"), r.tv.log)
    }

    /** Collects what the controller announces as the TV's own text. */
    private fun TestScope.collectAccepted(r: Rig): List<String> {
        val got = mutableListOf<String>()
        backgroundScope.launch { r.controller.acceptedTvText.collect { got += it } }
        runCurrent()
        return got
    }

    @Test
    fun whenTheTvKeepsItsOwnText_thatTextIsAnnounced() = runTest {
        // The TV always adds an "x" and never gets closer, so the controller accepts "abx"
        val r = rig { behaviour = { _, current -> report(current + "x"); emptyList() } }
        val accepted = collectAccepted(r)
        r.controller.begin("")
        r.controller.type("ab")
        pass()

        assertEquals(listOf("abx"), accepted)
    }

    @Test
    fun afterAcceptingTheTvText_laterEditsStartFromIt() = runTest {
        val r = rig { behaviour = { _, current -> report(current + "x"); emptyList() } }
        r.controller.begin("")
        r.controller.type("ab")
        pass()
        r.controller.type("abxc")
        pass()

        assertEquals("edit 'abx' -> 'abxc'", r.tv.log[2])
    }

    @Test
    fun aTvThatMatchesOrIsCorrected_announcesNothing() = runTest {
        var mangled = false
        val r = rig {
            behaviour = { _, current ->
                // Doubles the text once, then behaves: the correction works, so nothing is given up on
                if (!mangled) { mangled = true; report(current + current) } else report(current)
                emptyList()
            }
        }
        val accepted = collectAccepted(r)
        r.controller.begin("")
        r.controller.type("ab")
        pass()

        assertTrue(accepted.isEmpty())
    }
}
