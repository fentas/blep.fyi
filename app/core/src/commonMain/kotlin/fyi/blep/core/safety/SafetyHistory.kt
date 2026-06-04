package fyi.blep.core.safety

import fyi.blep.core.platform.KeyValueStore

/** What the persisted log says about a tracker kind seen across separate times. */
data class CrossSession(
    val kind: TrackerKind,
    val distinctHours: Int, // number of separate clock-hours it was seen close
    val firstMs: Long,
    val lastMs: Long,
) {
    /** Seen close across several separated hours ⇒ likely travelling with you, not
     *  a stranger you happened to pass. */
    val persistent: Boolean get() = distinctHours >= 3
}

/**
 * Opt-in cross-session memory for the safety scan. A single live scan only sees the
 * last few minutes — but a tracker planted on you gives itself away by reappearing
 * close **across separate hours**. We persist a small rolling log of close encounters
 * (tracker *kind* + time only — no device identity, no location) so the next scan can
 * say "this Find My tracker has been near you across 4 separate hours today", which
 * one session can't know on its own.
 *
 * Pure given a [KeyValueStore] and an explicit epoch clock, so it unit-tests without
 * any platform time source.
 */
class SafetyHistory(
    private val store: KeyValueStore,
    private val retentionMs: Long = 7 * 24 * 60 * 60_000L, // a week — stalking plays out across days
    private val minGapMs: Long = 5 * 60_000L,              // throttle: ≤1 record per kind / 5 min
    private val maxEntries: Int = 1000,                    // ~17 KB worst case; storage is a non-issue
    private val maxMuted: Int = 200,                       // bound the "it's mine" list (rotating tags churn it)
) {
    // On by default — the log never leaves the device, so there's no privacy cost,
    // and cross-session correlation is the feature's edge. Users can turn it off.
    fun enabled(): Boolean = store.getBoolean(KEY_ENABLED, true)
    fun setEnabled(on: Boolean) {
        store.putBoolean(KEY_ENABLED, on)
        if (!on) clear()
    }

    /** Records a close encounter with a recognised tracker kind (throttled, pruned). */
    fun record(kind: TrackerKind, nowEpochMs: Long) {
        if (kind == TrackerKind.UNKNOWN) return
        val kept = load().filter { nowEpochMs - it.epochMs <= retentionMs }
        val lastForKind = kept.filter { it.kind == kind }.maxOfOrNull { it.epochMs }
        if (lastForKind != null && nowEpochMs - lastForKind < minGapMs) return
        save((kept + Encounter(kind, nowEpochMs)).takeLast(maxEntries))
    }

    /** Cross-session context for [kind], or null if it's never been logged. */
    fun crossSession(kind: TrackerKind, nowEpochMs: Long): CrossSession? {
        val mine = load().filter { it.kind == kind && nowEpochMs - it.epochMs <= retentionMs }
        if (mine.isEmpty()) return null
        val hours = mine.map { it.epochMs / 3_600_000L }.toHashSet().size
        return CrossSession(kind, hours, mine.minOf { it.epochMs }, mine.maxOf { it.epochMs })
    }

    fun clear() = store.putString(KEY_LOG, "")

    // --- "It's mine" mute list ----------------------------------------------
    // Persisted addresses the user marked as their own — note these *are* device
    // identifiers (the user's own trackers), unlike the identity-free encounter log;
    // they stay on-device and are bounded. Best-effort: a tag that rotates its
    // address reappears under a new one (that rotation is the very thing the detector
    // exists to catch, and is why the list is capped), so the UI says so. Permanent
    // for stable-MAC trackers (many Tiles, SmartTags, headphones, fixed beacons).

    fun mutedAddresses(): Set<String> = readLines(KEY_MUTED).toCollection(LinkedHashSet())

    fun mute(address: String) {
        if (address.isBlank()) return
        // Newest-wins, capped — a rotating tag the user keeps re-muting can't grow it unbounded.
        writeLines(KEY_MUTED, (mutedAddresses() + address).toList().takeLast(maxMuted))
    }

    fun unmute(address: String) = writeLines(KEY_MUTED, (mutedAddresses() - address).toList())

    private fun load(): List<Encounter> = readLines(KEY_LOG).mapNotNull { line ->
        val p = line.split(':')
        if (p.size != 2) return@mapNotNull null
        val ord = p[0].toIntOrNull() ?: return@mapNotNull null
        val ms = p[1].toLongOrNull() ?: return@mapNotNull null
        TrackerKind.entries.getOrNull(ord)?.let { Encounter(it, ms) }
    }

    private fun save(list: List<Encounter>) =
        writeLines(KEY_LOG, list.map { "${it.kind.ordinal}:${it.epochMs}" })

    /** Newline-delimited string codec shared by the encounter log and the mute list. */
    private fun readLines(key: String): List<String> =
        store.getString(key)?.takeIf { it.isNotBlank() }?.split('\n')?.filter { it.isNotBlank() }.orEmpty()

    private fun writeLines(key: String, lines: List<String>) = store.putString(key, lines.joinToString("\n"))

    private data class Encounter(val kind: TrackerKind, val epochMs: Long)

    private companion object {
        const val KEY_ENABLED = "safety.remember"
        const val KEY_LOG = "safety.encounters"
        const val KEY_MUTED = "safety.muted"
    }
}
