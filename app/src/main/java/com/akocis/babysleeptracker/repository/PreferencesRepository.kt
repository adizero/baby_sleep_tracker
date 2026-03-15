package com.akocis.babysleeptracker.repository

import android.content.Context
import android.net.Uri
import com.akocis.babysleeptracker.model.FeedSide
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
        private const val KEY_FEED_START_EPOCH = "feed_start_epoch"
        private const val KEY_FEED_SIDE = "feed_side"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_BABY_NAME = "baby_name"
        private const val KEY_BABY_BIRTH_DATE = "baby_birth_date"
        private const val KEY_DROPBOX_APP_KEY = "dropbox_app_key"
        private const val KEY_DROPBOX_REFRESH_TOKEN = "dropbox_refresh_token"
        private const val KEY_DROPBOX_ACCESS_TOKEN = "dropbox_access_token"
        private const val KEY_DROPBOX_TOKEN_EXPIRY = "dropbox_token_expiry"
        private const val KEY_DROPBOX_FILE_PATH = "dropbox_file_path"
        private const val KEY_BOTTLE_PRESET_ML = "bottle_preset_ml"
        private const val KEY_DAY_START_HOUR = "day_start_hour"
        private const val KEY_DAY_END_HOUR = "day_end_hour"
        private const val KEY_LAST_NOISE_TYPE = "last_noise_type"
        private const val KEY_NOISE_VOLUME = "noise_volume"
        private const val KEY_NOISE_FADE_IN = "noise_fade_in"
        private const val KEY_NOISE_FADE_OUT = "noise_fade_out"
        private const val KEY_BABY_SEX = "baby_sex"
        private const val KEY_USE_METRIC = "use_metric"
        private const val KEY_HC_USE_GENERATED = "hc_use_generated"
        private const val KEY_HC_SLIDESHOW = "hc_slideshow"
        private const val KEY_HC_SLIDESHOW_DELAY = "hc_slideshow_delay"
        private const val KEY_HC_TRANSITION = "hc_transition"
        private const val KEY_HC_KEEP_SCREEN_ON = "hc_keep_screen_on"
        private const val KEY_HC_FOLDER_URI = "hc_folder_uri"
        private const val KEY_HC_ENABLED_PATTERNS = "hc_enabled_patterns"
        private const val KEY_HC_ENABLED_COLORS = "hc_enabled_colors"
        private const val KEY_SLEEP_ALARM_ENABLED = "sleep_alarm_enabled"
        private const val KEY_SLEEP_ALARM_MINUTES = "sleep_alarm_minutes"
        private const val KEY_SLEEP_ALARM_RINGTONE = "sleep_alarm_ringtone"
        private const val KEY_FEED_ALARM_ENABLED = "feed_alarm_enabled"
        private const val KEY_FEED_ALARM_MINUTES = "feed_alarm_minutes"
        private const val KEY_FEED_ALARM_RINGTONE = "feed_alarm_ringtone"
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

    var babySex: String?
        get() = prefs.getString(KEY_BABY_SEX, "BOY")
        set(value) = prefs.edit().putString(KEY_BABY_SEX, value).apply()

    var useMetric: Boolean
        get() = prefs.getBoolean(KEY_USE_METRIC, true)
        set(value) = prefs.edit().putBoolean(KEY_USE_METRIC, value).apply()

    fun saveTrackingState(state: TrackingState) {
        when (state) {
            is TrackingState.Idle -> {
                prefs.edit()
                    .remove(KEY_SLEEP_START_EPOCH)
                    .remove(KEY_FEED_START_EPOCH)
                    .remove(KEY_FEED_SIDE)
                    .apply()
            }
            is TrackingState.Sleeping -> {
                val epochSeconds = state.startDate.atTime(state.startTime)
                    .atZone(ZoneId.systemDefault())
                    .toEpochSecond()
                prefs.edit()
                    .putLong(KEY_SLEEP_START_EPOCH, epochSeconds)
                    .remove(KEY_FEED_START_EPOCH)
                    .remove(KEY_FEED_SIDE)
                    .apply()
            }
            is TrackingState.Feeding -> {
                val epochSeconds = state.startDate.atTime(state.startTime)
                    .atZone(ZoneId.systemDefault())
                    .toEpochSecond()
                prefs.edit()
                    .putLong(KEY_FEED_START_EPOCH, epochSeconds)
                    .putString(KEY_FEED_SIDE, state.side.name)
                    .remove(KEY_SLEEP_START_EPOCH)
                    .apply()
            }
        }
    }

    fun loadTrackingState(): TrackingState {
        val feedEpoch = prefs.getLong(KEY_FEED_START_EPOCH, -1L)
        if (feedEpoch != -1L) {
            val sideName = prefs.getString(KEY_FEED_SIDE, null)
            val side = sideName?.let { FeedSide.valueOf(it) } ?: FeedSide.LEFT
            val instant = Instant.ofEpochSecond(feedEpoch)
            val zdt = instant.atZone(ZoneId.systemDefault())
            return TrackingState.Feeding(
                side = side,
                startDate = zdt.toLocalDate(),
                startTime = LocalTime.of(zdt.hour, zdt.minute)
            )
        }

        val sleepEpoch = prefs.getLong(KEY_SLEEP_START_EPOCH, -1L)
        if (sleepEpoch != -1L) {
            val instant = Instant.ofEpochSecond(sleepEpoch)
            val zdt = instant.atZone(ZoneId.systemDefault())
            return TrackingState.Sleeping(
                startDate = zdt.toLocalDate(),
                startTime = LocalTime.of(zdt.hour, zdt.minute)
            )
        }

        return TrackingState.Idle
    }

    var dayStartHour: Int
        get() = prefs.getInt(KEY_DAY_START_HOUR, 7)
        set(value) = prefs.edit().putInt(KEY_DAY_START_HOUR, value).apply()

    var dayEndHour: Int
        get() = prefs.getInt(KEY_DAY_END_HOUR, 19)
        set(value) = prefs.edit().putInt(KEY_DAY_END_HOUR, value).apply()

    var bottlePresetMl: Int
        get() = prefs.getInt(KEY_BOTTLE_PRESET_ML, -1)
        set(value) = prefs.edit().putInt(KEY_BOTTLE_PRESET_ML, value).apply()

    // Dropbox sync preferences

    var dropboxAppKey: String?
        get() = prefs.getString(KEY_DROPBOX_APP_KEY, null)
        set(value) = prefs.edit().putString(KEY_DROPBOX_APP_KEY, value).apply()

    var dropboxRefreshToken: String?
        get() = prefs.getString(KEY_DROPBOX_REFRESH_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_DROPBOX_REFRESH_TOKEN, value).apply()

    var dropboxAccessToken: String?
        get() = prefs.getString(KEY_DROPBOX_ACCESS_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_DROPBOX_ACCESS_TOKEN, value).apply()

    var dropboxTokenExpiry: Long
        get() = prefs.getLong(KEY_DROPBOX_TOKEN_EXPIRY, 0L)
        set(value) = prefs.edit().putLong(KEY_DROPBOX_TOKEN_EXPIRY, value).apply()

    var dropboxFilePath: String
        get() = prefs.getString(KEY_DROPBOX_FILE_PATH, "/baby_sleep_log.txt")
            ?: "/baby_sleep_log.txt"
        set(value) = prefs.edit().putString(KEY_DROPBOX_FILE_PATH, value).apply()

    var lastNoiseType: String
        get() = prefs.getString(KEY_LAST_NOISE_TYPE, "WHITE") ?: "WHITE"
        set(value) = prefs.edit().putString(KEY_LAST_NOISE_TYPE, value).apply()

    var noiseVolume: Float
        get() = prefs.getFloat(KEY_NOISE_VOLUME, 0.5f)
        set(value) = prefs.edit().putFloat(KEY_NOISE_VOLUME, value).apply()

    var noiseFadeIn: Int
        get() = prefs.getInt(KEY_NOISE_FADE_IN, 0)
        set(value) = prefs.edit().putInt(KEY_NOISE_FADE_IN, value).apply()

    var noiseFadeOut: Int
        get() = prefs.getInt(KEY_NOISE_FADE_OUT, 0)
        set(value) = prefs.edit().putInt(KEY_NOISE_FADE_OUT, value).apply()

    val isDropboxConfigured: Boolean
        get() = !dropboxAppKey.isNullOrBlank() && !dropboxRefreshToken.isNullOrBlank()

    // High contrast image preferences
    var hcUseGenerated: Boolean
        get() = prefs.getBoolean(KEY_HC_USE_GENERATED, true)
        set(value) = prefs.edit().putBoolean(KEY_HC_USE_GENERATED, value).apply()

    var hcSlideshow: Boolean
        get() = prefs.getBoolean(KEY_HC_SLIDESHOW, true)
        set(value) = prefs.edit().putBoolean(KEY_HC_SLIDESHOW, value).apply()

    var hcSlideshowDelay: Int
        get() = prefs.getInt(KEY_HC_SLIDESHOW_DELAY, 10)
        set(value) = prefs.edit().putInt(KEY_HC_SLIDESHOW_DELAY, value).apply()

    var hcTransition: String
        get() = prefs.getString(KEY_HC_TRANSITION, "FADE") ?: "FADE"
        set(value) = prefs.edit().putString(KEY_HC_TRANSITION, value).apply()

    var hcKeepScreenOn: Boolean
        get() = prefs.getBoolean(KEY_HC_KEEP_SCREEN_ON, true)
        set(value) = prefs.edit().putBoolean(KEY_HC_KEEP_SCREEN_ON, value).apply()

    var hcFolderUri: String?
        get() = prefs.getString(KEY_HC_FOLDER_URI, null)
        set(value) = prefs.edit().putString(KEY_HC_FOLDER_URI, value).apply()

    var hcEnabledPatterns: String
        get() = prefs.getString(KEY_HC_ENABLED_PATTERNS, "") ?: ""
        set(value) = prefs.edit().putString(KEY_HC_ENABLED_PATTERNS, value).apply()

    var hcEnabledColors: String
        get() = prefs.getString(KEY_HC_ENABLED_COLORS, "") ?: ""
        set(value) = prefs.edit().putString(KEY_HC_ENABLED_COLORS, value).apply()

    var sleepAlarmEnabled: Boolean
        get() = prefs.getBoolean(KEY_SLEEP_ALARM_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_SLEEP_ALARM_ENABLED, value).apply()

    var sleepAlarmMinutes: Int
        get() = prefs.getInt(KEY_SLEEP_ALARM_MINUTES, 180)
        set(value) = prefs.edit().putInt(KEY_SLEEP_ALARM_MINUTES, value).apply()

    var sleepAlarmRingtone: String?
        get() = prefs.getString(KEY_SLEEP_ALARM_RINGTONE, null)
        set(value) = prefs.edit().putString(KEY_SLEEP_ALARM_RINGTONE, value).apply()

    var feedAlarmEnabled: Boolean
        get() = prefs.getBoolean(KEY_FEED_ALARM_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_FEED_ALARM_ENABLED, value).apply()

    var feedAlarmMinutes: Int
        get() = prefs.getInt(KEY_FEED_ALARM_MINUTES, 180)
        set(value) = prefs.edit().putInt(KEY_FEED_ALARM_MINUTES, value).apply()

    var feedAlarmRingtone: String?
        get() = prefs.getString(KEY_FEED_ALARM_RINGTONE, null)
        set(value) = prefs.edit().putString(KEY_FEED_ALARM_RINGTONE, value).apply()

    fun clearDropbox() {
        prefs.edit()
            .remove(KEY_DROPBOX_APP_KEY)
            .remove(KEY_DROPBOX_REFRESH_TOKEN)
            .remove(KEY_DROPBOX_ACCESS_TOKEN)
            .remove(KEY_DROPBOX_TOKEN_EXPIRY)
            .remove(KEY_DROPBOX_FILE_PATH)
            .apply()
    }
}
