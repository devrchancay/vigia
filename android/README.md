# Vigía — Android app

Kotlin, Jetpack Compose + Material 3, MVVM, Hilt, type-safe Navigation Compose, Timber.
minSdk 26, target/compile SDK 37. Milestone status: **M1 renderer, in progress** (see [`../docs/SPEC.md`](../docs/SPEC.md#9-milestones)).

## Requirements

- JDK 17 or newer (CI uses Temurin 21)
- Android SDK with platform 37. Point Gradle at it with `local.properties` (`sdk.dir=/path/to/sdk`) or `ANDROID_HOME`

## Run

```sh
./gradlew installDebug     # installs com.ramonchancay.vigia.debug on the connected device
./gradlew build            # ktlint + Android lint + unit tests + debug/release assemble
./gradlew test             # unit tests only (includes the Robolectric Compose UI test)
./gradlew ktlintFormat     # auto-fix code style
```

`build` is the bar for "done": Kotlin warnings and lint warnings are errors.

## Structure

```
app/                     Application, MainActivity (splash, edge-to-edge), VigiaNavHost
build-logic/convention/  Gradle convention plugins shared by every module
core/common              injected dispatchers (@Dispatcher), Result + asResult(), java.time.Clock binding
core/model               PanelConfig (kotlinx.serialization), PanelConfigParser, PanelConfigValidator
core/data                PanelConfigRepository; today it serves assets/panel/default_config.json
core/designsystem        VigiaTheme (ember palette, light/dark, optional dynamic color), PanelTile,
                         rememberNotificationPermissionState (Android 13+)
feature/panel            PanelScreen + PanelViewModel + ClockRepository: the pattern to copy
gradle/libs.versions.toml  every dependency version lives here
```

Modules from the spec that are not here yet arrive with the milestone that needs them, so nothing ships unused:
`core/device` (M1/M6), DataStore and config history in `core/data` (M2), `core/server` (M2, Ktor),
Room and OkHttp (M3), `feature/setup` (M2), `feature/sources` (M3).

### Convention plugins

| Plugin id | Applies |
|---|---|
| `vigia.android.application` | AGP app, SDK levels, Java 17, warnings as errors, ktlint |
| `vigia.android.library` | same for libraries + Robolectric-ready unit tests |
| `vigia.android.compose` | Compose compiler, BOM, Material 3, tooling |
| `vigia.hilt` | KSP + Hilt |
| `vigia.android.feature` | library + compose + hilt + navigation + lifecycle + test stack + `core:common`/`core:designsystem` |

## Adding a feature

1. Create `feature/<name>/build.gradle.kts`:
   ```kotlin
   plugins { alias(libs.plugins.vigia.android.feature) }
   android { namespace = "com.ramonchancay.vigia.feature.<name>" }
   ```
   and an empty `consumer-rules.pro`.
2. `include(":feature:<name>")` in `settings.gradle.kts` and `implementation(projects.feature.<name>)` in `app`.
3. Copy the shape of `feature/panel`:
   - `data/` repository interface + implementation; inject dispatchers with `@Dispatcher(VigiaDispatchers.IO)`, never `Dispatchers.IO`
   - `di/` Hilt module with `@Binds`
   - `<Name>UiState` (sealed) + `<Name>Action`, `<Name>ViewModel` exposing `StateFlow` and `onAction()`
   - `<Name>Route` (gets the ViewModel) and a stateless `<Name>Screen(uiState, onAction)` that is easy to preview and test
   - `navigation/` a `@Serializable` destination + `NavGraphBuilder.<name>Screen()`; register it in `VigiaNavHost`
4. Strings in `res/values` (English) and `res/values-es` (Spanish).
5. Tests: ViewModel with Turbine + MockK + `MainDispatcherRule`, screen with Robolectric + `createComposeRule` (v2).

## Release

Version lives in `gradle.properties` (`vigia.versionCode`, `vigia.versionName`). Bump both for every release.

1. Create a keystore once and keep it out of the repo (back it up; losing it means you cannot update the app):
   ```sh
   keytool -genkeypair -v -keystore release.jks -alias vigia -keyalg RSA -keysize 4096 -validity 10000
   ```
2. `cp keystore.properties.example keystore.properties` and fill it in (both files with secrets are gitignored).
3. Build:
   ```sh
   ./gradlew bundleRelease     # app/build/outputs/bundle/release/app-release.aab (Play Store)
   ./gradlew assembleRelease   # app/build/outputs/apk/release/app-release.apk (GitHub Releases / F-Droid)
   ```

Release builds run R8 (minify + resource shrinking). Without `keystore.properties` they are unsigned, which is what CI
produces. Release builds log nothing (Timber is planted only in debug).

Distribution: GitHub Releases and F-Droid are the primary channels (see `docs/CONTEXT.md`); device-owner kiosk apps
are a poor fit for Play review. The checklist below is for publishing a regular, non-kiosk build there.

### Play Console checklist

- [ ] Developer account verified (identity, and D-U-N-S for an organization account)
- [ ] App created; default language, app/game, free/paid set
- [ ] **New personal account: closed test with at least 12 testers opted in for 14 continuous days** before you can
      apply for production access. Start this early; testers must stay opted in for the full 14 days
- [ ] Play App Signing enabled; upload key = the keystore above
- [ ] Store listing: short and full description, 512×512 icon, 1024×500 feature graphic, at least 2 phone screenshots
- [ ] Privacy policy URL (required: the app runs a network server and requests notification permission)
- [ ] App content: Data safety form, ads declaration (none), content rating questionnaire, target audience
- [ ] Foreground service declaration for the `specialUse` type with a video/description of the use (from M2 on)
- [ ] Target API level meets the current Play requirement (target SDK 37 today)
- [ ] Internal testing → closed testing → production, raising `vigia.versionCode` on every upload
