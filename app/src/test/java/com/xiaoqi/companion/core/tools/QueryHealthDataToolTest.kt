package com.xiaoqi.companion.core.tools

import com.xiaoqi.companion.data.db.dao.HealthSnapshotDao
import com.xiaoqi.companion.data.db.entity.HealthSnapshotEntity
import com.xiaoqi.companion.data.source.HealthConnectDataSource
import io.mockk.coEvery
import io.mockk.mockk
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueryHealthDataToolTest {

    private val dao: HealthSnapshotDao = mockk(relaxed = true)
    private val dataSource: HealthConnectDataSource = mockk(relaxed = true)
    private val tool = QueryHealthDataTool(dao, dataSource)

    @Test fun `returns empty when local cache has nothing`() = runTest {
        coEvery { dao.findInRange(any(), any()) } returns emptyList()
        val output = tool.execute(QueryHealthDataTool.Args(days = 7))
        val parsed = Json.parseToJsonElement(output).jsonObject
        assertEquals(7, parsed["days"]!!.jsonPrimitive.content.toInt())
        assertEquals(0, parsed["count"]!!.jsonPrimitive.content.toInt())
        assertEquals(0, parsed["syncedDays"]!!.jsonPrimitive.content.toInt())
        assertTrue(parsed["snapshots"]!!.jsonArray.isEmpty())
    }

    @Test fun `does not call dataSource when sync=false`() = runTest {
        coEvery { dao.findInRange(any(), any()) } returns emptyList()
        tool.execute(QueryHealthDataTool.Args(days = 7, sync = false))
        io.mockk.coVerify(exactly = 0) { dataSource.syncRecentDays(any()) }
    }

    @Test fun `calls dataSource when sync=true`() = runTest {
        coEvery { dao.findInRange(any(), any()) } returns emptyList()
        coEvery { dataSource.syncRecentDays(7) } returns 3
        val output = tool.execute(QueryHealthDataTool.Args(days = 7, sync = true))
        val parsed = Json.parseToJsonElement(output).jsonObject
        assertEquals(3, parsed["syncedDays"]!!.jsonPrimitive.content.toInt())
        io.mockk.coVerify(exactly = 1) { dataSource.syncRecentDays(7) }
    }

    @Test fun `serializes existing snapshots`() = runTest {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val todayInt = today.year * 10000 + today.monthValue * 100 + today.dayOfMonth
        coEvery { dao.findInRange(any(), any()) } returns listOf(
            HealthSnapshotEntity(
                date = todayInt,
                steps = 8421,
                avgHeartRate = 72,
                minHeartRate = 55,
                maxHeartRate = 130,
                sleepDurationMinutes = 480,
                sourcePackages = """["com.mi.health"]""",
                fetchedAt = 1_700_000_000_000L,
            ),
        )
        val output = tool.execute(QueryHealthDataTool.Args(days = 7))
        val parsed = Json.parseToJsonElement(output).jsonObject
        assertEquals(1, parsed["count"]!!.jsonPrimitive.content.toInt())
        val first = parsed["snapshots"]!!.jsonArray.first().jsonObject
        assertEquals(8421, first["steps"]!!.jsonPrimitive.content.toInt())
        assertEquals(72, first["avgHeartRate"]!!.jsonPrimitive.content.toInt())
        assertEquals(480, first["sleepDurationMinutes"]!!.jsonPrimitive.content.toInt())
    }

    @Test fun `clamps days to 1-30`() = runTest {
        coEvery { dao.findInRange(any(), any()) } returns emptyList()
        // days=0 应该被 clamp 到 1
        val out1 = tool.execute(QueryHealthDataTool.Args(days = 0))
        val parsed1 = Json.parseToJsonElement(out1).jsonObject
        assertEquals(1, parsed1["days"]!!.jsonPrimitive.content.toInt())

        // days=999 应该被 clamp 到 30
        val out2 = tool.execute(QueryHealthDataTool.Args(days = 999))
        val parsed2 = Json.parseToJsonElement(out2).jsonObject
        assertEquals(30, parsed2["days"]!!.jsonPrimitive.content.toInt())
    }

    @Test fun `sync failure does not break tool output`() = runTest {
        coEvery { dataSource.syncRecentDays(7) } throws RuntimeException("HC SDK not installed")
        coEvery { dao.findInRange(any(), any()) } returns emptyList()
        val output = tool.execute(QueryHealthDataTool.Args(days = 7, sync = true))
        // Should still return valid JSON
        val parsed = Json.parseToJsonElement(output).jsonObject
        assertFalse(parsed.containsKey("error"))
        assertEquals(0, parsed["syncedDays"]!!.jsonPrimitive.content.toInt())
    }
}
