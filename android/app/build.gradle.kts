import java.net.URI
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.kotlin.serialization)
}

// Firebase Crashlytics is wired only when the private config exists (never committed).
val hasFirebaseConfig = file("google-services.json").isFile
if (hasFirebaseConfig) {
  pluginManager.apply("com.google.gms.google-services")
  pluginManager.apply("com.google.firebase.crashlytics")
}

fun releaseInput(name: String) = providers.gradleProperty(name).orElse(providers.environmentVariable(name))
fun escaped(value: String) = value.replace("\\", "\\\\").replace("\"", "\\\"")

val candidateApplicationId = "app.piyokey.piyokey"
val configuredApplicationId = releaseInput("PIYOKEY_APPLICATION_ID").orElse(candidateApplicationId)
val configuredVersionCode = releaseInput("PIYOKEY_VERSION_CODE").orElse("1").map {
  it.toIntOrNull() ?: error("PIYOKEY_VERSION_CODE must be an integer.")
}
val configuredVersionName = releaseInput("PIYOKEY_VERSION_NAME").orElse("1.1.2")
val configuredCatalogUrl = releaseInput("PIYOKEY_CATALOG_URL").orElse("")
val configuredPrivacyUrl = releaseInput("PIYOKEY_PRIVACY_URL").orElse("https://typee.app/privacy")
val configuredSupportUrl = releaseInput("PIYOKEY_SUPPORT_URL").orElse("https://typee.app/support")
val configuredPostHogToken = releaseInput("PIYOKEY_POSTHOG_PROJECT_TOKEN").orElse("")
val configuredPostHogHost = releaseInput("PIYOKEY_POSTHOG_HOST").orElse("https://eu.i.posthog.com")

val playGamesKeys = listOf(
  "flow_beginner", "flow_intermediate", "flow_advanced",
  "acid_rain_beginner", "acid_rain_intermediate", "acid_rain_advanced",
  "choseong_beginner", "choseong_intermediate", "choseong_advanced",
  "word_match_beginner", "word_match_intermediate", "word_match_advanced",
  "dictation_beginner", "dictation_intermediate", "dictation_advanced",
  "cup_weekly_flow",
  "growth_hatching", "growth_chick", "growth_rooster", "growth_typed_12000", "growth_streak_30",
)
val playGamesPropertyNames = listOf("PIYOKEY_PLAY_GAMES_PROJECT_ID") +
  playGamesKeys.map { "PIYOKEY_PLAY_GAMES_${it.uppercase()}_ID" }

val uploadSigningPropertyNames = listOf(
  "PIYOKEY_UPLOAD_STORE_FILE",
  "PIYOKEY_UPLOAD_STORE_PASSWORD",
  "PIYOKEY_UPLOAD_KEY_ALIAS",
  "PIYOKEY_UPLOAD_KEY_PASSWORD",
)
@Suppress("DEPRECATION")
val configurationCacheRequested = gradle.startParameter.isConfigurationCacheRequested
val configuredUploadStoreFile = releaseInput("PIYOKEY_UPLOAD_STORE_FILE").orNull
if (!configuredUploadStoreFile.isNullOrBlank() && configurationCacheRequested) {
  error("Upload signing requires --no-configuration-cache so signing secrets are not serialized.")
}
val uploadSigningValues = if (configuredUploadStoreFile.isNullOrBlank()) {
  uploadSigningPropertyNames.associateWith { null }
} else {
  uploadSigningPropertyNames.associateWith { releaseInput(it).orNull }
}
val hasCompleteUploadSigning = uploadSigningValues.values.all { !it.isNullOrBlank() }

