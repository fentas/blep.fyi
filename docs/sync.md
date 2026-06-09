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

`SyncManager` orchestrates: on a local change it reconciles the enabled categories into the
state and publishes; on an incoming replica it merges and writes the result back into the
local stores. Loop-free — a no-change merge neither re-applies nor re-publishes. Pure and
unit-tested (`SyncStateTest`, `SyncMessageTest`, `SyncManagerTest`).

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

## What's wired vs next

**Wired (Android):** state sync (favourites, names, tether set, settings) phone-side; the
Sync settings menu; phone→watch alert/tether relay; `peerNearby`; the watch receiving
relayed alerts; the watch's "you left your phone" via the companion link.

**Next:**
- The **watch reflecting** synced names/favourites in its own list (it receives the state;
  the wear UI doesn't overlay it yet).
- **Scan fusion** (item 3): relay throttled sighting snapshots both ways and merge for
  better coverage + rotation correlation. `SyncSettings.scans()` gates it (opt-in, off by
  default) — the message + manager hooks are in place.
- **Mutes** ("it's mine") in the converged state (the `SyncState.muted` field exists; not
  wired to `SafetyHistory` yet).
- **Apple**: the WatchConnectivity `actual` of `SyncTransport`, on iOS + watchOS.
- Migrate the legacy `BIND_LISTENER` "left phone" listener to `CapabilityClient`
  (the capability is already declared in `res/values/wear.xml` on both apps).
