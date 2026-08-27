plugins {
  alias(libs.plugins.kotlin.jvm)
}

kotlin {
  compilerOptions {
    jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
  }
}

java {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
  implementation(project(":core:hangul"))
  implementation(project(":core:deckkit"))
  testImplementation(libs.kotlin.test.junit)
}

tasks.withType<Test>().configureEach {
  systemProperty(
    "piyokey.repositoryRoot",
    rootProject.layout.projectDirectory.dir("..").asFile.absolutePath,
  )
}
