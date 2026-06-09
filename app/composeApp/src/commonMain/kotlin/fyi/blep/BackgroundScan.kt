package fyi.blep

/**
 * Schedules background safety (anti-tracking) scanning. On Android this is a
 * periodic WorkManager job (app closed) plus a foreground service that keeps a
 * safety scan alive when you leave the app. Other platforms are no-ops.
 *
 * Passive only — it runs the [fyi.blep.core.safety.SafetyScanner] over raw
 * advertisements; it never connects to or ranges paired devices.
 */
expect object BackgroundScan {
    /** (Re)schedule or cancel the periodic background scan to match settings. */
    fun applyPeriodic(enabled: Boolean, intervalMinutes: Int)

    /** Start/stop the foreground service that keeps a safety scan running off-screen. */
    fun setForeground(active: Boolean)
}
