package com.pocketwise.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Ported from the web theme's --radius: 0.75rem (12px) base scale:
// sm=0.6x, md=0.8x, lg=1x, xl=1.4x, 2xl=1.8x.
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(7.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(17.dp),
    extraLarge = RoundedCornerShape(22.dp),
)
