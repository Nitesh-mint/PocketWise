package com.pocketwise.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.pocketwise.R

// Single variable font file (Geist, matches --sans/--heading in the web
// theme); Compose resolves each weight from it directly.
val GeistFontFamily = FontFamily(
    Font(R.font.geist, FontWeight.Normal),
    Font(R.font.geist, FontWeight.Medium),
    Font(R.font.geist, FontWeight.SemiBold),
    Font(R.font.geist, FontWeight.Bold),
)

private fun TextStyle.withGeist() = copy(fontFamily = GeistFontFamily)

private val base = Typography()

val AppTypography = Typography(
    displayLarge = base.displayLarge.withGeist(),
    displayMedium = base.displayMedium.withGeist(),
    displaySmall = base.displaySmall.withGeist(),
    headlineLarge = base.headlineLarge.withGeist(),
    headlineMedium = base.headlineMedium.withGeist(),
    headlineSmall = base.headlineSmall.withGeist(),
    titleLarge = base.titleLarge.withGeist(),
    titleMedium = base.titleMedium.withGeist(),
    titleSmall = base.titleSmall.withGeist(),
    bodyLarge = base.bodyLarge.withGeist(),
    bodyMedium = base.bodyMedium.withGeist(),
    bodySmall = base.bodySmall.withGeist(),
    labelLarge = base.labelLarge.withGeist(),
    labelMedium = base.labelMedium.withGeist(),
    labelSmall = base.labelSmall.withGeist(),
)
