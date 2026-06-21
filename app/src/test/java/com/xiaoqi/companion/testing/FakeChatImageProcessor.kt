package com.xiaoqi.companion.testing

import com.xiaoqi.companion.feature.chat.ChatImageProcessor
import com.xiaoqi.companion.feature.chat.PreparedChatImage

/**
 * 图片预处理器的 Fake 实现 —— [prepare] 返回固定 base64 占位,通过 [shouldFail] 模拟失败。
 *
 * 用于 ChatViewModelTest、SendMessageUseCaseTest 等需要 ChatImageProcessor
 * 但不关心真实图片编解码的场景。
 */
class FakeChatImageProcessor : ChatImageProcessor {
    var shouldFail = false

    override suspend fun prepare(uriString: String): PreparedChatImage {
        if (shouldFail) error("bad image")
        return PreparedChatImage(
            uriString = uriString,
            imageBase64 = "prepared-base64",
            mediaType = "image/jpeg",
        )
    }
}
