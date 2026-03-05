package com.akocis.babysleeptracker.repository

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom

class DropboxSyncManager {

    companion object {
        private const val AUTHORIZE_URL = "https://www.dropbox.com/oauth2/authorize"
        private const val TOKEN_URL = "https://api.dropboxapi.com/oauth2/token"
        private const val UPLOAD_URL = "https://content.dropboxapi.com/2/files/upload"
        private const val DOWNLOAD_URL = "https://content.dropboxapi.com/2/files/download"
        private const val DEFAULT_FILE_PATH = "/baby_sleep_log.txt"

        private val DATE_TIME_REGEX = Regex(
            """(\d{4}-\d{2}-\d{2})\s+(\d{2}:\d{2})"""
        )
        private val ONGOING_SLEEP_REGEX = Regex(
            """^SLEEP\s+(\d{4}-\d{2}-\d{2})\s+(\d{2}:\d{2})\s*-\s*$"""
        )
        private val COMPLETED_SLEEP_REGEX = Regex(
            """^SLEEP\s+(\d{4}-\d{2}-\d{2})\s+(\d{2}:\d{2})\s*-\s*(\d{2}:\d{2})$"""
        )
        private val BABY_REGEX = Regex("""^BABY\s+.+""")
    }

    private var codeVerifier: String? = null

    fun generateCodeVerifier(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        val verifier = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        codeVerifier = verifier
        return verifier
    }

    private fun generateCodeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    fun getAuthorizeUrl(appKey: String): String {
        val verifier = generateCodeVerifier()
        val challenge = generateCodeChallenge(verifier)
        return "$AUTHORIZE_URL?client_id=$appKey" +
            "&response_type=code" +
            "&code_challenge=$challenge" +
            "&code_challenge_method=S256" +
            "&token_access_type=offline"
    }

    data class TokenResult(
        val accessToken: String,
        val refreshToken: String,
        val expiresIn: Long
    )

    suspend fun exchangeCode(appKey: String, authCode: String): TokenResult =
        withContext(Dispatchers.IO) {
            val verifier = codeVerifier
                ?: throw IllegalStateException("No code verifier — call getAuthorizeUrl first")

            val params = "code=$authCode" +
                "&grant_type=authorization_code" +
                "&client_id=$appKey" +
                "&code_verifier=$verifier"

            val json = postForm(TOKEN_URL, params)
            TokenResult(
                accessToken = json.getString("access_token"),
                refreshToken = json.getString("refresh_token"),
                expiresIn = json.getLong("expires_in")
            )
        }

    suspend fun refreshAccessToken(appKey: String, refreshToken: String): TokenResult =
        withContext(Dispatchers.IO) {
            val params = "grant_type=refresh_token" +
                "&refresh_token=$refreshToken" +
                "&client_id=$appKey"

            val json = postForm(TOKEN_URL, params)
            TokenResult(
                accessToken = json.getString("access_token"),
                refreshToken = refreshToken,
                expiresIn = json.getLong("expires_in")
            )
        }

    suspend fun downloadFile(accessToken: String, path: String = DEFAULT_FILE_PATH): String? =
        withContext(Dispatchers.IO) {
            val conn = URL(DOWNLOAD_URL).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.setRequestProperty("Authorization", "Bearer $accessToken")
                conn.setRequestProperty(
                    "Dropbox-API-Arg",
                    JSONObject().put("path", path).toString()
                )
                conn.doInput = true

                if (conn.responseCode == 200) {
                    conn.inputStream.bufferedReader().readText()
                } else {
                    val error = conn.errorStream?.bufferedReader()?.readText() ?: ""
                    if (conn.responseCode == 409 || error.contains("not_found")) {
                        // File not found — OK for first sync
                        null
                    } else {
                        throw Exception("Download failed (${conn.responseCode}): $error")
                    }
                }
            } finally {
                conn.disconnect()
            }
        }

    suspend fun uploadFile(
        accessToken: String,
        content: String,
        path: String = DEFAULT_FILE_PATH
    ): Unit = withContext(Dispatchers.IO) {
        val conn = URL(UPLOAD_URL).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $accessToken")
            conn.setRequestProperty("Content-Type", "application/octet-stream")
            conn.setRequestProperty(
                "Dropbox-API-Arg",
                JSONObject()
                    .put("path", path)
                    .put("mode", "overwrite")
                    .put("autorename", false)
                    .put("mute", false)
                    .toString()
            )
            conn.doOutput = true

            conn.outputStream.use { it.write(content.toByteArray(Charsets.UTF_8)) }

            if (conn.responseCode !in 200..299) {
                val error = conn.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                throw Exception("Upload failed (${conn.responseCode}): $error")
            }
        } finally {
            conn.disconnect()
        }
    }

    fun mergeContent(local: String, remote: String?): String {
        if (remote.isNullOrBlank()) return local
        if (local.isBlank()) return remote

        val localLines = local.lines().map { it.trim() }.filter { it.isNotBlank() }.toSet()
        val remoteLines = remote.lines().map { it.trim() }.filter { it.isNotBlank() }.toSet()

        val allLines = (localLines + remoteLines).toMutableSet()

        // Extract BABY header — keep only one
        val babyLine = allLines.firstOrNull { BABY_REGEX.matches(it) }
        allLines.removeAll { BABY_REGEX.matches(it) }

        // Resolve ongoing vs completed sleep: if both exist, keep completed
        val ongoingSleeps = allLines.filter { ONGOING_SLEEP_REGEX.matches(it) }
        for (ongoing in ongoingSleeps) {
            val match = ONGOING_SLEEP_REGEX.matchEntire(ongoing) ?: continue
            val date = match.groupValues[1]
            val startTime = match.groupValues[2]
            // Check if a completed version exists
            val hasCompleted = allLines.any { line ->
                val cm = COMPLETED_SLEEP_REGEX.matchEntire(line) ?: return@any false
                cm.groupValues[1] == date && cm.groupValues[2] == startTime
            }
            if (hasCompleted) {
                allLines.remove(ongoing)
            }
        }

        // Sort entries by date + time
        val sorted = allLines.sortedWith(compareBy { line ->
            val m = DATE_TIME_REGEX.find(line)
            m?.groupValues?.get(1).orEmpty() + " " + m?.groupValues?.get(2).orEmpty()
        })

        val result = mutableListOf<String>()
        if (babyLine != null) result.add(babyLine)
        result.addAll(sorted)
        return result.joinToString("\n")
    }

    fun encodeShareCode(appKey: String, refreshToken: String, filePath: String = DEFAULT_FILE_PATH): String {
        val json = JSONObject()
            .put("k", appKey)
            .put("t", refreshToken)
            .put("p", filePath)
        return Base64.encodeToString(json.toString().toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    data class ShareCodeData(
        val appKey: String,
        val refreshToken: String,
        val filePath: String
    )

    fun decodeShareCode(code: String): ShareCodeData {
        val json = JSONObject(String(Base64.decode(code.trim(), Base64.NO_WRAP), Charsets.UTF_8))
        return ShareCodeData(
            appKey = json.getString("k"),
            refreshToken = json.getString("t"),
            filePath = json.optString("p", DEFAULT_FILE_PATH)
        )
    }

    private fun postForm(url: String, params: String): JSONObject {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.doOutput = true

            OutputStreamWriter(conn.outputStream).use { it.write(params) }

            if (conn.responseCode in 200..299) {
                val body = conn.inputStream.bufferedReader().readText()
                return JSONObject(body)
            } else {
                val error = conn.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                throw Exception("Token request failed (${conn.responseCode}): $error")
            }
        } finally {
            conn.disconnect()
        }
    }
}
