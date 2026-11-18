package com.myapp.app.ui.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.myapp.app.ui.theme.AppFont

/** Text style helper that mirrors the Tailwind classes of the old app (size, line-height, tracking). */
fun appTextStyle(
    size: Float,
    color: Color,
    weight: FontWeight = FontWeight.Normal,
    lineHeight: Float = size * 1.5f,
    letterSpacingEm: Float = 0f,
    align: TextAlign = TextAlign.Start,
): TextStyle = TextStyle(
    fontFamily = AppFont,
    fontSize = size.sp,
    fontWeight = weight,
    color = color,
    lineHeight = lineHeight.sp,
    letterSpacing = (size * letterSpacingEm).sp,
    textAlign = align,
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    ),
)

/**
 * Plain text. [size] is the CSS font-size in px (= dp here). [lineHeight] defaults to 1.5 x size,
 * the page default of the old app; named Tailwind sizes pass their own (text-xs 12/16, text-sm 14/20...).
 * [uppercase] = CSS text-transform: uppercase.
 */
@Composable
fun AppText(
    text: String,
    size: Float,
    color: Color,
    modifier: Modifier = Modifier,
    weight: FontWeight = FontWeight.Normal,
    lineHeight: Float = size * 1.5f,
    letterSpacingEm: Float = 0f,
    align: TextAlign = TextAlign.Start,
    uppercase: Boolean = false,
    singleLineEllipsis: Boolean = false,
    noWrap: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
) {
    BasicText(
        text = if (uppercase) text.uppercase() else text,
        modifier = modifier,
        style = appTextStyle(size, color, weight, lineHeight, letterSpacingEm, align),
        softWrap = !(singleLineEllipsis || noWrap),
        maxLines = if (singleLineEllipsis || noWrap) 1 else maxLines,
        overflow = if (singleLineEllipsis) TextOverflow.Ellipsis else TextOverflow.Clip,
    )
}

/** Text with mixed styles (AnnotatedString), same base style rules as [AppText]. */
@Composable
fun AppRichText(
    text: AnnotatedString,
    size: Float,
    color: Color,
    modifier: Modifier = Modifier,
    weight: FontWeight = FontWeight.Normal,
    lineHeight: Float = size * 1.5f,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = appTextStyle(size, color, weight, lineHeight),
    )
}
