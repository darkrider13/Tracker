package com.myapp.app.ui.popup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.myapp.app.ui.components.AppText
import com.myapp.app.ui.components.FaIcon
import com.myapp.app.ui.components.FundTypeIcon
import com.myapp.app.ui.components.PagedViewport
import com.myapp.app.ui.components.pagedItem
import com.myapp.app.ui.components.rememberPagedState
import com.myapp.app.ui.theme.AppTheme
import com.myapp.app.ui.theme.Brand
import com.myapp.app.util.FUND_TYPES
import com.myapp.app.util.fundTint
import com.myapp.app.util.parseCssColor
import com.myapp.app.util.tr

/** Preset colours offered for a fund (same list, same order as before). */
val FUND_COLORS: List<String> = listOf(
    "var(--brand-green)", "#0d9488", "#0ea5e9", "#2563eb", "#7c3aed", "#9333ea", "#db2777",
    "var(--brand-red)", "#0891b2", "#ea580c", "#ca8a04", "#65a30d", "#d946ef", "#52525b",
    "#4f46e5", "#e11d48", "#059669", "#eab308", "#475569", "#0369a1", "#c2410c",
    "#78350f", "#a21caf", "#38bdf8", "#fb7185", "#4d7c0f", "#1e3a8a", "#f59e0b",
)

/** "Fund Type & Icon" picker: 4 across, 2 rows visible, scrolls a page of 2 rows at a time. */
@Composable
fun FundTypeGrid(selectedKey: String, onSelect: (String) -> Unit) {
    val c = AppTheme.colors
    val rows = FUND_TYPES.chunked(4)
    val paged = rememberPagedState(visible = 2, gap = 8.dp, snapStep = 2)
    PagedViewport(state = paged, count = rows.size, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((ri, rowTypes) in rows.withIndex()) {
                Row(
                    modifier = Modifier.fillMaxWidth().pagedItem(paged, ri),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (t in rowTypes) {
                        val sel = t.key == selectedKey
                        val tColor = parseCssColor(t.color) ?: Brand.red
                        val shape = RoundedCornerShape(12.dp)
                        val selTint = fundTint(t.color, 0x1A)
                        val bg = if (sel) selTint else Color.Black.copy(alpha = 0.03f)
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(shape)
                                .then(if (bg != null) Modifier.background(bg, shape) else Modifier)
                                .border(2.dp, if (sel) tColor else Color.Transparent, shape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { onSelect(t.key) }
                                .padding(horizontal = 4.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            val tint = fundTint(t.color, 0x22)
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .then(if (tint != null) Modifier.background(tint, CircleShape) else Modifier),
                                contentAlignment = Alignment.Center,
                            ) { FundTypeIcon(t.key, t.icon, 10.dp, tColor) }
                            Box(
                                modifier = Modifier.padding(top = 2.dp).defaultMinSize(minHeight = 18.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                AppText(
                                    text = tr("fund_type_" + t.key, t.label),
                                    size = 8f,
                                    lineHeight = 10f,
                                    weight = FontWeight.Bold,
                                    color = c.textGray600,
                                    align = TextAlign.Center,
                                )
                            }
                        }
                    }
                    repeat(4 - rowTypes.size) { Box(modifier = Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** "Colour" picker: 7 across, 2 rows visible, scrolls a page of 2 rows at a time. */
@Composable
fun FundColorGrid(selectedColor: String, onSelect: (String) -> Unit) {
    val rows = FUND_COLORS.chunked(7)
    val paged = rememberPagedState(visible = 2, gap = 10.dp, snapStep = 2)
    val sel = selectedColor.lowercase()
    PagedViewport(state = paged, count = rows.size, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for ((ri, rowColors) in rows.withIndex()) {
                Row(
                    modifier = Modifier.fillMaxWidth().pagedItem(paged, ri),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    for (col in rowColors) {
                        val isSel = col.lowercase() == sel
                        val colorValue = parseCssColor(col) ?: Brand.red
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { onSelect(col) },
                            contentAlignment = Alignment.Center,
                        ) {
                            // 20 px hit box; 16 px dot; the selected ring (1 px white + 1 px colour) stays inside it
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .drawBehind {
                                        if (isSel) {
                                            drawCircle(colorValue, radius = 10.dp.toPx())
                                            drawCircle(Color.White, radius = 9.dp.toPx())
                                        }
                                        drawCircle(colorValue, radius = 8.dp.toPx())
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (isSel) FaIcon("fa-check", 8.dp, Color.White)
                            }
                        }
                    }
                    repeat(7 - rowColors.size) { Box(modifier = Modifier.weight(1f)) }
                }
            }
        }
    }
}
