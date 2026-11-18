package com.myapp.app.util

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.myapp.app.data.AppStore
import com.myapp.app.data.CustomCategory
import com.myapp.app.data.Transaction

/** Goes up whenever the category chip order changes, so open pickers redraw. */
object CategoryUi {
    var revision by mutableIntStateOf(0)
        internal set
}

/** Only these 5 show by default in the category picker; the user's own categories go in front. */
val DEFAULT_QUICK_CATEGORIES: List<String> = listOf("Food", "Shopping", "Bills", "Transport", "Medicine")

/** Every built-in name the app understands (old transactions, typed text, translations, icons, stats). */
val ALL_CANONICAL_CATEGORIES: List<String> = listOf(
    "Food", "Shopping", "Bills", "Transport", "Grocery", "Medicine", "Event", "Rent", "Recharge",
    "Education", "Fun", "Fuel", "Family", "Donation", "Insurance", "Fitness", "Loan", "Travel",
    "Self Care", "Pet Care", "Gifts", "Subscription", "Internet", "Repair", "Other",
)

/** Category chips in the saved order (custom ones in front). Kept in step by [syncCustomCategories]. */
val QUICK_CATEGORIES: MutableList<String> = DEFAULT_QUICK_CATEGORIES.toMutableList()

/** The Edit-transaction picker shows the same list. */
val EDIT_TX_QUICK_CATEGORIES: MutableList<String> = DEFAULT_QUICK_CATEGORIES.toMutableList()

val CATEGORY_I18N_KEYS: Map<String, String> = mapOf(
    "Food" to "cat_food", "Shopping" to "cat_shopping", "Bills" to "cat_bills", "Transport" to "cat_transport",
    "Grocery" to "cat_grocery", "Medicine" to "cat_medicine", "Event" to "cat_event", "Rent" to "cat_rent",
    "Recharge" to "cat_recharge", "Education" to "cat_education", "Fun" to "cat_entertainment",
    "Fuel" to "cat_fuel", "Family" to "cat_family", "Donation" to "cat_donation", "Insurance" to "cat_insurance",
    "Fitness" to "cat_fitness", "Loan" to "cat_loan", "Travel" to "cat_travel", "Self Care" to "cat_selfcare",
    "Pet Care" to "cat_petcare", "Gifts" to "cat_gifts", "Subscription" to "cat_subscription",
    "Internet" to "cat_internet", "Repair" to "cat_repair", "Other" to "cat_other",
)

/** Removes emoji (the two ranges the old app stripped). */
fun stripEmoji(s: String): String {
    val sb = StringBuilder(s.length)
    var i = 0
    while (i < s.length) {
        val cp = s.codePointAt(i)
        val n = Character.charCount(cp)
        val isEmoji = (cp in 0x1F300..0x1FAFF) || (cp in 0x2600..0x27BF)
        if (!isEmoji) sb.append(s, i, i + n)
        i += n
    }
    return sb.toString()
}

fun getCustomCategories(): List<CustomCategory> = AppStore.state.customCategories

fun cleanCategoryName(raw: String?): String {
    var s = stripEmoji(raw ?: "")
    s = s.replace("\"", "").replace("<", "").replace(">", "").replace("\\", "")
    s = s.replace(Regex("\\s+"), " ").trim()
    return if (s.length > 20) s.substring(0, 20) else s
}

fun findCustomCategory(label: String?): CustomCategory? {
    val n = cleanCategoryName(label).lowercase()
    if (n.isEmpty()) return null
    return getCustomCategories().firstOrNull { it.name.lowercase() == n }
}

/**
 * Keeps the chip lists in step with the saved order (appState.categoryOrder): the user's own
 * categories come first (newest in front), the 5 defaults follow, and any drag order is remembered.
 */