// Shared contracts are copied (never hand-duplicated) into generated assets.
val sharedRoot = rootProject.layout.projectDirectory.dir("../shared")
val generatedAssets = layout.buildDirectory.dir("generated/piyokeyAssets")
val syncSharedAssets by tasks.registering(Sync::class) {
  from(sharedRoot.dir("mock_catalog")) {
    include("catalog.json", "decks/**", "updates/**", "audio/**", "spacing_passages.json")
    into("catalog")
  }
  from(sharedRoot.dir("tuning")) { into("tuning") }
  from(sharedRoot.file("schema/deck.schema.json")) { into("schema") }
  into(generatedAssets)
}
val generatedBrandRes = layout.buildDirectory.dir("generated/piyokeyBrand/res")
val syncBrandResources by tasks.registering(Sync::class) {
  from(sharedRoot.file("brand/piyokey_app_icon_source.png")) { rename { "piyokey_logo.png" } }
  into(generatedBrandRes.map { it.dir("drawable-nodpi") })
}

android {
  namespace = "app.piyokey.android"
  compileSdk = 37

  androidResources {
    localeFilters += listOf("en", "ja", "es", "de", "fr")
    noCompress += listOf("mp3")
  }

  defaultConfig {
    applicationId = configuredApplicationId.get()
    minSdk = 26
    targetSdk = 36
    versionCode = configuredVersionCode.get()
    versionName = configuredVersionName.get()
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    buildConfigField("String", "CATALOG_URL", "\"${escaped(configuredCatalogUrl.get())}\"")
    buildConfigField("String", "PRIVACY_URL", "\"${escaped(configuredPrivacyUrl.get())}\"")
    buildConfigField("String", "SUPPORT_URL", "\"${escaped(configuredSupportUrl.get())}\"")
    buildConfigField("String", "POSTHOG_PROJECT_TOKEN", "\"${escaped(configuredPostHogToken.get())}\"")
    buildConfigField("String", "POSTHOG_HOST", "\"${escaped(configuredPostHogHost.get())}\"")
    buildConfigField("boolean", "HAS_FIREBASE", hasFirebaseConfig.toString())
    resValue("string", "game_services_project_id", releaseInput("PIYOKEY_PLAY_GAMES_PROJECT_ID").orElse("0").get())
    playGamesKeys.forEach { key ->
      resValue("string", "piyokey_pgs_$key", releaseInput("PIYOKEY_PLAY_GAMES_${key.uppercase()}_ID").orElse("").get())
    }
  }

  buildFeatures {
    compose = true
    buildConfig = true
    resValues = true
  }

  signingConfigs {
    if (hasCompleteUploadSigning) {
      create("upload") {
        storeFile = file(requireNotNull(uploadSigningValues.getValue("PIYOKEY_UPLOAD_STORE_FILE")))
        storePassword = requireNotNull(uploadSigningValues.getValue("PIYOKEY_UPLOAD_STORE_PASSWORD"))
        keyAlias = requireNotNull(uploadSigningValues.getValue("PIYOKEY_UPLOAD_KEY_ALIAS"))
        keyPassword = requireNotNull(uploadSigningValues.getValue("PIYOKEY_UPLOAD_KEY_PASSWORD"))
      }
    }
  }

  buildTypes {
    debug { versionNameSuffix = ".dev" }
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      if (hasCompleteUploadSigning) signingConfig = signingConfigs.getByName("upload")
    }
  }

  sourceSets.getByName("main").assets.directories.add(generatedAssets.get().asFile.absolutePath)
  sourceSets.getByName("main").res.directories.add(generatedBrandRes.get().asFile.absolutePath)
  sourceSets.getByName("androidTest").assets.directories.add(
    sharedRoot.dir("piyodeck/fixtures").asFile.absolutePath,
  )

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  testOptions {
    unitTests.isIncludeAndroidResources = true
  }

  packaging {
    resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
  }
}

tasks.named("preBuild").configure { dependsOn(syncSharedAssets, syncBrandResources) }

kotlin {
  compilerOptions { jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17 }
}

fun isPublicHttpsUrl(value: String): Boolean = runCatching {
  val uri = URI(value)
  uri.scheme == "https" && !uri.host.isNullOrBlank() &&
    uri.host !in setOf("localhost", "127.0.0.1", "0.0.0.0", "example.com", "example.org", "example.net")
}.getOrDefault(false)

