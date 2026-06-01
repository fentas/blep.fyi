<h3 align="center">
	<img width="130" alt="blep mascot" src="./logo.svg">
	<br/>
	blep
</h3>

<h6 align="center">
  <a href="https://blep.fyi">Website</a>
  ·
  <a href="#-how-it-works--the-body-shielding-technique">How it works</a>
  ·
  <a href="#-building">Build</a>
  ·
  <a href="https://blep.fyi/#donate">Donate</a>
</h6>

<p align="center">
	<a href="https://github.com/fentas/blep.fyi/stargazers">
		<img alt="Stars" src="https://img.shields.io/github/stars/fentas/blep.fyi?style=for-the-badge&logo=starship&color=C9CBFF&logoColor=D9E0EE&labelColor=302D41"></a>
	<a href="https://github.com/fentas/blep.fyi/blob/main/LICENSE">
		<img alt="License" src="https://img.shields.io/github/license/fentas/blep.fyi?style=for-the-badge&color=F2CDCD&logoColor=D9E0EE&labelColor=302D41"></a>
	<a href="https://github.com/fentas/blep.fyi/actions/workflows/ci.yml">
		<img alt="CI" src="https://img.shields.io/github/actions/workflow/status/fentas/blep.fyi/ci.yml?branch=main&style=for-the-badge&logo=githubactions&label=CI&color=A6E3A1&logoColor=D9E0EE&labelColor=302D41"></a>
	<a href="https://blep.fyi">
		<img alt="Site" src="https://img.shields.io/github/actions/workflow/status/fentas/blep.fyi/deploy.yml?branch=main&style=for-the-badge&logo=githubpages&label=blep.fyi&color=89DCEB&logoColor=D9E0EE&labelColor=302D41"></a>
	<a href="https://kotlinlang.org/docs/multiplatform.html">
		<img alt="Kotlin Multiplatform" src="https://img.shields.io/badge/Kotlin-2.1-CBA6F7?style=for-the-badge&logo=kotlin&logoColor=D9E0EE&labelColor=302D41"></a>
</p>

<p align="center">
	<img alt="Platforms" src="https://img.shields.io/badge/iOS%20·%20Android%20·%20Wear%20OS%20·%20watchOS-89B4FA?style=for-the-badge&logoColor=D9E0EE&labelColor=302D41">
</p>

&nbsp;

<p align="center">
	<img src="./assets/flow.svg" width="240" alt="blep flow: pick a device, track it warm/cold, found it" />
</p>

&nbsp;

<p align="left">

**blep** turns your phone — or your watch — into a warm/cold pointer for the
Bluetooth Low Energy devices already around you. Pick a device and blep walks
you to it: a single animated arrow and a background colour that shifts from cool
to warm as you close in. No extra hardware, no maps, no accounts.

