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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.myapp.app.data.AppStore
import com.myapp.app.data.FundMeta
import com.myapp.app.ui.AppActions
import com.myapp.app.ui.AppUi
import com.myapp.app.ui.components.AppText
import com.myapp.app.ui.components.FaIcon
import com.myapp.app.ui.components.FundTypeIcon
import com.myapp.app.ui.components.appTextStyle
import com.myapp.app.ui.components.stripLeadingDigitsField
import com.myapp.app.ui.theme.AppTheme
import com.myapp.app.ui.theme.Brand
import com.myapp.app.util.ensureFundMeta
import com.myapp.app.util.formatAmountForInput
import com.myapp.app.util.fundDisplayName
import com.myapp.app.util.getFundType
import com.myapp.app.util.parseCssColor
import com.myapp.app.util.resolveCanonicalFundName
import com.myapp.app.util.tr
import com.myapp.app.util.trfMap

object EditFund {
    /** The fund being edited, under its stored (canonical) name. */
    var originalName by mutableStateOf("")
    var name by mutableStateOf(TextFieldValue(""))
    var amountText by mutableStateOf("")
    var selectedType by mutableStateOf("cash")
    var selectedColor by mutableStateOf("")

    fun open(fundName: String) {
        val state = AppStore.state
        originalName = fundName
        name = TextFieldValue(fundDisplayName(fundName))
        amountText = formatAmountForInput(state.funds[fundName])
        val meta = ensureFundMeta(fundName)
        selectedType = meta.type
        selectedColor = meta.color
        Popups.editFund = true
    }

    fun close() {
        Popups.editFund = false
        Popups.deleteFundConfirm = false
    }

    fun selectType(key: String) {
        selectedType = key
        selectedColor = getFundType(key).color
    }

    /** saveFundUpdates() */
    fun save() {
        val state = AppStore.state
        val fundName = originalName
        val typed = name.text.trim()
        // the box may just show the default fund's translated label - that is "unchanged", not a rename
        val newName = if (resolveCanonicalFundName(typed) == fundName) fundName else typed
        var target = fundName

        if (newName.isEmpty()) {
            AppDialogs.alert(tr("alert_type_account_name", "Please type a Fund Name!"))
            return
        }

        if (newName != fundName) {
            if (state.funds.containsKey(newName)) {
                AppDialogs.alert(tr("alert_fund_name_exists", "A fund with this name already exists. Please choose a different name."))
                return
            }
            // Move everything to the new key. Like the old object keys, the renamed fund goes to the END
            // of the order (delete old key, add new key).
            state.funds.remove(fundName)?.let { state.funds[newName] = it }
            state.fundMeta.remove(fundName)?.let { state.fundMeta[newName] = it }
            state.fundNotes.remove(fundName)?.let { state.fundNotes[newName] = it }

            for (t in state.transactions) {
                if (t.fund == fundName) t.fund = newName
                if (t.linkedFund == fundName) t.linkedFund = newName
            }
            for (d in state.debts) {
                if (d.fund == fundName) d.fund = newName
                for (pm in d.payments) if (pm.fund == fundName) pm.fund = newName
            }
            if (state.activeFund == fundName) state.activeFund = newName
            if (AppUi.currentDetailsFund == fundName) AppUi.currentDetailsFund = newName
            target = newName
            originalName = newName
        }

        val preset = getFundType(selectedType)
        state.fundMeta[target] = FundMeta(icon = preset.icon, color = selectedColor, type = preset.key)

        close()
        AppStore.commit()
    }

    /** triggerCustomDeleteConfirm() */
    fun askDelete() {
        Popups.deleteFundConfirm = true
    }

    fun cancelDelete() {
        Popups.deleteFundConfirm = false
    }

    /** confirmAndExecuteDelete() */
    fun confirmDelete() {
        val state = AppStore.state
        val gone = originalName
        state.funds.remove(gone)
        state.fundMeta.remove(gone)
        state.fundNotes.remove(gone)
        state.transactions = state.transactions.filter { it.fund != gone }.toMutableList()
        // debts tied to this fund lose the link, so a new fund with the same name can't be wrongly refunded/charged
        for (d in state.debts) {
            if (d.fund == gone) d.fund = null
            for (pm in d.payments) if (pm.fund == gone) pm.fund = null
        }
        state.activeFund = state.funds.keys.firstOrNull() ?: state.activeFund
        Popups.deleteFundConfirm = false
        close()
        AppStore.commit()
    }
}

