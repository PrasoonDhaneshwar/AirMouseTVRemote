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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SignalWifi4Bar
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.prasoon.airmousetv.data.repository.DiscoveredTv
import com.prasoon.airmousetv.presentation.RemoteViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoveryScreen(
    viewModel: RemoteViewModel = hiltViewModel(),
    onTvSelected: (DiscoveredTv) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    // NEW: Lifecycle-aware discovery
    LaunchedEffect(Unit) {
        viewModel.startDiscovery()
    }
    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopDiscovery()
        }
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0A0A0A),
                        Color(0xFF1A1A2E),
                        Color(0xFF16213E)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            // Scanning Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                if (uiState.isDiscovering) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            color = Color.Cyan,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Scanning Network",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Scanning for compatible displays...",
                                fontSize = 14.sp,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Available devices",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Cyan
                    )
                    Text(
                        text = "${uiState.discoveredTvs.size} found",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }

                IconButton(
                    onClick = { viewModel.retryDiscovery() },
                    modifier = Modifier.size(40.dp)
                ) {

                    val infiniteTransition = rememberInfiniteTransition(label = "refresh")

                    val rotation by if (uiState.isRefreshing) {
                        infiniteTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1000, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "rotation"
                        )
                    } else {
                        remember { mutableStateOf(0f) }
                    }
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Refresh scan",
                        tint = Color.Cyan,
                        modifier = Modifier
                            .size(24.dp)
                            .graphicsLayer(rotationZ = rotation)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // TV List
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(uiState.discoveredTvs) { tv ->
                    TargetItem(
                        tv = tv,
                        onClick = { onTvSelected(tv) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedButton(
                onClick = { viewModel.clearTvCache() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
            ) {
                Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = Color.White.copy(alpha = 0.7f))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Clear TV Cache",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { /* Connect */ },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan)
            ) {
                Text(
                    text = "ESTABLISH LINK",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }
        }
    }
}

@Composable
fun TargetItem(
    tv: DiscoveredTv,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2A2A3E).copy(alpha = 0.6f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Device Icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Cyan.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Tv, contentDescription = null, tint = Color.Cyan, modifier = Modifier.size(24.dp))
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Device Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tv.displayName,  // Uses friendlyName or truncated rawName
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = tv.name.take(8) + "..." + " • ${tv.host}",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }

            // Signal Strength
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SignalWifi4Bar, tint = Color.Cyan.copy(alpha = 0.7f), contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.Default.ArrowForward, tint = Color.White.copy(alpha = 0.5f), contentDescription = null)
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
fun DiscoveryScreenPreview() {
    DiscoveryScreen(
        onTvSelected = {}
    )
}