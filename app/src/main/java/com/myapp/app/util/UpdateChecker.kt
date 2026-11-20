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
 * "Check for update": asks GitHub for the newest release of this repo and compares its
 * versionCode (written into the release notes by build-apk.yml) with the installed one.
 */
object UpdateChecker {
    private const val RELEASE_API = "https://api.github.com/repos/darkrider13/Tracker-V2/releases/latest"

    /** true after a check found a newer version (shows the green dot on the update icon). */
    var updateAvailable by mutableStateOf(false)
    var checking by mutableStateOf(false)

    sealed class Result {
        object UpToDate : Result()
        class Available(val versionName: String?, val url: String) : Result()
        object Failed : Result()
    }

    @Suppress("DEPRECATION")
    private fun installedCode(context: Context): Long {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    }

    suspend fun check(context: Context): Result = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(RELEASE_API).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Tracker-App")
            }
            if (conn.responseCode != 200) return@withContext Result.Failed
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val body = json.optString("body")
            val remoteCode = Regex("versionCode:\\s*(\\d+)").find(body)?.groupValues?.get(1)?.toLongOrNull()
                ?: return@withContext Result.Failed
            val remoteName = Regex("versionName:\\s*([\\w.\\-]+)").find(body)?.groupValues?.get(1)

            if (remoteCode <= installedCode(context)) {
                updateAvailable = false
                return@withContext Result.UpToDate
            }
            var url = json.optString("html_url")
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                        url = a.optString("browser_download_url")
                        break
                    }
                }
            }
            updateAvailable = true
            Result.Available(remoteName, url)
        } catch (_: Exception) {
            Result.Failed
        } finally {
            conn?.disconnect()
        }
    }
}
