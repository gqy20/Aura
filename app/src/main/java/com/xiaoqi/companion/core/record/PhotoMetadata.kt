package com.xiaoqi.companion.core.record

/** 拍摄元数据:来自照片 EXIF,可能全部缺失(截图/转发图无 EXIF)。 */
data class PhotoMeta(
    val takenAtMillis: Long? = null,
    val lat: Double? = null,
    val lon: Double? = null,
)

/**
 * EXIF 元数据读取。餐次自动判定(EXIF 时间 > 模型 > 当前时间)与位置存档的数据源。
 * 实现必须对无 EXIF/解析失败静默返回 null 字段 —— EXIF 是"第一推断源",不是必需品。
 */
interface PhotoMetadataReader {
    suspend fun read(uriString: String): PhotoMeta?
}
