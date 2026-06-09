package fyi.blep.core.sync

/**
 * Apple backing is not wired yet. Phone↔watch sync on Apple will use WatchConnectivity
 * (WCSession: updateApplicationContext for state, sendMessage for events, reachability for
 * presence). A first WCSession attempt is parked because the delegate can't live in shared
 * `appleMain`: `WCSessionDelegate`'s required `sessionDidBecomeInactive`/`Deactivate` exist
 * only on iOS (not watchOS), and the `session:didReceive…:` methods collide as same-type
 * overloads in Kotlin — so it needs an `iosMain`/`watchosMain` split + Xcode iteration.
 * Stubbed so the framework builds; the Android Data Layer transport is live. See docs/sync.md.
 */
actual fun createSyncTransport(): SyncTransport = NoopSyncTransport()
