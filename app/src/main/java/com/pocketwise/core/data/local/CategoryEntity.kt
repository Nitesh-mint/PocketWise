package com.pocketwise.core.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.pocketwise.core.model.Category
import com.pocketwise.core.model.CategoryIcon

@Entity(tableName = "categories", indices = [Index(value = ["name"], unique = true)])
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String = CategoryIcon.OTHER.name
)

fun CategoryEntity.toDomain() = Category(
    id = id,
    name = name,
    icon = runCatching { CategoryIcon.valueOf(icon) }.getOrDefault(CategoryIcon.OTHER)
)

fun Category.toEntity() = CategoryEntity(id, name, icon.name)
