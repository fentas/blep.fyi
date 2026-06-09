# Tracker / follower detection — design notes

Working notes on how blep decides "something may be following you," what it does
today, where it gets false positives, and what profiles/heuristics could help.
This is a thinking document, not a spec.

## Location: off by default, opt-in and coarse

blep's BLE scan itself always runs with `neverForLocation` and stores nothing
off-device. By default everything below correlates by **time, sessions, movement,
and co-presence — no GPS at all**; where other apps (AirGuard, Apple Tracker
Detect) say "this tag was near you at these *places*," default blep only says
"across these *separated times / movement segments*."

Since then we added **opt-in, coarse, strictly-on-device location** for the
detection side only: when the user turns it on, `coarsePlaceCell()` buckets the
last-known *coarse* fix into a ~2 km grid cell, so a tracker can be counted across
distinct **places**, not just distinct hours (`CrossSession.distinctPlaces`).
It's off unless enabled, never fed to the finder (see `finding.md` — GPS is
net-negative there), never leaves the phone, and degrades to the time-only logic
when unavailable. The constraint that *shaped* every heuristic here was "no
location"; the place dimension is an additive bonus on top, not a replacement.

## What we detect today

`SafetyScanner` + `TrackerDetector` over raw advertisements:

1. **Known tracker kinds** — manufacturer data / service UUIDs for Find My
   (AirTag & Find My network), Tile, Samsung SmartTag, etc. → `TrackerKind`.
2. **Separated-from-owner** — a Find My device advertising in "lost/separated"
   mode (not near its owner) close to *you*.
3. **Address-rotation correlation** — a privacy tracker rotates its BLE address
   (RPA) to stay anonymous; it gives itself away by **reappearing at the same
   close range again and again**. The un-correlation *is* the correlation.
4. **Cross-session memory** (on by default, clearable) — a small on-device log of
   close encounters by tracker *kind* + time (no identity, no raw coordinates;
   plus a coarse place cell when location-aware detection is opted in). If a kind
   shows up close "across N separate clock-hours" — or, with location on, across
   distinct **places** — that's flagged as likely travelling with you
   (`CrossSession.persistent` at ≥3 distinct hours; `multiPlace` across places).

Severity escalates: nearby → separated-nearby → following (sustained) → rotation.

## The core false-positive problem

**Routine co-presence ≠ unwanted tracking.** The clearest case is the user's own:
*"if you take the same transit every day, you get the same devices."* Other
benign recurrers:

