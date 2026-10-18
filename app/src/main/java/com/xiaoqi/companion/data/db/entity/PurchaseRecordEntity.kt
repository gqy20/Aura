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
    // 多图逗号分隔路径;单图场景即单路径(向后兼容)
    val imageLocalPath: String? = null,
    // EXIF:拍摄时刻与 GPS(后期店名反查预填的数据源)
    val photoTakenAt: Long? = null,
    val lat: Double? = null,
    val lon: Double? = null,
)
