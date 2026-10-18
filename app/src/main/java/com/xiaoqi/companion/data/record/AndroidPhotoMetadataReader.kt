package com.xiaoqi.companion.data.record

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import com.xiaoqi.companion.core.record.PhotoMeta
import com.xiaoqi.companion.core.record.PhotoMetadataReader
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 从 content Uri 读 JPEG EXIF:拍摄时间(DateTimeOriginal)与 GPS 坐标。
 * 截图/IM 转发图无 EXIF → 全空字段,调用方按"无第一推断源"降级。
 */
class AndroidPhotoMetadataReader @Inject constructor(
    @ApplicationContext private val context: Context,
) : PhotoMetadataReader {

    override suspend fun read(uriString: String): PhotoMeta? = withContext(Dispatchers.IO) {
        val stream = runCatching {
            context.contentResolver.openInputStream(Uri.parse(uriString))
        }.getOrNull() ?: return@withContext null
        val meta = stream.use { input ->
            runCatching {
                val exif = ExifInterface(input)
                PhotoMeta(
                    takenAtMillis = parseTakenAt(exif),
                    lat = parseGps(exif, ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LATITUDE_REF, positive = 'N'),
                    lon = parseGps(exif, ExifInterface.TAG_GPS_LONGITUDE, ExifInterface.TAG_GPS_LONGITUDE_REF, positive = 'E'),
                )
            }.onFailure { error ->
                AppLogger.debug(
                    LogTags.Record,
                    "photo_meta_read_failed",
                    "error" to (error.message ?: error::class.simpleName.orEmpty()),
                )
            }.getOrNull()
        }
        AppLogger.debug(
            LogTags.Record,
            "photo_meta_read",
            "hasTakenAt" to (meta?.takenAtMillis != null),
            "hasGps" to (meta?.lat != null),
        )
        meta
    }

    private fun parseTakenAt(exif: ExifInterface): Long? {
        val raw = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
            ?: return null
        val local = runCatching {
            LocalDateTime.parse(raw, FORMATTER)
        }.getOrNull() ?: return null
        return local.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    // EXIF GPS 度量格式 "deg/1,min/1,sec/100",三元组不全视为无坐标
    private fun parseGps(exif: ExifInterface, tag: String, refTag: String, positive: Char): Double? {
        val raw = exif.getAttribute(tag) ?: return null
        val parts = raw.split(",").map { part ->
            part.trim().split("/").let { ratio ->
                val num = ratio.getOrNull(0)?.toDoubleOrNull() ?: return@map null
                val den = ratio.getOrNull(1)?.toDoubleOrNull() ?: 1.0
                if (den == 0.0) null else num / den
            }
        }
        if (parts.size != 3 || parts.any { it == null }) return null
        val value = parts[0]!! + parts[1]!! / 60.0 + parts[2]!! / 3600.0
        val ref = exif.getAttribute(refTag)?.trim()?.uppercase()
        return if (ref == positive.toString()) value else -value
    }

    private companion object {
        // EXIF 日期格式形如 "2026:10:18 08:30:00"
        val FORMATTER = java.time.format.DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")
    }
}
