package com.myapp.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.myapp.app.ui.components.pageScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.myapp.app.ui.popup.HOME_REVEAL_MS
import com.myapp.app.ui.popup.LOCK_HOME_REVEAL_DELAY_MS
import com.myapp.app.ui.components.CHART_GROW_TOTAL_MS
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import com.myapp.app.ui.components.replayEntrance
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myapp.app.data.AppSession
import com.myapp.app.data.AppState
import com.myapp.app.data.AppStore
import com.myapp.app.data.JsDate
import com.myapp.app.ui.AppActions
import com.myapp.app.ui.AppUi
import com.myapp.app.ui.Tab
import com.myapp.app.ui.components.AppRichText
import com.myapp.app.ui.components.AppText
import com.myapp.app.ui.components.CategoryIcon
import com.myapp.app.ui.components.DailySpendChart
import com.myapp.app.ui.components.DailySpendChartData
import com.myapp.app.ui.components.FaIcon
import com.myapp.app.ui.components.FaStyle
import com.myapp.app.ui.components.FitText
import com.myapp.app.ui.components.FundTypeIcon
import com.myapp.app.ui.components.PagedViewport
import com.myapp.app.ui.components.pagedItem
import com.myapp.app.ui.components.rememberPagedState
import com.myapp.app.ui.theme.AppTheme
import com.myapp.app.ui.theme.Brand
import com.myapp.app.util.BN_MONTHS
import com.myapp.app.util.BN_WEEKDAYS
import com.myapp.app.util.BN_WEEKDAYS_SHORT
import com.myapp.app.util.CategoryGroup
import com.myapp.app.util.DateOpts
import com.myapp.app.util.calculateFinance
import com.myapp.app.util.ensureFundMeta
import com.myapp.app.util.fmtDate
import com.myapp.app.util.fmtMoney
import com.myapp.app.util.fundDisplayName
import com.myapp.app.util.fundTint
import com.myapp.app.util.getDebtRemaining
import com.myapp.app.util.groupByCategory
import com.myapp.app.util.isBangla
import com.myapp.app.util.isSpendTx
import com.myapp.app.util.localizeDigits
import com.myapp.app.util.parseCssColor
import com.myapp.app.util.toBnDigits
import com.myapp.app.util.tr
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import com.myapp.app.ui.components.SoftGlass

private const val FUNDS_STAGGER_MAX = 6

/** The funds panel grows for this long; the cards and the three buttons all finish arriving by then (no late pop-in). */
private const val PANEL_OPEN_MS = 560
private val PanelEase = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
private val Ease200 = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

/** JS Math.round for positive numbers. */
private fun jsRound(v: Double): Double = floor(v + 0.5)

// ------------------------------------------------------------------ chart data

private class HomeChart(
    val data: DailySpendChartData,
    val groups: List<CategoryGroup>,
    val total: Double,
)

