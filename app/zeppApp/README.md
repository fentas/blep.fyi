# blep · ZeppOS (Amazfit) — find a thing by signal

A small, standalone **ZeppOS** port of blep's *find-by-signal* pointer for
Amazfit watches. Pick a nearby Bluetooth device and the watch turns into a
warm/cold guide — the screen flows **blue → yellow** and the haptics tick faster
as you close in, Geiger-counter style. Same RSSI→proximity tuning as the phone
and Wear/watchOS apps, reimplemented in JS (ZeppOS is its own runtime — nothing
from the Kotlin core carries over).

> **Find only.** This is the pointer, not the anti-tracking scanner. No
> background scans, no correlation — just "walk me to my thing."

## Requirements

- An Amazfit on **ZeppOS 3.0+** — the BLE scanner (`@zos/ble` `mstStartScan`,
  which is what gives us per-device RSSI) only exists from API level 3.0.
  - ✅ Known-good targets: **GTR 4 / GTS 4 / Cheetah** *after* the 3.0 OTA, plus
    Balance / Active 2 / T-Rex 3.
  - ❌ Older models (GTR/GTS 3, T-Rex 2, Bip) expose connect-only BLE — no
    advertisement scan — so this won't work there.
- The watch paired to the **Zepp** phone app, with **Developer Mode** enabled.

## Toolchain (via mise)

The ZeppOS toolchain is the **Zeus CLI** (an npm package). `mise` just pins Node
— Zeus is fussy about new Node majors, so this project pins **Node 20**.

```bash
cd app/zeppApp
mise install                 # Node 20
npm install                  # pulls @zeppos/zeus-cli locally
```

Scripts run Zeus from `node_modules/.bin` — `npm run dev` / `build` / `preview`.

> **`Cannot find module 'zeppos-app-utils'`** — Zeus loads a private module via
> `module-alias`, which reads the alias map from *this* project's `package.json`.
> That entry is already in here (`_moduleAliases`), so a plain `npm install` +
> `npm run dev` just works. (If you ever copy these sources into a bare project,
> carry the `_moduleAliases` block over, or install Zeus globally instead.)

## Build / run / flash

```bash
# Unit-test the signal engine — no watch needed
mise run test            # → 21 checks passed.

# Bundle a distributable .zab (validates app.json + all pages compile)
npm run build            # → dist/1318180-blep-<ver>-<stamp>.zab

# Install on the REAL watch: prints a QR — scan it in the Zepp app
# (Profile → Developer Mode → scan). Installs over the phone↔watch bridge.
npm run preview

# Live-reload against the ZeppOS DESKTOP SIMULATOR (must be running on :7650; no BLE)
npm run dev
```

`mise run preview` / `mise run dev` / `mise run test` wrap these too.

> **`dev` vs `preview`:** `preview` is the real-watch path (QR install). `dev`
> connects to the ZeppOS desktop simulator for hot-reload — if you see
> `connect simulator failed on http://127.0.0.1:7650`, that's `dev` with no
> simulator running; use `preview` for a physical watch.
>
> **Simulator can't do BLE.** The desktop simulator has no Bluetooth radio, so
> the device list stays empty and the tracker shows "signal lost" there. Layout
> renders fine; the real signal only happens on the watch.

## How it works

```
@zos/ble mstStartScan ──▶ {dev_name, dev_addr, rssi} per advertisement
        │
        ▼  libs/ble.js      (MAC normalise)
        ▼  libs/signal.js   EMA(α=0.45) ▶ proximity = clamp((rssi − −90)/(−58 − −90))
        ▼
   pages/index.js   list strongest named devices, tap to pick
   pages/track.js   full-screen warm/cold fill + proximity arc + % + Geiger haptic
```

The tuning constants live in `libs/signal.js` and mirror the phone's
`TrackingTuning.kt` exactly:

| Constant | Value | Meaning |
|----------|-------|---------|
| `RSSI_FAR` | −90 dBm | proximity 0.0 |
| `RSSI_NEAR` | −58 dBm | proximity 1.0 (point-blank) |
| `EMA_ALPHA` | 0.45 | smoothing |
| `PINPOINT_PROXIMITY` | 0.78 | "almost on it" |
| `COMPLETE_PROXIMITY` | 0.94 | "right here" |

Colours are the same blue→teal→green→yellow stops as the Wear/watchOS apps; the
haptic cadence is the same `1200 → 110 ms` ramp as `HapticCadence.kt`.

## Files

```
app.json            ZeppOS manifest — perms device:os.ble, targets gtr(round)/gts(square)
app.js              App() shell
libs/signal.js      pure engine: EMA, proximity, colour, haptic cadence, distance
libs/ble.js         @zos/ble scan wrapper + ArrayBuffer→MAC
pages/index.js      device picker (live RSSI, strongest first)
pages/track.js      the warm/cold pointer + haptics
test/signal.test.mjs  Node unit test for the engine
assets/gtr.r, gts.s   per-target app icon
```

## Caveats / nice-to-haves

- Devices using a **rotating (random) MAC** show up by their MAC tail rather than
  a name, and a rotation looks like the device "vanishing" — fine for finding
  *your* thing (pick it while it's strong), just don't expect persistence.
- No motion sensors are used — it's pure signal (no turn-by-turn arrow like the
  phone). RSSI alone is enough for warmer/colder; a compass arrow could be a fun
  follow-up via `@zos/sensor`.
- `appId` is a placeholder; set your own before any real distribution.
