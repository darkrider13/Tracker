package com.myapp.app.ui.components

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.myapp.app.util.formatAmountTyping
import com.myapp.app.util.stripLeadingDigits

/**
 * Live amount box (same as the old onAmountInput): reformats the text with commas / Bangla
 * numerals and keeps the cursor the same distance from the END of the text.
 * Use it in a text field's onValueChange: `onValueChange = { value = formatAmountField(it) }`.
 */
fun formatAmountField(input: TextFieldValue): TextFieldValue {
    val text = input.text
    val cursorFromEnd = text.length - input.selection.end
    val formatted = formatAmountTyping(text)
    val pos = (formatted.length - cursorFromEnd).coerceIn(0, formatted.length)
    return TextFieldValue(text = formatted, selection = TextRange(pos))
}

/**
 * Name boxes (person, category, fund name) never start with a digit
 * (same as the old stripLeadingDigitsInput). Text without a leading digit is returned unchanged.
 */
fun stripLeadingDigitsField(input: TextFieldValue): TextFieldValue {
    val text = input.text
    val stripped = stripLeadingDigits(text)
    if (stripped == text) return input
    val cursorFromEnd = text.length - input.selection.end
    val pos = (stripped.length - cursorFromEnd).coerceIn(0, stripped.length)
    return TextFieldValue(text = stripped, selection = TextRange(pos))
}
