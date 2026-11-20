package com.myapp.app.ui.theme

import android.app.Activity
import android.graphics.Color as AndroidColor
import androidx.core.view.WindowCompat

/**
 * Dark mode: status bar AND navigation bar are both #010101 on every screen
 * (Home, Lock, PIN, Pattern, Welcome ...), so they blend into the black app backdrop.
 * Light mode: status bar keeps its light colour (#f4f4f4).
 * While the app is in the background both bars are dark.
 * Navigation bar is always #010101 with light gesture-pill / icons.
 */
object SystemBars {
    private val LIGHT_BG = AndroidColor.parseColor("#F4F4F4")
    private val SYSTEM_BLACK = AndroidColor.parseColor("#010101")

    fun apply(activity: Activity, isLight: Boolean, pureBlackScreen: Boolean) {
        val window = activity.window
        // pureBlackScreen is kept in the signature for existing callers; in dark mode every screen is #010101 now.
        window.statusBarColor = if (isLight) LIGHT_BG else SYSTEM_BLACK
        window.navigationBarColor = SYSTEM_BLACK
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = isLight
        controller.isAppearanceLightNavigationBars = false
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
