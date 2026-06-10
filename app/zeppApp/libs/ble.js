/**
 * Thin wrapper over @zos/ble's master scanner (ZeppOS API_LEVEL 3.0+).
 *
 * mstStartScan fires its callback on every advertisement it hears, each with
 * { dev_name:string, dev_addr:ArrayBuffer(6), rssi:number, service_uuid_array,
 * service_data_array }. We normalise dev_addr to a "AA:BB:.." MAC string and
 * hand back a tidy record. There is no connection — pure advertisement listening.
 */
import { mstStartScan, mstStopScan } from "@zos/ble";

/** ArrayBuffer(6) → "AA:BB:CC:DD:EE:FF". */
export function ab2mac(ab) {
  const u8 = new Uint8Array(ab);
  const hex = [];
  for (let i = 0; i < u8.length; i++) {
    hex.push(u8[i].toString(16).padStart(2, "0").toUpperCase());
  }
  return hex.join(":");
}

/**
 * Start scanning. `onDevice` is called with { mac, name, rssi } for every
 * advertisement. Returns a stop() function. Safe to call stop() more than once.
 */
export function startScan(onDevice) {
  const handler = (res) => {
    if (!res || !res.dev_addr) return;
    onDevice({
      mac: ab2mac(res.dev_addr),
      name: res.dev_name || "",
      rssi: typeof res.rssi === "number" ? res.rssi : -127,
    });
  };
  const ok = mstStartScan(handler);
  let stopped = false;
  return function stop() {
    if (stopped) return;
    stopped = true;
    try {
      mstStopScan();
    } catch (e) {
      // already stopped / scanner torn down — ignore
    }
  };
}
