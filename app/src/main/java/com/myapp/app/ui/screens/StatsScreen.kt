package com.myapp.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.myapp.app.ui.components.pageScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.myapp.app.data.AppSession
import com.myapp.app.data.AppState
import com.myapp.app.data.AppStore
import com.myapp.app.data.JsDate
import com.myapp.app.data.Transaction
import com.myapp.app.ui.AppActions
import com.myapp.app.ui.AppUi
import com.myapp.app.ui.components.AppText
import com.myapp.app.ui.components.CategoryIcon
import com.myapp.app.ui.components.FaIcon
import com.myapp.app.ui.components.FaStyle
import com.myapp.app.ui.components.PagedViewport
import com.myapp.app.ui.components.pagedItem
import com.myapp.app.ui.components.rememberPagedState
import com.myapp.app.ui.popup.AllCategoriesSheet
import com.myapp.app.ui.popup.AllCatMonthPicker
import com.myapp.app.ui.theme.AppTheme
import com.myapp.app.ui.theme.Brand
import com.myapp.app.ui.theme.Tw
import com.myapp.app.util.BN_MONTHS
import com.myapp.app.util.CategoryGroup
import com.myapp.app.util.DateOpts
import com.myapp.app.util.debtPaymentNoteText
import com.myapp.app.util.fmtDate
import com.myapp.app.util.fmtMoney
import com.myapp.app.util.getLang
import com.myapp.app.util.groupByCategory
import com.myapp.app.util.isSpendTx
import com.myapp.app.util.localizeDigits
import com.myapp.app.util.tr
import com.myapp.app.util.trf
import com.myapp.app.util.translatedCategoryLabel
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private val GREEN600 = Tw.green600
private val GREEN500 = Tw.green500

// ------------------------------------------------------------------ month scope

class StatsMonth(val year: Int, val monthIdx: Int)

/** getStatsSpendMonthScope(): the month the Spending tab shows. */
fun statsSpendMonthScope(state: AppState): StatsMonth {
    AppUi.statsMonthOverride?.let { return it }
    val now = Calendar.getInstance()
    val f = state.filters
    if (f.time == "custommonth" && f.customMonth != null) {
        val p = f.customMonth!!.split("-")
        return StatsMonth(p[0].toInt(), p[1].toInt() - 1)
    }
    if (f.time == "custom" && f.customDate != null) {
        val ms = JsDate.parseMillis(f.customDate)
        if (ms != null) {
            val c = Calendar.getInstance().apply { timeInMillis = ms }
            return StatsMonth(c.get(Calendar.YEAR), c.get(Calendar.MONTH))
        }
    }
    return StatsMonth(now.get(Calendar.YEAR), now.get(Calendar.MONTH))
}

/** allCatMonthLabel(scope): "মার্চ ২০২৬" / "March 2026". */
fun allCatMonthLabel(scope: StatsMonth): String =
    if (getLang() == "bn") "${BN_MONTHS[scope.monthIdx]} ${localizeDigits(scope.year.toString())}"
    else {
        val c = Calendar.getInstance().apply { set(scope.year, scope.monthIdx, 1) }
        java.text.SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(c.time)
    }

private fun inMonth(t: Transaction, year: Int, monthIdx: Int): Boolean {
    val ms = JsDate.parseMillis(t.date) ?: return false
    val c = Calendar.getInstance().apply { timeInMillis = ms }
    return c.get(Calendar.YEAR) == year && c.get(Calendar.MONTH) == monthIdx
}

/** Spend lines of the active fund inside one month. */
fun statsMonthSpend(state: AppState, scope: StatsMonth): List<Transaction> =
    state.transactions.filter { it.fund == state.activeFund && isSpendTx(it) && inMonth(it, scope.year, scope.monthIdx) }

// ------------------------------------------------------------------ helpers (CSS negative margins / dividers)

/** `-mt-N`: pulls the element up by [amount] and lets the following siblings follow it. */
fun Modifier.pullUp(amount: Dp): Modifier = this.layout { m, c ->
    val p = m.measure(c)
    val d = amount.roundToPx()
    layout(p.width, max(0, p.height - d)) { p.place(0, -d) }
}

