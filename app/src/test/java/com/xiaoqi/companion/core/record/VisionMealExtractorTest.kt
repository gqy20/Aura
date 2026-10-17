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
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VisionMealExtractorTest {

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

    @Test
    fun parse_validToolInput_mapsToDraftWithDefaults() {
        val result = parseMealToolInput(
            """{"meal_type":"LUNCH","confidence":0.9,"items":[
                {"name":" 牛肉面 ","portion_text":"一碗","calories":450,"protein_g":25,"carbs_g":50,"fat_g":10},
                {"name":"卤蛋","portion_text":"","calories":80,"protein_g":6,"carbs_g":1,"fat_g":5}
            ]}""",
            note = "中午吃的",
            hour = 12,
        )

        val draft = (result as MealExtractionResult.Extracted).draft
        assertEquals(MealType.LUNCH, draft.mealType)
        assertEquals(RecordSource.PHOTO, draft.source)
        assertEquals(0.9, draft.confidence, 0.001)
        assertEquals("中午吃的", draft.rawNote)
        assertEquals(2, draft.items.size)
        assertEquals("牛肉面", draft.items[0].name)
        assertTrue(draft.items[0].isEstimate)
        assertEquals("一碗", draft.items[0].portionText)
        assertEquals("1 份", draft.items[1].portionText)
    }

    @Test
    fun parse_clampsOutOfRangeNutritionValues() {
        val result = parseMealToolInput(
            """{"meal_type":"DINNER","confidence":1.8,"items":[
                {"name":"feast","portion_text":"x","calories":99000,"protein_g":-5,"carbs_g":99999,"fat_g":500}
            ]}""",
            note = null,
            hour = 19,
        )

        val item = (result as MealExtractionResult.Extracted).draft.items.single()
        assertEquals(CALORIE_MAX_PER_ITEM, item.calories, 0.001)
        assertEquals(0.0, item.proteinG, 0.001)
        assertEquals(CARB_MAX_PER_ITEM, item.carbsG, 0.001)
        assertEquals(FAT_MAX_PER_ITEM, item.fatG, 0.001)
        assertEquals(1.0, (result as MealExtractionResult.Extracted).draft.confidence, 0.001)
    }

    @Test
    fun parse_invalidMealTypeDerivesFromHour() {
        val lunch = parseMealToolInput("""{"meal_type":"BRUNCH","items":[{"name":"a","portion_text":"1","calories":1}]}""", null, hour = 12)
        val dinner = parseMealToolInput("""{"meal_type":"whatever","items":[{"name":"a","portion_text":"1","calories":1}]}""", null, hour = 19)

        assertEquals(MealType.LUNCH, (lunch as MealExtractionResult.Extracted).draft.mealType)
        assertEquals(MealType.DINNER, (dinner as MealExtractionResult.Extracted).draft.mealType)
    }

    @Test
    fun parse_blankOrEmptyItemsReturnsFailed() {
        assertTrue(parseMealToolInput("""{"meal_type":"LUNCH","items":[]}""", null, 12) is MealExtractionResult.Failed)
        assertTrue(
            parseMealToolInput("""{"meal_type":"LUNCH","items":[{"name":"  ","portion_text":"1","calories":1}]}""", null, 12)
                is MealExtractionResult.Failed,
        )
    }

    @Test
    fun parse_malformedJsonReturnsFailed() {
        assertTrue(parseMealToolInput("""{"meal_type":LUNCH}""", null, 12) is MealExtractionResult.Failed)
        assertTrue(parseMealToolInput("not json at all", null, 12) is MealExtractionResult.Failed)
    }

    @Test
    fun extractMeal_successViaForcedToolCall() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"type":"message","content":[{"type":"tool_use","id":"c1","name":"submit_meal_record",""" +
                        """"input":{"meal_type":"LUNCH","confidence":0.9,"items":[""" +
                        """{"name":"Beef Noodle Soup","portion_text":"1 bowl","calories":450,"protein_g":25,"carbs_g":50,"fat_g":10}]}}],""" +
                        """"stop_reason":"tool_use","usage":{"input_tokens":10,"output_tokens":20}}"""
                )
        )
        val extractor = extractor(provider = LlmProvider.GLM)

        val result = extractor.extractMeal("aGk=", "image/jpeg", note = null)

        val draft = (result as MealExtractionResult.Extracted).draft
        assertEquals(MealType.LUNCH, draft.mealType)
        assertEquals(450.0, draft.items.single().calories, 0.001)
        val request = server.takeRequest()
        val requestBody = request.body.readUtf8()
        assertTrue(requestBody.contains("\"tool_choice\""))
        assertTrue(requestBody.contains("\"thinking\":{\"type\":\"disabled\"}"))
        assertTrue(requestBody.contains("\"image\""))
    }

    @Test
    fun extractMeal_localQwenProviderFailsWithoutRequest() = runTest {
        val extractor = extractor(provider = LlmProvider.LOCAL_QWEN)

        val result = extractor.extractMeal("aGk=", "image/jpeg")

        val failure = result as MealExtractionResult.Failed
        assertTrue(failure.reason.contains("本地模型"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun extractMeal_missingApiKeyFailsWithoutRequest() = runTest {
        val extractor = extractor(provider = LlmProvider.GLM, apiKey = "")

        val result = extractor.extractMeal("aGk=", "image/jpeg")

        assertTrue(result is MealExtractionResult.Failed)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun extractMeal_httpErrorReturnsFailed() = runTest {
        server.enqueue(MockResponse().setResponseCode(502).setBody("bad gateway"))
        val extractor = extractor(provider = LlmProvider.GLM)

        val result = extractor.extractMeal("aGk=", "image/jpeg")

        val failure = result as MealExtractionResult.Failed
        assertTrue(failure.reason.contains("提取请求失败"))
    }

    @Test
    fun extractMeal_modelDeclineSurfacesModelReply() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"type":"message","content":[{"type":"text",""" +
                        """"text":"这张照片不是餐食照片,而是应用截图,无法提取餐食记录。\n\n请提供包含食物的照片。"}],""" +
                        """"stop_reason":"end_turn","usage":{"input_tokens":10,"output_tokens":5}}"""
                )
        )
        val extractor = extractor(provider = LlmProvider.GLM)

        val result = extractor.extractMeal("aGk=", "image/jpeg")

        val failure = result as MealExtractionResult.Failed
        assertTrue(failure.reason.contains("这张照片不是餐食照片"))
        assertFalse(failure.reason.contains("提取请求失败"))
    }

    private fun extractor(provider: LlmProvider, apiKey: String = "test-key") = VisionMealExtractor(
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
