package com.myapp.app.ui.popup

import androidx.compose.foundation.background
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.myapp.app.data.AppStore
import com.myapp.app.data.FundMeta
import com.myapp.app.ui.components.AppText
import com.myapp.app.ui.components.FaIcon
import com.myapp.app.ui.components.formatAmountField
import com.myapp.app.ui.components.stripLeadingDigitsField
import com.myapp.app.ui.theme.AppTheme
import com.myapp.app.ui.theme.Brand
import com.myapp.app.util.getFundType
import com.myapp.app.util.parseAmountInput
import com.myapp.app.util.tr

/** The "New Fund" popup (old openAddFundModal / selectNewFundType / addNewFund). */
object AddFund {
    var name by mutableStateOf(TextFieldValue(""))
    var amount by mutableStateOf(TextFieldValue(""))
    var selectedType by mutableStateOf("cash")
    var selectedColor by mutableStateOf(getFundType("cash").color)

    fun open() {
        name = TextFieldValue("")
        amount = TextFieldValue("")
        selectedType = "cash"
        selectedColor = getFundType("cash").color
        Popups.addFund = true
    }

    fun close() {
        Popups.addFund = false
    }

    fun selectType(key: String) {
        selectedType = key
        selectedColor = getFundType(key).color
    }

    fun save() {
        val state = AppStore.state
        val fundName = name.text.trim()
        val rawAmount = parseAmountInput(amount.text)

        if (fundName.isEmpty()) {
            AppDialogs.alert(tr("alert_type_account_name", "Please type a Fund Name!"))
            return
        }
        // A fund name must contain at least one letter (English or Bangla): "1000" is not allowed, "A 1000" is.
        if (!Regex("[a-zA-Z\\u0980-\\u09FF]").containsMatchIn(fundName)) {
            AppDialogs.alert(tr("alert_fund_name_needs_letter", "Fund name can't be just numbers - it needs at least one letter."))
            return
        }
        val value = if (rawAmount.isNaN()) 0.0 else rawAmount
        if (value < 0) {
            AppDialogs.alert(tr("alert_fund_starting_balance_required", "Please enter a valid Starting Balance!"))
            return
        }

        val existing = state.funds[fundName]
        state.funds[fundName] = if (existing != null) existing + value else value

        val preset = getFundType(selectedType)
        state.fundMeta[fundName] = FundMeta(icon = preset.icon, color = selectedColor, type = preset.key)

        name = TextFieldValue("")
        amount = TextFieldValue("")
        close()
        AppStore.commit()
    }
}

/** Round white badge + title used at the top of the popups. */
@Composable
internal fun PopupHeaderIcon(icon: String, size: androidx.compose.ui.unit.Dp, iconSize: androidx.compose.ui.unit.Dp, tint: androidx.compose.ui.graphics.Color) {
    val c = AppTheme.colors
    Box(
        modifier = Modifier.size(size).clip(CircleShape).background(c.bgWhite, CircleShape),
        contentAlignment = Alignment.Center,
    ) { FaIcon(icon, iconSize, tint) }
}

@Composable
private fun FieldLabel(text: String, bottom: androidx.compose.ui.unit.Dp) {
    AppText(
        text = text,
        size = 10f,
        weight = FontWeight.Bold,
        letterSpacingEm = 0.05f,
        color = AppTheme.colors.textGray400,
        uppercase = true,
        modifier = Modifier.padding(bottom = bottom),
    )
}

@Composable
fun AddFundPopup() {
    val c = AppTheme.colors
    PopupLayer(visible = Popups.addFund, scrim = 0.6f, settleFrames = 3) {
        GlassCard(radius = 28.dp, padding = 24.dp) {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PopupHeaderIcon("fa-plus", 32.dp, 12.dp, Brand.red)
                    AppText(
                        text = tr("modal_new_account_title", "New Fund"),
                        size = 18f,
                        lineHeight = 28f,
                        weight = FontWeight.ExtraBold,
                        letterSpacingEm = -0.025f,
                        color = c.textGray900,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column {
                        FieldLabel(tr("modal_account_name_label", "Fund Name"), 4.dp)
                        AppTextField(
                            value = AddFund.name,
                            onValueChange = { AddFund.name = stripLeadingDigitsField(it) },
                            placeholder = tr("modal_fund_name_placeholder", "Enter Fund Name"),
                        )
                    }
                    Column {
                        FieldLabel(tr("modal_starting_balance_label", "Starting Balance (৳)"), 4.dp)
                        AppTextField(
                            value = AddFund.amount,
                            onValueChange = { AddFund.amount = formatAmountField(it) },
                            placeholder = "0.00",
                            keyboardType = KeyboardType.Decimal,
                        )
                    }
                    Column {
                        FieldLabel(tr("modal_account_type_icon_label", "Fund Type & Icon"), 4.dp)
                        FundTypeGrid(selectedKey = AddFund.selectedType) { AddFund.selectType(it) }
                    }
                    Column(modifier = Modifier.padding(top = 4.dp)) {
                        FieldLabel(tr("modal_colour_label", "Colour"), 8.dp)
                        Box(modifier = Modifier.padding(top = 8.dp)) {
                            FundColorGrid(selectedColor = AddFund.selectedColor) { AddFund.selectedColor = it }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ModalButton(tr("common_cancel", "Cancel"), ModalButtonKind.Secondary, Modifier.weight(1f)) { AddFund.close() }
                        ModalButton(tr("common_create", "Create"), ModalButtonKind.Red, Modifier.weight(1f)) { AddFund.save() }
                    }
                }
            }
        }
    }
}
