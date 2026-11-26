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
import kotlinx.coroutines.withTimeoutOrNull
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
    private const val BASE = "https://github.com/darkrider13/Tracker/releases/download/latest/"
    private const val VERSION_URL = BASE + "version.json"
    private const val APK_URL = BASE + "Tracker.apk"

    /** true after a check found a newer version (shows the green dot on the update icon). */
    var updateAvailable by mutableStateOf(false)
    var checking by mutableStateOf(false)

    /** Start-up check result waiting to be shown as a popup (set when a newer version was found in the first 5 s). */
    var startupFound by mutableStateOf<Result.Available?>(null)

    /** The automatic check only runs inside this window after the app is opened; later it never checks by itself. */
    private const val STARTUP_WINDOW_MS = 5_000L

    sealed class Result {
        object UpToDate : Result()
        class Available(val versionName: String?, val url: String) : Result()
        class Failed(val reason: String) : Result()
    }

    /** true while the new APK is being downloaded (shows the progress popup). */
    var downloading by mutableStateOf(false)
    /** 0..100, or -1 when the server did not tell the file size. */
    var percent by mutableStateOf(0)
    /** Bytes received so far, the file size (-1 = unknown) and the current speed in bytes/second. */
    var downloadedBytes by mutableStateOf(0L)
    var totalBytes by mutableStateOf(-1L)
    var speedBps by mutableStateOf(0L)

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

    /**
     * Automatic check right after the app is opened. It must finish within the first [STARTUP_WINDOW_MS]; if the
     * phone is offline / too slow the check is dropped silently (no popup, no dot) and nothing is checked again
     * until the next time the app is opened. The popup itself is shown by TrackerRoot once the lock / welcome
     * screen is gone.
     */
    fun startupCheck(context: Context) {
        val app = context.applicationContext
        startupFound = null
        scope.launch {
            val r = withTimeoutOrNull(STARTUP_WINDOW_MS) { check(app, 4_000) }
            if (r is Result.Available) startupFound = r
        }
    }

    /** The "new version" popup: Download starts the in-app download, Cancel leaves the green dot on the update icon. */
    fun showAvailable(context: Context, result: Result.Available) {
        val current = installedName(context)
        AppDialogs.confirm(
            message = tr("update_found_msg", "A new version is available.") +
                "\n" + tr("update_current_label", "Current version") + ": " + current +
                (result.versionName?.let { "\n" + tr("update_new_label", "New version") + ": " + it } ?: ""),
            title = tr("update_found_title", "Update available"),
            okLabel = tr("update_download", "Download"),
            cancelLabel = tr("common_cancel", "Cancel"),
            icon = "fa-circle-check",
            softBackdrop = true,
        ) { ok -> if (ok == true) startUpdate(context, result.url) }
    }

    suspend fun check(context: Context, timeoutMs: Int = 10_000): Result = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(VERSION_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
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

            ensureActive()   // a start-up check that ran out of its 5 s window must not change anything
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
        downloadedBytes = 0L
        totalBytes = -1L
        speedBps = 0L
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
            totalBytes = total
            conn.inputStream.use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(32 * 1024)
                    var read = 0L
                    var last = -2
                    var tickT = System.nanoTime()
                    var tickBytes = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        ensureActive()
                        out.write(buf, 0, n)
                        read += n
                        val now = System.nanoTime()
                        if (now - tickT >= 400_000_000L) {
                            val inst = (read - tickBytes) * 1_000_000_000L / (now - tickT)
                            speedBps = if (speedBps == 0L) inst else (speedBps * 0.6 + inst * 0.4).toLong()
                            downloadedBytes = read
                            tickT = now
                            tickBytes = read
                        }
                        val p = if (total > 0) (read * 100 / total).toInt().coerceIn(0, 100) else -1
                        if (p != last) {
                            last = p
                            percent = p
                        }
                    }
                    downloadedBytes = read
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
