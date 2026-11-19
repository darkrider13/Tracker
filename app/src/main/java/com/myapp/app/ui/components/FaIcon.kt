package com.myapp.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.myapp.app.R

/** Same three Font Awesome families the old app used: fas (solid), far (regular), fab (brands). */
enum class FaStyle { Solid, Regular, Brands }

private val SolidFamily = FontFamily(Font(R.font.fa_solid, FontWeight.Normal))
private val RegularFamily = FontFamily(Font(R.font.fa_regular, FontWeight.Normal))
private val BrandsFamily = FontFamily(Font(R.font.fa_brands, FontWeight.Normal))

/**
 * A Font Awesome icon that behaves exactly like <i class="fas fa-xxx"> in the old HTML:
 * box = 1.25em wide x 1em high (line-height 1), glyph centred.
 * An unknown name keeps its box but draws nothing (same as the old app).
 *
 * [size] plays the role of the CSS font-size.
 */
@Composable
fun FaIcon(
    name: String,
    size: Dp,
    color: Color,
    modifier: Modifier = Modifier,
    style: FaStyle = FaStyle.Solid,
) {
    // The bundled font has no arrow-up-right / arrow-down-left glyph. The old app built them from the
    // plain up / down arrows turned 45 degrees, so they do the same here.
    val bare = name.removePrefix("fa-")
    val diagonal = bare == "arrow-up-right" || bare == "arrow-down-left"
    val codepoint = when (bare) {
        "arrow-up-right" -> FaGlyphs.codepoint("arrow-up")
        "arrow-down-left" -> FaGlyphs.codepoint("arrow-down")
        else -> FaGlyphs.codepoint(name)
    }
    val family = when (style) {
        FaStyle.Solid -> SolidFamily
        FaStyle.Regular -> RegularFamily
        FaStyle.Brands -> BrandsFamily
    }
    val sizeSp = with(LocalDensity.current) { size.toSp() }
    Box(
        modifier = modifier
            .width(size * 1.25f)
            .height(size),
        contentAlignment = Alignment.Center,
    ) {
        if (codepoint != null) {
            BasicText(
                text = Character.toChars(codepoint).concatToString(),
                modifier = Modifier
                    .wrapContentSize(Alignment.Center, unbounded = true)
                    .then(if (diagonal) Modifier.rotate(45f) else Modifier),
                style = TextStyle(
                    fontFamily = family,
                    fontWeight = FontWeight.Normal,
                    fontSize = sizeSp,
                    lineHeight = sizeSp,
                    color = color,
                    textAlign = TextAlign.Center,
                    lineHeightStyle = LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Center,
                        trim = LineHeightStyle.Trim.Both,
                    ),
                ),
                softWrap = false,
                maxLines = 1,
            )
        }
    }
}
