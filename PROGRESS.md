# Tracker - Kotlin conversion progress

| # | Step | Weight | Status |
|---|------|--------|--------|
| 1 | Project setup, GitHub workflow, theme/colors/fonts, icons, splash | 8%  | DONE |
| 2 | Data model, storage, migration, backup/import                    | 12% | DONE |
| 3 | Utilities (money format, Bangla digits, dates, translations)      | 10% | DONE |
| 4 | Home screen, fund list, chart                                     | 14% | DONE |
| 5 | Add/edit transaction, transfer, categories                        | 14% | DONE |
| 6 | Logs screen, filters, search, calendar & wheel pickers            | 14% | DONE in code, NOT yet compiled (Debts/Receivables panes + section note come in 7 / 9) |
| 7 | Debts / receivables, payments, history, Fund Details, Edit Fund   | 10% | DONE in code, NOT yet compiled |
| 8 | Stats, analytics, Category Details (All Categories sheet)         | 8%  | DONE in code, NOT yet compiled |
| 9 | Profile, locks (biometric/PIN/pattern), reminders, onboarding     | 8%  | todo |
| 10| Final cross-check and cleanup                                     | 2%  | todo |

Written: 82% (Steps 1-8)   Remaining: 18% (Steps 9-10)   -   Steps 6-8 are NOT compiled yet

## Notes
- appId `com.myapp.app` and the same keystore secrets (KEYSTORE_BASE64 / KEYSTORE_PASSWORD) => installs over the old app.
- versionCode = 1000 + GitHub run number (always higher than every old build).
- System font scaling is locked to 100% (same as the old app) so the UI is identical on every device.
- Old web file kept out of this project on purpose; the Kotlin code is a line-by-line port.

## Step 2 notes
- `data/Models.kt`   : AppState / Transaction / Debt / DebtPayment / FundMeta ... (same field names as the old appState; unknown fields are kept)
- `data/AppStore.kt` : the old `appState` + `saveState()` + `initApp()` (`AppStore.state`, `saveState()`, `commit()`), safe atomic file save, 6-year transaction and 15-year settled-debt auto-purge
- `data/Backup.kt`   : export JSON (type smart_saver_elite_backup, version 2) and the full import check + all-or-nothing restore (reads old web-app backups)
- `data/ReminderSettings.kt`, `data/JsDate.kt` (reads the old ISO dates), `data/JsonUtil.kt`
- To move your old data: in the OLD app use Export, in the new app use Import (Profile screen, step 9).

## Step 3 notes (package com.myapp.app.util)
- `I18n.kt`      : tr(key, english) / trf(key, english, "name" to x) - same call style as the old code; Bangla table = assets/i18n_bn.json (475 entries, copied 1:1)
- `Digits.kt`    : toBnDigits / fromBnDigits / localizeDigits / amount-search matching
- `DateText.kt`  : fmtDate / fmtTime / bnTimePeriod / isDateFilterToday, BN month + weekday names
- `MoneyFormat.kt`: fmtMoney (incl. Crore compact), safeToFixed (JS toFixed rounding), formatAmountTyping, parseAmountInput
- `AppNames.kt`  : fundDisplayName, resolveCanonicalFundName, debtPaymentNoteText, debtNoteOtherFund
- `ui/components/InputFormat.kt`: live amount box + no-leading-digit name box

## Step 4 notes
- `ui/screens/HomeScreen.kt` : header (name, today badge, bell), balance card + funds panel, debts overview card, category breakdown card
- `ui/screens/FundList.kt`   : fund cards (tap = switch, arrow = details, hold 0.4 s + drag = reorder, pages of 4)
- `ui/components/DailySpendChart.kt` : the daily bar chart (week / month, tap a day for the tooltip)
- `ui/components/BottomNav.kt` : floating nav bar + round + button; `ui/AppUi.kt` : tab / panel state; `AppActions` = hooks for dialogs built in steps 5-9
- `util/FundTypes.kt`, `util/Categories.kt`, `util/Finance.kt`: fund types, category labels/icons/grouping, balance maths

