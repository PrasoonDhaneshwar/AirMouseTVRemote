package com.prasoon.airmousetv.data.api

import com.prasoon.airmousetv.data.model.TvKey
import com.prasoon.airmousetv.proto.remote.RemoteKeyCode

/** Translates the app's [TvKey]s into the Android key codes the TV's remote service understands. */
object TvKeyMapper {
    fun toRemoteKeyCode(key: TvKey): RemoteKeyCode = when (key) {
        TvKey.UP -> RemoteKeyCode.KEYCODE_DPAD_UP
        TvKey.DOWN -> RemoteKeyCode.KEYCODE_DPAD_DOWN
        TvKey.LEFT -> RemoteKeyCode.KEYCODE_DPAD_LEFT
        TvKey.RIGHT -> RemoteKeyCode.KEYCODE_DPAD_RIGHT
        TvKey.OK -> RemoteKeyCode.KEYCODE_DPAD_CENTER
        TvKey.BACK -> RemoteKeyCode.KEYCODE_BACK
        TvKey.HOME -> RemoteKeyCode.KEYCODE_HOME
        TvKey.VOLUME_UP -> RemoteKeyCode.KEYCODE_VOLUME_UP
        TvKey.VOLUME_DOWN -> RemoteKeyCode.KEYCODE_VOLUME_DOWN
        TvKey.POWER -> RemoteKeyCode.KEYCODE_POWER
        TvKey.MUTE -> RemoteKeyCode.KEYCODE_MUTE
        TvKey.PLAY_PAUSE -> RemoteKeyCode.KEYCODE_MEDIA_PLAY_PAUSE
        TvKey.PREVIOUS -> RemoteKeyCode.KEYCODE_MEDIA_PREVIOUS
        TvKey.NEXT -> RemoteKeyCode.KEYCODE_MEDIA_NEXT
        TvKey.ENTER -> RemoteKeyCode.KEYCODE_ENTER
    }
}
