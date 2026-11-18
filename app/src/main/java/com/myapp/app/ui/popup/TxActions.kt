package com.myapp.app.ui.popup

import com.myapp.app.data.AppStore
import com.myapp.app.data.JsDate
import com.myapp.app.data.Transaction
import com.myapp.app.util.getDebtRemaining
import com.myapp.app.util.tr

/** One day: after that a transaction's amount (and fund) can no longer be changed. */
const val TX_AMOUNT_LOCK_MS: Long = 24L * 60L * 60L * 1000L

fun isTxAmountLocked(tx: Transaction?): Boolean {
    if (tx == null || tx.date.isEmpty()) return false
    val created = JsDate.parseMillis(tx.date) ?: return false
    return (System.currentTimeMillis() - created) > TX_AMOUNT_LOCK_MS
}

/**
 * A manual "Add Money" entry can be edited / deleted only while it is under 1 day old AND no other
 * entry has been logged on that fund after it.
 */
fun isAddMoneyEditable(tx: Transaction?): Boolean {
    if (tx == null || tx.type != "add_money") return false
    if (isTxAmountLocked(tx)) return false
    val t0 = JsDate.parseMillis(tx.date)
    val hasLater = AppStore.state.transactions.any { o ->
        if (o.id == tx.id || o.fund != tx.fund || t0 == null) {
            false
        } else {
            val ot = JsDate.parseMillis(o.date)
            ot != null && (ot > t0 || (ot == t0 && o.id > tx.id))
        }
    }
    return !hasLater
}

private fun truthyId(id: Long?): Boolean = id != null && id != 0L

private fun after() {
    AppStore.commit()
}

/** Deleting a history line (the old deleteTransaction / deleteTransferEntry). */
object TxActions {

    fun deleteTransferEntry(txId: Long) {
        val state = AppStore.state
        val outTx = state.transactions.firstOrNull { it.id == txId && it.type == "transfer_out" } ?: return
        val inTx = state.transactions.firstOrNull { it.transferId == outTx.transferId && it.type == "transfer_in" }

        AppDialogs.confirm(
            message = tr("confirm_delete_fund_transfer", "Delete this fund transfer? The amount will be reversed on both funds."),
            icon = "fa-trash-alt",
        ) { ok ->
            if (ok == true) {
                val s = AppStore.state
                s.funds[outTx.fund]?.let { s.funds[outTx.fund] = it + outTx.amount }
                s.funds[outTx.linkedFund ?: ""]?.let { s.funds[outTx.linkedFund ?: ""] = it - outTx.amount }
                s.transactions = s.transactions.filter { it.id != outTx.id && !(inTx != null && it.id == inTx.id) }.toMutableList()
                after()
                if (Popups.editTransfer && EditTransfer.editingId == txId) EditTransfer.close()
            }
        }
    }

