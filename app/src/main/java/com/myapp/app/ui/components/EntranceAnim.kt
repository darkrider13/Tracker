package com.myapp.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.myapp.app.data.AppSession
import kotlinx.coroutines.delay

/** Soft ease-out (fast start, long gentle landing): the same curve the bars and the unlock reveal use. */
val EntranceEase = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

/** Process-wide: has the app finished its busy start-up (splash gone, first frames calm)? */
object UiSettleState {
    var settled by mutableStateOf(false)
}

/** Waits until the main thread is idle: the splash is gone AND a few frames in a row come on time. */
private suspend fun awaitCalmFrames() {
    while (!AppSession.uiReady) withFrameNanos { }
    val start = withFrameNanos { it }
    var last = start
    var calm = 0
    // 4 on-time frames in a row (< 24 ms apart, so also fine at 90 / 120 Hz); never wait longer than 1 s
    while (calm < 4 && last - start < 1_000_000_000L && !UiSettleState.settled) {
        val now = withFrameNanos { it }
        if (now - last < 24_000_000L) calm++ else calm = 0
        last = now
    }
    delay(50)
}

/**
 * false while the app is still starting (native splash on screen, first frames busy), true once it is calm.
 * Screens that animate in at launch (lock screen, Welcome screen) wait for this, so their entrance starts on a
 * quiet main thread and is smooth instead of stuttering through the start-up work. After start-up it is true at once.
 */
@Composable
fun rememberUiSettled(): Boolean {
    LaunchedEffect(Unit) {
        if (!UiSettleState.settled) {
            awaitCalmFrames()
            UiSettleState.settled = true
        }
    }
    return UiSettleState.settled
}

/**
 * Fade + gentle rise into place. Animates (once) after [go] turns true and [delayMs] have passed, so several
 * parts of one screen can come in one after another. Runs in the graphics layer (no recomposition per frame).
 */
@Composable
fun Modifier.entrance(
    go: Boolean,
    delayMs: Int = 0,
    rise: Dp = 22.dp,
    durationMs: Int = 560,
): Modifier {
    val p = remember { Animatable(0f) }
    LaunchedEffect(go) {
        if (go) {
            delay(delayMs.toLong())
            p.animateTo(1f, tween(durationMs, easing = EntranceEase))
        }
    }
    val risePx = with(LocalDensity.current) { rise.toPx() }
    return this.graphicsLayer {
        alpha = p.value
        translationY = (1f - p.value) * risePx
    }
}
