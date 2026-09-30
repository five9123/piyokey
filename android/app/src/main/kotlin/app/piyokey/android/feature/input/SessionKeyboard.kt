package app.piyokey.android.feature.input

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.piyokey.android.platform.audio.SoundEngine
import app.piyokey.android.platform.audio.TypingSoundKeyRole
import app.piyokey.core.hangul.Korean10KeyBackspaceResult
import app.piyokey.core.hangul.Korean10KeyInterpretation
import app.piyokey.core.hangul.Korean10KeyInterpreter
import app.piyokey.core.hangul.Korean10KeyKey

/**
 * Per-session keyboard state: the active input mode/layout, the Korean 10-key stroke buffer and
 * the OS-IME reset revision. This is the part of iOS `PracticeView`/`FlowGameView` state that is
 * shared by every session screen (`inputMode`, `builtInKeyboardLayout`,
 * `korean10KeyInterpreter`, `inputResetRevision`, `showsOSIMEUnavailable`).
 *
 * Create with [rememberSessionKeyboardState] (reads the user's keyboard defaults) and call
 * [resetForNewTarget] whenever the target/question changes.
 */
@Stable
class SessionKeyboardState(
  initialMode: SessionInputMode = SessionInputMode.BUILT_IN,
  initialLayout: BuiltInKeyboardLayout = BuiltInKeyboardLayout.DUBEOLSIK,
  /** False for screens without an OS-IME path (iOS `allowsOSKeyboard`). */
  val allowsOsIme: Boolean = true,
  /** False for screens that only support dubeolsik (iOS `allowsKorean10Key`). */
  val allowsKorean10Key: Boolean = true,
) {
  /** [SessionInputMode.BUILT_IN] or [SessionInputMode.OS_IME] (never the record-only 10-key value). */
  var mode by mutableStateOf(SessionInputMode.persistedDefault(initialMode))
    private set
  var layout by mutableStateOf(initialLayout)
    private set

  /** Current 10-key stroke buffer. Read [korean10KeyPendingDisplay] for the composition preview. */
  var korean10Key by mutableStateOf(Korean10KeyInterpreter())
    private set

  /** Bumped by [resetForNewTarget] / mode changes; drives the OS-IME field reset. */
  var resetRevision by mutableIntStateOf(0)
    private set

  /** "No Korean keyboard detected" hint (never blocks OS-IME mode on Android). */
  var showsOsImeUnavailable by mutableStateOf(false)

  /** While true the OS-IME field gives up focus (settings sheet, pause, result overlay). */
  var isOsImeFocusSuspended by mutableStateOf(false)

  /** Input-source ("keyboard is set to English") banner, for screens that draw it themselves. */
  val osImeBanner: MutableState<OsImeInputSourceBannerPresentation> =
    mutableStateOf(OsImeInputSourceBannerPresentation.Hidden)

  val usesOsIme: Boolean get() = allowsOsIme && mode == SessionInputMode.OS_IME
  val usesKorean10Key: Boolean
    get() = !usesOsIme && allowsKorean10Key && layout == BuiltInKeyboardLayout.KOREAN_10KEY

  /** Mode to store in game records / analytics (`builtin`, `builtin_korean_10key`, `os_ime`). */
  val recordInputMode: SessionInputMode
    get() = when {
      usesOsIme -> SessionInputMode.OS_IME
      usesKorean10Key -> SessionInputMode.BUILT_IN_KOREAN_10KEY
      else -> SessionInputMode.BUILT_IN
    }

  /** Unfinished 10-key strokes to append to the composition preview (`null` when none). */
  val korean10KeyPendingDisplay: String? get() = if (usesKorean10Key) korean10Key.pendingDisplay else null

  /** New target/question: drop pending 10-key strokes and reset the OS-IME field. */
  fun resetForNewTarget() {
    korean10Key = korean10Key.reset()
    resetRevision++
  }

  /** Switches built-in ↔ OS keyboard; [persist] also stores it as the user's default (iOS binding). */
  fun selectMode(newMode: SessionInputMode, persist: Boolean = true) {
    val resolved = SessionInputMode.persistedDefault(newMode)
    if (persist) KeyboardPreferences.setDefaultInputMode(resolved)
    if (resolved == mode) return
    mode = resolved
    showsOsImeUnavailable = false
    resetForNewTarget()
  }

  fun selectLayout(newLayout: BuiltInKeyboardLayout, persist: Boolean = true) {
    if (persist) KeyboardPreferences.setBuiltInLayout(newLayout)
    if (newLayout == layout) return
    layout = newLayout
    resetForNewTarget()
  }

  /** 10-key tap (also `→` and space). */
  fun korean10KeyInput(key: Korean10KeyKey, expected: Char?): Korean10KeyInterpretation {
    val step = korean10Key.input(key, expected)
    korean10Key = step.interpreter
    return step.interpretation
  }

  /** 10-key flick (`jamo == null` for an invalid / unassigned flick). */
  fun korean10KeyCompletedJamo(jamo: Char?, expected: Char?): Korean10KeyInterpretation {
    val step = korean10Key.inputCompletedJamo(jamo, expected)
    korean10Key = step.interpreter
    return step.interpretation
  }

  fun korean10KeyBackspace(): Korean10KeyBackspaceResult {
    val step = korean10Key.backspace()
    korean10Key = step.interpreter
    return step.result
  }
}

