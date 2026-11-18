package com.myapp.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class Tab { Home, Logs, Stats, Profile }

/**
 * Screen-level UI state that the old app kept in globals (current tab, funds panel open,
 * Home chart range, keyboard...). Nothing here is saved - it resets on every launch, like before.
 */
object AppUi {
    var tab by mutableStateOf(Tab.Home)
        private set

    /** The funds switcher on Home is expanded (also hides the bottom nav and the category card). */
    var fundsPanelOpen by mutableStateOf(false)
        private set

    /** 'week' or 'month' - the Category Breakdown range on Home. */
    var homeChartRange by mutableStateOf("week")
        private set

    /** On-screen keyboard is up (bottom nav hides, same as the old bottomNavHideReasons.keyboard). */
    var keyboardOpen by mutableStateOf(false)

    /** The fund whose Fund Details screen is open (null = none). Add Money / Transfer use it. */
    var currentDetailsFund by mutableStateOf<String?>(null)

    var logsSubtab by mutableStateOf("transactions")   // transactions | debts | receivables
    var statsSubtab by mutableStateOf("spending")      // spending | debts

    /** Month picked with the calendar button on Stats (statsMonthOverride) - memory only, resets on a fresh launch. */
    var statsMonthOverride by mutableStateOf<com.myapp.app.ui.screens.StatsMonth?>(null)

    /** Set by the Logs screen (later step): Debts/Receivables "Settled" tab hides the + button. */
    var addButtonForceHidden by mutableStateOf(false)

    val bottomNavHidden: Boolean get() = fundsPanelOpen || keyboardOpen

    /** The old updateAddButtonVisibility(). */
    val addButtonHidden: Boolean
        get() = tab == Tab.Stats || tab == Tab.Profile || (
            tab == Tab.Logs &&
                (logsSubtab == "debts" || logsSubtab == "receivables") &&
                com.myapp.app.ui.screens.DebtUi.of(logsSubtab).status == "settled"
            )

    /** openAccountDetails(fundName) */
    fun openFundDetails(fund: String) {
        currentDetailsFund = fund
        com.myapp.app.ui.popup.FundHistoryFilter.mode = null
        com.myapp.app.ui.popup.FundHistoryFilter.value = null
    }

    /** closeAccountDetails() */
    fun closeFundDetails() {
        currentDetailsFund = null
    }

    fun switchTab(target: Tab) {
        // The fund a Debts/Receivables form was opened for only lives while Logs stays on screen.
        if (target != Tab.Logs) com.myapp.app.ui.popup.AddDebt.fundContext = null
        // Leaving Home with the funds panel open closes it (and brings the nav bar back).
        if (target != Tab.Home && fundsPanelOpen) fundsPanelOpen = false
        tab = target
    }

    /** The old switchLogsSubtab(name). Each subtab keeps its own filters until the app restarts. */
    fun switchLogsSubtab(name: String) {
        logsSubtab = name
    }

    fun toggleFundsPanel() {
        fundsPanelOpen = !fundsPanelOpen
    }

    fun setHomeChartRange(mode: String) {
        homeChartRange = if (mode == "month") "month" else "week"
    }

    /** The old openStatsDebtsShortcut(). */
    fun openStatsDebtsShortcut() {
        switchTab(Tab.Stats)
        statsSubtab = "debts"
    }

    /** The old handleGlobalAddButton(). */
    fun handleGlobalAddButton() {
        if (tab == Tab.Logs && logsSubtab == "debts") {
            AppActions.openAddDebt("debt")
        } else if (tab == Tab.Logs && logsSubtab == "receivables") {
            AppActions.openAddDebt("receivable")
        } else {
            AppActions.openCustomSpend()
        }
    }
}

/**
 * Entry points for dialogs / screens that are built in later steps. They are plain no-ops until
 * those steps replace them, so Home already calls the right place.
 */
object AppActions {
    var openTransferFund: () -> Unit = {}
    var openAddFund: () -> Unit = {}
    var openAccountDetails: (String) -> Unit = {}
    var openNotificationsPanel: () -> Unit = {}
    var openCustomSpend: () -> Unit = {}
    var openAddDebt: (String) -> Unit = {}
    var openHomeCategoryDetails: () -> Unit = {}

    /** openSectionNoteModal(section) - built with the Profile/notes work (Step 9). */
    var openSectionNote: (String) -> Unit = {}

    /** The calendar button in Logs (openDateCalendarIcon('transactions')) - Step 6, part 2. */
    var openLogsDateMenu: () -> Unit = {}

    /** openEditFundModal(fund) - the "Fund Management" popup. */
    var openEditFund: (String) -> Unit = {}

    /** openFundNoteModal() - the note of the open Fund Details screen (notes work, Step 9). */
    var openFundNote: () -> Unit = {}
}
