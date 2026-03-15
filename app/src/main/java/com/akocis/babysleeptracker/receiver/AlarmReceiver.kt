package com.akocis.babysleeptracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.akocis.babysleeptracker.service.BabyAlarmService

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmType = intent.getStringExtra(BabyAlarmService.EXTRA_ALARM_TYPE) ?: return
        val ringtoneUri = intent.getStringExtra(BabyAlarmService.EXTRA_RINGTONE_URI)
        val serviceIntent = Intent(context, BabyAlarmService::class.java).apply {
            putExtra(BabyAlarmService.EXTRA_ALARM_TYPE, alarmType)
            ringtoneUri?.let { putExtra(BabyAlarmService.EXTRA_RINGTONE_URI, it) }
        }
        ContextCompat.startForegroundService(context, serviceIntent)
    }
}
