package com.myapp.app.ui.popup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.myapp.app.data.AppStore
import com.myapp.app.data.Debt
import com.myapp.app.data.JsDate
import com.myapp.app.data.Transaction
import com.myapp.app.ui.AppActions
import com.myapp.app.ui.components.AppSwitch
import com.myapp.app.ui.components.AppText
import com.myapp.app.ui.components.FaIcon
import com.myapp.app.ui.components.formatAmountField
import com.myapp.app.ui.components.stripLeadingDigitsField
import com.myapp.app.ui.screens.DebtActions
import com.myapp.app.ui.screens.debtPaid
import com.myapp.app.ui.screens.debtRemaining
import com.myapp.app.ui.screens.to12Hour
import com.myapp.app.ui.theme.AppTheme
import com.myapp.app.ui.theme.Brand
import com.myapp.app.util.DateOpts
import com.myapp.app.util.debtPaymentNoteText
import com.myapp.app.util.fmtDate
import com.myapp.app.util.fmtMoney
import com.myapp.app.util.formatAmountForInput
import com.myapp.app.util.fundDisplayName
import com.myapp.app.util.localizeDigits
import com.myapp.app.util.parseAmountInput
import com.myapp.app.util.tr
import com.myapp.app.util.trf

/** 7 full days after creation the amount of a debt / receivable is frozen (isDebtAmountLocked). */
private const val DEBT_AMOUNT_LOCK_MS = 7L * 24 * 60 * 60 * 1000

fun isDebtAmountLocked(d: Debt?): Boolean {
    if (d == null || d.createdDate.isEmpty()) return false
    val created = JsDate.parseMillis(d.createdDate) ?: return false
    return System.currentTimeMillis() - created > DEBT_AMOUNT_LOCK_MS
}

object AddDebt {
    var editId by mutableStateOf<Long?>(null)
    var type by mutableStateOf("debt")           // debt | receivable
    var person by mutableStateOf(TextFieldValue(""))
    var amount by mutableStateOf(TextFieldValue(""))
    var note by mutableStateOf(TextFieldValue(""))
    var dueDate by mutableStateOf<String?>(null)  // "YYYY-MM-DD"
    var reminderTime by mutableStateOf("10:00")

    var amountLocked by mutableStateOf(false)
    var personReadOnly by mutableStateOf(false)
    private var personAutoFilled = false

    // ---- "lending / borrowing from a fund" section
    var fundSectionVisible by mutableStateOf(false)
    var fundOn by mutableStateOf(false)
    var fundToggleHidden by mutableStateOf(false)   // opened from a Fund Details screen: no question, fund is fixed
    var fundToggleDisabled by mutableStateOf(false) // edit mode: fund of an existing entry is fixed
    var fundPickerLocked by mutableStateOf(false)
    var fundLockedLabel by mutableStateOf<String?>(null)
    var addFund by mutableStateOf("")

    // ---- own-fund deal section
    var ownOn by mutableStateOf(false)
    var ownFund by mutableStateOf("")

    /** debtsFundContext: set by a Fund Details screen before it opens the form. */
    var fundContext: String? = null

    private val funds get() = AppStore.state.funds

    val ownSectionVisible: Boolean
        get() = editId == null && fundOn && addFund.isNotEmpty() && funds.size >= 2

    val title: String
        get() = when {
            editId != null && type == "receivable" -> tr("debt_edit_receivable_title", "Edit Receivable")
            editId != null -> tr("debt_edit_debt_title", "Edit Debt")
            type == "receivable" -> tr("debt_add_receivable_title", "Add Receivable")
            else -> tr("debt_add_debt_title", "Add Debt")
        }

    // ------------------------------------------------------------------ open / close

    fun open(presetType: String?) {
        editId = null
        person = TextFieldValue("")
        amount = TextFieldValue("")
        note = TextFieldValue("")
        dueDate = null
        reminderTime = "10:00"
        amountLocked = false
        personReadOnly = false
        personAutoFilled = false
        ownOn = false
        ownFund = ""
        type = presetType ?: "debt"
        setupFundSection(null)
        refreshOwnSection()
        Popups.addDebt = true
    }

