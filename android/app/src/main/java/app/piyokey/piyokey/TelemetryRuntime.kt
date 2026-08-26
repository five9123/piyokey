package app.piyokey.piyokey

import android.content.Context
import app.piyokey.core.analytics.AnalyticsContract
import app.piyokey.core.analytics.AnalyticsEvent
import app.piyokey.core.analytics.AnalyticsProperty
import app.piyokey.core.settings.AppPreferences
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.posthog.PersonProfiles
import com.posthog.PostHog
import com.posthog.android.PostHogAndroid
import com.posthog.android.PostHogAndroidConfig

object TelemetryRuntime {
  private var postHogConfigured = false
  private var analyticsEnabled = false
  private var diagnosticsEnabled = false
  private var appOpenedCaptured = false
  private var appContext: Context? = null

  @Synchronized
  fun updateConsent(context: Context, preferences: AppPreferences) {
    if (BuildConfig.DEBUG) return
    appContext = context.applicationContext
    analyticsEnabled = preferences.anonymousAnalyticsEnabled
    diagnosticsEnabled = preferences.crashDiagnosticsEnabled

    if (analyticsEnabled && !postHogConfigured && BuildConfig.POSTHOG_PROJECT_TOKEN.isNotBlank()) {
      val config = PostHogAndroidConfig(
        apiKey = BuildConfig.POSTHOG_PROJECT_TOKEN,
        host = BuildConfig.POSTHOG_HOST,
      ).apply {
        optOut = false
        personProfiles = PersonProfiles.NEVER
        setDefaultPersonProperties = false
        captureApplicationLifecycleEvents = false
        captureDeepLinks = false
        captureScreenViews = false
        capturePushNotificationSubscriptions = false
        capturePushNotificationOpened = false
        sessionReplay = false
        surveys = false
        preloadFeatureFlags = false
        sendFeatureFlagEvent = false
        errorTrackingConfig.autoCapture = false
        errorTrackingConfig.captureNativeCrashes = false
      }
      PostHogAndroid.setup(context.applicationContext, config)
      postHogConfigured = true
    }
    if (postHogConfigured) {
      if (analyticsEnabled) PostHog.optIn() else PostHog.optOut()
      if (analyticsEnabled && !appOpenedCaptured) {
        capture(AnalyticsEvent.APP_OPENED, mapOf(AnalyticsProperty.ENTRY_POINT to "cold_start"))
        appOpenedCaptured = true
      }
    }

    val firebase = FirebaseApp.getApps(context).firstOrNull() ?: FirebaseApp.initializeApp(context)
    if (firebase != null) {
      FirebaseCrashlytics.getInstance().apply {
        setCrashlyticsCollectionEnabled(diagnosticsEnabled)
        if (!diagnosticsEnabled) deleteUnsentReports()
      }
    }
  }

  fun capture(
    event: AnalyticsEvent,
    eventProperties: Map<AnalyticsProperty, Any> = emptyMap(),
  ) {
    val context = appContext ?: return
    if (BuildConfig.DEBUG || !postHogConfigured || !analyticsEnabled) return
    val locale = context.resources.configuration.locales[0]?.language
      ?.takeIf { it in setOf("ja", "en", "ko") } ?: "other"
    val properties = mutableMapOf<AnalyticsProperty, Any>(
      AnalyticsProperty.SCHEMA_VERSION to AnalyticsContract.SCHEMA_VERSION,
      AnalyticsProperty.PLATFORM to "android",
      AnalyticsProperty.APP_VERSION to BuildConfig.VERSION_NAME,
      AnalyticsProperty.BUILD_NUMBER to BuildConfig.VERSION_CODE.toString(),
      AnalyticsProperty.LOCALE to locale,
    ).apply { putAll(eventProperties) }
    check(AnalyticsContract.accepts(event, properties)) {
      "Rejected analytics properties for ${event.wireName}"
    }
    PostHog.capture(
      event.wireName,
      properties = properties.mapKeys { it.key.wireName },
    )
  }

  fun setCrashContext(
    feature: String,
    sessionKind: String? = null,
    inputMode: String? = null,
    gameMode: String? = null,
  ) {
    if (BuildConfig.DEBUG || !diagnosticsEnabled || appContext == null) return
    FirebaseCrashlytics.getInstance().apply {
      setCustomKey(AnalyticsProperty.FEATURE.wireName, feature)
      sessionKind?.let { setCustomKey(AnalyticsProperty.SESSION_KIND.wireName, it) }
      inputMode?.let { setCustomKey(AnalyticsProperty.INPUT_MODE.wireName, it) }
      gameMode?.let { setCustomKey(AnalyticsProperty.GAME_MODE.wireName, it) }
    }
  }

  fun durationBucket(milliseconds: Long): String = when {
    milliseconds < 60_000 -> "under_1m"
    milliseconds < 180_000 -> "1_to_3m"
    milliseconds < 420_000 -> "3_to_7m"
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
}
