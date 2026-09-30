package app.piyokey.android.feature.input

import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.focused
import androidx.compose.ui.semantics.insertTextAtCursor
import androidx.compose.ui.semantics.requestFocus
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.piyokey.android.R
import app.piyokey.android.platform.audio.SoundWarmupPolicy
import app.piyokey.android.ui.theme.Piyo
import app.piyokey.android.ui.theme.PiyoType
import app.piyokey.core.hangul.HangulComposer
import app.piyokey.core.hangul.JamoDecompositionError
import app.piyokey.core.hangul.OSIMECandidateTextJudge
import app.piyokey.core.hangul.OSIMETextJudge
import app.piyokey.core.hangul.OSIMETextJudgeStatus
import kotlin.math.PI
import kotlinx.coroutines.delay
import kotlin.math.sin

/** Test tags (iOS accessibility identifiers). */
object OsImeTags {
  const val TEXT_FIELD = ImeEditText.TAG
  const val CHROME = "os_ime.input.chrome"
  const val RECOVERY = "os_ime.input.recovery"
  const val INPUT_SOURCE_WARNING = "os_ime.input_source_warning"
  const val UNAVAILABLE_BANNER = "os_ime.unavailable.banner"
}

/** iOS `Color.orange` (light / dark). */
internal val WarningOrange: Color
  @Composable get() = if (Piyo.colors.isDark) Color(0xFFFF9F0A) else Color(0xFFFF9500)

/** iOS `OSIMEInputSourceBannerPresentation`. */
@Immutable
data class OsImeInputSourceBannerPresentation(
  val isVisible: Boolean = false,
  val isEmphasized: Boolean = false,
  val shakeStep: Float = 0f,
) {
  companion object {
    val Hidden = OsImeInputSourceBannerPresentation()
  }
}

/** True when the system "remove animations" setting is on (iOS `accessibilityReduceMotion`). */
@Composable
fun rememberReduceMotion(): Boolean {
  val context = LocalContext.current
  return remember(context) {
    runCatching {
      Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }.getOrDefault(false)
  }
}

/**
 * OS IME input panel. Port of iOS `OSIMEInputPanel`: an invisible `EditText` whose text is
 * judged with [OSIMETextJudge] (or [OSIMECandidateTextJudge] when [candidateTargets] is set).
 *
 * - Matching / composing mismatch → [onAcceptedSequence] (`acceptedSequence`).
 * - Confirmed mismatch → [onAcceptedSequence], [onConfirmedMismatch], then the field is rewritten to
 *   the accepted prefix (so the same mistake is never counted twice).
 * - Committed ASCII → nothing is reported; the ASCII is removed and the "keyboard is set to English"
 *   banner appears (shakes / flashes on repeats; colour flash only under reduced motion).
 * - Bumping [resetRevision] rewrites the field to [acceptedText] and clears the IME composition
 *   (`restartInput`); use it after a target change.
 *
 * Presentations: [showsChrome] = visible labelled panel; otherwise [showsFocusRecovery] on tablets
 * = compact "type with your keyboard" strip; otherwise the field fills the given area invisibly
 * (tap anywhere in it to bring the keyboard back).
 *
 * @param externalBanner when non-null the banner is not drawn here; its presentation is written to
 *   this state so the screen can place [OsImeInputSourceBannerLayer] above opaque content.
 */
