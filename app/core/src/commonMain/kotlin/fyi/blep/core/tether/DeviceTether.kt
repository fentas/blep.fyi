package fyi.blep.core.tether

import fyi.blep.core.platform.KeyValueStore

/**
 * Devices the user has **tethered**: blep watches for them slipping out of Bluetooth
 * range and raises a leave/return alert (notification + vibrate) — the "you left your
 * keys / your phone behind" leash. Distinct from a *flag* (which warns while a device
 * is *present*); a tether warns on the *transition* (see [PresenceMonitor]).
 *
 * Persisted as a small newline-delimited id set — the same on-device, identity-stays-
 * local approach as the flag/favorite stores. Pure given a [KeyValueStore], so it
 * unit-tests without a platform store.
 */
class DeviceTether(
    private val store: KeyValueStore,
    private val maxEntries: Int = 50,
) {
    fun ids(): Set<String> =
        store.getString(KEY)?.takeIf { it.isNotBlank() }
            ?.split('\n')?.filter { it.isNotBlank() }?.toCollection(LinkedHashSet())
            .orEmpty()

    fun isTethered(id: String): Boolean = id in ids()

    /** Tether/untether [id]; returns the new tethered state. */
    fun toggle(id: String): Boolean {
        if (id.isBlank()) return false
        val now = ids()
        val next = if (id in now) now - id else (now + id).toList().takeLast(maxEntries).toSet()
        store.putString(KEY, next.joinToString("\n"))
        return id in next
    }

    /** Bytes this store currently occupies on disk. */
    fun sizeBytes(): Int = (store.getString(KEY) ?: "").encodeToByteArray().size

    /** Wipe this store (part of "clear stored data"). */
    fun clear() = store.putString(KEY, "")

    private companion object {
        const val KEY = "devices.tethered"
    }
}
