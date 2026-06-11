package fyi.blep.core.tether

import fyi.blep.core.platform.KeyValueStore
import fyi.blep.core.platform.epochMillis

/**
 * Turns a stream of "which tethered devices are present right now" samples into
 * leave/return *transition* events, with persistence so it works across the three scan
 * modes: in-app, the foreground service (sampled every ~1.5 s), and the periodic
 * background worker (sampled once per cycle, minutes apart).
 *
 * Rules:
 *  - **RETURNED** fires the moment a tethered id is present again after being absent.
 *  - **LEFT** fires once a tethered id has been *continuously* absent for [leaveGraceMs]
 *    — the grace debounces BLE's normal flicker so a momentary drop isn't a false alarm.
 *  - A device's first-ever sighting just seeds its state (no alert for something never
 *    seen present).
 *
 * State lives in the [KeyValueStore] (one short line per tethered id), so every scan
 * path reads/writes the same committed state and they don't double-fire across the
 * service and the worker. Stateless between calls; deterministic given the store + the
 * injected [nowMs], so it unit-tests cleanly.
 */
class PresenceMonitor(
    private val store: KeyValueStore,
    private val leaveGraceMs: Long = DEFAULT_LEAVE_GRACE_MS,
    private val seenRefreshMs: Long = DEFAULT_SEEN_REFRESH_MS,
) {
    enum class Event { LEFT, RETURNED }

    private data class Rec(val present: Boolean, val lastSeenMs: Long, val label: String = "")

    /**
     * Feed the set of currently-tethered ids and the set of ids present in this scan,
     * get back the transition (if any) for each tethered id. Untethered ids are pruned
     * from the persisted state. [labels] (id → display name) is remembered for present
     * devices so a *leave* alert — fired once the device is already gone from the scan —
     * can still name it (read back via [labelOf]).
     */
    fun update(
        tetheredIds: Set<String>,
        presentIds: Set<String>,
        nowMs: Long = epochMillis(),
        labels: Map<String, String> = emptyMap(),
    ): Map<String, Event> {
        val recs = load()
        val events = LinkedHashMap<String, Event>()
        var changed = false

        for (id in tetheredIds) {
            val prev = recs[id]
            val present = id in presentIds
            // Sanitised latest label, falling back to whatever we last knew.
            val label = labels[id]?.replace('\t', ' ')?.replace('\n', ' ')?.replace('\r', ' ')?.takeIf { it.isNotBlank() }
                ?: prev?.label.orEmpty()
            when {
                prev == null -> {
                    // First sighting (present or not) seeds state silently.
                    recs[id] = Rec(present, if (present) nowMs else 0L, label)
                    changed = true
                }
                present && !prev.present -> {
                    recs[id] = Rec(true, nowMs, label)
                    changed = true
                    events[id] = Event.RETURNED
                }
                present -> {
                    // Still present — only rewrite (a store hit) every [seenRefreshMs], or
                    // sooner if we learned its name.
                    if (nowMs - prev.lastSeenMs >= seenRefreshMs || label != prev.label) {
                        recs[id] = Rec(true, nowMs, label)
                        changed = true
                    }
                }
                prev.present -> {
                    // Just went absent — fire LEFT once the grace has fully elapsed.
                    if (nowMs - prev.lastSeenMs >= leaveGraceMs) {
                        recs[id] = Rec(false, prev.lastSeenMs, label)
                        changed = true
                        events[id] = Event.LEFT
                    }
                }
                // else: already committed-absent, nothing to do.
            }
        }

        // Forget devices that are no longer tethered.
        val stale = recs.keys - tetheredIds
        if (stale.isNotEmpty()) {
            stale.forEach { recs.remove(it) }
            changed = true
        }

        if (changed) save(recs)
        return events
    }

    /** Last-known display name for a tethered id, if we ever saw it present. */
    fun labelOf(id: String): String? = load()[id]?.label?.takeIf { it.isNotBlank() }

    /** Drop all remembered presence state (part of "clear stored data"). */
    fun clear() = store.putString(KEY, "")

    fun sizeBytes(): Int = (store.getString(KEY) ?: "").encodeToByteArray().size

    private fun load(): MutableMap<String, Rec> {
        val out = LinkedHashMap<String, Rec>()
        val raw = store.getString(KEY)?.takeIf { it.isNotBlank() } ?: return out
        for (line in raw.split('\n')) {
            val p = line.split('\t')
            if (p.size < 3) continue
            val id = p[0]
            if (id.isBlank()) continue
            out[id] = Rec(
                present = p[1] == "1",
                lastSeenMs = p[2].toLongOrNull() ?: 0L,
                label = p.getOrNull(3).orEmpty(),
            )
        }
        return out
    }

    private fun save(recs: Map<String, Rec>) {
        val raw = recs.entries.joinToString("\n") { (id, r) ->
            "$id\t${if (r.present) "1" else "0"}\t${r.lastSeenMs}\t${r.label}"
        }
        store.putString(KEY, raw)
    }

    companion object {
        const val KEY = "devices.presence"
        const val DEFAULT_LEAVE_GRACE_MS = 25_000L
        const val DEFAULT_SEEN_REFRESH_MS = 5_000L
    }
}
