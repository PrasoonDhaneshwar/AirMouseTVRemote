package com.prasoon.airmousetv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.prasoon.airmousetv.presentation.RemoteViewModel

//@Composable
//fun SettingsScreen(viewModel: RemoteViewModel) {
//    val isPaired by viewModel.isPaired.collectAsState()
//    val connection by viewModel.connectionStatus.collectAsState()
//
//    LazyColumn(
//        modifier = Modifier
//            .fillMaxSize()
//            .padding(16.dp),
//        verticalArrangement = Arrangement.spacedBy(12.dp)
//    ) {
//        item {
//            Card(modifier = Modifier.fillMaxWidth()) {
//                Column(modifier = Modifier.padding(20.dp)) {
//                    Text("Connection", style = MaterialTheme.typography.titleLarge)
//                    Text(connection, style = MaterialTheme.typography.bodyLarge)
//                    if (!isPaired) {
//                        Button(onClick = { viewModel.pairWithTv() }) {
//                            Text("PAIR WITH TV")
//                        }
//                    }
//                }
//            }
//        }
//
//        item {
//            Card(modifier = Modifier.fillMaxWidth()) {
//                Column(modifier = Modifier.padding(20.dp)) {
//                    Text("Install TV Companion", style = MaterialTheme.typography.titleLarge)
//                    Text("Required for air mouse overlay")
//                    Button(
//                        onClick = { /* Play Store deep link */ },
//                        modifier = Modifier.padding(top = 12.dp)
//                    ) {
//                        Text("GET TV APP")
//                    }
//                }
//            }
//        }
//
//        item {
//            Card(modifier = Modifier.fillMaxWidth()) {
//                Column(modifier = Modifier.padding(20.dp)) {
//                    Text("Sensitivity", style = MaterialTheme.typography.titleLarge)
//                    var sens by remember { mutableStateOf(75f) }
//                    Slider(
//                        value = sens,
//                        onValueChange = { sens = it },
//                        valueRange = 10f..200f
//                    )
//                }
//            }
//        }
//    }
//}