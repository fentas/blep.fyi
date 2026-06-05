package fyi.blep.core.ble

import fyi.blep.core.platform.KeyValueStore

/**
 * The user's starred devices — the few paired things they actually want to find
 * (keys with a tag, earbuds, a watch). Favourites always show in the main
 * discovery list, even when the device isn't advertising right now, so a long
 * bonded-device list (10–20 entries) doesn't drown out what's genuinely nearby.
 *
 * Persisted as a small newline-delimited set of device ids (same on-device,
 * identity-stays-local approach as [fyi.blep.core.safety.SafetyHistory]'s mute
 * list). Pure given a [KeyValueStore], so it unit-tests without a platform store.
 */
class DeviceFavorites(
    private val store: KeyValueStore,
    private val maxEntries: Int = 100,
) {
    fun ids(): Set<String> =
        store.getString(KEY)?.takeIf { it.isNotBlank() }
            ?.split('\n')?.filter { it.isNotBlank() }?.toCollection(LinkedHashSet())
            .orEmpty()

    fun isFavorite(id: String): Boolean = id in ids()

    /** Star/unstar [id]; returns the new favourite state. */
    fun toggle(id: String): Boolean {
        if (id.isBlank()) return false
        val now = ids()
        val next = if (id in now) now - id else (now + id).toList().takeLast(maxEntries).toSet()
        store.putString(KEY, next.joinToString("\n"))
        return id in next
    }

    private companion object {
        const val KEY = "devices.favorites"
    }
}
