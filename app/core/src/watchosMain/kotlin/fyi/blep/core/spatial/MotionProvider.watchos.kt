package fyi.blep.core.spatial

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * watchOS motion provider — stubbed for now (emits nothing), so the watch runs
 * RSSI-only until a dedicated CoreMotion/CoreLocation implementation lands.
 */
actual fun createMotionProvider(): MotionProvider = object : MotionProvider {
    override fun motion(): Flow<MotionSample> = emptyFlow()
}
