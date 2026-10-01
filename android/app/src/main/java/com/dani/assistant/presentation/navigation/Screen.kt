package com.dani.assistant.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    object Dashboard : Screen(
        route = "dashboard",
        title = "الرئيسية",
        icon = Icons.Default.Dashboard
    )

    object Tasks : Screen(
        route = "tasks",
        title = "المهام",
        icon = Icons.Default.CheckCircle
    )

    object Calendar : Screen(
        route = "calendar",
        title = "التقويم",
        icon = Icons.Default.CalendarMonth
    )

    object Memory : Screen(
        route = "memory",
        title = "الذاكرة",
        icon = Icons.Default.Psychology
    )

    object Dani : Screen(
        route = "dani",
        title = "DANI",
        icon = Icons.Default.SmartToy
    )

    companion object {
        val bottomNavItems = listOf(
            Dashboard,
            Tasks,
            Calendar,
            Memory,
            Dani
        )
    }
}
