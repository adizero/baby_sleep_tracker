package com.akocis.babysleeptracker.model

import java.time.Duration
import java.time.LocalDate

data class DayStats(
    val date: LocalDate,
    val totalSleep: Duration,
    val sleepCount: Int,
    val peeCount: Int,
    val pooCount: Int,
    val peepooCount: Int
) {
    val totalDiapers: Int get() = peeCount + pooCount + peepooCount
}
