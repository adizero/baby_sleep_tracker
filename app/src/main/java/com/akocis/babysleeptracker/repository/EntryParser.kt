package com.akocis.babysleeptracker.repository

import com.akocis.babysleeptracker.model.DiaperEntry
import com.akocis.babysleeptracker.model.DiaperType
import com.akocis.babysleeptracker.model.SleepEntry
import com.akocis.babysleeptracker.util.DateTimeUtil
import java.time.LocalDate
import java.time.LocalTime

object EntryParser {

    private val SLEEP_REGEX = Regex(
        """^SLEEP\s+(\d{4}-\d{2}-\d{2})\s+(\d{2}:\d{2})\s*-\s*(\d{2}:\d{2})$"""
    )
    private val DIAPER_REGEX = Regex(
        """^(PEE|POO|PEEPOO)\s+(\d{4}-\d{2}-\d{2})\s+(\d{2}:\d{2})$"""
    )

    fun parseLine(line: String): Any? {
        val trimmed = line.trim()
        if (trimmed.isBlank()) return null

        SLEEP_REGEX.matchEntire(trimmed)?.let { match ->
            val date = LocalDate.parse(match.groupValues[1], DateTimeUtil.DATE_FORMAT)
            val start = LocalTime.parse(match.groupValues[2], DateTimeUtil.TIME_FORMAT)
            val end = LocalTime.parse(match.groupValues[3], DateTimeUtil.TIME_FORMAT)
            return SleepEntry(date, start, end)
        }

        DIAPER_REGEX.matchEntire(trimmed)?.let { match ->
            val type = DiaperType.fromString(match.groupValues[1]) ?: return null
            val date = LocalDate.parse(match.groupValues[2], DateTimeUtil.DATE_FORMAT)
            val time = LocalTime.parse(match.groupValues[3], DateTimeUtil.TIME_FORMAT)
            return DiaperEntry(type, date, time)
        }

        return null
    }

    fun parseAll(content: String): Pair<List<SleepEntry>, List<DiaperEntry>> {
        val sleepEntries = mutableListOf<SleepEntry>()
        val diaperEntries = mutableListOf<DiaperEntry>()

        content.lines().forEach { line ->
            when (val entry = parseLine(line)) {
                is SleepEntry -> sleepEntries.add(entry)
                is DiaperEntry -> diaperEntries.add(entry)
            }
        }

        return sleepEntries to diaperEntries
    }

    fun formatSleepEntry(entry: SleepEntry): String {
        val date = entry.date.format(DateTimeUtil.DATE_FORMAT)
        val start = entry.startTime.format(DateTimeUtil.TIME_FORMAT)
        val end = entry.endTime.format(DateTimeUtil.TIME_FORMAT)
        return "SLEEP $date $start - $end"
    }

    fun formatDiaperEntry(entry: DiaperEntry): String {
        val date = entry.date.format(DateTimeUtil.DATE_FORMAT)
        val time = entry.time.format(DateTimeUtil.TIME_FORMAT)
        return "${entry.type.name} $date $time"
    }
}
