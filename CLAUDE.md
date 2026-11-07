# CLAUDE.md

Vigía: an open-source Android app that turns an old phone into an always-on, remotely configurable status panel (uptime, CI, Claude Code agents, leads). It embeds its own HTTP server and is controlled from a CLI/MCP server.

**Read first:** `docs/CONTEXT.md` (why) and `docs/SPEC.md` (what). Status: M0 (scaffold), M1 (renderer), M2 (embedded server, config API, setup screen) and M3 (uptime and GitHub Actions sources, secrets, `/status`) are merged (`android/`, see `android/README.md`); next is milestone M4 (events, `agents` and `feed` widgets, Claude Code hooks) in the spec.

## Working agreement
- The owner is Ramon (Spanish speaker). Talk to him in Spanish, concise and direct. Code, comments, commits, docs and identifiers in English. User-facing strings: English default + `values-es`.
- Small incremental steps with a pause for review between them. One milestone at a time, and inside a milestone, one coherent change per commit.
- `main` is protected: every change goes through a PR from a branch, CI (`build` job) must pass, linear history (squash or rebase).
- Before writing any dependency version, check the latest stable release. Do not guess versions.
- No filler code, no unexplained TODOs. If something is deferred, say where it is tracked.
- Keep the app device-agnostic. The reference device is a debloated Redmi 9C (Helio G35, 2–3 GB RAM, Android/MIUI); every UI change should respect the performance budget in the spec.
- Open source hygiene: no secrets, tokens, personal URLs or IPs in the repo. Example configs use `example.com` and `owner/repo`.

## Android conventions
- Kotlin, Compose + Material 3, MVVM (ViewModel + StateFlow, UiState + events), Hilt, Room, DataStore, kotlinx.serialization, OkHttp, Ktor server (CIO), Timber.
- Gradle Kotlin DSL + version catalog (`gradle/libs.versions.toml`). minSdk 26, latest stable target/compile SDK.
- Package by feature (`core/*`, `feature/*`) as in the spec. Dispatchers always injected. Add a module or library only with the milestone that uses it.
- Build logic lives in convention plugins (`android/build-logic`): `vigia.android.application`, `vigia.android.library`, `vigia.android.compose`, `vigia.android.feature`, `vigia.hilt`. New features copy the shape of `feature/panel` (steps in `android/README.md`).
- Release: R8 (minify + shrinkResources), signing from `keystore.properties` (gitignored, with `.example`), `applicationIdSuffix ".debug"` for debug.
- Quality: ktlint, unit tests for ViewModels and config validation (JUnit, Turbine, MockK, coroutines-test), Compose UI tests on Robolectric (run in `test`, no emulator), GitHub Actions (`.github/workflows/android.yml`) running lint + tests + build on every PR.
- `./gradlew build` must pass with no warnings before a milestone is considered done. Kotlin and Android lint warnings are already configured as errors.

## TypeScript conventions (packages/vigia)
- Node 22+, strict TypeScript, ESM. Small dependency surface. Tests with Vitest.

## Useful commands
```
cd android && ./gradlew build          # ktlint + lint + unit tests + debug/release
cd android && ./gradlew ktlintFormat
cd android && ./gradlew installDebug
cd android && ./gradlew bundleRelease  # AAB, signed only if keystore.properties exists
```
Later milestones (not available yet):
```
adb shell dpm set-device-owner com.ramonchancay.vigia/.kiosk.VigiaDeviceAdminReceiver   # M6
cd packages/vigia && npm test                                                            # M5
```