- Household devices (a partner's tag, the TV, a fixed beacon at home).
- A coworker who sits near you daily; the office printer/beacon.
- The gym, the regular café, the bus you always catch.

All of these reappear "across separate hours" — exactly the signal we use for
"following you." Time-based recurrence alone over-fires.

## The key insight: context **diversity**, not just recurrence

A real follower is distinguished not by *recurring*, but by **recurring across
contexts that should be unrelated**. Your commuter neighbour's phone co-occurs
with you in *one* recurring context (the 8:10 train). A planted tracker co-occurs
with you at home **and** on the train **and** somewhere new — it has no business
spanning all of them.

Without GPS, a "context" is approximated by on-device signals:

- **Movement segments** — accelerometer/step bouts between stationary periods. A
  device present across many *distinct movement segments* (you walked somewhere,
  drove, walked again, and it's still there) is suspicious; one present only
  during a single recurring segment is routine.
- **Co-present set fingerprint** — the *set* of other devices around you. Your
  commute has a churn of strangers but a stable backdrop; home has a different
  backdrop. A tag that persists while the **rest of the crowd changes** is the
  signal. (This is the rotation idea generalised: correlate the suspect against
  the de-correlation of everything else.)
- **Time-of-day / dwell** — seen only inside one daily window vs. bleeding across
  unrelated windows.

So the upgrade is: don't just count "separate hours," count **separate
*independent contexts*** (movement segments × time gaps × changed co-present set).
Threshold on context-diversity, not raw recurrence.

### Shipped (context-diversity + auto-learned baseline)

This is now implemented in `core/safety`:

- Each close encounter is logged with a coarse **context signature** =
  `place cell` (opt-in, location-aware) **×** `TrackerDetector.backdropFingerprint`
  — a stable, order-independent hash of the *close, non-rotating, non-tracker*
  devices around you (the home/desk/café backdrop; rotating strangers and trackers
  are excluded as churn). This gives context **without GPS**.
- `SafetyHistory.crossSession` learns a **baseline** = the single most-frequent
  context across all kinds (your auto-learned "usually around me"), and reports
  `distinctContexts` and `nonBaselineContexts` (contexts beyond the baseline).
  `CrossSession.diverse` = recurs across ≥2 non-baseline contexts.
- `SafetyScanner.promote` now keys on **diversity, not raw recurrence**: it promotes
  on `multiPlace` (≥2 places) or `diverse` (≥2 non-baseline contexts); raw
  hour-recurrence (`persistent`) is **suppressed when everything sits in one known
  context** (the commute/home false positive), and only the high-bar `veryPersistent`
  (≥6 separate hours) survives as a no-context-signal fallback.
- Tests: `SafetyHistoryTest` (baseline exclusion, single-context-not-diverse,
  new-context-beats-throttle) and `SafetyScannerTest` (routine single-context not
  promoted; diverse contexts and multi-place promote).

**Why baseline at the *context* level, not a device allowlist:** auto-allowlisting a
device or tracker *kind* "because it's always around" is dangerous — an unwanted tracker's tag
*is* always around (it's on you), so it would self-suppress. Downweighting the
**context** instead keeps the suspect flaggable the moment it appears beyond your
routine. The explicit per-device escape hatch stays the manual "It's mine" mute.

Still open: movement-segment counting (needs the motion stream wired into the scan)
and time-of-day/dwell, both of which would further sharpen `distinctContexts`.

## Profiles worth having

Rather than one global threshold, a small set of modes (auto-detected from
movement, or user-set) that retune sensitivity:

| Profile | When | Behaviour |
|---|---|---|
| **Stationary / home** | long still period, stable backdrop | Learn a *baseline allowlist* of devices usually around you; heavily downweight them. New persistent device near you while stationary is interesting but low-urgency. |
| **Commute / transit** | recurring movement window, high stranger churn | Expect lots of brief strangers → raise the bar; only flag a device that **survives the churn** (still there after the crowd turned over) or that also appears outside this window. |
| **Out & about / travel** | novel movement, unfamiliar backdrop | Most sensitive: a device following you here, *especially* if also seen at home/commute, is the strongest following signal. |

Auto-switching can be coarse (moving vs still, recurring-window vs novel) — it
doesn't need location, just motion + the time/co-presence fingerprint.

## Supporting mechanisms

- **Allowlist / "it's mine"** — already have mute; extend to an auto-learned
  "usually around me" set (household/baseline) so routine devices stop nagging.
  Must stay on-device and be easy to review/clear.
- **Per-device context tally** — store (kind/rotating-id, distinct-segments,
  distinct-hours, first/last) instead of just hour count. Drives the
  context-diversity threshold above.
- **Confidence, not binary** — surface "seen across 4 separate contexts incl.
  while you moved between 3 places-worth of segments" as the *reason*, so the
  user judges. blep's edge is it also points you to it.

## Scan modes: what each can actually see

Detection runs in two very different modes, and they don't have the same reach:

- **Foreground / foreground-service (continuous) scan.** A live, dense advertisement
  stream over a 15-min window. This is the only mode that can see *churn*: the
  rotation signal (many short-lived random ids, continuously close) and the
  id-switch correlator below both need a continuous stream to work.
- **Interval worker (app closed).** WorkManager's floor is 15 min between runs, and
  each run scans a ~20 s window with a *fresh* detector (no live state carries over),
  skipping while in battery-saver. Twenty seconds can't show four rotating ids churn,
  and can't accumulate "close for 5 minutes". So the interval mode effectively only
  catches **self-advertising, recognised-protocol trackers** (Find My / Tile /
  SmartTag / DULT), promoted to an alert by **place + backdrop-context recurrence**
  across sparse runs (the persisted cross-session log). An anonymous, protocol-less
  rotating tracker is **not** catchable in interval mode — that needs the continuous
  scan. We surface this rather than imply background == foreground coverage.

## Correlating rotating ids back into a device (`RotationTracker`)

Privacy MACs rotate *specifically* to stop per-tag tracking, so we don't try to
resolve identity — we correlate at the level of the **handover**:

- One id goes quiet exactly as a new id appears at the **same range** (within a
  `dbGate`, default 6 dBm) → the close-by population stays the same size (one out,
  one in) → treat the new id as the same physical device's next address, carrying
  its `firstSeen`, incrementing a rotation count. A new id at a *different* range
  with no matching departure is just a new device (the population grew). This is the
  user's "we have 4 devices, one at −30 dBm swaps id and we still have 4 — but a 5th
  at −90 just entered range" intuition.
- **Resolved on disappearance, not appearance.** A merge happens only when an old id
  actually dies while a matching successor lives on, so two devices that merely sit
  at the same range are never falsely merged. A successor must be born *after* the
  old id went quiet (small overlap allowed), never before.
- **Confidence scales with the dB match** and is divided across rival candidates —
  surfaced to the user, not a hidden binary.
- **Collisions don't discard data.** When a dying id has ≥2 plausible successors (or
  two die together at the same range), the orphaned lineage becomes a *branch* shared
  across the candidates — both keep it, probability split — and it's revalidated each
  tick until it resolves itself (a candidate leaves, or one keeps rotating and the
  other doesn't). A lone survivor absorbs at most one branch, so 2-die→1-survive never
  double-counts. While unresolved, stats report `contested` + the `alternatives`, so
  the device detail page can show the fork transparently.

Pure/deterministic and unit-tested (`RotationTrackerTest`). RSSI-only, so it's a
heuristic — the confidence carries the uncertainty rather than overclaiming.

**Calibrated to real RSSI (shipped):** the gate and confidence no longer use a flat
dB threshold. Each id is tracked with a tiny **α-β filter** (level + trend), so a
handover is matched against where the old id was *heading*, not a frozen value, and
the leftover residual is the device's true **jitter**. The gate then widens to that
jitter (clamped), confidence is high when a Δ sits *within* the noise, and a **closer
handover is weighted as more certain** (a tag right next to you can't be a stranger
teleporting in). A feeding bug that re-echoed a silent id from the scan snapshot —
which made a lone device never correlate — was fixed by feeding the correlator only
from the live advert stream.

**Active identification (shipped, `BleScanner.probe`):** a one-shot GATT connect (no
pairing) reads the GAP name + Device Information Service (maker/model/firmware/
hardware/serial) + the structural GATT fingerprint + battery + pairing posture. A
serial — or a name, or a same-structure/same-battery temporal match — re-links a
device across rotations the RSSI handover lost, and it's all shown on the device
panel. Dwell-gated, once-per-identity, cached in `IdentityStore`, runs in the finder
*and* the safety scan.

## Flagging a device (priority escalation)

A user can flag a suspicious device (persisted). A flagged device is promoted from
the ambient interval check to a **continuous foreground scan** that keeps tracking it,
with an active notification for as long as it stays in range — and clears when it
leaves or is unflagged. This buys the continuous-scan reach (above) for the one device
that matters, without running the foreground service all the time.

## Open questions

- How much movement signal survives in the background scan (periodic worker only
  gets short windows; the foreground service sees continuous motion)? Detection
  quality may differ per scan mode — worth measuring.
- Baseline-allowlist privacy: learning "devices usually around me" is powerful
  but is a behavioural fingerprint; keep it on-device, bounded, and clearable.
- Do we expose profiles to the user, or keep them fully automatic? Lean
  automatic with an override.
