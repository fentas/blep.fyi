# Finding algorithm — design notes (and the GPS question)

How the "walk me to my lost thing" hunt works, and the experiment on whether GPS
should feed it. Companion to `detection.md`.

## How finding works

Two fused layers (`core/spatial`, `core/tracking`):

- **Dead reckoning** (`DeadReckoner`) integrates IMU motion (steps + heading) into a
  *relative* local frame — where you've walked since you started.
- **RSSI triangulation** (`SpatialTracker` + particle filter + `SignalGrid`) folds the
  signal strength sampled along that path into a target estimate, and
  `SpatialGuidance` turns it into "turn 30° left · ~8 m".

The crucial property: **RSSI is anchored to the *real* target.** The particle filter
is pulled toward truth by every reading, so the closed loop (guidance → you move →
new RSSI → better estimate) self-corrects.

## Should GPS feed it? — measured, the answer is no

`updateGeo()` + `DeadReckoner` already support fusing a GPS fix (accuracy-weighted
complementary filter). The question was whether to wire it for finding. The sim
(`simulation_gps_suite`, run via `make sim-gps`) models realistic dead-reckoning
**drift** (constant compass bias + a slow non-cancelling heading wander + step-length
error) and injects a noisy GPS fix at a given accuracy. Mean closest-approach (m,
lower = better) over four outdoor walks:

```
drift      GPS off  GPS 3 m  GPS 8 m  GPS 15 m
none           0.2      4.3      1.5     11.0
moderate       0.1      2.2      4.0     12.4
heavy          0.4      3.5      0.9      9.4
```

**GPS off wins in every regime — at every accuracy, even a tight 3 m fix, even under
heavy drift.** Why:

- The RSSI+IMU loop already converges to **sub-metre**. Dead-reckoning drift mostly
  *rotates/scales* the relative frame, and because you execute *relative* turns and
  RSSI keeps pulling toward the true target, the loop absorbs it.
- GPS, by contrast, writes its **own noise** straight into the position the filter
  triangulates from. At any realistic accuracy that noise is larger than the drift it
  would correct, so fusion is net-negative.
- And GPS is exactly worst where finding matters most: **indoors / the last few
  metres**, where it's unavailable or ±10–20 m.

**Decision: do not fuse GPS into finding.** The plumbing stays (dormant, not fed) but
the finder remains RSSI + IMU. If drift ever becomes a real problem, the fix is better
IMU handling (heading recalibration, ZUPT), not GPS.

(Location *is* worth it for the anti-stalking **detection** side — coarse, on-device,
to count distinct *places* a tracker follows you across — which is a different,
larger-scale question than the sub-metre hunt. See `detection.md`.)

## Reproduce

```
make sim       # headline scenario suite (no drift, no GPS — the pristine baseline)
make sim-gps   # the drift × GPS-accuracy matrix above
```

Scenarios carry an `Env` (heading bias, heading wander, step scale, GPS accuracy), so
new conditions are a one-line addition.
