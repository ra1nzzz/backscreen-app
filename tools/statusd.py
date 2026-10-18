#!/usr/bin/env python3
"""Amoo / Codex 状态发布器。

采集本机 Amoo（openclaw）会话活动，输出给背屏工坊读取的 status.json：

    {
      "active": 1,           # 进行中会话数量
      "waiting": 0,          # 其中等待人工回答的数量
      "status": "developing",# developing | waiting | idle
      "detail": "…",         # 当前主要任务摘要
      "updated": "2026-10-18T20:05:00+08:00"
    }

发布位置：
  * 本地 /root/build/status/status.json（调试用）
  * 若配置了 GitHub 仓库（同目录 repo.txt），推送到仓库根目录 status.json
"""

import glob
import json
import os
import subprocess
import sys
import time
from datetime import datetime, timedelta, timezone

STATE_DIR = "/root/.openclaw"
OUT_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_FILE = os.path.join(OUT_DIR, "repo.txt")
INTERVAL = int(os.environ.get("STATUS_INTERVAL", "60"))
PUSH_EVERY = 300  # 秒，避免过于频繁打 GitHub API

CST = timezone(timedelta(hours=8))


def now_ms():
    return int(time.time() * 1000)


def load_sessions():
    """收集所有 agent 的会话元信息。"""
    out = []
    for path in glob.glob(os.path.join(STATE_DIR, "agents", "*", "sessions", "sessions.json")):
        try:
            data = json.load(open(path))
        except Exception:
            continue
        agent = path.split(os.sep)[-3]
        for key, meta in data.items():
            if not isinstance(meta, dict):
                continue
            out.append({
                "agent": agent,
                "key": key,
                "updated": int(meta.get("updatedAt") or 0),
                "file": meta.get("sessionFile") or "",
                "aborted": bool(meta.get("abortedLastRun")),
            })
    return out


def last_message_kind(session_file):
    """读取会话文件最后一条有意义的消息类型；读不到返回空串。"""
    try:
        if not session_file or not os.path.exists(session_file):
            return ""
        with open(session_file, "rb") as f:
            f.seek(0, os.SEEK_END)
            size = f.tell()
            f.seek(max(0, size - 64 * 1024))
            tail = f.read().decode("utf-8", "ignore")
        lines = [ln for ln in tail.split("\n") if ln.strip().startswith("{")]
        for line in reversed(lines[-30:]):
            try:
                obj = json.loads(line)
            except Exception:
                continue
            msg = obj.get("message") or {}
            role = msg.get("role") or obj.get("role")
            if role:
                return str(role)
            if obj.get("type") == "tool":
                return "tool"
    except Exception:
        pass
    return ""


MANUAL_FILE = os.path.join(OUT_DIR, "manual.json")


def manual_state():
    """Amoo 主动上报的状态（优先于自动探测）；超过 30 分钟视为过期。"""
    try:
        data = json.load(open(MANUAL_FILE))
        ts = int(data.get("ts") or 0)
        if ts and (time.time() * 1000 - ts) < 30 * 60 * 1000:
            return data
    except Exception:
        pass
    return None


def compute_state():
    manual = manual_state()
    if manual:
        return {
            "active": int(manual.get("active", 0)),
            "waiting": int(manual.get("waiting", 0)),
            "status": manual.get("status", "idle"),
            "detail": manual.get("detail", ""),
            "updated": datetime.now(CST).isoformat(timespec="seconds"),
        }
    sessions = load_sessions()
    t = now_ms()
    window = 15 * 60 * 1000
    recent = [s for s in sessions if t - s["updated"] <= window]
    active = len(recent)

    waiting = 0
    busy = 0
    for s in recent:
        kind = last_message_kind(s["file"])
        if kind == "user":
            waiting += 1
        elif kind in ("assistant", "tool"):
            busy += 1

    if waiting:
        status = "waiting"
    elif busy or (active and t - max(s["updated"] for s in recent) <= 3 * 60 * 1000):
        status = "developing"
    elif active:
        status = "developing"
    else:
        status = "idle"

    detail = ""
    if recent:
        latest = max(recent, key=lambda s: s["updated"])
        detail = latest["key"].split(":")[-1]

    return {
        "active": active,
        "waiting": waiting,
        "status": status,
        "detail": detail,
        "updated": datetime.now(CST).isoformat(timespec="seconds"),
    }


def push_github(payload):
    try:
        repo = open(REPO_FILE).read().strip()
    except Exception:
        return
    if not repo:
        return
    import base64
    body = json.dumps(payload, ensure_ascii=False, indent=2) + "\n"
    content = base64.b64encode(body.encode()).decode()
    sha = ""
    try:
        r = subprocess.run(["gh", "api", f"repos/{repo}/contents/status.json", "--jq", ".sha"],
                           capture_output=True, text=True, timeout=30)
        if r.returncode == 0:
            sha = r.stdout.strip()
    except Exception:
        pass
    cmd = ["gh", "api", "-X", "PUT", f"repos/{repo}/contents/status.json",
           "-f", "message=chore: update codex status",
           "-f", f"content={content}", "-f", "branch=main"]
    if sha:
        cmd += ["-f", f"sha={sha}"]
    try:
        r = subprocess.run(cmd, capture_output=True, text=True, timeout=60)
        if r.returncode != 0:
            print("push failed:", r.stderr.strip()[:200], flush=True)
    except Exception as e:
        print("push error:", e, flush=True)


def main():
    last_push = 0
    once = "--once" in sys.argv
    while True:
        payload = compute_state()
        try:
            with open(os.path.join(OUT_DIR, "status.json"), "w") as f:
                json.dump(payload, f, ensure_ascii=False, indent=2)
                f.write("\n")
        except Exception as e:
            print("write error:", e, flush=True)
        now = time.time()
        if now - last_push >= PUSH_EVERY or last_push == 0:
            push_github(payload)
            last_push = now
        print(json.dumps(payload, ensure_ascii=False), flush=True)
        if once:
            return
        time.sleep(INTERVAL)


if __name__ == "__main__":
    main()
