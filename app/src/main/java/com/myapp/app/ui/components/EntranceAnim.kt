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

/**
 * false while the native launch screen is still on screen, true a moment after it is gone.
 * Screens that animate in at launch (lock screen, Welcome screen) wait for this, so their entrance is
 * actually SEEN instead of happening behind the splash. Once the app is running it is true at once.
 */
@Composable
fun rememberSplashGone(extraDelayMs: Long = 60L): Boolean {
    var gone by remember { mutableStateOf(AppSession.uiReady) }
    LaunchedEffect(Unit) {
        if (!gone) {
            while (!AppSession.uiReady) withFrameNanos { }
            delay(extraDelayMs)
            gone = true
        }
    }
    return gone
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
