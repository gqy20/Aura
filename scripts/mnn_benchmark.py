#!/usr/bin/env python3
"""Standardized benchmark runner for Aura.

Supports:
  - Aura main-process benchmark runs via adb + activity intent
  - MNN llm_bench on-device runs via adb
  - Aura log parsing from mnn_bridge_* logs

Usage:
  python scripts/mnn_benchmark.py --mode app
  python scripts/mnn_benchmark.py --mode mnn --model-dir D:/path/to/model
  python scripts/mnn_benchmark.py --mode aura
"""

from __future__ import annotations

import argparse
import json
import os
import platform
import re
import shlex
import statistics
import subprocess
import sys
import time
from pathlib import Path

import yaml


ADB_DEFAULT = r"D:\tools\ADB_Cli\adb.exe"
MNN_TMP_DIR = "/data/local/tmp/mnn_benchmark"
DEFAULT_CONFIG_PATH = Path("scripts/mnn_benchmark.yml")
APP_PACKAGE_DEFAULT = "com.xiaoqi.companion.debug"
APP_ACTIVITY_DEFAULT = "com.xiaoqi.companion.MainActivity"
BENCHMARK_ACTION = "com.xiaoqi.companion.action.RUN_LOCAL_QWEN_BENCHMARK"
DEBUG_APK_DEFAULT = Path("app/build/outputs/apk/debug/app-debug.apk")


def run(cmd: list[str], *, check: bool = True, capture: bool = True) -> subprocess.CompletedProcess:
    return subprocess.run(
        cmd,
        check=check,
        text=True,
        encoding="utf-8",
        errors="replace",
        stdout=subprocess.PIPE if capture else None,
        stderr=subprocess.PIPE if capture else None,
    )


def adb(adb_path: str, *args: str, capture: bool = True) -> subprocess.CompletedProcess:
    return run([adb_path, *args], capture=capture)


def adb_shell(adb_path: str, command: str, capture: bool = True) -> subprocess.CompletedProcess:
    return adb(adb_path, "shell", command, capture=capture)


def adb_push(adb_path: str, src: str, dst: str) -> None:
    run([adb_path, "push", src, dst], capture=False)


def build_debug_apk(args: argparse.Namespace, out_dir: Path) -> Path:
    gradlew = "gradlew.bat" if platform.system() == "Windows" else "./gradlew"
    proc = run([gradlew, "assembleDebug"], check=False, capture=True)
    (out_dir / "assemble-debug.stdout.txt").write_text(proc.stdout or "", encoding="utf-8")
    (out_dir / "assemble-debug.stderr.txt").write_text(proc.stderr or "", encoding="utf-8")
    if proc.returncode != 0:
        raise RuntimeError(
            "assembleDebug failed. "
            f"See {out_dir / 'assemble-debug.stdout.txt'} and {out_dir / 'assemble-debug.stderr.txt'}"
        )
    apk_path = Path(args.apk_path).expanduser().resolve()
    if not apk_path.is_file():
        raise FileNotFoundError(apk_path)
    return apk_path


def install_debug_apk(adb_path: str, apk_path: Path, out_dir: Path) -> None:
    proc = run([adb_path, "install", "-r", str(apk_path)], check=False, capture=True)
    (out_dir / "install-debug.stdout.txt").write_text(proc.stdout or "", encoding="utf-8")
    (out_dir / "install-debug.stderr.txt").write_text(proc.stderr or "", encoding="utf-8")
    if proc.returncode != 0:
        raise RuntimeError(
            "adb install failed. "
            f"See {out_dir / 'install-debug.stdout.txt'} and {out_dir / 'install-debug.stderr.txt'}"
        )


def adb_exec_out(adb_path: str, command: str, output_path: Path) -> None:
    output_path.parent.mkdir(parents=True, exist_ok=True)
    with output_path.open("wb") as f:
        proc = subprocess.Popen(
            [adb_path, "exec-out", "sh", "-c", command],
            stdout=f,
            stderr=subprocess.PIPE,
        )
        _, stderr = proc.communicate()
        if proc.returncode != 0:
            raise RuntimeError((stderr or b"").decode("utf-8", "replace"))


