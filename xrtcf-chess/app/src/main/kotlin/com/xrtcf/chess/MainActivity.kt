package com.xrtcf.chess

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.xrtcf.chess.engine.StockfishEngine
import com.xrtcf.chess.screens.AnalysisScreen
import com.xrtcf.chess.screens.HomeScreen
import com.xrtcf.chess.screens.SettingsScreen
import com.xrtcf.chess.screens.TrainingScreen
import com.xrtcf.chess.ui.XrtcfChessTheme
import com.xrtcf.chess.ui.XrtcfColors

class MainActivity : ComponentActivity() {

    /** Shared engine process for the whole app lifecycle. */
    private var engine: StockfishEngine? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        engine = EngineProvider.obtain(applicationContext)

        setContent {
            XrtcfChessTheme {
                val navController = rememberNavController()
                val backStack by navController.currentBackStackEntryAsState()
                val currentRoute = backStack?.destination?.route ?: "home"

                val items = listOf(
                    NavItem("home", "Home", Icons.Filled.Home),
                    NavItem("analysis", "Analysis", Icons.Filled.TrendingUp),
                    NavItem("training", "Training", Icons.Filled.School),
                    NavItem("settings", "Settings", Icons.Filled.Settings),
                )

                Scaffold(
                    containerColor = XrtcfColors.Background,
                    bottomBar = {
                        NavigationBar(containerColor = XrtcfColors.Surface) {
                            items.forEach { item ->
                                NavigationBarItem(
                                    selected = currentRoute == item.route,
                                    onClick = {
                                        navController.navigate(item.route) {
                                            popUpTo("home") { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = { Icon(item.icon, contentDescription = item.label) },
                                    label = { Text(item.label) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = XrtcfColors.Primary,
                                        selectedTextColor = XrtcfColors.Primary,
                                        unselectedIconColor = XrtcfColors.OnSurfaceMuted,
                                        unselectedTextColor = XrtcfColors.OnSurfaceMuted,
                                        indicatorColor = XrtcfColors.Primary.copy(alpha = 0.15f),
                                    ),
                                )
                            }
                        }
                    },
                ) { padding ->
                    NavHost(
                        navController = navController,
                        startDestination = "home",
                        modifier = Modifier.padding(padding),
                    ) {
                        composable("home") {
                            HomeScreen(
                                engine = engine,
                                onStartAnalysis = { navController.navigate("analysis") },
                                onStartTraining = { navController.navigate("training") },
                            )
                        }
                        composable("analysis") {
                            AnalysisScreen(engine = engine)
                        }
                        composable("training") {
                            TrainingScreen(engine = engine)
                        }
                        composable("settings") {
                            SettingsScreen(engine = engine)
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            EngineProvider.release()
        }
    }
}

private data class NavItem(val route: String, val label: String, val icon: ImageVector)
