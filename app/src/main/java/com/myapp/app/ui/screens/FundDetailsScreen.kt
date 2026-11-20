package com.myapp.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.myapp.app.data.AppState
import com.myapp.app.data.AppStore
import com.myapp.app.data.JsDate
import com.myapp.app.data.Transaction
import com.myapp.app.ui.AppActions
import com.myapp.app.ui.AppUi
import com.myapp.app.ui.Tab
import com.myapp.app.ui.components.AppBackdrop
import com.myapp.app.ui.components.AppText
import com.myapp.app.ui.components.FaIcon
import com.myapp.app.ui.components.FaStyle
import com.myapp.app.ui.components.FundTypeIcon
import com.myapp.app.ui.components.NoteGlyph
import com.myapp.app.ui.components.PulseDot
import com.myapp.app.ui.components.ScrollFadePaged
import com.myapp.app.ui.components.pagedItem
import com.myapp.app.ui.components.rememberPagedState
import com.myapp.app.ui.popup.AddDebt
import com.myapp.app.ui.popup.AddMoney
import com.myapp.app.ui.popup.FundHistoryFilter
import com.myapp.app.ui.popup.TimeCalendar
import com.myapp.app.ui.popup.TransferFund
import com.myapp.app.ui.popup.TxActions
import com.myapp.app.ui.popup.isAddMoneyEditable
import com.myapp.app.ui.theme.AppTheme
import com.myapp.app.ui.theme.Brand
import com.myapp.app.ui.theme.Tw
import com.myapp.app.util.ensureFundMeta
import com.myapp.app.util.fmtMoney
import com.myapp.app.util.fundDisplayName
import com.myapp.app.util.isDateFilterToday
import com.myapp.app.util.isSpendTx
import com.myapp.app.util.parseCssColor
import com.myapp.app.util.tr
import java.util.Calendar
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

fun installFundDetailsHooks() {
    AppActions.openAccountDetails = { name -> AppUi.openFundDetails(name) }
    // the calendar of the history filter puts a dot on every day this fund has a line
    com.myapp.app.ui.popup.DatePickerSources.fundHistoryDates = {
        val fund = AppUi.currentDetailsFund
        AppStore.state.transactions.filter { it.fund == fund }.mapNotNull { JsDate.parseMillis(it.date) }
    }
}

/** The pending amount of debts that sit inside this fund (own-fund deals count on both sides). */
private fun borrowedInFund(state: AppState, fund: String): Double = state.debts
    .filter { !it.settled && ((it.type == "debt" && it.fund == fund) || (it.type == "receivable" && it.counterFund == fund)) }
    .sumOf { debtRemaining(it) }

private fun toReceiveFromFund(state: AppState, fund: String): Double = state.debts
    .filter { !it.settled && ((it.type == "receivable" && it.fund == fund) || (it.type == "debt" && it.counterFund == fund)) }
    .sumOf { debtRemaining(it) }

/** goToLogsDebtsFromDetails(kind): close this screen and open Logs on the matching sub-tab. */
private fun goToLogsDebts(kind: String) {
    val state = AppStore.state
    val from = AppUi.currentDetailsFund
    val contributing = state.debts.filter { d ->
        !d.settled && (
            if (kind == "receivables") (d.type == "receivable" && d.fund == from) || (d.type == "debt" && d.counterFund == from)
            else (d.type == "debt" && d.fund == from) || (d.type == "receivable" && d.counterFund == from)
            )
    }
    val ownDeals = contributing.filter { !it.counterFund.isNullOrEmpty() }
    val hasPersonDeals = contributing.any { it.counterFund.isNullOrEmpty() }
    val defaultType = if (kind == "receivables") "receivable" else "debt"
    var targetType = defaultType
    // only own-fund deals behind the chip -> open the tab that deal actually lives in
    if (ownDeals.isNotEmpty() && !hasPersonDeals && ownDeals.none { it.type == defaultType }) targetType = ownDeals[0].type
    val targetTab = if (targetType == "receivable") "receivables" else "debts"
    val jump = ownDeals.any { it.type == targetType }

    AppUi.closeFundDetails()
    AppUi.logsSubtab = targetTab
    AppUi.switchTab(Tab.Logs)
    if (jump) DebtUi.of(targetTab).reset()   // make sure the card is visible (Pending, no search / sort)
    AppUi.switchLogsSubtab(targetTab)
    AddDebt.fundContext = if (from != null && state.funds.containsKey(from)) from else null
}

