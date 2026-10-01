package com.dani.assistant.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.dani.assistant.DaniApplication
import com.dani.assistant.presentation.placeholder.PlaceholderScreen
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
            PlaceholderScreen(title = Screen.Dashboard.title)
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
            PlaceholderScreen(title = Screen.Calendar.title)
        }
        composable(Screen.Memory.route) {
            PlaceholderScreen(title = Screen.Memory.title)
        }
        composable(Screen.Dani.route) {
            PlaceholderScreen(title = Screen.Dani.title)
        }
    }
}
