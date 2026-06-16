# Aura 端侧 Qwen / MNN / SME2 调研备忘

> 更新时间：2026-06-16
>
> 范围：整理将 Qwen 小模型加入 Aura Android 项目、在手机本地部署的可行性分析。本文不包含后续 ADB 连机调试记录。

## 1. 结论摘要

可以实现，但不建议把端侧 Qwen 直接替代现有 GLM/Kimi 云端模型。更稳妥的产品和工程路线是：

- 云端 GLM/Kimi：继续作为高质量主模型，负责复杂推理、长上下文、Vision、工具调用和高稳定性回复。
- 本地 Qwen 小模型：作为离线模式、隐私模式、低延迟短聊、后台轻任务的小模型后端。
- 架构上采用“端云协同”，而不是二选一。

推荐第一阶段只做“本地文本聊天 MVP”：

- 支持文本输入。
- 支持流式 token 输出。
- 使用简化 prompt。
- 暂不启用 tools。
- 暂不启用 Vision。
- 支持失败时 fallback 到云端模型。

## 2. 当前项目适配性

Aura 现在的主链路是：

```text
ChatViewModel
  -> CompanionRuntime
  -> KoogAgentFactory
  -> Koog AIAgent / PromptExecutor
  -> AnthropicMessagesLLMClient
  -> GLM / Kimi Anthropic-compatible API
```

这套链路很适合云端 API，但本地推理不适合伪装成 Anthropic HTTP client。更好的做法是抽象一层模型运行时：

```kotlin
interface ChatModelRuntime {
    fun runEvents(prompt: BuiltPrompt): Flow<KoogAgentEvent>
    suspend fun <T> runStructured(...)
}
```

然后分成两个实现：

- `RemoteKoogChatModelRuntime`：复用现有 Koog + Anthropic Messages client。
- `LocalQwenChatModelRuntime`：接 MNN、llama.cpp 或其他 native 推理引擎。

这样 UI、Room、记忆展示、情绪状态、流式事件模型可以尽量复用，避免把本地模型逻辑侵入现有 Koog 云端链路。

## 3. Qwen 2B 端侧模型可行性

Qwen 2B 级模型适合作为手机本地小模型尝试，尤其是量化后用于短文本聊天和轻量任务。

需要注意：

- `2B` 在手机上仍然不轻，Q4/Q5 量化后模型文件通常仍是 GB 级。
- 不建议把模型直接打进 APK，应采用首次下载或用户手动导入。
- 实际运行还需要 KV cache、tokenizer、临时 buffer，内存压力不只来自模型文件。
- 端侧 2B 不适合一开始承担完整 agent tools、长上下文、多轮规划和复杂结构化输出。
- Vision 多模态成本更高，应放到文本 MVP 之后验证。

相关参考：

