package com.akocis.babysleeptracker.model

import java.time.LocalDate
import java.time.LocalTime

enum class ActivityType(val label: String) {
    STROLLER("Stroller"),
    BATH("Bath"),
    NOTE("Note");

    companion object {
        fun fromString(value: String): ActivityType? =
            entries.find { it.name.equals(value, ignoreCase = true) }
    }
}

data class ActivityEntry(
    val type: ActivityType,
    val date: LocalDate,
    val time: LocalTime,
    val note: String? = null
)
