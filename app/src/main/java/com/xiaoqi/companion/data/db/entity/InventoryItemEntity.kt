package com.xiaoqi.companion.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// id 是稳定身份键 normalize(name)+normalize(brand)，采购累加 / meal 消耗按此 upsert
@Entity(
    tableName = "inventory_items",
    indices = [Index(value = ["name"]), Index(value = ["updatedAt"])],
)
data class InventoryItemEntity(
    @PrimaryKey val id: String,
    val name: String,
    val brand: String? = null,
    val unit: String,
    val currentQuantity: Double,
    val lowStockThreshold: Double? = null,
    val createdAt: Long,
    val updatedAt: Long,
)
