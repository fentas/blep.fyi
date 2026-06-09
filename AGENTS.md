# AGENTS.md

Orientation for AI agents (and humans) working in this repo. Keep it accurate — update it when the structure or release flow changes.

## What blep is

A Kotlin Multiplatform BLE finder + anti-tracking tool. One shared core drives four apps:
- **Android phone** — Compose Multiplatform
- **Wear OS** — Wear Compose
- **iOS** — SwiftUI (`iosApp/`) linking a Kotlin framework
- **Apple Watch** — SwiftUI (`watchApp/`) linking a Kotlin framework
- Plus the **web** PWA/landing at `blep.fyi` (`web/`)

The product story and architecture deep-dive live in `README.md` — read it for the body-shielding technique, the RSSI phase machine, and the sensor-fusion spatial layer.

## Gradle module layout (post AGP 9→10 migration)

The Gradle project is under `app/`. **The Android phone app is `:androidApp`, NOT `:composeApp`.**

- **`:core`** — KMP library (`com.android.kotlin.multiplatform.library`). Pure tracking/safety logic + the `BlepCore` iOS/watchOS framework. No platform UI. Heavily unit-tested (`core/src/commonTest`).
- **`:composeApp`** — KMP **library** (`com.android.kotlin.multiplatform.library`). The shared Compose UI (`commonMain`), Android platform glue (`androidMain`: the background-scan `Service`, context provider, `expect`/`actual`), and it produces the **`ComposeApp` iOS framework** that `iosApp/` links. Namespace `fyi.blep.shared`.
  - ⚠️ **Load-bearing:** `android { androidResources.enable = true }`. The new KMP-library plugin disables the Android resource pipeline by default; without this, Compose resources (`.cvr`) don't package into the APK and the app crashes at the first `stringResource()` (`MissingResourceException`).
- **`:androidApp`** — the phone app (`com.android.application`, AGP built-in Kotlin). Launcher `MainActivity`, app manifest, signing, versionCode/Name, R8, app icons/themes. **Release artifact:** `app/androidApp/build/outputs/bundle/release/androidApp-release.aab`.
- **`:wearApp`** — the Wear OS app (`com.android.application`, built-in Kotlin).
- `iosApp/`, `watchApp/` — Swift apps (XcodeGen `project.yml`); they link the Kotlin frameworks via `embedAndSignAppleFrameworkForXcode`. The migration left these untouched.

Kotlin uses AGP 9 built-in Kotlin + the new DSL (no `org.jetbrains.kotlin.android`; no `android.builtInKotlin=false`/`newDsl=false`).

## Build / run / test

Use the Makefile — `make help` lists every target. Common ones:

| Task | Command |
| --- | --- |
| List targets | `make help` |
| Unit tests (core) | `make test` |
| Install + run on a device | `make run` |
| Install + run in **demo mode** (scripted data, no BLE/sensors — for screenshots/QA) | `make demo` |
| Build debug APK / release AAB | `make apk` / `make aab` |
| Tracking simulations / chaos / robustness | `make sim` / `make chaos` / `make robustness` |
| Boot the screenshot emulator | `make emulator` |

Gradle directly: `cd app && ./gradlew <task>` (Java 21, `mise` toolchain). CI (`.github/workflows/ci.yml`) runs `:core:jvmTest`, `:androidApp:assembleDebug :wearApp:assembleDebug`, the iOS/watchOS Kotlin compiles, and the Swift apps on macOS.

## Release (Google Play)

Closed-test tracks; **dry-run unless `PLAY_COMMIT=1`**. Needs `sa.json` (service-account, gitignored) and `app/keystore.properties` + `app/upload-keystore.jks` (gitignored).

- Phone → **`alpha`** track, versionCode band **1xxx** (in `:androidApp`). Wear → **`wear:blep`** track, band **2xxx** (in `:wearApp`). Same `applicationId fyi.blep`, one listing.
- `make release PLAY_COMMIT=1` — bump versionCode → build signed AABs → upload + release.
- `make publish-listing` / `make publish-store` — push store text (+ regen screenshots). `make ship` — everything.
- `make release-notes` drafts the English "what's new" (`store/release-notes/en-US.txt`, gitignored) from `feat:`/`fix:` commits since the last `play/*` tag; translate the other locales, then `make release` passes `--notes-dir`. **Tag `git tag play/<versionCode>` after each upload.**

## Conventions & gotchas

- **Commits:** conventional commits (`feat(scope): …`); end the body with `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`. Don't push or touch Play unless asked.
- **NEVER commit** `sa.json`, `keystore.properties`, or `*.jks` (all gitignored).
- **Strings:** Compose UI strings live in `composeApp/src/commonMain/composeResources` (14 locales). **composeResources use plain apostrophes (`'`)**; any `androidMain/res` strings use AAPT escaping (`\'`). Background-scan notifications read from composeResources (suspend `getString`) — no Android `R`.
- **Kotlin pitfall:** a `var x` generates `setX()`, which clashes with a `fun setX()` ("platform declaration clash") — rename the function (e.g. `selectTheme`, not `setThemeMode`).
- **Dark theme:** neutral screens route background/text/surface through `MaterialTheme.colorScheme.*` (flip); brand colors + the warm/cold proximity colors are fixed. `BlepColors.proximity(f, dark = true)` is the muted ramp for dark surfaces.
- **Screenshots:** `tools/screenshots-i18n.sh` needs `ANDROID_SERIAL=emulator-5554` when both the phone and wear emulators are running (bare `adb` otherwise errors "more than one device"). It captures 6 captioned screens/locale (incl. a dark-theme shot) by switching the emulator's system language.
- **iOS verification:** the iOS Xcode final link is confirmed only by CI (macOS); a Linux box can verify the Kotlin/native compile + the framework but not the Swift link.
