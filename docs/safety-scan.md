# Safety scan — "is something tracking *me*?"

The anti-tracking mirror of blep's hunt: detect an unwanted Bluetooth tracker
(AirTag, Tile, SmartTag, a Find My / DULT beacon, or an anonymous rotating-MAC
device) travelling **with you**, warn, and — blep's unique edge — **point you to
it** so you can find and remove it.

This roughly 10×'s the audience: everyone worried about being followed, not just
people who lost something.

## Two fused detection signals

1. **By protocol (single scan).** A tracker *separated from its owner* advertises
   a recognisable type — Apple Find My (`0x004C` offline-finding), the Google /
   Apple **DULT** "accessory not with owner" service (shipped 2024), Tile
   (`0xFEED`), Samsung SmartTag, Chipolo/Pebblebee. Its identity rotates, but the
   *kind* persisting close by is the tell, so the detector accumulates close
   presence **per kind** — rotation-proof.

2. **By rotation pattern.** The un-correlation *is* the correlation: a continuous
   close presence carried by a *churn of short-lived random addresses* (one
   vanishing as the next appears, all at ~the same range) is the fingerprint of a
   privacy-rotating tracker shadowing you. This catches the **pre-DULT / third-
   party / cheap** trackers that type detection misses. A couple of distinct
   passers-by don't trip it (needs sustained close coverage + many distinct IDs).

Severity: a *separated tracker close by* → **WARN**; *close & present for minutes*
→ **ALERT** ("may be following you"), with an address to hand the finder.

## What's built (v1)
- `core/.../safety/TrackerDetector.kt` — pure, deterministic detector + tunables
  (close dBm, "nearby"/"following" durations, rotation churn/coverage thresholds).
- `core/.../safety/Advert.kt` — `RawAdvert` + `TrackerClassifier` (manufacturer
  data + service UUIDs → `TrackerKind`/`separated`; `addressType` → `randomAddress`).
- `core/.../safety/SafetyScanner.kt` — raw-advert stream → classify → detect →
  `Flow<List<TrackerAlert>>`; enriches alerts with cross-session context.
- **Platform scan plumbing.** `BleScanner.advertisements(): Flow<RawAdvert>`:
  - **Android** (`AndroidBleScanner`) — unfiltered low-latency scan; maps the
    `ScanResult` (manufacturer data, short service UUIDs, best-effort address type).
  - **iOS** (`KableScanner`, CoreBluetooth via Kable) — foreground/on-open; Apple
    never exposes the MAC, so every identifier is an OS-scoped UUID marked RANDOM
    (the churn of distinct nearby identifiers *is* the rotation tell).
- **Cross-session memory (v2 history).** `SafetyHistory` + `KeyValueStore`
  (SharedPreferences / NSUserDefaults): **on by default** (the log never leaves the
  device, so there's no privacy cost — and it's the feature's edge; users can turn
  it off, which clears it). Persists a rolling log of close encounters (tracker
  *kind* + time, plus a **coarse place cell** when location-aware detection is on —
  no identity, no raw coordinates), and promotes a kind seen across **3+ separate
  hours** — and, with location on, across **distinct places** (`distinctPlaces` /
  `CrossSession.multiPlace`) — to a full ALERT. 7-day retention (~17 KB cap; storage
  is a non-issue), throttled to ≤1 record/kind/5 min.
- **Background watching (opt-in, off by default).** `AppSettings` exposes a
  **foreground service** (keep the scan alive when you leave the app) and a
  **periodic WorkManager scan** while the app is closed (interval 15–240 min,
  default 30), both wired through `BackgroundScan` (`expect`/`actual`) with a
  notification on a new alert. Background passes deliberately **don't actively
  range your own paired devices** (battery). Permission (notifications / location)
  is requested on activation.
- **Sensitivity profiles.** `ScanSensitivity` (RELAXED / BALANCED / STRICT) maps to
  `TrackerTuning` presets, surfaced both in Settings and on the safety screen via a
  tap-to-expand selector.
