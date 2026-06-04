package fyi.blep.core.safety

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrackerClassifierTest {

    private fun adv(
        type: AddressType = AddressType.UNKNOWN,
        svc: List<String> = emptyList(),
        mfg: Map<Int, ByteArray> = emptyMap(),
    ) = RawAdvert("aa:bb", rssi = -60, timeMs = 0, addressType = type, serviceUuids = svc, manufacturerData = mfg)

    @Test
    fun apple_offline_finding_is_a_separated_find_my_tracker() {
        val s = TrackerClassifier.classify(adv(mfg = mapOf(0x004C to byteArrayOf(0x12, 0x19, 0x00))))
        assertEquals(TrackerKind.FIND_MY, s.kind)
        assertTrue(s.separated)
    }

    @Test
    fun apple_non_finding_payload_is_not_a_tracker() {
        // e.g. an AirPods/handoff advert (type 0x07/0x10) — not offline finding.
        val s = TrackerClassifier.classify(adv(mfg = mapOf(0x004C to byteArrayOf(0x10, 0x05))))
        assertEquals(TrackerKind.UNKNOWN, s.kind)
    }

    @Test
    fun tile_and_smarttag_service_uuids_are_recognised() {
        assertEquals(TrackerKind.TILE, TrackerClassifier.classify(adv(svc = listOf("FEED"))).kind)
        assertEquals(TrackerKind.SMARTTAG, TrackerClassifier.classify(adv(svc = listOf("fd5a"))).kind)
        assertEquals(TrackerKind.SMARTTAG, TrackerClassifier.classify(adv(mfg = mapOf(0x0075 to byteArrayOf(1)))).kind)
    }

    @Test
    fun random_address_flag_is_carried_through() {
        assertTrue(TrackerClassifier.classify(adv(type = AddressType.RANDOM)).randomAddress)
        assertFalse(TrackerClassifier.classify(adv(type = AddressType.PUBLIC)).randomAddress)
    }
}
