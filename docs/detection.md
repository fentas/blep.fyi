# Tracker / follower detection — design notes

Working notes on how blep decides "something may be following you," what it does
today, where it gets false positives, and what profiles/heuristics could help.
This is a thinking document, not a spec.

## Hard constraint: no location

blep deliberately scans with `neverForLocation` and stores nothing off-device.
So everything below correlates by **time, sessions, movement, and co-presence —
never GPS**. Where other apps (AirGuard, Apple Tracker Detect) say "this tag was
near you at these *places*," blep can only say "across these *separated times /
movement segments*." That shapes every heuristic here.

## What we detect today

`SafetyScanner` + `TrackerDetector` over raw advertisements:

1. **Known tracker kinds** — manufacturer data / service UUIDs for Find My
   (AirTag & Find My network), Tile, Samsung SmartTag, etc. → `TrackerKind`.
2. **Separated-from-owner** — a Find My device advertising in "lost/separated"
   mode (not near its owner) close to *you*.
3. **Address-rotation correlation** — a privacy tracker rotates its BLE address
   (RPA) to stay anonymous; it gives itself away by **reappearing at the same
   close range again and again**. The un-correlation *is* the correlation.
4. **Cross-session memory** (opt-in) — a small on-device log of close encounters
   by tracker *kind* + time (no identity, no location). If a kind shows up close
   "across N separate clock-hours," that's flagged as likely travelling with you
   (`CrossSession.persistent` at ≥3 distinct hours).

Severity escalates: nearby → separated-nearby → following (sustained) → rotation.

## The core false-positive problem

**Routine co-presence ≠ stalking.** The clearest case is the user's own:
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

## Profiles worth having

Rather than one global threshold, a small set of modes (auto-detected from
movement, or user-set) that retune sensitivity:

| Profile | When | Behaviour |
|---|---|---|
| **Stationary / home** | long still period, stable backdrop | Learn a *baseline allowlist* of devices usually around you; heavily downweight them. New persistent device near you while stationary is interesting but low-urgency. |
| **Commute / transit** | recurring movement window, high stranger churn | Expect lots of brief strangers → raise the bar; only flag a device that **survives the churn** (still there after the crowd turned over) or that also appears outside this window. |
| **Out & about / travel** | novel movement, unfamiliar backdrop | Most sensitive: a device following you here, *especially* if also seen at home/commute, is the strongest stalking signal. |

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

## Open questions

- How much movement signal survives in the background scan (periodic worker only
  gets short windows; the foreground service sees continuous motion)? Detection
  quality may differ per scan mode — worth measuring.
- Baseline-allowlist privacy: learning "devices usually around me" is powerful
  but is a behavioural fingerprint; keep it on-device, bounded, and clearable.
- Do we expose profiles to the user, or keep them fully automatic? Lean
  automatic with an override.
