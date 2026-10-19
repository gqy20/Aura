package com.xiaoqi.companion.feature.record

import com.xiaoqi.companion.core.record.ExtractionImage
import com.xiaoqi.companion.core.record.MealItemDraft
import com.xiaoqi.companion.core.record.MealRecordDraft
import com.xiaoqi.companion.core.record.PurchaseItemDraft
import com.xiaoqi.companion.core.record.PurchaseRecordDraft
import com.xiaoqi.companion.core.record.RecordDomain
import com.xiaoqi.companion.core.record.RecordExtractionResult
import com.xiaoqi.companion.core.record.RecordNameSource
import com.xiaoqi.companion.core.record.VisionRecordExtractor
import com.xiaoqi.companion.data.db.converter.MealType
import com.xiaoqi.companion.data.db.converter.RecordSource
import com.xiaoqi.companion.data.record.RecordImageStore
import com.xiaoqi.companion.data.repository.MealRecordRepository
import com.xiaoqi.companion.data.repository.PurchaseRepository
import com.xiaoqi.companion.testing.FakeChatImageProcessor
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecordCaptureViewModelTest {

    private lateinit var extractor: VisionRecordExtractor
    private lateinit var mealRepository: MealRecordRepository
    private lateinit var purchaseRepository: PurchaseRepository
    private lateinit var imageStore: RecordImageStore
    private lateinit var barcodeScanner: com.xiaoqi.companion.core.record.BarcodeScannerSource
    private lateinit var openFoodFactsClient: com.xiaoqi.companion.data.record.OpenFoodFactsClient
    private lateinit var photoMetadataReader: com.xiaoqi.companion.core.record.PhotoMetadataReader
    private lateinit var imageProcessor: FakeChatImageProcessor
    private lateinit var viewModel: RecordCaptureViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        extractor = mockk()
        mealRepository = mockk()
        purchaseRepository = mockk()
        imageStore = mockk()
        barcodeScanner = mockk()
        openFoodFactsClient = mockk()
        photoMetadataReader = mockk {
            coEvery { read(any()) } returns com.xiaoqi.companion.core.record.PhotoMeta()
        }
        imageProcessor = FakeChatImageProcessor()
        viewModel = RecordCaptureViewModel(
            extractor, mealRepository, purchaseRepository, imageProcessor, imageStore,
            barcodeScanner, openFoodFactsClient, photoMetadataReader,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- 餐食域(P1 行为回归) ---

    @Test
    fun onImagePicked_mealExtraction_showsConfirmTable() = runTest {
        stubExtraction(extractedMeal())

        viewModel.onImagePicked("content://photo/1")

        val state = viewModel.state.value
        assertEquals(RecordCapturePhase.CONFIRMING, state.phase)
        assertEquals(RecordDomain.MEAL, state.domain)
        assertEquals(MealType.LUNCH, state.mealType)
        assertEquals(2, state.items.size)
        assertEquals(listOf(1.0, 1.0), state.portionScales)
    }
    @Test
    fun onImagePicked_purchaseExtraction_switchesToPurchaseTable() = runTest {
        stubExtraction(extractedPurchase())

        viewModel.onImagePicked("content://photo/receipt")

        val state = viewModel.state.value
        assertEquals(RecordCapturePhase.CONFIRMING, state.phase)
        assertEquals(RecordDomain.PURCHASE, state.domain)
        assertEquals(1, state.purchaseItems.size)
        assertEquals("鸡蛋", state.purchaseItems.single().name)
        assertEquals("永辉超市", state.storeName)
        assertTrue(state.items.isEmpty())
    }

    @Test
    fun onImagePicked_extractionFailed_showsFailedWithReason() = runTest {
        stubExtraction(RecordExtractionResult.Failed("未识别出条目,可手动填写"))

        viewModel.onImagePicked("content://photo/1")

        val state = viewModel.state.value
        assertEquals(RecordCapturePhase.FAILED, state.phase)
        assertEquals("未识别出条目,可手动填写", state.failureReason)
    }

    @Test
    fun onImagePicked_imagePrepareFailed_showsFailed() = runTest {
        imageProcessor.shouldFail = true

        viewModel.onImagePicked("content://broken")

        assertEquals(RecordCapturePhase.FAILED, viewModel.state.value.phase)
        assertTrue(viewModel.state.value.failureReason?.contains("图片读取失败") == true)
    }

    @Test
    fun adjustPortion_scalesFromBaseNotAccumulating() = runTest {
        stubExtraction(extractedMeal())
        viewModel.onImagePicked("content://photo/1")
        viewModel.adjustPortion(0, 0.75)
        val shrunk = viewModel.state.value.items[0].calories
        viewModel.adjustPortion(0, 1.25)

        // 第二次切换以提取基准 400 计算(400*1.25),不是在 300 上继续乘
        assertEquals(500.0, viewModel.state.value.items[0].calories, 0.001)
        assertEquals(300.0, shrunk, 0.001)
    }

    @Test
    fun updateItem_resetsPortionScaleBaseline() = runTest {
        stubExtraction(extractedMeal())
        viewModel.onImagePicked("content://photo/1")
        viewModel.adjustPortion(0, 0.75)
        viewModel.updateItem(0, viewModel.state.value.items[0].copy(calories = 600.0, isEstimate = false))
        viewModel.adjustPortion(0, 1.25)

        assertEquals(750.0, viewModel.state.value.items[0].calories, 0.001)
        assertEquals(false, viewModel.state.value.items[0].isEstimate)
    }

    @Test
    fun save_meal_savesDraftWithImage() = runTest {
        stubExtraction(extractedMeal())
        viewModel.onImagePicked("content://photo/1")
        val draftSlot = slot<MealRecordDraft>()
        coEvery { imageStore.saveBase64("prepared-base64", "image/jpeg") } returns "/data/record_images/a.jpg"
        coEvery { mealRepository.saveMeal(capture(draftSlot)) } returns "record-1"

        viewModel.save()

        assertEquals(RecordCapturePhase.SAVED, viewModel.state.value.phase)
        assertEquals(RecordSource.PHOTO, draftSlot.captured.source)
        assertEquals("/data/record_images/a.jpg", draftSlot.captured.imageLocalPath)
        assertEquals(530.0, draftSlot.captured.items.sumOf { it.calories }, 0.001)
    }

    @Test
    fun save_mealEmptyItems_showsErrorWithoutSaving() = runTest {
        viewModel.startManualEntry()

        viewModel.save()

        assertEquals(RecordCapturePhase.CONFIRMING, viewModel.state.value.phase)
        assertNotNull(viewModel.state.value.failureReason)
        coVerify(exactly = 0) { mealRepository.saveMeal(any()) }
    }

    @Test
    fun save_manualMealEntry_usesManualSourceWithoutImageStore() = runTest {
        viewModel.startManualEntry()
        viewModel.updateItem(0, viewModel.state.value.items[0].copy(name = "鸡蛋", calories = 80.0))
        val draftSlot = slot<MealRecordDraft>()
        coEvery { mealRepository.saveMeal(capture(draftSlot)) } returns "record-2"

        viewModel.save()

        assertEquals(RecordSource.MANUAL, draftSlot.captured.source)
        assertNull(draftSlot.captured.imageLocalPath)
        coVerify(exactly = 0) { imageStore.saveBase64(any(), any()) }
    }

    // --- 采购域(P2 新行为) ---

    @Test
    fun startManualEntry_purchaseDomain_givesOneBlankRow() = runTest {
        viewModel.switchDomain(RecordDomain.PURCHASE)
        viewModel.startManualEntry()

        val state = viewModel.state.value
        assertEquals(RecordDomain.PURCHASE, state.domain)
        assertEquals(RecordCapturePhase.CONFIRMING, state.phase)
        assertEquals(1, state.purchaseItems.size)
    }

    @Test
    fun switchDomain_withImage_reExtractsForTargetDomain() = runTest {
        stubExtraction(extractedMeal())
        viewModel.onImagePicked("content://photo/1")
        coEvery {
            extractor.extract(any<List<ExtractionImage>>(), any(), RecordDomain.PURCHASE, any())
        } returns extractedPurchase()

        viewModel.switchDomain(RecordDomain.PURCHASE)

        val state = viewModel.state.value
        assertEquals(RecordDomain.PURCHASE, state.domain)
        assertEquals(RecordCapturePhase.CONFIRMING, state.phase)
        assertEquals(1, state.purchaseItems.size)
        assertTrue(state.items.isEmpty())
    }

    @Test
    fun switchDomain_withoutImage_onlySwitchesTable() = runTest {
        viewModel.switchDomain(RecordDomain.PURCHASE)

        assertEquals(RecordDomain.PURCHASE, viewModel.state.value.domain)
        assertEquals(RecordCapturePhase.CONFIRMING, viewModel.state.value.phase)
        coVerify(exactly = 0) { extractor.extract(any<List<ExtractionImage>>(), any(), any(), any()) }
    }

    @Test
    fun switchDomain_reExtractFailure_keepsOldDomainAndItems() = runTest {
        stubExtraction(extractedMeal())
        viewModel.onImagePicked("content://photo/1")
        coEvery {
            extractor.extract(any<List<ExtractionImage>>(), any(), RecordDomain.PURCHASE, any())
        } returns RecordExtractionResult.Failed("切域重提失败")

        viewModel.switchDomain(RecordDomain.PURCHASE)

        // 事务性:域与条目都保持提取失败前的状态,失败只提示
        val state = viewModel.state.value
        assertEquals(RecordDomain.MEAL, state.domain)
        assertEquals(RecordCapturePhase.CONFIRMING, state.phase)
        assertEquals(2, state.items.size)
        assertEquals("切域重提失败", state.appendFailure)
    }

    @Test
    fun save_purchase_savesWithInventoryAdjustment() = runTest {
        stubExtraction(extractedPurchase())
        viewModel.onImagePicked("content://photo/receipt")
        val draftSlot = slot<PurchaseRecordDraft>()
        coEvery { imageStore.saveBase64(any(), any()) } returns "/data/record_images/b.jpg"
        coEvery { purchaseRepository.savePurchase(capture(draftSlot), any()) } returns "purchase-1"

        viewModel.save()

        assertEquals(RecordCapturePhase.SAVED, viewModel.state.value.phase)
        coVerify(exactly = 1) { purchaseRepository.savePurchase(any(), adjustInventory = true) }
        assertEquals("德清源", draftSlot.captured.items.single().brand)
        assertEquals(15.0, draftSlot.captured.items.single().quantity, 0.001)
        assertEquals("永辉超市", draftSlot.captured.storeName)
    }
    @Test
    fun save_purchase_adjustInventoryDisabledPropagates() = runTest {
        stubExtraction(extractedPurchase())
        viewModel.onImagePicked("content://photo/receipt")
        viewModel.setAdjustInventory(false)
        coEvery { imageStore.saveBase64(any(), any()) } returns "/data/record_images/b.jpg"
        coEvery { purchaseRepository.savePurchase(any(), any()) } returns "purchase-1"

        viewModel.save()

        coVerify(exactly = 1) { purchaseRepository.savePurchase(any(), adjustInventory = false) }
    }

    @Test
    fun save_purchaseBlankItems_showsErrorWithoutSaving() = runTest {
        viewModel.switchDomain(RecordDomain.PURCHASE)
        viewModel.startManualEntry()

        viewModel.save()

        assertEquals(RecordCapturePhase.CONFIRMING, viewModel.state.value.phase)
        assertNotNull(viewModel.state.value.failureReason)
        coVerify(exactly = 0) { purchaseRepository.savePurchase(any(), any()) }
    }

    @Test
    fun updatePurchaseItem_editsRowInPlace() = runTest {
        stubExtraction(extractedPurchase())
        viewModel.onImagePicked("content://photo/receipt")

        viewModel.updatePurchaseItem(0, viewModel.state.value.purchaseItems[0].copy(quantity = 20.0))

        assertEquals(20.0, viewModel.state.value.purchaseItems[0].quantity, 0.001)
    }

    // --- 条码预填(阶段3) ---

    @Test
    fun onBarcodeImagePicked_appendsPrefilledRow() = runTest {
        coEvery { barcodeScanner.scanFirst("content://barcode/1") } returns "3017620422003"
        coEvery { openFoodFactsClient.lookup("3017620422003") } returns com.xiaoqi.companion.data.record.OpenFoodFactsProduct(
            barcode = "3017620422003",
            name = "Nutella",
            brand = "Ferrero",
            kcalPer100g = 539.0,
            proteinPer100g = 6.3,
            carbsPer100g = 57.5,
            fatPer100g = 30.9,
        )

        viewModel.onBarcodeImagePicked("content://barcode/1")

        val state = viewModel.state.value
        assertEquals(false, state.barcodeScanning)
        assertEquals(false, state.barcodeNotFound)
        assertEquals(false, state.barcodeLookupFailed)
        val row = state.purchaseItems.single()
        assertEquals("Nutella", row.name)
        assertEquals("Ferrero", row.brand)
        assertEquals(1.0, row.quantity, 0.001)
        assertEquals("件", row.unit)
    }

    @Test
    fun onBarcodeImagePicked_noBarcodeShowsHint() = runTest {
        coEvery { barcodeScanner.scanFirst(any()) } returns null

        viewModel.onBarcodeImagePicked("content://barcode/none")

        val state = viewModel.state.value
        assertEquals(false, state.barcodeScanning)
        assertEquals(true, state.barcodeNotFound)
        assertEquals(true, state.purchaseItems.isEmpty())
        coVerify(exactly = 0) { openFoodFactsClient.lookup(any()) }
    }

    @Test
    fun onBarcodeImagePicked_productNotInOffShowsHint() = runTest {
        coEvery { barcodeScanner.scanFirst(any()) } returns "1234567890123"
        coEvery { openFoodFactsClient.lookup(any()) } returns null

        viewModel.onBarcodeImagePicked("content://barcode/x")

        val state = viewModel.state.value
        assertEquals(false, state.barcodeScanning)
        assertEquals(true, state.barcodeLookupFailed)
        assertEquals(true, state.purchaseItems.isEmpty())
    }

    // --- 多图单调用(路线 A:整组图一次提取,小票与菜品图跨图对齐) ---

    @Test
    fun onImagesPicked_dishAndReceiptPhotos_extractedInOneCall() = runTest {
        val imagesSlot = slot<List<ExtractionImage>>()
        coEvery { extractor.extract(capture(imagesSlot), any(), any(), any()) } returns extractedMeal()

        viewModel.onImagesPicked(listOf("content://photo/dish", "content://photo/receipt"))

        assertEquals(2, imagesSlot.captured.size)
        coVerify(exactly = 1) { extractor.extract(any<List<ExtractionImage>>(), any(), any(), any()) }
        assertEquals(2, viewModel.state.value.photos.size)
    }

    @Test
    fun onImagesPicked_twoImages_singleDraftItemsCarryPhotoIndexAndNameSource() = runTest {
        coEvery { extractor.extract(any<List<ExtractionImage>>(), any(), any(), any()) } returns RecordExtractionResult.Meal(
            MealRecordDraft(
                mealType = MealType.LUNCH,
                source = RecordSource.PHOTO,
                items = listOf(
                    MealItemDraft("牛肉面", "1碗", 550.0, 30.0, 70.0, 12.0, photoIndex = 0, nameSource = RecordNameSource.RECEIPT),
                    MealItemDraft("酸奶", "1杯", 120.0, 6.0, 15.0, 4.0, photoIndex = 1),
                ),
                confidence = 0.9,
            )
        )

        viewModel.onImagesPicked(listOf("content://photo/1", "content://photo/2"))

        val state = viewModel.state.value
        assertEquals(RecordCapturePhase.CONFIRMING, state.phase)
        assertEquals(2, state.photos.size)
        assertEquals(2, state.items.size)
        assertEquals(0, state.items[0].photoIndex)
        assertEquals(RecordNameSource.RECEIPT, state.items[0].nameSource)
        assertEquals(1, state.items[1].photoIndex)
        assertNull(state.items[1].nameSource)
        assertEquals(listOf(1.0, 1.0), state.portionScales)
        assertEquals(MealType.LUNCH, state.mealType)
    }

    @Test
    fun removePhoto_dropsOnlyThatPhotosItemsAndShifts() = runTest {
        coEvery { extractor.extract(any<List<ExtractionImage>>(), any(), any(), any()) } returns RecordExtractionResult.Meal(
            MealRecordDraft(
                mealType = MealType.LUNCH,
                source = RecordSource.PHOTO,
                items = listOf(
                    MealItemDraft("牛肉面", "1碗", 400.0, 20.0, 60.0, 8.0, photoIndex = 0),
                    MealItemDraft("卤蛋", "1 个", 130.0, 6.0, 1.0, 10.0, photoIndex = 0),
                    MealItemDraft("酸奶", "1杯", 120.0, 6.0, 15.0, 4.0, photoIndex = 1),
                ),
                confidence = 0.9,
            )
        )
        viewModel.onImagesPicked(listOf("content://photo/1", "content://photo/2"))

        assertEquals(3, viewModel.state.value.items.size)

        viewModel.removePhoto(0)

        val state = viewModel.state.value
        assertEquals(1, state.photos.size)
        assertEquals(1, state.items.size)
        assertEquals("酸奶", state.items.single().name)
        // 后续图的条目 photoIndex 前移对齐剩余图片下标
        assertEquals(0, state.items.single().photoIndex)
        assertEquals(listOf(1.0), state.portionScales)
    }

    @Test
    fun onImagesPicked_appendReExtractFailure_keepsOldItemsTransactionally() = runTest {
        val first = RecordExtractionResult.Meal(
            MealRecordDraft(
                mealType = MealType.LUNCH,
                source = RecordSource.PHOTO,
                items = listOf(MealItemDraft("牛肉面", "1碗", 550.0, 30.0, 70.0, 12.0, photoIndex = 0)),
                confidence = 0.9,
            )
        )
        coEvery { extractor.extract(any<List<ExtractionImage>>(), any(), any(), any()) } returnsMany listOf(
            first,
            RecordExtractionResult.Failed("未识别出食物"),
        )
        viewModel.onImagePicked("content://photo/1")
        viewModel.onImagePicked("content://photo/2")

        // 事务性追加:重提失败不丢首图条目,新图已入列可删可重试
        val state = viewModel.state.value
        assertEquals(RecordCapturePhase.CONFIRMING, state.phase)
        assertEquals(1, state.items.size)
        assertEquals("牛肉面", state.items.single().name)
        assertEquals(2, state.photos.size)
        assertEquals("未识别出食物", state.appendFailure)
    }

    @Test
    fun onImagesPicked_appendSuccess_replacesItemsWithWholeNewDraft() = runTest {
        val first = RecordExtractionResult.Meal(
            MealRecordDraft(
                mealType = MealType.LUNCH,
                source = RecordSource.PHOTO,
                items = listOf(MealItemDraft("牛肉面", "1碗", 550.0, 30.0, 70.0, 12.0, photoIndex = 0)),
                confidence = 0.9,
            )
        )
        val appended = RecordExtractionResult.Meal(
            MealRecordDraft(
                mealType = MealType.DINNER,
                source = RecordSource.PHOTO,
                items = listOf(
                    MealItemDraft("牛肉面", "1碗", 550.0, 30.0, 70.0, 12.0, photoIndex = 0),
                    MealItemDraft("凉拌黄瓜", "1份", 80.0, 2.0, 10.0, 4.0, photoIndex = 1, nameSource = RecordNameSource.RECEIPT),
                ),
                confidence = 0.85,
            )
        )
        coEvery { extractor.extract(any<List<ExtractionImage>>(), any(), any(), any()) } returnsMany listOf(first, appended)
        viewModel.onImagePicked("content://photo/1")
        viewModel.onImagePicked("content://photo/2")

        val state = viewModel.state.value
        // 整单重提:条目是第二份草稿(跨图对齐后的结果),不是两批拼接
        assertEquals(2, state.items.size)
        assertEquals(RecordNameSource.RECEIPT, state.items[1].nameSource)
        // 追加不覆盖用户已确认的餐次/置信度
        assertEquals(MealType.LUNCH, state.mealType)
        assertEquals(0.9, state.confidence, 0.001)
    }

    @Test
    fun onImagePicked_passesExifTakenAtToExtractor() = runTest {
        val takenAt = 1_700_000_000_000L
        coEvery { photoMetadataReader.read("content://photo/1") } returns
            com.xiaoqi.companion.core.record.PhotoMeta(takenAtMillis = takenAt, lat = 30.2, lon = 120.1)
        coEvery { extractor.extract(any<List<ExtractionImage>>(), any(), any(), takenAt) } returns extractedMeal()

        viewModel.onImagePicked("content://photo/1")

        val state = viewModel.state.value
        assertEquals(takenAt, state.photos.single().takenAt)
        assertEquals(30.2, state.photos.single().lat!!, 0.001)
        assertEquals(120.1, state.photos.single().lon!!, 0.001)
    }

    @Test
    fun save_meal_persistsJoinedImagePathsAndExif() = runTest {
        coEvery { extractor.extract(any<List<ExtractionImage>>(), any(), any(), any()) } returns RecordExtractionResult.Meal(
            MealRecordDraft(
                mealType = MealType.LUNCH,
                source = RecordSource.PHOTO,
                items = listOf(
                    MealItemDraft("牛肉面", "一碗", 400.0, 20.0, 60.0, 8.0, photoIndex = 0),
                    MealItemDraft("卤蛋", "1 个", 130.0, 6.0, 1.0, 10.0, photoIndex = 0),
                    MealItemDraft("酸奶", "1杯", 120.0, 6.0, 15.0, 4.0, photoIndex = 1),
                ),
                confidence = 0.9,
            )
        )
        viewModel.onImagesPicked(listOf("content://photo/1", "content://photo/2"))
        val draftSlot = slot<MealRecordDraft>()
        coEvery { imageStore.saveBase64(any(), any()) } returns "/data/record_images/a.jpg"
        coEvery { mealRepository.saveMeal(capture(draftSlot)) } returns "record-9"

        viewModel.save()

        val draft = draftSlot.captured
        assertEquals(2, draft.imageLocalPath!!.split(",").size)
        assertEquals(3, draft.items.size)
    }

    private fun stubExtraction(result: RecordExtractionResult) {
        coEvery { extractor.extract(any<List<ExtractionImage>>(), any(), any(), any()) } returns result
    }

    private fun extractedMeal() = RecordExtractionResult.Meal(
        MealRecordDraft(
            mealType = MealType.LUNCH,
            source = RecordSource.PHOTO,
            items = listOf(
                MealItemDraft("牛肉面", "一碗", 400.0, 20.0, 60.0, 8.0),
                MealItemDraft("卤蛋", "1 个", 130.0, 6.0, 1.0, 10.0),
            ),
            confidence = 0.9,
        ),
    )

    private fun extractedPurchase() = RecordExtractionResult.Purchase(
        PurchaseRecordDraft(
            items = listOf(
                PurchaseItemDraft("鸡蛋", "德清源", 15.0, "个", 1.6),
            ),
            source = RecordSource.PHOTO,
            storeName = "永辉超市",
        ),
    )
}