private fun midnightOf(ms: Long): Long {
    val c = Calendar.getInstance()
    c.timeInMillis = ms
    c.set(Calendar.HOUR_OF_DAY, 0)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

private val BN_WEEKDAY_CHART = listOf("রবি", "সোম", "মঙ্গল", "বুধ", "বৃহ", "শুক্র", "শনি")

private fun buildHomeChart(state: AppState, range: String): HomeChart {
    val nowCal = Calendar.getInstance()
    val todayMs = midnightOf(nowCal.timeInMillis)
    val monthCal = Calendar.getInstance()
    monthCal.set(nowCal.get(Calendar.YEAR), nowCal.get(Calendar.MONTH), 1, 0, 0, 0)
    monthCal.set(Calendar.MILLISECOND, 0)
    val monthStartMs = monthCal.timeInMillis

    val rollingCal = Calendar.getInstance()
    rollingCal.timeInMillis = todayMs
    rollingCal.add(Calendar.DAY_OF_MONTH, -6)
    val rollingStartMs = rollingCal.timeInMillis
    val weekStartMs = if (rollingStartMs < monthStartMs) monthStartMs else rollingStartMs

    val spendTx = state.transactions.filter { it.fund == state.activeFund && isSpendTx(it) }
    val ranged = spendTx.filter { t ->
        val ms = JsDate.parseMillis(t.date)
        if (ms == null) {
            false
        } else if (range == "month") {
            val c = Calendar.getInstance()
            c.timeInMillis = ms
            c.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR) && c.get(Calendar.MONTH) == nowCal.get(Calendar.MONTH)
        } else {
            midnightOf(ms) >= weekStartMs
        }
    }

    val groups = groupByCategory(ranged)
    val totalSpent = ranged.sumOf { it.amount }

    // the days that get a bar
    val dayDates = ArrayList<Long>()
    val isWeek = range != "month"
    if (isWeek) {
        val count = ((todayMs - weekStartMs) / 86400000.0).roundToInt() + 1
        for (i in 0 until count) {
            val c = Calendar.getInstance()
            c.timeInMillis = weekStartMs
            c.add(Calendar.DAY_OF_MONTH, i)
            dayDates.add(c.timeInMillis)
        }
    } else {
        val dim = monthCal.getActualMaximum(Calendar.DAY_OF_MONTH)
        for (i in 0 until dim) {
            val c = Calendar.getInstance()
            c.timeInMillis = monthStartMs
            c.add(Calendar.DAY_OF_MONTH, i)
            dayDates.add(c.timeInMillis)
        }
    }

    // money per calendar day
    val perDay = HashMap<String, Double>()
    for (t in ranged) {
        val ms = JsDate.parseMillis(t.date) ?: continue
        val c = Calendar.getInstance()
        c.timeInMillis = ms
        val key = "${c.get(Calendar.YEAR)}-${c.get(Calendar.MONTH)}-${c.get(Calendar.DAY_OF_MONTH)}"
        perDay[key] = (perDay[key] ?: 0.0) + t.amount
    }

    val totals = ArrayList<Double>()
    val xLabels = ArrayList<String>()
    val titles = ArrayList<String>()
    val bodies = ArrayList<String>()
    for (ms in dayDates) {
        val c = Calendar.getInstance()
        c.timeInMillis = ms
        val key = "${c.get(Calendar.YEAR)}-${c.get(Calendar.MONTH)}-${c.get(Calendar.DAY_OF_MONTH)}"
        val v = perDay[key] ?: 0.0
        totals.add(v)
        val dow = c.get(Calendar.DAY_OF_WEEK) - 1
        xLabels.add(if (isBangla()) BN_WEEKDAY_CHART[dow] else fmtDate(ms, DateOpts(weekday = "short")))
        titles.add(
            if (isBangla()) "${BN_WEEKDAYS[dow]}, ${fmtDate(ms)}"
            else fmtDate(ms, DateOpts(weekday = "short", day = "numeric", month = "short")),
        )
        bodies.add("৳" + fmtMoney(jsRound(v), 0))
    }
    val todayIdx = if (isWeek) -1 else nowCal.get(Calendar.DAY_OF_MONTH) - 1
    return HomeChart(DailySpendChartData(xLabels, totals, titles, bodies, isWeek, todayIdx), groups, totalSpent)
}

// ------------------------------------------------------------------ screen

@Composable
fun HomeScreen() {
    val state = AppStore.observe()
    @Suppress("UNUSED_VARIABLE")
    val lang = AppSession.lang   // redraw when the language changes
    val panelOpen = AppUi.fundsPanelOpen
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
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            HomeHeader(state)
            BalanceCard(state, panelOpen)
            DebtsCard(state)
            // Leaves at once when the funds panel opens (it sits last, so nothing above it moves);
            // comes back with a soft fade after the card has settled.
            AnimatedVisibility(
                visible = !panelOpen,
                enter = fadeIn(tween(220, delayMillis = 140, easing = Ease200)),
                exit = ExitTransition.None,
            ) { CategoryCard(state) }
        }
    }
}

// ------------------------------------------------------------------ header

