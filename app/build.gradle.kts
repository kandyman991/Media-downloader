import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Only the signed-release CI job receives these via GitHub Actions secrets.
// Never put a keystore or its password in the public repository.
val signingStoreFile = providers.environmentVariable("STREAMCATCH_SIGNING_STORE_FILE").orNull
val signingPassword = providers.environmentVariable("STREAMCATCH_SIGNING_PASSWORD").orNull
val signingAlias = providers.environmentVariable("STREAMCATCH_SIGNING_ALIAS").orNull

android {
    namespace = "dev.streamcatch.android"
    compileSdk {
        version = release(37) {
            minorApiLevel = 0
        }
    }

    defaultConfig {
        applicationId = "dev.streamcatch.android"
        minSdk = 29
        targetSdk = 35
        versionCode = 4
        versionName = "0.3.2"
        manifestPlaceholders["appLabel"] = "StreamCatch"
    }

    signingConfigs {
        create("stableRelease") {
            // Missing credentials are allowed for unsigned local/debug verification builds.
            if (!signingStoreFile.isNullOrBlank()) storeFile = file(signingStoreFile)
            if (!signingPassword.isNullOrBlank()) {
                storePassword = signingPassword
                keyPassword = signingPassword
            }
            if (!signingAlias.isNullOrBlank()) keyAlias = signingAlias
        }
    }

    buildTypes {
        // Side-by-side, disposable test package. Never overwrite signed StreamCatch.
        create("preview") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".preview"
            versionNameSuffix = "-gecko-preview"
            manifestPlaceholders["appLabel"] = "StreamCatch Preview"
            matchingFallbacks.add("debug")
            signingConfig = signingConfigs.getByName("debug")
            // Match the single-ABI GeckoView preview artifact.
            ndk.abiFilters.add("arm64-v8a")
        }
        release {
            isMinifyEnabled = false
            // Reuse one private, persistent signing key across all APK releases.
            if (!signingStoreFile.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("stableRelease")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // Keep universal GeckoView for debug/release builds; only the side-by-side
    // preview uses Mozilla's official ARM64-only AAR to reduce download size.
    // This must not silently limit the signed stable release to ARM64.
    debugImplementation("org.mozilla.geckoview:geckoview:158.0.20261007201254")
    releaseImplementation("org.mozilla.geckoview:geckoview:158.0.20261007201254")
    add("previewImplementation", "org.mozilla.geckoview:geckoview-arm64-v8a:158.0.20261007201254")
    // Real VLC decoding engine, not a dependency on the external VLC app.
    // LibVLC for Android 3.6.1 (LGPL-2.1). Preview ABI filtering remains ARM64-only.
    implementation("org.videolan.android:libvlc-all:3.6.1")
    testImplementation("junit:junit:4.13.2")
}