    /** editDebtEntry(id) */
    fun openEdit(id: Long) {
        val entry = AppStore.state.debts.firstOrNull { it.id == id } ?: return
        editId = entry.id
        person = TextFieldValue(entry.person)
        amount = TextFieldValue(formatAmountForInput(entry.amount))
        amountLocked = isDebtAmountLocked(entry)
        personAutoFilled = false
        personReadOnly = !entry.counterFund.isNullOrEmpty()
        note = TextFieldValue(entry.note)
        dueDate = entry.dueDate?.takeIf { it.isNotEmpty() }
        reminderTime = entry.reminderTime ?: "10:00"
        ownOn = false
        ownFund = ""
        type = entry.type
        setupFundSection(entry)
        refreshOwnSection()
        Popups.addDebt = true
    }

    fun close() {
        Popups.addDebt = false
        FundDropdownState.close()
    }

    // ------------------------------------------------------------------ fund section

    private fun setupFundSection(entry: Debt?) {
        fundToggleDisabled = false
        fundToggleHidden = false
        fundPickerLocked = false
        fundLockedLabel = null
        addFund = ""
        val fundNames = funds.keys.toList()

        if (entry != null) {
            val f = entry.fund
            if (!f.isNullOrEmpty() && funds.containsKey(f)) {
                fundSectionVisible = true
                fundOn = true
                fundToggleDisabled = true
                addFund = f
                fundPickerLocked = true
            } else {
                fundSectionVisible = false
                fundOn = false
            }
            return
        }
        if (fundNames.isEmpty()) {
            fundSectionVisible = false
            fundOn = false
            return
        }
        val ctx = fundContext
        if (ctx != null && funds.containsKey(ctx)) {
            fundSectionVisible = true
            fundOn = true
            fundToggleHidden = true
            fundLockedLabel = if (type == "receivable") {
                tr("debt_add_fund_locked_receivable", "This money will be taken from this fund")
            } else {
                tr("debt_add_fund_locked_debt", "This money will be added to this fund")
            }
            addFund = ctx
            fundPickerLocked = true
            return
        }
        // new entry: the toggle starts OFF; the list appears once the person switches it on
        fundSectionVisible = true
        fundOn = false
    }

    fun toggleAddFund(checked: Boolean) {
        fundOn = checked
        if (!checked) {
            addFund = ""
            refreshOwnSection()
            return
        }
        if (addFund.isNotEmpty()) {
            refreshOwnSection()
            return
        }
        val active = AppStore.state.activeFund
        val def = if (funds.containsKey(active)) active else funds.keys.firstOrNull()
        if (def == null) return
        addFund = def
        refreshOwnSection()
    }

    fun pickAddFund(name: String) {
        addFund = name
        refreshOwnSection()
    }

    // ------------------------------------------------------------------ own-fund section

    private fun setOwnPerson(show: Boolean) {
        if (show && ownFund.isNotEmpty()) {
            person = TextFieldValue(fundDisplayName(ownFund))
            personReadOnly = true
            personAutoFilled = true
        } else if (personAutoFilled) {
            person = TextFieldValue("")
            personReadOnly = false
            personAutoFilled = false
        }
    }

    fun refreshOwnSection() {
        val fundNames = funds.keys.toList()
        val mainOn = fundOn && addFund.isNotEmpty()
        val show = editId == null && mainOn && fundNames.size >= 2
        if (!show) {
            ownOn = false
            ownFund = ""
            setOwnPerson(false)
            return
        }
        if (ownFund == addFund) ownFund = ""     // the same fund can't be on both sides
        if (ownOn) {
            if (ownFund.isEmpty()) ownFund = fundNames.firstOrNull { it != addFund } ?: ""
            setOwnPerson(true)
        }
    }

    fun toggleOwn(checked: Boolean) {
        ownOn = checked
        if (!checked) ownFund = ""
        refreshOwnSection()
        if (!checked) setOwnPerson(false)
    }

    fun pickOwnFund(name: String) {
        ownFund = name
        setOwnPerson(true)
    }

