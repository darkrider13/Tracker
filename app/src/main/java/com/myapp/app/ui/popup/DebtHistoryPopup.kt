package com.myapp.app.ui.popup

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.myapp.app.data.AppStore
import com.myapp.app.data.Debt
import com.myapp.app.data.JsDate
import com.myapp.app.ui.components.AppText
import com.myapp.app.ui.components.FaIcon
import com.myapp.app.ui.components.FundTypeIcon
import com.myapp.app.ui.components.PagedViewport
import com.myapp.app.ui.components.pagedItem
import com.myapp.app.ui.components.rememberPagedState
import com.myapp.app.ui.screens.DebtActions
import com.myapp.app.ui.screens.debtPaid
import com.myapp.app.ui.screens.debtProgressColor
import com.myapp.app.ui.screens.debtRemaining
import com.myapp.app.ui.theme.AppTheme
import com.myapp.app.ui.theme.Brand
import com.myapp.app.ui.theme.Tw
import com.myapp.app.util.DateOpts
import com.myapp.app.util.ensureFundMeta
import com.myapp.app.util.fmtDate
import com.myapp.app.util.fmtMoney
import com.myapp.app.util.fmtTime
import com.myapp.app.util.fundDisplayName
import com.myapp.app.util.localizeDigits
import com.myapp.app.util.parseCssColor
import com.myapp.app.util.tr
import com.myapp.app.util.trf

object DebtHistory {
    var entryId by mutableStateOf<Long?>(null)

    fun open(id: Long) {
        if (AppStore.state.debts.none { it.id == id }) return
        entryId = id
        Popups.debtHistory = true
    }

    fun close() { Popups.debtHistory = false }
}

/**
 * deleteDebtEntry(id): confirm, undo what the still-unpaid part did to the funds,
 * drop the history lines tied to the entry, then the entry itself.
 * [onDeleted] runs only when it really was deleted (the confirm may be cancelled, or a fund too low).
 */
fun deleteDebtEntry(id: Long, onDeleted: () -> Unit = {}) {
    val state = AppStore.state
    val entry = state.debts.firstOrNull { it.id == id } ?: return
    AppDialogs.confirm(
        message = tr("confirm_delete_record_permanently", "Delete this record permanently?"),
        icon = "fa-trash-alt",
    ) { ok ->
        if (ok != true) return@confirm
        val e = state.debts.firstOrNull { it.id == id } ?: return@confirm

        val f = e.fund
        if (!e.settled && !f.isNullOrEmpty() && state.funds.containsKey(f)) {
            // only the still-unpaid part is reversed
            val undo = debtRemaining(e)
            val cf = e.counterFund
            val hasCounter = !cf.isNullOrEmpty() && state.funds.containsKey(cf)
            val low = tr("alert_insufficient_balance_edit_fund", "Insufficient balance in that fund! Please edit fund/add cash first.")
            if (e.type == "receivable") {
                if (hasCounter && (state.funds[cf!!] ?: 0.0) < undo - 0.005) { AppDialogs.alert(low); return@confirm }
                state.funds[f] = (state.funds[f] ?: 0.0) + undo
                if (hasCounter) state.funds[cf!!] = (state.funds[cf] ?: 0.0) - undo
            } else {
                if ((state.funds[f] ?: 0.0) < undo - 0.005) { AppDialogs.alert(low); return@confirm }
                state.funds[f] = (state.funds[f] ?: 0.0) - undo
                if (hasCounter) state.funds[cf!!] = (state.funds[cf] ?: 0.0) + undo
            }
        }

        state.transactions = state.transactions.filter { it.debtEntryId != id }.toMutableList()
        state.debts = state.debts.filter { it.id != id }.toMutableList()
        AppStore.commit()
        onDeleted()
    }
}

