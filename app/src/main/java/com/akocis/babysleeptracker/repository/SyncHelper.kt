package com.akocis.babysleeptracker.repository

import android.util.Log
import com.akocis.babysleeptracker.model.TrackingState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

object SyncHelper {

    private const val TAG = "SyncHelper"
    private const val DEBOUNCE_MS = 2000L
    private const val PERIODIC_INTERVAL_MS = 5 * 60 * 1000L

    private var scope: CoroutineScope? = null
    private var prefsRepo: PreferencesRepository? = null
    private var fileRepo: FileRepository? = null
    private val syncManager = DropboxSyncManager()
    private val syncMutex = Mutex()

    private var debounceJob: Job? = null
    private var periodicJob: Job? = null

    fun init(scope: CoroutineScope, prefsRepo: PreferencesRepository, fileRepo: FileRepository) {
        this.scope = scope
        this.prefsRepo = prefsRepo
        this.fileRepo = fileRepo
        startPeriodicSync()
    }

    fun notifyDataChanged() {
        val s = scope ?: return
        val p = prefsRepo ?: return
        val f = fileRepo ?: return
        if (!p.isDropboxConfigured) return

        debounceJob?.cancel()
        debounceJob = s.launch {
            delay(DEBOUNCE_MS)
            doSync(f, p)
        }
    }

    suspend fun pullLatest() {
        val p = prefsRepo ?: return
        val f = fileRepo ?: return
        if (!p.isDropboxConfigured) return
        doSync(f, p)
    }

    private fun startPeriodicSync() {
        periodicJob?.cancel()
        periodicJob = scope?.launch {
            while (true) {
                delay(PERIODIC_INTERVAL_MS)
                val p = prefsRepo ?: continue
                val f = fileRepo ?: continue
                if (!p.isDropboxConfigured) continue
                if (p.fileUri == null) continue
                doSync(f, p)
            }
        }
    }

    private suspend fun doSync(fileRepo: FileRepository, prefs: PreferencesRepository) {
        if (!syncMutex.tryLock()) return
        try {
            val uri = prefs.fileUri ?: return
            val accessToken = getValidAccessToken(prefs)
            val filePath = prefs.dropboxFilePath

            val remoteContent = syncManager.downloadFile(accessToken, filePath)
            val localContent = fileRepo.readRawContent(uri)
            val merged = syncManager.mergeContent(localContent, remoteContent)

            syncManager.uploadFile(accessToken, merged, filePath)
            fileRepo.writeRawContent(uri, merged)

            val parsed = EntryParser.parseAll(merged)
            if (parsed.babyName != null) prefs.babyName = parsed.babyName
            if (parsed.babyBirthDate != null) prefs.babyBirthDate = parsed.babyBirthDate
            val ongoing = parsed.sleepEntries.find { it.isOngoing }
            if (ongoing != null) {
                prefs.saveTrackingState(
                    TrackingState.Sleeping(ongoing.date, ongoing.startTime)
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "sync failed", e)
        } finally {
            syncMutex.unlock()
        }
    }

    private suspend fun getValidAccessToken(prefs: PreferencesRepository): String {
        val appKey = prefs.dropboxAppKey
            ?: throw IllegalStateException("No app key configured")
        val refreshToken = prefs.dropboxRefreshToken
            ?: throw IllegalStateException("No refresh token configured")

        val expiry = prefs.dropboxTokenExpiry
        val currentToken = prefs.dropboxAccessToken

        if (currentToken.isNullOrBlank() || System.currentTimeMillis() > expiry - 60_000) {
            val result = syncManager.refreshAccessToken(appKey, refreshToken)
            prefs.dropboxAccessToken = result.accessToken
            prefs.dropboxTokenExpiry =
                System.currentTimeMillis() + (result.expiresIn * 1000)
            return result.accessToken
        }
        return currentToken
    }
}
