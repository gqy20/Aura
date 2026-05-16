#!/usr/bin/env python3
"""Run Gradle through the checked-in wrapper on Windows or Unix-like shells."""

from __future__ import annotations

import os
import subprocess
import sys
from pathlib import Path


def main() -> int:
    root = Path(__file__).resolve().parents[2]
    wrapper = root / ("gradlew.bat" if os.name == "nt" else "gradlew")

    if not wrapper.exists():
        print(f"Gradle wrapper not found: {wrapper}")
        return 1

    command = [str(wrapper), *sys.argv[1:]]
    print("Running:", " ".join(command))
    return subprocess.call(command, cwd=root)


if __name__ == "__main__":
    raise SystemExit(main())
