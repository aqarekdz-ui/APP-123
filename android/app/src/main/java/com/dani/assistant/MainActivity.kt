package com.dani.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.dani.assistant.core.designsystem.DaniTheme
import com.dani.assistant.presentation.main.MainScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            // Requirement: Wrap content with RTL LayoutDirection and DaniTheme
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                DaniTheme {
                    MainScreen()
                }
            }
        }
    }
}
