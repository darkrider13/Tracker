package com.myapp.app.ui.popup

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.myapp.app.data.AppStore
import com.myapp.app.data.JsDate
import com.myapp.app.data.Transaction
import com.myapp.app.ui.AppUi
import com.myapp.app.ui.components.AppText
import com.myapp.app.ui.components.FaIcon
import com.myapp.app.ui.components.formatAmountField
import com.myapp.app.ui.theme.AppTheme
import com.myapp.app.ui.theme.Brand
import com.myapp.app.ui.theme.Tw
import com.myapp.app.util.formatAmountForInput
import com.myapp.app.util.parseAmountInput
import com.myapp.app.util.tr

/** Round white trash button shown in the header of the edit popups. */
@Composable
internal fun HeaderDeleteButton(onClick: () -> Unit) {
    val c = AppTheme.colors
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(c.bgWhite, CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) { FaIcon("fa-trash-alt", 12.dp, Brand.red) }
}

@Composable
internal fun SmallFieldLabel(text: String) {
    AppText(
        text = text,
        size = 9f,
        weight = FontWeight.Bold,
        letterSpacingEm = 0.05f,
        color = AppTheme.colors.textGray400,
        uppercase = true,
    )
}

// ---------------------------------------------------------------------------------------------
// Add Money (also the edit popup of an "Add Money" history line)

object AddMoney {
    var amount by mutableStateOf(TextFieldValue(""))
    var description by mutableStateOf(TextFieldValue(""))
    var editingId by mutableStateOf<Long?>(null)

    /** openAddMoneyModal(): adds to the fund whose details are open. */
    fun open() {
        if (AppUi.currentDetailsFund == null) return
        editingId = null
        amount = TextFieldValue("")
        description = TextFieldValue("")
        Popups.addMoney = true
    }

    /** openEditAddMoneyModal(txId) */
    fun openEdit(txId: Long) {
        val tx = AppStore.state.transactions.firstOrNull { it.id == txId }
        if (tx == null || tx.type != "add_money") return
        if (!isAddMoneyEditable(tx)) {
            AppDialogs.alert(tr("add_money_locked_alert", "This entry can't be edited: it is more than 1 day old, or another transaction has happened on this fund since."))
            return
        }
        editingId = txId
        amount = TextFieldValue(formatAmountForInput(tx.amount))
        description = TextFieldValue(tx.description)
        Popups.addMoney = true
    }

    fun close() {
        Popups.addMoney = false
        Popups.afterFade { if (!Popups.addMoney) editingId = null }
    }

    /** saveAddMoney() */
    fun save() {
        val state = AppStore.state
        val value = parseAmountInput(amount.text)
        val note = description.text.trim()
        if (value.isNaN() || value <= 0) {
            AppDialogs.alert(tr("alert_valid_amount", "Please enter a valid amount!"))
            return
        }
        val editId = editingId
        if (editId != null) {
            val tx = state.transactions.firstOrNull { it.id == editId }
            if (tx == null) {
                close()
                return
            }
            if (!isAddMoneyEditable(tx)) {
                close()
                return
            }
            val bal = state.funds[tx.fund]
            if (bal != null) state.funds[tx.fund] = bal - tx.amount + value
            tx.amount = value
            tx.description = note
        } else {
            val fund = AppUi.currentDetailsFund
            val bal = if (fund != null) state.funds[fund] else null
            if (fund == null || bal == null) {
                close()
                return
            }
            state.funds[fund] = bal + value
            state.transactions.add(
                0,
                Transaction(
                    id = System.currentTimeMillis(),
                    type = "add_money",
                    fund = fund,
                    category = "Add Money",
                    description = note,
                    amount = value,
                    date = JsDate.nowIso(),
                ),
            )
        }
        close()
        AppStore.commit()
    }
}

