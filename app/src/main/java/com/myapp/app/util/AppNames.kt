package com.myapp.app.util

import com.myapp.app.data.AppStore
import com.myapp.app.data.Transaction

/** Built-in default account name is translated in Bangla; custom names are shown exactly as typed. */
fun fundDisplayName(name: String): String {
    if (name == "Monthly Cash") return tr("fund_default_monthly_cash", name)
    return name
}

/**
 * Reverse of [fundDisplayName]: the canonical stored name for the default account (English or Bangla
 * text), or null for a custom name.
 */
fun resolveCanonicalFundName(raw: String?): String? {
    if (raw.isNullOrEmpty()) return null
    val trimmed = raw.trim()
    if (trimmed == "Monthly Cash") return "Monthly Cash"
    val bn = I18n.banglaText("fund_default_monthly_cash")
    if (bn != null && trimmed == bn) return "Monthly Cash"
    return null
}

/**
 * The live-translated history note of an auto-generated debt/receivable line
 * ("Received from X", "Paid back to X", "Lent to X", "Borrowed from X"),
 * or null for lines that do not carry these fields.
 */
fun debtPaymentNoteText(t: Transaction?): String? {
    if (t == null) return null
    val kind = t.debtNoteKind
    val person = t.debtNotePerson
    if (kind.isNullOrEmpty() || person.isNullOrEmpty()) return null
    return when (kind) {
        "received_from" -> trf("debt_note_received_from", "Received from $person", "name" to person)
        "paid_back_to" -> trf("debt_note_paid_back_to", "Paid back to $person", "name" to person)
        "lent_to" -> trf("debt_note_lent_to", "Lent to $person", "name" to person)
        "borrowed_from" -> trf("debt_note_borrowed_from", "Borrowed from $person", "name" to person)
        else -> null
    }
}

/**
 * For an own-fund debt deal the "other side" in the note is a FUND, not a person.
 * Returns that fund's name (looked up live so it survives renames), or null.
 */
fun debtNoteOtherFund(t: Transaction?): String? {
    if (t == null || t.fromDebtPayment != true) return null
    val entryId = t.debtEntryId?.takeIf { it != 0L } ?: t.debtPaymentEntryId?.takeIf { it != 0L } ?: return null
    val state = AppStore.state
    val entry = state.debts.firstOrNull { it.id == entryId } ?: return null
    val counter = entry.counterFund
    val main = entry.fund
    if (counter.isNullOrEmpty() || main.isNullOrEmpty()) return null
    val other = if (t.fund == main) counter else main
    return if (state.funds.containsKey(other)) other else null
}
