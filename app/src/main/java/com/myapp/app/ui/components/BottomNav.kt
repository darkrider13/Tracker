package com.myapp.app.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myapp.app.ui.AppUi
import com.myapp.app.ui.Tab
import com.myapp.app.ui.theme.AppFont
import com.myapp.app.ui.theme.AppTheme
import com.myapp.app.util.tr

private val NavEase = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

private fun navStyle(sizeSp: Float, weight: FontWeight, lineHeightSp: Float) = TextStyle(
    fontFamily = AppFont,
    fontSize = sizeSp.sp,
    fontWeight = weight,
    color = Color.White,
    lineHeight = lineHeightSp.sp,
    textAlign = TextAlign.Center,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)


/** Width (dp) the active pill needs: icon 18 + gap 5 + label + horizontal padding 2 x 15. */
private const val PILL_EXTRA_DP = 53f
/** Breathing room kept between the active pill and the bar's inner edge / neighbours (each side). */
private const val PILL_EDGE_GAP_DP = 4f
/** Inactive icon slots never get narrower than this. */
private const val MIN_SLOT_DP = 34f

/**
 * Weight the active slot needs so its pill always fits inside the bar with a small gap on both sides,
 * whatever the language / font scale / screen width. [innerDp] is the bar's usable width, 4 slots total.
 */
private fun activeSlotWeight(innerDp: Float, labelDp: Float): Float {
    val base = innerDp * 1.75f / 4.75f
    val needed = PILL_EXTRA_DP + labelDp + PILL_EDGE_GAP_DP * 2
    val active = maxOf(base, needed).coerceAtMost(innerDp - 3 * MIN_SLOT_DP)
    val inactive = (innerDp - active) / 3f
    return (active / inactive).coerceAtLeast(1f)
}

/**
 * One tab. Every tab sits in its own slot of the bar; the active slot grows (a weight that eases from 1 to
 * 2.2) while the others shrink by the same amount, so the bar's total width never changes and nothing
 * jumps. The label's text is measured once at its natural width; only its visible width and opacity are
 * animated, so no text is re-laid-out on every frame.
 */
@Composable
private fun RowScope.NavPill(
    icon: String,
    label: String,
    active: Boolean,
    activeWeight: Float,
    onClick: () -> Unit,
) {
    val shape = CircleShape
    val interaction = remember { MutableInteractionSource() }
    val spec = tween<Float>(durationMillis = 240, easing = NavEase)

    val slotWeight by animateFloatAsState(if (active) activeWeight else 1f, spec, label = "navSlotWeight")
    val bgAlpha by animateFloatAsState(if (active) 1f else 0f, spec, label = "navPillBg")
    val labelFrac by animateFloatAsState(if (active) 1f else 0f, spec, label = "navLabelFrac")
    val iconScale by animateFloatAsState(1f, spec, label = "navIconScale")

    // The slot only reserves room; the highlight is a small pill that hugs icon + label (not the whole slot).
    Box(
        modifier = Modifier
            .weight(slotWeight)
            .height(36.dp)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .height(34.dp)
                .clip(shape)
                .drawBehind {
                    // flat, solid highlight (no gradient, no border) – like a plain grey pill
                    if (bgAlpha > 0.001f) {
                        drawRect(Color.White.copy(alpha = 0.13f * bgAlpha))
                    }
                }
                .padding(horizontal = (7f + 8f * labelFrac).dp)
                .wrapContentWidth(unbounded = true),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.graphicsLayer { scaleX = iconScale; scaleY = iconScale }) {
                FaIcon(name = icon, size = 18.dp, color = Color.White)
            }
            Box(
                Modifier
                    .layout { measurable, _ ->
                        val p = measurable.measure(Constraints())
                        val w = (p.width * labelFrac).roundToInt()
                        layout(w, p.height) { p.place(0, 0) }
                    }
                    .graphicsLayer { alpha = labelFrac },
            ) {
                Row {
                    Spacer(Modifier.width(5.dp))
                    BasicText(
                        text = label,
                        style = navStyle(12f, FontWeight.Bold, 16f),
                        softWrap = false,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * The floating bottom bar: 4 tabs (active one shows its name) and the round + button.
 * Draws the dark-mode bottom fade too. Hidden while the funds panel or keyboard is open.
 * Place it inside a full-screen Box and align it to the bottom centre.
 */
@Composable
fun BottomNavBar(modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    val hidden = AppUi.bottomNavHidden
    if (hidden) return

    Box(modifier = modifier.fillMaxWidth()) {
        // dark-mode fade so content sinks into the bottom edge (height 2.25rem)
        if (!c.isLight) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(36.dp)
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.00f to Color.Black.copy(alpha = 0f),
                                0.55f to Color.Black.copy(alpha = 0.55f),
                                1.00f to Color.Black,
                            ),
                        ),
                    ),
            )
        }

        BoxWithConstraints(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            val screenW = maxWidth
            val navW = minOf(screenW * 0.92f, 384.dp)
            val gap = (screenW * 0.03f).coerceIn(8.dp, 12.dp)
            Row(
                modifier = Modifier
                    .padding(bottom = 12.dp)
                    .width(navW)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // the pill bar
                val labels = listOf(
                    tr("nav_home", "Home"),
                    tr("nav_logs", "Logs"),
                    tr("nav_stats", "Stats"),
                    tr("nav_profile", "Profile"),
                )
                val measurer = rememberTextMeasurer()
                val density = LocalDensity.current
                val labelDp = remember(labels, density.density, density.fontScale) {
                    labels.map {
                        val px = measurer.measure(
                            AnnotatedString(it), navStyle(12f, FontWeight.Bold, 16f),
                            softWrap = false, maxLines = 1,
                        ).size.width
                        px / density.density
                    }
                }
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .background(c.navBarBg, CircleShape)
                        .border(1.dp, c.navBarBorder, CircleShape)
                        .padding(horizontal = 3.dp, vertical = 6.dp),
                ) {
                    val innerDp = maxWidth.value
                    val current = AppUi.tab
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        NavPill("fa-home", labels[0], current == Tab.Home, activeSlotWeight(innerDp, labelDp[0])) { AppUi.switchTab(Tab.Home) }
                        NavPill("fa-list", labels[1], current == Tab.Logs, activeSlotWeight(innerDp, labelDp[1])) { AppUi.switchTab(Tab.Logs) }
                        NavPill("fa-chart-bar", labels[2], current == Tab.Stats, activeSlotWeight(innerDp, labelDp[2])) { AppUi.switchTab(Tab.Stats) }
                        NavPill("fa-user", labels[3], current == Tab.Profile, activeSlotWeight(innerDp, labelDp[3])) { AppUi.switchTab(Tab.Profile) }
                    }
                }

                Spacer(Modifier.width(gap))

                // the round + button (shrinks away on Stats / Profile, like the old scale-0 opacity-0)
                val hide = AppUi.addButtonHidden
                val s = animateFloatAsState(
                    targetValue = if (hide) 0f else 1f,
                    animationSpec = tween(durationMillis = 300, easing = NavEase),
                    label = "addButtonScale",
                )
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .graphicsLayer {
                            scaleX = s.value
                            scaleY = s.value
                            alpha = s.value
                        }
                        .clip(CircleShape)
                        .background(Color.Black, CircleShape)
                        .then(
                            if (hide) Modifier else Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { AppUi.handleGlobalAddButton() },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        text = "+",
                        style = navStyle(24f, FontWeight.Bold, 32f),
                    )
                }
            }
        }
    }
}
