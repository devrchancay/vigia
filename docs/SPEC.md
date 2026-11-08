# Vigía — Specification (v1)

Status: design. Nothing is implemented yet. Read `CONTEXT.md` first for the why.

## 1. Overview

Vigía turns an old Android phone into an always-on, remotely configurable status panel for developers.

```
 ┌─────────────── Android phone (kiosk) ───────────────┐
 │  Panel UI (Compose)  ◄──  StatusStore (Room+Flow)   │
 │         ▲                        ▲                  │
 │   PanelConfig (JSON,             │                  │
 │   DataStore + history)     Sources (pollers)        │
 │         ▲                  uptime · github · device │
 │         │                        ▲                  │
 │   Embedded server (Ktor, foreground service) ───────┘
 └─────────▲──────────────────────────▲─────────────────┘
           │ HTTP + bearer token over Tailscale
   vigia CLI / MCP server        Claude Code hooks, CI webhooks,
   (Claude Code, scripts)        Gmail→Apps Script, radar jobs
```

## 2. Repository layout (monorepo)

```
android/                 Kotlin app (Gradle, Kotlin DSL, version catalog)
  app/
  core/designsystem      theme, typography, base components
  core/model             PanelConfig, Widget, Event, Status (kotlinx.serialization)
  core/data              config repository (DataStore + history), status store (Room)
  core/server            Ktor server, auth, routes, validation
  core/device            battery, memory, network, brightness, kiosk helpers
  core/common            Result, injected dispatchers, extensions
  feature/panel          widget renderer and grid layout
  feature/setup          first-run screen: token + QR, kiosk checklist
  feature/sources        pollers (uptime, github actions)
packages/vigia           TypeScript CLI + MCP server (one npm package, `vigia mcp` subcommand)
integrations/claude-code hook scripts + settings snippet
integrations/examples    Apps Script (Gmail→events), GitHub Actions step, curl examples
docs/                    CONTEXT, SPEC, API reference, kiosk setup guide
```

## 3. Android app

### Stack
Kotlin, Jetpack Compose + Material 3, MVVM (ViewModel + StateFlow, UiState + events), Hilt, Room, DataStore, kotlinx.serialization, OkHttp (outbound polling), Ktor server (CIO engine), WorkManager only if needed, Timber. minSdk 26, latest stable target/compile SDK. Always verify latest stable library versions before writing them.

### Runtime behavior
- `PanelActivity` is fullscreen, edge-to-edge, immersive, `FLAG_KEEP_SCREEN_ON`.
- Always landscape (`sensorLandscape`: either landscape side, following the sensor). The panel is designed for a phone lying on its side on a desk stand; the grid is laid out for landscape.
- `VigiaService` foreground service hosts the Ktor server and pollers. Use `specialUse` foreground service type with a clear property description (`dataSync` has a 6 h/day limit on Android 15+). Started on boot (`BOOT_COMPLETED`) and by the activity.
- Kiosk: if the app is device owner, enable lock task mode and set itself as persistent preferred HOME. If not, run as a normal app and show the setup checklist.
- Night schedule: window brightness (no `WRITE_SETTINGS` needed) and optional dimmed layout.

### Performance budget (reference: Helio G35, 2 GB)
- Steady 60 fps on the panel, no recomposition storms (stable models, `key`s, derived state)
- < 150 MB RAM for the whole app
- Polling defaults 30–60 s, jittered, coalesced per host; exponential backoff on failures
- Room history pruned (e.g. 7 days) to protect the eMMC

## 4. Panel config

The whole UI is described by one JSON document, validated on the device. Unknown widget types render as a visible error tile, never crash.

Validation rules (implemented in `core/model`, `PanelConfigValidator`): unknown keys are rejected (typos surface instead of being ignored); `schemaVersion` must be 1; grid `columns`/`rows` 1–24, `gap` 0–64; at most 64 widgets; widget ids are 1–64 chars of `a-z 0-9 - _`, unique; every widget lies inside the grid and widgets do not overlap; `night` times are `HH:mm` and `brightness` 0–1; `theme.palette` is one of `ember`, `teal`, `indigo` (named palettes, decided 2026-11-07: raw colors could produce unreadable panels). Each known widget type validates its own `params` (unknown keys rejected, errors at `widgets[i].params.<field>`); see the widget table. Defaults: landscape grid 8×4, gap 8; dark theme, `ember` palette, solid background.

