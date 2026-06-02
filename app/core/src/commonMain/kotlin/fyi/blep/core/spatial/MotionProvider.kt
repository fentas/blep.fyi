package fyi.blep.core.spatial

import kotlinx.coroutines.flow.Flow

/**
 * Platform motion/position sensing, normalised to [MotionSample] in the local
 * frame. Cold/flow-based like [fyi.blep.core.ble.BleScanner]: collecting starts
 * the sensors, cancelling stops them.
 *
 * Best-effort by design — a platform or device missing the relevant sensors (or
 * with permissions denied) simply emits sparse/empty samples, and the tracker
 * falls back to RSSI-only behaviour.
 */
interface MotionProvider {
    fun motion(): Flow<MotionSample>
}

/** Creates the platform [MotionProvider]. */
expect fun createMotionProvider(): MotionProvider
