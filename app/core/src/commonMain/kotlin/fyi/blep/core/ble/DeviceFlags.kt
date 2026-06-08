package fyi.blep.core.ble

import fyi.blep.core.platform.KeyValueStore

/**
 * Devices the user has **flagged** for priority watching. A flagged device escalates
 * the ambient (interval) background check to a continuous foreground watch that posts
 * an active notification while the device is in range — useful for "keep an eye on
 * this one" (a suspected tracker, or a thing you're actively hunting).
 *
 * Persisted as a small newline-delimited id set — the same on-device, identity-stays-
 * local approach as [DeviceFavorites]. Pure given a [KeyValueStore], so it unit-tests
 * without a platform store.
 */
class DeviceFlags(
    private val store: KeyValueStore,
    private val maxEntries: Int = 50,
) {
    fun ids(): Set<String> =
        store.getString(KEY)?.takeIf { it.isNotBlank() }
            ?.split('\n')?.filter { it.isNotBlank() }?.toCollection(LinkedHashSet())
            .orEmpty()

    fun isFlagged(id: String): Boolean = id in ids()

    /** Flag/unflag [id]; returns the new flagged state. */
    fun toggle(id: String): Boolean {
        if (id.isBlank()) return false
        val now = ids()
        val next = if (id in now) now - id else (now + id).toList().takeLast(maxEntries).toSet()
        store.putString(KEY, next.joinToString("\n"))
        return id in next
    }

    private companion object {
        const val KEY = "devices.flagged"
    }
}