## Step 5 notes (everything below is DONE)
- `ui/popup/PopupLayer.kt` : popup frame (dim, 0.27 s fade, card scale/slide), `Popups` flags, `GlassCard`
- `ui/popup/Dialogs.kt`    : Alert + Confirm (`AppDialogs.alert / confirm`, answer comes to a callback)
- `ui/popup/Forms.kt`      : text box + Cancel/Save/Red buttons used in every popup
- `ui/popup/CategoryChips.kt` : category slider (5 chips per width, snap, hold+drag re-order, filter-while-typing)
- `ui/popup/AddExpensePopup.kt` : Add Transaction (+ `saveTransaction` logic)
- `ui/popup/AddCategoryPopup.kt`: New Category (146 icons, my categories list, delete)
- `ui/popup/FundDropdown.kt`, `FundGrids.kt` : fund picker list, fund type & colour grids
- `ui/popup/MoneyPopups.kt` : Add Money (+edit) and Transfer Fund
- `ui/popup/EditPopups.kt`  : Edit Transaction and Edit Transfer (1-day amount lock, balance checks)
- `ui/popup/TxActions.kt`   : delete transaction / transfer / add-money / debt-payment line rules, lock rules
- `ui/popup/AddFundPopup.kt`: New Fund
- Hooks now live: Home "+" button, Transfer, Add New Fund. Still no-ops until later steps: fund details, bell panel, category details.

## Known small differences (to re-check in the final test)
- Frosted blur: the whole screen behind a popup is blurred only on Android 12+ (older phones: dim only).
- The fund list that opens inside a popup dims the card, but does not blur it.
- Android back button / keyboard-hides-nav-bar are not wired yet (Step 10 cross-check).

## Fixes made while resuming
- Removed a duplicate `util/Format.kt` that redeclared functions from Digits.kt / MoneyFormat.kt (would not compile).
- Home list scrolling now snaps one card at a time (as the old fitListToVisibleCount did), not 4 at a time.
- `docs/i18n_english_reference.json` = every English text by key (reference only).

## Step 6 notes (part 1 - written in this session, build NOT yet tested)
Done in code:
- `ui/screens/LogsScreen.kt`  : title, Transactions/Debts/Receivables switch, Transactions search box (+ clear x, amount live-format), magnifier picker button, sort/time/price chips with red dots, calendar + clear-history buttons, 5-card snapping list, transaction card (icon colours, transfer two-colour arrows, mirrored Receivable-Collected icon, note/badge rows, search highlight, full-match tint, edit button rules)
- `ui/screens/LogsState.kt`   : search text / search field / staged filter state, `logsFilteredTransactions` (exact port of renderLogsList filtering + sorting), `getTxNoteText`, `highlightMatch`, clear-history target rows
- `ui/popup/FilterPickerPopup.kt`: shared Sort/Time/Price/search-field picker (stage -> Apply, Clear Filter, Cancel)
- Wired: `TrackerRoot` shows LogsScreen, `Popups.filterPicker`, `AllPopups`, `AppUi.switchLogsSubtab`
Still TODO in Step 6:
- Calendar button ("Filter History" menu: Select a Month wheel / Select a Date calendar, Clear) -> `AppActions.openLogsDateMenu` is an empty hook
- Month/year wheel pickers and the unified date wheel picker
- Section note button -> `AppActions.openSectionNote` (empty hook until Step 9)
- Debts / Receivables panes (Step 7)
- Bangla/English Logs scroll-fade overlay at the list bottom
- Compile check on GitHub Actions (no Gradle in the sandbox where this was written)

## Step 6 notes (part 2)
- `ui/components/WheelPicker.kt` : reusable wheel (own drag, capped inertia, 380 ms settle, tap glide, loop, fade mask) + `PulseDot` (animate-pulse). Steps 7 & 9 reuse it (due date, reminder time).
- `ui/popup/DatePickers.kt` : grid calendar (dots on days with entries, Friday red, today tint, min-year limit, Clear Filter) <-> wheel date picker (Date/Month/Year, "All" = whole month, red rows for days with entries). One implementation for transactions / debts / receivables / fundHistory via `DatePickerSources` hooks (debt hooks are filled in Step 7).
- Logs calendar button now opens the grid calendar (as the original does).
- `ModalButton` got an optional `textColor`.

## Step 7 notes (part 1 - written, NOT yet compiled)
Done in code:
- `ui/screens/DebtState.kt`  : per-tab session state (search, search field, Pending/Settled, Priority + Amount sort per status, date filter per status), exact port of `getFilteredDebtEntries`, totals, key-person toggle, 12-hour helper, `DebtActions` hooks
- `ui/screens/DebtsPane.kt`  : black summary card (+ calendar button on Settled), search row, Pending/Settled/Priority/Amount chips, 2-card snapping list, full debt/receivable card (progress bar colour, due + reminder line, key star, pay/receive pill, trash on settled, search highlight, full-match tint)
- `ui/components/ScrollFade.kt` : the bottom scroll-fade; also used by the Transactions list now
- Filter picker: Priority (toggle, Key Person / Urgent Due), Amount, and the Person/Note/Amount search-field picker for Debts & Receivables
- Date calendar / wheel hooks for debts + receivables connected; "+" button hides on the Settled tab
Hooks still empty (next parts of Step 7): `DebtActions.openHistory / openEdit / openPayment / delete`, `AppActions.openAddDebt`
Not rendered on purpose: the old "clear history" trash and time pill of the Debts tab (their parent div is `hidden` in the HTML, so they never show)
Known approximation: scroll-fade height is a fixed 40 dp (HTML sizes it from the last visible card)

