package com.myapp.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import java.util.Locale

/** Why an import failed. The screens turn these into the same messages the old app showed. */
enum class ImportError {
    EMPTY,          // file is 0 bytes
    TOO_BIG,        // bigger than Backup.MAX_IMPORT_BYTES
    ZIP,            // zip / Excel file
    NOT_JSON,       // does not start like JSON
    TRUNCATED,      // cut off during download / transfer
    CORRUPT,        // damaged JSON
    STRUCTURE,      // required data has the wrong shape ([reason] = transactions / funds / debts)
    INVALID_FORMAT, // JSON but not a Tracker backup
    MEMORY,         // out of memory while reading
}

/** What the apply step can run into (disk full, permission, ...). */
enum class ApplyErrorKind { STORAGE_FULL, PERMISSION, MEMORY, OTHER }

class ApplyError(val kind: ApplyErrorKind, val message: String)

sealed class ImportResult {
    class Failure(
        val error: ImportError,
        val reason: String = "",
        val sizeBytes: Long = 0L,
    ) : ImportResult()

    /** Older export that only contained a list of transactions. */
    class TransactionsOnly(val transactions: List<Transaction>) : ImportResult()

    /** A full backup, checked and ready to restore once the user confirms. */
    class Full(
        val state: AppState,
        val reminders: ReminderSettings?,
        val theme: String?,
        val language: String?,
        val transactionCount: Int,
        val debtCount: Int,
        val fundCount: Int,
        val sizeBytes: Long,
    ) : ImportResult()
}

/** Same backup file format as the old app (type "smart_saver_elite_backup", version 2). */
object Backup {
    const val TYPE = "smart_saver_elite_backup"
    const val VERSION = 2
    const val MAX_IMPORT_BYTES: Long = 150L * 1024L * 1024L

    /** "12 B" / "3.4 KB" / "1.2 MB" */
    fun formatBytesNice(bytes: Long): String {
        val n = if (bytes < 0) 0L else bytes
        if (n < 1024) return "$n B"
        if (n < 1024 * 1024) return String.format(Locale.US, "%.1f KB", n / 1024.0)
        return String.format(Locale.US, "%.1f MB", n / (1024.0 * 1024.0))
    }

    // ---------------------------------------------------------------- export

    /**
     * The full A-to-Z backup: all data + reminders + theme + language, compact JSON.
     * (Same fields, same order as the old exportLogs().)
     */
    fun buildExportJson(
        state: AppState,
        reminders: ReminderSettings,
        theme: String,
        language: String,
    ): String {
        val o = JSONObject()
        o.put("type", TYPE)
        o.put("version", VERSION)
        o.put("exportedAt", JsDate.nowIso())
        o.put("data", state.toJson())
        o.put("reminders", reminders.toJson())
        o.put("theme", theme)
        o.put("language", language)
        return o.toString()
    }

    // ---------------------------------------------------------------- import (check only, changes nothing)

