package app.piyokey.piyokey

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performScrollToNode
import androidx.test.platform.app.InstrumentationRegistry
import androidx.core.content.FileProvider
import app.piyokey.core.data.PIYODECK_STAGING_DIRECTORY_NAME
import app.piyokey.core.platform.PiyoDeckDocumentGateway
import app.piyokey.core.piyodeck.PiyoDeckPackageReader
import app.piyokey.core.piyodeck.PiyoDeckPackageWriter
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

class R11UserDeckDocumentInstrumentedTest {
  val composeRule = createAndroidComposeRule<MainActivity>()

  @get:Rule
  val rules: RuleChain = RuleChain
    .outerRule(TestAppStateRule(skipOnboarding = true, resetStorage = true))
    .around(composeRule)

  @Test
  fun externalDeckWaitsForPracticeExitThenPreviewsAndImportsWithoutPurchase() {
    waitForShell()
    composeRule.onNodeWithTag("nav-discover").performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) {
      composeRule.onAllNodesWithTag("deck-card-official_keyboard_start", useUnmergedTree = true)
        .fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag("deck-card-official_keyboard_start", useUnmergedTree = true).performClick()
    composeRule.onNodeWithTag("deck-detail-download").performClick()
    composeRule.waitUntil(timeoutMillis = 15_000) {
      composeRule.onAllNodesWithTag("deck-detail-play").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag("deck-detail-play").performClick()
    composeRule.onNodeWithTag("practice-screen").assertIsDisplayed()

    val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
    val incomingFile = File(targetContext.cacheDir, "shared_results/r11-external.piyodeck").apply {
      parentFile?.mkdirs()
      InstrumentationRegistry.getInstrumentation().context.assets.open("valid/basic.piyodeck").use { input ->
        outputStream().use { output -> input.copyTo(output) }
      }
    }
    val incomingUri = FileProvider.getUriForFile(
      targetContext,
      "${targetContext.packageName}.files",
      incomingFile,
    )
    composeRule.activityRule.scenario.onActivity { activity ->
      activity.publishIncomingDocument(
        Intent(Intent.ACTION_VIEW)
          .setDataAndType(incomingUri, PiyoDeckDocumentGateway.MIME_TYPE)
          .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
      )
    }
    composeRule.waitUntil(timeoutMillis = 10_000) {
      File(targetContext.cacheDir, PIYODECK_STAGING_DIRECTORY_NAME)
        .listFiles().orEmpty().isNotEmpty()
    }
    composeRule.onNodeWithTag("practice-screen").assertIsDisplayed()
    check(composeRule.onAllNodesWithTag("user-deck-import-screen").fetchSemanticsNodes().isEmpty())

    composeRule.onNodeWithTag("close-practice-session").performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) {
      composeRule.onAllNodesWithTag("confirm-import").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag("user-deck-import-screen").assertIsDisplayed()
    composeRule.onNodeWithText("はじめてのマイデッキ").assertIsDisplayed()
    composeRule.onNodeWithTag("confirm-import").performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) {
      composeRule.onAllNodesWithTag("user-deck-import-screen").fetchSemanticsNodes().isEmpty()
    }
  }

