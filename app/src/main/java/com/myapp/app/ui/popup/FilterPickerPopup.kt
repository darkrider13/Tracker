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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.myapp.app.data.AppStore
import com.myapp.app.ui.components.AppText
import com.myapp.app.ui.screens.DebtUi
import com.myapp.app.ui.screens.LogsState
import com.myapp.app.ui.theme.AppTheme
import com.myapp.app.ui.theme.Brand
import com.myapp.app.util.formatAmountTyping
import com.myapp.app.util.tr

/**
 * The shared Sort / Time / Price / search-field picker (popup-filter-picker).
 * Tapping an option only STAGES it (LogsState.pendingValue); nothing is saved until Apply.
 * Cancel / tapping outside the buttons leaves the old selection untouched.
 */
object FilterPicker {
    fun close() {
        Popups.filterPicker = false
        LogsState.pendingMode = null
    }

    fun stage(value: String, label: String) {
        // Priority works like a toggle: tapping the staged option again goes back to "default"
        LogsState.pendingValue =
            if (LogsState.pendingMode == "priority" && LogsState.pendingValue == value) "default" else value
        LogsState.pendingLabel = label
    }

    private fun defaultSearchField() = if (LogsState.pickerCategory == "transactions") "category" else "person"

    /** The old updateFilterModalClearBtnVisibility(): Clear Filter only shows for a non-default pick. */
    fun hasActiveFilter(): Boolean {
        val v = LogsState.pendingValue
        return when (LogsState.pendingMode) {
            "main" -> {
                val def = mapOf("sort" to "newest", "time" to "month", "price" to "all")[LogsState.activeFilterType]
                v != def
            }
            "searchField" -> v != defaultSearchField()
            "priority" -> v != "default"
            "debtAmount" -> v != "all"
            else -> false
        }
    }

    /** applyFilterModal() */
    fun apply() {
        when (LogsState.pendingMode) {
            "main" -> {
                val f = AppStore.state.filters
                when (LogsState.activeFilterType) {
                    "sort" -> f.sort = LogsState.pendingValue
                    "time" -> f.time = LogsState.pendingValue
                    "price" -> f.price = LogsState.pendingValue
                }
                AppStore.commit()
                close()
            }
            "searchField" -> {
                val cat = LogsState.pickerCategory
                if (cat == "transactions") {
                    LogsState.searchField = LogsState.pendingValue
                    // text already typed + Amount picked: reformat it right away (like typing it)
                    if (LogsState.pendingValue == "amount" && LogsState.searchText.text.isNotEmpty()) {
                        val formatted = formatAmountTyping(LogsState.searchText.text)
                        LogsState.searchText = TextFieldValue(formatted, TextRange(formatted.length))
                    }
                } else {
                    val cs = DebtUi.of(cat)
                    cs.searchField = LogsState.pendingValue
                    if (LogsState.pendingValue == "amount" && cs.search.text.isNotEmpty()) {
                        val formatted = formatAmountTyping(cs.search.text)
                        cs.search = TextFieldValue(formatted, TextRange(formatted.length))
                    }
                }
                close()
            }
            "priority" -> {
                DebtUi.of(LogsState.pickerCategory).sort.priority = LogsState.pendingValue
                close()
            }
            "debtAmount" -> {
                DebtUi.of(LogsState.pickerCategory).sort.amount = LogsState.pendingValue
                close()
            }
            else -> close()
        }
    }

    /** clearFilterModal(): back to the default option, applied at once. */
    fun clear() {
        when (LogsState.pendingMode) {
            "main" -> {
                val f = AppStore.state.filters
                when (LogsState.activeFilterType) {
                    "sort" -> f.sort = "newest"
                    "time" -> {
                        f.time = "month"
                        f.customDate = null
                        f.customMonth = null
                    }
                    "price" -> f.price = "all"
                }
                AppStore.commit()
                close()
            }
            "searchField" -> {
                if (LogsState.pickerCategory == "transactions") LogsState.searchField = "category"
                else DebtUi.of(LogsState.pickerCategory).searchField = "person"
                close()
            }
            "priority" -> {
                DebtUi.of(LogsState.pickerCategory).sort.priority = "default"
                close()
            }
            "debtAmount" -> {
                DebtUi.of(LogsState.pickerCategory).sort.amount = "all"
                close()
            }
            else -> close()
        }
    }
}

@Composable
fun FilterPickerPopup() {
    val c = AppTheme.colors
    PopupLayer(visible = Popups.filterPicker, scrim = 0.6f) {
        GlassCard(radius = 28.dp, padding = 24.dp) {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PopupHeaderIcon("fa-filter", 32.dp, 12.dp, Brand.red)
                    if (LogsState.pickerTitleIsHeadline) {
                        // the search picker's headline is a full sentence: text-xs, leading-snug
                        AppText(
                            text = LogsState.pickerTitle, size = 12f, lineHeight = 15f,
                            weight = FontWeight.ExtraBold, letterSpacingEm = -0.025f, color = c.textGray900,
                        )
                    } else {
                        AppText(
                            text = LogsState.pickerTitle, size = 18f, lineHeight = 28f,
                            weight = FontWeight.ExtraBold, letterSpacingEm = -0.025f, color = c.textGray900,
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((value, label) in LogsState.pickerOptions) {
                        PickerOption(
                            label, selected = value == LogsState.pendingValue,
                            icon = LogsState.pickerIcons[value],
                        ) { FilterPicker.stage(value, label) }
                    }
                }

                if (FilterPicker.hasActiveFilter()) {
                    ModalButton(
                        tr("filter_clear_filter", "Clear Filter"), ModalButtonKind.Secondary,
                        Modifier.fillMaxWidth(),
                    ) { FilterPicker.clear() }
                }

                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModalButton(tr("common_cancel", "Cancel"), ModalButtonKind.Secondary, Modifier.weight(1f)) { FilterPicker.close() }
                    ModalButton(tr("modal_apply_btn", "Apply"), ModalButtonKind.Green, Modifier.weight(1f)) { FilterPicker.apply() }
                }
            }
        }
    }
}

@Composable
private fun PickerOption(
    label: String,
    selected: Boolean,
    icon: Pair<String, com.myapp.app.ui.components.FaStyle>? = null,
    onClick: () -> Unit,
) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) Brand.red else c.bgWhite60, shape)
            .then(if (!selected) Modifier.border(1.dp, c.borderGray100, shape) else Modifier)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        val fg = if (selected) androidx.compose.ui.graphics.Color.White else c.textDefault
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                com.myapp.app.ui.components.FaIcon(icon.first, 12.dp, fg, Modifier.padding(end = 6.dp), style = icon.second)
            }
            AppText(
                text = label, size = 12f, lineHeight = 16f, weight = FontWeight.Bold,
                color = fg, align = TextAlign.Center,
            )
        }
    }
}
