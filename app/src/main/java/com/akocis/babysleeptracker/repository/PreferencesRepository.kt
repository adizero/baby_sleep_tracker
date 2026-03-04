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
        private const val KEY_DARK_THEME = "dark_theme"
    }

    var fileUri: Uri?
        get() = prefs.getString(KEY_FILE_URI, null)?.let { Uri.parse(it) }
        set(value) = prefs.edit().putString(KEY_FILE_URI, value?.toString()).apply()

    var darkTheme: Boolean
        get() = prefs.getBoolean(KEY_DARK_THEME, false)
        set(value) = prefs.edit().putBoolean(KEY_DARK_THEME, value).apply()

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
