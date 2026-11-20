package com.myapp.app.ui.theme

import android.app.Activity
import android.graphics.Color as AndroidColor
import android.os.Build
import androidx.core.view.WindowCompat

/**
 * Both bars blend into the app backdrop (AppBackdrop.kt):
 *  - Dark : status bar + navigation bar #010101 (backdrop is pure black at the top and bottom edge).
 *  - Light: status bar #F9FAFB (the backdrop's solid top colour);
 *           navigation bar #F6EBEB (the backdrop's bottom edge: #F9FAFB + 12% brand red tint + the
 *           white glow, averaged across the screen width), with dark gesture pill / icons.
 *           Android 7 and older cannot draw dark nav icons, so there the nav bar stays #010101.
 * While the app is in the background both bars are dark.
 */
object SystemBars {
    private val LIGHT_STATUS = AndroidColor.parseColor("#F9FAFB")
    private val LIGHT_NAV = AndroidColor.parseColor("#F6EBEB")
    private val SYSTEM_BLACK = AndroidColor.parseColor("#010101")

    fun apply(activity: Activity, isLight: Boolean, pureBlackScreen: Boolean) {
        val window = activity.window
        // pureBlackScreen is kept in the signature for existing callers; it no longer changes the colours.
        val darkNavIcons = isLight && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        window.statusBarColor = if (isLight) LIGHT_STATUS else SYSTEM_BLACK
        window.navigationBarColor = if (darkNavIcons) LIGHT_NAV else SYSTEM_BLACK
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = isLight
        controller.isAppearanceLightNavigationBars = darkNavIcons
    }

    fun forceDark(activity: Activity) {
        val window = activity.window
        window.statusBarColor = SYSTEM_BLACK
        window.navigationBarColor = SYSTEM_BLACK
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
    }
}
