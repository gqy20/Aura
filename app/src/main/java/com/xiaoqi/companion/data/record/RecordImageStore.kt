package com.xiaoqi.companion.data.record

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// 记录图片走文件系统 + DB 存路径：高频餐食/小票照片若沿用 MemoryEntity.imageBase64 的
// base64-in-Room 模式会把库撑到几十 MB（roadmap MR 的刻意分歧点）
class RecordImageStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    suspend fun save(imageBytes: ByteArray, mediaType: String): String = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, DIRECTORY)
        dir.mkdirs()
        val file = File(dir, "${UUID.randomUUID()}${extensionFor(mediaType)}")
        file.writeBytes(imageBytes)
        file.absolutePath
    }

    // base64 解码留在本类:RecordImageStoreTest 走 Robolectric,ViewModel 纯 JVM 测试可继续 mock
    suspend fun saveBase64(imageBase64: String, mediaType: String): String =
        save(android.util.Base64.decode(imageBase64, android.util.Base64.DEFAULT), mediaType)

    fun resolve(path: String): File = File(path)

    suspend fun delete(path: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(path)
        // 只删本目录下的文件，防止 DB 路径被污染后误删任意位置
        if (file.parentFile == File(context.filesDir, DIRECTORY)) file.delete() else false
    }

    private fun extensionFor(mediaType: String): String = when (mediaType.substringAfter('/').lowercase()) {
        "png" -> ".png"
        "webp" -> ".webp"
        else -> ".jpg"
    }

    private companion object {
        const val DIRECTORY = "record_images"
    }
}
