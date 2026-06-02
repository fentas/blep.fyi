import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    // Android plugin is applied conditionally below so the module can be built
    // (and `:core:jvmTest` run) on machines without an Android SDK.
}

/** Toggle: include Android targets/plugin. Disable with `-Pblep.android=false`. */
val enableAndroid = (project.findProperty("blep.android")?.toString() ?: "true") != "false"

if (enableAndroid) {
    apply(plugin = "com.android.library")
}

kotlin {
    jvm() // pure-logic test target — no platform deps

    if (enableAndroid) {
        androidTarget {
            compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
        }
    }

    // Apple targets: phone (ios*) and watch (watchos*). Both back the BLE
    // scanner with CoreBluetooth via Kable, shared through `appleMain`.
    listOf(
        iosX64(), iosArm64(), iosSimulatorArm64(),
        watchosArm64(), watchosSimulatorArm64(),
    ).forEach { appleTarget ->
        appleTarget.binaries.framework {
            baseName = "BlepCore"
            isStatic = true
        }
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            // Pure logic only — keep platform BLE libs out of commonMain.
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }

        // iOS uses Kable (CoreBluetooth). Android has its own raw-Android scanner
        // (low-latency scanning + bonded/connected devices + GATT RSSI), so it
        // doesn't depend on Kable. watchOS ships a no-op scanner (its Swift app
        // does CoreBluetooth directly). jvmMain ships a fake scanner for tests.
        val kableMain by creating {
            dependsOn(commonMain.get())
            dependencies { implementation(libs.kable.core) }
        }
        iosMain.get().dependsOn(kableMain)
    }
}

// Configured only when the Android plugin was actually applied.
pluginManager.withPlugin("com.android.library") {
    extensions.configure<com.android.build.gradle.LibraryExtension>("android") {
        namespace = "fyi.blep.core"
        compileSdk = libs.versions.androidCompileSdk.get().toInt()
        defaultConfig {
            minSdk = libs.versions.androidMinSdk.get().toInt()
        }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_11
            targetCompatibility = JavaVersion.VERSION_11
        }
    }
}