@Composable
private fun dividerColor(): Color = if (AppTheme.colors.isLight) Color(0x736B7280) else Color(0x3DFFFFFF)

@Composable
private fun StatsCard(
    topPad: Dp = 20.dp,
    bottomPad: Dp = 20.dp,
    horizontalPad: Dp = 20.dp,
    spacing: Dp = 16.dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.bgGray200a50, shape)
            .padding(start = horizontalPad, end = horizontalPad, top = topPad, bottom = bottomPad),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) { content() }
}

@Composable
private fun CardHeader(icon: String, iconColor: Color, title: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    val c = AppTheme.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.size(28.dp).shadow(1.dp, CircleShape).background(c.bgWhite, CircleShape),
                contentAlignment = Alignment.Center,
            ) { FaIcon(icon, 12.dp, iconColor) }
            AppText(text = title, size = 14f, lineHeight = 20f, weight = FontWeight.Bold, color = c.textGray900)
        }
        trailing?.invoke()
    }
}

// ------------------------------------------------------------------ screen

@Composable
fun StatsScreen() {
    val state = AppStore.observe()
    @Suppress("UNUSED_VARIABLE")
    val lang = AppSession.lang
    val c = AppTheme.colors
    val spending = AppUi.statsSubtab != "debts"
    val scope = statsSpendMonthScope(state)

    val pageScrollState = androidx.compose.foundation.rememberScrollState()
    Box(Modifier.fillMaxSize().pageScroll(pageScrollState), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 448.dp)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 128.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // title + month button
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    AppText(
                        text = if (spending) tr("stats_title_spending", "Spending Analytics") else tr("stats_title_debts", "Liability Analytics"),
                        size = 24f, lineHeight = 32f, weight = FontWeight.Bold, letterSpacingEm = -0.025f, color = c.textGray900,
                    )
                    AppText(
                        text = if (spending) tr("stats_subtitle_spending", "Deep insights into your spending patterns.")
                        else tr("stats_subtitle_debts", "Insights into your outstanding debts and receivables."),
                        size = 12f, lineHeight = 16f, color = c.textGray500, modifier = Modifier.padding(top = 2.dp),
                    )
                }
                if (spending) {
                    Row(
                        Modifier
                            .padding(top = 5.dp)
                            .height(22.dp)
                            .clip(CircleShape)
                            .background(c.bgGray200a60, CircleShape)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                AllCatMonthPicker.open()
                            }
                            .padding(start = 10.dp, end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        FaIcon("fa-calendar-alt", 12.dp, Brand.red, style = FaStyle.Regular)
                        AppText(
                            text = allCatMonthLabel(scope), size = 11f, lineHeight = 16.5f,
                            weight = FontWeight.ExtraBold, color = c.textGray700, noWrap = true,
                        )
                    }
                }
            }

            // Spending | Debts & Receivables
            val swShape = RoundedCornerShape(16.dp)
            Row(
                Modifier.fillMaxWidth().clip(swShape).background(c.bgGray200a60, swShape).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf("spending" to tr("stats_tab_spending", "Spending"), "debts" to tr("stats_tab_debts", "Debts & Receivables")).forEach { (key, label) ->
                    val active = AppUi.statsSubtab == key || (key == "spending" && AppUi.statsSubtab != "debts")
                    val itemShape = RoundedCornerShape(12.dp)
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(itemShape)
                            .background(if (active) Brand.red else Color.Transparent, itemShape)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { AppUi.statsSubtab = key }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        AppText(
                            text = label, size = 12f, lineHeight = 16f, weight = FontWeight.Bold,
                            color = if (active) Color.White else c.textGray500, align = TextAlign.Center,
                        )
                    }
                }
            }

            if (spending) SpendingTab(state, scope) else LiabilityTab(state)
        }
    }
}

// ------------------------------------------------------------------ Spending tab

