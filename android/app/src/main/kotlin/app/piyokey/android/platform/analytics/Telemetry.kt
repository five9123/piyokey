package app.piyokey.android.platform.analytics

import android.util.Log
import app.piyokey.android.BuildConfig
import app.piyokey.android.Services
import app.piyokey.android.data.settings.AppSettings
import app.piyokey.android.data.settings.PrivacyNoticePolicy
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.posthog.PersonProfiles
import com.posthog.PostHog
import com.posthog.PostHogBeforeSend
import com.posthog.android.PostHogAndroid
import com.posthog.android.PostHogAndroidConfig
import java.io.File

/**
 * Privacy-first product analytics + crash diagnostics (iOS `TelemetryService`).
 *
 * - PostHog (EU host) only in non-debug builds with an injected project token, and only while
 *   `settings.anonymous_analytics_enabled` is on (default off). Person profiles never, no
 *   autocapture / lifecycle / screen views / replay / surveys / feature flags, and every event
 *   carries `$geoip_disable = true`.
 * - Events and properties must pass the generated [AnalyticsContract]; anything else is dropped.
 * - Usage context (app locale + device form) is attached only after privacy notice v2 consent
 *   ([PrivacyNoticePolicy.allowsUsageContext]).
 * - On opt-out the SDK is closed and its on-disk queue is deleted so nothing queued is sent later
 *   (Android counterpart of iOS `AnalyticsConsentURLProtocol`).
 * - Crashlytics only when `BuildConfig.HAS_FIREBASE`; collection follows
 *   `settings.crash_diagnostics_enabled`.
 */
object Telemetry {
  private const val TAG = "Telemetry"
  private const val QUEUE_DIR = "piyokey-analytics"

  @Volatile var isProductAnalyticsConfigured = false
    private set
  @Volatile var isCrashDiagnosticsConfigured = false
    private set

  private var appOpenTracker = AnalyticsAppOpenTracker(analyticsEnabled = false)

  private val runtimeEnabled: Boolean get() = !BuildConfig.DEBUG && Services.isInstalled

  private val analyticsConsent: Boolean get() = AppSettings.anonymousAnalyticsEnabled.value
  private val diagnosticsConsent: Boolean get() = AppSettings.crashDiagnosticsEnabled.value

  private val usageContextAllowed: Boolean
    get() = PrivacyNoticePolicy.allowsUsageContext(analyticsConsent, AppSettings.privacyNoticeVersion.value)

  /** Call once from `Application.onCreate` (via `PlatformServices.install`). */
  @Synchronized
  fun configure() {
    if (!runtimeEnabled) return
    appOpenTracker = AnalyticsAppOpenTracker(analyticsConsent)
    if (appOpenTracker.analyticsEnabled) configurePostHogIfAvailable() else deleteQueuedEvents()
    configureCrashlyticsIfAvailable()
    // `app_opened` is captured from the first process foreground (sceneDidBecomeActive), not here:
    // the reminder alarm and boot broadcasts also start the process without opening the app.
  }

  /** Re-reads both consent settings (iOS `updateConsent(productAnalytics:crashDiagnostics:)`). */
  fun applyConsent() = updateConsent(analyticsConsent, diagnosticsConsent)

  @Synchronized
  fun updateConsent(productAnalytics: Boolean, crashDiagnostics: Boolean) {
    if (!runtimeEnabled) return
    val entryPoint = appOpenTracker.updateConsent(productAnalytics)
    if (productAnalytics && !isProductAnalyticsConfigured) configurePostHogIfAvailable()
    if (isProductAnalyticsConfigured) {
      if (productAnalytics) {
        PostHog.optIn()
      } else {
        PostHog.optOut()
        PostHog.close()
        isProductAnalyticsConfigured = false
      }
    }
    if (!productAnalytics) deleteQueuedEvents()
    captureAppOpened(entryPoint)
    if (isCrashDiagnosticsConfigured) {
      runCatching {
        val crashlytics = FirebaseCrashlytics.getInstance()
        crashlytics.setCrashlyticsCollectionEnabled(crashDiagnostics)
        if (!crashDiagnostics) crashlytics.deleteUnsentReports()
      }
    }
  }

  @Synchronized
  fun sceneDidBecomeActive() {
    if (!runtimeEnabled) return
    captureAppOpened(appOpenTracker.sceneDidBecomeActive())
  }

  @Synchronized
  fun sceneDidEnterBackground() {
    if (!runtimeEnabled) return
    appOpenTracker.sceneDidEnterBackground()
  }

  /** Alias of [capture] matching the task-level API name. */
  fun track(event: AnalyticsEvent, properties: Map<AnalyticsProperty, Any> = emptyMap()) = capture(event, properties)

