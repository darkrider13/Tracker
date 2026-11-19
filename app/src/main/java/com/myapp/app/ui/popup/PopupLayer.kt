package com.myapp.app.ui.popup

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.myapp.app.ui.theme.AppTheme

/**
 * Which popups are open. A popup stays composed while it fades out, so these are plain flags
 * (the old showPopup(id) / hidePopup(id) pair).
 */
object Popups {
    var addExpense by mutableStateOf(false)
    var addCategory by mutableStateOf(false)
    var addMoney by mutableStateOf(false)
    var transferFund by mutableStateOf(false)
    var editTransaction by mutableStateOf(false)
    var editTransfer by mutableStateOf(false)
    var addFund by mutableStateOf(false)
    var filterPicker by mutableStateOf(false)
    var timeCalendar by mutableStateOf(false)
    var unifiedDatePicker by mutableStateOf(false)
    var timePicker by mutableStateOf(false)
    var dueDateCalendar by mutableStateOf(false)
    var addDebt by mutableStateOf(false)
    var debtPayment by mutableStateOf(false)
    var debtHistory by mutableStateOf(false)
    var editFund by mutableStateOf(false)
    var deleteFundConfirm by mutableStateOf(false)
    var allCategories by mutableStateOf(false)
    var avatarPicker by mutableStateOf(false)
    var lockType by mutableStateOf(false)
    var noteModal by mutableStateOf(false)
    var reminders by mutableStateOf(false)
    var allCatMonthPicker by mutableStateOf(false)
    var alert by mutableStateOf<AlertRequest?>(null)
    var confirm by mutableStateOf<ConfirmRequest?>(null)

    /** Any popup up -> the screen behind gets the frosted blur (old backdrop-filter). */
    val anyVisible: Boolean
        get() = addExpense || addCategory || addMoney || transferFund || editTransaction ||
            editTransfer || addFund || filterPicker || timeCalendar || unifiedDatePicker || timePicker || dueDateCalendar || addDebt || debtPayment || debtHistory || editFund || deleteFundConfirm || allCategories || allCatMonthPicker || avatarPicker || lockType || noteModal || reminders || alert != null || confirm != null
}

/** Animation progress of the popup that is currently drawing (0 = closed, 1 = fully open). */
internal val LocalPopupProgress = compositionLocalOf<State<Float>> { mutableStateOf(1f) }

/** 88% of the screen height: the tallest a popup card may be (max-h-[88vh] before). */
internal val LocalPopupMaxHeight = compositionLocalOf { 600.dp }

private val PopupEase = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

/**
 * Full-screen overlay for one popup: the dim background fades in 0.27 s (out 0.15 s) and the
 * card inside scales 0.95 -> 1 while sliding up 10 px (same as div[id^="popup-"] before).
 */
@Composable
fun PopupLayer(
    visible: Boolean,
    modifier: Modifier = Modifier,
    scrim: Float = 0.6f,
    content: @Composable () -> Unit,
) {
    val progress = animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = if (visible) 274 else 150, easing = PopupEase),
        label = "popupProgress",
    )
    if (!visible && progress.value <= 0.001f) return

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = progress.value }
            .background(Color.Black.copy(alpha = scrim))
            // swallow every tap so nothing behind the popup reacts
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { FundDropdownState.close() }
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(
            LocalPopupProgress provides progress,
            LocalPopupMaxHeight provides (maxHeight * 0.88f),
        ) {
            content()
        }
    }
}

/**
 * The card of a popup (.glass-card): animated with the layer, rounded, tinted by the theme,
 * scrolls inside when the screen is small. [padding] is the p-5 / p-6 of the old markup.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    radius: Dp = 24.dp,
    padding: Dp = 20.dp,
    maxWidth: Dp = 384.dp,
    scrollable: Boolean = true,
    overlay: @Composable BoxScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = AppTheme.colors
    val progress = LocalPopupProgress.current
    val maxHeight = LocalPopupMaxHeight.current
    val shape = RoundedCornerShape(radius)
    Box(
        modifier = modifier
            .widthIn(max = maxWidth)
            .fillMaxWidth()
            .graphicsLayer {
                val p = progress.value
                alpha = p
                val s = 0.95f + 0.05f * p
                scaleX = s
                scaleY = s
                translationY = (1f - p) * 10.dp.toPx()
            }
            .heightIn(max = maxHeight)
            .clip(shape)
            .background(c.cardBg, shape)
            .border(1.dp, c.cardBorder, shape),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(padding),
            content = content,
        )
        overlay()
    }
}
