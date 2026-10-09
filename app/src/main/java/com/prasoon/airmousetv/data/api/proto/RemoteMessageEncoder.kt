package com.prasoon.airmousetv.data.api.proto

import android.util.Log
import com.prasoon.airmousetv.proto.polo.OuterMessage
import com.prasoon.airmousetv.proto.polo.PairingRequest
import com.prasoon.airmousetv.proto.polo.Options
import com.prasoon.airmousetv.proto.polo.Configuration
import com.prasoon.airmousetv.proto.polo.Secret
import com.prasoon.airmousetv.proto.remote.RemoteMessage
import com.prasoon.airmousetv.proto.remote.RemoteConfigure
import com.prasoon.airmousetv.proto.remote.RemoteDeviceInfo
import com.prasoon.airmousetv.proto.remote.RemoteEditInfo
import com.prasoon.airmousetv.proto.remote.RemoteImeBatchEdit
import com.prasoon.airmousetv.proto.remote.RemoteImeObject
import com.prasoon.airmousetv.proto.remote.RemoteKeyCode
import com.prasoon.airmousetv.proto.remote.RemoteKeyInject
import com.prasoon.airmousetv.proto.remote.RemoteDirection
import com.prasoon.airmousetv.proto.remote.RemotePingResponse
import com.prasoon.airmousetv.proto.remote.RemoteSetActive
import com.prasoon.airmousetv.data.model.KeyAction
import com.prasoon.airmousetv.data.model.TvKey
import com.prasoon.airmousetv.data.api.TvKeyMapper

private const val TAG = "RemoteMessageEncoder"

/**
 * Serialises protocol messages to bytes. Framing (the varint length prefix) is added later by
 * [com.prasoon.airmousetv.data.api.FrameCodec].
 *
 * Two protocols are covered: Polo pairing (wrapped in an OuterMessage, protocol version 2) on the
 * pairing port, and the RemoteMessage protocol on the remote-control port.
 */
object RemoteMessageEncoder {

    // ------------------ Polo Pairing Protocol ------------------

    /** First pairing message; "atvremote" is the service name the TV expects. */
    fun encodePairingRequest(clientName: String): ByteArray {
        val pairingRequest = PairingRequest.newBuilder()
            .setServiceName("atvremote")
            .setClientName(clientName)
            .build()

        val outerMessage = OuterMessage.newBuilder()
            .setProtocolVersion(2)
            .setStatus(OuterMessage.Status.STATUS_OK)
            .setPairingRequest(pairingRequest)
            .build()

        Log.d(TAG, "Encoded PairingRequest")
        return outerMessage.toByteArray()
    }

    /** Declares that we can take a 6-symbol hex code as input (the TV displays it). */
    fun encodeOptions(): ByteArray {
        val encoding = Options.Encoding.newBuilder()
            .setType(Options.Encoding.EncodingType.ENCODING_TYPE_HEXADECIMAL)
            .setSymbolLength(6)
            .build()

        val options = Options.newBuilder()
            .addInputEncodings(encoding)
            .setPreferredRole(Options.RoleType.ROLE_TYPE_INPUT)
            .build()

        val outerMessage = OuterMessage.newBuilder()
            .setProtocolVersion(2)
            .setStatus(OuterMessage.Status.STATUS_OK)
            .setOptions(options)
            .build()

        Log.d(TAG, "Encoded Options")
        return outerMessage.toByteArray()
    }

    /** Sent after the TV echoes its own Options; the TV shows the pairing code once it receives this. */
    fun encodeConfiguration(): ByteArray {
        val encoding = Options.Encoding.newBuilder()
            .setType(Options.Encoding.EncodingType.ENCODING_TYPE_HEXADECIMAL)
            .setSymbolLength(6)
            .build()

        val configuration = Configuration.newBuilder()
            .setEncoding(encoding)
            .setClientRole(Options.RoleType.ROLE_TYPE_INPUT)
            .build()

        val outerMessage = OuterMessage.newBuilder()
            .setProtocolVersion(2)
            .setStatus(OuterMessage.Status.STATUS_OK)
            .setConfiguration(configuration)
            .build()

        Log.d(TAG, "Encoded Configuration")
        return outerMessage.toByteArray()
    }

