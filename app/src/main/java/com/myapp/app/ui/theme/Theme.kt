package com.myapp.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density

val LocalAppColors = staticCompositionLocalOf { darkAppColors() }
val LocalIsBangla = staticCompositionLocalOf { false }

object AppTheme {
    val colors: AppColors
        @Composable
        @ReadOnlyComposable
        get() = LocalAppColors.current

    val isBangla: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalIsBangla.current
}

/**
 * The old web version used the system font (Open Sans was never bundled, so Android fell back
 * to the default system font; Bangla uses the system Bangla font).
 */
val AppFont: FontFamily = FontFamily.Default

/**
 * Wraps the whole app. System font-size scaling is switched OFF (same as the old version
 * locking text zoom to 100%), so layouts look identical on every phone and tablet.
 */
@Composable
fun TrackerTheme(
    isLight: Boolean,
    isBangla: Boolean,
    content: @Composable () -> Unit,
) {
    val colors = remember(isLight) { if (isLight) lightAppColors() else darkAppColors() }
    val density = LocalDensity.current
    val fixedDensity = remember(density.density) { Density(density = density.density, fontScale = 1f) }
    CompositionLocalProvider(
        LocalAppColors provides colors,
        LocalIsBangla provides isBangla,
        LocalDensity provides fixedDensity,
        content = content,
    )
}
