import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.kotlinCompose)
    // Android application plugin applied conditionally (see below).
}

/** Toggle: include Android app target/plugin. Disable with `-Pblep.android=false`. */
val enableAndroid = (project.findProperty("blep.android")?.toString() ?: "true") != "false"

if (enableAndroid) {
    apply(plugin = "com.android.application")
}

kotlin {
    // expect/actual classes (e.g. BackgroundScan) are still flagged Beta; opt in.
    compilerOptions { freeCompilerArgs.add("-Xexpect-actual-classes") }

    if (enableAndroid) {
        androidTarget {
            compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
        }
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
        if (enableAndroid) {
            androidMain.dependencies {
                implementation(compose.preview)
                implementation(libs.androidx.activity.compose)
                implementation(libs.androidx.work.runtime)
            }
        }
    }
}

// Stable package for the generated Compose resources accessor (Res).
compose.resources {
    publicResClass = true
    packageOfResClass = "fyi.blep.resources"
}

pluginManager.withPlugin("com.android.application") {
    extensions.configure<com.android.build.gradle.internal.dsl.BaseAppModuleExtension>("android") {
        namespace = "fyi.blep"
        compileSdk = libs.versions.androidCompileSdk.get().toInt()
        defaultConfig {
            applicationId = "fyi.blep"
            minSdk = libs.versions.androidMinSdk.get().toInt()
            targetSdk = libs.versions.androidTargetSdk.get().toInt()
            // versionCode is banded by form factor so the phone and watch bundles
            // (which share applicationId and so must have globally-unique codes)
            // never collide: phone = 1xxx, Wear OS = 2xxx (see :wearApp). Bump within
            // the band. 1000 clears the early phone uploads (5/6/7).
            versionCode = 1004
            versionName = "1.3.0"
        }
        // Release signing from a gitignored keystore.properties (created locally, or
        // written from secrets in CI). Absent → release falls back to debug signing
        // so dev/CI builds still work; only a real upload key yields a Play-uploadable
        // bundle. Keys: storeFile, storePassword, keyAlias, keyPassword.
        val keystoreProps = rootProject.file("keystore.properties").takeIf { it.exists() }
            ?.let { f -> Properties().apply { f.inputStream().use { load(it) } } }
        signingConfigs {
            if (keystoreProps != null) {
                create("release") {
                    storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                    storePassword = keystoreProps.getProperty("storePassword")
                    keyAlias = keystoreProps.getProperty("keyAlias")
                    keyPassword = keystoreProps.getProperty("keyPassword")
                }
            }
        }
        buildTypes {
            getByName("release") {
                // R8 on: shrinks the app and produces the crash-mapping file (the AAB
                // embeds it, so Play deobfuscates automatically). blep has no
                // reflection/serialization that R8 would break (Compose + native BLE
                // APIs; Kable is iOS-only), so keeps are minimal — but verify a
                // minified build on a device via the internal track. If a screen
                // crashes, add a keep to proguard-rules.pro from the (now readable)
                // stack trace.
                isMinifyEnabled = true
                isShrinkResources = true
                proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
                signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
                // Ship native debug symbols (Compose's Skiko .so) so Play can
                // symbolicate crashes/ANRs — clears the "no debug symbols" warning.
                ndk { debugSymbolLevel = "SYMBOL_TABLE" }
            }
        }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_11
            targetCompatibility = JavaVersion.VERSION_11
        }
        buildFeatures { compose = true }
        packaging {
            resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}
