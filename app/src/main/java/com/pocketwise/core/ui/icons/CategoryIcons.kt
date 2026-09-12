package com.pocketwise.core.ui.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalGroceryStore
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.ui.graphics.vector.ImageVector
import com.pocketwise.core.model.CategoryIcon

// The Compose-specific glyph for each icon identifier — kept out of
// core/model so the domain layer has no Compose dependency.
fun CategoryIcon.imageVector(): ImageVector = when (this) {
    CategoryIcon.FOOD -> Icons.Filled.Restaurant
    CategoryIcon.GROCERIES -> Icons.Filled.LocalGroceryStore
    CategoryIcon.TRANSPORT -> Icons.Filled.DirectionsBus
    CategoryIcon.BILLS -> Icons.AutoMirrored.Filled.ReceiptLong
    CategoryIcon.ENTERTAINMENT -> Icons.Filled.Movie
    CategoryIcon.SHOPPING -> Icons.Filled.ShoppingBag
    CategoryIcon.HEALTH -> Icons.Filled.LocalHospital
    CategoryIcon.HOME -> Icons.Filled.Home
    CategoryIcon.TRAVEL -> Icons.Filled.Flight
    CategoryIcon.FITNESS -> Icons.Filled.FitnessCenter
    CategoryIcon.PETS -> Icons.Filled.Pets
    CategoryIcon.EDUCATION -> Icons.Filled.School
    CategoryIcon.COFFEE -> Icons.Filled.Coffee
    CategoryIcon.GAMES -> Icons.Filled.SportsEsports
    CategoryIcon.GIFTS -> Icons.Filled.CardGiftcard
    CategoryIcon.SAVINGS -> Icons.Filled.Savings
    CategoryIcon.OTHER -> Icons.Filled.Category
}

// Fixed order for the icon-picker grid.
val PickableCategoryIcons = CategoryIcon.entries
