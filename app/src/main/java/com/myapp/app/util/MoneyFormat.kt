package com.myapp.app.util

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.floor

/** JS `String(n)` for numbers, never in exponent form (10.0 -> "10", 0.1 -> "0.1"). */
fun safeNumberToString(n: Double): String {
    if (n.isNaN()) return "NaN"
    if (n.isInfinite()) return if (n > 0) "Infinity" else "-Infinity"
    if (n == 0.0) return "0"
    return BigDecimal(java.lang.Double.toString(n)).stripTrailingZeros().toPlainString()
}

/**
 * JS `n.toFixed(decimals)`: rounds the exact stored value half-up, so 1.005 -> "1.00" like before
 * (and never switches to exponent form for huge numbers).
 */
fun safeToFixed(n: Double, decimals: Int = 2): String {
    val num = if (n.isNaN()) 0.0 else n
    if (num.isInfinite()) return if (num > 0) "Infinity" else "-Infinity"
    val bd = BigDecimal(abs(num)).setScale(decimals, RoundingMode.HALF_UP)
    val plain = bd.toPlainString()
    return if (num < 0) "-$plain" else plain
}

/** 1234567 -> "1,234,567" (3-digit groups from the right). */
private fun groupThousands(digits: String): String {
    if (digits.length <= 3) return digits
    val sb = StringBuilder()
    val first = digits.length % 3
    if (first > 0) sb.append(digits, 0, first)
    var i = first
    while (i < digits.length) {
        if (sb.isNotEmpty()) sb.append(',')
        sb.append(digits, i, i + 3)
        i += 3
    }
    return sb.toString()
}

/**
 * Money text: 91673613.5 -> "91,673,613.50". From 1 crore (10,000,000) up it is shown compact:
 * "1Cr", "9.9Cr" ("১ কোটি", "৯.৯ কোটি" in Bangla), the crore value floored to 1 decimal.
 */
fun fmtMoney(n: Double?, decimals: Int = 2): String {
    var num = n ?: 0.0
    if (num.isNaN()) num = 0.0
    val sign = if (num < 0) "-" else ""
    val absNum = abs(num)
    val crore = 10000000.0
    if (absNum >= crore) {
        val croreValue = floor((absNum / crore) * 10) / 10
        val croreStr = if (croreValue == floor(croreValue)) safeNumberToString(croreValue) else safeToFixed(croreValue, 1)
        return sign + localizeDigits(croreStr) + (if (isBangla()) " কোটি" else "Cr")
    }
    val fixed = safeToFixed(absNum, decimals)
    val dot = fixed.indexOf('.')
    val intPart = if (dot >= 0) fixed.substring(0, dot) else fixed
    val decPart = if (dot >= 0) fixed.substring(dot) else ""
    return localizeDigits(sign + groupThousands(intPart) + decPart)
}

fun fmtMoney(n: Long, decimals: Int = 2): String = fmtMoney(n.toDouble(), decimals)

fun fmtMoney(n: Int, decimals: Int = 2): String = fmtMoney(n.toDouble(), decimals)

/**
 * Live formatting while typing an amount: keeps digits and one dot, strips leading zeros,
 * adds thousands commas, and shows Bangla numerals in Bangla mode.
 */
fun formatAmountTyping(raw: String?): String {
    var str = fromBnDigits(raw ?: "").filter { it in '0'..'9' || it == '.' }
    val firstDot = str.indexOf('.')
    if (firstDot != -1) {
        str = str.substring(0, firstDot + 1) + str.substring(firstDot + 1).replace(".", "")
    }
    val dot = str.indexOf('.')
    var intPart = if (dot >= 0) str.substring(0, dot) else str
    val decPart: String? = if (dot >= 0) str.substring(dot + 1) else null
    // "046" -> "46"; a lone "0" (or "0." while starting a decimal) is kept.
    intPart = intPart.replace(Regex("^0+(?=[0-9])"), "")
    intPart = groupThousands(intPart)
    val formatted = if (decPart != null) "$intPart.$decPart" else intPart
    return localizeDigits(formatted)
}

fun formatAmountForInput(n: Double?): String {
    if (n == null || n.isNaN()) return ""
    return formatAmountTyping(safeNumberToString(n))
}

private val FLOAT_PREFIX = Regex("^[+-]?(?:[0-9]+\\.?[0-9]*|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?")

/** Number typed in an amount box (commas / Bangla digits allowed). NaN when there is no number. */
fun parseAmountInput(str: String?): Double {
    if (str == null || str.isEmpty()) return Double.NaN
    val cleaned = fromBnDigits(str).replace(",", "").trimStart()
    // JS parseFloat: takes the leading numeric part, ignores the rest.
    val m = FLOAT_PREFIX.find(cleaned) ?: return Double.NaN
    return m.value.toDoubleOrNull() ?: Double.NaN
}

/** Name fields never start with a digit: a leading run of 0-9 / ০-৯ is removed. */
fun stripLeadingDigits(str: String): String = str.replace(Regex("^[0-9০-৯]+"), "")
