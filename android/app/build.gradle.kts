plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
}

val generatedBrandRes = layout.buildDirectory.dir("generated/piyokeyBrand/res")
val generatePiyokeyBrandResources by tasks.registering(Sync::class) {
  from(rootProject.layout.projectDirectory.dir("../ios/Hanco/Hanco/Resources/Assets.xcassets/PiyokeyLogo.imageset")) {
    include("PiyokeyLogo.png")
    rename { "piyokey_logo.png" }
  }
  into(generatedBrandRes.map { it.dir("drawable-nodpi") })
}

android {
  namespace = "app.piyokey.piyokey"
  compileSdk = 37

  defaultConfig {
    applicationId = "app.piyokey.piyokey"
    minSdk = 26
    targetSdk = 36
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    versionCode = 1
    versionName = "0.6.2-m6c"
    val catalogUrl = providers.gradleProperty("PIYOKEY_CATALOG_URL").orElse("").get()
      .replace("\\", "\\\\")
      .replace("\"", "\\\"")
    buildConfigField("String", "CATALOG_URL", "\"$catalogUrl\"")
  }

  buildFeatures {
    compose = true
    buildConfig = true
  }

  sourceSets.getByName("main").res.directories.add(generatedBrandRes.get().asFile.absolutePath)

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  packaging {
    resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
  }
}

tasks.named("preBuild").configure { dependsOn(generatePiyokeyBrandResources) }

kotlin {
  compilerOptions {
    jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
  }
}

dependencies {
  implementation(project(":core:data"))
  implementation(project(":core:deckkit"))
  implementation(project(":core:session"))
  implementation(project(":core:game"))
  implementation(project(":core:retention"))
  implementation(project(":core:platform"))
  implementation(project(":core:settings"))
  implementation(project(":feature:discover"))
  implementation(project(":feature:game"))
  implementation(project(":feature:practice"))
  implementation(project(":feature:retention"))
  implementation(project(":feature:onboarding"))
  implementation(project(":feature:settings"))
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)

  debugImplementation(libs.androidx.compose.ui.tooling)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
