package com.myapp.app.ui.theme

import android.app.Activity
import android.graphics.Color as AndroidColor
import androidx.core.view.WindowCompat

/**
 * Status bar colours mirror the app background (#f4f4f4 light, #0b0b0d dark),
 * pure black on Welcome / Lock / PIN / Pattern screens in dark mode,
 * and always dark while the app is in the background.
 * Navigation bar is always black.
 */
object SystemBars {
    private val LIGHT_BG = AndroidColor.parseColor("#F4F4F4")
    private val DARK_BG = AndroidColor.parseColor("#0B0B0D")
    private val PURE_BLACK = AndroidColor.parseColor("#000000")

    fun apply(activity: Activity, isLight: Boolean, pureBlackScreen: Boolean) {
        val window = activity.window
        val onPureBlack = !isLight && pureBlackScreen
        window.statusBarColor = if (isLight) LIGHT_BG else if (onPureBlack) PURE_BLACK else DARK_BG
        window.navigationBarColor = PURE_BLACK
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = isLight
        controller.isAppearanceLightNavigationBars = false
    }

    fun forceDark(activity: Activity) {
        val window = activity.window
        window.statusBarColor = DARK_BG
        window.navigationBarColor = PURE_BLACK
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
    }
}
