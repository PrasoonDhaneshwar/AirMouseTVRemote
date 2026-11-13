package com.prasoon.airmousetv.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.prasoon.airmousetv.data.api.parseIpv4
import com.prasoon.airmousetv.data.model.DiscoveredTv
import com.prasoon.airmousetv.data.model.RemoteMode
import com.prasoon.airmousetv.data.model.RemoteUiState
import com.prasoon.airmousetv.presentation.RemoteViewModel
import com.prasoon.airmousetv.ui.theme.AirMouseTVRemoteTheme
import com.prasoon.airmousetv.ui.theme.Amber
import com.prasoon.airmousetv.ui.theme.AmberText
import com.prasoon.airmousetv.ui.theme.Online
import com.prasoon.airmousetv.ui.theme.PadSurface
import com.prasoon.airmousetv.ui.theme.SurfaceHigh
import com.prasoon.airmousetv.ui.theme.SurfaceMid
import kotlinx.coroutines.awaitCancellation

/** Lists TVs found on the network. Tapping a row connects to it ([onTvSelected]). */
@Composable
fun DiscoveryScreen(
    viewModel: RemoteViewModel = hiltViewModel(),
    onTvSelected: (DiscoveredTv) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Discovery runs only while the screen is STARTED. The block is cancelled when the app goes to
    // the background (STOPPED) and when this screen leaves the composition, and either way the
    // finally stops the scan and the liveness probes.
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.startDiscovery()
            try {
                awaitCancellation()
            } finally {
                viewModel.stopDiscovery()
            }
        }
    }

    DiscoveryContent(
        // While this screen fades out the TV has been picked, so "checking" is already over: keep drawing
        // the list as it was rather than let the headline change under the user's finger
        uiState = rememberLastNonNull(uiState.takeIf { it.mode is RemoteMode.Discovery }) ?: uiState,
        onRefresh = viewModel::retryDiscovery,
        onTvClick = onTvSelected,
        onAddByIp = viewModel::addTvByAddress,
        onDismissError = viewModel::dismissError,
        modifier = modifier
    )
}

@Composable
private fun DiscoveryContent(
    uiState: RemoteUiState,
    onRefresh: () -> Unit,
    onTvClick: (DiscoveredTv) -> Unit,
    modifier: Modifier = Modifier,
    onAddByIp: (String) -> Unit = {},
    onDismissError: () -> Unit = {}
) {
    var showAddDialog by remember { mutableStateOf(false) }
    val scanning = uiState.isDiscovering
    val checking = uiState.isCheckingTv
    val tvs = uiState.discoveredTvs
    // Nothing found and nothing still looking: the list makes way for what to check
    val nothingFound = tvs.isEmpty() && !scanning && !checking
    // The row that was tapped, so only it shows the check; a TV added by IP has no row yet
    var checkingHost by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(checking) { if (!checking) checkingHost = null }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Header(
            scanning = scanning,
            checking = checking,
            found = tvs.size,
            refreshing = uiState.isRefreshing,
            // With nothing found, scanning again is the screen's main button instead
            onRefresh = onRefresh.takeUnless { nothingFound }
        )

        uiState.error?.let { ErrorCard(it, onScanAgain = onRefresh, onDismiss = onDismissError) }

        if (nothingFound) {
            NothingFound(Modifier.weight(1f))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryAction("Scan again", onRefresh)
                SecondaryAction("Add by IP address", { showAddDialog = true })
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                items(tvs, key = { it.name }) { tv ->
                    TvRow(
                        tv = tv,
                        checking = checking && tv.host == checkingHost,
                        enabled = !checking,
                        onClick = {
                            checkingHost = tv.host
                            onTvClick(tv)
                        },
                        // TVs slide into place as the scan finds them, instead of the list jumping
                        modifier = Modifier.animateItem()
                    )
                }
                // More may still turn up: the scan isn't over
                if (scanning) item(key = "scanning") { ScanningRow(Modifier.animateItem()) }
            }
            SecondaryAction("Add by IP address", { showAddDialog = true }, enabled = !checking)
        }
    }

    if (showAddDialog) {
        AddByIpDialog(
            onAdd = {
                showAddDialog = false
                onAddByIp(it)
            },
            onDismiss = { showAddDialog = false }
        )
    }
}

