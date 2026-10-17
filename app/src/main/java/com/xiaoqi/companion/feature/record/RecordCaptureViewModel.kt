package com.xiaoqi.companion.feature.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import com.xiaoqi.companion.core.record.MealExtractionResult
import com.xiaoqi.companion.core.record.MealItemDraft
import com.xiaoqi.companion.core.record.MealRecordDraft
import com.xiaoqi.companion.core.record.VisionMealExtractor
import com.xiaoqi.companion.data.db.converter.MealType
import com.xiaoqi.companion.data.db.converter.RecordSource
import com.xiaoqi.companion.data.record.RecordImageStore
import com.xiaoqi.companion.data.repository.MealRecordRepository
import com.xiaoqi.companion.feature.chat.ChatImageProcessor
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class RecordCapturePhase { PICK_IMAGE, EXTRACTING, CONFIRMING, SAVING, SAVED, FAILED }

data class RecordCaptureUiState(
    val phase: RecordCapturePhase = RecordCapturePhase.PICK_IMAGE,
    val imageUriString: String? = null,
    val imageBase64: String? = null,
    val imageMediaType: String = "image/jpeg",
    val note: String = "",
    val mealType: MealType = MealType.LUNCH,
    val items: List<MealItemDraft> = emptyList(),
    // 份量 chips 的缩放基准:小 0.75 / 中 1.0 / 大 1.25 都相对 base 计算,避免连击累积误差
    val baseItems: List<MealItemDraft> = emptyList(),
    val portionScales: List<Double> = emptyList(),
    val confidence: Double = 0.0,
    val failureReason: String? = null,
    val savedRecordId: String? = null,
)

