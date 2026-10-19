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
import java.time.ZoneId
import java.util.zip.ZipInputStream
import org.junit.Test
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class RecordExporterTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    private fun export(vararg extraMeals: MealRecordWithItems): Map<String, String> =
        readZipEntries(writeZipToBytes(defaultMeals() + extraMeals, defaultPurchases(), emptyList()))
            .mapValues { (_, bytes) -> bytes.toString(Charsets.UTF_8) }

    private fun writeZipToBytes(
        meals: List<MealRecordWithItems>,
        purchases: List<PurchaseRecordWithItems>,
        images: List<RecordExporter.ExportImage>,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        RecordExporter.writeZip(
            out = out,
            meals = meals,
            purchases = purchases,
            inventory = defaultInventory(),
            images = images,
            exportedAtMillis = 1_792_312_200_000L, // 2026-10-18 08:30 +08:00
            zone = zone,
        )
        return out.toByteArray()
    }

    private fun readZipEntries(raw: ByteArray): Map<String, ByteArray> {
        val files = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(raw)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                files[entry.name] = zip.readBytes()
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
        assertTrue(manifest.contains("\"schema_version\":4"))
        // 构建标识来自 BuildConfig(brand 版本 + git sha),不再是手写常量
        assertTrue(manifest.contains("\"app_version\":\"${com.xiaoqi.companion.BuildConfig.BRAND_VERSION_NAME}\""))
        assertTrue(manifest.contains("\"version_code\":${com.xiaoqi.companion.BuildConfig.BRAND_VERSION_CODE}"))
        assertTrue(manifest.contains("\"build_id\":\"${com.xiaoqi.companion.BuildConfig.BUILD_GIT_SHA}\""))
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
        // 名字来源分级:receipt(小票真值) 导出为小写;无来源的条目字段省略
        assertTrue(first.contains("\"name_source\":\"receipt\""))
        // 牛肉面条目无来源,首行 name_source 仅出现一次(鸡蛋条目)
        assertEquals(1, first.split("\"name_source\"").size - 1)
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
        assertTrue(lines[0].endsWith(",name_source"))
        // 含逗号的菜名被引号包裹
        assertTrue(csv.contains("\"鸡蛋, 散装\""))
        // record_id 关联列存在;小票来源条目行尾 name_source=receipt,无来源为空
        assertTrue(lines[1].startsWith("m1,2026-10-18T08:30:00+08:00,exif,BREAKFAST"))
        assertTrue(lines[1].endsWith(",,"))
        assertTrue(lines[2].endsWith(",receipt"))
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
        val files = readZipEntries(
            writeZipToBytes(emptyList(), emptyList(), emptyList())
        ).mapValues { (_, bytes) -> bytes.toString(Charsets.UTF_8) }
        assertEquals(
            "record_id,effective_time,time_source,meal_type,food_name,portion,calories,protein_g,carbs_g,fat_g,value_source,photo_index,image_path,name_source",
            files["items.csv"]!!.trim(),
        )
        assertTrue(files["meals.jsonl"]!!.isEmpty())
        // 无图片时 manifest 不携带图片字段,zip 也不含 images/ 条目
        assertFalse(files["manifest.json"]!!.contains("image_count"))
        assertFalse(files.keys.any { it.startsWith("images/") })
    }

    @Test
    fun imagesWrittenAsZipEntriesWithRelativeReferences() {
        val bytesA = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3)
        val bytesB = byteArrayOf(9, 8, 7)
        val files = readZipEntries(
            writeZipToBytes(
                defaultMeals() + photoMealWithImages(),
                defaultPurchases() + purchaseWithImage(),
                listOf(
                    RecordExporter.ExportImage("/data/record_images/uuid-a.jpg", bytesA),
                    RecordExporter.ExportImage("/data/record_images/uuid-b.jpg", bytesB),
                    RecordExporter.ExportImage("/data/record_images/uuid-c.jpg", byteArrayOf(4)),
                ),
            )
        )

        assertArrayEquals(bytesA, files["images/uuid-a.jpg"])
        assertArrayEquals(bytesB, files["images/uuid-b.jpg"])
        assertArrayEquals(byteArrayOf(4), files["images/uuid-c.jpg"])

        val jsonl = files["meals.jsonl"]!!.toString(Charsets.UTF_8)
        assertTrue(jsonl.contains("\"image_paths\":[\"images/uuid-a.jpg\",\"images/uuid-b.jpg\"]"))
        // name_source 按条目输出(小写);receipt=小票真值,photo=纯视觉
        assertTrue(jsonl.contains("\"name\":\"宫保鸡丁\",\"portion\":\"1份\""))
        assertTrue(jsonl.contains("\"name_source\":\"receipt\""))
        assertTrue(jsonl.contains("\"name_source\":\"photo\""))
        val itemsCsv = files["items.csv"]!!.toString(Charsets.UTF_8)
        // photo_index 对应位次的图,行尾 image_path + name_source 列
        assertTrue(itemsCsv.contains("宫保鸡丁,1份,300.0,15.0,20.0,10.0,estimated,0,images/uuid-a.jpg,receipt"))
        assertTrue(itemsCsv.contains("米饭,1碗,200.0,10.0,40.0,5.0,estimated,1,images/uuid-b.jpg,photo"))
        val purchasesCsv = files["purchases.csv"]!!.toString(Charsets.UTF_8)
        assertTrue(purchasesCsv.contains("三文鱼,,1.0,份,39.9,0,images/uuid-c.jpg"))
        val manifest = files["manifest.json"]!!.toString(Charsets.UTF_8)
        assertTrue(manifest.contains("\"image_count\":3"))
        assertTrue(manifest.contains("\"image_total_bytes\":9"))
    }

    @Test
    fun missingImageFileLeavesNullPlaceholderWithoutDanglingReference() {
        val files = readZipEntries(
            writeZipToBytes(
                defaultMeals() + photoMealWithImages(),
                defaultPurchases(),
                listOf(RecordExporter.ExportImage("/data/record_images/uuid-a.jpg", byteArrayOf(1))),
            )
        )

        assertFalse(files.containsKey("images/uuid-b.jpg"))
        val jsonl = files["meals.jsonl"]!!.toString(Charsets.UTF_8)
        // 按位对齐:缺失文件的位次是 null,photo_index 引用不错位
        assertTrue(jsonl.contains("\"image_paths\":[\"images/uuid-a.jpg\",null]"))
        val itemsCsv = files["items.csv"]!!.toString(Charsets.UTF_8)
        assertTrue(itemsCsv.contains("米饭,1碗,200.0,10.0,40.0,5.0,estimated,1,,"))
        val manifest = files["manifest.json"]!!.toString(Charsets.UTF_8)
        assertTrue(manifest.contains("\"image_count\":1"))
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
                        isEstimate = false, photoIndex = 0, nameSource = "RECEIPT",
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

    private fun purchaseWithImage(): PurchaseRecordWithItems = PurchaseRecordWithItems(
        record = PurchaseRecordEntity(
            id = "p2", purchasedAt = millis("2026-10-18", "13:00"), updatedAt = millis("2026-10-18", "13:00"),
            source = RecordSource.PHOTO, storeName = "盒马", imageLocalPath = "/data/record_images/uuid-c.jpg",
            photoTakenAt = millis("2026-10-18", "13:00"),
        ),
        items = listOf(
            PurchaseItemEntity(
                recordId = "p2", name = "三文鱼", brand = null, quantity = 1.0, unit = "份",
                unitPrice = 39.9, photoIndex = 0,
            ),
        ),
    )

    private fun photoMealWithImages(): MealRecordWithItems = MealRecordWithItems(
        record = MealRecordEntity(
            id = "m3", createdAt = millis("2026-10-18", "12:10"), updatedAt = millis("2026-10-18", "12:10"),
            mealType = MealType.LUNCH, source = RecordSource.PHOTO,
            totalCalories = 500.0, totalProteinG = 25.0, totalCarbsG = 60.0, totalFatG = 15.0,
            confidence = 0.85, photoTakenAt = millis("2026-10-18", "12:00"),
            imageLocalPath = "/data/record_images/uuid-a.jpg,/data/record_images/uuid-b.jpg",
        ),
        items = listOf(
            MealItemEntity(
                recordId = "m3", name = "宫保鸡丁", portionText = "1份",
                estCalories = 300.0, estProteinG = 15.0, estCarbsG = 20.0, estFatG = 10.0,
                isEstimate = true, photoIndex = 0, nameSource = "RECEIPT",
            ),
            MealItemEntity(
                recordId = "m3", name = "米饭", portionText = "1碗",
                estCalories = 200.0, estProteinG = 10.0, estCarbsG = 40.0, estFatG = 5.0,
                isEstimate = true, photoIndex = 1, nameSource = "PHOTO",
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
