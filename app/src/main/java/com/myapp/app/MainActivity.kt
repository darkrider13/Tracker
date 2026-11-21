package com.myapp.app

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.myapp.app.data.AppSession
import com.myapp.app.data.AppStore
import com.myapp.app.ui.TrackerRoot
import com.myapp.app.ui.popup.installPopupHooks
import com.myapp.app.ui.theme.SystemBars
import com.myapp.app.util.I18n
import com.myapp.app.util.syncCustomCategories

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate. No custom splash: the native one is just the plain
        // background colour (transparent icon) and the app shows immediately.
        val splash = installSplashScreen()
        // Keep the plain-background launch screen until the first real frame is ready, so the user
        // never sees a blank / half-built screen, and never sees Home before the lock.
        splash.setKeepOnScreenCondition { !AppSession.uiReady }
        // Remove the launch screen at once (its background equals the app background): the system's own exit
        // animation would run at the same time as our entrance animation and make it look unsteady.
        splash.setOnExitAnimationListener { it.remove() }
        super.onCreate(savedInstanceState)

        AppSession.init(applicationContext)
        I18n.init(applicationContext)       // Bangla translation table
        AppStore.init(applicationContext)   // loads saved data (or creates the defaults on first launch)
        syncCustomCategories()              // category chip order (custom ones first)
        installPopupHooks()                 // + button, Transfer, Add Fund open their popups
        com.myapp.app.ui.popup.BackupUi.attach(this)   // file picker for Import
        com.myapp.app.ui.popup.BiometricHelper.activity = this   // fingerprint prompt host
        com.myapp.app.ui.popup.ReminderPanel.attach(this)         // notification permission launcher
        // Edge-to-edge: the page background is drawn under the (transparent, day mode) bars; TrackerRoot
        // pads the content by the bar sizes, so layouts stay exactly where they were.
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        SystemBars.apply(this, AppSession.isLight, AppSession.pureBlackScreen)

        // Security: the lock is raised BEFORE the first frame is composed (not after it), so Home can
        // never flash on screen before the PIN / pattern / fingerprint screen.
        if (AppStore.state.onboardingComplete) com.myapp.app.ui.popup.LockState.showStartupLock()

        setContent {
            TrackerRoot(activity = this)
        }
    }

    override fun onStart() {
        super.onStart()
        // Back in the foreground: status bar tracks the app theme again.
        SystemBars.apply(this, AppSession.isLight, AppSession.pureBlackScreen)
    }

    override fun onStop() {
        super.onStop()
        // In the background the status bar is always dark so it never leaves a light bar behind.
        SystemBars.forceDark(this)
    }
}
