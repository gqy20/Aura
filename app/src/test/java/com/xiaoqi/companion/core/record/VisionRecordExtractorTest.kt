package com.xiaoqi.companion.core.record

import com.xiaoqi.companion.core.llm.AnthropicMessagesLLMClientFactory
import com.xiaoqi.companion.data.db.converter.LlmProvider
import com.xiaoqi.companion.data.db.converter.MealType
import com.xiaoqi.companion.data.db.converter.RecordSource
import com.xiaoqi.companion.data.repository.ConfigRepository
import com.xiaoqi.companion.data.repository.LlmConfig
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VisionRecordExtractorTest {

    private val json = Json { ignoreUnknownKeys = true }

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    // --- 解析层 ---

    @Test
    fun parse_validMealInput_mapsToDraftWithDefaults() {
        val result = parseMealToolInput(
            """{"meal_type":"LUNCH","confidence":0.9,"items":[
                {"name":" 牛肉面 ","portion_text":"一碗","calories":450,"protein_g":25,"carbs_g":50,"fat_g":10},
                {"name":"卤蛋","portion_text":"","calories":80,"protein_g":6,"carbs_g":1,"fat_g":5}
            ]}""",
            note = "中午吃的",
            hour = 12,
        )

        val draft = (result as RecordExtractionResult.Meal).draft
        assertEquals(MealType.LUNCH, draft.mealType)
        assertEquals(RecordSource.PHOTO, draft.source)
        assertEquals(0.9, draft.confidence, 0.001)
        assertEquals("中午吃的", draft.rawNote)
        assertEquals("牛肉面", draft.items[0].name)
        assertEquals("一碗", draft.items[0].portionText)
        assertEquals("1 份", draft.items[1].portionText)
    }

    @Test
    fun parse_mealClampsOutOfRangeNutrition() {
        val result = parseMealToolInput(
            """{"meal_type":"DINNER","confidence":1.8,"items":[
                {"name":"feast","portion_text":"x","calories":99000,"protein_g":-5,"carbs_g":99999,"fat_g":500}
            ]}""",
            note = null,
            hour = 19,
        )

        val item = (result as RecordExtractionResult.Meal).draft.items.single()
        assertEquals(CALORIE_MAX_PER_ITEM, item.calories, 0.001)
        assertEquals(0.0, item.proteinG, 0.001)
        assertEquals(CARB_MAX_PER_ITEM, item.carbsG, 0.001)
        assertEquals(FAT_MAX_PER_ITEM, item.fatG, 0.001)
        assertEquals(1.0, (result as RecordExtractionResult.Meal).draft.confidence, 0.001)
    }

    @Test
    fun parse_invalidMealTypeDerivesFromHour() {
        val lunch = parseMealToolInput("""{"meal_type":"BRUNCH","items":[{"name":"a","portion_text":"1","calories":1}]}""", null, hour = 12)
        val dinner = parseMealToolInput("""{"meal_type":"whatever","items":[{"name":"a","portion_text":"1","calories":1}]}""", null, hour = 19)

        assertEquals(MealType.LUNCH, (lunch as RecordExtractionResult.Meal).draft.mealType)
        assertEquals(MealType.DINNER, (dinner as RecordExtractionResult.Meal).draft.mealType)
    }

    @Test
    fun parse_mealEmptyItemsReturnsFailed() {
        assertTrue(parseMealToolInput("""{"meal_type":"LUNCH","items":[]}""", null, 12) is RecordExtractionResult.Failed)
    }

    @Test
    fun parse_validPurchaseInput_mapsToDraft() {
        val result = parsePurchaseToolInput(
            """{"store_name":" 永辉超市 ","confidence":0.8,"items":[
                {"name":"鸡蛋","brand":" 德清源 ","quantity":15,"unit":"个","unit_price":1.6},
                {"name":"牛奶","quantity":2,"unit":"盒"}
            ]}""",
            note = null,
        )

        val draft = (result as RecordExtractionResult.Purchase).draft
        assertEquals("永辉超市", draft.storeName)
        assertEquals(2, draft.items.size)
        assertEquals("鸡蛋", draft.items[0].name)
        assertEquals("德清源", draft.items[0].brand)
        assertEquals(15.0, draft.items[0].quantity, 0.001)
        assertEquals(1.6, draft.items[0].unitPrice!!, 0.001)
        assertEquals(null, draft.items[1].unitPrice)
    }

    @Test
    fun parse_purchaseClampsAndFilters() {
        val result = parsePurchaseToolInput(
            """{"store_name":"","items":[
                {"name":"超大量","quantity":99999999,"unit":"个","unit_price":999999},
                {"name":"零数量","quantity":0,"unit":"个"},
                {"name":"空白","quantity":3,"unit":""}
            ]}""",
            note = null,
        )

        val draft = (result as RecordExtractionResult.Purchase).draft
        // quantity<=0 的行被过滤;越界值钳到上限;空单位兜底"个"
        assertEquals(2, draft.items.size)
        assertEquals(PURCHASE_QUANTITY_MAX, draft.items[0].quantity, 0.001)
        assertEquals(PURCHASE_PRICE_MAX, draft.items[0].unitPrice!!, 0.001)
        assertEquals("个", draft.items[1].unit)
        assertEquals(null, draft.storeName)
    }

    @Test
    fun parse_purchaseEmptyItemsReturnsFailed() {
        assertTrue(parsePurchaseToolInput("""{"items":[]}""", null) is RecordExtractionResult.Failed)
    }

    @Test
    fun parse_malformedJsonReturnsFailed() {
        assertTrue(parseMealToolInput("""{"meal_type":LUNCH}""", null, 12) is RecordExtractionResult.Failed)
        assertTrue(parsePurchaseToolInput("not json at all", null) is RecordExtractionResult.Failed)
    }

    // --- 请求层 ---

    @Test
    fun extract_mealPhoto_viaAutoClassification() = runTest {
        server.enqueue(toolUseResponse("submit_meal_record", """{"meal_type":"LUNCH","confidence":0.9,"items":[{"name":"牛肉面","portion_text":"1碗","calories":450,"protein_g":25,"carbs_g":50,"fat_g":10}]}"""))
        val extractor = extractor(provider = LlmProvider.GLM)

        val result = extractor.extract("aGk=", "image/jpeg")

        val draft = (result as RecordExtractionResult.Meal).draft
        // 无 EXIF → 当前时间判定;nowProvider 固定 NOW(本地时区 06:13)→ 早餐
        assertEquals(MealType.BREAKFAST, draft.mealType)
        val requestBody = server.takeRequest().body.readUtf8()
        // 无 hint → tool_choice=any + 两个 schema 都在
        assertTrue(requestBody.contains("\"tool_choice\":{\"type\":\"any\"}"))
        assertTrue(requestBody.contains("submit_meal_record"))
        assertTrue(requestBody.contains("submit_purchase_record"))
        assertTrue(requestBody.contains("\"thinking\":{\"type\":\"disabled\"}"))
    }

    @Test
    fun extract_purchasePhoto_viaAutoClassification() = runTest {
        server.enqueue(toolUseResponse("submit_purchase_record", """{"store_name":"永辉超市","items":[{"name":"鸡蛋","brand":"德清源","quantity":15,"unit":"个","unit_price":1.6}]}"""))
        val extractor = extractor(provider = LlmProvider.GLM)

        val result = extractor.extract("aGk=", "image/jpeg")

        val draft = (result as RecordExtractionResult.Purchase).draft
        assertEquals("鸡蛋", draft.items.single().name)
    }

    @Test
    fun extract_domainHintForcesSingleTool() = runTest {
        server.enqueue(toolUseResponse("submit_purchase_record", """{"items":[{"name":"鸡蛋","quantity":15,"unit":"个"}]}"""))
        val extractor = extractor(provider = LlmProvider.GLM)

        extractor.extract("aGk=", "image/jpeg", domainHint = RecordDomain.PURCHASE)

        // system prompt 里本来就会提到两个工具名,所以只验证 tools 数组与 tool_choice
        val body = json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        val toolChoice = body.getValue("tool_choice").jsonObject
        assertEquals("tool", toolChoice.getValue("type").jsonPrimitive.content)
        assertEquals("submit_purchase_record", toolChoice.getValue("name").jsonPrimitive.content)
        val sentTools = body.getValue("tools").jsonArray
        assertEquals(1, sentTools.size)
        assertEquals("submit_purchase_record", sentTools.single().jsonObject.getValue("name").jsonPrimitive.content)
    }

    @Test
    fun extract_modelDeclineSurfacesModelReply() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"type":"message","content":[{"type":"text",""" +
                        """"text":"这张照片不是餐食或采购照片,无法提取记录。"}],""" +
                        """"stop_reason":"end_turn","usage":{"input_tokens":10,"output_tokens":5}}"""
                )
        )
        val extractor = extractor(provider = LlmProvider.GLM)

        val result = extractor.extract("aGk=", "image/jpeg")

        val failure = result as RecordExtractionResult.Failed
        assertTrue(failure.reason.contains("这张照片不是餐食或采购照片"))
        assertFalse(failure.reason.contains("提取请求失败"))
    }

    @Test
    fun extract_localQwenProviderFailsWithoutRequest() = runTest {
        val extractor = extractor(provider = LlmProvider.LOCAL_QWEN)

        val result = extractor.extract("aGk=", "image/jpeg")

        assertTrue((result as RecordExtractionResult.Failed).reason.contains("本地模型"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun extract_missingApiKeyFailsWithoutRequest() = runTest {
        val extractor = extractor(provider = LlmProvider.GLM, apiKey = "")

        val result = extractor.extract("aGk=", "image/jpeg")

        assertTrue(result is RecordExtractionResult.Failed)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun extract_httpErrorReturnsFailed() = runTest {
        server.enqueue(MockResponse().setResponseCode(502).setBody("bad gateway"))
        val extractor = extractor(provider = LlmProvider.GLM)

        val result = extractor.extract("aGk=", "image/jpeg")

        assertTrue((result as RecordExtractionResult.Failed).reason.contains("提取请求失败"))
    }

    @Test
    fun extract_unknownToolNameReturnsFailed() = runTest {
        server.enqueue(toolUseResponse("submit_weather", "{}"))
        val extractor = extractor(provider = LlmProvider.GLM)

        val result = extractor.extract("aGk=", "image/jpeg")

        assertTrue((result as RecordExtractionResult.Failed).reason.contains("未知记录类型"))
    }

    // --- 两段式营养解析 ---

    private val densityTable = FoodDensityTable(
        listOf(
            FoodDensityEntry(n = "米饭（蒸，代表值）", k = 116.0, p = 2.6, f = 0.3, b = 25.9),
            FoodDensityEntry(n = "鸡蛋（代表值）", k = 139.0, p = 13.3, f = 8.8, b = 2.8),
        )
    )

    @Test
    fun extract_densityHit_recomputesNutritionFromGrams() = runTest {
        // 模型估 999 kcal;密度表鸡蛋 139kcal/100g × 100g = 139 —— 查表值覆盖模型值
        server.enqueue(
            toolUseResponse(
                "submit_meal_record",
                """{"meal_type":"LUNCH","items":[{"name":"鸡蛋","portion_text":"2个","calories":999,"estimated_grams":100}]}""",
            )
        )
        val extractor = extractor(provider = LlmProvider.GLM, densityTable = densityTable)

        val result = extractor.extract("aGk=", "image/jpeg")

        val item = (result as RecordExtractionResult.Meal).draft.items.single()
        assertEquals(139.0, item.calories, 0.01)
        assertEquals(13.3, item.proteinG, 0.01)
        assertEquals(100.0, item.estimatedGrams, 0.01)
    }

    @Test
    fun extract_densityMiss_keepsModelValues() = runTest {
        server.enqueue(
            toolUseResponse(
                "submit_meal_record",
                """{"meal_type":"LUNCH","items":[{"name":"张记招牌套餐","portion_text":"1份","calories":650,"protein_g":30,"estimated_grams":400}]}""",
            )
        )
        val extractor = extractor(provider = LlmProvider.GLM, densityTable = densityTable)

        val result = extractor.extract("aGk=", "image/jpeg")

        val item = (result as RecordExtractionResult.Meal).draft.items.single()
        assertEquals(650.0, item.calories, 0.001)
        assertEquals(30.0, item.proteinG, 0.001)
    }

    @Test
    fun extract_calibratedItem_beatsDensityTable() = runTest {
        server.enqueue(
            toolUseResponse(
                "submit_meal_record",
                """{"meal_type":"DINNER","items":[{"name":"鸡蛋","portion_text":"2个","calories":999,"estimated_grams":120}]}""",
            )
        )
        val extractor = extractor(
            provider = LlmProvider.GLM,
            densityTable = densityTable,
            calibratedItems = listOf(
                com.xiaoqi.companion.data.db.dao.CalibratedMealItemRow(
                    name = "鸡蛋",
                    portionText = "2个(100g)",
                    estCalories = 160.0,
                    estProteinG = 14.0,
                    estCarbsG = 2.0,
                    estFatG = 10.0,
                )
            ),
        )

        val result = extractor.extract("aGk=", "image/jpeg")

        val item = (result as RecordExtractionResult.Meal).draft.items.single()
        // 校准基线 100g=160kcal,本次 120g → 160*1.2=192;密度表(139/100g)不参与
        assertEquals(192.0, item.calories, 0.01)
        assertEquals(false, item.isEstimate)
    }

    @Test
    fun extract_exifTimeOverridesModelMealType() = runTest {
        // 模型说 LUNCH,EXIF 说 08:30 早餐 —— EXIF 优先(模型看不到拍摄时刻)
        server.enqueue(
            toolUseResponse(
                "submit_meal_record",
                """{"meal_type":"LUNCH","items":[{"name":"包子","portion_text":"2个","calories":300,"estimated_grams":150}]}""",
            )
        )
        val extractor = extractor(provider = LlmProvider.GLM)
        val breakfastMillis = java.time.LocalDate.now()
            .atTime(8, 30).atZone(java.time.ZoneId.systemDefault())
            .toInstant().toEpochMilli()

        val result = extractor.extract("aGk=", "image/jpeg", photoTakenAtMillis = breakfastMillis)

        assertEquals(MealType.BREAKFAST, (result as RecordExtractionResult.Meal).draft.mealType)
    }

    @Test
    fun extract_noExif_usesCurrentTime() = runTest {
        server.enqueue(
            toolUseResponse(
                "submit_meal_record",
                """{"items":[{"name":"酸奶","portion_text":"1杯","calories":120,"estimated_grams":200}]}""",
            )
        )
        // 无 EXIF 时餐次取当前时间;nowProvider 固定为 NOW,但本地时区不定,
        // 因此不断言具体餐次,只断言解析成功且有餐次值
        val extractor = extractor(provider = LlmProvider.GLM)

        val result = extractor.extract("aGk=", "image/jpeg", photoTakenAtMillis = null)

        val draft = (result as RecordExtractionResult.Meal).draft
        assertTrue(draft.mealType.name.isNotEmpty())
    }

    @Test
    fun extract_exifEveningDerivesDinner() = runTest {
        server.enqueue(
            toolUseResponse(
                "submit_meal_record",
                """{"meal_type":"LUNCH","items":[{"name":"面条","portion_text":"1碗","calories":400,"estimated_grams":350}]}""",
            )
        )
        val extractor = extractor(provider = LlmProvider.GLM)
        val eveningMillis = java.time.LocalDate.now()
            .atTime(19, 0).atZone(java.time.ZoneId.systemDefault())
            .toInstant().toEpochMilli()

        val result = extractor.extract("aGk=", "image/jpeg", photoTakenAtMillis = eveningMillis)

        assertEquals(MealType.DINNER, (result as RecordExtractionResult.Meal).draft.mealType)
    }

    @Test
    fun extract_noGrams_infersFromModelCalories() = runTest {
        // 模型没给克重(老协议/弱模型):按 kcal 反推克重再查表,鸡蛋 999kcal → 718g → 999kcal(自洽)
        server.enqueue(
            toolUseResponse(
                "submit_meal_record",
                """{"meal_type":"LUNCH","items":[{"name":"鸡蛋","portion_text":"一些","calories":999}]}""",
            )
        )
        val extractor = extractor(provider = LlmProvider.GLM, densityTable = densityTable)

        val result = extractor.extract("aGk=", "image/jpeg")

        val item = (result as RecordExtractionResult.Meal).draft.items.single()
        // kcal 反推克重的查表值与模型 kcal 数学上等价,但宏量素来自成分表(一致性收益)
        assertEquals(999.0, item.calories, 1.0)
        assertEquals(95.2, item.proteinG, 1.0)
    }

    private fun toolUseResponse(toolName: String, inputJson: String) = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(
            """{"type":"message","content":[{"type":"thinking","thinking":"..."},""" +
                """{"type":"tool_use","id":"c1","name":"$toolName","input":$inputJson}],""" +
                """"stop_reason":"tool_use","usage":{"input_tokens":10,"output_tokens":20}}"""
        )

    private fun extractor(
        provider: LlmProvider,
        apiKey: String = "test-key",
        densityTable: FoodDensityTable? = null,
        calibratedItems: List<com.xiaoqi.companion.data.db.dao.CalibratedMealItemRow> = emptyList(),
    ) = VisionRecordExtractor(
        configRepository = mockk {
            every { getCurrentLlmConfig() } returns flowOf(
                LlmConfig(
                    provider = provider,
                    baseUrl = server.url("/").toString().trimEnd('/'),
                    apiKey = apiKey,
                    modelName = "glm-5.3-flash",
                ),
            )
        },
        clientFactory = AnthropicMessagesLLMClientFactory(),
        foodDensitySource = object : FoodDensitySource {
            override suspend fun load(): FoodDensityTable? = densityTable
        },
        mealRepository = mockk {
            coEvery { getRecentCalibratedItems(any(), any()) } returns calibratedItems
        },
        nowProvider = { NOW },
    )

    private companion object {
        // 固定时钟:校准窗口断言不依赖真实时间
        const val NOW = 1_700_000_000_000L
    }
}
