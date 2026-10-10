package com.prasoon.airmousetv.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.prasoon.airmousetv.data.model.DiscoveredTv
import com.prasoon.airmousetv.data.model.RemoteMode
import com.prasoon.airmousetv.data.model.RemoteUiState
import com.prasoon.airmousetv.ui.theme.AirMouseTVRemoteTheme
import com.prasoon.airmousetv.presentation.RemoteViewModel

private const val CODE_LENGTH = 6

/**
 * Shown while connecting to a TV. Displays a spinner until the TV asks for its pairing code,
 * then six boxes for the 6-digit hex code. Cancel is always available.
 */
@Composable
fun PairingScreen(
    viewModel: RemoteViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    PairingContent(
        uiState = uiState,
        onCodeChange = viewModel::updatePairingCode,
        onSubmit = viewModel::submitPairingCode,
        onCancel = viewModel::cancelPairing,
        modifier = modifier
    )
}

@Composable
private fun PairingContent(
    uiState: RemoteUiState,
    onCodeChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tv = (uiState.mode as? RemoteMode.Pairing)?.tv

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onCancel() }) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel pairing")
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (uiState.awaitingCode) "AWAITING CODE" else "CONNECTING",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.size(48.dp))
            }

            Spacer(Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Tv, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            if (tv != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Target: ${tv.displayName}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${tv.host}:${tv.port}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(32.dp))

            if (uiState.awaitingCode) {
                Text("Enter Pairing Code", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Input the 6-character code currently displayed on your TV screen.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(32.dp))
                CodeField(
                    code = uiState.pairingCode,
                    onCodeChange = onCodeChange
                )
            } else if (uiState.error == null) {
                Spacer(Modifier.height(24.dp))
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                Text("Connecting to TV…", style = MaterialTheme.typography.titleMedium)
            } else {
                // Failed before a code was asked for: no spinner, just a way out
                Spacer(Modifier.height(24.dp))
                Text("Couldn't connect", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = onCancel, shape = MaterialTheme.shapes.medium) {
                    Text("Back to list", style = MaterialTheme.typography.titleSmall)
                }
            }

            uiState.error?.let {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.weight(1f))

            if (uiState.awaitingCode) {
                Button(
                    onClick = { onSubmit() },
                    enabled = uiState.pairingCode.length == CODE_LENGTH,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text("Connect Device", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/**
 * Six boxes showing [code], filled from a hidden text field so the system keyboard supplies
 * 0-9 and A-F. Input is upper-cased and limited to hex digits.
 */
@Composable
private fun CodeField(code: String, onCodeChange: (String) -> Unit) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    BasicTextField(
        value = code,
        onValueChange = { raw ->
            onCodeChange(raw.uppercase().filter { it in '0'..'9' || it in 'A'..'F' }.take(CODE_LENGTH))
        },
        modifier = Modifier.focusRequester(focusRequester),
        singleLine = true,
        cursorBrush = SolidColor(Color.Transparent),
        textStyle = MaterialTheme.typography.titleLarge.copy(color = Color.Transparent),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Ascii,
            capitalization = KeyboardCapitalization.Characters
        ),
        decorationBox = { innerTextField ->
            // The real field must stay composed to hold focus and the keyboard; the boxes are what the user sees
            Box(Modifier.size(1.dp).alpha(0f)) { innerTextField() }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(CODE_LENGTH) { index ->
                    if (index == CODE_LENGTH / 2) {
                        Box(Modifier.size(width = 8.dp, height = 2.dp).background(MaterialTheme.colorScheme.outline, CircleShape))
                    }
                    CodeBox(char = code.getOrNull(index), active = index == code.length)
                }
            }
        }
    )
}

@Composable
private fun CodeBox(char: Char?, active: Boolean) {
    val teal = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .size(width = 46.dp, height = 60.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(
                BorderStroke(if (active) 2.dp else 1.dp, if (active) teal else MaterialTheme.colorScheme.outline),
                MaterialTheme.shapes.medium
            ),
        contentAlignment = Alignment.Center
    ) {
        if (char != null) Text(char.toString(), style = MaterialTheme.typography.headlineMedium)
    }
}

private val previewTv = DiscoveredTv(name = "living-room-tv", friendlyName = "Living Room TV", host = "192.168.1.15", port = 6466)

@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "Entering code")
@Composable
private fun PairingCodePreview() {
    AirMouseTVRemoteTheme {
        PairingContent(
            uiState = RemoteUiState(mode = RemoteMode.Pairing(previewTv), awaitingCode = true, pairingCode = "4F2"),
            onCodeChange = {}, onSubmit = {}, onCancel = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "Connecting")
@Composable
private fun PairingConnectingPreview() {
    AirMouseTVRemoteTheme {
        PairingContent(
            uiState = RemoteUiState(mode = RemoteMode.Pairing(previewTv)),
            onCodeChange = {}, onSubmit = {}, onCancel = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "Wrong code")
@Composable
private fun PairingErrorPreview() {
    AirMouseTVRemoteTheme {
        PairingContent(
            uiState = RemoteUiState(
                mode = RemoteMode.Pairing(previewTv), awaitingCode = true,
                pairingCode = "4F2A91", error = "That code did not match. Check the TV and try again."
            ),
            onCodeChange = {}, onSubmit = {}, onCancel = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "Couldn't connect")
@Composable
private fun PairingFailedPreview() {
    AirMouseTVRemoteTheme {
        PairingContent(
            uiState = RemoteUiState(
                mode = RemoteMode.Pairing(previewTv),
                error = "Living Room TV didn't respond. Make sure it is on, then try again."
            ),
            onCodeChange = {}, onSubmit = {}, onCancel = {}
        )
    }
}
