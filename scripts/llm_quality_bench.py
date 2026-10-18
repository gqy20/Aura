#!/usr/bin/env python3
"""GLM vs MiniMax 提取质量/成本/速度基准。

四配置(2 模型 × thinking on/off) × N 次,同一张食物照片走 forced tool call
(生产路径: vision + submit_meal_record schema),记录耗时/usage token/提取结果。
输出 JSONL 原始数据 + 汇总表。

用法:
    uv run python scripts/llm_quality_bench.py --runs 15
    uv run python scripts/llm_quality_bench.py --runs 5 --only glm-off   # 调试
"""

import argparse
import base64
import json
import statistics
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "app/build/llm-bench-results.jsonl"

MEAL_TOOL = {
    "name": "submit_meal_record",
    "description": "Submit one structured MEAL record for a photo of prepared food. Call exactly once with the complete record.",
    "input_schema": {
        "type": "object",
        "properties": {
            "confidence": {"type": "number", "description": "0.0-1.0"},
            "items": {
                "type": "array",
                "description": "Distinct food items visible in the photo",
                "items": {
                    "type": "object",
                    "properties": {
                        "name": {"type": "string", "description": "Food name; follow the language of the photo"},
                        "portion_text": {"type": "string", "description": "Portion basis, e.g. 1 bowl / about 200g"},
                        "calories": {"type": "number", "description": "Estimated kcal for this portion"},
                        "protein_g": {"type": "number"},
                        "carbs_g": {"type": "number"},
                        "fat_g": {"type": "number"},
                        "estimated_grams": {"type": "number", "description": "Edible weight in grams"},
                    },
                    "required": ["name", "portion_text", "calories", "estimated_grams"],
                },
            },
        },
        "required": ["items"],
    },
}

SYSTEM = (
    "You are a meal logging extractor. Analyze the meal photo and submit ONE structured record "
    "by choosing the matching tool. Nutrition values are estimates; record the portion basis in "
    "portion_text. Use the language of the photo for item names. Reference anchors: standard rice "
    "bowl ~300ml (~200g cooked rice), large ramen bowl ~600ml (~300g noodles), palm-sized meat "
    "~100g, one egg ~50g."
)


def read_env():
    env = {}
    for line in (ROOT / ".env").read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if "=" in line and not line.startswith("#"):
            k, v = line.split("=", 1)
            env[k.strip()] = v.strip()
    return env


def call(provider, key, base_url, model, image_b64, thinking):
    body = {
        "model": model,
        "max_tokens": 4096,
        "stream": False,
        "system": SYSTEM,
        "messages": [{
            "role": "user",
            "content": [
                {"type": "text", "text": "提取这张餐食照片的结构化记录。"},
                {"type": "image", "source": {"type": "base64", "media_type": "image/jpeg", "data": image_b64}},
            ],
        }],
        "tools": [MEAL_TOOL],
        "tool_choice": {"type": "tool", "name": "submit_meal_record"},
        "thinking": {"type": "disabled"} if not thinking else {"type": "enabled"},
    }
    req = urllib.request.Request(
        base_url.rstrip("/") + "/v1/messages",
        data=json.dumps(body).encode(),
        headers={
            "x-api-key": key,
            "anthropic-version": "2023-06-01",
            "content-type": "application/json",
        },
    )
    t0 = time.time()
    with urllib.request.urlopen(req, timeout=180) as resp:
        payload = json.loads(resp.read())
    return payload, time.time() - t0


