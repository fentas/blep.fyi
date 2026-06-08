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
    private class Rec(val id: String, var firstSeenMs: Long, var lastSeenMs: Long, var fingerprint: String?, val addresses: MutableSet<String>)

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
            records.remove(r)
        }
        survivor.addresses.addAll(addresses)
        survivor.lastSeenMs = t
        if (fingerprint != null) survivor.fingerprint = fingerprint
        persist()
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
            "${r.id}\t${r.firstSeenMs}\t${r.lastSeenMs}\t${r.fingerprint ?: ""}\t${r.addresses.joinToString(",")}"
        })
    }

    private fun parse(raw: String?): MutableList<Rec> {
        val out = ArrayList<Rec>()
        raw?.takeIf { it.isNotBlank() }?.split('\n')?.forEach { line ->
            val p = line.split('\t')
            if (p.size == 5) {
                val fs = p[1].toLongOrNull() ?: return@forEach
                val ls = p[2].toLongOrNull() ?: return@forEach
                val addrs = p[4].split(',').filter { it.isNotBlank() }.toMutableSet()
                if (addrs.isNotEmpty()) out += Rec(p[0], fs, ls, p[3].ifBlank { null }, addrs)
            }
        }
        return out
    }

    private companion object {
        const val KEY = "devices.identities"
    }
}
