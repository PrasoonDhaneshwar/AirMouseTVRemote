package com.prasoon.airmousetv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** The design is dark-only: it is meant for dimly lit rooms, so there is no light or dynamic scheme. */
private val AirMouseColorScheme = darkColorScheme(
    primary = Teal,
    onPrimary = OnTeal,
    primaryContainer = TealDim,
    onPrimaryContainer = TextPrimary,
    secondary = Amber,
    onSecondary = OnAmber,
    background = Charcoal,
    onBackground = TextPrimary,
    surface = Charcoal,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceMid,
    onSurfaceVariant = TextSecondary,
    surfaceContainer = SurfaceMid,
    surfaceContainerHigh = SurfaceHigh,
    outline = Outline,
    error = Amber,
    onError = OnAmber,
)

private val AirMouseShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun AirMouseTVRemoteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AirMouseColorScheme,
        typography = Typography,
        shapes = AirMouseShapes
    ) {
        // Without a Surface the default content colour is black, which is invisible on this dark background
        Surface(color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
            content()
        }
    }
}
