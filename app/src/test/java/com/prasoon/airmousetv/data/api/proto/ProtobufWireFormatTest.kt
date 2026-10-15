package com.prasoon.airmousetv.data.api.proto

import com.prasoon.airmousetv.data.model.KeyAction
import com.prasoon.airmousetv.proto.remote.RemoteDirection
import com.prasoon.airmousetv.proto.remote.RemoteKeyCode
import com.prasoon.airmousetv.proto.remote.RemoteKeyInject
import com.prasoon.airmousetv.proto.remote.RemoteMessage
import com.prasoon.airmousetv.proto.remote.RemotePingRequest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the exact bytes of a few messages, to show how protobuf encodes them:
 * each field is `tag, value` where tag = (field_number shl 3) or wire_type.
 */
class ProtobufWireFormatTest {

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    // 52 04 08 04 10 03
    //  |  |  |  |  |  '- direction = 3 (SHORT)
    //  |  |  |  |  '---- tag: field 2, wire type 0 (varint)
    //  |  |  |  '------- key_code = 4 (KEYCODE_BACK)
    //  |  |  '---------- tag: field 1, wire type 0 (varint)
    //  |  '------------- the nested message is 4 bytes long
    //  '---------------- tag: field 10 (remote_key_inject), wire type 2 (length-delimited)
    private val backShortBytes = bytes(0x52, 0x04, 0x08, 0x04, 0x10, 0x03)

    // 42 04 08 05 10 07: field 8 (remote_ping_request), then val1 = 5 and val2 = 7
    private val pingBytes = bytes(0x42, 0x04, 0x08, 0x05, 0x10, 0x07)

    @Test
    fun tag_isFieldNumberShiftedLeftThreePlusWireType() {
        assertEquals(0x52, (10 shl 3) or 2) // field 10, length-delimited
        assertEquals(0x42, (8 shl 3) or 2) // field 8, length-delimited
        assertEquals(0x08, (1 shl 3) or 0) // field 1, varint
        assertEquals(0x10, (2 shl 3) or 0) // field 2, varint
    }

    @Test
    fun keyInject_backShort_encodesToExpectedBytes() {
        val message = RemoteMessage.newBuilder()
            .setRemoteKeyInject(
                RemoteKeyInject.newBuilder()
                    .setKeyCode(RemoteKeyCode.KEYCODE_BACK)
                    .setDirection(RemoteDirection.SHORT)
            )
            .build()

        assertArrayEquals(backShortBytes, message.toByteArray())
    }

    @Test
    fun keyInject_backShort_decodesFromBytes() {
        val message = RemoteMessage.parseFrom(backShortBytes)

        assertTrue(message.hasRemoteKeyInject())
        assertEquals(RemoteKeyCode.KEYCODE_BACK, message.remoteKeyInject.keyCode)
        assertEquals(RemoteDirection.SHORT, message.remoteKeyInject.direction)
    }

    @Test
    fun pingRequest_decodesFromBytes() {
        val message = RemoteMessage.parseFrom(pingBytes)

        assertTrue(message.hasRemotePingRequest())
        assertEquals(5, message.remotePingRequest.val1)
        assertEquals(7, message.remotePingRequest.val2)
    }

    @Test
    fun pingRequest_encodesToExpectedBytes() {
        val message = RemoteMessage.newBuilder()
            .setRemotePingRequest(RemotePingRequest.newBuilder().setVal1(5).setVal2(7))
            .build()

        assertArrayEquals(pingBytes, message.toByteArray())
    }

    @Test
    fun encoder_tapOnBack_producesTheSameBytes() {
        // KeyAction.TAP maps to RemoteDirection.SHORT
        val encoded = RemoteMessageEncoder.encodeKeyCode(RemoteKeyCode.KEYCODE_BACK, KeyAction.TAP)

        assertArrayEquals(backShortBytes, encoded)
    }

    @Test
    fun dpadCenterTap_matchesTheWalkthroughBytes() {
        val encoded = RemoteMessageEncoder.encodeKeyCode(RemoteKeyCode.KEYCODE_DPAD_CENTER, KeyAction.TAP)

        assertArrayEquals(bytes(0x52, 0x04, 0x08, 0x17, 0x10, 0x03), encoded)
    }

    @Test
    fun defaultValues_areNotWritten() {
        // proto3 omits fields equal to their default, so an empty message is zero bytes
        assertEquals(0, RemoteMessage.getDefaultInstance().toByteArray().size)
        // key_code UNKNOWN = 0 is dropped; only the direction field remains
        val keyInject = RemoteKeyInject.newBuilder().setDirection(RemoteDirection.SHORT).build()
        assertArrayEquals(bytes(0x10, 0x03), keyInject.toByteArray())
    }

    @Test
    fun unknownFields_areSkippedWhenParsing() {
        // Field 99 (tag 0x9A 0x06, wire type 2) holding 2 bytes, then a known ping request.
        // A decoder must skip what it doesn't know; the wire type says how long the value is.
        val withUnknown = bytes(0x9A, 0x06, 0x02, 0xAA, 0xBB) + pingBytes
        val message = RemoteMessage.parseFrom(withUnknown)

        assertEquals(5, message.remotePingRequest.val1)
        assertEquals(7, message.remotePingRequest.val2)
    }
}
