import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinCompose)
    // Kotlin is provided by AGP's built-in Kotlin since AGP 9 — no separate
    // org.jetbrains.kotlin.android plugin.
}

// JVM target on the built-in Kotlin's compilerOptions.
kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
}

android {
    namespace = "fyi.blep.wear"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()

    defaultConfig {
        // Same applicationId as the phone app so Play serves both from ONE listing
        // (the watch form factor is selected by the `android.hardware.type.watch`
        // feature in the manifest). The code/resource package stays `fyi.blep.wear`
        // via `namespace`, which is independent of the install identity.
        applicationId = "fyi.blep"
        minSdk = 30 // Wear OS 3
        targetSdk = libs.versions.androidTargetSdk.get().toInt()
        // versionCode is banded by form factor (phone = 1xxx in :androidApp,
        // Wear OS = 2xxx here) so the two bundles — same applicationId, so codes
        // must be globally unique — never collide. Bump within the band. 2000
        // clears the early watch uploads (10/11).
        versionCode = 2011
        versionName = "0.7.1"
    }

    // Release signing from the same gitignored keystore.properties as the phone app.
    // Absent → falls back to debug signing so dev/CI builds still work.
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
            // R8 on (parity with the phone): shrinks the app and emits the crash
            // mapping.txt, which the AAB embeds so Play deobfuscates automatically.
            // Plain Compose + :core (no reflection/serialization) → minimal keeps in
            // proguard-rules.pro. Smoke-test a minified build on the Wear emulator.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            // Package native debug symbols so Play can symbolicate crashes/ANRs.
            ndk { debugSymbolLevel = "SYMBOL_TABLE" }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures { compose = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.work.runtime) // periodic (no-FGS) safety check
    implementation(libs.play.services.wearable) // Data Layer: detect the paired phone leaving
    // play-services-wearable transitively pins androidx.fragment 1.1.0; bump it so the
    // ActivityResult lint check (needs >= 1.3.0) passes. Wear uses ComponentActivity, not Fragments.
    implementation("androidx.fragment:fragment:1.8.5")

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose.android)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.wear.compose.material)
    implementation(libs.wear.compose.foundation)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
