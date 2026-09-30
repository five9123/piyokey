package app.piyokey.android.platform.analytics

/**
 * Pure analytics privacy rules (iOS `AnalyticsTransportPrivacy`, `AnalyticsAppOpenTracker` and the
 * bucket helpers of `TelemetryService`). JVM-testable, no Android/PostHog imports.
 */
object AnalyticsPrivacy {
  const val USAGE_CONTEXT_CONSENT_PROPERTY = "piyokey_usage_context_consent_version"
  const val USAGE_CONTEXT_NOTICE_VERSION = 2
  const val PLATFORM = "android"

  /** iOS/ingestion allowlist (`release/analytics/usage_context.json` `sdk_properties`). */
  val usageContextProperties: Set<String> = setOf(
    "\$app_name", "\$app_version", "\$app_build", "\$app_namespace",
    "\$device_manufacturer", "\$device_model", "\$device_type",
    "\$os_name", "\$os_version", "\$screen_width", "\$screen_height",
    "\$locale", "\$timezone", "\$network_wifi", "\$network_cellular",
    "\$is_emulator", "\$is_testflight", "\$is_sideloaded",
    "\$is_ios_running_on_mac", "\$is_mac_catalyst_app", "\$session_id",
  )

  /**
   * Android sends only the app-locale region and device form after notice v2
   * (ANDROID_PORT_PLAN §5.1 item 4). Subset of [usageContextProperties].
   */
  val androidUsageContextProperties: Set<String> = setOf("\$locale", "\$device_type")

  private val sdkOperationalProperties = setOf("\$geoip_disable", "\$process_person_profile", "\$lib", "\$lib_version")

  /**
   * Final filter applied in PostHog `beforeSend`: drops unknown events, keeps only contract
   * properties + SDK operational keys, always forces `$geoip_disable` and no person profile.
   * Usage context is admitted only when [usageContext] is non-null (consent + notice v2) **and** the
   * event itself was captured with the v2 consent marker; its values come from the app (app locale,
   * device form), never from whatever the SDK attached, and only [androidUsageContextProperties].
   */
  fun sanitizedProperties(
    eventName: String,
    properties: Map<String, Any?>,
    usageContext: Map<String, Any>?,
  ): Map<String, Any>? {
    val event = AnalyticsEvent.entries.firstOrNull { it.wireName == eventName } ?: return null
    val eventProperties = AnalyticsContract.allowedProperties[event] ?: return null
    val allowed = eventProperties.mapTo(mutableSetOf()) { it.wireName } + sdkOperationalProperties
    val sanitized = LinkedHashMap<String, Any>()
    properties.forEach { (key, value) -> if (key in allowed && value != null) sanitized[key] = value }
    sanitized["\$geoip_disable"] = true
    sanitized["\$process_person_profile"] = false
    val version = (properties[USAGE_CONTEXT_CONSENT_PROPERTY] as? Number)?.toInt()
    if (usageContext != null && version == USAGE_CONTEXT_NOTICE_VERSION) {
      usageContext.forEach { (key, value) -> if (key in androidUsageContextProperties) sanitized[key] = value }
      sanitized[USAGE_CONTEXT_CONSENT_PROPERTY] = version
    }
    return sanitized
  }

  /** `$device_type` value for the device form (PostHog convention). */
  fun deviceType(smallestScreenWidthDp: Int) = if (smallestScreenWidthDp >= 600) "Tablet" else "Mobile"

  /** Validates app-level properties against the generated contract before capture. */
  fun accepts(event: AnalyticsEvent, properties: Map<AnalyticsProperty, Any>) = AnalyticsContract.accepts(event, properties)

  fun commonProperties(appVersion: String, buildNumber: String, language: String): Map<AnalyticsProperty, Any> = mapOf(
    AnalyticsProperty.SCHEMA_VERSION to AnalyticsContract.SCHEMA_VERSION,
    AnalyticsProperty.PLATFORM to PLATFORM,
    AnalyticsProperty.APP_VERSION to appVersion,
    AnalyticsProperty.BUILD_NUMBER to buildNumber,
    AnalyticsProperty.LOCALE to localeBucket(language),
  )

  fun localeBucket(language: String) = if (language in setOf("ja", "en", "ko", "es", "de", "fr")) language else "other"

  fun durationBucket(seconds: Double): String = when {
    seconds < 60 -> "under_1m"
    seconds < 180 -> "1_to_3m"
    seconds < 420 -> "3_to_7m"
    else -> "over_7m"
  }

  fun itemCountBucket(count: Int): String = when {
    count <= 3 -> "1_to_3"
    count <= 10 -> "4_to_10"
    count <= 30 -> "11_to_30"
    else -> "over_30"
  }

  fun scoreBucket(score: Int): String = when {
    score <= 0 -> "0"
    score < 1_000 -> "1_to_999"
    score < 5_000 -> "1000_to_4999"
    else -> "5000_plus"
  }

  fun deckCategory(tags: List<String>, level: Int): String {
    val normalized = tags.map { it.lowercase() }.toSet()
    fun has(vararg needles: String) = normalized.any { tag -> needles.any { tag.contains(it) } }
    return when {
      has("topik", "검정") -> "topik"
      has("여행", "travel", "旅行") -> "travel"
      has("일상", "daily", "日常") -> "daily"
      has("k-pop", "kドラマ", "今どき", "sns", "트렌드", "trend") -> "trend"
      level <= 1 || has("입문", "beginner", "入門") -> "beginner"
      else -> "unknown"
    }
  }
}

/** iOS `AnalyticsAppOpenTracker`: at most one `app_opened` per foreground, only with consent. */
class AnalyticsAppOpenTracker(analyticsEnabled: Boolean) {
  var analyticsEnabled: Boolean = analyticsEnabled
    private set
  var capturedInCurrentForeground = false
    private set
  var currentEntryPoint = "cold_start"
    private set

  fun configure(): String? = captureEntryPointIfNeeded()

  fun updateConsent(enabled: Boolean): String? {
    analyticsEnabled = enabled
    return captureEntryPointIfNeeded()
  }

  fun sceneDidBecomeActive(): String? = captureEntryPointIfNeeded()

  fun sceneDidEnterBackground() {
    capturedInCurrentForeground = false
    currentEntryPoint = "foreground"
  }

  private fun captureEntryPointIfNeeded(): String? {
    if (!analyticsEnabled || capturedInCurrentForeground) return null
    capturedInCurrentForeground = true
    return currentEntryPoint
  }
}
