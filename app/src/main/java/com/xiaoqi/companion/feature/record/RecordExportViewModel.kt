package com.xiaoqi.companion.feature.record

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import com.xiaoqi.companion.data.record.RecordExporter
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

data class RecordExportUiState(
    val exporting: Boolean = false,
    val exportedFile: File? = null,
    val error: String? = null,
)

/**
 * 记录分析导出:三域数据 → zip 契约(RecordExporter)写到 cache。
 * SAF 保存与系统分享共用导出文件,由 SettingsScreen 的入口触发。
 */
@HiltViewModel
class RecordExportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mealRecordRepository: MealRecordRepository,
    private val purchaseRepository: PurchaseRepository,
    private val inventoryRepository: InventoryRepository,
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
                    val file = File(context.cacheDir, "record-export-${System.currentTimeMillis()}.zip")
                    file.outputStream().use { out ->
                        RecordExporter.writeZip(out, meals, purchases, inventory)
                    }
                    AppLogger.info(
                        LogTags.Record,
                        "record_export_completed",
                        "meals" to meals.size,
                        "purchases" to purchases.size,
                        "inventory" to inventory.size,
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

    fun consumeFile() = _state.update { RecordExportUiState() }
}
