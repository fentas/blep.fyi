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

## What's built
- `core/.../safety/TrackerDetector.kt` — pure, deterministic detector + tunables
  (close dBm, "nearby"/"following" durations, rotation churn/coverage thresholds).
- `core/.../safety/TrackerDetectorTest.kt` — 6 scenarios (following AirTag,
  brief separated tag, rotating churn, stable earbuds, far devices, passers-by).

## Remaining work
- **Platform scan plumbing.** Extend the scanner to surface a `TrackerSighting`
  stream from raw advertisements: parse manufacturer data + service UUIDs into a
  `TrackerKind`/`separated`, and set `randomAddress` from the BLE address type.
  - **Android:** full support — reads raw adv data; foreground + opt-in periodic/
    background scan feasible (battery-aware).
  - **iOS:** background BLE is heavily restricted and Find My is OS-reserved, so
    **foreground / on-open only**; the OS already does native unwanted-tracker
    alerts, but blep still adds the *find-it* step + a manual scan.
- **UI.** A "Safety scan" entry → live list of suspected trackers (kind, severity,
  signal) → **tap to find** (reuses the tracking engine). Clear, non-alarmist copy.
- **v2 — "watch for followers".** Opt-in: persist a seen-list with timestamps (+
  optional coarse location); on app open and/or an Android background scan, flag a
  tracker seen across multiple places/hours. iOS = on-open only.

## Notes
- This is a **safety** feature: strictly local/on-device, no new data leaves the
  phone — consistent with the privacy policy. It must never become a tracking tool
  itself.
- Detection partly overlaps modern OS alerts (DULT); blep's differentiators are
  the **find step**, the **manual scan**, the **rotation heuristic**, and coverage
  of **non-DULT / legacy** trackers.
