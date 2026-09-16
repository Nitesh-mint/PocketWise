plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose") // Kotlin 2.x: Compose compiler ships with Kotlin
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services") // reads app/google-services.json
}

android {
    namespace = "com.pocketwise"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.pocketwise"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            // Debuggable builds run Compose interpreted (no AOT/baseline
            // profile) — janky until JIT warms up. Release is the real perf.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // ponytail: debug key so it installs locally; real keystore before shipping.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true // App Check picks the debug vs Play Integrity provider from BuildConfig.DEBUG
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.01.01"))
    implementation("androidx.compose.ui:ui")
    // ponytail: alpha pinned over the BOM's 1.4.0 — wavy progress indicators only ship in 1.5.0 alphas.
    // alpha18 is the newest that builds on AGP 8 / compileSdk 36 (alpha20+ need AGP 9.1 + compileSdk 37).
    // Move back to the BOM once 1.5.0 is stable and the AGP 9 migration is done.
    implementation("androidx.compose.material3:material3:1.5.0-alpha18")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Applies the baseline profiles Compose/Room ship with, so first launch is AOT-compiled.
    implementation("androidx.profileinstaller:profileinstaller:1.3.1")
    implementation("androidx.glance:glance-appwidget:1.1.0")

    // App lock: fingerprint/face with phone PIN fallback. 1.1.0 is the latest stable (needs a FragmentActivity).
    implementation("androidx.biometric:biometric:1.1.0")

    // Voice logging: Gemini via Firebase AI Logic (no custom backend); App Check keeps the key server-side.
    implementation(platform("com.google.firebase:firebase-bom:34.19.0"))
    implementation("com.google.firebase:firebase-ai")
    implementation("com.google.firebase:firebase-appcheck-playintegrity")
    // Debug provider is used for any install not from Google Play (see App.kt); Play installs use Play Integrity.
    implementation("com.google.firebase:firebase-appcheck-debug")

    testImplementation("junit:junit:4.13.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")

    implementation("androidx.datastore:datastore-preferences:1.1.1")

    implementation("com.google.dagger:hilt-android:2.57.2")
    ksp("com.google.dagger:hilt-android-compiler:2.57.2")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
}
