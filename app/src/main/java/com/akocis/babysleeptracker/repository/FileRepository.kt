package com.akocis.babysleeptracker.repository

import android.content.Context
import android.net.Uri
import com.akocis.babysleeptracker.model.ActivityEntry
import com.akocis.babysleeptracker.model.BottleFeedEntry
import com.akocis.babysleeptracker.model.DiaperEntry
import com.akocis.babysleeptracker.model.FeedEntry
import com.akocis.babysleeptracker.model.HighContrastEntry
import com.akocis.babysleeptracker.model.SleepEntry
import com.akocis.babysleeptracker.model.WhiteNoiseEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate

class FileRepository(private val context: Context) {

    private val mutex = Mutex()

    suspend fun readAll(uri: Uri): ParsedData =
        withContext(Dispatchers.IO) {
            try {
                val content = readContent(uri)
                EntryParser.parseAll(content)
            } catch (_: Exception) {
                ParsedData()
            }
        }

    suspend fun appendSleepEntry(uri: Uri, entry: SleepEntry) {
        val withId = if (entry.id == null) entry.copy(id = EntryParser.generateId()) else entry
        appendLine(uri, EntryParser.formatSleepEntry(withId))
    }

    suspend fun appendDiaperEntry(uri: Uri, entry: DiaperEntry) {
        val withId = if (entry.id == null) entry.copy(id = EntryParser.generateId()) else entry
        appendLine(uri, EntryParser.formatDiaperEntry(withId))
    }

    suspend fun appendActivityEntry(uri: Uri, entry: ActivityEntry) {
        val withId = if (entry.id == null) entry.copy(id = EntryParser.generateId()) else entry
        appendLine(uri, EntryParser.formatActivityEntry(withId))
    }

    suspend fun appendFeedEntry(uri: Uri, entry: FeedEntry) {
        val withId = if (entry.id == null) entry.copy(id = EntryParser.generateId()) else entry
        appendLine(uri, EntryParser.formatFeedEntry(withId))
    }

    suspend fun appendBottleFeedEntry(uri: Uri, entry: BottleFeedEntry) {
        val withId = if (entry.id == null) entry.copy(id = EntryParser.generateId()) else entry
        appendLine(uri, EntryParser.formatBottleFeedEntry(withId))
    }

    suspend fun appendWhiteNoiseEntry(uri: Uri, entry: WhiteNoiseEntry) {
        val withId = if (entry.id == null) entry.copy(id = EntryParser.generateId()) else entry
        appendLine(uri, EntryParser.formatWhiteNoiseEntry(withId))
    }

    suspend fun appendHighContrastEntry(uri: Uri, entry: HighContrastEntry) {
        val withId = if (entry.id == null) entry.copy(id = EntryParser.generateId()) else entry
        appendLine(uri, EntryParser.formatHighContrastEntry(withId))
    }

