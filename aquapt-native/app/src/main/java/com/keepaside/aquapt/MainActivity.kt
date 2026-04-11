package com.keepaside.aquapt

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.keepaside.aquapt.core.notifications.reminderNotificationRouteExtraKey
import com.keepaside.aquapt.core.repository.AppSettingsStore
import com.keepaside.aquapt.ui.theme.AquaPTTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.koin.java.KoinJavaComponent

class MainActivity : ComponentActivity() {

    private val pendingNotificationRoute = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        publishRouteFromIntent(intent)

        setContent {
            val appSettingsStore: AppSettingsStore = remember {
                KoinJavaComponent.get(AppSettingsStore::class.java)
            }
            val appSettings by appSettingsStore.settings.collectAsState()
            val externalRoute by pendingNotificationRoute.collectAsState()

            AquaPTTheme(themePreference = appSettings.themePreference) {
                AquaPTApp(
                    externalRoute = externalRoute,
                    onExternalRouteConsumed = { consumedRoute ->
                        if (pendingNotificationRoute.value == consumedRoute) {
                            pendingNotificationRoute.value = null
                        }
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        publishRouteFromIntent(intent)
    }

    private fun publishRouteFromIntent(intent: Intent?) {
        val route = intent
            ?.getStringExtra(reminderNotificationRouteExtraKey)
            ?.trim()
            ?.takeIf { value -> value.isNotEmpty() }
            ?: return

        pendingNotificationRoute.value = route
    }
}