@Composable
private fun SpendingTab(state: AppState, scope: StatsMonth) {
    // Same AppState object every time -> Compose would skip this function; reading the revision makes it redraw after a commit.
    AppStore.observe()
    val c = AppTheme.colors
    val filtered = statsMonthSpend(state, scope)

    if (filtered.isEmpty()) {
        val shape = RoundedCornerShape(16.dp)
        Box(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(c.bgWhite60, shape)
                .border(1.dp, Color.White.copy(alpha = 0.3f), shape)
                .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            AppText(
                text = tr("stats_no_spending_data", "No spending statistics available for this asset."),
                size = 12f, lineHeight = 16f, weight = FontWeight.Bold, color = c.textGray400, align = TextAlign.Center,
            )
        }
        return
    }

    val totalSpent = filtered.sumOf { it.amount }
    val txCount = filtered.size
    // Daily average: current month -> 1st up to today, any other month -> the whole month.
    val now = Calendar.getInstance()
    val daysInMonth = Calendar.getInstance().apply { set(scope.year, scope.monthIdx, 1) }.getActualMaximum(Calendar.DAY_OF_MONTH)
    val isCurrent = now.get(Calendar.YEAR) == scope.year && now.get(Calendar.MONTH) == scope.monthIdx
    val daySpan = max(1, if (isCurrent) now.get(Calendar.DAY_OF_MONTH) else daysInMonth)
    val dailyAvg = totalSpent / daySpan
    val groups = groupByCategory(filtered)

    // ---- Overview
    StatsCard(topPad = 12.dp, spacing = 16.dp) {
        CardHeader("fa-chart-simple", Brand.red, tr("stats_overview", "Overview"))
        val div = dividerColor()
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            OverviewCell("৳" + fmtMoney(Math.round(totalSpent).toDouble(), 0), tr("stats_total_spent", "Total Spent"), Brand.red, Modifier.weight(1f))
            Box(Modifier.width(1.dp).fillMaxHeight().background(div))
            OverviewCell("৳" + fmtMoney(Math.round(dailyAvg).toDouble(), 0), tr("stats_daily_avg", "Daily Avg"), c.textGray900, Modifier.weight(1f))
            Box(Modifier.width(1.dp).fillMaxHeight().background(div))
            OverviewCell(localizeDigits(txCount.toString()), tr("logs_transactions", "Transactions"), c.textGray900, Modifier.weight(1f))
        }
    }

    // ---- Top Spending Category (top 3 slider)
    TopCategoriesCard(groups, totalSpent)

    // ---- Spending Insights
    // sort(b - a)[0] keeps the FIRST of equal amounts
    val biggestPick = filtered.sortedByDescending { it.amount }.first()
    val biggestLabel = translatedCategoryLabel(biggestPick.category)
    val dayCounts = LinkedHashMap<String, Int>()
    filtered.forEach { t ->
        val wd = fmtDate(t.date, DateOpts(weekday = "long"))
        dayCounts[wd] = (dayCounts[wd] ?: 0) + 1
    }
    val mostActiveDay = dayCounts.entries.sortedByDescending { it.value }.firstOrNull()?.key ?: "—"
    val avgPerTx = totalSpent / txCount
    val noteText = debtPaymentNoteText(biggestPick) ?: biggestPick.description

    StatsCard(topPad = 8.dp, bottomPad = 16.dp, horizontalPad = 16.dp, spacing = 12.dp) {
        CardHeader("fa-lightbulb", Brand.red, tr("stats_insights_title", "Spending Insights"))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            InsightRow(
                "fa-receipt", FaStyle.Solid, tr("stats_biggest_expense", "Biggest Expense"),
                if (noteText.isNotEmpty()) "$noteText ($biggestLabel)" else biggestLabel,
                "৳" + fmtMoney(Math.round(biggestPick.amount).toDouble(), 0),
            )
            InsightRow(
                "fa-calendar-check", FaStyle.Regular, tr("stats_most_active_day", "Most Active Day"),
                trf("stats_tx_count_suffix", "${dayCounts[mostActiveDay] ?: 0} transactions", "count" to localizeDigits((dayCounts[mostActiveDay] ?: 0).toString())),
                mostActiveDay,
            )
            InsightRow(
                "fa-calculator", FaStyle.Solid, tr("stats_avg_per_tx", "Avg. per Transaction"),
                trf("stats_across_entries", "across $txCount entries", "count" to localizeDigits(txCount.toString())),
                "৳" + fmtMoney(Math.round(avgPerTx).toDouble(), 0),
            )
            InsightRow(
                "fa-layer-group", FaStyle.Solid, tr("stats_categories_used", "Categories Used"),
                tr("stats_unique_categories", "unique spending categories"),
                localizeDigits(groups.size.toString()),
            )
        }
    }
}

