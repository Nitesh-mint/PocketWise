package com.pocketwise.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * These three are momentary actions (voice capture, manual add, AI
 * extraction), not persistent nav destinations — so `selected` is
 * intentionally never toggled. "Add" is the one action people reach for
 * most, so it's sized and colored to read as primary; the other two are
 * deliberately quieter (muted tone) so they don't visually compete with it.
 */
@Composable
fun BottomNavBar(
    onMicClick: () -> Unit = {},
    onAddClick: () -> Unit = {},
    onMagicClick: () -> Unit = {}
) {
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)

        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp
        ) {
            val secondaryColors = NavigationBarItemDefaults.colors(
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                indicatorColor = Color.Transparent
            )

            NavigationBarItem(
                selected = false,
                onClick = onMicClick,
                icon = { Icon(Icons.Filled.Mic, contentDescription = "Voice entry") },
                colors = secondaryColors
            )
            NavigationBarItem(
                selected = false,
                onClick = onAddClick,
                icon = {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .shadow(elevation = 4.dp, shape = CircleShape, clip = false)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Add expense", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                },
                colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent)
            )
            NavigationBarItem(
                selected = false,
                onClick = onMagicClick,
                icon = { Icon(Icons.Filled.AutoAwesome, contentDescription = "AI extraction") },
                colors = secondaryColors
            )
        }
    }
}