@Composable
fun OsImeInputPanel(
  target: String,
  acceptedText: String,
  resetRevision: Int,
  onAcceptedSequence: (List<Char>) -> Unit,
  onConfirmedMismatch: () -> Unit,
  modifier: Modifier = Modifier,
  candidateTargets: List<String> = emptyList(),
  onAcceptedCandidateSequence: ((target: String, sequence: List<Char>) -> Unit)? = null,
  onInputStart: () -> Unit = {},
  showsChrome: Boolean = true,
  showsFocusRecovery: Boolean = false,
  isFocusSuspended: Boolean = false,
  externalBanner: MutableState<OsImeInputSourceBannerPresentation>? = null,
) {
  val metrics = Piyo.metrics
  val reduceMotion = rememberReduceMotion()
  var field by remember { mutableStateOf<ImeEditText?>(null) }
  var fieldText by remember { mutableStateOf(acceptedText) }
  var isFieldFocused by remember { mutableStateOf(false) }
  var banner by remember { mutableStateOf(OsImeInputSourceBannerPresentation.Hidden) }
  var feedbackRevision by remember { mutableIntStateOf(0) }

  val latest = rememberUpdatedState(
    PanelInputs(
      target, candidateTargets, acceptedText, onAcceptedSequence, onAcceptedCandidateSequence,
      onConfirmedMismatch, onInputStart, reduceMotion,
    ),
  )

  fun publish(presentation: OsImeInputSourceBannerPresentation) {
    banner = presentation
    externalBanner?.value = presentation
  }

  fun requestFocus() {
    field?.requestImeFocus()
  }

  fun rewriteField(text: String) {
    fieldText = text
    field?.replaceIfNotComposing(text)
  }

  fun handleUnsupportedAscii(acceptedSequence: List<Char>) {
    val shouldEmphasize = banner.isVisible
    publish(banner.copy(isVisible = true))
    rewriteField(HangulComposer.compose(acceptedSequence).text)
    if (!shouldEmphasize) return
    feedbackRevision++
    if (!latest.value.reduceMotion) publish(banner.copy(shakeStep = banner.shakeStep + 1f))
  }

  fun dismissWarning() {
    if (banner.isVisible || banner.isEmphasized) publish(banner.copy(isVisible = false, isEmphasized = false))
  }

  fun evaluate(committed: String, marked: String?) {
    val inputs = latest.value
    if (SoundWarmupPolicy.shouldPrepareForOsImeInput(committed, marked)) inputs.onInputStart()

    val onCandidate = inputs.onAcceptedCandidateSequence
    if (inputs.candidateTargets.isNotEmpty() && onCandidate != null) {
      val selection = OSIMECandidateTextJudge.evaluate(inputs.candidateTargets, inputs.target, committed, marked)
      if (selection != null) {
        val evaluation = selection.evaluation
        when (evaluation.status) {
          is OSIMETextJudgeStatus.Matching, OSIMETextJudgeStatus.ComposingMismatch -> {
            dismissWarning()
            onCandidate(selection.target, evaluation.acceptedSequence)
          }
          OSIMETextJudgeStatus.UnsupportedASCIIInput -> handleUnsupportedAscii(evaluation.acceptedSequence)
          is OSIMETextJudgeStatus.ConfirmedMismatch -> {
            dismissWarning()
            onCandidate(selection.target, evaluation.acceptedSequence)
            inputs.onConfirmedMismatch()
            rewriteField(HangulComposer.compose(evaluation.acceptedSequence).text)
          }
        }
        return
      }
    }

    val evaluation = try {
      OSIMETextJudge.evaluate(inputs.target, committed, marked)
    } catch (_: JamoDecompositionError) {
      return
    }
    when (evaluation.status) {
      is OSIMETextJudgeStatus.Matching, OSIMETextJudgeStatus.ComposingMismatch -> {
        dismissWarning()
        inputs.onAcceptedSequence(evaluation.acceptedSequence)
      }
      OSIMETextJudgeStatus.UnsupportedASCIIInput -> handleUnsupportedAscii(evaluation.acceptedSequence)
      is OSIMETextJudgeStatus.ConfirmedMismatch -> {
        dismissWarning()
        inputs.onAcceptedSequence(evaluation.acceptedSequence)
        inputs.onConfirmedMismatch()
        rewriteField(HangulComposer.compose(evaluation.acceptedSequence).text)
      }
    }
  }

  // Appear / disappear (iOS onAppear / onDisappear).
  DisposableEffect(Unit) {
    externalBanner?.value = banner
    onDispose { externalBanner?.value = OsImeInputSourceBannerPresentation.Hidden }
  }
  // Reset (target change / explicit reset). The first composition is the initial text, not a reset.
  var lastResetRevision by remember { mutableIntStateOf(resetRevision) }
  LaunchedEffect(resetRevision) {
    if (resetRevision == lastResetRevision) return@LaunchedEffect
    lastResetRevision = resetRevision
    val text = latest.value.acceptedText
    fieldText = text
    publish(banner.copy(isVisible = false))
    field?.scheduleReset(text)
    if (!isFocusSuspended) requestFocus()
  }
  // Emphasis flash (iOS feedback revision task: 0.1s in, hold 0.42s, 0.18s out).
  LaunchedEffect(feedbackRevision) {
    if (feedbackRevision == 0) return@LaunchedEffect
    publish(banner.copy(isEmphasized = true))
    delay(420)
    publish(banner.copy(isEmphasized = false))
  }
  // Scene becomes active again → refocus (iOS scenePhase == .active).
  LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
    if (!isFocusSuspended) requestFocus()
  }

  val inputField: @Composable (Modifier) -> Unit = { fieldModifier ->
    ImeTextField(
      initialText = acceptedText,
      targets = if (candidateTargets.isEmpty()) listOf(target) else candidateTargets,
      isFocusSuspended = isFocusSuspended,
      isFocused = isFieldFocused,
      onAttach = {
        field = it
        if (!isFocusSuspended) it.requestImeFocus()
      },
      onFocusChanged = { isFieldFocused = it },
      onReturn = { requestFocus() },
      onTextChange = { committed, marked ->
        fieldText = committed + (marked ?: "")
        evaluate(committed, marked)
      },
      modifier = fieldModifier,
    )
  }

  Box(modifier, contentAlignment = Alignment.TopCenter) {
    when {
      showsChrome -> VisibleInputPanel(fieldText, inputField)
      OsImeInputPanelPolicy.showsVisibleFocusRecovery(showsFocusRecovery, metrics) ->
        CompactInputPanel(isFieldFocused, inputField)
      else -> inputField(Modifier.fillMaxSize())
    }
    if (externalBanner == null) {
      OsImeInputSourceBannerLayer(banner, testTag = OsImeTags.INPUT_SOURCE_WARNING)
    }
  }
}

