package com.prasoon.airmousetv.ui

import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.prasoon.airmousetv.data.model.KeyAction
import com.prasoon.airmousetv.data.model.RemoteMode
import com.prasoon.airmousetv.data.model.TvKey
import com.prasoon.airmousetv.presentation.RemoteViewModel
import com.prasoon.airmousetv.ui.screens.DiscoveryScreen
import com.prasoon.airmousetv.ui.screens.PairingScreen
import com.prasoon.airmousetv.ui.screens.RemoteScreen
import com.prasoon.airmousetv.ui.screens.SCREEN_FADE_MS
import com.prasoon.airmousetv.ui.theme.AirMouseTVRemoteTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: RemoteViewModel by viewModels()

    /** The TV volume key that a held phone volume button is holding down, or null when none is. */
    private var heldVolumeKey: TvKey? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // A remote isn't meant to rotate on a phone. Tablets, foldables and Chrome OS (sw >= 600dp) stay
        // free to rotate, since Android 16 ignores fixed orientations there anyway.
        if (resources.configuration.smallestScreenWidthDp < 600) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        // Edge-to-edge status/navigation bars
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        setContent {
            AirMouseTVRemoteTheme {
                AppScreens(viewModel)
            }
        }
    }

    /**
     * While the remote is showing, the phone's volume buttons change the TV's volume: it is the
     * volume control already under the user's finger. On any other screen, and while the connection
     * is being re-established, they stay the phone's.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val key = tvVolumeKey(keyCode) ?: return super.onKeyDown(keyCode, event)
        // Android repeats a held button, starting after about the time the on-screen keys take to
        // count as held; the first repeat is where a tap turns into a hold
        if (event.repeatCount > 0 && heldVolumeKey == null) {
            heldVolumeKey = key
            viewModel.sendKey(key, KeyAction.LONG_START)
        }
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (heldVolumeKey != null && (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            releaseHeldVolumeKey()
            return true
        }
        val key = tvVolumeKey(keyCode) ?: return super.onKeyUp(keyCode, event)
        // Like the on-screen keys, a tap is sent on release, once it is known not to be a hold
        viewModel.sendKey(key, KeyAction.TAP)
        return true
    }

    /**
     * A window that loses focus (the notification shade, a dialog, another app) never sees the
     * button come back up. Without this the TV would keep the key held and run its volume all the
     * way up or down.
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) releaseHeldVolumeKey()
    }

    private fun releaseHeldVolumeKey() {
        val key = heldVolumeKey ?: return
        heldVolumeKey = null
        viewModel.sendKey(key, KeyAction.LONG_END)
    }

    /** The TV key a phone volume button stands for right now, or null to leave the button to the phone. */
    private fun tvVolumeKey(keyCode: Int): TvKey? {
        val state = viewModel.uiState.value
        if (state.mode !is RemoteMode.Connected || state.isReconnecting) return null
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> TvKey.VOLUME_UP
            KeyEvent.KEYCODE_VOLUME_DOWN -> TvKey.VOLUME_DOWN
            else -> null
        }
    }
}

/** Where a screen sits in the Discovery → Pairing → Remote flow, so a change knows whether it goes forward or back. */
private val RemoteMode.step: Int
    get() = when (this) {
        RemoteMode.Discovery -> 0
        is RemoteMode.Pairing -> 1
        is RemoteMode.Connected -> 2
    }

/**
 * Picks the screen from the connection state and fades between them. Going forward the new screen
 * grows into place; going back the old one shrinks away, so the two directions can be told apart
 * even though there is no back stack. A hard cut here used to feel like the app restarting.
 */
@Composable
private fun AppScreens(viewModel: RemoteViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    // There is no back stack: screens are picked from state, so Back must be told what "back" means here.
    // It lives outside the fading content, or a pairing screen on its way out would still catch Back
    BackHandler(enabled = uiState.mode is RemoteMode.Pairing, onBack = viewModel::cancelPairing)

    AnimatedContent(
        targetState = uiState.mode.step,
        transitionSpec = {
            val fade = tween<Float>(SCREEN_FADE_MS)
            if (targetState > initialState) {
                (fadeIn(fade) + scaleIn(fade, initialScale = 0.96f)) togetherWith fadeOut(fade)
            } else {
                fadeIn(fade) togetherWith (fadeOut(fade) + scaleOut(fade, targetScale = 0.96f))
            }
        },
        label = "screen"
    ) { step ->
        when (step) {
            0 -> DiscoveryScreen(viewModel = viewModel, onTvSelected = viewModel::selectTv)
            1 -> PairingScreen(viewModel)
            else -> RemoteScreen(viewModel)
        }
    }
}
