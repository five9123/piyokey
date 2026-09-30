plugins {
  alias(libs.plugins.kotlin.jvm)
  jacoco
}

kotlin {
  compilerOptions { jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17 }
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
  systemProperty("piyokey.sharedRoot", rootProject.layout.projectDirectory.dir("../shared").asFile.absolutePath)
  finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
  dependsOn(tasks.test)
  reports { xml.required.set(true); html.required.set(true) }
}

tasks.jacocoTestCoverageVerification {
  dependsOn(tasks.test)
  violationRules {
    rule {
      limit { counter = "LINE"; value = "COVEREDRATIO"; minimum = "0.95".toBigDecimal() }
      limit { counter = "BRANCH"; value = "COVEREDRATIO"; minimum = "0.90".toBigDecimal() }
    }
  }
}

tasks.check { dependsOn(tasks.jacocoTestCoverageVerification) }
