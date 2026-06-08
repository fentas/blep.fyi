package fyi.blep.core.safety

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class PayloadFingerprintTest {

    private fun advert(address: String, svc: List<String> = emptyList(), mfg: Map<Int, ByteArray> = emptyMap()) =
        RawAdvert(address = address, rssi = -60, timeMs = 0, serviceUuids = svc, manufacturerData = mfg)

    @Test
    fun empty_payload_has_no_fingerprint() {
        assertNull(payloadFingerprint(advert("aa:bb")))
    }

    @Test
    fun stable_across_a_mac_rotation_when_only_the_value_bytes_change() {
        // Apple Find My: same company + type(0x12) + length, only the key bytes differ.
        val key1 = byteArrayOf(0x12, 0x19, 1, 2, 3, 4, 5)
        val key2 = byteArrayOf(0x12, 0x19, 9, 8, 7, 6, 5) // rotated key, same header/length
        val a = payloadFingerprint(advert("addr-A", mfg = mapOf(0x4C to key1)))
        val b = payloadFingerprint(advert("addr-B", mfg = mapOf(0x4C to key2)))
        assertEquals(a, b) // the rotation doesn't change the fingerprint
    }

    @Test
    fun differs_by_device_class() {
        val airtag = payloadFingerprint(advert("a", mfg = mapOf(0x4C to byteArrayOf(0x12, 0x19, 0, 0))))
        val tile = payloadFingerprint(advert("b", svc = listOf("feed")))
        val smarttag = payloadFingerprint(advert("c", svc = listOf("fd5a")))
        assertNotEquals(airtag, tile)
        assertNotEquals(tile, smarttag)
        assertNotEquals(airtag, smarttag)
    }

    @Test
    fun differs_on_record_type_even_at_the_same_length() {
        val findMy = payloadFingerprint(advert("a", mfg = mapOf(0x4C to byteArrayOf(0x12, 5, 0, 0, 0))))
        val nearby = payloadFingerprint(advert("b", mfg = mapOf(0x4C to byteArrayOf(0x10, 5, 0, 0, 0))))
        assertNotEquals(findMy, nearby)
    }

    @Test
    fun order_independent_for_service_uuids() {
        val a = payloadFingerprint(advert("a", svc = listOf("feed", "feec")))
        val b = payloadFingerprint(advert("b", svc = listOf("feec", "feed")))
        assertEquals(a, b)
    }
}
