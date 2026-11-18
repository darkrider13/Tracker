package com.myapp.app.data

import org.json.JSONArray
import org.json.JSONObject

/*
 * The app data, exactly the same shape as the old `appState` (same field names, same meanings),
 * so a backup file made by the old web version loads here and the other way round.
 * Fields this version does not know about are kept in `extra` and written back untouched.
 */

/** One payment logged against a debt / receivable. */
class DebtPayment(
    var amount: Double = 0.0,
    var date: String = "",
    var fund: String? = null,
    var internal: Boolean? = null,
    var fromFund: String? = null,
    var toFund: String? = null,
    var txId: Long? = null,
    var txIds: MutableList<Long>? = null,
    val extra: JSONObject = JSONObject(),
) {
    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("amount", safeDouble(amount))
        o.put("date", date)
        o.putNullable("fund", fund)
        o.putIfNotNull("internal", internal)
        o.putIfNotNull("fromFund", fromFund)
        o.putIfNotNull("toFund", toFund)
        o.putIfNotNull("txId", txId)
        txIds?.let { ids ->
            val arr = JSONArray()
            ids.forEach { arr.put(it) }
            o.put("txIds", arr)
        }
        o.putExtras(extra)
        return o
    }

    companion object {
        private val KNOWN = setOf("amount", "date", "fund", "internal", "fromFund", "toFund", "txId", "txIds")

        fun fromJson(o: JSONObject): DebtPayment = DebtPayment(
            amount = o.dbl("amount"),
            date = o.str("date") ?: "",
            fund = o.str("fund"),
            internal = o.bool("internal"),
            fromFund = o.str("fromFund"),
            toFund = o.str("toFund"),
            txId = o.long("txId"),
            txIds = o.optJSONArray("txIds")?.toLongList(),
            extra = o.extras(KNOWN),
        )
    }
}

/** One line in a fund's history (expense, add money, transfer, debt lines...). */
class Transaction(
    var id: Long = 0L,
    var type: String? = null,            // null = normal expense; 'add_money', 'transfer_in', 'transfer_out', 'debt_lent'
    var fund: String = "",
    var category: String = "",
    var description: String = "",
    var amount: Double = 0.0,
    var date: String = "",
    var transferId: Long? = null,
    var linkedFund: String? = null,
    var debtNoteKind: String? = null,
    var debtNotePerson: String? = null,
    var fromDebtPayment: Boolean? = null,
    var debtEntryId: Long? = null,
    var debtPaymentEntryId: Long? = null,
    var debtCounterTx: Boolean? = null,
    var historyHidden: Boolean? = null,
    val extra: JSONObject = JSONObject(),
) {
    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("id", id)
        o.putIfNotNull("transferId", transferId)
        o.putIfNotNull("type", type)
        o.put("fund", fund)
        o.putIfNotNull("linkedFund", linkedFund)
        o.put("category", category)
        o.put("description", description)
        o.putIfNotNull("debtNoteKind", debtNoteKind)
        o.putIfNotNull("debtNotePerson", debtNotePerson)
        o.put("amount", safeDouble(amount))
        o.put("date", date)
        o.putIfNotNull("fromDebtPayment", fromDebtPayment)
        o.putIfNotNull("debtEntryId", debtEntryId)
        o.putIfNotNull("debtPaymentEntryId", debtPaymentEntryId)
        o.putIfNotNull("debtCounterTx", debtCounterTx)
        o.putIfNotNull("historyHidden", historyHidden)
        o.putExtras(extra)
        return o
    }

    companion object {
        private val KNOWN = setOf(
            "id", "type", "fund", "category", "description", "amount", "date", "transferId",
            "linkedFund", "debtNoteKind", "debtNotePerson", "fromDebtPayment", "debtEntryId",
            "debtPaymentEntryId", "debtCounterTx", "historyHidden",
        )

        fun fromJson(o: JSONObject): Transaction = Transaction(
            id = o.long("id") ?: 0L,
            type = o.str("type"),
            fund = o.str("fund") ?: "",
            category = o.str("category") ?: "",
            description = o.str("description") ?: "",
            amount = o.dbl("amount"),
            date = o.str("date") ?: "",
            transferId = o.long("transferId"),
            linkedFund = o.str("linkedFund"),
            debtNoteKind = o.str("debtNoteKind"),
            debtNotePerson = o.str("debtNotePerson"),
            fromDebtPayment = o.bool("fromDebtPayment"),
            debtEntryId = o.long("debtEntryId"),
            debtPaymentEntryId = o.long("debtPaymentEntryId"),
            debtCounterTx = o.bool("debtCounterTx"),
            historyHidden = o.bool("historyHidden"),
            extra = o.extras(KNOWN),
        )
    }
}

