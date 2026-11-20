package com.myapp.app.util

import android.content.Context
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * "Check for update": downloads the small version.json that build-apk.yml attaches to the
 * "latest" release and compares its versionCode with the installed one.
 * (Plain github.com download links are used, NOT api.github.com, because the API refuses
 * anonymous phones with HTTP 403 once the shared IP address hits its hourly limit.)
 */
object UpdateChecker {
    private const val BASE = "https://github.com/darkrider13/Tracker-V2/releases/download/latest/"
    private const val VERSION_URL = BASE + "version.json"
    private const val APK_URL = BASE + "Tracker.apk"

    /** true after a check found a newer version (shows the green dot on the update icon). */
    var updateAvailable by mutableStateOf(false)
    var checking by mutableStateOf(false)

    sealed class Result {
        object UpToDate : Result()
        class Available(val versionName: String?, val url: String) : Result()
        class Failed(val reason: String) : Result()
    }

    @Suppress("DEPRECATION")
    private fun installedCode(context: Context): Long {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    }

    suspend fun check(context: Context): Result = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(VERSION_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                useCaches = false
                instanceFollowRedirects = true
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("User-Agent", "Tracker-App")
            }
            val code = conn.responseCode
            if (code != 200) return@withContext Result.Failed("HTTP $code")
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val remoteCode = json.optLong("versionCode", -1L)
            if (remoteCode < 0) return@withContext Result.Failed("bad version.json")
            val remoteName = json.optString("versionName").ifEmpty { null }

            if (remoteCode <= installedCode(context)) {
                updateAvailable = false
                return@withContext Result.UpToDate
            }
            updateAvailable = true
            Result.Available(remoteName, APK_URL)
        } catch (e: Exception) {
            Result.Failed(e.javaClass.simpleName)
        } finally {
            conn?.disconnect()
        }
    }
}
