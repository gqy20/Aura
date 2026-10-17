#!/usr/bin/env python3
"""Run Gradle through the checked-in wrapper on Windows or Unix-like shells."""

from __future__ import annotations

import os
import subprocess
import sys
from pathlib import Path


def main() -> int:
    # 不用 __file__.resolve():D:\C\Desktop\ai\android 是 E:\ai\android 的 junction 链,
    # resolve() 会把根切到 E:,与发起提交一侧的构建根不一致,KSP 增量缓存报
    # "different roots"。pre-commit 保证 hook 的 cwd 就是仓库根,优先用它。
    cwd = Path.cwd()
    root = cwd if (cwd / "gradlew.bat").exists() or (cwd / "gradlew").exists() else Path(__file__).resolve().parents[2]
    wrapper = root / ("gradlew.bat" if os.name == "nt" else "gradlew")

    if not wrapper.exists():
        print(f"Gradle wrapper not found: {wrapper}")
        return 1

    command = [str(wrapper), *sys.argv[1:]]
    print("Running:", " ".join(command))
    return subprocess.call(command, cwd=root)


if __name__ == "__main__":
    raise SystemExit(main())