    // ------------------------------------------------------------------ due date / reminder

    fun clearDue() {
        dueDate = null
    }

    // ------------------------------------------------------------------ delete (edit mode)

    fun deleteFromEdit() {
        val id = editId ?: return
        DebtActions.deleteThen(id) { close() }
    }

    // ------------------------------------------------------------------ save (saveDebtEntry)

    fun save() {
        val state = AppStore.state
        val editing = editId
        val typeNow = type
        val personName = person.text.trim()
        val amountVal = parseAmountInput(amount.text)
        val noteVal = note.text.trim()
        val due = dueDate?.takeIf { it.isNotEmpty() }
        val reminder = if (due != null) reminderTime.ifEmpty { "10:00" } else null

        if (personName.isEmpty()) {
            AppDialogs.alert(tr("alert_enter_person_name", "Please enter the person's name!")); return
        }
        if (amountVal <= 0) {
            AppDialogs.alert(tr("alert_valid_amount", "Please enter a valid amount!")); return
        }

        var newEntryFund: String? = null
        var newEntryCounter: String? = null
        if (editing == null) {
            val picked = addFund
            if (fundOn && picked.isNotEmpty() && state.funds.containsKey(picked)) {
                val ownVisible = ownSectionVisible
                val ownActive = ownVisible && ownOn
                val own = ownFund
                if (ownActive) {
                    if (own.isEmpty() || !state.funds.containsKey(own)) {
                        AppDialogs.alert(tr("alert_own_fund_pick", "Please pick which fund this is with!")); return
                    }
                    if (own == picked) {
                        AppDialogs.alert(tr("alert_own_fund_same", "The two funds must be different!")); return
                    }
                    val payer = if (typeNow == "receivable") picked else own
                    val payerBal = state.funds[payer] ?: 0.0
                    if (payerBal < amountVal - 0.005) {
                        AppDialogs.alert(
                            trf(
                                "alert_own_fund_insufficient",
                                "\"${fundDisplayName(payer)}\" only has ৳${fmtMoney(payerBal)}, so ৳${fmtMoney(amountVal)} can't be given!",
                                "fund" to fundDisplayName(payer), "bal" to fmtMoney(payerBal), "amt" to fmtMoney(amountVal),
                            ),
                        )
                        return
                    }
                } else if (typeNow == "receivable" && (state.funds[picked] ?: 0.0) < amountVal) {
                    AppDialogs.alert(
                        tr("alert_insufficient_balance_edit_fund", "Insufficient balance in that fund! Please edit fund/add cash first."),
                    )
                    return
                }
                newEntryFund = picked
                state.funds[picked] = (state.funds[picked] ?: 0.0) + (if (typeNow == "receivable") -amountVal else amountVal)
                if (ownActive) {
                    newEntryCounter = own
                    state.funds[own] = (state.funds[own] ?: 0.0) + (if (typeNow == "receivable") amountVal else -amountVal)
                }
            }
        }

        if (editing != null) {
            val entry = state.debts.firstOrNull { it.id == editing }
            if (entry == null) { close(); return }

            val locked = isDebtAmountLocked(entry)
            val finalAmount = if (locked) entry.amount else amountVal

            if (!locked) {
                val paidSoFar = debtPaid(entry)
                if (finalAmount < paidSoFar) {
                    AppDialogs.alert(
                        trf(
                            "alert_amount_below_paid",
                            "The amount can't be less than what's already been paid (৳${fmtMoney(paidSoFar)}).",
                            "amt" to fmtMoney(paidSoFar),
                        ),
                    )
                    return
                }
            }

            val ef = entry.fund
            if (!ef.isNullOrEmpty() && state.funds.containsKey(ef) && finalAmount != entry.amount) {
                val diff = finalAmount - entry.amount
                val delta = if (entry.type == "receivable") -diff else diff
                val cf = entry.counterFund
                val hasCounter = !cf.isNullOrEmpty() && state.funds.containsKey(cf)
                if ((state.funds[ef] ?: 0.0) + delta < -0.005 ||
                    (hasCounter && (state.funds[cf!!] ?: 0.0) - delta < -0.005)
                ) {
                    AppDialogs.alert(
                        tr("alert_insufficient_balance_edit_fund", "Insufficient balance in that fund! Please edit fund/add cash first."),
                    )
                    return
                }
                state.funds[ef] = (state.funds[ef] ?: 0.0) + delta
                if (hasCounter) state.funds[cf!!] = (state.funds[cf] ?: 0.0) - delta
            }
            for (t in state.transactions) {
                if (t.debtEntryId == entry.id) {
                    t.amount = finalAmount
                    if (t.debtNotePerson != null && entry.counterFund.isNullOrEmpty()) {
                        t.debtNotePerson = personName
                        t.description = debtPaymentNoteText(t) ?: t.description
                    }
                }
            }

            entry.type = typeNow
            entry.person = personName
            entry.amount = finalAmount
            entry.note = noteVal
            entry.dueDate = due
            entry.reminderTime = reminder
            // raising the total can turn a settled entry pending again, and vice versa
            if (debtRemaining(entry) <= 0.005) {
                entry.settled = true
                entry.settledDate = entry.settledDate ?: JsDate.nowIso()
            } else {
                entry.settled = false
                entry.settledDate = null
            }
        } else {
            val newId = System.currentTimeMillis()
            val now = JsDate.nowIso()
            if (newEntryFund != null) {
                state.transactions.add(
                    0,
                    if (typeNow == "receivable") Transaction(
                        id = newId + 1, type = "debt_lent", fund = newEntryFund, category = "Debt Lent",
                        description = trf("debt_note_lent_to", "Lent to $personName", "name" to personName),
                        debtNoteKind = "lent_to", debtNotePerson = personName, amount = amountVal, date = now,
                        fromDebtPayment = true, debtEntryId = newId,
                    ) else Transaction(
                        id = newId + 1, type = "add_money", fund = newEntryFund, category = "Debt Borrowed",
                        description = trf("debt_note_borrowed_from", "Borrowed from $personName", "name" to personName),
                        debtNoteKind = "borrowed_from", debtNotePerson = personName, amount = amountVal, date = now,
                        fromDebtPayment = true, debtEntryId = newId,
                    ),
                )
            }
            if (newEntryFund != null && newEntryCounter != null) {
                val mainName = fundDisplayName(newEntryFund)
                state.transactions.add(
                    0,
                    if (typeNow == "receivable") Transaction(
                        id = newId + 2, type = "add_money", fund = newEntryCounter, category = "Debt Borrowed",
                        description = trf("debt_note_borrowed_from", "Borrowed from $mainName", "name" to mainName),
                        debtNoteKind = "borrowed_from", debtNotePerson = mainName, amount = amountVal, date = now,
                        fromDebtPayment = true, debtEntryId = newId, debtCounterTx = true,
                    ) else Transaction(
                        id = newId + 2, type = "debt_lent", fund = newEntryCounter, category = "Debt Lent",
                        description = trf("debt_note_lent_to", "Lent to $mainName", "name" to mainName),
                        debtNoteKind = "lent_to", debtNotePerson = mainName, amount = amountVal, date = now,
                        fromDebtPayment = true, debtEntryId = newId, debtCounterTx = true,
                    ),
                )
            }
            state.debts.add(
                0,
                Debt(
                    id = newId, type = typeNow, person = personName, amount = amountVal,
                    fund = newEntryFund, counterFund = newEntryCounter, note = noteVal,
                    dueDate = due, reminderTime = reminder, dueReminderFired = false,
                    createdDate = now, settled = false, settledDate = null,
                    payments = mutableListOf(), isKey = false,
                ),
            )
        }

        close()
        AppStore.commit()
    }
}