## Step 7 notes (part 2 - written, NOT yet compiled)
Done in code:
- `ui/popup/AddDebtPopup.kt` : Add / Edit Debt & Receivable (person, amount with 7-day lock + hint, note, due date, reminder time, Clear Due, "lend/borrow from a fund" toggle + fund list, own-fund deal section, trash-in-header for edit) and the full `saveDebtEntry` port (fund balances, history lines incl. own-fund counter line, edit adjustments, settled <-> pending re-check)
- `ui/popup/DebtPaymentPopup.kt` : Record Payment Made / Received (Pay remaining full amount, optional fund, silent fund for fund-linked entries, own-fund repay) = `submitDebtPayment`
- `ui/popup/DebtHistoryPopup.kt` : name, progress, paid/remaining, payment list (3 rows per page, fund badge) + `deleteDebtEntry` (confirm, undo unpaid part on funds, remove linked lines)
- `ui/popup/TimeAndDuePickers.kt` : time picker (hour/minute wheels + AM/PM toggle; reusable for Step 9 reminders via `TimePicker.open(time24, title) { }`) and the Due Date calendar (no past days)
- `ui/components/AppSwitch.kt` : the old `.switch` toggle
- `FundButtonVariant.Debt` for the fund buttons of these popups
- Hooks now connected: `DebtActions.openHistory/openEdit/openPayment/delete/deleteThen`, `AppActions.openAddDebt`
Fund Details + Edit Fund: see "Step 7 notes (part 3)" below
Small differences: in locked-fund mode the fund button shows a chevron (old app showed a lock icon)

## Step 7 notes (part 3 - written, NOT yet compiled)
Done in code:
- `ui/screens/FundDetailsScreen.kt` : full-screen Fund Details (back / title / edit pen, black balance card with the red/green "debt / receivable" chips that shrink-to-fit, add-money "+", Total Spent (follows the history filter) + Transfer Fund box, Transaction History with note + calendar buttons (dots), 4-card snapping list with fade, delete/edit buttons per the old rules). Chips jump to Logs > Debts/Receivables and pass the fund to the forms (`AddDebt.fundContext`). System back closes it.
- `ui/popup/EditFundPopup.kt` : "Fund Management" popup (rename with full cascade - transactions, linked funds, debts, payments, active/open fund; renamed fund moves to the end of the order like the old object keys; type + colour grids; locked balance; delete + "Are you sure?" confirm). This popup was missing from the original step plan - added here.
- `TxCard` of Logs is now shared (`actions` slot) so Fund Details uses the very same card.
- Fund-history calendar dots wired (`DatePickerSources.fundHistoryDates`).
Hooks still empty: `AppActions.openFundNote` (fund note modal) and `AppActions.openSectionNote` -> Step 9 notes work.
Not ported: the "scroll to the highlighted card" after a chip jump (debtJumpHighlightIds only scrolled; no visual highlight exists in the old card)

## Step 8 notes (written, NOT yet compiled)
- `ui/screens/StatsScreen.kt` : header with the month button (Spending tab only), Spending | Debts & Receivables switch.
  Spending: empty state, Overview (total / daily average / count, daily average uses days elapsed for the current month), Top Spending Category pager (top 3, dots, "See all"), Spending Insights (biggest expense, most active day, average per transaction, categories used).
  Debts & Receivables: Net Position, Settlement Progress, Top Partners (2 rows per page, snapping).
  The month follows the Logs time filter (custom month / the month of a picked date) unless a month was picked with the Stats calendar button.
- `ui/popup/StatsPopups.kt` : "All Categories Summary" bottom sheet (3 rows per page) = the old Category Details, reached from "See all" and from Home > Category Breakdown; month picker (Month + Year wheels, Reset Filter).
- The old bar chart (`categoryBarChart`) has no canvas in the HTML, so it never drew - not ported on purpose.
- `Modifier.pullUp(dp)` reproduces CSS negative top margins.
