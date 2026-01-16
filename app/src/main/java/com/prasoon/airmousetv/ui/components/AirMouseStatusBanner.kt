package com.prasoon.airmousetv.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun AirMouseStatusBanner(
    isActive: Boolean = true,
    connection: String = "Connected • Stable"
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) Color(0xFF4CAF50) else Color.Gray
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Mouse,
                "Air Mouse",
                modifier = Modifier.size(40.dp),
                tint = Color.White
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    "Air Mouse",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White
                )
                Text(
                    connection,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
            Spacer(Modifier.weight(1f))
            Switch(
                checked = isActive,
                onCheckedChange = { }
            )
        }
    }
}