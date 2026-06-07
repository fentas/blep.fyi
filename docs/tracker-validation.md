# Tracker fingerprint validation — checklist (real hardware)

blep's tracker recognition (`core/safety/Advert.kt` → `TrackerClassifier`) is unit-
tested and exercised end-to-end against **netsim/Bumble** injected adverts
(`tools/ble-netsim/advertise.py`). But netsim only validates the *parse path* — it
replays the very byte assumptions the classifier holds, so a wrong assumption passes
on both sides. The fingerprints themselves still need **one confirmation against real
hardware** (or an authoritative published advertising spec). This is that checklist —
it needs physical tags, so it's yours to run; tick a box when a device classifies as
expected.

## What the classifier currently keys on

| Kind | Signal blep matches | Confidence | Notes |
|---|---|:--:|---|
| `FIND_MY` | Apple mfg `0x004C`, TLV type `0x12`; **len ≥ 0x19 ⇒ separated**, len `0x02` ⇒ with-owner | high | Spec-based; TLV-walked so it's found even after a `0x10` record. |
| `TILE` | service UUID `0xFEED` / `0xFEEC` | high | Tiles advertise continuously. |
| `SMARTTAG` | service UUID `0xFD5A` | med-high | **Bare Samsung company id `0x0075` is deliberately NOT matched** (every Galaxy phone/watch/buds carries it). Confirm a real SmartTag still exposes `0xFD5A`. |
| `DULT` | service UUID `0xFD44` (Apple Find My) **or** `0xFEAA` | mixed | `0xFD44` solid; **`0xFEAA` is Eddystone** — Google's FMDN uses it but so do plain beacons, so it's the most likely false-positive / needs the closest look. |

Everything else stays `UNKNOWN` and is left to the rotation heuristic.

## Fast path — netsim regression (no hardware)

Confirms the parse path + the new guards (with-owner AirTag, Google/DULT, and the
Samsung-phone negative case) before you touch real tags:

```
make emulator            # boot the phone AVD (separate terminal)
make ble-trackers        # injects the fake adverts from tools/ble-netsim/advertise.py
# On the emulator: open blep → "Is something tracking you?"
```

Expected on screen: `airtag` (separated → WARN/ALERT), `tile`, `smarttag`,
`googletag` flagged; `airtag-owned` present but **not** a "separated nearby" WARN;
`galaxy-phone` **not** flagged at all.

## Real-hardware matrix

For each, advertise the device close to the phone, open **"Is something tracking
you?"**, and check the surfaced kind/severity. To force an AirTag/SmartTag into
**separated/lost** mode, leave it away from its owner phone for the vendor's timeout
(AirTag ≈ 15 min; others vary) before testing.

| Device | Mode | Expected kind | Expected `separated` | Pass? |
|---|---|---|:--:|:--:|
| Apple AirTag | with owner nearby | `FIND_MY` | no | ☐ |
| Apple AirTag | separated (owner away ~15 min) | `FIND_MY` | yes | ☐ |
| Find My 3rd-party (Chipolo/Pebblebee Find My) | separated | `FIND_MY` | yes | ☐ |
| Tile (Mate/Pro/Slim) | normal | `TILE` | yes | ☐ |
| Samsung SmartTag / SmartTag2 | separated (away from Galaxy) | `SMARTTAG` | yes | ☐ |
| Google Find My Device tag (Chipolo/Pebblebee for Google) | separated | `DULT` | yes | ☐ |
| **Negatives** (must stay UNKNOWN / unflagged): | | | | |
| Samsung Galaxy phone | screen on, BT on | `UNKNOWN` | — | ☐ |
| AirPods / Apple device (handoff, no Find My) | normal | `UNKNOWN` | — | ☐ |
| Eddystone beacon (e.g. a retail/IoT `0xFEAA` beacon) | normal | `UNKNOWN`* | — | ☐ |

\* The Eddystone negative is the key one: if a plain `0xFEAA` beacon shows up as
`DULT`, the `0xFEAA` entry in `DULT` is too broad — narrow it (prefer the FMDN
service-data structure over the bare UUID) and re-test.

## How to capture the raw bytes (when something misclassifies)

Use **nRF Connect** (Android/iOS) or `bluetoothctl` / Wireshark+`btmon` (Linux) to
dump the advertisement, then compare the manufacturer data / service UUIDs +
service-data to what `TrackerClassifier` expects. Update the constants in
`Advert.kt`, add a byte-level case to `TrackerClassifierTest`, and mirror it in
`tools/ble-netsim/advertise.py` so the regression covers it.

## Authoritative references to check against

- Apple — *Find My Network accessory spec* (offline-finding advertisement: Apple
  data type `0x12`, separated vs. nearby payload lengths + status byte).
- DULT — *Detecting Unwanted Location Trackers* IETF draft (cross-vendor accessory
  "not with owner" advertising).
- Google — *Find My Device network* accessory spec (FMDN beacon format).
- Bluetooth SIG — *Assigned Numbers* (16-bit UUIDs / company identifiers): confirm
  `0xFEED`/`0xFEEC` (Tile), `0xFD5A` (Samsung), `0xFD44`, `0xFEAA` (Eddystone),
  `0x004C` (Apple), `0x0075` (Samsung).