    fun deleteTransaction(txId: Long?) {
        if (txId == null) return
        val state = AppStore.state
        val tx = state.transactions.firstOrNull { it.id == txId } ?: return

        // History line of a collected / repaid payment: deleting it also undoes that payment on the debt record.
        if (truthyId(tx.debtPaymentEntryId)) {
            val payEntry = state.debts.firstOrNull { it.id == tx.debtPaymentEntryId }
            if (payEntry != null) {
                AppDialogs.confirm(
                    message = tr("confirm_delete_debt_payment", "Delete this payment? The debt/receivable balance will go back as it was before this payment."),
                    icon = "fa-trash-alt",
                ) { okPay ->
                    if (okPay != true) return@confirm
                    val s = AppStore.state
                    val internalRec = payEntry.payments.firstOrNull { pm ->
                        pm.internal == true && (pm.txId == tx.id || (pm.txIds?.contains(tx.id) == true))
                    }
                    if (internalRec != null) {
                        val toF = internalRec.toFund ?: ""
                        val fromF = internalRec.fromFund ?: ""
                        val toBal = s.funds[toF]
                        if (toBal != null && toBal < internalRec.amount - 0.005) {
                            AppDialogs.alert(tr("alert_insufficient_balance_edit_fund", "Insufficient balance in that fund! Please edit fund/add cash first."))
                            return@confirm
                        }
                        if (toBal != null) s.funds[toF] = toBal - internalRec.amount
                        s.funds[fromF]?.let { s.funds[fromF] = it + internalRec.amount }
                        payEntry.payments = payEntry.payments.filter { it !== internalRec }.toMutableList()
                        if (getDebtRemaining(payEntry) > 0.005) {
                            payEntry.settled = false
                            payEntry.settledDate = null
                        }
                        val gone = internalRec.txIds ?: mutableListOf(tx.id)
                        s.transactions = s.transactions.filter { !gone.contains(it.id) }.toMutableList()
                        after()
                        return@confirm
                    }
                    val isCollected = payEntry.type == "receivable"
                    val bal = s.funds[tx.fund]
                    if (isCollected && bal != null && bal < tx.amount) {
                        AppDialogs.alert(tr("alert_insufficient_balance_edit_fund", "Insufficient balance in that fund! Please edit fund/add cash first."))
                        return@confirm
                    }
                    if (bal != null) s.funds[tx.fund] = bal + (if (isCollected) -tx.amount else tx.amount)
                    payEntry.payments = payEntry.payments.filter { it.txId != tx.id }.toMutableList()
                    if (getDebtRemaining(payEntry) > 0.005) {
                        payEntry.settled = false
                        payEntry.settledDate = null
                    }
                    s.transactions = s.transactions.filter { it.id != txId }.toMutableList()
                    after()
                }
                return
            }
        }

        // Line created by an Add Debt/Receivable with a fund: removed together with its debt record only.
        if (truthyId(tx.debtEntryId)) {
            if (state.debts.any { it.id == tx.debtEntryId }) {
                AppDialogs.alert(tr("debt_delete_from_debts_alert", "Delete this entry from Debts/Receivables."))
                return
            }
            state.transactions = state.transactions.filter { it.id != txId }.toMutableList()
            after()
            return
        }

        // Fund Transfer entries (either side) delete as a pair.
        if (tx.type == "transfer_out" || tx.type == "transfer_in") {
            val master = if (tx.type == "transfer_out") tx
            else state.transactions.firstOrNull { it.transferId == tx.transferId && it.type == "transfer_out" }
            if (master != null) deleteTransferEntry(master.id)
            return
        }

        // "Add Money": removing it subtracts the amount back out.
        if (tx.type == "add_money") {
            if (tx.fromDebtPayment != true && !isAddMoneyEditable(tx)) {
                AppDialogs.alert(tr("add_money_locked_alert", "This entry can't be deleted: it is more than 1 day old, or another transaction has happened on this fund since."))
                return
            }
            AppDialogs.confirm(
                message = tr("confirm_delete_debt_entry", "Delete this entry? The amount will be removed from the fund balance."),
                icon = "fa-trash-alt",
            ) { ok ->
                if (ok == true) {
                    val s = AppStore.state
                    s.funds[tx.fund]?.let { s.funds[tx.fund] = it - tx.amount }
                    s.transactions = s.transactions.filter { it.id != txId }.toMutableList()
                    AppStore.saveState()
                    if (AddMoney.editingId == txId) AddMoney.close()
                    AppStore.refresh()
                }
            }
            return
        }

        // Older than 1 day: soft delete - hidden from every list, NOT refunded, still counted as spent.
        if (isTxAmountLocked(tx)) {
            AppDialogs.confirm(
                message = tr("confirm_delete_transaction_locked", "Delete this entry? Since it's more than 1 day old, the amount will NOT be refunded to the fund - it stays counted as spent."),
                icon = "fa-trash-alt",
            ) { ok ->
                if (ok == true) {
                    tx.historyHidden = true
                    after()
                    if (Popups.editTransaction && EditTransaction.editingId == txId) EditTransaction.close()
                }
            }
            return
        }

        AppDialogs.confirm(
            message = tr("confirm_delete_transaction", "Delete this transaction? The amount will be refunded to the fund."),
            icon = "fa-trash-alt",
        ) { ok ->
            if (ok == true) {
                val s = AppStore.state
                s.funds[tx.fund]?.let { s.funds[tx.fund] = it + tx.amount }
                s.transactions = s.transactions.filter { it.id != txId }.toMutableList()
                after()
                if (Popups.editTransaction && EditTransaction.editingId == txId) EditTransaction.close()
            }
        }
    }

    /** The old openEditTransactionModal(txId): transfers have their own popup, mirrors are not editable. */
    fun openEditTransaction(txId: Long) {
        val tx = AppStore.state.transactions.firstOrNull { it.id == txId } ?: return
        if (tx.type == "transfer_out") {
            EditTransfer.open(txId)
            return
        }
        if (tx.type == "transfer_in" || tx.type == "add_money") return
        EditTransaction.open(txId)
    }
}
