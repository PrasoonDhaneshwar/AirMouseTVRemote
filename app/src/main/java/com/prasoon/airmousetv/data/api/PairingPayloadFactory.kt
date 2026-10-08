package com.prasoon.airmousetv.data.api

import com.prasoon.airmousetv.data.api.proto.RemoteMessageEncoder

/** Builds the client-side messages of the Polo pairing handshake, in the order they are sent. */
object PairingPayloadFactory {

    /** Step 1: announce ourselves as [clientName]. */
    fun createPairingRequest(clientName: String): ByteArray =
        RemoteMessageEncoder.encodePairingRequest(clientName)

    /** Step 2: offer to type a 6-digit hex code shown by the TV. */
    fun createOptions(): ByteArray =
        RemoteMessageEncoder.encodeOptions()

    /** Step 3: confirm the encoding and role; the TV then shows the code. */
    fun createConfiguration(): ByteArray =
        RemoteMessageEncoder.encodeConfiguration()

    /** Step 4: prove we know the code, via [secretBytes] (see RemoteSessionManager.computeSecret). */
    fun createSecret(secretBytes: ByteArray): ByteArray =
        RemoteMessageEncoder.encodeSecret(secretBytes)
}
