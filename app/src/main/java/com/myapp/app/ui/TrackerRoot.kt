package com.myapp.app.ui

import android.app.Activity
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import com.myapp.app.ui.popup.AllPopups
import com.myapp.app.ui.popup.Popups
import com.myapp.app.data.AppSession
import com.myapp.app.data.AppStore
import com.myapp.app.ui.components.AppBackdrop
import com.myapp.app.ui.components.BottomNavBar
import com.myapp.app.ui.screens.BootSplash
import com.myapp.app.ui.screens.FundDetailsScreen
import com.myapp.app.ui.screens.HomeScreen
import com.myapp.app.ui.screens.ProfileScreen
import com.myapp.app.ui.screens.StatsScreen
import com.myapp.app.ui.screens.LogsScreen
import com.myapp.app.ui.theme.AppTheme
import com.myapp.app.ui.theme.SystemBars
import com.myapp.app.ui.theme.TrackerTheme
import kotlinx.coroutines.delay

/** The splash stays up for at least this long from app start (was 2.2 s; shortened so the app opens faster). */
private const val MIN_SPLASH_MS = 1200L

@Composable
fun TrackerRoot(activity: Activity) {
    val isLight = AppSession.isLight
    val isBangla = AppSession.isBangla
    val lockUp = com.myapp.app.ui.popup.LockState.pinVisible || com.myapp.app.ui.popup.LockState.patternVisible ||
        com.myapp.app.ui.popup.LockState.biometricVisible
    val onboarding = !AppStore.observe().onboardingComplete
    val pureBlack = AppSession.pureBlackScreen || lockUp || onboarding

    // Status bar follows the theme (and goes pure black on Welcome/Lock screens in dark mode).
    LaunchedEffect(isLight, pureBlack) {
        SystemBars.apply(activity, isLight, pureBlack)
    }

    TrackerTheme(isLight = isLight, isBangla = isBangla) {
        var bootPending by remember { mutableStateOf(true) }
        LaunchedEffect(Unit) {
            val elapsed = SystemClock.elapsedRealtime() - AppSession.bootStartMs
            delay((MIN_SPLASH_MS - elapsed).coerceAtLeast(0L))
            bootPending = false
            // The lock appears together with the splash going away (a finished profile only).
            if (AppStore.state.onboardingComplete) com.myapp.app.ui.popup.LockState.showStartupLock()
        }

        // initReminders(): every launch asks for the notification permission (the system only shows the
        // dialog while it is still allowed to) and registers the alarms again with the current wording.
        val appContext = activity.applicationContext
        LaunchedEffect(Unit) {
            com.myapp.app.ui.popup.ReminderPanel.requestAtLaunch()
            delay(1500)   // keep the first frames free: alarm registration is not urgent
            com.myapp.app.reminder.Reminders.scheduleAll(appContext)
        }
        // A new / edited / settled / deleted debt changes its reminder: re-register shortly after the change.
        val revision = com.myapp.app.data.AppStore.revision
        LaunchedEffect(revision) {
            delay(800)
            com.myapp.app.reminder.Reminders.scheduleDebts(appContext)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppTheme.colors.bodyBg),
        ) {
            AppBackdrop()

            // Frosted blur behind any open popup (the old backdrop-filter: blur(20px)).
            val blurRadius by animateDpAsState(
                targetValue = if (Popups.anyVisible) 20.dp else 0.dp,
                animationSpec = tween(200),
                label = "popupBlur",
            )

            // The app screens appear (fade in) once the splash is leaving, like #app-root before.
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = if (bootPending) 0f else 1f }
                    .blur(blurRadius),
            ) {
                when (AppUi.tab) {
                    Tab.Home -> HomeScreen()
                    Tab.Logs -> LogsScreen()
                    Tab.Stats -> StatsScreen()
                    Tab.Profile -> ProfileScreen()
                }
                BottomNavBar(Modifier.align(Alignment.BottomCenter))
                FundDetailsScreen()
                com.myapp.app.ui.popup.OnboardingScreen()
            }

            AllPopups()
            com.myapp.app.ui.popup.BusyOverlay()
            com.myapp.app.ui.popup.UpdateDownloadOverlay()
            com.myapp.app.ui.popup.LockHost()

            BootSplash(
                isLight = isLight,
                isBangla = isBangla,
                visible = bootPending,
            )
        }
    }
}