It's a minimalist, cross-platform [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html)
app (iOS, Android, Apple Watch, Wear OS) plus a small static PWA landing page at
[blep.fyi](https://blep.fyi).

</p>

&nbsp;

### 🐾 How it works — the body-shielding technique

A phone's BLE radio is roughly omnidirectional, so raw RSSI tells you *how far*
but not *which way*. blep makes it directional using your own body:

> Hold the phone flat against your chest. Your torso absorbs ~10–20 dB of 2.4 GHz
> signal, so the reading is strongest when the target is **in front of you**.

From there it's a guided loop driven entirely by the change in signal (ΔRSSI):

| Step | Phase | What you do | blep says |
| ---- | ----- | ----------- | --------- |
| 1 | **Calibrate** | Hold at your chest, stay still | *Hold at your chest* |
| 2 | **Axis sweep** | Turn slowly in place | *Warmer · turn back* |
| 3 | **Vector walk** | Walk forward | *Keep going · stop* |
| 4 | **Reorient** | Re-sweep as needed | *Turn again* |
| 5 | **Pinpoint** | Kneel, search low | *Almost there* |
| 6 | **Done** | 🎉 | *Finished — congratulations!* |

RSSI is noisy and multipath-prone, so this is an **assistive heuristic, not a
precise locator**. Every threshold lives in
[`TrackingTuning`](app/core/src/commonMain/kotlin/fyi/blep/core/tracking/TrackingTuning.kt)
and is unit-tested.

&nbsp;

### 🗂 Repository layout

```
.
├── app/                      Kotlin Multiplatform project
│   ├── core/                 Pure tracking logic + BLE scanner (shared, tested)
│   │   └── src/
│   │       ├── commonMain/   RssiFilter · SignalTrend · DeviceTable ·
│   │       │                 TrackingSession · BleScanner (expect)
│   │       ├── commonTest/   JVM-runnable unit tests
│   │       ├── kableMain/    Kable scanner (Android + Apple share this)
│   │       └── jvmMain/      Fake scanner for tests/preview
│   ├── composeApp/           Compose Multiplatform phone UI (Android + iOS)
│   ├── wearApp/              Wear OS app (Wear Compose)
│   ├── iosApp/               SwiftUI shell for iPhone (XcodeGen)
│   └── watchApp/             SwiftUI watchOS app (uses shared BlepCore)
├── web/                      Vite + Tailwind PWA for blep.fyi
├── design/tokens.md          Design tokens shared by app + web
└── .github/workflows/        CI (tests + cross-platform compile) + Pages deploy
```

The `core` module is deliberately **free of platform and UI dependencies** so the
tracking logic is provable on a plain JVM. The Compose phone app and the Wear app
each provide a thin state holder (`BlepController` / `WearController`) over the
same `TrackingSession`; the watchOS app does CoreBluetooth in Swift and feeds RSSI
into that same Kotlin engine — no Flow bridging.

&nbsp;

### 🏗 Architecture

```
        ┌─────────────────────── :core (commonMain, pure) ───────────────────────┐
        │  RssiFilter (EMA, ΔRSSI) → SignalTrend (peak/drop) → TrackingSession    │
        │  DeviceTable (dedup/sort/TTL)        BleScanner (expect)  TrackingStatus │
        └───────▲───────────────────────────────────▲─────────────────▲──────────┘
                │ actual (Kable)                     │ actual (Kable)   │ actual (fake)
            androidMain ─┐                       appleMain ─┐         jvmMain
                         └── kableMain (shared) ◄───────────┘
   ┌─────────────┴───────────┐     ┌────────────┴───────────┐   ┌──────┴───────┐
   │ composeApp (Android+iOS)│     │ wearApp (Wear Compose) │   │ watchApp     │
   │ BlepController + screens│     │ WearController         │   │ SwiftUI +    │
   └─────────────────────────┘     └────────────────────────┘   │ BlepCore     │
                                                                 └──────────────┘
```

&nbsp;

### 🔧 Building

The toolchain is pinned with [mise](https://mise.jdx.dev) (`mise.toml`):
JDK 21 + Gradle. Run `mise install` once, or bring your own JDK 21.

**Core unit tests** — no Android SDK needed:

```bash
cd app
./gradlew :core:jvmTest -Pblep.android=false
```

`-Pblep.android=false` skips the Android targets so the pure logic builds and
tests anywhere. CI runs the full matrix.

**Android (phone + Wear)** — needs the Android SDK (`ANDROID_HOME` or
`app/local.properties`):

```bash
cd app
./gradlew :composeApp:assembleDebug   # phone APK
./gradlew :wearApp:assembleDebug      # Wear OS APK
```

Min SDK 26 (phone) / 30 (Wear OS 3). BLE permissions are requested at runtime.

**iOS / watchOS** — needs macOS + Xcode + [XcodeGen](https://github.com/yonaskolb/XcodeGen):

```bash
cd app/iosApp   && xcodegen generate && open iosApp.xcodeproj      # iPhone
cd app/watchApp && xcodegen generate && open watchApp.xcodeproj    # Apple Watch
```

A pre-build phase compiles the shared Kotlin framework via Gradle; the
`Info.plist`s declare `NSBluetoothAlwaysUsageDescription`.

**Website:**

```bash
cd web
npm install
npm run dev        # local dev server
npm run build      # static output in web/dist
npm run gen:icons  # regenerate PWA icons from logo.svg (only if it changes)
```

Deployed to GitHub Pages by `.github/workflows/deploy.yml` on push to `main`. The
Pages source and `blep.fyi` custom domain are set in repo settings, so no `CNAME`
file is committed.

&nbsp;

### 📱 Platform support

| Platform        | Status | Notes                                             |
| --------------- | :----: | ------------------------------------------------- |
| Android phone   | ✅ | Compose Multiplatform                                  |
| iOS (iPhone)    | ✅ | Compose Multiplatform in a SwiftUI shell               |
| Wear OS         | ✅ | Wear Compose, standalone                               |
| Apple Watch     | ✅ | SwiftUI + shared `BlepCore`                            |
| Zepp OS         | ❌ | Zepp OS mini-apps have no general third-party BLE central/scan API, which the tracking technique requires. |

&nbsp;

### ♥ Donations

The website `#donate` section (the target of the app's *Help & donate* button)
supports **Stripe, Open Collective, PayPal and GitHub Sponsors**, each with a
downloadable receipt. The provider URLs in [`web/index.html`](web/index.html) are
placeholders marked `REPLACE_ME` — set your Stripe Payment Link, Open Collective
slug, and PayPal hosted-button id.

&nbsp;

### 🧪 Testing & CI

`:core` ships JVM unit tests for the RSSI filter, trend detector, device table and
the full tracking state machine (calibration → … → completion, plus re-aim and
stale-eviction edge cases). [CI](.github/workflows/ci.yml) additionally compiles
the Android, iOS and watchOS targets to catch platform API regressions.

&nbsp;

### 📄 License

[MIT](LICENSE) © Jan Guth
