package com.xiaoqi.companion.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Onboarding 5 问(plan §5.2 冷启动种子期):
 * 1. 最近 1-2 件挂心事
 * 2. 未来 14 天 1-2 个重要日期
 * 3. 称呼 + 说话风格
 * 4. 3 个高频聊天朋友/家人名字
 * 5. 作息节奏(早睡/晚睡/工作日周末区别)
 *
 * 不入 LLM,纯模板表单;每步非空才能进下一步。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    var step by rememberSaveable { mutableStateOf(0) }
    var q1 by rememberSaveable { mutableStateOf("") }
    var q2 by rememberSaveable { mutableStateOf("") }
    var q3 by rememberSaveable { mutableStateOf("") }
    val q4Friends = remember { mutableStateOf(listOf("", "", "")) }
    var q5Choice by rememberSaveable { mutableStateOf("") }

    val isLast = step == 4
    val progress = (step + 1) / 5f

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("认识 Aura") },
                navigationIcon = {
                    if (step > 0) {
                        IconButton(onClick = { step-- }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "上一步")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (step) {
                    0 -> item {
                        QuestionBlock(
                            title = "最近 1-2 件让你挂心的事?",
                            subtitle = "你最近在忙什么",
                        ) {
                            OutlinedTextField(
                                value = q1,
                                onValueChange = { q1 = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("例:换工作的面试,下周的体检") },
                                minLines = 3,
                                maxLines = 6,
                            )
                        }
                    }
                    1 -> item {
                        QuestionBlock(
                            title = "未来 14 天的 1-2 个重要日期?",
                            subtitle = "想被提醒的事",
                        ) {
                            OutlinedTextField(
                                value = q2,
                                onValueChange = { q2 = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("例：6/22 妈妈生日，6/30 论文截止") },
                                minLines = 2,
                                maxLines = 4,
                            )
                        }
                    }
                    2 -> item {
                        QuestionBlock(
                            title = "希望我怎么称呼你 / 怎么说话?",
                            subtitle = "希望的称呼和语气",
                        ) {
                            OutlinedTextField(
                                value = q3,
                                onValueChange = { q3 = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("例：叫我小王，语气像朋友聊天") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Sentences,
                                ),
                            )
                        }
                    }
                    3 -> item {
                        QuestionBlock(
                            title = "3 个高频聊天的朋友/家人",
                            subtitle = "常聊到的名字",
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                q4Friends.value.forEachIndexed { index, name ->
                                    OutlinedTextField(
                                        value = name,
                                        onValueChange = { newValue ->
                                            q4Friends.value = q4Friends.value.toMutableList().also {
                                                it[index] = newValue
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        label = { Text("朋友 ${index + 1}") },
                                        singleLine = true,
                                    )
                                }
                            }
                        }
                    }
                    4 -> item {
                        QuestionBlock(
                            title = "你最近的作息节奏?",
                            subtitle = "选最贴近的",
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                // chip 内嵌 Column 容易高度不一致,label 本身已 self-descriptive,hint 删掉。
                                listOf(
                                    "早睡早起",
                                    "晚睡晚起",
                                    "工作日规律,周末放飞",
                                ).forEach { label ->
                                    FilterChip(
                                        selected = q5Choice == label,
                                        onClick = { q5Choice = label },
                                        label = { Text(label) },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (step > 0) {
                    TextButton(onClick = { step-- }) {
                        Text("上一步")
                    }
                } else {
                    Spacer(Modifier.width(1.dp))
                }
                Button(
                    onClick = {
                        if (isLast) {
                            viewModel.saveAnswers(
                                concerns = q1,
                                upcomingDates = q2,
                                addressStyle = q3,
                                friends = q4Friends.value,
                                scheduleChoice = q5Choice,
                                onDone = onComplete,
                            )
                        } else {
                            step++
                        }
                    },
                    // 称呼是用户身份的核心信息,必须填,否则 LLM 后续不知道怎么称呼用户。
                    // 其他步 q1/q2/q4/q5 是次要信息,空也能跳过。
                    enabled = step != 2 || q3.isNotBlank(),
                ) {
                    Text(if (isLast) "完成" else "下一步")
                    if (!isLast) {
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Filled.ArrowForward, contentDescription = null)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuestionBlock(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = Color(0xFFFFF8EA),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            content()
        }
    }
}
