package com.myapp.app.ui.popup

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.myapp.app.data.AppStore
import com.myapp.app.data.Debt
import com.myapp.app.data.DebtPayment
import com.myapp.app.data.JsDate
import com.myapp.app.data.Transaction
import com.myapp.app.ui.AppActions
import com.myapp.app.ui.components.AppSwitch
import com.myapp.app.ui.components.AppText
import com.myapp.app.ui.components.appTextStyle
import com.myapp.app.ui.components.formatAmountField
import com.myapp.app.ui.screens.DebtActions
import com.myapp.app.ui.screens.debtRemaining
import com.myapp.app.ui.screens.isDebtInternal
import com.myapp.app.ui.theme.AppTheme
import com.myapp.app.ui.theme.Brand
import com.myapp.app.ui.theme.Tw
import com.myapp.app.util.fmtMoney
import com.myapp.app.util.formatAmountForInput
import com.myapp.app.util.fundDisplayName
import com.myapp.app.util.parseAmountInput
import com.myapp.app.util.safeToFixed
import com.myapp.app.util.tr
import com.myapp.app.util.trf

object DebtPaymentModal {
    var entryId by mutableStateOf<Long?>(null)
    var amount by mutableStateOf(TextFieldValue(""))

    var fundSectionVisible by mutableStateOf(false)
    var fundOn by mutableStateOf(false)
    var fundToggleHidden by mutableStateOf(false)
    var fundToggleDisabled by mutableStateOf(false)
    var fundPickerLocked by mutableStateOf(false)
    var fundLabel by mutableStateOf("")
    var payFund by mutableStateOf("")

    private val state get() = AppStore.state
    private val entry: Debt? get() = state.debts.firstOrNull { it.id == entryId }

    val title: String
        get() = if (entry?.type == "receivable") tr("debt_payment_received_title", "Record Payment Received")
        else tr("debt_payment_made_title", "Record Payment Made")

    /** openDebtPaymentModal(id) */
    fun open(id: Long) {
        val e = state.debts.firstOrNull { it.id == id } ?: return
        entryId = id
        amount = TextFieldValue("")
        val hasFunds = state.funds.isNotEmpty()
        fundSectionVisible = hasFunds
        fundOn = false
        fundToggleHidden = false
        fundToggleDisabled = false
        fundPickerLocked = false
        payFund = ""
        fundLabel = if (e.type == "receivable") tr("debt_payment_fund_toggle_receivable", "Add this to a fund?")
        else tr("debt_payment_fund_toggle_debt", "Pay this from a fund?")

        // Opened from a Fund Details screen: lock to that fund (unless the entry belongs to another one).
        val ctx = AddDebt.fundContext
        val lockFund = if (ctx != null && state.funds.containsKey(ctx) && (e.fund.isNullOrEmpty() || e.fund == ctx)) ctx else null
        if (lockFund != null) {
            fundOn = true
            fundToggleHidden = true
            fundSectionVisible = true
            fundLabel = if (e.type == "receivable") tr("debt_payment_fund_locked_receivable", "This money will be added to this fund")
            else tr("debt_payment_fund_locked_debt", "This money will be taken from this fund")
            payFund = lockFund
            fundPickerLocked = true
            // the entry already carries this fund, or it is an own-fund deal: nothing left to ask
            if ((!e.fund.isNullOrEmpty() && e.fund == lockFund) || isDebtInternal(e)) fundSectionVisible = false
            Popups.debtPayment = true
            return
        }
        val ef = e.fund
        if (!ef.isNullOrEmpty() && state.funds.containsKey(ef)) {
            fundOn = true
            fundToggleDisabled = true
            payFund = ef
            // the fund was chosen when the entry was created: used silently, no question
            fundSectionVisible = false
        }
        if (isDebtInternal(e)) fundSectionVisible = false
        Popups.debtPayment = true
    }

    fun close() {
        Popups.debtPayment = false
        FundDropdownState.close()
    }

    fun toggleFund(checked: Boolean) {
        fundOn = checked
        if (!checked) { payFund = ""; return }
        if (payFund.isNotEmpty()) return
        val active = state.activeFund
        val def = if (state.funds.containsKey(active)) active else state.funds.keys.firstOrNull()
        if (def != null) payFund = def
    }

    fun pickFund(name: String) { payFund = name }

