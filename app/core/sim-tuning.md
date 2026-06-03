# Tracking tuning log

Driven by `TrackingSimulationTest` (a virtual user reads the on-screen guidance
and follows it, against a body-shielding RSSI model + walls/slope/elevation/
canopy/noise/motion). We record each tweak and whether it helped, so we keep
what works and revert what doesn't.

Metric: **solved** = reached within 2 m before the 180 s timeout. **path-eff** =
distance walked to reach ÷ straight-line (1.0 = beeline). Lower is better.

## Baseline (after: compass-bearing-first, peak-interior sweep, proximity rescale)

`solved 8/13 · avg path-eff 0.8×`

| scenario       | straight | closest | found   | path-eff | solved |
|----------------|---------:|--------:|---------|---------:|:------:|
| ahead 5 m      |   5.0 m  |  0.2 m  | 12 s    |   0.6×   |  ✓ |
| behind 8 m     |   8.0 m  |  4.0 m  | timeout |  32×     |  ✗ |
| to the side 6 m|   6.0 m  |  0.0 m  | 14 s    |   0.8×   |  ✓ |
| diagonal 14 m  |  14.1 m  |  2.2 m  | timeout |  18×     |  ✗ |
| far 20 m       |  20.0 m  |  0.2 m  | 22 s    |   0.9×   |  ✓ |
| extra-far 35 m |  35.0 m  |  0.2 m  | 32 s    |   0.9×   |  ✓ |
| through a wall |   9.0 m  |  0.0 m  | 15 s    |   0.8×   |  ✓ |
| up a slope     |  12.1 m  |  0.0 m  | 17 s    |   0.8×   |  ✓ |
| forest 12 m    |  12.0 m  |  0.1 m  | 17 s    |   0.8×   |  ✓ |
| noisy room     |   8.0 m  |  0.2 m  | 14 s    |   0.8×   |  ✓ |
| randomized     |  13.0 m  |  2.3 m  | timeout |  18×     |  ✗ |
| one floor up   |   5.0 m  |  3.0 m  | timeout |  —       |  ✗ (needs stairs) |
| moving device  |   8.0 m  |  3.8 m  | timeout |  29×     |  ✗ (edge case) |

Open: `behind` (bearing wrong → never closes), `diagonal`/`randomized` (orbit at
~2.2 m, just outside the 2 m ring), `one floor up` + `moving` (expected edge
cases).

## Changes

| # | change | result | keep? |
|---|--------|--------|:-----:|
| 0 | baseline above | 8/13, eff 0.8× | — |
| 1 | AngularSignalField coverage 0.8→0.92 | 7/13 — broke far/extra-far | ✗ revert |
| 2 | bearing = peak-bin local window (±2) | 5/13 — broke side/noisy | ✗ revert |
| 3 | **SignalGrid memory + "warmer back that way" recovery** | **11/13** — solved behind, diagonal, randomized (orbiters); eff 1.9× (recovery adds some backtrack) | ✓ **keep** |

After #3: only `one floor up` (needs stairs) and `moving device` (edge case)
remain. Recovery fires when the signal drops ≥6 dB below the warmest grid cell
visited, pointing you back to it.

Next idea (from grid): the cells are a multi-vantage dataset — triangulate the
target by intersecting bearings / RSSI ranges across cells (robust under
body-shielding, unlike single range-trilateration).
