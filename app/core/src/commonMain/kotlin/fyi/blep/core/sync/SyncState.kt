package fyi.blep.core.sync

/** One last-write-wins datum: a value + the epoch-ms it was set. Newer [ts] wins on merge. */
internal data class Lww<V>(val value: V, val ts: Long)

/**
 * The convergent slice of state a user's phone and watch share — favourites, the tether
 * set, "it's mine" mutes, device names (aliases), and a few settings. Modelled as
 * last-write-wins maps (a small CRDT): [merge] is commutative, associative and idempotent,
 * so any two peers converge regardless of the order or duplication of syncs. Removals are
 * real — a tombstone (`false` / blank) carrying a newer ts — so un-favouriting on the phone
 * actually un-favourites on the watch instead of being resurrected by an old replica.
 *
 * Pure and dependency-free, so it unit-tests without any platform transport.
 */
class SyncState internal constructor(
    internal val favorites: Map<String, Lww<Boolean>> = emptyMap(),
    internal val tethered: Map<String, Lww<Boolean>> = emptyMap(),
    internal val muted: Map<String, Lww<Boolean>> = emptyMap(),
    internal val names: Map<String, Lww<String>> = emptyMap(), // "" = cleared (tombstone)
    internal val settings: Map<String, Lww<String>> = emptyMap(),
) {
    // ── reads ────────────────────────────────────────────────────────────────
    fun favoriteIds(): Set<String> = favorites.filterValues { it.value }.keys
    fun tetheredIds(): Set<String> = tethered.filterValues { it.value }.keys
    fun mutedIds(): Set<String> = muted.filterValues { it.value }.keys
    fun aliasMap(): Map<String, String> = names.entries.filter { it.value.value.isNotBlank() }.associate { it.key to it.value.value }
    fun setting(name: String): String? = settings[name]?.value

    /** Highest timestamp anywhere in the state — the Lamport floor for the next local
     *  write: stamping above it means a peer with a fast clock can't out-rank an edit
     *  that causally happened *after* its replica was seen. */
    fun maxTs(): Long = sequenceOf(favorites, tethered, muted, names, settings)
        .flatMap { it.values.asSequence() }.maxOfOrNull { it.ts } ?: 0L

    // ── local edits (bump ts only on real change, so we never clobber a peer) ──
    fun withSet(section: Section, ids: Set<String>, nowMs: Long): SyncState {
        val cur = section.pick(this)
        val out = cur.toMutableMap()
        for (id in ids) if (cur[id]?.value != true) out[id] = Lww(true, nowMs)
        for ((id, e) in cur) if (e.value && id !in ids) out[id] = Lww(false, nowMs)
        return section.put(this, out)
    }

    fun withNames(aliases: Map<String, String>, nowMs: Long): SyncState {
        val out = names.toMutableMap()
        for ((id, name) in aliases) if (names[id]?.value != name) out[id] = Lww(name, nowMs)
        for ((id, e) in names) if (e.value.isNotBlank() && id !in aliases) out[id] = Lww("", nowMs)
        return SyncState(favorites, tethered, muted, out, settings)
    }

    fun withSetting(name: String, value: String, nowMs: Long): SyncState {
        if (settings[name]?.value == value) return this
        return SyncState(favorites, tethered, muted, names, settings + (name to Lww(value, nowMs)))
    }

    // ── merge (the CRDT join) ──────────────────────────────────────────────────
    fun merge(other: SyncState) = SyncState(
        mergeMap(favorites, other.favorites),
        mergeMap(tethered, other.tethered),
        mergeMap(muted, other.muted),
        mergeMap(names, other.names),
        mergeMap(settings, other.settings),
    )

    // ── serialisation: one tab-separated, section-tagged line per entry ────────
    fun encode(): String = buildList {
        favorites.forEach { (k, e) -> add("F\t$k\t${e.ts}\t${if (e.value) "1" else "0"}") }
        tethered.forEach { (k, e) -> add("T\t$k\t${e.ts}\t${if (e.value) "1" else "0"}") }
        muted.forEach { (k, e) -> add("M\t$k\t${e.ts}\t${if (e.value) "1" else "0"}") }
        names.forEach { (k, e) -> add("A\t$k\t${e.ts}\t${e.value.clean()}") }
        settings.forEach { (k, e) -> add("S\t$k\t${e.ts}\t${e.value.clean()}") }
    }.joinToString("\n")

    override fun equals(other: Any?) = other is SyncState &&
        favorites == other.favorites && tethered == other.tethered && muted == other.muted &&
        names == other.names && settings == other.settings

    override fun hashCode(): Int = favorites.hashCode() * 31 + names.hashCode()

    companion object {
        val EMPTY = SyncState()

        fun decode(raw: String): SyncState {
            if (raw.isBlank()) return EMPTY
            val fav = HashMap<String, Lww<Boolean>>()
            val teth = HashMap<String, Lww<Boolean>>()
            val mut = HashMap<String, Lww<Boolean>>()
            val nm = HashMap<String, Lww<String>>()
            val st = HashMap<String, Lww<String>>()
            for (line in raw.split('\n')) {
                val p = line.split('\t', limit = 4)
                if (p.size < 4) continue
                val key = p[1]
                val ts = p[2].toLongOrNull() ?: continue
                if (key.isEmpty()) continue
                when (p[0]) {
                    "F" -> fav[key] = Lww(p[3] == "1", ts)
                    "T" -> teth[key] = Lww(p[3] == "1", ts)
                    "M" -> mut[key] = Lww(p[3] == "1", ts)
                    "A" -> nm[key] = Lww(p[3], ts)
                    "S" -> st[key] = Lww(p[3], ts)
                }
            }
            return SyncState(fav, teth, mut, nm, st)
        }

        private fun <V> mergeMap(a: Map<String, Lww<V>>, b: Map<String, Lww<V>>): Map<String, Lww<V>> {
            if (a.isEmpty()) return b
            if (b.isEmpty()) return a
            val out = HashMap<String, Lww<V>>(a)
            for ((k, y) in b) {
                val x = out[k]
                if (x == null || wins(y, x)) out[k] = y
            }
            return out
        }

        /** True LWW order. Ties (same ts, different value — a same-millisecond concurrent
         *  edit) break on the value's string form, so the merge stays commutative: without
         *  this each side would keep its *own* entry and the two replicas never converge. */
        private fun <V> wins(y: Lww<V>, x: Lww<V>): Boolean =
            y.ts > x.ts || (y.ts == x.ts && y.value.toString() > x.value.toString())

        private fun String.clean() = replace('\t', ' ').replace('\n', ' ')
    }
}

/** The three boolean-set sections, so [SyncState.withSet] can be generic over them. */
enum class Section {
    FAVORITES, TETHERED, MUTED;

    internal fun pick(s: SyncState): Map<String, Lww<Boolean>> = when (this) {
        FAVORITES -> s.favorites; TETHERED -> s.tethered; MUTED -> s.muted
    }

    internal fun put(s: SyncState, m: Map<String, Lww<Boolean>>): SyncState = when (this) {
        FAVORITES -> SyncState(m, s.tethered, s.muted, s.names, s.settings)
        TETHERED -> SyncState(s.favorites, m, s.muted, s.names, s.settings)
        MUTED -> SyncState(s.favorites, s.tethered, m, s.names, s.settings)
    }
}
