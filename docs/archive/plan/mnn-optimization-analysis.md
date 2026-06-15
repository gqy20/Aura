# Aura MNN 深度优化调研与差距分析

> Archived on 2026-06-15. Kept for historical design context; no longer a current planning entry.
>
> 更新时间：2026-06-15
>
> 测试设备：Realme RMX3562 / Dimensity 8200 (MT6895) / 4×A78 + 4×A55 / Mali-G610 MC6 / 12GB RAM / Android 14
>
> 范围：对照 MNN 官方 LLM Chat App 和最新编译选项，分析 Aura 当前 MNN 实现的优化差距和可执行改进方案。

---

## 0. 结论摘要

Aura 的 MNN JNI 桥接已经做了**正确的基础工作**（mmap、KV-cache 持久化、prefix cache、UTF-8 流式处理、Android stepping 兼容），但在 **编译选项、运行时配置、GPU 后端、投机解码、采样调优** 五个维度还有明确的优化空间。

**优化优先级排序**（投入产出比从高到低）：

| 优先级 | 优化项 | 预期收益 | 工作量 |
|---|---|---|---|
| P0 | MNN 编译选项补全（LOW_MEMORY + TRANSFORMER_FUSE + ARM82） | decode 速度 +20-40% | 改 CMake/build.sh，半天 |
| P0 | 运行时 config 参数补全（thread_num + precision + sampler） | decode 速度 +10-20% | 改 JNI set_config，2h |
| P1 | 按场景动态调采样参数（心跳/Dream/对话不同配置） | 心跳推理省电 50%+ | Kotlin 层改动，1天 |
| P1 | OpenCL GPU 后端启用 | prefill 速度 +2-5x | 重编 MNN + 运行时切换，1-2天 |
| P2 | AWQ 量化模型替换 | 同等大小下精度更好 | 模型重新导出 + 下载，1天 |
| P2 | Android PerformanceHint API | 系统级调度优化 | JNI + Kotlin，半天 |
| P3 | 投机解码（EAGLE / DFlash） | decode 速度 +2-3x | 需要额外 draft 模型，3-5天 |
| P3 | CPU 大核绑定（sched_setaffinity） | decode 速度 +5-15% | JNI 改动，半天 |

---

## 1. 当前实现状态

### 1.1 已经做对的

Aura 的 `aura_mnn_llm_jni.cpp` 已经启用了以下关键特性：

```cpp
// aura_mnn_llm_jni.cpp L324-331 — 已启用的 set_config
llm->set_config(
    "{\"tmp_path\":\"" + tmpDir +
    "\",\"prefix_cache_path\":\"" + prefixCacheDir +
    "\",\"use_mmap\":true,\"kvcache_mmap\":true,"    // ✅ mmap 加载
    "\"reuse_kv\":true,"                              // ✅ KV-cache 复用
    "\"prompt_cache\":true,"                          // ✅ Prompt 缓存
    "\"max_new_tokens\":" + ... + ","
    "\"jinja\":{\"context\":{\"enable_thinking\":false}}}");
```

其他已实现的优化：
- ✅ `setPrefixCacheFile` + `syncPromptCache`：多轮对话前缀缓存持久化
- ✅ `AndroidSteppingState`：兼容 MNN 的 stepping 机制
- ✅ `Utf8StreamProcessor`：正确的 UTF-8 流式处理
- ✅ `CallbackStreamBuffer`：零拷贝流式输出
- ✅ 性能指标收集：prompt_tokens / completion_tokens / prefill_us / decode_us / load_us
- ✅ `arm64-v8a` only（不编译 32 位，减少 APK 体积）
- ✅ `ANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON`（Android 15 16KB 页面兼容）

### 1.2 缺失的优化（差距分析）

---

## 2. P0 — 编译选项补全

### 2.1 问题

当前 Aura 的 MNN 编译**没有使用**官方推荐的优化选项。对比 MnnLlmChat 的 `build.sh`：

