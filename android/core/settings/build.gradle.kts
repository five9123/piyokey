plugins {
  alias(libs.plugins.android.library)
}

android {
  namespace = "app.piyokey.core.settings"
  compileSdk = 37

  defaultConfig { minSdk = 26 }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}

kotlin {
  compilerOptions { jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17 }
}

dependencies {
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.kotlinx.coroutines.core)
  testImplementation(libs.kotlin.test.junit)
  testImplementation(libs.kotlinx.coroutines.test)
}
