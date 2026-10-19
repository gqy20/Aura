package com.xiaoqi.companion.data.record

import com.xiaoqi.companion.BuildConfig
import com.xiaoqi.companion.data.db.dao.MealRecordWithItems
import com.xiaoqi.companion.data.db.dao.PurchaseRecordWithItems
import com.xiaoqi.companion.data.db.entity.InventoryItemEntity
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 记录数据导出器:餐食/采购/库存 → 分析友好的 zip 契约。
 *
 * 契约(分域文件 + 自描述清单,外部系统的校验锚点是 manifest.json):
 * - meals.jsonl        一行一餐,嵌套 items(结构保真,给程序消费)
 * - items.csv          拍平明细(record_id 关联,给 pandas/Excel)
 * - daily_summary.csv  天×营养聚合(最常用粒度,省下游自己算)
 * - purchases.csv / inventory.csv
 * - images/<uuid>.jpg  记录原图(入库时已压到 1280px/JPEG 82,导出不再二次压缩)
 * - manifest.json      版本/构建标识/行数/导出时间/图片数
 *
 * 语义约定:
 * - effective_time = photoTakenAt(EXIF 拍摄时刻)优先,缺失回退 createdAt/purchasedAt;
 *   time_source 标注 exif/recorded,下游不需要猜用哪个时间。
 * - value_source = user_verified(isEstimate=false) / estimated —— 数值质量分级。
 * - image_paths 与 items 的 photo_index 按位对齐;文件缺失的位次为 null,不产生悬空引用。
 * - app_version/version_code/build_id 来自构建(Brand 版本 + git sha),
 *   用于对账"哪个构建产出的导出"——不再手写常量。
 */
object RecordExporter {

    const val SCHEMA_VERSION = 3
    private const val IMAGE_DIR = "images"

    /** sourcePath 是 DB imageLocalPath 里的原始绝对路径,导出时改写为 images/<文件名>。 */
    class ExportImage(val sourcePath: String, val bytes: ByteArray)

    private val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")
    private val json = Json { encodeDefaults = false }

    @Serializable
    data class MealItemJson(
        val name: String,
        val portion: String,
        val calories: Double,
        val protein_g: Double,
        val carbs_g: Double,
        val fat_g: Double,
        val value_source: String,
        val photo_index: Int? = null,
    )

    @Serializable
    data class MealJson(
        val id: String,
        val effective_time: String,
        val time_source: String,
        val meal_type: String,
        val lat: Double? = null,
        val lon: Double? = null,
        val confidence: Double = 0.0,
        // zip 相对路径,与 items.photo_index 按位对齐;未导出的位次为 null
        val image_paths: List<String?> = emptyList(),
        val items: List<MealItemJson>,
    )

    @Serializable
    data class Manifest(
        val schema_version: Int,
        val app_version: String,
        val version_code: Int,
        // 产出该导出的构建 git 短 sha;定位"数据是哪个版本记的"
        val build_id: String,
        val exported_at: String,
        val zone: String,
        val meal_count: Int,
        val meal_item_count: Int,
        val purchase_count: Int,
        val inventory_count: Int,
        val image_count: Int = 0,
        val image_total_bytes: Long = 0,
    )

    fun writeZip(
        out: OutputStream,
        meals: List<MealRecordWithItems>,
        purchases: List<PurchaseRecordWithItems>,
        inventory: List<InventoryItemEntity>,
        images: List<ExportImage> = emptyList(),
        exportedAtMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ) {
        val manifest = Manifest(
            schema_version = SCHEMA_VERSION,
            app_version = BuildConfig.BRAND_VERSION_NAME,
            version_code = BuildConfig.BRAND_VERSION_CODE,
            build_id = BuildConfig.BUILD_GIT_SHA,
            exported_at = timeFormatter.format(Instant.ofEpochMilli(exportedAtMillis).atZone(zone)),
            zone = zone.id,
            meal_count = meals.size,
            meal_item_count = meals.sumOf { it.items.size },
            purchase_count = purchases.size,
            inventory_count = inventory.size,
            image_count = images.size,
            image_total_bytes = images.sumOf { it.bytes.size.toLong() },
        )
        // 引用改写映射:只有真正进 zip 的文件才生成相对路径引用
        val imageMap = images.associate { it.sourcePath to "$IMAGE_DIR/${it.sourcePath.substringAfterLast('/')}" }
        ZipOutputStream(out).use { zip ->
            fun entry(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }

            entry("manifest.json", json.encodeToString(manifest))
            entry("meals.jsonl", buildMealsJsonl(meals, zone, imageMap))
            entry("items.csv", buildItemsCsv(meals, zone, imageMap))
            entry("daily_summary.csv", buildDailySummaryCsv(meals, zone))
            entry("purchases.csv", buildPurchasesCsv(purchases, zone, imageMap))
            entry("inventory.csv", buildInventoryCsv(inventory))
            images.forEach { image ->
                zip.putNextEntry(ZipEntry(imageMap.getValue(image.sourcePath)))
                zip.write(image.bytes)
                zip.closeEntry()
            }
        }
    }

    // imageLocalPath 逗号分隔 → 逐位映射;未传入 images 的位次为 null
    private fun relativePaths(imageLocalPath: String?, imageMap: Map<String, String>): List<String?> =
        imageLocalPath?.split(',')?.filter { it.isNotBlank() }?.map { imageMap[it] } ?: emptyList()

