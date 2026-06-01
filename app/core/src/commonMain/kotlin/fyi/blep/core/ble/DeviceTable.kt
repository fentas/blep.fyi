package fyi.blep.core.ble

import fyi.blep.core.model.BleDevice

/**
 * Accumulates BLE scan results into a stable, de-duplicated, sorted list.
 *
 * Platform scanners (Kable on Android/Apple) feed raw advertisement updates in
 * via [upsert]; the UI reads [snapshot]. Kept free of any platform types so the
 * ordering/retention rules are unit-tested once on the JVM.
 *
 * Ordering: connected first, then named devices, then by signal strength.
 */
class DeviceTable {
    private val byId = HashMap<String, BleDevice>()
    private val lastSeenMs = HashMap<String, Long>()

    /**
     * Inserts or updates a device, preserving any previously set
     * [BleDevice.alias].
     * @param seenAtMs monotonic timestamp of this sighting, used by [prune].
     */
    fun upsert(id: String, name: String?, rssi: Int, isConnected: Boolean, seenAtMs: Long = 0L) {
        val existing = byId[id]
        byId[id] = BleDevice(
            id = id,
            name = name ?: existing?.name,
            rssi = rssi,
            isConnected = isConnected,
            alias = existing?.alias,
        )
        lastSeenMs[id] = seenAtMs
    }

    /** Assigns a user-facing rename to a device, if present. */
    fun setAlias(id: String, alias: String?) {
        byId[id]?.let { byId[id] = it.copy(alias = alias?.takeIf { a -> a.isNotBlank() }) }
    }

    /** Drops devices not seen within [ttlMs] of [nowMs] so stale ghosts age out. */
    fun prune(nowMs: Long, ttlMs: Long) {
        val expired = lastSeenMs.filterValues { nowMs - it > ttlMs }.keys.toList()
        expired.forEach { byId.remove(it); lastSeenMs.remove(it) }
    }

    fun clear() {
        byId.clear()
        lastSeenMs.clear()
    }

    /**
     * Current devices in display order.
     * @param includeUnnamed when false, hides devices with no advertised name
     *   (kept available behind an explicit "show all" toggle in the UI).
     */
    fun snapshot(includeUnnamed: Boolean = false): List<BleDevice> =
        byId.values
            .asSequence()
            .filter { includeUnnamed || it.isNamed }
            .sortedWith(
                compareByDescending<BleDevice> { it.isConnected }
                    .thenByDescending { it.isNamed }
                    .thenByDescending { it.rssi },
            )
            .toList()
}
