package com.keepaside.aquapt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.Assistant
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.keepaside.aquapt.feature.settings.SettingsBackupScreen
import com.keepaside.aquapt.feature.livestock.LivestockScreen
import com.keepaside.aquapt.feature.tasks.TasksDashboardScreen
import com.keepaside.aquapt.feature.tanks.TanksDashboardScreen
import com.keepaside.aquapt.feature.timeline.TimelineScreen

private data class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: @Composable () -> Unit
)

private object AquaPTRoute {
    const val Tanks = "tanks"
    const val Tasks = "tasks"
    const val Timeline = "timeline"
    const val Assistant = "assistant"
    const val Settings = "settings"

    const val Livestock = "livestock"
    const val Insights = "insights"
}

private val topLevelDestinations = listOf(
    TopLevelDestination(AquaPTRoute.Tanks, "Tanks") {
        Icon(Icons.Rounded.Home, contentDescription = null)
    },
    TopLevelDestination(AquaPTRoute.Tasks, "Tasks") {
        Icon(Icons.Rounded.TaskAlt, contentDescription = null)
    },
    TopLevelDestination(AquaPTRoute.Timeline, "Timeline") {
        Icon(Icons.Rounded.Timeline, contentDescription = null)
    },
    TopLevelDestination(AquaPTRoute.Assistant, "Assistant") {
        Icon(Icons.Rounded.Assistant, contentDescription = null)
    },
    TopLevelDestination(AquaPTRoute.Settings, "Settings") {
        Icon(Icons.Outlined.Settings, contentDescription = null)
    }
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AquaPTApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = currentRoute?.replaceFirstChar { it.uppercaseChar() } ?: "AquaPT",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            )
        },
        bottomBar = {
            NavigationBar {
                topLevelDestinations.forEach { destination ->
                    NavigationBarItem(
                        selected = currentDestination.isOnRoute(destination.route),
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        label = { Text(destination.label) },
                        icon = destination.icon
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AquaPTRoute.Tanks,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(AquaPTRoute.Tanks) {
                TanksDashboardScreen()
            }
            composable(AquaPTRoute.Tasks) {
                TasksDashboardScreen()
            }
            composable(AquaPTRoute.Timeline) {
                TimelineScreen()
            }
            composable(AquaPTRoute.Assistant) {
                PlaceholderScreen(
                    title = "Assistant",
                    subtitle = "Streaming chat, action review, and memory controls are planned for Phase 3.",
                    icon = { Icon(Icons.Outlined.Psychology, contentDescription = null) }
                )
            }
            composable(AquaPTRoute.Settings) {
                SettingsBackupScreen()
            }
            composable(AquaPTRoute.Livestock) {
                LivestockScreen()
            }
            composable(AquaPTRoute.Insights) {
                PlaceholderScreen(
                    title = "Global insights",
                    subtitle = "Portfolio-level indicators and recommendations are staged for a modal flow.",
                    icon = { Icon(Icons.Rounded.Analytics, contentDescription = null) }
                )
            }
        }
    }
}

private fun NavDestination?.isOnRoute(route: String): Boolean {
    return this?.hierarchy?.any { it.route == route } == true
}

@Composable
private fun PlaceholderScreen(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(24.dp)
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            icon()
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
