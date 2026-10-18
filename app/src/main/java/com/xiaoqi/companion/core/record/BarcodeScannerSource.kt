package com.xiaoqi.companion.core.record

/** 条码扫描:输入图片 Uri,返回首个识别到的条码原始值;null = 没有可识别条码。 */
interface BarcodeScannerSource {
    suspend fun scanFirst(uriString: String): String?
}
