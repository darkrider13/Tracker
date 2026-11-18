package com.myapp.app.ui.popup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.myapp.app.data.AppStore
import com.myapp.app.data.JsDate
import com.myapp.app.data.Transaction
import com.myapp.app.ui.components.AppText
import com.myapp.app.ui.components.formatAmountField
import com.myapp.app.ui.components.stripLeadingDigitsField
import com.myapp.app.ui.theme.AppTheme
import com.myapp.app.util.categoryDisplayName
import com.myapp.app.util.parseAmountInput
import com.myapp.app.util.resolveCanonicalCategory
import com.myapp.app.util.tr

/** The "Add Transaction" popup (old popup-add-expense + openCustomSpendModal + saveTransaction). */
object AddExpense {
    var amount by mutableStateOf(TextFieldValue(""))
    var description by mutableStateOf(TextFieldValue(""))
    var category by mutableStateOf(TextFieldValue(""))
    var fund by mutableStateOf("")
    val picker = CategoryPickerState()

    /** openCustomSpendModal(presetCategory) */
    fun open(presetCategory: String? = null) {
        amount = TextFieldValue("")
        description = TextFieldValue("")
        category = TextFieldValue(presetCategory ?: "")
        fund = AppStore.state.activeFund
        FundDropdownState.close()
        picker.onOpen(presetCategory)
        Popups.addExpense = true
    }

    /** closeAddModal() */
    fun close() {
        Popups.addExpense = false
        FundDropdownState.close()
        amount = TextFieldValue("")
        description = TextFieldValue("")
        category = TextFieldValue("")
        picker.onOpen(null)
    }

    /** selectQuickCategory(name) */
    fun pickCategory(name: String) {
        val display = categoryDisplayName(name)
        category = TextFieldValue(display, androidx.compose.ui.text.TextRange(display.length))
        picker.onSelect(name)
    }

    /** saveTransaction() */
    fun save() {
        val state = AppStore.state
        val amountValue = parseAmountInput(amount.text)
        val note = description.text.trim()
        val typedCategory = category.text.trim()
        val resolved = if (typedCategory.isNotEmpty()) resolveCanonicalCategory(typedCategory) else null
        val categoryName = resolved ?: (if (typedCategory.isNotEmpty()) typedCategory else "Other")
        val targetFund = if (fund.isNotEmpty()) fund else state.activeFund

        if (amountValue.isNaN() || amountValue <= 0) {
            AppDialogs.alert(tr("alert_valid_amount", "Please enter a valid amount!"))
            return
        }
        val balance = state.funds[targetFund]
        if (balance != null && balance < amountValue) {
            AppDialogs.alert(tr("alert_insufficient_balance_edit_fund", "Insufficient balance in that fund! Please edit fund/add cash first."))
            return
        }

        state.funds[targetFund] = (balance ?: 0.0) - amountValue
        state.transactions.add(
            0,
            Transaction(
                id = System.currentTimeMillis(),
                fund = targetFund,
                category = categoryName,
                description = note,
                amount = amountValue,
                date = JsDate.nowIso(),
            ),
        )
        close()
        AppStore.commit()
    }
}

@Composable
fun AddExpensePopup() {
    val c = AppTheme.colors
    PopupLayer(visible = Popups.addExpense, scrim = 0.6f) {
        GlassCard(
            radius = 24.dp,
            padding = 20.dp,
            overlay = {
                FundDropdownOverlay(context = "modal", selectedFund = AddExpense.fund) { AddExpense.fund = it }
            },
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AppText(
                    text = tr("modal_add_transaction_title", "Add Transaction"),
                    size = 20f,
                    lineHeight = 28f,
                    weight = FontWeight.ExtraBold,
                    letterSpacingEm = -0.025f,
                    color = c.textGray900,
                )

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppTextField(
                        value = AddExpense.amount,
                        onValueChange = { AddExpense.amount = formatAmountField(it) },
                        placeholder = tr("modal_amount_placeholder", "Amount (৳)"),
                        keyboardType = KeyboardType.Decimal,
                    )
                    AppTextField(
                        value = AddExpense.description,
                        onValueChange = { AddExpense.description = it },
                        placeholder = tr("modal_description_placeholder", "Note"),
                    )
                    AppTextField(
                        value = AddExpense.category,
                        onValueChange = {
                            val stripped = stripLeadingDigitsField(it)
                            AddExpense.category = stripped
                            AddExpense.picker.onTyped(stripped.text)
                        },
                        placeholder = tr("modal_enter_category_placeholder", "Enter Category"),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppText(
                            text = tr("modal_select_category", "Select Category"),
                            size = 10f,
                            weight = FontWeight.Bold,
                            letterSpacingEm = 0.1f,
                            color = c.textGray400,
                            uppercase = true,
                        )
                        CategoryChips(
                            picker = AddExpense.picker,
                            onPick = { AddExpense.pickCategory(it) },
                            onAddNew = { AddCategory.open { name -> AddExpense.pickCategory(name) } },
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        AppText(
                            text = tr("modal_account_label", "Fund"),
                            size = 10f,
                            weight = FontWeight.Bold,
                            letterSpacingEm = 0.1f,
                            color = c.textGray400,
                            uppercase = true,
                        )
                        FundSelectButton(context = "modal", selectedFund = AddExpense.fund)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ModalButton(tr("common_cancel", "Cancel"), ModalButtonKind.Secondary, Modifier.weight(1f)) { AddExpense.close() }
                    ModalButton(tr("common_save", "Save"), ModalButtonKind.Green, Modifier.weight(1f)) { AddExpense.save() }
                }
            }
        }
    }
}
