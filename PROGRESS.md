# Tracker - Kotlin conversion progress

| # | Step | Weight | Status |
|---|------|--------|--------|
| 1 | Project setup, GitHub workflow, theme/colors/fonts, icons, splash | 8%  | DONE |
| 2 | Data model, storage, migration, backup/import                    | 12% | DONE |
| 3 | Utilities (money format, Bangla digits, dates, translations)      | 10% | DONE |
| 4 | Home screen, fund list, chart                                     | 14% | DONE |
| 5 | Add/edit transaction, transfer, categories                        | 14% | DONE |
| 6 | Logs screen, filters, search, calendar & wheel pickers            | 14% | DONE in code, NOT yet compiled |
| 7 | Debts / receivables, payments, history, Fund Details, Edit Fund   | 10% | DONE in code, NOT yet compiled |
| 8 | Stats, analytics, Category Details (All Categories sheet)         | 8%  | DONE in code, NOT yet compiled |
| 9 | Profile, locks (biometric/PIN/pattern), reminders, onboarding, notes, backup UI | 8%  | DONE in code, NOT yet compiled |
| 10| Final cross-check and cleanup                                     | 2%  | DONE (static checks; compile = your GitHub build) |

ALL 10 steps are written (100% of the code). NOTHING from Step 6 onward has been compiled yet - the first GitHub build is the first real compile.

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

## Step 9 notes (part 1 - written, NOT yet compiled)
Done in code:
- `ui/screens/ProfileScreen.kt` : Profile tab - title, name box (scattered faint icons, avatar button, editable name with width-fitting underline, saved on Done / focus loss), Day Mode switch (live theme), Language switch (En <-> Bangla), Screen Lock Type row (icon + label of the current lock), Backup & Restore buttons, developer credit with the 4 social links, copyright line.
- `ui/popup/BackupUi.kt` : Export (confirm "Save File / Share", Documents via MediaStore or share sheet via FileProvider, human-readable file name in English / Bangla, size check, the same alerts) and Import (file picker -> reading / checking busy cards -> every error message of the old app -> restore summary confirm -> all-or-nothing restore through `Backup.applyFull`, older transactions-only files too); busy card (`BusyOverlay`); Avatar picker popup (18 avatars, 4 per row, 3 rows per page).
- `util/Avatars.kt`, FileProvider (manifest + `res/xml/file_paths.xml`), `BackupUi.attach(activity)` in MainActivity.
Still to do in Step 9: lock type chooser + PIN / pattern / fingerprint setup + the lock screen (`AppActions.openLockTypeChooser` is empty), reminder settings + notifications / alarms (debt due, daily), welcome / onboarding, fund note + section note modals (`openFundNote`, `openSectionNote`).

## Step 9 notes (part 2 - lock system, written, NOT yet compiled)
- `ui/popup/LockUi.kt` : `LockState` (PIN pad, pattern pad, biometric lock screen, startup lock, `verifyCurrentLock`, `chooseLockType` = handleLockTypeChoice), `BiometricHelper` (androidx BiometricPrompt, weak+strong biometrics, negative button "Cancel"), the three screens (`PinPadScreen`, `PatternPadScreen`, `BiometricLockScreen`, drawn by `LockHost` above everything), and the Screen Lock Type chooser popup (Off / Fingerprint / PIN / Pattern).
  PIN: 4 digits, set -> confirm (mismatch = red dots + shake, restart), verify (wrong = red dots + shake 450 ms). Pattern: 3x3 dots, >= 4 dots, drag with live line, set -> confirm -> verify, shake on error, touch blocked during the shake. Both stored only as SHA-256 (PIN "1234", pattern "0-1-2-5").
  Changing / turning off a lock first asks for the current lock; turning fingerprint ON first proves a scan works.
  The old app locks only on a fresh launch (not on every return from background) - same here.
- `ui/components/LockDecor.kt` : the 13 scattered finance icons (shared with the future Welcome screen); lock screens use a pure-black background in dark mode, a grey -> light-red gradient in day mode; the status bar goes pure black while a lock is up.
- build: `androidx.biometric` + `fragment-ktx` added, `MainActivity` now extends `FragmentActivity` (needed by BiometricPrompt).
- NOTE: the startup lock is shown only when `onboardingComplete` is true. The Welcome / onboarding screen (which sets it) is still to be written (Step 9 part 3), so until then a lock set in Profile will not appear at the next launch.
Still to do in Step 9: reminders (settings + notifications / alarms), Welcome / onboarding, fund note + section note modals.