fun syncCustomCategories() {
    val state = AppStore.state
    val customNames = state.customCategories.map { it.name }
    val valid = HashSet<String>(DEFAULT_QUICK_CATEGORIES)
    valid.addAll(customNames)
    val seen = HashSet<String>()
    var order: List<String> = state.categoryOrder.filter { n -> valid.contains(n) && seen.add(n) }
    val missingCustom = customNames.filter { !order.contains(it) }.reversed()   // newest first
    val missingDefault = DEFAULT_QUICK_CATEGORIES.filter { !order.contains(it) }
    order = missingCustom + order + missingDefault
    state.categoryOrder = order.toMutableList()
    QUICK_CATEGORIES.clear(); QUICK_CATEGORIES.addAll(order)
    EDIT_TX_QUICK_CATEGORIES.clear(); EDIT_TX_QUICK_CATEGORIES.addAll(order)
    CategoryUi.revision += 1
}

/** English category name -> text in the current language (custom names unchanged). */
fun categoryDisplayName(name: String): String {
    val key = CATEGORY_I18N_KEYS[name]
    return if (key != null) tr(key, name) else name
}

/**
 * Any category text (English, its Bangla translation, or a custom name) -> the matching built-in
 * name, or null. Works in every language so a category saved in Bangla still resolves in English.
 */
fun resolveCanonicalCategory(raw: String?): String? {
    if (raw.isNullOrEmpty()) return null
    val rawLower = raw.trim().lowercase()
    val pool = QUICK_CATEGORIES + ALL_CANONICAL_CATEGORIES.filter { !QUICK_CATEGORIES.contains(it) }
    return pool.firstOrNull { c ->
        if (c.lowercase() == rawLower) {
            true
        } else {
            val key = CATEGORY_I18N_KEYS[c]
            val bnName = if (key != null) I18n.banglaText(key) else null
            bnName != null && bnName.lowercase() == rawLower
        }
    }
}

/** Category text of a saved transaction in the current language ("Other" when empty). */
fun translatedCategoryLabel(raw: String?): String {
    val stripped = stripEmoji(raw ?: "").trim()
    if (stripped.isEmpty()) return tr("home_other", "Other")
    if (stripped == "Add Money") return tr("cat_add_money_log", "Money Added")
    if (stripped == "Debt Collection") return tr("cat_debt_collection_log", "Receivable Collected")
    if (stripped == "Debt Repayment") return tr("cat_debt_repayment_log", "Debt Repayment")
    if (stripped == "Debt Lent") return tr("cat_debt_lent_log", "Money Lent")
    if (stripped == "Debt Borrowed") return tr("cat_debt_borrowed_log", "Money Borrowed")
    if (stripped == "Fund Transfer") return tr("cat_fund_transfer_log", "Fund Transfer")
    val canonical = resolveCanonicalCategory(stripped)
    return if (canonical != null) categoryDisplayName(canonical) else stripped
}

/** Every name a category can be searched under: English AND Bangla, whatever language is on screen. */
fun categorySearchLabels(raw: String?): List<String> {
    val stripped = stripEmoji(raw ?: "").trim()
    fun pair(en: String, bnKey: String): List<String> {
        val bn = I18n.banglaText(bnKey)
        return if (bn != null) listOf(en, bn) else listOf(en)
    }
    if (stripped.isEmpty()) return pair("Other", "home_other")
    if (stripped == "Add Money") return pair("Money Added", "cat_add_money_log")
    if (stripped == "Debt Collection") return pair("Receivable Collected", "cat_debt_collection_log")
    if (stripped == "Debt Repayment") return pair("Debt Repayment", "cat_debt_repayment_log")
    if (stripped == "Debt Lent") return pair("Money Lent", "cat_debt_lent_log")
    if (stripped == "Debt Borrowed") return pair("Money Borrowed", "cat_debt_borrowed_log")
    if (stripped == "Fund Transfer") return pair("Fund Transfer", "cat_fund_transfer_log")
    val canonical = resolveCanonicalCategory(stripped)
    if (canonical != null) {
        val key = CATEGORY_I18N_KEYS[canonical]
        val bn = if (key != null) I18n.banglaText(key) else null
        return if (bn != null) listOf(canonical, bn) else listOf(canonical)
    }
    return listOf(stripped)
}