    /**
     * Reads and checks a backup file. Nothing is changed here: the caller shows the summary,
     * asks the user to confirm, then calls [applyFull] / [applyTransactionsOnly].
     */
    fun parse(bytes: ByteArray): ImportResult {
        val size = bytes.size.toLong()
        if (size == 0L) return ImportResult.Failure(ImportError.EMPTY, sizeBytes = size)
        if (size > MAX_IMPORT_BYTES) return ImportResult.Failure(ImportError.TOO_BIG, sizeBytes = size)

        // A zip/xlsx starts with the bytes "PK" - not something this app writes.
        if (bytes.size >= 2 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()) {
            return ImportResult.Failure(ImportError.ZIP, sizeBytes = size)
        }

        var text: String
        try {
            text = String(bytes, Charsets.UTF_8)
        } catch (e: OutOfMemoryError) {
            return ImportResult.Failure(ImportError.MEMORY, sizeBytes = size)
        }
        if (text.isNotEmpty() && text[0] == '\uFEFF') text = text.substring(1)
        text = text.trim()

        val firstCh = if (text.isNotEmpty()) text[0] else ' '
        val lastCh = if (text.isNotEmpty()) text[text.length - 1] else ' '
        if (firstCh != '{' && firstCh != '[' && firstCh != '"') {
            return ImportResult.Failure(ImportError.NOT_JSON, sizeBytes = size)
        }

        var parsed: Any?
        try {
            parsed = JSONTokener(text).nextValue()
            // Some apps double-encode the JSON as a string - unwrap once.
            if (parsed is String) parsed = JSONTokener(parsed).nextValue()
        } catch (e: OutOfMemoryError) {
            return ImportResult.Failure(ImportError.MEMORY, sizeBytes = size)
        } catch (e: JSONException) {
            val reason = e.message ?: "invalid JSON"
            return if ((firstCh == '{' && lastCh != '}') || (firstCh == '[' && lastCh != ']')) {
                ImportResult.Failure(ImportError.TRUNCATED, reason, size)
            } else {
                ImportResult.Failure(ImportError.CORRUPT, reason, size)
            }
        }

        // Accept a bare appState (no wrapper) too.
        if (parsed is JSONObject && parsed.isFalsy("data") &&
            jsTruthy(parsed.opt("funds")) && jsTruthy(parsed.opt("transactions"))
        ) {
            val wrapped = JSONObject()
            wrapped.put("type", TYPE)
            wrapped.put("version", 1)
            wrapped.put("data", parsed)
            parsed = wrapped
        }

        // ---- structure ----
        if (parsed is JSONArray) {
            // Older transactions-only export.
            val list = mutableListOf<Transaction>()
            for (i in 0 until parsed.length()) {
                val t = parsed.opt(i)
                if (t !is JSONObject) {
                    return ImportResult.Failure(ImportError.STRUCTURE, "transactions", size)
                }
                list.add(Transaction.fromJson(t))
            }
            return ImportResult.TransactionsOnly(list)
        }

        val root = parsed as? JSONObject
        val data = root?.opt("data")
        val looksLikeBackup = root != null && data is JSONObject &&
            (root.str("type") == TYPE || jsTruthy(data.opt("funds")) || jsTruthy(data.opt("transactions")))
        if (!looksLikeBackup || root == null || data !is JSONObject) {
            return ImportResult.Failure(ImportError.INVALID_FORMAT, sizeBytes = size)
        }

        if (data.has("funds") && data.opt("funds") !is JSONObject) {
            return ImportResult.Failure(ImportError.STRUCTURE, "funds", size)
        }
        if (data.has("transactions") && data.opt("transactions") !is JSONArray) {
            return ImportResult.Failure(ImportError.STRUCTURE, "transactions", size)
        }
        if (data.has("debts") && data.opt("debts") !is JSONArray) {
            return ImportResult.Failure(ImportError.STRUCTURE, "debts", size)
        }
        val txArr = data.optJSONArray("transactions")
        if (txArr != null) {
            for (i in 0 until txArr.length()) {
                if (txArr.opt(i) !is JSONObject) return ImportResult.Failure(ImportError.STRUCTURE, "transactions", size)
            }
        }
        val debtArr = data.optJSONArray("debts")
        if (debtArr != null) {
            for (i in 0 until debtArr.length()) {
                if (debtArr.opt(i) !is JSONObject) return ImportResult.Failure(ImportError.STRUCTURE, "debts", size)
            }
        }

        // Numbers shown in the "Backup contains ..." summary (counted before normalising, like the old app).
        val txCount = txArr?.length() ?: 0
        val debtCount = debtArr?.length() ?: 0
        val fundCount = data.optJSONObject("funds")?.length() ?: 0

        val newState: AppState = try {
            normalizeImportedJson(data)
            AppState.fromJson(data)
        } catch (e: OutOfMemoryError) {
            return ImportResult.Failure(ImportError.MEMORY, sizeBytes = size)
        } catch (e: Exception) {
            return ImportResult.Failure(ImportError.CORRUPT, e.message ?: "", size)
        }

        // v2 backups also carry reminders / theme / language; older ones do not.
        var reminders: ReminderSettings? = null
        val remindersObj = root.opt("reminders")
        if (remindersObj is JSONObject) reminders = ReminderSettings.fromJson(remindersObj)

        val theme = if (jsTruthy(root.opt("theme"))) root.str("theme") else null
        val language = if (jsTruthy(root.opt("language"))) root.str("language") else null

        return ImportResult.Full(
            state = newState,
            reminders = reminders,
            theme = theme,
            language = language,
            transactionCount = txCount,
            debtCount = debtCount,
            fundCount = fundCount,
            sizeBytes = size,
        )
    }

