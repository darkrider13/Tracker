package com.myapp.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
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
)

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

/** The daily spending bar chart (replaces Chart.js): y-axis numbers, dashed grid, tap a day for a tooltip. */
@Composable
fun DailySpendChart(
    data: DailySpendChartData,
    isDark: Boolean,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    var selected by remember(data) { mutableStateOf<Int?>(null) }

    val gridColor = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.10f)
    val tickColor = if (isDark) Color(0xFFA1A1AA) else Color(0xFF52525B)
    val yScale = remember(data) { computeYScale(data.totals) }

    val dp = density.density
    val padTop = 8f * dp
    val xAxisH = 16f * dp
    val yPad = 2f * dp
    val n = data.totals.size

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

    Canvas(
        modifier = modifier.pointerInput(data) {
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
                    selected = if (data.totals[idx] > 0) idx else null
                }
                pick(down.position)
                do {
                    val event = awaitPointerEvent()
                    event.changes.forEach { if (it.pressed && !it.isConsumed) pick(it.position) }
                } while (event.changes.any { it.pressed })
            }
        },
    ) {
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
            drawLine(gridColor, Offset(left, y), Offset(w, y), strokeWidth = 1f * dp, pathEffect = dash)
            val label = yLabels[i]
            if (label.isNotEmpty()) {
                val layout = measurer.measure(label, yLabelStyle, softWrap = false, maxLines = 1)
                drawText(
                    layout,
                    topLeft = Offset(yAxisW - yPad - layout.size.width, y - layout.size.height / 2f),
                )
            }
        }

        // ---- bars ----
        val slot = areaW / n
        val isWeek = data.isWeek
        val barPct = if (isWeek) 0.6f else (if (n >= 31) 0.68f else 0.75f)
        val maxThick = (if (isWeek) 30f else 12f) * dp
        val radius = (if (isWeek) 8f else 4f) * dp
        val barW = min(slot * barPct, maxThick)
        val minBar = 4f * dp
        for (i in 0 until n) {
            val v = data.totals[i]
            if (v <= 0) continue
            val cx = left + slot * (i + 0.5f)
            val barH = max((v / yScale.max).toFloat() * plotH, minBar)
            val r = min(radius, min(barW / 2f, barH / 2f))
            val path = Path()
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
            drawPath(path, Brand.red)
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
                drawText(layout, topLeft = Offset(cx - layout.size.width / 2f, centerY - layout.size.height / 2f))
            }
        } else {
            val slotDp = slot / dp
            val fs = max(7f, min(9f, slotDp * 1.02f))
            val dotR = max(0.8f, min(1.1f, slotDp * 0.12f)) * dp
            val st = textStyle(fs, true, tickColor)
            for (i in 0 until n) {
                val cx = left + slot * (i + 0.5f)
                if (isLabeledMonthDay(i, n)) {
                    val label = localizeDigits((i + 1).toString())
                    val layout = measurer.measure(label, st, softWrap = false, maxLines = 1)
                    drawText(layout, topLeft = Offset(cx - layout.size.width / 2f, centerY - layout.size.height / 2f))
                } else {
                    drawCircle(tickColor, radius = dotR, center = Offset(cx, centerY))
                }
            }
        }

        // ---- tooltip for the tapped day ----
        val sel = selected
        if (sel != null && sel in 0 until n && data.totals[sel] > 0) {
            drawTooltip(
                measurer = measurer,
                title = data.tipTitles.getOrNull(sel) ?: "",
                body = data.tipBodies.getOrNull(sel) ?: "",
                posX = left + slot * (sel + 0.5f),
                posY = (bottom - max((data.totals[sel] / yScale.max).toFloat() * plotH, minBar) + bottom) / 2f,
                chartLeft = left,
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
