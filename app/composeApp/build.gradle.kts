plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.kotlinCompose)
    alias(libs.plugins.androidKmpLibrary)
}

// Shared Compose UI + platform glue. The Android *app* lives in :androidApp (a
// thin com.android.application that depends on this); this module is the KMP
// library (android + iOS) so it can keep producing the ComposeApp iOS framework.
kotlin {
    // expect/actual classes (e.g. BackgroundScan) are still flagged Beta; opt in.
    compilerOptions { freeCompilerArgs.add("-Xexpect-actual-classes") }

    android {
        namespace = "fyi.blep.shared"
        compileSdk = libs.versions.androidCompileSdk.get().toInt()
        minSdk = libs.versions.androidMinSdk.get().toInt()
        // The new KMP-library plugin disables the Android resources pipeline by
        // default; Compose resources need it to package the .cvr bundles into the
        // AAR/APK (else MissingResourceException at runtime).
        androidResources.enable = true
    }

    // Compose Multiplatform 1.11 dropped iosX64 (Intel simulator); Apple-silicon
    // simulator + device only.
    listOf(iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.work.runtime)
        }
    }
}

// Stable package for the generated Compose resources accessor (Res).
compose.resources {
    publicResClass = true
    packageOfResClass = "fyi.blep.resources"
}
