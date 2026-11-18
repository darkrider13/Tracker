package com.myapp.app.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import com.myapp.app.data.AppState
import com.myapp.app.data.AppStore
import com.myapp.app.data.JsDate
import com.myapp.app.data.Transaction
import com.myapp.app.util.amountTextMatchesQuery
import com.myapp.app.util.categorySearchLabels
import com.myapp.app.util.debtPaymentNoteText
import com.myapp.app.util.fmtMoney
import com.myapp.app.util.fromBnDigits
import com.myapp.app.util.fundDisplayName
import com.myapp.app.util.normalizeHyphenForSearch
import com.myapp.app.util.safeNumberToString
import com.myapp.app.util.trf
import java.util.Calendar

/**
 * Screen state of the Logs tab that the old app kept in globals
 * (searchFieldFilters, the search box text, the staged filter option).
 * Nothing here is saved - it resets on every launch, like before.
 * The saved part (sort / time / price / customDate / customMonth) lives in AppState.filters.
 */
object LogsState {
    /** Text of the Transactions search box (#logs-search-input). */
    var searchText by mutableStateOf(TextFieldValue(""))

    /** searchFieldFilters.transactions: category (default) | description | amount. */
    var searchField by mutableStateOf("category")

    // ---- the shared filter picker popup (popup-filter-picker) ----
    /** 'main' (sort/time/price) | 'searchField' (more modes are added by Step 7). */
    var pendingMode by mutableStateOf<String?>(null)
    var pendingValue by mutableStateOf("")
    var pendingLabel by mutableStateOf<String?>(null)

    /** Which of sort / time / price the 'main' picker is editing (activeFilterType). */
    var activeFilterType by mutableStateOf("sort")

    /** Title text shown at the top of the picker + whether it is the small "headline" size. */
    var pickerTitle by mutableStateOf("")
    var pickerTitleIsHeadline by mutableStateOf(false)

    /** transactions | debts | receivables - which list the open picker belongs to. */
    var pickerCategory by mutableStateOf("transactions")

    /** Optional icon in front of an option label (the Priority picker's star / clock). */
    var pickerIcons by mutableStateOf<Map<String, Pair<String, com.myapp.app.ui.components.FaStyle>>>(emptyMap())

    /** The option rows of the open picker: value to label. */
    var pickerOptions by mutableStateOf<List<Pair<String, String>>>(emptyList())
}

// ---------------------------------------------------------------------------------------------
// Dates (JS `new Date(t.date)` semantics, local time zone)
// ---------------------------------------------------------------------------------------------

private fun cal(millis: Long): Calendar = Calendar.getInstance().apply { timeInMillis = millis }

private fun sameDay(a: Calendar, b: Calendar): Boolean =
    a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
        a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

/** Local midnight of this week's Sunday (the old `new Date(y, m, d - now.getDay())`). */
fun logsWeekStartMillis(now: Calendar = Calendar.getInstance()): Long {
    val c = now.clone() as Calendar
    c.set(Calendar.HOUR_OF_DAY, 0)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    c.add(Calendar.DAY_OF_MONTH, -(c.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY))
    return c.timeInMillis
}

/** "yyyy-MM" key of a moment in local time (the old customMonth key). */
fun monthKeyOf(millis: Long): String {
    val c = cal(millis)
    return "%04d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1)
}

/**
 * The time part of the Transactions filter: today | week | month | custom | custommonth.
 * Used by the list AND by "clear history" so both always cover exactly the same rows.
 */
fun matchesLogsTime(t: Transaction, timeVal: String, state: AppState, now: Calendar = Calendar.getInstance()): Boolean {
    val ms = JsDate.parseMillis(t.date) ?: return false
    val d = cal(ms)
    return when {
        timeVal == "today" -> sameDay(d, now)
        timeVal == "week" -> ms >= logsWeekStartMillis(now)
        timeVal == "month" -> d.get(Calendar.MONTH) == now.get(Calendar.MONTH) && d.get(Calendar.YEAR) == now.get(Calendar.YEAR)
        timeVal == "custom" && state.filters.customDate != null -> {
            val target = JsDate.parseMillis(state.filters.customDate) ?: return false
            sameDay(d, cal(target))
        }
        timeVal == "custommonth" && state.filters.customMonth != null -> monthKeyOf(ms) == state.filters.customMonth
        // unknown value / custom without a date: the old code leaves the list unfiltered
        else -> true
    }
}