| 编译选项 | MnnLlmChat | Aura 当前 | 差距 |
|---|---|---|---|
| `MNN_LOW_MEMORY=true` | ✅ | ❌ | **缺少**：低内存权重量化推理 |
| `MNN_SUPPORT_TRANSFORMER_FUSE=true` | ✅ | ❌ | **缺少**：Transformer 算子融合 |
| `MNN_ARM82=true` | ✅ | ❓（默认 ON 但未显式指定） | **建议**：显式启用 |
| `MNN_USE_THREAD_POOL=true` | ✅ | ❓（默认 ON） | **建议**：显式启用 |
| `MNN_OPENCL=true` | ✅ | ❌ | **缺少**：GPU 后端 |
| `MNN_SEP_BUILD=OFF` | ✅ | ❌ | **缺少**：单库编译减少加载开销 |
| `MNN_USE_LOGCAT=true` | ✅ | ❌ | **缺少**：MNN 内部日志输出到 logcat |
| 16KB page alignment | ✅ | ❌ | **缺少**：Android 15 兼容 |

### 2.2 修复方案

**方案 A**（推荐）：使用 MnnLlmChat 相同的编译脚本重新编译 MNN：

```bash
cd $AURA_MNN_HOME/project/android && mkdir -p build_64 && cd build_64
../build_64.sh "\
-DMNN_LOW_MEMORY=true \
-DMNN_BUILD_LLM=true \
-DMNN_SUPPORT_TRANSFORMER_FUSE=true \
-DMNN_ARM82=true \
-DMNN_USE_LOGCAT=true \
-DMNN_OPENCL=true \
-DMNN_SME2=true \
-DMNN_KLEIDIAI=true \
-DMNN_USE_THREAD_POOL=true \
-DMNN_SEP_BUILD=OFF \
-DCMAKE_SHARED_LINKER_FLAGS='-Wl,-z,max-page-size=16384' \
-DCMAKE_INSTALL_PREFIX=."
make -j$(nproc) install
```

**预期收益**：
- `MNN_LOW_MEMORY` + `MNN_SUPPORT_TRANSFORMER_FUSE`：decode 速度 **+20-40%**
- `MNN_OPENCL`：prefill 速度 **+2-5x**（GPU 加速 prefill）
- `MNN_SEP_BUILD=OFF`：减少动态库加载开销

### 2.3 CMakeLists.txt 同步更新

Aura 的 `CMakeLists.txt` 不需要大改，因为它只是链接预编译的 `libMNN.so`。关键是**编译 MNN 时**要用正确选项。但可以增加 OpenCL 库的链接：

```cmake
# 如果 MNN 编译时启用了 OpenCL，需要链接 OpenCL
find_library(OPENCL_LIB OpenCL)
if(OPENCL_LIB)
    target_link_libraries(aura_mnn_llm ${OPENCL_LIB})
endif()
```

---

## 3. P0 — 运行时 config 参数补全

### 3.1 问题

当前 `set_config` 缺少对推理性能和输出质量至关重要的参数：

```cpp
// 当前 — 缺少的关键参数
// "thread_num": 4,          ← 线程数未设置，MNN 用默认值
// "precision": "low",       ← 精度模式未设置
// "memory": "low",          ← 内存模式未设置
// "backend_type": "cpu",    ← 后端未显式选择
// "sampler_type": "mixed",  ← 采样器类型未设置
// "repetition_penalty": 1.05, ← 重复惩罚未设置
```

### 3.2 修复方案

更新 JNI 中的 `set_config` 调用：

```cpp
llm->set_config(
    "{\"tmp_path\":\"" + tmpDir +
    "\",\"prefix_cache_path\":\"" + prefixCacheDir +
    "\",\"use_mmap\":true,\"kvcache_mmap\":true,"
    "\"reuse_kv\":true,\"prompt_cache\":true,"
    // ↓ 新增参数
    "\"thread_num\":4,"
    "\"precision\":\"low\","
    "\"memory\":\"low\","
    "\"backend_type\":\"cpu\","
    "\"sampler_type\":\"mixed\","
    "\"temperature\":0.7,"
    "\"top_k\":40,"
    "\"top_p\":0.9,"
    "\"min_p\":0.05,"
    "\"repetition_penalty\":1.05,"
    // ↑ 新增参数结束
    "\"max_new_tokens\":" + std::to_string(effectiveMaxNewTokens) + ","
    "\"jinja\":{\"context\":{\"enable_thinking\":false}}}");
```

