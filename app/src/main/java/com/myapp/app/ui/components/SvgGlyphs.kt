package com.myapp.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import com.myapp.app.util.getCategoryFAIcon
import com.myapp.app.util.isOtherCategoryIcon

/*
 * The three hand-drawn SVG glyphs of the old app (24x24 view box, 1em x 1em):
 *  - siren for the "Emergency Fund" type
 *  - four mixed shapes for "Other" (fund type AND category)
 *  - small notepad shown on a fund that has a note
 */

private const val SIREN_BODY =
    "M12 3a1 1 0 0 1 1 1v.1A7 7 0 0 1 19 11v4l1.5 2.25A1 1 0 0 1 19.65 19H4.35a1 1 0 0 1-.85-1.75L5 15v-4a7 7 0 0 1 6-6.9V4a1 1 0 0 1 1-1Z"
private const val SIREN_CROSS = "M11 8.3h2v2.5h2.4v2H13v2.4h-2v-2.4H8.6v-2H11V8.3Z"

private fun parsePath(data: String): Path = PathParser().parsePathString(data).toPath()

/** Siren glyph in [color] with a white cross, [size] = the CSS font-size (1em). */
@Composable
fun SirenGlyph(size: Dp, color: Color, modifier: Modifier = Modifier) {
    val body = remember { parsePath(SIREN_BODY) }
    val cross = remember { parsePath(SIREN_CROSS) }
    Canvas(modifier.size(size)) {
        val s = this.size.width / 24f
        scale(scaleX = s, scaleY = s, pivot = Offset.Zero) {
            drawPath(body, color)
            drawRoundRect(
                color = color,
                topLeft = Offset(8.5f, 19.2f),
                size = Size(7f, 1.8f),
                cornerRadius = CornerRadius(0.9f, 0.9f),
            )
            drawPath(cross, Color.White)
        }
    }
}

/** The four-shape "Other" glyph (circle / square / rounded square / circle). */
@Composable
fun OtherGlyph(size: Dp, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val s = this.size.width / 24f
        scale(scaleX = s, scaleY = s, pivot = Offset.Zero) {
            drawCircle(color = color, radius = 4.5f, center = Offset(5.5f, 5.5f))
            drawRoundRect(color = color, topLeft = Offset(13f, 1f), size = Size(9f, 9f), cornerRadius = CornerRadius(1f, 1f))
            drawRoundRect(color = color, topLeft = Offset(1f, 13f), size = Size(9f, 9f), cornerRadius = CornerRadius(3f, 3f))
            drawCircle(color = color, radius = 4.5f, center = Offset(17.5f, 17.5f))
        }
    }
}

/** Tiny notepad icon (outlined) - [size] is the real width/height (w-2 h-2 = 8dp on Home). */
@Composable
fun NoteGlyph(size: Dp, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val s = this.size.width / 24f
        scale(scaleX = s, scaleY = s, pivot = Offset.Zero) {
            drawRoundRect(
                color = color,
                topLeft = Offset(4f, 5f),
                size = Size(14f, 16f),
                cornerRadius = CornerRadius(2f, 2f),
                style = Stroke(width = 2f),
            )
            drawLine(color, Offset(8f, 3f), Offset(8f, 6f), strokeWidth = 2f, cap = StrokeCap.Round)
            drawLine(color, Offset(14f, 3f), Offset(14f, 6f), strokeWidth = 2f, cap = StrokeCap.Round)
            drawLine(color, Offset(7f, 10f), Offset(13f, 10f), strokeWidth = 1.6f, cap = StrokeCap.Round)
            drawLine(color, Offset(7f, 13f), Offset(13f, 13f), strokeWidth = 1.6f, cap = StrokeCap.Round)
            drawLine(color, Offset(7f, 16f), Offset(11f, 16f), strokeWidth = 1.6f, cap = StrokeCap.Round)
        }
    }
}

/**
 * A fund's type icon (the old fundIconHTML): siren for Emergency, four shapes for Other,
 * otherwise the Font Awesome icon. [fontSize] is the CSS font-size of the surrounding text.
 */
@Composable
fun FundTypeIcon(typeKey: String, iconName: String, fontSize: Dp, color: Color, modifier: Modifier = Modifier) {
    when (typeKey) {
        "emergency" -> SirenGlyph(fontSize, color, modifier)
        "other" -> OtherGlyph(fontSize, color, modifier)
        else -> FaIcon(iconName, fontSize, color, modifier)
    }
}

/** A category's icon (the old categoryIconHTML): four shapes for "Other", otherwise Font Awesome. */
@Composable
fun CategoryIcon(rawCategory: String?, fontSize: Dp, color: Color, modifier: Modifier = Modifier) {
    if (isOtherCategoryIcon(rawCategory)) {
        Box(modifier, contentAlignment = Alignment.Center) { OtherGlyph(fontSize, color) }
    } else {
        FaIcon(getCategoryFAIcon(rawCategory), fontSize, color, modifier)
    }
}
