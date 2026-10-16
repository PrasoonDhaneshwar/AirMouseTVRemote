package com.prasoon.airmousetv.ui

import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.prasoon.airmousetv.data.model.RemoteMode
import com.prasoon.airmousetv.presentation.RemoteViewModel
import com.prasoon.airmousetv.ui.screens.DiscoveryScreen
import com.prasoon.airmousetv.ui.screens.PairingScreen
import com.prasoon.airmousetv.ui.screens.RemoteScreen
import com.prasoon.airmousetv.ui.theme.AirMouseTVRemoteTheme
import dagger.hilt.android.AndroidEntryPoint

private const val TAG = "MainActivity"

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

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
                DiscoveryScreenContent()
            }
        }
    }
}

@Composable
private fun DiscoveryScreenContent(viewModel: RemoteViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    when (val mode = uiState.mode) {
        is RemoteMode.Discovery -> {
            Log.d(TAG, "Displaying DiscoveryScreen")
            DiscoveryScreen(
                viewModel = viewModel,
                onTvSelected = viewModel::selectTv
            )
        }

        is RemoteMode.Pairing -> {
            Log.d(TAG, "Displaying PairingScreen for TV: ${mode.tv.displayName}")
            PairingScreen(viewModel)
        }

        is RemoteMode.Connected -> {
            Log.d(TAG, "Displaying RemoteScreen for TV: ${mode.tv.displayName}")
            RemoteScreen(viewModel)
        }
    }
}