    suspend fun saveBabyInfo(uri: Uri, name: String, birthDate: LocalDate, sex: com.akocis.babysleeptracker.model.BabySex? = null) {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val content = readContent(uri)
                val lines = content.lines().toMutableList()
                val babyLineIndex = lines.indexOfFirst {
                    EntryParser.stripId(it.trim()).startsWith("BABY ")
                }
                if (babyLineIndex >= 0) {
                    val existingId = EntryParser.extractId(lines[babyLineIndex])
                    val newLine = EntryParser.formatBabyInfo(name, birthDate, sex, existingId)
                    lines[babyLineIndex] = newLine
                } else {
                    val newLine = EntryParser.formatBabyInfo(name, birthDate, sex)
                    lines.add(0, newLine)
                }
                writeContent(uri, lines.joinToString("\n"))
            }
        }
    }

    suspend fun appendMeasurementEntry(uri: Uri, entry: com.akocis.babysleeptracker.model.MeasurementEntry) {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val content = readContent(uri)
                val newLine = EntryParser.formatMeasurementEntry(entry)
                writeContent(uri, if (content.endsWith("\n") || content.isEmpty()) "$content$newLine\n" else "$content\n$newLine\n")
            }
        }
    }

    suspend fun deleteEntry(uri: Uri, lineToRemove: String) {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val content = readContent(uri)
                val lines = content.lines().toMutableList()
                val strippedToRemove = EntryParser.stripId(lineToRemove)
                val index = lines.indexOfFirst {
                    EntryParser.stripId(it) == strippedToRemove
                }
                if (index >= 0) {
                    val id = EntryParser.extractId(lines[index])
                    lines.removeAt(index)
                    if (id != null) {
                        lines.add(EntryParser.formatDeletion(id))
                    }
                    writeContent(uri, lines.joinToString("\n"))
                }
            }
        }
    }

    suspend fun replaceById(uri: Uri, entryId: String, newLine: String) {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val content = readContent(uri)
                val lines = content.lines().toMutableList()
                val firstIndex = lines.indexOfFirst {
                    EntryParser.extractId(it) == entryId
                }

                // Remove ALL lines with this ID
                lines.removeAll { EntryParser.extractId(it) == entryId }

                // Insert the new line at the original position (or append)
                val insertAt = if (firstIndex >= 0) firstIndex.coerceAtMost(lines.size) else lines.size
                val modEpoch = System.currentTimeMillis() / 1000
                lines.add(insertAt, "${EntryParser.formatIdPrefix(entryId, modEpoch)} ${EntryParser.stripId(newLine)}")

                writeContent(uri, lines.joinToString("\n"))
            }
        }
    }

    suspend fun updateEntry(uri: Uri, oldLine: String, newLine: String) {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val content = readContent(uri)
                val lines = content.lines().toMutableList()
                val strippedOld = EntryParser.stripId(oldLine)
                val index = lines.indexOfFirst {
                    EntryParser.stripId(it) == strippedOld
                }
                if (index >= 0) {
                    val existingId = EntryParser.extractId(lines[index])
                    val strippedNew = EntryParser.stripId(newLine)
                    lines[index] = if (existingId != null) {
                        val modEpoch = System.currentTimeMillis() / 1000
                        "${EntryParser.formatIdPrefix(existingId, modEpoch)} $strippedNew"
                    } else {
                        strippedNew
                    }
                    writeContent(uri, lines.joinToString("\n"))
                }
            }
        }
    }

    suspend fun deleteLastLine(uri: Uri) {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val content = readContent(uri)
                val lines = content.lines().toMutableList()
                while (lines.isNotEmpty() && lines.last().isBlank()) {
                    lines.removeAt(lines.lastIndex)
                }
                if (lines.isNotEmpty()) {
                    lines.removeAt(lines.lastIndex)
                }
                writeContent(uri, lines.joinToString("\n"))
            }
        }
    }

    suspend fun removeDeletionTombstone(uri: Uri, entryId: String) {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val content = readContent(uri)
                val lines = content.lines().toMutableList()
                val tombstone = EntryParser.formatDeletion(entryId)
                val index = lines.indexOfFirst { it.trim() == tombstone }
                if (index >= 0) {
                    lines.removeAt(index)
                    writeContent(uri, lines.joinToString("\n"))
                }
            }
        }
    }

    suspend fun importFrom(targetUri: Uri, sourceUri: Uri): Int {
        val sourceData = withContext(Dispatchers.IO) {
            try {
                val content = readContent(sourceUri)
                EntryParser.parseAll(content)
            } catch (_: Exception) {
                ParsedData()
            }
        }

        var count = 0
        sourceData.sleepEntries.forEach {
            appendSleepEntry(targetUri, it)
            count++
        }
        sourceData.diaperEntries.forEach {
            appendDiaperEntry(targetUri, it)
            count++
        }
        sourceData.activityEntries.forEach {
            appendActivityEntry(targetUri, it)
            count++
        }
        sourceData.feedEntries.forEach {
            appendFeedEntry(targetUri, it)
            count++
        }
        sourceData.bottleFeedEntries.forEach {
            appendBottleFeedEntry(targetUri, it)
            count++
        }
        sourceData.whiteNoiseEntries.forEach {
            appendWhiteNoiseEntry(targetUri, it)
            count++
        }
        return count
    }

    suspend fun readRawContent(uri: Uri): String =
        withContext(Dispatchers.IO) { readContent(uri) }

    suspend fun writeRawContent(uri: Uri, content: String) {
        mutex.withLock {
            withContext(Dispatchers.IO) { writeContent(uri, content) }
        }
    }

    suspend fun readMergeWrite(uri: Uri, transform: (String) -> String): String {
        return mutex.withLock {
            withContext(Dispatchers.IO) {
                val content = readContent(uri)
                val result = transform(content)
                writeContent(uri, result)
                result
            }
        }
    }

    private fun readContent(uri: Uri): String {
        return try {
            context.contentResolver.openInputStream(uri)?.use {
                it.bufferedReader().readText()
            } ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun writeContent(uri: Uri, content: String) {
        val stream = context.contentResolver.openOutputStream(uri, "wt")
            ?: throw IllegalStateException("Cannot open file for writing")
        stream.use { it.write(content.toByteArray()) }
    }

    private suspend fun appendLine(uri: Uri, line: String) {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val existing = readContent(uri)
                val newContent = if (existing.isBlank()) {
                    line
                } else if (existing.endsWith("\n")) {
                    existing + line
                } else {
                    existing + "\n" + line
                }
                writeContent(uri, newContent)
            }
        }
    }
}
