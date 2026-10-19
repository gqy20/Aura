package com.xiaoqi.companion.feature.record

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import com.xiaoqi.companion.data.db.dao.MealRecordWithItems
import com.xiaoqi.companion.data.db.dao.PurchaseRecordWithItems
import com.xiaoqi.companion.data.record.RecordExporter
import com.xiaoqi.companion.data.record.RecordImageStore
import com.xiaoqi.companion.data.repository.InventoryRepository
import com.xiaoqi.companion.data.repository.MealRecordRepository
import com.xiaoqi.companion.data.repository.PurchaseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class RecordCounts(
    val meals: Int = 0,
    val purchases: Int = 0,
    val inventoryItems: Int = 0,
)

data class RecordExportUiState(
    val exporting: Boolean = false,
    val exportedFile: File? = null,
    val error: String? = null,
    val counts: RecordCounts = RecordCounts(),
)

/**
 * 记录分析导出:三域数据 + 记录原图 → zip 契约(RecordExporter)写到 cache。
 * SAF 保存与系统分享共用导出文件,由 SettingsScreen 的入口触发。
 */
@HiltViewModel
class RecordExportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mealRecordRepository: MealRecordRepository,
    private val purchaseRepository: PurchaseRepository,
    private val inventoryRepository: InventoryRepository,
    private val imageStore: RecordImageStore,
) : ViewModel() {

    private val _state = MutableStateFlow(RecordExportUiState())
    val state: StateFlow<RecordExportUiState> = _state.asStateFlow()

    fun export() {
        if (_state.value.exporting) return
        _state.update { RecordExportUiState(exporting = true) }
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val meals = mealRecordRepository.getAllRecords()
                    val purchases = purchaseRepository.getAllPurchases()
                    val inventory = inventoryRepository.observeInventory().first()
                    val images = collectImages(meals, purchases)
                    val file = File(context.cacheDir, "record-export-${System.currentTimeMillis()}.zip")
                    file.outputStream().use { out ->
                        RecordExporter.writeZip(out, meals, purchases, inventory, images)
                    }
                    AppLogger.info(
                        LogTags.Record,
                        "record_export_completed",
                        "meals" to meals.size,
                        "purchases" to purchases.size,
                        "inventory" to inventory.size,
                        // key 带 image 会被 LogFieldSanitizer 整值脱敏,计数/长度用白名单后缀命名
                        "imageCount" to images.size,
                        "imageByteLength" to images.sumOf { it.bytes.size },
                    )
                    file
                }
            }
            _state.update { state ->
                result.fold(
                    onSuccess = { state.copy(exporting = false, exportedFile = it, error = null) },
                    onFailure = { error ->
                        AppLogger.warn(LogTags.Record, "record_export_failed", "error" to (error.message ?: ""))
                        state.copy(exporting = false, error = "导出失败: ${error.message ?: "未知错误"}")
                    },
                )
            }
        }
    }

    // 图片文件缺失(被外部清理)时跳过该张,导出引用自动降级为 null,不阻断整次导出
    private fun collectImages(
        meals: List<MealRecordWithItems>,
        purchases: List<PurchaseRecordWithItems>,
    ): List<RecordExporter.ExportImage> {
        val paths = meals.flatMap { it.record.imageLocalPath?.split(',') ?: emptyList() } +
            purchases.flatMap { it.record.imageLocalPath?.split(',') ?: emptyList() }
        return paths.filter { it.isNotBlank() }.distinct().mapNotNull { path ->
            val file = imageStore.resolve(path)
            if (file.isFile) RecordExporter.ExportImage(path, file.readBytes()) else null
        }
    }

    fun consumeFile() = _state.update { RecordExportUiState() }

    /** 透明区条数展示(进入设置页时调用一次)。 */
    fun loadCounts() {
        if (_state.value.counts.meals > 0 || _state.value.counts.purchases > 0 || _state.value.counts.inventoryItems > 0) return
        viewModelScope.launch {
            val counts = runCatching {
                withContext(Dispatchers.IO) {
                    RecordCounts(
                        meals = mealRecordRepository.getAllRecords().size,
                        purchases = purchaseRepository.getAllPurchases().size,
                        inventoryItems = inventoryRepository.observeInventory().first().size,
                    )
                }
            }.getOrDefault(RecordCounts())
            _state.update { it.copy(counts = counts) }
        }
    }
}
