package com.xiaoqi.companion.data.repository

import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import com.xiaoqi.companion.core.record.PurchaseRecordDraft
import com.xiaoqi.companion.data.db.dao.PurchaseDao
import com.xiaoqi.companion.data.db.dao.PurchaseRecordWithItems
import com.xiaoqi.companion.data.db.entity.PurchaseItemEntity
import com.xiaoqi.companion.data.db.entity.PurchaseRecordEntity
import com.xiaoqi.companion.data.record.RecordImageStore
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

interface PurchaseRepository {
    fun observeRecentPurchases(limit: Int = 50): Flow<List<PurchaseRecordWithItems>>

    /** 保存采购单；adjustInventory=true 时同步把每个明细行累加进库存。 */
    suspend fun savePurchase(draft: PurchaseRecordDraft, adjustInventory: Boolean): String

    // 删除采购单不回滚库存（数量已流入现实，回滚反而失真）；纠错时手动 adjustStock 负数
    suspend fun deletePurchase(recordId: String)

    /** 全量快照(导出用):按时间正序,含明细。 */
    suspend fun getAllPurchases(): List<PurchaseRecordWithItems>
}

class PurchaseRepositoryImpl @Inject constructor(
    private val purchaseDao: PurchaseDao,
    private val inventoryRepository: InventoryRepository,
    private val imageStore: RecordImageStore,
) : PurchaseRepository {

    override fun observeRecentPurchases(limit: Int): Flow<List<PurchaseRecordWithItems>> =
        purchaseDao.observeRecent(limit)

    override suspend fun savePurchase(draft: PurchaseRecordDraft, adjustInventory: Boolean): String =
        withContext(Dispatchers.IO) {
            val id = UUID.randomUUID().toString()
            purchaseDao.insertPurchaseWithItems(
                PurchaseRecordEntity(
                    id = id,
                    purchasedAt = draft.purchasedAt,
                    updatedAt = System.currentTimeMillis(),
                    source = draft.source,
                    storeName = draft.storeName?.trim()?.takeIf { it.isNotBlank() },
                    totalCost = draft.totalCost ?: draft.items.sumOf { (it.unitPrice ?: 0.0) * it.quantity }.takeIf { it > 0.0 },
                    imageLocalPath = draft.imageLocalPath,
                    photoTakenAt = draft.photoTakenAt,
                    lat = draft.lat,
                    lon = draft.lon,
                ),
                draft.items.map { item ->
                    PurchaseItemEntity(
                        recordId = id,
                        name = item.name,
                        brand = item.brand?.trim()?.takeIf { it.isNotBlank() },
                        quantity = item.quantity,
                        unit = item.unit,
                        unitPrice = item.unitPrice,
                        photoIndex = item.photoIndex,
                    )
                },
            )
            if (adjustInventory) {
                draft.items.forEach { item ->
                    inventoryRepository.adjustStock(item.name, item.brand, item.unit, item.quantity)
                }
            }
            AppLogger.info(
                LogTags.Record,
                "purchase_record_saved",
                "recordId" to id,
                "itemCount" to draft.items.size,
                "adjustInventory" to adjustInventory,
            )
            id
        }

    override suspend fun deletePurchase(recordId: String) = withContext(Dispatchers.IO) {
        purchaseDao.getRecord(recordId)?.imageLocalPath
            ?.split(',')?.filter { it.isNotBlank() }?.forEach { imageStore.delete(it) }
        purchaseDao.deletePurchaseWithItems(recordId)
        AppLogger.info(LogTags.Record, "purchase_record_deleted", "recordId" to recordId)
    }

    override suspend fun getAllPurchases(): List<PurchaseRecordWithItems> =
        withContext(Dispatchers.IO) { purchaseDao.observeRecent(Int.MAX_VALUE).let { it.first() } }
}
