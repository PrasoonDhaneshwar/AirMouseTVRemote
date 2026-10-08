package com.prasoon.airmousetv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.prasoon.airmousetv.data.model.TvKey
import com.prasoon.airmousetv.presentation.RemoteViewModel

/** The working remote: D-pad plus Back/Home/Mute, volume and power. Each button sends one key tap. */
@Composable
fun RemoteScreen(viewModel: RemoteViewModel) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Connected", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(24.dp))

        KeyButton("▲", TvKey.UP, viewModel)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KeyButton("◀", TvKey.LEFT, viewModel)
            KeyButton("OK", TvKey.OK, viewModel)
            KeyButton("▶", TvKey.RIGHT, viewModel)
        }
        KeyButton("▼", TvKey.DOWN, viewModel)

        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedKey("Back", TvKey.BACK, viewModel)
            OutlinedKey("Home", TvKey.HOME, viewModel)
            OutlinedKey("Mute", TvKey.MUTE, viewModel)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedKey("Vol −", TvKey.VOLUME_DOWN, viewModel)
            OutlinedKey("Vol +", TvKey.VOLUME_UP, viewModel)
            OutlinedKey("Power", TvKey.POWER, viewModel)
        }
    }
}

/** Filled button for the primary D-pad keys. */
@Composable
private fun KeyButton(label: String, key: TvKey, viewModel: RemoteViewModel) {
    Button(
        onClick = { viewModel.sendKey(key) },
        modifier = Modifier.padding(4.dp).size(width = 88.dp, height = 64.dp)
    ) { Text(label) }
}

/** Outlined button for the secondary keys. */
@Composable
private fun OutlinedKey(label: String, key: TvKey, viewModel: RemoteViewModel) {
    OutlinedButton(onClick = { viewModel.sendKey(key) }) { Text(label) }
}
