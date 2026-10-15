package com.prasoon.airmousetv.data.api

import com.prasoon.airmousetv.proto.remote.RemoteKeyCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextKeyMapperTest {

    @Test
    fun lowerCaseLetters_mapWithoutShift() {
        for (c in 'a'..'z') {
            val stroke = TextKeyMapper.toStroke(c)
            assertNotNull("$c", stroke)
            assertEquals("KEYCODE_${c.uppercaseChar()}", stroke!!.code.name)
            assertFalse("$c", stroke.shift)
        }
    }

    @Test
    fun upperCaseLetters_mapWithShift() {
        for (c in 'A'..'Z') {
            val stroke = TextKeyMapper.toStroke(c)
            assertNotNull("$c", stroke)
            assertEquals("KEYCODE_$c", stroke!!.code.name)
            assertTrue("$c", stroke.shift)
        }
    }

    @Test
    fun digits_mapWithoutShift() {
        for (c in '0'..'9') {
            val stroke = TextKeyMapper.toStroke(c)
            assertNotNull("$c", stroke)
            assertEquals("KEYCODE_$c", stroke!!.code.name)
            assertFalse("$c", stroke.shift)
        }
    }

    @Test
    fun symbols_useTheExpectedKeys() {
        assertEquals(TextKeyMapper.Stroke(RemoteKeyCode.KEYCODE_SPACE), TextKeyMapper.toStroke(' '))
        assertEquals(TextKeyMapper.Stroke(RemoteKeyCode.KEYCODE_PERIOD), TextKeyMapper.toStroke('.'))
        assertEquals(TextKeyMapper.Stroke(RemoteKeyCode.KEYCODE_MINUS), TextKeyMapper.toStroke('-'))
        // Shifted symbols share the key of their unshifted partner
        assertEquals(TextKeyMapper.Stroke(RemoteKeyCode.KEYCODE_MINUS, shift = true), TextKeyMapper.toStroke('_'))
        assertEquals(TextKeyMapper.Stroke(RemoteKeyCode.KEYCODE_1, shift = true), TextKeyMapper.toStroke('!'))
        assertEquals(TextKeyMapper.Stroke(RemoteKeyCode.KEYCODE_9, shift = true), TextKeyMapper.toStroke('('))
        assertEquals(TextKeyMapper.Stroke(RemoteKeyCode.KEYCODE_0, shift = true), TextKeyMapper.toStroke(')'))
    }

    @Test
    fun charactersTheRemoteCannotType_returnNull() {
        for (c in listOf('é', '€', '\n', '\t', '你', '\uD83D', '<', '>', '{', '}', '|', '~', '%', '&', '$', '^')) {
            assertNull("$c", TextKeyMapper.toStroke(c))
        }
    }
}
