package com.myapp.app.data

import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Small, app-wide UI session state: theme (dark/light) and language (en/bn).
 * Same storage keys as the old web app (smart_saver_theme / smart_saver_lang):
 *  - theme: dark unless the saved value is "light"
 *  - lang : "en" unless the saved value is "bn"
 */
object AppSession {
    private const val PREFS = "tracker_prefs"
    const val THEME_KEY = "smart_saver_theme"
    const val LANG_KEY = "smart_saver_lang"

    var isLight by mutableStateOf(false)
        private set
    var lang by mutableStateOf("en")
        private set

    /** true while Welcome / Lock / PIN / Pattern screens are showing (pure black status bar in dark mode). */
    var pureBlackScreen by mutableStateOf(false)

    /** Time the app started booting (used to keep the splash up for a minimum time). */
    var bootStartMs: Long = 0L
        private set

    private var inited = false

    val isBangla: Boolean get() = lang == "bn"

    fun init(context: Context) {
        if (inited) return
        inited = true
        bootStartMs = SystemClock.elapsedRealtime()
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        isLight = p.getString(THEME_KEY, null) == "light"
        lang = if (p.getString(LANG_KEY, null) == "bn") "bn" else "en"
    }

    fun setLight(context: Context, light: Boolean) {
        isLight = light
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(THEME_KEY, if (light) "light" else "dark").apply()
    }

    fun setLang(context: Context, value: String) {
        lang = if (value == "bn") "bn" else "en"
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(LANG_KEY, lang).apply()
    }
}
