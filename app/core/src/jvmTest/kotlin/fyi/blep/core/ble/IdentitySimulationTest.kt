package fyi.blep.core.ble

import fyi.blep.core.platform.createKeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * End-to-end-ish scenario: you carry a tracker that rotates its address every "cycle"
 * while a churn of strangers come and go around you. The rotation correlator + the
 * persisted identity layer should keep the tracker as one device — so a rename saved
 * under its first address still resolves under its latest, strangers never get pulled
 * into its identity, and it survives a restart.
 */
class IdentitySimulationTest {

    // The lookup the controller does: resolve a rename across the device's whole
    // persisted identity (every address it has worn), not just the current one.
    private fun aliasOf(ids: IdentityStore, saved: Map<String, String>, address: String): String? =
        ids.addressesFor(address).firstNotNullOfOrNull { saved[it] }

    @Test
    fun a_rename_follows_a_tracker_through_rotations_and_a_crowd() {
        val rt = RotationTracker()
        val store = createKeyValueStore()
        val ids = IdentityStore(store, now = { 1_000_000L })
        val saved = mutableMapOf<String, String>() // address-keyed renames, as persisted
        val fp = "airtag"                            // the tracker's stable payload signature
        val cycles = 5

        var t = 0L
        rt.observe("tag-0", -55, t, fp)
        saved["tag-0"] = "My bag" // user renames it on first sight
        t += 5_000
        rt.observe("tag-0", -55, t, fp)

        for (r in 0 until cycles) {
            val next = "tag-${r + 1}"
            // the new address appears at the same range + fingerprint as the old goes quiet…
            t += 1_000
            rt.observe(next, -55, t, fp)
            // …and stays present (a stranger drifting by) while the old id falls silent
            // long enough to retire ⇒ the handover resolves onto `next`.
            repeat(4) {
                t += 9_000
                rt.observe(next, -55 + (it % 3 - 1), t, fp)
                rt.observe("stranger-${t / 9000}", -76, t) // out of range, never joins
            }
            // the controller persists a confident, uncontested lineage each cycle
            rt.identityFor(next)?.let { id ->
                if (!id.contested && id.confidence >= 0.6 && id.addresses.size > 1) ids.link(id.addresses)
            }
        }

        val latest = "tag-$cycles"
        assertEquals(cycles, rt.statsFor(latest)!!.rotations)          // every hop correlated
        assertEquals("My bag", aliasOf(ids, saved, latest))            // the rename followed all the way
        assertFalse(ids.addressesFor(latest).any { it.startsWith("stranger") }) // crowd stayed out
        // survives a restart: a fresh store instance over the same persistence still resolves it
        assertEquals("My bag", aliasOf(IdentityStore(store, now = { 1_000_000L }), saved, latest))
    }
}
