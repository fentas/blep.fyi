package fyi.blep.core.tracking

/** Whether the target's signal is currently "lost", and how old the last reading is. */
data class SignalFreshness(val lost: Boolean, val ageSec: Int)

/**
 * Pure decision for the tracking screen's "signal lost / age" state — extracted from
 * the controller so it's unit-testable without Compose or a live scan.
 *
 * @param sinceLastRssiMs ms since the last RSSI reading, or null if none has arrived.
 * @param sinceStartMs    ms since tracking began, or null if not tracking.
 *
 * Lost when: we had a reading but it's gone stale ([lostAfterMs]), OR we never got one
 * and the grace period since start has elapsed ([graceMs]) — i.e. the device is off /
 * out of range / never acquired. Age is the freshest known interval (last reading,
 * else time since start), in whole seconds.
 */
fun signalFreshness(
    sinceLastRssiMs: Long?,
    sinceStartMs: Long?,
    lostAfterMs: Long = 4_000,
    graceMs: Long = 6_000,
): SignalFreshness {
    val lost = (sinceLastRssiMs != null && sinceLastRssiMs > lostAfterMs) ||
        (sinceLastRssiMs == null && sinceStartMs != null && sinceStartMs > graceMs)
    val ageSec = ((sinceLastRssiMs ?: sinceStartMs) ?: 0L).coerceAtLeast(0L).let { (it / 1000L).toInt() }
    return SignalFreshness(lost, ageSec)
}
