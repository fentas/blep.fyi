package fyi.blep.core.safety

import fyi.blep.core.platform.KeyValueStore

/** What the persisted log says about a tracker kind seen across separate times. */
data class CrossSession(
    val kind: TrackerKind,
    val distinctHours: Int, // number of separate clock-hours it was seen close
    val firstMs: Long,
    val lastMs: Long,
    val distinctPlaces: Int = 0,      // distinct coarse places (0 if location-aware off)
    val distinctContexts: Int = 0,    // distinct contexts (place × stable-crowd backdrop)
    val nonBaselineContexts: Int = 0, // distinct contexts excluding your learned baseline
) {
    /** Seen close across several separated hours ⇒ likely travelling with you, not
     *  a stranger you happened to pass. (Raw recurrence over-fires on routine
     *  co-presence by itself, so the scanner gates it on context — see [diverse].) */
    val persistent: Boolean get() = distinctHours >= 3

    /** Seen near you in ≥2 distinct places — a stationary device you merely pass
     *  stays in one place, so moving across places is a strong "following" signal. */
    val multiPlace: Boolean get() = distinctPlaces >= 2

    /** Recurs across ≥2 contexts *beyond* your routine baseline (the auto-learned
     *  "usually around me"). A real follower spans unrelated contexts — home **and**
     *  the train **and** somewhere new; your commuter neighbour shares only one.
     *  The GPS-free diversity signal from docs/detection.md. */
    val diverse: Boolean get() = nonBaselineContexts >= 2

    /** Seen across so many separate hours that it's suspicious even with no context
     *  signal at all (location off, no stable backdrop) — a last-resort fallback. */
    val veryPersistent: Boolean get() = distinctHours >= 6
}

/**
 * Opt-in cross-session memory for the safety scan. A single live scan only sees the
 * last few minutes — but a tracker planted on you gives itself away by reappearing
 * close **across separate hours** — and, more tellingly, across *unrelated contexts*.
 * We persist a small rolling log of close encounters (tracker *kind* + time, plus an
 * optional coarse place and a hashed context signature — never a raw device identity)
 * so the next scan can say "this Find My tracker has been near you across 4 separate
 * hours and 3 contexts today", which one session can't know on its own.
 *
 * Pure given a [KeyValueStore] and an explicit epoch clock, so it unit-tests without
 * any platform time source.
 */
class SafetyHistory(
    private val store: KeyValueStore,
    private val retentionMs: Long = 7 * 24 * 60 * 60_000L, // a week — unwanted tracking plays out across days
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

    /** Records a close encounter with a recognised tracker kind (throttled, pruned).
     *  [place] is an optional coarse, on-device place cell (null when location-aware
     *  detection is off), counted as distinct places. [context] is an optional coarse
     *  signature of *where/among-whom* (place × the stable nearby-device backdrop)
     *  used to measure context-diversity — see [crossSession]. */
    fun record(kind: TrackerKind, nowEpochMs: Long, place: String? = null, context: String? = null) {
        if (kind == TrackerKind.UNKNOWN) return
        val kept = load().filter { nowEpochMs - it.epochMs <= retentionMs }
        val lastForKind = kept.filter { it.kind == kind }.maxOfOrNull { it.epochMs }
        // Throttle by time, but always record a *new* place or context so the
        // distinct-place / distinct-context counts stay honest.
        val knownPlace = place != null && kept.any { it.kind == kind && it.place == place }
        val knownContext = context != null && kept.any { it.kind == kind && it.context == context }
        if (lastForKind != null && nowEpochMs - lastForKind < minGapMs &&
            (place == null || knownPlace) && (context == null || knownContext)
        ) {
            return
        }
        save((kept + Encounter(kind, nowEpochMs, place, context)).takeLast(maxEntries))
    }

    /** Cross-session context for [kind], or null if it's never been logged.
     *
     *  The "baseline" is the single most-frequent context across *all* kinds — your
     *  auto-learned "usually around me" (home/desk). Contexts beyond it are what make
     *  recurrence suspicious, so [CrossSession.nonBaselineContexts] excludes it. The
     *  baseline is on-device only and cleared with the log. */
    fun crossSession(kind: TrackerKind, nowEpochMs: Long): CrossSession? {
        val all = load().filter { nowEpochMs - it.epochMs <= retentionMs }
        val mine = all.filter { it.kind == kind }
        if (mine.isEmpty()) return null
        val hours = mine.map { it.epochMs / 3_600_000L }.toHashSet().size
        val places = mine.mapNotNull { it.place }.toHashSet().size
        val contexts = mine.mapNotNull { it.context }.toHashSet()
        val baseline = all.mapNotNull { it.context }
            .takeIf { it.isNotEmpty() }?.groupingBy { it }?.eachCount()?.maxByOrNull { it.value }?.key
        val nonBaseline = (contexts - setOfNotNull(baseline)).size
        return CrossSession(
            kind, hours, mine.minOf { it.epochMs }, mine.maxOf { it.epochMs },
            distinctPlaces = places, distinctContexts = contexts.size, nonBaselineContexts = nonBaseline,
        )
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
        val p = line.split(':')          // "ord:ms[:place[:context]]" (place/context are comma/hex, never ':')
        if (p.size < 2) return@mapNotNull null
        val ord = p[0].toIntOrNull() ?: return@mapNotNull null
        val ms = p[1].toLongOrNull() ?: return@mapNotNull null
        val place = p.getOrNull(2)?.takeIf { it.isNotBlank() }
        val context = p.getOrNull(3)?.takeIf { it.isNotBlank() }
        TrackerKind.entries.getOrNull(ord)?.let { Encounter(it, ms, place, context) }
    }

    private fun save(list: List<Encounter>) =
        writeLines(KEY_LOG, list.map { "${it.kind.ordinal}:${it.epochMs}:${it.place.orEmpty()}:${it.context.orEmpty()}" })

    /** Newline-delimited string codec shared by the encounter log and the mute list. */
    private fun readLines(key: String): List<String> =
        store.getString(key)?.takeIf { it.isNotBlank() }?.split('\n')?.filter { it.isNotBlank() }.orEmpty()

    private fun writeLines(key: String, lines: List<String>) = store.putString(key, lines.joinToString("\n"))

    private data class Encounter(val kind: TrackerKind, val epochMs: Long, val place: String? = null, val context: String? = null)

    private companion object {
        const val KEY_ENABLED = "safety.remember"
        const val KEY_LOG = "safety.encounters"
        const val KEY_MUTED = "safety.muted"
    }
}