private fun homeTodayLabel(): String {
    val today = Calendar.getInstance()
    val dow = today.get(Calendar.DAY_OF_WEEK) - 1
    val day = today.get(Calendar.DAY_OF_MONTH)
    val month = today.get(Calendar.MONTH)
    if (isBangla()) {
        return "${BN_WEEKDAYS_SHORT[dow]}, ${toBnDigits(day.toString())} ${BN_MONTHS[month]}"
    }
    val weekdays = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    return "${weekdays[dow]}, $day ${months[month]}"
}

@Composable
private fun HomeHeader(state: AppState) {
    // Same AppState object every time -> Compose would skip this function; reading the revision makes it redraw after a commit.
    AppStore.observe()
    val c = AppTheme.colors
    // name + date pill + bell all share one centre line
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 36.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp), verticalArrangement = Arrangement.Center) {
            AppText(
                text = state.username.ifEmpty { "User" },
                size = 24f,
                lineHeight = 30f,
                weight = FontWeight.Bold,
                letterSpacingEm = -0.025f,
                color = c.textGray900,
                singleLineEllipsis = true,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // date badge
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(c.chipBg, CircleShape)
                    .padding(start = 12.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FaIcon("fa-calendar-alt", 12.dp, Brand.red, style = FaStyle.Regular)
                AppText(
                    text = homeTodayLabel(),
                    size = 11f,
                    lineHeight = 16.5f,
                    weight = FontWeight.Bold,
                    color = c.textGray700,
                    noWrap = true,
                )
            }
            // bell
            val wobble = rememberInfiniteTransition(label = "bellWobble")
            val t by wobble.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart),
                label = "bellWobbleT",
            )
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(c.chipBg, CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { AppActions.openNotificationsPanel() },
                contentAlignment = Alignment.Center,
            ) {
                FaIcon(
                    name = "fa-bell",
                    size = 18.dp,
                    color = Brand.green,
                    modifier = Modifier.graphicsLayer {
                        transformOrigin = TransformOrigin(0.5f, 0f)
                        rotationZ = 10f * sin(2.0 * PI * t).toFloat()
                    },
                )
            }
        }
    }
}

// ------------------------------------------------------------------ balance card

@Composable
private fun HomeCardBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(if (c.isLight) 24.dp else SoftGlass.radius)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (c.isLight) c.homeCardBg else SoftGlass.bg, shape)
            .border(
                if (c.isLight) BorderStroke(c.homeCardBorderWidthDp.dp, c.homeCardBorder) else BorderStroke(1.dp, SoftGlass.border),
                shape,
            ),
    ) {
        content()
    }
}

