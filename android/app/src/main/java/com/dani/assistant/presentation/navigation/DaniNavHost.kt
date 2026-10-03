package com.dani.assistant.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.dani.assistant.DaniApplication
import com.dani.assistant.presentation.calendar.CalendarScreen
import com.dani.assistant.presentation.chat.ChatScreen
import com.dani.assistant.presentation.memory.MemoryScreen
import com.dani.assistant.presentation.dashboard.DashboardScreen
import com.dani.assistant.presentation.settings.SettingsScreen
import com.dani.assistant.presentation.tasks.TasksScreen
import com.dani.assistant.presentation.tasks.TasksViewModel

@Composable
fun DaniNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard.route,
        modifier = modifier
    ) {
        composable(Screen.Dashboard.route) {
            val app = DaniApplication.instance
            val viewModel: TasksViewModel = viewModel(
                factory = TasksViewModel.provideFactory(
                    taskRepository = app.taskRepository,
                    alarmScheduler = app.alarmScheduler
                )
            )
            DashboardScreen(viewModel = viewModel, onOpenSettings = { navController.navigate("settings") })
        }
        composable(Screen.Tasks.route) {
            val app = DaniApplication.instance
            val viewModel: TasksViewModel = viewModel(
                factory = TasksViewModel.provideFactory(
                    taskRepository = app.taskRepository,
                    alarmScheduler = app.alarmScheduler
                )
            )
            TasksScreen(viewModel = viewModel)
        }
        composable(Screen.Calendar.route) {
            val app = DaniApplication.instance
            val viewModel: TasksViewModel = viewModel(
                factory = TasksViewModel.provideFactory(
                    taskRepository = app.taskRepository,
                    alarmScheduler = app.alarmScheduler
                )
            )
            CalendarScreen(viewModel = viewModel)
        }
        composable(Screen.Memory.route) {
            MemoryScreen()
        }
        composable(Screen.Dani.route) {
            ChatScreen()
        }
        composable("settings") {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
