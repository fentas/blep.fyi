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

## Current best: `clean 12/13 · reached 12/13 · avg path-eff 1.2×`

| scenario       | path-eff | result |
|----------------|---------:|--------|
| ahead 5 m      |   0.6×   | ★ clean |
| behind 8 m     |   0.8×   | ★ clean |
| to the side 6 m|   0.8×   | ★ clean |
| diagonal 14 m  |   3.1×   | ★ clean |
| far 20 m       |   0.9×   | ★ clean |
| extra-far 35 m |   0.9×   | ★ clean |
| through a wall |   0.8×   | ★ clean |
| up a slope     |   0.8×   | ★ clean |
| forest 12 m    |   0.9×   | ★ clean |
| noisy room     |   0.8×   | ★ clean |
| randomized     |   1.5×   | ★ clean |
| one floor up   |    —     | ✗ fail (needs stairs — vertical can't be auto-detected) |
| moving device  |   2.3×   | ★ clean |

Only `one floor up` is unsolved: the virtual user can't climb, and auto floor
detection is provably unreliable (#4). 12/13 is effectively the ceiling here.

### Original baseline (for reference): `clean 9/13 · reached 11/13 · 1.9×`
`behind` 7.6×, `randomized` 4.5×, `moving` 11× fail. The journey 9→12 below.

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
| 8 | peak-region centroid (window cutoff) | 8–9/13 | 11/13 | ✗ reverted — too narrow breaks diagonal; behind no better |
| 9 | signal-bearing beats recovery (reorder) | 8/13 | 8/13 | ✗ reverted — recovery *was* the crude search that reached behind |
| 10 | peakInterior ±2 + descending (turn through peak) | 9/13 | 10/13 | ✓ kept (in #11) — fixes behind 7.6×→0.8×; cost diagonal until #11 |
| 11 | sticky recovery (hysteresis) | 11/13 | 12/13 | ✓ kept — stops recover↔chase flip-flop; fixes randomized + moving |
| 12 | freshness decay, every step | 8/13 | 8/13 | ✗ reverted — ages valid long approaches (far/wall/forest fail) |
| 13 | recoverDb 6→4 | 9/13 | 10/13 | ✗ reverted — over-eager recovery breaks forest/moving |
| 14 | freshness decay **gated on cooling** | **12/13** | 12/13 | ✓ kept — diagonal 5.2×→3.1×; long approaches untouched (still warming) |

## Observations (the 9→12 climb)

The sim traces were decisive — eyeballing a metric never would have found these:

1. **The bearing was committed at the sweep *edge*.** Turning in place from north,
   `behind` locked onto a heading ~30° short of the true peak (the strongest bin
   *sampled so far*, not the real peak), and overshot. Fix: `peakInterior` now
   requires turning ±2 bins **past** the peak with the signal descending on both
   sides (#10). This alone fixed `behind` 7.6×→0.8×.

2. **Recovery and the bearing cue fought every tick.** Once you overshot, the
   "head back to the warm spot" cue and the "follow the signal" cue alternated at
   the single dB threshold — you orbited the target forever. Fix: recovery is now
   **sticky** (engage at recoverDb below warmest, release only at 30% of it) (#11).
   This fixed `randomized` *and* `moving` for free.

3. **A straight walk coasts on a stale bearing.** The swept field is anchored to
   where you stood; walk past the target and it still says "forward" because you
   only refresh the bin you face. Fix: bins **age with travel — but only while
   cooling** (#14). The cooling gate is the whole trick: `far`/`wall`/`forest`
   keep warming on a straight approach so their bearing is never thrown away,
   while `diagonal` cools the moment it passes and re-sweeps onto the now-sideways
   target. Distance-only decay (#12) broke the long approaches; the cooling gate
   doesn't.

**Meta:** every win came from *watching the agent fail in the trace*, not from
turning a constant. The chaos search's verdict held — the angular field was the
lever, but the fixes were structural (when to trust / age / re-earn the bearing),
not parameter values.

## Held-out generalisation (robustness_suite)

The fixed 13 can be overfit, so `robustness_suite` runs N random worlds (bearing,
distance 4–28 m, noise, ~30% a wall, ~20% a slope) as a held-out set. Current
tuning: **~85% clean across seeds** (31–37/40, seeds 1234/77/2025), 88% reached.
The structural fixes generalise; they don't just fit the suite.

Things tried against held-out that did **not** stick:
- `angularStaleHalfLifeM` 3→2.5/2: nudges held-out +3% but breaks `forest` +
  `moving` in the fixed suite (too many re-sweeps for noisy/moving signals). 3.0
  is the sweet spot — kept.
- Gate the particle-target branch when a warm spot is known: fixed 12→10/13 and
  held-out swung 73–98% by seed (the branch genuinely helps move-without-turning).
  Reverted.

Two residual held-out failure modes (genuinely hard — fundamental BLE limits):
1. **Low-confidence thrash**: in a noisy world the swept bearing hovers right at
   `signalMinConfidence`, flickering on/off, so the user shuffles instead of doing
   one clean in-place sweep. Never localises.
2. **Shielding-inflated range**: up close the particle filter is *confident* but
   reports a distance way too large (e.g. "~16 m" at 4 m) because body-shielding
   keeps the signal strong all around — so it orbits. This is the very reason the
   compass bearing is trusted over trilateration; it only bites when the bearing
   has also gone stale/low-confidence.

Both are the same root: when *no* cue is reliable the arbitration flip-flops.
A future structural fix would be directional commitment (don't reverse the cue
every tick on a marginal confidence wobble) — needs guidance to carry state.

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

→ This conclusion drove the #10–#14 structural fixes above (9/13 → 12/13).
