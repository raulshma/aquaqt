package com.keepaside.aquapt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.keepaside.aquapt.ui.theme.AquaPTTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AquaPTTheme {
                AquaPTApp()
            }
        }
    }
}