```json
{
  "schemaVersion": 1,
  "theme": { "mode": "dark", "palette": "ember", "background": "gradient" },
  "grid": { "columns": 8, "rows": 4, "gap": 8 },
  "night": { "from": "23:00", "to": "07:00", "brightness": 0.05 },
  "widgets": [
    { "id": "clock", "type": "clock", "pos": { "x": 0, "y": 0, "w": 3, "h": 2 } },
    { "id": "device", "type": "device", "pos": { "x": 0, "y": 2, "w": 3, "h": 1 },
      "params": { "show": ["battery", "batteryTemp", "memory", "wifi"] } },
    { "id": "agents", "type": "agents", "title": "Claude Code", "pos": { "x": 0, "y": 3, "w": 3, "h": 1 } },
    { "id": "sites", "type": "uptime", "title": "Sites", "pos": { "x": 3, "y": 0, "w": 2, "h": 2 },
      "params": { "intervalSec": 60, "targets": [
        { "name": "website", "url": "https://example.com" },
        { "name": "api", "url": "https://api.example.com/health", "expectStatus": 200 } ] } },
    { "id": "ci", "type": "github_actions", "title": "CI", "pos": { "x": 5, "y": 0, "w": 3, "h": 2 },
      "params": { "repos": ["owner/repo"], "branch": "main", "intervalSec": 60, "tokenSecret": "github" } },
    { "id": "inbox", "type": "feed", "title": "Upwork & leads", "pos": { "x": 3, "y": 2, "w": 5, "h": 2 },
      "params": { "sources": ["upwork", "radar"], "max": 5 } }
  ]
}
```

### Widget types (v1)
| type | data source | notes |
|---|---|---|
| `clock` | device | time, date, locale-aware |
| `device` | device | battery %, temperature (warns at 40 °C), charging, memory, Wi-Fi signal. `params.show` picks rows: `battery`, `batteryTemp`, `memory`, `wifi` (default: all). No SSID: reading it needs the location permission |
| `uptime` | poller | status, latency, last change; red/amber/green. `params`: `targets` (1–20 of `{name, url, expectStatus?}`), `intervalSec` 15–3600 (default 60), `slowMs` (amber above it, default 1000) |
| `github_actions` | poller | latest run per repo/workflow, conclusion, duration. `params`: `repos` (1–10 `owner/repo`), `branch?`, `workflow?` (file name or id), `intervalSec` 30–3600 (default 60), `tokenSecret?` |
| `agents` | pushed events (`source: claude-code`) | one row per session (event key): waiting for permission first (red), then working (amber), then done (green). `params`: `source` (default `claude-code`), `max` 1–20 (default 6). Event `kind`: `working`, `waiting_permission`, `done` |
| `feed` | pushed events filtered by `source` | newest items with level color, source (when mixed) and age; expired items hidden. `params`: `sources` (1–10), `max` 1–20 (default 5), `showBody`. Tickets: `sources: ["jira", "hub"]` |
| `metric` | pushed value | big number + label + optional trend (generic) |
| `note` | config | short text for reminders. `params.text` (1–500 chars) |
| `world_clock` | device | time in 1–6 cities (`zones: [{label, zone}]`, IANA ids like `Europe/Madrid`), with +1/−1 when the day differs |
| `weather` | poller (Open-Meteo, no key) | temperature, sky, feels-like, today's max/min, rain probability. `params`: `name`, `latitude`, `longitude`, `intervalSec` 600–3600 (default 900) |
| `web` | network (WebView) | an embedded https page filling the tile, e.g. a Spotify playlist embed (`https://open.spotify.com/embed/playlist/<id>`); links to other hosts are blocked. Spotify embeds play 30 s previews unless signed in inside the embed |
| `x_followers` | poller (X API v2, paid per read) | follower count and change over 24 h. `params`: `username`, `tokenSecret` (bearer token stored with `PUT /secrets/{name}`), `intervalSec` 3600–86400 (default 21600) |

Generic `metric`, `feed` and `note` mean any script can drive the panel without new app code.

### Secrets
Secrets (GitHub token, webhook URLs) are never in the config. They are stored write-only via `PUT /api/v1/secrets/{name}` (raw value as the body, up to 4096 chars), encrypted with AES-256-GCM under a non-exportable Android Keystore key, and referenced by name (`tokenSecret`). `GET /api/v1/secrets` lists names only; no endpoint returns a value. Names follow the widget id format.

### History and rollback
Every accepted config change is stored with a timestamp and author (from a header). Keep the last 20. `POST /api/v1/config/rollback` restores the previous one.

## 5. HTTP API (embedded server)

- Base: `http://<tailscale-ip>:8787/api/v1`
- Auth: `Authorization: Bearer <token>`. Token generated on first launch, shown on the setup screen as text + QR, rotatable.
- JSON everywhere; errors as `{ "error": { "code", "message", "path" } }` with path pointing at the invalid field.
- Server listens on all interfaces but docs strongly recommend Tailscale only. Optional allowlist of CIDRs (default: Tailscale `100.64.0.0/10` + localhost).

| Method & path | Purpose |
|---|---|
| `GET /health` | liveness, app version, uptime, device info |
| `GET /config` | current config |
| `PUT /config` | replace config (validated) |
| `PATCH /config` | JSON Merge Patch (RFC 7396) |
| `POST /config/validate` | dry run, returns errors without applying |
| `POST /config/rollback` | restore previous config |
| `GET /config/history` | list of previous versions |
| `POST /widgets` / `PATCH /widgets/{id}` / `DELETE /widgets/{id}` | widget-level edits |
| `POST /widgets/{id}/data` | push data to `metric`/`note` widgets |
| `GET /widgets/types` | widget catalog with param specs (type, required, default, description) so agents discover what is possible |
| `POST /events` / `GET /events` / `DELETE /events/{source}[/{key}]` | push, list and remove events (see below) |
| `GET /status` | current data of every widget (what the screen shows, as JSON) |
| `GET /screenshot` | PNG of the current panel (PixelCopy) so agents can verify changes; 503 when the panel is not on screen |
| `POST /commands` | `reload`, `brightness`, `screen_on`, `screen_off`, `toast`, `identify` (flash the screen) |
| `GET /secrets` / `PUT /secrets/{name}` / `DELETE /secrets/{name}` | write-only secrets (list returns names only) |

