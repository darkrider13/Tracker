package com.myapp.app.ui.popup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.myapp.app.ui.components.AppText
import com.myapp.app.ui.components.appTextStyle
import com.myapp.app.ui.theme.AppTheme
import com.myapp.app.ui.theme.Brand
import com.myapp.app.ui.theme.Tw

/**
 * The text box used in every popup (p-3 bg-white/80 rounded-xl text-sm font-bold border,
 * green border while focused).
 */
@Composable
fun AppTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    textColor: Color? = null,
    maxLength: Int = Int.MAX_VALUE,
    focusColor: Color = Brand.green,
    readOnly: Boolean = false,
) {
    val c = AppTheme.colors
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    val color = textColor ?: c.textDefault
    BasicTextField(
        value = value,
        onValueChange = { nv -> if (nv.text.length <= maxLength) onValueChange(nv) },
        singleLine = true,
        readOnly = readOnly,
        textStyle = appTextStyle(14f, color, FontWeight.Bold, lineHeight = 20f),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        cursorBrush = SolidColor(color),
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused },
        decorationBox = { inner ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(c.bgWhite80, shape)
                    .border(1.dp, if (focused) focusColor else c.borderGray200, shape)
                    .padding(12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.text.isEmpty()) {
                    AppText(
                        text = placeholder,
                        size = 14f,
                        lineHeight = 20f,
                        weight = FontWeight.Bold,
                        color = c.placeholder,
                        singleLineEllipsis = true,
                    )
                }
                inner()
            }
        },
    )
}

enum class ModalButtonKind { Secondary, Green, Red, GreenLight }

private class ButtonSpec(val bg: Color, val fg: Color, val border: Color?)

/** Popup buttons (py-2.5 rounded-xl text-xs font-bold). */
@Composable
fun ModalButton(
    text: String,
    kind: ModalButtonKind,
    modifier: Modifier = Modifier,
    textColor: Color? = null,
    onClick: () -> Unit,
) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(12.dp)
    val spec = when (kind) {
        ModalButtonKind.Secondary ->
            if (c.isLight) ButtonSpec(c.bgWhite, c.textGray700, null)
            else ButtonSpec(Color(0xFF1E1E1E), Color(0xFFD4D4D8), Color(0xFF2A2A2A))
        ModalButtonKind.Green -> ButtonSpec(Brand.greenBtnBg, Color.White, null)
        ModalButtonKind.GreenLight -> ButtonSpec(Tw.green500, Color.White, null)
        ModalButtonKind.Red -> ButtonSpec(Brand.red, Color.White, null)
    }
    val bg = spec.bg
    val fg = textColor ?: spec.fg
    val borderColor = spec.border
    Box(
        modifier = modifier
            .clip(shape)
            .background(bg, shape)
            .then(if (borderColor != null) Modifier.border(1.dp, borderColor, shape) else Modifier)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        AppText(
            text = text,
            size = 12f,
            lineHeight = 16f,
            weight = FontWeight.Bold,
            color = fg,
            align = TextAlign.Center,
        )
    }
}
