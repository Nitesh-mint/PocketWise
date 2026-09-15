package com.pocketwise.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

data class BottomNavDestination(val route: String, val label: String, val icon: ImageVector)

val BottomNavDestinations = listOf(
    BottomNavDestination("home", "Home", Icons.Filled.Home),
    BottomNavDestination("reports", "Reports", Icons.Filled.PieChart),
    BottomNavDestination("categories", "Categories", Icons.Filled.Sell),
)

/**
 * Real navigation between the app's actual top-level screens — selection
 * follows the current back-stack destination. The "+" add-expense action
 * lives in a proper FloatingActionButton (Scaffold-level, see MainActivity),
 * not in here: it's a one-off create action, not a destination you stay on,
 * so it doesn't belong in a NavigationBarItem.
 */
@Composable
fun BottomNavBar(currentRoute: String?, onNavigate: (String) -> Unit) {
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)

        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp
        ) {
            BottomNavDestinations.forEach { destination ->
                val selected = currentRoute == destination.route
                NavigationBarItem(
                    selected = selected,
                    onClick = { onNavigate(destination.route) },
                    icon = { Icon(destination.icon, contentDescription = destination.label) },
                    label = { Text(destination.label) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                        indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                )
            }
        }
    }
}
