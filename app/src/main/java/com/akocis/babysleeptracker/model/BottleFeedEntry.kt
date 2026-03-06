package com.akocis.babysleeptracker.model

import java.time.LocalDate
import java.time.LocalTime

enum class BottleType(val label: String) {
    DONOR("Donor"),
    FORMULA("Formula"),
    PUMPED("Pumped");

    companion object {
        fun fromString(value: String): BottleType? = when (value.uppercase()) {
            "DONOR" -> DONOR
            "FORMULA" -> FORMULA
            "PUMPED" -> PUMPED
            else -> null
        }
    }
}

data class BottleFeedEntry(
    val type: BottleType,
    val date: LocalDate,
    val time: LocalTime,
    val amountMl: Int,
    val id: String? = null
)
