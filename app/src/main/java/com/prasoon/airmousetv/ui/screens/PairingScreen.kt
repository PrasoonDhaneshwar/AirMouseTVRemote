package com.prasoon.airmousetv.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prasoon.airmousetv.presentation.RemoteViewModel

/**
 * Shown while connecting to a TV. Displays a spinner until the TV asks for its pairing code,
 * then a field for the 6-digit hex code. Cancel is always available.
 */
@Composable
fun PairingScreen(
    viewModel: RemoteViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            if (uiState.awaitingCode) {
                Text(
                    text = "Enter Pairing Code",
                    fontSize = 22.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = uiState.pairingCode,
                    onValueChange = { viewModel.updatePairingCode(it) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Ascii,
                        capitalization = KeyboardCapitalization.Characters
                    ),
                    singleLine = true,
                    label = { Text("Code shown on TV") }
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { viewModel.submitPairingCode() },
                    enabled = uiState.pairingCode.length == 6,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("PAIR")
                }
            } else {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "Connecting to TV…", fontSize = 18.sp)
            }

            uiState.error?.let {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = { viewModel.cancelPairing() }) {
                Text("Cancel")
            }
        }
    }
}
