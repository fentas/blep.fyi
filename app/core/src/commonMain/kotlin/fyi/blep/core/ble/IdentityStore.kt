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
    private val ttlMs: Long = 14L * 24 * 60 * 60_000, // 14 days
    private val maxIdentities: Int = 500,
    private val now: () -> Long = ::epochMillis,
) {
    private class Rec(val id: String, var lastSeenMs: Long, var fingerprint: String?, val addresses: MutableSet<String>)

    private val records: MutableList<Rec> = parse(store.getString(KEY))

    init {
        if (records.removeAll { now() - it.lastSeenMs > ttlMs }) persist() // prune stale on load
    }

    /** The stable identity id owning [address], or null if we've never linked it. */
    fun identityOf(address: String): String? = records.firstOrNull { address in it.addresses }?.id

    /** Every address the device behind [address] has worn (so a rename/flag saved under
     *  any of them resolves to all of them). Just [address] itself if we've not linked it. */
    fun addressesFor(address: String): Set<String> =
        records.firstOrNull { address in it.addresses }?.addresses?.toSet() ?: setOf(address)

    /** Record that all of [addresses] belong to one device (merging any identities they
     *  already touch), with an optional [fingerprint]; refreshes the identity's last-seen. */
    fun link(addresses: Set<String>, fingerprint: String? = null) {
        if (addresses.isEmpty()) return
        val touched = records.filter { r -> r.addresses.any { it in addresses } }
        // Merge every identity these addresses touch into one (the lowest id survives —
        // stable + deterministic), then fold in the new addresses.
        val survivor = touched.minByOrNull { it.id } ?: Rec(addresses.min(), now(), fingerprint, mutableSetOf()).also { records += it }
        for (r in touched) if (r !== survivor) { survivor.addresses.addAll(r.addresses); records.remove(r) }
        survivor.addresses.addAll(addresses)
        survivor.lastSeenMs = now()
        if (fingerprint != null) survivor.fingerprint = fingerprint
        persist()
    }

    /** Refresh last-seen for the identities owning any of [addresses] (keeps them alive). */
    fun seen(addresses: Set<String>) {
        if (addresses.isEmpty()) return
        var changed = false
        val t = now()
        for (r in records) if (r.addresses.any { it in addresses }) { r.lastSeenMs = t; changed = true }
        if (changed) persist()
    }

    private fun persist() {
        records.removeAll { now() - it.lastSeenMs > ttlMs }
        if (records.size > maxIdentities) {
            records.sortByDescending { it.lastSeenMs }
            while (records.size > maxIdentities) records.removeAt(records.size - 1)
        }
        store.putString(KEY, records.joinToString("\n") { r ->
            "${r.id}\t${r.lastSeenMs}\t${r.fingerprint ?: ""}\t${r.addresses.joinToString(",")}"
        })
    }

    private fun parse(raw: String?): MutableList<Rec> {
        val out = ArrayList<Rec>()
        raw?.takeIf { it.isNotBlank() }?.split('\n')?.forEach { line ->
            val p = line.split('\t')
            if (p.size == 4) {
                val ls = p[1].toLongOrNull() ?: return@forEach
                val addrs = p[3].split(',').filter { it.isNotBlank() }.toMutableSet()
                if (addrs.isNotEmpty()) out += Rec(p[0], ls, p[2].ifBlank { null }, addrs)
            }
        }
        return out
    }

    private companion object {
        const val KEY = "devices.identities"
    }
}