def pull_aura_model_dir(adb_path: str, model_name: str, local_root: Path) -> Path:
    local_dir = local_root / model_name
    local_dir.mkdir(parents=True, exist_ok=True)
    listing = adb_shell(
        adb_path,
        f"run-as com.xiaoqi.companion.debug sh -c 'find ./files/models/{model_name} -maxdepth 1 -type f 2>/dev/null'",
    ).stdout.splitlines()
    if not listing:
        raise RuntimeError(f"Model files not found in app sandbox: {model_name}")
    for remote in listing:
        name = Path(remote).name
        adb_exec_out(
            adb_path,
            f"run-as com.xiaoqi.companion.debug cat {shlex.quote(remote)}",
            local_dir / name,
        )
    return local_dir


def ensure_device(adb_path: str) -> str:
    out = adb(adb_path, "devices").stdout.splitlines()
    devices = [line.split("\t")[0] for line in out[1:] if "\tdevice" in line]
    if not devices:
        raise RuntimeError("No adb device connected")
    return devices[0]


def device_props(adb_path: str) -> dict[str, str]:
    out = adb_shell(adb_path, "getprop").stdout.splitlines()
    props: dict[str, str] = {}
    for line in out:
        m = re.match(r"\[(.+?)\]: \[(.*)\]", line.strip())
        if m:
            props[m.group(1)] = m.group(2)
    return props


def parse_mnn_json(path: Path) -> dict | None:
    if not path.exists():
        return None
    data = json.loads(path.read_text(encoding="utf-8"))
    results = data.get("results", [])
    out = {"model": data.get("model"), "threads": data.get("threads"), "results": []}
    for item in results:
        row = dict(item)
        if row.get("type") == "prefill":
            prompt_len = row.get("prompt_len") or 0
            tps = row.get("tps")
            row["prefill_us"] = None if not tps else 1e6 * prompt_len / tps
        elif row.get("type") == "decode":
            gen_len = row.get("generate_len") or 0
            tps = row.get("tps")
            row["decode_us"] = None if not tps else 1e6 * gen_len / tps
        out["results"].append(row)
    return out


def parse_aura_log(text: str) -> dict:
    metrics = []
    for line in text.splitlines():
        if "mnn_bridge_generate_completed" in line or "mnn_bridge_generate_with_image_completed" in line:
            item = {
                "line": line,
                "prompt_tokens": _extract_int(line, "prompt_tokens"),
                "completion_tokens": _extract_int(line, "completion_tokens"),
                "prefill_us": _extract_int(line, "prefill_us"),
                "decode_us": _extract_int(line, "decode_us"),
                "load_us": _extract_int(line, "load_us"),
                "vision_us": _extract_int(line, "vision_us"),
            }
            metrics.append(item)
    return {"runs": metrics}


def _extract_int(line: str, key: str) -> int | None:
    m = re.search(rf"{re.escape(key)}=(\d+)", line)
    return int(m.group(1)) if m else None


def summarize_runs(runs: list[dict]) -> dict:
    def mean(values: list[float | int | None]) -> float | None:
        vals = [v for v in values if v is not None]
        return float(statistics.mean(vals)) if vals else None

    prompt = [r.get("prompt_tokens") for r in runs]
    comp = [r.get("completion_tokens") for r in runs]
    prefill = [r.get("prefill_us") for r in runs]
    decode = [r.get("decode_us") for r in runs]
    load = [r.get("load_us") for r in runs]
    vision = [r.get("vision_us") for r in runs]

    return {
        "runs": len(runs),
        "prompt_tokens_avg": mean(prompt),
        "completion_tokens_avg": mean(comp),
        "prefill_us_avg": mean(prefill),
        "decode_us_avg": mean(decode),
        "load_us_avg": mean(load),
        "vision_us_avg": mean(vision),
        "prefill_tps_avg": mean([1e6 * p / u if p and u else None for p, u in zip(prompt, prefill)]),
        "decode_tps_avg": mean([1e6 * c / u if c and u else None for c, u in zip(comp, decode)]),
    }