// =============================================================================================
// UI
// =============================================================================================

@Composable
fun AddDebtPopup() {
    val c = AppTheme.colors
    PopupLayer(visible = Popups.addDebt, scrim = 0.6f) {
        val a = AddDebt
        GlassCard(
            radius = 28.dp,
            padding = 24.dp,
            overlay = {
                FundDropdownOverlay("debt-add", a.addFund) { AddDebt.pickAddFund(it) }
                FundDropdownOverlay("debt-own", a.ownFund, excludeFund = a.addFund) { AddDebt.pickOwnFund(it) }
            },
        ) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // title + (edit only) delete
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    AppText(
                        text = a.title, size = 18f, lineHeight = 28f, weight = FontWeight.ExtraBold,
                        letterSpacingEm = -0.025f, color = c.textGray900, modifier = Modifier.weight(1f),
                    )
                    if (a.editId != null) {
                        Box(
                            Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF991B1B).copy(alpha = 0.10f), CircleShape)
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                    AddDebt.deleteFromEdit()
                                },
                            contentAlignment = Alignment.Center,
                        ) { FaIcon("fa-trash-alt", 12.dp, Brand.red) }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppTextField(
                        value = a.person,
                        onValueChange = { AddDebt.person = stripLeadingDigitsField(it) },
                        placeholder = tr("modal_person_name_placeholder", "Person's Name"),
                        readOnly = a.personReadOnly,
                        modifier = Modifier.alpha(if (a.personReadOnly) 0.5f else 1f),
                    )

                    Column {
                        AppTextField(
                            value = a.amount,
                            onValueChange = { AddDebt.amount = formatAmountField(it) },
                            placeholder = tr("modal_amount_placeholder", "Amount (৳)"),
                            keyboardType = KeyboardType.Decimal,
                            readOnly = a.amountLocked,
                            modifier = Modifier.alpha(if (a.amountLocked) 0.5f else 1f),
                        )
                        if (a.amountLocked) {
                            // -mt-1.5: the hint tucks 6 dp closer to the box than the normal 12 dp gap
                            Row(Modifier.padding(start = 4.dp, end = 4.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                FaIcon("fa-lock", 8.dp, c.textGray400, Modifier.padding(end = 4.dp))
                                AppText(
                                    text = tr("debt_amount_locked_hint", "You can't change this because the history is more than 7 days old."),
                                    size = 8f, lineHeight = 12f, weight = FontWeight.Medium, color = c.textGray400,
                                )
                            }
                        }
                    }

                    AppTextField(
                        value = a.note,
                        onValueChange = { AddDebt.note = it },
                        placeholder = tr("modal_reason_note_placeholder", "Note"),
                    )

                    // due date
                    Column {
                        FieldLabel(tr("modal_due_date_optional_label", "Due Date (Optional)"))
                        val dueShape = RoundedCornerShape(12.dp)
                        val due = a.dueDate
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(dueShape)
                                .background(c.bgWhite80, dueShape)
                                .border(1.dp, c.borderGray200, dueShape)
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                    DueDateCalendar.open(AddDebt.dueDate) { AddDebt.dueDate = it }
                                }
                                .padding(12.dp),
                        ) {
                            AppText(
                                text = if (due != null) fmtDate(due, DateOpts(month = "short", day = "2-digit", year = "numeric"))
                                else tr("debt_select_date", "Select a date"),
                                size = 14f, lineHeight = 20f, weight = FontWeight.Bold,
                                color = if (due != null) c.textGray800 else c.textGray400,
                            )
                        }
                    }

                    // reminder time (only with a due date)
                    if (a.dueDate != null) {
                        Column {
                            FieldLabel(tr("modal_remind_me_at_label", "Remind Me At"))
                            val timeShape = RoundedCornerShape(12.dp)
                            val (h12, minute, ampm) = to12Hour(a.reminderTime)
                            val ampmLabel = if (ampm == "PM") tr("reminder_pm", "PM") else tr("reminder_am", "AM")
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(timeShape)
                                    .background(c.bgWhite80, timeShape)
                                    .border(1.dp, c.borderGray200, timeShape)
                                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                        TimePicker.open(AddDebt.reminderTime) { AddDebt.reminderTime = it }
                                    }
                                    .padding(12.dp),
                            ) {
                                AppText(
                                    text = localizeDigits(h12.toString()) + ":" + localizeDigits(minute.toString().padStart(2, '0')) + " " + ampmLabel,
                                    size = 14f, lineHeight = 20f, weight = FontWeight.Bold, color = c.textGray800,
                                )
                            }
                            AppText(
                                text = tr(
                                    "modal_debt_reminder_note",
                                    "You'll get a notification naming this person every day from the due date until it's settled.",
                                ),
                                size = 9f, lineHeight = 13.5f, weight = FontWeight.Medium, color = c.textGray400,
                                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp),
                            )
                        }

                        // Clear Due
                        val clearShape = RoundedCornerShape(12.dp)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(clearShape)
                                .background(c.bgGray200a50, clearShape)
                                .border(1.dp, c.borderGray300, clearShape)
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                    AddDebt.clearDue()
                                }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            FaIcon("fa-calendar-xmark", 12.dp, c.textGray600, Modifier.padding(end = 6.dp))
                            AppText(
                                text = tr("modal_clear_due_date_btn", "Clear Due"), size = 12f, lineHeight = 16f,
                                weight = FontWeight.Bold, color = c.textGray600,
                            )
                        }
                    }

                    // fund section
                    if (a.fundSectionVisible) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val toggleLabel = a.fundLockedLabel ?: if (a.type == "receivable") {
                                tr("debt_add_fund_toggle_receivable", "Are you lending from any fund?")
                            } else {
                                tr("debt_add_fund_toggle_debt", "Add the borrowed money to a fund?")
                            }
                            ToggleRow(toggleLabel, a.fundOn, hideSwitch = a.fundToggleHidden, enabled = !a.fundToggleDisabled) {
                                AddDebt.toggleAddFund(it)
                            }
                            if (a.fundOn && a.addFund.isNotEmpty()) {
                                FundSelectButton(
                                    context = "debt-add", selectedFund = a.addFund,
                                    variant = FundButtonVariant.Debt, enabled = !a.fundPickerLocked,
                                )
                            }
                        }
                    }

                    // own-fund deal
                    if (a.ownSectionVisible) {
                        Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ToggleRow(
                                if (a.type == "receivable") tr("debt_own_toggle_receivable", "Are you lending money to another fund?")
                                else tr("debt_own_toggle_debt", "Are you borrowing money from another fund?"),
                                a.ownOn, hideSwitch = false, enabled = true,
                            ) { AddDebt.toggleOwn(it) }
                            if (a.ownOn && a.ownFund.isNotEmpty()) {
                                Column {
                                    AppText(
                                        text = if (a.type == "receivable") tr("debt_own_pick_receivable", "Which fund will receive the borrowed money?")
                                        else tr("debt_own_pick_debt", "Which fund is the money being borrowed from?"),
                                        size = 10f, lineHeight = 15f, weight = FontWeight.Bold, letterSpacingEm = 0.05f,
                                        color = c.textGray400, uppercase = true,
                                        modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp),
                                    )
                                    FundSelectButton(context = "debt-own", selectedFund = a.ownFund, variant = FundButtonVariant.Debt)
                                }
                            }
                        }
                    }
                }

                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModalButton(tr("common_cancel", "Cancel"), ModalButtonKind.Secondary, Modifier.weight(1f)) { AddDebt.close() }
                    ModalButton(tr("common_save", "Save"), ModalButtonKind.Green, Modifier.weight(1f)) { AddDebt.save() }
                }
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    AppText(
        text = text, size = 10f, lineHeight = 15f, weight = FontWeight.Bold, letterSpacingEm = 0.05f,
        color = AppTheme.colors.textGray400, uppercase = true, modifier = Modifier.padding(bottom = 4.dp),
    )
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, hideSwitch: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    val c = AppTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppText(
            text = label, size = 12f, lineHeight = 16f, weight = FontWeight.Bold,
            color = c.textGray600, modifier = Modifier.weight(1f),
        )
        if (!hideSwitch) AppSwitch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

/** Installed from installPopupHooks(). */
fun installDebtPopupHooks() {
    AppActions.openAddDebt = { type -> AddDebt.open(type) }
    DebtActions.openEdit = { id -> AddDebt.openEdit(id) }
}
