import java.io.FileInputStream
import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
}

val versionPropsFile = rootProject.file("version.properties")
val versionProps = Properties().apply {
  if (versionPropsFile.exists()) {
    FileInputStream(versionPropsFile).use { load(it) }
  }
}

val baseVersionCode = (versionProps.getProperty("VERSION_CODE") ?: "14").toIntOrNull() ?: 14
val baseVersionName = versionProps.getProperty("VERSION_NAME") ?: "1.3.0"

val ghRunNumber = providers.environmentVariable("GITHUB_RUN_NUMBER").map { it.toIntOrNull() ?: 0 }.orElse(0).get()
val envVersionCode = providers.environmentVariable("VERSION_CODE").map { it.toIntOrNull() ?: 0 }.orElse(0).get()

val computedVersionCode = when {
  envVersionCode > 0 -> envVersionCode
  ghRunNumber > 0 -> baseVersionCode + ghRunNumber
  else -> baseVersionCode
}

val computedVersionName = when {
  ghRunNumber > 0 -> "$baseVersionName-b$ghRunNumber"
  else -> baseVersionName
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.soundbox.xmpzq"
    minSdk = 21
    targetSdk = 36
    versionCode = computedVersionCode
    versionName = computedVersionName
    multiDexEnabled = true
    vectorDrawables.useSupportLibrary = true

    // Phone APKs only need ARM; dropping x86/x86_64 cuts native + Media3 decoder duplicates hard
    ndk {
      abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86_64")
    }

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    getByName("debug") {
      val ksFile = rootProject.file("debug.keystore")
      if (ksFile.exists()) {
        storeFile = ksFile
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
      }
    }
    create("release") {
      val envPath = System.getenv("KEYSTORE_PATH")
      val hasCustomKeystore = when {
        !envPath.isNullOrEmpty() && file(envPath).isAbsolute && file(envPath).exists() -> true
        !envPath.isNullOrEmpty() && rootProject.file(envPath).exists() -> true
        !envPath.isNullOrEmpty() && file(envPath).exists() -> true
        rootProject.file("my-upload-key.jks").exists() -> true
        else -> false
      }
      val keystoreFile = when {
        !envPath.isNullOrEmpty() && file(envPath).isAbsolute && file(envPath).exists() -> file(envPath)
        !envPath.isNullOrEmpty() && rootProject.file(envPath).exists() -> rootProject.file(envPath)
        !envPath.isNullOrEmpty() && file(envPath).exists() -> file(envPath)
        rootProject.file("my-upload-key.jks").exists() -> rootProject.file("my-upload-key.jks")
        else -> rootProject.file("debug.keystore")
      }
      if (hasCustomKeystore && keystoreFile.exists()) {
        storeFile = keystoreFile
        storePassword = System.getenv("KEYSTORE_PASSWORD") ?: System.getenv("STORE_PASSWORD") ?: "SoundBox2026Key!"
        keyAlias = System.getenv("KEY_ALIAS") ?: "upload"
        keyPassword = System.getenv("KEY_PASSWORD") ?: System.getenv("KEYSTORE_PASSWORD") ?: System.getenv("STORE_PASSWORD") ?: "SoundBox2026Key!"
      } else if (keystoreFile.exists()) {
        storeFile = keystoreFile
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
      }
    }
  }

  buildTypes {
    release {
      isCrunchPngs = true
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro"
      )

      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      signingConfig = signingConfigs.getByName("debug")
    }
  }

  packaging {
    resources {
      excludes += setOf(
        "META-INF/LICENSE*",
        "META-INF/NOTICE*",
        "META-INF/*.kotlin_module",
        "META-INF/AL2.0",
        "META-INF/LGPL2.1",
        "DebugProbesKt.bin"
      )
    }
    jniLibs {
      useLegacyPackaging = false
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  // Firebase BOM removed — firebase-ai is not used; kept BOM was pulling unused metadata weight
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  implementation(libs.retrofit)

  implementation(libs.media3.exoplayer)
  implementation(libs.media3.ui)
  implementation(libs.media3.session)
  implementation(libs.androidx.palette)

  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}
