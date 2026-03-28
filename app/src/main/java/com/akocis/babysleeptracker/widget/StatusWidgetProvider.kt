package com.akocis.babysleeptracker.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import com.akocis.babysleeptracker.MainActivity
import com.akocis.babysleeptracker.R
import com.akocis.babysleeptracker.model.TrackingState
import com.akocis.babysleeptracker.repository.PreferencesRepository
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class StatusWidgetProvider : AppWidgetProvider() {

    companion object {
        fun updateAllWidgets(context: Context) {
            val intent = Intent(context, StatusWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            }
            val ids = AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, StatusWidgetProvider::class.java))
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            context.sendBroadcast(intent)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (widgetId in appWidgetIds) {
            val options = appWidgetManager.getAppWidgetOptions(widgetId)
            updateWidget(context, appWidgetManager, widgetId, options)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        updateWidget(context, appWidgetManager, appWidgetId, newOptions)
    }

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        widgetId: Int,
        options: Bundle
    ) {
        val prefs = PreferencesRepository(context)
        val state = prefs.loadTrackingState()
        val layoutMode = prefs.widgetLayout

        val statusText: String
        val bgRes: Int
        var startEpochMillis: Long = -1L

        when (state) {
            is TrackingState.Sleeping -> {
                statusText = "\uD83C\uDF19 Sleeping"
                startEpochMillis = state.startDate.atTime(state.startTime)
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                bgRes = R.drawable.widget_bg_sleeping
            }
            is TrackingState.Feeding -> {
                statusText = "\uD83C\uDF7C Feeding (${state.side.label[0]})"
                startEpochMillis = state.startDate.atTime(state.startTime)
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                bgRes = R.drawable.widget_bg_feeding
            }
            is TrackingState.Idle -> {
                statusText = "\u2600\uFE0F Awake"
                val lastSleepEndEpoch = prefs.lastSleepEndEpoch
                if (lastSleepEndEpoch > 0) {
                    startEpochMillis = lastSleepEndEpoch * 1000L
                }
                bgRes = R.drawable.widget_bg_awake
            }
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val layoutRes = when (layoutMode) {
            "baby_right" -> R.layout.widget_status_baby_right
            else -> R.layout.widget_status
        }

        val views = RemoteViews(context.packageName, layoutRes)
        views.setTextViewText(R.id.widget_status_text, statusText)
        views.setInt(R.id.widget_root, "setBackgroundResource", bgRes)
        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

        // Configure chronometer for elapsed time
        if (startEpochMillis > 0) {
            val elapsedSinceStart = System.currentTimeMillis() - startEpochMillis
            val base = SystemClock.elapsedRealtime() - elapsedSinceStart
            views.setChronometer(R.id.widget_elapsed, base, null, true)
            views.setViewVisibility(R.id.widget_elapsed, View.VISIBLE)
        } else {
            views.setChronometer(R.id.widget_elapsed, SystemClock.elapsedRealtime(), null, false)
            views.setViewVisibility(R.id.widget_elapsed, View.GONE)
        }

        // Show baby info when widget is wide enough and not hidden
        val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        val showBabyInfo = widthDp >= 180 && layoutMode != "status_only"
        if (showBabyInfo) {
            val babyName = prefs.babyName ?: ""
            val birthDate = prefs.babyBirthDate
            val ageText = if (birthDate != null) formatAge(birthDate) else ""
            views.setViewVisibility(R.id.widget_baby_info, View.VISIBLE)
            views.setViewVisibility(R.id.widget_divider, View.VISIBLE)
            views.setTextViewText(R.id.widget_baby_name, babyName)
            views.setTextViewText(R.id.widget_baby_age, ageText)
        } else {
            views.setViewVisibility(R.id.widget_baby_info, View.GONE)
            views.setViewVisibility(R.id.widget_divider, View.GONE)
        }

        appWidgetManager.updateAppWidget(widgetId, views)
    }

    private fun formatAge(birthDate: LocalDate): String {
        val today = LocalDate.now()
        val months = ChronoUnit.MONTHS.between(birthDate, today)
        val days = ChronoUnit.DAYS.between(birthDate.plusMonths(months), today)
        return when {
            months >= 12 -> {
                val years = months / 12
                val remainingMonths = months % 12
                if (remainingMonths > 0) "${years}y ${remainingMonths}m"
                else "${years}y"
            }
            months > 0 -> {
                if (days > 0) "${months}m ${days}d"
                else "${months}m"
            }
            else -> "${days}d"
        }
    }
}
