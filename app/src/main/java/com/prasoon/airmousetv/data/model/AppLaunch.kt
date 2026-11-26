package com.prasoon.airmousetv.data.model

import com.prasoon.airmousetv.data.api.TvApp

/** An app being opened on the TV from the Apps sheet, shown as a short card over the pad. */
data class AppLaunch(val app: TvApp, val stage: Stage) {
    enum class Stage {
        /** The link was sent and the TV hasn't shown the app yet. */
        OPENING,

        /** The TV showed the Play Store instead: the app isn't installed, and its store page is open. */
        NOT_INSTALLED,

        /** The TV never showed the app; typically an app installed outside the Play Store. */
        DID_NOT_OPEN,
    }
}
