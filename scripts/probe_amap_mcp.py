"""Probe the public AMap MCP endpoint end-to-end.

Steps:
  1. initialize  (capture protocol version + Mcp-Session-Id)
  2. notifications/initialized
  3. tools/list   (dump all tool names + brief description)
  4. tools/call   (try a realistic "find food nearby" call)

Run with the system Python 3.12. Uses only stdlib.
"""
from __future__ import annotations

import json
import sys
import urllib.error
import urllib.request

URL = "https://mcp.amap.com/mcp?key=e7ee6467e1bd2a1fe7a5be0672a8f81d"
CLIENT_INFO = {"name": "manual-probe", "version": "0.0.1"}
PROTOCOL_VERSION_HINT = "2025-03-26"


def post(payload: dict, session_id: str | None, protocol_version: str | None) -> tuple[bytes, dict]:
    headers = {
        "Content-Type": "application/json",
        "Accept": "application/json, text/event-stream",
    }
    if session_id:
        headers["Mcp-Session-Id"] = session_id
    if protocol_version:
        headers["MCP-Protocol-Version"] = protocol_version
    req = urllib.request.Request(
        URL,
        data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
        headers=headers,
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            return resp.read(), resp.headers
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8", "replace")
        print(f"[HTTP {e.code}] {body[:2000]}", file=sys.stderr)
        raise


def parse_sse_or_json(raw: bytes) -> dict:
    text = raw.decode("utf-8", "replace").strip()
    if not text:
        return {}
    if text.startswith("data:"):
        # SSE form — join all data: lines
        joined = "\n".join(
            line[len("data:"):].lstrip() for line in text.splitlines() if line.startswith("data:")
        )
        return json.loads(joined)
    return json.loads(text)


def main() -> None:
    # 1. initialize -----------------------------------------------------------
    init_payload = {
        "jsonrpc": "2.0",
        "id": 1,
        "method": "initialize",
        "params": {
            "protocolVersion": PROTOCOL_VERSION_HINT,
            "capabilities": {},
            "clientInfo": CLIENT_INFO,
        },
    }
    raw, headers = post(init_payload, session_id=None, protocol_version=None)
    init = parse_sse_or_json(raw)
    session_id = headers.get("Mcp-Session-Id")
    result = init.get("result", {})
    negotiated = result.get("protocolVersion")
    server_info = result.get("serverInfo", {})
    print("=== 1. initialize ===")
    print(f"  serverInfo     = {server_info}")
    print(f"  protocolVersion= {negotiated}")
    print(f"  capabilities   = {result.get('capabilities')}")
    print(f"  Mcp-Session-Id = {session_id!r}")

    if not session_id:
        print("WARNING: no Mcp-Session-Id returned, will try stateless mode")

    # 2. notifications/initialized -------------------------------------------
    note_payload = {"jsonrpc": "2.0", "method": "notifications/initialized"}
    try:
        post(note_payload, session_id=session_id, protocol_version=negotiated)
        print("=== 2. notifications/initialized ===  OK")
    except Exception as e:  # noqa: BLE001
        print(f"=== 2. notifications/initialized ===  FAILED: {e}")

    # 3. tools/list ------------------------------------------------------------
    list_payload = {"jsonrpc": "2.0", "id": 2, "method": "tools/list", "params": {}}
    raw, _ = post(list_payload, session_id=session_id, protocol_version=negotiated)
    listed = parse_sse_or_json(raw)
    tools = listed.get("result", {}).get("tools", [])
    print(f"=== 3. tools/list ===  {len(tools)} tool(s)")
    for t in tools:
        name = t.get("name", "?")
        desc = (t.get("description") or "").splitlines()[0][:120]
        print(f"  - {name:32s}  {desc}")

    # 4. tools/call — try a couple of likely "nearby / POI" candidates -------
    candidate_calls = [
        {
            "label": "maps_around_search  keywords=美食 / 餐饮 / 1km / 天安门",
            "payload": {
                "jsonrpc": "2.0",
                "id": 3,
                "method": "tools/call",
                "params": {
                    "name": "maps_around_search",
                    "arguments": {
                        "keywords": "美食",
                        "location": "116.397428,39.90923",
                        "radius": "1000",
                        "types": "餐饮",
                    },
                },
            },
        },
        {
            "label": "maps_text_search    keywords=美食  city=北京",
            "payload": {
                "jsonrpc": "2.0",
                "id": 4,
                "method": "tools/call",
                "params": {
                    "name": "maps_text_search",
                    "arguments": {
                        "keywords": "美食",
                        "city": "北京",
                    },
                },
            },
        },
        {
            "label": "maps_geo            address=北京市朝阳区阜通东大街",
            "payload": {
                "jsonrpc": "2.0",
                "id": 5,
                "method": "tools/call",
                "params": {
                    "name": "maps_geo",
                    "arguments": {
                        "address": "北京市朝阳区阜通东大街",
                    },
                },
            },
        },
    ]
    for call in candidate_calls:
        print(f"\n=== 4. tools/call  ::  {call['label']} ===")
        try:
            raw, _ = post(
                call["payload"],
                session_id=session_id,
                protocol_version=negotiated,
            )
            resp = parse_sse_or_json(raw)
            content = resp.get("result", {}).get("content")
            if content is None:
                print(f"  raw response: {json.dumps(resp, ensure_ascii=False)[:1500]}")
            else:
                for block in content:
                    if block.get("type") == "text":
                        print(block.get("text", "")[:4000])
                    else:
                        print(json.dumps(block, ensure_ascii=False)[:4000])
        except Exception as e:  # noqa: BLE001
            print(f"  FAILED: {e}")


if __name__ == "__main__":
    main()
