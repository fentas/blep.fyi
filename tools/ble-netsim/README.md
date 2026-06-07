# BLE tracker injection (netsim + Bumble)

Validate the **safety scan's Android parse path** without physical hardware.

Since emulator 33+/API 31+, the Android emulator ships a virtual Bluetooth stack
backed by **netsim**. Google's [Bumble](https://github.com/google/bumble) can join
that virtual radio over gRPC and broadcast arbitrary BLE advertisements. We use it
to inject fake AirTag / Tile / SmartTag adverts and confirm that the *real*
`AndroidBleScanner.advertisements()` → `TrackerClassifier` → `TrackerDetector` path
flags them — something the JVM unit tests can't cover (they hand-build `RawAdvert`
and skip the actual Android `ScanResult` parsing).

## What it does and doesn't prove

- ✅ **Parse path / plumbing.** A real advertisement crosses netsim into the
  emulator; `getManufacturerSpecificData()` / service UUIDs / address bits are
  parsed and classified correctly.
- ❌ **Fingerprint correctness.** The byte patterns injected here are the *same
  assumptions the classifier holds*, so this can't confirm them against real
  hardware. For that you still need a real AirTag/Tile/SmartTag (or an
  authoritative published advertising spec).

## Run

`make ble-trackers` wraps the steps below (creates the venv, finds the gRPC port,
runs the advertiser). To do it by hand:

```bash
make emulator                 # boot the blep AVD (API 31+)
python3 -m venv tools/ble-netsim/venv
tools/ble-netsim/venv/bin/pip install bumble
# netsim's gRPC port is in "$TMPDIR/netsim.ini" (key: grpc.port); default below is 35677
tools/ble-netsim/venv/bin/python tools/ble-netsim/advertise.py [grpc_port]
```

Then open blep on the device in **real mode** (not `make demo`) →
*"Is something tracking you?"*. You should see the injected fakes flagged
(e.g. *"Unknown Find My tracker nearby"*).

### Per-device runs

netsim reliably brings up **one** virtual peripheral at a time, so to check each
fingerprint pass the device name(s) after the port and cycle them (re-open the scan
between devices to reset the detector window):

```bash
tools/ble-netsim/venv/bin/python tools/ble-netsim/advertise.py 35677 tile
# names: airtag airtag-owned tile smarttag googletag galaxy-phone  (default: all)
```

Last validated live (emulator-5554, API 35) — the real `AndroidBleScanner` →
`TrackerClassifier` path:

| injected device | expected | live result |
|---|---|---|
| `airtag` (Find My, len `0x19`) | Find My, separated | ✅ "Unknown Find My tracker nearby" |
| `tile` (`0xFEED`) | Tile | ✅ "Unknown Tile nearby" |
| `smarttag` (`0xFD5A`) | SmartTag | ✅ "Unknown SmartTag nearby" |
| `googletag` (DULT `0xFD44`) | DULT | ✅ "Unknown tracker nearby" |
| `airtag-owned` (Find My, len `0x02`) | **not** flagged (with owner) | ✅ no alert |
| `galaxy-phone` (bare `0x0075`) | **not** flagged (Samsung phone) | ✅ no alert |

The last two are the regression guards for the bugs fixed in the classifier
(length-aware Find My separation; dropping the over-broad Samsung company-id match).

## Bridge checks (the tracking platform glue)

The same virtual-radio setup powers the "bridge" checks for the *tracking* path —
the Android sensor/BLE glue the JVM `TrackingSimulationTest` bypasses:

- `make bridge-rssi` (`check_rssi_bridge.sh`) — injects a peripheral, drives blep to
  the tracking screen, asserts `AndroidBleScanner.rssi()` streams a reading to the UI.
- `make bridge-motion` — two layers: (1) `:core:connectedDebugAndroidTest`
  (`AndroidMotionProviderBridgeTest`) checks the fused rotation-vector → heading
  transform produces well-formed samples on-device; (2) `check_motion_bridge.sh`
  injects four 90° yaw steps via `adb emu sensor set` and asserts the heading
  *tracks* them (~90°/step, consistent sign) — catching remap/scale/sign bugs the
  smoke test can't see.
- `make bridge` — both.

These are **plumbing** checks, not path-finding. netsim assigns a single static BLE
RSSI (no warmer/colder gradient) and the emulator has no controlled orientation, so
"does it walk me to the target?" stays in the deterministic JVM sim (`make sim`).

Notes:
- netsim reports a non-physical RSSI (often a positive value); it still sits above
  the detector's "close" threshold, so detection fires.
- Legacy advertising caps the AD payload at 31 bytes — keep names + manufacturer
  data short or the controller rejects `LE_SET_EXTENDED_ADVERTISING_DATA`.
