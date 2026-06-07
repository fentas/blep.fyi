#!/usr/bin/env python3
"""Inject fake BLE trackers into the running Android emulator via netsim + Bumble.

This validates the **Android parse path** end-to-end: a real advertisement crosses
netsim into the emulator, where `AndroidBleScanner.advertisements()` turns the
`ScanResult` into a `RawAdvert` and `TrackerClassifier` recognises the tracker —
the part the JVM unit tests can't cover because they hand-build `RawAdvert`.

What this does NOT prove: that these byte fingerprints are what *real* hardware
emits. The payloads here are the same assumptions the classifier holds, so it's
circular on that axis — only a real AirTag/Tile/SmartTag (or an authoritative
spec) confirms the fingerprints themselves.

Prereqs:
  - A running blep emulator (API 31+; this was validated on API 35, emulator 36.x).
  - python3 -m venv venv && venv/bin/pip install bumble
Run:
  python3 advertise.py [grpc_port]      # default port 35677
Find the port if needed:  cat "$TMPDIR/netsim.ini"  (key: grpc.port)

Then on the device, open blep (real mode, not demo) → "Is something tracking you?"
and confirm the fakes are flagged.
"""
import asyncio
import sys

from bumble.core import AdvertisingData
from bumble.device import Device
from bumble.hci import Address
from bumble.transport import open_transport

GRPC_PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 35677

# name -> advertising payload. Pass device names after the port to advertise only a
# subset (e.g. `advertise.py 35677 tile`) — netsim brings up one virtual peripheral
# reliably, so per-device runs are how the validation loop checks each fingerprint.
# Expected classification is in the comment beside each.
DEVICES = {
    # Apple Find My, "separated/lost": type 0x12 len 0x19 ⇒ FIND_MY, separated=true
    'airtag': dict(addr='F0:1A:2B:3C:4D:5E', mfg=[0x4C, 0x00, 0x12, 0x19, 0x10, 0, 0, 0]),
    # Apple Find My, "with owner": type 0x12 len 0x02 ⇒ FIND_MY, separated=FALSE
    'airtag-owned': dict(addr='F0:1A:2B:3C:4D:5F', mfg=[0x4C, 0x00, 0x12, 0x02, 0x00]),
    # Tile: service UUID 0xFEED ⇒ TILE
    'tile': dict(addr='C0:11:22:33:44:55', service_uuids=['FEED']),
    # Samsung SmartTag: service UUID 0xFD5A ⇒ SMARTTAG
    'smarttag': dict(addr='D0:66:77:88:99:AA', service_uuids=['FD5A']),
    # Google Find My Device / DULT UUID 0xFD44 ⇒ DULT
    'googletag': dict(addr='E0:AB:CD:EF:01:23', service_uuids=['FD44']),
    # NEGATIVE: a Samsung *phone* (company 0x0075, no SmartTag UUID) ⇒ UNKNOWN / not flagged
    'galaxy-phone': dict(addr='A0:BB:CC:DD:EE:01', mfg=[0x75, 0x00, 0x42, 0x01]),
}


async def advertise(name, addr, mfg=None, service_uuids=None):
    """Start one virtual peripheral broadcasting the given AD payload.

    `mfg` is the raw manufacturer-specific-data bytes *including* the 2-byte
    little-endian company id (Android strips the company id before handing the
    rest to getManufacturerSpecificData()).
    """
    transport = await open_transport(f'android-netsim:localhost:{GRPC_PORT},name={name}')
    fields = [(AdvertisingData.COMPLETE_LOCAL_NAME, name.encode())]
    if mfg:
        fields.append((AdvertisingData.MANUFACTURER_SPECIFIC_DATA, bytes(mfg)))
    if service_uuids:
        data = b''.join(int(u, 16).to_bytes(2, 'little') for u in service_uuids)
        fields.append((AdvertisingData.INCOMPLETE_LIST_OF_16_BIT_SERVICE_CLASS_UUIDS, data))
    device = Device.with_hci(name, Address(addr), transport.source, transport.sink)
    await device.power_on()
    device.advertising_data = bytes(AdvertisingData(fields))  # legacy AD: keep < 31 bytes
    await device.start_advertising()
    print(f'  advertising {name} ({addr})', flush=True)


async def main():
    print(f'Connecting fake trackers to netsim gRPC :{GRPC_PORT} …', flush=True)
    wanted = [a for a in sys.argv[2:] if a in DEVICES] or list(DEVICES)
    for name in wanted:
        await advertise(name, **DEVICES[name])
    print('Trackers live. Open blep → "Is something tracking you?". Ctrl-C to stop.', flush=True)
    await asyncio.sleep(3600)


if __name__ == '__main__':
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        pass
