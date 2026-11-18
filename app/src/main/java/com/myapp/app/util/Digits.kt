package com.myapp.app.util

private val BN_DIGITS = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')

/** Every ASCII digit 0-9 becomes its Bangla numeral; everything else is left untouched. */
fun toBnDigits(str: String): String {
    val sb = StringBuilder(str.length)
    for (c in str) {
        if (c in '0'..'9') sb.append(BN_DIGITS[c - '0']) else sb.append(c)
    }
    return sb.toString()
}

/** Reverse of [toBnDigits]: Bangla numerals back to ASCII digits. */
fun fromBnDigits(str: String): String {
    val sb = StringBuilder(str.length)
    for (c in str) {
        val i = BN_DIGITS.indexOf(c)
        if (i >= 0) sb.append(('0' + i)) else sb.append(c)
    }
    return sb.toString()
}

/** Bangla numerals only while the app is in Bangla, otherwise unchanged. */
fun localizeDigits(str: String): String = if (isBangla()) toBnDigits(str) else str

/**
 * Amount search: commas and digit scripts are ignored on BOTH sides, so a typed "2,000"
 * (or "২,০০০") finds an amount that is displayed as "২,০০০.০০".
 */
fun amountTextMatchesQuery(amountText: String?, query: String?): Boolean {
    val normQuery = fromBnDigits(query ?: "").replace(",", "").lowercase()
    if (normQuery.isEmpty()) return false
    val normText = fromBnDigits(amountText ?: "").replace(",", "").lowercase()
    return normText.contains(normQuery)
}

fun amountTextExactMatchesQuery(amountText: String?, query: String?): Boolean {
    val normQuery = fromBnDigits(query ?: "").replace(",", "").trim().lowercase()
    if (normQuery.isEmpty()) return false
    val normText = fromBnDigits(amountText ?: "").replace(",", "").trim().lowercase()
    if (normText == normQuery) return true
    // Typing "2000" fully matches "2000.00": only when every digit after the point is a zero.
    if (!normQuery.contains('.') && normText.startsWith("$normQuery.")) {
        val fraction = normText.substring(normQuery.length + 1)
        if (fraction.all { it == '0' }) return true
    }
    return false
}

/** Same as [toBnDigits] for a number (any Int / Long / Double). */
fun toBnDigits(n: Number): String = toBnDigits(n.toString())

/** Treats a hyphen like a space for search matching (same length, so indexes line up 1:1). */
fun normalizeHyphenForSearch(str: String?): String = (str ?: "").replace('-', ' ')
