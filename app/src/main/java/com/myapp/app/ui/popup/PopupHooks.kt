package com.myapp.app.ui.popup

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
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
    installIncomingOverviewHooks()
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
    // A popup opened on top of another one blurs the popup beneath it (done by PopupLayer itself, dialogs included).
    Box(Modifier.fillMaxSize()) {
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
        IncomingOverviewSheet()
        AllCatMonthPickerPopup()
        AvatarPickerPopup()
        LockTypeChooserPopup()
        NoteModalPopup()
        DueDateCalendarPopup()
        TimePickerPopup()
    }
    DialogsHost()
}
