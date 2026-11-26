package com.prasoon.airmousetv.data.model

/** Why a voice search couldn't finish; each has its own message in the voice panel. */
enum class VoiceError {
    /** The connection to the TV dropped while the user was talking; nothing was sent. */
    WIFI_LOST,
    /** Another app (a call, a recorder) holds the microphone. */
    MIC_BUSY,
    /** The TV didn't open a voice session after the search key. */
    TV_NO_ANSWER
}

/** Where a voice search is, as shown by the Ask key and the panel over the pad. */
sealed interface VoiceState {
    /** Nothing going on: the Ask key at rest. */
    data object Idle : VoiceState

    /**
     * The microphone is on. [levels] are the latest sound levels (0 to 1), oldest first, for the bars.
     * [cancelArmed]: the finger has slid off the key, so letting go cancels. [tapMode]: started by a
     * tap (as TalkBack does), so it runs until Stop is tapped.
     */
    data class Listening(
        val levels: List<Float> = emptyList(),
        val cancelArmed: Boolean = false,
        val tapMode: Boolean = false
    ) : VoiceState

    /** The voice went to the TV; its results show there. */
    data object Sent : VoiceState

    /** The search ended without reaching the TV's assistant. */
    data class Failed(val error: VoiceError) : VoiceState
}
