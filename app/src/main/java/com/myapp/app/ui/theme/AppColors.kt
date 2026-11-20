package com.myapp.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** Brand colours (same single shade in light AND dark mode). */
object Brand {
    val green = Color(0xFF16A34A)
    val greenHover = Color(0xFF15803D)
    val greenBtnBg = Color(0xFF166534)
    val greenBtnHover = Color(0xFF14532D)
    val red = Color(0xFFB91C1C)
    val redHover = Color(0xFF991B1B)
}

/**
 * Theme tokens. Every value comes from the old app CSS:
 * the dark-theme overrides of the Tailwind grays (html.dark-theme ...) and the light defaults.
 * "bgXxx" = background-color utilities, "textXxx" = text-color utilities, "borderXxx" = border utilities.
 */
@Immutable
class AppColors(
    val isLight: Boolean,

    // page / body
    val bodyBg: Color,          // body background
    val screenBg: Color,        // bg-[#f4f4f4] (screens)
    val statusBarBg: Color,

    // surfaces (bg-white, bg-gray-xx)
    val bgWhite: Color,
    val bgWhite40: Color,
    val bgWhite60: Color,
    val bgWhite70: Color,
    val bgWhite80: Color,
    val bgGray50: Color,
    val bgGray100: Color,
    val bgGray100a70: Color,
    val bgGray200: Color,
    val bgGray200a50: Color,
    val bgGray200a60: Color,
    val bgGray200a70: Color,
    val bgGray200a80: Color,
    val bgGray300: Color,
    val bgGray300a60: Color,
    val bgGray300a70: Color,

    // text
    val textGray900: Color,
    val textGray800: Color,
    val textGray700: Color,
    val textGray600: Color,
    val textGray500: Color,
    val textGray400: Color,
    val textGray300: Color,
    val placeholder: Color,

    // borders
    val borderGray100: Color,
    val borderGray200: Color,
    val borderGray300: Color,

    // cards (.glass-card / .logs-tx-card / nav bar)
    val cardBg: Color,
    val cardBorder: Color,

    // default text colour (browser default: black in light, white in dark)
    val textDefault: Color,

    // Home screen cards (#home-balance-card / #home-debts-card)
    val homeCardBg: Color,
    val homeCardBorder: Color,
    val homeCardBorderWidthDp: Float,
    val homeCardInnerBg: Color,        // bg-white boxes inside the balance card
    val homeCardInnerBorder: Color,
    val chipBg: Color,                 // today badge / bell button
    val borderGray200a60: Color,       // border-gray-200/60 (panel top line)
    val divider: Color,                // #home-debts-divider
    val navBarBg: Color,
    val navBarBorder: Color,
    val categoryCardBg: Color,         // .daymode-flip-card
    val categoryCardBorder: Color?,
    val categoryCardText: Color,
    val categoryIconBoxBg: Color,      // bg-white boxes in the category card
    val categoryPillBg: Color,         // bg-zinc-800
    val categoryTextMuted: Color,      // text-zinc-400
    val categoryTextName: Color,       // text-zinc-200
    val categoryTextPct: Color,        // text-zinc-300
    val categoryTextEmpty: Color,      // text-zinc-500
    val categoryTextAmount: Color,     // text-white
    val categoryIconTint: Color,       // text-zinc-300 icons
)

fun darkAppColors() = AppColors(
    isLight = false,
    bodyBg = Color(0xFF0B0B0D),
    screenBg = Color(0xFF0B0B0D),
    statusBarBg = Color(0xFF0B0B0D),

    bgWhite = Color(0xFF131315),
    bgWhite40 = Color(0xFF1C1C1F).copy(alpha = 0.55f),
    bgWhite60 = Color(0xFF1C1C1F).copy(alpha = 0.65f),
    bgWhite70 = Color(0xFF1C1C1F).copy(alpha = 0.70f),
    bgWhite80 = Color(0xFF1C1C1F).copy(alpha = 0.85f),
    bgGray50 = Color(0xFF141416),
    bgGray100 = Color(0xFF1E1E21),
    bgGray100a70 = Color(0xFF1E1E21).copy(alpha = 0.70f),
    bgGray200 = Color(0xFF2A2A2E),
    bgGray200a50 = Color(0xFF2A2A2E).copy(alpha = 0.50f),
    bgGray200a60 = Color(0xFF2A2A2E).copy(alpha = 0.60f),
    bgGray200a70 = Color(0xFF2A2A2E).copy(alpha = 0.70f),
    bgGray200a80 = Color(0xFF2A2A2E).copy(alpha = 0.80f),
    bgGray300 = Color(0xFF3A3A40),
    bgGray300a60 = Color(0xFF3A3A40).copy(alpha = 0.60f),
    bgGray300a70 = Color(0xFF3A3A40).copy(alpha = 0.70f),

    textGray900 = Color(0xFFF5F5F7),
    textGray800 = Color(0xFFE4E4E7),
    textGray700 = Color(0xFFD4D4D8),
    textGray600 = Color(0xFFB5B5BD),
    textGray500 = Color(0xFF9D9DA5),
    textGray400 = Color(0xFF86868F),
    textGray300 = Color(0xFF6B6B73),
    placeholder = Color(0xFF6B6B73),

    borderGray100 = Color.White.copy(alpha = 0.08f),
    borderGray200 = Color.White.copy(alpha = 0.12f),
    borderGray300 = Color.White.copy(alpha = 0.16f),

    cardBg = Color(0xFF141414),
    cardBorder = Color(0xFF232323),

    textDefault = Color(0xFFFFFFFF),

    homeCardBg = Color(0xFF141414),
    homeCardBorder = Color(0xFF232323),
    homeCardBorderWidthDp = 0.5f,
    homeCardInnerBg = Color(0xFF1E1E1E),
    homeCardInnerBorder = Color(0xFF2A2A2A),
    chipBg = Color(0xFF141414),
    borderGray200a60 = Color.White.copy(alpha = 0.10f),
    divider = Color(0xFF2A2A2E),
    navBarBg = Color(0xFF141414),
    navBarBorder = Color(0xFF232323),
    categoryCardBg = Color(0xFF000000),
    categoryCardBorder = null,
    categoryCardText = Color(0xFFFFFFFF),
    categoryIconBoxBg = Color(0xFF000000),
    categoryPillBg = Tw.zinc800,
    categoryTextMuted = Tw.zinc400,
    categoryTextName = Tw.zinc200,
    categoryTextPct = Tw.zinc300,
    categoryTextEmpty = Tw.zinc500,
    categoryTextAmount = Color(0xFFFFFFFF),
    categoryIconTint = Tw.zinc300,
)