## Step 9 notes (part 3 - Welcome screen + notes, written, NOT yet compiled)
- `ui/popup/OnboardingAndNotes.kt` :
  Welcome / "Get Started" screen (shown while `onboardingComplete` is false = first launch): big title block, "Your Profile" card with the name field (red underline on focus), name is REQUIRED - an empty name shakes the row (500 ms), turns the underline + placeholder red and gives a short haptic buzz; "Get Started" saves the name and enters the app. Same finance-icon backdrop as the lock screens, pure-black status bar.
  Note popup for Fund Details notes and the Logs / Debts / Receivables section notes: label "<name> related", named placeholder, box exactly 6 lines tall, scroll settles on a whole line with a top/bottom fade while scrolling, eraser button (with confirm) only for an existing note, Cancel / Save. The small red dot on the note buttons now shows when a note exists.
- Correction: the diagonal debt arrows (arrow-up-right / arrow-down-left) are NOT blank in the old app - its CSS draws the plain up / down arrow turned 45 degrees. `FaIcon` now does the same (this also fixes the Logs cards for debts and the Fund Details chips).
- Since the Welcome screen now exists, the startup lock (Step 9 part 2) works from the second launch on.
Still to do in Step 9: reminders (settings popup + notifications / alarms: debt due + daily), then Step 10.

## Step 9 notes (part 4 - reminders, written, NOT yet compiled)
- `reminder/Reminders.kt` : notification channel + `notifyNow`, alarms through AlarmManager (exact + allow-while-idle when the device allows, else inexact), two schedules:
  * four daily expense reminders (Morning 10:00 / Afternoon 14:00 / Evening 20:00 / Night 23:30 by default, ids 9101-9104) - each alarm re-books itself for the next day when it fires;
  * one reminder per pending debt / receivable with a due date + time (id 40000 + id % 500000): "pay / collect from <person>" at that moment. A moment that passed less than a day ago is announced right away, once (never repeated); older ones are skipped, a changed due date gets a fresh chance.
  `ReminderReceiver` posts the notification, `BootReceiver` re-registers everything after a reboot / app update. Both are registered in the manifest.
- `ui/popup/ReminderPopup.kt` : the bell panel "Expense Reminders" (opened by the Home bell): red "Allow notifications" banner, amber "Enable exact alarms" banner (opens the system screen; re-checked when you come back), one row per reminder with a 12-hour time button (opens the shared time picker "Select <Morning> Time") and a switch, Close / Save (Save stores + re-registers the alarms).
- Alarms are registered at every launch (the notification permission is asked then too, like the old app), after Save, and ~0.8 s after any data change (so a new / edited / settled / deleted debt updates its reminder).
- Not ported on purpose: the old browser-only `Notification` API fallback and the 20-second foreground polling - the native alarms replace them.
Step 9 is complete in code. Next: Step 10 (final checklist, remaining gaps, build notes).

---
## STEP 10 - final cross-check (read this before the first build)

### What was checked without a compiler
`tools/static-audit/` holds small Python scripts (run from the project root: `python3 tools/static-audit/audit.py`, `audit3.py` ... `audit7.py`; known harmless findings: the `R` import, `isBangla` / `tween` / `authenticate` name look-alikes, dynamic translation keys such as `fund_type_`, `avatar_`). They check all 75 Kotlin files for:
- every `import com.myapp...` points at something that exists; duplicate top-level names; brace / bracket balance
- every `tr()/trf()` translation key exists in `i18n_bn.json`; every `fa-...` icon exists in the glyph map
- every `Popups.xxx` flag and `AppActions.xxx` hook exists and every hook is assigned somewhere
- named arguments exist on the called function; required arguments are not missing; no trailing lambda on a non-function last parameter; argument counts
- members used on `AppState / Debt / Transaction / DebtPayment`; `return@label` targets
- missing androidx / compose imports for ~100 commonly used symbols
Real bugs found and fixed by these checks: WheelPicker's trailing lambda (parameter order), `.align()` outside a Box scope on the lock screens, a missing `arrow-trend-down` icon, wrong lock-type icons, URL strings eating a brace in the checker itself (false alarm).
What this CANNOT catch: type mismatches, wrong overloads, nullability / smart-cast errors, Compose-scope mistakes. Expect a handful of compile errors on the first build - paste the Gradle log and they get fixed one by one.

