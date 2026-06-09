package fyi.blep.core.sync

/**
 * A one-shot event relayed between the phone and watch (fire-and-forget, not converged
 * state): the phone's continuous scan finding a tracker, or a tethered device leaving /
 * returning. Lets the device that's better placed to scan do the work and just buzz the
 * other — e.g. the phone in your pocket detects, the watch on your wrist alerts.
 *
 * Encoded as a single tab-separated line so the transport can ship it as a tiny payload.
 */
sealed interface SyncMessage {
    /** The sender's scan flagged a suspected tracker. [label] names it if known. */
    data class TrackerAlert(val label: String?) : SyncMessage

    /** A tethered device left Bluetooth range on the sender. */
    data class TetherLeft(val id: String, val name: String) : SyncMessage

    /** A tethered device came back into range on the sender. */
    data class TetherReturned(val id: String, val name: String) : SyncMessage

    fun encode(): String = when (this) {
        is TrackerAlert -> "ALERT\t${(label ?: "").c()}"
        is TetherLeft -> "LEFT\t${id.c()}\t${name.c()}"
        is TetherReturned -> "BACK\t${id.c()}\t${name.c()}"
    }

    companion object {
        fun decode(raw: String): SyncMessage? {
            val p = raw.split('\t')
            return when (p.getOrNull(0)) {
                "ALERT" -> TrackerAlert(p.getOrNull(1)?.takeIf { it.isNotBlank() })
                "LEFT" -> TetherLeft(p.getOrNull(1).orEmpty(), p.getOrNull(2).orEmpty())
                "BACK" -> TetherReturned(p.getOrNull(1).orEmpty(), p.getOrNull(2).orEmpty())
                else -> null
            }
        }

        private fun String.c() = replace('\t', ' ').replace('\n', ' ')
    }
}
