# CLAUDE.md

Vigía: an open-source Android app that turns an old phone into an always-on, remotely configurable status panel (uptime, CI, Claude Code agents, leads). It embeds its own HTTP server and is controlled from a CLI/MCP server.

**Read first:** `docs/CONTEXT.md` (why) and `docs/SPEC.md` (what). The project is at the design stage; start at milestone M0 in the spec.

## Working agreement
- The owner is Ramon (Spanish speaker). Talk to him in Spanish, concise and direct. Code, comments, commits, docs and identifiers in English. User-facing strings: English default + `values-es`.
- Small incremental steps with a pause for review between them. One milestone at a time, and inside a milestone, one coherent change per commit.
- Before writing any dependency version, check the latest stable release. Do not guess versions.
- No filler code, no unexplained TODOs. If something is deferred, say where it is tracked.
- Keep the app device-agnostic. The reference device is a debloated Redmi 9C (Helio G35, 2–3 GB RAM, Android/MIUI); every UI change should respect the performance budget in the spec.
- Open source hygiene: no secrets, tokens, personal URLs or IPs in the repo. Example configs use `example.com` and `owner/repo`.

## Android conventions
- Kotlin, Compose + Material 3, MVVM (ViewModel + StateFlow, UiState + events), Hilt, Room, DataStore, kotlinx.serialization, OkHttp, Ktor server (CIO), Timber.
- Gradle Kotlin DSL + version catalog (`gradle/libs.versions.toml`). minSdk 26, latest stable target/compile SDK.
- Package by feature (`core/*`, `feature/*`) as in the spec. Dispatchers always injected.
- Release: R8 (minify + shrinkResources), signing from `keystore.properties` (gitignored, with `.example`), `applicationIdSuffix ".debug"` for debug.
- Quality: ktlint or detekt, unit tests for ViewModels and config validation (JUnit, Turbine, MockK, coroutines-test), one Compose UI test, GitHub Actions running lint + tests + build on every PR.
- `./gradlew build` must pass with no warnings before a milestone is considered done.

## TypeScript conventions (packages/vigia)
- Node 22+, strict TypeScript, ESM. Small dependency surface. Tests with Vitest.

## Useful commands (once scaffolded)
```
cd android && ./gradlew build
cd android && ./gradlew installDebug
adb shell dpm set-device-owner com.ramonchancay.vigia/.kiosk.VigiaDeviceAdminReceiver
cd packages/vigia && npm test
```
