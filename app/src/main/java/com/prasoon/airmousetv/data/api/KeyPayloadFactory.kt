package com.prasoon.airmousetv.data.api

import com.prasoon.airmousetv.data.api.proto.RemoteMessageEncoder
import com.prasoon.airmousetv.data.model.KeyAction
import com.prasoon.airmousetv.data.model.TvKey
import com.prasoon.airmousetv.proto.remote.RemoteKeyCode

/** Builds ready-to-send frames for remote-control key events. */
object KeyPayloadFactory {

    /** [action] on [key]: a tap, or the start/end of a hold. */
    fun keyPress(key: TvKey, action: KeyAction = KeyAction.TAP): ByteArray =
        RemoteMessageEncoder.encodeKeyPress(key, action)

    /** [action] on a protocol key code directly; used for typing characters. */
    fun keyCode(code: RemoteKeyCode, action: KeyAction = KeyAction.TAP): ByteArray =
        RemoteMessageEncoder.encodeKeyCode(code, action)

    /** Text edit: [text] replaces the word at the cursor of the TV's focused text field. */
    fun textEdit(text: String): ByteArray =
        RemoteMessageEncoder.encodeTextEdit(text)

    /** Deletes the [count] characters before the cursor in the TV's focused text field. */
    fun textDelete(count: Int): ByteArray =
        RemoteMessageEncoder.encodeTextDelete(count)
}
