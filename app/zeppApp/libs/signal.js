/**
 * blep · find-by-signal engine (pure logic, no ZeppOS imports).
 *
 * Ported from the Kotlin core's TrackingTuning: smooth the raw RSSI with an
 * EMA, map it linearly to a 0..1 "proximity", and derive the warm/cold colour,
 * the Geiger haptic cadence and a rough distance from that. Kept dependency-free
 * so it runs under Node for the unit test in ../test/signal.test.mjs.
 */

// dBm calibration — identical to the phone/watch core (TrackingTuning.kt).
export const RSSI_FAR = -90; // proximity 0.0 — far edge of BLE range (~15-20 m indoors)
export const RSSI_NEAR = -58; // proximity 1.0 — point-blank (~1 m)
export const EMA_ALPHA = 0.45; // smoothing: fast to react, still filters jitter

// Phase thresholds (TrackingSession.kt).
export const PINPOINT_PROXIMITY = 0.78; // "almost on it"
export const COMPLETE_PROXIMITY = 0.94; // "right here"

/** Exponential moving average. Pass the previous smoothed value (or null to seed). */
export function ema(prev, rssi) {
  return prev == null ? rssi : prev + EMA_ALPHA * (rssi - prev);
}

/** Linear RSSI → proximity in [0,1]. */
export function proximityOf(smoothedRssi) {
  const frac = (smoothedRssi - RSSI_FAR) / (RSSI_NEAR - RSSI_FAR);
  return Math.max(0, Math.min(1, frac));
}

// Warm/cold gradient — the exact stops the watch apps use (WearApp.kt line 52).
const STOPS = [
  [0.0, 127, 168, 212], // cold blue
  [0.4, 143, 208, 203], // cool teal
  [0.7, 174, 223, 166], // warm green
  [1.0, 244, 213, 141], // warm yellow
];

/** Proximity (0..1) → 0xRRGGBB colour int, smoothly interpolated between stops. */
export function proximityColor(p) {
  const x = Math.max(0, Math.min(1, p));
  let lo = STOPS[0];
  let hi = STOPS[STOPS.length - 1];
  for (let i = 0; i < STOPS.length - 1; i++) {
    if (x >= STOPS[i][0] && x <= STOPS[i + 1][0]) {
      lo = STOPS[i];
      hi = STOPS[i + 1];
      break;
    }
  }
  const span = hi[0] - lo[0] || 1;
  const t = (x - lo[0]) / span;
  const r = Math.round(lo[1] + (hi[1] - lo[1]) * t);
  const g = Math.round(lo[2] + (hi[2] - lo[2]) * t);
  const b = Math.round(lo[3] + (hi[3] - lo[3]) * t);
  return (r << 16) | (g << 8) | b;
}

/**
 * Geiger-counter haptic interval in ms (HapticCadence.kt): slow & sparse when
 * far, a rapid stutter on top of it. Returns 0 when too far to bother buzzing.
 */
export function hapticInterval(p) {
  if (p < 0.05) return 0;
  return Math.round(1200 - (1200 - 110) * p);
}

/**
 * Very rough distance estimate (metres) from smoothed RSSI via the log-distance
 * path-loss model. Indicative only — multipath indoors makes this wobble — but
 * good enough for an "~Xm" hint. txPower ≈ -59 dBm @ 1 m, path-loss exponent 2.5.
 */
export function roughDistanceM(smoothedRssi) {
  const txPower = -59;
  const n = 2.5;
  return Math.pow(10, (txPower - smoothedRssi) / (10 * n));
}

/** Trend bucket from the change in smoothed proximity, with a flat dead-band. */
export function trend(prevProx, prox) {
  const d = prox - prevProx;
  if (d > 0.012) return "warmer";
  if (d < -0.012) return "colder";
  return "flat";
}
