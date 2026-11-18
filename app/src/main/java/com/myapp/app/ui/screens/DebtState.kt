package com.myapp.app.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.TextFieldValue
import com.myapp.app.data.AppState
import com.myapp.app.data.AppStore
import com.myapp.app.data.Debt
import com.myapp.app.data.JsDate
import com.myapp.app.util.amountTextMatchesQuery
import com.myapp.app.util.fmtMoney
import com.myapp.app.util.normalizeHyphenForSearch
import com.myapp.app.util.safeNumberToString
import java.util.Calendar

/** Priority + amount sort of ONE status tab (debtSortFilters[category][status]). */
class DebtSortFilter {
    /** default | keyperson | due */
    var priority by mutableStateOf("default")
    /** all | high | low */
    var amount by mutableStateOf("all")
}

/**
 * Everything the old app kept in globals for one of the Debts / Receivables tabs
 * (debtStatusFilters, debtSortFilters, debtDateFilters, searchFieldFilters + the search box).
 * Session only - it is not saved, a new app launch starts fresh like before.
 */
class DebtCategoryState {
    var search by mutableStateOf(TextFieldValue(""))
    /** person | note | amount */
    var searchField by mutableStateOf("person")
    /** pending | settled */
    var status by mutableStateOf("pending")

    val sortPending = DebtSortFilter()
    val sortSettled = DebtSortFilter()

    /** debtDateFilters: "month" (= no filter), "YYYY-MM" or "YYYY-MM-DD" - one per status tab. */
    private var datePending by mutableStateOf("month")
    private var dateSettled by mutableStateOf("month")

    val sort: DebtSortFilter get() = if (status == "settled") sortSettled else sortPending

    /** resetDebtCategoryFilters(category): Pending, no search / sort / date filter. */
    fun reset() {
        status = "pending"
        datePending = "month"
        dateSettled = "month"
        sortPending.priority = "default"; sortPending.amount = "all"
        sortSettled.priority = "default"; sortSettled.amount = "all"
        search = TextFieldValue("")
        searchField = "person"
    }

    var dateFilter: String
        get() = if (status == "settled") dateSettled else datePending
        set(v) { if (status == "settled") dateSettled = v else datePending = v }
}

object DebtUi {
    val debts = DebtCategoryState()
    val receivables = DebtCategoryState()

    fun of(category: String): DebtCategoryState = if (category == "receivables") receivables else debts
    fun typeOf(category: String): String = if (category == "receivables") "receivable" else "debt"
}

/**
 * Hooks the Debts/Receivables modals (Add/Edit, Payment, History, Delete) plug into.
 * Their screens are added after the list itself; until then the buttons do nothing.
 */
object DebtActions {
    var openHistory: (Long) -> Unit = {}
    var openEdit: (Long) -> Unit = {}
    var openPayment: (Long) -> Unit = {}
    var delete: (Long) -> Unit = {}

    /** Delete + run [onDeleted] only when the entry really was removed (the confirm may be cancelled). */
    var deleteThen: (Long, () -> Unit) -> Unit = { _, _ -> }
}

// ---------------------------------------------------------------------------------------------
// Money helpers (getDebtPaidAmount / getDebtRemaining)
// ---------------------------------------------------------------------------------------------

fun debtPaid(d: Debt): Double = d.payments.sumOf { it.amount }

fun debtRemaining(d: Debt): Double = maxOf(0.0, d.amount - debtPaid(d))

// ---------------------------------------------------------------------------------------------
// Filtering (port of getFilteredDebtEntries)
// ---------------------------------------------------------------------------------------------

private fun cal(ms: Long): Calendar = Calendar.getInstance().apply { timeInMillis = ms }

private fun sameDay(a: Long, b: Long): Boolean {
    val x = cal(a)
    val y = cal(b)
    return x.get(Calendar.YEAR) == y.get(Calendar.YEAR) && x.get(Calendar.DAY_OF_YEAR) == y.get(Calendar.DAY_OF_YEAR)
}

private val YMD_RE = Regex("^\\d{4}-\\d{2}-\\d{2}$")
private val YM_RE = Regex("^\\d{4}-\\d{2}$")

/** The date an entry counts under for history: when it was settled, else when it was created. */
fun debtHistoryMillis(d: Debt): Long? = JsDate.parseMillis(d.settledDate ?: d.createdDate)

