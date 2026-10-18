package com.xiaoqi.companion.feature.record

import com.xiaoqi.companion.core.record.MealItemDraft
import com.xiaoqi.companion.core.record.MealRecordDraft
import com.xiaoqi.companion.core.record.PurchaseItemDraft
import com.xiaoqi.companion.core.record.PurchaseRecordDraft
import com.xiaoqi.companion.core.record.RecordDomain
import com.xiaoqi.companion.core.record.RecordExtractionResult
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
    private lateinit var imageProcessor: FakeChatImageProcessor
    private lateinit var viewModel: RecordCaptureViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        extractor = mockk()
        mealRepository = mockk()
        purchaseRepository = mockk()
        imageStore = mockk()
        imageProcessor = FakeChatImageProcessor()
        viewModel = RecordCaptureViewModel(extractor, mealRepository, purchaseRepository, imageProcessor, imageStore)
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
            extractor.extract("prepared-base64", "image/jpeg", null, RecordDomain.PURCHASE)
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
        coVerify(exactly = 0) { extractor.extract(any(), any(), any(), any()) }
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

    private fun stubExtraction(result: RecordExtractionResult) {
        coEvery { extractor.extract("prepared-base64", "image/jpeg", null, null) } returns result
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
