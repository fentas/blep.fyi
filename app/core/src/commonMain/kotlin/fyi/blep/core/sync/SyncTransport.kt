package fyi.blep.core.sync

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

/**
 * The platform link between a user's phone and watch. Android backs it with the Wear Data
 * Layer (DataClient for converged [SyncState], MessageClient for one-shot [SyncMessage]s,
 * CapabilityClient for "is the peer nearby"); Apple will back it with WatchConnectivity.
 * A pure interface so [SyncManager] is testable with a fake.
 */
interface SyncTransport {
    /** Replace the shared state replica with [encoded] ([SyncState.encode]). Idempotent. */
    fun publishState(encoded: String)

    /** Encoded peer state replicas as they arrive / change. */
    val incomingState: Flow<String>

    /** Send a one-shot encoded [SyncMessage] to the peer (best-effort). */
    fun sendMessage(encoded: String)

    /** Encoded messages received from the peer. */
    val incomingMessages: Flow<String>

    /** Whether a peer (the other device) is currently reachable and nearby. */
    val peerNearby: Flow<Boolean>

    fun close() {}
}

/** Platform transport. Android = Wear Data Layer; Apple/JVM = a no-op until wired. */
expect fun createSyncTransport(): SyncTransport

/** A transport that does nothing — the JVM-test + (for now) Apple backing. */
class NoopSyncTransport : SyncTransport {
    override fun publishState(encoded: String) {}
    override val incomingState: Flow<String> = emptyFlow()
    override fun sendMessage(encoded: String) {}
    override val incomingMessages: Flow<String> = emptyFlow()
    override val peerNearby: Flow<Boolean> = flowOf(false)
}
