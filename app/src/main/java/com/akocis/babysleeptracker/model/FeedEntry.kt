package com.akocis.babysleeptracker.model

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

enum class FeedSide(val label: String) {
    LEFT("Left"), RIGHT("Right");

    companion object {
        fun fromString(value: String): FeedSide? = when (value.uppercase()) {
            "FEEDL", "LEFT", "L" -> LEFT
            "FEEDR", "RIGHT", "R" -> RIGHT
            else -> null
        }
    }
}

data class FeedEntry(
    val side: FeedSide,
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

    val typeTag: String get() = if (side == FeedSide.LEFT) "FEEDL" else "FEEDR"
}
