# Context and decisions

This file records where Vigía came from and why the main decisions were made, so anyone (human or agent) picking up the project does not have to rediscover them.

## Origin

- Inspiration: Luis Yáñez ([@is_lu_is](https://x.com/is_lu_is/status/2118718044482699497)) wiped Android from a 2015 Moto E (Snapdragon 410, 1 GB RAM, 540×960), installed Linux and had Claude build "Fósil": a single C program (161 KB binary) that draws a status UI with the phone's GPU at 60 fps, using ~230 MB of RAM. The UI shows clock, battery, memory, Wi-Fi, Tailscale and tiles for Terminal, System, Network, Chat, Settings and Power.
- Goal here: the same idea (an old phone as an always-on status screen on the desk), but useful for a developer's daily work and **remotely configurable**: the owner (or an AI agent like Claude Code) sends instructions and the panel changes its widgets, layout and theme without touching the phone.

## Reference device

The first target is a **Xiaomi Redmi 9C** (codename `angelica`):

- MediaTek Helio G35 (MT6765G), 8× Cortex-A53, PowerVR GE8320 GPU
- 2–3 GB RAM, slow eMMC storage, 720×1600 IPS LCD, 5000 mAh battery
- Running MIUI, already **debloated via ADB** (`pm uninstall --user 0` of MIUI ads/analytics, Facebook services, unused Google apps) with animations at 0.5x
- On the owner's Wi-Fi and Tailscale network

The app must not depend on this device: any Android 8.0+ (API 26) phone or tablet should work.

## Decisions

### Native Android app (Kotlin) instead of Linux + C

Options considered:

1. **Linux + C/LVGL (Fósil style).** postmarketOS has a "testing" port for `angelica`, installable with mtkclient. But there is no usable open-source driver for the PowerVR GE8320, ports run on the downstream MediaTek kernel, and the UI would be software-rendered. High risk (nvram/IMEI loss if partitions are not backed up), lots of effort, weak result on this chip. The Moto E worked so well because MSM8916 has excellent mainline support (freedreno).
2. **Web page in a kiosk browser.** Fastest to iterate, but needs a server elsewhere and a third-party kiosk browser.
3. **Native Kotlin app in kiosk mode.** ✅ Chosen. Hardware-accelerated rendering through Android, no flashing, survives reboots, and it doubles as a portfolio/open-source Android project.

### The server lives inside the app

The owner's Mac sleeps, so it cannot be the hub. Running Node/Fastify in Termux on the phone was discarded (MIUI kills Termux, needs Termux:Boot and wake locks, two things to maintain).

✅ Chosen: an **embedded HTTP server (Ktor) inside the same app**, running in a foreground service. One APK, one process. It polls sources (uptime, GitHub Actions), receives pushed events (Claude Code hooks, CI, scripts) and exposes the remote-control API.

### Always on

- Screen kept on by the app (`FLAG_KEEP_SCREEN_ON`). With the screen on, Doze does not kick in, so in-app polling works without tricks.
- Real kiosk: app set as **device owner** via ADB + **lock task mode** + registered as **HOME launcher**, so it starts after reboot and MIUI does not kill it.
- MIUI: battery "No restrictions" and autostart enabled.
- Night schedule lowers brightness (LCD, so no burn-in).
- **Battery risk:** the Redmi 9C cannot limit charging without root, and being at 100% 24/7 can swell the battery over months. Mitigation: a smart plug on a schedule, or a generic webhook action the app fires at battery thresholds (see SPEC). Show battery temperature on the panel and warn if it is high.

### What to show (v1)

The owner picked all four:

1. Uptime of sites/APIs (own projects and clients' sites)
2. Claude Code agents: finished, waiting for permission, working
3. Deploys and CI (GitHub Actions first; Coolify/Netlify/Railway via webhooks)
4. Upwork and leads

### Upwork limitation

Upwork has no simple public API (the official GraphQL API needs an approved OAuth app). v1 handles it through **generic pushed events**: e.g. a Gmail filter + Apps Script forwarding Upwork notification emails, or the owner's existing daily "opportunities radar" job POSTing to the panel. No Upwork scraping.

### Remote instructions

The panel's entire look is a **JSON config** stored on the phone. Remote changes go through the HTTP API (over Tailscale), wrapped by:

- `vigia` CLI (TypeScript, npm)
- an MCP server, so Claude Code can do "add an uptime widget for pet-meet and switch to the green theme" in natural language
- a screenshot endpoint so the agent can check the result

### Open source

- License: MIT
- No proprietary dependencies (no Firebase, no Play Services) so it can ship on GitHub Releases and F-Droid
- Device-owner kiosk apps are a poor fit for Play Store review; GitHub Releases/F-Droid are the primary channels
- Code, comments and docs in English. Spanish UI strings are welcome as a translation (`values-es`)
