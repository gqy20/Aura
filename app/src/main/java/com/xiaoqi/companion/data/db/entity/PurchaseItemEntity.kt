package com.xiaoqi.companion.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// 无外键约束，删除随 PurchaseDao.deletePurchaseWithItems 级联
@Entity(
    tableName = "purchase_items",
    indices = [Index(value = ["recordId"])],
)
data class PurchaseItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recordId: String,
    val name: String,
    val brand: String? = null,
    val quantity: Double,
    val unit: String,
    val unitPrice: Double? = null,
    // 多图场景:该条目来自第几张图(0-based)
    val photoIndex: Int? = null,
)