def load_yaml_config(path: Path) -> dict:
    if not path.exists():
        raise FileNotFoundError(path)
    data = yaml.safe_load(path.read_text(encoding="utf-8")) or {}
    if not isinstance(data, dict):
        raise ValueError(f"Benchmark config must be a mapping: {path}")
    return data


def apply_yaml_defaults(args: argparse.Namespace) -> argparse.Namespace:
    config_path = Path(args.config).expanduser().resolve()
    args.config = str(config_path)
    config = load_yaml_config(config_path)
    cli_values = {
        key.split("=", 1)[0]
        for key in sys.argv[1:]
        if key.startswith("--")
    }

    general = config.get("general", {})
    if not isinstance(general, dict):
        raise ValueError("general section in benchmark config must be a mapping")

    mode_configs = config.get("modes", {})
    if not isinstance(mode_configs, dict):
        raise ValueError("modes section in benchmark config must be a mapping")
    mode_config = mode_configs.get(args.mode, {})
    if mode_config is None:
        mode_config = {}
    if not isinstance(mode_config, dict):
        raise ValueError(f"modes.{args.mode} in benchmark config must be a mapping")

    merged = {**general, **mode_config}
    for key, value in merged.items():
        flag = f"--{key.replace('_', '-')}"
        if flag not in cli_values and hasattr(args, key):
            setattr(args, key, value)
    return args


def run_mnn(args: argparse.Namespace) -> dict:
    adb_path = args.adb
    ensure_device(adb_path)
    props = device_props(adb_path)

    bench = Path(args.llm_bench).expanduser().resolve()
    mnn_build_dir = Path(args.mnn_build_dir).expanduser().resolve()
    model_dir = Path(args.model_dir).expanduser().resolve()
    if not bench.exists():
        raise FileNotFoundError(bench)
    if not mnn_build_dir.exists():
        raise FileNotFoundError(mnn_build_dir)
    if not model_dir.exists():
        model_dir = pull_aura_model_dir(adb_path, args.model_name, model_dir.parent)

    remote_bench = f"{MNN_TMP_DIR}/llm_bench"
    remote_model = f"{MNN_TMP_DIR}/model"

    adb_shell(adb_path, f"mkdir -p {shlex.quote(MNN_TMP_DIR)}")
    adb_push(adb_path, str(bench), remote_bench)
    adb_shell(adb_path, f"chmod 755 {shlex.quote(remote_bench)}")
    for so_name in ["libMNN.so", "libllm.so", "libMNN_Express.so", "libMNN_CL.so"]:
        so_path = mnn_build_dir / so_name
        if so_path.exists():
            adb_push(adb_path, str(so_path), f"{MNN_TMP_DIR}/{so_name}")

    # Push only the files llm_bench expects from the model directory.
    adb_shell(adb_path, f"rm -rf {shlex.quote(remote_model)} && mkdir -p {shlex.quote(remote_model)}")
    for name in sorted(p.name for p in model_dir.iterdir() if p.is_file()):
        adb_push(adb_path, str(model_dir / name), f"{remote_model}/{name}")

    bench_args = [
        "./llm_bench",
        "-m", f"{remote_model}/config.json",
        "-p", str(args.prompt_len),
        "-n", str(args.decode_len),
        "-t", str(args.threads),
        "-c", str(args.precision),
        "--memory", str(args.memory),
        "--power", str(args.power),
        "-a", args.backend,
        "-kv", "false",
        "-load", "true",
        "-j", "llm_bench.json",
    ]
    if args.use_mmap:
        bench_args += ["-mmp", "true"]
    if args.repeat is not None:
        bench_args += ["-rep", str(args.repeat)]
    shell_cmd = (
        f"cd {shlex.quote(MNN_TMP_DIR)} "
        f"&& export LD_LIBRARY_PATH={shlex.quote(MNN_TMP_DIR)}:$LD_LIBRARY_PATH "
        f"&& {' '.join(shlex.quote(arg) for arg in bench_args)}"
    )

    t0 = time.perf_counter()
    proc = run([adb_path, "shell", shell_cmd], check=False, capture=True)
    wall = time.perf_counter() - t0

    out_dir = Path(args.out_dir).expanduser().resolve()
    out_dir.mkdir(parents=True, exist_ok=True)
    stdout_path = out_dir / "llm_bench.stdout.txt"
    stderr_path = out_dir / "llm_bench.stderr.txt"
    stdout_path.write_text(proc.stdout or "", encoding="utf-8")
    stderr_path.write_text(proc.stderr or "", encoding="utf-8")
    if proc.returncode != 0:
        raise RuntimeError(
            f"llm_bench failed with exit code {proc.returncode}. "
            f"See {stdout_path} and {stderr_path}"
        )

    json_path = out_dir / "llm_bench.json"
    adb_exec_out(adb_path, f"run-as com.xiaoqi.companion.debug cat {remote_model}/../llm_bench.json 2>/dev/null || cat {MNN_TMP_DIR}/llm_bench.json", json_path)
    parsed = parse_mnn_json(json_path)

    return {
        "mode": "mnn",
        "device": props.get("ro.product.model"),
        "soc": props.get("ro.soc.model") or props.get("ro.hardware") or props.get("ro.board.platform"),
        "wall_s": wall,
        "json": parsed,
        "stdout_file": str(stdout_path),
        "stderr_file": str(stderr_path),
        "json_file": str(json_path),
    }


