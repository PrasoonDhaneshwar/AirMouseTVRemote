package com.prasoon.airmousetv

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Color Palette
val TVDarkGrey = Color(0xFF1C1C1E)
val TVAccentGreen = Color(0xFF32D74B)
val TVSurfaceGrey = Color(0xFF2C2C2E)

@Composable
fun StandardRemoteScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(20.dp))

        // Volume and Channel Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            VerticalControlStack("VOL", Icons.Default.Add, Icons.Default.KeyboardArrowDown)
            VerticalControlStack("CH", Icons.Default.KeyboardArrowUp, Icons.Default.KeyboardArrowDown)
        }

        Spacer(modifier = Modifier.weight(1f)) // Push D-Pad to center

        // D-Pad Section
        DPadControl()

        Spacer(modifier = Modifier.weight(1f))

        // System Navigation
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 40.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            RemoteRoundButton(Icons.Default.ArrowBack, "Back")
            RemoteRoundButton(Icons.Default.Home, "Home")
            RemoteRoundButton(Icons.Default.Menu, "Menu")
        }
    }
}

@Composable
fun DPadControl() {
    Box(
        modifier = Modifier
            .size(240.dp)
            .background(TVSurfaceGrey, androidx.compose.foundation.shape.CircleShape),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        // Center OK Button
        Button(
            onClick = { /* Action */ },
            modifier = Modifier.size(85.dp),
            shape = androidx.compose.foundation.shape.CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = TVAccentGreen)
        ) {
            Text("OK", color = Color.Black, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        }

        // Directional Icons (Explicit naming to avoid compiler errors)
        val iconSize = 48.dp

        IconButton(onClick = { /* Up */ }, modifier = Modifier.align(androidx.compose.ui.Alignment.TopCenter)) {
            Icon(imageVector = Icons.Default.KeyboardArrowUp, contentDescription = "Up", tint = Color.White, modifier = Modifier.size(iconSize))
        }
        IconButton(onClick = { /* Down */ }, modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter)) {
            Icon(imageVector = Icons.Default.KeyboardArrowDown, contentDescription = "Down", tint = Color.White, modifier = Modifier.size(iconSize))
        }
        IconButton(onClick = { /* Left */ }, modifier = Modifier.align(androidx.compose.ui.Alignment.CenterStart)) {
            Icon(imageVector = Icons.Default.KeyboardArrowLeft, contentDescription = "Left", tint = Color.White, modifier = Modifier.size(iconSize))
        }
        IconButton(onClick = { /* Right */ }, modifier = Modifier.align(androidx.compose.ui.Alignment.CenterEnd)) {
            Icon(imageVector = Icons.Default.KeyboardArrowRight, contentDescription = "Right", tint = Color.White, modifier = Modifier.size(iconSize))
        }
    }
}

@Composable
fun VerticalControlStack(
    label: String,
    upIcon: ImageVector,
    downIcon: ImageVector
) {
    Column(
        modifier = Modifier
            .width(65.dp)
            .height(140.dp) // Fixed height to give it a "pill" look
            .background(TVSurfaceGrey, RoundedCornerShape(32.dp))
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Button (Plus / Channel Up)
        IconButton(
            onClick = { /* TODO: Send Volume Up / CH Up */ },
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = upIcon,
                contentDescription = "$label Up",
                tint = Color.White
            )
        }

        // Label in the middle
        Text(
            text = label,
            color = Color.LightGray,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )

        // Bottom Button (Minus / Channel Down)
        IconButton(
            onClick = { /* TODO: Send Volume Down / CH Down */ },
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = downIcon,
                contentDescription = "$label Down",
                tint = Color.White
            )
        }
    }
}

@Composable
fun RemoteRoundButton(icon: ImageVector, label: String) {
    IconButton(
        onClick = { /* Action */ },
        modifier = Modifier
            .size(56.dp)
            .background(TVSurfaceGrey, CircleShape)
    ) {
        Icon(icon, contentDescription = label, tint = Color.White)
    }
}

@Preview(showBackground = true, device = "id:pixel_7")
@Composable
fun RemotePreview() {
    StandardRemoteScreen()
}