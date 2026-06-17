# Benchmark

## Goal

This project's local Qwen benchmark is used to verify three things:

1. `assembleDebug -> install -> am start` app benchmark flow is healthy
2. the main app process can load the real on-device MNN model
3. we can stably collect `load / prefill / decode` timings and tokens/s

The benchmark runs in the **main app process**, not instrumentation, so results are closer to the real product path.

## Entry

Default entry:

```bash
make benchmark-mnn
```

Equivalent command:

```bash
python scripts/mnn_benchmark.py --mode app
```

Default config file:

```text
scripts/mnn_benchmark.yml
```

Commonly changed fields:

- `model_name`
- `prompt_len`
- `decode_len`
- `warmup_runs`
- `measure_runs`
- `threads`
- `backend`
- `precision`
- `memory`
- `timeout_s`

## Standard Flow

`--mode app` runs in this order:

1. `./gradlew.bat assembleDebug`
2. `D:\tools\ADB_Cli\adb.exe install -r app/build/outputs/apk/debug/app-debug.apk`
3. `adb shell am start` triggers `MainActivity` benchmark action
4. `LocalQwenBenchmarkRunner` runs inside the main app process
5. result JSON is pulled back from `files/benchmarks/`

If the APK is already fresh:

```bash
python scripts/mnn_benchmark.py --mode app --skip-build-install
```

## Output Files

Device-side:

```text
files/benchmarks/local-qwen-benchmark.json
files/benchmarks/local-qwen-benchmark.error.txt
```

Pulled to local machine:

```text
docs/plan/visual-audit-assets/local-qwen-benchmark-app-*.json
```

The local filename now includes:

- mode
- model name
- backend
- thread count
- prompt/decode length
- timestamp

This avoids result overwrite between runs.

## Key Logs

Useful log events:

- `local_qwen_benchmark_triggered`
- `local_qwen_benchmark_request_parsed`
- `local_qwen_benchmark_load_started`
- `local_qwen_benchmark_load_completed`
- `local_qwen_benchmark_warmup_started`
- `local_qwen_benchmark_measure_started`
- `mnn_runtime_config_merged`
- `mnn_prefill_completed`
- `mnn_decode_progress`
- `local_qwen_benchmark_completed`
- `local_qwen_benchmark_failed`

## Device

- Device: `RMX3562`
- SoC: `Dimensity 8200` series
- App package: `com.xiaoqi.companion.debug`

## Current Conclusions

- `Qwen3.5-0.8B-MNN` is the current **fast path** and fits primary chat better.
- `Qwen3.5-4B-MNN` should stay on **CPU + threadNum=4** for now.
- `Qwen3.5-0.8B-MNN` benefits from `backendType=opencl` on this device.
- `Qwen3.5-4B-MNN` does **not** currently benefit from `backendType=opencl`.

## Current Results

### 4B / CPU / smoke

Config:

- `model_name`: `Qwen3.5-4B-MNN`
- `prompt_len`: `32`
- `decode_len`: `8`
- `warmup_runs`: `1`
- `measure_runs`: `1`
- `threadNum`: `4`
- `backendType`: `cpu`
- `precision`: `low`

Measured:

- `prefillUs`: `3,937,784`
- `decodeUs`: `2,250,212`
- `loadUs`: `388,967`
- `prefillTokensPerSecond`: `22.348`
- `decodeTokensPerSecond`: `3.555`

### 4B / CPU / thread sweep

`prompt_len=32`, `decode_len=8`, `warmup=1`, `measure=1`

| threadNum | backend | prefillUs | decodeUs | prefillTPS | decodeTPS |
|---|---|---:|---:|---:|---:|
| 4 | cpu | 3,937,784 | 2,250,212 | 22.348 | 3.555 |
| 6 | cpu | 4,312,324 | 3,701,745 | 20.407 | 2.161 |
| 8 | cpu | 4,167,061 | 3,037,098 | 21.118 | 2.634 |

Interpretation:

- `threadNum=4` is best on this device for 4B CPU
- increasing threads hurts decode noticeably