### 3.3 按场景动态调参

不同场景需要不同的采样参数，可以通过 JNI 增加 `updateConfig` 方法：

| 场景 | temperature | top_k | max_new_tokens | 说明 |
|---|---|---|---|---|
| **L1 心跳（Inner Monologue）** | 0.9 | 20 | 30 | 高温度 → 更多样的内心独白；极少 token → 省电 |
| **L2 Dream 摘要** | 0.3 | 40 | 500 | 低温度 → 准确的摘要；多 token → 完整内容 |
| **L2 Dream 分类** | 0.2 | 20 | 100 | 极低温度 → 结构化输出 |
| **L3 即时闲聊** | 0.8 | 40 | 200 | 标准对话参数 |
| **对话体（云端）** | — | — | — | 不走本地 |

**Kotlin 层**增加 `LocalQwenExecutor` 的场景化调用：

```kotlin
// core/presence/runtime/LocalQwenExecutor.kt
suspend fun executeForHeartbeat(): LocalLlmResult = execute(
    prompt = heartbeatPrompt,
    temperature = 0.9f,
    maxTokens = 30,
    topK = 20,
)

suspend fun executeForDreamSummary(messages: List<Message>): LocalLlmResult = execute(
    prompt = dreamSummaryPrompt(messages),
    temperature = 0.3f,
    maxTokens = 500,
    topK = 40,
)
```

---

## 4. P1 — OpenCL GPU 后端

### 4.1 价值

MNN 官方性能数据（Qwen-7B，Android）：

| 对比 | Prefill 加速比 | Decode 加速比 |
|---|---|---|
| CPU vs llama.cpp | 8.6x | 2.3x |
| **GPU** vs llama.cpp | **25.3x** | **7.1x** |

GPU 后端对 **prefill 阶段**（处理 system prompt + 历史消息）的收益尤其大。

### 4.2 实现方式

**前提**：MNN 编译时启用 `MNN_OPENCL=true`。

**运行时切换后端**：

```cpp
// 通过 set_config 切换后端
llm->set_config("{\"backend_type\":\"opencl\"}");  // GPU
llm->set_config("{\"backend_type\":\"cpu\"}");     // CPU
```

**自适应策略**：

```kotlin
// Kotlin 层 — 根据设备能力和电量选择后端
fun selectBackend(): MnnBackend {
    val batteryLevel = BatteryHelper.getLevel(context)
    val hasOpenCL = checkOpenCLAvailable()  // JNI 检测

    return when {
        !hasOpenCL -> MnnBackend.CPU
        batteryLevel < 0.2f -> MnnBackend.CPU     // 低电量用 CPU
        else -> MnnBackend.OPENCL                   // 默认 GPU
    }
}
```

### 4.3 注意事项

- OpenCL 需要设备 GPU 驱动支持，部分低端机可能不可用
- GPU 模式下功耗更高，需要电量感知
- GPU 对 decode 阶段（batch=1 GEMV）收益不如 prefill（batch>1 GEMM）
- **推荐**：prefill 走 GPU，decode 走 CPU（如果 MNN 支持混合后端）

---

## 5. P1 — 模型量化优化

### 5.1 当前状态

Aura 从 ModelScope 下载 `MNN/Qwen3.5-0.8B-MNN` 预量化模型（Q4），但**没有选择量化方案**。

### 5.2 AWQ 量化

AWQ（Activation-Aware Quantization）在**同等模型大小**下提供更好的精度：

