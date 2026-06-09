package fyi.blep.core.sync

/** No peer on the JVM test target. */
actual fun createSyncTransport(): SyncTransport = NoopSyncTransport()