/** A debt (I owe) or receivable (I am owed). */
class Debt(
    var id: Long = 0L,
    var type: String = "debt",           // 'debt' | 'receivable'
    var person: String = "",
    var amount: Double = 0.0,
    var fund: String? = null,
    var counterFund: String? = null,
    var note: String = "",
    var dueDate: String? = null,
    var reminderTime: String? = null,
    var dueReminderFired: Boolean = false,
    var createdDate: String = "",
    var settled: Boolean = false,
    var settledDate: String? = null,
    var payments: MutableList<DebtPayment> = mutableListOf(),
    var isKey: Boolean = false,
    val extra: JSONObject = JSONObject(),
) {
    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("id", id)
        o.put("type", type)
        o.put("person", person)
        o.put("amount", safeDouble(amount))
        o.putNullable("fund", fund)
        o.putNullable("counterFund", counterFund)
        o.put("note", note)
        o.putNullable("dueDate", dueDate)
        o.putNullable("reminderTime", reminderTime)
        o.put("dueReminderFired", dueReminderFired)
        o.put("createdDate", createdDate)
        o.put("settled", settled)
        o.putNullable("settledDate", settledDate)
        val arr = JSONArray()
        payments.forEach { arr.put(it.toJson()) }
        o.put("payments", arr)
        o.put("isKey", isKey)
        o.putExtras(extra)
        return o
    }

    companion object {
        private val KNOWN = setOf(
            "id", "type", "person", "amount", "fund", "counterFund", "note", "dueDate", "reminderTime",
            "dueReminderFired", "createdDate", "settled", "settledDate", "payments", "isKey",
        )

        fun fromJson(o: JSONObject): Debt {
            val pays = mutableListOf<DebtPayment>()
            val arr = o.optJSONArray("payments")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val p = arr.optJSONObject(i)
                    if (p != null) pays.add(DebtPayment.fromJson(p))
                }
            }
            return Debt(
                id = o.long("id") ?: 0L,
                type = o.str("type") ?: "debt",
                person = o.str("person") ?: "",
                amount = o.dbl("amount"),
                fund = o.str("fund"),
                counterFund = o.str("counterFund"),
                note = o.str("note") ?: "",
                dueDate = o.str("dueDate"),
                reminderTime = o.str("reminderTime"),
                dueReminderFired = o.bool("dueReminderFired") ?: false,
                createdDate = o.str("createdDate") ?: "",
                settled = o.bool("settled") ?: false,
                settledDate = o.str("settledDate"),
                payments = pays,
                isKey = o.bool("isKey") ?: false,
                extra = o.extras(KNOWN),
            )
        }
    }
}

/** Icon / colour / type chosen for a fund (account). */
class FundMeta(
    var icon: String = "",
    var color: String = "",
    var type: String = "",
    val extra: JSONObject = JSONObject(),
) {
    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("icon", icon)
        o.put("color", color)
        o.put("type", type)
        o.putExtras(extra)
        return o
    }

    companion object {
        private val KNOWN = setOf("icon", "color", "type")

        fun fromJson(o: JSONObject): FundMeta = FundMeta(
            icon = o.str("icon") ?: "",
            color = o.str("color") ?: "",
            type = o.str("type") ?: "",
            extra = o.extras(KNOWN),
        )
    }
}

class CustomCategory(
    var name: String = "",
    var icon: String = "",
    val extra: JSONObject = JSONObject(),
) {
    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("name", name)
        o.put("icon", icon)
        o.putExtras(extra)
        return o
    }

    companion object {
        private val KNOWN = setOf("name", "icon")

        fun fromJson(o: JSONObject): CustomCategory = CustomCategory(
            name = o.str("name") ?: "",
            icon = o.str("icon") ?: "",
            extra = o.extras(KNOWN),
        )
    }
}

