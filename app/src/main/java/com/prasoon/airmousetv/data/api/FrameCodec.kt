package com.prasoon.airmousetv.data.api

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/**
 * The Android TV Remote protocol (Polo + RemoteMessage) uses
 * varint length-prefixed protobuf messages over TLS.
 * The TLS layer handles its own record framing; at the application layer,
 * each message is preceded by a protobuf varint encoding its byte length.
 */
object FrameCodec {

    fun readFrame(input: DataInputStream): ByteArray {
        // Read the varint length prefix (protobuf-style MSB encoding)
        var length = 0
        var shift = 0
        var byteRead: Int
        do {
            byteRead = input.read()
            if (byteRead == -1) {
                throw IOException("EOF reached while reading length prefix")
            }
            length = length or ((byteRead and 0x7F) shl shift)
            shift += 7
        } while ((byteRead and 0x80) != 0)

        // Read the message body
        val buffer = ByteArray(length)
        input.readFully(buffer)
        return buffer
    }

    fun writeFrame(output: DataOutputStream, payload: ByteArray) {
        // Write the varint length prefix (protobuf-style MSB encoding)
        var value = payload.size
        while (value > 0x7F) {
            output.write((value and 0x7F) or 0x80)
            value = value ushr 7
        }
        output.write(value and 0x7F)

        // Write the protobuf message body
        output.write(payload)
        output.flush()
    }
}
