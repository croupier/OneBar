plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
}

import java.util.Properties

// Схема версий — semver 2.0.0 без пре-релизных суффиксов (правило workspace, 2026-11-23):
//   формат MAJOR.MINOR.PATCH; bump: совместимая функциональность → MINOR, фиксы → PATCH,
//   ломка публичного API → MAJOR; до фиксации API держим 0.x, 1.0.0 — первый релизный билд.
//   Итерации одной задачи версию повторно не поднимают. versionCode — не semver: +1 на
//   каждый раздаваемый билд.
val appVersion = "0.15.0"
val appVersionCode = 32

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) keystorePropertiesFile.inputStream().use(::load)
}
val releaseStoreFile = keystoreProperties.getProperty("storeFile") ?: System.getenv("ANDROID_STORE_FILE")
val hasReleaseSigning = !releaseStoreFile.isNullOrBlank()
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}
val appMetricaApiKey = (
    localProperties.getProperty("APP_METRICA_API_KEY")
        ?: providers.gradleProperty("APP_METRICA_API_KEY").orNull
        ?: System.getenv("ORG_GRADLE_PROJECT_APP_METRICA_API_KEY")
    ).orEmpty().trim()
fun String.asBuildConfigValue(): String = "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
  namespace = "dev.onebar.app"
  compileSdk = 36

  defaultConfig {
    applicationId = "dev.onebar.app"
    minSdk = 26
    targetSdk = 36
    versionCode = appVersionCode
    versionName = appVersion
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    buildConfigField("String", "APP_METRICA_API_KEY", appMetricaApiKey.asBuildConfigValue())
  }

  signingConfigs {
    if (hasReleaseSigning) {
      create("release") {
        storeFile = rootProject.file(releaseStoreFile!!)
        storePassword = keystoreProperties.getProperty("storePassword") ?: System.getenv("ANDROID_STORE_PASSWORD")
        keyAlias = keystoreProperties.getProperty("keyAlias") ?: System.getenv("ANDROID_KEY_ALIAS")
        keyPassword = keystoreProperties.getProperty("keyPassword") ?: System.getenv("ANDROID_KEY_PASSWORD")
      }
    }
  }
  buildTypes {
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.findByName("release")
    }
    debug {
      applicationIdSuffix = ".debug"
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  buildFeatures {
    compose = true
    buildConfig = true
    aidl = false
    shaders = false
  }

  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
  }

  // Заглушки источников одни на юнит-тесты и на тесты в телефоне: экран собирается по ним же.
  sourceSets {
    getByName("test") { kotlin.srcDir("src/sharedTest/kotlin") }
    getByName("androidTest") { kotlin.srcDir("src/sharedTest/kotlin") }
  }
}

kotlin {
  jvmToolchain(17)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.appcompat)

  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.appmetrica.sdk)

  // Keep the core transport without ads, billing, location, advertising IDs or screenshots.
  configurations.configureEach {
    listOf(
      "analytics-ad-revenue",
      "analytics-ad-revenue-admob-v23",
      "analytics-ad-revenue-applovin-v12",
      "analytics-ad-revenue-applovin-v12-auto",
      "analytics-ad-revenue-fyber-v3",
      "analytics-ad-revenue-ironsource-v7",
      "analytics-ad-revenue-ironsource-v9",
      "analytics-ad-revenue-other",
      "analytics-billing",
      "analytics-certificates",
      "analytics-identifiers",
      "analytics-location",
      "analytics-ndk-crashes",
      "analytics-product-flow",
      "analytics-remote-permissions",
      "analytics-reporter-extension",
      "analytics-screenshot",
    ).forEach { module -> exclude(group = "io.appmetrica.analytics", module = module) }
    exclude(group = "com.google.android.gms")
  }

  debugImplementation(libs.androidx.compose.ui.tooling)
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)
}
