package com.xiaoqi.companion.core.record

import com.xiaoqi.companion.data.db.converter.MealType
import com.xiaoqi.companion.data.db.converter.RecordSource

data class MealItemDraft(
    val name: String,
    val portionText: String,
    val calories: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val isEstimate: Boolean = true,
)

data class MealRecordDraft(
    val mealType: MealType,
    val source: RecordSource,
    val items: List<MealItemDraft>,
    val confidence: Double = 0.0,
    val imageLocalPath: String? = null,
    val rawNote: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

data class PurchaseItemDraft(
    val name: String,
    val brand: String? = null,
    val quantity: Double,
    val unit: String,
    val unitPrice: Double? = null,
)

data class PurchaseRecordDraft(
    val items: List<PurchaseItemDraft>,
    val source: RecordSource,
    val storeName: String? = null,
    val totalCost: Double? = null,
    val imageLocalPath: String? = null,
    val purchasedAt: Long = System.currentTimeMillis(),
)
