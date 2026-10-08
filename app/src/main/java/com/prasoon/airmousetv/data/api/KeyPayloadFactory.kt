package com.prasoon.airmousetv.data.api

import com.prasoon.airmousetv.data.api.proto.RemoteMessageEncoder
import com.prasoon.airmousetv.data.model.TvKey

/** Builds ready-to-send frames for remote-control key events. */
object KeyPayloadFactory {

    /** A single tap of [key]. */
    fun keyPress(key: TvKey): ByteArray =
        RemoteMessageEncoder.encodeKeyPress(key)
}