@Composable
fun AddMoneyPopup() {
    val c = AppTheme.colors
    PopupLayer(visible = Popups.addMoney, scrim = 0.6f) {
        GlassCard(radius = 24.dp, padding = 20.dp) {
            val editing = AddMoney.editingId != null
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PopupHeaderIcon("fa-plus", 28.dp, 10.dp, Tw.green500)
                    AppText(
                        text = if (editing) tr("modal_edit_amount_title", "Edit Amount") else tr("modal_add_money_title", "Add Money"),
                        size = 18f,
                        lineHeight = 28f,
                        weight = FontWeight.ExtraBold,
                        letterSpacingEm = -0.025f,
                        color = c.textGray900,
                        modifier = Modifier.weight(1f),
                    )
                    if (editing) HeaderDeleteButton { TxActions.deleteTransaction(AddMoney.editingId) }
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppTextField(
                        value = AddMoney.amount,
                        onValueChange = { AddMoney.amount = formatAmountField(it) },
                        placeholder = tr("modal_amount_placeholder", "Amount (৳)"),
                        keyboardType = KeyboardType.Decimal,
                        focusColor = Tw.green500,
                    )
                    AppTextField(
                        value = AddMoney.description,
                        onValueChange = { AddMoney.description = it },
                        placeholder = tr("modal_note_optional_placeholder", "Note (optional)"),
                        focusColor = Tw.green500,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ModalButton(tr("common_cancel", "Cancel"), ModalButtonKind.Secondary, Modifier.weight(1f)) { AddMoney.close() }
                        ModalButton(
                            text = if (editing) tr("common_save", "Save") else tr("modal_add_btn", "Add"),
                            kind = ModalButtonKind.GreenLight,
                            modifier = Modifier.weight(1f),
                        ) { AddMoney.save() }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Transfer Fund

object TransferFund {
    var amount by mutableStateOf(TextFieldValue(""))
    var description by mutableStateOf(TextFieldValue(""))
    var from by mutableStateOf("")
    var to by mutableStateOf("")

    /** openTransferFundModal(presetFromFund) */
    fun open(presetFromFund: String? = null) {
        val state = AppStore.state
        val names = state.funds.keys.toList()
        if (names.size < 2) {
            AppDialogs.alert(tr("alert_need_2_accounts", "You need at least 2 funds to make a transfer. Add another fund first."))
            return
        }
        val fromFund = if (presetFromFund != null && state.funds.containsKey(presetFromFund)) presetFromFund
        else (if (state.activeFund.isNotEmpty()) state.activeFund else names[0])
        val toFund = names.firstOrNull { it != fromFund } ?: names[0]
        amount = TextFieldValue("")
        description = TextFieldValue("")
        from = fromFund
        to = toFund
        FundDropdownState.close()
        Popups.transferFund = true
    }

    fun close() {
        Popups.transferFund = false
        FundDropdownState.close()
    }

    /** From/To are paired: picking the fund that sits on the other side swaps them. */
    fun pickFrom(fund: String) {
        val previous = from
        from = fund
        if (to == fund && previous.isNotEmpty() && previous != fund) to = previous
    }

    fun pickTo(fund: String) {
        val previous = to
        to = fund
        if (from == fund && previous.isNotEmpty() && previous != fund) from = previous
    }

    /** saveFundTransfer() */
    fun save() {
        val state = AppStore.state
        val value = parseAmountInput(amount.text)
        val note = description.text.trim()
        val fromFund = from
        val toFund = to

        if (value.isNaN() || value <= 0) {
            AppDialogs.alert(tr("alert_valid_amount", "Please enter a valid amount!"))
            return
        }
        if (fromFund.isEmpty() || toFund.isEmpty() || fromFund == toFund) {
            AppDialogs.alert(tr("alert_choose_two_different_accounts", "Please choose two different funds to transfer between!"))
            return
        }
        if ((state.funds[fromFund] ?: 0.0) < value) {
            AppDialogs.alert(tr("alert_insufficient_source_balance", "Insufficient balance in the source fund!"))
            return
        }

        state.funds[fromFund] = (state.funds[fromFund] ?: 0.0) - value
        state.funds[toFund] = (state.funds[toFund] ?: 0.0) + value

        // Two paired entries sharing one transferId: outgoing (source fund) and incoming mirror (destination).
        val transferId = System.currentTimeMillis()
        val now = JsDate.nowIso()
        state.transactions.add(
            0,
            Transaction(
                id = transferId + 1,
                transferId = transferId,
                type = "transfer_in",
                fund = toFund,
                linkedFund = fromFund,
                category = "Fund Transfer",
                description = note,
                amount = value,
                date = now,
            ),
        )
        state.transactions.add(
            0,
            Transaction(
                id = transferId,
                transferId = transferId,
                type = "transfer_out",
                fund = fromFund,
                linkedFund = toFund,
                category = "Fund Transfer",
                description = note,
                amount = value,
                date = now,
            ),
        )
        close()
        AppStore.commit()
    }
}

@Composable
fun TransferFundPopup() {
    val c = AppTheme.colors
    PopupLayer(visible = Popups.transferFund, scrim = 0.6f) {
        GlassCard(
            radius = 24.dp,
            padding = 20.dp,
            overlay = {
                FundDropdownOverlay("transfer-from", TransferFund.from, compact = true, tight = true) { TransferFund.pickFrom(it) }
                FundDropdownOverlay("transfer-to", TransferFund.to, compact = true, tight = true) { TransferFund.pickTo(it) }
            },
        ) {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PopupHeaderIcon("fa-right-left", 28.dp, 10.dp, Brand.red)
                    AppText(
                        text = tr("modal_transfer_fund_title", "Transfer Fund"),
                        size = 18f,
                        lineHeight = 28f,
                        weight = FontWeight.ExtraBold,
                        letterSpacingEm = -0.025f,
                        color = c.textGray900,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppTextField(
                        value = TransferFund.amount,
                        onValueChange = { TransferFund.amount = formatAmountField(it) },
                        placeholder = tr("modal_amount_placeholder", "Amount (৳)"),
                        keyboardType = KeyboardType.Decimal,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        SmallFieldLabel(tr("modal_from_account", "From Fund"))
                        FundSelectButton("transfer-from", TransferFund.from, variant = FundButtonVariant.Transfer, textColor = Brand.red)
                    }
                    Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp), contentAlignment = Alignment.Center) {
                        FaIcon("fa-arrow-down", 12.dp, c.textGray400)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        SmallFieldLabel(tr("modal_to_account", "To Fund"))
                        FundSelectButton("transfer-to", TransferFund.to, variant = FundButtonVariant.Transfer, textColor = Brand.green)
                    }
                    AppTextField(
                        value = TransferFund.description,
                        onValueChange = { TransferFund.description = it },
                        placeholder = tr("modal_note_optional_placeholder", "Note (optional)"),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ModalButton(tr("common_cancel", "Cancel"), ModalButtonKind.Secondary, Modifier.weight(1f)) { TransferFund.close() }
                        ModalButton(tr("home_transfer", "Transfer"), ModalButtonKind.Red, Modifier.weight(1f)) { TransferFund.save() }
                    }
                }
            }
        }
    }
}