/** Font Awesome icon name for a category label (custom categories win, then keyword guesses). */
fun getCategoryFAIcon(label: String?): String {
    val c = (label ?: "").lowercase()
    val customHit = findCustomCategory(label)
    if (customHit != null) return customHit.icon
    if (c.contains("transfer") || c.contains("ট্রান্সফার")) return "fa-right-left"
    if (c.contains("add money") || c.contains("টাকা যোগ")) return "fa-circle-dollar-to-slot"
    if (c.contains("debt collect") || c.contains("পাওনা আদায়")) return "fa-hand-holding-dollar"
    if (c.contains("debt repay") || c.contains("ধার পরিশোধ")) return "fa-hand-holding-dollar"
    // Transaction-log icons show the cash movement AT THE TIME: lent = out (up-right), borrowed = in (down-left).
    if (c.contains("debt borrowed") || c.contains("money borrowed") || c.contains("ধার নেওয়া")) return "fa-arrow-down-left"
    if (c.contains("debt lent") || c.contains("money lent") || c.contains("ধার দেওয়া")) return "fa-arrow-up-right"
    if (c.contains("grocery") || c.contains("মুদি")) return "fa-cart-shopping"
    if (c.contains("medic") || c.contains("ওষুধ")) return "fa-briefcase-medical"
    if (c.contains("event") || c.contains("অনুষ্ঠান")) return "fa-calendar-days"
    if (c.contains("rent") || c.contains("ভাড়া")) return "fa-house"
    if (c.contains("recharge") || c.contains("mobile") || c.contains("রিচার্জ")) return "fa-mobile-screen-button"
    if (c.contains("educat") || c.contains("tuition") || c.contains("শিক্ষা")) return "fa-graduation-cap"
    if (c.contains("entertain") || c.contains("বিনোদন")) return "fa-face-laugh-beam"
    if (c.contains("fuel") || c.contains("petrol") || c.contains("octane") || c.contains("জ্বালানি")) return "fa-gas-pump"
    if (c.contains("famil") || c.contains("পরিবার")) return "fa-people-roof"
    if (c.contains("donat") || c.contains("charity") || c.contains("দান")) return "fa-hand-holding-heart"
    if (c.contains("food") || c.contains("খাবার")) return "fa-utensils"
    if (c.contains("shop") || c.contains("কেনাকাটা")) return "fa-bag-shopping"
    if (c.contains("bill") || c.contains("বিল")) return "fa-file-invoice-dollar"
    if (c.contains("transport") || Regex("\\bcar\\b").containsMatchIn(c) || c.contains("যাতায়াত")) return "fa-car"
    if (c.contains("insurance") || c.contains("বীমা")) return "fa-hand-holding-medical"
    if (c.contains("fitness") || c.contains("ফিটনেস")) return "fa-dumbbell"
    if (c.contains("loan") || c.contains("ঋণ")) return "fa-hand-holding-dollar"
    if (c.contains("travel") || c.contains("ভ্রমণ")) return "fa-plane"
    if (c.contains("self care") || c.contains("নিজের যত্ন")) return "fa-spa"
    if (c.contains("pet") || c.contains("পোষা")) return "fa-paw"
    if (c.contains("gift") || c.contains("উপহার")) return "fa-gift"
    if (c.contains("subscription") || c.contains("সাবস্ক্রিপশন")) return "fa-arrows-rotate"
    if (c.contains("internet") || c.contains("wifi") || c.contains("ইন্টারনেট")) return "fa-wifi"
    if (c.contains("repair") || c.contains("মেরামত")) return "fa-screwdriver-wrench"
    return "fa-circle-plus"
}

/** True when this category shows the four-shape "Other" glyph (also for an empty category). */
fun isOtherCategoryIcon(rawCategory: String?): Boolean {
    val canonical = resolveCanonicalCategory(rawCategory)
        ?: (if (rawCategory == null || rawCategory.trim().isEmpty()) "Other" else null)
    return canonical == "Other"
}

class CategoryGroup(val label: String, var total: Double, var count: Int)

/** Spending grouped by (translated) category, biggest first. */
fun groupByCategory(transactions: List<Transaction>): List<CategoryGroup> {
    val map = LinkedHashMap<String, CategoryGroup>()
    for (t in transactions) {
        val label = translatedCategoryLabel(t.category)
        val g = map.getOrPut(label) { CategoryGroup(label, 0.0, 0) }
        g.total += t.amount
        g.count += 1
    }
    return map.values.sortedByDescending { it.total }
}