/** One headline saying what is happening, one line saying what to do, and the scan button. */
@Composable
private fun Header(scanning: Boolean, checking: Boolean, found: Int, refreshing: Boolean, onRefresh: (() -> Unit)?) {
    val busy = scanning || checking
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ScanIndicator(
            active = busy,
            color = when {
                busy -> MaterialTheme.colorScheme.primary
                found > 0 -> Online
                else -> MaterialTheme.colorScheme.outline
            }
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = when {
                    checking -> "Checking TV"
                    scanning -> "Looking for TVs"
                    found > 0 -> "Tap your TV"
                    else -> "No TVs found"
                },
                // Sized so the longest headline, "Looking for TVs", stays on one line on a 360 dp wide phone
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 24.sp, letterSpacing = 0.sp),
                maxLines = 1
            )
            Text(
                text = when {
                    checking -> "Making sure it's awake."
                    scanning && found > 0 -> "Tap your TV to connect."
                    scanning -> "They appear here as they're found."
                    found == 1 -> "1 TV on this Wi-Fi."
                    found > 1 -> "$found TVs on this Wi-Fi."
                    else -> "Check these, then scan again."
                },
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        if (onRefresh != null) {
            FilledIconButton(
                onClick = onRefresh,
                enabled = !checking,
                modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                if (refreshing) SpinningRefreshIcon() else RefreshIcon()
            }
        }
    }
}

/** Something went wrong with a TV. Sits above the list, which stays usable, and offers the way out. */
@Composable
private fun ErrorCard(message: String, onScanAgain: () -> Unit, onDismiss: () -> Unit) {
    NoticeCard {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Amber, modifier = Modifier.padding(top = 2.dp).size(20.dp))
            Column(Modifier.weight(1f)) {
                Text(message, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onScanAgain, contentPadding = PaddingValues(horizontal = 2.dp)) {
                    Text("Scan again", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = AmberText)
                }
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Close, contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/** What to check when the scan comes back empty, in the order most likely to be the cause. */
@Composable
private fun NothingFound(modifier: Modifier = Modifier) {
    val hint = MaterialTheme.colorScheme.onSurfaceVariant
    val line = MaterialTheme.colorScheme.outline
    // Scrolls, so the checklist is still all there on a short screen or with large text
    Column(modifier.verticalScroll(rememberScrollState())) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 36.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Smartphone, contentDescription = null, tint = hint, modifier = Modifier.size(44.dp))
            // A broken line: the phone and the TV aren't reaching each other
            Canvas(Modifier.size(width = 72.dp, height = 2.dp)) {
                drawLine(
                    line, Offset(0f, size.height / 2), Offset(size.width, size.height / 2),
                    strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 8.dp.toPx()))
                )
            }
            Icon(Icons.Default.Tv, contentDescription = null, tint = hint, modifier = Modifier.size(56.dp))
        }
        listOf(
            "The TV is switched on",
            "The phone and TV are on the same Wi-Fi",
            "Guest or office Wi-Fi may hide the TV"
        ).forEachIndexed { index, check ->
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Row(
                modifier = Modifier.padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(28.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape), contentAlignment = Alignment.Center) {
                    Text("${index + 1}", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold))
                }
                Text(check, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp))
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
    }
}

