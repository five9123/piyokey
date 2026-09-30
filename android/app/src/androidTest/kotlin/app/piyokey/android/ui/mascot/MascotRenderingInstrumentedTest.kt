package app.piyokey.android.ui.mascot

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.piyokey.android.data.mascot.MascotEggPattern
import app.piyokey.android.data.mascot.MascotMood
import app.piyokey.android.data.mascot.MascotProp
import app.piyokey.android.data.mascot.MascotReaction
import app.piyokey.android.data.mascot.MascotStage
import app.piyokey.android.ui.theme.LightPalette
import app.piyokey.android.ui.theme.LocalPalette
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Smoke tests: every stage, mood, prop and pattern renders; the closet composes. */
@RunWith(AndroidJUnit4::class)
class MascotRenderingInstrumentedTest {
  @get:Rule val rule = createComposeRule()

  @Test
  fun everyStageRenders() {
    rule.setContent {
      CompositionLocalProvider(LocalMascotAnimationsEnabled provides false, LocalPalette provides LightPalette) {
        Row {
          for (stage in MascotStage.entries) {
            ChickMascot(stage = stage, eggPattern = MascotEggPattern.HEARTS, size = 48.dp, nameTag = "Piyo")
          }
        }
      }
    }
    rule.waitForIdle()
    assertEquals(MascotStage.entries.size, rule.onAllNodesWithTag("mascot.current").fetchSemanticsNodes().size)
  }

  @Test
  fun everyMoodPropAndPatternRenders() {
    rule.setContent {
      CompositionLocalProvider(LocalMascotAnimationsEnabled provides false) {
        Column {
          Row { for (mood in MascotMood.entries) ChickMascot(mood = mood, size = 24.dp) }
          Row { for (prop in MascotProp.entries) ChickMascot(stage = MascotStage.ROOSTER, prop = prop, size = 24.dp) }
          Row {
            for (pattern in MascotEggPattern.entries) ChickMascot(stage = MascotStage.CRACKING, eggPattern = pattern, size = 24.dp)
          }
          ChickMascot(
            reaction = MascotReaction.ComboMilestone(12),
            reactionRevision = 1,
            speech = "잘했어!",
            showsFriend = true,
            size = 48.dp,
          )
        }
      }
    }
    rule.waitForIdle()
  }

  @Test
  fun interactiveMascotExposesTag() {
    rule.setContent {
      CompositionLocalProvider(LocalMascotAnimationsEnabled provides false) {
        ChickMascot(interactive = true, onOpenCloset = {}, size = 64.dp)
      }
    }
    rule.onNodeWithTag("mascot.interactive").assertIsDisplayed()
  }

  @Test
  fun closetRenders() {
    rule.setContent {
      CompositionLocalProvider(LocalMascotAnimationsEnabled provides false) {
        MascotClosetScreen(onDone = {})
      }
    }
    rule.onNodeWithTag("closet.preview").assertIsDisplayed()
    rule.onNodeWithTag("mascot.name").assertIsDisplayed()
  }

  @Test
  fun growthCelebrationRenders() {
    rule.setContent {
      CompositionLocalProvider(LocalMascotAnimationsEnabled provides false) {
        GrowthCelebration(newStage = MascotStage.HATCHING, onDone = {})
      }
    }
    rule.onNodeWithTag("mascot.growth.confirm").assertIsDisplayed()
  }
}
