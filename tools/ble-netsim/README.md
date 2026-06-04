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

## Bridge checks (the tracking platform glue)

The same virtual-radio setup powers the "bridge" checks for the *tracking* path —
the Android sensor/BLE glue the JVM `TrackingSimulationTest` bypasses:

- `make bridge-rssi` (`check_rssi_bridge.sh`) — injects a peripheral, drives blep to
  the tracking screen, asserts `AndroidBleScanner.rssi()` streams a reading to the UI.
- `make bridge-motion` — runs `:core:connectedDebugAndroidTest`
  (`AndroidMotionProviderBridgeTest`): the real fused rotation-vector sensor →
  heading transform produces well-formed samples on-device.
- `make bridge` — both.

These are **plumbing** checks, not path-finding. netsim assigns a single static BLE
RSSI (no warmer/colder gradient) and the emulator has no controlled orientation, so
"does it walk me to the target?" stays in the deterministic JVM sim (`make sim`).

Notes:
- netsim reports a non-physical RSSI (often a positive value); it still sits above
  the detector's "close" threshold, so detection fires.
- Legacy advertising caps the AD payload at 31 bytes — keep names + manufacturer
  data short or the controller rejects `LE_SET_EXTENDED_ADVERTISING_DATA`.
