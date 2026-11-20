package com.myapp.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import com.myapp.app.ui.components.pageScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.myapp.app.data.AppSession
import com.myapp.app.data.AppState
import com.myapp.app.data.AppStore
import com.myapp.app.data.Transaction
import com.myapp.app.ui.AppActions
import com.myapp.app.ui.AppUi
import com.myapp.app.ui.components.AppRichText
import com.myapp.app.ui.components.AppText
import com.myapp.app.ui.components.CategoryIcon
import com.myapp.app.ui.components.FaIcon
import com.myapp.app.ui.components.FaStyle
import com.myapp.app.ui.components.FundTypeIcon
import com.myapp.app.ui.components.NoteGlyph
import com.myapp.app.ui.components.PagedViewport
import com.myapp.app.ui.components.pagedItem
import com.myapp.app.ui.components.rememberPagedState
import com.myapp.app.ui.popup.AddMoney
import com.myapp.app.ui.popup.AppDialogs
import com.myapp.app.ui.popup.TxActions
import com.myapp.app.ui.popup.isAddMoneyEditable
import com.myapp.app.ui.theme.AppTheme
import com.myapp.app.ui.theme.Brand
import com.myapp.app.ui.theme.Tw
import com.myapp.app.util.BN_MONTHS
import com.myapp.app.util.DateOpts
import com.myapp.app.util.amountTextExactMatchesQuery
import com.myapp.app.util.debtNoteOtherFund
import com.myapp.app.util.debtPaymentNoteText
import com.myapp.app.util.ensureFundMeta
import com.myapp.app.util.fmtDate
import com.myapp.app.util.fmtMoney
import com.myapp.app.util.formatAmountTyping
import com.myapp.app.util.fundDisplayName
import com.myapp.app.util.getLang
import com.myapp.app.util.isDateFilterToday
import com.myapp.app.util.fmtTime
import com.myapp.app.util.normalizeHyphenForSearch
import com.myapp.app.util.tr
import com.myapp.app.util.trf
import com.myapp.app.util.translatedCategoryLabel

/** The Transactions list shows 5 full cards at a time (fitListToVisibleCount(container, 5)). */
private const val LOGS_VISIBLE_COUNT = 5
private const val LOGS_FIRST_BATCH = 12
private const val LOGS_NEXT_BATCH = 14
private val FUND_HISTORY_MONTH_NAMES_SHORT =
    listOf("Jan.", "Feb.", "Mar.", "Apr.", "May", "Jun.", "Jul.", "Aug.", "Sep.", "Oct.", "Nov.", "Dec.")

@Composable
fun LogsScreen() {
    val state = AppStore.observe()
    @Suppress("UNUSED_VARIABLE")
    val lang = AppSession.lang   // redraw when the language changes
    val c = AppTheme.colors
    val scroll = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pageScroll(scroll),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 448.dp)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 128.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column {
                AppText(
                    text = tr("logs_title", "Ledger & Logs"),
                    size = 24f, lineHeight = 32f, weight = FontWeight.Bold,
                    letterSpacingEm = -0.025f, color = c.textGray900,
                )
                AppText(
                    text = tr("logs_subtitle", "Filter, search, and review your logs."),
                    size = 12f, lineHeight = 16f, color = c.textGray500,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            SubtabSwitch()

            when (AppUi.logsSubtab) {
                "debts" -> DebtsPane("debts")
                "receivables" -> DebtsPane("receivables")
                else -> TransactionsPane(state)
            }
        }
    }
}

// ------------------------------------------------------------------ subtab switch

@Composable
private fun SubtabSwitch() {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.bgGray200a50, shape)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val items = listOf(
            "transactions" to tr("logs_transactions", "Transactions"),
            "debts" to tr("logs_debts", "Debts"),
            "receivables" to tr("logs_receivables", "Receivables"),
        )
        for ((key, label) in items) {
            val active = AppUi.logsSubtab == key
            val itemShape = RoundedCornerShape(12.dp)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(itemShape)
                    .background(if (active) Brand.red else Color.Transparent, itemShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { AppUi.switchLogsSubtab(key) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                AppText(
                    text = label, size = 11f, lineHeight = 16.5f, weight = FontWeight.Bold,
                    color = if (active) Color.White else c.textGray600,
                    align = TextAlign.Center,
                )
            }
        }
    }
}

