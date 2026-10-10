package com.prasoon.airmousetv.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SignalWifi4Bar
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.prasoon.airmousetv.data.model.DiscoveredTv
import com.prasoon.airmousetv.data.model.RemoteUiState
import com.prasoon.airmousetv.presentation.RemoteViewModel
import com.prasoon.airmousetv.ui.theme.AirMouseTVRemoteTheme
import com.prasoon.airmousetv.ui.theme.Online

/** Lists TVs found on the network. Tapping a row connects to it ([onTvSelected]). */
@Composable
fun DiscoveryScreen(
    viewModel: RemoteViewModel = hiltViewModel(),
    onTvSelected: (DiscoveredTv) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Starts discovery when STARTED, cancels when STOPPED
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.startDiscovery()
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopDiscovery()
        }
    }

    DiscoveryContent(
        uiState = uiState,
        onRefresh = viewModel::retryDiscovery,
        onTvClick = onTvSelected,
        modifier = modifier
    )
}

@Composable
private fun DiscoveryContent(
    uiState: RemoteUiState,
    onRefresh: () -> Unit,
    onTvClick: (DiscoveredTv) -> Unit,
    modifier: Modifier = Modifier
) {
    val scanning = uiState.isDiscovering
    val checking = uiState.isCheckingTv
    val found = uiState.discoveredTvs.isNotEmpty()

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "DISCOVERY MODE",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.height(32.dp))

            ScanIndicator(
                active = scanning || checking,
                color = when {
                    scanning || checking -> MaterialTheme.colorScheme.primary
                    found -> Online
                    else -> MaterialTheme.colorScheme.secondary
                }
            )

            Spacer(Modifier.height(16.dp))
            Text(
                text = when {
                    checking -> "Checking TV"
                    scanning -> "Scanning Network"
                    found -> "Scan Complete"
                    else -> "No Displays Found"
                },
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = when {
                    checking -> "Making sure it is awake..."
                    scanning -> "Searching for compatible displays..."
                    found -> "Tap a display to connect."
                    else -> "Make sure your TV is on and connected to the same Wi-Fi, then tap refresh."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            uiState.error?.let {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "AVAILABLE TARGETS",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${uiState.discoveredTvs.size} FOUND",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    RefreshButton(spinning = uiState.isRefreshing, onClick = onRefresh)
                }
            }

            Spacer(Modifier.height(8.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(uiState.discoveredTvs, key = { it.name }) { tv ->
                    TargetItem(tv = tv, enabled = !checking, onClick = { onTvClick(tv) })
                }
            }
        }
    }
}

/** A pulsing dot while busy; a steady one, in [color], once done. */
@Composable
private fun ScanIndicator(active: Boolean, color: Color) {
    val transition = rememberInfiniteTransition(label = "scan")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 2.4f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "pulse"
    )
    Box(modifier = Modifier.size(72.dp), contentAlignment = Alignment.Center) {
        if (active) {
            Box(
                Modifier
                    .size(16.dp)
                    .graphicsLayer(scaleX = pulse, scaleY = pulse, alpha = (2.4f - pulse) / 1.4f * 0.5f)
                    .background(color, CircleShape)
            )
        }
        Box(Modifier.size(16.dp).background(color, CircleShape))
    }
}

@Composable
private fun RefreshButton(spinning: Boolean, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "refresh")
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart),
        label = "rotation"
    )
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(
            Icons.Default.Refresh,
            contentDescription = "Refresh scan",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp).graphicsLayer(rotationZ = if (spinning) spin else 0f)
        )
    }
}

@Composable
fun TargetItem(
    tv: DiscoveredTv,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(72.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Tv,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(text = tv.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(
                    text = tv.host,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                Icons.Default.SignalWifi4Bar,
                contentDescription = "Online",
                tint = Online
            )
        }
    }
}

private val previewTvs = listOf(
    DiscoveredTv(name = "living-room-tv", friendlyName = "Living Room TV", host = "192.168.1.15", port = 6466),
    DiscoveredTv(name = "bedroom-roku", friendlyName = "Bedroom Roku", host = "192.168.1.22", port = 6466),
    DiscoveredTv(name = "samsung-7", friendlyName = "Samsung Series 7", host = "192.168.1.40", port = 6466),
)

@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "Scanning")
@Composable
private fun DiscoveryScanningPreview() {
    AirMouseTVRemoteTheme {
        DiscoveryContent(
            uiState = RemoteUiState(discoveredTvs = previewTvs, isDiscovering = true),
            onRefresh = {}, onTvClick = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "Scan complete")
@Composable
private fun DiscoveryCompletePreview() {
    AirMouseTVRemoteTheme {
        DiscoveryContent(
            uiState = RemoteUiState(discoveredTvs = previewTvs),
            onRefresh = {}, onTvClick = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "Checking TV")
@Composable
private fun DiscoveryCheckingPreview() {
    AirMouseTVRemoteTheme {
        DiscoveryContent(
            uiState = RemoteUiState(discoveredTvs = previewTvs, isCheckingTv = true),
            onRefresh = {}, onTvClick = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "Nothing found")
@Composable
private fun DiscoveryEmptyPreview() {
    AirMouseTVRemoteTheme {
        DiscoveryContent(
            uiState = RemoteUiState(),
            onRefresh = {}, onTvClick = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "TV not responding")
@Composable
private fun DiscoveryUnreachablePreview() {
    AirMouseTVRemoteTheme {
        DiscoveryContent(
            uiState = RemoteUiState(
                discoveredTvs = previewTvs.drop(1),
                error = "Living Room TV isn't responding at 192.168.1.15, so it was removed from the list. Turn it on and tap refresh."
            ),
            onRefresh = {}, onTvClick = {}
        )
    }
}
