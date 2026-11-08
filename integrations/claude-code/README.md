# Claude Code hook

Shows each Claude Code session on the panel's `agents` widget: **working** while it runs, **needs permission**
(red, on top) when it waits for you to approve a tool, **done** when the turn ends. The row disappears when
the session closes.

## Install

1. Copy the hook:
   ```sh
   mkdir -p ~/.claude/hooks && cp vigia_hook.py ~/.claude/hooks/
   ```
2. Merge `settings.example.json` into `~/.claude/settings.json` (user-wide) or a project's
   `.claude/settings.json`. Set `VIGIA_URL` to the address on the panel's setup screen and `VIGIA_TOKEN` to its
   token. Keep the token out of repositories: user settings live outside them.
3. Add an `agents` widget to the panel config (`"type": "agents"`, source `claude-code` by default).

The phone must be reachable from the computer: over Tailscale (`http://100.x.y.z:8787`), or over USB with
`adb forward tcp:8787 tcp:8787` and `VIGIA_URL=http://127.0.0.1:8787`.

## How it behaves

| Hook | Sends |
|---|---|
| `UserPromptSubmit` | `working`, with the prompt as detail |
| `PostToolUse` | `working` (after you approve a tool) |
| `Notification` (permission) | `waiting_permission`, with the message |
| `Notification` (idle) / `Stop` | `done` |
| `SessionEnd` | removes the row |

- Never slows Claude Code: it forks and returns in well under 100 ms; the request runs in the background with a
  2 s timeout, and every failure is ignored (a panel that is off changes nothing).
- Sends only when a session's state changes, tracked in a small file per session under the temp directory, so
  `PostToolUse` does not flood the panel.
- Python 3 standard library only. Tests: `python3 -m unittest` in this folder.
