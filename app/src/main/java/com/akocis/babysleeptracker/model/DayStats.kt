package com.akocis.babysleeptracker.model

import java.time.Duration
import java.time.LocalDate

data class DayStats(
    val date: LocalDate,
    val totalSleep: Duration,
    val sleepCount: Int,
    val peeCount: Int,
    val pooCount: Int,
    val peepooCount: Int,
    val daySleep: Duration = Duration.ZERO,
    val nightSleep: Duration = Duration.ZERO,
    val longestNap: Duration = Duration.ZERO,
    val shortestNap: Duration = Duration.ZERO,
    val feedCount: Int = 0,
    val totalFeedDuration: Duration = Duration.ZERO,
    val label: String? = null
) {
    val totalDiapers: Int get() = peeCount + pooCount + peepooCount
}
