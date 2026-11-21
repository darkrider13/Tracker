package com.myapp.app.ui.popup

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.animation.animateContentSize
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.myapp.app.data.AppState
import com.myapp.app.data.AppStore
import com.myapp.app.data.JsDate
import com.myapp.app.data.Transaction
import com.myapp.app.ui.AppActions
import com.myapp.app.ui.components.AppText
import com.myapp.app.ui.components.CategoryIcon
import com.myapp.app.ui.components.FaIcon
import com.myapp.app.ui.components.FundTypeIcon
import com.myapp.app.ui.components.SoftGlass
import com.myapp.app.ui.components.PagedViewport
import com.myapp.app.ui.components.entrance
import com.myapp.app.ui.components.pagedItem
import com.myapp.app.ui.components.rememberPagedState
import com.myapp.app.ui.theme.AppTheme
import com.myapp.app.ui.theme.Brand
import com.myapp.app.util.DateOpts
import com.myapp.app.util.debtPaymentNoteText
import com.myapp.app.util.ensureFundMeta
import com.myapp.app.util.fmtDate
import com.myapp.app.util.fmtMoney
import com.myapp.app.util.fmtTime
import com.myapp.app.util.fundDisplayName
import com.myapp.app.util.localizeDigits
import com.myapp.app.util.tr
import com.myapp.app.util.translatedCategoryLabel
import com.myapp.app.util.trf
import java.util.Calendar

// =============================================================================================
// Incoming Overview: every money-in line of every fund, in one combined list
// (Money Added, Receivable Collected, Money Borrowed - fund transfers are NOT counted)
// =============================================================================================

object IncomingOverview {
    /** "month" = this month, "last" = last month, "all" = all time. Resets to this month on every open. */
    var filter by mutableStateOf("month")

    /** "fund" = list by fund (first tab), "received" = list of the received transactions. Resets to "fund" on every open. */
    var tab by mutableStateOf("fund")

    fun open() {
        filter = "month"
        tab = "fund"
        Popups.incomingOverview = true
    }

    fun close() {
        Popups.incomingOverview = false
    }
}

fun installIncomingOverviewHooks() {
    AppActions.openIncomingOverview = { IncomingOverview.open() }
}

/** A line that brings new money INTO a fund. Fund transfers (transfer_in) are not new money, so they are left out. */
private fun isIncomingTx(t: Transaction): Boolean =
    t.type == "add_money" && t.historyHidden != true

private fun monthIndexOf(ms: Long): Int {
    val cal = Calendar.getInstance()
    cal.timeInMillis = ms
    return cal.get(Calendar.YEAR) * 12 + cal.get(Calendar.MONTH)
}

private fun incomingFor(state: AppState, filter: String): List<Transaction> {
    val nowCal = Calendar.getInstance()
    val curIdx = nowCal.get(Calendar.YEAR) * 12 + nowCal.get(Calendar.MONTH)
    fun ms(t: Transaction) = JsDate.parseMillis(t.date) ?: 0L
    return state.transactions
        .filter { t ->
            if (!isIncomingTx(t)) return@filter false
            when (filter) {
                "month" -> JsDate.parseMillis(t.date)?.let { monthIndexOf(it) == curIdx } ?: false
                "last" -> JsDate.parseMillis(t.date)?.let { monthIndexOf(it) == curIdx - 1 } ?: false
                else -> true
            }
        }
        .sortedWith(compareByDescending<Transaction> { ms(it) }.thenByDescending { it.id })
}

/** "From whom": the person of a debt line, or the note of added money. */
private fun incomingSourceText(t: Transaction): String? {
    debtPaymentNoteText(t)?.let { return it }
    return t.description.ifEmpty { null }
}

private class FundTotal(val fund: String, val total: Double, val count: Int)

