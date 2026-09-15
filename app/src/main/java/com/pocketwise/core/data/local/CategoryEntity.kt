package com.pocketwise.core.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.pocketwise.core.model.Category
import com.pocketwise.core.model.CategoryIcon

@Entity(tableName = "categories", indices = [Index(value = ["name"], unique = true)])
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String = CategoryIcon.OTHER.name,
    // defaultValue must match MIGRATION_4_5's column DEFAULT or Room's schema check fails.
    @ColumnInfo(defaultValue = "0") val monthlyBudget: Double = 0.0
)

fun CategoryEntity.toDomain() = Category(
    id = id,
    name = name,
    icon = runCatching { CategoryIcon.valueOf(icon) }.getOrDefault(CategoryIcon.OTHER),
    monthlyBudget = monthlyBudget
)

fun Category.toEntity() = CategoryEntity(id, name, icon.name, monthlyBudget)
