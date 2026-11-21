package com.myapp.app.ui.popup

import android.content.ContentValues
import android.content.Context
import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.myapp.app.data.AppSession
import com.myapp.app.data.AppStore
import com.myapp.app.data.Backup
import com.myapp.app.data.ImportError
import com.myapp.app.data.ImportResult
import com.myapp.app.data.ApplyErrorKind
import com.myapp.app.data.ReminderSettings
import com.myapp.app.ui.components.AppText
import com.myapp.app.ui.components.FaIcon
import com.myapp.app.ui.components.PagedViewport
import com.myapp.app.ui.components.appTextStyle
import com.myapp.app.ui.components.pagedItem
import com.myapp.app.ui.components.rememberPagedState
import com.myapp.app.ui.theme.AppTheme
import com.myapp.app.ui.theme.Brand
import com.myapp.app.util.BN_MONTHS_SHORT
import com.myapp.app.util.getAvatarOption
import com.myapp.app.util.AVATAR_OPTIONS
import com.myapp.app.util.getLang
import com.myapp.app.util.toBnDigits
import com.myapp.app.util.tr
import com.myapp.app.util.trf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar

object BackupUi {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var picker: ActivityResultLauncher<Array<String>>? = null
    /** Android 9 and older: saving into Documents needs the storage permission asked at run time. */
    private var storagePermission: ActivityResultLauncher<String>? = null
    private var afterStoragePermission: (() -> Unit)? = null
    private var appContext: Context? = null

    /** Message of the full-screen "please wait" card (null = hidden). */
    var busy by mutableStateOf<String?>(null)

    /** false = no "Saving / Reading / Restoring..." card is drawn. */
    const val SHOW_BUSY_CARD = false

