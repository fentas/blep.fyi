// Plain-Node sanity test for the find-by-signal engine — no ZeppOS needed.
// Run: node test/signal.test.mjs   (or: mise run test)
import assert from "node:assert";
import {
  ema,
  proximityOf,
  proximityColor,
  hapticInterval,
  roughDistanceM,
  trend,
  RSSI_FAR,
  RSSI_NEAR,
} from "../libs/signal.js";

let n = 0;
function ok(name, cond) {
  n++;
  assert.ok(cond, name);
  console.log("  ✓ " + name);
}

// --- proximity mapping --------------------------------------------------
ok("far edge → 0", proximityOf(RSSI_FAR) === 0);
ok("near edge → 1", proximityOf(RSSI_NEAR) === 1);
ok("beyond far clamps to 0", proximityOf(-120) === 0);
ok("beyond near clamps to 1", proximityOf(-30) === 1);
ok("midpoint ≈ 0.5", Math.abs(proximityOf((RSSI_FAR + RSSI_NEAR) / 2) - 0.5) < 1e-9);
ok("monotonic: closer ⇒ higher", proximityOf(-70) < proximityOf(-65));

// --- EMA smoothing ------------------------------------------------------
ok("ema seeds on first sample", ema(null, -70) === -70);
{
  // Feeding a constant value converges to it; a step is damped, not jumped.
  let v = ema(null, -80);
  v = ema(v, -60);
  ok("ema damps a +20 dB step (stays below halfway-fast jump)", v > -80 && v < -68);
  for (let i = 0; i < 20; i++) v = ema(v, -60);
  ok("ema converges to steady input", Math.abs(v - -60) < 0.5);
}

// --- colour gradient ----------------------------------------------------
ok("colour at 0 is the cold-blue stop", proximityColor(0) === ((127 << 16) | (168 << 8) | 212));
ok("colour at 1 is the warm-yellow stop", proximityColor(1) === ((244 << 16) | (213 << 8) | 141));
{
  const c = proximityColor(0.5);
  ok("mid colour is a valid 24-bit int", c >= 0 && c <= 0xffffff);
}

// --- haptic cadence -----------------------------------------------------
ok("no buzz when very far", hapticInterval(0) === 0);
ok("far buzz is slow (~1.2s)", hapticInterval(0.05) > 1000);
ok("on-it buzz is rapid (~110ms)", hapticInterval(1) <= 120);
ok("cadence speeds up as you close in", hapticInterval(0.3) > hapticInterval(0.9));

// --- rough distance -----------------------------------------------------
ok("≈1 m at the 1 m reference power", Math.abs(roughDistanceM(-59) - 1) < 0.05);
ok("farther signal ⇒ larger distance", roughDistanceM(-90) > roughDistanceM(-70));

// --- trend bucket -------------------------------------------------------
ok("rising proximity reads warmer", trend(0.4, 0.5) === "warmer");
ok("falling proximity reads colder", trend(0.5, 0.4) === "colder");
ok("tiny wobble reads flat", trend(0.5, 0.505) === "flat");

console.log("\n" + n + " checks passed.");