// ------------------------------------------------------------------ transactions pane

@Composable
private fun TransactionsPane(state: AppState) {
    // Same AppState object every time -> Compose would skip this function; reading the revision makes it redraw after a commit.
    AppStore.observe()
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SearchRow(state)
        FilterChips(state)
        DateRow()

        val query = normalizedLogsQuery(LogsState.searchText.text)
        val list = logsFilteredTransactions(state, query, LogsState.searchField)
        TransactionList(list, query)
    }
}

@Composable
private fun RedDot(modifier: Modifier = Modifier) {
    com.myapp.app.ui.components.PulseDot(4.dp, modifier)
}

@Composable
private fun SearchRow(state: AppState) {
    AppStore.observe()
    val c = AppTheme.colors
    val fieldShape = RoundedCornerShape(16.dp)
    val placeholder = when (LogsState.searchField) {
        "description" -> tr("logs_search_by_description", "Search by Note...")
        "amount" -> tr("logs_search_by_amount", "Search by Amount...")
        else -> tr("logs_search_by_category", "Search by Category...")
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // search box
        Box(Modifier.weight(1f)) {
            BasicTextField(
                value = LogsState.searchText,
                onValueChange = { nv ->
                    var v = nv
                    // Amount mode: live comma / digit-script formatting (onSearchInput)
                    if (LogsState.searchField == "amount") {
                        val fromEnd = v.text.length - v.selection.end
                        val formatted = formatAmountTyping(v.text)
                        val pos = (formatted.length - fromEnd).coerceIn(0, formatted.length)
                        v = TextFieldValue(formatted, TextRange(pos))
                    }
                    LogsState.searchText = v
                },
                singleLine = true,
                textStyle = com.myapp.app.ui.components.appTextStyle(12f, c.textDefault, FontWeight.Bold, lineHeight = 16f),
                cursorBrush = SolidColor(c.textDefault),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(fieldShape)
                            .background(c.bgGray200a50, fieldShape)
                            .padding(start = 40.dp, end = 36.dp, top = 12.dp, bottom = 12.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (LogsState.searchText.text.isEmpty()) {
                            AppText(
                                text = placeholder, size = 12f, lineHeight = 16f,
                                weight = FontWeight.Bold, color = c.placeholder, singleLineEllipsis = true,
                            )
                        }
                        inner()
                    }
                },
            )
            FaIcon(
                "fa-search", 14.dp, c.textGray400,
                Modifier.align(Alignment.CenterStart).padding(start = 16.dp),
            )
            if (LogsState.searchText.text.isNotEmpty()) {
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 12.dp)
                        .size(20.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { LogsState.searchText = TextFieldValue("") },
                    contentAlignment = Alignment.Center,
                ) { FaIcon("fa-xmark", 10.dp, c.textGray400) }
            }
        }

        // magnifier = "what am I searching in" picker
        SquareIconButton(onClick = { openSearchFieldPicker("transactions") }, dot = LogsState.searchField != "category") {
            FaIcon("fa-search", 14.dp, Brand.red)
        }
        // section note
        SquareIconButton(
            onClick = { AppActions.openSectionNote("transactions") },
            dot = AppStore.state.logSectionNotes["transactions"]?.isNotBlank() == true,
        ) {
            NoteGlyph(18.dp, Brand.red)
        }
    }
}

@Composable
private fun SquareIconButton(onClick: () -> Unit, dot: Boolean, icon: @Composable () -> Unit) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(shape)
            .background(c.bgGray200a50, shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        icon()
        if (dot) RedDot(Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 8.dp))
    }
}

// ------------------------------------------------------------------ sort / time / price chips

