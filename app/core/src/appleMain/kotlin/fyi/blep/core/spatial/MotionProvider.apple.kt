package fyi.blep.core.spatial

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Apple (iOS + watchOS) motion provider. Stubbed for now — emits nothing, so the
 * tracker runs RSSI-only on Apple devices until a CoreMotion + CoreLocation
 * implementation lands. Keeps the Apple frameworks compiling.
 */
actual fun createMotionProvider(): MotionProvider = AppleMotionProvider

private object AppleMotionProvider : MotionProvider {
    override fun motion(): Flow<MotionSample> = emptyFlow()
}
