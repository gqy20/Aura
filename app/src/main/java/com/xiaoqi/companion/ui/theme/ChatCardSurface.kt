package com.xiaoqi.companion.ui.theme

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 聊天域统一卡片容器:统一 [ChatColors.CardSurface] + [MaterialTheme.shapes.medium]。
 *
 * 把原本散在 8 个 Composable 里的 `Surface(shape = ..., color = ..., modifier = fillMaxWidth())`
 * 三行模板收敛到一处。变体(自定义 shape / 缺 fillMaxWidth / 加 clickable)继续用 [Surface]。
 */
@Composable
fun ChatCardSurface(
    modifier: Modifier = Modifier.fillMaxWidth(),
    content: @Composable () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = ChatColors.CardSurface,
        modifier = modifier,
        content = content,
    )
}
