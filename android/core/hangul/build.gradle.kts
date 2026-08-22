plugins {
  alias(libs.plugins.kotlin.jvm)
  jacoco
}

jacoco {
  toolVersion = "0.8.13"
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
  testImplementation(libs.kotlin.test.junit)
  testImplementation(libs.kotlinx.serialization.json)
}

tasks.test {
  systemProperty(
    "piyokey.sharedTestVectors",
    rootProject.layout.projectDirectory.file("../shared/test_vectors.json").asFile.absolutePath,
  )
}

tasks.jacocoTestReport {
  dependsOn(tasks.test)
  reports {
    html.required.set(true)
    xml.required.set(true)
    csv.required.set(false)
  }
}

tasks.jacocoTestCoverageVerification {
  dependsOn(tasks.test)
  violationRules {
    rule {
      limit {
        counter = "LINE"
        value = "COVEREDRATIO"
        minimum = "0.95".toBigDecimal()
      }
      limit {
        counter = "BRANCH"
        value = "COVEREDRATIO"
        minimum = "0.95".toBigDecimal()
      }
    }
  }
}

tasks.check {
  dependsOn(tasks.jacocoTestCoverageVerification)
}
