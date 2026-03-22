package com.akocis.babysleeptracker.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.akocis.babysleeptracker.receiver.AlarmReceiver
import com.akocis.babysleeptracker.service.BabyAlarmService

object AlarmScheduler {

    private const val SLEEP_ALARM_REQUEST_CODE = 1001
    private const val FEED_ALARM_REQUEST_CODE = 1002
    private const val BREAST_ALARM_REQUEST_CODE = 1003

    fun scheduleSleepAlarm(context: Context, triggerAtMillis: Long, ringtoneUri: String?) {
        schedule(context, triggerAtMillis, ringtoneUri, BabyAlarmService.ALARM_TYPE_SLEEP, SLEEP_ALARM_REQUEST_CODE)
    }

    fun scheduleFeedAlarm(context: Context, triggerAtMillis: Long, ringtoneUri: String?) {
        schedule(context, triggerAtMillis, ringtoneUri, BabyAlarmService.ALARM_TYPE_FEED, FEED_ALARM_REQUEST_CODE)
    }

    fun cancelSleepAlarm(context: Context) {
        cancel(context, BabyAlarmService.ALARM_TYPE_SLEEP, SLEEP_ALARM_REQUEST_CODE)
    }

    fun cancelFeedAlarm(context: Context) {
        cancel(context, BabyAlarmService.ALARM_TYPE_FEED, FEED_ALARM_REQUEST_CODE)
    }

    fun scheduleBreastAlarm(context: Context, triggerAtMillis: Long, ringtoneUri: String?) {
        schedule(context, triggerAtMillis, ringtoneUri, BabyAlarmService.ALARM_TYPE_BREAST, BREAST_ALARM_REQUEST_CODE)
    }

    fun cancelBreastAlarm(context: Context) {
        cancel(context, BabyAlarmService.ALARM_TYPE_BREAST, BREAST_ALARM_REQUEST_CODE)
    }

    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return alarmManager.canScheduleExactAlarms()
    }

    private fun schedule(context: Context, triggerAtMillis: Long, ringtoneUri: String?, alarmType: String, requestCode: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            return
        }

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(BabyAlarmService.EXTRA_ALARM_TYPE, alarmType)
            ringtoneUri?.let { putExtra(BabyAlarmService.EXTRA_RINGTONE_URI, it) }
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val showIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val showPendingIntent = PendingIntent.getActivity(
            context, 0, showIntent ?: Intent(),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerAtMillis, showPendingIntent)
        alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
    }

    private fun cancel(context: Context, alarmType: String, requestCode: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(BabyAlarmService.EXTRA_ALARM_TYPE, alarmType)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }
}