private data class PanelInputs(
  val target: String,
  val candidateTargets: List<String>,
  val acceptedText: String,
  val onAcceptedSequence: (List<Char>) -> Unit,
  val onAcceptedCandidateSequence: ((String, List<Char>) -> Unit)?,
  val onConfirmedMismatch: () -> Unit,
  val onInputStart: () -> Unit,
  val reduceMotion: Boolean,
)

/** Hosts [ImeEditText]; exposes text-input semantics so Compose tests can type into it. */
@Composable
private fun ImeTextField(
  initialText: String,
  targets: List<String>,
  isFocusSuspended: Boolean,
  isFocused: Boolean,
  onAttach: (ImeEditText) -> Unit,
  onFocusChanged: (Boolean) -> Unit,
  onReturn: () -> Unit,
  onTextChange: (String, String?) -> Unit,
  modifier: Modifier,
) {
  val label = stringResource(R.string.os_ime_input_accessibility_label)
  var view by remember { mutableStateOf<ImeEditText?>(null) }
  val latestOnTextChange by rememberUpdatedState(onTextChange)
  val latestOnReturn by rememberUpdatedState(onReturn)
  val latestOnFocusChanged by rememberUpdatedState(onFocusChanged)
  AndroidView(
    factory = { context ->
      ImeEditText(context).apply {
        setText(initialText)
        setSelection(initialText.length)
        contentDescription = label
        this.isFocusSuspended = isFocusSuspended
        this.targets = targets
        this.onTextChange = { committed, marked -> latestOnTextChange(committed, marked) }
        this.onReturn = { latestOnReturn() }
        onFocusStateChanged = { latestOnFocusChanged(it) }
        view = this
        onAttach(this)
      }
    },
    update = {
      it.targets = targets
      it.isFocusSuspended = isFocusSuspended
      it.contentDescription = label
    },
    onRelease = {
      it.onTextChange = { _, _ -> }
      view = null
    },
    modifier = modifier
      .testTag(OsImeTags.TEXT_FIELD)
      .semantics {
        focused = isFocused
        requestFocus {
          view?.requestImeFocus()
          true
        }
        setText { text ->
          val v = view ?: return@setText false
          v.setText(text.text)
          v.setSelection(text.length)
          true
        }
        insertTextAtCursor { text ->
          val v = view ?: return@insertTextAtCursor false
          v.text?.insert(v.selectionEnd.coerceAtLeast(0), text.text)
          true
        }
      },
  )
}

