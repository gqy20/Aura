package com.xiaoqi.companion.data.record

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class OpenFoodFactsClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OpenFoodFactsClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = OpenFoodFactsClient(
            httpClient = OkHttpClient.Builder().build(),
            baseUrl = server.url("/api/v2/product").toString().trimEnd('/'),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun lookup_hit_parsesNameBrandAndNutriments() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"status":1,"code":"3017620422003","product":{"product_name":"Nutella","brands":"Ferrero",""" +
                        """"nutriments":{"energy-kcal_100g":"539","proteins_100g":"6.3","carbohydrates_100g":"57.5","fat_100g":"30.9"}}}"""
                )
        )

        val product = client.lookup("3017620422003")

        assertEquals("Nutella", product!!.name)
        assertEquals("Ferrero", product.brand)
        assertEquals(539.0, product.kcalPer100g!!, 0.001)
        assertEquals(6.3, product.proteinPer100g!!, 0.001)
        assertEquals(57.5, product.carbsPer100g!!, 0.001)
        assertEquals(30.9, product.fatPer100g!!, 0.001)
        assertEquals("/api/v2/product/3017620422003.json", server.takeRequest().path)
    }

    @Test
    fun lookup_statusZero_notRecorded() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"status":0,"code":"9999999999999"}""")
        )

        assertNull(client.lookup("9999999999999"))
    }

    @Test
    fun lookup_blankProductName_null() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"status":1,"product":{"product_name":"","brands":"X"}}""")
        )

        assertNull(client.lookup("1234567890123"))
    }

    @Test
    fun lookup_httpError_null() = runTest {
        server.enqueue(MockResponse().setResponseCode(503).setBody("down"))

        assertNull(client.lookup("3017620422003"))
    }

    @Test
    fun lookup_rejectsNonDigitBarcode_withoutRequest() = runTest {
        assertNull(client.lookup("ABC-123!"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun lookup_rejectsOutOfRangeLength_withoutRequest() = runTest {
        assertNull(client.lookup("12345"))
        assertNull(client.lookup("1".repeat(19)))
        assertEquals(0, server.requestCount)
    }
}
