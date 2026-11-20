package com.myapp.app.ui.theme

import android.app.Activity
import android.graphics.Color as AndroidColor
import android.os.Build
import androidx.core.view.WindowCompat

/**
 * Day mode: transparent status + navigation bars over the app's own page background (dark icons).
 * Dark mode: both bars solid #010101 (the backdrop is pure black at the top and bottom edge).
 * While the app is in the background both bars are dark.
 */
object SystemBars {
    private val SYSTEM_BLACK = AndroidColor.parseColor("#010101")

    fun apply(activity: Activity, isLight: Boolean, pureBlackScreen: Boolean) {
        val window = activity.window
        // pureBlackScreen is kept in the signature for existing callers; it no longer changes the colours.
        val darkNavIcons = isLight && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        val darkStatusIcons = isLight && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
        // Day mode: both bars are TRANSPARENT and the app draws its page background right under them
        // (edge-to-edge, see TrackerRoot), so the bars look like part of the page.
        // Dark mode: solid #010101 as before (the backdrop is pure black at both edges).
        window.statusBarColor = if (darkStatusIcons) AndroidColor.TRANSPARENT else SYSTEM_BLACK
        window.navigationBarColor = if (darkNavIcons) AndroidColor.TRANSPARENT else SYSTEM_BLACK
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = darkStatusIcons
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
