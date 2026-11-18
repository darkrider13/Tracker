package com.myapp.app.util

import android.text.format.DateFormat
import com.myapp.app.data.JsDate
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

val BN_MONTHS = listOf(
    "জানুয়ারি", "ফেব্রুয়ারি", "মার্চ", "এপ্রিল", "মে", "জুন",
    "জুলাই", "আগস্ট", "সেপ্টেম্বর", "অক্টোবর", "নভেম্বর", "ডিসেম্বর",
)

/** Proper Bangla month abbreviations for the compact month wheel. */
val BN_MONTHS_SHORT = listOf(
    "জানু.", "ফেব্রু.", "মার্চ", "এপ্রিল", "মে", "জুন",
    "জুলাই", "আগ.", "সেপ্ট.", "অক্টো.", "নভে.", "ডিসে.",
)

val BN_WEEKDAYS = listOf("রবিবার", "সোমবার", "মঙ্গলবার", "বুধবার", "বৃহস্পতিবার", "শুক্রবার", "শনিবার")
val BN_WEEKDAYS_SHORT = listOf("রবি", "সোম", "মঙ্গল", "বুধ", "বৃহঃ", "শুক্র", "শনি")

/**
 * The same options the old app passed to toLocaleDateString / fmtDate:
 * weekday = "long" | "short", day = "numeric" | "2-digit", month = "short" | "long", year = "numeric".
 */
class DateOpts(
    val weekday: String? = null,
    val day: String? = null,
    val month: String? = null,
    val year: String? = null,
)

private val patternCache = HashMap<String, String>()

private fun skeletonOf(o: DateOpts): String {
    val sb = StringBuilder()
    when (o.weekday) {
        "long" -> sb.append("EEEE")
        "short" -> sb.append("EEE")
    }
    when (o.month) {
        "short" -> sb.append("MMM")
        "long" -> sb.append("MMMM")
        "numeric" -> sb.append("M")
        "2-digit" -> sb.append("MM")
    }
    when (o.day) {
        "numeric" -> sb.append("d")
        "2-digit" -> sb.append("dd")
    }
    if (o.year == "numeric") sb.append("y")
    // toLocaleDateString([], {}) = plain numeric date, e.g. 11/16/2026
    if (sb.isEmpty()) sb.append("yMd")
    return sb.toString()
}

private fun englishDate(millis: Long, o: DateOpts): String {
    val locale = Locale.getDefault()
    val skeleton = skeletonOf(o)
    val key = locale.toString() + "|" + skeleton
    val pattern = patternCache.getOrPut(key) { DateFormat.getBestDateTimePattern(locale, skeleton) }
    return SimpleDateFormat(pattern, locale).format(java.util.Date(millis))
}

private fun toMillis(dateVal: String?): Long? = JsDate.parseMillis(dateVal)

/**
 * Date text. English: the phone's own date format (same as toLocaleDateString).
 * Bangla: full Bangla month names + Bangla numerals, no leading zeros ("১৯ আগস্ট").
 */
fun fmtDate(dateVal: String?, opts: DateOpts = DateOpts()): String {
    val ms = toMillis(dateVal) ?: return "Invalid Date"
    return fmtDate(ms, opts)
}

fun fmtDate(millis: Long, opts: DateOpts = DateOpts()): String {
    if (!isBangla()) return englishDate(millis, opts)
    val cal = Calendar.getInstance()
    cal.timeInMillis = millis
    if (opts.weekday != null) return BN_WEEKDAYS[cal.get(Calendar.DAY_OF_WEEK) - 1]
    var out = "${toBnDigits(cal.get(Calendar.DAY_OF_MONTH).toString())} ${BN_MONTHS[cal.get(Calendar.MONTH)]}"
    if (opts.year != null) out += ", ${toBnDigits(cal.get(Calendar.YEAR).toString())}"
    return out
}

/** Bangla time-of-day word for a 24h hour (same bands as the Morning/Afternoon/Evening/Night reminders). */
fun bnTimePeriod(hour: Int): String {
    if (hour in 4..11) return tr("reminder_label_morning", "Morning")
    if (hour in 12..15) return tr("reminder_label_afternoon", "Afternoon")
    if (hour in 16..19) return tr("reminder_label_evening", "Evening")
    return tr("reminder_label_night", "Night")
}

/** "3:39 PM" in English; "৩:৩৯ দুপুর"-style in Bangla. */
fun fmtTime(dateVal: String?): String {
    val ms = toMillis(dateVal) ?: return "Invalid Date"
    return fmtTime(ms)
}

fun fmtTime(millis: Long): String {
    if (!isBangla()) {
        return SimpleDateFormat("h:mm a", Locale.getDefault()).format(java.util.Date(millis))
    }
    val cal = Calendar.getInstance()
    cal.timeInMillis = millis
    val h = cal.get(Calendar.HOUR_OF_DAY)
    var h12 = h % 12
    if (h12 == 0) h12 = 12
    val mm = cal.get(Calendar.MINUTE).toString().padStart(2, '0')
    return "${toBnDigits(h12.toString())}:${toBnDigits(mm)} ${bnTimePeriod(h)}"
}

/** True when [dateStr] is exactly today as YYYY-MM-DD (month-only values never count). */
fun isDateFilterToday(dateStr: String?): Boolean {
    if (dateStr == null || !Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(dateStr)) return false
    val now = Calendar.getInstance()
    val today = "%04d-%02d-%02d".format(
        Locale.US,
        now.get(Calendar.YEAR),
        now.get(Calendar.MONTH) + 1,
        now.get(Calendar.DAY_OF_MONTH),
    )
    return dateStr == today
}
