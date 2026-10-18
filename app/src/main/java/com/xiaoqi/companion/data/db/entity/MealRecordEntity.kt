package com.xiaoqi.companion.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.xiaoqi.companion.data.db.converter.MealType
import com.xiaoqi.companion.data.db.converter.RecordSource

@Entity(
    tableName = "meal_records",
    indices = [Index(value = ["createdAt"])],
)
data class MealRecordEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val updatedAt: Long,
    val mealType: MealType,
    val source: RecordSource,
    // 头表总量 = meal_items 明细行之和，保存时由 Repository 汇总写入
    val totalCalories: Double,
    val totalProteinG: Double,
    val totalCarbsG: Double,
    val totalFatG: Double,
    val confidence: Double,
    // 多图逗号分隔路径;单图场景即单路径(向后兼容)
    val imageLocalPath: String? = null,
    val rawNote: String? = null,
    // EXIF 拍摄时刻:餐次自动判定的锚点,补记场景下比 createdAt 更接近真实用餐时间
    val photoTakenAt: Long? = null,
    val lat: Double? = null,
    val lon: Double? = null,
)
