package com.keepaside.aquapt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.keepaside.aquapt.core.repository.AppSettingsStore
import com.keepaside.aquapt.ui.theme.AquaPTTheme
import org.koin.java.KoinJavaComponent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val appSettingsStore: AppSettingsStore = remember {
                KoinJavaComponent.get(AppSettingsStore::class.java)
            }
            val appSettings by appSettingsStore.settings.collectAsState()

            AquaPTTheme(themePreference = appSettings.themePreference) {
                AquaPTApp()
            }
        }
    }
}
