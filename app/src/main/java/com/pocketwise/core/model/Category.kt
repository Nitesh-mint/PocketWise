package com.pocketwise.core.model

data class Category(val id: Long = 0, val name: String, val icon: CategoryIcon = CategoryIcon.OTHER)

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
