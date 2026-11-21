package com.myapp.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myapp.app.ui.theme.AppFont
import com.myapp.app.ui.theme.Brand
import com.myapp.app.util.localizeDigits
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Data for the Home "Category Breakdown" bar chart: one bar per day.
 *  Week  = the days from the start of the 7-day window up to today (never before the 1st of the month)
 *  Month = every day of the current month
 */
class DailySpendChartData(
    val xLabels: List<String>,      // week: weekday names; month: unused for drawing the numbers
    val totals: List<Double>,       // money spent on each day
    val tipTitles: List<String>,    // tooltip title per day, e.g. "Mon, Nov 16"
    val tipBodies: List<String>,    // tooltip text per day, e.g. "৳1,200"
    val isWeek: Boolean,
    val todayIndex: Int = -1,       // month: 0-based index of today (the slide that holds today opens first)
)

private const val MONTH_PAGES = 3
private const val DAYS_PER_PAGE = 10

/** Month slides: 1-10, 11-20, 21-end (28/29/30/31 days: last slide shows 8/9/10/11 days). */
private fun pageStart(page: Int): Int = page * DAYS_PER_PAGE
private fun pageEnd(page: Int, dim: Int): Int = if (page >= MONTH_PAGES - 1) dim else (page + 1) * DAYS_PER_PAGE

private class YScale(val max: Double, val step: Double)

/** Neat 1/2/5 x 10^n step so labels read 10-20-30 instead of 50-100. */
private fun computeYScale(totals: List<Double>): YScale {
    val totalSpent = totals.sum()
    val yMaxData = if (totalSpent <= 0) 1000.0 else max(1.0, totals.maxOrNull() ?: 1.0)
    var yStep = 1.0
    val powers = doubleArrayOf(1.0, 10.0, 100.0, 1000.0, 10000.0, 100000.0, 1000000.0)
    outer@ for (p in powers) {
        for (m in doubleArrayOf(1.0, 2.0, 5.0)) {
            val s = m * p
            if (yMaxData / s <= 6) {
                yStep = s
                break@outer
            }
        }
    }
    val yNiceMax = ceil(yMaxData / yStep) * yStep
    return YScale(yNiceMax, yStep)
}

private fun isLabeledMonthDay(idx: Int, dim: Int): Boolean {
    val day = idx + 1
    if (day == dim) return true            // last day of the month always shown
    if (dim - day <= 1) return false       // the day right before it becomes a dot (no collision)
    return day == 1 || day % 5 == 0
}

