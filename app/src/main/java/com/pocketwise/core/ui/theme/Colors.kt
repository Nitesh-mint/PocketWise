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
    // One step lighter than secondaryDark — reserved for the one card that
    // should read as "more elevated than everything else": still a plain
    // neutral, not a color, consistent with the rest of the monochrome theme.
    val heroSurfaceDark = Color(0xFF262B33)

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

    // Chart categorical palette — the one place real hue is allowed, because
    // here color IS data (category identity). Fixed order, validated against
    // the card surfaces (secondaryLight / secondaryDark): adjacent colorblind
    // ΔE ≥ 9.1 light / 8.4 dark, normal-vision ΔE ≥ 19.3. The ORDER is the
    // colorblind-safety mechanism — never re-order, never cycle or generate a
    // 9th. Four light hues sit under 3:1 on the card, so charts must always
    // show text labels next to them (never color alone).
    val chartCategoricalLight = listOf(
        Color(0xFF2A78D6), Color(0xFFEB6834), Color(0xFF1BAF7A), Color(0xFFEDA100),
        Color(0xFFE87BA4), Color(0xFF008300), Color(0xFF4A3AA7), Color(0xFFE34948),
    )
    val chartCategoricalDark = listOf(
        Color(0xFF3987E5), Color(0xFFD95926), Color(0xFF199E70), Color(0xFFC98500),
        Color(0xFFD55181), Color(0xFF008300), Color(0xFF9085E9), Color(0xFFE66767),
    )
    // "Other" / overflow categories — a neutral, not a 9th hue.
    val chartNeutralLight = Color(0xFFA3A7AE)
    val chartNeutralDark = Color(0xFF5C616A)
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
    // Dedicated role for the dashboard hero card — kept separate from
    // primaryContainer (used by the nav "+" FAB) so the two can differ in
    // dark mode without fighting over the same color slot.
    tertiaryContainer = AppColors.nearBlack,
    onTertiaryContainer = AppColors.nearWhite,
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
    // FAB stays the neutral black/white inversion (white button in dark mode).
    primaryContainer = AppColors.nearWhite,
    onPrimaryContainer = AppColors.nearBlack,
    secondary = AppColors.secondaryDark,
    onSecondary = AppColors.onSurfaceDark,
    secondaryContainer = AppColors.secondaryDark,
    onSecondaryContainer = AppColors.onSurfaceDark,
    // Hero card: a calm, elevated dark neutral — not a color, not an
    // inverted-white card. This app's whole theme is deliberately monochrome
    // with accent color reserved for tiny, rare interactive details; the
    // hero should stand out through elevation and scale, not saturation.
    tertiaryContainer = AppColors.heroSurfaceDark,
    onTertiaryContainer = AppColors.onSurfaceDark,
    outline = Color.White.copy(alpha = 0.1f),
    error = AppColors.destructiveDark,
    onError = Color.White,
)
