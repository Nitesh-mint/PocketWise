package com.pocketwise.core.model

// monthlyBudget 0.0 = no limit (same convention as the overall monthly budget).
data class Category(
    val id: Long = 0,
    val name: String,
    val icon: CategoryIcon = CategoryIcon.OTHER,
    val monthlyBudget: Double = 0.0
)

data class DefaultCategory(val name: String, val icon: CategoryIcon)

val DefaultCategories = listOf(
    DefaultCategory("Food & Dining", CategoryIcon.FOOD),
    DefaultCategory("Groceries", CategoryIcon.GROCERIES),
    DefaultCategory("Transport", CategoryIcon.TRANSPORT),
    DefaultCategory("Bills & Utilities", CategoryIcon.BILLS),
    DefaultCategory("Entertainment", CategoryIcon.ENTERTAINMENT),
    DefaultCategory("Shopping", CategoryIcon.SHOPPING),
    DefaultCategory("Health", CategoryIcon.HEALTH),
    DefaultCategory("Other", CategoryIcon.OTHER),
)
