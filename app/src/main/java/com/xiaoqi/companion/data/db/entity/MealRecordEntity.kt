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
    val imageLocalPath: String? = null,
    val rawNote: String? = null,
)
