"""UTF-8 正确编码的 AI 三件套冒烟 + Agent 全流程验证"""
import json
import sys
import urllib.request

BASE = "http://localhost:8000"


def post_sse(path, payload, timeout=300):
    req = urllib.request.Request(
        f"{BASE}{path}",
        data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
        headers={"Content-Type": "application/json; charset=utf-8", "X-User-Id": "1"},
        method="POST",
    )
    resp = urllib.request.urlopen(req, timeout=timeout)
    return resp.read().decode("utf-8")


def test_writing():
    raw = post_sse("/api/ai/writing-assist",
                   {"type": "continue", "content": "微服务架构将单体应用拆分为多个独立服务，每个服务专注于单一业务能力。"})
    has_error = "event: error" in raw
    has_done = raw.strip().endswith("data: [DONE]")
    chinese_ok = any("\u4e00" <= ch <= "\u9fff" for ch in raw)
    print(f"[{'PASS' if not has_error and has_done and chinese_ok else 'FAIL'}] writing-assist: "
          f"error={has_error} done={has_done} chinese={chinese_ok} len={len(raw)}")
    print("  sample:", raw[:120].replace("\n", "\\n"))


def test_qa():
    raw = post_sse("/api/qa/ask", {"articleId": 26, "question": "这篇文章讲了什么？"})
    has_done = "data: [DONE]" in raw
    chinese_ok = any("\u4e00" <= ch <= "\u9fff" for ch in raw)
    print(f"[{'PASS' if has_done and chinese_ok else 'FAIL'}] rag-qa: done={has_done} chinese={chinese_ok} len={len(raw)}")
    print("  sample:", raw[:120].replace("\n", "\\n"))


def test_agent():
    events = []
    raw = post_sse("/api/ai/agent/create",
                   {"goal": "写一篇150字左右的短文介绍Redis缓存", "style": "简洁", "wordCount": 150},
                   timeout=600)
    for line in raw.split("\n"):
        line = line.strip()
        if line.startswith("data:"):
            try:
                events.append(json.loads(line[5:].strip()))
            except ValueError:
                pass
    types = [e.get("type") for e in events]
    has_thinking = "thinking" in types
    has_tool = "tool_start" in types
    has_tool_result = "tool_result" in types
    has_final = "final_chunk" in types
    has_done = "done" in types
    final = next((e["data"] for e in events if e["type"] == "final_chunk"), "")
    ok = has_thinking and has_final and has_done
    print(f"[{'PASS' if ok else 'FAIL'}] agent-sse: thinking={has_thinking} tool_start={has_tool} "
          f"tool_result={has_tool_result} final_chunk={has_final} done={has_done}")
    print(f"  event sequence: {types}")
    if final:
        print(f"  final answer ({len(final)} chars): {final[:150]}...")


if __name__ == "__main__":
    which = sys.argv[1] if len(sys.argv) > 1 else "all"
    if which in ("all", "writing"):
        test_writing()
    if which in ("all", "qa"):
        test_qa()
    if which in ("all", "agent"):
        test_agent()