/** Logs-screen filters that are saved with the data. */
class Filters(
    var sort: String = "newest",
    var time: String = "month",
    var price: String = "all",
    var customDate: String? = null,
    var customMonth: String? = null,
    val extra: JSONObject = JSONObject(),
) {
    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("sort", sort)
        o.put("time", time)
        o.put("price", price)
        o.putIfNotNull("customDate", customDate)
        o.putIfNotNull("customMonth", customMonth)
        o.putExtras(extra)
        return o
    }

    companion object {
        private val KNOWN = setOf("sort", "time", "price", "customDate", "customMonth")

        fun fromJson(o: JSONObject): Filters = Filters(
            sort = o.str("sort") ?: "newest",
            time = o.str("time") ?: "month",
            price = o.str("price") ?: "all",
            customDate = o.str("customDate"),
            customMonth = o.str("customMonth"),
            extra = o.extras(KNOWN),
        )
    }
}

/** Everything the user saved. Same fields as the old `appState`. */
class AppState(
    var username: String = "User",
    var savingsTarget: Double = 0.0,
    var onboardingComplete: Boolean = false,
    var activeFund: String = "Monthly Cash",
    var funds: LinkedHashMap<String, Double> = linkedMapOf("Monthly Cash" to 0.0),
    var transactions: MutableList<Transaction> = mutableListOf(),
    var debts: MutableList<Debt> = mutableListOf(),
    var filters: Filters = Filters(),
    var fundMeta: LinkedHashMap<String, FundMeta> = linkedMapOf(),
    var fundNotes: LinkedHashMap<String, String> = linkedMapOf(),
    var logSectionNotes: LinkedHashMap<String, String> = linkedMapOf(),
    var categoryOrder: MutableList<String> = mutableListOf(),
    var customCategories: MutableList<CustomCategory> = mutableListOf(),
    var biometricLockEnabled: Boolean = false,
    var lockType: String = "none",       // none | biometric | pin | pattern
    var lockPinHash: String? = null,
    var lockPatternHash: String? = null,
    var profileAvatar: String = "simple",
    var dashboardRangeStart: String? = null,
    var dashboardRangeEnd: String? = null,
    val extra: JSONObject = JSONObject(),
) {
    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("username", username)
        o.put("savingsTarget", safeDouble(savingsTarget))
        o.put("onboardingComplete", onboardingComplete)
        o.put("activeFund", activeFund)

        val fundsObj = JSONObject()
        funds.forEach { (k, v) -> fundsObj.put(k, safeDouble(v)) }
        o.put("funds", fundsObj)

        val txArr = JSONArray()
        transactions.forEach { txArr.put(it.toJson()) }
        o.put("transactions", txArr)

        val debtArr = JSONArray()
        debts.forEach { debtArr.put(it.toJson()) }
        o.put("debts", debtArr)

        o.put("filters", filters.toJson())

        val metaObj = JSONObject()
        fundMeta.forEach { (k, v) -> metaObj.put(k, v.toJson()) }
        o.put("fundMeta", metaObj)

        val notesObj = JSONObject()
        fundNotes.forEach { (k, v) -> notesObj.put(k, v) }
        o.put("fundNotes", notesObj)

        val sectionObj = JSONObject()
        logSectionNotes.forEach { (k, v) -> sectionObj.put(k, v) }
        o.put("logSectionNotes", sectionObj)

        val orderArr = JSONArray()
        categoryOrder.forEach { orderArr.put(it) }
        o.put("categoryOrder", orderArr)

        val catArr = JSONArray()
        customCategories.forEach { catArr.put(it.toJson()) }
        o.put("customCategories", catArr)

        o.put("biometricLockEnabled", biometricLockEnabled)
        o.put("lockType", lockType)
        o.putNullable("lockPinHash", lockPinHash)
        o.putNullable("lockPatternHash", lockPatternHash)
        o.put("profileAvatar", profileAvatar)
        o.putIfNotNull("dashboardRangeStart", dashboardRangeStart)
        o.putIfNotNull("dashboardRangeEnd", dashboardRangeEnd)
        o.putExtras(extra)
        return o
    }

    companion object {
        private val KNOWN = setOf(
            "username", "savingsTarget", "onboardingComplete", "activeFund", "funds", "transactions",
            "debts", "filters", "fundMeta", "fundNotes", "logSectionNotes", "categoryOrder",
            "customCategories", "biometricLockEnabled", "lockType", "lockPinHash", "lockPatternHash",
            "profileAvatar", "dashboardRangeStart", "dashboardRangeEnd",
        )

        /** Brand-new data (first launch). Same as the old default appState. */
        fun newDefault(): AppState = AppState()

        /**
         * Builds the state from saved JSON and applies every default / migration the old app
         * applied at startup (older saved data keeps working):
         *  - missing filters / debts / fundMeta / payments get their defaults
         *  - the old on/off biometric toggle becomes lockType
         */
        fun fromJson(o: JSONObject): AppState {
            val funds = LinkedHashMap<String, Double>()
            val fundsObj = o.optJSONObject("funds")
            if (fundsObj != null) {
                val it = fundsObj.keys()
                while (it.hasNext()) {
                    val k = it.next()
                    funds[k] = fundsObj.dbl(k)
                }
            } else {
                funds["Monthly Cash"] = 0.0
            }

            val txs = mutableListOf<Transaction>()
            val txArr = o.optJSONArray("transactions")
            if (txArr != null) {
                for (i in 0 until txArr.length()) {
                    val t = txArr.optJSONObject(i)
                    if (t != null) txs.add(Transaction.fromJson(t))
                }
            }

            val debts = mutableListOf<Debt>()
            val debtArr = o.optJSONArray("debts")
            if (debtArr != null) {
                for (i in 0 until debtArr.length()) {
                    val d = debtArr.optJSONObject(i)
                    if (d != null) debts.add(Debt.fromJson(d))
                }
            }

            val meta = LinkedHashMap<String, FundMeta>()
            val metaObj = o.optJSONObject("fundMeta")
            if (metaObj != null) {
                val it = metaObj.keys()
                while (it.hasNext()) {
                    val k = it.next()
                    val m = metaObj.optJSONObject(k)
                    if (m != null) meta[k] = FundMeta.fromJson(m)
                }
            }

            val notes = LinkedHashMap<String, String>()
            val notesObj = o.optJSONObject("fundNotes")
            if (notesObj != null) {
                val it = notesObj.keys()
                while (it.hasNext()) {
                    val k = it.next()
                    notes[k] = notesObj.str(k) ?: ""
                }
            }

            val sections = LinkedHashMap<String, String>()
            val sectionObj = o.optJSONObject("logSectionNotes")
            if (sectionObj != null) {
                val it = sectionObj.keys()
                while (it.hasNext()) {
                    val k = it.next()
                    sections[k] = sectionObj.str(k) ?: ""
                }
            }

            val order = mutableListOf<String>()
            val orderArr = o.optJSONArray("categoryOrder")
            if (orderArr != null) {
                for (i in 0 until orderArr.length()) {
                    val n = orderArr.opt(i)
                    if (n is String) order.add(n)
                }
            }

            val cats = mutableListOf<CustomCategory>()
            val catArr = o.optJSONArray("customCategories")
            if (catArr != null) {
                for (i in 0 until catArr.length()) {
                    val c = catArr.optJSONObject(i)
                    if (c != null) cats.add(CustomCategory.fromJson(c))
                }
            }

            val biometric = o.bool("biometricLockEnabled") ?: false
            val lockType = o.str("lockType") ?: (if (biometric) "biometric" else "none")
            val avatar = o.str("profileAvatar")?.takeIf { it.isNotEmpty() } ?: "simple"

            val active = o.str("activeFund") ?: "Monthly Cash"

            return AppState(
                username = o.str("username") ?: "User",
                savingsTarget = o.dbl("savingsTarget"),
                onboardingComplete = o.bool("onboardingComplete") ?: false,
                activeFund = active,
                funds = funds,
                transactions = txs,
                debts = debts,
                filters = o.optJSONObject("filters")?.let { Filters.fromJson(it) } ?: Filters(),
                fundMeta = meta,
                fundNotes = notes,
                logSectionNotes = sections,
                categoryOrder = order,
                customCategories = cats,
                biometricLockEnabled = biometric,
                lockType = lockType,
                lockPinHash = o.str("lockPinHash"),
                lockPatternHash = o.str("lockPatternHash"),
                profileAvatar = avatar,
                dashboardRangeStart = o.str("dashboardRangeStart"),
                dashboardRangeEnd = o.str("dashboardRangeEnd"),
                extra = o.extras(KNOWN),
            )
        }
    }
}
