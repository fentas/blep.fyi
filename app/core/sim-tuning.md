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
| 5 | SIGNAL_MIN_CONFIDENCE 0.35→0.45 / 0.55 | 6/13 | 7/13 | ✗ reverted — fixes randomized but a single threshold starves the weak far signal (far/extra-far fail) |
| 6 | hoist all knobs into SpatialTuning | 9/13 | 11/13 | ✓ kept — behaviour-neutral; enables chaos search |
| 7 | angularPeakednessDb 5→4.5 + binEma 0.5→0.55 | 9/13 | 9/13 | ✗ reverted — turns the 2 wanderers into outright fails (worse) |

## Chaos search (random parameter sweep)

`CHAOS_N=70 ./gradlew :core:jvmTest --tests '*chaos*'` turns all 17 dials to
random values, runs the suite, and reports which dials separate the top third
from the bottom third. Run across 4 seeds (42/7/99/2024):

- The **angular field is the only consistent lever** — `angularCoverageFraction ↑`
  appears in *every* seed, with `angularPeakednessDb ↓` and `angularBinEma ↑`
  close behind. The sweep/bearing logic is where clean solves live, confirming the
  `behind`/`randomized` wander is a sweep-bias problem.
- But the "good third" means **hug the current defaults** (coverage ≈ 0.80 = default,
  peakedness ≈ 5.1, binEma ≈ 0.5). The defaults already sit in a good basin; the
  10–11/13 configs win by *luck* on the two borderline scenarios, and their
  individual best-config dials are noisy/contradictory across seeds (overfit to
  these 13 fixed worlds).
- Acting on the signal directly (change #7) made things worse, as predicted.

**Conclusion:** no single-knob win exists. `behind`/`randomized` need a smarter
sweep (commit to the bearing only after turning fully *through* the peak from both
sides), not a constant tweak. Defaults stay. The chaos harness stays for future
exploration (e.g. after the sweep logic changes).
