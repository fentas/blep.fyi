# Safety scan — "is something tracking *me*?"

The anti-stalking mirror of blep's hunt: detect an unwanted Bluetooth tracker
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
  (SharedPreferences / NSUserDefaults): opt-in, persists a rolling log of close
  encounters (tracker *kind* + time only — no identity, no location), and promotes
  a kind seen across **3+ separate hours** to a full ALERT. 7-day retention
  (~17 KB cap; storage is a non-issue), throttled to ≤1 record/kind/5 min,
  cleared when the toggle is turned off.
- **UI.** Discovery "🛡️ Is something tracking you?" entry → `SafetyScreen`: live
  list of suspected trackers (kind, severity, signal) → **Find it** (reuses the
  tracking engine) + the "Remember across sessions" opt-in toggle.
- Tests: `TrackerDetectorTest` (6), `TrackerClassifierTest` (4),
  `SafetyHistoryTest` (7).

## Remaining work
- **On-device protocol validation.** The byte/UUID fingerprints (Find My `0x12`,
  DULT `0xFD44`/`0xFEAA`, SmartTag `0xFD5A`) are marked "verify on-device" — confirm
  against a real AirTag/Tile/SmartTag via the internal testing track (the emulator
  has no BLE radio).
- **Continuous background scan — deferred past v1.** A true always-on background
  scan needs an Android foreground service + `ACCESS_BACKGROUND_LOCATION`, which
  triggers Play's background-location review (justification video, slower approval)
  and a stronger privacy disclosure. To keep the first release shippable, v1 does
  **on-open cross-session correlation only** (re-evaluates the persisted log every
  time the scan is opened). The background service is the clear fast-follow; revisit
  once v1 is live.
- **"Across places" (location) correlation.** Cross-session is currently time-only
  (hours). Adding coarse location to distinguish "same tracker in different places"
  would strengthen it but pulls in the location-permission/Play surface above — also
  a v2 item, gated behind the same opt-in.
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
