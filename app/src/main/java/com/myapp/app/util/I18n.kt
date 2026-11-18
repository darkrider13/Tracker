package com.myapp.app.util

import android.content.Context
import com.myapp.app.data.AppSession
import org.json.JSONObject

/**
 * Translations. English text is written at each call site (tr("key", "English text")),
 * exactly like the old app; this table only holds the Bangla versions (assets/i18n_bn.json,
 * copied 1:1 from the old I18N.bn table).
 */
object I18n {
    @Volatile
    private var bn: Map<String, String> = emptyMap()

    fun init(context: Context) {
        if (bn.isNotEmpty()) return
        try {
            val text = context.applicationContext.assets.open("i18n_bn.json")
                .bufferedReader(Charsets.UTF_8).use { it.readText() }
            val o = JSONObject(text)
            val map = HashMap<String, String>(o.length() * 2)
            val keys = o.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = o.optString(k, "")
                if (v.isNotEmpty()) map[k] = v
            }
            bn = map
        } catch (e: Exception) {
            bn = emptyMap()   // app still works in English
        }
    }

    internal fun bangla(key: String): String? = bn[key]

    /** Raw Bangla text for a key (used by resolveCanonicalFundName-style reverse lookups). */
    fun banglaText(key: String): String? = bn[key]
}

/** Current app language: "en" or "bn". */
fun getLang(): String = AppSession.lang

fun isBangla(): Boolean = AppSession.lang == "bn"

/** Translated text if available (Bangla mode), otherwise the English fallback. */
fun tr(key: String, englishFallback: String): String {
    if (AppSession.lang == "bn") {
        val v = I18n.bangla(key)
        if (!v.isNullOrEmpty()) return v
    }
    return englishFallback
}

/** JavaScript-style text of a value (10.0 -> "10", null -> "null"). */
fun jsString(v: Any?): String = when (v) {
    null -> "null"
    is Double -> safeNumberToString(v)
    is Float -> safeNumberToString(v.toDouble())
    else -> v.toString()
}

/** Like [tr] but fills {placeholders}: trf("key", "Lent to {name}", "name" to person). */
fun trf(key: String, englishFallback: String, vararg vars: Pair<String, Any?>): String {
    var str = tr(key, englishFallback)
    for ((k, v) in vars) {
        str = str.replace("{$k}", jsString(v))
    }
    return str
}

/** Same as [trf] with the values already in a map. */
fun trfMap(key: String, englishFallback: String, vars: Map<String, Any?>?): String {
    var str = tr(key, englishFallback)
    if (vars != null) {
        for ((k, v) in vars) {
            str = str.replace("{$k}", jsString(v))
        }
    }
    return str
}
