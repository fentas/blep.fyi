import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.kotlinCompose)
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
        // Unique across the whole app: the phone bundles use 5/6, the watch uses
        // its own band (10+) so the two never collide in a multi-bundle release.
        versionCode = 11
        versionName = "0.1.0"
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
    kotlinOptions { jvmTarget = "11" }
    buildFeatures { compose = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.kotlinx.coroutines.core)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose.android)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.wear.compose.material)
    implementation(libs.wear.compose.foundation)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
