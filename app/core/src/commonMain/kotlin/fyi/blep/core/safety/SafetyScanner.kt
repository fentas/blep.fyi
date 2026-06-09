package fyi.blep.core.safety

import fyi.blep.core.ble.BleScanner
import fyi.blep.core.ble.IdentityStore
import fyi.blep.core.ble.ProbeResult
import fyi.blep.core.platform.epochMillis
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Runs the "is something tracking me?" scan: feeds the scanner's raw advertisement
 * stream through the [TrackerClassifier] into the [TrackerDetector], and emits the
 * current set of suspected-tracker [TrackerAlert]s. Cold — collecting starts the
 * scan, cancelling stops it.
 *
 * When the user has opted into [history], alerts are also recorded and then enriched
 * with cross-session context: a tracker seen close across several separate hours is
 * promoted to a full ALERT, because that persistence is what a single live scan can't
 * see.
 *
 * When an [identityStore] is supplied it adds an **active-identity** layer: close
 * suspects are probed (one short GATT connect), their stable identity (serial / GATT
 * structure / battery) is persisted, and a device whose identity keeps recurring across
 * checks even though its address rotated is surfaced as [AlertReason.PERSISTENT]. This
 * is the only way an **interval** scanner (the watch) can catch a rotating follower — it
 * never sees the continuous churn, so it leans on the probe's rotation-stable id instead.
 */
class SafetyScanner(
    private val scanner: BleScanner,
    private val detector: TrackerDetector = TrackerDetector(),
    private val history: SafetyHistory? = null,
    private val identityStore: IdentityStore? = null,
    private val nowEpochMs: () -> Long = ::epochMillis,
    // Coarse on-device place cell for the current location, or null (location-aware
    // detection off / no permission). Sampled when an encounter is recorded.
    private val place: () -> String? = { null },
    // A device whose persisted identity has been around at least this long (across its
    // rotations) is treated as following you.
    private val followerWindowMs: Long = 90 * 60_000L,
) {
    /** Addresses the user marked "it's mine" — never observed, never alerted. */
    private val muted: MutableSet<String> = history?.mutedAddresses()?.toMutableSet() ?: mutableSetOf()

    /** Serialises identity-store access between this collector and the background probe. */
    private val storeLock = Mutex()

    fun reset() = detector.reset()

    /** Mark a tracker as the user's own so it stops being flagged (persisted). */
    fun mute(address: String) {
        muted += address
        history?.mute(address)
    }

    /** Reverse a [mute] — the address is observed and alerted on again. */
    fun unmute(address: String) {
        muted -= address
        history?.unmute(address)
    }

    fun alerts(): Flow<List<TrackerAlert>> = channelFlow {
        send(emptyList())
        val closePresent = HashMap<String, Int>() // close address (seen recently) -> latest rssi
        val closeSeenMs = HashMap<String, Long>()
        var probing = false
        var lastProbeMs = 0L
        var lastSeenSyncMs = 0L

        scanner.advertisements().collect { adv ->
            val now = adv.timeMs
            if (adv.address !in muted) detector.observe(TrackerClassifier.classify(adv))
            if (adv.rssi >= detector.closeDbm && adv.address !in muted) {
                closePresent[adv.address] = adv.rssi
                closeSeenMs[adv.address] = now
            }
            // Drop devices we haven't heard from recently — "present" means present now.
            closeSeenMs.entries.removeAll { now - it.value > CLOSE_PRESENT_MS }
            closePresent.keys.retainAll(closeSeenMs.keys)

            val here = place()
            val context = listOfNotNull(here, detector.backdropFingerprint(now))
                .joinToString("|").ifBlank { null }
            val base = enrich(detector.evaluate(now), here, context)

            val store = identityStore
            // All IdentityStore access (here + the background probe) is serialised by
            // storeLock: on the watch the flow runs on Dispatchers.Default, so the probe
            // child and this collector can be on different threads, racing the store's
            // plain MutableList. The lock gives mutual exclusion without changing dispatchers.
            val persistent = if (store != null) {
                storeLock.withLock {
                    if (now - lastSeenSyncMs > SEEN_SYNC_MS && closePresent.isNotEmpty()) {
                        lastSeenSyncMs = now
                        store.seen(closePresent.keys.toSet()) // persist first-seen across checks/sessions
                    }
                    persistentTrackerAlerts(
                        store, closePresent, nowEpochMs(), followerWindowMs, muted,
                        excluded = base.mapNotNull { it.trackingAddress }.toSet(),
                    )
                }
            } else {
                emptyList()
            }

            send((base + persistent).filter { it.trackingAddress !in muted })

            // Probe one close, unprobed, un-muted suspect — one at a time, gently — so its
            // rotation-stable identity is learned + persisted (re-linking it next time).
            if (store != null && !probing && now - lastProbeMs > PROBE_GAP_MS) {
                val target = storeLock.withLock { closePresent.keys.firstOrNull { it !in muted && !store.isProbed(it) } }
                if (target != null) {
                    probing = true
                    lastProbeMs = now
                    launch {
                        try {
                            val r = runCatching { scanner.probe(target) }.getOrNull() ?: ProbeResult(connectable = false)
                            storeLock.withLock { runCatching { store.recordProbe(target, r) } } // a store failure mustn't cancel the scan
                        } finally {
                            probing = false // always clear, even if the probe/record threw
                        }
                    }
                }
            }
        }
    }

    private fun enrich(alerts: List<TrackerAlert>, place: String?, context: String?): List<TrackerAlert> {
        val log = history?.takeIf { it.enabled() } ?: return alerts
        val now = nowEpochMs()
        return alerts.map { alert ->
            log.record(alert.kind, now, place, context)
            val cross = log.crossSession(alert.kind, now)
            if (cross != null && promote(cross)) {
                // Promote + carry the hour/place counts; the UI appends the localized sentence.
                alert.copy(
                    severity = Severity.ALERT,
                    crossSessionHours = cross.distinctHours,
                    crossSessionPlaces = cross.distinctPlaces,
                )
            } else {
                alert
            }
        }
    }

    /**
     * Promote a live sighting to ALERT on cross-session evidence — but key on context
     * *diversity*, not raw recurrence (docs/detection.md):
     *  - moved across distinct places, or recurred across ≥2 contexts beyond your
     *    learned baseline ⇒ following you;
     *  - recurrence within a *single* known context (your home/desk) is routine, so
     *    the hour-based [CrossSession.persistent] signal is suppressed there;
     *  - with no context signal at all (location off, no stable backdrop) we can't
     *    tell, so the hour fallbacks still apply.
     */
    private fun promote(cross: CrossSession): Boolean {
        val routine = cross.distinctContexts == 1 // everything seen in one known context
        return cross.multiPlace || cross.diverse || cross.veryPersistent || (cross.persistent && !routine)
    }

    private companion object {
        const val CLOSE_PRESENT_MS = 30_000L // seen within this ⇒ "present now"
        const val SEEN_SYNC_MS = 20_000L     // how often to persist first-seen
        const val PROBE_GAP_MS = 8_000L      // min gap between probes (one at a time + cooldown)
    }
}

