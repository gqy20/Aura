#!/usr/bin/env python3
"""Fast staged-file checks for the Android project."""

from __future__ import annotations

import sys
from pathlib import Path


MAX_FILE_BYTES = 2 * 1024 * 1024
SENSITIVE_NAMES = {
    ".env",
    "local.properties",
    "keystore.properties",
    "google-services.json",
}
SENSITIVE_SUFFIXES = {
    ".jks",
    ".keystore",
    ".p12",
    ".pem",
    ".key",
}
TEXT_SUFFIXES = {
    ".bat",
    ".gradle",
    ".java",
    ".json",
    ".kts",
    ".kt",
    ".md",
    ".properties",
    ".pro",
    ".toml",
    ".txt",
    ".xml",
    ".yaml",
    ".yml",
}
CONFLICT_MARKERS = ("<<<<<<< ", "=======", ">>>>>>> ")


def is_probably_text(path: Path) -> bool:
    if path.suffix.lower() in TEXT_SUFFIXES:
        return True
    try:
        return b"\0" not in path.read_bytes()[:4096]
    except OSError:
        return False


def check_path(path: Path) -> list[str]:
    issues: list[str] = []
    normalized = path.as_posix()

    if path.name in SENSITIVE_NAMES or path.suffix.lower() in SENSITIVE_SUFFIXES:
        issues.append(f"{normalized}: sensitive local/config file should not be committed")
        return issues

    if not path.exists() or not path.is_file():
        return issues

    size = path.stat().st_size
    if size > MAX_FILE_BYTES:
        issues.append(f"{normalized}: file is larger than 2 MiB ({size} bytes)")

    if not is_probably_text(path):
        return issues

    try:
        lines = path.read_text(encoding="utf-8").splitlines(keepends=True)
    except UnicodeDecodeError:
        return issues

    for line_no, line in enumerate(lines, start=1):
        content = line.rstrip("\r\n")
        if content.rstrip(" \t") != content:
            issues.append(f"{normalized}:{line_no}: trailing whitespace")
        if any(content.startswith(marker) for marker in CONFLICT_MARKERS):
            issues.append(f"{normalized}:{line_no}: unresolved merge conflict marker")

    if lines and not lines[-1].endswith(("\n", "\r")):
        issues.append(f"{normalized}: missing newline at end of file")

    return issues


def main() -> int:
    issues: list[str] = []
    for raw_path in sys.argv[1:]:
        issues.extend(check_path(Path(raw_path)))

    if issues:
        print("File sanity checks failed:")
        for issue in issues:
            print(f"  - {issue}")
        return 1

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