fun lightAppColors() = AppColors(
    isLight = true,
    bodyBg = Color(0xFFF4F4F4),
    screenBg = Color(0xFFF4F4F4),
    statusBarBg = Color(0xFFF4F4F4),

    bgWhite = Color(0xFFE4E4E7).copy(alpha = 0.85f),
    bgWhite40 = Color(0xFFE4E4E7).copy(alpha = 0.85f),
    bgWhite60 = Color(0xFFE4E4E7).copy(alpha = 0.85f),
    bgWhite70 = Color(0xFFE4E4E7).copy(alpha = 0.85f),
    bgWhite80 = Color(0xFFE4E4E7).copy(alpha = 0.85f),
    bgGray50 = Color(0xFFE4E4E7).copy(alpha = 0.85f),
    bgGray100 = Color(0xFFE4E4E7).copy(alpha = 0.85f),
    bgGray100a70 = Color(0xFFE4E4E7).copy(alpha = 0.85f),
    bgGray200 = Color(0xFFE4E4E7).copy(alpha = 0.85f),
    bgGray200a50 = Color(0xFFE4E4E7).copy(alpha = 0.85f),
    bgGray200a60 = Color(0xFFE4E4E7).copy(alpha = 0.85f),
    bgGray200a70 = Color(0xFFE4E4E7).copy(alpha = 0.85f),
    bgGray200a80 = Color(0xFFE4E4E7).copy(alpha = 0.85f),
    // light mode overrides bg-gray-300 to zinc-300 #d4d4d8
    bgGray300 = Color(0xFFD4D4D8),
    bgGray300a60 = Color(0xFFD4D4D8).copy(alpha = 0.70f),
    bgGray300a70 = Color(0xFFD4D4D8).copy(alpha = 0.78f),

    textGray900 = Tw.gray900,
    textGray800 = Tw.gray800,
    textGray700 = Tw.gray700,
    textGray600 = Tw.gray600,
    textGray500 = Tw.gray500,
    // light mode overrides text-gray-400 -> #52525b and text-gray-300 -> #71717a
    textGray400 = Color(0xFF52525B),
    textGray300 = Color(0xFF71717A),
    placeholder = Color(0xFF9CA3AF),

    borderGray100 = Tw.gray100,
    borderGray200 = Tw.gray200,
    borderGray300 = Tw.gray300,

    cardBg = Color(0xFFE4E4E7).copy(alpha = 0.85f),
    cardBorder = Color(0xFFD4D4D8).copy(alpha = 0.80f),

    textDefault = Color(0xFF000000),

    homeCardBg = Color(0xFFE4E4E7).copy(alpha = 0.85f),
    homeCardBorder = Color.White.copy(alpha = 0.60f),
    homeCardBorderWidthDp = 1f,
    homeCardInnerBg = Color(0xFFE4E4E7).copy(alpha = 0.85f),
    homeCardInnerBorder = Color.Transparent,
    chipBg = Color(0xFFE4E4E7).copy(alpha = 0.85f),
    borderGray200a60 = Tw.gray200.copy(alpha = 0.60f),
    divider = Color(0xFFB8B8BF),
    navBarBg = Color(0xFF0F0F0F).copy(alpha = 0.28f),
    navBarBorder = Color.White.copy(alpha = 0.10f),
    categoryCardBg = Color(0xFFE4E4E7).copy(alpha = 0.85f),
    categoryCardBorder = Color(0xFFD4D4D8).copy(alpha = 0.80f),
    categoryCardText = Color(0xFF18181B),
    categoryIconBoxBg = Color(0xFFFFFFFF),
    categoryPillBg = Color(0xFFD4D4D8),
    categoryTextMuted = Color(0xFF71717A),
    categoryTextName = Color(0xFF18181B),
    categoryTextPct = Color(0xFF52525B),
    categoryTextEmpty = Color(0xFF52525B),
    categoryTextAmount = Color(0xFF18181B),
    categoryIconTint = Color(0xFF52525B),
)
