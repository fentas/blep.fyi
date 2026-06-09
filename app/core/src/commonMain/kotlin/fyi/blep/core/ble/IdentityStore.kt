package fyi.blep.core.ble

import fyi.blep.core.platform.KeyValueStore
import fyi.blep.core.platform.epochMillis

/**
 * Persisted map of BLE addresses → a stable internal **device identity**, so a rename
 * or flag saved against a device follows it across its rotating addresses *and*
 * survives an app restart.
 *
 * The live [RotationTracker] does the correlation each session; this records the
 * resulting address groupings (plus the payload fingerprint and last-seen time) so the
 * mapping persists. For the many devices that **don't** rotate, an identity is just its
 * one stable address — persisted and resolved trivially. For rotating ones it holds as
 * long as the device keeps showing an address we've already linked.
 *
 * Self-cleaning: an identity not seen within [ttlMs] (default 14 days) is dropped, so
 * the store can't grow without bound. Stored as newline-delimited records:
 * `<id>\t<lastSeenMs>\t<fingerprint>\t<addr,addr,…>`. Pure given a [KeyValueStore].
 */
class IdentityStore(
    private val store: KeyValueStore,
    ttlMs: Long = 14L * 24 * 60 * 60_000, // 14 days
    private val maxIdentities: Int = 500,
    private val now: () -> Long = ::epochMillis,
) {
    /** How long an unseen identity is kept before it's pruned. Live-settable (a user
     *  preference); the next prune/save applies it. */
    var ttlMs: Long = ttlMs
    private class Rec(
        val id: String,
        var firstSeenMs: Long,
        var lastSeenMs: Long,
        var fingerprint: String?,
        val addresses: MutableSet<String>,
        var probedAtMs: Long = 0L,       // 0 = never actively probed (so we probe it once)
        var probeLabel: String? = null,  // human label the probe found, if any
        var probeKey: String? = null,    // stable cross-rotation key from the probe, if any
        var probeDetail: String? = null, // packed ProbeResult (Device-info card + telemetry match)
    )

    private val records: MutableList<Rec> = parse(store.getString(KEY))

    init {
        if (records.removeAll { now() - it.lastSeenMs > ttlMs }) persist() // prune stale on load
    }

    /** The stable identity id owning [address], or null if we've never linked it. */
    fun identityOf(address: String): String? = records.firstOrNull { address in it.addresses }?.id

    /** Every address the device behind [address] has worn (so a rename/flag saved under
     *  any of them resolves to all of them). Just [address] itself if we've not seen it. */
    fun addressesFor(address: String): Set<String> =
        records.firstOrNull { address in it.addresses }?.addresses?.toSet() ?: setOf(address)

    /** When the device behind [address] was first seen (epoch ms), carried across its id
     *  rotations and persisted — so it doesn't reset when the live track ages out. */
    fun firstSeenOf(address: String): Long? = records.firstOrNull { address in it.addresses }?.firstSeenMs

    /** Record that all of [addresses] belong to one device (merging any identities they
     *  already touch), with an optional [fingerprint]; refreshes last-seen, keeps the
     *  earliest first-seen. */
    fun link(addresses: Set<String>, fingerprint: String? = null) {
        if (addresses.isEmpty()) return
        val t = now()
        val touched = records.filter { r -> r.addresses.any { it in addresses } }
        // Merge every identity these addresses touch into one (the lowest id survives —
        // stable + deterministic), then fold in the new addresses.
        val survivor = touched.minByOrNull { it.id } ?: Rec(addresses.min(), t, t, fingerprint, mutableSetOf()).also { records += it }
        for (r in touched) if (r !== survivor) {
            survivor.addresses.addAll(r.addresses)
            survivor.firstSeenMs = minOf(survivor.firstSeenMs, r.firstSeenMs)
            if (survivor.probedAtMs == 0L && r.probedAtMs > 0L) { // carry a probe result across the merge
                survivor.probedAtMs = r.probedAtMs
                survivor.probeLabel = r.probeLabel
                survivor.probeKey = r.probeKey
                survivor.probeDetail = r.probeDetail
            }
            records.remove(r)
        }
        survivor.addresses.addAll(addresses)
        survivor.lastSeenMs = t
        if (fingerprint != null) survivor.fingerprint = fingerprint
        persist()
    }

    /** Has the device behind [address] already been actively probed (so we don't
     *  re-poke it)? A non-connectable result still counts — that's a stable trait. */
    fun isProbed(address: String): Boolean =
        records.firstOrNull { address in it.addresses }?.let { it.probedAtMs > 0L } ?: false

    /** The human label a probe found for this device, if any (e.g. "Pixel Buds Pro"). */
    fun probeLabelOf(address: String): String? =
        records.firstOrNull { address in it.addresses }?.probeLabel

    /** The packed probe detail for this device's identity (for the Device-info card),
     *  or null if never probed. Survives a restart. */
    fun probeDetailOf(address: String): String? =
        records.firstOrNull { address in it.addresses }?.probeDetail

    /**
     * Cache the outcome of an active probe of [address] (persisted, so it survives a
     * restart and an interval scan can match it). Re-correlates to a *different* identity:
     *  - by a stable key (a serial, or a personalised name) — definitive; or
     *  - failing that, by the **structural fingerprint + temporally-consistent battery**:
     *    the same model whose battery ticked down a hair within a short window is almost
     *    certainly the same unit (the user's "temporal glue"). Heuristic, so it's
     *    deliberately conservative (a tiny drop, a short window).
     * Either way it recovers a rotation the RSSI handover lost.
     */
    fun recordProbe(address: String, result: ProbeResult) {
        val t = now()
        val key = result.identityKey
        val twin = if (key != null) records.firstOrNull { address !in it.addresses && it.probeKey == key }
        else matchByTelemetry(address, result, t)
        if (twin != null) link(twin.addresses + address) // merge; survivor = lowest id
        val rec = records.firstOrNull { address in it.addresses }
            ?: Rec(address, t, t, null, mutableSetOf(address)).also { records += it }
        rec.probedAtMs = t
        rec.probeLabel = result.label
        rec.probeKey = key
        rec.probeDetail = result.pack()
        persist()
    }

    /** A keyless re-correlation: same GATT structure + a battery that only ticked down a
     *  little since a recent probe of a different identity ⇒ the same physical device.
     *  Requires both (structure alone is a model, not a unit; battery is the temporal id). */
    private fun matchByTelemetry(address: String, result: ProbeResult, t: Long): Rec? {
        val struct = result.structure ?: return null
        val batt = result.batteryPct ?: return null
        return records.firstOrNull { r ->
            if (address in r.addresses || r.probeDetail == null) return@firstOrNull false
            val other = ProbeResult.unpack(r.probeDetail!!)
            val otherBatt = other.batteryPct ?: return@firstOrNull false
            other.structure == struct && batt <= otherBatt && otherBatt - batt <= MAX_BATTERY_DROP &&
                (t - r.probedAtMs) in 1..TELEMETRY_WINDOW_MS
        }
    }

    /** Note that [addresses] are present now: refresh last-seen for ones we know, and
     *  start tracking first-seen for ones we don't (so even a non-rotating device gets a
     *  stable "first seen" that survives the live track ageing out or a restart). */
    fun seen(addresses: Set<String>) {
        if (addresses.isEmpty()) return
        val t = now()
        val known = records.filter { r -> r.addresses.any { it in addresses } }
        for (r in known) r.lastSeenMs = t
        val seenAddrs = known.flatMap { it.addresses }.toHashSet()
        val fresh = addresses.filter { it !in seenAddrs }
        for (a in fresh) records += Rec(a, t, t, null, mutableSetOf(a))
        persist()
    }

    private fun persist() {
        records.removeAll { now() - it.lastSeenMs > ttlMs }
        if (records.size > maxIdentities) {
            records.sortByDescending { it.lastSeenMs }
            while (records.size > maxIdentities) records.removeAt(records.size - 1)
        }
        store.putString(KEY, records.joinToString("\n") { r ->
            "${r.id}\t${r.firstSeenMs}\t${r.lastSeenMs}\t${r.fingerprint ?: ""}\t${r.addresses.joinToString(",")}" +
                "\t${r.probedAtMs}\t${sanitize(r.probeLabel)}\t${sanitize(r.probeKey)}\t${sanitize(r.probeDetail)}"
        })
    }

    /** Strip the field/record separators a probed name might contain. */
    private fun sanitize(s: String?): String = s.orEmpty().replace('\t', ' ').replace('\n', ' ')

    private fun parse(raw: String?): MutableList<Rec> {
        val out = ArrayList<Rec>()
        raw?.takeIf { it.isNotBlank() }?.split('\n')?.forEach { line ->
            val p = line.split('\t')
            if (p.size >= 5) { // 5 = pre-probe records; 8 = with probe cache
                val fs = p[1].toLongOrNull() ?: return@forEach
                val ls = p[2].toLongOrNull() ?: return@forEach
                val addrs = p[4].split(',').filter { it.isNotBlank() }.toMutableSet()
                if (addrs.isEmpty()) return@forEach
                val rec = Rec(p[0], fs, ls, p[3].ifBlank { null }, addrs)
                if (p.size >= 8) {
                    rec.probedAtMs = p[5].toLongOrNull() ?: 0L
                    rec.probeLabel = p[6].ifBlank { null }
                    rec.probeKey = p[7].ifBlank { null }
                }
                if (p.size >= 9) rec.probeDetail = p[8].ifBlank { null }
                out += rec
            }
        }
        return out
    }

    private companion object {
        const val KEY = "devices.identities"
        const val MAX_BATTERY_DROP = 2        // %: a keyless telemetry match tolerates only a tiny drop
        const val TELEMETRY_WINDOW_MS = 15L * 60_000 // …within this since the other id was probed
    }
}
