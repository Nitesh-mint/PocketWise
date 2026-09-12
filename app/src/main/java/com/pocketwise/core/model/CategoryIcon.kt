package com.pocketwise.core.model

// Plain identifiers only — no ImageVector/Compose dependency here, keeping
// core/model decoupled from UI. The actual icon glyphs are mapped in
// core/ui/icons/CategoryIcons.kt.
enum class CategoryIcon {
    FOOD,
    GROCERIES,
    TRANSPORT,
    BILLS,
    ENTERTAINMENT,
    SHOPPING,
    HEALTH,
    HOME,
    TRAVEL,
    FITNESS,
    PETS,
    EDUCATION,
    COFFEE,
    GAMES,
    GIFTS,
    SAVINGS,
    OTHER,
}