/**
 * Session keyboard state initialised from the user's defaults, like iOS
 * `resolveInitialInputModeIfNeeded`. When the default is OS keyboard and no Korean keyboard is
 * detected, the session still starts in OS-IME mode but [SessionKeyboardState.showsOsImeUnavailable]
 * is set (Android: hint, never block).
 */
@Composable
fun rememberSessionKeyboardState(
  allowsOsIme: Boolean = true,
  allowsKorean10Key: Boolean = true,
): SessionKeyboardState {
  val context = LocalContext.current
  return remember {
    val snapshot = KeyboardPreferences.snapshot
    val mode = if (allowsOsIme && snapshot.defaultInputMode == SessionInputMode.OS_IME) {
      SessionInputMode.OS_IME
    } else {
      SessionInputMode.BUILT_IN
    }
    SessionKeyboardState(mode, snapshot.builtInLayout, allowsOsIme, allowsKorean10Key).also {
      if (it.usesOsIme && !KoreanKeyboardAvailability.isAvailable(context)) it.showsOsImeUnavailable = true
    }
  }
}

/** Live [HangulKeyboardOptions] from `keyboard.shows_key_guide` / `shows_roman_hints` / `haptics_enabled`. */
@Composable
fun rememberKeyboardOptions(): HangulKeyboardOptions {
  val guide by KeyboardPreferences.showsKeyGuide.flow.collectAsState()
  val roman by KeyboardPreferences.showsRomanHints.flow.collectAsState()
  val haptics by KeyboardPreferences.hapticsEnabled.flow.collectAsState()
  return HangulKeyboardOptions(guide, roman, haptics)
}

const val SESSION_KEYBOARD_TAG = "session_keyboard"

/**
 * The input area every practice / game screen embeds (iOS `inputArea` of `PracticeView`,
 * `FlowGameView`, `ChoseongQuizView`). Picks the dubeolsik keyboard, the 10-key keyboard or the
 * OS-IME field from [state], runs the 10-key interpreter and reports judged-ready events:
 *
 * - [onJamo]: one standard jamo (or `' '`) to feed the jamo judge + composer. Dubeolsik keys are
 *   reported as pressed (wrong keys included — the judge rejects them); 10-key strokes are only
 *   reported once the expected jamo's recipe is complete.
 * - [onBackspace]: forward a backspace to the Hangul engine (10-key pending strokes are rewound here).
 * - [onKorean10KeyMistake]: a 10-key stroke/flick that cannot reach the expected jamo (count a mistake).
 * - OS IME: [onOsImeAcceptedSequence] (set progress to the accepted jamo prefix) and
 *   [onOsImeConfirmedMismatch] (count one mistake); optional multi-target variant for acid rain.
 *
 * @param expectedNextJamo the judge's next jamo; required for 10-key interpretation even when hidden.
 * @param revealsExpectedKey false in recall games: no key-guide highlight, no physical-guide key.
 * @param osImeTarget target text for the OS-IME judge (current word).
 * @param osImeAcceptedText composed text accepted so far; the OS-IME field is reset to it.
 * @param osImeResetRevision extra reset trigger (e.g. a game's card revision), added to the state's.
 * @param embedsOsImeField false when the screen hosts [OsImeInputPanel] itself (e.g. Practice's
 *   full-area hidden field on phones); the keyboard area then only shows guides/banners.
 * @param showsInputSourceBanner draw the English-keyboard banner above the OS-IME field. Set false
 *   and render `OsImeInputSourceBannerLayer(state.osImeBanner.value)` to place it elsewhere.
 * @param onInputStart pointer-event uptime of each key press (feed [InputLatencyMonitor.beginInput]).
 */
