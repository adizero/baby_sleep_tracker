package com.akocis.babysleeptracker.util

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

object DateTimeUtil {
    val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun formatDuration(duration: Duration): String {
        val hours = duration.toHours()
        val minutes = duration.toMinutes() % 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            else -> "${minutes}m"
        }
    }

    fun formatDurationWithDays(duration: Duration): String {
        val days = duration.toDays()
        val hours = duration.toHours() % 24
        val minutes = duration.toMinutes() % 60
        return when {
            days > 0 -> "${days}d ${hours}h"
            hours > 0 -> "${hours}h ${minutes}m"
            else -> "${minutes}m"
        }
    }

    fun overlapDuration(
        entryStart: LocalDateTime,
        entryEnd: LocalDateTime,
        bucketStart: LocalDateTime,
        bucketEnd: LocalDateTime
    ): Duration {
        val overlapStart = if (entryStart > bucketStart) entryStart else bucketStart
        val overlapEnd = if (entryEnd < bucketEnd) entryEnd else bucketEnd
        return if (overlapStart < overlapEnd) Duration.between(overlapStart, overlapEnd) else Duration.ZERO
    }

    fun formatElapsed(startDate: LocalDate, startTime: LocalTime): String {
        val startSeconds = startDate.atTime(startTime).toEpochSecond(java.time.ZoneOffset.UTC)
        val nowSeconds = LocalDate.now().atTime(LocalTime.now()).toEpochSecond(java.time.ZoneOffset.UTC)
        val elapsed = Duration.ofSeconds(nowSeconds - startSeconds)
        return formatDuration(elapsed)
    }
}
