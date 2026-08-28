package app.piyokey.piyokey

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import app.piyokey.core.piyodeck.PiyoDeckPackageLimits
import app.piyokey.core.piyodeck.UserDeckDraft
import app.piyokey.core.piyodeck.UserDeckItemDraft
import app.piyokey.core.piyodeck.UserDeckLanguage
import app.piyokey.feature.discover.UserDeckEditorScreen
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

class R11DeckMakerEditorInstrumentedTest {
  val composeRule = createAndroidComposeRule<MainActivity>()

  @get:Rule
  val rules: RuleChain = RuleChain
    .outerRule(TestAppStateRule(skipOnboarding = true, resetStorage = true))
    .around(composeRule)

  @Test
  fun validationAnnouncesAndFocusesTheFirstInvalidField() {
    val draft = UserDeckDraft.new(Instant.parse("2026-08-25T00:00:00Z")) { "1".repeat(32) }
    composeRule.activityRule.scenario.onActivity { activity ->
      activity.setContent {
        MaterialTheme {
          UserDeckEditorScreen(
            initialDraft = draft,
            isWorking = false,
            saveError = false,
            onDraftChanged = {},
            onSave = { _, _ -> error("invalid draft must not save") },
            onClose = {},
          )
        }
      }
    }

    composeRule.onNodeWithTag("deck-editor-save").performClick()
    composeRule.onNodeWithTag("deck-editor-validation").assertIsDisplayed()
    composeRule.waitUntil(timeoutMillis = 5_000) {
      runCatching { composeRule.onNodeWithTag("deck-editor-name").assertIsFocused() }.isSuccess
    }
  }

  @Test
  fun validationExpandsScrollsAndFocusesTheFirstInvalidItemField() {
    val base = UserDeckDraft.new(Instant.parse("2026-08-25T00:00:00Z")) { "1".repeat(32) }
    val draft = base.copy(
      name = "Deck",
      authorNickname = "Author",
      tags = listOf("test"),
      items = listOf(
        base.items.single().copy(ko = "가", readingJa = "カ", meaningJa = "意味"),
        UserDeckItemDraft(id = "item_${"2".repeat(32)}"),
      ),
    )
    composeRule.activityRule.scenario.onActivity { activity ->
      activity.setContent {
        MaterialTheme {
          UserDeckEditorScreen(
            initialDraft = draft,
            isWorking = false,
            saveError = false,
            onDraftChanged = {},
            onSave = { _, _ -> error("invalid draft must not save") },
            onClose = {},
          )
        }
      }
    }

    composeRule.onNodeWithTag("deck-editor-save").performClick()
    composeRule.waitUntil(timeoutMillis = 5_000) {
      runCatching { composeRule.onNodeWithTag("deck-editor-item-1-ko").assertIsFocused() }.isSuccess
    }
    composeRule.onNodeWithTag("deck-editor-item-1-ko").assertIsDisplayed().assertIsFocused()
  }

  @Test
  fun activityPauseFlushesTheCurrentDraftWithoutWaitingForDebounce() {
    val draft = UserDeckDraft.new(Instant.parse("2026-08-25T00:00:00Z")) { "3".repeat(32) }
    val flushed = AtomicReference<UserDeckDraft?>()
    composeRule.activityRule.scenario.onActivity { activity ->
      activity.setContent {
        MaterialTheme {
          UserDeckEditorScreen(
            initialDraft = draft,
            isWorking = false,
            saveError = false,
            onDraftChanged = flushed::set,
            onSave = { _, _ -> },
            onClose = {},
          )
        }
      }
    }
    composeRule.onNodeWithTag("deck-editor-name").performTextInput("Background draft")
    composeRule.mainClock.autoAdvance = false

    composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
    composeRule.mainClock.advanceTimeByFrame()

    composeRule.waitUntil(timeoutMillis = 5_000) {
      flushed.get()?.let { saved ->
        UserDeckLanguage.entries.any { language -> saved.name(language) == "Background draft" }
      } == true
    }
    composeRule.mainClock.autoAdvance = true
    composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
  }

  @Test
  fun thousandItemEditorKeepsRowsReachableThroughOneLazyCollapsibleList() {
    val base = UserDeckDraft.new(Instant.parse("2026-08-25T00:00:00Z")) { "1".repeat(32) }
    val large = base.copy(
      name = "Large",
      authorNickname = "Author",
      tags = listOf("test"),
      items = List(PiyoDeckPackageLimits.MAXIMUM_ITEM_COUNT) { index ->
        UserDeckItemDraft(
          id = "item_${(index + 1).toString(16).padStart(32, '0')}",
          ko = "가",
          readingJa = "カ",
          meaningJa = "意味",
        )
      },
    )
    composeRule.activityRule.scenario.onActivity { activity ->
      activity.setContent {
        MaterialTheme {
          UserDeckEditorScreen(
            initialDraft = large,
            isWorking = false,
            saveError = false,
            onDraftChanged = {},
            onSave = { _, _ -> },
            onClose = {},
          )
        }
      }
    }

    composeRule.onNodeWithTag("deck-maker-editor")
      .performScrollToNode(hasText("1000. 가"))
    composeRule.onNode(hasText("1000. 가")).assertIsDisplayed()
  }
}