@Composable
fun DebtHistoryPopup() {
    val c = AppTheme.colors
    PopupLayer(visible = Popups.debtHistory, scrim = 0.6f) {
        val state = AppStore.observe()
        val entry: Debt? = state.debts.firstOrNull { it.id == DebtHistory.entryId }
        // the entry vanished (deleted) while open: nothing to show
        if (entry == null) return@PopupLayer

        val paid = debtPaid(entry)
        val remaining = debtRemaining(entry)
        val pct = if (entry.amount > 0) minOf(100.0, paid / entry.amount * 100.0) else 0.0
        val progress = debtProgressColor(pct)
        val payments = entry.payments.sortedByDescending { JsDate.parseMillis(it.date) ?: 0L }

        GlassCard(radius = 28.dp, padding = 24.dp) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // name + subtitle + close
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        AppText(
                            text = entry.person, size = 18f, lineHeight = 28f, weight = FontWeight.ExtraBold,
                            letterSpacingEm = -0.025f, color = c.textGray900,
                        )
                        val typeLabel = if (entry.type == "receivable") tr("logs_receivables", "Receivable") else tr("logs_debts", "Debt")
                        AppText(
                            text = trf(
                                "debt_history_total_suffix",
                                "${if (entry.type == "receivable") "Receivable" else "Debt"} • ৳${fmtMoney(entry.amount)} total",
                                "type" to typeLabel, "amt" to fmtMoney(entry.amount),
                            ),
                            size = 12f, lineHeight = 16f, weight = FontWeight.Medium, color = c.textGray500,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(c.bgGray200a70, CircleShape)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { DebtHistory.close() },
                        contentAlignment = Alignment.Center,
                    ) { FaIcon("fa-xmark", 12.dp, c.textGray500) }
                }

                // progress box
                val boxShape = RoundedCornerShape(16.dp)
                Column(
                    Modifier.fillMaxWidth().clip(boxShape).background(c.bgGray100a70, boxShape).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(c.bgGray300a60, CircleShape)) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction = (pct / 100.0).toFloat().coerceIn(0f, 1f))
                                .background(progress),
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        AppText(
                            text = trf("debt_paid_amt", "Paid: ৳${fmtMoney(paid)}", "amt" to fmtMoney(paid)),
                            size = 11f, lineHeight = 16.5f, weight = FontWeight.Bold, color = Tw.green500,
                        )
                        AppText(
                            text = trf("debt_remaining_amt", "Remaining: ৳${fmtMoney(remaining)}", "amt" to fmtMoney(remaining)),
                            size = 11f, lineHeight = 16.5f, weight = FontWeight.Bold, color = Brand.red,
                        )
                    }
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        AppText(
                            text = trf(
                                "debt_pct_paid_suffix", "${"%.0f".format(pct)}% paid",
                                "pct" to localizeDigits("%.0f".format(pct)),
                            ) + (if (entry.settled) tr("debt_fully_settled_suffix", " • Fully Settled") else ""),
                            size = 12f, lineHeight = 16f, weight = FontWeight.Bold, color = progress, align = TextAlign.Center,
                        )
                    }
                }

                // payment history
                Column {
                    AppText(
                        text = tr("debt_payment_history_heading", "Payment History"), size = 12f, lineHeight = 16f,
                        weight = FontWeight.Bold, letterSpacingEm = 0.05f, color = c.textGray400, uppercase = true,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    if (payments.isEmpty()) {
                        Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                            AppText(
                                text = tr("debt_no_payments_yet", "No payments recorded yet."),
                                size = 12f, lineHeight = 16f, weight = FontWeight.Medium, color = c.textGray400,
                                align = TextAlign.Center,
                            )
                        }
                    } else {
                        // exactly 3 rows at a time; a scroll snaps a whole page of 3
                        val paged = rememberPagedState(visible = 3, gap = 8.dp)
                        PagedViewport(state = paged, count = payments.size, modifier = Modifier.fillMaxWidth()) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                payments.forEachIndexed { i, p ->
                                    key(i, p.date, p.amount) {
                                        val rowShape = RoundedCornerShape(12.dp)
                                        Row(
                                            Modifier
                                                .fillMaxWidth()
                                                .pagedItem(paged, i)
                                                .clip(rowShape)
                                                .background(c.bgGray100a70, rowShape)
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Row(
                                                Modifier.weight(1f),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            ) {
                                                AppText(
                                                    text = fmtDate(p.date, DateOpts(month = "short", day = "2-digit", year = "numeric")) +
                                                        " • " + fmtTime(p.date),
                                                    size = 8f, lineHeight = 12f, weight = FontWeight.Bold,
                                                    color = Tw.green600, noWrap = true,
                                                )
                                                val pf = p.fund
                                                if (!pf.isNullOrEmpty() && state.funds.containsKey(pf)) {
                                                    val meta = ensureFundMeta(pf)
                                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                                        FundTypeIcon(meta.type, meta.icon, 8.dp, parseCssColor(meta.color) ?: Brand.red)
                                                        AppText(
                                                            text = fundDisplayName(pf), size = 8f, lineHeight = 12f,
                                                            weight = FontWeight.Bold, color = c.textGray500, singleLineEllipsis = true,
                                                        )
                                                    }
                                                }
                                            }
                                            AppText(
                                                text = "৳" + fmtMoney(p.amount), size = 10f, lineHeight = 15f,
                                                weight = FontWeight.Black, color = Tw.green600, noWrap = true,
                                                modifier = Modifier.padding(start = 8.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun installDebtHistoryHooks() {
    DebtActions.openHistory = { id -> DebtHistory.open(id) }
    DebtActions.delete = { id -> deleteDebtEntry(id) }
    DebtActions.deleteThen = { id, done -> deleteDebtEntry(id, done) }
}
