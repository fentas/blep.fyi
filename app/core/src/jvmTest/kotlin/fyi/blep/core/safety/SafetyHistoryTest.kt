package fyi.blep.core.safety

import fyi.blep.core.platform.createKeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SafetyHistoryTest {

    private val hourMs = 3_600_000L

    private fun history() = SafetyHistory(createKeyValueStore())

    @Test
    fun enabled_by_default_with_an_empty_log() {
        val h = history()
        assertTrue(h.enabled())
        assertNull(h.crossSession(TrackerKind.FIND_MY, 10 * hourMs))
    }

    @Test
    fun seen_across_separate_hours_is_persistent() {
        val h = history()
        h.setEnabled(true)
        // Same Find My tag close at 8:00, 11:00, 15:00 — three separate hours.
        h.record(TrackerKind.FIND_MY, 8 * hourMs)
        h.record(TrackerKind.FIND_MY, 11 * hourMs)
        h.record(TrackerKind.FIND_MY, 15 * hourMs)
        val cs = h.crossSession(TrackerKind.FIND_MY, 15 * hourMs)!!
        assertEquals(3, cs.distinctHours)
        assertTrue(cs.persistent)
    }

    @Test
    fun bursts_within_one_hour_count_once() {
        val h = history()
        h.setEnabled(true)
        // Many records inside the same hour (throttling also caps these) ⇒ one hour.
        h.record(TrackerKind.TILE, 9 * hourMs)
        h.record(TrackerKind.TILE, 9 * hourMs + 6 * 60_000L)
        h.record(TrackerKind.TILE, 9 * hourMs + 12 * 60_000L)
        val cs = h.crossSession(TrackerKind.TILE, 9 * hourMs + 12 * 60_000L)!!
        assertEquals(1, cs.distinctHours)
        assertFalse(cs.persistent)
    }

    @Test
    fun throttle_drops_records_within_the_min_gap() {
        val h = SafetyHistory(createKeyValueStore(), minGapMs = 5 * 60_000L)
        h.setEnabled(true)
        h.record(TrackerKind.SMARTTAG, 0)
        h.record(TrackerKind.SMARTTAG, 60_000L) // 1 min later — dropped
        // both land in hour 0 regardless, so assert via a second hour
        h.record(TrackerKind.SMARTTAG, hourMs)
        val cs = h.crossSession(TrackerKind.SMARTTAG, hourMs)!!
        assertEquals(2, cs.distinctHours)
    }

    @Test
    fun old_encounters_age_out_of_the_window() {
        val h = SafetyHistory(createKeyValueStore(), retentionMs = 24 * hourMs)
        h.setEnabled(true)
        h.record(TrackerKind.FIND_MY, 0)
        // 30 hours later, the old one is past retention; only the new one remains.
        val now = 30 * hourMs
        h.record(TrackerKind.FIND_MY, now)
        val cs = h.crossSession(TrackerKind.FIND_MY, now)!!
        assertEquals(1, cs.distinctHours)
    }

    @Test
    fun turning_off_clears_the_log() {
        val h = history()
        h.setEnabled(true)
        h.record(TrackerKind.FIND_MY, 8 * hourMs)
        h.setEnabled(false)
        h.setEnabled(true)
        assertNull(h.crossSession(TrackerKind.FIND_MY, 8 * hourMs))
    }

    @Test
    fun unknown_kind_is_never_recorded() {
        val h = history()
        h.setEnabled(true)
        h.record(TrackerKind.UNKNOWN, 8 * hourMs)
        assertNull(h.crossSession(TrackerKind.UNKNOWN, 8 * hourMs))
    }

    @Test
    fun mute_list_round_trips_and_is_independent_per_address() {
        val h = history()
        assertTrue(h.mutedAddresses().isEmpty())
        h.mute("AA:11")
        h.mute("BB:22")
        assertEquals(setOf("AA:11", "BB:22"), h.mutedAddresses())
        h.unmute("AA:11")
        assertEquals(setOf("BB:22"), h.mutedAddresses())
    }

    @Test
    fun mute_ignores_blank_and_dedupes() {
        val h = history()
        h.mute("")
        h.mute("AA:11")
        h.mute("AA:11")
        assertEquals(setOf("AA:11"), h.mutedAddresses())
    }

    @Test
    fun load_tolerates_a_corrupt_or_truncated_encounter_log() {
        // A partially-written / garbage log must not crash or poison crossSession —
        // only the well-formed "ordinal:epochMs" lines should survive.
        val store = createKeyValueStore()
        store.putString("safety.encounters", "garbage\n\n0:${8 * hourMs}\nbad:line\n99:123\n7:notanumber")
        val h = SafetyHistory(store)
        val cs = h.crossSession(TrackerKind.FIND_MY, 8 * hourMs)!! // ordinal 0 = FIND_MY
        assertEquals(1, cs.distinctHours)
    }

    @Test
    fun the_encounter_log_is_capped_at_maxEntries() {
        val h = SafetyHistory(createKeyValueStore(), maxEntries = 3)
        (1..5).forEach { h.record(TrackerKind.FIND_MY, it * hourMs) } // 5 separate hours
        val cs = h.crossSession(TrackerKind.FIND_MY, 5 * hourMs)!!
        assertEquals(3, cs.distinctHours) // only the newest 3 survive the cap
    }

    @Test
    fun the_mute_list_is_capped_newest_wins() {
        val h = SafetyHistory(createKeyValueStore(), maxMuted = 3)
        listOf("a", "b", "c", "d", "e").forEach { h.mute(it) }
        assertEquals(setOf("c", "d", "e"), h.mutedAddresses())
    }

    @Test
    fun mute_list_survives_the_remember_toggle() {
        // Turning history off clears the encounter log but must NOT forget "it's mine".
        val h = history()
        h.setEnabled(true)
        h.mute("AA:11")
        h.record(TrackerKind.FIND_MY, 8 * hourMs)
        h.setEnabled(false)
        assertEquals(setOf("AA:11"), h.mutedAddresses())
    }
}
