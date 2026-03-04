package com.akocis.babysleeptracker.repository

import android.content.Context
import android.net.Uri
import com.akocis.babysleeptracker.model.DiaperEntry
import com.akocis.babysleeptracker.model.SleepEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class FileRepository(private val context: Context) {

    private val mutex = Mutex()

    suspend fun readAll(uri: Uri): Pair<List<SleepEntry>, List<DiaperEntry>> =
        withContext(Dispatchers.IO) {
            try {
                val content = context.contentResolver.openInputStream(uri)?.use {
                    it.bufferedReader().readText()
                } ?: ""
                EntryParser.parseAll(content)
            } catch (_: Exception) {
                emptyList<SleepEntry>() to emptyList<DiaperEntry>()
            }
        }

    suspend fun appendSleepEntry(uri: Uri, entry: SleepEntry) {
        appendLine(uri, EntryParser.formatSleepEntry(entry))
    }

    suspend fun appendDiaperEntry(uri: Uri, entry: DiaperEntry) {
        appendLine(uri, EntryParser.formatDiaperEntry(entry))
    }

    suspend fun deleteEntry(uri: Uri, lineToRemove: String) {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val content = context.contentResolver.openInputStream(uri)?.use {
                    it.bufferedReader().readText()
                } ?: return@withContext

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

    suspend fun importFrom(targetUri: Uri, sourceUri: Uri): Int {
        val (sourceSleep, sourceDiaper) = withContext(Dispatchers.IO) {
            try {
                val content = context.contentResolver.openInputStream(sourceUri)?.use {
                    it.bufferedReader().readText()
                } ?: ""
                EntryParser.parseAll(content)
            } catch (_: Exception) {
                emptyList<SleepEntry>() to emptyList<DiaperEntry>()
            }
        }

        var count = 0
        sourceSleep.forEach {
            appendSleepEntry(targetUri, it)
            count++
        }
        sourceDiaper.forEach {
            appendDiaperEntry(targetUri, it)
            count++
        }
        return count
    }

    private suspend fun appendLine(uri: Uri, line: String) {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val existing = try {
                    context.contentResolver.openInputStream(uri)?.use {
                        it.bufferedReader().readText()
                    } ?: ""
                } catch (_: Exception) {
                    ""
                }

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
