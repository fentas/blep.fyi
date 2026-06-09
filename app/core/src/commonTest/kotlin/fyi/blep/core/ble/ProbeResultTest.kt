package fyi.blep.core.ble

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProbeResultTest {

    @Test
    fun a_serial_is_the_strongest_cross_rotation_key() {
        val p = ProbeResult(connectable = true, name = "Buds", model = "GA03201", serial = "SN-42")
        assertEquals("ser:SN-42", p.identityKey)
    }

    @Test
    fun a_personalised_name_is_a_usable_key() {
        val p = ProbeResult(connectable = true, name = "Jan's Buds", model = "GA03201")
        assertEquals("nm:jan's buds", p.identityKey)
    }

    @Test
    fun a_name_that_only_restates_the_model_is_not_a_key() {
        // Two identical earbuds both advertise "GA03201" — keying on that would fuse them.
        val p = ProbeResult(connectable = true, name = "GA03201", model = "GA03201")
        assertNull(p.identityKey)
    }

    @Test
    fun manufacturer_and_model_alone_are_not_a_key_but_do_label() {
        val p = ProbeResult(connectable = true, manufacturer = "Google", model = "GA03201")
        assertNull(p.identityKey)                 // shared by every unit ⇒ not an identity
        assertEquals("Google GA03201", p.label)   // …but fine as a display label
        assertTrue(p.isInformative)
    }

    @Test
    fun a_non_connectable_probe_carries_nothing() {
        val p = ProbeResult(connectable = false)
        assertFalse(p.isInformative)
        assertNull(p.identityKey)
        assertNull(p.label)
    }

    @Test
    fun a_name_labels_in_preference_to_the_model() {
        val p = ProbeResult(connectable = true, name = "Jan's Buds", manufacturer = "Google", model = "GA03201")
        assertEquals("Jan's Buds", p.label)
    }
}
