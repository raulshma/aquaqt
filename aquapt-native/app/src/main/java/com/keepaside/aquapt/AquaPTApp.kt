package com.keepaside.aquapt

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.Assistant
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.keepaside.aquapt.core.model.EntityKind
import com.keepaside.aquapt.feature.assistant.AssistantScreen
import com.keepaside.aquapt.feature.entity.EntityDetailScreen
import com.keepaside.aquapt.feature.entity.EntityEditKind
import com.keepaside.aquapt.feature.entity.EntityEditScreen
import com.keepaside.aquapt.feature.entity.EntityFormScreen
import com.keepaside.aquapt.feature.insights.GlobalInsightsScreen
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
    const val Entity = "entity"
    const val EntityForm = "entity-form"
    const val EntityEdit = "entity-edit"

    private const val EntityKindArg = "kind"
    private const val EntityIdArg = "id"
    private const val EntityAquariumIdArg = "aquariumId"
    private const val EntityTargetIdArg = "targetId"
    private const val EntityEditKindArg = "editKind"
    private const val EntityEditIdArg = "editId"
    private const val MissingAquariumIdToken = "_"

    const val EntityDetailPattern = "$Entity/{$EntityKindArg}/{$EntityIdArg}/{$EntityAquariumIdArg}"
    const val EntityFormPattern =
        "$EntityForm/{$EntityKindArg}/{$EntityAquariumIdArg}?$EntityTargetIdArg={$EntityTargetIdArg}"
    const val EntityEditPattern = "$EntityEdit/{$EntityEditKindArg}/{$EntityEditIdArg}"

    fun entityDetailRoute(kind: EntityKind, id: String, aquariumId: String?): String {
        val encodedKind = Uri.encode(kind.name)
        val encodedId = Uri.encode(id)
        val encodedAquariumId = Uri.encode(aquariumId ?: MissingAquariumIdToken)
        return "$Entity/$encodedKind/$encodedId/$encodedAquariumId"
    }

    fun entityFormRoute(
        kind: EntityKind,
        aquariumId: String?,
        targetEntityId: String? = null
    ): String {
        val encodedKind = Uri.encode(kind.name)
        val encodedAquariumId = Uri.encode(aquariumId ?: MissingAquariumIdToken)
        val baseRoute = "$EntityForm/$encodedKind/$encodedAquariumId"
        val encodedTargetId = targetEntityId?.trim()?.takeIf { it.isNotEmpty() }?.let(Uri::encode)
        return if (encodedTargetId == null) {
            baseRoute
        } else {
            "$baseRoute?$EntityTargetIdArg=$encodedTargetId"
        }
    }

    fun entityEditRoute(kind: EntityEditKind, id: String): String {
        val encodedKind = Uri.encode(kind.routeToken)
        val encodedId = Uri.encode(id)
        return "$EntityEdit/$encodedKind/$encodedId"
    }

    fun parseEntityKind(value: String?): EntityKind? =
        runCatching { value?.let { EntityKind.valueOf(Uri.decode(it)) } }.getOrNull()

    fun parseEntityId(value: String?): String = value?.let(Uri::decode).orEmpty()

    fun parseEntityAquariumId(value: String?): String? {
        val decoded = value?.let(Uri::decode)
        return decoded?.takeUnless { it == MissingAquariumIdToken }
    }

    fun parseEntityTargetId(value: String?): String? =
        value?.let(Uri::decode)?.takeIf { it.isNotBlank() }

    fun parseEntityEditKind(value: String?): EntityEditKind? =
        EntityEditKind.fromRouteToken(value?.let(Uri::decode))

    fun parseEntityEditId(value: String?): String = value?.let(Uri::decode).orEmpty()
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
                        text = topBarTitleForRoute(currentRoute),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                actions = {
                    if (currentRoute != AquaPTRoute.Insights) {
                        IconButton(
                            onClick = {
                                navController.navigate(AquaPTRoute.Insights) {
                                    launchSingleTop = true
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Analytics,
                                contentDescription = "Open global insights"
                            )
                        }
                    }
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
                TimelineScreen(
                    onOpenEntityDeepLink = { kind, entityId, aquariumId ->
                        navController.navigate(
                            AquaPTRoute.entityDetailRoute(
                                kind = kind,
                                id = entityId,
                                aquariumId = aquariumId
                            )
                        ) {
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(AquaPTRoute.Assistant) {
                AssistantScreen()
            }
            composable(AquaPTRoute.Settings) {
                SettingsBackupScreen()
            }
            composable(AquaPTRoute.Livestock) {
                LivestockScreen(
                    onOpenEntityDeepLink = { kind, entityId, aquariumId ->
                        navController.navigate(
                            AquaPTRoute.entityDetailRoute(
                                kind = kind,
                                id = entityId,
                                aquariumId = aquariumId
                            )
                        ) {
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(AquaPTRoute.Insights) {
                GlobalInsightsScreen(
                    onBackToDashboard = {
                        navController.navigate(AquaPTRoute.Tanks) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable(AquaPTRoute.EntityDetailPattern) { backStackEntry ->
                val kind = AquaPTRoute.parseEntityKind(backStackEntry.arguments?.getString("kind"))
                val entityId = AquaPTRoute.parseEntityId(backStackEntry.arguments?.getString("id"))
                val aquariumId = AquaPTRoute.parseEntityAquariumId(backStackEntry.arguments?.getString("aquariumId"))

                EntityDetailScreen(
                    kind = kind,
                    entityId = entityId,
                    aquariumId = aquariumId,
                    onOpenEntityForm = { formKind, formAquariumId, targetEntityId ->
                        navController.navigate(
                            AquaPTRoute.entityFormRoute(
                                kind = formKind,
                                aquariumId = formAquariumId,
                                targetEntityId = targetEntityId
                            )
                        ) {
                            launchSingleTop = true
                        }
                    },
                    onOpenEntityEdit = { editKind, editId ->
                        navController.navigate(
                            AquaPTRoute.entityEditRoute(
                                kind = editKind,
                                id = editId
                            )
                        ) {
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(AquaPTRoute.EntityFormPattern) { backStackEntry ->
                val kind = AquaPTRoute.parseEntityKind(backStackEntry.arguments?.getString("kind"))
                val aquariumId = AquaPTRoute.parseEntityAquariumId(backStackEntry.arguments?.getString("aquariumId"))
                val targetEntityId = AquaPTRoute.parseEntityTargetId(backStackEntry.arguments?.getString("targetId"))

                EntityFormScreen(
                    kind = kind,
                    aquariumId = aquariumId,
                    targetEntityId = targetEntityId,
                    onDone = {
                        navController.popBackStack()
                    }
                )
            }
            composable(AquaPTRoute.EntityEditPattern) { backStackEntry ->
                val kind = AquaPTRoute.parseEntityEditKind(backStackEntry.arguments?.getString("editKind"))
                val entityId = AquaPTRoute.parseEntityEditId(backStackEntry.arguments?.getString("editId"))

                EntityEditScreen(
                    kind = kind,
                    entityId = entityId,
                    onDone = {
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}

private fun topBarTitleForRoute(route: String?): String = when {
    route == AquaPTRoute.Tanks -> "Tanks"
    route == AquaPTRoute.Tasks -> "Tasks"
    route == AquaPTRoute.Timeline -> "Timeline"
    route == AquaPTRoute.Assistant -> "Assistant"
    route == AquaPTRoute.Settings -> "Settings"
    route == AquaPTRoute.Livestock -> "Livestock"
    route == AquaPTRoute.Insights -> "Global insights"
    route?.startsWith(AquaPTRoute.EntityForm) == true -> "New activity"
    route?.startsWith(AquaPTRoute.EntityEdit) == true -> "Edit activity"
    route?.startsWith(AquaPTRoute.Entity) == true -> "Entity details"
    else -> "AquaPT"
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
