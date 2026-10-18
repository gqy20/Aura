package com.xiaoqi.companion.feature.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import com.xiaoqi.companion.core.record.BarcodeScannerSource
import com.xiaoqi.companion.core.record.MealItemDraft
import com.xiaoqi.companion.core.record.MealRecordDraft
import com.xiaoqi.companion.core.record.PhotoMetadataReader
import com.xiaoqi.companion.core.record.PurchaseItemDraft
import com.xiaoqi.companion.core.record.PurchaseRecordDraft
import com.xiaoqi.companion.core.record.RecordDomain
import com.xiaoqi.companion.core.record.RecordExtractionResult
import com.xiaoqi.companion.core.record.VisionRecordExtractor
import com.xiaoqi.companion.data.db.converter.MealType
import com.xiaoqi.companion.data.db.converter.RecordSource
import com.xiaoqi.companion.data.record.OpenFoodFactsClient
import com.xiaoqi.companion.data.record.RecordImageStore
import com.xiaoqi.companion.data.repository.MealRecordRepository
import com.xiaoqi.companion.data.repository.PurchaseRepository
import com.xiaoqi.companion.feature.chat.ChatImageProcessor
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class RecordCapturePhase { PICK_IMAGE, EXTRACTING, CONFIRMING, SAVING, SAVED, FAILED }

/** 一张已提取的图:展示缩略 + 条目归属(photoIndex) + EXIF 元数据。 */
data class PhotoEntry(
    val uriString: String,
    val base64: String,
    val mediaType: String = "image/jpeg",
    val takenAt: Long? = null,
    val lat: Double? = null,
    val lon: Double? = null,
)

data class RecordCaptureUiState(
    val phase: RecordCapturePhase = RecordCapturePhase.PICK_IMAGE,
    val domain: RecordDomain = RecordDomain.MEAL,
    val note: String = "",
    // --- 图片(多图追加流) ---
    val photos: List<PhotoEntry> = emptyList(),
    // photos[i] 贡献的条目索引范围;删除某图 = 删该范围的条目(图-条目对应)
    val photoRanges: List<IntRange> = emptyList(),
    // 正在提取的照片数(0 = 空闲);追加时用户可继续编辑已出条目
    val extractingCount: Int = 0,
    val appendFailure: String? = null,
    // --- 餐食域 ---
    val mealType: MealType = MealType.LUNCH,
    val items: List<MealItemDraft> = emptyList(),
    // 份量 chips 的缩放基准:小 0.75 / 中 1.0 / 大 1.25 都相对 base 计算,避免连击累积误差
    val baseItems: List<MealItemDraft> = emptyList(),
    val portionScales: List<Double> = emptyList(),
    // --- 采购域 ---
    val purchaseItems: List<PurchaseItemDraft> = emptyList(),
    val storeName: String = "",
    val adjustInventory: Boolean = true,
    // --- 共用 ---
    val confidence: Double = 0.0,
    val failureReason: String? = null,
    val savedRecordId: String? = null,
    // --- 条码(采购域) ---
    val barcodeScanning: Boolean = false,
    val barcodeNotFound: Boolean = false,
    val barcodeLookupFailed: Boolean = false,
) {
    val hasExtractedContent: Boolean get() = items.isNotEmpty() || purchaseItems.isNotEmpty()
}

