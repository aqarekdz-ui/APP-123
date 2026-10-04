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
import com.dani.assistant.presentation.habits.HabitsScreen
import com.dani.assistant.presentation.money.MoneyScreen
import com.dani.assistant.presentation.realestate.RealEstateScreen
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
            DashboardScreen(viewModel = viewModel, onOpenSettings = { navController.navigate("settings") }, onOpenRealEstate = { navController.navigate("realestate") }, onOpenHabits = { navController.navigate("habits") }, onOpenMoney = { navController.navigate("money") }, onOpenGoals = { navController.navigate("goals") }, onOpenFocus = { navController.navigate("focus") }, onOpenMeds = { navController.navigate("meds") }, onOpenEvents = { navController.navigate("events") }, onOpenNotes = { navController.navigate("notes") }, onOpenSearch = { navController.navigate("search") }, onOpenReceipt = { navController.navigate("receipt") })
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
        composable("money") {
            MoneyScreen(onBack = { navController.popBackStack() })
        }
        composable("receipt") {
            com.dani.assistant.presentation.money.ReceiptScreen(onBack = { navController.popBackStack() })
        }
        composable("search") {
            com.dani.assistant.presentation.search.SearchScreen(onBack = { navController.popBackStack() }, onOpen = { r -> navController.navigate(r) })
        }
        composable("notes") {
            com.dani.assistant.presentation.notes.NotesScreen(onBack = { navController.popBackStack() })
        }
        composable("events") {
            com.dani.assistant.presentation.events.EventsScreen(onBack = { navController.popBackStack() })
        }
        composable("meds") {
            com.dani.assistant.presentation.meds.MedsScreen(onBack = { navController.popBackStack() })
        }
        composable("focus") {
            com.dani.assistant.presentation.focus.FocusScreen(onBack = { navController.popBackStack() })
        }
        composable("goals") {
            com.dani.assistant.presentation.goals.GoalsScreen(onBack = { navController.popBackStack() })
        }
        composable("habits") {
            HabitsScreen(onBack = { navController.popBackStack() })
        }
        composable("realestate") {
            RealEstateScreen(onBack = { navController.popBackStack() })
        }
        composable("settings") {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