@Composable
private fun VisibleInputPanel(fieldText: String, inputField: @Composable (Modifier) -> Unit) {
  val colors = Piyo.colors
  Column(
    Modifier
      .fillMaxWidth()
      .background(colors.card.copy(alpha = if (colors.isDark) 0.7f else 0.55f))
      .padding(horizontal = 14.dp, vertical = 11.dp)
      .testTag(OsImeTags.CHROME),
    verticalArrangement = Arrangement.spacedBy(9.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Icon(Icons.Outlined.Keyboard, contentDescription = null, tint = colors.accent)
      Text(stringResource(R.string.os_ime_input_title), style = PiyoType.caption().copy(fontWeight = FontWeight.Bold))
      Spacer(Modifier.weight(1f))
      Text(
        stringResource(R.string.os_ime_input_live_badge),
        style = PiyoType.caption2().copy(fontWeight = FontWeight.Bold, color = colors.secondary),
      )
    }
    Box(
      Modifier
        .fillMaxWidth()
        .height(50.dp)
        .background(colors.backgroundTop, RoundedCornerShape(14.dp))
        .border(BorderStroke(1.5.dp, colors.accent.copy(alpha = 0.42f)), RoundedCornerShape(14.dp)),
    ) {
      inputField(Modifier.fillMaxSize())
      Row(
        Modifier.fillMaxSize().padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          fieldText.ifEmpty { "…" },
          style = PiyoType.title3().copy(
            fontWeight = FontWeight.Bold,
            color = if (fieldText.isEmpty()) colors.mutedInk else colors.ink,
          ),
          maxLines = 1,
          modifier = Modifier.weight(1f),
        )
        Icon(Icons.Filled.TouchApp, contentDescription = null, tint = colors.secondary)
      }
    }
    Text(stringResource(R.string.os_ime_android_input_hint), style = PiyoType.caption2().copy(color = colors.mutedInk))
  }
}

@Composable
private fun CompactInputPanel(isFieldFocused: Boolean, inputField: @Composable (Modifier) -> Unit) {
  val colors = Piyo.colors
  val shape = RoundedCornerShape(15.dp)
  Box(
    Modifier
      .fillMaxWidth()
      .heightIn(min = 48.dp)
      .background(colors.card.copy(alpha = 0.96f), shape)
      .border(BorderStroke(1.5.dp, colors.accent.copy(alpha = if (isFieldFocused) 0.26f else 0.55f)), shape),
    contentAlignment = Alignment.Center,
  ) {
    inputField(Modifier.matchParentSize())
    Row(
      Modifier.padding(horizontal = 14.dp).testTag(OsImeTags.RECOVERY),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      val tint = if (isFieldFocused) colors.secondary else colors.accent
      Icon(if (isFieldFocused) Icons.Filled.Keyboard else Icons.Outlined.Keyboard, contentDescription = null, tint = tint)
      Text(stringResource(R.string.os_ime_input_title), style = PiyoType.caption().copy(fontWeight = FontWeight.Bold, color = tint))
    }
  }
}

/** iOS `OSIMEInputSourceBannerLayer`: slides in from the top; shakes horizontally on repeats. */
@Composable
fun OsImeInputSourceBannerLayer(
  presentation: OsImeInputSourceBannerPresentation,
  modifier: Modifier = Modifier,
  testTag: String = OsImeTags.INPUT_SOURCE_WARNING,
) {
  val shake = remember { Animatable(presentation.shakeStep) }
  LaunchedEffect(presentation.shakeStep) {
    shake.animateTo(presentation.shakeStep, tween(300, easing = LinearEasing))
  }
  AnimatedVisibility(
    visible = presentation.isVisible,
    modifier = modifier,
    enter = slideInVertically(tween(180)) { -it } + fadeIn(tween(180)),
    exit = slideOutVertically(tween(180)) { -it } + fadeOut(tween(180)),
  ) {
    OsImeInputSourceBanner(
      isEmphasized = presentation.isEmphasized,
      modifier = Modifier
        .padding(start = 12.dp, end = 12.dp, top = 10.dp)
        .graphicsLayer { translationX = 9.dp.toPx() * sin(shake.value * PI.toFloat() * 6f) }
        .testTag(testTag),
    )
  }
}

