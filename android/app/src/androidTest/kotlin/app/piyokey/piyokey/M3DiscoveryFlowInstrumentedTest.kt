package app.piyokey.piyokey

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import java.io.File
import java.io.FileOutputStream
import org.junit.Rule
import org.junit.Test

class M3DiscoveryFlowInstrumentedTest {
  @get:Rule
  val composeRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun bundledDeckCanBeDiscoveredInstalledAndPlayedOffline() {
    composeRule.waitUntil(timeoutMillis = 15_000) {
      runCatching { composeRule.onNodeWithTag("nav-discover").fetchSemanticsNode() }.isSuccess
    }
    saveScreenshot("01-home.png")
    composeRule.onNodeWithTag("nav-discover").performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) {
      runCatching {
        composeRule.onNodeWithTag("deck-card-official_keyboard_start", useUnmergedTree = true)
          .fetchSemanticsNode()
      }.isSuccess
    }
    saveScreenshot("02-discover.png")
    composeRule.onNodeWithTag("deck-card-official_keyboard_start", useUnmergedTree = true).performClick()
    saveScreenshot("03-detail.png")
    composeRule.onNodeWithTag("deck-detail-download").assertIsDisplayed().performClick()
    composeRule.waitUntil(timeoutMillis = 15_000) {
      runCatching { composeRule.onNodeWithTag("deck-detail-play").fetchSemanticsNode() }.isSuccess
    }
    composeRule.onNodeWithTag("deck-detail-play").assertIsDisplayed().performClick()
    composeRule.onNodeWithTag("practice-screen").assertIsDisplayed()
    saveScreenshot("04-practice.png")
  }

  private fun saveScreenshot(name: String) {
    val directory = File(composeRule.activity.getExternalFilesDir(null), "m3-evidence").apply {
      mkdirs()
    }
    val output = File(directory, name)
    FileOutputStream(output).use { stream ->
      composeRule.onRoot().captureToImage().asAndroidBitmap()
        .compress(Bitmap.CompressFormat.PNG, 100, stream)
    }
    println("PIYOKEY_M3_SCREENSHOT=${output.absolutePath}")
  }
}
