plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.stitch.authenticator"
    // Compose BOM 2026.09.00 (Compose UI 1.12.x) requires compiling against API 37+.
    // AGP 9 resolves this to the published "platforms;android-37.0" package.
    compileSdk {
        version = release(37)
    }
    defaultConfig {
        applicationId = "com.stitch.authenticator"
        minSdk = 26
        targetSdk = 36
        // CI supplies APP_VERSION_CODE / APP_VERSION_NAME (see .github/workflows/release.yml).
        versionCode = (System.getenv("APP_VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("APP_VERSION_NAME") ?: "1.0"
        // Two ABIs, not four: the QR scanner's barhopper native library costs ~5 MB per
        // architecture, so also shipping x86/x86_64 would add ~11.6 MB for devices that never
        // sideload authenticators. arm64 + armv7 cover every real phone and tablet on minSdk 26.
        ndk {
            abiFilters += setOf("arm64-v8a", "armeabi-v7a")
        }
    }

    // Release signing is configured only in CI, where keystore/release.jks is decoded
    // from the ANDROID_KEYSTORE_B64 secret. Without the secret, the release build type
    // falls back to an unsigned APK, so plain build.yml runs never need the keystore.
    signingConfigs {
        if (System.getenv("ANDROID_KEYSTORE_B64") != null) {
            create("release") {
                storeFile = rootProject.file("keystore/release.jks")
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = false
      shaders = false
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  // Explicit: Motion.kt uses Modifier.animateContentSize / animation specs directly, and the
  // expressive motion helpers build on animation-core. Version comes from the Compose BOM.
  implementation(libs.androidx.compose.animation)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  // Icons: material3 1.5.0-beta01 no longer drags material-icons-core in transitively, and the app
  // needs nothing beyond the "core" set (Add, Delete). material-icons-extended stays out on purpose:
  // it is deprecated upstream and would add thousands of unused vectors to the APK.
  implementation(libs.androidx.compose.material.icons.core)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)

  // Storage: TOTP secrets at rest are encrypted with a Keystore-backed master key
  // (EncryptedSharedPreferences is deprecated upstream but still the smallest correct dependency
  // for this; TokenStorage is the seam that would absorb a raw Keystore cipher instead).
  implementation(libs.androidx.security.crypto)
  // Serialization: the stored token list. Also required by the Navigation3 routes.
  implementation(libs.kotlinx.serialization.json)

  // Settings storage: theme, dynamic color and the biometric-lock flag. A Preferences DataStore
  // is deliberately *not* Keystore-backed — only token secrets earn a Keystore round trip.
  implementation(libs.androidx.datastore.preferences)
  // Biometric lock: androidx.biometric is still the only surface that backports BiometricPrompt
  // uniformly to minSdk 26, so it stays even though the platform has its own API for 28+.
  implementation(libs.androidx.biometric)

  // QR scanning (Phase 5b): CameraX owns the camera/preview/analysis lifecycle; ML Kit's *bundled*
  // barcode model reads QR codes inside this process — no Play Services, no network, nothing ever
  // saved. The model adds about 2.4 MB to the APK.
  implementation(libs.androidx.camera.core)
  implementation(libs.androidx.camera.camera2)
  implementation(libs.androidx.camera.lifecycle)
  implementation(libs.androidx.camera.view)
  implementation(libs.mlkit.barcode.scanning)

  // Phase 7.1: FIDO2/WebAuthn passkey provider via androidx.credentials
  implementation("androidx.credentials:credentials:1.3.0")
  implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
}