```bash
# 使用 AWQ 量化导出
python llmexport.py \
    --path /path/to/Qwen3.5-0.8B \
    --export mnn \
    --quant_bit 4 \
    --quant_block 64 \
    --awq \
    --dst_path /output/qwen3.5-0.8b-q4-awq
```

**预期收益**：模型大小不变，中文生成质量提升（特别是情感细腻表达）。

### 5.3 混合精度（LM Head 单独 Q8）

```bash
python llmexport.py \
    --path /path/to/Qwen3.5-0.8B \
    --export mnn \
    --quant_bit 4 \
    --lm_quant_bit 8 \
    --dst_path /output/qwen3.5-0.8b-q4-lm-q8
```

**预期收益**：LM Head 层用 Q8 精度更高，最终 token 生成质量更好，大小只增加 ~10%。

### 5.4 对比赛的价值

在视频演示中展示**不同量化方案的性能/质量对比**，是很好的技术深度加分项。

---

## 6. P2 — Android PerformanceHint API

### 6.1 价值

Android 12+ (API 31+) 提供 `PerformanceHintManager`，可以让系统调度器知道 LLM 推理线程的时间预算，从而获得更好的 CPU 调度（优先使用大核）。

### 6.2 实现方式

```kotlin
// platform/performance/PerformanceHintHelper.kt

@RequiresApi(Build.VERSION_CODES.S)
class PerformanceHintHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var session: PerformanceHintManager.Session? = null

    fun startInferenceHint(threadIds: IntArray) {
        val manager = context.getSystemService(PerformanceHintManager::class.java) ?: return
        session = manager.createHintSession(
            threadIds,
            33_000_000L  // 33ms 目标（~30 token/s）
        )
    }

    fun updateProgress(actualDurationNs: Long) {
        session?.reportActualWorkDuration(actualDurationNs)
    }

    fun stopHint() {
        session?.close()
        session = null
    }
}
```

**JNI 侧**获取当前线程 ID：

```cpp
#include <unistd.h>
int tid = gettid();  // 传给 Kotlin 层
```

### 6.3 预期收益

- 系统调度器优先把 LLM 推理线程放到大核
- 减少调度延迟抖动
- 不需要 root 权限

---

## 7. P3 — 投机解码（Speculative Decoding）

### 7.1 价值

投机解码是目前 LLM 推理加速的**前沿技术**，通过小模型（draft model）预生成多个 token，再由大模型并行验证，实现 **2-3x decode 加速**。

MNN 支持三种方案：
- **EAGLE**：需要额外的 EAGLE draft 模型
- **DFlash**：需要额外的 DFlash draft 模型
- **MTP**：模型内置 Multi-Token Prediction 头（如果 Qwen3.5 支持）

### 7.2 实现复杂度

高。需要：
1. 导出额外的 draft 模型（~100MB）
2. 修改 `set_config` 启用投机解码
3. 管理两个模型的内存

### 7.3 建议

**初赛阶段不做**（时间不够），**决赛阶段可以作为技术亮点**。在答辩 PPT 中作为"后续优化路线"展示也是加分项。

---

## 8. P3 — CPU 大核绑定

### 8.1 价值

把 LLM 推理线程绑定到 CPU 大核（Cortex-X / Cortex-A720），避免被调度到小核导致性能下降。

### 8.2 实现方式

```cpp
// JNI 侧 — 绑定当前线程到大核
#include <sched.h>

void bindToBigCores() {
    cpu_set_t cpuset;
    CPU_ZERO(&cpuset);
    // 大核通常是最后几个核心
    // 需要根据设备 CPU 拓扑动态检测
    int numCores = sysconf(_SC_NPROCESSORS_ONLN);
    for (int i = numCores / 2; i < numCores; i++) {
        CPU_SET(i, &cpuset);
    }
    sched_setaffinity(0, sizeof(cpuset), &cpuset);
}
```

### 8.3 注意事项

- 不需要 root（`sched_setaffinity` 对自己线程有效）
- 需要动态检测 CPU 拓扑（不同设备大核位置不同）
- 与 PerformanceHint API 配合效果更好

---

