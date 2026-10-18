package com.xiaoqi.companion.data.record

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import com.xiaoqi.companion.core.record.BarcodeScannerSource
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * ML Kit 静态图条码识别(零售 EAN-13/UPC 优先,兼顾 QR)。
 * 走 Photo Picker 的既有 MVP 路线,不引入 CameraX —— 生命周期与运行时权限成本都省掉。
 */
class MlKitBarcodeScanner @Inject constructor(
    @ApplicationContext private val context: Context,
) : BarcodeScannerSource {

    override suspend fun scanFirst(uriString: String): String? = withContext(Dispatchers.IO) {
        val bytes = runCatching {
            context.contentResolver.openInputStream(Uri.parse(uriString))?.use { it.readBytes() }
        }.getOrNull()
        if (bytes == null) {
            AppLogger.warn(LogTags.Record, "barcode_scan_read_failed", "uriLength" to uriString.length)
            return@withContext null
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: return@withContext null
        val image = InputImage.fromBitmap(bitmap, 0)
        val client = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_EAN_13 or Barcode.FORMAT_EAN_8 or
                        Barcode.FORMAT_UPC_A or Barcode.FORMAT_UPC_E or
                        Barcode.FORMAT_QR_CODE,
                )
                .build(),
        )
        val scanned = withTimeoutOrNull(TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                client.process(image)
                    .addOnSuccessListener { barcodes ->
                        val raw = barcodes.firstOrNull()?.rawValue
                        if (cont.isActive) cont.resumeWith(Result.success(raw))
                    }
                    .addOnFailureListener { error ->
                        AppLogger.warn(
                            LogTags.Record,
                            "barcode_scan_failed",
                            "error" to (error.message ?: error::class.simpleName.orEmpty()),
                        )
                        if (cont.isActive) cont.resumeWith(Result.success(null))
                    }
                cont.invokeOnCancellation { client.close() }
            }
        }
        client.close()
        bitmap.recycle()
        AppLogger.info(
            LogTags.Record,
            "barcode_scan_completed",
            "found" to (scanned != null),
            "length" to scanned?.length,
        )
        scanned
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