### Where compile errors are most likely (newest / riskiest code)
1. `ui/popup/LockUi.kt` (BiometricPrompt, pointer input), `reminder/Reminders.kt` (alarm APIs), `ui/popup/BackupUi.kt` (MediaStore / FileProvider), `ui/components/WheelPicker.kt` (custom drag + inertia), `ui/screens/FundDetailsScreen.kt` (FitRow layout), `ui/screens/StatsScreen.kt` (pager).
2. Dependencies added in Step 9: `androidx.biometric:biometric:1.1.0`, `androidx.fragment:fragment-ktx:1.8.3`. `MainActivity` is now a `FragmentActivity`.
3. `LocalLifecycleOwner` is imported from `androidx.compose.ui.platform` (Compose BOM 2024.09). If a newer BOM is ever used it moves to `androidx.lifecycle.compose`.

### Every screen / popup of the old app and where it lives now
Screens: Home (HomeScreen) - Logs (LogsScreen + DebtsPane) - Stats (StatsScreen) - Profile (ProfileScreen) - Fund Details (FundDetailsScreen) - Welcome (OnboardingScreen) - PIN / Pattern / Fingerprint lock (LockUi).
Popups: add expense / add money / transfer / edit transaction / edit transfer / add fund / edit fund (+ delete confirm) / add category (Step 5 + EditFundPopup) - filter picker, time calendar, wheel date picker (LogsState, FilterPickerPopup, DatePickers) - add/edit debt, payment, history, due-date calendar, time picker (AddDebtPopup, DebtPaymentPopup, DebtHistoryPopup, TimeAndDuePickers) - avatar picker, lock type, fund / section note, reminders panel, all-categories sheet + month picker, custom alert / confirm, busy card.
Not ported because they can never be opened in the old app (no code ever shows them): `popup-logs-history-menu`, `popup-fund-history-menu` (+ their month wheels) and `popup-date-range`.

### Known small differences from the old app (all minor)
- Scroll-fade at the bottom of the card lists is a fixed 40 dp (the old code measured it from the last card).
- In fund-locked forms the fund button keeps its chevron (old: lock icon).
- After a Fund Details chip jump the Debts list does not auto-scroll to the card (the old app only scrolled, it had no highlight).
- Debts tab: the old "clear history" bin and time pill sit in a `hidden` div - never visible - so they are not drawn.
- Reminders: the browser-only `Notification` fallback and the 20 s polling are replaced by native alarms.
- Export / Import use Android's own file picker / Documents folder / share sheet instead of the Capacitor plugins.

### Manual test script for the first working build (about 15 minutes)
1. First launch -> Welcome screen: empty name shakes; type a name -> Get Started.
2. Home: add a fund, add money, add an expense, transfer between funds; open Fund Details (back button), edit fund (rename -> history keeps), delete a fund.
3. Logs > Transactions: search (category / note / amount), sort / time / price filters, calendar (grid and month-year wheel), clear history.
4. Logs > Debts and Receivables: add with and without a fund, with own-fund deal, due date + time; partial payment; settle; Settled tab + calendar; key star; edit (amount locked after 7 days); delete.
5. Stats: Spending (month button, See all sheet, pager), Debts & Receivables tab.
6. Profile: avatar, name, day mode, language, export -> import the same file, lock PIN / pattern / fingerprint (close app, reopen -> lock shows).
7. Bell: set a reminder two minutes ahead -> notification arrives (also with the app closed); a debt due today with a time.
8. Notes: fund note, Logs / Debts / Receivables section notes (dot appears).

---
## NEW FEATURE - Incoming Overview (Home, written, NOT yet compiled)
- Home: small green "Incoming Overview" / "আয়ের সারসংক্ষেপ" pill at the right end of the "spent from this fund" line (`HomeScreen.kt`, `IncomingOverviewButton`).
- `ui/popup/IncomingOverview.kt`: bottom sheet - filter chips (This Month / Last Month / All Time), Total Received card (+ "This month" pill when another filter is on), By Fund summary, combined list (category, from whom, fund, date/time, amount).
- Counted as incoming: Money Added, Fund Transfer (in), Receivable Collected, Money Borrowed (all `add_money` / `transfer_in` lines, history-hidden lines skipped).
- Wiring: `Popups.incomingOverview`, `AppActions.openIncomingOverview`, `installIncomingOverviewHooks()`, 13 new keys `incoming_*` in `i18n_bn.json`.
