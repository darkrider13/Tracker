package com.myapp.app.ui.popup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.myapp.app.data.AppStore
import com.myapp.app.ui.components.AppText
import com.myapp.app.ui.components.FaIcon
import com.myapp.app.ui.components.formatAmountField
import com.myapp.app.ui.components.negativeTopMargin
import com.myapp.app.ui.components.stripLeadingDigitsField
import com.myapp.app.ui.theme.AppTheme
import com.myapp.app.ui.theme.Brand
import com.myapp.app.util.categoryDisplayName
import com.myapp.app.util.formatAmountForInput
import com.myapp.app.util.parseAmountInput
import com.myapp.app.util.resolveCanonicalCategory
import com.myapp.app.util.tr

@Composable
private fun LockHint() {
    Row(
        modifier = Modifier.fillMaxWidth().negativeTopMargin(6.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FaIcon("fa-lock", 8.dp, AppTheme.colors.textGray400)
        AppText(
            text = tr("tx_amount_locked_hint", "You can't change this because the history is more than 1 day old."),
            size = 8f,
            weight = FontWeight.Medium,
            color = AppTheme.colors.textGray400,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Edit Transfer

object EditTransfer {
    var editingId by mutableStateOf<Long?>(null)
    var amount by mutableStateOf(TextFieldValue(""))
    var description by mutableStateOf(TextFieldValue(""))
    var from by mutableStateOf("")
    var to by mutableStateOf("")
    var locked by mutableStateOf(false)

    /** openEditTransferModal(txId) */
    fun open(txId: Long) {
        val tx = AppStore.state.transactions.firstOrNull { it.id == txId }
        if (tx == null || tx.type != "transfer_out") return
        editingId = txId
        amount = TextFieldValue(formatAmountForInput(tx.amount))
        locked = isTxAmountLocked(tx)
        description = TextFieldValue(tx.description)
        from = tx.fund
        to = tx.linkedFund ?: ""
        FundDropdownState.close()
        Popups.editTransfer = true
    }

    fun close() {
        Popups.editTransfer = false
        Popups.afterFade { if (!Popups.editTransfer) editingId = null }
        FundDropdownState.close()
    }

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

    /** saveEditedTransfer() */
    fun save() {
        val state = AppStore.state
        val outTx = state.transactions.firstOrNull { it.id == editingId } ?: return
        val inTx = state.transactions.firstOrNull { it.transferId == outTx.transferId && it.type == "transfer_in" }

        val isLocked = isTxAmountLocked(outTx)
        val value = if (isLocked) outTx.amount else parseAmountInput(amount.text)
        val note = description.text.trim()
        val fromFund = if (isLocked) outTx.fund else (if (from.isNotEmpty()) from else outTx.fund)
        val toFund = if (isLocked) (outTx.linkedFund ?: "") else (if (to.isNotEmpty()) to else (outTx.linkedFund ?: ""))

        if (value.isNaN() || value <= 0) {
            AppDialogs.alert(tr("alert_valid_amount", "Please enter a valid amount!"))
            return
        }
        if (fromFund == toFund) {
            AppDialogs.alert(tr("alert_choose_two_different_accounts", "Please choose two different funds to transfer between!"))
            return
        }
        // What the source fund would hold once the ORIGINAL amount is refunded to it
        val projectedFrom = (state.funds[fromFund] ?: 0.0) + (if (fromFund == outTx.fund) outTx.amount else 0.0)
        if (projectedFrom < value) {
            AppDialogs.alert(tr("alert_insufficient_source_balance", "Insufficient balance in the source fund!"))
            return
        }

        // Undo the original movement on both funds, then apply the new one.
        val oldLinked = outTx.linkedFund ?: ""
        state.funds[outTx.fund]?.let { state.funds[outTx.fund] = it + outTx.amount }
        state.funds[oldLinked]?.let { state.funds[oldLinked] = it - outTx.amount }
        state.funds[fromFund]?.let { state.funds[fromFund] = it - value }
        state.funds[toFund]?.let { state.funds[toFund] = it + value }

        outTx.amount = value
        outTx.description = note
        outTx.fund = fromFund
        outTx.linkedFund = toFund
        if (inTx != null) {
            inTx.amount = value
            inTx.description = note
            inTx.fund = toFund
            inTx.linkedFund = fromFund
        }
        close()
        AppStore.commit()
    }
}

@Composable
fun EditTransferPopup() {
    val c = AppTheme.colors
    PopupLayer(visible = Popups.editTransfer, scrim = 0.6f) {
        GlassCard(
            radius = 28.dp,
            padding = 24.dp,
            overlay = {
                FundDropdownOverlay("edit-transfer-from", EditTransfer.from, compact = true, tight = true) { EditTransfer.pickFrom(it) }
                FundDropdownOverlay("edit-transfer-to", EditTransfer.to, compact = true, tight = true) { EditTransfer.pickTo(it) }
            },
        ) {
            val locked = EditTransfer.locked
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PopupHeaderIcon("fa-right-left", 32.dp, 12.dp, Brand.red)
                    AppText(
                        text = tr("modal_edit_transfer_title", "Edit Transfer"),
                        size = 18f,
                        lineHeight = 28f,
                        weight = FontWeight.ExtraBold,
                        letterSpacingEm = -0.025f,
                        color = c.textGray900,
                        modifier = Modifier.weight(1f),
                    )
                    HeaderDeleteButton { EditTransfer.editingId?.let { TxActions.deleteTransferEntry(it) } }
                }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(modifier = Modifier.graphicsLayer { alpha = if (locked) 0.5f else 1f }) {
                        AppTextField(
                            value = EditTransfer.amount,
                            onValueChange = { EditTransfer.amount = formatAmountField(it) },
                            placeholder = tr("modal_amount_placeholder", "Amount (৳)"),
                            keyboardType = KeyboardType.Decimal,
                            readOnly = locked,
                        )
                    }
                    if (locked) LockHint()
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SmallFieldLabel(tr("modal_from_account", "From Fund"))
                        FundSelectButton(
                            "edit-transfer-from", EditTransfer.from,
                            variant = FundButtonVariant.Transfer, textColor = Brand.red, enabled = !locked,
                        )
                    }
                    Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                        FaIcon("fa-arrow-down", 12.dp, c.textGray400)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SmallFieldLabel(tr("modal_to_account", "To Fund"))
                        FundSelectButton(
                            "edit-transfer-to", EditTransfer.to,
                            variant = FundButtonVariant.Transfer, textColor = Brand.green, enabled = !locked,
                        )
                    }
                    AppTextField(
                        value = EditTransfer.description,
                        onValueChange = { EditTransfer.description = it },
                        placeholder = tr("modal_note_optional_placeholder", "Note (optional)"),
                    )
                    if (!locked) {
                        AppText(
                            text = tr("modal_transfer_balance_note", "Changing the amount or funds will automatically adjust both fund balances. The linked entry on the receiving fund updates automatically and can't be edited separately."),
                            size = 10f,
                            weight = FontWeight.Medium,
                            color = c.textGray400,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ModalButton(tr("common_cancel", "Cancel"), ModalButtonKind.Secondary, Modifier.weight(1f)) { EditTransfer.close() }
                        ModalButton(tr("common_save", "Save"), ModalButtonKind.Green, Modifier.weight(1f)) { EditTransfer.save() }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Edit Transaction

object EditTransaction {
    var editingId by mutableStateOf<Long?>(null)
    var amount by mutableStateOf(TextFieldValue(""))
    var description by mutableStateOf(TextFieldValue(""))
    var category by mutableStateOf(TextFieldValue(""))
    var fund by mutableStateOf("")
    var locked by mutableStateOf(false)
    val picker = CategoryPickerState()

    /** openEditTransactionModal(txId) for a normal spend line */
    fun open(txId: Long) {
        val tx = AppStore.state.transactions.firstOrNull { it.id == txId } ?: return
        editingId = txId
        amount = TextFieldValue(formatAmountForInput(tx.amount))
        locked = isTxAmountLocked(tx)
        description = TextFieldValue(tx.description)
        val canonical = resolveCanonicalCategory(tx.category)
        category = TextFieldValue(if (canonical != null) categoryDisplayName(canonical) else tx.category)
        fund = tx.fund
        FundDropdownState.close()
        picker.onOpen(tx.category)
        Popups.editTransaction = true
    }

    fun close() {
        Popups.editTransaction = false
        Popups.afterFade { if (!Popups.editTransaction) editingId = null }
        FundDropdownState.close()
    }

    /** selectEditTxQuickCategory(name) */
    fun pickCategory(name: String) {
        val display = categoryDisplayName(name)
        category = TextFieldValue(display, TextRange(display.length))
        picker.onSelect(name)
    }

    /** saveEditedTransaction() */
    fun save() {
        val state = AppStore.state
        val tx = state.transactions.firstOrNull { it.id == editingId } ?: return

        val isLocked = isTxAmountLocked(tx)
        val value = if (isLocked) tx.amount else parseAmountInput(amount.text)
        val note = description.text.trim()
        val typed = category.text.trim()
        val resolved = if (typed.isNotEmpty()) resolveCanonicalCategory(typed) else null
        val categoryName = resolved ?: (if (typed.isNotEmpty()) typed else "Other")
        val targetFund = if (isLocked) tx.fund else (if (fund.isNotEmpty()) fund else tx.fund)

        if (value.isNaN() || value <= 0) {
            AppDialogs.alert(tr("alert_valid_amount", "Please enter a valid amount!"))
            return
        }

        // Never let an edit push a fund below zero: check BEFORE touching anything.
        val originalBal = state.funds[tx.fund]
        val refundedOriginal = if (originalBal != null) originalBal + tx.amount else null
        val projected: Double? = if (targetFund == tx.fund) {
            if (refundedOriginal != null) refundedOriginal - value else null
        } else {
            val tb = state.funds[targetFund]
            if (tb != null) tb - value else null
        }
        if (projected != null && projected < 0) {
            AppDialogs.alert(tr("alert_insufficient_balance_edit_fund", "Insufficient balance in that fund! Please edit fund/add cash first."))
            return
        }

        // Refund the ORIGINAL amount to the ORIGINAL fund, then take the NEW amount from the target fund.
        state.funds[tx.fund]?.let { state.funds[tx.fund] = it + tx.amount }
        state.funds[targetFund]?.let { state.funds[targetFund] = it - value }

        tx.amount = value
        tx.description = note
        tx.category = categoryName
        tx.fund = targetFund

        close()
        AppStore.commit()
    }
}

@Composable
fun EditTransactionPopup() {
    val c = AppTheme.colors
    PopupLayer(visible = Popups.editTransaction, scrim = 0.6f) {
        GlassCard(
            radius = 28.dp,
            padding = 24.dp,
            overlay = {
                FundDropdownOverlay("edit-tx", EditTransaction.fund) { EditTransaction.fund = it }
            },
        ) {
            val locked = EditTransaction.locked
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PopupHeaderIcon("fa-pen", 32.dp, 12.dp, Brand.red)
                    AppText(
                        text = tr("modal_edit_transaction_title", "Edit Transaction"),
                        size = 18f,
                        lineHeight = 28f,
                        weight = FontWeight.ExtraBold,
                        letterSpacingEm = -0.025f,
                        color = c.textGray900,
                        modifier = Modifier.weight(1f),
                    )
                    HeaderDeleteButton { TxActions.deleteTransaction(EditTransaction.editingId) }
                }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(modifier = Modifier.graphicsLayer { alpha = if (locked) 0.5f else 1f }) {
                        AppTextField(
                            value = EditTransaction.amount,
                            onValueChange = { EditTransaction.amount = formatAmountField(it) },
                            placeholder = tr("modal_amount_placeholder", "Amount (৳)"),
                            focusColor = com.myapp.app.ui.theme.Brand.green,
                            keyboardType = KeyboardType.Decimal,
                            readOnly = locked,
                        )
                    }
                    if (locked) LockHint()
                    AppTextField(
                        value = EditTransaction.description,
                        onValueChange = { EditTransaction.description = it },
                        placeholder = tr("modal_description_placeholder", "Note"),
                        focusColor = com.myapp.app.ui.theme.Brand.green,
                    )
                    AppTextField(
                        value = EditTransaction.category,
                        onValueChange = {
                            val stripped = stripLeadingDigitsField(it)
                            EditTransaction.category = stripped
                            EditTransaction.picker.onTyped(stripped.text)
                        },
                        placeholder = tr("modal_enter_category_placeholder", "Enter Category"),
                        focusColor = com.myapp.app.ui.theme.Brand.green,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppText(
                            text = tr("modal_select_category", "Select Category"),
                            size = 10f,
                            weight = FontWeight.Bold,
                            letterSpacingEm = 0.05f,
                            color = c.textGray400,
                            uppercase = true,
                        )
                        CategoryChips(
                            picker = EditTransaction.picker,
                            onPick = { EditTransaction.pickCategory(it) },
                            onAddNew = { AddCategory.open { name -> EditTransaction.pickCategory(name) } },
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        AppText(
                            text = tr("modal_account_label", "Fund"),
                            size = 10f,
                            weight = FontWeight.Bold,
                            letterSpacingEm = 0.05f,
                            color = c.textGray400,
                            uppercase = true,
                        )
                        FundSelectButton(
                            "edit-tx", EditTransaction.fund,
                            variant = FundButtonVariant.EditTx, enabled = !locked,
                        )
                    }
                    if (!locked) {
                        AppText(
                            text = tr("modal_balance_adjust_note", "Changing the amount or fund will automatically adjust the affected fund balance(s)."),
                            size = 10f,
                            weight = FontWeight.Medium,
                            color = c.textGray400,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ModalButton(tr("common_cancel", "Cancel"), ModalButtonKind.Secondary, Modifier.weight(1f)) { EditTransaction.close() }
                        ModalButton(tr("common_save", "Save"), ModalButtonKind.Green, Modifier.weight(1f)) { EditTransaction.save() }
                    }
                }
            }
        }
    }
}
