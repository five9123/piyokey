plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.serialization)
}

kotlin {
  compilerOptions { jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17 }
}

java {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
  api(project(":core:hangul"))
  api(project(":core:deckkit"))
  testImplementation(libs.kotlin.test.junit)
}

tasks.test {
  systemProperty("piyokey.sharedRoot", rootProject.layout.projectDirectory.dir("../shared").asFile.absolutePath)
}
