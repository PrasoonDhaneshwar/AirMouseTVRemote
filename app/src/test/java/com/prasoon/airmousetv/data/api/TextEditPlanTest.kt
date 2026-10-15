package com.prasoon.airmousetv.data.api

import org.junit.Assert.assertEquals
import org.junit.Test

class TextEditPlanTest {

    private fun plan(previous: String, current: String) = planTextEdit(previous, current)

    @Test
    fun typingFirstWord_sendsItWhole() {
        assertEquals(TextEditPlan(0, "why", "why"), plan("", "why"))
    }

    @Test
    fun appendingASpace_resendsTheWordWithIt() {
        // The TV replaces the word at the cursor, so the word goes out again with the space
        assertEquals(TextEditPlan(0, " ", "why "), plan("why", "why "))
    }

    @Test
    fun startingANewWord_sendsOnlyThatWord() {
        assertEquals(TextEditPlan(0, "h", "h"), plan("why ", "why h"))
    }

    @Test
    fun continuingAWord_replacesTheWholeWord() {
        assertEquals(TextEditPlan(0, "llo", "hello"), plan("he", "hello"))
        assertEquals(TextEditPlan(0, "d", "world"), plan("hello worl", "hello world"))
    }

    @Test
    fun changingTheLastLetter_deletesThenReplacesTheWord() {
        // "abc" -> "abd": drop the c, then the word "ab" is replaced by "abd"
        assertEquals(TextEditPlan(1, "d", "abd"), plan("abc", "abd"))
    }

    @Test
    fun deletingOnly_hasNothingToSend() {
        assertEquals(TextEditPlan(6, "", ""), plan("hello world", "hello"))
        assertEquals(TextEditPlan(3, "", ""), plan("abc", ""))
    }

    @Test
    fun unchangedText_isANoOp() {
        assertEquals(TextEditPlan(0, "", ""), plan("same", "same"))
        assertEquals(TextEditPlan(0, "", ""), plan("", ""))
    }

    @Test
    fun replacingEverything_deletesAllThenSendsTheNewText() {
        assertEquals(TextEditPlan(3, "xyz", "xyz"), plan("abc", "xyz"))
    }

    @Test
    fun whitespaceOtherThanSpace_countsAsAWordBreak() {
        assertEquals(TextEditPlan(0, "b", "b"), plan("a\n", "a\nb"))
        assertEquals(TextEditPlan(0, "b", "b"), plan("a\t", "a\tb"))
    }

    @Test
    fun editInTheMiddle_deletesEverythingAfterTheChange() {
        // The cursor is assumed to be at the end, so the tail has to go too and be typed again.
        // The common prefix is "one": " two cat" (8 characters) is deleted, "X cat" is new, and the
        // word "one" at the cursor is replaced by "oneX cat".
        assertEquals(TextEditPlan(8, "X cat", "oneX cat"), plan("one two cat", "oneX cat"))
    }

    // ---- what is deleted through the text input session ----

    @Test
    fun session_appendingNeedsNoDelete_andReplacesTheWordAtTheCursor() {
        val plan = plan("why", "why ")
        assertEquals(0, plan.sessionDeleteCount)
        assertEquals("why ", plan.replacement)
    }

    @Test
    fun session_deleteThenSend_alsoDeletesThePartialWordUnderTheCursor() {
        // "abc" -> "abd": delete the c and the "ab" that the replacement "abd" contains
        val plan = plan("abc", "abd")
        assertEquals(1, plan.deleteCount)
        assertEquals(3, plan.sessionDeleteCount)
        assertEquals("abd", plan.replacement)
    }

    @Test
    fun session_afterTheDelete_theCursorIsOnAWordBoundary() {
        val previous = "one two three four"
        val current = "one two thre four"
        val plan = plan(previous, current)

        // What the TV holds after the session delete is the text up to where the replacement starts
        val kept = previous.dropLast(plan.sessionDeleteCount)
        assertEquals("one two ", kept)
        assertEquals(current, kept + plan.replacement)
    }

    @Test
    fun session_pureDelete_isNotExtended() {
        val plan = plan("hello world", "hello")
        assertEquals(6, plan.sessionDeleteCount)
        assertEquals("", plan.replacement)
    }

    @Test
    fun session_deleteStartingAtAWordBoundary_isNotExtended() {
        // The common text ends with a space: no word under the cursor, so nothing extra to delete
        val plan = plan("one two", "one xyz")
        assertEquals(3, plan.deleteCount)
        assertEquals(3, plan.sessionDeleteCount)
        assertEquals("xyz", plan.replacement)
    }

    @Test
    fun session_deleteInsideAWord_includesTheWordBeforeTheCursor() {
        // "one two" -> "one three" share "one t": delete "wo" and also the "t" that the replacement "three" starts with
        val plan = plan("one two", "one three")
        assertEquals(2, plan.deleteCount)
        assertEquals(3, plan.sessionDeleteCount)
        assertEquals("three", plan.replacement)
    }