private fun textStyle(sizeSp: Float, bold: Boolean, color: Color): TextStyle = TextStyle(
    fontFamily = AppFont,
    fontSize = sizeSp.sp,
    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
    color = color,
    textAlign = TextAlign.Start,
    lineHeight = (sizeSp * 1.2f).sp,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

private fun measureW(m: TextMeasurer, text: String, style: TextStyle): Float =
    m.measure(text = text, style = style, softWrap = false, maxLines = 1).size.width.toFloat()

/** Soft ease-out (fast start, very long gentle landing) used by every bar. */
private val BarEase = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

/** Whole grow-up of one slide: the bars start one after another and all land together. */
private const val GROW_MS = 950

/** From the moment `play` turns true until the last bar has landed (start delay + grow-up + a small margin). */
const val CHART_GROW_TOTAL_MS = 20L + GROW_MS + 150L

/**
 * The daily spending bar chart: y-axis numbers, dashed grid, tap a day for a tooltip.
 * Month = three 10-day slides (1-10, 11-20, 21-end) in the SAME swipe pager as the Stats
 * "Top Spending Category" slider (HorizontalPager, 24dp gap, snap). Every time a slide comes into view
 * its bars grow up again, one after another, exactly like the first-open animation.
 */
@Composable
fun DailySpendChart(
    data: DailySpendChartData,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    /**
     * true while the chart is on screen (Home tab open, no lock screen): each time it turns true the bars grow up again.
     * A lambda on purpose: it is read only inside the animation effects, so flipping it does not recompose the chart.
     */
    play: () -> Boolean = { true },
) {
    // a fresh pager when switching Week <-> Month
    key(data.isWeek) { DailySpendChartBody(data, isDark, modifier, play) }
}

@Composable
private fun DailySpendChartBody(
    data: DailySpendChartData,
    isDark: Boolean,
    modifier: Modifier,
    play: () -> Boolean,
) {
    val n0 = data.totals.size
    val isWeek = data.isWeek
    val pageCount = if (isWeek) 1 else MONTH_PAGES
    val contentKey = remember(data) { data.totals to data.isWeek }   // compared by content, not by object
    // bigger cache: the default (8) is far smaller than the ~40 labels drawn every frame, so every frame re-measured text and the bars stuttered
    val measurer = rememberTextMeasurer(cacheSize = 96)
    val density = LocalDensity.current
    var selected by remember(data) { mutableStateOf<Int?>(null) }   // GLOBAL day index (0-based)

    // the slide that holds today opens first
    val pagerState = rememberPagerState(
        initialPage = if (isWeek) 0 else ((Calendar.getInstance().get(Calendar.DAY_OF_MONTH) - 1) / DAYS_PER_PAGE).coerceIn(0, MONTH_PAGES - 1),
        pageCount = { pageCount },
    )
    LaunchedEffect(pagerState.currentPage) { selected = null }

    // grid + y labels fade in once; they stay still while the slides move
    val frame = remember(contentKey) { Animatable(0f) }
    LaunchedEffect(frame) {
        snapshotFlow { play() }.collectLatest { on ->
            frame.snapTo(0f)
            if (on) {
                delay(20)
                frame.animateTo(1f, tween(durationMillis = 360, easing = FastOutSlowInEasing))
            }
        }
    }

    val gridColor = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.10f)
    val tickColor = if (isDark) Color(0xFFA1A1AA) else Color(0xFF52525B)
    val yScale = remember(data) { computeYScale(data.totals) }   // one scale for the whole month, so slides compare

    val dp = density.density
    val padTop = 8f * dp
    val xAxisH = 16f * dp
    val yPad = 2f * dp

    val ticks = remember(yScale) {
        val list = ArrayList<Double>()
        var v = 0.0
        while (v <= yScale.max + 1e-9) {
            list.add(v)
            v += yScale.step
        }
        list
    }
    val yLabelStyle = textStyle(9f, false, tickColor)
    val yLabels = ticks.map { localizeDigits(it.roundToInt().toString()) }
    var widest = 0f
    for (l in yLabels) if (l.isNotEmpty()) widest = max(widest, measureW(measurer, l, yLabelStyle))
    val yAxisW = widest + yPad
    val yAxisDp = with(density) { yAxisW.toDp() }

    Box(modifier = modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val a = frame.value
            val w = size.width
            val bottom = size.height - xAxisH
            val plotH = bottom - padTop
            if (n0 == 0 || plotH <= 0f) return@Canvas
            val dash = PathEffect.dashPathEffect(floatArrayOf(3f * dp, 4f * dp), 0f)
            for ((i, tv) in ticks.withIndex()) {
                val y = bottom - (tv / yScale.max).toFloat() * plotH
                drawLine(gridColor, Offset(yAxisW, y), Offset(w, y), strokeWidth = 1f * dp, pathEffect = dash, alpha = a)
                val label = yLabels[i]
                if (label.isNotEmpty()) {
                    val layout = measurer.measure(label, yLabelStyle, softWrap = false, maxLines = 1)
                    drawText(layout, topLeft = Offset(yAxisW - yPad - layout.size.width, y - layout.size.height / 2f), alpha = a)
                }
            }
        }

        HorizontalPager(
            state = pagerState,
            pageSpacing = 24.dp,
            userScrollEnabled = !isWeek,
            modifier = Modifier.fillMaxSize().padding(start = yAxisDp),
        ) { page ->
            DayBarsPage(
                page = page,
                pagerState = pagerState,
                data = data,
                play = play,
                contentKey = contentKey,
                yScale = yScale,
                tickColor = tickColor,
                isDark = isDark,
                measurer = measurer,
                selected = selected,
                onSelect = { selected = it },
                padTop = padTop,
                xAxisH = xAxisH,
                dp = dp,
            )
        }
    }
}

