plugins {
  alias(libs.plugins.android.library)
}

android {
  namespace = "app.piyokey.core.platform"
  compileSdk = 37
  defaultConfig { minSdk = 26 }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}

dependencies {
  implementation(project(":core:data"))
  implementation(project(":core:piyodeck"))
  implementation(project(":core:settings"))
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.play.billing)
  testImplementation(libs.kotlin.test.junit)
  testImplementation(libs.kotlinx.coroutines.test)
}
