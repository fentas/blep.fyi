import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinCompose)
    // Kotlin via AGP built-in Kotlin (since AGP 9) — no org.jetbrains.kotlin.android.
}

// Thin Android app: the launcher Activity + packaging/signing. All UI + platform
// glue lives in :composeApp (a KMP library), which this depends on.
kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
}

android {
    namespace = "fyi.blep"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()
    defaultConfig {
        applicationId = "fyi.blep"
        minSdk = libs.versions.androidMinSdk.get().toInt()
        targetSdk = libs.versions.androidTargetSdk.get().toInt()
        // Phone band = 1xxx (Wear = 2xxx in :wearApp); globally-unique per upload.
        versionCode = 1007
        versionName = "1.4.0"
    }
    // Release signing from a gitignored keystore.properties; absent → debug signing.
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
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
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

dependencies {
    implementation(project(":composeApp"))
    implementation(libs.androidx.activity.compose)
}
