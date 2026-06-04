# R8 / ProGuard rules for the Wear release build (minify is ON in build.gradle.kts).
#
# The watch app ships no reflection-heavy or serialization-over-the-wire code
# (Android BLE uses the platform APIs; the tracking logic in :core is plain
# Kotlin), so the defaults + Wear Compose's bundled consumer rules cover almost
# everything. Add keeps below only if a minified build misbehaves — the embedded
# mapping.txt makes the stack trace readable.

# Kotlin coroutines internals occasionally tripped up by aggressive optimisation.
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.**

# Keep enum values()/valueOf() (used across the :core tracking state machines).
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