### 4B / OpenCL / smoke

Config:

- `model_name`: `Qwen3.5-4B-MNN`
- `prompt_len`: `32`
- `decode_len`: `8`
- `warmup_runs`: `1`
- `measure_runs`: `1`
- `threadNum`: `4`
- `backendType`: `opencl`
- `precision`: `low`

Measured:

- `prefillUs`: `15,501,447`
- `decodeUs`: `3,045,127`
- `loadUs`: `435,409`
- `prefillTokensPerSecond`: `5.677`
- `decodeTokensPerSecond`: `2.627`

Interpretation:

- OpenCL is functional in app benchmark
- but it is much worse than CPU for 4B on this device

Source:

- [4B OpenCL smoke](</d:/C/Desktop/ai/android/docs/plan/visual-audit-assets/local-qwen-benchmark-app-Qwen3.5-4B-MNN-opencl-t4-p32-d8-20260617-125339.json>)

### 4B / CPU / report

Config:

- `model_name`: `Qwen3.5-4B-MNN`
- `prompt_len`: `256`
- `decode_len`: `64`
- `warmup_runs`: `1`
- `measure_runs`: `3`
- `threadNum`: `4`
- `backendType`: `cpu`

Measured average:

- `promptTokens`: `439`
- `completionTokens`: `50`
- `prefillUs`: `16,801,127`
- `decodeUs`: `13,941,270`
- `loadUs`: `360,470`
- `prefillTokensPerSecond`: `26.129`
- `decodeTokensPerSecond`: `3.586`

Interpretation:

- 4B can run end to end in the real app path
- decode is still too slow for primary chat UX

### 0.8B / CPU / smoke

Config:

- `model_name`: `Qwen3.5-0.8B-MNN`
- `prompt_len`: `32`
- `decode_len`: `8`
- `warmup_runs`: `1`
- `measure_runs`: `1`
- `threadNum`: `4`
- `backendType`: `cpu`
- `precision`: `low`

Measured:

- `prefillUs`: `575,836`
- `decodeUs`: `223,883`
- `loadUs`: `324,848`
- `prefillTokensPerSecond`: `152.821`
- `decodeTokensPerSecond`: `35.733`

Source:

- [0.8B CPU smoke](</d:/C/Desktop/ai/android/docs/plan/visual-audit-assets/local-qwen-benchmark-app-Qwen3.5-0.8B-MNN-cpu-t4-p32-d8-20260617-124740.json>)

### 0.8B / OpenCL / smoke

Config:

- `model_name`: `Qwen3.5-0.8B-MNN`
- `prompt_len`: `32`
- `decode_len`: `8`
- `warmup_runs`: `1`
- `measure_runs`: `1`
- `threadNum`: `4`
- `backendType`: `opencl`
- `precision`: `low`

Measured:

- `prefillUs`: `2,362,492`
- `decodeUs`: `965,349`
- `loadUs`: `355,123`
- `prefillTokensPerSecond`: `37.249`
- `decodeTokensPerSecond`: `8.287`

Source:

- [0.8B OpenCL smoke](</d:/C/Desktop/ai/android/docs/plan/visual-audit-assets/local-qwen-benchmark-app-Qwen3.5-0.8B-MNN-opencl-t4-p32-d8-20260617-125128.json>)

## How To Use This In Aura

Recommended product split right now:

1. `Qwen3.5-0.8B-MNN`
   - primary chat
   - quick reaction
   - high-frequency interaction

2. `Qwen3.5-4B-MNN`
   - low-frequency heavy tasks
   - summary
   - insight extraction
   - dream / background generation

3. `backendType`
   - `0.8B`: keep testing CPU vs OpenCL, but do not assume GPU wins without clean repeated runs
   - `4B`: keep `cpu`

## Recommended Next Steps

1. Add repeated runs for `0.8B cpu/opencl` to confirm whether OpenCL wins consistently or only in some runs.
2. Add a business-shaped benchmark pair:
   - `chat_short`
   - `insight_long`
3. Keep `4B` on `cpu + threadNum=4` unless a future backend path proves better.
