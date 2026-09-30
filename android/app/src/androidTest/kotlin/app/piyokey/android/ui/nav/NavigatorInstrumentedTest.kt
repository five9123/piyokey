package app.piyokey.android.ui.nav

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.center
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigatorInstrumentedTest {
  @get:Rule val rule = createComposeRule()

  /** Plain `remember` state, like a game controller or practice runner. */
  private class CounterRoute : Route {
    @Composable
    override fun Content() {
      var count by remember { mutableIntStateOf(0) }
      Box(Modifier.fillMaxSize()) {
        Text("$count", Modifier.testTag("counter").clickable { count++ })
      }
    }
  }

  private class BlankRoute : Route {
    @Composable
    override fun Content() {
      Box(Modifier.fillMaxSize().testTag("blank"))
    }
  }

  @Test
  fun lowerEntryKeepsStateAcrossPushAndPop() {
    val navigator = Navigator(CounterRoute())
    rule.setContent { navigator.RenderStack() }
    rule.onNodeWithTag("counter").performClick().performClick()
    rule.onNodeWithTag("counter").assertTextEquals("2")

    rule.runOnIdle { navigator.push(BlankRoute()) }
    rule.waitForIdle()
    rule.runOnIdle { navigator.pop() }
    rule.waitForIdle()

    rule.onNodeWithTag("counter").assertTextEquals("2")
  }

  @Test
  fun touchesOnTopEntryDoNotReachEntriesBelow() {
    var belowClicks = 0
    val below = object : Route {
      @Composable
      override fun Content() {
        Box(Modifier.fillMaxSize().testTag("below").clickable { belowClicks++ })
      }
    }
    val navigator = Navigator(below)
    rule.setContent { navigator.RenderStack() }
    rule.runOnIdle { navigator.push(BlankRoute()) }
    rule.waitForIdle()

    rule.onNodeWithTag("blank").performTouchInput { click(center) }
    rule.waitForIdle()
    assertEquals(0, belowClicks)
  }
}
