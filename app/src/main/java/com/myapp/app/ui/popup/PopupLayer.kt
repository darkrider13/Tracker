package com.myapp.app.ui.popup

import android.os.Handler
import android.os.Looper
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
    var incomingOverview by mutableStateOf(false)
    var avatarPicker by mutableStateOf(false)
    var lockType by mutableStateOf(false)
    var noteModal by mutableStateOf(false)
    var reminders by mutableStateOf(false)
    var allCatMonthPicker by mutableStateOf(false)

    /** true while the Incoming Overview sheet is on screen: its blur is then driven by the sheet's own progress. */
    var sheetSliding by mutableStateOf(false)

    /** 0..1 slide progress of that sheet (written every frame by the sheet, read only in the draw phase of the root). */
    var sheetProgress by mutableFloatStateOf(0f)

    /**
     * Blur radius (dp) behind the Incoming Overview sheet. It does not snap on and it does not wait for the landing:
     * it grows together with the slide (starting after the sheet is about a third of the way up and reaching full
     * strength exactly when it lands), so the frosted look arrives as part of one motion. Rounded to 2 dp steps so the
     * GPU does not have to rebuild the blur for every tiny change.
     */
    val sheetBlurDp: Float
        get() {
            val t = ((sheetProgress - 0.35f) / 0.65f).coerceIn(0f, 1f)
            val eased = t * t * (3f - 2f * t)
            return Math.round(20f * eased / 2f) * 2f
        }

    var sheetBlurSoft by mutableStateOf(false)
    private val fadeHandler = Handler(Looper.getMainLooper())

    /** Runs [block] once the close animation (0.15 s) is over, so a popup never changes while it fades out. */
    fun afterFade(block: () -> Unit) {
        fadeHandler.postDelayed(block, 200L)
    }

    /** Layers on screen right now, fading in or out included (blur stays until the last one is gone). */
    var layersShown by mutableIntStateOf(0)
    var alert by mutableStateOf<AlertRequest?>(null)
    var confirm by mutableStateOf<ConfirmRequest?>(null)

    /** Any popup up -> the screen behind gets the frosted blur (old backdrop-filter). */
    val anyVisible: Boolean
        get() = addExpense || addCategory || addMoney || transferFund || editTransaction ||
            editTransfer || addFund || filterPicker || timeCalendar || unifiedDatePicker || timePicker || dueDateCalendar || addDebt || debtPayment || debtHistory || editFund || deleteFundConfirm || allCategories || incomingOverview || allCatMonthPicker || avatarPicker || lockType || noteModal || reminders || alert != null || confirm != null
}

/**
 * Open order of the popups that are up right now (a popup leaves the list the moment it starts to close). Only the
 * last one is drawn: a popup opened on top of another hides the one beneath at once, and when the top one is
 * closed the one beneath is back on screen instantly. The frosted blur of the page stays the whole time.
 */
internal object PopupStack {
    val order = androidx.compose.runtime.mutableStateListOf<Any>()
}

/** Animation progress of the popup that is currently drawing (0 = closed, 1 = fully open). */
internal val LocalPopupProgress = compositionLocalOf<State<Float>> { mutableStateOf(1f) }

/** 88% of the screen height: the tallest a popup card may be (max-h-[88vh] before). */
internal val LocalPopupMaxHeight = compositionLocalOf { 600.dp }

internal val DropdownEase = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

/**
 * Bottom sheets (All Categories Summary, Incoming Overview): one steady glide up (gentle start, long soft landing) and a
 * quicker, even glide down. The old curve flew up in the first ~100 ms and then crawled, which felt like a hard stop.
 */
internal val SheetEase = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)
internal val SheetCloseEase = CubicBezierEasing(0.4f, 0f, 0.6f, 1f)
internal const val SHEET_OPEN_MS = 420
internal const val SHEET_CLOSE_MS = 260
/** Sheet rows are built just after the slide-in has finished, so the slide runs on light frames. */
internal const val SHEET_ROWS_DELAY_MS = SHEET_OPEN_MS + 30L

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
    /**
     * Frames to wait, invisibly at progress 0, before the opening motion starts. A popup with a heavy form (grids that
     * measure themselves, many fields) is built and laid out in these frames, so the motion never starts on a long first
     * frame and the card never changes size while it is moving. 0 = start at once (the old behaviour).
     */
    settleFrames: Int = 0,
    content: @Composable () -> Unit,
) {
    val progressAnim = remember { androidx.compose.animation.core.Animatable(0f) }
    val progress = progressAnim.asState()
    val token = remember { Any() }
    DisposableEffect(visible) {
        PopupStack.order.remove(token)
        if (visible) PopupStack.order.add(token)
        onDispose { PopupStack.order.remove(token) }
    }
    // covered = another popup was opened after this one and is still up: this one is hidden (not faded) meanwhile
    val covered = visible && token in PopupStack.order && PopupStack.order.lastOrNull() !== token
    androidx.compose.runtime.LaunchedEffect(visible) {
        if (visible) {
            repeat(settleFrames) { androidx.compose.runtime.withFrameNanos { } }
            progressAnim.animateTo(1f, tween(durationMillis = 274, easing = PopupEase))
        } else {
            progressAnim.animateTo(0f, tween(durationMillis = 150, easing = PopupEase))
        }
    }
    if (!visible && progress.value <= 0.001f) return
    DisposableEffect(Unit) {
        Popups.layersShown++
        onDispose { Popups.layersShown-- }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = if (covered) 0f else progress.value }
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
    /** Fully opaque card (day mode): nothing of the page shows through it. */
    solid: Boolean = false,
    overlay: @Composable BoxScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = AppTheme.colors
    val progress = LocalPopupProgress.current
    val maxHeight = LocalPopupMaxHeight.current
    val shape = RoundedCornerShape(radius)
    // a fund list floating over the card blurs the card behind it
    val dropdownOpen = FundDropdownState.openContext != null
    val behindBlur by animateFloatAsState(
        targetValue = if (dropdownOpen) 10f else 0f,
        animationSpec = if (dropdownOpen) tween(300, easing = DropdownEase) else snap(),
        label = "cardBehindBlur",
    )
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
            .background(if (solid && c.isLight) Color(0xFFF8FAFC) else c.cardBg, shape)
            .border(1.dp, c.cardBorder, shape),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    val r = behindBlur * density
                    renderEffect = if (android.os.Build.VERSION.SDK_INT >= 31 && r > 0.5f) BlurEffect(r, r, TileMode.Clamp) else null
                }
                .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(padding),
            content = content,
        )
        overlay()
    }
}
