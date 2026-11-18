package com.myapp.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myapp.app.R
import com.myapp.app.ui.components.FaIcon
import com.myapp.app.ui.theme.AppFont
import com.myapp.app.ui.theme.Brand
import kotlin.math.roundToInt

// CSS: cubic-bezier(0.22, 1, 0.36, 1)  and  "ease"
private val RiseEase = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
private val CssEase = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

/** One faded background icon of the splash (positions are % of the screen, like the old CSS). */
private class DecoSpec(
    val icon: String,
    val sizeDp: Int,
    val rotation: Float,
    val white: Boolean,
    val top: Float? = null,
    val bottom: Float? = null,
    val left: Float? = null,
    val right: Float? = null,
)

private val DecoIcons = listOf(
    DecoSpec("chart-pie", 60, -15f, false, top = 0.05f, left = 0.08f),
    DecoSpec("coins", 36, 12f, true, top = 0.09f, right = 0.22f),
    DecoSpec("landmark", 48, 8f, true, top = 0.19f, right = 0.08f),
    DecoSpec("percentage", 36, -8f, false, top = 0.20f, left = 0.20f),
    DecoSpec("wallet", 72, -10f, false, top = 0.38f, left = -0.05f),
    DecoSpec("university", 72, 8f, true, top = 0.36f, right = -0.07f),
    DecoSpec("receipt", 36, 16f, true, top = 0.55f, right = 0.03f),
    DecoSpec("money-bill-wave", 48, -6f, false, top = 0.56f, left = -0.02f),
    DecoSpec("hand-holding-usd", 48, -6f, false, bottom = 0.20f, left = 0.26f),
    DecoSpec("file-invoice-dollar", 48, 14f, true, bottom = 0.18f, right = 0.14f),
    DecoSpec("calculator", 60, 10f, true, bottom = 0.06f, left = 0.06f),
    DecoSpec("credit-card", 48, -14f, false, bottom = 0.07f, right = -0.03f),
    DecoSpec("chart-line", 60, -8f, false, bottom = -0.02f, left = 0.42f),
)

@Composable
private fun rememberEntrance(durationMs: Int, delayMs: Int, easing: Easing): State<Float> {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = durationMs, delayMillis = delayMs, easing = easing),
        )
    }
    return progress.asState()
}

@Composable
private fun DecoLayer(isLight: Boolean) {
    Layout(
        content = {
            DecoIcons.forEach { spec ->
                val base = if (spec.white) Color.White else Brand.red
                val alpha = if (isLight) {
                    if (spec.white) 0.05f else 0.07f
                } else {
                    if (spec.white) 0.16f else 0.20f
                }
                FaIcon(
                    name = spec.icon,
                    size = spec.sizeDp.dp,
                    color = base.copy(alpha = alpha),
                    modifier = Modifier.graphicsLayer { rotationZ = spec.rotation },
                )
            }
        },
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds(),
    ) { measurables, constraints ->
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val placeables = measurables.map { it.measure(Constraints()) }
        layout(w, h) {
            placeables.forEachIndexed { index, p ->
                val s = DecoIcons[index]
                val x = when {
                    s.left != null -> w * s.left
                    s.right != null -> w - w * s.right - p.width
                    else -> 0f
                }
                val y = when {
                    s.top != null -> h * s.top
                    s.bottom != null -> h - h * s.bottom - p.height
                    else -> 0f
                }
                p.place(x.roundToInt(), y.roundToInt())
            }
        }
    }
}

/**
 * Boot splash: app name, icon, tagline over faded finance icons.
 * Fades out in 0.5s when [visible] turns false (same as the old #boot-splash).
 */
@Composable
fun BootSplash(
    isLight: Boolean,
    isBangla: Boolean,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    val fade = animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 500, easing = CssEase),
        label = "bootSplashFade",
    )
    if (!visible && fade.value <= 0.001f) return

    val background = if (isLight) Color(0xFFFFFFFF) else Color(0xFF05070A)
    val titleColor = if (isLight) Color(0xFF0F172A) else Color(0xFFF5F7FA)
    val taglineColor = if (isLight) Color(0xFF6B7280) else Color(0xFFA3A8C3)

    val decoAlpha = rememberEntrance(durationMs = 1100, delayMs = 200, easing = CssEase)
    val titleP = rememberEntrance(durationMs = 800, delayMs = 100, easing = RiseEase)
    val iconP = rememberEntrance(durationMs = 900, delayMs = 500, easing = RiseEase)
    val taglineP = rememberEntrance(durationMs = 800, delayMs = 1000, easing = RiseEase)

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = fade.value }
            .background(background),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = decoAlpha.value },
        ) {
            DecoLayer(isLight = isLight)
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // 1) app name
            BasicText(
                text = "Tracker",
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .graphicsLayer {
                        val p = titleP.value
                        alpha = p
                        translationY = (1f - p) * 16.dp.toPx()
                    },
                style = TextStyle(
                    fontFamily = AppFont,
                    fontSize = 48.sp,
                    lineHeight = 56.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.48).sp,
                    color = titleColor,
                    textAlign = TextAlign.Center,
                    lineHeightStyle = LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Center,
                        trim = LineHeightStyle.Trim.None,
                    ),
                ),
            )

            // 2) icon
            Box(
                modifier = Modifier
                    .padding(top = 64.dp)
                    .size(104.dp)
                    .graphicsLayer {
                        val p = iconP.value
                        alpha = p
                        val s = 0.82f + 0.18f * p
                        scaleX = s
                        scaleY = s
                        translationY = (1f - p) * 10.dp.toPx()
                    },
            ) {
                Image(
                    painter = painterResource(id = R.drawable.splash_icon_art),
                    contentDescription = "Tracker",
                    modifier = Modifier.size(104.dp),
                )
            }

            // 3) tagline
            BasicText(
                text = if (isBangla) "টাকার হিসাব হোক আরও সহজ।" else "Money management, made simpler.",
                modifier = Modifier
                    .padding(top = 44.dp)
                    .padding(horizontal = 28.dp)
                    .graphicsLayer {
                        val p = taglineP.value
                        alpha = p
                        translationY = (1f - p) * 16.dp.toPx()
                    },
                style = TextStyle(
                    fontFamily = AppFont,
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.32.sp,
                    color = taglineColor,
                    textAlign = TextAlign.Center,
                    lineHeightStyle = LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Center,
                        trim = LineHeightStyle.Trim.None,
                    ),
                ),
            )
        }
    }
}
