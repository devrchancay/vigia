# Vigía

Turn an old Android phone into an always-on status panel for your desk, and change it remotely, from a CLI or by asking your AI agent.

> **Status: M0 scaffold.** The Android project builds and shows a clock; the panel itself starts in M1. See [docs/SPEC.md](docs/SPEC.md) and [android/README.md](android/README.md).

## What it does

- Shows what matters while you work: site/API uptime, CI runs, deploys, your Claude Code agents (working, waiting for permission, done) and any feed you push to it (leads, client messages).
- Runs its own small HTTP server on the phone, so there is nothing else to host. Reach it over Tailscale.
- The whole layout is a JSON config. Add, move or remove widgets and switch themes remotely with `vigia` CLI, plain `curl`, or the built-in MCP server ("add an uptime widget for my API and make it green").
- Kiosk mode: starts on boot, keeps the screen on, dims at night.
- Works on any Android 8.0+ device. Reference device: a debloated Xiaomi Redmi 9C.

## Why

Inspired by [Fósil](https://x.com/is_lu_is/status/2118718044482699497), a custom Linux UI on a 2015 Moto E. Vigía takes the same "give the old phone a job" idea, keeps Android underneath so it runs on almost any phone without flashing, and makes it programmable. The reasoning is in [docs/CONTEXT.md](docs/CONTEXT.md).

## Roadmap

M0 scaffold → M1 renderer → M2 embedded server and config API → M3 uptime and GitHub Actions → M4 events and Claude Code hooks → M5 CLI and MCP → M6 kiosk hardening → M7 first release (GitHub Releases, F-Droid).

## License

[MIT](LICENSE)