@Composable
private fun FilterChips(state: AppState) {
    // Same AppState object is passed every time, so Compose would skip this row; reading the revision makes the chips redraw right after a filter change.
    AppStore.observe()
    val f = state.filters
    val sortLabel = if (f.sort == "oldest") tr("logs_oldest", "Oldest") else tr("logs_newest", "Newest")
    val priceLabel = when (f.price) {
        "high" -> tr("logs_high_to_low", "High to Low")
        "low" -> tr("logs_low_to_high", "Low to High")
        else -> tr("logs_all_prices", "All Amounts")
    }
    val timeLabel = when {
        f.time == "today" -> tr("logs_today", "Today")
        f.time == "week" -> tr("logs_this_week", "Week")
        f.time == "custom" && f.customDate != null ->
            fmtDate(f.customDate, DateOpts(day = "numeric", month = "short"))
        f.time == "custommonth" && f.customMonth != null -> {
            val m = f.customMonth!!.split("-").getOrNull(1)?.toIntOrNull() ?: 1
            if (getLang() == "bn") BN_MONTHS[m - 1] else FUND_HISTORY_MONTH_NAMES_SHORT[m - 1]
        }
        else -> tr("logs_this_month", "Month")
    }
    val priceByAmount = f.price == "high" || f.price == "low"

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(
            Modifier.weight(1f), icon = "fa-sort-amount-down", iconStyle = FaStyle.Solid,
            label = sortLabel, dot = logsSortIsActive(state), labelSize = 9f, centered = true,
        ) { openFilterPicker("sort") }
        FilterChip(
            Modifier.weight(1f), icon = "fa-calendar-alt", iconStyle = FaStyle.Regular,
            label = timeLabel, dot = logsTimeIsActive(state), labelSize = 9f, centered = true,
        ) { openFilterPicker("time") }
        FilterChip(
            Modifier.weight(1f), icon = "fa-money-bill-wave", iconStyle = FaStyle.Solid,
            label = priceLabel, dot = logsPriceIsActive(state),
            labelSize = if (priceByAmount) 7f else 9f, centered = false,
        ) { openFilterPicker("price") }
    }
}

@Composable
private fun FilterChip(
    modifier: Modifier,
    icon: String,
    iconStyle: FaStyle,
    label: String,
    dot: Boolean,
    labelSize: Float,
    centered: Boolean,
    onClick: () -> Unit,
) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(c.bgWhite, shape)
            .border(1.dp, c.borderGray200, shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(start = if (centered) 8.dp else 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (centered) Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally) else Arrangement.SpaceBetween,
    ) {
        Row(Modifier.weight(1f, fill = false), verticalAlignment = Alignment.CenterVertically) {
            FaIcon(icon, 9.dp, Tw.red500, Modifier.padding(end = 6.dp), style = iconStyle)
            AppText(
                text = label, size = labelSize, lineHeight = labelSize * 1.5f,
                weight = FontWeight.Bold, color = c.textGray700, noWrap = true,
            )
            if (dot) RedDot(Modifier.padding(start = 4.dp))
        }
        FaIcon("fa-caret-down", 10.dp, c.textGray400)
    }
}

// ------------------------------------------------------------------ calendar + clear-history row

@Composable
private fun DateRow() {
    val c = AppTheme.colors
    val state = AppStore.observe()
    val f = state.filters
    val showDot = (f.time == "custom" && !isDateFilterToday(f.customDate)) || f.time == "custommonth"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        RoundIconButton(onClick = { AppActions.openLogsDateMenu() }, dot = showDot) {
            FaIcon("fa-calendar-alt", 14.dp, Brand.red, style = FaStyle.Regular)
        }
        RoundIconButton(onClick = { clearAllTransactionsHistory() }, dot = false) {
            FaIcon("fa-trash-alt", 14.dp, Brand.red)
        }
    }
}

@Composable
private fun RoundIconButton(onClick: () -> Unit, dot: Boolean, icon: @Composable () -> Unit) {
    val c = AppTheme.colors
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(c.bgGray200a70, CircleShape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        icon()
        if (dot) RedDot(Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 6.dp))
    }
}

/** The old clearAllTransactionsHistory(): deletes exactly the rows the Time filter covers. */
private fun clearAllTransactionsHistory() {
    val s = AppStore.state
    val ids = logsClearTargetIds()
    val fund = fundDisplayName(s.activeFund)
    if (ids.isEmpty()) {
        AppDialogs.alert(
            trf("alert_no_tx_to_clear", "There are no transactions in this range to clear for $fund.", "fund" to fund),
        )
        return
    }
    AppDialogs.confirm(
        message = trf(
            "confirm_clear_all_tx_message",
            "This will permanently delete the transaction logs currently shown for $fund. Fund balance will not be refunded. This cannot be undone.",
            "fund" to fund,
        ),
        title = tr("confirm_clear_all_tx_title", "Clear These Transactions?"),
        okLabel = tr("common_yes_clear_all", "Yes, Clear All"),
        cancelLabel = tr("common_cancel", "Cancel"),
    ) { ok ->
        if (ok != true) return@confirm
        // re-read: the list may have changed while the dialog was open
        val again = logsClearTargetIds()
        AppStore.state.transactions = AppStore.state.transactions.filter { it.id !in again }.toMutableList()
        AppStore.commit()
    }
}

