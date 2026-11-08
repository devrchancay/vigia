#!/usr/bin/env python3
"""Claude Code hook that shows each session on a Vigia panel (agents widget).

Wire it to the UserPromptSubmit, Notification, PostToolUse, Stop and SessionEnd hooks (see
settings.example.json). Claude Code passes the hook payload as JSON on stdin.

It must never slow Claude Code down: it decides what to send, forks, and the parent exits at once. The
child sends one request with a short timeout and ignores every failure. It only sends when the session
state changes, because PostToolUse fires on every tool call.

Configuration (environment variables):
  VIGIA_URL    base URL of the panel, e.g. http://100.101.102.103:8787 (Tailscale) or http://127.0.0.1:8787
  VIGIA_TOKEN  bearer token from the panel's setup screen
  VIGIA_SOURCE event source, default "claude-code" (must match the agents widget)
Standard library only.
"""

import json
import os
import re
import sys
import tempfile
import urllib.request

TIMEOUT_SECONDS = 2
STATE_DIR = os.path.join(tempfile.gettempdir(), "vigia-claude-hook")

# Event kinds understood by the agents widget.
WORKING = "working"
WAITING = "waiting_permission"
DONE = "done"


def decide(payload):
    """Returns (kind, title, body, level) to send for this hook call, ("delete", ...) to remove the row,
    or None when the hook does not change the session state."""
    event = payload.get("hook_event_name", "")
    project = os.path.basename(os.path.normpath(payload.get("cwd") or "")) or "claude"
    if event == "UserPromptSubmit":
        prompt = " ".join((payload.get("prompt") or "").split())
        return WORKING, f"{project}: working", prompt[:120] or None, "info"
    if event == "PostToolUse":
        return WORKING, f"{project}: working", None, "info"
    if event == "Notification":
        message = payload.get("message") or ""
        kind = payload.get("notification_type") or ""
        if kind == "permission_prompt" or re.search(r"permission", message, re.IGNORECASE):
            return WAITING, f"{project}: needs permission", message[:200] or None, "warn"
        # Idle notifications ("waiting for your input") mean the turn is over.
        return DONE, f"{project}: waiting for you", message[:200] or None, "ok"
    if event == "Stop":
        return DONE, f"{project}: done", None, "ok"
    if event == "SessionEnd":
        return "delete", project, None, None
    return None


def changed(session_id, kind):
    """True when [kind] differs from what was last sent for this session; records it."""
    os.makedirs(STATE_DIR, exist_ok=True)
    path = os.path.join(STATE_DIR, re.sub(r"[^A-Za-z0-9_-]", "_", session_id))
    try:
        with open(path, encoding="utf-8") as f:
            if f.read() == kind:
                return False
    except OSError:
        pass
    if kind == "delete":
        try:
            os.remove(path)
        except OSError:
            pass
    else:
        with open(path, "w", encoding="utf-8") as f:
            f.write(kind)
    return True


def build_request(base_url, token, source, session_id, decision):
    kind, title, body, level = decision
    headers = {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}
    if kind == "delete":
        url = f"{base_url}/api/v1/events/{source}/{urllib.request.quote(session_id, safe='')}"
        return urllib.request.Request(url, headers=headers, method="DELETE")
    event = {"source": source, "key": session_id, "kind": kind, "title": title, "level": level,
             "ttlSec": 2 * 60 * 60 if kind != DONE else 60 * 60}
    if body:
        event["body"] = body
    return urllib.request.Request(f"{base_url}/api/v1/events", data=json.dumps(event).encode(), headers=headers,
                                  method="POST")


def main():
    base_url = os.environ.get("VIGIA_URL", "").rstrip("/")
    token = os.environ.get("VIGIA_TOKEN", "")
    source = os.environ.get("VIGIA_SOURCE", "claude-code")
    if not base_url or not token:
        return
    try:
        payload = json.load(sys.stdin)
    except ValueError:
        return
    session_id = payload.get("session_id")
    decision = decide(payload)
    if not session_id or decision is None or not changed(session_id, decision[0]):
        return
    request = build_request(base_url, token, source, session_id, decision)
    if hasattr(os, "fork") and os.fork() > 0:
        return  # Parent returns to Claude Code immediately; the child sends.
    try:
        urllib.request.urlopen(request, timeout=TIMEOUT_SECONDS).close()
    except Exception:  # noqa: BLE001 - a panel that is off or unreachable must never surface in Claude Code
        pass
    finally:
        if hasattr(os, "fork"):
            os._exit(0)


if __name__ == "__main__":
    try:
        main()
    except Exception:  # noqa: BLE001
        pass
    sys.exit(0)