def quality(payload):
    """结构化质量评分:工具调用成功 + 菜名语言 + 数值合理 + 条目稳定。"""
    blocks = payload.get("content", [])
    tool = next((b for b in blocks if b.get("type") == "tool_use"), None)
    if tool is None:
        text = " ".join(b.get("text", "") for b in blocks if b.get("type") == "text")
        return {"ok": False, "reason": "declined", "decline_preview": text[:80]}
    inp = tool.get("input", {})
    items = inp.get("items", [])
    if not items:
        return {"ok": False, "reason": "empty_items"}
    kcal = sum(i.get("calories", 0) for i in items)
    grams = sum(i.get("estimated_grams", 0) for i in items)
    names = [i.get("name", "") for i in items]
    has_cjk = any(any("\u4e00" <= ch <= "\u9fff" for ch in n) for n in names)
    kcal_sane = 50 <= kcal <= 3000
    grams_sane = 50 <= grams <= 3000
    return {
        "ok": True,
        "items": len(items),
        "names": names,
        "total_kcal": kcal,
        "total_grams": grams,
        "has_cjk": has_cjk,
        "kcal_sane": kcal_sane,
        "grams_sane": grams_sane,
        "confidence": inp.get("confidence"),
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--runs", type=int, default=15)
    parser.add_argument("--only", type=str, default="")
    parser.add_argument("--image", type=str, default=str(ROOT / "tmp-bench-meal.jpg"))
    args = parser.parse_args()

    env = read_env()
    image_b64 = base64.b64encode(Path(args.image).read_bytes()).decode()

    providers = [
        {"id": "glm-off", "key": env["GLM_API_KEY"], "url": "https://open.bigmodel.cn/api/anthropic", "model": "glm-5.3-flash", "thinking": False},
        {"id": "glm-on", "key": env["GLM_API_KEY"], "url": "https://open.bigmodel.cn/api/anthropic", "model": "glm-5.3-flash", "thinking": True},
        {"id": "minimax-off", "key": env["MINIMAX_API_KEY"], "url": "https://api.minimaxi.com/anthropic", "model": "MiniMax-M3", "thinking": False},
        {"id": "minimax-on", "key": env["MINIMAX_API_KEY"], "url": "https://api.minimaxi.com/anthropic", "model": "MiniMax-M3", "thinking": True},
    ]

    OUT.parent.mkdir(parents=True, exist_ok=True)
    results = []
    for p in providers:
        if args.only and p["id"] != args.only:
            continue
        for i in range(args.runs):
            try:
                payload, dur = call(p, p["key"], p["url"], p["model"], image_b64, p["thinking"])
                usage = payload.get("usage", {})
                rec = {
                    "config": p["id"], "run": i, "duration_s": round(dur, 2),
                    "input_tokens": usage.get("input_tokens", 0),
                    "output_tokens": usage.get("output_tokens", 0),
                    "stop_reason": payload.get("stop_reason"),
                    **quality(payload),
                }
            except urllib.error.HTTPError as e:
                rec = {"config": p["id"], "run": i, "ok": False, "reason": f"http_{e.code}"}
            except Exception as e:
                rec = {"config": p["id"], "run": i, "ok": False, "reason": f"{type(e).__name__}"}
            results.append(rec)
            print(f"[{p['id']}] run {i+1}/{args.runs}: ok={rec.get('ok')} dur={rec.get('duration_s')}s "
                  f"tokens={rec.get('input_tokens')}/{rec.get('output_tokens')} "
                  f"kcal={rec.get('total_kcal')} items={rec.get('items')}", flush=True)

    with OUT.open("w", encoding="utf-8") as f:
        for r in results:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")

    print("\n" + "=" * 72)
    for p in providers:
        rows = [r for r in results if r["config"] == p["id"]]
        if not rows:
            continue
        oks = [r for r in rows if r.get("ok")]
        durs = [r["duration_s"] for r in oks]
        in_toks = [r["input_tokens"] for r in oks]
        out_toks = [r["output_tokens"] for r in oks]
        kcals = [r["total_kcal"] for r in oks]
        print(f"\n[{p['id']}] success {len(oks)}/{len(rows)}")
        if oks:
            print(f"  duration  median={statistics.median(durs):.1f}s  mean={statistics.mean(durs):.1f}s  max={max(durs):.1f}s")
            print(f"  tokens    in median={statistics.median(in_toks):.0f}  out median={statistics.median(out_toks):.0f}  out mean={statistics.mean(out_toks):.0f}")
            if kcals:
                print(f"  kcal      median={statistics.median(kcals):.0f}  stdev={statistics.stdev(kcals) if len(kcals) > 1 else 0:.0f}  min={min(kcals):.0f}  max={max(kcals):.0f}")
            if len(oks) >= 3:
                print(f"  cjk_names {sum(1 for r in oks if r['has_cjk'])}/{len(oks)}  sane_kcal {sum(1 for r in oks if r['kcal_sane'])}/{len(oks)}")
    print(f"\nraw -> {OUT}")


if __name__ == "__main__":
    main()
