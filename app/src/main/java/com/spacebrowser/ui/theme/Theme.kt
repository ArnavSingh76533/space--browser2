package com.spacebrowser.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.spacebrowser.core.settings.SpaceSettings
import com.spacebrowser.core.settings.ThemeMode

private fun spaceDarkScheme(accent: Accent, amoled: Boolean) = darkColorScheme(
    primary = accent.color,
    onPrimary = Color(0xFF0B0E1A),
    secondary = accent.secondary,
    onSecondary = Color(0xFF0B0E1A),
    tertiary = accent.secondary,
    background = if (amoled) Amoled else SpaceDeep,
    onBackground = StarWhite,
    surface = if (amoled) AmoledSurface else SpaceSurface,
    onSurface = StarWhite,
    surfaceVariant = if (amoled) AmoledSurface else SpaceSurfaceHigh,
    onSurfaceVariant = MutedStar,
    outline = MutedStar.copy(alpha = 0.5f),
    error = Color(0xFFF87171),
)

private fun spaceLightScheme(accent: Accent) = lightColorScheme(
    primary = accent.color,
    onPrimary = Color.White,
    secondary = accent.secondary,
    onSecondary = Color.White,
    background = DayBackground,
    onBackground = DayOnSurface,
    surface = DaySurface,
    onSurface = DayOnSurface,
    surfaceVariant = DaySurfaceHigh,
    onSurfaceVariant = Color(0xFF565B75),
    outline = Color(0xFFA5A9C4),
    error = Color(0xFFDC2626),
)

private val SpaceTypography = Typography(
    // Wide-tracked labels give the UI its quiet "mission control" voice; the
    // rest of the type stays close to Material defaults for readability.
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, letterSpacing = 0.2.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, letterSpacing = 0.15.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, letterSpacing = 1.2.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 1.6.sp),
)

/** Auto mode: Daylight from 07:00 to 17:59, dark otherwise. */
private fun isNightNow(): Boolean {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return hour < 7 || hour >= 18
}

@Composable
fun isAppInDarkTheme(settings: SpaceSettings): Boolean = when (settings.themeMode) {
    ThemeMode.LIGHT -> false
    ThemeMode.DARK, ThemeMode.AMOLED -> true
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.AUTO -> {
        // Re-evaluate once a minute so the switch happens without a restart.
        var tick by remember { mutableIntStateOf(0) }
        LaunchedEffect(Unit) {
            while (true) {
                kotlinx.coroutines.delay(60_000)
                tick++
            }
        }
        @Suppress("UNUSED_EXPRESSION") tick
        isNightNow()
    }
}

@Composable
fun SpaceTheme(settings: SpaceSettings, content: @Composable () -> Unit) {
    val dark = isAppInDarkTheme(settings)
    val amoled = settings.themeMode == ThemeMode.AMOLED
    val accent = accentAt(settings.accentIndex)

    val scheme = when {
        settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        dark -> spaceDarkScheme(accent, amoled)
        else -> spaceLightScheme(accent)
    }

    MaterialTheme(
        colorScheme = scheme,
        typography = SpaceTypography,
        content = content,
    )
}
