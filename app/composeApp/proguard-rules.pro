# R8 / ProGuard rules for the release build.
#
# Currently minify is OFF (see build.gradle.kts) so these aren't applied yet —
# they're here so enabling `isMinifyEnabled = true` later is a one-line flip.
# blep ships no reflection-heavy or serialization-over-the-wire code (Android BLE
# uses the platform APIs, not Kable), so the defaults + Compose's bundled rules
# cover almost everything. Add keeps below only if a release build misbehaves.

# Kotlin coroutines internals occasionally tripped up by aggressive optimisation.
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.**

# Keep enum values()/valueOf() (used across the tracking state machines).
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
