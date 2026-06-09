package fyi.blep.core.sync

import fyi.blep.core.platform.epochMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Reads the local converged state from this device's stores (favourites, tether, …). */
interface SyncSource {
    fun favorites(): Set<String>
    fun tethered(): Set<String>
    fun muted(): Set<String>
    fun aliases(): Map<String, String>
    /** Synced settings as name→value (e.g. sensitivity, tether direction). */
    fun settings(): Map<String, String>
}

/** Writes merged peer state back into this device's stores + handles relayed events. */
interface SyncSink {
    fun applyFavorites(ids: Set<String>)
    fun applyTethered(ids: Set<String>)
    fun applyMuted(ids: Set<String>)
    fun applyAliases(map: Map<String, String>)
    fun applySetting(name: String, value: String)
    fun onMessage(msg: SyncMessage)
    fun onPeerNearby(nearby: Boolean) {}
}

/**
 * Keeps the phone and watch in step. Holds the merged [SyncState]; on a local change it
 * reconciles the enabled categories into the state and publishes; on an incoming peer
 * replica it merges (last-write-wins) and writes the result back into the local stores.
 * Convergence is loop-free: [SyncState.merge] is idempotent and a no-change merge neither
 * re-applies nor re-publishes. Every category is gated by [SyncSettings].
 */
class SyncManager(
    private val transport: SyncTransport,
    private val settings: SyncSettings,
    private val source: SyncSource,
    private val sink: SyncSink,
    private val scope: CoroutineScope,
    private val now: () -> Long = ::epochMillis,
) {
    private var state = SyncState.EMPTY
    private val jobs = mutableListOf<Job>()
    private val _peerNearby = MutableStateFlow(false)
    val peerNearby: StateFlow<Boolean> = _peerNearby

    /** (Re)start syncing. Idempotent — stops any prior collectors first; a no-op while the
     *  master switch is off. Call again after toggling the master switch on. */
    fun start() {
        stop()
        if (!settings.enabled()) return
        // Seed our own local state into the CRDT and publish it BEFORE subscribing to the
        // peer's replica. Order matters: if a remote replica arrives before we've reconciled
        // our local stores, onIncoming would merge it against an empty `state` and write the
        // result back over our own un-reconciled local entries (e.g. a favourite the peer
        // doesn't know yet). Reconciling first means every incoming merge already carries our
        // local truth, so nothing local is clobbered.
        localChanged() // publish our starting state first
        jobs += scope.launch { transport.incomingState.collect(::onIncoming) }
        jobs += scope.launch { transport.incomingMessages.collect { SyncMessage.decode(it)?.let(sink::onMessage) } }
        jobs += scope.launch {
            transport.peerNearby.collect {
                _peerNearby.value = it
                sink.onPeerNearby(it)
            }
        }
    }

    fun stop() {
        jobs.forEach { it.cancel() }
        jobs.clear()
    }

    /** Call after the user changes anything synced locally (favourite, rename, tether…). */
    fun localChanged() {
        if (!settings.enabled()) return
        val n = now()
        var s = state
        if (settings.favorites()) s = s.withSet(Section.FAVORITES, source.favorites(), n)
        if (settings.tethered()) s = s.withSet(Section.TETHERED, source.tethered(), n)
        if (settings.mutes()) s = s.withSet(Section.MUTED, source.muted(), n)
        if (settings.names()) s = s.withNames(source.aliases(), n)
        if (settings.settings()) for ((k, v) in source.settings()) s = s.withSetting(k, v, n)
        if (s != state) {
            state = s
            transport.publishState(s.encode())
        }
    }

    /** Relay a one-shot event to the peer (e.g. phone's scan → buzz the watch). */
    fun send(msg: SyncMessage) {
        if (settings.enabled() && settings.alerts()) transport.sendMessage(msg.encode())
    }

    /** Relay a scan snapshot (scan fusion) — gated by the separate, opt-in "scans" toggle. */
    fun sendSightings(devices: List<Sighting>) {
        if (settings.enabled() && settings.scans()) transport.sendMessage(SyncMessage.Sightings(devices).encode())
    }

    private fun onIncoming(encoded: String) {
        if (!settings.enabled()) return
        val merged = state.merge(SyncState.decode(encoded))
        if (merged == state) return
        state = merged
        if (settings.favorites()) sink.applyFavorites(merged.favoriteIds())
        if (settings.tethered()) sink.applyTethered(merged.tetheredIds())
        if (settings.mutes()) sink.applyMuted(merged.mutedIds())
        if (settings.names()) sink.applyAliases(merged.aliasMap())
        if (settings.settings()) for ((k, e) in merged.settings) sink.applySetting(k, e.value)
        transport.publishState(merged.encode()) // hand our merge back so the peer converges too
    }
}
