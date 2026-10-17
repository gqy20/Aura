package com.xiaoqi.companion.feature.record

import com.xiaoqi.companion.core.record.MealExtractionResult
import com.xiaoqi.companion.core.record.MealItemDraft
import com.xiaoqi.companion.core.record.MealRecordDraft
import com.xiaoqi.companion.core.record.VisionMealExtractor
import com.xiaoqi.companion.data.db.converter.MealType
import com.xiaoqi.companion.data.db.converter.RecordSource
import com.xiaoqi.companion.data.record.RecordImageStore
import com.xiaoqi.companion.data.repository.MealRecordRepository
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

    private lateinit var extractor: VisionMealExtractor
    private lateinit var mealRepository: MealRecordRepository
    private lateinit var imageStore: RecordImageStore
    private lateinit var imageProcessor: FakeChatImageProcessor
    private lateinit var viewModel: RecordCaptureViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        extractor = mockk()
        mealRepository = mockk()
        imageStore = mockk()
        imageProcessor = FakeChatImageProcessor()
        viewModel = RecordCaptureViewModel(extractor, mealRepository, imageProcessor, imageStore)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun onImagePicked_extractSuccess_showsConfirmTable() = runTest {
        stubExtraction(extractedDraft())

        viewModel.onImagePicked("content://photo/1")

        val state = viewModel.state.value
        assertEquals(RecordCapturePhase.CONFIRMING, state.phase)
        assertEquals("prepared-base64", state.imageBase64)
        assertEquals(MealType.LUNCH, state.mealType)
        assertEquals(2, state.items.size)
        assertEquals(listOf(1.0, 1.0), state.portionScales)
        assertEquals(0.9, state.confidence, 0.001)
    }

    @Test
    fun onImagePicked_extractionFailed_showsFailedWithReason() = runTest {
        coEvery { extractor.extractMeal("prepared-base64", "image/jpeg", null) } returns
            MealExtractionResult.Failed("未识别出食物条目,可手动填写")

        viewModel.onImagePicked("content://photo/1")

        val state = viewModel.state.value
        assertEquals(RecordCapturePhase.FAILED, state.phase)
        assertEquals("未识别出食物条目,可手动填写", state.failureReason)
    }

    @Test
    fun onImagePicked_imagePrepareFailed_showsFailed() = runTest {
        imageProcessor.shouldFail = true

        viewModel.onImagePicked("content://broken")

        assertEquals(RecordCapturePhase.FAILED, viewModel.state.value.phase)
        assertTrue(viewModel.state.value.failureReason?.contains("图片读取失败") == true)
    }

    @Test
    fun startManualEntry_givesOneBlankRow() = runTest {
        viewModel.startManualEntry()

        val state = viewModel.state.value
        assertEquals(RecordCapturePhase.CONFIRMING, state.phase)
        assertEquals(1, state.items.size)
        assertEquals("", state.items.single().name)
        assertEquals(false, state.items.single().isEstimate)
    }

    @Test
    fun adjustPortion_scalesFromBaseNotAccumulating() = runTest {
        stubExtraction(extractedDraft())

        viewModel.onImagePicked("content://photo/1")
        viewModel.adjustPortion(0, 0.75)
        val shrunk = viewModel.state.value.items[0].calories
        viewModel.adjustPortion(0, 1.25)

        // 第二次切换以提取基准 400 计算(400*1.25),不是在 300 上继续乘
        assertEquals(500.0, viewModel.state.value.items[0].calories, 0.001)
        assertEquals(300.0, shrunk, 0.001)
        assertEquals(1.25, viewModel.state.value.portionScales[0], 0.001)
    }

    @Test
    fun updateItem_resetsPortionScaleBaseline() = runTest {
        stubExtraction(extractedDraft())

        viewModel.onImagePicked("content://photo/1")
        viewModel.adjustPortion(0, 0.75)
        viewModel.updateItem(0, viewModel.state.value.items[0].copy(calories = 600.0, isEstimate = false))
        viewModel.adjustPortion(0, 1.25)

        // 用户改过 600 后,份量基准更新为 600,大份 = 600*1.25
        assertEquals(750.0, viewModel.state.value.items[0].calories, 0.001)
        assertEquals(false, viewModel.state.value.items[0].isEstimate)
    }

    @Test
    fun deleteAndAddItems_keepParallelListsAligned() = runTest {
        stubExtraction(extractedDraft())
        viewModel.onImagePicked("content://photo/1")

        viewModel.deleteItem(0)
        viewModel.addItem()

        val state = viewModel.state.value
        assertEquals(2, state.items.size)
        assertEquals(2, state.baseItems.size)
        assertEquals(2, state.portionScales.size)
    }

    @Test
    fun save_validItems_savesDraftWithImage() = runTest {
        stubExtraction(extractedDraft())
        viewModel.onImagePicked("content://photo/1")
        val draftSlot = slot<MealRecordDraft>()
        coEvery { imageStore.saveBase64("prepared-base64", "image/jpeg") } returns "/data/record_images/a.jpg"
        coEvery { mealRepository.saveMeal(capture(draftSlot)) } returns "record-1"

        viewModel.save()

        val state = viewModel.state.value
        assertEquals(RecordCapturePhase.SAVED, state.phase)
        assertEquals("record-1", state.savedRecordId)
        val draft = draftSlot.captured
        assertEquals(RecordSource.PHOTO, draft.source)
        assertEquals("/data/record_images/a.jpg", draft.imageLocalPath)
        assertEquals(MealType.LUNCH, draft.mealType)
        assertEquals(2, draft.items.size)
        assertEquals(530.0, draft.items.sumOf { it.calories }, 0.001)
    }

    @Test
    fun save_skipsBlankNamedItems() = runTest {
        stubExtraction(extractedDraft())
        viewModel.onImagePicked("content://photo/1")
        viewModel.updateItem(0, viewModel.state.value.items[0].copy(name = "   "))
        val draftSlot = slot<MealRecordDraft>()
        coEvery { imageStore.saveBase64(any(), any()) } returns "/data/record_images/a.jpg"
        coEvery { mealRepository.saveMeal(capture(draftSlot)) } returns "record-1"

        viewModel.save()

        assertEquals(RecordCapturePhase.SAVED, viewModel.state.value.phase)
        assertEquals(1, draftSlot.captured.items.size)
        assertEquals("卤蛋", draftSlot.captured.items.single().name)
    }

    @Test
    fun save_emptyValidItems_showsErrorWithoutSaving() = runTest {
        viewModel.startManualEntry()

        viewModel.save()

        val state = viewModel.state.value
        assertEquals(RecordCapturePhase.CONFIRMING, state.phase)
        assertNotNull(state.failureReason)
        coVerify(exactly = 0) { mealRepository.saveMeal(any()) }
    }

    @Test
    fun save_manualEntry_usesManualSourceWithoutImageStore() = runTest {
        viewModel.startManualEntry()
        viewModel.updateItem(0, viewModel.state.value.items[0].copy(name = "鸡蛋", calories = 80.0))
        val draftSlot = slot<MealRecordDraft>()
        coEvery { mealRepository.saveMeal(capture(draftSlot)) } returns "record-2"

        viewModel.save()

        val draft = draftSlot.captured
        assertEquals(RecordSource.MANUAL, draft.source)
        assertNull(draft.imageLocalPath)
        coVerify(exactly = 0) { imageStore.saveBase64(any(), any()) }
    }

    @Test
    fun retryExtraction_withoutImage_returnsToPickImage() = runTest {
        viewModel.retryExtraction()

        assertEquals(RecordCapturePhase.PICK_IMAGE, viewModel.state.value.phase)
    }

    private fun stubExtraction(draft: MealRecordDraft) {
        coEvery { extractor.extractMeal("prepared-base64", "image/jpeg", null) } returns
            MealExtractionResult.Extracted(draft)
    }

    private fun extractedDraft() = MealRecordDraft(
        mealType = MealType.LUNCH,
        source = RecordSource.PHOTO,
        items = listOf(
            MealItemDraft("牛肉面", "一碗", 400.0, 20.0, 60.0, 8.0),
            MealItemDraft("卤蛋", "1 个", 130.0, 6.0, 1.0, 10.0),
        ),
        confidence = 0.9,
    )
}