/** iOS `OSIMEInputSourceBanner` ("Your keyboard is set to English"). */
@Composable
fun OsImeInputSourceBanner(isEmphasized: Boolean, modifier: Modifier = Modifier) {
  val colors = Piyo.colors
  val orange = WarningOrange
  val shape = RoundedCornerShape(15.dp)
  val background by animateColorAsState(orange.copy(alpha = if (isEmphasized) 0.34f else 0.16f), tween(100), label = "bannerBg")
  val stroke by animateColorAsState(orange.copy(alpha = if (isEmphasized) 0.9f else 0.45f), tween(100), label = "bannerStroke")
  val title = stringResource(R.string.os_ime_input_source_warning_title)
  val detail = stringResource(R.string.os_ime_android_input_source_warning_detail)
  Row(
    modifier
      .fillMaxWidth()
      .background(colors.card, shape)
      .background(background, shape)
      .border(BorderStroke(if (isEmphasized) 2.5.dp else 1.dp, stroke), shape)
      .padding(horizontal = 14.dp, vertical = 11.dp)
      .semantics(mergeDescendants = true) { contentDescription = "$title. $detail" },
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Icon(Icons.Filled.Language, contentDescription = null, tint = orange)
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(title, style = PiyoType.subheadline().copy(fontWeight = FontWeight.Bold))
      Text(detail, style = PiyoType.caption().copy(color = colors.mutedInk))
    }
  }
}

/**
 * iOS `OSIMEUnavailableBanner`, Android wording: a *hint* that no Korean keyboard was detected.
 * In sessions leave [onOpenGuide] null (no interruptions); elsewhere it adds a guide link.
 */
@Composable
fun OsImeUnavailableBanner(modifier: Modifier = Modifier, onOpenGuide: (() -> Unit)? = null) {
  val colors = Piyo.colors
  Column(
    modifier
      .fillMaxWidth()
      .background(WarningOrange.copy(alpha = 0.16f), RoundedCornerShape(13.dp))
      .padding(horizontal = 13.dp, vertical = 10.dp)
      .testTag(OsImeTags.UNAVAILABLE_BANNER),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Icon(Icons.Filled.Warning, contentDescription = null, tint = WarningOrange, modifier = Modifier.size(18.dp))
      Text(
        stringResource(R.string.keyboard_guide_android_not_detected),
        style = PiyoType.caption().copy(fontWeight = FontWeight.SemiBold),
      )
    }
    if (onOpenGuide != null) {
      Text(
        stringResource(R.string.keyboard_guide_android_link),
        style = PiyoType.caption().copy(fontWeight = FontWeight.Bold, color = colors.accent),
        modifier = Modifier
          .defaultMinSize(minHeight = 44.dp)
          .clickable(role = Role.Button, onClick = onOpenGuide)
          .padding(vertical = 12.dp),
      )
    }
  }
}

/**
 * iOS `SessionInputModeControl`: built-in / OS keyboard segmented control. Selecting OS keyboard
 * when no Korean keyboard is detected still selects it (Android hint policy) and calls
 * [onUnavailableOsIme] so the screen can show [OsImeUnavailableBanner].
 */
@Composable
fun SessionInputModeControl(
  selection: SessionInputMode,
  onSelect: (SessionInputMode) -> Unit,
  modifier: Modifier = Modifier,
  onUnavailableOsIme: () -> Unit = {},
) {
  val context = LocalContext.current
  val colors = Piyo.colors
  Row(
    modifier
      .fillMaxWidth()
      .background(colors.card.copy(alpha = 0.92f), RoundedCornerShape(15.dp))
      .padding(5.dp),
    horizontalArrangement = Arrangement.spacedBy(7.dp),
  ) {
    listOf(
      Triple(SessionInputMode.BUILT_IN, R.string.input_mode_builtin, Icons.Filled.ViewModule),
      Triple(SessionInputMode.OS_IME, R.string.input_mode_os_ime, Icons.Filled.Keyboard),
    ).forEach { (mode, title, icon) ->
      val selected = selection == mode || (mode == SessionInputMode.BUILT_IN && selection == SessionInputMode.BUILT_IN_KOREAN_10KEY)
      Row(
        Modifier
          .weight(1f)
          .background(if (selected) colors.accent else Color.Transparent, RoundedCornerShape(11.dp))
          .clickable(role = Role.Button) {
            if (mode == SessionInputMode.OS_IME && !KoreanKeyboardAvailability.isAvailable(context)) onUnavailableOsIme()
            onSelect(mode)
          }
          .defaultMinSize(minHeight = 44.dp)
          .padding(vertical = 9.dp)
          .semantics { this.selected = selected }
          .testTag("input_mode.${mode.raw}"),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        val tint = if (selected) Color.White else colors.mutedInk
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp).padding(end = 2.dp))
        Text(stringResource(title), style = PiyoType.caption().copy(fontWeight = FontWeight.Bold, color = tint))
      }
    }
  }
}