  /** iOS `capture(_:properties:)`. Rejects anything outside the generated contract. */
  fun capture(event: AnalyticsEvent, properties: Map<AnalyticsProperty, Any> = emptyMap()) {
    if (!runtimeEnabled || !isProductAnalyticsConfigured || !appOpenTracker.analyticsEnabled) return
    val merged = commonProperties() + properties
    if (!AnalyticsContract.accepts(event, merged)) {
      Log.w(TAG, "Rejected analytics properties for ${event.wireName}")
      return
    }
    val sdkProperties = HashMap<String, Any>(merged.size + 2)
    merged.forEach { (key, value) -> sdkProperties[key.wireName] = value }
    sdkProperties["\$geoip_disable"] = true
    if (usageContextAllowed) {
      sdkProperties[AnalyticsPrivacy.USAGE_CONTEXT_CONSENT_PROPERTY] = PrivacyNoticePolicy.CURRENT_VERSION
    }
    runCatching { PostHog.capture(event = event.wireName, properties = sdkProperties) }
  }

  /** iOS `setCrashContext`. */
  fun setCrashContext(feature: String, sessionKind: String? = null, inputMode: String? = null, gameMode: String? = null) {
    if (!runtimeEnabled || !isCrashDiagnosticsConfigured) return
    runCatching {
      val crashlytics = FirebaseCrashlytics.getInstance()
      crashlytics.setCustomKey(AnalyticsProperty.FEATURE.wireName, feature)
      sessionKind?.let { crashlytics.setCustomKey(AnalyticsProperty.SESSION_KIND.wireName, it) }
      inputMode?.let { crashlytics.setCustomKey(AnalyticsProperty.INPUT_MODE.wireName, it) }
      gameMode?.let { crashlytics.setCustomKey(AnalyticsProperty.GAME_MODE.wireName, it) }
    }
  }

  fun durationBucket(seconds: Double) = AnalyticsPrivacy.durationBucket(seconds)
  fun itemCountBucket(count: Int) = AnalyticsPrivacy.itemCountBucket(count)
  fun scoreBucket(score: Int) = AnalyticsPrivacy.scoreBucket(score)
  fun deckCategory(tags: List<String>, level: Int) = AnalyticsPrivacy.deckCategory(tags, level)

  // Typed helpers for the iOS call sites -------------------------------------------------------

  fun featureViewed(feature: String) = capture(AnalyticsEvent.FEATURE_VIEWED, mapOf(AnalyticsProperty.FEATURE to feature))

  fun onboardingStepCompleted(step: String) =
    capture(AnalyticsEvent.ONBOARDING_STEP_COMPLETED, mapOf(AnalyticsProperty.ONBOARDING_STEP to step))

  fun deckDownloaded(deckSource: String, deckCategory: String) = capture(
    AnalyticsEvent.DECK_DOWNLOADED,
    mapOf(AnalyticsProperty.DECK_SOURCE to deckSource, AnalyticsProperty.DECK_CATEGORY to deckCategory),
  )

  fun sessionStarted(sessionKind: String, deckSource: String, inputMode: String, gameMode: String? = null, difficulty: String? = null) =
    capture(
      AnalyticsEvent.SESSION_STARTED,
      buildMap {
        put(AnalyticsProperty.SESSION_KIND, sessionKind)
        put(AnalyticsProperty.DECK_SOURCE, deckSource)
        put(AnalyticsProperty.INPUT_MODE, inputMode)
        gameMode?.let { put(AnalyticsProperty.GAME_MODE, it) }
        difficulty?.let { put(AnalyticsProperty.DIFFICULTY, it) }
      },
    )

  fun sessionCompleted(
    sessionKind: String,
    result: String,
    durationSeconds: Double,
    itemCount: Int,
    deckSource: String? = null,
    inputMode: String? = null,
    gameMode: String? = null,
    difficulty: String? = null,
  ) = capture(
    AnalyticsEvent.SESSION_COMPLETED,
    buildMap {
      put(AnalyticsProperty.SESSION_KIND, sessionKind)
      put(AnalyticsProperty.RESULT, result)
      put(AnalyticsProperty.DURATION_BUCKET, durationBucket(durationSeconds))
      put(AnalyticsProperty.ITEM_COUNT_BUCKET, itemCountBucket(itemCount))
      deckSource?.let { put(AnalyticsProperty.DECK_SOURCE, it) }
      inputMode?.let { put(AnalyticsProperty.INPUT_MODE, it) }
      gameMode?.let { put(AnalyticsProperty.GAME_MODE, it) }
      difficulty?.let { put(AnalyticsProperty.DIFFICULTY, it) }
    },
  )

  fun sessionAbandoned(
    sessionKind: String,
    reason: String,
    durationSeconds: Double,
    deckSource: String? = null,
    inputMode: String? = null,
    gameMode: String? = null,
    difficulty: String? = null,
  ) = capture(
    AnalyticsEvent.SESSION_ABANDONED,
    buildMap {
      put(AnalyticsProperty.SESSION_KIND, sessionKind)
      put(AnalyticsProperty.REASON, reason)
      put(AnalyticsProperty.DURATION_BUCKET, durationBucket(durationSeconds))
      deckSource?.let { put(AnalyticsProperty.DECK_SOURCE, it) }
      inputMode?.let { put(AnalyticsProperty.INPUT_MODE, it) }
      gameMode?.let { put(AnalyticsProperty.GAME_MODE, it) }
      difficulty?.let { put(AnalyticsProperty.DIFFICULTY, it) }
    },
  )

