package com.xiaoqi.companion.data.record

import com.xiaoqi.companion.data.db.converter.MealType
import com.xiaoqi.companion.data.db.converter.RecordSource
import com.xiaoqi.companion.data.db.dao.MealRecordWithItems
import com.xiaoqi.companion.data.db.dao.PurchaseRecordWithItems
import com.xiaoqi.companion.data.db.entity.InventoryItemEntity
import com.xiaoqi.companion.data.db.entity.MealItemEntity
import com.xiaoqi.companion.data.db.entity.MealRecordEntity
import com.xiaoqi.companion.data.db.entity.PurchaseItemEntity
import com.xiaoqi.companion.data.db.entity.PurchaseRecordEntity
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.ZoneId
import java.util.zip.ZipInputStream
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class RecordExporterTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    private fun export(vararg extraMeals: MealRecordWithItems): Map<String, String> {
        val out = ByteArrayOutputStream()
        RecordExporter.writeZip(
            out = out,
            meals = defaultMeals() + extraMeals,
            purchases = defaultPurchases(),
            inventory = defaultInventory(),
            exportedAtMillis = 1_792_312_200_000L, // 2026-10-18 08:30 +08:00
            zone = zone,
        )
        val files = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                files[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                entry = zip.nextEntry
            }
        }
        return files
    }

    @Test
    fun zipContainsAllSixContractFiles() {
        val files = export()
        assertEquals(
            setOf("manifest.json", "meals.jsonl", "items.csv", "daily_summary.csv", "purchases.csv", "inventory.csv"),
            files.keys,
        )
    }

    @Test
    fun manifestCountsRowsAndStampsVersion() {
        val manifest = export()["manifest.json"]!!
        assertTrue(manifest.contains("\"schema_version\":1"))
        assertTrue(manifest.contains("\"meal_count\":2"))
        assertTrue(manifest.contains("\"meal_item_count\":3"))
        assertTrue(manifest.contains("\"purchase_count\":1"))
        assertTrue(manifest.contains("\"inventory_count\":1"))
        assertTrue(manifest.contains("\"zone\":\"Asia/Shanghai\""))
    }

    @Test
    fun mealsJsonlEmbedsItemsWithTimeSourceSemantics() {
        val jsonl = export()["meals.jsonl"]!!
        val lines = jsonl.trim().lines()
        assertEquals(2, lines.size)
        val first = lines.first()
        // EXIF 时间优先 + time_source 标注
        assertTrue(first.contains("\"effective_time\":\"2026-10-18T08:30:00+08:00\""))
        assertTrue(first.contains("\"time_source\":\"exif\""))
        assertTrue(first.contains("\"meal_type\":\"BREAKFAST\""))
        assertTrue(first.contains("\"value_source\":\"estimated\""))
        assertTrue(first.contains("\"value_source\":\"user_verified\""))
        // 嵌套 items 而非拍平
        assertTrue(first.contains("\"items\":["))
        // 第二条无 EXIF → recorded
        assertTrue(lines[1].contains("\"time_source\":\"recorded\""))
    }

    @Test
    fun itemsCsvFlattensRowsAndEscapesDelimiters() {
        val csv = export()["items.csv"]!!
        val lines = csv.trim().lines()
        assertEquals(1 + 3, lines.size) // 表头 + 3 行明细
        assertTrue(lines[0].startsWith("record_id,effective_time,time_source,meal_type,food_name"))
        // 含逗号的菜名被引号包裹
        assertTrue(csv.contains("\"鸡蛋, 散装\""))
        // record_id 关联列存在
        assertTrue(lines[1].startsWith("m1,2026-10-18T08:30:00+08:00,exif,BREAKFAST"))
    }

    @Test
    fun dailySummaryAggregatesPerDaySorted() {
        val csv = export()["daily_summary.csv"]!!
        val lines = csv.trim().lines()
        assertEquals(1 + 2, lines.size) // 表头 + 两天
        assertEquals("date,meal_count,total_calories,total_protein_g,total_carbs_g,total_fat_g", lines[0])
        // toSortedMap 升序:17 号在前,18 号(含 EXIF)在后
        assertEquals("2026-10-17,1,650,30,70,20", lines[1])
        assertEquals("2026-10-18,1,800,40,70,25", lines[2])
    }

    @Test
    fun purchasesAndInventoryCsvRenderRows() {
        val purchases = export()["purchases.csv"]!!
        assertTrue(purchases.contains("p1,2026-10-18T08:30:00+08:00,exif,永辉超市,鸡蛋,德清源,15.0,个,1.6,0"))
        val inventory = export()["inventory.csv"]!!
        assertTrue(inventory.contains("鸡蛋,德清源,个,13.0,3.0"))
    }

    @Test
    fun emptyInputsYieldHeadersOnly() {
        val out = ByteArrayOutputStream()
        RecordExporter.writeZip(out, emptyList(), emptyList(), emptyList(), zone = zone)
        val files = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                files[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                entry = zip.nextEntry
            }
        }
        assertEquals("record_id,effective_time,time_source,meal_type,food_name,portion,calories,protein_g,carbs_g,fat_g,value_source,photo_index", files["items.csv"]!!.trim())
        assertTrue(files["meals.jsonl"]!!.isEmpty())
    }

    private fun millis(date: String, time: String): Long =
        java.time.LocalDateTime.parse("$date T $time".replace(" ", "")).atZone(zone).toInstant().toEpochMilli()

    private fun defaultMeals(): List<MealRecordWithItems> {
        val breakfast = millis("2026-10-18", "08:30")
        val evening = millis("2026-10-17", "20:00")
        return listOf(
            MealRecordWithItems(
                record = MealRecordEntity(
                    id = "m1", createdAt = millis("2026-10-18", "21:00"), updatedAt = breakfast,
                    mealType = MealType.BREAKFAST, source = RecordSource.PHOTO,
                    totalCalories = 800.0, totalProteinG = 40.0, totalCarbsG = 70.0, totalFatG = 25.0,
                    confidence = 0.9, photoTakenAt = breakfast, lat = 30.27, lon = 120.15,
                ),
                items = listOf(
                    MealItemEntity(
                        recordId = "m1", name = "牛肉面", portionText = "1大碗",
                        estCalories = 650.0, estProteinG = 32.0, estCarbsG = 65.0, estFatG = 18.0,
                        isEstimate = true, photoIndex = 0,
                    ),
                    MealItemEntity(
                        recordId = "m1", name = "鸡蛋, 散装", portionText = "1个",
                        estCalories = 150.0, estProteinG = 8.0, estCarbsG = 5.0, estFatG = 7.0,
                        isEstimate = false, photoIndex = 0,
                    ),
                ),
            ),
            MealRecordWithItems(
                record = MealRecordEntity(
                    id = "m2", createdAt = evening, updatedAt = evening,
                    mealType = MealType.DINNER, source = RecordSource.TEXT,
                    totalCalories = 650.0, totalProteinG = 30.0, totalCarbsG = 70.0, totalFatG = 20.0,
                    confidence = 0.5,
                ),
                items = listOf(
                    MealItemEntity(
                        recordId = "m2", name = "晚饭", portionText = "1份",
                        estCalories = 650.0, estProteinG = 30.0, estCarbsG = 70.0, estFatG = 20.0,
                        isEstimate = true,
                    ),
                ),
            ),
        ).map { it }
    }

    private fun defaultPurchases(): List<PurchaseRecordWithItems> = listOf(
        PurchaseRecordWithItems(
            record = PurchaseRecordEntity(
                id = "p1", purchasedAt = millis("2026-10-18", "08:30"), updatedAt = millis("2026-10-18", "08:30"),
                source = RecordSource.PHOTO, storeName = "永辉超市", photoTakenAt = millis("2026-10-18", "08:30"),
            ),
            items = listOf(
                PurchaseItemEntity(
                    recordId = "p1", name = "鸡蛋", brand = "德清源", quantity = 15.0, unit = "个",
                    unitPrice = 1.6, photoIndex = 0,
                ),
            ),
        ),
    )

    private fun defaultInventory(): List<InventoryItemEntity> = listOf(
        InventoryItemEntity(
            id = "鸡蛋|德清源", name = "鸡蛋", brand = "德清源", unit = "个",
            currentQuantity = 13.0, lowStockThreshold = 3.0, createdAt = 1L, updatedAt = 1L,
        ),
    )
}
