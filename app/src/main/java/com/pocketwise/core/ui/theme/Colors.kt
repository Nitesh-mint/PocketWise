package com.pocketwise.core.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Single source of truth for the app's colors — ported from the web app's
 * "cool slate-blue professional SaaS + indigo brand" theme (OKLCH values
 * converted to sRGB hex). Change a value here to re-theme the whole app;
 * nothing else should hardcode a color.
 */
object AppColors {
    // Light theme surfaces (--background/--card/--foreground)
    val backgroundLight = Color(0xFFFBFCFD)
    val surfaceLight = Color(0xFFF9FAFC)
    val onSurfaceLight = Color(0xFF0A0D12)
    val secondaryLight = Color(0xFFEEF0F4)
    val mutedForegroundLight = Color(0xFF5F646B)
    val borderLight = Color(0xFFE5E5E5)

    // Dark theme surfaces
    val backgroundDark = Color(0xFF090D14)
    val surfaceDark = Color(0xFF13171E)
    val onSurfaceDark = Color(0xFFF5F5F5)
    val secondaryDark = Color(0xFF20242B)
    val mutedForegroundDark = Color(0xFF9499A0)

    // Neutral "primary" (near-black/white) — the inverted hero-card color,
    // same value pair in both themes (dark mode's hero card is the light one).
    val nearBlack = Color(0xFF0E1217)
    val nearWhite = Color(0xFFF8F8F8)

    // Brand — indigo accent. One accent color app-wide: nav "+" button,
    // selected states, FAB. Keep it out of secondary/neutral UI.
    val brandLight = Color(0xFF296CD8)
    val brandDark = Color(0xFF4983E5)

    // Semantic (not yet wired into ColorScheme — no success/warning/error
    // UI exists yet; kept here ready for when budget-status features need them)
    val success = Color(0xFF3A9742)
    val warning = Color(0xFFE49E22)
    val destructiveLight = Color(0xFFE7000B)
    val destructiveDark = Color(0xFFFF6467)
    val dashPositive = Color(0xFF439976)
    val dashNegative = Color(0xFFC15C62)
    val dashCaution = Color(0xFFD8B260)
}

val PocketWiseLightColors = lightColorScheme(
    background = AppColors.backgroundLight,
    onBackground = AppColors.onSurfaceLight,
    surface = AppColors.surfaceLight,
    onSurface = AppColors.onSurfaceLight,
    surfaceVariant = AppColors.secondaryLight,
    onSurfaceVariant = AppColors.mutedForegroundLight,
    primary = AppColors.brandLight,
    onPrimary = AppColors.nearWhite,
    primaryContainer = AppColors.nearBlack,
    onPrimaryContainer = AppColors.nearWhite,
    secondary = AppColors.secondaryLight,
    onSecondary = AppColors.nearBlack,
    secondaryContainer = AppColors.secondaryLight,
    onSecondaryContainer = AppColors.nearBlack,
    outline = AppColors.borderLight,
    error = AppColors.destructiveLight,
    onError = Color.White,
)

val PocketWiseDarkColors = darkColorScheme(
    background = AppColors.backgroundDark,
    onBackground = AppColors.onSurfaceDark,
    surface = AppColors.surfaceDark,
    onSurface = AppColors.onSurfaceDark,
    surfaceVariant = AppColors.secondaryDark,
    onSurfaceVariant = AppColors.mutedForegroundDark,
    primary = AppColors.brandDark,
    onPrimary = AppColors.nearWhite,
    // hero card inverts the other way in dark mode — a light card popping
    // against the dark page, same "invert for emphasis" logic as before
    primaryContainer = AppColors.nearWhite,
    onPrimaryContainer = AppColors.nearBlack,
    secondary = AppColors.secondaryDark,
    onSecondary = AppColors.onSurfaceDark,
    secondaryContainer = AppColors.secondaryDark,
    onSecondaryContainer = AppColors.onSurfaceDark,
    outline = Color.White.copy(alpha = 0.1f),
    error = AppColors.destructiveDark,
    onError = Color.White,
)