    /** Final pairing message: the SHA-256 proof of the code, bound to both certificates. */
    fun encodeSecret(secretBytes: ByteArray): ByteArray {
        val secret = Secret.newBuilder()
            .setSecret(com.google.protobuf.ByteString.copyFrom(secretBytes))
            .build()

        val outerMessage = OuterMessage.newBuilder()
            .setProtocolVersion(2)
            .setStatus(OuterMessage.Status.STATUS_OK)
            .setSecret(secret)
            .build()

        Log.d(TAG, "Encoded Secret")
        return outerMessage.toByteArray()
    }

    // ------------------ Remote Control Protocol ------------------

    // A SHORT press is a complete tap; END_LONG is only valid after START_LONG
    fun encodeKeyPress(key: TvKey, action: KeyAction = KeyAction.TAP): ByteArray =
        encodeKeyCode(TvKeyMapper.toRemoteKeyCode(key), action)

    /** Same as [encodeKeyPress] for a protocol key code that has no [TvKey], such as a typed character. */
    fun encodeKeyCode(code: RemoteKeyCode, action: KeyAction = KeyAction.TAP): ByteArray {
        val keyInject = RemoteKeyInject.newBuilder()
            .setKeyCode(code)
            .setDirection(
                when (action) {
                    KeyAction.TAP -> RemoteDirection.SHORT
                    KeyAction.LONG_START -> RemoteDirection.START_LONG
                    KeyAction.LONG_END -> RemoteDirection.END_LONG
                }
            )
            .build()

        Log.d(TAG, "Encoded key press: $code $action")
        return RemoteMessage.newBuilder()
            .setRemoteKeyInject(keyInject)
            .build()
            .toByteArray()
    }

    /**
     * Edit for the TV's text input session: [text] replaces the word at the cursor (the text
     * after the last whitespace) and the rest of the field is kept. The caret is given as
     * length - 1.
     */
    fun encodeTextEdit(text: String): ByteArray {
        val caret = (text.length - 1).coerceAtLeast(0)
        return encodeBatchEdit(insert = 1, start = caret, end = caret, value = text)
    }

    /** Edit for the TV's text input session that deletes the [count] characters before the cursor. */
    fun encodeTextDelete(count: Int): ByteArray =
        encodeBatchEdit(insert = 0, start = 0, end = count, value = "")

    /**
     * One ImeBatchEdit with a single edit. Both counters are left at 0: on the TV tested they are
     * not needed, and the values it reports don't work. Found by trying edits against the TV and
     * reading back the field.
     */
    private fun encodeBatchEdit(insert: Int, start: Int, end: Int, value: String): ByteArray {
        val editInfo = RemoteEditInfo.newBuilder()
            .setInsert(insert)
            .setTextFieldStatus(
                RemoteImeObject.newBuilder().setStart(start).setEnd(end).setValue(value)
            )
            .build()

        return RemoteMessage.newBuilder()
            .setRemoteImeBatchEdit(RemoteImeBatchEdit.newBuilder().addEditInfo(editInfo))
            .build()
            .toByteArray()
    }

    /** Answers a keep-alive ping; [val1] must echo the value the TV sent. */
    fun encodePingResponse(val1: Int): ByteArray {
        val pingResponse = RemotePingResponse.newBuilder()
            .setVal1(val1)
            .build()

        return RemoteMessage.newBuilder()
            .setRemotePingResponse(pingResponse)
            .build()
            .toByteArray()
    }

    /**
     * Reply to the TV's own RemoteConfigure, identifying this app and the features
     * ([features] bitmask) we will use.
     */
    fun encodeConfigure(features: Int): ByteArray {
        val deviceInfo = RemoteDeviceInfo.newBuilder()
            .setModel("AirMouseTV")
            .setVendor("AirMouseTV")
            .setUnknown1(1)
            .setUnknown2("1")
            .setPackageName("com.prasoon.airmousetv")
            .setAppVersion("1.0.0")
            .build()

        val configure = RemoteConfigure.newBuilder()
            .setCode1(features)
            .setDeviceInfo(deviceInfo)
            .build()

        return RemoteMessage.newBuilder()
            .setRemoteConfigure(configure)
            .build()
            .toByteArray()
    }

    /** Activates the session; [active] mirrors the feature bitmask we advertised. */
    fun encodeSetActive(active: Int): ByteArray {
        val setActive = RemoteSetActive.newBuilder()
            .setActive(active)
            .build()

        return RemoteMessage.newBuilder()
            .setRemoteSetActive(setActive)
            .build()
            .toByteArray()
    }
}