@Composable
fun IncomingOverviewSheet() {
    val open = Popups.incomingOverview
    // Driven by one Animatable that is read only in the draw / layer phase (scrim + slide), so the sheet is NOT
    // recomposed on every frame of the animation. `mounted` keeps it on screen until the slide-out has finished.
    val progress = remember { androidx.compose.animation.core.Animatable(0f) }
    var mounted by remember { mutableStateOf(false) }
    LaunchedEffect(open) {
        if (open) {
            mounted = true
            progress.animateTo(1f, tween(SHEET_OPEN_MS, easing = SheetEase))
        } else if (mounted) {
            progress.animateTo(0f, tween(SHEET_CLOSE_MS, easing = SheetCloseEase))
            mounted = false
        }
    }
    if (!mounted) return
    val c = AppTheme.colors
    val state = AppStore.observe()
    val revision = AppStore.revision
    val filter = IncomingOverview.filter
    BackHandler(enabled = open) { IncomingOverview.close() }

    val list = remember(state, revision, filter) { incomingFor(state, filter) }
    val total = remember(list) { list.sumOf { it.amount } }
    val monthTotal = remember(state, revision) {
        if (filter == "month") 0.0 else incomingFor(state, "month").sumOf { it.amount }
    }
    val perFund = remember(list) {
        list.groupBy { it.fund }
            .map { (f, rows) -> FundTotal(f, rows.sumOf { it.amount }, rows.size) }
            .sortedByDescending { it.total }
    }

    // the rows are mounted a moment after the sheet has slid in, so the slide itself runs on a light frame
    var listReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(SHEET_ROWS_DELAY_MS)
        listReady = true
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .drawBehind { drawRect(Color.Black.copy(alpha = 0.45f * progress.value)) }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { IncomingOverview.close() },
        contentAlignment = Alignment.BottomCenter,
    ) {
        val sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        // The sheet is only as tall as its content (no empty space under the last card), up to 88 % of the screen.
        // (The rows are mounted only after the slide-in, so the sheet's height does not change while it moves.)
        val maxSheetH = maxHeight * 0.88f
        // 4 whole rows when the screen is tall enough, fewer on a small one (so nothing below needs an outer scroll)
        val visibleRows = (((maxSheetH.value - INCOMING_CHROME_DP + 8f) / (INCOMING_ROW_DP + 8f)).toInt()).coerceIn(2, INCOMING_VISIBLE)
        Column(
            Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .heightIn(max = maxSheetH)
                .graphicsLayer { translationY = (1f - progress.value) * size.height }
                .clip(sheetShape)
                .background(if (c.isLight) Color(0xFFF3F4F6) else Color(0xFF131315), sheetShape)
                // taps on the sheet itself must not close it
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                .padding(start = 16.dp, end = 16.dp, top = 20.dp),
        ) {
            // ---- header
            Row(
                Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f)) {
                    AppText(
                        text = tr("incoming_overview", "Incoming Overview"), size = 16f, lineHeight = 24f,
                        weight = FontWeight.ExtraBold, color = c.textGray900,
                    )
                    AppText(
                        text = tr("incoming_all_funds", "All funds combined"), size = 10f, lineHeight = 15f,
                        weight = FontWeight.Bold, color = c.textGray400,
                    )
                }
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(c.bgWhite80, CircleShape)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { IncomingOverview.close() },
                    contentAlignment = Alignment.Center,
                ) { FaIcon("fa-xmark", 14.dp, c.textGray500) }
            }

            // ---- filter chips
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(tr("incoming_filter_month", "This Month"), filter == "month", Modifier.weight(1f)) { IncomingOverview.filter = "month" }
                FilterChip(tr("incoming_filter_last", "Last Month"), filter == "last", Modifier.weight(1f)) { IncomingOverview.filter = "last" }
                FilterChip(tr("incoming_filter_all", "All Time"), filter == "all", Modifier.weight(1f)) { IncomingOverview.filter = "all" }
            }

            // ---- total + the two tabs. Each tab shows exactly 4 whole rows; the rest scrolls a row at a time (no cut-off row).
            // The outer scroll only matters on a very small screen, where the sheet itself is shorter than its content.
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth()
                    // the sheet glides to its new height when the tab / period changes the number of rows
                    .animateContentSize(tween(240, easing = DropdownEase)),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TotalCard(
                    label = when (filter) {
                        "last" -> tr("incoming_total_last", "Total Received Last Month")
                        "all" -> tr("incoming_total_all", "Total Received (All Time)")
                        else -> tr("incoming_total_month", "Total Received This Month")
                    },
                    total = total,
                    count = list.size,
                    monthTotal = if (filter == "month") null else monthTotal,
                )
                if (list.isEmpty()) {
                    val shape = RoundedCornerShape(16.dp)
                    Box(
                        Modifier.fillMaxWidth().clip(shape).background(c.bgWhite70, shape).padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        AppText(
                            text = tr("incoming_empty", "No incoming transactions in this period."),
                            size = 11f, lineHeight = 16.5f, weight = FontWeight.Bold,
                            color = c.textGray500, align = TextAlign.Center,
                        )
                    }
                } else {
                    val tab = IncomingOverview.tab
                    TabSwitch(tab)
                    // a fresh list (new measuring, new entrance) whenever the tab or the period changes
                    key(tab, filter) {
                        if (!listReady) {
                            // placeholder of about the final size while the sheet slides in (the rows come in right after)
                            val n = minOf(if (tab == "fund") perFund.size else list.size, visibleRows)
                            Spacer(Modifier.fillMaxWidth().height(INCOMING_ROW_DP.dp * n + 8.dp * (n - 1)))
                        } else if (tab == "fund") {
                            IncomingPagedList(count = perFund.size, visible = visibleRows) { i -> FundRow(perFund[i], total) }
                        } else {
                            IncomingPagedList(count = list.size, visible = visibleRows) { i -> IncomingRow(list[i]) }
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

/** How many rows are visible at once in each tab. */
private const val INCOMING_VISIBLE = 4

/** Height of the sheet above the list (header, chips, total, tabs, paddings) and of one row, in dp: used to pick how many rows fit. */
private const val INCOMING_CHROME_DP = 320f
private const val INCOMING_ROW_DP = 78f

/**
 * Exactly [INCOMING_VISIBLE] whole rows, the rest scrolls one row at a time (same viewport as the Home fund list).
 * The first rows come in one after another (fade + rise + settle), like the fund list does when it opens.
 */
@Composable
private fun IncomingPagedList(count: Int, visible: Int, row: @Composable (Int) -> Unit) {
    val paged = rememberPagedState(visible = visible, gap = 8.dp)
    PagedViewport(
        state = paged,
        count = count,
        modifier = Modifier.fillMaxWidth(),
        animateSize = false,
        smoothSnap = true,
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (i in 0 until count) key(i) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .pagedItem(paged, i)
                        .then(
                            if (i < visible + 1) Modifier.entrance(go = true, delayMs = 20 + i * 55, rise = 18.dp, durationMs = 440, scaleFrom = 0.94f)
                            else Modifier,
                        ),
                ) { row(i) }
            }
        }
    }
}