@HiltViewModel
class RecordCaptureViewModel @Inject constructor(
    private val extractor: VisionRecordExtractor,
    private val mealRepository: MealRecordRepository,
    private val purchaseRepository: PurchaseRepository,
    private val imageProcessor: ChatImageProcessor,
    private val imageStore: RecordImageStore,
    private val barcodeScanner: BarcodeScannerSource,
    private val openFoodFactsClient: OpenFoodFactsClient,
    private val photoMetadataReader: PhotoMetadataReader,
) : ViewModel() {

    private val _state = MutableStateFlow(RecordCaptureUiState())
    val state: StateFlow<RecordCaptureUiState> = _state.asStateFlow()

    /** 单图入口(兼容旧调用);多选走 [onImagesPicked]。 */
    fun onImagePicked(uriString: String) = onImagesPicked(listOf(uriString))

    /**
     * 多图入口:逐张 prepare(EXIF)→ 提取 → 条目追加进同一张表。
     * 第一张失败且无已出条目 → FAILED(可手填);追加途中单张失败只提示不阻塞。
     */
    fun onImagesPicked(uriStrings: List<String>) {
        if (uriStrings.isEmpty()) return
        val isFirstBatch = _state.value.photos.isEmpty()
        _state.update {
            it.copy(
                phase = RecordCapturePhase.EXTRACTING,
                extractingCount = it.extractingCount + uriStrings.size,
                failureReason = null,
                appendFailure = null,
            )
        }
        val domainHint = _state.value.domain
        viewModelScope.launch {
            uriStrings.forEach { uri -> processPhoto(uri, append = !isFirstBatch, domainHint = domainHint) }
            _state.update { state ->
                val done = state.extractingCount - uriStrings.size
                val hasContent = state.hasExtractedContent
                state.copy(
                    extractingCount = done,
                    phase = when {
                        done > 0 -> RecordCapturePhase.EXTRACTING
                        hasContent -> RecordCapturePhase.CONFIRMING
                        state.failureReason != null -> RecordCapturePhase.FAILED
                        else -> RecordCapturePhase.PICK_IMAGE
                    },
                )
            }
        }
    }

    private suspend fun processPhoto(uriString: String, append: Boolean, domainHint: RecordDomain? = null) {
        val prepared = try {
            imageProcessor.prepare(uriString)
        } catch (error: Exception) {
            AppLogger.warn(LogTags.Record, "record_image_prepare_failed", "error" to (error.message ?: ""))
            handlePhotoFailure(append, "图片读取失败: ${error.message ?: "无法打开"}")
            return
        }
        val meta = photoMetadataReader.read(uriString)
        val photoIndex = _state.value.photos.size
        when (val result = extractor.extract(
            imageBase64 = prepared.imageBase64,
            mediaType = prepared.mediaType,
            note = _state.value.note.takeIf { it.isNotBlank() },
            domainHint = domainHint,
            photoTakenAtMillis = meta?.takenAtMillis,
        )) {
            is RecordExtractionResult.Meal -> _state.update { state ->
                val start = state.items.size
                val newItems = result.draft.items.map { it.copy(photoIndex = photoIndex) }
                state.copy(
                    domain = RecordDomain.MEAL,
                    photos = state.photos + PhotoEntry(
                        uriString = uriString,
                        base64 = prepared.imageBase64,
                        mediaType = prepared.mediaType,
                        takenAt = meta?.takenAtMillis,
                        lat = meta?.lat,
                        lon = meta?.lon,
                    ),
                    photoRanges = state.photoRanges + listOf(IntRange(start, start + newItems.size - 1)),
                    // 第一张图决定餐次(EXIF 判定在 extractor 内完成),追加图不覆盖用户已选餐次
                    mealType = if (state.photos.isEmpty()) result.draft.mealType else state.mealType,
                    items = state.items + newItems,
                    baseItems = state.baseItems + newItems,
                    portionScales = state.portionScales + List(newItems.size) { 1.0 },
                    confidence = if (state.photos.isEmpty()) result.draft.confidence else state.confidence,
                    failureReason = null,
                )
            }
            is RecordExtractionResult.Purchase -> _state.update { state ->
                val start = state.purchaseItems.size
                val newItems = result.draft.items.map { it.copy(photoIndex = photoIndex) }
                state.copy(
                    domain = RecordDomain.PURCHASE,
                    photos = state.photos + PhotoEntry(
                        uriString = uriString,
                        base64 = prepared.imageBase64,
                        mediaType = prepared.mediaType,
                        takenAt = meta?.takenAtMillis,
                        lat = meta?.lat,
                        lon = meta?.lon,
                    ),
                    photoRanges = state.photoRanges + listOf(IntRange(start, start + newItems.size - 1)),
                    storeName = result.draft.storeName ?: state.storeName,
                    purchaseItems = state.purchaseItems + newItems,
                    confidence = if (state.photos.isEmpty()) 0.5 else state.confidence,
                    failureReason = null,
                )
            }
            is RecordExtractionResult.Failed -> handlePhotoFailure(append, result.reason)
        }
    }

    private fun handlePhotoFailure(append: Boolean, reason: String) {
        _state.update { state ->
            if (append || state.hasExtractedContent) {
                // 追加图失败不阻塞已有条目
                state.copy(appendFailure = reason)
            } else {
                state.copy(phase = RecordCapturePhase.FAILED, failureReason = reason)
            }
        }
    }

    fun setNote(note: String) = _state.update { it.copy(note = note) }

    fun retryExtraction() {
        val photos = _state.value.photos
        if (photos.isEmpty()) {
            _state.update { it.copy(phase = RecordCapturePhase.PICK_IMAGE, failureReason = null) }
            return
        }
        // 重试 = 清条目重提全部图(失败态时 photos 为空,此路径只在 append 失败后手动重试单图场景外使用)
        reExtractPhotos(photos)
    }

    /** 域切换:已有图时按目标域重提全部图(替换条目);无图只切表格。 */
    fun switchDomain(domain: RecordDomain) {
        val current = _state.value
        if (current.domain == domain) return
        if (current.photos.isEmpty()) {
            _state.update { it.copy(domain = domain, phase = RecordCapturePhase.CONFIRMING, failureReason = null) }
            return
        }
        _state.update { it.copy(domain = domain, phase = RecordCapturePhase.EXTRACTING, failureReason = null) }
        viewModelScope.launch {
            val photos = current.photos
            _state.update { it.copy(items = emptyList(), baseItems = emptyList(), portionScales = emptyList(), purchaseItems = emptyList(), photoRanges = emptyList(), photos = emptyList()) }
            photos.forEach { photo -> processPhoto(photo.uriString, append = false, domainHint = domain) }
            _state.update { state ->
                state.copy(
                    phase = if (state.extractingCount > 0 || !state.hasExtractedContent && state.failureReason != null) {
                        if (state.failureReason != null && !state.hasExtractedContent) RecordCapturePhase.FAILED else RecordCapturePhase.CONFIRMING
                    } else {
                        RecordCapturePhase.CONFIRMING
                    },
                )
            }
        }
    }

    private fun reExtractPhotos(photos: List<PhotoEntry>) {
        val domainHint = _state.value.domain
        _state.update { it.copy(phase = RecordCapturePhase.EXTRACTING, extractingCount = photos.size, failureReason = null, appendFailure = null) }
        viewModelScope.launch {
            _state.update { it.copy(items = emptyList(), baseItems = emptyList(), portionScales = emptyList(), purchaseItems = emptyList(), photoRanges = emptyList(), photos = emptyList()) }
            photos.forEach { photo -> processPhoto(photo.uriString, append = false, domainHint = domainHint) }
            _state.update { state ->
                state.copy(
                    extractingCount = 0,
                    phase = when {
                        state.hasExtractedContent -> RecordCapturePhase.CONFIRMING
                        state.failureReason != null -> RecordCapturePhase.FAILED
                        else -> RecordCapturePhase.CONFIRMING
                    },
                )
            }
        }
    }

    /** 删除某张图及其贡献的条目(图-条目对应的核心操作)。 */
    fun removePhoto(photoIndex: Int) {
        _state.update { state ->
            if (photoIndex !in state.photos.indices) return@update state
            val range = state.photoRanges[photoIndex]
            // ranges 在被删范围之后的所有索引平移
            val removedCount = range.count()
            state.copy(
                photos = state.photos.filterIndexed { i, _ -> i != photoIndex },
                photoRanges = state.photoRanges.filterIndexed { i, _ -> i != photoIndex }
                    .map { r -> if (r.first >= range.last + 1) (r.first - removedCount)..(r.last - removedCount) else r },
                items = state.items.filterIndexed { i, _ -> i !in range },
                baseItems = state.baseItems.filterIndexed { i, _ -> i !in range },
                portionScales = state.portionScales.filterIndexed { i, _ -> i !in range },
                purchaseItems = state.purchaseItems.filterIndexed { i, _ -> i !in range },
            )
        }
    }

    // 提取失败不挡记录:降级为当前域的空表格手填(design.md 事务面规范)
    fun startManualEntry() {
        _state.update {
            when (it.domain) {
                RecordDomain.MEAL -> it.copy(
                    phase = RecordCapturePhase.CONFIRMING,
                    failureReason = null,
                    items = listOf(blankMealItem()),
                    baseItems = listOf(blankMealItem()),
                    portionScales = listOf(1.0),
                )
                RecordDomain.PURCHASE -> it.copy(
                    phase = RecordCapturePhase.CONFIRMING,
                    failureReason = null,
                    purchaseItems = listOf(blankPurchaseItem()),
                )
            }
        }
    }

    // --- 餐食域操作 ---

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
                photoRanges = state.photoRanges.map { r ->
                    when {
                        index > r.last -> r
                        r.count() == 1 && index == r.first -> r // 空范围由 removePhoto 负责清理,这里保留原位
                        else -> (r.first)..(r.last - 1)
                    }
                },
            )
        }
    }

    fun addItem() {
        _state.update { state ->
            val blank = blankMealItem()
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

    // --- 采购域操作 ---

    fun setStoreName(name: String) = _state.update { it.copy(storeName = name) }

    fun setAdjustInventory(enabled: Boolean) = _state.update { it.copy(adjustInventory = enabled) }

    fun updatePurchaseItem(index: Int, item: PurchaseItemDraft) {
        _state.update { state ->
            if (index !in state.purchaseItems.indices) return@update state
            state.copy(purchaseItems = state.purchaseItems.toMutableList().also { it[index] = item })
        }
    }

    fun deletePurchaseItem(index: Int) {
        _state.update { state ->
            if (index !in state.purchaseItems.indices) return@update state
            state.copy(purchaseItems = state.purchaseItems.filterIndexed { i, _ -> i != index })
        }
    }

    fun addPurchaseItem() {
        _state.update { it.copy(purchaseItems = it.purchaseItems + blankPurchaseItem()) }
    }

    /**
     * 扫条码预填(采购域):选包装条码照片 → ML Kit 识别 → OFF 查商品 → 追加预填行。
     * 扫不到条码或 OFF 未收录时提示手填,绝不阻塞记录(design.md 事务面规范)。
     */
    fun onBarcodeImagePicked(uriString: String) {
        _state.update { it.copy(barcodeScanning = true, barcodeNotFound = false, barcodeLookupFailed = false) }
        viewModelScope.launch {
            val barcode = runCatching { barcodeScanner.scanFirst(uriString) }.getOrNull()
            if (barcode == null) {
                _state.update { it.copy(barcodeScanning = false, barcodeNotFound = true) }
                return@launch
            }
            val product = openFoodFactsClient.lookup(barcode)
            _state.update { state ->
                if (product == null) {
                    state.copy(barcodeScanning = false, barcodeLookupFailed = true)
                } else {
                    state.copy(
                        barcodeScanning = false,
                        barcodeNotFound = false,
                        barcodeLookupFailed = false,
                        purchaseItems = state.purchaseItems + PurchaseItemDraft(
                            name = product.name,
                            brand = product.brand,
                            quantity = 1.0,
                            unit = "件",
                        ),
                    )
                }
            }
            AppLogger.info(
                LogTags.Record,
                "barcode_prefill_completed",
                "found" to (product != null),
                "nameLength" to product?.name?.length,
            )
        }
    }

    // --- 保存 ---

    fun save() {
        val current = _state.value
        when (current.domain) {
            RecordDomain.MEAL -> saveMeal(current)
            RecordDomain.PURCHASE -> savePurchase(current)
        }
    }

    private fun saveMeal(current: RecordCaptureUiState) {
        val validItems = current.items.filter { it.name.isNotBlank() }
        if (validItems.isEmpty()) {
            _state.update { it.copy(failureReason = "至少要有一项食物(名称不能为空)") }
            return
        }
        _state.update { it.copy(phase = RecordCapturePhase.SAVING, failureReason = null) }
        viewModelScope.launch {
            val imagePaths = persistImages(current.photos)
            try {
                val id = mealRepository.saveMeal(
                    MealRecordDraft(
                        mealType = current.mealType,
                        source = if (current.photos.isNotEmpty()) RecordSource.PHOTO else RecordSource.MANUAL,
                        items = validItems,
                        confidence = current.confidence,
                        imageLocalPath = imagePaths.takeIf { it.isNotEmpty() }?.joinToString(","),
                        rawNote = current.note.takeIf { it.isNotBlank() },
                        // 记录级 EXIF 元数据取首图(用餐时刻/地点的代表)
                        photoTakenAt = current.photos.firstOrNull()?.takenAt,
                        lat = current.photos.firstOrNull()?.lat,
                        lon = current.photos.firstOrNull()?.lon,
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

    private fun savePurchase(current: RecordCaptureUiState) {
        val validItems = current.purchaseItems.filter { it.name.isNotBlank() && it.quantity > 0 }
        if (validItems.isEmpty()) {
            _state.update { it.copy(failureReason = "至少要有一项商品(名称与数量不能为空)") }
            return
        }
        _state.update { it.copy(phase = RecordCapturePhase.SAVING, failureReason = null) }
        viewModelScope.launch {
            val imagePaths = persistImages(current.photos)
            try {
                val id = purchaseRepository.savePurchase(
                    PurchaseRecordDraft(
                        items = validItems,
                        source = if (current.photos.isNotEmpty()) RecordSource.PHOTO else RecordSource.MANUAL,
                        storeName = current.storeName.takeIf { it.isNotBlank() },
                        totalCost = null,
                        imageLocalPath = imagePaths.takeIf { it.isNotEmpty() }?.joinToString(","),
                        photoTakenAt = current.photos.firstOrNull()?.takenAt,
                        lat = current.photos.firstOrNull()?.lat,
                        lon = current.photos.firstOrNull()?.lon,
                    ),
                    adjustInventory = current.adjustInventory,
                )
                _state.update { it.copy(phase = RecordCapturePhase.SAVED, savedRecordId = id) }
            } catch (error: Exception) {
                AppLogger.warn(LogTags.Record, "purchase_record_save_failed", "error" to (error.message ?: ""))
                _state.update {
                    it.copy(phase = RecordCapturePhase.CONFIRMING, failureReason = "保存失败: ${error.message ?: "未知错误"}")
                }
            }
        }
    }

    private suspend fun persistImages(photos: List<PhotoEntry>): List<String> =
        photos.mapNotNull { photo ->
            runCatching { imageStore.saveBase64(photo.base64, photo.mediaType) }
                .onFailure { error ->
                    AppLogger.warn(LogTags.Record, "record_image_save_failed", "error" to (error.message ?: ""))
                }
                .getOrNull()
        }

    private fun blankMealItem() = MealItemDraft(
        name = "",
        portionText = "1 份",
        calories = 0.0,
        proteinG = 0.0,
        carbsG = 0.0,
        fatG = 0.0,
        isEstimate = false,
    )

    private fun blankPurchaseItem() = PurchaseItemDraft(
        name = "",
        brand = null,
        quantity = 1.0,
        unit = "个",
        unitPrice = null,
    )
}
