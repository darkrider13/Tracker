package com.myapp.app.data

import org.json.JSONObject

/**
 * The backup as an Excel (.xlsx) file: one sheet per kind of data, one header row, one row per item.
 * Every field of the JSON backup has a column, and unknown fields travel in the "Extra" column as JSON
 * text, so Export -> Import through Excel brings back the same data.
 */
object ExcelBackup {
    const val SHEET_SETTINGS = "Settings"
    const val SHEET_FUNDS = "Funds"
    const val SHEET_TRANSACTIONS = "Transactions"
    const val SHEET_DEBTS = "Debts"
    const val SHEET_PAYMENTS = "Debt Payments"
    const val SHEET_CATEGORIES = "Categories"
    const val SHEET_NOTES = "Notes"

    // ---- Settings sheet keys (column A)
    const val K_TYPE = "type"
    const val K_VERSION = "version"
    const val K_EXPORTED_AT = "exportedAt"
    const val K_THEME = "theme"
    const val K_LANGUAGE = "language"
    const val K_USERNAME = "username"
    const val K_SAVINGS_TARGET = "savingsTarget"
    const val K_ONBOARDING = "onboardingComplete"
    const val K_ACTIVE_FUND = "activeFund"
    const val K_AVATAR = "profileAvatar"
    const val K_BIOMETRIC = "biometricLockEnabled"
    const val K_LOCK_TYPE = "lockType"
    const val K_LOCK_PIN = "lockPinHash"
    const val K_LOCK_PATTERN = "lockPatternHash"
    const val K_RANGE_START = "dashboardRangeStart"
    const val K_RANGE_END = "dashboardRangeEnd"
    const val K_FILTER_SORT = "filters.sort"
    const val K_FILTER_TIME = "filters.time"
    const val K_FILTER_PRICE = "filters.price"
    const val K_FILTER_DATE = "filters.customDate"
    const val K_FILTER_MONTH = "filters.customMonth"
    const val K_FILTER_EXTRA = "filters.extra"
    const val K_CATEGORY_ORDER = "categoryOrder"
    const val K_REMINDERS = "reminders"
    const val K_EXTRA = "extra"

    /** Notes sheet: "Kind" column values. */
    const val NOTE_FUND = "fund"
    const val NOTE_SECTION = "section"

    private fun b(v: Boolean?): String? = v?.toString()

    private fun extraText(o: JSONObject): String? = if (o.length() > 0) o.toString() else null

