package fyi.blep.core.tether

/**
 * Which presence transitions of a tethered device should raise an alert.
 *
 * A *tethered* device is one the user asked to be warned about when it slips out of
 * Bluetooth range ("you left your keys / your phone behind") — and, optionally, when
 * it comes back. Configurable on the Settings screen; [BOTH] by default.
 */
enum class TetherAlertDirection {
    /** Only when the device leaves range. */
    LEAVE,

    /** Only when the device returns to range. */
    RETURN,

    /** Both leave and return (default). */
    BOTH;

    val onLeave: Boolean get() = this == LEAVE || this == BOTH
    val onReturn: Boolean get() = this == RETURN || this == BOTH

    companion object {
        val DEFAULT = BOTH
        fun fromName(name: String?): TetherAlertDirection =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
