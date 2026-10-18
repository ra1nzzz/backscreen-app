#!/usr/bin/env python3
"""给 Amoo / 任何进程上报 Codex 状态。

用法：
  python3 statusctl.py developing 2 "ecom-after-sales-local"
  python3 statusctl.py waiting 1 "等待确认部署"
  python3 statusctl.py idle 0 ""
"""
import json, os, sys, time

OUT = os.path.dirname(os.path.abspath(__file__))
state = (sys.argv[1] if len(sys.argv) > 1 else "idle").strip() or "idle"
active = int(sys.argv[2]) if len(sys.argv) > 2 else 0
detail = sys.argv[3] if len(sys.argv) > 3 else ""
waiting = 1 if state == "waiting" else 0
payload = {"ts": int(time.time() * 1000), "active": active, "waiting": waiting,
           "status": state, "detail": detail}
json.dump(payload, open(os.path.join(OUT, "manual.json"), "w"), ensure_ascii=False)
print("reported:", payload)
