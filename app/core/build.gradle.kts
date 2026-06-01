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

        // Shared Kable-backed scanner for every CoreBluetooth/Android target.
        // Android + Apple (ios*, watchos*) all depend on this single source set
        // so the BLE glue is written once. The JVM target opts out (fake scanner).
        val kableMain by creating {
            dependsOn(commonMain.get())
            dependencies { implementation(libs.kable.core) }
        }
        appleMain.get().dependsOn(kableMain)
        if (enableAndroid) {
            androidMain.get().dependsOn(kableMain)
        }
        // jvmMain intentionally has no BLE dependency — it ships a fake scanner
        // so the domain logic is testable on a plain JVM.
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
