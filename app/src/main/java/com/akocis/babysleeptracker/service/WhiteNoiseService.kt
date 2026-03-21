package com.akocis.babysleeptracker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.akocis.babysleeptracker.R
import com.akocis.babysleeptracker.model.NoiseType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

sealed class NoiseServiceState {
    data object Idle : NoiseServiceState()
    data class Playing(
        val noiseType: NoiseType,
        val startDate: LocalDate,
        val startTime: LocalTime,
        val durationMs: Long
    ) : NoiseServiceState()
}

class WhiteNoiseService : Service() {

    companion object {
        const val ACTION_STOP = "com.akocis.babysleeptracker.STOP_NOISE"
        const val EXTRA_NOISE_TYPE = "noise_type"
        const val EXTRA_VOLUME = "volume"
        const val EXTRA_FADE_IN = "fade_in"
        const val EXTRA_FADE_OUT = "fade_out"
        const val EXTRA_DURATION_MS = "duration_ms"

        private const val CHANNEL_ID = "white_noise_channel"
        private const val NOTIFICATION_ID = 42

        private val _serviceState = MutableStateFlow<NoiseServiceState>(NoiseServiceState.Idle)
        val serviceState: StateFlow<NoiseServiceState> = _serviceState
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val noiseGenerator = NoiseGenerator()
    private var mediaSession: MediaSession? = null
    private var autoStopJob: Job? = null
    private var fadeOutSeconds = 0f

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopNoise()
            return START_NOT_STICKY
        }

        val noiseTypeName = intent?.getStringExtra(EXTRA_NOISE_TYPE) ?: "WHITE"
        val noiseType = NoiseType.fromString(noiseTypeName) ?: NoiseType.WHITE
        val volume = intent?.getFloatExtra(EXTRA_VOLUME, 0.5f) ?: 0.5f
        val fadeIn = intent?.getFloatExtra(EXTRA_FADE_IN, 0f) ?: 0f
        fadeOutSeconds = intent?.getFloatExtra(EXTRA_FADE_OUT, 0f) ?: 0f
        val durationMs = intent?.getLongExtra(EXTRA_DURATION_MS, 0L) ?: 0L

        // MediaSession required for mediaPlayback foreground service type on Android 14+
        mediaSession = MediaSession(this, "WhiteNoise").apply {
            setPlaybackState(
                PlaybackState.Builder()
                    .setState(PlaybackState.STATE_PLAYING, 0, 1f)
                    .build()
            )
            isActive = true
        }

        startForeground(NOTIFICATION_ID, buildNotification(noiseType))

        noiseGenerator.start(noiseType, volume, fadeIn)

        val now = LocalTime.now().withSecond(0).withNano(0)
        _serviceState.value = NoiseServiceState.Playing(noiseType, LocalDate.now(), now, durationMs)

        // Auto-stop timer
        autoStopJob?.cancel()
        if (durationMs > 0) {
            autoStopJob = scope.launch {
                delay(durationMs)
                stopNoise()
            }
        }

        return START_NOT_STICKY
    }

    private fun stopNoise() {
        autoStopJob?.cancel()
        if (fadeOutSeconds > 0) {
            noiseGenerator.fadeOutAndStop(fadeOutSeconds) {
                _serviceState.value = NoiseServiceState.Idle
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        } else {
            noiseGenerator.stop()
            _serviceState.value = NoiseServiceState.Idle
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        autoStopJob?.cancel()
        noiseGenerator.stop()
        mediaSession?.isActive = false
        mediaSession?.release()
        mediaSession = null
        _serviceState.value = NoiseServiceState.Idle
        scope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "White Noise",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setSound(null, null)
            description = "White noise playback"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(noiseType: NoiseType): Notification {
        val stopIntent = Intent(this, WhiteNoiseService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("${noiseType.label} Noise")
            .setContentText("Playing...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setSilent(true)
            .addAction(0, "Stop", stopPendingIntent)
            .build()
    }
}