fun filteredDebtEntries(state: AppState, category: String): List<Debt> {
    val cs = DebtUi.of(category)
    val type = DebtUi.typeOf(category)
    val query = normalizedLogsQuery(cs.search.text)
    val status = cs.status

    var entries = state.debts.filter { it.type == type }
    entries = when (status) {
        "pending" -> entries.filter { !it.settled }
        "settled" -> entries.filter { it.settled }
        else -> entries
    }

    val dateFilter = cs.dateFilter
    if (dateFilter != "month") {
        if (YMD_RE.matches(dateFilter)) {
            val target = JsDate.parseMillis(dateFilter)
            if (target != null) entries = entries.filter { d -> debtHistoryMillis(d)?.let { sameDay(it, target) } == true }
        } else if (YM_RE.matches(dateFilter)) {
            val p = dateFilter.split("-")
            val y = p[0].toInt()
            val m = p[1].toInt()
            entries = entries.filter { d ->
                val ms = debtHistoryMillis(d) ?: return@filter false
                val c = cal(ms)
                c.get(Calendar.YEAR) == y && c.get(Calendar.MONTH) + 1 == m
            }
        }
    }

    if (query.isNotEmpty()) {
        entries = when (cs.searchField) {
            "note" -> entries.filter { normalizeHyphenForSearch(it.note).lowercase().startsWith(query) }
            "amount" -> entries.filter {
                amountTextMatchesQuery(safeNumberToString(it.amount), query) ||
                    amountTextMatchesQuery(fmtMoney(it.amount), query)
            }
            else -> entries.filter { normalizeHyphenForSearch(it.person).lowercase().startsWith(query) }
        }
    }

    val sort = cs.sort
    val priorityMode = sort.priority
    val amountMode = sort.amount
    val amountOn = amountMode != "all"
    val amountDir = if (amountMode == "low") 1 else -1
    val amountOf: (Debt) -> Double = if (status == "settled") { d -> d.amount } else { d -> debtRemaining(d) }

    if (priorityMode == "keyperson") {
        return entries.filter { it.isKey }.sortedWith { a, b -> amountOf(b).compareTo(amountOf(a)) }
    }
    if (priorityMode == "due" && status == "pending") {
        entries = entries.filter { !it.dueDate.isNullOrEmpty() }
        if (amountOn) return entries.sortedWith { a, b -> (amountDir * (amountOf(a) - amountOf(b))).compareTo(0.0) }
        return entries.sortedWith(compareBy { JsDate.parseMillis(it.dueDate) ?: 0L })
    }
    if (amountOn) {
        return entries.sortedWith { a, b -> (amountDir * (amountOf(a) - amountOf(b))).compareTo(0.0) }
    }
    return entries.sortedWith { a, b ->
        val aMs = (if (a.settled) debtHistoryMillis(a) else JsDate.parseMillis(a.createdDate)) ?: 0L
        val bMs = (if (b.settled) debtHistoryMillis(b) else JsDate.parseMillis(b.createdDate)) ?: 0L
        bMs.compareTo(aMs)
    }
}

/** Total shown on the black card: everything still owed in this tab, ignoring every filter. */
fun debtTotalPending(state: AppState, category: String): Double {
    val type = DebtUi.typeOf(category)
    return state.debts.filter { it.type == type && !it.settled }.sumOf { debtRemaining(it) }
}

/** Dates (millis) of this tab's entries for the calendar dots (status tab applied, history date). */
fun debtEntryDatesFor(category: String): List<Long> {
    val cs = DebtUi.of(category)
    val type = DebtUi.typeOf(category)
    return AppStore.state.debts
        .filter { it.type == type && (if (cs.status == "settled") it.settled else !it.settled) }
        .mapNotNull { debtHistoryMillis(it) }
}

/** toggleDebtKeyPerson(id) */
fun toggleDebtKeyPerson(id: Long) {
    val d = AppStore.state.debts.firstOrNull { it.id == id } ?: return
    d.isKey = !d.isKey
    AppStore.commit()
}

/** to12Hour("HH:mm") -> (hour 1..12, minute, "AM"/"PM") */
fun to12Hour(time24: String?): Triple<Int, Int, String> {
    val parts = (time24 ?: "00:00").split(":")
    val h = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
    val ampm = if (h >= 12) "PM" else "AM"
    var h12 = h % 12
    if (h12 == 0) h12 = 12
    return Triple(h12, m, ampm)
}

/** isDebtInternal(entry): an own-fund deal - both sides are still existing funds of this person. */
fun isDebtInternal(e: Debt?): Boolean {
    if (e == null) return false
    val f = e.fund
    val cf = e.counterFund
    if (f.isNullOrEmpty() || cf.isNullOrEmpty()) return false
    val funds = AppStore.state.funds
    return funds.containsKey(f) && funds.containsKey(cf)
}

/** debtProgressColor(pct): hue 0 (red) -> 60 (yellow) -> 120 (green) over 0..100 % paid, hsl(h, 85%, 45%). */
fun debtProgressColor(pct: Double): Color =
    Color.hsl((pct.coerceIn(0.0, 100.0) * 1.2).toFloat(), 0.85f, 0.45f)