val distributionMissingKeys = providers.provider {
  buildList {
    val applicationId = configuredApplicationId.get()
    if (releaseInput("PIYOKEY_APPLICATION_ID_CONFIRMED").orNull != applicationId) add("PIYOKEY_APPLICATION_ID_CONFIRMED")
    if (!Regex("^\\d+\\.\\d+\\.\\d+$").matches(configuredVersionName.get())) add("PIYOKEY_VERSION_NAME")
    if (!isPublicHttpsUrl(configuredPrivacyUrl.get())) add("PIYOKEY_PRIVACY_URL")
    if (!isPublicHttpsUrl(configuredSupportUrl.get())) add("PIYOKEY_SUPPORT_URL")
    if (configuredPostHogToken.get().isBlank()) add("PIYOKEY_POSTHOG_PROJECT_TOKEN")
    if (configuredPostHogHost.get() != "https://eu.i.posthog.com") add("PIYOKEY_POSTHOG_HOST")
    if (!hasFirebaseConfig) add("app/google-services.json")
    if (!releaseInput("PIYOKEY_CONTENT_RIGHTS_CONFIRMED").orNull.equals("true", ignoreCase = true)) {
      add("PIYOKEY_CONTENT_RIGHTS_CONFIRMED")
    }
    uploadSigningPropertyNames.filterTo(this) { uploadSigningValues[it].isNullOrBlank() }
    playGamesPropertyNames.filterTo(this) { releaseInput(it).orNull.isNullOrBlank() }
  }.distinct().sorted().joinToString(",")
}

val verifyReleaseManifestContract by tasks.registering {
  group = "verification"
  description = "Verifies security-critical values in the merged Release manifest."
  dependsOn("processReleaseMainManifest")
  val mergedManifest = layout.buildDirectory.file(
    "intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml",
  )
  inputs.file(mergedManifest)
  doLast {
    val document = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
      .newDocumentBuilder().parse(mergedManifest.get().asFile)
    val application = document.getElementsByTagName("application").item(0) as Element
    val ns = "http://schemas.android.com/apk/res/android"
    check(application.getAttributeNS(ns, "debuggable") != "true") { "Release must not be debuggable." }
    check(application.getAttributeNS(ns, "allowBackup") == "false") { "Release must disable platform backup." }
    check(application.getAttributeNS(ns, "usesCleartextTraffic") != "true") { "Release must not allow cleartext." }
    val permissions = (0 until document.getElementsByTagName("uses-permission").length)
      .map { (document.getElementsByTagName("uses-permission").item(it) as Element).getAttributeNS(ns, "name") }
    val forbidden = setOf(
      "android.permission.READ_EXTERNAL_STORAGE",
      "android.permission.MANAGE_EXTERNAL_STORAGE",
      "com.google.android.gms.permission.AD_ID",
    )
    check(permissions.none { it in forbidden }) { "Release manifest requests a forbidden permission: $permissions" }
  }
}

val verifyDistributionConfiguration by tasks.registering {
  group = "verification"
  description = "Fails closed unless every private and external Play distribution input is present."
  dependsOn(verifyReleaseManifestContract)
  inputs.property("missingOrInvalidKeys", distributionMissingKeys)
  doLast {
    val missing = (inputs.properties.getValue("missingOrInvalidKeys") as String).split(',').filter(String::isNotBlank)
    check(missing.isEmpty()) {
      "Missing or invalid distribution properties: ${missing.joinToString()}. Secret values are never printed."
    }
  }
}

tasks.register("bundleDistributionRelease") {
  group = "distribution"
  description = "Verifies all release gates, then creates the signed Play AAB."
  dependsOn(verifyDistributionConfiguration, "bundleRelease")
}
tasks.matching { it.name == "bundleRelease" }.configureEach { mustRunAfter(verifyDistributionConfiguration) }

dependencies {
  implementation(project(":core:hangul"))
  implementation(project(":core:deckkit"))
  implementation(project(":core:domain"))
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.lifecycle.process)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons)
  implementation(libs.play.billing)
  implementation(libs.play.games.v2)
  implementation(libs.posthog.android)
  implementation(platform(libs.firebase.bom))
  implementation(libs.firebase.crashlytics)

  testImplementation(libs.kotlin.test.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  debugImplementation(libs.androidx.compose.ui.tooling)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
