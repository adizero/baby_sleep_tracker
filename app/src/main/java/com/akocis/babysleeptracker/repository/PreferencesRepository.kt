package com.akocis.babysleeptracker.repository

import android.content.Context
import android.net.Uri
import com.akocis.babysleeptracker.model.TrackingState
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class PreferencesRepository(context: Context) {

    private val prefs = context.getSharedPreferences("baby_sleep_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_FILE_URI = "file_uri"
        private const val KEY_SLEEP_START_EPOCH = "sleep_start_epoch"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_BABY_NAME = "baby_name"
        private const val KEY_BABY_BIRTH_DATE = "baby_birth_date"
    }

    var fileUri: Uri?
        get() = prefs.getString(KEY_FILE_URI, null)?.let { Uri.parse(it) }
        set(value) = prefs.edit().putString(KEY_FILE_URI, value?.toString()).apply()

    // themeMode: "light", "dark", or "auto"
    var themeMode: String
        get() = prefs.getString(KEY_THEME_MODE, "light") ?: "light"
        set(value) = prefs.edit().putString(KEY_THEME_MODE, value).apply()

    // Keep backward compat getter
    val darkTheme: Boolean get() = themeMode == "dark"

    var babyName: String?
        get() = prefs.getString(KEY_BABY_NAME, null)
        set(value) = prefs.edit().putString(KEY_BABY_NAME, value).apply()

    var babyBirthDate: LocalDate?
        get() = prefs.getString(KEY_BABY_BIRTH_DATE, null)?.let { LocalDate.parse(it) }
        set(value) = prefs.edit().putString(KEY_BABY_BIRTH_DATE, value?.toString()).apply()

    fun saveTrackingState(state: TrackingState) {
        when (state) {
            is TrackingState.Idle -> {
                prefs.edit().remove(KEY_SLEEP_START_EPOCH).apply()
            }
            is TrackingState.Sleeping -> {
                val epochSeconds = state.startDate.atTime(state.startTime)
                    .atZone(ZoneId.systemDefault())
                    .toEpochSecond()
                prefs.edit().putLong(KEY_SLEEP_START_EPOCH, epochSeconds).apply()
            }
        }
    }

    fun loadTrackingState(): TrackingState {
        val epoch = prefs.getLong(KEY_SLEEP_START_EPOCH, -1L)
        if (epoch == -1L) return TrackingState.Idle

        val instant = Instant.ofEpochSecond(epoch)
        val zdt = instant.atZone(ZoneId.systemDefault())
        return TrackingState.Sleeping(
            startDate = zdt.toLocalDate(),
            startTime = LocalTime.of(zdt.hour, zdt.minute)
        )
    }
}
