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
        assertEquals(TrackerKind.TILE, TrackerClassifier.classify(adv(svc = listOf("feec"))).kind)
        assertEquals(TrackerKind.SMARTTAG, TrackerClassifier.classify(adv(svc = listOf("fd5a"))).kind)
    }

    @Test
    fun bare_samsung_manufacturer_id_is_not_a_smarttag() {
        // 0x0075 is the Samsung company id carried by every Galaxy phone/watch/buds —
        // matching it alone flagged a room full of Samsung gear as trackers.
        val s = TrackerClassifier.classify(adv(mfg = mapOf(0x0075 to byteArrayOf(1, 2, 3))))
        assertEquals(TrackerKind.UNKNOWN, s.kind)
        assertFalse(s.separated)
    }

    @Test
    fun airtag_with_owner_is_find_my_but_not_separated() {
        // Short 0x12 record (len 0x02) = a paired AirTag still near its owner. Recognise
        // the kind, but it is NOT separated, so it shouldn't trip the "separated nearby" WARN.
        val s = TrackerClassifier.classify(adv(mfg = mapOf(0x004C to byteArrayOf(0x12, 0x02, 0x00))))
        assertEquals(TrackerKind.FIND_MY, s.kind)
        assertFalse(s.separated)
    }

    @Test
    fun find_my_record_is_found_after_another_apple_tlv() {
        // A 0x10 (nearby/handoff) record first, THEN the 0x12 offline-finding record —
        // the TLV walk must still find it (real adverts concatenate Apple records).
        val m = byteArrayOf(0x10, 0x02, 0x00, 0x00, 0x12, 0x19, 0x01)
        val s = TrackerClassifier.classify(adv(mfg = mapOf(0x004C to m)))
        assertEquals(TrackerKind.FIND_MY, s.kind)
        assertTrue(s.separated)
    }

    @Test
    fun malformed_apple_tlv_length_does_not_crash_or_match() {
        // A TLV length that runs past the buffer must be ignored, not throw or false-match.
        val s = TrackerClassifier.classify(adv(mfg = mapOf(0x004C to byteArrayOf(0x10, 0x7F, 0x00))))
        assertEquals(TrackerKind.UNKNOWN, s.kind)
    }

    @Test
    fun unrelated_service_uuids_stay_unknown() {
        // Exposure Notification (0xFD6F) and an arbitrary 16-bit service must NOT be
        // mistaken for trackers — they're left to the rotation heuristic.
        assertEquals(TrackerKind.UNKNOWN, TrackerClassifier.classify(adv(svc = listOf("fd6f"))).kind)
        assertEquals(TrackerKind.UNKNOWN, TrackerClassifier.classify(adv(svc = listOf("180f"))).kind)
    }

    @Test
    fun random_address_flag_is_carried_through() {
        assertTrue(TrackerClassifier.classify(adv(type = AddressType.RANDOM)).randomAddress)
        assertFalse(TrackerClassifier.classify(adv(type = AddressType.PUBLIC)).randomAddress)
    }

    @Test
    fun dult_service_uuids_are_recognised_as_separated() {
        // The cross-vendor DULT "accessory not with owner" / Find My Device network UUIDs.
        for (uuid in listOf("fd44", "feaa")) {
            val s = TrackerClassifier.classify(adv(svc = listOf(uuid)))
            assertEquals(TrackerKind.DULT, s.kind, "uuid=$uuid")
            assertTrue(s.separated, "uuid=$uuid")
        }
    }

    @Test
    fun apple_with_empty_manufacturer_payload_is_not_a_tracker() {
        // No payload byte to inspect ⇒ must not be misread as offline-finding.
        val s = TrackerClassifier.classify(adv(mfg = mapOf(0x004C to byteArrayOf())))
        assertEquals(TrackerKind.UNKNOWN, s.kind)
        assertFalse(s.separated)
    }
}
