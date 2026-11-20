package com.myapp.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.myapp.app.ui.popup.AppDialogs
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * "Check for update" + in-app download/install: downloads the small version.json that build-apk.yml attaches to the
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

    /** true while the new APK is being downloaded (shows the progress popup). */
    var downloading by mutableStateOf(false)
    /** 0..100, or -1 when the server did not tell the file size. */
    var percent by mutableStateOf(0)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    /** Version name of the installed app, e.g. "2.0.137". */
    fun installedName(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
    } catch (_: Exception) {
        ""
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

    // ---------------------------------------------------------------------------------------
    // Download inside the app (progress popup) and start the installer when finished
    // ---------------------------------------------------------------------------------------

    fun startUpdate(context: Context, apkUrl: String) {
        if (downloading) return
        val app = context.applicationContext
        if (Build.VERSION.SDK_INT < 24) {
            // Very old Android cannot hand a private file to the installer: use the browser instead.
            try {
                app.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Exception) {
            }
            return
        }
        if (Build.VERSION.SDK_INT >= 26 && !app.packageManager.canRequestPackageInstalls()) {
            AppDialogs.confirm(
                message = tr("update_permission_msg", "Allow this app to install updates, then tap Update again."),
                title = tr("update_permission_title", "Allow installation"),
                okLabel = tr("update_open_settings", "Settings"),
                cancelLabel = tr("common_cancel", "Cancel"),
                icon = "fa-circle-info",
            ) { ok ->
                if (ok == true) {
                    try {
                        app.startActivity(
                            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + app.packageName))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    } catch (_: Exception) {
                    }
                }
            }
            return
        }
        downloading = true
        percent = 0
        job = scope.launch {
            try {
                val file = download(app, apkUrl)
                percent = 100
                install(app, file)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppDialogs.alert(
                    message = tr("update_failed_msg", "Check your internet connection and try again.") +
                        "\n(" + (e.message ?: e.javaClass.simpleName) + ")",
                    title = tr("update_download_failed_title", "Could not download the update"),
                )
            } finally {
                downloading = false
            }
        }
    }

    fun cancelDownload() {
        job?.cancel()
    }

    private suspend fun download(context: Context, url: String): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "Tracker.apk")
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                useCaches = false
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Tracker-App")
            }
            val code = conn.responseCode
            if (code != 200) throw IOException("HTTP $code")
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(32 * 1024)
                    var read = 0L
                    var last = -2
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        ensureActive()
                        out.write(buf, 0, n)
                        read += n
                        val p = if (total > 0) (read * 100 / total).toInt().coerceIn(0, 100) else -1
                        if (p != last) {
                            last = p
                            percent = p
                        }
                    }
                }
            }
            if (total > 0 && file.length() != total) throw IOException("incomplete download")
            file
        } catch (e: Exception) {
            file.delete()
            throw e
        } finally {
            conn?.disconnect()
        }
    }

    private fun install(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
