package com.akocis.babysleeptracker.repository

import android.content.Context
import android.net.Uri
import com.akocis.babysleeptracker.model.ActivityEntry
import com.akocis.babysleeptracker.model.DiaperEntry
import com.akocis.babysleeptracker.model.SleepEntry
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
        appendLine(uri, EntryParser.formatSleepEntry(entry))
    }

    suspend fun appendDiaperEntry(uri: Uri, entry: DiaperEntry) {
        appendLine(uri, EntryParser.formatDiaperEntry(entry))
    }

    suspend fun appendActivityEntry(uri: Uri, entry: ActivityEntry) {
        appendLine(uri, EntryParser.formatActivityEntry(entry))
    }

    suspend fun saveBabyInfo(uri: Uri, name: String, birthDate: LocalDate) {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val content = readContent(uri)
                val lines = content.lines().toMutableList()
                val babyLineIndex = lines.indexOfFirst { it.trim().startsWith("BABY ") }
                val newLine = EntryParser.formatBabyInfo(name, birthDate)
                if (babyLineIndex >= 0) {
                    lines[babyLineIndex] = newLine
                } else {
                    lines.add(0, newLine)
                }
                val newContent = lines.joinToString("\n")
                context.contentResolver.openOutputStream(uri, "wt")?.use {
                    it.write(newContent.toByteArray())
                }
            }
        }
    }

    suspend fun deleteEntry(uri: Uri, lineToRemove: String) {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val content = readContent(uri)

                val lines = content.lines().toMutableList()
                val index = lines.indexOfFirst { it.trim() == lineToRemove.trim() }
                if (index >= 0) {
                    lines.removeAt(index)
                    val newContent = lines.joinToString("\n")
                    context.contentResolver.openOutputStream(uri, "wt")?.use {
                        it.write(newContent.toByteArray())
                    }
                }
            }
        }
    }

    suspend fun updateEntry(uri: Uri, oldLine: String, newLine: String) {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val content = readContent(uri)

                val lines = content.lines().toMutableList()
                val index = lines.indexOfFirst { it.trim() == oldLine.trim() }
                if (index >= 0) {
                    lines[index] = newLine
                    val newContent = lines.joinToString("\n")
                    context.contentResolver.openOutputStream(uri, "wt")?.use {
                        it.write(newContent.toByteArray())
                    }
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
                val newContent = lines.joinToString("\n")
                context.contentResolver.openOutputStream(uri, "wt")?.use {
                    it.write(newContent.toByteArray())
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
        return count
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

                context.contentResolver.openOutputStream(uri, "wt")?.use {
                    it.write(newContent.toByteArray())
                }
            }
        }
    }
}
