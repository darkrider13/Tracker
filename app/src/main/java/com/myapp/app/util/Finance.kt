package com.myapp.app.util

import com.myapp.app.data.AppStore
import com.myapp.app.data.Debt
import com.myapp.app.data.JsDate
import com.myapp.app.data.Transaction
import java.util.Calendar
import kotlin.math.max

/** True for lines that count as spending (not transfers, added money, lending or debt repayment). */
fun isSpendTx(t: Transaction?): Boolean {
    if (t == null) return false
    val type = t.type
    if (type == "transfer_in" || type == "transfer_out" || type == "add_money" || type == "debt_lent") return false
    if (t.category == "Debt Repayment") return false
    return true
}

fun getDebtPaidAmount(d: Debt): Double = d.payments.sumOf { it.amount }

fun getDebtRemaining(d: Debt): Double = max(0.0, d.amount - getDebtPaidAmount(d))

/** Balance of the active fund and how much was spent from it in the current calendar month. */
class HomeFinance(val activeBalance: Double, val spentThisMonth: Double)

/** The old calculateFinance(): reads the data, never shows stale numbers. */
fun calculateFinance(): HomeFinance {
    val state = AppStore.state
    val balance = state.funds[state.activeFund] ?: 0.0
    val now = Calendar.getInstance()
    val cal = Calendar.getInstance()
    var spent = 0.0
    for (t in state.transactions) {
        if (t.fund != state.activeFund || !isSpendTx(t)) continue
        val ms = JsDate.parseMillis(t.date) ?: continue
        cal.timeInMillis = ms
        if (cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) && cal.get(Calendar.MONTH) == now.get(Calendar.MONTH)) {
            spent += t.amount
        }
    }
    return HomeFinance(balance, spent)
}
