package fyi.blep.core.ble

import kotlin.test.Test
import kotlin.test.assertEquals

class AddressKindTest {

    @Test
    fun public_mac_top_bits_10() {
        assertEquals(AddressKind.PUBLIC, addressKind("84:2B:2B:00:11:22")) // 0x84 & 0xC0 == 0x80
    }

    @Test
    fun random_macs_other_top_bits() {
        assertEquals(AddressKind.RANDOM, addressKind("C0:11:22:33:44:55")) // static random
        assertEquals(AddressKind.RANDOM, addressKind("40:11:22:33:44:55")) // resolvable private
        assertEquals(AddressKind.RANDOM, addressKind("12:11:22:33:44:55")) // non-resolvable
    }

    @Test
    fun non_mac_ids_are_opaque() {
        assertEquals(AddressKind.OPAQUE, addressKind("550E8400-E29B-41D4-A716-446655440000")) // Apple UUID
        assertEquals(AddressKind.OPAQUE, addressKind("keys"))                                  // demo id
        assertEquals(AddressKind.OPAQUE, addressKind(""))
    }
}
