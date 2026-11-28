plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
}

// Схема версий — semver 2.0.0 без пре-релизных суффиксов (правило workspace, 2026-11-23):
//   формат MAJOR.MINOR.PATCH; bump: совместимая функциональность → MINOR, фиксы → PATCH,
//   ломка публичного API → MAJOR; до фиксации API держим 0.x, 1.0.0 — первый релизный билд.
//   Итерации одной задачи версию повторно не поднимают. versionCode — не semver: +1 на
//   каждый раздаваемый билд.
val appVersion = "0.14.0"
val appVersionCode = 30

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
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
