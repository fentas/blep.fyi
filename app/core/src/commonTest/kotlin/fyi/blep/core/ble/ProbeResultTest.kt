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

    @Test
    fun pack_then_unpack_round_trips_the_descriptive_fields() {
        val p = ProbeResult(
            connectable = true, name = "Jan's Buds", manufacturer = "Google", model = "GA03201",
            firmware = "4.0.1", hardware = "1.2", serial = "SN-7", structure = "k3f9qz",
            serviceCount = 7, batteryPct = 82, needsPairing = true,
        )
        val r = ProbeResult.unpack(p.pack())
        // Assert *every* persisted field — an index drift in pack/unpack would corrupt
        // data silently, so the roundtrip must pin each position.
        assertEquals(p.name, r.name)
        assertEquals(p.manufacturer, r.manufacturer)
        assertEquals(p.model, r.model)
        assertEquals(p.firmware, r.firmware)
        assertEquals(p.hardware, r.hardware)
        assertEquals(p.serial, r.serial)
        assertEquals(p.structure, r.structure)
        assertEquals(p.serviceCount, r.serviceCount)
        assertEquals(p.batteryPct, r.batteryPct)
        assertTrue(r.connectable)
        assertTrue(r.needsPairing)
    }
}