/**
 * Persistent-identity follower alerts: a close, present device whose **identity** (via the
 * active-probe re-correlation in [store]) has been around ≥ [followerWindowMs] *and* has
 * worn more than one address — i.e. it rotated its id but we re-linked it by serial /
 * structure / battery. An interval scan can't see that from the MAC alone. One alert per
 * identity; muted devices and those already in the base alert set are excluded.
 */
internal fun persistentTrackerAlerts(
    store: IdentityStore,
    present: Map<String, Int>, // close present address -> rssi
    nowEpochMs: Long,
    followerWindowMs: Long,
    muted: Set<String>,
    excluded: Set<String>,
): List<TrackerAlert> = present.entries
    .mapNotNull { (addr, rssi) ->
        if (addr in muted || addr in excluded) return@mapNotNull null
        val firstSeen = store.firstSeenOf(addr) ?: return@mapNotNull null
        val age = nowEpochMs - firstSeen
        if (age < followerWindowMs || store.addressesFor(addr).size <= 1) return@mapNotNull null
        TrackerAlert(
            severity = Severity.ALERT, kind = TrackerKind.UNKNOWN, reason = AlertReason.PERSISTENT,
            rssi = rssi, trackingAddress = addr, durationMs = age, label = store.probeLabelOf(addr),
        )
    }
    .distinctBy { store.identityOf(it.trackingAddress!!) ?: it.trackingAddress }
