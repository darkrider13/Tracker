package com.myapp.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.myapp.app.ui.theme.AppTheme

/**
 * A [PagedViewport] with a bottom fade that appears only while the list is being scrolled (and there
 * is more below) and disappears instantly when scrolling stops or the very end is reached.
 *
 * The fade is 40 dp and takes the page background colour (#000 dark / #f4f4f4 light), 90 % opaque
 * at the bottom, fading in/out over 250 ms.
 */
@Composable
fun ScrollFadePaged(
    state: PagedState,
    count: Int,
    modifier: Modifier = Modifier,
    fade: Boolean = true,
    smoothSnap: Boolean = true,
    animateSize: Boolean = true,
    defaultItemDp: Dp = 76.dp,
    content: @Composable () -> Unit,
) {
    val c = AppTheme.colors
    val scroll = state.scroll
    val scrollable = scroll.maxValue in 3 until Int.MAX_VALUE
    val more = if (scrollable) scroll.value < scroll.maxValue - 2 else (count > 0 && count >= state.visible)
    // The fade shows only while the list is moving (and there is more below). The moment the
    // scrolling stops it is gone at once (no fade-out animation); it eases in softly when scrolling starts.
    val moving = scroll.isScrollInProgress
    val show = fade && more && moving
    val alpha by animateFloatAsState(
        targetValue = if (show) 1f else 0f,
        animationSpec = if (show) tween(140, easing = FastOutSlowInEasing) else snap(),
        label = "scrollFade",
    )

    Box(modifier) {
        PagedViewport(
            state = state, count = count, smoothSnap = smoothSnap,
            animateSize = animateSize, defaultItemDp = defaultItemDp, content = content,
        )
        if (fade) Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(40.dp)
                .alpha(alpha)
                .background(
                    Brush.verticalGradient(
                        0f to c.screenBg.copy(alpha = 0f),
                        0.9f to c.screenBg,
                        1f to c.screenBg,
                    ),
                ),
        )
    }
}