@Composable
fun SessionKeyboard(
  state: SessionKeyboardState,
  expectedNextJamo: Char?,
  osImeTarget: String,
  osImeAcceptedText: String,
  onJamo: (Char) -> Unit,
  onBackspace: () -> Unit,
  onKorean10KeyMistake: () -> Unit,
  onOsImeAcceptedSequence: (List<Char>) -> Unit,
  onOsImeConfirmedMismatch: () -> Unit,
  modifier: Modifier = Modifier,
  revealsExpectedKey: Boolean = true,
  options: HangulKeyboardOptions = rememberKeyboardOptions(),
  showsPhysicalKeyboardGuide: Boolean = KeyboardPreferences.showsPhysicalKeyboardGuide.flow.collectAsState().value,
  osImeCandidateTargets: List<String> = emptyList(),
  onOsImeAcceptedCandidateSequence: ((target: String, sequence: List<Char>) -> Unit)? = null,
  osImeResetRevision: Int = 0,
  embedsOsImeField: Boolean = true,
  showsInputSourceBanner: Boolean = true,
  onInputStart: (eventUptimeMillis: Long) -> Unit = {},
  onKeyFeedback: (TypingSoundKeyRole) -> Unit = { SoundEngine.keyTap(it) },
  onOsImeInputStart: () -> Unit = { SoundEngine.warmUp() },
) {
  val latestExpected by rememberUpdatedState(expectedNextJamo)
  val latestOnJamo by rememberUpdatedState(onJamo)
  val latestOnBackspace by rememberUpdatedState(onBackspace)
  val latestOnMistake by rememberUpdatedState(onKorean10KeyMistake)
  val effectiveOptions = if (revealsExpectedKey) options else options.copy(showsKeyGuide = false)

  fun apply(interpretation: Korean10KeyInterpretation) {
    when (interpretation) {
      is Korean10KeyInterpretation.Committed -> latestOnJamo(interpretation.jamo)
      is Korean10KeyInterpretation.Incorrect -> latestOnMistake()
      is Korean10KeyInterpretation.Pending, Korean10KeyInterpretation.SeparatorAccepted -> Unit
    }
  }

  Column(modifier.fillMaxWidth().testTag(SESSION_KEYBOARD_TAG), verticalArrangement = Arrangement.spacedBy(7.dp)) {
    if (state.showsOsImeUnavailable) {
      OsImeUnavailableBanner(Modifier.padding(horizontal = 12.dp))
    }
    when {
      state.usesOsIme -> {
        if (embedsOsImeField) {
          if (showsInputSourceBanner) OsImeInputSourceBannerLayer(state.osImeBanner.value)
          OsImeInputPanel(
            target = osImeTarget,
            acceptedText = osImeAcceptedText,
            resetRevision = state.resetRevision + osImeResetRevision,
            onAcceptedSequence = onOsImeAcceptedSequence,
            onConfirmedMismatch = onOsImeConfirmedMismatch,
            candidateTargets = osImeCandidateTargets,
            onAcceptedCandidateSequence = onOsImeAcceptedCandidateSequence,
            onInputStart = onOsImeInputStart,
            showsChrome = false,
            showsFocusRecovery = true,
            isFocusSuspended = state.isOsImeFocusSuspended,
            externalBanner = state.osImeBanner,
            modifier = Modifier.fillMaxWidth().height(56.dp),
          )
        }
        if (showsPhysicalKeyboardGuide) {
          PhysicalKeyboardGuide(
            nextExpectedKey = if (revealsExpectedKey) expectedNextJamo else null,
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 6.dp),
          )
        }
      }
      state.usesKorean10Key -> Korean10KeyKeyboard(
        nextExpectedKey = state.korean10Key.nextKey(expectedNextJamo),
        options = effectiveOptions.copy(showsRomanHints = false),
        onInputStart = onInputStart,
        onKeyFeedback = onKeyFeedback,
        onKey = { key -> apply(state.korean10KeyInput(key, latestExpected)) },
        onCompletedJamo = { jamo -> apply(state.korean10KeyCompletedJamo(jamo, latestExpected)) },
        onBackspace = {
          when (state.korean10KeyBackspace()) {
            is Korean10KeyBackspaceResult.PendingChanged -> Unit
            Korean10KeyBackspaceResult.ForwardToHangulEngine -> latestOnBackspace()
          }
        },
      )
      else -> DubeolsikKeyboard(
        nextExpectedKey = if (revealsExpectedKey) expectedNextJamo else null,
        options = effectiveOptions,
        onInputStart = onInputStart,
        onKeyFeedback = onKeyFeedback,
        onKey = { latestOnJamo(it) },
        onBackspace = { latestOnBackspace() },
      )
    }
  }
}
