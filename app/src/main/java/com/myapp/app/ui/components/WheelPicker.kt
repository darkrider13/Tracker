package com.myapp.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.myapp.app.ui.theme.AppTheme
import com.myapp.app.ui.theme.Brand
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/** One row of a wheel. [hasTx] = a date that has entries (drawn in red, like data-has-tx). */
class WheelItem(val value: String, val label: String, val hasTx: Boolean = false)

private const val ROW_H = 44f                 // TIME_WHEEL_ROW_H
private const val MAX_VELOCITY = 0.75f        // UDP_MAX_VELOCITY, px per ms
private const val FRICTION = 0.0005f          // UDP_FRICTION, px per ms^2

/** Number of back-to-back copies of a looped list (timeWheelLoopCopies). */
private fun loopCopies(n: Int): Int {
    val perCopy = n * ROW_H
    val each = max(1, ceil(900f / perCopy).toInt())
    return 1 + 2 * each
}

private val EaseOutQuint = Easing { t -> 1f - (1f - t).pow(5) }

/** The pulsing 4-6 dp red dot (Tailwind animate-pulse: opacity 1 -> 0.5 -> 1 every 2 s). */
@Composable
fun PulseDot(size: Dp, modifier: Modifier = Modifier, color: Color = Brand.red) {
    val t = rememberInfiniteTransition(label = "pulse")
    val a by t.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 2000
                1f at 0
                0.5f at 1000
                1f at 2000
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulseAlpha",
    )
    Box(modifier.size(size).alpha(a).background(color, CircleShape))
}

/**
 * A 3-row wheel (132 dp tall) like .time-wheel + initUdpWheel:
 * own drag handling, capped fling speed, fixed friction, eased settle on a row (380 ms),
 * tap on a row glides to it (360 ms), optional looping, top/bottom fade mask.
 *
 * [resetKey] rebuilds the wheel (items changed, e.g. the day wheel when the month changes).
 * [onSettle] gets the VALUE of the row the wheel rests on (also called for a tapped row).
 */
@Composable
fun WheelPicker(
    items: List<WheelItem>,
    initialValue: String,
    loop: Boolean,
    width: Dp,
    resetKey: Any,
    modifier: Modifier = Modifier,
    onSettle: (String) -> Unit,
) {
    val c = AppTheme.colors
    val n = items.size
    val copies = if (loop) loopCopies(n) else 1
    val total = n * copies
    val density = androidx.compose.ui.platform.LocalDensity.current
    val rowPx = with(density) { ROW_H.dp.toPx() }
    val scope = rememberCoroutineScope()

    val baseIdx = max(0, items.indexOfFirst { it.value == initialValue })
    val startIdx = if (loop) (copies / 2) * n + baseIdx else baseIdx

    // scroll position in px (the old scrollTop)
    val scroll = remember(resetKey) { Animatable(startIdx * rowPx) }
    var activeForce by remember(resetKey) { mutableStateOf<Int?>(null) }
    val latestOnSettle by rememberUpdatedStateCompat(onSettle)
    var job by remember(resetKey) { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    val maxScroll = (total - 1) * rowPx

    fun clampTop(v: Float) = if (loop) v else v.coerceIn(0f, maxScroll)

    /** timeWheelWrapCheck: jump one copy when drifting into the first / last copy (invisible). */
    suspend fun wrapCheck() {
        if (!loop) return
        val idx = (scroll.value / rowPx).roundToInt()
        if (idx < n) scroll.snapTo(scroll.value + n * rowPx)
        else if (idx >= (copies - 1) * n) scroll.snapTo(scroll.value - n * rowPx)
    }

    fun indexOfScroll(): Int = (scroll.value / rowPx).roundToInt().coerceIn(0, total - 1)

    suspend fun settle() {
        wrapCheck()
        val idx = indexOfScroll()
        scroll.animateTo(idx * rowPx, tween(380, EaseOutQuint))
        latestOnSettle(items[idx % n].value)
    }

    suspend fun runInertia(vPxPerMs: Float) {
        var v = vPxPerMs.coerceIn(-MAX_VELOCITY, MAX_VELOCITY)
        if (abs(v) < 0.02f) { settle(); return }
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val dt = min(32f, (now - last) / 1_000_000f)
            last = now
            scroll.snapTo(clampTop(scroll.value + v * dt))
            wrapCheck()
            val sign = if (v > 0) 1f else -1f
            v -= sign * FRICTION * dt
            if (sign * v < 0) v = 0f
            if (!loop && (scroll.value <= 0f || scroll.value >= maxScroll)) break
            if (abs(v) <= 0.01f) break
        }
        settle()
    }

    val dragState = rememberDraggableState { delta ->
        // finger moves down -> wheel content moves down -> scrollTop decreases
        scope.launch { scroll.snapTo(clampTop(scroll.value - delta)) }
    }

    val active = activeForce ?: indexOfScroll()

    Box(
        modifier = modifier
            .width(width)
            .height(132.dp)
            .draggable(
                state = dragState,
                orientation = Orientation.Vertical,
                onDragStarted = { job?.cancel(); activeForce = null },
                onDragStopped = { velocityPxPerSec ->
                    job?.cancel()
                    job = scope.launch { runInertia(-velocityPxPerSec / 1000f) }
                },
            )
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                // mask: transparent -> black 28% ... black 72% -> transparent
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.28f to Color.Black,
                        0.72f to Color.Black,
                        1f to Color.Transparent,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            }
            .clipToBounds(),
    ) {
        val first = max(0, (scroll.value / rowPx).toInt() - 2)
        val last = min(total - 1, first + 6)
        for (i in first..last) {
            val item = items[i % n]
            val isActive = i == active
            val color = when {
                item.hasTx && isActive -> Brand.red
                item.hasTx -> Color(0xFF991B1B).copy(alpha = 0.70f)
                isActive -> c.textGray900
                else -> c.textGray300
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ROW_H.dp)
                    .layout { m, cons ->
                        val p = m.measure(cons)
                        layout(p.width, p.height) {
                            // item i's top = (i + 1) * row - scrollTop (one spacer row above)
                            p.place(0, (((i + 1) * rowPx) - scroll.value).roundToInt())
                        }
                    }
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        job?.cancel()
                        activeForce = i
                        job = scope.launch {
                            scroll.animateTo(i * rowPx, tween(360, EaseOutQuint))
                            latestOnSettle(items[i % n].value)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                AppText(
                    text = item.label,
                    size = if (isActive) 18f else 14f,
                    lineHeight = if (isActive) 28f else 20f,
                    weight = if (isActive) FontWeight.ExtraBold else FontWeight.Bold,
                    color = color,
                    align = TextAlign.Center,
                    noWrap = true,
                    modifier = Modifier.graphicsLayer {
                        val s = if (isActive) 1.05f else 1f
                        scaleX = s
                        scaleY = s
                    },
                )
            }
        }
    }

    // a drag/animation finished: the forced highlight is no longer needed
    LaunchedEffect(scroll.isRunning) {
        if (!scroll.isRunning) activeForce = null
    }
}

@Composable
private fun <T> rememberUpdatedStateCompat(v: T) = androidx.compose.runtime.rememberUpdatedState(v)

private fun <T> tween(durationMillis: Int, easing: Easing) =
    androidx.compose.animation.core.tween<T>(durationMillis = durationMillis, easing = easing)