    /** fillFullRemainingPayment() */
    fun fillFullRemaining() {
        val e = entry ?: return
        amount = TextFieldValue(formatAmountForInput(safeToFixed(debtRemaining(e), 2).toDouble()))
    }

    /** submitDebtPayment() */
    fun submit() {
        val e = entry ?: return
        val amt = parseAmountInput(amount.text)
        if (amt <= 0) {
            AppDialogs.alert(tr("alert_valid_amount", "Please enter a valid amount!")); return
        }
        val remaining = debtRemaining(e)
        if (amt > remaining + 0.005) {
            AppDialogs.alert(
                trf(
                    "alert_amount_above_remaining",
                    "That's more than the remaining balance (৳${fmtMoney(remaining)}). Please enter an amount up to the remaining balance.",
                    "amt" to fmtMoney(remaining),
                ),
            )
            return
        }

        // own-fund deal: money always moves back between the two funds
        if (isDebtInternal(e)) {
            val fromFund = if (e.type == "receivable") e.counterFund!! else e.fund!!
            val toFund = if (e.type == "receivable") e.fund!! else e.counterFund!!
            val fromBal = state.funds[fromFund] ?: 0.0
            if (fromBal < amt - 0.005) {
                AppDialogs.alert(
                    trf(
                        "alert_own_fund_insufficient",
                        "\"${fundDisplayName(fromFund)}\" only has ৳${fmtMoney(fromBal)}, so ৳${fmtMoney(amt)} can't be given!",
                        "fund" to fundDisplayName(fromFund), "bal" to fmtMoney(fromBal), "amt" to fmtMoney(amt),
                    ),
                )
                return
            }
            val nowIso = JsDate.nowIso()
            val baseId = System.currentTimeMillis()
            state.funds[fromFund] = fromBal - amt
            state.funds[toFund] = (state.funds[toFund] ?: 0.0) + amt
            val toName = fundDisplayName(toFund)
            val fromName = fundDisplayName(fromFund)
            state.transactions.add(
                0,
                Transaction(
                    id = baseId + 1, type = "add_money", fund = toFund, category = "Debt Collection",
                    description = trf("debt_note_received_from", "Received from $fromName", "name" to fromName),
                    debtNoteKind = "received_from", debtNotePerson = fromName,
                    amount = amt, date = nowIso, fromDebtPayment = true, debtPaymentEntryId = e.id,
                ),
            )
            state.transactions.add(
                0,
                Transaction(
                    id = baseId, fund = fromFund, category = "Debt Repayment",
                    description = trf("debt_note_paid_back_to", "Paid back to $toName", "name" to toName),
                    debtNoteKind = "paid_back_to", debtNotePerson = toName,
                    amount = amt, date = nowIso, fromDebtPayment = true, debtPaymentEntryId = e.id,
                ),
            )
            e.payments.add(
                DebtPayment(
                    amount = amt, date = nowIso, fund = e.fund, internal = true,
                    fromFund = fromFund, toFund = toFund, txId = baseId, txIds = mutableListOf(baseId, baseId + 1),
                ),
            )
            if (debtRemaining(e) <= 0.005) { e.settled = true; e.settledDate = nowIso }
            close()
            AppStore.commit()
            return
        }

        var paymentFund: String? = null
        var payTx: Transaction? = null
        if (fundOn && payFund.isNotEmpty() && state.funds.containsKey(payFund)) {
            val bal = state.funds[payFund] ?: 0.0
            if (e.type != "receivable" && bal < amt) {
                AppDialogs.alert(
                    tr("alert_insufficient_balance_edit_fund", "Insufficient balance in that fund! Please edit fund/add cash first."),
                )
                return
            }
            paymentFund = payFund
            val now = JsDate.nowIso()
            if (e.type == "receivable") {
                state.funds[payFund] = bal + amt
                payTx = Transaction(
                    id = System.currentTimeMillis(), type = "add_money", fund = payFund, category = "Debt Collection",
                    description = trf("debt_note_received_from", "Received from ${e.person}", "name" to e.person),
                    debtNoteKind = "received_from", debtNotePerson = e.person,
                    amount = amt, date = now, fromDebtPayment = true,
                )
            } else {
                state.funds[payFund] = bal - amt
                payTx = Transaction(
                    id = System.currentTimeMillis(), fund = payFund, category = "Debt Repayment",
                    description = trf("debt_note_paid_back_to", "Paid back to ${e.person}", "name" to e.person),
                    debtNoteKind = "paid_back_to", debtNotePerson = e.person,
                    amount = amt, date = now, fromDebtPayment = true,
                )
            }
            state.transactions.add(0, payTx)
        }

        val record = DebtPayment(amount = amt, date = JsDate.nowIso(), fund = paymentFund)
        if (paymentFund != null) {
            val first = state.transactions.firstOrNull()
            if (first != null && first.fromDebtPayment == true && first.debtEntryId == null) {
                first.debtPaymentEntryId = e.id
                record.txId = first.id
            }
        }
        e.payments.add(record)

        if (debtRemaining(e) <= 0.005) {
            e.settled = true
            e.settledDate = JsDate.nowIso()
        }
        close()
        AppStore.commit()
    }
}

