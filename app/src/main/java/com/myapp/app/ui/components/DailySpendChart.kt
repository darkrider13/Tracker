package com.myapp.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.myapp.app.ui.theme.AppFont
import com.myapp.app.ui.theme.Brand
import com.myapp.app.util.localizeDigits
import kotlinx.coroutines.delay
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

/** Soft ease-out (fast start, long gentle landing) used by every bar. */
private val BarEase = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

/** The daily spending bar chart (replaces Chart.js): y-axis numbers, dashed grid, tap a day for a tooltip. */
@Composable
fun DailySpendChart(
    data: DailySpendChartData,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    /** true while the chart is on screen (Home tab open, no lock screen): each time it turns true the bars grow up again */
    play: Boolean = true,
) {
    // Grow-up animation: the bars rise from the baseline one after another (left to right).
    // It replays every time [play] turns true (coming back to Home) and when the week/month data changes.
    // A NEW Animatable (starting at 0) is created in the SAME composition as the new data, so the new
    // week/month bars are never drawn once at full height before the animation restarts (that flash was
    // what made the week -> month switch look like a clash).
    val n0 = data.totals.size
    var page by remember(data.isWeek, n0) {
        mutableStateOf(if (data.isWeek || data.todayIndex < 0) 0 else (data.todayIndex / DAYS_PER_PAGE).coerceIn(0, MONTH_PAGES - 1))
    }
    val contentKey = remember(data, page) { Triple(data.totals, data.isWeek, page) }   // compared by content, not by object
    val grow = remember(play, contentKey) { Animatable(0f) }
    LaunchedEffect(grow) {
        if (play) {
            delay(20)
            grow.animateTo(1f, tween(durationMillis = 650, easing = LinearEasing))
        }
    }
    // [grow] is read ONLY inside the Canvas draw block below, so every animation frame just redraws the
    // canvas - nothing is re-composed or re-measured per frame (that is what keeps it smooth).
    val barPath = remember { Path() }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    var selected by remember(data, page) { mutableStateOf<Int?>(null) }   // GLOBAL day index (0-based)

    val gridColor = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.10f)
    val tickColor = if (isDark) Color(0xFFA1A1AA) else Color(0xFF52525B)
    val yScale = remember(data) { computeYScale(data.totals) }

    val dp = density.density
    val padTop = 8f * dp
    val xAxisH = 16f * dp
    val yPad = 2f * dp
    // visible slice: week = everything, month = one 10-day slide
    val off = if (data.isWeek) 0 else pageStart(page)
    val n = if (data.isWeek) n0 else (pageEnd(page, n0) - off)

    // y tick values and their text
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
    val yLabels = ticks.map { localizeDigits(it.roundToInt().toString()) }   // starts at 0, then 10, 20, ...
    var widest = 0f
    for (l in yLabels) if (l.isNotEmpty()) widest = max(widest, measureW(measurer, l, yLabelStyle))
    val yAxisW = widest + yPad

    Column(modifier = modifier) {
    Canvas(
        modifier = Modifier.fillMaxWidth().weight(1f).pointerInput(data, page) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val w = size.width.toFloat()
                val h = size.height.toFloat()
                val left = yAxisW
                val top = padTop
                val bottom = h - xAxisH
                val slot = if (n > 0) (w - left) / n else 1f
                fun pick(p: Offset) {
                    if (n == 0 || p.x < left || p.x > w || p.y < top - 2f || p.y > bottom + 2f) {
                        selected = null
                        return
                    }
                    val idx = ((p.x - left) / slot).toInt().coerceIn(0, n - 1)
                    selected = if (data.totals[off + idx] > 0) off + idx else null
                }
                pick(down.position)
                if (data.isWeek) {
                    do {
                        val event = awaitPointerEvent()
                        event.changes.forEach { if (it.pressed && !it.isConsumed) pick(it.position) }
                    } while (event.changes.any { it.pressed })
                } else {
                    // month: tap = tooltip, swipe left/right = next/previous 10-day slide
                    var dx = 0f
                    val threshold = 36.dp.toPx()
                    do {
                        val event = awaitPointerEvent()
                        val ch = event.changes.firstOrNull() ?: break
                        dx += ch.positionChange().x
                        if (abs(dx) > threshold) {
                            ch.consume()
                            selected = null
                            page = (page + (if (dx < 0) 1 else -1)).coerceIn(0, MONTH_PAGES - 1)
                            break
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
        },
    ) {
        val growT = grow.value
        // The frame (grid + axis labels) eases in during the first part while the first bars already start
        // to rise, so the old chart is gone at once and the new one appears calmly instead of overlapping.
        val frameA = FastOutSlowInEasing.transform((growT / 0.25f).coerceIn(0f, 1f))
        val w = size.width
        val h = size.height
        val left = yAxisW
        val top = padTop
        val bottom = h - xAxisH
        val plotH = bottom - top
        val areaW = w - left
        if (n == 0 || plotH <= 0f || areaW <= 0f) return@Canvas

        fun yOf(v: Double): Float = bottom - (v / yScale.max).toFloat() * plotH

        // ---- grid + y labels ----
        val dash = PathEffect.dashPathEffect(floatArrayOf(3f * dp, 4f * dp), 0f)
        for ((i, tv) in ticks.withIndex()) {
            val y = yOf(tv)
            drawLine(gridColor, Offset(left, y), Offset(w, y), strokeWidth = 1f * dp, pathEffect = dash, alpha = frameA)
            val label = yLabels[i]
            if (label.isNotEmpty()) {
                val layout = measurer.measure(label, yLabelStyle, softWrap = false, maxLines = 1)
                drawText(
                    layout,
                    topLeft = Offset(yAxisW - yPad - layout.size.width, y - layout.size.height / 2f),
                    alpha = frameA,
                )
            }
        }

        // ---- bars ----
        val slot = areaW / n
        val isWeek = data.isWeek
        val barPct = if (isWeek) 0.6f else 0.55f
        val maxThick = (if (isWeek) 30f else 18f) * dp
        val radius = (if (isWeek) 8f else 5f) * dp
        val barW = min(slot * barPct, maxThick)
        val minBar = 4f * dp
        for (i in 0 until n) {
            val v = data.totals[off + i]
            if (v <= 0) continue
            val cx = left + slot * (i + 0.5f)
            val fullH = max((v / yScale.max).toFloat() * plotH, minBar)
            // each bar starts a little after the previous one and eases out (fast start, soft landing)
            val startAt = if (n > 1) 0.30f * i / (n - 1) else 0f
            val local = BarEase.transform(((growT - startAt) / 0.70f).coerceIn(0f, 1f))
            val barH = fullH * local
            if (barH < 0.5f) continue
            val r = min(radius, min(barW / 2f, barH / 2f))
            val path = barPath
            path.rewind()   // one Path reused for every bar and frame - no allocation while animating
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
            // a quick fade-in on top of the rise: bars never pop in, they emerge
            drawPath(path, Brand.red, alpha = (local * 4f).coerceIn(0f, 1f))
        }

        // ---- x labels (week: weekday names, month: some dates + dots) ----
        val centerY = bottom + 10f * dp
        if (isWeek) {
            val st = textStyle(10f, true, tickColor)
            for (i in 0 until n) {
                val label = data.xLabels.getOrNull(i) ?: ""
                if (label.isEmpty()) continue
                val layout = measurer.measure(label, st, softWrap = false, maxLines = 1)
                val cx = left + slot * (i + 0.5f)
                drawText(layout, topLeft = Offset(cx - layout.size.width / 2f, centerY - layout.size.height / 2f), alpha = frameA)
            }
        } else {
            val st = textStyle(9f, true, tickColor)
            for (i in 0 until n) {
                val cx = left + slot * (i + 0.5f)
                val label = localizeDigits((off + i + 1).toString())   // every day of the slide is labelled
                val layout = measurer.measure(label, st, softWrap = false, maxLines = 1)
                drawText(layout, topLeft = Offset(cx - layout.size.width / 2f, centerY - layout.size.height / 2f), alpha = frameA)
            }
        }

        // ---- tooltip for the tapped day ----
        val sel = selected
        if (sel != null && sel >= off && sel < off + n && data.totals[sel] > 0) {
            drawTooltip(
                measurer = measurer,
                title = data.tipTitles.getOrNull(sel) ?: "",
                body = data.tipBodies.getOrNull(sel) ?: "",
                posX = left + slot * ((sel - off) + 0.5f),
                posY = (bottom - max((data.totals[sel] / yScale.max).toFloat() * plotH, minBar) + bottom) / 2f,
                chartLeft = left,
                chartRight = w,
                canvasW = w,
                canvasH = h,
                dp = dp,
            )
        }
    }

    // ---- month slide switcher: 1-10 | 11-20 | 21-end (tap or swipe) ----
    if (!data.isWeek) {
        Row(
            modifier = Modifier.fillMaxWidth().height(22.dp).padding(top = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (pg in 0 until MONTH_PAGES) {
                val active = pg == page
                val label = localizeDigits("${pageStart(pg) + 1}-${pageEnd(pg, n0)}")
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (active) Brand.red.copy(alpha = 0.18f) else Color.Transparent)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { page = pg }
                        .padding(horizontal = 10.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.foundation.text.BasicText(
                        text = label,
                        style = textStyle(9f, active, if (active) Brand.red else tickColor),
                    )
                }
            }
        }
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