@Composable
private fun OverviewCell(value: String, label: String, valueColor: Color, modifier: Modifier) {
    val c = AppTheme.colors
    Column(modifier.padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        AppText(
            text = value, size = 14f, lineHeight = 20f, weight = FontWeight.ExtraBold, color = valueColor,
            align = TextAlign.Center, singleLineEllipsis = true,
        )
        AppText(
            text = label, size = 10f, lineHeight = 15f, weight = FontWeight.Bold, color = c.textGray500,
            align = TextAlign.Center, modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun InsightRow(icon: String, style: FaStyle, title: String, sub: String, value: String) {
    val c = AppTheme.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val shape = RoundedCornerShape(12.dp)
            Box(Modifier.size(32.dp).clip(shape).background(c.bgWhite, shape), contentAlignment = Alignment.Center) {
                FaIcon(icon, 12.dp, c.textGray500, style = style)
            }
            Column(Modifier.weight(1f)) {
                AppText(text = title, size = 12f, lineHeight = 16f, weight = FontWeight.Bold, color = c.textGray900)
                AppText(text = sub, size = 9f, lineHeight = 13.5f, weight = FontWeight.Bold, color = c.textGray400, singleLineEllipsis = true)
            }
        }
        AppText(
            text = value, size = 12f, lineHeight = 16f, weight = FontWeight.Black, color = c.textGray900,
            noWrap = true, modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun TopCategoriesCard(groups: List<CategoryGroup>, totalSpent: Double) {
    val c = AppTheme.colors
    val top = groups.take(3)
    // same red as the progress bar below, so the text and the bar match
    val textRed = Brand.red
    val subRed = Brand.red
    StatsCard(topPad = 12.dp, spacing = 12.dp) {
        CardHeader(
            "fa-arrow-trend-up", Brand.red, tr("stats_top_category", "Top Spending Category"),
            trailing = {
                Row(
                    Modifier
                        .clip(CircleShape)
                        .background(c.bgWhite80, CircleShape)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            AllCategoriesSheet.open()
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    AppText(
                        text = tr("stats_see_all", "See all"), size = 10f, lineHeight = 15f,
                        weight = FontWeight.Black, color = textRed, noWrap = true,
                    )
                    FaIcon("fa-chevron-right", 8.dp, textRed)
                }
            },
        )
        val pager = rememberPagerState(pageCount = { top.size })
        HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth()) { page ->
            val g = top[page]
            val pct = if (totalSpent > 0) g.total / totalSpent * 100 else 0.0
            Column(Modifier.fillMaxWidth()) {
                // no edge / no fill: the item row blends straight into the card background
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                            CategoryIcon(g.label, 14.dp, textRed)
                        }
                        Column(Modifier.weight(1f)) {
                            AppText(text = g.label, size = 14f, lineHeight = 20f, weight = FontWeight.Bold, color = textRed, singleLineEllipsis = true)
                            AppText(
                                text = trf("stats_pct_of_total", "${"%.1f".format(pct)}% of total spending", "pct" to localizeDigits("%.1f".format(pct))) +
                                    " · " + trf("stats_tx_count_suffix", "${g.count} transactions", "count" to localizeDigits(g.count.toString())),
                                size = 10f, lineHeight = 15f, weight = FontWeight.Bold, color = subRed,
                            )
                        }
                    }
                    AppText(
                        text = "৳" + fmtMoney(Math.round(g.total).toDouble(), 0), size = 16f, lineHeight = 24f,
                        weight = FontWeight.ExtraBold, color = textRed, noWrap = true, modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Box(
                    Modifier.padding(top = 12.dp).fillMaxWidth().height(8.dp).clip(CircleShape).background(c.bgGray300a60, CircleShape),
                ) {
                    Box(
                        Modifier.fillMaxHeight().fillMaxWidth(fraction = (pct / 100.0).toFloat().coerceIn(0f, 1f))
                            .clip(CircleShape).background(Brand.red, CircleShape),
                    )
                }
            }
        }
        if (top.size > 1) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
                for (i in top.indices) {
                    val active = pager.currentPage == i
                    val w by animateDpAsState(if (active) 16.dp else 6.dp, tween(200), label = "dotW")
                    val col by animateColorAsState(if (active) Brand.red else Color(0x999CA3AF), tween(200), label = "dotC")
                    Box(Modifier.width(w).height(6.dp).background(col, CircleShape))
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Debts & Receivables tab

@Composable
private fun LiabilityTab(state: AppState) {
    // Same AppState object every time -> Compose would skip this function; reading the revision makes it redraw after a commit.
    AppStore.observe()
    val c = AppTheme.colors
    val debts = state.debts
    val pendingDebts = debts.filter { it.type == "debt" && !it.settled }
    val pendingReceivables = debts.filter { it.type == "receivable" && !it.settled }
    val allDebts = debts.filter { it.type == "debt" }
    val allReceivables = debts.filter { it.type == "receivable" }

    val totalOwe = pendingDebts.sumOf { debtRemaining(it) }
    val totalOwed = pendingReceivables.sumOf { debtRemaining(it) }
    val net = totalOwed - totalOwe

    val debtsSettled = allDebts.count { it.settled }
    val receivablesSettled = allReceivables.count { it.settled }
    val debtsPct = if (allDebts.isNotEmpty()) debtsSettled.toDouble() / allDebts.size * 100 else 0.0
    val recvPct = if (allReceivables.isNotEmpty()) receivablesSettled.toDouble() / allReceivables.size * 100 else 0.0

    fun partners(entries: List<com.myapp.app.data.Debt>): List<Pair<String, Double>> {
        val map = LinkedHashMap<String, Double>()
        entries.forEach { map[it.person] = (map[it.person] ?: 0.0) + debtRemaining(it) }
        return map.entries.sortedByDescending { it.value }.map { it.key to it.value }
    }
    val topOwe = partners(pendingDebts)
    val topOwed = partners(pendingReceivables)

    // ---- Net Position
    val netColor = if (net >= 0) GREEN600 else Brand.red
    StatsCard(topPad = 12.dp, spacing = 16.dp) {
        CardHeader(if (net >= 0) "fa-arrow-trend-up" else "fa-arrow-trend-down", netColor, tr("stats_net_position", "Net Position"))
        AppText(
            text = (if (net >= 0) "+" else "-") + "৳" + fmtMoney(Math.round(abs(net)).toDouble(), 0),
            size = 24f, lineHeight = 32f, weight = FontWeight.ExtraBold, color = netColor, modifier = Modifier.pullUp(8.dp),
        )
        AppText(
            text = when {
                net > 0 -> tr("stats_net_positive", "Others owe you more than you owe.")
                net < 0 -> tr("stats_net_negative", "You owe more than others owe you.")
                else -> tr("stats_net_zero", "You are settled up.")
            },
            size = 9f, lineHeight = 13.5f, weight = FontWeight.Medium, color = c.textGray500, modifier = Modifier.pullUp(12.dp),
        )
        Column(Modifier.pullUp(8.dp)) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(dividerColor()))
            Row(Modifier.fillMaxWidth().padding(top = 8.dp).height(IntrinsicSize.Min)) {
                Column(Modifier.weight(1f)) {
                    AppText(text = tr("home_you_owe", "You Owe"), size = 10f, lineHeight = 15f, weight = FontWeight.Bold, color = c.textGray400, uppercase = true)
                    AppText(text = "৳" + fmtMoney(Math.round(totalOwe).toDouble(), 0), size = 16f, lineHeight = 24f, weight = FontWeight.ExtraBold, color = Brand.red)
                }
                Box(Modifier.padding(horizontal = 12.dp).width(1.dp).fillMaxHeight().background(dividerColor()))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    AppText(text = tr("home_you_are_owed", "You Are Owed"), size = 10f, lineHeight = 15f, weight = FontWeight.Bold, color = c.textGray400, uppercase = true)
                    AppText(text = "৳" + fmtMoney(Math.round(totalOwed).toDouble(), 0), size = 16f, lineHeight = 24f, weight = FontWeight.ExtraBold, color = GREEN600)
                }
            }
        }
    }

    // ---- Settlement Progress
    StatsCard(spacing = 16.dp) {
        CardHeader("fa-circle-check", Brand.red, tr("stats_settlement_progress", "Settlement Progress"))
        ProgressBlock(
            tr("stats_debts_repaid", "Debts Repaid"), debtsSettled, allDebts.size, debtsPct, Brand.red, Modifier.pullUp(8.dp),
        )
        ProgressBlock(
            tr("stats_receivables_collected", "Receivables Collected"), receivablesSettled, allReceivables.size, recvPct, GREEN500, Modifier,
        )
    }

    // ---- Top Partners Breakdown
    StatsCard(topPad = 12.dp, spacing = 16.dp, modifier = Modifier.padding(bottom = 32.dp)) {
        CardHeader("fa-people-arrows", Brand.red, tr("stats_top_partners", "Top Partners Breakdown"))
        Column(Modifier.padding(top = 4.dp)) {
            AppText(
                text = tr("stats_top_people_owe", "Top People You Owe"), size = 10f, lineHeight = 15f, weight = FontWeight.Bold,
                letterSpacingEm = 0.05f, color = c.textGray400, uppercase = true, modifier = Modifier.padding(bottom = 4.dp),
            )
            PartnerList(topOwe, Brand.red, tr("stats_no_pending_debts", "No pending debts."))
        }
        Column(Modifier.pullUp(4.dp)) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(dividerColor()))
            Column(Modifier.padding(top = 8.dp)) {
                AppText(
                    text = tr("stats_top_people_owed", "Top People Who Owe You"), size = 10f, lineHeight = 15f, weight = FontWeight.Bold,
                    letterSpacingEm = 0.05f, color = c.textGray400, uppercase = true, modifier = Modifier.padding(bottom = 4.dp),
                )
                PartnerList(topOwed, GREEN600, tr("stats_no_pending_receivables", "No pending receivables."))
            }
        }
    }
}

