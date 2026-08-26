package app.piyokey.core.analytics

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnalyticsContractTest {
  @Test
  fun commonAndEventPropertiesAreAccepted() {
    assertTrue(
      AnalyticsContract.accepts(
        AnalyticsEvent.FEATURE_VIEWED,
        mapOf(
          AnalyticsProperty.SCHEMA_VERSION to 1,
          AnalyticsProperty.PLATFORM to "android",
          AnalyticsProperty.APP_VERSION to "1.1.0",
          AnalyticsProperty.BUILD_NUMBER to "8",
          AnalyticsProperty.LOCALE to "ko",
          AnalyticsProperty.FEATURE to "practice",
        ),
      ),
    )
  }

  @Test
  fun propertyFromAnotherEventIsRejected() {
    assertFalse(
      AnalyticsContract.accepts(
        AnalyticsEvent.APP_OPENED,
        mapOf(AnalyticsProperty.PURCHASE_STATE to "completed"),
      ),
    )
  }

  @Test
  fun missingRequiredPropertyIsRejected() {
    assertFalse(
      AnalyticsContract.accepts(
        AnalyticsEvent.FEATURE_VIEWED,
        mapOf(
          AnalyticsProperty.SCHEMA_VERSION to 1,
          AnalyticsProperty.PLATFORM to "android",
          AnalyticsProperty.APP_VERSION to "1.1.0",
          AnalyticsProperty.BUILD_NUMBER to "8",
          AnalyticsProperty.LOCALE to "ko",
        ),
      ),
    )
  }

  @Test
  fun unknownEnumValueIsRejected() {
    assertFalse(
      AnalyticsContract.accepts(
        AnalyticsEvent.FEATURE_VIEWED,
        mapOf(
          AnalyticsProperty.SCHEMA_VERSION to 1,
          AnalyticsProperty.PLATFORM to "android",
          AnalyticsProperty.APP_VERSION to "1.1.0",
          AnalyticsProperty.BUILD_NUMBER to "8",
          AnalyticsProperty.LOCALE to "ko",
          AnalyticsProperty.FEATURE to "typed free text",
        ),
      ),
    )
  }
}
