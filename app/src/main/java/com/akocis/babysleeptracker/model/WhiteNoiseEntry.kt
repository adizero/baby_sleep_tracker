package com.akocis.babysleeptracker.model

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

data class WhiteNoiseEntry(
    val noiseType: NoiseType,
    val date: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime? = null,
    val id: String? = null
) {
    val isOngoing: Boolean get() = endTime == null

    val duration: Duration
        get() {
            val end = endTime ?: LocalTime.now().withSecond(0).withNano(0)
            val dur = Duration.between(startTime, end)
            return if (dur.isNegative) dur.plusHours(24) else dur
        }
}