@Composable
private fun ProgressBlock(label: String, settled: Int, total: Int, pct: Double, fill: Color, modifier: Modifier) {
    val c = AppTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            AppText(text = label, size = 10f, lineHeight = 15f, weight = FontWeight.Bold, color = c.textGray600)
            AppText(
                text = trf(
                    "stats_cleared_fraction", "$settled/$total cleared (${"%.0f".format(pct)}%)",
                    "settled" to localizeDigits(settled.toString()), "total" to localizeDigits(total.toString()),
                    "pct" to localizeDigits("%.0f".format(pct)),
                ),
                size = 10f, lineHeight = 15f, weight = FontWeight.Bold, color = c.textGray600,
            )
        }
        Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(c.bgGray300a60, CircleShape)) {
            Box(
                Modifier.fillMaxHeight().fillMaxWidth(fraction = (pct / 100.0).toFloat().coerceIn(0f, 1f))
                    .clip(CircleShape).background(fill, CircleShape),
            )
        }
    }
}

/** Two rows at a time; a swipe moves a whole page of two (fitListToVisibleCount(list, 2, 2, true)). */
@Composable
private fun PartnerList(entries: List<Pair<String, Double>>, color: Color, emptyText: String) {
    val c = AppTheme.colors
    if (entries.isEmpty()) {
        AppText(
            text = emptyText, size = 10f, lineHeight = 15f, weight = FontWeight.Medium, color = c.textGray400,
            modifier = Modifier.alpha(0.6f),
        )
        return
    }
    val paged = rememberPagedState(visible = 2, gap = 2.dp)
    PagedViewport(state = paged, count = entries.size, modifier = Modifier.fillMaxWidth().pullUp(2.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
            entries.forEachIndexed { i, (person, amt) ->
                Row(
                    Modifier.fillMaxWidth().pagedItem(paged, i).padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppText(
                        text = person, size = 10f, lineHeight = 15f, weight = FontWeight.Bold,
                        color = c.textGray900, singleLineEllipsis = true, modifier = Modifier.weight(1f),
                    )
                    AppText(
                        text = "৳" + fmtMoney(Math.round(amt).toDouble(), 0), size = 10f, lineHeight = 15f,
                        weight = FontWeight.Black, color = color, noWrap = true, modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}
