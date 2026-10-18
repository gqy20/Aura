package com.xiaoqi.companion.core.record

import com.xiaoqi.companion.data.db.converter.MealType
import com.xiaoqi.companion.data.db.converter.RecordSource

/** 记录域:餐食 / 采购。提取器按照片内容分类,确认页可手动切换兜底。 */
enum class RecordDomain { MEAL, PURCHASE }

data class MealItemDraft(
    val name: String,
    val portionText: String,
    val calories: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val isEstimate: Boolean = true,
    // 模型估的可食部克重;两段式提取(密度表 × 克重)与后续 UI 微调的依据,不持久化到 meal_items
    val estimatedGrams: Double = 0.0,
    // 多图场景:该条目来自第几张图(0-based)
    val photoIndex: Int? = null,
)

data class MealRecordDraft(
    val mealType: MealType,
    val source: RecordSource,
    val items: List<MealItemDraft>,
    val confidence: Double = 0.0,
    // 多图逗号分隔路径
    val imageLocalPath: String? = null,
    val rawNote: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    // EXIF 拍摄时刻:餐次自动判定锚点
    val photoTakenAt: Long? = null,
    val lat: Double? = null,
    val lon: Double? = null,
)

data class PurchaseItemDraft(
    val name: String,
    val brand: String? = null,
    val quantity: Double,
    val unit: String,
    val unitPrice: Double? = null,
    // 多图场景:该条目来自第几张图(0-based)
    val photoIndex: Int? = null,
)

data class PurchaseRecordDraft(
    val items: List<PurchaseItemDraft>,
    val source: RecordSource,
    val storeName: String? = null,
    val totalCost: Double? = null,
    // 多图逗号分隔路径
    val imageLocalPath: String? = null,
    val purchasedAt: Long = System.currentTimeMillis(),
    // EXIF:拍摄时刻与 GPS
    val photoTakenAt: Long? = null,
    val lat: Double? = null,
    val lon: Double? = null,
)