- [Qwen/Qwen3.5-2B](https://huggingface.co/Qwen/Qwen3.5-2B)
- [Qwen/Qwen3-VL-2B-Instruct](https://huggingface.co/Qwen/Qwen3-VL-2B-Instruct)
- [taobao-mnn/Qwen3.5-2B-MNN](https://huggingface.co/taobao-mnn/Qwen3.5-2B-MNN)

## 4. MNN 路线判断

MNN 是可行路线，尤其适合 Android 端侧部署和后续性能优化。MNN 官方已有 Android LLM Chat App，并支持端侧 LLM/多模态方向。

MNN 路线可以理解为：

```text
MNN 模型
  -> MNN runtime
  -> ARM CPU / OpenCL / Vulkan / KleidiAI / SME2 等优化路径
  -> Android App
```

优势：

- 更贴近移动端工程和 Android 性能优化。
- 与 Arm SME2、KleidiAI 等新硬件优化方向更契合。
- 已有 Qwen MNN 格式模型可作为切入点，减少从零转换风险。

风险：

- 模型格式、转换、算子覆盖和 native 接入调试成本高于普通云端 API。
- 文档和生态模型数量不如 GGUF / llama.cpp 路线丰富。
- 真实性能需要按设备实测，不能只看宣传指标。

相关参考：

- [Alibaba MNN GitHub](https://github.com/alibaba/MNN)
- [MNN Android LLM Chat App](https://github.com/alibaba/MNN/blob/master/apps/Android/MnnLlmChat/README.md)

## 5. SME2 判断

SME2 是 Arm Scalable Matrix Extension 2，主要用于提升矩阵计算能力，对端侧 AI/LLM 推理有潜在加速价值。

需要谨慎理解：

- SME2 是加速红利，不是本地模型运行的必要条件。
- 不是所有 Armv9 手机都有 SME2。
- 需要具体 CPU 核心和 SoC 实现支持。
- 没有 SME2 的设备仍然可以跑 MNN，只是走普通 CPU/OpenCL/Vulkan/厂商后端。

时间线：

- 架构层面，SME2 大约在 2022 年 Arm A-profile architecture updates 中出现。
- Android 手机端的大规模宣传和落地主要是 2025 年之后。
- 对普通开发者真正值得关注，大概率从 2026 年新设备开始。

比较明确的支持方向：

- MediaTek Dimensity 9500 官方明确提到 Armv9.3 和 SME2。
- 搭载 Dimensity 9500 的机型更值得作为 SME2 测试设备候选。

不应默认支持的方向：

- Snapdragon 8 Elite / 小米 15 Pro 这类 Oryon CPU 设备，不应按 Arm SME2 路线推断。
- Dimensity 9400、旧款 Cortex-X/A 系列设备也不应默认支持 SME2。

实机判断方式：

```bash
adb shell "cat /proc/cpuinfo | grep -i sme"
```

如果系统暴露了 `sme2` feature，才算实锤。

NDK 内可以用 `getauxval(AT_HWCAP2)` 检测：

```cpp
#include <sys/auxv.h>
#include <asm/hwcap.h>

bool has_sme2() {
    unsigned long hwcap2 = getauxval(AT_HWCAP2);
    return (hwcap2 & HWCAP2_SME2) != 0;
}
```

相关参考：

- [Arm SME2](https://www.arm.com/technologies/sme2)
- [Arm: SME2 for Android mobile apps](https://newsroom.arm.com/blog/arm-sme2-android-mobile-apps)
- [MediaTek Dimensity 9500](https://www.mediatek.com/products/smartphones/mediatek-dimensity-9500)
- [Android NDK CPU features](https://developer.android.com/ndk/guides/cpu-features)

## 6. PocketPal 技术路线参考

PocketPal 是一个有参考价值的端侧 LLM App。它的核心路线是：

```text
React Native UI
  -> llama.rn / native binding
  -> llama.cpp
  -> GGUF 量化模型
  -> 手机本地推理
```

它说明了手机运行大模型的关键不是云端 API，而是：

- native 推理内核。
- GGUF 或其他端侧友好模型格式。
- Q4/Q5/Q8 等量化模型。
- 模型文件本地下载和管理。
- token-by-token 流式输出。
- 上下文长度、采样参数、模板和内存管理。

PocketPal 路线与 MNN 路线对比：

| 路线 | 优势 | 风险 |
| --- | --- | --- |
| llama.cpp / GGUF | 模型生态丰富，原型快，社区资料多 | Android 产品级性能优化可能需要额外打磨 |
| MNN | 更贴移动端部署，适合 Android 性能优化和 SME2/KleidiAI 路线 | 接入和模型适配复杂度更高 |

对 Aura 的启发：

- 如果目标是最快验证端侧聊天体验，可以先考虑 llama.cpp/GGUF。
- 如果目标是长期做 Android 手机端 AI 性能优化，MNN 更值得投入。
- 两条路线都应该包在 `LocalModelRuntime` 抽象后面，避免污染业务层。

相关参考：

- [PocketPal 官网](https://pocketpal.dev/)
- [PocketPal GitHub](https://github.com/a-ghorbani/pocketpal-ai)

## 7. 推荐实施阶段

### 阶段 1：本地文本聊天原型

目标：证明 Aura 可以在手机本地跑小模型并接入现有聊天 UI。

建议范围：

- 新增 `LlmProvider.LOCAL_QWEN`。
- 新增 `LocalQwenEngine` / `LocalModelRuntime` 接口。
- 先用 fake engine 测通 UI、Runtime 和事件流。
- 接入一个真实 native 后端。
- 只支持文本输入和流式输出。
- 本地 prompt 只包含人格、最近几轮对话、少量记忆。
- 禁用 tools 和 Vision。

预计新增代码量：约 1500-2500 行。

### 阶段 2：产品化 MVP

目标：让本地模型成为用户可感知、可管理的功能。

建议范围：

- 模型下载、校验、删除。
- 模型状态显示。
- 设置页选择本地/云端模型。
- 本地推理失败 fallback 云端。
- 设备能力检测：ABI、RAM、是否 SME2、tokens/s benchmark。
- 本地 prompt 压缩策略。

预计新增代码量：在阶段 1 基础上增加约 1300-2850 行。

### 阶段 3：端侧增强能力

目标：让本地模型参与 Aura 的核心陪伴能力。

建议范围：

- 本地摘要、分类、记忆候选提取。
- 少量只读工具调用。
- 更严格的结构化输出 parser。
- Vision 输入实验。
- 性能/温度/内存策略。

预计新增代码量：再增加约 2000-5000 行。

## 8. 对 Aura 的推荐定位

本地 Qwen 小模型最适合在 Aura 中承担这些角色：

- 离线陪伴短聊。
- 隐私模式。
- 低成本后台反思或摘要。
- 记忆候选提取。
- 情绪/主题分类。
- 云端请求失败时的降级回复。

不建议第一版承担：

- 完整 Koog tools agent。
- 长上下文多轮规划。
- 高稳定 JSON function calling。
- Vision 主链路。
- 完整替代 GLM/Kimi。

最终推荐架构：

```text
                +----------------------+
User / Chat UI -> CompanionRuntime      |
                +----------+-----------+
                           |
               +-----------+------------+
               |                        |
       RemoteKoogRuntime          LocalModelRuntime
       GLM / Kimi API             Qwen on device
       Koog tools                 MNN or llama.cpp
       Vision / complex tasks     offline / privacy / short chat
```

这条路线既保留现有云端 agent 能力，也为 Aura 增加真正的端侧生命感和隐私能力。

---

## 9. 实际落地状态（2026-06-16 更新）

调研完成 16 天后，跟进 M3 / M4 阶段的实际落地情况（commit `1b826d1` 起）：

### 阶段 1（本地文本聊天原型）— ✅ 已落地

调研建议的"阶段 1：本地文本聊天 MVP"全部落地：

| 调研建议 | 实际落地 | 引用 |
|---------|---------|------|
| 新增 `LlmProvider.LOCAL_QWEN` | ✅ `LlmProvider.LOCAL_QWEN` 已在 SettingsScreen ProviderPicker | `feature/chat/SettingsScreen.kt` |
| 新增 `LocalQwenEngine` / `LocalModelRuntime` 接口 | ✅ `LocalQwenEngine` + `MnnLocalQwenEngine` + `NativeMnnLlmBridge` 落地 | `core/local/` |
| 接入真实 native 后端 | ✅ MNN native 桥接 + ARM CPU 推理路径 | `core/local/MnnLocalQwenEngine.kt` |
| 文本输入 + 流式输出 | ✅ `LocalQwenExecutor` 包装 MNN + `Request(maxTokens/temperature)` + `parsePatternDetectOutput` | `core/presence/runtime/LocalQwenExecutor.kt` |
| 本地 prompt 简化 | ✅ DreamDataCollector 生成轻量 prompt（不含 base64） | `core/presence/runtime/DreamDataCollector.kt` |
| 禁用 tools | ✅ MNN 路径不挂 Koog tool loop | runtime 设计 |
| 失败 fallback 云端 | ✅ SettingsScreen 允许 Provider 切换，Runtime 按 Provider 路由 | `CompanionRuntime` |

### 阶段 2（产品化 MVP）— ✅ 已落地

| 调研建议 | 实际落地 | 引用 |
|---------|---------|------|
| 模型下载、校验、删除 | ✅ `LocalQwenModelDownloader` UI 入口（SettingsScreen "下载 / 删除" 按钮） | commit `5d1920b` 修"两个未安装"显示 |
| 模型状态显示 | ✅ `LocalQwenDownloadSection` 实时显示下载进度 + 字节数 + 状态 | `feature/chat/SettingsScreen.kt` |
| 设置页选择本地/云端 | ✅ ProviderPicker 包含 `Local Qwen` chip | 同上 |
| 设备能力检测 | ⏳ SME2 检测未做（调研结论"SME2 不是必要条件"） | — |
| 本地 prompt 压缩 | ✅ DreamDataCollector 走 metadata-only 路径 | 阶段 1 落地 |

### 阶段 3（端侧增强能力）— 🚧 部分落地

| 调研建议 | 实际落地 | 引用 |
|---------|---------|------|
| 本地摘要 / 分类 / 记忆候选提取 | ✅ `parsePatternDetectOutput` 走 `LocalQwenExecutor` | `core/presence/runtime/LocalQwenExecutor.kt` |
| 少量只读工具调用 | ⏳ 未做（依赖 P2 tool decision 抽象） | — |
| 更严格结构化输出 parser | ✅ `parsePatternDetectOutput` + `InsightValidator` 8 边界 | commit `5b77241` |
| Vision 输入实验 | ❌ 未做（调研建议"放到文本 MVP 之后"，M4 已走云端 GLM Vision） | — |

### 调研结论的验证

- ✅ **"端云协同，而非二选一"**：Aura 实际采用 Provider 切换模型，云端 GLM/Kimi + 本地 Qwen 0.8B 并存。
- ✅ **"MNN 是可行路线"**：`MnnLocalQwenEngine` + `NativeMnnLlmBridge` 跑通，本地文本生成可用。
- ✅ **"Q4/Q5 量化后 GB 级"**：当前 Qwen 0.8B MNN 模型约 1GB，与调研判断一致。
- ✅ **"SME2 不是必要条件"**：当前未做 SME2 优化，普通 ARM CPU 跑通。

### 当前未做的项

1. **SME2 / KleidiAI 优化路径**：调研时标注"2026 年新设备开始关注"，当前真机是 realme RMX3888 (ARMv8.2)，MNN 走普通 CPU 路径够用。
2. **本地 LLM 替代 GLM Vision**：调研时标注"放到文本 MVP 之后"，目前 Vision 仍走云端 GLM-5v-turbo，本地仅服务 DreamLoop 文本分析。
3. **PocketPal 风格的 llama.cpp/GGUF 路线**：选择 MNN 路线后未实施，作为未来备选。

### 后续建议

- M3 PoC 完善（用户在真机触发 Qwen 模型下载 → DreamLoop 跑出第一条 LLM 真实生成的 insight）是当前最优先项。
- 本地 LLM 增强能力（阶段 3）依赖 M5 PulseWorker 落地后的内存预算反馈再排期。
- llama.cpp/GGUF 路线作为"Plan B"，若 MNN 路线出现性能瓶颈再考虑切换。