@Composable
private fun ChevronToggle(open: Boolean, onClick: () -> Unit) {
    val c = AppTheme.colors
    val rotation by animateFloatAsState(
        targetValue = if (open) 180f else 0f,
        animationSpec = tween(200, easing = Ease200),
        label = "fundsChevron",
    )
    val strokeColor = c.textGray600
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(c.bgGray200a80, CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            Modifier
                .size(16.dp)
                .graphicsLayer { rotationZ = rotation },
        ) {
            val s = size.width / 24f
            scale(scaleX = s, scaleY = s, pivot = Offset.Zero) {
                val p = Path()
                p.moveTo(6f, 9f)
                p.lineTo(12f, 15f)
                p.lineTo(18f, 9f)
                drawPath(p, strokeColor, style = Stroke(width = 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

@Composable
private fun BalanceCard(state: AppState, panelOpen: Boolean) {
    // Same AppState object every time -> Compose would skip this function; reading the revision makes it redraw after a commit.
    AppStore.observe()
    val c = AppTheme.colors
    val fin = calculateFinance()
    val meta = ensureFundMeta(state.activeFund)
    val metaColor = parseCssColor(meta.color) ?: Brand.red
    val iconTint = fundTint(meta.color, 0x22)

    HomeCardBox(Modifier.padding(0.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            // (Spacing is given by the children's own padding, so the animated panel below adds no gap when closed.)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppText(
                            text = tr("home_remaining_balance", "Remaining Balance"),
                            size = 10f,
                            weight = FontWeight.Bold,
                            letterSpacingEm = 0.05f,
                            color = c.textGray400,
                            uppercase = true,
                        )
                        Box(
                            modifier = Modifier
                                .padding(start = 6.dp, end = 4.dp)
                                .size(16.dp)
                                .clip(CircleShape)
                                .then(if (iconTint != null) Modifier.background(iconTint, CircleShape) else Modifier),
                            contentAlignment = Alignment.Center,
                        ) {
                            FundTypeIcon(meta.type, meta.icon, 8.dp, metaColor)
                        }
                        AppText(
                            text = fundDisplayName(state.activeFund),
                            size = 10f,
                            weight = FontWeight.ExtraBold,
                            letterSpacingEm = 0.05f,
                            color = metaColor,
                            uppercase = true,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                    FitText(
                        text = "৳" + fmtMoney(fin.activeBalance),
                        maxFontSize = 26f,
                        color = c.textDefault,
                        weight = FontWeight.ExtraBold,
                        letterSpacingEm = -0.025f,
                        lineHeightRatio = 1.2f,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                ChevronToggle(open = panelOpen) { AppUi.toggleFundsPanel() }
            }

            // The funds panel. It is built once (when Home is idle, or on the first tap) and then only SHOWN / HIDDEN:
            // opening no longer has to compose + measure four fund cards in the very frame the animation starts
            // (that long first frame ate the fast start of the motion and made the panel look stuck).
            // Height + fade are driven by one progress value read in the layout / draw phase (no recomposition per frame).
            var mounted by remember { mutableStateOf(false) }
            LaunchedEffect(panelOpen) { if (panelOpen) mounted = true }
            LaunchedEffect(Unit) {
                if (!mounted) {
                    delay(1200)
                    com.myapp.app.ui.components.awaitIntroIdle()
                    delay(300)
                    mounted = true
                }
            }
            val progress = remember { Animatable(if (panelOpen) 1f else 0f) }
            LaunchedEffect(panelOpen) {
                // Opened before the panel was pre-built (first tap right after launch): let it be composed + measured at
                // progress 0 first, so that heavy frame is not counted as animation time and the motion does not stall at the end.
                if (panelOpen && progress.value == 0f) {
                    androidx.compose.runtime.withFrameNanos { }
                    androidx.compose.runtime.withFrameNanos { }
                }
                progress.animateTo(
                    if (panelOpen) 1f else 0f,
                    tween(if (panelOpen) PANEL_OPEN_MS else 280, easing = PanelEase),
                )
            }
            if (mounted || panelOpen) {
                val lineColor = c.borderGray200a60
                Box(
                    Modifier
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            val h = (placeable.height * progress.value).roundToInt()
                            layout(placeable.width, h) { placeable.place(0, 0) }
                        }
                        .clipToBounds()
                        .graphicsLayer { alpha = (progress.value * 1.6f).coerceIn(0f, 1f) },
                ) {
                    Column(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .fillMaxWidth()
                            .drawBehind {
                                drawLine(lineColor, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
                            }
                            .padding(top = 13.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AppText(
                            text = tr("home_tap_fund", "Tap Fund to Switch"),
                            size = 10f,
                            weight = FontWeight.Bold,
                            letterSpacingEm = 0.05f,
                            color = c.textGray400,
                            uppercase = true,
                            modifier = Modifier.replayEntrance(panelOpen, delayMs = 0, rise = 6.dp, durationMs = 300),
                        )
                        FundList(state, open = panelOpen)
                        val borderTop = c.borderGray100
                        // the buttons come in after the fund cards (same motion, one after another)
                        // the buttons start while the last cards are still arriving and finish with the panel height
                        val afterCards = 40 + state.funds.size.coerceAtMost(FUNDS_STAGGER_MAX) * 30
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .replayEntrance(panelOpen, delayMs = afterCards, rise = 10.dp, durationMs = PANEL_OPEN_MS - afterCards)
                                .drawBehind {
                                    drawLine(borderTop, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
                                }
                                .padding(top = 9.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            PanelButton(
                                icon = "fa-right-left",
                                label = tr("home_transfer", "Transfer"),
                                modifier = Modifier.weight(1f),
                            ) { AppActions.openTransferFund() }
                            PanelButton(
                                icon = "fa-plus",
                                label = tr("home_add_new_account", "Add New Fund"),
                                modifier = Modifier.weight(1f),
                            ) { AppActions.openAddFund() }
                        }
                        // funds list expanded: the "spent" line is replaced by the Incoming Overview button
                        // (same look as the Transfer / Add New Fund buttons). It lives INSIDE the panel now, so it
                        // grows and fades in with it instead of popping in at once.
                        PanelButton(
                            icon = "fa-hand-holding-dollar",
                            label = tr("incoming_overview", "Incoming Overview"),
                            modifier = Modifier
                                .fillMaxWidth()
                                .replayEntrance(panelOpen, delayMs = afterCards + 40, rise = 10.dp, durationMs = PANEL_OPEN_MS - afterCards - 40),
                        ) { AppActions.openIncomingOverview() }
                    }
                }
            }

            val spentText = buildAnnotatedString {
                withStyle(SpanStyle(color = Brand.red, fontWeight = FontWeight.Bold, fontSize = 12.sp)) {
                    append("৳" + fmtMoney(fin.spentThisMonth))
                }
                append(" " + tr("home_spent_from_fund", "spent from this fund"))
            }
            // the "spent" line leaves quickly while the panel opens and comes back after it has closed
            AnimatedVisibility(
                visible = !panelOpen,
                enter = fadeIn(tween(220, delayMillis = 160, easing = Ease200)) +
                    expandVertically(tween(280, easing = PanelEase), expandFrom = Alignment.Top),
                exit = fadeOut(tween(100, easing = Ease200)) +
                    shrinkVertically(tween(280, easing = PanelEase), shrinkTowards = Alignment.Top),
            ) {
                AppRichText(
                    text = spentText,
                    size = 12f,
                    lineHeight = 16f,
                    weight = FontWeight.Medium,
                    color = c.textGray500,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun PanelButton(icon: String, label: String, modifier: Modifier, onClick: () -> Unit) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .border(BorderStroke(1.dp, c.borderGray300), shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FaIcon(icon, 10.dp, c.textGray700)
        Spacer(Modifier.width(6.dp))
        AppText(
            text = label,
            size = 11f,
            weight = FontWeight.Bold,
            color = c.textGray700,
        )
    }
}

// ------------------------------------------------------------------ debts card

@Composable
private fun DebtsCard(state: AppState) {
    // Same AppState object every time -> Compose would skip this function; reading the revision makes it redraw after a commit.
    AppStore.observe()
    val c = AppTheme.colors
    var totalOwe = 0.0
    var totalOwed = 0.0
    for (d in state.debts) {
        if (d.settled) continue
        val rem = getDebtRemaining(d)
        if (d.type == "debt") totalOwe += rem else totalOwed += rem
    }

    Box(
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
        ) { AppUi.openStatsDebtsShortcut() },
    ) {
        HomeCardBox {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(c.bgWhite, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            FaIcon("fa-handshake", 12.dp, Brand.red)
                        }
                        AppText(
                            text = tr("home_debts_overview", "Debts & Receivables Overview"),
                            size = 12f,
                            lineHeight = 16f,
                            weight = FontWeight.Bold,
                            letterSpacingEm = 0.05f,
                            color = Brand.red,
                            uppercase = true,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                    FaIcon("fa-chevron-right", 12.dp, c.textGray300)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        AppText(
                            text = tr("home_you_owe", "You Owe"),
                            size = 10f,
                            weight = FontWeight.Bold,
                            letterSpacingEm = 0.05f,
                            color = c.textGray400,
                            uppercase = true,
                        )
                        FitText(
                            text = "৳" + fmtMoney(jsRound(totalOwe), 0),
                            maxFontSize = 16f,
                            color = Brand.red,
                            weight = FontWeight.ExtraBold,
                            lineHeightRatio = 1.4f,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .width(1.dp)
                            .height(36.dp)
                            .background(c.divider),
                    )
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        AppText(
                            text = tr("home_you_are_owed", "You Are Owed"),
                            size = 10f,
                            weight = FontWeight.Bold,
                            letterSpacingEm = 0.05f,
                            color = c.textGray400,
                            uppercase = true,
                            align = TextAlign.End,
                        )
                        FitText(
                            text = "৳" + fmtMoney(jsRound(totalOwed), 0),
                            maxFontSize = 16f,
                            color = Brand.green,
                            weight = FontWeight.ExtraBold,
                            lineHeightRatio = 1.4f,
                            align = TextAlign.End,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ category card

/**
 * Wait after the lock goes away before the chart starts: Home's reveal starts LOCK_HOME_REVEAL_DELAY_MS after the lock
 * begins to dissolve and lasts HOME_REVEAL_MS. The bars begin only when it is completely over, so the two animations
 * never share frames (that overlap was what made the bars stutter).
 */
private const val UNLOCK_CHART_START_MS = LOCK_HOME_REVEAL_DELAY_MS + HOME_REVEAL_MS + 40L

/** No-lock launch: wait this long after the first frame (splash gone, Home faded in) before the bars grow. */
private const val LAUNCH_CHART_START_MS = 260L

/** true once the chart has played its first time in this app process (later opens use the normal timing). */
private object ChartLaunch { var done = false }

private fun anyLockUp(): Boolean = com.myapp.app.ui.popup.LockState.pinVisible || com.myapp.app.ui.popup.LockState.patternVisible ||
    com.myapp.app.ui.popup.LockState.biometricVisible

@Composable
private fun CategoryCard(state: AppState) {
    // Same AppState object every time -> Compose would skip this function; reading the revision makes it redraw after a commit.
    AppStore.observe()
    val c = AppTheme.colors
    val range = AppUi.homeChartRange
    // built ONCE per change of data / range / language / day, not on every recomposition
    val dayKey = Calendar.getInstance().let { it.get(Calendar.YEAR) * 1000 + it.get(Calendar.DAY_OF_YEAR) }
    val chart = remember(AppStore.revision, range, AppSession.lang, state.activeFund, dayKey) { buildHomeChart(state, range) }
    // Order: the lock dissolves, Home fades in after it, and the chart starts only when that reveal is over (frame fades
    // in first, bars grow after): no overlap, so every animation has the frames to itself. With no lock (or when only
    // switching tabs) there is no wait.
    // IMPORTANT: lock / onboarding / start-up state is read in a snapshotFlow (not during composition) and `lockSettled`
    // is only read inside the chart's own lambda, so this whole card is NOT recomposed at the moment the bars start
    // (that recomposition used to eat the first frames of the grow-up and looked like a stuck chart).
    var lockSettled by remember { mutableStateOf(!anyLockUp() && ChartLaunch.done) }
    LaunchedEffect(Unit) {
        var sawLock = anyLockUp()
        snapshotFlow { Triple(anyLockUp(), AppStore.observe().onboardingComplete, com.myapp.app.ui.components.UiSettleState.settled) }
            .collectLatest { (lockUp, onboarded, startSettled) ->
                val busy = com.myapp.app.ui.components.UiSettleState
                if (!onboarded) {
                    lockSettled = false
                    busy.introBusy = false
                } else if (lockUp) {
                    sawLock = true
                    lockSettled = false
                    busy.introBusy = false
                } else {
                    if (!lockSettled) {
                        // from here until the bars have landed, background work (tab warm-up, alarms) is held back
                        busy.introBusy = true
                        if (ChartLaunch.done || sawLock) {
                            delay(UNLOCK_CHART_START_MS)
                        } else {
                            if (!startSettled) return@collectLatest   // start-up still busy: wait (this runs again when it is calm)
                            delay(LAUNCH_CHART_START_MS)
                        }
                        ChartLaunch.done = true
                        lockSettled = true
                        delay(CHART_GROW_TOTAL_MS)
                        busy.introBusy = false
                    } else {
                        ChartLaunch.done = true
                    }
                }
            }
    }
    val shape = RoundedCornerShape(if (c.isLight) 28.dp else SoftGlass.radius)
    val borderColor = if (c.isLight) c.categoryCardBorder else SoftGlass.border

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (c.isLight) c.categoryCardBg else SoftGlass.bg, shape)
            .then(if (borderColor != null) Modifier.border(BorderStroke(1.dp, borderColor), shape) else Modifier)
            .padding(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(c.categoryIconBoxBg, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    FaIcon("fa-chart-simple", 16.dp, Brand.red)
                }
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    AppText(
                        text = tr("home_category_breakdown", "Category Breakdown"),
                        size = 12f,
                        lineHeight = 17f,
                        weight = FontWeight.Bold,
                        letterSpacingEm = -0.025f,
                        color = c.categoryCardText,
                    )
                    AppText(
                        text = fundDisplayName(state.activeFund),
                        size = 10f,
                        color = c.categoryTextMuted,
                    )
                }
            }
            // week / month switch
            Row(
                modifier = Modifier
                    .offset(x = 8.dp)
                    .clip(CircleShape)
                    .background(c.categoryPillBg, CircleShape)
                    .padding(4.dp),
            ) {
                RangeButton(tr("home_week", "Week"), range == "week") { AppUi.setHomeChartRange("week") }
                RangeButton(tr("home_month", "Month"), range == "month") { AppUi.setHomeChartRange("month") }
            }
        }

        DailySpendChart(
            data = chart.data,
            isDark = !c.isLight,
            // the bars grow up again every time the Home tab is opened (and not while a lock screen is up)
            play = { AppUi.tab == Tab.Home && lockSettled },
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp),
        )
        Spacer(Modifier.height(16.dp))

        val groups = chart.groups
        if (groups.isEmpty()) {
            AppText(
                text = tr("home_no_spending_range", "No spending in this range yet."),
                size = 12f,
                lineHeight = 16f,
                color = c.categoryTextEmpty,
                align = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
        } else {
            val paged = rememberPagedState(visible = 2, gap = 12.dp)
            PagedViewport(state = paged, count = groups.size, modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    for ((i, g) in groups.withIndex()) {
                        val pct = if (chart.total > 0) jsRound(g.total / chart.total * 100).toInt() else 0
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .pagedItem(paged, i)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { AppActions.openHomeCategoryDetails() },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                modifier = Modifier.weight(1f, fill = false),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(c.categoryPillBg, RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CategoryIcon(g.label, 12.dp, c.categoryIconTint)
                                }
                                AppText(
                                    text = g.label,
                                    size = 9f,
                                    weight = FontWeight.Medium,
                                    color = c.categoryTextName,
                                    singleLineEllipsis = true,
                                    modifier = Modifier.widthIn(max = 110.dp),
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AppText(
                                    text = localizeDigits("$pct%"),
                                    size = 8f,
                                    lineHeight = 12f,
                                    weight = FontWeight.Bold,
                                    color = c.categoryTextPct,
                                )
                                AppText(
                                    text = "৳" + fmtMoney(jsRound(g.total), 0),
                                    size = 9f,
                                    weight = FontWeight.Black,
                                    color = c.categoryTextAmount,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RangeButton(label: String, active: Boolean, onClick: () -> Unit) {
    val c = AppTheme.colors
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .then(if (active) Modifier.background(Brand.red, CircleShape) else Modifier)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        AppText(
            text = label,
            size = 10f,
            weight = FontWeight.Bold,
            color = if (active) Color.White else c.categoryTextMuted,
            uppercase = true,
        )
    }
}