    private fun imageCell(paths: List<String?>, photoIndex: Int?): String =
        photoIndex?.let { paths.getOrNull(it) } ?: ""

    private fun effectiveTime(takenAt: Long?, fallback: Long, zone: ZoneId): Pair<String, String> {
        val millis = takenAt ?: fallback
        val source = if (takenAt != null) "exif" else "recorded"
        return timeFormatter.format(Instant.ofEpochMilli(millis).atZone(zone)) to source
    }

    private fun buildMealsJsonl(meals: List<MealRecordWithItems>, zone: ZoneId, imageMap: Map<String, String>): String {
        if (meals.isEmpty()) return ""
        return meals.joinToString("\n") { meal ->
            val (time, source) = effectiveTime(meal.record.photoTakenAt, meal.record.createdAt, zone)
            json.encodeToString(
                MealJson(
                    id = meal.record.id,
                    effective_time = time,
                    time_source = source,
                    meal_type = meal.record.mealType.name,
                    lat = meal.record.lat,
                    lon = meal.record.lon,
                    confidence = meal.record.confidence,
                    image_paths = relativePaths(meal.record.imageLocalPath, imageMap),
                    items = meal.items.map { item ->
                        MealItemJson(
                            name = item.name,
                            portion = item.portionText,
                            calories = item.estCalories,
                            protein_g = item.estProteinG,
                            carbs_g = item.estCarbsG,
                            fat_g = item.estFatG,
                            value_source = if (item.isEstimate) "estimated" else "user_verified",
                            photo_index = item.photoIndex,
                        )
                    },
                )
            )
        } + "\n"
    }

    private fun buildItemsCsv(meals: List<MealRecordWithItems>, zone: ZoneId, imageMap: Map<String, String>): String = buildString {
        appendLine("record_id,effective_time,time_source,meal_type,food_name,portion,calories,protein_g,carbs_g,fat_g,value_source,photo_index,image_path")
        meals.forEach { meal ->
            val (time, source) = effectiveTime(meal.record.photoTakenAt, meal.record.createdAt, zone)
            val imagePaths = relativePaths(meal.record.imageLocalPath, imageMap)
            meal.items.forEach { item ->
                appendLine(
                    listOf(
                        csvCell(meal.record.id),
                        time,
                        source,
                        meal.record.mealType.name,
                        csvCell(item.name),
                        csvCell(item.portionText),
                        item.estCalories,
                        item.estProteinG,
                        item.estCarbsG,
                        item.estFatG,
                        if (item.isEstimate) "estimated" else "user_verified",
                        item.photoIndex ?: "",
                        csvCell(imageCell(imagePaths, item.photoIndex)),
                    ).joinToString(",")
                )
            }
        }
    }

    private fun buildDailySummaryCsv(meals: List<MealRecordWithItems>, zone: ZoneId): String = buildString {
        appendLine("date,meal_count,total_calories,total_protein_g,total_carbs_g,total_fat_g")
        meals.groupBy { meal ->
            Instant.ofEpochMilli(meal.record.photoTakenAt ?: meal.record.createdAt)
                .atZone(zone).toLocalDate()
        }.toSortedMap().forEach { (date, dayMeals) ->
            appendLine(
                listOf(
                    date.toString(),
                    dayMeals.size,
                    dayMeals.sumOf { it.record.totalCalories }.toInt(),
                    dayMeals.sumOf { it.record.totalProteinG }.toInt(),
                    dayMeals.sumOf { it.record.totalCarbsG }.toInt(),
                    dayMeals.sumOf { it.record.totalFatG }.toInt(),
                ).joinToString(",")
            )
        }
    }

    private fun buildPurchasesCsv(purchases: List<PurchaseRecordWithItems>, zone: ZoneId, imageMap: Map<String, String>): String = buildString {
        appendLine("record_id,effective_time,time_source,store_name,item_name,brand,quantity,unit,unit_price,photo_index,image_path")
        purchases.forEach { purchase ->
            val (time, source) = effectiveTime(purchase.record.photoTakenAt, purchase.record.purchasedAt, zone)
            val imagePaths = relativePaths(purchase.record.imageLocalPath, imageMap)
            purchase.items.forEach { item ->
                appendLine(
                    listOf(
                        csvCell(purchase.record.id),
                        time,
                        source,
                        csvCell(purchase.record.storeName ?: ""),
                        csvCell(item.name),
                        csvCell(item.brand ?: ""),
                        item.quantity,
                        csvCell(item.unit),
                        item.unitPrice ?: "",
                        item.photoIndex ?: "",
                        csvCell(imageCell(imagePaths, item.photoIndex)),
                    ).joinToString(",")
                )
            }
        }
    }

    private fun buildInventoryCsv(inventory: List<InventoryItemEntity>): String = buildString {
        appendLine("name,brand,unit,current_quantity,low_stock_threshold")
        inventory.forEach { item ->
            appendLine(
                listOf(
                    csvCell(item.name),
                    csvCell(item.brand ?: ""),
                    csvCell(item.unit),
                    item.currentQuantity,
                    item.lowStockThreshold ?: "",
                ).joinToString(",")
            )
        }
    }

    // RFC 4180 转义:含逗号/引号/换行的字段包引号,内部引号翻倍。菜名如 "鸡蛋,散装" 不转义会撕裂列
    private fun csvCell(raw: String): String =
        if (raw.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + raw.replace("\"", "\"\"") + "\""
        } else {
            raw
        }
}