@Composable
private fun TabSwitch(tab: String) {
    val c = AppTheme.colors
    val track = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(track)
            .background(c.bgWhite70, track)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TabButton(tr("incoming_by_fund", "By Fund"), tab == "fund", Modifier.weight(1f)) { IncomingOverview.tab = "fund" }
        TabButton(tr("incoming_tab_received", "By Received"), tab == "received", Modifier.weight(1f)) { IncomingOverview.tab = "received" }
    }
}

@Composable
private fun TabButton(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(10.dp)
    val bg by androidx.compose.animation.animateColorAsState(
        if (selected) Brand.green.copy(alpha = 0.20f) else Color.Transparent,
        animationSpec = tween(200),
        label = "incomingTabBg",
    )
    Box(
        modifier
            .clip(shape)
            .background(bg, shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        AppText(
            text = label, size = 11f, lineHeight = 16f, weight = FontWeight.ExtraBold,
            color = if (selected) Brand.green else c.textGray500, noWrap = true,
        )
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .clip(shape)
            .background(if (selected) Brand.green else c.bgWhite70, shape)
            .border(BorderStroke(1.dp, if (selected) Brand.green else c.borderGray200), shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        AppText(
            text = label, size = 11f, lineHeight = 16f, weight = FontWeight.ExtraBold,
            color = if (selected) Color.White else c.textGray600, noWrap = true,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    val c = AppTheme.colors
    AppText(
        text = text, size = 10f, lineHeight = 15f, weight = FontWeight.Bold, letterSpacingEm = 0.05f,
        color = c.textGray400, uppercase = true, modifier = Modifier.padding(top = 6.dp, start = 2.dp),
    )
}

@Composable
private fun TotalCard(label: String, total: Double, count: Int, monthTotal: Double?) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brand.green.copy(alpha = if (c.isLight) 0.10f else 0.14f), shape)
            .border(BorderStroke(1.dp, Brand.green.copy(alpha = 0.35f)), shape)
            .padding(16.dp),
    ) {
        AppText(
            text = label, size = 10f, lineHeight = 15f, weight = FontWeight.Bold, letterSpacingEm = 0.05f,
            color = c.textGray500, uppercase = true,
        )
        AppText(
            text = "৳" + fmtMoney(total), size = 26f, lineHeight = 34f, weight = FontWeight.ExtraBold,
            letterSpacingEm = -0.025f, color = Brand.green, noWrap = true, modifier = Modifier.padding(top = 2.dp),
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppText(
                text = trf("stats_tx_count_suffix", "{count} transactions", "count" to localizeDigits(count.toString())),
                size = 10f, lineHeight = 15f, weight = FontWeight.Bold, color = c.textGray400,
            )
            if (monthTotal != null) {
                AppText(
                    text = trf("incoming_this_month_pill", "This month: ৳{amt}", "amt" to fmtMoney(monthTotal)),
                    size = 10f, lineHeight = 15f, weight = FontWeight.ExtraBold, color = Brand.green, noWrap = true,
                )
            }
        }
    }
}

@Composable
private fun FundRow(f: FundTotal, total: Double) {
    val c = AppTheme.colors
    val meta = ensureFundMeta(f.fund)
    val pct = if (total > 0) f.total / total else 0.0
    val shape = RoundedCornerShape(16.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(c.bgWhite70, shape).padding(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val iconShape = RoundedCornerShape(12.dp)
                Box(Modifier.size(36.dp).clip(iconShape).background(c.bgGray100, iconShape), contentAlignment = Alignment.Center) {
                    FundTypeIcon(meta.type, meta.icon, 14.dp, Brand.green)
                }
                Column(Modifier.weight(1f)) {
                    AppText(
                        text = fundDisplayName(f.fund), size = 12f, lineHeight = 16f, weight = FontWeight.Bold,
                        color = c.textGray900, singleLineEllipsis = true,
                    )
                    AppText(
                        text = trf("stats_tx_count_suffix", "{count} transactions", "count" to localizeDigits(f.count.toString())) +
                            " · " + localizeDigits("%.1f".format(pct * 100)) + "%",
                        size = 10f, lineHeight = 15f, weight = FontWeight.Bold, color = c.textGray400,
                    )
                }
            }
            AppText(
                text = "৳" + fmtMoney(f.total), size = 13f, lineHeight = 20f, weight = FontWeight.ExtraBold,
                color = Brand.green, noWrap = true, modifier = Modifier.padding(start = 8.dp),
            )
        }
        Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(6.dp).clip(CircleShape).background(c.bgGray300a60, CircleShape)) {
            Box(
                Modifier.fillMaxHeight().fillMaxWidth(fraction = pct.toFloat().coerceIn(0f, 1f))
                    .clip(CircleShape).background(Brand.green, CircleShape),
            )
        }
    }
}

