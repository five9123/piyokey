import java.net.URI
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
}

val hasFirebaseConfig = file("google-services.json").isFile
if (hasFirebaseConfig) {
  pluginManager.apply("com.google.gms.google-services")
  pluginManager.apply("com.google.firebase.crashlytics")
}

fun releaseInput(name: String) = providers.gradleProperty(name).orElse(providers.environmentVariable(name))

val candidateApplicationId = "app.piyokey.piyokey"
val configuredApplicationId = releaseInput("PIYOKEY_APPLICATION_ID").orElse(candidateApplicationId)
val configuredVersionCode = releaseInput("PIYOKEY_VERSION_CODE").orElse("8").map { value ->
  value.toIntOrNull() ?: error("PIYOKEY_VERSION_CODE must be an integer.")
}
val configuredVersionName = releaseInput("PIYOKEY_VERSION_NAME").orElse("1.1.0")
val configuredCatalogUrl = releaseInput("PIYOKEY_CATALOG_URL").orElse("")
val configuredPrivacyUrl = releaseInput("PIYOKEY_PRIVACY_URL")
  .orElse("https://typee.app/privacy")
val configuredSupportUrl = releaseInput("PIYOKEY_SUPPORT_URL")
  .orElse("https://typee.app/support")
val configuredPostHogToken = releaseInput("PIYOKEY_POSTHOG_PROJECT_TOKEN").orElse("")
val configuredPostHogHost = releaseInput("PIYOKEY_POSTHOG_HOST")
  .orElse("https://eu.i.posthog.com")

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

val generatedBrandRes = layout.buildDirectory.dir("generated/piyokeyBrand/res")
val generatePiyokeyBrandResources by tasks.registering(Sync::class) {
  from(rootProject.layout.projectDirectory.file("../shared/brand/piyokey_app_icon_source.png")) {
    rename { "piyokey_logo.png" }
  }
  into(generatedBrandRes.map { it.dir("drawable-nodpi") })
}

android {
  namespace = "app.piyokey.piyokey"
  compileSdk = 37

  androidResources {
    // Keep legacy learning strings in source, but ship only supported UI locales.
    localeFilters += listOf("en", "ja", "es")
  }

  defaultConfig {
    applicationId = configuredApplicationId.get()
    minSdk = 26
    targetSdk = 36
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    versionCode = configuredVersionCode.get()
    versionName = configuredVersionName.get()
    val catalogUrl = configuredCatalogUrl.get()
      .replace("\\", "\\\\")
      .replace("\"", "\\\"")
    buildConfigField("String", "CATALOG_URL", "\"$catalogUrl\"")
    val privacyUrl = configuredPrivacyUrl.get().replace("\\", "\\\\").replace("\"", "\\\"")
    val supportUrl = configuredSupportUrl.get().replace("\\", "\\\\").replace("\"", "\\\"")
    buildConfigField("String", "PRIVACY_URL", "\"$privacyUrl\"")
    buildConfigField("String", "SUPPORT_URL", "\"$supportUrl\"")
    val postHogToken = configuredPostHogToken.get().replace("\\", "\\\\").replace("\"", "\\\"")
    val postHogHost = configuredPostHogHost.get().replace("\\", "\\\\").replace("\"", "\\\"")
    buildConfigField("String", "POSTHOG_PROJECT_TOKEN", "\"$postHogToken\"")
    buildConfigField("String", "POSTHOG_HOST", "\"$postHogHost\"")
    val playGamesProjectId = releaseInput("PIYOKEY_PLAY_GAMES_PROJECT_ID").orElse("0").get()
    resValue("string", "game_services_project_id", playGamesProjectId)
    val playGamesKeys = listOf(
      "flow_beginner", "flow_intermediate", "flow_advanced",
      "acid_rain_beginner", "acid_rain_intermediate", "acid_rain_advanced",
      "choseong_beginner", "choseong_intermediate", "choseong_advanced",
      "word_match_beginner", "word_match_intermediate", "word_match_advanced",
      "dictation_beginner", "dictation_intermediate", "dictation_advanced",
      "chapter_one", "chapter_three", "chapter_six", "jamo_12000", "streak_30",
    )
    playGamesKeys.forEach { key ->
      val property = "PIYOKEY_PLAY_GAMES_${key.uppercase()}_ID"
      resValue("string", "piyokey_pgs_$key", releaseInput(property).orElse("").get())
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
    debug {
      versionNameSuffix = ".dev"
    }
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro",
      )
      if (hasCompleteUploadSigning) signingConfig = signingConfigs.getByName("upload")
    }
  }

  sourceSets.getByName("main").res.directories.add(generatedBrandRes.get().asFile.absolutePath)
  sourceSets.getByName("androidTest").assets.directories.add(
    rootProject.layout.projectDirectory.dir("../shared/piyodeck/fixtures").asFile.absolutePath,
  )

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  packaging {
    resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
  }
}

tasks.named("preBuild").configure { dependsOn(generatePiyokeyBrandResources) }

val playGamesPropertyNames = listOf(
  "PIYOKEY_PLAY_GAMES_PROJECT_ID",
) + listOf(
  "flow_beginner", "flow_intermediate", "flow_advanced",
  "acid_rain_beginner", "acid_rain_intermediate", "acid_rain_advanced",
  "choseong_beginner", "choseong_intermediate", "choseong_advanced",
  "word_match_beginner", "word_match_intermediate", "word_match_advanced",
  "dictation_beginner", "dictation_intermediate", "dictation_advanced",
  "chapter_one", "chapter_three", "chapter_six", "jamo_12000", "streak_30",
).map { "PIYOKEY_PLAY_GAMES_${it.uppercase()}_ID" }

