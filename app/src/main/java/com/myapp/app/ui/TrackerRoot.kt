package com.myapp.app.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import com.myapp.app.ui.popup.AllPopups
import com.myapp.app.ui.popup.Popups
import com.myapp.app.data.AppSession
import com.myapp.app.data.AppStore
import com.myapp.app.ui.components.AppBackdrop
import com.myapp.app.ui.components.BottomNavBar
import com.myapp.app.ui.screens.FundDetailsScreen
import com.myapp.app.ui.screens.HomeScreen
import com.myapp.app.ui.screens.ProfileScreen
import com.myapp.app.ui.screens.StatsScreen
import com.myapp.app.ui.screens.LogsScreen
import com.myapp.app.ui.theme.AppTheme
import com.myapp.app.ui.theme.SystemBars
import com.myapp.app.ui.theme.TrackerTheme
import kotlinx.coroutines.delay

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
        // The startup lock is already raised by MainActivity before the first frame. While it is up at
        // launch the app screens stay hidden underneath (they are shown again once it is unlocked).
        var startupGuard by remember { mutableStateOf(lockUp) }
        LaunchedEffect(lockUp) { if (!lockUp) startupGuard = false }
        // Tell the native launch screen the first frame is ready (it then goes away with no flash).
        LaunchedEffect(Unit) {
            androidx.compose.runtime.withFrameNanos { }
            AppSession.uiReady = true
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
            // The radius is eased in/out in the draw phase (no recomposition) and stays on until the
            // blur clears within a moment of the popup closing (no lingering blur).
            val blurOn = Popups.anyVisible || com.myapp.app.util.UpdateChecker.downloading
            val blurDp by animateFloatAsState(
                targetValue = if (blurOn) 20f else 0f,
                // Blur comes up at once with the popup / sheet (60 ms), and leaves softly (180 ms).
                animationSpec = tween(
                    durationMillis = if (blurOn) 60 else 180,
                    easing = if (blurOn) LinearEasing else com.myapp.app.ui.popup.DropdownEase,
                ),
                label = "popupBlur",
            )

            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = if (startupGuard) 0f else 1f
                        val r = blurDp * density
                        renderEffect = if (android.os.Build.VERSION.SDK_INT >= 31 && r > 0.5f) BlurEffect(r, r, TileMode.Clamp) else null
                    },
            ) {
                // Tabs stay alive once opened (the others are warmed up quietly after launch), so a tap
                // only shows/hides a ready screen instead of building a whole screen from scratch.
                var warm by remember { mutableStateOf(setOf(Tab.Home)) }
                val current = AppUi.tab
                val alive = warm + current
                LaunchedEffect(current) { warm = warm + current }
                LaunchedEffect(Unit) {
                    delay(600)
                    for (t in listOf(Tab.Logs, Tab.Stats, Tab.Profile)) {
                        delay(350)
                        warm = warm + t
                    }
                }
                // A text field left focused on a hidden tab must not keep the keyboard open.
                val focus = LocalFocusManager.current
                LaunchedEffect(current) { focus.clearFocus() }

                Tab.values().forEach { t ->
                    key(t) {
                        if (t in alive) {
                            TabLayer(t) {
                                when (t) {
                                    Tab.Home -> HomeScreen()
                                    Tab.Logs -> LogsScreen()
                                    Tab.Stats -> StatsScreen()
                                    Tab.Profile -> ProfileScreen()
                                }
                            }
                        }
                    }
                }
                BottomNavBar(Modifier.align(Alignment.BottomCenter))
                FundDetailsScreen()
                com.myapp.app.ui.popup.OnboardingScreen()
            }

            AllPopups()
            com.myapp.app.ui.popup.BusyOverlay()
            com.myapp.app.ui.popup.UpdateDownloadOverlay()
            com.myapp.app.ui.popup.LockHost()

        }
    }
}

/**
 * One tab's screen. Only the current tab is placed (drawn + touchable); the others stay composed
 * but are not placed, so they cost nothing to draw and cannot catch touches.
 */
@Composable
private fun TabLayer(tab: Tab, content: @Composable () -> Unit) {
    val active = AppUi.tab == tab
    Box(
        Modifier
            .fillMaxSize()
            .layout { measurable, constraints ->
                val p = measurable.measure(constraints)
                layout(p.width, p.height) { if (active) p.place(0, 0) }
            },
    ) { content() }
}
