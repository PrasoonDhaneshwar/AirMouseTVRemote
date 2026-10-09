package com.prasoon.airmousetv.data.model

/**
 * Logical remote keys exposed to the UI.
 * [com.prasoon.airmousetv.data.api.TvKeyMapper] maps each one to a protocol-level
 * [com.prasoon.airmousetv.proto.remote.RemoteKeyCode].
 */
enum class TvKey {
    UP,
    DOWN,
    LEFT,
    RIGHT,
    OK,
    BACK,
    HOME,
    VOLUME_UP,
    VOLUME_DOWN,
    POWER,
    MUTE,
    PLAY_PAUSE,
    PREVIOUS,
    NEXT,
    ENTER
}