/** Asks for the TV's IPv4 address, for networks where it can't be found automatically. */
@Composable
private fun AddByIpDialog(onAdd: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val address = parseIpv4(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add TV by IP address") },
        text = {
            Column {
                Text(
                    "Find it on the TV under Settings > Network, then enter it here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("IP address") },
                    placeholder = { Text("192.168.1.20") },
                    singleLine = true,
                    isError = text.isNotBlank() && address == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { address?.let(onAdd) })
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { address?.let(onAdd) }, enabled = address != null) { Text("Connect") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** A pulsing dot while busy; a steady one, in [color], once done. */
@Composable
private fun ScanIndicator(active: Boolean, color: Color) {
    Box(modifier = Modifier.size(56.dp), contentAlignment = Alignment.Center) {
        if (active) PulsingRing(color)
        Box(Modifier.size(14.dp).background(color, CircleShape))
    }
}

/** The expanding, fading ring around the dot. Its animation only exists while it is on screen. */
@Composable
private fun PulsingRing(color: Color) {
    val transition = rememberInfiniteTransition(label = "scan")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 3.6f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "pulse"
    )
    Box(
        Modifier
            .size(14.dp)
            // The value is read inside the lambda, so each frame only redraws instead of recomposing
            .graphicsLayer {
                scaleX = pulse
                scaleY = pulse
                alpha = (3.6f - pulse) / 2.6f * 0.4f
            }
            .background(color, CircleShape)
    )
}

@Composable
private fun SpinningRefreshIcon() {
    val transition = rememberInfiniteTransition(label = "refresh")
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart),
        label = "rotation"
    )
    RefreshIcon(rotation = { spin })
}

/** The scan-again icon turned by [rotation] degrees; the value is read when drawing, not when composing. */
@Composable
private fun RefreshIcon(rotation: () -> Float = { 0f }) {
    Icon(
        Icons.Default.Refresh,
        contentDescription = "Scan again",
        modifier = Modifier.size(22.dp).graphicsLayer { rotationZ = rotation() }
    )
}

/**
 * One TV in the list. While [checking] it is outlined in teal and says so; the rows that weren't
 * tapped are dimmed and off ([enabled] false), so a second tap can't start another check.
 */
@Composable
private fun TvRow(tv: DiscoveredTv, checking: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val hint = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(72.dp).alpha(if (enabled || checking) 1f else 0.45f),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = if (checking) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp).background(SurfaceHigh, MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Tv, contentDescription = null, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    tv.displayName,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (checking) "Checking it's on…" else tv.host,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    color = hint
                )
            }
            if (checking) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                // Green is only ever "this TV is answering right now"
                Box(Modifier.size(8.dp).background(Online, CircleShape))
                if (enabled) Icon(Icons.Default.ChevronRight, contentDescription = null, tint = hint, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** A row-shaped placeholder with a slow sheen, at the end of the list while the scan is still running. */
@Composable
private fun ScanningRow(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "sheen")
    val sweep by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
        label = "sweep"
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(MaterialTheme.shapes.medium)
            .drawBehind {
                val start = size.width * sweep
                drawRect(
                    Brush.horizontalGradient(
                        listOf(PadSurface, SurfaceMid, PadSurface),
                        startX = start - size.width, endX = start + size.width
                    )
                )
            }
            // Decoration only: a screen reader hears the headline say that the scan is running
            .clearAndSetSemantics { }
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(40.dp).background(SurfaceMid, MaterialTheme.shapes.small))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(width = 150.dp, height = 12.dp).background(SurfaceHigh, CircleShape))
            Box(Modifier.size(width = 90.dp, height = 10.dp).background(SurfaceHigh, CircleShape))
        }
    }
}

private val previewTvs = listOf(
    DiscoveredTv(name = "living-room-tv", friendlyName = "Living Room TV", host = "192.168.1.15", port = 6466),
    DiscoveredTv(name = "bedroom-roku", friendlyName = "Bedroom Roku", host = "192.168.1.22", port = 6466),
    DiscoveredTv(name = "samsung-7", friendlyName = "Samsung Series 7", host = "192.168.1.40", port = 6466),
)

@Composable
private fun DiscoveryPreview(uiState: RemoteUiState) {
    AirMouseTVRemoteTheme { DiscoveryContent(uiState = uiState, onRefresh = {}, onTvClick = {}) }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "Scanning")
@Composable
private fun DiscoveryScanningPreview() = DiscoveryPreview(RemoteUiState(discoveredTvs = previewTvs.take(1), isDiscovering = true))

@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "Scan complete")
@Composable
private fun DiscoveryCompletePreview() = DiscoveryPreview(RemoteUiState(discoveredTvs = previewTvs))

@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "Checking TV")
@Composable
private fun DiscoveryCheckingPreview() = DiscoveryPreview(RemoteUiState(discoveredTvs = previewTvs, isCheckingTv = true))

@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "Nothing found")
@Composable
private fun DiscoveryEmptyPreview() = DiscoveryPreview(RemoteUiState())

@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "TV didn't answer")
@Composable
private fun DiscoveryUnreachablePreview() = DiscoveryPreview(
    RemoteUiState(
        discoveredTvs = previewTvs.drop(1),
        error = "Living Room TV didn't answer, so it was taken off the list. Turn it on, then scan again."
    )
)