    /**
     * Fills in every default the app applies to saved data at startup, so a backup from an
     * older version restores cleanly (same rules as the old normalizeImportedState()).
     * Works on the raw JSON before it becomes an [AppState].
     */
    fun normalizeImportedJson(d: JSONObject) {
        if (d.isFalsy("filters")) {
            val f = JSONObject()
            f.put("sort", "newest")
            f.put("time", "month")
            f.put("price", "all")
            d.put("filters", f)
        }
        if (d.isFalsy("funds")) {
            val f = JSONObject()
            f.put("Monthly Cash", 0.0)
            d.put("funds", f)
        }
        val funds = d.optJSONObject("funds")
        val active = d.opt("activeFund")
        if (!jsTruthy(active) || funds == null || !funds.has(active.toString())) {
            val keys = funds?.keys()
            if (keys != null && keys.hasNext()) d.put("activeFund", keys.next())
        }
        if (d.isFalsy("transactions")) d.put("transactions", JSONArray())
        if (d.isFalsy("debts")) d.put("debts", JSONArray())
        if (d.isFalsy("fundMeta")) d.put("fundMeta", JSONObject())
        if (!d.has("onboardingComplete")) d.put("onboardingComplete", true)

        val debts = d.optJSONArray("debts")
        if (debts != null) {
            for (i in 0 until debts.length()) {
                val x = debts.optJSONObject(i)
                if (x != null && x.isFalsy("payments")) x.put("payments", JSONArray())
            }
        }

        if (!d.has("biometricLockEnabled")) d.put("biometricLockEnabled", false)
        if (d.isFalsy("profileAvatar")) d.put("profileAvatar", "simple")
        if (!d.has("lockType")) {
            d.put("lockType", if (jsTruthy(d.opt("biometricLockEnabled"))) "biometric" else "none")
        }
        if (!d.has("lockPinHash")) d.put("lockPinHash", JSONObject.NULL)
        if (!d.has("lockPatternHash")) d.put("lockPatternHash", JSONObject.NULL)
    }

    // ---------------------------------------------------------------- import (apply, all-or-nothing)

    /**
     * Restores a full backup. All-or-nothing: the new data is written to disk FIRST; if that
     * fails nothing is changed. If anything breaks afterwards everything is put back.
     * Returns null on success, or the reason it failed.
     */
    fun applyFull(context: Context, full: ImportResult.Full): ApplyError? {
        val prevState = AppStore.state
        val prevReminders = ReminderSettings.load(context)
        val prevLight = AppSession.isLight
        val prevLang = AppSession.lang

        try {
            val serialized = full.state.toJson().toString()
            AppStore.writeRaw(serialized)
        } catch (e: Throwable) {
            return describe(e)
        }

        try {
            AppStore.replaceStateInMemory(full.state)
            full.reminders?.persist(context)
            val theme = full.theme
            if (theme != null) AppSession.setLight(context, theme == "light")
            val language = full.language
            if (language != null) AppSession.setLang(context, language)
            AppStore.refresh()
            return null
        } catch (e: Throwable) {
            // Something broke half-way: put everything back the way it was.
            try {
                AppStore.replaceStateInMemory(prevState)
                AppStore.saveState()
                prevReminders.persist(context)
                AppSession.setLight(context, prevLight)
                AppSession.setLang(context, prevLang)
                AppStore.refresh()
            } catch (rollbackErr: Throwable) {
                // nothing more we can do
            }
            return describe(e)
        }
    }

    /** Older transactions-only file: the imported lines are put in front of the existing ones. */
    fun applyTransactionsOnly(result: ImportResult.TransactionsOnly): ApplyError? {
        val state = AppStore.state
        val previous = state.transactions
        val merged = mutableListOf<Transaction>()
        merged.addAll(result.transactions)
        merged.addAll(previous)
        state.transactions = merged
        if (!AppStore.saveState()) {
            state.transactions = previous
            AppStore.refresh()
            return describe(AppStore.lastSaveError ?: IllegalStateException("save failed"))
        }
        AppStore.refresh()
        return null
    }

    /** Same wording rules as the old describeBackupError(). */
    private fun describe(e: Throwable): ApplyError {
        if (e is OutOfMemoryError) return ApplyError(ApplyErrorKind.MEMORY, e.message ?: "out of memory")
        val m = e.message ?: e.toString()
        val lower = m.lowercase(Locale.US)
        val kind = when {
            Regex("enospc|no space|space left|quota").containsMatchIn(lower) -> ApplyErrorKind.STORAGE_FULL
            Regex("eacces|eperm|permission|denied").containsMatchIn(lower) -> ApplyErrorKind.PERMISSION
            else -> ApplyErrorKind.OTHER
        }
        return ApplyError(kind, m)
    }
}
