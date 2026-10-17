package com.xiaoqi.companion.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.xiaoqi.companion.data.db.converter.RecordSource

@Entity(
    tableName = "purchase_records",
    indices = [Index(value = ["purchasedAt"])],
)
data class PurchaseRecordEntity(
    @PrimaryKey val id: String,
    val purchasedAt: Long,
    val updatedAt: Long,
    val source: RecordSource,
    val storeName: String? = null,
    val totalCost: Double? = null,
    val imageLocalPath: String? = null,
)