## 9. 运行时性能监控框架

### 9.1 建议新增的指标

当前已有 `prompt_tokens` / `completion_tokens` / `prefill_us` / `decode_us` / `load_us`，建议补充：

```kotlin
data class MnnPerformanceStats(
    val promptTokens: Long,
    val completionTokens: Long,
    val prefillUs: Long,
    val decodeUs: Long,
    val loadUs: Long,
    // ↓ 新增
    val prefillTokensPerSecond: Double,    // prefill 吞吐
    val decodeTokensPerSecond: Double,     // decode 吞吐
    val peakMemoryMb: Long,                // 峰值内存
    val backendType: String,               // cpu / opencl
    val threadCount: Int,                  // 实际线程数
    val deviceModel: String,               // 设备型号
    val hasSme2: Boolean,                  // 是否 SME2
)
```

### 9.2 价值

- 初赛视频中可以展示这些指标 → 证明技术可行性
- 决赛答辩中可以做**不同设备性能对比** → 展示工程深度
- 为后续自适应策略提供数据基础

---

## 10. 实施路线图（7 天初赛冲刺版）

| 天 | 任务 | 产出 |
|---|---|---|
| D1 | 用完整编译选项重编 MNN + 替换 `libMNN.so` | 编译通过，APK 可安装 |
| D2 | JNI `set_config` 补全参数 + 性能指标补充 | 推理速度提升可测量 |
| D3 | 真机性能基准测试（0.8B Q4，记录 t/s） | 数据可用于视频和文档 |
| D4 | 按场景动态参数（心跳/Dream/对话） | Kotlin 层改动完成 |
| D5 | 录视频（模型加载 → 推理 → 交互） | 初赛视频素材 |
| D6 | 视频剪辑 + 小红书笔记 | 初赛提交物 |
| D7 | 提交表单填写 + 辅助材料整理 | 初赛提交完成 |

---

## 11. 对比赛的价值总结

| 优化项 | 对评分的贡献 |
|---|---|
| 编译选项补全 → decode +20-40% | **技术可行性 25%**：实测数据更好看 |
| 动态采样参数 → 心跳省电 50% | **创新性 40%**：展示场景化推理优化 |
| OpenCL GPU → prefill +2-5x | **技术可行性 25%**：多后端自适应是工程深度 |
| 性能监控框架 → 完整 benchmark | **方案完整性 10%**：可量化的性能对比 |
| SME2 + KleidiAI（编译启用） | **技术可行性 25%**：比赛推荐方向，必须体现 |

---

## 12. 测试设备硬件能力档案

### 12.1 当前设备：Realme RMX3562 (Dimensity 8200)

| 属性 | 值 |
|---|---|
| SoC | MediaTek Dimensity 8200 (MT6895) |
| CPU | 4× Cortex-A78 (大核) + 4× Cortex-A55 (小核) |
| GPU | Mali-G610 MC6 |
| RAM | ~12 GB |
| Android | 14 / SDK 34 |
| CPU Features | `fphp` `asimdhp` (ARM82 fp16) ✅ / `asimddp` (dot product) ✅ |
| SME2 | **不支持** (需 Cortex-X3+) |
| SVE | **不支持** |
| OpenCL | `/vendor/lib64/libOpenCL.so` ✅ |
| Vulkan | ✅ (version 4198400) |

**适配策略**：
- 编译启用 `MNN_ARM82` + `MNN_OPENCL` + `MNN_LOW_MEMORY` + `MNN_SUPPORT_TRANSFORMER_FUSE`
- **不启用** `MNN_SME2` / `MNN_KLEIDIAI`（设备不支持，MNN 会自动 fallback）
- 运行时 `thread_num=4`（匹配 4 个大核）
- 可选 OpenCL GPU 后端用于 prefill

### 12.2 决赛旗舰设备（待定）

| 属性 | 预期值 |
|---|---|
| SoC | Dimensity 9300/9400 或 Snapdragon 8 Gen 3/4 |
| CPU | Cortex-X3/X4 + A720/A725 全大核 |
| SME2 | **支持** → 编译时开启，运行时自动启用 |
| OpenCL | **支持** |
| RAM | 12-16 GB |