  fun gameResult(gameMode: String, difficulty: String, result: String, score: Int, inputMode: String, deckSource: String) = capture(
    AnalyticsEvent.GAME_RESULT,
    mapOf(
      AnalyticsProperty.GAME_MODE to gameMode,
      AnalyticsProperty.DIFFICULTY to difficulty,
      AnalyticsProperty.RESULT to result,
      AnalyticsProperty.SCORE_BUCKET to scoreBucket(score),
      AnalyticsProperty.INPUT_MODE to inputMode,
      AnalyticsProperty.DECK_SOURCE to deckSource,
    ),
  )

  fun reviewCompleted(itemCount: Int, result: String) = capture(
    AnalyticsEvent.REVIEW_COMPLETED,
    mapOf(AnalyticsProperty.ITEM_COUNT_BUCKET to itemCountBucket(itemCount), AnalyticsProperty.RESULT to result),
  )

  fun deckMakerAction(action: String, itemCount: Int? = null, deckSource: String? = null) = capture(
    AnalyticsEvent.DECK_MAKER_ACTION,
    buildMap {
      put(AnalyticsProperty.ACTION, action)
      itemCount?.let { put(AnalyticsProperty.ITEM_COUNT_BUCKET, itemCountBucket(it)) }
      deckSource?.let { put(AnalyticsProperty.DECK_SOURCE, it) }
    },
  )

  fun purchaseFlow(state: String) = capture(AnalyticsEvent.PURCHASE_FLOW, mapOf(AnalyticsProperty.PURCHASE_STATE to state))

  fun settingChanged(setting: String, valueBucket: String) = capture(
    AnalyticsEvent.SETTING_CHANGED,
    mapOf(AnalyticsProperty.SETTING to setting, AnalyticsProperty.VALUE_BUCKET to valueBucket),
  )

  fun shareCompleted(action: String, gameMode: String? = null) = capture(
    AnalyticsEvent.SHARE_COMPLETED,
    buildMap {
      put(AnalyticsProperty.ACTION, action)
      gameMode?.let { put(AnalyticsProperty.GAME_MODE, it) }
    },
  )

  // Internals -------------------------------------------------------------------------------

  private fun commonProperties(): Map<AnalyticsProperty, Any> = AnalyticsPrivacy.commonProperties(
    appVersion = BuildConfig.VERSION_NAME.removeSuffix(".dev"),
    buildNumber = BuildConfig.VERSION_CODE.toString(),
    language = AppSettings.currentLanguage.raw,
  )

  private fun usageContext(): Map<String, Any> {
    val configuration = Services.context.resources.configuration
    return mapOf(
      "\$locale" to AppSettings.currentLanguage.locale.toLanguageTag(),
      "\$device_type" to AnalyticsPrivacy.deviceType(configuration.smallestScreenWidthDp),
    )
  }

  private fun queueDirectory() = File(Services.context.cacheDir, QUEUE_DIR)

  private fun deleteQueuedEvents() {
    runCatching { queueDirectory().deleteRecursively() }
  }

  private fun configurePostHogIfAvailable() {
    if (!appOpenTracker.analyticsEnabled || isProductAnalyticsConfigured) return
    val token = BuildConfig.POSTHOG_PROJECT_TOKEN.trim()
    if (token.isEmpty()) return
    val host = BuildConfig.POSTHOG_HOST.ifBlank { "https://eu.i.posthog.com" }
    val config = PostHogAndroidConfig(apiKey = token, host = host).apply {
      optOut = !analyticsConsent
      personProfiles = PersonProfiles.NEVER
      setDefaultPersonProperties = false
      captureApplicationLifecycleEvents = false
      captureScreenViews = false
      captureDeepLinks = false
      capturePushNotificationSubscriptions = false
      capturePushNotificationOpened = false
      sessionReplay = false
      surveys = false
      preloadFeatureFlags = false
      sendFeatureFlagEvent = false
      errorTrackingConfig.autoCapture = false
      storagePrefix = File(queueDirectory(), "queue").absolutePath
      replayStoragePrefix = File(queueDirectory(), "replay").absolutePath
      addBeforeSend(
        PostHogBeforeSend { event ->
          val context = if (usageContextAllowed) usageContext() else null
          val sanitized = AnalyticsPrivacy.sanitizedProperties(event.event, event.properties.orEmpty(), context)
          sanitized?.let { event.copy(properties = it.toMutableMap()) }
        },
      )
    }
    runCatching { PostHogAndroid.setup(Services.context, config) }
      .onSuccess { isProductAnalyticsConfigured = true }
      .onFailure { Log.w(TAG, "PostHog setup failed", it) }
  }

  private fun captureAppOpened(entryPoint: String?) {
    entryPoint ?: return
    capture(AnalyticsEvent.APP_OPENED, mapOf(AnalyticsProperty.ENTRY_POINT to entryPoint))
  }

  private fun configureCrashlyticsIfAvailable() {
    if (!BuildConfig.HAS_FIREBASE) return
    runCatching {
      FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(diagnosticsConsent)
      isCrashDiagnosticsConfigured = true
    }
  }
}
