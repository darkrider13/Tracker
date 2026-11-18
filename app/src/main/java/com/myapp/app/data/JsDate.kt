package com.myapp.app.data

import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone

/**
 * Reads the date strings the old app saved (JavaScript `new Date(string)` rules for the
 * formats the app actually writes):
 *  - "2026-11-16T10:30:00.000Z"        full ISO (UTC or with an offset)
 *  - "2026-11-16T10:30"                date + time without zone  -> LOCAL time
 *  - "2026-11-16", "2026-11", "2026"   date only                 -> UTC midnight
 * Anything else returns null (JS gives NaN).
 */
object JsDate {
    private val ISO = Regex(
        "^(\\d{4})(?:-(\\d{2})(?:-(\\d{2}))?)?" +
            "(?:[T ](\\d{2}):(\\d{2})(?::(\\d{2})(?:[.,](\\d{1,9}))?)?)?" +
            "\\s*(Z|[+-]\\d{2}(?::?\\d{2})?)?$",
        RegexOption.IGNORE_CASE,
    )

    /** Milliseconds since 1970-01-01 UTC, or null when the text is not a date. */
    fun parseMillis(text: String?): Long? {
        if (text == null) return null
        val s = text.trim()
        if (s.isEmpty()) return null
        val m = ISO.matchEntire(s) ?: return null
        val g = m.groupValues

        val year = g[1].toInt()
        val month = if (g[2].isNotEmpty()) g[2].toInt() else 1
        val day = if (g[3].isNotEmpty()) g[3].toInt() else 1
        val hasTime = g[4].isNotEmpty()
        val hour = if (hasTime) g[4].toInt() else 0
        val minute = if (hasTime) g[5].toInt() else 0
        val second = if (g[6].isNotEmpty()) g[6].toInt() else 0
        val millis = if (g[7].isNotEmpty()) (g[7] + "00").substring(0, 3).toInt() else 0
        val zone = g[8]

        if (month !in 1..12 || day !in 1..31 || hour !in 0..24 || minute !in 0..59 || second !in 0..59) return null

        val tz: TimeZone = when {
            zone.isNotEmpty() -> {
                if (zone.equals("Z", ignoreCase = true)) {
                    TimeZone.getTimeZone("UTC")
                } else {
                    val sign = if (zone[0] == '-') "-" else "+"
                    val digits = zone.substring(1).replace(":", "")
                    val hh = digits.substring(0, 2)
                    val mm = if (digits.length >= 4) digits.substring(2, 4) else "00"
                    TimeZone.getTimeZone("GMT$sign$hh:$mm")
                }
            }
            hasTime -> TimeZone.getDefault()      // date-time with no zone = local time
            else -> TimeZone.getTimeZone("UTC")   // date-only = UTC
        }

        val cal = GregorianCalendar(tz)
        cal.isLenient = false
        cal.clear()
        return try {
            cal.set(year, month - 1, day, hour, minute, second)
            cal.set(Calendar.MILLISECOND, millis)
            cal.timeInMillis
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /** `new Date(text).getFullYear()` (local time zone), or null when the text is not a date. */
    fun localYear(text: String?): Int? {
        val ms = parseMillis(text) ?: return null
        val cal = Calendar.getInstance()
        cal.timeInMillis = ms
        return cal.get(Calendar.YEAR)
    }

    /** Current time as an ISO string like JS `new Date().toISOString()` (UTC, milliseconds, "Z"). */
    fun nowIso(): String = toIso(System.currentTimeMillis())

    fun toIso(millis: Long): String {
        val cal = GregorianCalendar(TimeZone.getTimeZone("UTC"))
        cal.timeInMillis = millis
        return String.format(
            java.util.Locale.US,
            "%04d-%02d-%02dT%02d:%02d:%02d.%03dZ",
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH),
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE),
            cal.get(Calendar.SECOND),
            cal.get(Calendar.MILLISECOND),
        )
    }
}
