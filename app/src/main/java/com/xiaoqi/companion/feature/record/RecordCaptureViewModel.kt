package com.xiaoqi.companion.feature.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import com.xiaoqi.companion.core.record.BarcodeScannerSource
import com.xiaoqi.companion.core.record.ExtractionImage
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
    // --- 图片(多图单调用提取:全部图进同一次请求,小票与菜品图跨图对齐) ---
    val photos: List<PhotoEntry> = emptyList(),
    // 正在提取的照片数(0 = 空闲);提取中新图已入列,条目成功后整体替换
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
     * 多图入口(路线 A):prepare 全部新图 → 与已有图合并成一次提取请求。
     * 新图 prepare 后立即入列(缩略图可见);提取成功才整体替换条目,失败保留旧条目
     * 只报 appendFailure —— 追加是事务性的,重提失败不会丢已确认的内容。
     * 追加(已有图)会整单重跑,用户的份量微调不保留 —— 跨图对齐的代价,重调即可。
     */
    fun onImagesPicked(uriStrings: List<String>) {
        if (uriStrings.isEmpty()) return
        _state.update {
            it.copy(
                phase = RecordCapturePhase.EXTRACTING,
                extractingCount = it.extractingCount + uriStrings.size,
                failureReason = null,
                appendFailure = null,
            )
        }
        viewModelScope.launch {
            val failures = mutableListOf<String>()
            val newEntries = uriStrings.mapNotNull { uri -> preparePhotoEntry(uri, failures) }
            if (newEntries.isNotEmpty()) {
                _state.update { it.copy(photos = it.photos + newEntries) }
            }
            extractAllPhotos(extraFailures = failures)
            _state.update { state ->
                val done = (state.extractingCount - uriStrings.size).coerceAtLeast(0)
                state.copy(
                    extractingCount = done,
                    phase = when {
                        done > 0 -> RecordCapturePhase.EXTRACTING
                        state.hasExtractedContent -> RecordCapturePhase.CONFIRMING
                        state.failureReason != null -> RecordCapturePhase.FAILED
                        else -> RecordCapturePhase.PICK_IMAGE
                    },
                )
            }
        }
    }

    /** 对当前全部图跑一次批量提取;失败时已有条目原样保留。[domainHint] 供切域重提,成功才提交新域。 */
    private suspend fun extractAllPhotos(extraFailures: List<String>, domainHint: RecordDomain? = null) {
        val photos = _state.value.photos
        if (photos.isEmpty()) {
            _state.update { it.copy(phase = RecordCapturePhase.FAILED, failureReason = extraFailures.firstOrNull() ?: "没有可提取的图片") }
            return
        }
        val state = _state.value
        val result = extractor.extract(
            images = photos.map { ExtractionImage(it.base64, it.mediaType) },
            note = state.note.takeIf { it.isNotBlank() },
            domainHint = domainHint ?: state.domain,
            photoTakenAtMillis = photos.firstOrNull()?.takenAt,
        )
        // 已有内容(追加/切域/重试)时保留用户已选餐次与置信度;首提以提取结果为准
        val keepUserChoices = state.hasExtractedContent
        when (result) {
            is RecordExtractionResult.Meal -> {
                val ordered = result.draft.items.sortedBy { it.photoIndex ?: Int.MAX_VALUE }
                _state.update { s ->
                    s.copy(
                        domain = RecordDomain.MEAL,
                        mealType = if (keepUserChoices) s.mealType else result.draft.mealType,
                        items = ordered,
                        baseItems = ordered,
                        portionScales = List(ordered.size) { 1.0 },
                        purchaseItems = emptyList(),
                        confidence = if (keepUserChoices) s.confidence else result.draft.confidence,
                        appendFailure = extraFailures.takeIf { it.isNotEmpty() }?.joinToString(";"),
                        failureReason = null,
                    )
                }
            }
            is RecordExtractionResult.Purchase -> {
                val ordered = result.draft.items.sortedBy { it.photoIndex ?: Int.MAX_VALUE }
                _state.update { s ->
                    s.copy(
                        domain = RecordDomain.PURCHASE,
                        storeName = if (keepUserChoices && s.storeName.isNotBlank()) s.storeName else (result.draft.storeName ?: s.storeName),
                        purchaseItems = ordered,
                        items = emptyList(),
                        baseItems = emptyList(),
                        portionScales = emptyList(),
                        confidence = if (keepUserChoices) s.confidence else 0.5,
                        appendFailure = extraFailures.takeIf { it.isNotEmpty() }?.joinToString(";"),
                        failureReason = null,
                    )
                }
            }
            is RecordExtractionResult.Failed -> {
                if (keepUserChoices) {
                    // 事务性追加:旧条目原样保留,失败只提示
                    _state.update { it.copy(appendFailure = result.reason) }
                } else {
                    _state.update { it.copy(phase = RecordCapturePhase.FAILED, failureReason = result.reason) }
                }
            }
        }
    }

    private suspend fun preparePhotoEntry(uriString: String, failures: MutableList<String>): PhotoEntry? {
        val prepared = try {
            imageProcessor.prepare(uriString)
        } catch (error: Exception) {
            AppLogger.warn(LogTags.Record, "record_image_prepare_failed", "error" to (error.message ?: ""))
            failures += "图片读取失败:${error.message ?: "无法打开"}"
            return null
        }
        val meta = photoMetadataReader.read(uriString)
        return PhotoEntry(
            uriString = uriString,
            base64 = prepared.imageBase64,
            mediaType = prepared.mediaType,
            takenAt = meta?.takenAtMillis,
            lat = meta?.lat,
            lon = meta?.lon,
        )
    }

    fun setNote(note: String) = _state.update { it.copy(note = note) }

    fun retryExtraction() {
        val photos = _state.value.photos
        if (photos.isEmpty()) {
            _state.update { it.copy(phase = RecordCapturePhase.PICK_IMAGE, failureReason = null) }
            return
        }
        _state.update { it.copy(phase = RecordCapturePhase.EXTRACTING, extractingCount = photos.size, failureReason = null, appendFailure = null) }
        viewModelScope.launch {
            // 重试 = 对当前全部图(含追加失败后已入列的新图)再跑一次批量提取
            extractAllPhotos(extraFailures = emptyList())
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

    /** 域切换:已有图时按目标域整单重提(事务性,失败保留原域原条目);无图只切表格。 */
    fun switchDomain(domain: RecordDomain) {
        val current = _state.value
        if (current.domain == domain) return
        if (current.photos.isEmpty()) {
            _state.update { it.copy(domain = domain, phase = RecordCapturePhase.CONFIRMING, failureReason = null) }
            return
        }
        _state.update { it.copy(phase = RecordCapturePhase.EXTRACTING, extractingCount = current.photos.size, failureReason = null, appendFailure = null) }
        viewModelScope.launch {
            // 域不先提交:提取成功后由结果分支落位,失败时原域原条目原样保留
            extractAllPhotos(extraFailures = emptyList(), domainHint = domain)
            _state.update { state ->
                state.copy(
                    extractingCount = 0,
                    phase = when {
                        state.hasExtractedContent -> RecordCapturePhase.CONFIRMING
                        state.failureReason != null -> RecordCapturePhase.CONFIRMING // 旧条目仍在,不进 FAILED
                        else -> RecordCapturePhase.CONFIRMING
                    },
                )
            }
        }
    }

    /**
     * 删除某张图及其归属条目(条目 photoIndex 随身携带,删除即过滤,无需范围表)。
     * 大于被删序号的 photoIndex 整体前移,保持与剩余图片下标对齐。
     */
    fun removePhoto(photoIndex: Int) {
        _state.update { state ->
            if (photoIndex !in state.photos.indices) return@update state
            // portionScales 与 items 按索引对齐,同步过滤
            val keptIndices = state.items.indices.filter { state.items[it].photoIndex != photoIndex }
            state.copy(
                photos = state.photos.filterIndexed { i, _ -> i != photoIndex },
                items = keptIndices.map { state.items[it] }.shiftPhotoIndexAfter(photoIndex),
                baseItems = keptIndices.map { state.baseItems[it] }.shiftPhotoIndexAfter(photoIndex),
                portionScales = keptIndices.map { state.portionScales[it] },
                purchaseItems = state.purchaseItems.filterNot { it.photoIndex == photoIndex }
                    .map { if ((it.photoIndex ?: -1) > photoIndex) it.copy(photoIndex = it.photoIndex!! - 1) else it },
            )
        }
    }

    // meal items 的 photoIndex 位移(baseItems 同步);null(无归属条目)不动
    private fun List<MealItemDraft>.shiftPhotoIndexAfter(removedIndex: Int): List<MealItemDraft> =
        map { item ->
            if ((item.photoIndex ?: -1) > removedIndex) item.copy(photoIndex = item.photoIndex!! - 1) else item
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
            // 条目自带 photoIndex,删除无需位置簿记
            state.copy(
                items = state.items.filterIndexed { i, _ -> i != index },
                baseItems = state.baseItems.filterIndexed { i, _ -> i != index },
                portionScales = state.portionScales.filterIndexed { i, _ -> i != index },
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