/** One slide (week = the whole week, month = 10 days): the bars, the date labels and the tooltip. */
@Composable
private fun DayBarsPage(
    page: Int,
    pagerState: PagerState,
    data: DailySpendChartData,
    play: () -> Boolean,
    contentKey: Any,
    yScale: YScale,
    tickColor: Color,
    isDark: Boolean,
    measurer: TextMeasurer,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    padTop: Float,
    xAxisH: Float,
    dp: Float,
) {
    val n0 = data.totals.size
    val isWeek = data.isWeek
    val off = if (isWeek) 0 else pageStart(page)
    val pn = if (isWeek) n0 else pageEnd(page, n0) - off

    // Each slide has its own grow-up animation. While the slide is out of sight its bars sit at 0; the moment it
    // starts sliding into view they grow up one after another (same look as the very first open).
    val grow = remember(contentKey) { Animatable(0f) }
    val barPath = remember { Path() }
    // The date / weekday labels never change while a slide is on screen, so they are measured ONCE here instead of
    // 10-31 times in every frame of the grow-up (that measuring was a big part of the stutter).
    val labelLayouts = remember(data, page, tickColor, measurer) {
        // today (weekday name in Week, date number in Month): same size as the others, bold red
        val todayIdx = if (isWeek) n0 - 1 else Calendar.getInstance().get(Calendar.DAY_OF_MONTH) - 1
        val st = textStyle(if (isWeek) 10f else 9f, true, tickColor)
        val stToday = textStyle(if (isWeek) 10f else 9f, true, Brand.red).copy(fontWeight = FontWeight.Black)
        List(pn) { i ->
            val label = if (isWeek) (data.xLabels.getOrNull(off + i) ?: "") else localizeDigits((off + i + 1).toString())
            measurer.measure(label, if (off + i == todayIdx) stToday else st, softWrap = false, maxLines = 1)
        }
    }
    LaunchedEffect(grow) {
        snapshotFlow {
            abs(pagerState.currentPage + pagerState.currentPageOffsetFraction - page) < 0.995f && play()
        }.collectLatest { near ->
            if (near) {
                delay(20)
                grow.animateTo(1f, tween(durationMillis = GROW_MS, easing = LinearEasing))
            } else {
                grow.snapTo(0f)
            }
        }
    }

    Canvas(
        modifier = Modifier.fillMaxSize().pointerInput(data) {
            val w = size.width.toFloat()
            val h = size.height.toFloat()
            val bottom = h - xAxisH
            fun pick(p: Offset) {
                if (pn <= 0 || p.x < 0f || p.x > w || p.y < padTop - 2f || p.y > bottom + 2f) {
                    onSelect(null)
                    return
                }
                val idx = (p.x / (w / pn)).toInt().coerceIn(0, pn - 1)
                onSelect(if (data.totals[off + idx] > 0) off + idx else null)
            }
            if (isWeek) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    pick(down.position)
                    do {
                        val event = awaitPointerEvent()
                        event.changes.forEach { if (it.pressed && !it.isConsumed) pick(it.position) }
                    } while (event.changes.any { it.pressed })
                }
            } else {
                detectTapGestures { p -> pick(p) }   // month: tap = tooltip, swipe is the pager's
            }
        },
    ) {
        val growT = grow.value
        val frameA = FastOutSlowInEasing.transform((growT / 0.30f).coerceIn(0f, 1f))
        val w = size.width
        val h = size.height
        val bottom = h - xAxisH
        val plotH = bottom - padTop
        if (pn <= 0 || plotH <= 0f || w <= 0f) return@Canvas
        val slot = w / pn
        val minBar = 4f * dp

        val barPct = if (isWeek) 0.6f else 0.55f
        val maxThick = (if (isWeek) 30f else 18f) * dp
        val radius = (if (isWeek) 8f else 5f) * dp
        val barW = min(slot * barPct, maxThick)
        for (i in 0 until pn) {
            val v = data.totals[off + i]
            if (v <= 0) continue
            val cx = slot * (i + 0.5f)
            val fullH = max((v / yScale.max).toFloat() * plotH, minBar)
            // each bar starts a little after the previous one and eases out (fast start, soft landing)
            val startAt = if (pn > 1) 0.34f * i / (pn - 1) else 0f
            val local = BarEase.transform(((growT - startAt) / 0.66f).coerceIn(0f, 1f))
            val barH = fullH * local
            if (barH < 0.5f) continue
            val r = min(radius, min(barW / 2f, barH / 2f))
            val path = barPath
            path.rewind()
            path.addRoundRect(
                RoundRect(
                    left = cx - barW / 2f,
                    top = bottom - barH,
                    right = cx + barW / 2f,
                    bottom = bottom,
                    topLeftCornerRadius = CornerRadius(r, r),
                    topRightCornerRadius = CornerRadius(r, r),
                    bottomRightCornerRadius = CornerRadius.Zero,
                    bottomLeftCornerRadius = CornerRadius.Zero,
                ),
            )
            drawPath(path, Brand.red, alpha = (local * 6f).coerceIn(0f, 1f))
        }

        // x labels (measured once, above): week = weekday names, month = every date of the slide.
        val centerY = bottom + 10f * dp
        for (i in 0 until pn) {
            val layout = labelLayouts.getOrNull(i) ?: continue
            drawText(layout, topLeft = Offset(slot * (i + 0.5f) - layout.size.width / 2f, centerY - layout.size.height / 2f), alpha = frameA)
        }

        // tooltip for the tapped day (only on the slide that holds it)
        val sel = selected
        if (sel != null && sel >= off && sel < off + pn && data.totals[sel] > 0) {
            drawTooltip(
                measurer = measurer,
                title = data.tipTitles.getOrNull(sel) ?: "",
                body = data.tipBodies.getOrNull(sel) ?: "",
                posX = slot * ((sel - off) + 0.5f),
                posY = (bottom - max((data.totals[sel] / yScale.max).toFloat() * plotH, minBar) + bottom) / 2f,
                chartLeft = 0f,
                chartRight = w,
                canvasW = w,
                canvasH = h,
                dp = dp,
            )
        }
    }
}

