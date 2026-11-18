package com.myapp.app.data

import org.json.JSONArray
import org.json.JSONObject

/*
 * Small helpers that make org.json behave like the loosely-typed JSON the old web app
 * used (missing key = undefined, explicit null, numbers that may arrive as int or double).
 */

/** String value, or null when the key is missing / null. */
internal fun JSONObject.str(key: String): String? {
    if (!has(key) || isNull(key)) return null
    val v = opt(key)
    return if (v is String) v else v?.toString()
}

/** Long value, or null when the key is missing / null / not a number. */
internal fun JSONObject.long(key: String): Long? {
    if (!has(key) || isNull(key)) return null
    return when (val v = opt(key)) {
        is Number -> v.toLong()
        is String -> v.toLongOrNull() ?: v.toDoubleOrNull()?.toLong()
        else -> null
    }
}

/** Boolean value (JS-style truthiness for numbers/strings), or null when missing / null. */
internal fun JSONObject.bool(key: String): Boolean? {
    if (!has(key) || isNull(key)) return null
    return when (val v = opt(key)) {
        is Boolean -> v
        is Number -> v.toDouble() != 0.0
        is String -> v.isNotEmpty()
        else -> null
    }
}

/** Double value with a default for missing / null / not a number. */
internal fun JSONObject.dbl(key: String, default: Double = 0.0): Double {
    if (!has(key) || isNull(key)) return default
    return when (val v = opt(key)) {
        is Number -> v.toDouble()
        is String -> v.toDoubleOrNull() ?: default
        else -> default
    }
}

/** Every key that is not in [known], so unknown fields survive a load/save round trip. */
internal fun JSONObject.extras(known: Set<String>): JSONObject {
    val result = JSONObject()
    val it = keys()
    while (it.hasNext()) {
        val k = it.next()
        if (k !in known) result.put(k, opt(k))
    }
    return result
}

internal fun JSONObject.putExtras(extra: JSONObject) {
    val it = extra.keys()
    while (it.hasNext()) {
        val k = it.next()
        if (!has(k)) put(k, extra.opt(k))
    }
}

/** Writes [value], or an explicit JSON null when it is null. */
internal fun JSONObject.putNullable(key: String, value: Any?) {
    put(key, value ?: JSONObject.NULL)
}

/** Writes [value] only when it is not null (the key stays "undefined" otherwise). */
internal fun JSONObject.putIfNotNull(key: String, value: Any?) {
    if (value != null) put(key, value)
}

internal fun safeDouble(v: Double): Double = if (v.isNaN() || v.isInfinite()) 0.0 else v

/** JavaScript truthiness of a parsed JSON value. */
internal fun jsTruthy(v: Any?): Boolean = when (v) {
    null -> false
    JSONObject.NULL -> false
    is Boolean -> v
    is Number -> v.toDouble() != 0.0 && !v.toDouble().isNaN()
    is String -> v.isNotEmpty()
    else -> true
}

/** True when the key is missing, null, false, 0 or "" (JS `!obj.key`). */
internal fun JSONObject.isFalsy(key: String): Boolean = !has(key) || !jsTruthy(opt(key))

internal fun JSONArray.toLongList(): MutableList<Long> {
    val list = mutableListOf<Long>()
    for (i in 0 until length()) {
        val v = opt(i)
        when (v) {
            is Number -> list.add(v.toLong())
            is String -> v.toLongOrNull()?.let { list.add(it) }
            else -> {}
        }
    }
    return list
}
