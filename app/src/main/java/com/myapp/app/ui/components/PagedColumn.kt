package com.myapp.app.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Measures a list so it always shows exactly [visible] full cards (never a half card) and
 * settles on a whole card after every scroll (the old fitListToVisibleCount with snap).
 */
class PagedState(val scroll: ScrollState, val visible: Int, val gapPx: Int, val snapStep: Int = 1) {
    /** Measured height (px) of every item, by index. */
    val heights = mutableStateMapOf<Int, Int>()

    private fun allMeasured(count: Int): Boolean {
        for (i in 0 until count) if (heights[i] == null) return false
        return true
    }

    /** Height of [n] items starting at [from], gaps included. */
    private fun span(from: Int, n: Int): Int {
        var sum = 0
        for (i in from until from + n) sum += (heights[i] ?: 0)
        return sum + gapPx * (n - 1)
    }

    /**
     * Viewport height (px), or null while nothing needs clamping / not measured yet.
     * Every item is a snap point, so EVERY window of [visible] consecutive items must fit:
     * the height of the tallest window is used (same as the old fitListToVisibleCount with snap).
     */
    fun viewportMax(count: Int): Int? {
        if (count <= visible) return null
        if (!allMeasured(count)) return null
        var best = 0
        var start = 0
        while (start + visible <= count) {
            best = maxOf(best, span(start, visible))
            start += 1
        }
        return if (best > 0) best else null
    }

    /**
     * A good-enough viewport height (px) while the real one is not known yet (some items are still
     * unmeasured): average of the items measured so far. Keeps the list from flashing at full length
     * for a frame and then collapsing (the "shake" when a long list replaces a short one).
     */
    fun provisionalMax(defaultItemPx: Int): Int {
        var sum = 0
        var n = 0
        for (h in heights.values) { sum += h; n++ }
        val avg = if (n > 0) sum / n else defaultItemPx
        return avg * visible + gapPx * (visible - 1)
    }

    /** Scroll offset (px) where page-start item [index] sits at the top. */
    fun offsetOf(index: Int): Int {
        var sum = 0
        for (i in 0 until index) sum += (heights[i] ?: 0) + gapPx
        return sum
    }

    /** Scroll offset of the page that contains item [index]. */
    fun pageOffsetOfItem(index: Int): Int {
        val groupStart = if (index > 0) (index / visible) * visible else 0
        return offsetOf(groupStart)
    }

    /** Nearest item start (every item is a snap point). */
    fun nearestPageOffset(count: Int): Int {
        var best = 0
        var bestDist = Int.MAX_VALUE
        for (i in 0 until count) {
            if (i % snapStep != 0) continue
            val off = offsetOf(i)
            val d = abs(off - scroll.value)
            if (d < bestDist) {
                bestDist = d
                best = off
            }
        }
        return minOf(best, scroll.maxValue)
    }
}

@Composable
fun rememberPagedState(visible: Int, gap: Dp, snapStep: Int = 1): PagedState {
    val scroll = rememberScrollState()
    val gapPx = with(LocalDensity.current) { gap.roundToPx() }
    return remember(visible, gapPx, snapStep) { PagedState(scroll, visible, gapPx, snapStep) }
}

/** Gives an item its size report so [PagedState] can measure it. */
fun Modifier.pagedItem(state: PagedState, index: Int): Modifier =
    this.onSizeChanged { state.heights[index] = it.height }

/**
 * Scrollable viewport clamped to full pages. Put the items in [content] and mark each one with
 * `Modifier.pagedItem(state, index)`. [snap] = settle on a whole page after every scroll.
 */
@Composable
fun PagedViewport(
    state: PagedState,
    count: Int,
    modifier: Modifier = Modifier,
    snap: Boolean = true,
    animateSize: Boolean = true,
    smoothSnap: Boolean = false,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val maxPx = state.viewportMax(count)
    val shownPx = maxPx ?: if (count > state.visible) state.provisionalMax(with(density) { 76.dp.roundToPx() }) else null
    val clamp = if (shownPx != null) Modifier.heightIn(max = with(density) { shownPx.toDp() }) else Modifier.heightIn(max = 100000.dp)

    // smoothSnap: the fling itself glides onto the nearest card in ONE continuous motion
    // (no separate "stop, then jump-animate" step after the list has already come to rest).
    val decay = rememberSplineBasedDecay<Float>()
    val fallbackFling = ScrollableDefaults.flingBehavior()
    val currentCount by rememberUpdatedState(count)
    val snapFling = remember(state, decay, fallbackFling) {
        PagedSnapFling(state, { currentCount }, fallbackFling, decay)
    }

    LaunchedEffect(state, count, snap, smoothSnap) {
        if (smoothSnap) return@LaunchedEffect
        snapshotFlow { state.scroll.isScrollInProgress }.collect { moving ->
            if (!moving && snap && count > state.visible && state.viewportMax(count) != null) {
                val target = state.nearestPageOffset(count)
                if (abs(target - state.scroll.value) > 1) state.scroll.animateScrollTo(target)
            }
        }
    }

    // size changes (a longer / shorter list after a filter) glide instead of jumping
    Box(
        modifier
            .then(if (animateSize) Modifier.animateContentSize(tween(180, easing = FastOutSlowInEasing)) else Modifier)
            .then(clamp)
            .then(
                if (smoothSnap && snap) Modifier.verticalScroll(state.scroll, flingBehavior = snapFling)
                else Modifier.verticalScroll(state.scroll),
            ),
    ) {
        content()
    }
}

/**
 * Natural (decaying) fling that, once it has almost stopped, keeps its remaining velocity and eases
 * into the nearest whole card with a critically damped spring. One smooth motion: no hard stop and
 * no second "snap" animation, no overshoot.
 */
private class PagedSnapFling(
    private val state: PagedState,
    private val count: () -> Int,
    private val fallback: FlingBehavior,
    private val decay: DecayAnimationSpec<Float>,
) : FlingBehavior {
    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        val n = count()
        if (n <= state.visible || state.viewportMax(n) == null) {
            return with(fallback) { performFling(initialVelocity) }
        }
        var carry = initialVelocity
        if (abs(initialVelocity) > 300f) {
            var last = 0f
            AnimationState(initialValue = 0f, initialVelocity = initialVelocity).animateDecay(decay) {
                val delta = value - last
                last = value
                val consumed = scrollBy(delta)
                carry = this.velocity
                // hit the top / bottom edge, or slow enough: hand over to the snap glide
                if (abs(delta - consumed) > 0.5f || abs(this.velocity) < 220f) cancelAnimation()
            }
        }
        val target = state.nearestPageOffset(n).toFloat()
        val from = state.scroll.value.toFloat()
        if (abs(target - from) > 0.5f) {
            var last = from
            animate(
                initialValue = from,
                targetValue = target,
                initialVelocity = carry,
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
            ) { v, _ ->
                scrollBy(v - last)
                last = v
            }
        }
        return 0f
    }
}
