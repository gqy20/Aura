# Benchmark

## 目标

本项目的本地 Qwen benchmark 统一验证三件事：

1. APK 构建与安装链路可用
2. 主 App 进程能正确加载 MNN 本地模型
3. 能稳定产出 `load / prefill / decode` 三段时延与 tokens/s

当前 benchmark 不走 instrumentation，而是走主 App 进程，避免测试沙箱与真实用户进程的模型目录差异。

## 入口

默认入口：

```bash
make benchmark-mnn
```

等价命令：

```bash
python scripts/mnn_benchmark.py --mode app
```

默认配置文件：

```text
scripts/mnn_benchmark.yml
```

常改字段：

- `apk_path`
- `app_package`
- `app_activity`
- `model_name`
- `prompt_len`
- `decode_len`
- `warmup_runs`
- `measure_runs`
- `timeout_s`

## 标准流程

`--mode app` 的执行顺序固定为：

1. `./gradlew.bat assembleDebug`
2. `D:\tools\ADB_Cli\adb.exe install -r app/build/outputs/apk/debug/app-debug.apk`
3. `adb shell am start` 触发 `MainActivity` 的 benchmark action
4. 主 App 进程执行 `LocalQwenBenchmarkRunner`
5. 用 `run-as com.xiaoqi.companion.debug` 拉取 `files/benchmarks/local-qwen-benchmark.json`

如果 APK 已经是最新的，可跳过前两步：

```bash
python scripts/mnn_benchmark.py --mode app --skip-build-install
```

## 结果文件

主结果：

```text
files/benchmarks/local-qwen-benchmark.json
```

拉回本地后默认保存到：

```text
docs/plan/visual-audit-assets/local-qwen-benchmark.json
```

失败时：

```text
files/benchmarks/local-qwen-benchmark.error.txt
```

## 日志信号

关键日志：

- `local_qwen_benchmark_triggered`
- `local_model_lookup_found`
- `mnn_bridge_load_started`
- `mnn_bridge_load_completed`
- `mnn_bridge_generate_completed`
- `local_qwen_benchmark_written`
- `local_qwen_benchmark_completed`
- `local_qwen_benchmark_failed`

如果 `am start` 命中了当前前台 `MainActivity` 实例，benchmark 会走 `onNewIntent()`；如果是新实例启动，则走 `onCreate()`。

## 当前实测

### 设备

- Device: `RMX3562`
- SoC: `Dimensity 8200` 系列
- App package: `com.xiaoqi.companion.debug`

### Qwen3.5-4B-MNN

测试配置：

- `model_name`: `Qwen3.5-4B-MNN`
- `prompt_len`: `32`
- `decode_len`: `8`
- `warmup_runs`: `1`
- `measure_runs`: `1`
- `threadNum`: `4`
- `backendType`: `cpu`
- `precision`: `low`

实测结果：

- `loadUs`: `10,918,355` us, 约 `10.9 s`
- `prefillUs`: `4,889,600` us
- `decodeUs`: `10,025,458` us
- `prefillTokensPerSecond`: `17.997`
- `decodeTokensPerSecond`: `5.187`

结果来源：

- [local-qwen-benchmark.json](</d:/C/Desktop/ai/android/docs/plan/visual-audit-assets/local-qwen-benchmark.json>)

### 0.8B 旧基线（llm_bench）

这是早期外部 `llm_bench` 路径的 CPU 结果，可作为量级参考，不等同于当前主 App 进程 benchmark：

- `pp256`: `190.71 tok/s`
- `tg64`: `19.19 tok/s`
- `loadingTime`: `1.59 - 1.88 s`

来源：

- `docs/plan/visual-audit-assets/llm_bench.stdout.txt`

## 解读

1. 当前 4B benchmark 已经打通到“主 App 进程真实加载并返回 JSON”。
2. `load` 时间明显高于 0.8B，这是符合预期的。
3. 当前 `prompt_len=32` / `decode_len=8` 更适合做链路验证与参数回归，不适合直接拿去做比赛级最终性能口径。
4. 若要做正式横向对比，建议至少固定：
   - 同一设备
   - 同一线程数
   - 同一 backend
   - 同一 prompt/decode 参数
   - warmup 与 measured runs 分开记录

## 建议下一步

建议补两组标准档位：

1. `smoke`
   - `prompt_len=32`
   - `decode_len=8`
   - 用于验证链路是否通

2. `report`
   - `prompt_len=256`
   - `decode_len=64`
   - `warmup_runs=1`
   - `measure_runs=3`
   - 用于后续文档、比赛材料和回归比较