    /** Call once from MainActivity.onCreate (the launcher must be registered before the activity starts). */
    fun attach(activity: ComponentActivity) {
        appContext = activity.applicationContext
        picker = activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) handlePicked(activity.applicationContext, uri)
        }
        storagePermission = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // granted or not, carry on: a refusal makes the save fail and the share sheet opens instead
            val next = afterStoragePermission
            afterStoragePermission = null
            next?.invoke()
        }
    }

    fun pickFile() {
        try {
            picker?.launch(arrayOf("*/*"))
        } catch (_: Exception) {
        }
    }

    private fun errorWord(t: Throwable): String = t.message?.takeIf { it.isNotBlank() } ?: t.javaClass.simpleName

    // ============================================================================ export

    /** "Tracker_21_Aug_2026_03.45_PM.json" / "ট্র্যাকার_২১_আগ._২০২৬_০৩.৪৫_পিএম.json" */
    private fun exportFileName(): String {
        val now = Calendar.getInstance()
        val day = now.get(Calendar.DAY_OF_MONTH).toString().padStart(2, '0')
        val year = now.get(Calendar.YEAR).toString()
        var h = now.get(Calendar.HOUR_OF_DAY)
        val isPm = h >= 12
        h %= 12
        if (h == 0) h = 12
        val hh = h.toString().padStart(2, '0')
        val mm = now.get(Calendar.MINUTE).toString().padStart(2, '0')
        return if (getLang() == "bn") {
            val ampm = if (isPm) tr("reminder_pm", "PM") else tr("reminder_am", "AM")
            "ট্র্যাকার_${toBnDigits(day)}_${BN_MONTHS_SHORT[now.get(Calendar.MONTH)]}_${toBnDigits(year)}_${toBnDigits(hh)}.${toBnDigits(mm)}_$ampm.json"
        } else {
            val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
            "Tracker_${day}_${months[now.get(Calendar.MONTH)]}_${year}_$hh.${mm}_${if (isPm) "PM" else "AM"}.json"
        }
    }

    fun export(context: Context) {
        val app = context.applicationContext
        val json: String
        try {
            json = Backup.buildExportJson(
                AppStore.state, ReminderSettings.load(app),
                if (AppSession.isLight) "light" else "dark", AppSession.lang,
            )
        } catch (e: Throwable) {
            AppDialogs.alert(
                trf("export_err_build", "Couldn't create the backup (${errorWord(e)}).", "reason" to errorWord(e)),
                title = tr("export_err_title", "Export failed"), icon = "fa-exclamation-triangle",
            )
            return
        }
        val fileName = exportFileName()
        AppDialogs.confirm(
            message = tr("export_choose_message", "Save the file straight into your Documents folder, or send it using your phone's share menu."),
            title = tr("export_choose_title", "Export backup"),
            okLabel = tr("export_save_file", "Save File"),
            cancelLabel = tr("export_share", "Share"),
            icon = "fa-file-export",
            showClose = true,
        ) { choice ->
            if (choice == null) return@confirm
            val saveMode = choice == true
            val go: () -> Unit = {
            busy = if (saveMode) tr("busy_saving", "Saving file...") else tr("busy_exporting", "Preparing backup...")
            scope.launch {
                var result: String? = null
                var failure: Throwable? = null
                try {
                    val bytes = withContext(Dispatchers.Default) { json.toByteArray(Charsets.UTF_8) }
                    result = withContext(Dispatchers.IO) {
                        if (saveMode) {
                            try {
                                saveToDocuments(app, fileName, bytes)
                                "documents"
                            } catch (e: Throwable) {
                                failure = e
                                // never leave a half-written file behind, then fall back to the share sheet
                                null
                            }
                        } else null
                    }
                    if (result == null) {
                        val uri = withContext(Dispatchers.IO) { writeCacheCopy(app, fileName, bytes) }
                        val armed = withContext(Dispatchers.Main) { launchShare(app, uri, fileName) }
                        // armed = the "shared" popup comes later, once the user has really picked an app
                        result = if (armed) "share_pending" else "share"
                    }
                } catch (e: Throwable) {
                    failure = failure ?: e
                    result = null
                }
                busy = null
                when (result) {
                    "share_pending" -> Unit
                    "share" -> {
                        delay(SHARE_POPUP_DELAY_MS)
                        AppDialogs.alert(
                            trf("alert_backup_shared", "Backup $fileName shared", "filename" to fileName), icon = "fa-circle-check",
                        )
                    }
                    "documents" -> AppDialogs.alert(
                        trf("alert_backup_saved_to_documents", "Backup saved to Documents/$fileName", "filename" to fileName),
                        icon = "fa-circle-check",
                    )
                    else -> {
                        val why = failure?.let { "\n\n" + trf("export_reason_line", "Reason: ${errorWord(it)}", "reason" to errorWord(it)) } ?: ""
                        AppDialogs.alert(
                            tr(
                                "alert_backup_save_failed",
                                "Couldn't save the backup file on this device. Please try again, or check that the app has storage/file permission.",
                            ) + why,
                            title = tr("export_err_title", "Export failed"), icon = "fa-exclamation-triangle",
                        )
                    }
                }
            }
            }
            val needsPermission = saveMode && Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                ContextCompat.checkSelfPermission(app, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
            val launcher = storagePermission
            if (needsPermission && launcher != null) {
                afterStoragePermission = go
                try {
                    launcher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                } catch (_: Exception) {
                    afterStoragePermission = null
                    go()
                }
            } else go()
        }
    }

    /** Writes into Documents/ (MediaStore on Android 10+, the public folder before that) and re-reads the size. */
    private fun saveToDocuments(context: Context, fileName: String, bytes: ByteArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS)
            }
            val uri = resolver.insert(MediaStore.Files.getContentUri("external"), values)
                ?: throw IllegalStateException("could not create the file")
            try {
                resolver.openOutputStream(uri)?.use { it.write(bytes); it.flush() } ?: throw IllegalStateException("could not open the file")
                val got = resolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: bytes.size.toLong()
                if (got != bytes.size.toLong()) {
                    throw IllegalStateException(
                        trf(
                            "export_err_incomplete", "The saved file is incomplete (expected ${Backup.formatBytesNice(bytes.size.toLong())}, got ${Backup.formatBytesNice(got)}).",
                            "expected" to Backup.formatBytesNice(bytes.size.toLong()), "got" to Backup.formatBytesNice(got),
                        ),
                    )
                }
            } catch (e: Throwable) {
                try { resolver.delete(uri, null, null) } catch (_: Throwable) {}
                throw e
            }
        } else {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            dir.mkdirs()
            val file = File(dir, fileName)
            try {
                file.outputStream().use { it.write(bytes); it.flush() }
                if (file.length() != bytes.size.toLong()) throw IllegalStateException("incomplete file")
            } catch (e: Throwable) {
                try { file.delete() } catch (_: Throwable) {}
                throw e
            }
        }
    }

    private fun writeCacheCopy(context: Context, fileName: String, bytes: ByteArray): Uri {
        val dir = File(context.cacheDir, "backups")
        dir.mkdirs()
        val file = File(dir, fileName)
        file.outputStream().use { it.write(bytes); it.flush() }
        return FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
    }

    /** Delay between choosing an app in the share sheet and the "Backup shared" success popup. */
    private const val SHARE_POPUP_DELAY_MS = 6000L
    private const val ACTION_SHARE_DONE = "com.myapp.app.BACKUP_SHARE_DONE"
    private var shareReceiver: BroadcastReceiver? = null

    private fun dropShareReceiver(context: Context) {
        shareReceiver?.let { try { context.unregisterReceiver(it) } catch (_: Throwable) {} }
        shareReceiver = null
    }

    /**
     * Opens the share sheet. Returns true when the "Backup ... shared" popup is armed: it is then shown only
     * after the user has picked an app in the sheet (the system reports that choice). Closing the sheet
     * without choosing shows nothing. Returns false if the choice report is not available (old behaviour).
     */
    private fun launchShare(context: Context, uri: Uri, fileName: String): Boolean {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, fileName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        var armed = false
        var chooser: Intent? = null
        try {
            dropShareReceiver(context)
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context, intent: Intent) {
                    dropShareReceiver(context)
                    // user picked an app: the success popup comes SHARE_POPUP_DELAY_MS later
                    scope.launch {
                        delay(SHARE_POPUP_DELAY_MS)
                        AppDialogs.alert(
                            trf("alert_backup_shared", "Backup $fileName shared", "filename" to fileName), icon = "fa-circle-check",
                        )
                    }
                }
            }
            ContextCompat.registerReceiver(
                context, receiver, IntentFilter(ACTION_SHARE_DONE), ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            shareReceiver = receiver
            val callback = Intent(ACTION_SHARE_DONE).setPackage(context.packageName)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            val pi = PendingIntent.getBroadcast(context, 7701, callback, flags)
            chooser = Intent.createChooser(send, fileName, pi.intentSender)
            armed = true
        } catch (_: Throwable) {
            dropShareReceiver(context)
            armed = false
        }
        context.startActivity((chooser ?: Intent.createChooser(send, fileName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return armed
    }

    // ============================================================================ import

    private fun fail(message: String) {
        AppDialogs.alert(message, title = tr("import_err_title", "Import failed"), icon = "fa-exclamation-triangle")
    }

    private fun handlePicked(context: Context, uri: Uri) {
        scope.launch {
            busy = tr("busy_reading", "Reading backup file...")
            val bytes: ByteArray
            try {
                bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        // stop one byte over the limit - parse() reports "too big" with the real size
                        val out = java.io.ByteArrayOutputStream()
                        val buf = ByteArray(64 * 1024)
                        var total = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            total += n
                            if (total > Backup.MAX_IMPORT_BYTES) break
                            out.write(buf, 0, n)
                        }
                        if (total > Backup.MAX_IMPORT_BYTES) ByteArray(Backup.MAX_IMPORT_BYTES.toInt() + 1) else out.toByteArray()
                    } ?: throw IllegalStateException("could not open the file")
                }
            } catch (oom: OutOfMemoryError) {
                busy = null
                fail(trf("import_err_memory", "Not enough free memory to open a file this big.", "size" to "?"))
                return@launch
            } catch (e: Throwable) {
                busy = null
                fail(trf("import_err_read", "The file could not be read (${errorWord(e)}).", "reason" to errorWord(e)))
                return@launch
            }

            busy = tr("busy_checking", "Checking file...")
            val result = withContext(Dispatchers.Default) {
                try { Backup.parse(bytes) } catch (oom: OutOfMemoryError) {
                    ImportResult.Failure(ImportError.MEMORY, sizeBytes = bytes.size.toLong())
                }
            }
            busy = null

            when (result) {
                is ImportResult.Failure -> showParseFailure(result)
                is ImportResult.TransactionsOnly -> {
                    val err = Backup.applyTransactionsOnly(result)
                    if (err != null) showApplyError(err.kind, err.message, bytes.size.toLong())
                    else AppDialogs.alert(tr("alert_logs_imported", "Logs imported successfully!"), icon = "fa-circle-check")
                }
                is ImportResult.Full -> {
                    val summary = trf(
                        "import_summary", "Backup contains ${result.transactionCount} transactions, ${result.debtCount} debts and ${result.fundCount} funds.",
                        "tx" to result.transactionCount, "debts" to result.debtCount, "funds" to result.fundCount,
                        "size" to Backup.formatBytesNice(result.sizeBytes),
                    )
                    AppDialogs.confirm(
                        message = tr(
                            "confirm_restore_backup",
                            "This will restore your name, funds, balances, target, reminders, theme, settings and full transaction history from this backup, replacing current data. Continue?",
                        ) + "\n\n" + summary,
                        icon = "fa-file-import",
                    ) { ok ->
                        if (ok != true) return@confirm
                        scope.launch {
                            busy = tr("busy_restoring", "Restoring data...")
                            delay(16)
                            val err = Backup.applyFull(context, result)
                            busy = null
                            if (err != null) {
                                showApplyError(err.kind, err.message, result.sizeBytes)
                            } else {
                                delay(700)
                                AppDialogs.alert(tr("alert_backup_restored", "Backup restored successfully!"), icon = "fa-circle-check")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun showParseFailure(f: ImportResult.Failure) {
        val size = Backup.formatBytesNice(f.sizeBytes)
        fail(
            when (f.error) {
                ImportError.EMPTY -> tr("import_err_empty", "The selected file is empty (0 bytes).")
                ImportError.TOO_BIG -> trf(
                    "import_err_too_big", "The file is too large ($size).",
                    "size" to size, "max" to Backup.formatBytesNice(Backup.MAX_IMPORT_BYTES),
                )
                ImportError.ZIP -> tr("import_err_zip", "This looks like a zip/Excel file, not a Tracker .json backup.")
                ImportError.NOT_JSON -> tr("import_err_not_json", "This doesn't look like a Tracker backup. Pick the .json file that Export created.")
                ImportError.TRUNCATED -> trf("import_err_truncated", "The backup file looks incomplete. (${f.reason})", "reason" to f.reason)
                ImportError.CORRUPT -> trf("import_err_corrupt", "The backup file is damaged. (${f.reason})", "reason" to f.reason)
                ImportError.STRUCTURE -> trf("import_err_structure", "The backup is missing required data (${f.reason}).", "reason" to f.reason)
                ImportError.INVALID_FORMAT -> tr("alert_invalid_backup_format", "Invalid backup file format!")
                ImportError.MEMORY -> trf("import_err_memory", "Not enough free memory to open a file this big ($size).", "size" to size)
            },
        )
    }

    private fun showApplyError(kind: ApplyErrorKind, reason: String, sizeBytes: Long) {
        fail(
            when (kind) {
                ApplyErrorKind.STORAGE_FULL -> trf(
                    "import_err_storage", "This backup doesn't fit in the app's storage. Nothing was changed.",
                    "need" to Backup.formatBytesNice(sizeBytes),
                )
                ApplyErrorKind.MEMORY -> trf("import_err_memory", "Not enough free memory.", "size" to Backup.formatBytesNice(sizeBytes))
                else -> trf("import_err_restore", "Something went wrong while restoring ($reason). Your previous data was kept.", "reason" to reason)
            },
        )
    }
}

// =============================================================================================
// Busy card (showBusy / hideBusy)
// =============================================================================================

@Composable
fun BusyOverlay() {
    // The "please wait" card is switched off (import / export are quick): it only flashed on screen.
    if (!BackupUi.SHOW_BUSY_CARD) return
    val message = BackupUi.busy ?: return
    val c = AppTheme.colors
    val dark = !c.isLight
    val spin = rememberInfiniteTransition(label = "busySpin")
    val angle by spin.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Restart), label = "angle",
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
        contentAlignment = Alignment.Center,
    ) {
        val shape = RoundedCornerShape(24.dp)
        Column(
            Modifier
                .padding(24.dp)
                .widthIn(max = 290.dp)
                .clip(shape)
                .background(if (dark) Color(0xFF141414) else Color.White, shape)
                .border(1.dp, if (dark) Color(0xFF2A2A2A) else Color(0xFFE5E7EB), shape)
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Canvas(Modifier.size(34.dp)) {
                val stroke = 3.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(
                    color = if (dark) Color(0xFF333333) else Color(0xFFE5E7EB), startAngle = 0f, sweepAngle = 360f, useCenter = false,
                    topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke),
                )
                drawArc(
                    color = Color(0xFFB91C1C), startAngle = angle - 90f, sweepAngle = 90f, useCenter = false,
                    topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke, cap = StrokeCap.Butt),
                )
            }
            AppText(
                text = message, size = 14f, lineHeight = 21f, weight = FontWeight.Bold,
                color = if (dark) Color(0xFFE5E5E5) else Color(0xFF111827), align = TextAlign.Center,
                modifier = Modifier.padding(top = 14.dp),
            )
            AppText(
                text = tr("busy_wait", "Please wait, do not close the app."), size = 11f, lineHeight = 16.5f,
                color = Color(0xFF9CA3AF), align = TextAlign.Center, modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

// =============================================================================================
// Avatar picker (popup-avatar-picker)
// =============================================================================================

object AvatarPicker {
    var selected by mutableStateOf("simple")

    fun open() {
        selected = AppStore.state.profileAvatar
        Popups.avatarPicker = true
    }

    fun close() { Popups.avatarPicker = false }

    fun confirm() {
        AppStore.state.profileAvatar = selected
        AppStore.commit()
        close()
    }
}

@Composable
fun AvatarPickerPopup() {
    val c = AppTheme.colors
    PopupLayer(visible = Popups.avatarPicker, scrim = 0.6f) {
        GlassCard(radius = 28.dp, padding = 24.dp) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PopupHeaderIcon("fa-user-circle", 32.dp, 14.dp, Brand.red)
                    AppText(
                        text = tr("modal_choose_avatar_title", "Choose Your Avatar"), size = 18f, lineHeight = 28f,
                        weight = FontWeight.ExtraBold, letterSpacingEm = -0.025f, color = c.textGray900,
                    )
                }
                Column {
                    AppText(
                        text = tr("modal_choose_avatar_subtitle", "Pick a style that fits you"), size = 10f, lineHeight = 15f,
                        weight = FontWeight.Bold, letterSpacingEm = 0.05f, color = c.textGray400, uppercase = true,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    // 4 icons per row, exactly 3 rows visible, one swipe = 3 rows
                    val rows = AVATAR_OPTIONS.chunked(4)
                    val paged = rememberPagedState(visible = 3, gap = 12.dp, snapStep = 3)
                    PagedViewport(state = paged, count = rows.size, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            rows.forEachIndexed { r, row ->
                                Row(Modifier.fillMaxWidth().pagedItem(paged, r), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    row.forEach { opt ->
                                        val selected = AvatarPicker.selected == opt.key
                                        val tile = RoundedCornerShape(16.dp)
                                        Column(
                                            Modifier
                                                .weight(1f)
                                                .clip(tile)
                                                .background(if (selected) opt.color.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.03f), tile)
                                                .border(2.dp, if (selected) opt.color else Color.Transparent, tile)
                                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                                    AvatarPicker.selected = opt.key
                                                }
                                                .padding(vertical = 8.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                        ) {
                                            Box(
                                                Modifier.size(44.dp).clip(CircleShape).background(opt.color.copy(alpha = 0.13f), CircleShape),
                                                contentAlignment = Alignment.Center,
                                            ) { FaIcon(opt.icon, 16.dp, opt.color) }
                                            AppText(
                                                text = tr("avatar_" + opt.key, opt.label), size = 9f, lineHeight = 11.25f,
                                                weight = FontWeight.Bold, color = c.textGray600, align = TextAlign.Center,
                                                modifier = Modifier.padding(top = 4.dp),
                                            )
                                        }
                                    }
                                    // an incomplete last row keeps the same column width
                                    repeat(4 - row.size) { Box(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModalButton(tr("common_cancel", "Cancel"), ModalButtonKind.Secondary, Modifier.weight(1f)) { AvatarPicker.close() }
                    ModalButton(tr("common_select", "Select"), ModalButtonKind.Red, Modifier.weight(1f)) { AvatarPicker.confirm() }
                }
            }
        }
    }
}