// ---------------------------------------------------------------------------------------------
// Note text + search
// ---------------------------------------------------------------------------------------------

/** The old getTxNoteText(t): what the card shows as the note (live-translated for debt lines). */
fun getTxNoteText(t: Transaction): String {
    debtPaymentNoteText(t)?.let { return it }
    if (t.description.isNotEmpty()) return t.description
    if (t.type == "add_money") {
        val f = fundDisplayName(t.fund)
        return trf("home_added_to_fund", "Added to $f", "fund" to f)
    }
    return ""
}

/** What the user typed, normalised the way renderLogsList did (Bangla digits -> ASCII, hyphen -> space). */
fun normalizedLogsQuery(raw: String): String =
    normalizeHyphenForSearch(fromBnDigits(raw)).lowercase().trim()

/**
 * The rows of the Transactions list: active fund, not hidden, search, time filter, then sort.
 * Straight port of renderLogsList().
 */
fun logsFilteredTransactions(state: AppState, query: String, searchField: String): List<Transaction> {
    val f = state.filters
    var list = state.transactions.filter { it.fund == state.activeFund && it.historyHidden != true }

    if (query.isNotEmpty()) {
        list = when (searchField) {
            "description" -> list.filter { normalizeHyphenForSearch(getTxNoteText(it)).lowercase().startsWith(query) }
            "amount" -> list.filter {
                amountTextMatchesQuery(safeNumberToString(it.amount), query) ||
                    amountTextMatchesQuery(fmtMoney(it.amount), query)
            }
            else -> list.filter { t ->
                categorySearchLabels(t.category).any { normalizeHyphenForSearch(it).lowercase().startsWith(query) }
            }
        }
    }

    val now = Calendar.getInstance()
    list = list.filter { matchesLogsTime(it, f.time, state, now) }

    fun ms(t: Transaction) = JsDate.parseMillis(t.date) ?: 0L
    return when {
        f.price == "high" -> list.sortedWith(compareByDescending<Transaction> { it.amount }.thenByDescending { it.id })
        f.price == "low" -> list.sortedWith(compareBy<Transaction> { it.amount }.thenBy { it.id })
        f.sort == "oldest" -> list.sortedWith(compareBy<Transaction> { ms(it) }.thenBy { it.id })
        else -> list.sortedWith(compareByDescending<Transaction> { ms(it) }.thenByDescending { it.id })
    }
}

/**
 * The old highlightMatch(): colours the FIRST occurrence of [query] in [text]
 * (hyphens count as spaces when locating it, the shown text keeps its hyphens).
 */
fun highlightMatch(text: String, query: String, color: Color): AnnotatedString {
    if (query.isEmpty()) return AnnotatedString(text)
    val lowerText = normalizeHyphenForSearch(text).lowercase()
    val lowerQuery = normalizeHyphenForSearch(query).lowercase()
    val idx = lowerText.indexOf(lowerQuery)
    if (idx == -1) return AnnotatedString(text)
    val end = (idx + query.length).coerceAtMost(text.length)
    return buildAnnotatedString {
        append(text.substring(0, idx))
        withStyle(SpanStyle(color = color)) { append(text.substring(idx, end)) }
        append(text.substring(end))
    }
}

/** True when the saved Logs filters are not the defaults (drives the small red dots on the chips). */
fun logsSortIsActive(s: AppState) = s.filters.sort != "newest"
fun logsTimeIsActive(s: AppState) = s.filters.time != "month"
fun logsPriceIsActive(s: AppState) = s.filters.price != "all"

/** Clear History (trash button): the rows currently covered by the Time filter for the active fund. */
fun logsClearTargetIds(): Set<Long> {
    val s = AppStore.state
    val now = Calendar.getInstance()
    val f = s.filters
    // The old clear code falls back to the REAL current month for anything it cannot use
    // (unknown value, or custom / custommonth without its date).
    val effective = when {
        f.time == "today" || f.time == "week" -> f.time
        f.time == "custom" && f.customDate != null -> "custom"
        f.time == "custommonth" && f.customMonth != null -> "custommonth"
        else -> "month"
    }
    return s.transactions
        .filter { it.fund == s.activeFund && matchesLogsTime(it, effective, s, now) }
        .map { it.id }
        .toSet()
}
