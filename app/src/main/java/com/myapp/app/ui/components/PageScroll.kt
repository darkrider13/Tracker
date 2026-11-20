package com.myapp.app.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Page scroll for a main tab screen that stays LOCKED while the content fits above the bottom bar.
 * The page only scrolls when the content really runs under the nav bar (small phones, long content);
 * lists inside the page keep their own normal scrolling.
 *
 * [forceLock] keeps the page locked no matter how tall the content is.
 *
 * [bottomPad] is the empty space the screen keeps at its end (128 dp), [navClear] the part of it the
 * floating nav bar covers. Only what is more than the rest counts as real overflow.
 */
@Composable
fun Modifier.pageScroll(state: ScrollState, bottomPad: Dp = 128.dp, navClear: Dp = 76.dp, forceLock: Boolean = false): Modifier {
    val slack = with(LocalDensity.current) { (bottomPad - navClear).roundToPx() }
    val canScroll = !forceLock && state.maxValue > slack
    // If the content shrinks until it fits again, the page returns to the top and locks.
    LaunchedEffect(canScroll) {
        if (!canScroll && state.value > 0) state.animateScrollTo(0)
    }
    return this.verticalScroll(state, enabled = canScroll)
}
