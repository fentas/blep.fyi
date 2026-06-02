package fyi.blep.core.spatial

/** JVM (desktop / tests) has no haptics. */
actual fun createHaptic(): Haptic = NoHaptic