@Composable
private fun IncomingRow(t: Transaction) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(if (c.isLight) 12.dp else SoftGlass.radius)
    val source = incomingSourceText(t)
    val meta = ensureFundMeta(t.fund)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (c.isLight) c.bgWhite else SoftGlass.bg, shape)
            .border(1.dp, if (c.isLight) c.homeCardBorder else SoftGlass.border, shape)
            // every row has the same height (with or without the "from whom" line), so a snapped page is always exactly
            // 4 whole rows and the next row never peeks in at the bottom
            .heightIn(min = 78.dp)
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(c.bgGray100, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                CategoryIcon(t.category, 14.dp, Brand.green)
            }
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                AppText(
                    text = translatedCategoryLabel(t.category), size = 12f, lineHeight = 16f,
                    weight = FontWeight.Bold, color = c.textGray800, singleLineEllipsis = true,
                )
                if (source != null) {
                    AppText(
                        text = source, size = 9f, lineHeight = 13.5f, weight = FontWeight.Medium,
                        color = c.textGray500, singleLineEllipsis = true,
                    )
                }
                // the fund that received the money
                Row(
                    Modifier
                        .padding(top = 2.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x2616A34A), RoundedCornerShape(6.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    FundTypeIcon(meta.type, meta.icon, 7.dp, Brand.green)
                    AppText(
                        text = fundDisplayName(t.fund), size = 8f, lineHeight = 11f, weight = FontWeight.Bold,
                        color = Brand.green, singleLineEllipsis = true,
                    )
                }
            }
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 8.dp)) {
            AppText(
                text = fmtDate(t.date, DateOpts(day = "2-digit", month = "short")) + " • " + fmtTime(t.date),
                size = 9f, lineHeight = 13.5f, weight = FontWeight.Medium, color = c.textGray400,
                modifier = Modifier.padding(bottom = 4.dp), noWrap = true,
            )
            AppText(
                text = "+৳" + fmtMoney(t.amount), size = 11f, lineHeight = 16.5f, weight = FontWeight.Black,
                color = Brand.green, noWrap = true,
            )
        }
    }
}
