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
    // Pinned Mozilla stable GeckoView; change with security updates after regression tests.
    implementation("org.mozilla.geckoview:geckoview:158.0.20261007201254")
    testImplementation("junit:junit:4.13.2")
}
