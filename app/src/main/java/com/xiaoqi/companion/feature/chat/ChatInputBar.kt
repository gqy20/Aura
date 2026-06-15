package com.xiaoqi.companion.feature.chat

import android.content.Context
import android.provider.Settings
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import coil.compose.AsyncImage
import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import com.xiaoqi.companion.ui.theme.ChatColors
import kotlinx.coroutines.delay

/**
 * 聊天页底部输入栏:
 * - 文本输入框(OutlinedTextField)
 * - 图片选择按钮 + 待发送图片预览
 * - 发送按钮
 * - IME 卡死检测 + "Switch input" 兜底提示
 *
 * 副作用(IME 状态轮询)封装在本文件内,详见 [ImeSnapshot] / [currentImeSnapshot]。
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun InputBar(
    inputText: String,
    onInputTextChanged: (String) -> Unit,
    onSendMessage: () -> Unit,
    pendingImage: ChatImageAttachment?,
    isPreparingImage: Boolean,
    onPickImage: () -> Unit,
    onRemoveImage: () -> Unit,
    isLoading: Boolean = false,
    isConfigReady: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val canSend = (inputText.isNotBlank() || pendingImage != null) &&
        isConfigReady &&
        !isLoading &&
        !isPreparingImage
    val context = LocalContext.current
    val inputView = LocalView.current
    val primary = MaterialTheme.colorScheme.primary
    var imeStuck by remember { mutableStateOf(false) }

    Surface(
        color = Color.Transparent,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            pendingImage?.let {
                PendingImagePreview(
                    imageUri = it.uriString,
                    onRemoveImage = onRemoveImage,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    color = InputBarContainerColor,
                    shape = CircleShape,
                ) {
                    IconButton(
                        onClick = onPickImage,
                        enabled = !isLoading && !isPreparingImage,
                        modifier = Modifier.semantics { contentDescription = "添加图片" },
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = null,
                            tint = primary,
                        )
                    }
                }
                OutlinedTextField(
                    value = inputText,
                    onValueChange = onInputTextChanged,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                    placeholder = {
                        Text(
                            text = "说点什么",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Send,
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (canSend) onSendMessage()
                        },
                    ),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    shape = RoundedCornerShape(22.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = InputFieldFocusedColor,
                        unfocusedContainerColor = InputBarContainerColor,
                        disabledContainerColor = InputBarContainerColor.copy(alpha = 0.6f),
                        focusedIndicatorColor = primary.copy(alpha = 0.18f),
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = primary,
                    ),
                )
                if (isLoading || isPreparingImage) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp).padding(4.dp),
                        strokeWidth = 2.dp,
                        color = primary,
                    )
                } else {
                    Surface(
                        color = if (canSend) SendButtonReadyColor else InputBarContainerColor,
                        shape = CircleShape,
                    ) {
                        IconButton(
                            onClick = onSendMessage,
                            enabled = canSend,
                            modifier = Modifier.semantics { contentDescription = "发送" },
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = null,
                                tint = if (canSend) primary else primary.copy(alpha = 0.32f),
                            )
                        }
                    }
                }
            }
            ImeRecoveryHint(
                visible = imeStuck,
                onSwitchInputMethod = { context.showInputMethodPicker() },
            )
        }
    }

    LaunchedEffect(inputView) {
        while (true) {
            delay(IME_STUCK_CHECK_INTERVAL_MS)
            val state = inputView.currentImeSnapshot()
            val stuck = state.isWeChatInputMethod &&
                state.bottomInset in 1 until (inputView.height * MIN_USEFUL_IME_HEIGHT_RATIO).toInt()
            if (stuck != imeStuck) {
                imeStuck = stuck
            }
            if (stuck) {
                AppLogger.warn(
                    LogTags.Chat,
                    "ime_stuck_small_panel",
                    "defaultIme" to state.defaultIme,
                    "imeBottom" to state.bottomInset,
                    "viewHeight" to inputView.height,
                    "hasWindowFocus" to inputView.hasWindowFocus(),
                    "hasViewFocus" to inputView.hasFocus(),
                )
            }
        }
    }
}

@Composable
private fun ImeRecoveryHint(
    visible: Boolean,
    onSwitchInputMethod: () -> Unit,
) {
    if (!visible) {
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onSwitchInputMethod) {
            Text("切换输入法")
        }
    }
}

@Composable
private fun PendingImagePreview(
    imageUri: String,
    onRemoveImage: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.64f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = imageUri,
                contentDescription = "已选图片",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .height(78.dp)
                    .width(96.dp)
                    .clip(RoundedCornerShape(10.dp)),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "就绪",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "加句话，或直接发",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            IconButton(
                onClick = onRemoveImage,
                modifier = Modifier.semantics { contentDescription = "移除图片" },
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private data class ImeSnapshot(
    val defaultIme: String?,
    val bottomInset: Int,
) {
    val isWeChatInputMethod: Boolean =
        defaultIme?.startsWith("com.tencent.wetype/") == true
}

private fun View.currentImeSnapshot(): ImeSnapshot {
    val defaultIme = runCatching {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
    }.getOrNull()
    val bottomInset = ViewCompat.getRootWindowInsets(this)
        ?.getInsets(WindowInsetsCompat.Type.ime())
        ?.bottom ?: 0
    return ImeSnapshot(
        defaultIme = defaultIme,
        bottomInset = bottomInset,
    )
}

private fun Context.showInputMethodPicker() {
    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
    imm?.showInputMethodPicker()
}

private const val MIN_USEFUL_IME_HEIGHT_RATIO = 0.12f
private const val IME_STUCK_CHECK_INTERVAL_MS = 350L

// 图片按钮 / 输入框 unfocused / 发送按钮 disabled 共用同一容器色,
// 比 #FFF8EA 再淡一档,让三处看起来同源;焦点态用 #FFFCF6 区分。
private val InputBarContainerColor = Color(0xFFFBF5E7)
private val InputFieldFocusedColor = ChatColors.InputSurface
private val SendButtonReadyColor = ChatColors.BubbleUser
