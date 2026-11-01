#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
此地无银三百两 · MCP 启动包装器

有些 MCP 客户端（如 AstroBot）只允许用 python / node 等解释器启动服务，
不允许直接执行 exe。本脚本把客户端的 stdio 转发给 MucageLedger.exe --mcp。

用法：
    python mucage_mcp.py

客户端配置：
    {
      "mcpServers": {
        "mucage-ledger": {
          "command": "python",
          "args": ["C:\\\\...\\\\MucageLedger\\\\mucage_mcp.py"]
        }
      }
    }

可用环境变量 MUCAGE_LEDGER_EXE 指定 MucageLedger.exe 的完整路径；
默认读取与本脚本同目录下的 MucageLedger.exe。
"""

import os
import subprocess
import sys
import threading

def resolve_exe() -> str:
    custom = os.environ.get("MUCAGE_LEDGER_EXE", "").strip()
    if custom:
        return custom
    here = os.path.dirname(os.path.abspath(__file__))
    return os.path.join(here, "MucageLedger.exe")

def main() -> int:
    exe = resolve_exe()
    if not os.path.isfile(exe):
        sys.stderr.write("[mucage-mcp] 找不到 MucageLedger.exe：%s\n" % exe)
        return 2

    for stream in (sys.stdin, sys.stdout):
        try:
            stream.reconfigure(encoding="utf-8")
        except Exception:
            pass

    creationflags = 0
    if os.name == "nt":
        creationflags = getattr(subprocess, "CREATE_NO_WINDOW", 0x08000000)

    proc = subprocess.Popen(
        [exe, "--mcp"],
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=None,
        text=True,
        encoding="utf-8",
        bufsize=1,
        creationflags=creationflags,
    )

    def pump_input():
        try:
            for line in sys.stdin:
                proc.stdin.write(line)
                proc.stdin.flush()
        except Exception:
            pass
        finally:
            try:
                proc.stdin.close()
            except Exception:
                pass

    threading.Thread(target=pump_input, daemon=True).start()

    try:
        for line in proc.stdout:
            sys.stdout.write(line)
            sys.stdout.flush()
    finally:
        try:
            proc.wait(timeout=10)
        except Exception:
            proc.kill()
    return proc.returncode or 0

if __name__ == "__main__":
    sys.exit(main())
