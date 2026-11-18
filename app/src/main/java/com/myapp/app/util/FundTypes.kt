package com.myapp.app.util

import androidx.compose.ui.graphics.Color
import com.myapp.app.data.AppStore
import com.myapp.app.data.FundMeta
import com.myapp.app.ui.theme.Brand

/** Account / fund type presets (icon + colour identity). Same list, same order as the old app. */
class FundType(val key: String, val label: String, val icon: String, val color: String)

val FUND_TYPES: List<FundType> = listOf(
    FundType("cash", "Cash Wallet", "fa-wallet", "var(--brand-green)"),
    FundType("bank", "Bank", "fa-building-columns", "#0891b2"),
    FundType("mobile", "Mobile Banking", "fa-mobile-screen-button", "#db2777"),
    FundType("credit", "Credit Card", "fa-credit-card", "#2563eb"),
    FundType("saving", "Mutual Fund", "fa-chart-pie", "#9333ea"),
    FundType("savingsAcc", "Savings Account", "fa-piggy-bank", "#0ea5e9"),
    FundType("dps", "DPS", "fa-calendar-check", "#7c3aed"),
    FundType("fdr", "Fixed Deposit", "fa-lock", "#ea580c"),
    FundType("investment", "Investment", "fa-chart-line", "#ca8a04"),
    FundType("meal", "Meal Wallet", "fa-utensils", "#0d9488"),
    FundType("insurance", "Insurance", "fa-shield-heart", "#65a30d"),
    FundType("loan", "Loan", "fa-file-contract", "#9f1239"),
    FundType("pf", "Provident Fund", "fa-briefcase", "#4338ca"),
    FundType("business", "Business", "fa-store", "var(--brand-green)"),
    FundType("emergency", "Emergency Fund", "fa-life-ring", "#b45309"),
    FundType("family", "Family Fund", "fa-people-roof", "#c026d3"),
    FundType("education", "Education Fund", "fa-graduation-cap", "#0369a1"),
    FundType("travel", "Travel Fund", "fa-plane", "#f97316"),
    FundType("health", "Health Fund", "fa-kit-medical", "#dc2626"),
    FundType("rent", "Rent", "fa-house", "#059669"),
    FundType("donation", "Donation", "fa-hand-holding-heart", "#a16207"),
    FundType("vehicle", "Vehicle Fund", "fa-car", "#1d4ed8"),
    FundType("gift", "Gift Fund", "fa-gift", "#ec4899"),
    // Kept last on purpose: the catch-all option always sits at the end of the grid.
    FundType("other", "Other", "fa-ellipsis", "#71717a"),
)

fun getFundType(key: String?): FundType = FUND_TYPES.firstOrNull { it.key == key } ?: FUND_TYPES[0]

/**
 * Icon / colour / type for a fund name. The first time a fund is seen a sensible default is
 * picked and remembered, so old data and freshly imported backups always get an identity.
 */
fun ensureFundMeta(name: String): FundMeta {
    val state = AppStore.state
    val existing = state.fundMeta[name]
    if (existing != null) return existing
    val n = name.lowercase()
    var preset: FundType? = null
    if (n.contains("saving") || n.contains("mutual")) preset = getFundType("saving")
    else if (n.contains("bank")) preset = getFundType("bank")
    else if (n.contains("meal") || n.contains("food")) preset = getFundType("meal")
    else if (n.contains("card")) preset = getFundType("credit")
    else if (n.contains("mobile") || n.contains("bkash") || n.contains("nagad") || n.contains("rocket")) preset = getFundType("mobile")
    else if (n.contains("invest") || n.contains("share") || n.contains("stock")) preset = getFundType("investment")
    else if (n.contains("cash")) preset = getFundType("cash")
    if (preset == null) {
        val idx = state.fundMeta.size % FUND_TYPES.size
        preset = FUND_TYPES[idx]
    }
    val meta = FundMeta(icon = preset.icon, color = preset.color, type = preset.key)
    state.fundMeta[name] = meta
    return meta
}

/** A saved colour string -> Color. Handles "#rrggbb", "#rgb" and the "var(--brand-green)" preset. */
fun parseCssColor(css: String?): Color? {
    if (css.isNullOrEmpty()) return null
    val c = css.trim()
    if (c == "var(--brand-green)") return Brand.green
    if (c == "var(--brand-red)") return Brand.red
    if (!c.startsWith("#")) return null
    val hex = c.substring(1)
    return try {
        when (hex.length) {
            6 -> Color(0xFF000000L or hex.toLong(16))
            3 -> {
                val r = hex.substring(0, 1).repeat(2)
                val g = hex.substring(1, 2).repeat(2)
                val b = hex.substring(2, 3).repeat(2)
                Color(0xFF000000L or "$r$g$b".toLong(16))
            }
            else -> null
        }
    } catch (e: NumberFormatException) {
        null
    }
}

/**
 * The old code built tints as `color + '22'` (hex alpha). That only works for real #rrggbb
 * colours - for the "var(--brand-green)" preset the CSS was invalid and showed NO tint.
 * Returns null in that case, exactly like before.
 */
fun fundTint(css: String?, alphaByte: Int): Color? {
    if (css == null || !css.startsWith("#") || css.length != 7) return null
    val base = parseCssColor(css) ?: return null
    return base.copy(alpha = alphaByte / 255f)
}