def run_aura(args: argparse.Namespace) -> dict:
    adb_path = args.adb
    ensure_device(adb_path)
    props = device_props(adb_path)
    logcat = adb(adb_path, "logcat", "-d", "-v", "time", capture=True).stdout
    out = parse_aura_log(logcat)
    out.update({
        "mode": "aura",
        "device": props.get("ro.product.model"),
        "soc": props.get("ro.soc.model") or props.get("ro.hardware") or props.get("ro.board.platform"),
    })
    out_dir = Path(args.out_dir).expanduser().resolve()
    out_dir.mkdir(parents=True, exist_ok=True)
    (out_dir / "aura.logcat.txt").write_text(logcat, encoding="utf-8")
    (out_dir / "aura.summary.json").write_text(json.dumps(out, indent=2, ensure_ascii=False), encoding="utf-8")
    return out


def wait_for_remote_file(adb_path: str, package_name: str, remote_path: str, timeout_s: int) -> bool:
    deadline = time.time() + timeout_s
    while time.time() < deadline:
        proc = run(
            [
                adb_path,
                "shell",
                f"run-as {package_name} sh -c 'test -f {shlex.quote(remote_path)} && echo READY'",
            ],
            check=False,
            capture=True,
        )
        if "READY" in (proc.stdout or ""):
            return True
        time.sleep(1.0)
    return False


