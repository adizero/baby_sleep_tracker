package com.akocis.babysleeptracker.model

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

data class SleepEntry(
    val date: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime
) {
    val duration: Duration
        get() {
            val dur = Duration.between(startTime, endTime)
            return if (dur.isNegative) dur.plusHours(24) else dur
        }
}
