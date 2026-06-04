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
    # Apple Find My, "separated from owner": company 0x004C (LE 4C 00), payload byte0 0x12
    await advertise('airtag', 'F0:1A:2B:3C:4D:5E', mfg=[0x4C, 0x00, 0x12, 0x19, 0x10, 0, 0, 0])
    # Tile: 16-bit service UUID 0xFEED
    await advertise('tile', 'C0:11:22:33:44:55', service_uuids=['FEED'])
    # Samsung SmartTag: 16-bit service UUID 0xFD5A
    await advertise('smarttag', 'D0:66:77:88:99:AA', service_uuids=['FD5A'])
    print('Trackers live. Open blep → "Is something tracking you?". Ctrl-C to stop.', flush=True)
    await asyncio.sleep(3600)


if __name__ == '__main__':
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        pass
