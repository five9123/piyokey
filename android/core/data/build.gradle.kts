plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.ksp)
  alias(libs.plugins.androidx.room)
}

android {
  namespace = "app.piyokey.core.data"
  compileSdk = 37

  defaultConfig {
    minSdk = 26
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  sourceSets {
    getByName("main").assets.directories.addAll(
      setOf(
        rootProject.layout.projectDirectory.dir("../shared/mock_catalog").asFile.absolutePath,
        rootProject.layout.projectDirectory.dir("../shared/schema").asFile.absolutePath,
        rootProject.layout.projectDirectory.dir("../shared/tuning").asFile.absolutePath,
      ),
    )
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}

room {
  schemaDirectory("$projectDir/schemas")
}

kotlin {
  compilerOptions {
    jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
  }
}

dependencies {
  implementation(project(":core:deckkit"))
  implementation(project(":core:game"))
  implementation(project(":core:retention"))
  implementation(project(":core:session"))
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.serialization.json)
  ksp(libs.androidx.room.compiler)

  testImplementation(libs.kotlin.test.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.room.testing)
  androidTestImplementation(libs.kotlinx.coroutines.test)
}

tasks.withType<Test>().configureEach {
  systemProperty(
    "piyokey.repositoryRoot",
    rootProject.layout.projectDirectory.dir("..").asFile.absolutePath,
  )
}
