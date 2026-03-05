package com.akocis.babysleeptracker.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.akocis.babysleeptracker.repository.DropboxSyncManager
import com.akocis.babysleeptracker.repository.FileRepository
import com.akocis.babysleeptracker.repository.PreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SyncViewModel(application: Application) : AndroidViewModel(application) {

    private val prefsRepository = PreferencesRepository(application)
    private val fileRepository = FileRepository(application)
    private val syncManager = DropboxSyncManager()

    private val _isConnected = MutableStateFlow(prefsRepository.isDropboxConfigured)
    val isConnected: StateFlow<Boolean> = _isConnected

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    private val _shareCode = MutableStateFlow<String?>(null)
    val shareCode: StateFlow<String?> = _shareCode

    fun dismissMessage() {
        _message.value = null
    }

    fun getAuthorizeUrl(appKey: String): String {
        return syncManager.getAuthorizeUrl(appKey)
    }

    fun exchangeCode(appKey: String, authCode: String) {
        viewModelScope.launch {
            try {
                val result = syncManager.exchangeCode(appKey, authCode.trim())
                prefsRepository.dropboxAppKey = appKey
                prefsRepository.dropboxRefreshToken = result.refreshToken
                prefsRepository.dropboxAccessToken = result.accessToken
                prefsRepository.dropboxTokenExpiry =
                    System.currentTimeMillis() + (result.expiresIn * 1000)
                _isConnected.value = true
                _message.value = "Connected to Dropbox"
            } catch (e: Exception) {
                Log.e(TAG, "exchangeCode failed", e)
                _message.value = "Connection failed: ${e.message}"
            }
        }
    }

    fun sync() {
        val uri = prefsRepository.fileUri ?: run {
            _message.value = "No local file selected — set one in Settings first"
            return
        }
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                val accessToken = getValidAccessToken()
                val filePath = prefsRepository.dropboxFilePath

                val remoteContent = syncManager.downloadFile(accessToken, filePath)
                val localContent = fileRepository.readRawContent(uri)
                val merged = syncManager.mergeContent(localContent, remoteContent)

                syncManager.uploadFile(accessToken, merged, filePath)
                fileRepository.writeRawContent(uri, merged)

                _message.value = "Sync complete"
            } catch (e: Exception) {
                Log.e(TAG, "sync failed", e)
                _message.value = "Sync failed: ${e.message}"
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun generateShareCode() {
        val appKey = prefsRepository.dropboxAppKey
        val refreshToken = prefsRepository.dropboxRefreshToken
        if (appKey.isNullOrBlank() || refreshToken.isNullOrBlank()) {
            _message.value = "Not connected to Dropbox"
            return
        }
        _shareCode.value = syncManager.encodeShareCode(
            appKey, refreshToken, prefsRepository.dropboxFilePath
        )
    }

    fun joinWithCode(code: String) {
        viewModelScope.launch {
            try {
                val data = syncManager.decodeShareCode(code)
                // Refresh to get a fresh access token
                val result = syncManager.refreshAccessToken(data.appKey, data.refreshToken)

                prefsRepository.dropboxAppKey = data.appKey
                prefsRepository.dropboxRefreshToken = data.refreshToken
                prefsRepository.dropboxAccessToken = result.accessToken
                prefsRepository.dropboxTokenExpiry =
                    System.currentTimeMillis() + (result.expiresIn * 1000)
                prefsRepository.dropboxFilePath = data.filePath

                _isConnected.value = true
                _message.value = "Joined successfully — tap Sync Now"
            } catch (e: Exception) {
                Log.e(TAG, "joinWithCode failed", e)
                _message.value = "Invalid share code: ${e.message}"
            }
        }
    }

    fun disconnect() {
        prefsRepository.clearDropbox()
        _isConnected.value = false
        _shareCode.value = null
        _message.value = "Disconnected from Dropbox"
    }

    companion object {
        private const val TAG = "BabySync"
    }

    private suspend fun getValidAccessToken(): String {
        val appKey = prefsRepository.dropboxAppKey
            ?: throw IllegalStateException("No app key configured")
        val refreshToken = prefsRepository.dropboxRefreshToken
            ?: throw IllegalStateException("No refresh token configured")

        val expiry = prefsRepository.dropboxTokenExpiry
        val currentToken = prefsRepository.dropboxAccessToken

        // Refresh if token is missing or expires within 60 seconds
        if (currentToken.isNullOrBlank() || System.currentTimeMillis() > expiry - 60_000) {
            val result = syncManager.refreshAccessToken(appKey, refreshToken)
            prefsRepository.dropboxAccessToken = result.accessToken
            prefsRepository.dropboxTokenExpiry =
                System.currentTimeMillis() + (result.expiresIn * 1000)
            return result.accessToken
        }
        return currentToken
    }
}