// ------------------------------------------------------------------ the list

@Composable
private fun TransactionList(list: List<Transaction>, query: String) {
    val c = AppTheme.colors
    if (list.isEmpty()) {
        val shape = RoundedCornerShape(24.dp)
        Box(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(c.bgGray200a50, shape)
                .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            AppText(
                text = tr("logs_no_matching_tx", "No transactions found."),
                size = 12f, lineHeight = 16f, weight = FontWeight.Bold,
                letterSpacingEm = 0.025f, color = c.textGray500, align = TextAlign.Center,
            )
        }
        return
    }
    val paged = rememberPagedState(visible = LOGS_VISIBLE_COUNT, gap = 8.dp)
    // A long list (a whole month) is not built in one frame: the first cards appear at once and the rest
    // follow in small batches over the next frames, so changing the filter never freezes the screen.
    val f = AppStore.state.filters
    val filterKey = "${f.sort}|${f.time}|${f.price}|${f.customDate}|${f.customMonth}|$query|${LogsState.searchField}|${AppStore.state.activeFund}"
    var limit by remember(filterKey) { mutableStateOf(LOGS_FIRST_BATCH) }
    LaunchedEffect(filterKey, list.size) {
        while (limit < list.size) {
            withFrameNanos { }
            limit = minOf(list.size, limit + LOGS_NEXT_BATCH)
        }
    }
    val shownList = if (limit >= list.size) list else list.take(limit)
    com.myapp.app.ui.components.ScrollFadePaged(state = paged, count = shownList.size, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            shownList.forEachIndexed { index, t ->
                key(t.id) {
                    Box(Modifier.pagedItem(paged, index)) { TxCard(t, query) }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ one card

@Composable
internal fun TxCard(
    t: Transaction,
    query: String,
    actions: (@Composable (Color) -> Unit)? = null,
) {
    val c = AppTheme.colors
    val isTransferIn = t.type == "transfer_in"
    val isAddMoney = t.type == "add_money"
    val isIncoming = isTransferIn || isAddMoney
    val isTransferCard = t.type == "transfer_in" || t.type == "transfer_out"
    val iconColor = if (isIncoming) Tw.green600 else Brand.red
    val amountColor = iconColor
    val highlightColor = if (isIncoming) Brand.green else Brand.red

    val categoryLabel = translatedCategoryLabel(t.category)
    val dateLabel = fmtDate(t.date, DateOpts(day = "2-digit", month = "short"))
    val timeLabel = fmtTime(t.date)
    val descText = getTxNoteText(t)
    val amountText = fmtMoney(t.amount)

    val hasQuery = query.isNotEmpty()
    val mode = LogsState.searchField
    val checkCategory = mode == "category"
    val checkDescription = mode == "description"
    val checkAmount = mode == "amount"
    val categoryIsMatch = hasQuery && checkCategory &&
        normalizeHyphenForSearch(categoryLabel).lowercase().startsWith(query)
    val descriptionIsMatch = hasQuery && checkDescription &&
        normalizeHyphenForSearch(descText).lowercase().startsWith(query)

    val realNote = if (t.fromDebtPayment == true) "" else t.description
    val hasNote = realNote.isNotEmpty()
    val badge: (@Composable () -> Unit)? = badgeFor(t)
    val hasBadge = badge != null

    val isFullMatch = hasQuery && (
        (checkCategory && normalizeHyphenForSearch(categoryLabel).trim().lowercase() == query) ||
            (checkDescription && descText.isNotEmpty() && normalizeHyphenForSearch(descText).trim().lowercase() == query) ||
            (checkAmount && amountTextExactMatchesQuery(amountText, query))
        )
    val cardBg = when {
        isFullMatch && isIncoming -> Brand.green.copy(alpha = if (c.isLight) 0.10f else 0.16f)
        isFullMatch -> Brand.red.copy(alpha = if (c.isLight) 0.10f else 0.16f)
        else -> c.bgWhite
    }
    val shape = RoundedCornerShape(12.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(cardBg, shape)
            .then(if (!c.isLight) Modifier.border(1.dp, c.cardBorder, shape) else Modifier)
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        // left: icon box + category / note / badge
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(c.bgGray100, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (isTransferCard) {
                    TransferArrowsIcon()
                } else {
                    // Receivable Collected is drawn mirrored (scaleX = -1)
                    val mirror = if (t.category == "Debt Collection") Modifier.graphicsMirror() else Modifier
                    CategoryIcon(t.category, 14.dp, iconColor, mirror)
                }
            }
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                val catText = if (categoryIsMatch) highlightMatch(categoryLabel, query, highlightColor)
                else androidx.compose.ui.text.AnnotatedString(categoryLabel)
                AppRichTextEllipsis(catText, 12f, c.textGray800, FontWeight.Bold, 16f)

                val noteContent: @Composable () -> Unit = {
                    val noteText = if (descriptionIsMatch) highlightMatch(realNote, query, highlightColor)
                    else androidx.compose.ui.text.AnnotatedString(realNote)
                    AppRichTextEllipsis(noteText, 9f, c.textGray500, FontWeight.Medium, 13.5f, Modifier.widthIn(max = 150.dp))
                }
                val blank: @Composable () -> Unit = { Spacer(Modifier.height(13.5.dp)) }
                val badgeBox: @Composable () -> Unit = { Box(Modifier.widthIn(max = 150.dp)) { badge?.invoke() } }
                when {
                    hasNote && hasBadge -> { noteContent(); badgeBox() }
                    hasNote -> { blank(); noteContent() }
                    hasBadge -> { blank(); badgeBox() }
                    else -> { blank(); blank() }
                }
            }
        }

        // right: date/time, amount, edit
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 8.dp)) {
            AppText(
                text = "$dateLabel • $timeLabel", size = 9f, lineHeight = 13.5f,
                weight = FontWeight.Medium, color = c.textGray400,
                modifier = Modifier.padding(bottom = 4.dp, end = 6.dp), noWrap = true,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppText(
                    text = (if (isIncoming) "+" else "-") + "৳" + amountText,
                    size = 11f, lineHeight = 16.5f, weight = FontWeight.Black,
                    color = amountColor, modifier = Modifier.padding(end = 4.dp, start = 4.dp), noWrap = true,
                )
                if (actions != null) actions(amountColor) else EditButton(t, amountColor)
            }
        }
    }
}