  @Test
  fun rejectedExternalDeckRemovesItsPrivateStagingCopy() {
    waitForShell()
    val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
    val incomingFile = File(targetContext.cacheDir, "shared_results/r11-invalid.piyodeck").apply {
      parentFile?.mkdirs()
      InstrumentationRegistry.getInstrumentation().context.assets.open("invalid/wrong-sha.piyodeck").use { input ->
        outputStream().use { output -> input.copyTo(output) }
      }
    }
    val incomingUri = FileProvider.getUriForFile(
      targetContext,
      "${targetContext.packageName}.files",
      incomingFile,
    )

    composeRule.activityRule.scenario.onActivity { activity ->
      activity.publishIncomingDocument(
        Intent(Intent.ACTION_VIEW)
          .setDataAndType(incomingUri, PiyoDeckDocumentGateway.MIME_TYPE)
          .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
      )
    }

    composeRule.waitUntil(timeoutMillis = 10_000) {
      composeRule.onAllNodesWithTag("import-error").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag("import-error").assertIsDisplayed()
    composeRule.waitUntil(timeoutMillis = 10_000) {
      File(targetContext.cacheDir, PIYODECK_STAGING_DIRECTORY_NAME)
        .listFiles().orEmpty().none { it.extension in setOf("piyodeck", "pending") }
    }
  }

  @Test
  fun paidMakerEntryShowsPaywallWhileFreeDocumentFlowRemainsUngated() {
    waitForShell()
    composeRule.onNodeWithTag("nav-profile").performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) {
      composeRule.onAllNodesWithTag("new-deck").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag("import-deck").assertIsDisplayed()
    composeRule.onNodeWithTag("new-deck").performClick()
    composeRule.onNodeWithTag("deck-maker-paywall").assertIsDisplayed()
    composeRule.onNodeWithTag("deck-maker-purchase").assertIsDisplayed()
    check(composeRule.onAllNodesWithTag("user-deck-import-screen").fetchSemanticsNodes().isEmpty())
  }

  @Test
  fun conflictingImportOffersPaidSeparateCopyAndReturnsIntactAfterClosingPaywall() {
    waitForShell()
    val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
    val schema = targetContext.assets.open("deck.schema.json").bufferedReader().use { it.readText() }
    val originalBytes = InstrumentationRegistry.getInstrumentation().context.assets
      .open("valid/basic.piyodeck").use { it.readBytes() }
    val original = File(targetContext.cacheDir, "shared_results/original.piyodeck").apply {
      parentFile?.mkdirs()
      writeBytes(originalBytes)
    }
    publish(original)
    composeRule.waitUntil(timeoutMillis = 10_000) {
      composeRule.onAllNodesWithTag("confirm-import").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag("confirm-import").performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) {
      composeRule.onAllNodesWithTag("user-deck-import-screen").fetchSemanticsNodes().isEmpty()
    }

    val parsed = PiyoDeckPackageReader.read(originalBytes, schema)
    val changed = parsed.deck.copy(
      items = parsed.deck.items.mapIndexed { index, item ->
        if (index == 0) item.copy(meaningJa = item.meaningJa + "（別内容）") else item
      },
    )
    val conflict = File(targetContext.cacheDir, "shared_results/conflict.piyodeck").apply {
      writeBytes(PiyoDeckPackageWriter.write(changed, schema))
    }
    publish(conflict)
    composeRule.waitUntil(timeoutMillis = 10_000) {
      composeRule.onAllNodesWithTag("user-deck-import-screen").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag("user-deck-import-screen").performScrollToNode(hasTestTag("import-as-copy"))
    composeRule.onNodeWithTag("import-as-copy").assertIsDisplayed().performClick()
    composeRule.onNodeWithTag("deck-maker-paywall").assertIsDisplayed()

    composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
    composeRule.onNodeWithTag("user-deck-import-screen").performScrollToNode(hasTestTag("import-as-copy"))
    composeRule.onNodeWithTag("import-as-copy").assertIsDisplayed()
  }

  private fun publish(file: File) {
    val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
    val uri = FileProvider.getUriForFile(targetContext, "${targetContext.packageName}.files", file)
    composeRule.activityRule.scenario.onActivity { activity ->
      activity.publishIncomingDocument(
        Intent(Intent.ACTION_VIEW)
          .setDataAndType(uri, PiyoDeckDocumentGateway.MIME_TYPE)
          .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
      )
    }
  }

  private fun waitForShell() {
    composeRule.waitUntil(timeoutMillis = 15_000) {
      composeRule.onAllNodesWithTag("nav-discover").fetchSemanticsNodes().isNotEmpty()
    }
  }
}
