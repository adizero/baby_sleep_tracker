package com.akocis.babysleeptracker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.akocis.babysleeptracker.R

class BabyAlarmService : Service() {

    companion object {
        const val ACTION_DISMISS = "com.akocis.babysleeptracker.DISMISS_ALARM"
        const val EXTRA_ALARM_TYPE = "alarm_type"
        const val EXTRA_RINGTONE_URI = "ringtone_uri"
        const val ALARM_TYPE_SLEEP = "sleep"
        const val ALARM_TYPE_FEED = "feed"

        private const val CHANNEL_ID = "baby_alarm_channel"
        private const val NOTIFICATION_ID = 99
    }

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISMISS) {
            stopAlarm()
            return START_NOT_STICKY
        }

        val alarmType = intent?.getStringExtra(EXTRA_ALARM_TYPE) ?: ALARM_TYPE_SLEEP
        val ringtoneUriStr = intent?.getStringExtra(EXTRA_RINGTONE_URI)

        val title = if (alarmType == ALARM_TYPE_SLEEP) "Sleep Alarm" else "Feeding Alarm"
        val text = if (alarmType == ALARM_TYPE_SLEEP)
            "Baby has been sleeping for the configured duration"
        else
            "Time to feed the baby"

        startForeground(NOTIFICATION_ID, buildNotification(title, text))
        playAlarm(ringtoneUriStr)
        startVibration()

        return START_NOT_STICKY
    }

    private fun playAlarm(ringtoneUriStr: String?) {
        try {
            val uri = if (ringtoneUriStr != null) {
                Uri.parse(ringtoneUriStr)
            } else {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            }

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@BabyAlarmService, uri)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            // Fallback to default alarm
            try {
                val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                if (defaultUri != null) {
                    mediaPlayer = MediaPlayer().apply {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ALARM)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build()
                        )
                        setDataSource(this@BabyAlarmService, defaultUri)
                        isLooping = true
                        prepare()
                        start()
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun startVibration() {
        vibrator = if (android.os.Build.VERSION.SDK_INT >= 31) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        val pattern = longArrayOf(0, 500, 500)
        vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
    }

    private fun stopAlarm() {
        mediaPlayer?.apply {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
        vibrator?.cancel()
        vibrator = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        mediaPlayer?.apply {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
        vibrator?.cancel()
        vibrator = null
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Baby Alarms",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Sleep and feeding alarms"
            enableVibration(true)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(title: String, text: String): Notification {
        val dismissIntent = Intent(this, BabyAlarmService::class.java).apply {
            action = ACTION_DISMISS
        }
        val dismissPendingIntent = PendingIntent.getService(
            this, 0, dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .addAction(0, "Dismiss", dismissPendingIntent)
            .build()
    }
}
