package com.xiaoqi.companion.data.repository

import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import com.xiaoqi.companion.data.db.dao.InventoryDao
import com.xiaoqi.companion.data.db.entity.InventoryItemEntity
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

interface InventoryRepository {
    fun observeInventory(): Flow<List<InventoryItemEntity>>

    /** 正数累加（采购）、负数扣减（meal 消耗）；新品不存在且 delta<=0 时不建行，返回 null。 */
    suspend fun adjustStock(name: String, brand: String?, unit: String, quantityDelta: Double): InventoryItemEntity?

    suspend fun setLowStockThreshold(name: String, brand: String?, threshold: Double?): Boolean

    suspend fun deleteItem(name: String, brand: String?)
}

class InventoryRepositoryImpl @Inject constructor(
    private val inventoryDao: InventoryDao,
) : InventoryRepository {

    override fun observeInventory(): Flow<List<InventoryItemEntity>> = inventoryDao.observeAll()

    override suspend fun adjustStock(
        name: String,
        brand: String?,
        unit: String,
        quantityDelta: Double,
    ): InventoryItemEntity? = withContext(Dispatchers.IO) {
        val id = inventoryId(name, brand)
        val existing = inventoryDao.getById(id)
        val now = System.currentTimeMillis()
        val result = if (existing == null) {
            if (quantityDelta <= 0) {
                null
            } else {
                val entity = InventoryItemEntity(
                    id = id,
                    name = name.trim(),
                    brand = brand?.trim()?.takeIf { it.isNotBlank() },
                    unit = unit,
                    currentQuantity = quantityDelta,
                    createdAt = now,
                    updatedAt = now,
                )
                inventoryDao.upsert(entity)
                entity
            }
        } else {
            // 同名同牌但单位不同（板 vs 个）：保守视为同一物，沿用首个单位累加
            inventoryDao.adjustQuantity(id, quantityDelta, now)
            inventoryDao.getById(id)
        }
        AppLogger.info(
            LogTags.Record,
            "inventory_stock_adjusted",
            "itemId" to id,
            "delta" to quantityDelta,
            "quantity" to (result?.currentQuantity ?: 0.0),
        )
        result
    }

    override suspend fun setLowStockThreshold(name: String, brand: String?, threshold: Double?): Boolean =
        withContext(Dispatchers.IO) {
            val id = inventoryId(name, brand)
            val exists = inventoryDao.getById(id) != null
            if (exists) {
                inventoryDao.setLowStockThreshold(id, threshold, System.currentTimeMillis())
            }
            exists
        }

    override suspend fun deleteItem(name: String, brand: String?) = withContext(Dispatchers.IO) {
        inventoryDao.deleteById(inventoryId(name, brand))
        AppLogger.info(LogTags.Record, "inventory_item_deleted", "itemId" to inventoryId(name, brand))
    }

    private fun inventoryId(name: String, brand: String?): String =
        "${name.trim().lowercase()}|${brand?.trim()?.lowercase().orEmpty()}"
}