**自适应设计**：编译时**全特性开启**（SME2 + ARM82 + OpenCL），MNN 运行时按设备能力自动 fallback。
`MnnInferenceConfig.forDimensity8200()` / `.forFlagshipSoc()` 预设已在 Kotlin 层实现。

---

## 13. 实测性能基准（待填充）

> **状态：占位，待真机实测后填入。**
>
> 测量方法：使用 MNN 返回的 `prefill_us` / `decode_us` / `prompt_tokens` / `completion_tokens` 计算。
> 在 `mnn_bridge_generate_completed` 日志中直接读取。

### 13.1 Qwen3.5-0.8B Q4 — Dimensity 8200

| 指标 | 值 | 测量条件 |
|---|---|---|
| 模型加载时间 (load_us) | `TODO: 实测` | 冷启动，mmap 加载 |
| Prefill 吞吐 (tokens/s) | `TODO: 实测` | system prompt ~500 tokens |
| Decode 速度 (tokens/s) | `TODO: 实测` | 生成 100 tokens，thread_num=4 |
| 峰值内存 | `TODO: 实测` | dumpsys meminfo |
| 平均 CPU 占用 | `TODO: 实测` | dumpsys cpuinfo |
| 平均功耗 | `TODO: 实测` | BatteryManager / dumpsys batterystats |
| 端到端延迟 (E2E) | `TODO: 实测` | 用户发消息到首 token 出现 |

### 13.2 Qwen3.5-0.8B Q4 — 优化后（ARM82 + thread_num=4 + sampler）

| 指标 | 基准值 | 优化后 | 提升 |
|---|---|---|---|
| Decode 速度 (tokens/s) | `TODO` | `TODO` | `TODO` |
| Prefill 吞吐 (tokens/s) | `TODO` | `TODO` | `TODO` |
| 峰值内存 | `TODO` | `TODO` | `TODO` |

### 13.3 Qwen3.5-2B Q4 — Dimensity 8200（可选，如果内存允许）

| 指标 | 值 | 测量条件 |
|---|---|---|
| 模型加载时间 | `TODO: 实测` | |
| Decode 速度 | `TODO: 实测` | |
| 峰值内存 | `TODO: 实测` | 12GB RAM 是否 OOM |

### 13.4 场景化推理参数实测

| 场景 | temperature | top_k | max_tokens | 实测 decode t/s | 实测质量 |
|---|---|---|---|---|---|
| L1 心跳 (Inner Monologue) | 0.9 | 20 | 30 | `TODO` | `TODO` |
| L2 Dream 摘要 | 0.3 | 40 | 500 | `TODO` | `TODO` |
| L3 即时闲聊 | 0.8 | 40 | 200 | `TODO` | `TODO` |

### 13.5 测量脚本

```powershell
# 1. 清除日志缓存
& 'D:\tools\ADB_Cli\adb.exe' logcat -c

# 2. 启动 App，发送一条消息，等待回复完成

# 3. 拉取性能日志
& 'D:\tools\ADB_Cli\adb.exe' logcat -d -v time | Select-String -Pattern 'mnn_prefill_completed|mnn_decode_progress|mnn_submit_completed|mnn_bridge_generate_completed'

# 4. 关键字段：
#    - prefill_us: prefill 阶段总时间（微秒）
#    - decode_us: decode 阶段总时间（微秒）
#    - prompt_tokens: 输入 token 数
#    - completion_tokens: 输出 token 数
#    - load_us: 模型加载时间（微秒）
#
# 计算公式：
#   Prefill t/s = prompt_tokens / (prefill_us / 1_000_000)
#   Decode  t/s = completion_tokens / (decode_us / 1_000_000)
#   E2E latency = (prefill_us + decode_us) / 1_000_000 ms
```

---

**Status: 代码改造完成，待实测数据填充（2026-06-15）**