def run_app(args: argparse.Namespace) -> dict:
    out_dir = Path(args.out_dir).expanduser().resolve()
    out_dir.mkdir(parents=True, exist_ok=True)
    adb_path = args.adb
    ensure_device(adb_path)
    if not args.skip_build_install:
        apk_path = build_debug_apk(args, out_dir)
        install_debug_apk(adb_path, apk_path, out_dir)
    package_name = args.app_package
    activity_name = args.app_activity
    remote_json = "./files/benchmarks/local-qwen-benchmark.json"
    remote_error = "./files/benchmarks/local-qwen-benchmark.error.txt"
    adb_shell(
        adb_path,
        (
            f"run-as {package_name} sh -c "
            f"'rm -f {shlex.quote(remote_json)} {shlex.quote(remote_error)}'"
        ),
    )
    start_cmd = [
        adb_path,
        "shell",
        "am",
        "start",
        "-n",
        f"{package_name}/{activity_name}",
        "-a",
        BENCHMARK_ACTION,
        "--es",
        "modelName",
        args.model_name,
        "--ei",
        "promptTokens",
        str(args.prompt_len),
        "--ei",
        "decodeTokens",
        str(args.decode_len),
        "--ei",
        "warmupRuns",
        str(args.warmup_runs),
        "--ei",
        "measureRuns",
        str(args.measure_runs),
    ]
    proc = run(start_cmd, check=False, capture=True)
    (out_dir / "app-benchmark.stdout.txt").write_text(proc.stdout or "", encoding="utf-8")
    (out_dir / "app-benchmark.stderr.txt").write_text(proc.stderr or "", encoding="utf-8")
    if proc.returncode != 0:
        raise RuntimeError(
            "App benchmark launch failed. "
            f"See {out_dir / 'app-benchmark.stdout.txt'} and {out_dir / 'app-benchmark.stderr.txt'}"
        )

    json_ready = wait_for_remote_file(adb_path, package_name, remote_json, args.timeout_s)
    error_ready = wait_for_remote_file(adb_path, package_name, remote_error, 1) if not json_ready else False
    if not json_ready and not error_ready:
        raise RuntimeError(f"Timed out waiting for benchmark result after {args.timeout_s}s")

    local_json = out_dir / "local-qwen-benchmark.json"
    if error_ready:
        local_error = out_dir / "local-qwen-benchmark.error.txt"
        adb_exec_out(
            adb_path,
            f"run-as {package_name} cat {remote_error}",
            local_error,
        )
        raise RuntimeError(f"App benchmark failed. See {local_error}")
    adb_exec_out(
        adb_path,
        f"run-as {package_name} cat {remote_json}",
        local_json,
    )
    data = json.loads(local_json.read_text(encoding="utf-8"))
    return {
        "mode": "app",
        "json_file": str(local_json),
        "summary": data.get("averages"),
        "model": data.get("modelName"),
    }


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--config", default=str(DEFAULT_CONFIG_PATH))
    p.add_argument("--mode", choices=["app", "mnn", "aura"], required=True)
    p.add_argument("--adb", default=ADB_DEFAULT)
    p.add_argument("--out-dir", default="docs/plan/visual-audit-assets")
    p.add_argument("--apk-path", default=str(DEBUG_APK_DEFAULT))
    p.add_argument("--app-package", default=APP_PACKAGE_DEFAULT)
    p.add_argument("--app-activity", default=APP_ACTIVITY_DEFAULT)
    p.add_argument("--llm-bench", default=r"..\MNN\project\android\build_64\llm_bench")
    p.add_argument("--mnn-build-dir", default=r"..\MNN\project\android\build_64")
    p.add_argument("--model-dir", default="")
    p.add_argument("--backend", default="cpu", choices=["cpu", "opencl", "metal", "cuda"])
    p.add_argument("--threads", type=int, default=4)
    p.add_argument("--prompt-len", type=int, default=512)
    p.add_argument("--decode-len", type=int, default=128)
    p.add_argument("--warmup-runs", type=int, default=1)
    p.add_argument("--measure-runs", type=int, default=3)
    p.add_argument("--precision", type=int, default=2)
    p.add_argument("--memory", type=int, default=2)
    p.add_argument("--power", type=int, default=0)
    p.add_argument("--use-mmap", action="store_true")
    p.add_argument("--repeat", type=int, default=5)
    p.add_argument("--model-name", default="Qwen3.5-0.8B-MNN")
    p.add_argument("--timeout-s", type=int, default=180)
    p.add_argument("--skip-build-install", action="store_true")
    args = p.parse_args()
    args = apply_yaml_defaults(args)

    if args.mode == "mnn" and not args.model_dir:
        args.model_dir = r"D:\C\Desktop\ai\android\tmp-adb-model"

    if args.mode == "app":
        result = run_app(args)
    elif args.mode == "mnn":
        result = run_mnn(args)
    else:
        result = run_aura(args)
    print(json.dumps(result, indent=2, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