@HiltViewModel
class RecordCaptureViewModel @Inject constructor(
    private val extractor: VisionMealExtractor,
    private val mealRepository: MealRecordRepository,
    private val imageProcessor: ChatImageProcessor,
    private val imageStore: RecordImageStore,
) : ViewModel() {

    private val _state = MutableStateFlow(RecordCaptureUiState())
    val state: StateFlow<RecordCaptureUiState> = _state.asStateFlow()

    fun onImagePicked(uriString: String) {
        _state.update { it.copy(imageUriString = uriString, phase = RecordCapturePhase.EXTRACTING, failureReason = null) }
        viewModelScope.launch {
            val prepared = try {
                imageProcessor.prepare(uriString)
            } catch (error: Exception) {
                AppLogger.warn(LogTags.Record, "record_image_prepare_failed", "error" to (error.message ?: ""))
                _state.update { it.copy(phase = RecordCapturePhase.FAILED, failureReason = "图片读取失败: ${error.message ?: "无法打开"}") }
                return@launch
            }
            _state.update { it.copy(imageBase64 = prepared.imageBase64, imageMediaType = prepared.mediaType) }
            extract()
        }
    }

    fun setNote(note: String) = _state.update { it.copy(note = note) }

    fun retryExtraction() {
        if (_state.value.imageBase64 == null) {
            _state.update { it.copy(phase = RecordCapturePhase.PICK_IMAGE, failureReason = null) }
            return
        }
        _state.update { it.copy(phase = RecordCapturePhase.EXTRACTING, failureReason = null) }
        viewModelScope.launch { extract() }
    }

    // 提取失败不挡记录:降级为空表格手填(design.md 事务面规范)
    fun startManualEntry() {
        val blank = blankItem()
        _state.update {
            it.copy(
                phase = RecordCapturePhase.CONFIRMING,
                failureReason = null,
                items = listOf(blank),
                baseItems = listOf(blank),
                portionScales = listOf(1.0),
            )
        }
    }

    fun setMealType(type: MealType) = _state.update { it.copy(mealType = type) }

    fun updateItem(index: Int, item: MealItemDraft) {
        _state.update { state ->
            if (index !in state.items.indices) return@update state
            state.copy(
                items = state.items.toMutableList().also { it[index] = item },
                baseItems = state.baseItems.toMutableList().also { it[index] = item },
                portionScales = state.portionScales.toMutableList().also { it[index] = 1.0 },
            )
        }
    }

    fun deleteItem(index: Int) {
        _state.update { state ->
            if (index !in state.items.indices) return@update state
            state.copy(
                items = state.items.filterIndexed { i, _ -> i != index },
                baseItems = state.baseItems.filterIndexed { i, _ -> i != index },
                portionScales = state.portionScales.filterIndexed { i, _ -> i != index },
            )
        }
    }

    fun addItem() {
        _state.update { state ->
            val blank = blankItem()
            state.copy(
                items = state.items + blank,
                baseItems = state.baseItems + blank,
                portionScales = state.portionScales + 1.0,
            )
        }
    }

    fun adjustPortion(index: Int, scale: Double) {
        _state.update { state ->
            if (index !in state.items.indices) return@update state
            val base = state.baseItems[index]
            val scaled = base.copy(
                calories = base.calories * scale,
                proteinG = base.proteinG * scale,
                carbsG = base.carbsG * scale,
                fatG = base.fatG * scale,
            )
            state.copy(
                items = state.items.toMutableList().also { it[index] = scaled },
                portionScales = state.portionScales.toMutableList().also { it[index] = scale },
            )
        }
    }

    fun save() {
        val current = _state.value
        val validItems = current.items.filter { it.name.isNotBlank() }
        if (validItems.isEmpty()) {
            _state.update { it.copy(failureReason = "至少要有一项食物(名称不能为空)") }
            return
        }
        _state.update { it.copy(phase = RecordCapturePhase.SAVING, failureReason = null) }
        viewModelScope.launch {
            val imagePath = current.imageBase64?.let { base64 ->
                runCatching { imageStore.saveBase64(base64, current.imageMediaType) }
                    .onFailure { error ->
                        AppLogger.warn(LogTags.Record, "record_image_save_failed", "error" to (error.message ?: ""))
                    }
                    .getOrNull()
            }
            try {
                val id = mealRepository.saveMeal(
                    MealRecordDraft(
                        mealType = current.mealType,
                        source = if (current.imageBase64 != null) RecordSource.PHOTO else RecordSource.MANUAL,
                        items = validItems,
                        confidence = current.confidence,
                        imageLocalPath = imagePath,
                        rawNote = current.note.takeIf { it.isNotBlank() },
                    )
                )
                _state.update { it.copy(phase = RecordCapturePhase.SAVED, savedRecordId = id) }
            } catch (error: Exception) {
                AppLogger.warn(LogTags.Record, "meal_record_save_failed", "error" to (error.message ?: ""))
                _state.update {
                    it.copy(phase = RecordCapturePhase.CONFIRMING, failureReason = "保存失败: ${error.message ?: "未知错误"}")
                }
            }
        }
    }

    private suspend fun extract() {
        val current = _state.value
        val base64 = current.imageBase64 ?: return
        when (val result = extractor.extractMeal(base64, current.imageMediaType, current.note.takeIf { it.isNotBlank() })) {
            is MealExtractionResult.Extracted -> _state.update {
                it.copy(
                    phase = RecordCapturePhase.CONFIRMING,
                    mealType = result.draft.mealType,
                    items = result.draft.items,
                    baseItems = result.draft.items,
                    portionScales = result.draft.items.map { 1.0 },
                    confidence = result.draft.confidence,
                    failureReason = null,
                )
            }
            is MealExtractionResult.Failed -> _state.update {
                it.copy(phase = RecordCapturePhase.FAILED, failureReason = result.reason)
            }
        }
    }

    private fun blankItem() = MealItemDraft(
        name = "",
        portionText = "1 份",
        calories = 0.0,
        proteinG = 0.0,
        carbsG = 0.0,
        fatG = 0.0,
        isEstimate = false,
    )
}
