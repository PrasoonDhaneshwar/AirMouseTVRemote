package com.prasoon.airmousetv.ui

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
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

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge status/navigation bars
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        setContent {
            AirMouseTVRemoteTheme {
                val windowSizeClass = calculateWindowSizeClass(this)
                DiscoveryScreenContent(windowSizeClass = windowSizeClass)
            }
        }
    }
}

@Composable
private fun DiscoveryScreenContent(
    windowSizeClass: androidx.compose.material3.windowsizeclass.WindowSizeClass,
    viewModel: RemoteViewModel = hiltViewModel()
) {
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
