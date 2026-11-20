package com.myapp.app.ui.popup

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import com.myapp.app.ui.AppActions

/** Connects the buttons of the screens (Home, + button...) to the popups built in this step. */
fun installPopupHooks() {
    com.myapp.app.data.AppStore.redrawGate = { Popups.anyVisible }
    installDatePickerHooks()
    installDebtPopupHooks()
    com.myapp.app.ui.screens.installFundDetailsHooks()
    installDebtPaymentHooks()
    installDebtHistoryHooks()
    installEditFundHooks()
    installStatsHooks()
    installLockHooks()
    installNoteHooks()
    installReminderHooks()
    AppActions.openCustomSpend = { AddExpense.open(null) }
    AppActions.openTransferFund = { TransferFund.open(null) }
    AppActions.openAddFund = { AddFund.open() }
}

/** Every popup layer, lowest first (dialogs are always on top). Call once, inside the root Box. */
@Composable
fun AllPopups() {
    // An alert / confirm blurs the popup that is open behind it, like the screen is blurred behind a popup.
    val dialogOn = Popups.alert != null || Popups.confirm != null
    val underBlur by animateFloatAsState(
        targetValue = if (dialogOn) 20f else 0f,
        animationSpec = tween(if (dialogOn) 200 else 70, easing = LinearEasing),
        label = "underDialogBlur",
    )
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val r = underBlur * density
                renderEffect = if (android.os.Build.VERSION.SDK_INT >= 31 && r > 0.5f) BlurEffect(r, r, TileMode.Clamp) else null
            },
    ) {
        AddExpensePopup()
        AddMoneyPopup()
        TransferFundPopup()
        EditTransactionPopup()
        EditTransferPopup()
        AddFundPopup()
        AddCategoryPopup()
        FilterPickerPopup()
        TimeCalendarPopup()
        UnifiedDatePickerPopup()
        ReminderPopup()
        AddDebtPopup()
        DebtPaymentPopup()
        DebtHistoryPopup()
        EditFundPopup()
        AllCategoriesSheetOverlay()
        AllCatMonthPickerPopup()
        AvatarPickerPopup()
        LockTypeChooserPopup()
        NoteModalPopup()
        DueDateCalendarPopup()
        TimePickerPopup()
    }
    DialogsHost()
}