private fun DrawScope.drawTooltip(
    measurer: TextMeasurer,
    title: String,
    body: String,
    posX: Float,
    posY: Float,
    chartLeft: Float,
    chartRight: Float,
    canvasW: Float,
    canvasH: Float,
    dp: Float,
) {
    val titleStyle = textStyle(8f, true, Color.White)
    val bodyStyle = textStyle(8f, false, Color.White)
    val tl = measurer.measure(title, titleStyle, softWrap = false, maxLines = 1)
    val bl = measurer.measure(body, bodyStyle, softWrap = false, maxLines = 1)
    val padX = 5f * dp
    val padY = 3f * dp
    val caret = 3f * dp
    val caretPad = 4f * dp
    val boxW = max(tl.size.width, bl.size.width) + 2 * padX
    val boxH = tl.size.height + 1f * dp + bl.size.height + 2 * padY
    val toRight = posX <= (chartLeft + chartRight) / 2f      // box sits right of the bar, else left of it
    var boxLeft = if (toRight) posX + caret + caretPad else posX - caret - caretPad - boxW
    boxLeft = boxLeft.coerceIn(0f, max(0f, canvasW - boxW))
    val boxTop = (posY - boxH / 2f).coerceIn(0f, max(0f, canvasH - boxH))
    val bg = Color.Black.copy(alpha = 0.8f)

    val path = Path()
    path.addRoundRect(RoundRect(boxLeft, boxTop, boxLeft + boxW, boxTop + boxH, CornerRadius(5f * dp, 5f * dp)))
    drawPath(path, bg)

    // caret pointing at the bar
    val cy = (posY).coerceIn(boxTop + caret, boxTop + boxH - caret)
    val tri = Path()
    if (toRight) {
        tri.moveTo(boxLeft, cy - caret)
        tri.lineTo(boxLeft, cy + caret)
        tri.lineTo(boxLeft - caret, cy)
    } else {
        tri.moveTo(boxLeft + boxW, cy - caret)
        tri.lineTo(boxLeft + boxW, cy + caret)
        tri.lineTo(boxLeft + boxW + caret, cy)
    }
    tri.close()
    drawPath(tri, bg)

    drawText(tl, topLeft = Offset(boxLeft + padX, boxTop + padY))
    drawText(bl, topLeft = Offset(boxLeft + padX, boxTop + padY + tl.size.height + 1f * dp))
}
