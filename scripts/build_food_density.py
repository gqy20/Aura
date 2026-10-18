#!/usr/bin/env python3
"""从《中国食物成分表(第6版)》社区 OCR 数据生成 App 内置食物密度表。

输入: Sanotsu/china-food-composition-data 的 food_composition_full.csv
     (1677 项, 双视觉模型 OCR + 交叉校验; 93% 通过 Atwater 一致性抽检)
输出: app/src/main/assets/food_density.json (紧凑密度表: 每100g 能量/蛋白/碳水/脂肪)

用法: 先下载 CSV 到项目根(或传路径), 再运行:
    uv run python scripts/build_food_density.py tmp-food-full.csv

版权说明: 底层数据来自出版物《中国食物成分表 标准版(第6版)》(杨月欣主编),
OCR 仓库无开源许可、声明仅限个人学习研究。本表仅供本项目个人使用,不入商用链路。
"""

import csv
import io
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "app/src/main/assets/food_density.json"


def num(value: str) -> float | None:
    v = (value or "").strip()
    if v in ("", "-", "—"):
        return None
    if v == "Tr":
        return 0.0
    try:
        return float(v)
    except ValueError:
        return None


def main(csv_path: str) -> None:
    raw = Path(csv_path).read_bytes()
    text = raw.decode("utf-8-sig")
    rows = list(csv.DictReader(io.StringIO(text)))
    out = []
    skipped = 0
    for r in rows:
        kcal = num(r["energyKCal"])
        protein = num(r["protein"])
        fat = num(r["fat"])
        cho = num(r["CHO"])
        if kcal is None or protein is None or fat is None or cho is None:
            skipped += 1
            continue
        out.append({
            "n": r["foodName"].strip(),
            "c": r["category"].strip(),
            "k": kcal,
            "p": protein,
            "f": fat,
            "b": cho,
        })

    OUT.parent.mkdir(parents=True, exist_ok=True)
    # 末尾换行是 pre-commit 文件规范;json.dumps compact 不带,显式补上
    OUT.write_text(
        json.dumps(out, ensure_ascii=False, separators=(",", ":")) + "\n",
        encoding="utf-8",
    )
    print(f"wrote {len(out)} entries ({skipped} skipped, {OUT.stat().st_size // 1024} KB)")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else str(ROOT / "tmp-food-full.csv"))
