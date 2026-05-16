#!/usr/bin/env python3
"""Validate commit messages against the project's Conventional Commits subset."""

from __future__ import annotations

import re
import sys
from pathlib import Path


ALLOWED_PREFIXES = ("Merge ", "Revert ", "fixup!", "squash!")
MESSAGE_RE = re.compile(
    r"^(feat|fix|refactor|perf|test|chore|docs|style)"
    r"(\([a-z0-9][a-z0-9-]*\))?"
    r"(!)?: .{1,72}$"
)


def main() -> int:
    if len(sys.argv) != 2:
        print("Usage: check_commit_msg.py <commit-msg-file>")
        return 2

    message_path = Path(sys.argv[1])
    lines = message_path.read_text(encoding="utf-8-sig").splitlines()
    first_line = lines[0].strip() if lines else ""

    if not first_line:
        print("Commit message subject is empty.")
        return 1

    if first_line.startswith(ALLOWED_PREFIXES) or MESSAGE_RE.match(first_line):
        return 0

    print("Commit message must follow Conventional Commits:")
    print("  <type>(<scope>): <subject>")
    print("Allowed types: feat, fix, refactor, perf, test, chore, docs, style")
    print("Example: feat(chat): add streaming reply bubble")
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
