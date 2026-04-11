package com.keepaside.aquapt

import androidx.compose.foundation.background
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
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.keepaside.aquapt.core.model.EntityKind
import com.keepaside.aquapt.feature.assistant.AssistantScreen
import com.keepaside.aquapt.feature.entity.EntityDetailScreen
import com.keepaside.aquapt.feature.entity.EntityEditKind
import com.keepaside.aquapt.feature.entity.EntityEditScreen
import com.keepaside.aquapt.feature.entity.EntityFormScreen
import com.keepaside.aquapt.feature.insights.GlobalInsightsScreen
import com.keepaside.aquapt.feature.settings.SettingsBackupScreen
import com.keepaside.aquapt.feature.settings.ModelBrowserTarget
import com.keepaside.aquapt.feature.settings.SettingsMemoryScreen
import com.keepaside.aquapt.feature.settings.SettingsModelBrowserScreen
import com.keepaside.aquapt.feature.settings.SettingsScreen
import com.keepaside.aquapt.feature.settings.SettingsWorkflowScreen
import com.keepaside.aquapt.feature.livestock.LivestockScreen
import com.keepaside.aquapt.feature.tasks.TasksDashboardScreen
import com.keepaside.aquapt.feature.tanks.TanksDashboardScreen
import com.keepaside.aquapt.feature.timeline.TimelineScreen
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

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
    const val SettingsCore = "settings-core"

    const val Livestock = "livestock"
    const val Insights = "insights"
    const val Workflows = "workflows"
    const val MemoryTools = "settings-memory"
    const val ModelBrowser = "model-browser"
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

    private const val ModelBrowserTargetArg = "target"
    private const val ModelBrowserSelectedIdArg = "selectedId"

    const val ModelBrowserPattern = "$ModelBrowser/{$ModelBrowserTargetArg}?$ModelBrowserSelectedIdArg={$ModelBrowserSelectedIdArg}"

    fun modelBrowserRoute(target: ModelBrowserTarget, selectedModelId: String? = null): String {
        val encodedTarget = encodeForRoute(target.name)
        val base = "$ModelBrowser/$encodedTarget"
        val encodedId = selectedModelId?.trim()?.takeIf { it.isNotEmpty() }?.let(::encodeForRoute)
        return if (encodedId == null) base else "$base?$ModelBrowserSelectedIdArg=$encodedId"
    }

    fun parseModelBrowserTarget(value: String?): ModelBrowserTarget? =
        runCatching { value?.let { ModelBrowserTarget.valueOf(decodeForRoute(it)) } }.getOrNull()

    fun parseModelBrowserSelectedId(value: String?): String? =
        value?.let(::decodeForRoute)?.takeIf { it.isNotBlank() }

    const val EntityDetailPattern = "$Entity/{$EntityKindArg}/{$EntityIdArg}/{$EntityAquariumIdArg}"
    const val EntityFormPattern =
        "$EntityForm/{$EntityKindArg}/{$EntityAquariumIdArg}?$EntityTargetIdArg={$EntityTargetIdArg}"
    const val EntityEditPattern = "$EntityEdit/{$EntityEditKindArg}/{$EntityEditIdArg}"

    fun entityDetailRoute(kind: EntityKind, id: String, aquariumId: String?): String {
        val encodedKind = encodeForRoute(kind.name)
        val encodedId = encodeForRoute(id)
        val encodedAquariumId = encodeForRoute(aquariumId ?: MissingAquariumIdToken)
        return "$Entity/$encodedKind/$encodedId/$encodedAquariumId"
    }

    fun entityFormRoute(
        kind: EntityKind,
        aquariumId: String?,
        targetEntityId: String? = null
    ): String {
        val encodedKind = encodeForRoute(kind.name)
        val encodedAquariumId = encodeForRoute(aquariumId ?: MissingAquariumIdToken)
        val baseRoute = "$EntityForm/$encodedKind/$encodedAquariumId"
        val encodedTargetId = targetEntityId?.trim()?.takeIf { it.isNotEmpty() }?.let(::encodeForRoute)
        return if (encodedTargetId == null) {
            baseRoute
        } else {
            "$baseRoute?$EntityTargetIdArg=$encodedTargetId"
        }
    }

    fun entityEditRoute(kind: EntityEditKind, id: String): String {
        val encodedKind = encodeForRoute(kind.routeToken)
        val encodedId = encodeForRoute(id)
        return "$EntityEdit/$encodedKind/$encodedId"
    }

    fun parseEntityKind(value: String?): EntityKind? =
        runCatching { value?.let { EntityKind.valueOf(decodeForRoute(it)) } }.getOrNull()

    fun parseEntityId(value: String?): String = value?.let(::decodeForRoute).orEmpty()

    fun parseEntityAquariumId(value: String?): String? {
        val decoded = value?.let(::decodeForRoute)
        return decoded?.takeUnless { it == MissingAquariumIdToken }
    }

    fun parseEntityTargetId(value: String?): String? =
        value?.let(::decodeForRoute)?.takeIf { it.isNotBlank() }

    fun parseEntityEditKind(value: String?): EntityEditKind? =
        EntityEditKind.fromRouteToken(value?.let(::decodeForRoute))

    fun parseEntityEditId(value: String?): String = value?.let(::decodeForRoute).orEmpty()
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
fun AquaPTApp(
    externalRoute: String? = null,
    onExternalRouteConsumed: (String) -> Unit = {}
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route

    LaunchedEffect(externalRoute) {
        val pendingRoute = externalRoute
            ?.trim()
            ?.takeIf { route -> route.isNotEmpty() }
            ?: return@LaunchedEffect

        val targetRoute = mapExternalRouteToNativeRoute(pendingRoute)
            ?: run {
                onExternalRouteConsumed(pendingRoute)
                return@LaunchedEffect
            }

        navController.navigate(targetRoute) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }

        onExternalRouteConsumed(pendingRoute)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.26f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            topBar = {
                CenterAlignedTopAppBar(
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    title = {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = topBarTitleForRoute(currentRoute),
                                style = MaterialTheme.typography.titleLarge
                            )
                            topBarSubtitleForRoute(currentRoute)?.let { subtitle ->
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
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
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
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
                            icon = destination.icon,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                indicatorColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
                SettingsScreen(
                    onOpenCoreSettings = {
                        navController.navigate(AquaPTRoute.SettingsCore) {
                            launchSingleTop = true
                        }
                    },
                    onOpenWorkflows = {
                        navController.navigate(AquaPTRoute.Workflows) {
                            launchSingleTop = true
                        }
                    },
                    onOpenMemoryTools = {
                        navController.navigate(AquaPTRoute.MemoryTools) {
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(AquaPTRoute.SettingsCore) {
                val settingsEntry = navController.currentBackStackEntry
                val selectedAssistantModelId by settingsEntry
                    ?.savedStateHandle
                    ?.getStateFlow<String?>("selectedAssistantModelId", null)
                    ?.collectAsState()
                    ?: remember { mutableStateOf(null) }
                val selectedMemoryModelId by settingsEntry
                    ?.savedStateHandle
                    ?.getStateFlow<String?>("selectedMemoryModelId", null)
                    ?.collectAsState()
                    ?: remember { mutableStateOf(null) }

                LaunchedEffect(selectedAssistantModelId) {
                    selectedAssistantModelId?.let { id ->
                        settingsEntry?.savedStateHandle?.remove<String>("selectedAssistantModelId")
                    }
                }
                LaunchedEffect(selectedMemoryModelId) {
                    selectedMemoryModelId?.let { id ->
                        settingsEntry?.savedStateHandle?.remove<String>("selectedMemoryModelId")
                    }
                }

                SettingsBackupScreen(
                    onOpenWorkflows = {
                        navController.navigate(AquaPTRoute.Workflows) {
                            launchSingleTop = true
                        }
                    },
                    onOpenMemoryTools = {
                        navController.navigate(AquaPTRoute.MemoryTools) {
                            launchSingleTop = true
                        }
                    },
                    onOpenModelBrowser = { target, currentModelId ->
                        navController.navigate(
                            AquaPTRoute.modelBrowserRoute(
                                target = target,
                                selectedModelId = currentModelId
                            )
                        ) {
                            launchSingleTop = true
                        }
                    },
                    selectedAssistantModelId = selectedAssistantModelId,
                    selectedMemoryModelId = selectedMemoryModelId
                )
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
            composable(AquaPTRoute.Workflows) {
                SettingsWorkflowScreen(
                    onBack = {
                        navController.popBackStack()
                    }
                )
            }
            composable(AquaPTRoute.MemoryTools) {
                SettingsMemoryScreen(
                    onBack = {
                        navController.popBackStack()
                    }
                )
            }
            composable(AquaPTRoute.ModelBrowserPattern) { backStackEntry ->
                val target = AquaPTRoute.parseModelBrowserTarget(
                    backStackEntry.arguments?.getString("target")
                ) ?: ModelBrowserTarget.ASSISTANT
                val selectedId = AquaPTRoute.parseModelBrowserSelectedId(
                    backStackEntry.arguments?.getString("selectedId")
                )

                SettingsModelBrowserScreen(
                    initialTarget = target,
                    initialModelId = selectedId,
                    onModelSelected = { selectedTarget, modelId ->
                        when (selectedTarget) {
                            ModelBrowserTarget.ASSISTANT -> {
                                navController.previousBackStackEntry?.savedStateHandle?.set(
                                    "selectedAssistantModelId", modelId
                                )
                            }
                            ModelBrowserTarget.MEMORY -> {
                                navController.previousBackStackEntry?.savedStateHandle?.set(
                                    "selectedMemoryModelId", modelId
                                )
                            }
                        }
                        navController.popBackStack()
                    },
                    onBack = {
                        navController.popBackStack()
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
                    onOpenEntityDeepLink = { linkedKind, linkedEntityId, linkedAquariumId ->
                        navController.navigate(
                            AquaPTRoute.entityDetailRoute(
                                kind = linkedKind,
                                id = linkedEntityId,
                                aquariumId = linkedAquariumId
                            )
                        ) {
                            launchSingleTop = true
                        }
                    },
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
}

private fun topBarTitleForRoute(route: String?): String = when {
    route == AquaPTRoute.Tanks -> "Tanks"
    route == AquaPTRoute.Tasks -> "Tasks"
    route == AquaPTRoute.Timeline -> "Timeline"
    route == AquaPTRoute.Assistant -> "Assistant"
    route == AquaPTRoute.Settings -> "Settings"
    route == AquaPTRoute.SettingsCore -> "Core settings"
    route == AquaPTRoute.Livestock -> "Livestock"
    route == AquaPTRoute.Insights -> "Global insights"
    route == AquaPTRoute.Workflows -> "AI workflows"
    route == AquaPTRoute.MemoryTools -> "Assistant memory"
    route?.startsWith(AquaPTRoute.ModelBrowser) == true -> "Browse models"
    route?.startsWith(AquaPTRoute.EntityForm) == true -> "New activity"
    route?.startsWith(AquaPTRoute.EntityEdit) == true -> "Edit activity"
    route?.startsWith(AquaPTRoute.Entity) == true -> "Entity details"
    else -> "AquaPT"
}

private fun topBarSubtitleForRoute(route: String?): String? = when {
    route == AquaPTRoute.Tanks -> "Live tank health at a glance"
    route == AquaPTRoute.Tasks -> "Recurring care, done on time"
    route == AquaPTRoute.Timeline -> "Every event, one clear stream"
    route == AquaPTRoute.Assistant -> "Context-aware aquarium copilot"
    route == AquaPTRoute.Settings -> "Grouped controls for faster navigation"
    route == AquaPTRoute.SettingsCore -> "Preferences, reminders, and backup"
    route == AquaPTRoute.Insights -> "Cross-tank performance insights"
    route?.startsWith(AquaPTRoute.Entity) == true -> "Detailed activity context"
    else -> null
}

internal fun mapExternalRouteToNativeRoute(route: String): String? {
    val normalizedInput = route
        .trim()
        .takeIf { value -> value.isNotEmpty() }
        ?.let(::decodeForRoute)
        ?.removePrefix("aquapt://")
        ?.removePrefix("aquapt:")
        ?.trim()
        ?.trimStart('/')
        ?: return null

    val parsedExternalRoute = parseExternalRoute(normalizedInput)
    val segments = parsedExternalRoute.segments
    val queryParams = parsedExternalRoute.queryParameters

    if (segments.isEmpty()) {
        return AquaPTRoute.Tanks
    }

    val first = segments.getOrNull(0)?.trim()?.lowercase().orEmpty()
    val second = segments.getOrNull(1)?.trim()?.lowercase().orEmpty()

    return when (first) {
        "(tabs)" -> when (second) {
            "tanks", "index", "" -> AquaPTRoute.Tanks
            "tasks" -> AquaPTRoute.Tasks
            "timeline" -> AquaPTRoute.Timeline
            "assistant" -> AquaPTRoute.Assistant
            "settings" -> AquaPTRoute.Settings
            "livestock" -> AquaPTRoute.Livestock
            else -> null
        }

        "", "tanks", "index" -> AquaPTRoute.Tanks
        "tasks" -> AquaPTRoute.Tasks
        "timeline" -> AquaPTRoute.Timeline
        "assistant" -> AquaPTRoute.Assistant
        "livestock" -> AquaPTRoute.Livestock
        "modal", "insights" -> AquaPTRoute.Insights
        "workflows" -> AquaPTRoute.Workflows
        "memory" -> AquaPTRoute.MemoryTools

        "settings" -> when (second) {
            "", "index" -> AquaPTRoute.Settings
            "assistant", "backup", "reminders", "reminder-groups", "core" -> AquaPTRoute.SettingsCore
            "workflows" -> AquaPTRoute.Workflows
            "memory" -> AquaPTRoute.MemoryTools
            "models" -> {
                val target = parseExternalModelBrowserTarget(
                    pathTarget = segments.getOrNull(2),
                    queryTarget = queryParams["target"]
                ) ?: ModelBrowserTarget.ASSISTANT
                AquaPTRoute.modelBrowserRoute(
                    target = target,
                    selectedModelId = queryParams["selectedId"]?.trim()?.takeIf { it.isNotEmpty() }
                )
            }
            else -> AquaPTRoute.Settings
        }

        "models", "model-browser" -> {
            val target = parseExternalModelBrowserTarget(
                pathTarget = segments.getOrNull(1),
                queryTarget = queryParams["target"]
            ) ?: ModelBrowserTarget.ASSISTANT
            AquaPTRoute.modelBrowserRoute(
                target = target,
                selectedModelId = queryParams["selectedId"]?.trim()?.takeIf { it.isNotEmpty() }
            )
        }

        "entity" -> {
            val kind = parseExternalEntityKindToken(segments.getOrNull(1)) ?: return null
            val entityId = segments.getOrNull(2)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val aquariumId = segments.getOrNull(3)
                ?.trim()
                ?.takeIf { value -> value.isNotEmpty() }
                ?: queryParams["aquariumId"]
                    ?.trim()
                    ?.takeIf { value -> value.isNotEmpty() }

            AquaPTRoute.entityDetailRoute(
                kind = kind,
                id = entityId,
                aquariumId = aquariumId
            )
        }

        "entity-form" -> {
            val kindToken = segments.getOrNull(1)?.trim()?.lowercase().orEmpty()
            val aquariumId = segments.getOrNull(2)
                ?.trim()
                ?.takeIf { value -> value.isNotEmpty() }
                ?: queryParams["aquariumId"]
                    ?.trim()
                    ?.takeIf { value -> value.isNotEmpty() }

            val targetEntityId = queryParams["targetId"]
                ?.trim()
                ?.takeIf { value -> value.isNotEmpty() }
                ?: queryParams["id"]
                    ?.trim()
                    ?.takeIf { value -> value.isNotEmpty() }

            when (kindToken) {
                "issue" -> AquaPTRoute.entityFormRoute(EntityKind.ISSUE, aquariumId, targetEntityId)
                "memo" -> AquaPTRoute.entityFormRoute(EntityKind.MEMO, aquariumId, targetEntityId)
                "dosing" -> AquaPTRoute.entityFormRoute(EntityKind.DOSING, aquariumId, targetEntityId)
                "parameter-log", "parameter_log", "parameterlog", "parameter" ->
                    AquaPTRoute.entityFormRoute(EntityKind.PARAMETER_LOG, aquariumId, targetEntityId)
                "consumable" -> AquaPTRoute.entityFormRoute(EntityKind.CONSUMABLE, aquariumId, targetEntityId)
                "task-execution" -> AquaPTRoute.Tasks
                "livestock" -> AquaPTRoute.Livestock
                else -> null
            }
        }

        "entity-edit" -> {
            val kind = EntityEditKind.fromRouteToken(segments.getOrNull(1)) ?: return null
            val entityId = segments.getOrNull(2)
                ?.trim()
                ?.takeIf { value -> value.isNotEmpty() }
                ?: queryParams["id"]
                    ?.trim()
                    ?.takeIf { value -> value.isNotEmpty() }
                ?: return null

            AquaPTRoute.entityEditRoute(kind = kind, id = entityId)
        }

        else -> null
    }
}

private fun parseExternalModelBrowserTarget(
    pathTarget: String?,
    queryTarget: String?
): ModelBrowserTarget? {
    val token = pathTarget
        ?.trim()
        ?.takeIf { value -> value.isNotEmpty() }
        ?: queryTarget
            ?.trim()
            ?.takeIf { value -> value.isNotEmpty() }
        ?: return null

    return when (token.lowercase()) {
        "assistant" -> ModelBrowserTarget.ASSISTANT
        "memory" -> ModelBrowserTarget.MEMORY
        else -> runCatching { ModelBrowserTarget.valueOf(token.uppercase()) }.getOrNull()
    }
}

private fun parseExternalEntityKindToken(value: String?): EntityKind? {
    val token = value?.trim()?.lowercase().orEmpty()
    return when (token) {
        "aquarium" -> EntityKind.AQUARIUM
        "task" -> EntityKind.TASK
        "livestock" -> EntityKind.LIVESTOCK
        "asset" -> EntityKind.ASSET
        "consumable" -> EntityKind.CONSUMABLE
        "issue" -> EntityKind.ISSUE
        "memo" -> EntityKind.MEMO
        "dosing" -> EntityKind.DOSING
        "parameter-log", "parameter_log", "parameterlog", "parameter" -> EntityKind.PARAMETER_LOG
        else -> runCatching {
            value
                ?.trim()
                ?.takeIf { raw -> raw.isNotEmpty() }
                ?.uppercase()
                ?.let(EntityKind::valueOf)
        }.getOrNull()
    }
}

private data class ParsedExternalRoute(
    val segments: List<String>,
    val queryParameters: Map<String, String>
)

private fun parseExternalRoute(route: String): ParsedExternalRoute {
    val parts = route.split('?', limit = 2)
    val pathPart = parts[0]
    val queryPart = parts.getOrNull(1)

    val segments = pathPart
        .split('/')
        .map { segment -> segment.trim() }
        .filter { segment -> segment.isNotEmpty() }
        .map(::decodeForRoute)

    val queryParameters = parseRouteQueryParameters(queryPart)
    return ParsedExternalRoute(segments = segments, queryParameters = queryParameters)
}

private fun parseRouteQueryParameters(queryPart: String?): Map<String, String> {
    if (queryPart.isNullOrBlank()) {
        return emptyMap()
    }

    return queryPart
        .split('&')
        .mapNotNull { rawPair ->
            val trimmedPair = rawPair.trim()
            if (trimmedPair.isEmpty()) {
                return@mapNotNull null
            }

            val keyValue = trimmedPair.split('=', limit = 2)
            val key = keyValue[0].trim().takeIf { value -> value.isNotEmpty() }?.let(::decodeForRoute)
                ?: return@mapNotNull null
            val value = keyValue.getOrNull(1)?.let(::decodeForRoute).orEmpty()
            key to value
        }
        .toMap()
}

private fun encodeForRoute(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")

private fun decodeForRoute(value: String): String =
    URLDecoder.decode(value, StandardCharsets.UTF_8)

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
