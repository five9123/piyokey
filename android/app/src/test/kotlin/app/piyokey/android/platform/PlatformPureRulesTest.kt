package app.piyokey.android.platform

import app.piyokey.android.platform.analytics.AnalyticsAppOpenTracker
import app.piyokey.android.platform.analytics.AnalyticsContract
import app.piyokey.android.platform.analytics.AnalyticsEvent
import app.piyokey.android.platform.analytics.AnalyticsPrivacy
import app.piyokey.android.platform.analytics.AnalyticsProperty
import app.piyokey.android.platform.audio.PronunciationAudio
import app.piyokey.android.platform.audio.PronunciationBackend
import app.piyokey.android.platform.audio.SoundEvent
import app.piyokey.android.platform.audio.SoundPlanner
import app.piyokey.android.platform.audio.SoundRenderer
import app.piyokey.android.platform.audio.SoundWarmupPolicy
import app.piyokey.android.platform.audio.TypingSoundKeyRole
import app.piyokey.android.platform.audio.TypingSoundPreset
import app.piyokey.android.platform.audio.TypingSoundTuning
import app.piyokey.android.platform.billing.OwnedPurchase
import app.piyokey.android.platform.billing.OwnedPurchaseState
import app.piyokey.android.platform.billing.PiyokeyProPolicy
import app.piyokey.android.platform.billing.ProEntitlementPolicy
import app.piyokey.android.platform.files.ContentFeedbackContext
import app.piyokey.android.platform.files.ContentFeedbackLinkBuilder
import app.piyokey.android.platform.files.ContentFeedbackSource
import app.piyokey.android.platform.files.DocumentIO
import app.piyokey.android.platform.reminder.ReminderSchedule
import java.io.ByteArrayInputStream
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlatformPureRulesTest {
  private val repoRoot: File = generateSequence(File("").absoluteFile) { it.parentFile }
    .first { File(it, "shared/mock_catalog").isDirectory }

  // Pronunciation -------------------------------------------------------------------------

  @Test fun canonicalAudioPathMatchesBundledFile() {
    val path = PronunciationAudio.relativePath("안녕하세요")
    assertEquals("audio/ko_2c68318e352971113645.mp3", path)
    assertTrue(File(repoRoot, "shared/mock_catalog/$path").isFile)
  }

  @Test fun everyDeclaredDeckAudioUsesCanonicalHashName() {
    val decks = File(repoRoot, "shared/mock_catalog/decks").walk().filter { it.extension == "json" }.toList()
    val pattern = Regex("\"ko\"\\s*:\\s*\"([^\"]+)\"[^{}]*?\"audio\"\\s*:\\s*\"([^\"]+)\"")
    var checked = 0
    decks.forEach { file ->
      pattern.findAll(file.readText()).forEach { match ->
        val (ko, audio) = match.destructured
        if (audio == PronunciationAudio.relativePath(ko)) checked++
      }
    }
    assertTrue(checked > 0, "expected at least one declared audio path equal to its canonical hash")
  }

  @Test fun candidatesPreferDeclaredThenCanonicalAndRejectTraversal() {
    val canonical = PronunciationAudio.assetPath(PronunciationAudio.relativePath("사과"))
    val all = { _: String -> true }
    assertEquals(
      listOf("catalog/audio/x.mp3" to PronunciationBackend.EXPLICIT_BUNDLED, canonical to PronunciationBackend.CANONICAL_BUNDLED),
      PronunciationAudio.candidates("사과", "audio/x.mp3", all),
    )
    assertEquals(listOf(canonical to PronunciationBackend.CANONICAL_BUNDLED), PronunciationAudio.candidates("사과", "../secret.mp3", all))
    assertEquals(listOf(canonical to PronunciationBackend.CANONICAL_BUNDLED), PronunciationAudio.candidates("사과", "/abs.mp3", all))
    assertTrue(PronunciationAudio.candidates("사과", null) { false }.isEmpty())
    assertEquals(0.84f, PronunciationAudio.androidSpeechRate, 0.001f)
  }

  // Reminder ------------------------------------------------------------------------------

  @Test fun nextTriggerTodayOrTomorrow() {
    val zone = ZoneId.of("Asia/Tokyo")
    val morning = ZonedDateTime.of(2026, 9, 30, 9, 0, 0, 0, zone)
    assertEquals(ZonedDateTime.of(2026, 9, 30, 20, 0, 0, 0, zone), ReminderSchedule.nextTrigger(morning, 20, 0))
    val exactly = ZonedDateTime.of(2026, 9, 30, 20, 0, 0, 0, zone)
    assertEquals(ZonedDateTime.of(2026, 10, 1, 20, 0, 0, 0, zone), ReminderSchedule.nextTrigger(exactly, 20, 0))
  }

  @Test fun nextTriggerKeepsWallClockAcrossDst() {
    val newYork = ZoneId.of("America/New_York")
    // Evening before spring-forward (2026-03-08 02:00 → 03:00).
    val before = ZonedDateTime.of(2026, 3, 7, 21, 0, 0, 0, newYork)
    val next = ReminderSchedule.nextTrigger(before, 20, 0)
    assertEquals(LocalDateTime.of(2026, 3, 8, 20, 0), next.toLocalDateTime())
    assertEquals(22, // 23 h wall-clock minus the skipped hour
      java.time.Duration.between(before, next).toHours())
    // A time inside the gap resolves after it, once.
    val gap = ReminderSchedule.nextTrigger(ZonedDateTime.of(2026, 3, 8, 0, 0, 0, 0, newYork), 2, 30)
    assertEquals(LocalDateTime.of(2026, 3, 8, 3, 30), gap.toLocalDateTime())
    // Fall-back overlap uses the earlier offset.
    val overlap = ReminderSchedule.nextTrigger(ZonedDateTime.of(2026, 11, 1, 0, 0, 0, 0, newYork), 1, 30)
    assertEquals(LocalDateTime.of(2026, 11, 1, 1, 30), overlap.toLocalDateTime())
    assertEquals(-4 * 3600, overlap.offset.totalSeconds)
    // Travel: same instant in another zone keeps the local 20:00.
    val paris = ReminderSchedule.nextTrigger(before.withZoneSameInstant(ZoneId.of("Europe/Paris")), 20, 0)
    assertEquals(LocalDateTime.of(2026, 3, 8, 20, 0), paris.toLocalDateTime())
  }

  @Test fun nextTriggerSkipsAlreadyNotifiedDay() {
    val zone = ZoneId.of("UTC")
    val now = ZonedDateTime.of(2026, 9, 30, 19, 55, 0, 0, zone)
    assertEquals(LocalDate.of(2026, 10, 1), ReminderSchedule.nextTrigger(now, 20, 0, LocalDate.of(2026, 9, 30)).toLocalDate())
    assertEquals(LocalDate.of(2026, 9, 30), ReminderSchedule.nextTrigger(now, 20, 0, LocalDate.of(2026, 9, 29)).toLocalDate())
    assertFalse(ReminderSchedule.isValid(24, 0))
    assertFalse(ReminderSchedule.isValid(0, 60))
  }

  // Analytics ------------------------------------------------------------------------------

  private fun common() = AnalyticsPrivacy.commonProperties("1.1.1", "7", "ja")

  @Test fun contractAcceptsValidAndRejectsUnknownOrMissing() {
    val ok = common() + (AnalyticsProperty.PURCHASE_STATE to "started")
    assertTrue(AnalyticsContract.accepts(AnalyticsEvent.PURCHASE_FLOW, ok))
    assertEquals("android", ok[AnalyticsProperty.PLATFORM])
    assertFalse(AnalyticsContract.accepts(AnalyticsEvent.PURCHASE_FLOW, common()))
    assertFalse(AnalyticsContract.accepts(AnalyticsEvent.PURCHASE_FLOW, ok + (AnalyticsProperty.FEATURE to "home")))
    assertFalse(AnalyticsContract.accepts(AnalyticsEvent.PURCHASE_FLOW, common() + (AnalyticsProperty.PURCHASE_STATE to "bogus")))
    assertFalse(AnalyticsContract.accepts(AnalyticsEvent.PURCHASE_FLOW, ok + (AnalyticsProperty.SCHEMA_VERSION to 2)))
    assertEquals("other", AnalyticsPrivacy.localeBucket("zh"))
  }

  @Test fun sanitizerForcesGeoipAndGatesUsageContext() {
    val raw = mapOf(
      "schema_version" to 1, "platform" to "android", "app_version" to "1.1.1", "build_number" to "7",
      "locale" to "ja", "purchase_state" to "started", "text" to "secret", "\$device_model" to "Pixel",
      "\$geoip_disable" to false, AnalyticsPrivacy.USAGE_CONTEXT_CONSENT_PROPERTY to 2,
    )
    val context = mapOf("\$locale" to "ja-JP", "\$device_type" to "Mobile", "\$device_model" to "Pixel")
    val denied = AnalyticsPrivacy.sanitizedProperties("purchase_flow", raw, null)!!
    assertEquals(true, denied["\$geoip_disable"])
    assertEquals(false, denied["\$process_person_profile"])
    assertFalse("text" in denied || "\$device_model" in denied || "\$locale" in denied)
    assertFalse(AnalyticsPrivacy.USAGE_CONTEXT_CONSENT_PROPERTY in denied)
    val allowed = AnalyticsPrivacy.sanitizedProperties("purchase_flow", raw, context)!!
    assertEquals("ja-JP", allowed["\$locale"])
    assertEquals("Mobile", allowed["\$device_type"])
    assertFalse("\$device_model" in allowed)
    assertEquals(2, allowed[AnalyticsPrivacy.USAGE_CONTEXT_CONSENT_PROPERTY])
    val preConsentEvent = AnalyticsPrivacy.sanitizedProperties("purchase_flow", raw - AnalyticsPrivacy.USAGE_CONTEXT_CONSENT_PROPERTY, context)!!
    assertFalse("\$locale" in preConsentEvent)
    assertNull(AnalyticsPrivacy.sanitizedProperties("\$autocapture", raw, context))
    assertTrue(AnalyticsPrivacy.androidUsageContextProperties.all { it in AnalyticsPrivacy.usageContextProperties })
  }

  @Test fun usageContextAllowlistMatchesReleaseContract() {
    val json = File(repoRoot, "release/analytics/usage_context.json").readText()
    val list = json.substringAfter("\"sdk_properties\"").substringAfter('[').substringBefore(']')
    val keys = Regex("\"([^\"]+)\"").findAll(list).map { it.groupValues[1] }.toSet()
    assertEquals(keys, AnalyticsPrivacy.usageContextProperties)
  }

  @Test fun bucketsMatchIos() {
    assertEquals("under_1m", AnalyticsPrivacy.durationBucket(59.9))
    assertEquals("1_to_3m", AnalyticsPrivacy.durationBucket(60.0))
    assertEquals("over_7m", AnalyticsPrivacy.durationBucket(420.0))
    assertEquals("1_to_3", AnalyticsPrivacy.itemCountBucket(3))
    assertEquals("over_30", AnalyticsPrivacy.itemCountBucket(31))
    assertEquals("0", AnalyticsPrivacy.scoreBucket(0))
    assertEquals("1000_to_4999", AnalyticsPrivacy.scoreBucket(1_000))
    assertEquals("5000_plus", AnalyticsPrivacy.scoreBucket(5_000))
    assertEquals("topik", AnalyticsPrivacy.deckCategory(listOf("TOPIK I"), 3))
    assertEquals("travel", AnalyticsPrivacy.deckCategory(listOf("旅行"), 3))
    assertEquals("beginner", AnalyticsPrivacy.deckCategory(emptyList(), 1))
    assertEquals("unknown", AnalyticsPrivacy.deckCategory(emptyList(), 2))
  }

  @Test fun appOpenTrackerOncePerForegroundWithConsent() {
    val tracker = AnalyticsAppOpenTracker(analyticsEnabled = false)
    assertNull(tracker.configure())
    assertEquals("cold_start", tracker.updateConsent(true))
    assertNull(tracker.sceneDidBecomeActive())
    tracker.sceneDidEnterBackground()
    assertEquals("foreground", tracker.sceneDidBecomeActive())
  }

  // Sound ----------------------------------------------------------------------------------

  @Test fun tuningMatchesIos() {
    val profile = TypingSoundTuning.profile(TypingSoundPreset.SYSTEM, TypingSoundKeyRole.BACKSPACE, -1)
    assertEquals(1.012 * 0.94, profile.playbackRate, 1e-9)
    assertEquals(0.34f * 0.93f * 0.72f, profile.gain, 1e-6f)
    assertEquals(TypingSoundPreset.SYSTEM, TypingSoundPreset.resolved("nope"))
    assertEquals(listOf(20), SoundWarmupPolicy.completionCombosToPrepare(99))
    assertEquals(listOf(0, 1), SoundWarmupPolicy.completionCombosToPrepare(-3))
  }

  @Test fun renderedPlansAreBoundedAndSized() {
    val completion = SoundRenderer.render(SoundPlanner.plan(SoundEvent.Completion(5)))
    assertEquals(Math.ceil(0.215 * 48_000).toInt(), completion.size)
    assertTrue(completion.all { abs(it) <= 0.9f })
    val variant = SoundRenderer.typingVariant(completion, TypingSoundTuning.profile(TypingSoundPreset.SOFT, TypingSoundKeyRole.SHIFT, 0))!!
    assertTrue(variant.all { abs(it) <= TypingSoundTuning.PEAK_LIMIT })
  }

  @Test fun bundledClickDecodesAt48kMono() {
    val wav = File("src/main/res/raw/ui_basic_mouse_click_640020.wav")
    val samples = assertNotNull(SoundRenderer.decodeWav(wav.readBytes()))
    assertEquals(918, samples.size)
    val roundTrip = SoundRenderer.decodeWav(SoundRenderer.wavBytes(samples))!!
    assertEquals(samples.size, roundTrip.size)
  }

  // Billing / files -------------------------------------------------------------------------

  @Test fun onlyPurchasedUnlocksAndFailedQueryUsesCache() {
    val id = "app.piyokey.deckmaker.lifetime"
    val purchased = OwnedPurchase(listOf(id), OwnedPurchaseState.PURCHASED, false, "t")
    val pending = purchased.copy(state = OwnedPurchaseState.PENDING)
    assertTrue(ProEntitlementPolicy.resolveAccess(listOf(purchased), cached = false))
    assertTrue(ProEntitlementPolicy.needsAcknowledgement(purchased))
    assertFalse(ProEntitlementPolicy.needsAcknowledgement(purchased.copy(isAcknowledged = true)))
    assertFalse(ProEntitlementPolicy.resolveAccess(listOf(pending), cached = false))
    assertTrue(ProEntitlementPolicy.isPending(listOf(pending)))
    assertFalse(ProEntitlementPolicy.resolveAccess(emptyList(), cached = true)) // refund relocks
    assertTrue(ProEntitlementPolicy.resolveAccess(null, cached = true)) // offline
    assertFalse(ProEntitlementPolicy.resolveAccess(listOf(purchased.copy(productIds = listOf("other"))), cached = false))
    assertTrue(PiyokeyProPolicy.canInstallUserDeck(false, 2))
    assertFalse(PiyokeyProPolicy.canInstallUserDeck(false, 3))
    assertTrue(PiyokeyProPolicy.canInstallUserDeck(true, 50))
  }

  @Test fun boundedReadRejectsOversize() {
    assertContentEquals(ByteArray(10), DocumentIO.readCapped(ByteArrayInputStream(ByteArray(10)), 10))
    assertNull(DocumentIO.readCapped(ByteArrayInputStream(ByteArray(11)), 10))
    assertEquals(8L * 1024 * 1024, DocumentIO.MAX_BYTES)
  }

  @Test fun feedbackMailtoMatchesIosFormat() {
    val context = ContentFeedbackContext.report("flow_topik_beginner", 3)
    val body = ContentFeedbackLinkBuilder.body("Prompt", "Caution", context, "1.1.1", "7", "ja")
    assertEquals(
      "Prompt\n\n---\ntype: content_report\nsource: deck_detail\napp_version: 1.1.1\nbuild: 7\nlanguage: ja\n" +
        "deck_id: flow_topik_beginner\ndeck_version: 3\n\nCaution",
      body,
    )
    val url = ContentFeedbackLinkBuilder.mailto("A b&c", "P", "C", ContentFeedbackContext.general(ContentFeedbackSource.SETTINGS), "1", "2", "en")
    assertTrue(url.startsWith("mailto:contact@typee.app?subject=A%20b%26c&body="))
    assertFalse(url.contains('+') || url.contains('\n'))
  }
}
