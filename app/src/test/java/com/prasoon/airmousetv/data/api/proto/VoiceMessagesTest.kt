package com.prasoon.airmousetv.data.api.proto

import com.prasoon.airmousetv.proto.remote.RemoteMessage
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/** Pins the voice messages to the field numbers the TV expects (30 to 32 of RemoteMessage). */
class VoiceMessagesTest {

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun voiceBegin_isField30WithTheSessionId() {
        // F2 01: field 30, length-delimited (30 shl 3 or 2 = 242, a two-byte varint); 02: two bytes follow; 08 07: session_id = 7
        assertArrayEquals(bytes(0xF2, 0x01, 0x02, 0x08, 0x07), RemoteMessageEncoder.encodeVoiceBegin(7))
    }

    @Test
    fun voiceEnd_isField32WithTheSessionId() {
        assertArrayEquals(bytes(0x82, 0x02, 0x02, 0x08, 0x07), RemoteMessageEncoder.encodeVoiceEnd(7))
    }

    @Test
    fun voicePayload_carriesTheSessionAndTheSamples() {
        val samples = ByteArray(8192) { it.toByte() }
        val message = RemoteMessage.parseFrom(RemoteMessageEncoder.encodeVoicePayload(3, samples))
        assertEquals(3, message.remoteVoicePayload.sessionId)
        assertArrayEquals(samples, message.remoteVoicePayload.samples.toByteArray())
    }

    @Test
    fun tvVoiceBegin_isReadWithItsSessionAndPackage() {
        // What a TV sends after the search key: session 12 for the assistant's package
        val fromTv = RemoteMessage.newBuilder().setRemoteVoiceBegin(
            com.prasoon.airmousetv.proto.remote.RemoteVoiceBegin.newBuilder()
                .setSessionId(12).setPackageName("com.google.android.katniss")
        ).build().toByteArray()
        val message = RemoteMessage.parseFrom(fromTv)
        assertEquals(12, message.remoteVoiceBegin.sessionId)
        assertEquals("com.google.android.katniss", message.remoteVoiceBegin.packageName)
    }
}
