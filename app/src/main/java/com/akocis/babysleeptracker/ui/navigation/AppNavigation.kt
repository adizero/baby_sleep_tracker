package com.akocis.babysleeptracker.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.akocis.babysleeptracker.repository.FileRepository
import com.akocis.babysleeptracker.repository.PreferencesRepository
import com.akocis.babysleeptracker.ui.screen.HistoryScreen
import com.akocis.babysleeptracker.ui.screen.HomeScreen
import com.akocis.babysleeptracker.ui.screen.ManualEntryScreen
import com.akocis.babysleeptracker.ui.screen.SettingsScreen
import com.akocis.babysleeptracker.ui.screen.StatsScreen
import com.akocis.babysleeptracker.viewmodel.HistoryViewModel
import com.akocis.babysleeptracker.viewmodel.HomeViewModel
import com.akocis.babysleeptracker.viewmodel.ManualEntryViewModel
import com.akocis.babysleeptracker.viewmodel.StatsViewModel

object Routes {
    const val HOME = "home"
    const val MANUAL_ENTRY = "manual_entry"
    const val STATS = "stats"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
}

@Composable
fun AppNavigation(
    navController: NavHostController,
    prefsRepository: PreferencesRepository,
    fileRepository: FileRepository,
    onThemeChanged: (Boolean) -> Unit
) {
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            val viewModel: HomeViewModel = viewModel()
            HomeScreen(
                viewModel = viewModel,
                onNavigateToManualEntry = { navController.navigate(Routes.MANUAL_ENTRY) },
                onNavigateToStats = { navController.navigate(Routes.STATS) },
                onNavigateToHistory = { navController.navigate(Routes.HISTORY) },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }
        composable(Routes.MANUAL_ENTRY) {
            val viewModel: ManualEntryViewModel = viewModel()
            ManualEntryScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.STATS) {
            val viewModel: StatsViewModel = viewModel()
            StatsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.HISTORY) {
            val viewModel: HistoryViewModel = viewModel()
            HistoryScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                prefsRepository = prefsRepository,
                fileRepository = fileRepository,
                onThemeChanged = onThemeChanged,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
