package app.piyokey.android.ui.mascot

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.piyokey.android.data.mascot.MascotEggPattern
import app.piyokey.android.data.mascot.MascotGrowthAppearance
import app.piyokey.android.data.mascot.MascotMood
import app.piyokey.android.data.mascot.MascotProp
import app.piyokey.android.data.mascot.MascotStage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Visual QA aid: renders a sheet of stages, moods and props and writes
 * `files/mascot_sheet.png` in the app sandbox (pull with `adb exec-out run-as <appId> cat files/mascot_sheet.png`).
 */
@RunWith(AndroidJUnit4::class)
class MascotScreenshotInstrumentedTest {
  @get:Rule val rule = createComposeRule()

  @Test
  fun renderSheet() {
    rule.setContent {
      CompositionLocalProvider(LocalMascotAnimationsEnabled provides false) {
        Column(Modifier.testTag("sheet").background(Color.White)) {
          Row {
            for (stage in MascotStage.entries) ChickMascot(stage = stage, eggPattern = MascotEggPattern.HEARTS, size = 72.dp)
            ChickMascot(stage = MascotStage.CRACKING, eggPattern = MascotEggPattern.STARS, size = 72.dp,
              growthAppearance = MascotGrowthAppearance.of(0f, 1f, 0.35f, 3))
            ChickMascot(stage = MascotStage.EGG, eggPattern = MascotEggPattern.POLKA, size = 72.dp)
          }
          for (chunk in MascotMood.entries.chunked(8)) {
            Row { for (mood in chunk) ChickMascot(mood = mood, size = 60.dp) }
          }
          for (chunk in MascotProp.entries.chunked(6)) {
            Row { for (prop in chunk) ChickMascot(stage = MascotStage.CHICK, prop = prop, size = 72.dp) }
          }
          Row {
            ChickMascot(stage = MascotStage.ROOSTER, prop = MascotProp.GRAD_CAP, size = 72.dp, nameTag = "Piyo",
              growthAppearance = MascotGrowthAppearance.of(1f, 1f, 1f, 3))
            ChickMascot(size = 72.dp, speech = "Let's go!", showsFriend = true)
          }
        }
      }
    }
    rule.waitForIdle()
    val bitmap = rule.onNodeWithTag("sheet").captureToImage().asAndroidBitmap()
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    File(context.filesDir, "mascot_sheet.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
  }
}
