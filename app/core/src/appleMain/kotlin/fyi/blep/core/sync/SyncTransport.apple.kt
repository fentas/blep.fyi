package fyi.blep.core.sync

/**
 * Apple backing is not wired yet — phone↔watch sync on Apple will use WatchConnectivity
 * (WCSession: updateApplicationContext for state, sendMessage for events, reachability for
 * presence). Stubbed so the framework builds; the Android Data Layer transport is live.
 */
actual fun createSyncTransport(): SyncTransport = NoopSyncTransport()
