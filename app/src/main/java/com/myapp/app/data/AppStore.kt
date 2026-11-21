package com.myapp.app.data

import android.content.Context
import android.util.AtomicFile
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.Executors
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.Calendar

/**
 * The one place the app data lives (the old global `appState` + saveState()).
 *
 * Use it exactly like the old code:
 *   - read / change `AppStore.state` directly
 *   - call [saveState] to write it to disk
 *   - call [commit] = saveState + refresh every screen (the old `saveState(); initApp();`)
 *
 * Screens read [observe] (or [revision]) so they redraw after [commit] / [refresh].
 */
object AppStore {
    private const val TAG = "AppStore"

    /** Same name as the old localStorage key (smart_saver_isolated_v12). */
    private const val FILE_NAME = "smart_saver_isolated_v12.json"

    private lateinit var file: File
    private var initialized = false

    var state: AppState = AppState.newDefault()
        private set

    /** Goes up by one every time the screens must redraw. */
    var revision by mutableIntStateOf(0)
        private set

    /** Last disk-write problem (null = last save worked). Screens can show a "storage full" popup from it. */
    var lastSaveError: Throwable? = null
        private set

    /** Read this inside a composable and it redraws whenever [commit] / [refresh] runs. */
    fun observe(): AppState {
        @Suppress("UNUSED_VARIABLE")
        val r = revision
        return state
    }

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        file = File(context.applicationContext.filesDir, FILE_NAME)
        state = loadFromDisk()
        resetViewStateForFreshLaunch()
        ensureActiveFundValid()
        purgeOldTransactionHistory()
        purgeOldDebtHistory()
    }

    /**
     * The app was closed (swiped out of Recents) and is now starting fresh: the screen state starts from
     * the defaults again - the FIRST fund is selected and the Logs filters (sort / time / price / custom
     * date / custom month) are cleared. Runs once per process (init() is guarded), so it never touches
     * anything while the app is in use, and the saved money data is not changed.
     */
    private fun resetViewStateForFreshLaunch() {
        val s = state
        s.funds.keys.firstOrNull()?.let { s.activeFund = it }
        val f = s.filters
        f.sort = "newest"
        f.time = "month"
        f.price = "all"
        f.customDate = null
        f.customMonth = null
    }

    // ---------- load ----------

    private fun loadFromDisk(): AppState {
        val bytes: ByteArray? = try {
            AtomicFile(file).readFully()
        } catch (e: FileNotFoundException) {
            null
        } catch (e: IOException) {
            Log.e(TAG, "read failed", e)
            null
        }

        if (bytes == null || bytes.isEmpty()) {
            // First launch: create and save the default data (same as the old app).
            val fresh = AppState.newDefault()
            try {
                writeRaw(fresh.toJson().toString())
            } catch (e: Exception) {
                lastSaveError = e
            }
            return fresh
        }

        return try {
            AppState.fromJson(JSONObject(String(bytes, Charsets.UTF_8)))
        } catch (e: Exception) {
            // Unreadable file: never delete it - move it aside so nothing is lost, then start fresh.
            Log.e(TAG, "saved data unreadable, keeping a copy and starting fresh", e)
            try {
                val aside = File(file.parentFile, "$FILE_NAME.unreadable-${System.currentTimeMillis()}")
                file.renameTo(aside)
            } catch (ignored: Exception) {
            }
            AppState.newDefault()
        }
    }

    // ---------- save ----------

    /** Writes the whole file safely (a crash half-way never leaves a broken file). Throws on failure. */
    internal fun writeRaw(text: String) {
        val atomic = AtomicFile(file)
        val out = atomic.startWrite()
        try {
            out.write(text.toByteArray(Charsets.UTF_8))
            atomic.finishWrite(out)
        } catch (e: IOException) {
            atomic.failWrite(out)
            throw e
        }
    }

    /** One background thread writes the file, in order, so a slow disk never freezes a popup animation. */
    private val saver = Executors.newSingleThreadExecutor { r ->
        Thread(r, "store-save").apply { priority = Thread.NORM_PRIORITY - 1 }
    }

    /** The old saveState(). Returns true when the data reached the disk (waits for the writer, keeps order). */
    fun saveState(): Boolean {
        return try {
            val text = state.toJson().toString()
            saver.submit<Boolean> {
                try {
                    writeRaw(text)
                    lastSaveError = null
                    true
                } catch (e: Exception) {
                    lastSaveError = e
                    Log.e(TAG, "saveState failed", e)
                    false
                }
            }.get()
        } catch (e: Exception) {
            lastSaveError = e
            Log.e(TAG, "saveState failed", e)
            false
        }
    }

    /** Same as [saveState] but the disk write happens in the background (the UI thread only builds the text). */
    private fun saveStateAsync() {
        try {
            val text = state.toJson().toString()
            saver.execute {
                try {
                    writeRaw(text)
                    lastSaveError = null
                } catch (e: Exception) {
                    lastSaveError = e
                    Log.e(TAG, "saveState failed", e)
                }
            }
        } catch (e: Exception) {
            lastSaveError = e
            Log.e(TAG, "saveState failed", e)
        }
    }

    /**
     * Guards against an activeFund that no longer exists: falls back to the first fund instead of
     * silently showing zero (same as the start of the old calculateFinance()).
     */
    fun ensureActiveFundValid() {
        val s = state
        if (!s.funds.containsKey(s.activeFund)) {
            val first = s.funds.keys.firstOrNull()
            if (first != null) s.activeFund = first
        }
    }

    /** Redraw every screen (the old initApp()/re-render) without saving. */
    fun refresh() {
        ensureActiveFundValid()
        if (redrawGate()) {
            // A popup is open (and most likely closing right now): redraw every screen only after its
            // fade-out, so the heavy redraw never lands in the middle of the animation.
            if (!redrawPending) {
                redrawPending = true
                mainHandler.postDelayed({
                    redrawPending = false
                    revision += 1
                }, 180L)
            }
        } else {
            revision += 1
        }
    }

    /** Set by the popup layer: true while a popup is on screen. */
    var redrawGate: () -> Boolean = { false }
    private var redrawPending = false
    private val mainHandler = Handler(Looper.getMainLooper())

    /** The old `saveState(); initApp();` pair. */
    fun commit() {
        saveStateAsync()
        refresh()
    }

    /** Save without redrawing (background bookkeeping such as the "reminder already sent" flag). */
    fun commitQuietly() {
        saveState()
    }

    /** Swap in a whole new state (used by backup import) and redraw. Caller already wrote it to disk. */
    internal fun replaceStateInMemory(newState: AppState) {
        state = newState
    }

    // ---------- auto-purge (same rules as the old app, run once per launch) ----------

    /**
     * Rolling 6-year window for transactions: once a calendar year falls out of
     * (currentYear - 5 .. currentYear) its transactions are removed. Unreadable dates are kept.
     */
    private fun purgeOldTransactionHistory() {
        val cutoffYear = Calendar.getInstance().get(Calendar.YEAR) - 5
        val before = state.transactions.size
        if (before == 0) return
        state.transactions = state.transactions.filter { t ->
            val y = JsDate.localYear(t.date)
            y == null || y >= cutoffYear
        }.toMutableList()
        if (state.transactions.size != before) saveState()
    }

    /**
     * Rolling 15-year window for SETTLED debts/receivables, counted from their created date.
     * Pending (unsettled) entries are never removed. Unreadable dates are kept.
     */
    private fun purgeOldDebtHistory() {
        val cutoffYear = Calendar.getInstance().get(Calendar.YEAR) - 15
        val before = state.debts.size
        if (before == 0) return
        state.debts = state.debts.filter { d ->
            if (!d.settled) {
                true
            } else {
                val y = JsDate.localYear(d.createdDate)
                y == null || y >= cutoffYear
            }
        }.toMutableList()
        if (state.debts.size != before) saveState()
    }
}
