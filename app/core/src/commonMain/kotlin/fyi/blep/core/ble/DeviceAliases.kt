package fyi.blep.core.ble

import fyi.blep.core.platform.KeyValueStore

/**
 * User renames for devices, persisted so they survive app restarts (the in-memory
 * map this replaced was lost every launch). Keyed by device id, stored as a small
 * newline-delimited "id<TAB>alias" set — the same on-device, identity-stays-local
 * approach as [DeviceFavorites]. Pure given a [KeyValueStore], so it unit-tests
 * without a platform store.
 *
 * Note: a rename only sticks for as long as the device's id is stable — a
 * privacy-rotating address will reappear under a new id and lose its alias. That's
 * inherent to the platform, and the same constraint [DeviceFavorites] has.
 */
class DeviceAliases(
    private val store: KeyValueStore,
    private val maxEntries: Int = 200,
) {
    /** All saved renames, id → alias, in insertion order. */
    fun all(): Map<String, String> =
        store.getString(KEY)?.takeIf { it.isNotBlank() }
            ?.split('\n')
            ?.mapNotNull { line ->
                val tab = line.indexOf('\t')
                if (tab <= 0 || tab == line.length - 1) null
                else line.substring(0, tab) to line.substring(tab + 1)
            }
            ?.toMap(LinkedHashMap())
            .orEmpty()

    fun of(id: String): String? = all()[id]

    /**
     * Sets or clears [id]'s alias (blank/null clears it); returns the cleaned alias,
     * or null if cleared. Tabs/newlines in the name are flattened to spaces so the
     * line format stays intact.
     */
    fun set(id: String, alias: String?): String? {
        if (id.isBlank()) return null
        val clean = alias?.replace('\t', ' ')?.replace('\n', ' ')?.trim()?.takeIf { it.isNotEmpty() }
        val next = LinkedHashMap(all())
        next.remove(id) // re-insert at the end so the cap keeps the most-recently-set
        if (clean != null) next[id] = clean
        val capped = if (next.size > maxEntries) {
            next.entries.toList().takeLast(maxEntries).associate { it.key to it.value }
        } else {
            next
        }
        store.putString(KEY, capped.entries.joinToString("\n") { "${it.key}\t${it.value}" })
        return clean
    }

    private companion object {
        const val KEY = "devices.aliases"
    }
}