@Composable
private fun EditButton(t: Transaction, color: Color) {
    val isTransferIn = t.type == "transfer_in"
    val isAddMoney = t.type == "add_money"
    val hidden = isTransferIn || t.fromDebtPayment == true || t.debtEntryId != null ||
        (isAddMoney && !isAddMoneyEditable(t))
    val mod = Modifier.padding(6.dp)
    if (hidden) {
        // same size, invisible (keeps the card height and the amount position)
        Box(mod) { Spacer(Modifier.size(width = 15.dp, height = 12.dp)) }
        return
    }
    Box(
        mod.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
            if (isAddMoney) AddMoney.openEdit(t.id) else TxActions.openEditTransaction(t.id)
        },
    ) { FaIcon("fa-pen", 12.dp, color) }
}

/** Fund Transfer cards keep the two-arrow glyph: top half red, bottom half green. */
@Composable
private fun TransferArrowsIcon() {
    Box(contentAlignment = Alignment.Center) {
        Box(Modifier.clipHalf(top = true)) { FaIcon("fa-right-left", 14.dp, Color(0xFFDC2626)) }
        Box(Modifier.clipHalf(top = false)) { FaIcon("fa-right-left", 14.dp, Color(0xFF16A34A)) }
    }
}

/** The coloured one-line badge under the category (transfer from/to, fund or debt note). */
@Composable
private fun badgeFor(t: Transaction): (@Composable () -> Unit)? {
    val c = AppTheme.colors
    return when {
        t.type == "transfer_out" || t.type == "transfer_in" -> {
            val from = if (t.type == "transfer_out") t.fund else (t.linkedFund ?: "")
            val to = if (t.type == "transfer_out") (t.linkedFund ?: "") else t.fund
            val inner: @Composable () -> Unit = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (t.type == "transfer_in") {
                        BadgeText(fundDisplayName(to), Brand.green)
                        FaIcon("fa-arrow-left", 5.dp, c.textGray400)
                        BadgeText(fundDisplayName(from), Brand.red)
                    } else {
                        BadgeText(fundDisplayName(from), Brand.red)
                        FaIcon("fa-arrow-right", 5.dp, c.textGray400)
                        BadgeText(fundDisplayName(to), Brand.green)
                    }
                }
            }
            inner
        }
        t.type == "add_money" -> {
            val lineColor = Brand.green
            val note = if (t.fromDebtPayment == true) debtFundNoteBadge(t, lineColor) else null
            val plain: @Composable () -> Unit = {
                val label = if (t.fromDebtPayment == true) {
                    debtPaymentNoteText(t) ?: t.description.ifEmpty { fundDisplayName(t.fund) }
                } else fundDisplayName(t.fund)
                BadgeText(label, lineColor)
            }
            note ?: plain
        }
        (t.category == "Debt Repayment" || t.category == "Debt Lent") && t.fund.isNotEmpty() -> {
            val lineColor = Brand.red
            val plain: @Composable () -> Unit = {
                BadgeText(debtPaymentNoteText(t) ?: t.description.ifEmpty { fundDisplayName(t.fund) }, lineColor)
            }
            debtFundNoteBadge(t, lineColor) ?: plain
        }
        else -> null
    }
}

