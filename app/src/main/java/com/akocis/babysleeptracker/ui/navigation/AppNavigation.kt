package com.akocis.babysleeptracker.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.akocis.babysleeptracker.repository.FileRepository
import com.akocis.babysleeptracker.repository.PreferencesRepository
import com.akocis.babysleeptracker.ui.screen.CalendarScreen
import com.akocis.babysleeptracker.ui.screen.HistoryScreen
import com.akocis.babysleeptracker.ui.screen.HomeScreen
import com.akocis.babysleeptracker.ui.screen.ManualEntryScreen
import com.akocis.babysleeptracker.ui.screen.SettingsScreen
import com.akocis.babysleeptracker.ui.screen.StatsScreen
import com.akocis.babysleeptracker.ui.screen.GrowthScreen
import com.akocis.babysleeptracker.ui.screen.HighContrastScreen
import com.akocis.babysleeptracker.ui.screen.SyncScreen
import com.akocis.babysleeptracker.viewmodel.CalendarViewModel
import com.akocis.babysleeptracker.viewmodel.GrowthViewModel
import com.akocis.babysleeptracker.viewmodel.HistoryViewModel
import com.akocis.babysleeptracker.viewmodel.HomeViewModel
import com.akocis.babysleeptracker.viewmodel.ManualEntryViewModel
import com.akocis.babysleeptracker.viewmodel.StatsViewModel
import com.akocis.babysleeptracker.viewmodel.SyncViewModel

object Routes {
    const val HOME = "home"
    const val MANUAL_ENTRY = "manual_entry"
    const val EDIT_ENTRY = "edit_entry/{rawLine}"
    const val STATS = "stats"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val CALENDAR = "calendar"
    const val SYNC = "sync"
    const val GROWTH = "growth"
    const val MANUAL_ENTRY_MEASURE = "manual_entry_measure"
    const val HIGH_CONTRAST = "high_contrast"

    fun editEntry(rawLine: String): String {
        val encoded = Uri.encode(rawLine)
        return "edit_entry/$encoded"
    }
}

@Composable
fun AppNavigation(
    navController: NavHostController,
    prefsRepository: PreferencesRepository,
    fileRepository: FileRepository,
    onThemeModeChanged: (String) -> Unit
) {
    val safePopBack: () -> Unit = {
        if (navController.currentDestination?.route != Routes.HOME) {
            navController.popBackStack()
        }
    }

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) { backStackEntry ->
            val viewModel: HomeViewModel = viewModel()
            DisposableEffect(backStackEntry.lifecycle) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        viewModel.syncAndRefresh()
                    }
                }
                backStackEntry.lifecycle.addObserver(observer)
                onDispose { backStackEntry.lifecycle.removeObserver(observer) }
            }
            HomeScreen(
                viewModel = viewModel,
                onNavigateToManualEntry = { navController.navigate(Routes.MANUAL_ENTRY) },
                onNavigateToStats = { navController.navigate(Routes.STATS) },
                onNavigateToHistory = { navController.navigate(Routes.HISTORY) },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onNavigateToCalendar = { navController.navigate(Routes.CALENDAR) },
                onNavigateToGrowth = { navController.navigate(Routes.GROWTH) },
                onNavigateToHighContrast = { navController.navigate(Routes.HIGH_CONTRAST) }
            )
        }
        composable(Routes.MANUAL_ENTRY) {
            val viewModel: ManualEntryViewModel = viewModel()
            ManualEntryScreen(
                viewModel = viewModel,
                onBack = safePopBack
            )
        }
        composable(Routes.MANUAL_ENTRY_MEASURE) {
            val viewModel: ManualEntryViewModel = viewModel()
            LaunchedEffect(Unit) {
                viewModel.setEntryKind(com.akocis.babysleeptracker.viewmodel.EntryKind.MEASURE)
            }
            ManualEntryScreen(
                viewModel = viewModel,
                onBack = safePopBack
            )
        }
        composable(
            route = Routes.EDIT_ENTRY,
            arguments = listOf(navArgument("rawLine") { type = NavType.StringType })
        ) { backStackEntry ->
            val rawLine = Uri.decode(
                backStackEntry.arguments?.getString("rawLine") ?: ""
            )
            val viewModel: ManualEntryViewModel = viewModel()
            LaunchedEffect(Unit) {
                viewModel.initForEdit(rawLine)
            }
            ManualEntryScreen(
                viewModel = viewModel,
                onBack = safePopBack
            )
        }
        composable(Routes.STATS) {
            val viewModel: StatsViewModel = viewModel()
            StatsScreen(
                viewModel = viewModel,
                prefsRepository = prefsRepository,
                onBack = safePopBack
            )
        }
        composable(Routes.HISTORY) { backStackEntry ->
            val viewModel: HistoryViewModel = viewModel()
            DisposableEffect(backStackEntry.lifecycle) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        viewModel.loadEntries()
                    }
                }
                backStackEntry.lifecycle.addObserver(observer)
                onDispose { backStackEntry.lifecycle.removeObserver(observer) }
            }
            HistoryScreen(
                viewModel = viewModel,
                onBack = safePopBack,
                onEditEntry = { item ->
                    navController.navigate(Routes.editEntry(item.rawLine))
                }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                prefsRepository = prefsRepository,
                fileRepository = fileRepository,
                onThemeModeChanged = onThemeModeChanged,
                onNavigateToSync = { navController.navigate(Routes.SYNC) },
                onBack = safePopBack
            )
        }
        composable(Routes.SYNC) {
            val viewModel: SyncViewModel = viewModel()
            SyncScreen(
                viewModel = viewModel,
                onBack = safePopBack
            )
        }
        composable(Routes.CALENDAR) {
            val viewModel: CalendarViewModel = viewModel()
            CalendarScreen(
                viewModel = viewModel,
                onBack = safePopBack
            )
        }
        composable(Routes.GROWTH) { backStackEntry ->
            val viewModel: GrowthViewModel = viewModel()
            DisposableEffect(backStackEntry.lifecycle) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        viewModel.loadData()
                    }
                }
                backStackEntry.lifecycle.addObserver(observer)
                onDispose { backStackEntry.lifecycle.removeObserver(observer) }
            }
            GrowthScreen(
                viewModel = viewModel,
                onBack = safePopBack,
                onAddMeasurement = { navController.navigate(Routes.MANUAL_ENTRY_MEASURE) },
                onEditMeasurement = { rawLine ->
                    navController.navigate(Routes.editEntry(rawLine))
                }
            )
        }
        composable(Routes.HIGH_CONTRAST) {
            HighContrastScreen(
                prefsRepository = prefsRepository,
                onBack = safePopBack
            )
        }
    }
}
