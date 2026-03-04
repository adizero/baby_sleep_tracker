package com.akocis.babysleeptracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation.compose.rememberNavController
import com.akocis.babysleeptracker.repository.FileRepository
import com.akocis.babysleeptracker.repository.PreferencesRepository
import com.akocis.babysleeptracker.ui.navigation.AppNavigation
import com.akocis.babysleeptracker.ui.theme.BabySleepTrackerTheme

class MainActivity : ComponentActivity() {

    private lateinit var prefsRepository: PreferencesRepository
    private lateinit var fileRepository: FileRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        prefsRepository = PreferencesRepository(applicationContext)
        fileRepository = FileRepository(applicationContext)

        var darkTheme by mutableStateOf(prefsRepository.darkTheme)

        setContent {
            BabySleepTrackerTheme(darkTheme = darkTheme) {
                val navController = rememberNavController()
                AppNavigation(
                    navController = navController,
                    prefsRepository = prefsRepository,
                    fileRepository = fileRepository,
                    onThemeChanged = { darkTheme = it }
                )
            }
        }
    }
}
