package fyi.blep.core.spatial

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** JVM (desktop / tests) has no motion sensors — emits nothing. */
actual fun createMotionProvider(): MotionProvider = object : MotionProvider {
    override fun motion(): Flow<MotionSample> = emptyFlow()
}