@Composable
fun FundDetailsScreen() {
    val fund = AppUi.currentDetailsFund ?: return
    val state = AppStore.observe()
    @Suppress("UNUSED_VARIABLE")
    val lang = com.myapp.app.data.AppSession.lang
    val c = AppTheme.colors

    // the fund was deleted / renamed away while this screen was open
    if (!state.funds.containsKey(fund)) {
        LaunchedEffect(fund) { AppUi.closeFundDetails() }
        return
    }
    BackHandler(enabled = !com.myapp.app.ui.popup.Popups.anyVisible) { AppUi.closeFundDetails() }

    val meta = ensureFundMeta(fund)
    val metaColor = parseCssColor(meta.color) ?: Brand.red
    val balance = state.funds[fund] ?: 0.0
    val debtSum = borrowedInFund(state, fund)
    val recvSum = toReceiveFromFund(state, fund)

    // ---- history rows (filter -> list), Total Spent follows the same filter
    val fundTx = state.transactions.filter { it.fund == fund }
    val f = FundHistoryFilter
    val hasManualFilter = f.mode != null && !f.value.isNullOrEmpty()
    val filtered: List<Transaction> = when {
        f.mode == "month" && !f.value.isNullOrEmpty() -> fundTx.filter { t -> JsDate.parseMillis(t.date)?.let { monthKey(it) == f.value } == true }
        f.mode == "date" && !f.value.isNullOrEmpty() -> {
            val target = JsDate.parseMillis(f.value)
            fundTx.filter { t ->
                val ms = JsDate.parseMillis(t.date)
                target != null && ms != null && sameLocalDay(ms, target)
            }
        }
        else -> {
            val now = Calendar.getInstance()
            fundTx.filter { t ->
                val ms = JsDate.parseMillis(t.date) ?: return@filter false
                val d = Calendar.getInstance().apply { timeInMillis = ms }
                d.get(Calendar.YEAR) == now.get(Calendar.YEAR) && d.get(Calendar.MONTH) == now.get(Calendar.MONTH)
            }
        }
    }
    val totalSpent = filtered.filter { isSpendTx(it) }.sumOf { it.amount }
    val visible = filtered.filter { it.historyHidden != true }
    val visibleAll = fundTx.filter { it.historyHidden != true }
    val sorted = visible.sortedByDescending { JsDate.parseMillis(it.date) ?: 0L }

    val noteText = state.fundNotes[fund] ?: ""
    val manualDateToday = f.mode == "date" && isDateFilterToday(f.value)
    val showFilterDot = hasManualFilter && !manualDateToday

    Box(
        Modifier
            .fillMaxSize()
            .background(c.bodyBg)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }, // swallow taps
        contentAlignment = Alignment.TopCenter,
    ) {
        // exactly the same page background as the Home screen (black + soft glow / light tint)
        AppBackdrop()
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier
                    .widthIn(max = 448.dp)
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // ---- top bar
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TopCircleButton("fa-arrow-left", 16.dp) { AppUi.closeFundDetails() }
                    AppText(
                        text = tr("fund_account_details_title", "Fund Details"), size = 18f, lineHeight = 28f,
                        weight = FontWeight.Bold, color = c.textGray900,
                    )
                    TopCircleButton("fa-pen", 14.dp) { AppActions.openEditFund(fund) }
                }

                // ---- balance card
                val cardShape = RoundedCornerShape(28.dp)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(cardShape)
                        .background(c.categoryCardBg, cardShape)
                        .then(if (c.categoryCardBorder != null) Modifier.border(1.dp, c.categoryCardBorder!!, cardShape) else Modifier)
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(
                                Modifier.size(48.dp).clip(CircleShape).background(metaColor.copy(alpha = 0.2f), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) { FundTypeIcon(meta.type, meta.icon, 18.dp, metaColor) }
                            AppText(
                                text = fundDisplayName(fund), size = 18f, lineHeight = 28f, weight = FontWeight.Bold,
                                color = c.categoryCardText, singleLineEllipsis = true, modifier = Modifier.weight(1f, fill = false),
                            )
                        }
                        val addShape = RoundedCornerShape(6.dp)
                        Box(
                            Modifier
                                .size(24.dp)
                                .clip(addShape)
                                .background(Color.White.copy(alpha = 0.15f), addShape)
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { AddMoney.open() },
                            contentAlignment = Alignment.Center,
                        ) { FaIcon("fa-plus", 10.dp, c.categoryCardText) }
                    }
                    Column {
                        AppText(
                            text = tr("acct_current_balance", "Current Balance"), size = 10f, lineHeight = 15f,
                            weight = FontWeight.Bold, letterSpacingEm = 0.05f, color = mutedOnCard(), uppercase = true,
                        )
                        // balance + the two small chips; the whole line shrinks to fit (fitBalanceText)
                        FitRow {
                            AppText(
                                text = "৳" + fmtMoney(balance), size = 20f, lineHeight = 28f, weight = FontWeight.ExtraBold,
                                letterSpacingEm = -0.025f, color = c.categoryCardText, noWrap = true,
                            )
                            if (debtSum > 0.005 || recvSum > 0.005) {
                                Row(
                                    Modifier.padding(start = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(13.dp),
                                ) {
                                    if (debtSum > 0.005) {
                                        BalanceChip(
                                            "fa-arrow-up-right", Color(0xFFFEE2E2), Color(0xFFB91C1C), Brand.red, debtSum,
                                        ) { goToLogsDebts("debts") }
                                    }
                                    if (recvSum > 0.005) {
                                        BalanceChip(
                                            "fa-arrow-down-left", Color(0xFFDCFCE7), Color(0xFF16A34A), Brand.green, recvSum,
                                        ) { goToLogsDebts("receivables") }
                                    }
                                }
                            }
                        }
                    }
                }

                // ---- Total Spent + Transfer Fund
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val boxShape = RoundedCornerShape(16.dp)
                    Column(
                        Modifier
                            .weight(2f)
                            .fillMaxHeight()
                            .clip(boxShape)
                            .background(c.categoryCardBg, boxShape)
                            .then(if (c.categoryCardBorder != null) Modifier.border(1.dp, c.categoryCardBorder!!, boxShape) else Modifier)
                            .padding(16.dp),
                    ) {
                        AppText(
                            text = tr("stats_total_spent", "Total Spent"), size = 10f, lineHeight = 15f,
                            weight = FontWeight.Bold, letterSpacingEm = 0.05f, color = mutedOnCard(), uppercase = true,
                        )
                        AppText(
                            text = "৳" + fmtMoney(totalSpent), size = 18f, lineHeight = 28f,
                            weight = FontWeight.ExtraBold, color = Brand.red, singleLineEllipsis = true,
                        )
                    }
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(boxShape)
                            .background(c.categoryCardBg, boxShape)
                            .then(if (c.categoryCardBorder != null) Modifier.border(1.dp, c.categoryCardBorder!!, boxShape) else Modifier)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                TransferFund.open(fund)
                            }
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                    ) {
                        FaIcon("fa-right-left", 14.dp, c.categoryCardText)
                        AppText(
                            text = tr("modal_transfer_fund_title", "Transfer Fund"), size = 12f, lineHeight = 16f,
                            weight = FontWeight.Bold, color = c.categoryCardText, align = TextAlign.Center,
                        )
                    }
                }

                // ---- Transaction History
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        AppText(
                            text = tr("acct_transaction_history", "Transaction History"), size = 18f, lineHeight = 28f,
                            weight = FontWeight.Bold, letterSpacingEm = -0.025f, color = c.textGray900,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SmallSquare(dot = noteText.isNotBlank(), onClick = { AppActions.openFundNote() }) {
                                NoteGlyph(14.dp, Brand.red)
                            }
                            SmallSquare(dot = showFilterDot, onClick = { TimeCalendar.open("fundHistory") }) {
                                FaIcon("fa-calendar-alt", 14.dp, Brand.red, style = FaStyle.Regular)
                            }
                        }
                    }

                    if (visible.isEmpty()) {
                        val msg = when {
                            visibleAll.isEmpty() -> tr("acct_no_transactions", "No transactions logged from this fund.")
                            hasManualFilter && f.mode == "month" -> tr("fund_history_no_tx_month", "No transactions in the selected month.")
                            hasManualFilter -> tr("fund_history_no_tx_date", "No transactions on the selected date.")
                            else -> tr("acct_no_transactions_month", "No transactions this month.")
                        }
                        val emptyShape = RoundedCornerShape(16.dp)
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(emptyShape)
                                .background(c.bgWhite60, emptyShape)
                                .border(1.dp, Color.White.copy(alpha = 0.3f), emptyShape)
                                .padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            AppText(
                                text = msg, size = 12f, lineHeight = 16f, weight = FontWeight.Bold,
                                color = c.textGray400, align = TextAlign.Center,
                            )
                        }
                    } else {
                        // 4 cards per view; the last visible one fades
                        val paged = rememberPagedState(visible = 4, gap = 8.dp)
                        ScrollFadePaged(state = paged, count = sorted.size, modifier = Modifier.fillMaxWidth().padding(end = 2.dp)) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                sorted.forEachIndexed { i, t ->
                                    key(t.id) {
                                        Box(Modifier.pagedItem(paged, i)) { TxCard(t, "") { color -> DetailsActions(t, color) } }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ pieces

@Composable
private fun mutedOnCard(): Color {
    val c = AppTheme.colors
    return if (c.isLight) Color(0xFF6B7280) else c.textGray400
}

@Composable
private fun TopCircleButton(icon: String, iconSize: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    val c = AppTheme.colors
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(c.bgGray200a80, CircleShape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { FaIcon(icon, iconSize, c.textGray700) }
}

@Composable
private fun SmallSquare(dot: Boolean, onClick: () -> Unit, icon: @Composable () -> Unit) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier
            .size(36.dp)
            .clip(shape)
            .background(c.bgGray200a50, shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        icon()
        if (dot) PulseDot(4.dp, Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 6.dp))
    }
}

/** One small "[arrow box] ৳amount" chip next to the balance (0.36 em of the balance size). */
@Composable
private fun BalanceChip(arrow: String, boxBg: Color, boxFg: Color, textColor: Color, amount: Double, onClick: () -> Unit) {
    Row(
        Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val boxShape = RoundedCornerShape(4.5.dp)
        Box(
            Modifier.padding(end = 3.9.dp).size(16.8.dp).clip(boxShape).background(boxBg, boxShape),
            contentAlignment = Alignment.Center,
        ) { FaIcon(arrow, 9.7.dp, boxFg) }
        AppText(
            text = "৳" + fmtMoney(amount), size = 12.96f, lineHeight = 16.8f, weight = FontWeight.ExtraBold,
            color = textColor, noWrap = true,
        )
    }
}

/**
 * fitBalanceText(): lays its content out on one line and shrinks the whole line (down to 32 %)
 * when it is wider than the available space.
 */
@Composable
private fun FitRow(content: @Composable () -> Unit) {
    Layout(
        content = { Row(verticalAlignment = Alignment.CenterVertically) { content() } },
        modifier = Modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val p = measurables[0].measure(Constraints(0, Constraints.Infinity, 0, Constraints.Infinity))
        val avail = constraints.maxWidth
        val scale = if (p.width > avail && p.width > 0) max(avail.toFloat() / p.width, 0.32f) else 1f
        val w = min((p.width * scale).roundToInt(), avail)
        val h = (p.height * scale).roundToInt()
        layout(constraints.maxWidth, h) {
            p.placeWithLayer(0, 0) {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0f)
            }
        }
    }
}

/** The right-hand button of a history card (generateDetailsTxMarkup). */
@Composable
private fun DetailsActions(t: Transaction, color: Color) {
    val isTransferIn = t.type == "transfer_in"
    val isAddMoney = t.type == "add_money"
    val padding = Modifier.padding(6.dp)
    val invisible: @Composable () -> Unit = {
        Box(padding.alpha(0f)) { FaIcon("fa-pen", 12.dp, color) }
    }
    when {
        isTransferIn || t.debtEntryId != null -> invisible()
        t.fromDebtPayment == true -> TrashButton(t, color)
        isAddMoney -> if (isAddMoneyEditable(t)) {
            Box(
                padding.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { AddMoney.openEdit(t.id) },
            ) { FaIcon("fa-pen", 12.dp, color) }
        } else invisible()
        else -> Box(
            padding.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                TxActions.openEditTransaction(t.id)
            },
        ) { FaIcon("fa-pen", 12.dp, color) }
    }
}

@Composable
private fun TrashButton(t: Transaction, color: Color) {
    Box(
        Modifier.padding(6.dp).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
            TxActions.deleteTransaction(t.id)
        },
    ) { FaIcon("fa-trash-alt", 12.dp, color) }
}

private fun monthKey(ms: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = ms }
    return "%04d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1)
}

private fun sameLocalDay(a: Long, b: Long): Boolean {
    val x = Calendar.getInstance().apply { timeInMillis = a }
    val y = Calendar.getInstance().apply { timeInMillis = b }
    return x.get(Calendar.YEAR) == y.get(Calendar.YEAR) && x.get(Calendar.DAY_OF_YEAR) == y.get(Calendar.DAY_OF_YEAR)
}
