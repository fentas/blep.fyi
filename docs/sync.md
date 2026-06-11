# Phone ↔ watch sync

blep's phone and watch behave like one device: they share a small slice of state and
relay live events to each other. This note covers the design.

## Transport

Android backs it with the **Wear Data Layer** (`com.google.android.gms:play-services-wearable`);
the two apps share an `applicationId` + signing key, which is what lets the Data Layer
pair them. Three clients, three jobs:

| Client | Style | Used for |
|--------|-------|----------|
| `DataClient` | replicated, persistent, syncs on reconnect | converged **state** (favourites, names, tether set, settings) |
| `MessageClient` | fire-and-forget RPC | one-shot **events** (tracker found, tether left/returned) |
| `CapabilityClient` | who's present + `isNearby` | **presence** (peer reachable & nearby) |

The transport is an `expect`/`actual`: `core/sync/SyncTransport.android.kt` is the Data
Layer; `…/SyncTransport.apple.kt` + `…jvm` are no-ops for now. **Apple** will back the
same interface with **WatchConnectivity** — `updateApplicationContext` ≈ DataClient,
`sendMessage` ≈ MessageClient, `isReachable` ≈ CapabilityClient.

## State: a last-write-wins CRDT

`SyncState` (in `core/sync`) is the converged slice — favourites, the tether set, mutes,
device names, and a few settings. It's modelled as **last-write-wins maps**: every entry
carries the epoch-ms it was set, and `merge` keeps the newer one per key. That makes
`merge` commutative, associative and idempotent, so the two devices converge regardless of
sync order or duplication — and **removals are real** (a tombstone with a newer ts), so
un-favouriting on the phone isn't resurrected by the watch's stale copy.

Two refinements keep wall-clock out of the correctness story:
- **Lamport bump** — a local edit is stamped `max(now, maxTsSeen + 1)`, so a peer whose
  clock runs fast can't keep out-ranking edits made *after* its replica was seen.
- **Deterministic tie-break** — same-ts conflicting entries (a same-millisecond concurrent
  edit) resolve by the value's string form, keeping `merge` commutative; without it each
  side would keep its own entry and never converge.

`SyncManager` orchestrates: on a local change it reconciles the enabled categories into the
state and publishes; on an incoming replica it merges and writes the result back into the
local stores. Loop-free — a no-change merge neither re-applies nor re-publishes.

**Ordering invariant**: `start()` reconciles this device's own stores into the CRDT and
publishes *before* it subscribes to the peer's replica. Otherwise a replica arriving in the
gap would merge against an empty local `state`, and the write-back would clobber a local-only
entry the peer hasn't seen yet (e.g. a favourite added offline). Reconciling first means
every incoming merge already carries our local truth.

Pure and unit-tested (`SyncStateTest`, `SyncMessageTest`, `SyncManagerTest`). The
hardware-free **end-to-end** proof is `SyncConvergenceTest`: two real `SyncManager`s (a
"phone" and a "watch") wired by a crossed in-memory link, asserting that a change on either
device reaches the other, that both converge on the union, that messages relay both ways, and
that the exchange terminates (no re-publish ping-pong). It's the reliable stand-in for two
paired emulators — see below.

## Events

`SyncMessage` is a one-shot relay: `TrackerAlert`, `TetherLeft`, `TetherReturned`. The
phone's foreground service — better antenna, better battery — runs the continuous scan and
sends these so the **watch buzzes** without scanning itself. The watch receives them in a
`WearableListenerService` (delivered even when the watch app is closed).

## Settings

A **Sync** menu: a master switch plus one toggle per category (favourites, names,
left-behind, tracker alerts), so a user can share favourites but keep names private. Read
by `SyncManager` before publishing/applying each category (`SyncSettings`). Default: all on
except live scan-fusion (opt-in).

## Testing

Two layers, because each proves a different thing:

- **`SyncConvergenceTest` (unit, no hardware)** — the authoritative proof of the CRDT +
  transport *contract*: publish → merge → apply → re-publish converges and terminates. Runs
  in `make test`. This is what gates the release.
- **`scripts/sync-emu.sh` (two emulators)** — a smoke test of the *real Android link*. It
  boots a phone + Wear emulator, installs both apps, sideloads the Wear OS companion, and
  pairs them headlessly. Pairing is **not** BLE/netsim: Android Studio's "Pair Wearable"
  bridges the Wear Data Layer over a plain **TCP socket on port 5601**, so the rig reproduces
  that bridge (`adb reverse` on the watch + `adb forward` on the phone) and drives the
  companion's hidden `EmulatorActivity` entry through the GMS Terms-of-Service consent until
  the watch reports `companionDisconnected=false`. **Caveat**: this establishes the companion
  *link*, but full DataItem replication between the two sandboxed GMS instances over the
  loopback bridge is unreliable, so `make sync-emu-verify` is best-effort — treat the rig as a
  link smoke test, not a sync guarantee. The unit test is the source of truth.

## What's wired vs next

**Wired (Android, both directions):**
- State sync — favourites, **names**, tether set, **mutes** ("it's mine"), settings.
- The **Sync settings** menu (master + per-category, incl. opt-in scan fusion).
- Phone→watch alert/tether relay; the watch receiving relayed alerts; `peerNearby`.
- The watch **reflecting** synced names + favourites in its own device list (`WearController`
  overlays them).
- The watch's "you left your phone" now via **`CapabilityClient`** (`onCapabilityChanged` +
  `isNearby`) — the deprecated `BIND_LISTENER` is gone.
- **Scan fusion** (item 3): both devices relay throttled sighting snapshots
  (`SyncMessage.Sightings`, gated by the opt-in `SyncSettings.scans()`); the phone exposes
  the watch's extra sightings (`remoteSightings` / `watchOnlyCount`).

**Next:**
- **Apple**: still a no-op stub. A first shared-`appleMain` WCSession actual was tried and
  reverted — `WCSessionDelegate` can't be implemented in shared `appleMain` (its required
  `sessionDidBecomeInactive`/`sessionDidDeactivate` are **iOS-only**, absent on watchOS, and
  the `session:didReceive…:` methods collide as same-type Kotlin overloads). The real shape
  is **separate `iosMain` + `watchosMain` actuals**, each with its own delegate subclass
  (disambiguating the overloads via the interop), built/iterated in Xcode. Then the watchOS
  Swift app needs to *activate* sync, and the whole path needs on-device testing.
- Surface `remoteSightings` visually in the discovery list (data flows; no UI chip yet).
- Feed remote sightings into the safety correlator (deeper fusion than the current
  "combined view").
