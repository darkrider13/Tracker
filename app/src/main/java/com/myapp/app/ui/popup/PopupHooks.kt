package com.myapp.app.ui.popup

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
@androidx.compose.runtime.Composable
fun AllPopups() {
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
    DialogsHost()
}
