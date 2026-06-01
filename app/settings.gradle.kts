rootProject.name = "blep"

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

include(":core")
include(":composeApp")

// The Wear OS app is Android-only; skip it when building without an Android SDK
// (e.g. `:core:jvmTest -Pblep.android=false`).
val androidEnabled = (providers.gradleProperty("blep.android").orNull ?: "true") != "false"
if (androidEnabled) {
    include(":wearApp")
}
