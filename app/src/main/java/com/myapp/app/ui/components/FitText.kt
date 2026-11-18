package com.myapp.app.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.myapp.app.ui.theme.AppFont
import kotlin.math.max

private fun fitStyle(
    sizeSp: Float,
    weight: FontWeight,
    color: Color,
    letterSpacingEm: Float,
    lineHeightRatio: Float,
    align: TextAlign,
): TextStyle = TextStyle(
    fontFamily = AppFont,
    fontSize = sizeSp.sp,
    fontWeight = weight,
    color = color,
    letterSpacing = (sizeSp * letterSpacingEm).sp,
    lineHeight = (sizeSp * lineHeightRatio).sp,
    textAlign = align,
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    ),
)

private fun textWidthPx(measurer: TextMeasurer, text: String, style: TextStyle): Int =
    measurer.measure(text = text, style = style, softWrap = false, maxLines = 1).size.width

/**
 * Money text that shrinks to fit its width (the old fitBalanceText): starts at [maxFontSize]
 * and goes down 1 px at a time, but never below max(9, 32% of the maximum).
 */
@Composable
fun FitText(
    text: String,
    maxFontSize: Float,
    color: Color,
    modifier: Modifier = Modifier,
    weight: FontWeight = FontWeight.ExtraBold,
    letterSpacingEm: Float = 0f,
    lineHeightRatio: Float = 1.2f,
    align: TextAlign = TextAlign.Start,
) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier) {
        val availablePx = constraints.maxWidth
        val minSize = max(9f, maxFontSize * 0.32f)
        var size = maxFontSize
        var guard = 0
        while (guard < 60 && size > minSize &&
            textWidthPx(measurer, text, fitStyle(size, weight, color, letterSpacingEm, lineHeightRatio, align)) > availablePx
        ) {
            size -= 1f
            guard++
        }
        BasicText(
            text = text,
            style = fitStyle(size, weight, color, letterSpacingEm, lineHeightRatio, align),
            softWrap = false,
            maxLines = 1,
        )
    }
}