@Composable
fun DebtPaymentPopup() {
    val c = AppTheme.colors
    PopupLayer(visible = Popups.debtPayment, scrim = 0.6f) {
        val p = DebtPaymentModal
        val state = AppStore.observe()
        val entry = state.debts.firstOrNull { it.id == p.entryId }
        GlassCard(
            radius = 28.dp,
            padding = 24.dp,
            overlay = { FundDropdownOverlay("debt-payment", p.payFund) { DebtPaymentModal.pickFund(it) } },
        ) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PopupHeaderIcon("fa-hand-holding-dollar", 32.dp, 12.dp, Brand.red)
                    AppText(
                        text = p.title, size = 18f, lineHeight = 28f, weight = FontWeight.ExtraBold,
                        letterSpacingEm = -0.025f, color = c.textGray900,
                    )
                }

                // "Name • Remaining: ৳x"  +  "Pay remaining full amount"
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    val remaining = if (entry != null) debtRemaining(entry) else 0.0
                    val line = buildAnnotatedString {
                        append((entry?.person ?: "") + " • ")
                        withStyle(SpanStyle(color = Brand.red, fontWeight = FontWeight.Bold)) {
                            append(trf("debt_remaining_amt", "Remaining: ৳${fmtMoney(remaining)}", "amt" to fmtMoney(remaining)))
                        }
                    }
                    BasicText(
                        text = line,
                        style = appTextStyle(12f, c.textGray500, FontWeight.Medium, lineHeight = 16f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Row(
                        Modifier
                            .clip(CircleShape)
                            .background(Tw.green600, CircleShape)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                DebtPaymentModal.fillFullRemaining()
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        AppText(
                            text = tr("debt_pay_full_remaining_btn", "Pay remaining full amount"),
                            size = 9f, lineHeight = 13.5f, weight = FontWeight.Bold, color = Color.White, noWrap = true,
                        )
                    }
                }

                AppTextField(
                    value = p.amount,
                    onValueChange = { DebtPaymentModal.amount = formatAmountField(it) },
                    placeholder = tr("modal_amount_placeholder", "Amount (৳)"),
                    keyboardType = KeyboardType.Decimal,
                )

                if (p.fundSectionVisible) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppText(
                                text = p.fundLabel, size = 12f, lineHeight = 16f, weight = FontWeight.Bold,
                                color = c.textGray600, modifier = Modifier.weight(1f),
                            )
                            if (!p.fundToggleHidden) {
                                AppSwitch(checked = p.fundOn, onCheckedChange = { DebtPaymentModal.toggleFund(it) }, enabled = !p.fundToggleDisabled)
                            }
                        }
                        if (p.fundOn && p.payFund.isNotEmpty()) {
                            FundSelectButton(
                                context = "debt-payment", selectedFund = p.payFund,
                                variant = FundButtonVariant.Debt, enabled = !p.fundPickerLocked,
                            )
                        }
                    }
                }

                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModalButton(tr("common_cancel", "Cancel"), ModalButtonKind.Secondary, Modifier.weight(1f)) { DebtPaymentModal.close() }
                    ModalButton(tr("common_confirm", "Confirm"), ModalButtonKind.Red, Modifier.weight(1f)) { DebtPaymentModal.submit() }
                }
            }
        }
    }
}

fun installDebtPaymentHooks() {
    DebtActions.openPayment = { id -> DebtPaymentModal.open(id) }
}