tasks.register("verifyPlayGamesConfiguration") {
  group = "verification"
  description = "Fails unless every Play Console-issued Play Games v2 resource id is supplied."
  inputs.property(
    "missingProperties",
    providers.provider {
      playGamesPropertyNames.filter { releaseInput(it).orNull.isNullOrBlank() }.joinToString()
    },
  )
  doLast {
    val missing = inputs.properties.getValue("missingProperties") as String
    check(missing.isBlank()) { "Missing Play Games Gradle properties: $missing" }
  }
}

fun isPublicHttpsUrl(value: String): Boolean = runCatching {
  val uri = URI(value)
  uri.scheme == "https" && !uri.host.isNullOrBlank() &&
    uri.host !in setOf("localhost", "127.0.0.1", "0.0.0.0") &&
    uri.host !in setOf("example.com", "example.org", "example.net")
}.getOrDefault(false)

val distributionMissingKeys = providers.provider {
  buildList {
    val applicationId = configuredApplicationId.get()
    if (releaseInput("PIYOKEY_APPLICATION_ID_CONFIRMED").orNull != applicationId ||
      !Regex("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$").matches(applicationId)
    ) add("PIYOKEY_APPLICATION_ID_CONFIRMED")
    if (configuredVersionCode.get() < 8) add("PIYOKEY_VERSION_CODE")
    if (!Regex("^\\d+\\.\\d+\\.\\d+$").matches(configuredVersionName.get())) {
      add("PIYOKEY_VERSION_NAME")
    }
    if (!isPublicHttpsUrl(configuredCatalogUrl.get())) add("PIYOKEY_CATALOG_URL")
    if (!isPublicHttpsUrl(configuredPrivacyUrl.get())) add("PIYOKEY_PRIVACY_URL")
    if (!isPublicHttpsUrl(configuredSupportUrl.get())) add("PIYOKEY_SUPPORT_URL")
    if (configuredPostHogToken.get().isBlank()) add("PIYOKEY_POSTHOG_PROJECT_TOKEN")
    if (configuredPostHogHost.get() != "https://eu.i.posthog.com") add("PIYOKEY_POSTHOG_HOST")
    if (!hasFirebaseConfig) add("app/google-services.json")
    if (!releaseInput("PIYOKEY_ANALYTICS_PRIVACY_CONFIRMED").orNull.equals("true", ignoreCase = true)) {
      add("PIYOKEY_ANALYTICS_PRIVACY_CONFIRMED")
    }
    if (!releaseInput("PIYOKEY_CONTENT_RIGHTS_CONFIRMED").orNull.equals("true", ignoreCase = true)) {
      add("PIYOKEY_CONTENT_RIGHTS_CONFIRMED")
    }
    uploadSigningPropertyNames.filterTo(this) { uploadSigningValues[it].isNullOrBlank() }
    uploadSigningValues["PIYOKEY_UPLOAD_STORE_FILE"]?.takeIf(String::isNotBlank)?.let { path ->
      if (!file(path).isFile) add("PIYOKEY_UPLOAD_STORE_FILE")
    }
    playGamesPropertyNames.filterTo(this) { name ->
      val value = releaseInput(name).orNull.orEmpty()
      if (name == "PIYOKEY_PLAY_GAMES_PROJECT_ID") {
        !Regex("^[1-9]\\d{4,}$").matches(value)
      } else {
        !Regex("^Cgk[A-Za-z0-9_-]{5,}$").matches(value)
      }
    }
    playGamesPropertyNames.drop(1)
      .groupBy { name -> releaseInput(name).orNull.orEmpty() }
      .filter { (value, names) -> value.isNotBlank() && names.size > 1 }
      .values.flatten().forEach(::add)
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
    val document = DocumentBuilderFactory.newInstance().apply {
      isNamespaceAware = true
    }.newDocumentBuilder().parse(mergedManifest.get().asFile)
    val application = document.getElementsByTagName("application").item(0) as Element
    val androidNamespace = "http://schemas.android.com/apk/res/android"
    check(application.getAttributeNS(androidNamespace, "debuggable") != "true") {
      "Release application must not be debuggable."
    }
    check(application.getAttributeNS(androidNamespace, "allowBackup") == "false") {
      "Release application must disable platform backup for local-only user decks."
    }
    check(application.getAttributeNS(androidNamespace, "usesCleartextTraffic") != "true") {
      "Release application must not opt into cleartext traffic."
    }
  }
}

val verifyDistributionConfiguration by tasks.registering {
  group = "verification"
  description = "Fails closed unless all private and external Play distribution gates are present."
  dependsOn(verifyReleaseManifestContract)
  inputs.property("missingOrInvalidKeys", distributionMissingKeys)
  doLast {
    val missing = (inputs.properties.getValue("missingOrInvalidKeys") as String)
      .split(',').filter(String::isNotBlank)
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
tasks.matching { it.name == "bundleRelease" }.configureEach {
  mustRunAfter(verifyDistributionConfiguration)
}

kotlin {
  compilerOptions {
    jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
  }
}

dependencies {
  implementation(project(":core:analytics"))
  implementation(project(":core:data"))
  implementation(project(":core:deckkit"))
  implementation(project(":core:session"))
  implementation(project(":core:game"))
  implementation(project(":core:retention"))
  implementation(project(":core:platform"))
  implementation(project(":core:piyodeck"))
  implementation(project(":core:settings"))
  implementation(project(":core:design"))
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
  implementation(libs.posthog.android)
  implementation(platform(libs.firebase.bom))
  implementation(libs.firebase.crashlytics)

  debugImplementation(libs.androidx.compose.ui.tooling)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