@Composable
private fun BadgeText(text: String, color: Color) {
    AppText(text = text, size = 7f, lineHeight = 10.5f, weight = FontWeight.Bold, color = color, noWrap = true)
}

/** The old debtFundNoteBadgeHtml(): "Lent to [fund chip]" style line for own-fund debt deals. */
private fun debtFundNoteBadge(t: Transaction, lineColor: Color): (@Composable () -> Unit)? {
    val other = debtNoteOtherFund(t) ?: return null
    val kind = t.debtNoteKind ?: return null
    val (key, fallback) = when (kind) {
        "lent_to" -> "debt_note_lent_to" to "Lent to {name}"
        "borrowed_from" -> "debt_note_borrowed_from" to "Borrowed from {name}"
        "received_from" -> "debt_note_received_from" to "Received from {name}"
        "paid_back_to" -> "debt_note_paid_back_to" to "Paid back to {name}"
        else -> return null
    }
    val parts = tr(key, fallback).split("{name}")
    if (parts.size != 2) return null
    val meta = ensureFundMeta(other)
    val isGreen = lineColor == Brand.green
    val chipTint = if (isGreen) Color(0x2616A34A) else Color(0x26DC2626)
    return {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            if (parts[0].isNotEmpty()) BadgeText(parts[0], lineColor)
            Row(
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(chipTint, RoundedCornerShape(6.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                FundTypeIcon(meta.type, meta.icon, 7.dp, lineColor)
                BadgeText(fundDisplayName(other), lineColor)
            }
            if (parts[1].isNotEmpty()) BadgeText(parts[1], lineColor)
        }
    }
}

// ------------------------------------------------------------------ small layout helpers

@Composable
private fun AppRichTextEllipsis(
    text: androidx.compose.ui.text.AnnotatedString,
    size: Float,
    color: Color,
    weight: FontWeight,
    lineHeight: Float,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.text.BasicText(
        text = text,
        modifier = modifier,
        style = com.myapp.app.ui.components.appTextStyle(size, color, weight, lineHeight = lineHeight),
        softWrap = false,
        maxLines = 1,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
    )
}

private fun Modifier.graphicsMirror(): Modifier =
    this.then(Modifier.graphicsLayer { scaleX = -1f })

/** Shows only the top or bottom half of the content (CSS clip-path: inset(0 0 50% 0) / inset(50% 0 0 0)). */
private fun Modifier.clipHalf(top: Boolean): Modifier = this.drawWithContent {
    clipRect(
        top = if (top) 0f else size.height / 2f,
        bottom = if (top) size.height / 2f else size.height,
    ) { this@drawWithContent.drawContent() }
}

// ------------------------------------------------------------------ picker openers

internal fun openFilterPicker(type: String) {
    val f = AppStore.state.filters
    LogsState.activeFilterType = type
    LogsState.pickerCategory = "transactions"
    LogsState.pickerIcons = emptyMap()
    LogsState.pendingMode = "main"
    LogsState.pendingLabel = null
    LogsState.pickerTitleIsHeadline = false
    when (type) {
        "sort" -> {
            LogsState.pickerTitle = tr("filter_select_sort", "Select Sort Order")
            LogsState.pendingValue = f.sort
            LogsState.pickerOptions = listOf(
                "newest" to tr("logs_newest", "Newest"),
                "oldest" to tr("logs_oldest", "Oldest"),
            )
        }
        "time" -> {
            LogsState.pickerTitle = tr("filter_select_time", "Select Time")
            LogsState.pendingValue = f.time
            LogsState.pickerOptions = listOf(
                "today" to tr("logs_today", "Today"),
                "week" to tr("logs_this_week", "Week"),
                "month" to tr("logs_this_month", "Month"),
            )
        }
        else -> {
            LogsState.pickerTitle = tr("filter_select_price", "Select Price Filter")
            LogsState.pendingValue = f.price
            LogsState.pickerOptions = listOf(
                "all" to tr("logs_all_prices", "All Amounts"),
                "high" to tr("logs_high_to_low", "High to Low"),
                "low" to tr("logs_low_to_high", "Low to High"),
            )
        }
    }
    com.myapp.app.ui.popup.Popups.filterPicker = true
}

internal fun openSearchFieldPicker(category: String) {
    LogsState.pickerCategory = category
    LogsState.pendingMode = "searchField"
    LogsState.pendingLabel = null
    LogsState.pickerIcons = emptyMap()
    LogsState.pendingValue = if (category == "transactions") LogsState.searchField else DebtUi.of(category).searchField
    LogsState.pickerTitle = tr("search_filter_headline", "Depending on what you are looking for, I can help you find it.")
    LogsState.pickerTitleIsHeadline = true
    LogsState.pickerOptions = if (category == "transactions") listOf(
        "category" to tr("search_filter_category", "Category"),
        "description" to tr("search_filter_description", "Note"),
        "amount" to tr("search_filter_amount", "Amount"),
    ) else listOf(
        "person" to tr("search_filter_person", "Person Name"),
        "note" to tr("search_filter_note", "Note"),
        "amount" to tr("search_filter_amount", "Amount"),
    )
    com.myapp.app.ui.popup.Popups.filterPicker = true
}

/** openPriorityPickerModal(category): Key Person always, Urgent Due only on the Pending tab. */
internal fun openPriorityPicker(category: String) {
    val cs = DebtUi.of(category)
    LogsState.pickerCategory = category
    LogsState.pendingMode = "priority"
    LogsState.pendingLabel = null
    LogsState.pickerTitleIsHeadline = false
    LogsState.pickerTitle = tr("filter_select_priority", "Select Priority")
    LogsState.pendingValue = cs.sort.priority
    val opts = mutableListOf("keyperson" to tr("logs_sort_key_person", "Key Person"))
    val icons = mutableMapOf("keyperson" to ("fa-star" to com.myapp.app.ui.components.FaStyle.Solid))
    if (cs.status == "pending") {
        opts += "due" to tr("logs_sort_urgent_due", "Urgent Due")
        icons["due"] = "fa-clock" to com.myapp.app.ui.components.FaStyle.Regular
    }
    LogsState.pickerOptions = opts
    LogsState.pickerIcons = icons
    com.myapp.app.ui.popup.Popups.filterPicker = true
}

/** openDebtAmountPickerModal(category) */
internal fun openDebtAmountPicker(category: String) {
    val cs = DebtUi.of(category)
    LogsState.pickerCategory = category
    LogsState.pendingMode = "debtAmount"
    LogsState.pendingLabel = null
    LogsState.pickerIcons = emptyMap()
    LogsState.pickerTitleIsHeadline = false
    LogsState.pickerTitle = tr("filter_select_price", "Select Price Filter")
    LogsState.pendingValue = cs.sort.amount
    LogsState.pickerOptions = listOf(
        "all" to tr("logs_all_prices", "All Amounts"),
        "high" to tr("logs_high_to_low", "High to Low"),
        "low" to tr("logs_low_to_high", "Low to High"),
    )
    com.myapp.app.ui.popup.Popups.filterPicker = true
}