    @Test
    fun session_resultAlwaysRebuildsTheCurrentText() {
        // What the TV ends up with: the text left after the deletes, with the word under the cursor
        // (if the edit deleted nothing, so none was deleted for it) replaced by the replacement
        val texts = listOf("", "a", "ab", "ab cd", "ab cd ef", "ab ce", "xy", "ab  cd", "ab cd\nef")
        for (previous in texts) for (current in texts) {
            val plan = plan(previous, current)
            val partialWord = plan.replacement.length - plan.newChars.length
            val removed = plan.sessionDeleteCount + if (plan.deleteCount == 0) partialWord else 0
            val result = previous.dropLast(removed) + plan.replacement
            assertEquals("$previous -> $current", current, result)
        }
    }

    // ---- how far apart two texts are ----

    @Test
    fun mismatchSize_equalTextsAreZero() {
        assertEquals(0, mismatchSize("hello", "hello"))
        assertEquals(0, mismatchSize("", ""))
    }

    @Test
    fun mismatchSize_countsOnlyTheDifferingMiddle() {
        assertEquals(1, mismatchSize("hello world", "hello worXd"))
        assertEquals(5, mismatchSize("hello", "hello world".take(5) + "12345"))
        assertEquals(6, mismatchSize("hello", "hello world"))
    }

    @Test
    fun mismatchSize_doubledTextIsFarFromTheOriginal() {
        assertEquals(10, mismatchSize("fou", "foufoufoufoufou".take(13)))
    }

    @Test
    fun mismatchSize_closerTextHasASmallerValue() {
        val wanted = "Z aboabc defY four seven"
        val far = "Z aboabc defY foufoufour seven"
        val near = "Z aboabc defY fouur seven"
        assertEquals(true, mismatchSize(near, wanted) < mismatchSize(far, wanted))
    }

    @Test
    fun mismatchSize_isSymmetric() {
        assertEquals(mismatchSize("abcdef", "abXef"), mismatchSize("abXef", "abcdef"))
    }

    // ---- key presses (no text session) ----

    private fun chars(plan: KeyPressPlan) = plan.strokes.joinToString("") { it.code.name.removePrefix("KEYCODE_") }

    @Test
    fun keyPresses_typeOnlyTheNewCharacters() {
        val plan = planKeyPressEdit("ab", "abc")
        assertEquals(0, plan.deleteCount)
        assertEquals("C", chars(plan))
        assertEquals(emptyList<Char>(), plan.skipped)
    }

    @Test
    fun keyPresses_backspaceOverWhatChangedAtTheEnd() {
        val plan = planKeyPressEdit("abc", "abd")
        assertEquals(1, plan.deleteCount)
        assertEquals("D", chars(plan))
    }

    @Test
    fun keyPresses_capitalLetterUsesShift() {
        val plan = planKeyPressEdit("", "A")
        assertEquals(listOf(TextKeyMapper.Stroke(com.prasoon.airmousetv.proto.remote.RemoteKeyCode.KEYCODE_A, shift = true)), plan.strokes)
    }

    @Test
    fun keyPresses_untypeableCharacterIsSkippedAndReported() {
        val plan = planKeyPressEdit("a", "aéb")
        assertEquals(0, plan.deleteCount)
        assertEquals("B", chars(plan))
        assertEquals(listOf('é'), plan.skipped)
    }

    @Test
    fun keyPresses_skippedCharacterIsNotDeletedLater() {
        // The TV shows "a": the é was never typed. Going back to "a" must not press backspace
        val plan = planKeyPressEdit("aé", "a")
        assertEquals(0, plan.deleteCount)
        assertEquals(emptyList<TextKeyMapper.Stroke>(), plan.strokes)
        assertEquals(emptyList<Char>(), plan.skipped)
    }

    @Test
    fun keyPresses_skippedCharacterInTheMiddleDoesNotRetypeTheTail() {
        // TV holds "abc" (é never typed); adding "d" should type only "d"
        val plan = planKeyPressEdit("aébc", "aébcd")
        assertEquals(0, plan.deleteCount)
        assertEquals("D", chars(plan))
        assertEquals(emptyList<Char>(), plan.skipped)
    }

    @Test
    fun keyPresses_deletingATypedCharacterBeforeASkippedOne() {
        // TV holds "ab"; the phone goes from "abé" to "a": one backspace for the b only
        val plan = planKeyPressEdit("abé", "a")
        assertEquals(1, plan.deleteCount)
        assertEquals(emptyList<TextKeyMapper.Stroke>(), plan.strokes)
    }

    @Test
    fun emoji_arenNotSplitInTheMiddleOfASurrogatePair() {
        // The two emoji share a leading surrogate; the common prefix must not stop inside one
        val result = plan("a😀", "a😁")
        assertEquals(2, result.deleteCount)
        assertEquals("😁", result.newChars)
    }
}
