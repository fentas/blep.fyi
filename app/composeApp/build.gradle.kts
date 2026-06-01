import org.jetbrains.kotlin.gradle.dsl.JvmTarget

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
    if (enableAndroid) {
        androidTarget {
            compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
        }
    }

    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
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
            }
        }
    }
}

pluginManager.withPlugin("com.android.application") {
    extensions.configure<com.android.build.gradle.internal.dsl.BaseAppModuleExtension>("android") {
        namespace = "fyi.blep"
        compileSdk = libs.versions.androidCompileSdk.get().toInt()
        defaultConfig {
            applicationId = "fyi.blep"
            minSdk = libs.versions.androidMinSdk.get().toInt()
            targetSdk = libs.versions.androidTargetSdk.get().toInt()
            versionCode = 1
            versionName = "0.1.0"
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