- **Location-aware detection (opt-in, off by default).** `coarsePlaceCell()`
  (`PlaceProvider`, `expect`/`actual`) buckets a coarse last-known fix into a ~2 km
  grid cell, feeding the distinct-places promotion above. Strictly on-device,
  permission-gated, never the finder (GPS is net-negative there — see `finding.md`).
- **"It's mine" mute.** Each alert offers *It's mine* → persists the address to a
  mute list (`SafetyHistory`); the scanner then feeds neither detection signal from
  it and filters it out of results. Permanent for stable-MAC trackers; a tag that
  *rotates* its address reappears under a new one (that rotation is the very thing
  the detector catches), so the copy is honest about it. The mute list survives the
  "Remember" toggle being turned off.
- **UI.** Discovery "🛡️ Is something tracking you?" entry → `SafetyScreen`: live
  list of suspected trackers (kind, severity, signal) → **Find it** (reuses the
  tracking engine) + **It's mine** (mute) + the "Remember across sessions" toggle
  (on by default).
- Tests (37): `TrackerDetectorTest` (9), `TrackerClassifierTest` (6),
  `SafetyHistoryTest` (13 — incl. mute round-trip/cap, corrupt-log tolerance,
  entry-cap), `SafetyScannerTest` (9 — full pipeline scenarios: following AirTag,
  brief Tile, rotation churn, far device, mute silences/unmute re-flags/keeps-others,
  cross-session promote/disabled).
- **Emulator integration check** (`tools/ble-netsim/`). The Android emulator (33+/
  API 31+) has a virtual Bluetooth stack via **netsim**; Bumble injects fake adverts
  over its gRPC. Confirmed live that the *real* `AndroidBleScanner.advertisements()`
  → classifier flags an injected Find My beacon ("Unknown Find My tracker nearby") —
  i.e. the Android `ScanResult` parse path works, not just the hand-built `RawAdvert`.

## Remaining work
- **Fingerprint-vs-real-hardware.** netsim/Bumble validates the *parse path* but not
  the fingerprints themselves (it replays the same byte assumptions the classifier
  holds). The classifier was since tightened — Find My is TLV-walked and length-aware
  (`0x12` len `0x19` = separated vs `0x02` = with-owner), and the over-broad Samsung
  company-id (`0x0075`) match was dropped in favour of the `0xFD5A` SmartTag UUID — but
  `0xFEAA` (Eddystone/Google FMDN) and the exact SmartTag/Google payloads still want
  one confirmation against real tags. Checklist + expected results:
  **[`docs/tracker-validation.md`](tracker-validation.md)** (and `make ble-trackers`
  now injects the with-owner, Google/DULT, and Samsung-phone negative cases too).
- **Continuous background scan — DONE (opt-in).** Shipped as a foreground service +
  periodic WorkManager scan (see "What's built"). Kept off by default and permission-
  gated to avoid forcing Play's background-location review on users who don't want
  it; the coarse-location path uses foreground/`ACCESS_COARSE_LOCATION` rather than
  `ACCESS_BACKGROUND_LOCATION`. Remaining: measure detection quality in the periodic
  (short-window) mode vs. the continuous foreground service — motion signal differs.
- **"Across places" (location) correlation — DONE (opt-in).** Cross-session now
  counts distinct **coarse places** alongside hours (see "What's built"), gated
  behind its own explicit, off-by-default location consent. Remaining: the richer
  context-diversity model in `detection.md` (movement segments × co-present-set
  fingerprint), beyond the simple ~2 km place cell.
- **iOS** background BLE is heavily restricted and Find My is OS-reserved; iOS stays
  **foreground / on-open only** by design. The OS already does native unwanted-
  tracker alerts — blep still adds the *find-it* step + the manual/rotation scan.

## Notes
- This is a **safety** feature: strictly local/on-device, no new data leaves the
  phone — consistent with the privacy policy. It must never become a tracking tool
  itself.
- Detection partly overlaps modern OS alerts (DULT); blep's differentiators are
  the **find step**, the **manual scan**, the **rotation heuristic**, and coverage
  of **non-DULT / legacy** trackers.
