plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
}

kotlin {
    // The safety persistence layer uses an expect/actual class (KeyValueStore);
    // that language feature is stable in practice but still flagged "Beta".
    compilerOptions { freeCompilerArgs.add("-Xexpect-actual-classes") }

    jvm() // pure-logic test target — no platform deps

    // Android via the new com.android.kotlin.multiplatform.library DSL (AGP 9+).
    // NOTE: this drops the old `-Pblep.android=false` no-SDK escape hatch for
    // :core — CI runs :core:jvmTest with the SDK present, so it's unaffected.
    android {
        namespace = "fyi.blep.core"
        compileSdk = libs.versions.androidCompileSdk.get().toInt()
        minSdk = libs.versions.androidMinSdk.get().toInt()

        withHostTestBuilder {}
        withDeviceTestBuilder { sourceSetTreeName = "test" }
            .configure { instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
    }

    // Apple targets: phone (ios*) and watch (watchos*). Both back the BLE
    // scanner with CoreBluetooth via Kable, shared through `appleMain`.
    listOf(
        iosArm64(), iosSimulatorArm64(),
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
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        val kableMain by creating {
            dependsOn(commonMain.get())
            dependencies { implementation(libs.kable.core) }
        }
        iosMain.get().dependsOn(kableMain)
    }
}
