package fyi.blep.core.spatial

/**
 * Apple haptics aren't wired through the shared framework yet (iOS uses
 * CoreHaptics/UIKit, watchOS uses WKInterfaceDevice — both Swift-side). No-op
 * here so the shared driver runs; the Swift apps can add native haptics later.
 */
actual fun createHaptic(): Haptic = NoHaptic