    /** The whole backup as the bytes of an .xlsx file. */
    fun build(
        state: AppState,
        reminders: ReminderSettings,
        theme: String,
        language: String,
    ): ByteArray {
        val sheets = mutableListOf<XlsxWriter.Sheet>()

        // ---------------------------------------------------------------- Settings
        val f = state.filters
        val categoryOrder = org.json.JSONArray().also { arr -> state.categoryOrder.forEach { arr.put(it) } }
        val settings = listOf<Pair<String, Any?>>(
            K_TYPE to Backup.TYPE,
            K_VERSION to Backup.VERSION,
            K_EXPORTED_AT to JsDate.nowIso(),
            K_THEME to theme,
            K_LANGUAGE to language,
            K_USERNAME to state.username,
            K_SAVINGS_TARGET to safeDouble(state.savingsTarget),
            K_ONBOARDING to state.onboardingComplete.toString(),
            K_ACTIVE_FUND to state.activeFund,
            K_AVATAR to state.profileAvatar,
            K_BIOMETRIC to state.biometricLockEnabled.toString(),
            K_LOCK_TYPE to state.lockType,
            K_LOCK_PIN to state.lockPinHash,
            K_LOCK_PATTERN to state.lockPatternHash,
            K_RANGE_START to state.dashboardRangeStart,
            K_RANGE_END to state.dashboardRangeEnd,
            K_FILTER_SORT to f.sort,
            K_FILTER_TIME to f.time,
            K_FILTER_PRICE to f.price,
            K_FILTER_DATE to f.customDate,
            K_FILTER_MONTH to f.customMonth,
            K_FILTER_EXTRA to extraText(f.extra),
            K_CATEGORY_ORDER to categoryOrder.toString(),
            K_REMINDERS to reminders.toJson().toString(),
            K_EXTRA to extraText(state.extra),
        )
        sheets.add(
            XlsxWriter.Sheet(
                SHEET_SETTINGS, listOf("Key", "Value"),
                settings.map { listOf<Any?>(it.first, it.second) },
                listOf(26, 60),
            ),
        )

        // ---------------------------------------------------------------- Funds
        val fundRows = state.funds.map { (name, balance) ->
            val m = state.fundMeta[name]
            listOf<Any?>(name, safeDouble(balance), m?.icon, m?.color, m?.type, m?.let { extraText(it.extra) })
        }
        sheets.add(
            XlsxWriter.Sheet(
                SHEET_FUNDS, listOf("Fund", "Balance", "Icon", "Color", "Type", "Extra"),
                fundRows, listOf(24, 14, 18, 14, 16, 24),
            ),
        )

        // ---------------------------------------------------------------- Transactions
        val txRows = state.transactions.map { t ->
            listOf<Any?>(
                t.id, t.type, t.fund, t.category, t.description, safeDouble(t.amount), t.date,
                t.transferId, t.linkedFund, t.debtNoteKind, t.debtNotePerson,
                b(t.fromDebtPayment), t.debtEntryId, t.debtPaymentEntryId, b(t.debtCounterTx), b(t.historyHidden),
                extraText(t.extra),
            )
        }
        sheets.add(
            XlsxWriter.Sheet(
                SHEET_TRANSACTIONS,
                listOf(
                    "ID", "Type", "Fund", "Category", "Description", "Amount", "Date",
                    "Transfer ID", "Linked Fund", "Debt Note Kind", "Debt Note Person",
                    "From Debt Payment", "Debt Entry ID", "Debt Payment Entry ID", "Debt Counter Tx", "History Hidden",
                    "Extra",
                ),
                txRows, listOf(16, 14, 20, 20, 32, 12, 26, 16, 20, 16, 20, 18, 16, 20, 16, 16, 24),
            ),
        )

        // ---------------------------------------------------------------- Debts + their payments
        val debtRows = state.debts.map { d ->
            listOf<Any?>(
                d.id, d.type, d.person, safeDouble(d.amount), d.fund, d.counterFund, d.note,
                d.dueDate, d.reminderTime, d.dueReminderFired.toString(), d.createdDate,
                d.settled.toString(), d.settledDate, d.isKey.toString(), extraText(d.extra),
            )
        }
        sheets.add(
            XlsxWriter.Sheet(
                SHEET_DEBTS,
                listOf(
                    "ID", "Type", "Person", "Amount", "Fund", "Counter Fund", "Note",
                    "Due Date", "Reminder Time", "Due Reminder Fired", "Created Date",
                    "Settled", "Settled Date", "Key Person", "Extra",
                ),
                debtRows, listOf(16, 12, 22, 12, 20, 20, 32, 26, 14, 18, 26, 10, 26, 12, 24),
            ),
        )

        val paymentRows = mutableListOf<List<Any?>>()
        for (d in state.debts) {
            for (p in d.payments) {
                paymentRows.add(
                    listOf<Any?>(
                        d.id, safeDouble(p.amount), p.date, p.fund, b(p.internal), p.fromFund, p.toFund,
                        p.txId, p.txIds?.joinToString(","), extraText(p.extra),
                    ),
                )
            }
        }
        sheets.add(
            XlsxWriter.Sheet(
                SHEET_PAYMENTS,
                listOf("Debt ID", "Amount", "Date", "Fund", "Internal", "From Fund", "To Fund", "Tx ID", "Tx IDs", "Extra"),
                paymentRows, listOf(16, 12, 26, 20, 10, 20, 20, 16, 24, 24),
            ),
        )

        // ---------------------------------------------------------------- Custom categories
        val catRows = state.customCategories.map { listOf<Any?>(it.name, it.icon, extraText(it.extra)) }
        sheets.add(
            XlsxWriter.Sheet(SHEET_CATEGORIES, listOf("Name", "Icon", "Extra"), catRows, listOf(26, 22, 24)),
        )

        // ---------------------------------------------------------------- Notes (fund notes + Logs section notes)
        val noteRows = mutableListOf<List<Any?>>()
        state.fundNotes.forEach { (k, v) -> noteRows.add(listOf<Any?>(NOTE_FUND, k, v)) }
        state.logSectionNotes.forEach { (k, v) -> noteRows.add(listOf<Any?>(NOTE_SECTION, k, v)) }
        sheets.add(
            XlsxWriter.Sheet(SHEET_NOTES, listOf("Kind", "Name", "Text"), noteRows, listOf(12, 24, 60)),
        )

        return XlsxWriter.build(sheets)
    }
}