@Composable
fun EditFundPopup() {
    val c = AppTheme.colors
    PopupLayer(visible = Popups.editFund, scrim = 0.6f) {
        val state = AppStore.observe()
        val color = parseCssColor(EditFund.selectedColor) ?: Brand.red
        GlassCard(radius = 28.dp, padding = 24.dp) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // the badge follows the picked type + colour live
                        Box(
                            Modifier.size(32.dp).clip(CircleShape).background(color.copy(alpha = 0.133f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            val preset = getFundType(EditFund.selectedType)
                            FundTypeIcon(preset.key, preset.icon, 12.dp, color)
                        }
                        AppText(
                            text = fundDisplayName(EditFund.originalName), size = 18f, lineHeight = 28f,
                            weight = FontWeight.ExtraBold, letterSpacingEm = -0.025f, color = c.textGray900,
                            singleLineEllipsis = true,
                        )
                    }
                    // a last remaining fund cannot be deleted
                    if (state.funds.size > 1) {
                        Box(
                            Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { EditFund.askDelete() },
                            contentAlignment = Alignment.Center,
                        ) { FaIcon("fa-trash-alt", 14.dp, Brand.red) }
                    }
                }

                Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column {
                        Label(tr("modal_fund_name_locked_label", "Fund Name"), 4)
                        AppTextField(
                            value = EditFund.name,
                            onValueChange = { EditFund.name = stripLeadingDigitsField(it) },
                            placeholder = "",
                        )
                    }
                    Column {
                        Label(tr("modal_current_balance_label", "Current Cash Balance (Locked) (৳)"), 4)
                        // locked: grey, not editable
                        val lockShape = RoundedCornerShape(12.dp)
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(lockShape)
                                .background(c.bgGray200a50, lockShape)
                                .padding(12.dp),
                        ) {
                            AppText(
                                text = EditFund.amountText.ifEmpty { "0.00" }, size = 14f, lineHeight = 20f,
                                weight = FontWeight.Bold, color = c.textGray400,
                            )
                        }
                    }
                    Column {
                        Label(tr("modal_account_type_icon_label", "Fund Type & Icon"), 4)
                        FundTypeGrid(selectedKey = EditFund.selectedType) { EditFund.selectType(it) }
                    }
                    Column(Modifier.padding(top = 4.dp)) {
                        Label(tr("modal_colour_label", "Colour"), 8)
                        Box(Modifier.padding(top = 8.dp)) {
                            FundColorGrid(selectedColor = EditFund.selectedColor) { EditFund.selectedColor = it }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ModalButton(tr("common_cancel", "Cancel"), ModalButtonKind.Secondary, Modifier.weight(1f)) { EditFund.close() }
                        ModalButton(tr("common_save", "Save"), ModalButtonKind.Green, Modifier.weight(1f)) { EditFund.save() }
                    }
                }
            }
        }
    }

    // "Are you sure?" before a fund and all of its history are deleted
    PopupLayer(visible = Popups.deleteFundConfirm, scrim = 0.7f) {
        GlassCard(radius = 28.dp, padding = 24.dp) {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    FaIcon("fa-exclamation-triangle", 30.dp, Brand.red)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AppText(
                        text = tr("confirm_default_title", "Are you sure?"), size = 18f, lineHeight = 28f,
                        weight = FontWeight.ExtraBold, letterSpacingEm = -0.025f, color = c.textGray900, align = TextAlign.Center,
                    )
                    // the fund name in the sentence is bold + dark
                    val fund = EditFund.originalName
                    val sentence = trfMap(
                        "delete_confirm_message",
                        "This will permanently delete \"{fund}\" and all of its logged history. This process cannot be undone.",
                        mapOf("fund" to "\u0000"),
                    ).split("\u0000")
                    val msg = buildAnnotatedString {
                        append(sentence.getOrElse(0) { "" })
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = c.textGray900)) { append(fund) }
                        append(sentence.getOrElse(1) { "" })
                    }
                    BasicText(
                        text = msg,
                        style = appTextStyle(12f, c.textGray500, FontWeight.Medium, lineHeight = 16f).copy(textAlign = TextAlign.Center),
                        modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 4.dp),
                    )
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModalButton(tr("common_cancel", "Cancel"), ModalButtonKind.Secondary, Modifier.weight(1f)) { EditFund.cancelDelete() }
                    ModalButton(tr("common_yes_delete", "Yes, Delete"), ModalButtonKind.Red, Modifier.weight(1f)) { EditFund.confirmDelete() }
                }
            }
        }
    }
}

@Composable
private fun Label(text: String, bottom: Int) {
    AppText(
        text = text, size = 10f, lineHeight = 15f, weight = FontWeight.Bold, letterSpacingEm = 0.05f,
        color = AppTheme.colors.textGray400, uppercase = true, modifier = Modifier.padding(bottom = bottom.dp),
    )
}

fun installEditFundHooks() {
    AppActions.openEditFund = { name -> EditFund.open(name) }
}
