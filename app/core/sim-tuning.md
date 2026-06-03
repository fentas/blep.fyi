# Tracking tuning log

Driven by `TrackingSimulationTest`: a virtual user reads the on-screen guidance
and follows it, against a body-shielding RSSI model + walls / slope / elevation /
canopy / noise / motion. We commit **one change at a time** with its score so we
can diff what helped.

## Metric

- **reached** = got within 2 m before the 180 s timeout.
- **path-eff** = distance walked to reach ÷ straight-line (1.0 = a beeline).
- **★ clean** = reached **and** path-eff < 3.5× (a real solve). Reaching an 8 m
  target by wandering 77 m (7.6×) is **~ wander** — counts as a fail.

Headline score: **clean / total**.

## Baseline (compass-bearing-first · grid recovery · proximity rescale)

`clean 9/13 · reached 11/13 · avg path-eff 1.9×`

| scenario       | path-eff | result |
|----------------|---------:|--------|
| ahead 5 m      |   0.6×   | ★ clean |
| behind 8 m     |   7.6×   | ~ wander |
| to the side 6 m|   0.8×   | ★ clean |
| diagonal 14 m  |   2.6×   | ★ clean |
| far 20 m       |   0.9×   | ★ clean |
| extra-far 35 m |   0.9×   | ★ clean |
| through a wall |   0.8×   | ★ clean |
| up a slope     |   0.8×   | ★ clean |
| forest 12 m    |   0.8×   | ★ clean |
| noisy room     |   0.8×   | ★ clean |
| randomized     |   4.5×   | ~ wander |
| one floor up   |    —     | ✗ fail (can't auto-detect another floor) |
| moving device  |   11×    | ✗ fail (edge case) |

Open: `behind` + `randomized` wander (sweep commits to a slightly-wrong bearing,
then the grid recovery brute-forces it). `one floor up` / `moving` are hard cases.

## Change history

| # | change | clean | reached | note |
|---|--------|:-----:|:-------:|------|
| 0 | baseline (compass-first + grid recovery) | 9/13 | 11/13 | committed |
| 1 | AngularSignalField coverage 0.8→0.92 | — | 7/13 reached | ✗ reverted (broke far) |
| 2 | bearing = peak-bin local window | — | 5/13 reached | ✗ reverted |
| 3 | cross-bearing (fox-hunt) triangulator | 9/13 | 11/13 | neutral (dormant — needs lateral spread); not kept |
| 4 | auto "try another floor" (4 variants) | ≤8 | — | ✗ all reverted — can't tell "stuck on wrong bearing" from "another floor" |

Next: make `behind`/`randomized` **clean** (fix the sweep committing to a biased
bearing) — that's the real win, not just reaching by wandering.