### Event format
```json
{
  "source": "claude-code",
  "kind": "waiting_permission",
  "key": "session-abc123",
  "title": "vigia: needs permission to run gradle",
  "body": "optional detail",
  "level": "warn",
  "ttlSec": 3600,
  "url": "optional link",
  "at": "2026-11-07T15:00:00Z"
}
```
`key` makes events idempotent: a new event with the same `source`+`key` replaces the previous one (that is how an agent row moves from working → waiting → done).

Rules (implemented in `core/model`, `PanelEvent`): `source` 1–64 chars of `a-z 0-9 - _`; `title` 1–200 chars; `key` ≤ 128; `kind` `a-z 0-9 _` ≤ 40; `body` ≤ 1000; `level` `info|ok|warn|error` (default `info`); `ttlSec` 10 s – 7 days (default 1 day); `url` http(s) ≤ 500; `at` optional ISO-8601 instant for things that happen at a time (meetings, deadlines). `POST /events` takes one object or an array of up to 50, validates all before storing any (422 with `events[i].field` paths) and answers 202 `{"accepted": n}`. Each source keeps its newest 100 events; expired ones are pruned every 5 minutes. `GET /events?source=` lists active events; `DELETE /events/{source}` and `DELETE /events/{source}/{key}` remove them.

### Battery actions (optional)
Config can declare `actions.battery`: `{ "above": 80, "webhook": "smart-plug-off" }`, `{ "below": 40, "webhook": "smart-plug-on" }`, webhook URLs referenced as secrets. Generic enough for any smart plug with an HTTP API.

## 6. `vigia` CLI and MCP server (packages/vigia)

TypeScript, Node 22+, published to npm.

```
vigia login <url> <token>          # saves to ~/.config/vigia
vigia status
vigia config get | set <file> | patch '<json>' | validate <file> | rollback | history
vigia widget add <type> --id x --pos 0,3,2,2 --param key=value
vigia widget rm <id>
vigia event send --source radar --title "New lead" --level info
vigia screenshot -o panel.png
vigia cmd brightness 0.3
vigia secret set <name>            # value from stdin
vigia types
vigia mcp                          # stdio MCP server
```

MCP tools: `get_config`, `list_widget_types`, `validate_config`, `patch_config`, `add_widget`, `remove_widget`, `send_event`, `get_status`, `screenshot` (returns image), `rollback`. Tool descriptions must tell the agent to validate before applying and to take a screenshot after.

## 7. Integrations

- **Claude Code hooks** (`integrations/claude-code`): small script wired to `SessionStart`, `Notification` and `Stop` events that POSTs `agents` events with `key = session_id`. Must never block or slow Claude Code: short timeout, fire and forget, silent on failure.
- **GitHub Actions**: polling via REST API with a fine-grained read-only token; optional webhook step example to push immediately.
- **Coolify / Netlify / Railway**: deploy webhooks → `POST /events` (`source: deploy`). Examples only, no first-party code in v1.
- **Upwork / leads**: Gmail filter + Apps Script example forwarding notification emails as `feed` events; the daily opportunities radar can POST directly.

## 8. Security

- Bearer token required on every route except a minimal `GET /health` (which reveals no data).
- Request body size limits, rate limit per IP, strict JSON validation.
- No secret is ever logged or returned.
- Kiosk exit: a hidden gesture + PIN on the device, and device owner removal documented.

## 9. Milestones

Work in small steps, each one reviewable and runnable.

- **M0 Scaffold** — Gradle project, modules, Hilt, theme, CI (lint + tests + build), license, README.
- **M1 Renderer** — load a bundled default config; grid layout; `clock`, `device`, `note` widgets; error tile for unknown types; 60 fps check on the device.
- **M2 Server** — foreground service, Ktor, token + QR setup screen, `/health`, `/config` (get/put/patch/validate/rollback/history), live UI update on config change.
- **M3 Sources** — `uptime` and `github_actions` pollers, secrets endpoint, `/status`.
- **M4 Events** — `/events`, `agents` and `feed` widgets, TTL, Claude Code hook script.
- **M5 CLI + MCP** — `packages/vigia`, MCP tools, `/screenshot`, `/widgets/types`.
- **M6 Kiosk** — device owner + lock task + HOME, boot start, night schedule, `/commands`, battery actions, kiosk setup guide.
- **M7 Release** — signed APK on GitHub Releases, F-Droid metadata, screenshots, contribution guide, `values-es`.

## 10. Open questions

- Multiple pages/screens swiped or rotated on a timer?
- Should the phone also expose its own tiles as actions (e.g. "re-run CI") or stay read-only in v1? Proposal: read-only in v1.
