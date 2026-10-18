package com.xiaoqi.companion.core.record

import com.xiaoqi.companion.core.llm.AnthropicMessagesLLMClientFactory
import com.xiaoqi.companion.data.db.converter.LlmProvider
import com.xiaoqi.companion.data.db.converter.MealType
import com.xiaoqi.companion.data.db.converter.RecordSource
import com.xiaoqi.companion.data.repository.ConfigRepository
import com.xiaoqi.companion.data.repository.LlmConfig
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
        assertEquals(MealType.LUNCH, draft.mealType)
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

    private fun toolUseResponse(toolName: String, inputJson: String) = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(
            """{"type":"message","content":[{"type":"thinking","thinking":"..."},""" +
                """{"type":"tool_use","id":"c1","name":"$toolName","input":$inputJson}],""" +
                """"stop_reason":"tool_use","usage":{"input_tokens":10,"output_tokens":20}}"""
        )

    private fun extractor(provider: LlmProvider, apiKey: String = "test-key") = VisionRecordExtractor(
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
    )
}
