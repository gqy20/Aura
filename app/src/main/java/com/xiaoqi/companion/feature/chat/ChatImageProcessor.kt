package com.xiaoqi.companion.feature.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PreparedChatImage(
    val uriString: String,
    val imageBase64: String,
    val mediaType: String = "image/jpeg",
)

interface ChatImageProcessor {
    suspend fun prepare(uriString: String): PreparedChatImage
}

class AndroidChatImageProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
) : ChatImageProcessor {

    override suspend fun prepare(uriString: String): PreparedChatImage = withContext(Dispatchers.IO) {
        val uri = Uri.parse(uriString)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = context.contentResolver.openInputStream(uri) ?: error("无法读取图片")
        boundsStream.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        }

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            error("图片格式无法解析")
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight)
        }
        val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: error("图片解码失败")

        val normalized = decoded.scaleToMaxSide(MAX_IMAGE_SIDE)
        val bytes = ByteArrayOutputStream().use { output ->
            normalized.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
            output.toByteArray()
        }
        if (normalized !== decoded) {
            normalized.recycle()
        }
        decoded.recycle()

        PreparedChatImage(
            uriString = uriString,
            imageBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP),
            mediaType = "image/jpeg",
        )
    }

    private fun calculateInSampleSize(width: Int, height: Int): Int {
        var sampleSize = 1
        var halfWidth = width / 2
        var halfHeight = height / 2
        while (halfWidth / sampleSize >= MAX_IMAGE_SIDE && halfHeight / sampleSize >= MAX_IMAGE_SIDE) {
            sampleSize *= 2
        }
        return sampleSize
    }

    private fun Bitmap.scaleToMaxSide(maxSide: Int): Bitmap {
        val sourceMaxSide = max(width, height)
        if (sourceMaxSide <= maxSide) return this

        val scale = maxSide.toFloat() / sourceMaxSide.toFloat()
        val targetWidth = (width * scale).roundToInt().coerceAtLeast(1)
        val targetHeight = (height * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true)
    }

    private companion object {
        const val MAX_IMAGE_SIDE = 1280
        const val JPEG_QUALITY = 82
    }
}
