package com.xiaoqi.companion.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// 无外键约束（与 tool_calls/ memories 一致），删除随 MealRecordDao.deleteRecordWithItems 级联
@Entity(
    tableName = "meal_items",
    indices = [Index(value = ["recordId"])],
)
data class MealItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recordId: String,
    val name: String,
    val portionText: String,
    val estCalories: Double,
    val estProteinG: Double,
    val estCarbsG: Double,
    val estFatG: Double,
    // 用户改过数值后置 false，与模型估算区分（design.md 估算诚实原则）
    val isEstimate: Boolean = true,
)
