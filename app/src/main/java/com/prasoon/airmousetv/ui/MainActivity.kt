package com.prasoon.airmousetv.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.prasoon.airmousetv.data.repository.DiscoveredTv
import com.prasoon.airmousetv.presentation.RemoteViewModel
import com.prasoon.airmousetv.ui.screens.DiscoveryScreen
import com.prasoon.airmousetv.ui.theme.AirMouseTVRemoteTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(
                android.graphics.Color.TRANSPARENT,
            ),
            navigationBarStyle = SystemBarStyle.dark(
                android.graphics.Color.TRANSPARENT,
            )
        )

        setContent {
            AirMouseTVRemoteTheme {
                val windowSizeClass = calculateWindowSizeClass(this)
                DiscoveryScreenContent(windowSizeClass)
            }
        }
    }
}

@Composable
private fun DiscoveryScreenContent(
    windowSizeClass: androidx.compose.material3.windowsizeclass.WindowSizeClass
) {
    val viewModel: RemoteViewModel = hiltViewModel()

    DiscoveryScreen(
        viewModel = viewModel,
        onTvSelected = { tv: DiscoveredTv ->
            // TODO: Navigate to pairing screen
            // navController.navigate("pairing/${tv.host}/${tv.port}")
        },
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
    )
}

//@Composable
//fun DashboardScreen() {
//    LazyVerticalGrid(columns = GridCells.Fixed(1)) {
//        item { AirMouseStatusBanner() }
//        item { SettingsRow() }
//        item { ConnectionInfoRow() }
//    }
//}
//@Composable
//fun PairingScreen(code: String, timeLeft: Int) {
//    Box(modifier = Modifier.Companion.fillMaxSize().background(Color.Companion.Black)) {
//        Column(horizontalAlignment = Alignment.Companion.CenterHorizontally) {
//            Text(
//                code.chunked(2).joinToString(" "),
//                fontSize = 100.sp,
//                color = Color.Companion.White,
//                modifier = Modifier.Companion.padding(top = 200.dp)
//            )
//            Text("Waiting for phone...", fontSize = 24.sp, color = Color.Companion.Gray)
//            // QR Code composable
//            Text("192.168.1.100:8080", color = Color.Companion.Cyan)
//            Text("$timeLeft seconds", color = Color.Companion.White)
//        }
//    }
//}
//
//@Composable
//fun AccessibilitySetupScreen(currentStep: Int) {
//    Column {
//        Text("Enable Air Mouse Control", style = MaterialTheme.typography.headlineMedium)
//        Text("Step $currentStep/3")
//        Button(onClick = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) {
//            Text("ENABLE ACCESSIBILITY")
//        }
//        if (currentStep == 3) {
//            Text("✅ Air Mouse Ready!", color = Color.Companion.Green)
//        }
//    }
//}
//
//@Composable
//fun MainNavHost() {
//    val navController = rememberNavController()
//    NavHost(
//        navController = navController,
//        startDestination = "discovery"
//    ) {
//        composable("discovery") { DiscoveryScreen(onConnect = { navController.navigate("remote") }) }
//        composable("remote") {
//            RemoteScreen(
//                onAirMouse = { navController.navigate("airmouse") },
//                onKeyboard = { navController.navigate("keyboard") }
//            )
//        }
//        composable("airmouse") { AirMouseScreen(onRemote = { navController.navigate("remote") }) }
//        composable("keyboard") {
//            KeyboardScreen(onDismiss = { navController.popBackStack() })
//        }
//        val viewModel = ViewModelProvider(this)[RemoteViewModel::class.java]
//        composable("settings") { SettingsScreen(viewModel = viewModel ) }
//    }
//
//    // Bottom Navigation
//    BottomNavigationBar(navController)
//}
//
//@Composable
//private fun BottomNavigationBar(navController: NavHostController) {
//    NavigationBar {
//        NavigationBarItem(
//            icon = { Icon(Icons.Default.Tv, "TV") },
//            label = { Text("Setup") },
//            selected = navController.currentDestination?.route == "discovery",
//            onClick = { navController.navigate("discovery") }
//        )
//        NavigationBarItem(
//            icon = { Icon(Icons.Filled.Videocam, "Remote") },
//            label = { Text("Remote") },
//            selected = navController.currentDestination?.route == "remote",
//            onClick = { navController.navigate("remote") }
//        )
//        NavigationBarItem(
//            icon = { Icon(Icons.Filled.Mouse, "Air Mouse") },
//            label = { Text("Air Mouse") },
//            selected = navController.currentDestination?.route == "airmouse",
//            onClick = { navController.navigate("airmouse") }
//        )
//        NavigationBarItem(
//            icon = { Icon(Icons.Default.Keyboard, "Keyboard") },
//            label = { Text("Keyboard") },
//            selected = navController.currentDestination?.route == "keyboard",
//            onClick = { navController.navigate("keyboard") }
//        )
//        NavigationBarItem(
//            icon = { Icon(Icons.Default.Settings, "Settings") },
//            label = { Text("Settings") },
//            selected = navController.currentDestination?.route == "settings",
//            onClick = { navController.navigate("settings") }
//        )
//    }
//}
//@Composable
//fun MainRemoteContainer() {
//    // This state controls which UI the user sees
//    var isAirMouseMode by remember { mutableStateOf(false) }
//
//    Scaffold(
//        topBar = {
//            // Mode Selector Header
//            Row(
//                modifier = Modifier.Companion
//                    .fillMaxWidth()
//                    .background(TVDarkGrey)
//                    .padding(horizontal = 24.dp, vertical = 16.dp),
//                horizontalArrangement = Arrangement.SpaceBetween,
//                verticalAlignment = Alignment.CenterVertically
//            ) {
//                Text(
//                    text = if (isAirMouseMode) "AIR MOUSE MODE" else "STANDARD REMOTE",
//                    color = Color.Companion.White,
//                    style = MaterialTheme.typography.labelLarge
//                )
//                Switch(
//                    checked = isAirMouseMode,
//                    onCheckedChange = { isAirMouseMode = it },
//                    colors = SwitchDefaults.colors(
//                        checkedThumbColor = TVAccentGreen,
//                        checkedTrackColor = TVAccentGreen.copy(alpha = 0.5f)
//                    )
//                )
//            }
//        },
//        containerColor = TVDarkGrey
//    ) { paddingValues ->
//        Box(modifier = Modifier.Companion.padding(paddingValues)) {
//            if (isAirMouseMode) {
//                // We'll build this next!
//                Text("Air Mouse UI Placeholder", color = Color.Companion.White)
//            } else {
//                StandardRemoteScreen()
//            }
//        }
//    }
//}
//
