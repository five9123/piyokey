package app.piyokey.android.feature.input

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import app.piyokey.android.R
import app.piyokey.android.platform.audio.TypingSoundKeyRole
import app.piyokey.android.ui.theme.Piyo
import app.piyokey.core.hangul.Korean10KeyFlickGestureResolver
import app.piyokey.core.hangul.Korean10KeyFlickMapping
import app.piyokey.core.hangul.Korean10KeyKey
import app.piyokey.core.hangul.Korean10KeyTouchInterpretation

/** Test tags / accessibility identifiers (same strings as the iOS accessibility identifiers). */
object KeyboardTags {
  const val DUBEOLSIK_CONTAINER = "keyboard.container"
  const val TEN_KEY_CONTAINER = "keyboard.10key.container"
  const val SHIFT = "keyboard.shift"
  const val BACKSPACE = "keyboard.backspace"
  const val SPACE = "keyboard.space"
  fun key(jamo: Char) = "keyboard.key.$jamo"
  fun tenKey(key: Korean10KeyKey) = "keyboard.10key.${key.rawValue}"
}

/**
 * In-app dubeolsik (2-set) keyboard. Port of iOS `HangulKeyboardView`.
 *
 * Every key is committed on touch-down (multi-touch rollover); wrong-key rejection is the
 * caller's job (feed [onKey] into the jamo judge). Shift is one-shot: it turns off after the
 * next character.
 *
 * @param nextExpectedKey the judge's next jamo; drives the key-guide highlight (`null` = none).
 * @param onInputStart called first on every activation with the pointer-event uptime (latency probe).
 * @param onKeyFeedback key sound hook (`character` / `shift` / `backspace`).
 * @param onKey a jamo (or `' '` for space) was pressed.
 */
@Composable
fun DubeolsikKeyboard(
  nextExpectedKey: Char?,
  onKey: (Char) -> Unit,
  onBackspace: () -> Unit,
  modifier: Modifier = Modifier,
  options: HangulKeyboardOptions = HangulKeyboardOptions(),
  onInputStart: (eventUptimeMillis: Long) -> Unit = {},
  onKeyFeedback: (TypingSoundKeyRole) -> Unit = {},
) {
  val metrics = Piyo.metrics
  val view = LocalView.current
  val registry = rememberKeyboardTouchRegistry()
  var isShifted by remember { mutableStateOf(false) }
  val pulse = rememberGuidePulse(options.showsKeyGuide)
  val latestOnKey by rememberUpdatedState(onKey)
  val latestOnBackspace by rememberUpdatedState(onBackspace)
  val latestOnInputStart by rememberUpdatedState(onInputStart)
  val latestOnKeyFeedback by rememberUpdatedState(onKeyFeedback)
  val latestOptions by rememberUpdatedState(options)

  val activate: (KeyboardAction, Long) -> Unit = activate@{ action, uptime ->
    latestOnInputStart(uptime)
    val role = when (action) {
      is KeyboardAction.Character -> {
        latestOnKey(action.jamo)
        if (isShifted) isShifted = false
        TypingSoundKeyRole.CHARACTER
      }
      KeyboardAction.Shift -> {
        isShifted = !isShifted
        TypingSoundKeyRole.SHIFT
      }
      KeyboardAction.Backspace -> {
        latestOnBackspace()
        TypingSoundKeyRole.BACKSPACE
      }
      is KeyboardAction.TenKey -> return@activate
    }
    KeyHaptics.fire(view, latestOptions.hapticsEnabled)
    latestOnKeyFeedback(role)
  }

  val accessibilityLabel = stringResource(R.string.keyboard_accessibility_label)
  Box(
    modifier
      .fillMaxWidth()
      .background(keyboardSurfaceColor())
      .semantics { contentDescription = accessibilityLabel }
      .testTag(KeyboardTags.DUBEOLSIK_CONTAINER)
      .padding(start = 8.dp, end = 8.dp, top = 10.dp, bottom = 8.dp),
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
      KeyRow {
        DubeolsikKey.TOP_ROW.forEach { CharacterKey(it, isShifted, nextExpectedKey, options, pulse, registry, activate) }
      }
      KeyRow(Modifier.padding(horizontal = 12.dp)) {
        DubeolsikKey.HOME_ROW.forEach { CharacterKey(it, isShifted, nextExpectedKey, options, pulse, registry, activate) }
      }
      KeyRow {
        Keycap(
          action = KeyboardAction.Shift,
          label = stringResource(R.string.keyboard_shift),
          tag = KeyboardTags.SHIFT,
          highlighted = DubeolsikGuide.shiftHighlighted(options.showsKeyGuide, nextExpectedKey, isShifted),
          pulse = pulse,
          registry = registry,
          onActivate = activate,
          icon = if (isShifted) ShiftFilledIcon else ShiftIcon,
          stateDescription = if (isShifted) "on" else null,
        )
        DubeolsikKey.BOTTOM_ROW.forEach { CharacterKey(it, isShifted, nextExpectedKey, options, pulse, registry, activate) }
        Keycap(
          action = KeyboardAction.Backspace,
          label = stringResource(R.string.keyboard_backspace),
          tag = KeyboardTags.BACKSPACE,
          highlighted = false,
          pulse = pulse,
          registry = registry,
          onActivate = activate,
          icon = Icons.AutoMirrored.Outlined.Backspace,
        )
      }
      Box(Modifier.fillMaxWidth().padding(horizontal = 56.dp), contentAlignment = Alignment.Center) {
        val spaceMaxWidth = if (metrics.isExpanded) metrics.availableWidth * 0.4f else 180.dp
        Keycap(
          action = KeyboardAction.Character(' '),
          label = stringResource(R.string.keyboard_space),
          tag = KeyboardTags.SPACE,
          highlighted = DubeolsikGuide.spaceHighlighted(options.showsKeyGuide, nextExpectedKey),
          pulse = pulse,
          registry = registry,
          onActivate = activate,
          modifier = Modifier.widthIn(max = spaceMaxWidth).fillMaxWidth(),
          title = "",
          overlay = {
            Box(
              Modifier.size(width = 56.dp, height = 4.dp).background(Piyo.colors.mutedInk.copy(alpha = 0.24f), CircleShape),
            )
          },
        )
      }
    }
    Box(
      Modifier
        .matchParentSize()
        .rolloverTouchSurface(
          registry = registry,
          onTouchBegan = { action, uptime ->
            registry.press(action)
            activate(action, uptime)
          },
          onTouchEnded = { action, _ -> registry.release(action) },
        ),
    )
  }
}

/**
 * In-app Korean 10-key (Cheonjiin) keyboard. Port of iOS `Korean10KeyKeyboardView`.
 *
 * Layout `ㅣ ㆍ ㅡ / ㄱㅋ ㄴㄹ ㄷㅌ / ㅂㅍ ㅅㅎ ㅈㅊ / → ㅇㅁ ⌫ / space`, max 600dp wide on tablets.
 * Flick-capable keys (all stroke/consonant keys) resolve on touch-up: a tap calls [onKey], a
 * flick calls [onCompletedJamo] with the mapped jamo, and an unassigned direction, slow or
 * diagonal flick calls `onCompletedJamo(null)`. `→`, space and ⌫ act on touch-down.
 * There is intentionally no visual flick preview (TYP-111).
 *
 * @param nextExpectedKey `Korean10KeyInterpreter.nextKey(expected)` for the guide highlight.
 */
@Composable
fun Korean10KeyKeyboard(
  nextExpectedKey: Korean10KeyKey?,
  onKey: (Korean10KeyKey) -> Unit,
  onCompletedJamo: (Char?) -> Unit,
  onBackspace: () -> Unit,
  modifier: Modifier = Modifier,
  options: HangulKeyboardOptions = HangulKeyboardOptions(showsRomanHints = false),
  onInputStart: (eventUptimeMillis: Long) -> Unit = {},
  onKeyFeedback: (TypingSoundKeyRole) -> Unit = {},
) {
  val metrics = Piyo.metrics
  val view = LocalView.current
  val registry = rememberKeyboardTouchRegistry()
  val pulse = rememberGuidePulse(options.showsKeyGuide)
  val latestOnKey by rememberUpdatedState(onKey)
  val latestOnCompletedJamo by rememberUpdatedState(onCompletedJamo)
  val latestOnBackspace by rememberUpdatedState(onBackspace)
  val latestOnInputStart by rememberUpdatedState(onInputStart)
  val latestOnKeyFeedback by rememberUpdatedState(onKeyFeedback)
  val latestOptions by rememberUpdatedState(options)

  // Tap / accessibility activation and immediate keys (→, space, ⌫).
  val activate: (KeyboardAction, Long) -> Unit = activate@{ action, uptime ->
    latestOnInputStart(uptime)
    val role = when (action) {
      is KeyboardAction.TenKey -> {
        latestOnKey(action.key)
        TypingSoundKeyRole.CHARACTER
      }
      KeyboardAction.Backspace -> {
        latestOnBackspace()
        TypingSoundKeyRole.BACKSPACE
      }
      is KeyboardAction.Character, KeyboardAction.Shift -> return@activate
    }
    KeyHaptics.fire(view, latestOptions.hapticsEnabled)
    latestOnKeyFeedback(role)
  }

  val accessibilityLabel = stringResource(R.string.keyboard_10key_accessibility_label)
  Box(
    modifier
      .fillMaxWidth()
      .background(keyboardSurfaceColor())
      .semantics { contentDescription = accessibilityLabel }
      .testTag(KeyboardTags.TEN_KEY_CONTAINER)
      .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 8.dp),
    contentAlignment = Alignment.TopCenter,
  ) {
    Box(Modifier.widthIn(max = if (metrics.isExpanded) 600.dp else Dp.Infinity).fillMaxWidth()) {
      Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Korean10KeyLayout.ROWS.forEach { row ->
          KeyRow(spacing = 7.dp) { row.forEach { TenKey(it, nextExpectedKey, options, pulse, registry, activate) } }
        }
        KeyRow(spacing = 7.dp) {
          TenKey(Korean10KeyKey.NEXT, nextExpectedKey, options, pulse, registry, activate)
          TenKey(Korean10KeyKey.IEUNG, nextExpectedKey, options, pulse, registry, activate)
          Keycap(
            action = KeyboardAction.Backspace,
            label = stringResource(R.string.keyboard_backspace),
            tag = KeyboardTags.BACKSPACE,
            highlighted = false,
            pulse = pulse,
            registry = registry,
            onActivate = activate,
            icon = Icons.AutoMirrored.Outlined.Backspace,
            height = 52.dp,
          )
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 56.dp)) {
          TenKey(Korean10KeyKey.SPACE, nextExpectedKey, options, pulse, registry, activate, icon = Icons.Filled.SpaceBar)
        }
      }
      Box(
        Modifier
          .matchParentSize()
          .rolloverTouchSurface(
            registry = registry,
            onTouchBegan = { action, uptime ->
              registry.press(action)
              if (action is KeyboardAction.TenKey && action.key.supportsFlick) {
                // Flick keys decide tap vs flick on touch-up; give press feedback now.
                KeyHaptics.fire(view, latestOptions.hapticsEnabled)
                latestOnKeyFeedback(TypingSoundKeyRole.CHARACTER)
              } else {
                activate(action, uptime)
              }
            },
            onTouchEnded = { action, gesture ->
              if (action is KeyboardAction.TenKey && action.key.supportsFlick && !gesture.wasCancelled) {
                latestOnInputStart(nowUptimeMillis())
                when (
                  val touch = Korean10KeyFlickGestureResolver.interpretation(
                    translationX = gesture.translationXDp,
                    translationY = gesture.translationYDp,
                    durationSeconds = gesture.durationSeconds,
                  )
                ) {
                  Korean10KeyTouchInterpretation.Tap -> latestOnKey(action.key)
                  is Korean10KeyTouchInterpretation.Flick ->
                    latestOnCompletedJamo(Korean10KeyFlickMapping.completedJamo(action.key, touch.direction))
                  Korean10KeyTouchInterpretation.InvalidFlick -> latestOnCompletedJamo(null)
                }
              }
              registry.release(action)
            },
          ),
      )
    }
  }
}

@Composable
private fun keyboardSurfaceColor(): Color =
  // iOS `.ultraThinMaterial`: a translucent frosted strip over the session background.
  Piyo.colors.card.copy(alpha = if (Piyo.colors.isDark) 0.7f else 0.55f)

@Composable
private fun KeyRow(
  modifier: Modifier = Modifier,
  spacing: Dp = 5.dp,
  content: @Composable RowScope.() -> Unit,
) {
  Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing), content = content)
}

@Composable
private fun RowScope.CharacterKey(
  definition: DubeolsikKey,
  isShifted: Boolean,
  expected: Char?,
  options: HangulKeyboardOptions,
  pulse: State<Float>,
  registry: KeyboardTouchRegistry,
  onActivate: (KeyboardAction, Long) -> Unit,
) {
  val character = definition.output(isShifted)
  Keycap(
    action = KeyboardAction.Character(character),
    label = character.toString(),
    tag = KeyboardTags.key(character),
    highlighted = DubeolsikGuide.keyHighlighted(options.showsKeyGuide, expected, character, isShifted),
    pulse = pulse,
    registry = registry,
    onActivate = onActivate,
    modifier = Modifier.weight(1f),
    title = character.toString(),
    romanHint = if (options.showsRomanHints) definition.roman else null,
  )
}

@Composable
private fun RowScope.TenKey(
  key: Korean10KeyKey,
  expected: Korean10KeyKey?,
  options: HangulKeyboardOptions,
  pulse: State<Float>,
  registry: KeyboardTouchRegistry,
  onActivate: (KeyboardAction, Long) -> Unit,
  icon: ImageVector? = null,
) {
  Keycap(
    action = KeyboardAction.TenKey(key),
    label = stringResource(tenKeyLabelRes(key)),
    tag = KeyboardTags.tenKey(key),
    highlighted = options.showsKeyGuide && expected == key,
    pulse = pulse,
    registry = registry,
    onActivate = onActivate,
    modifier = Modifier.weight(1f),
    title = if (icon == null) key.displayText else null,
    icon = icon,
    hint = flickHintText(key),
    height = 52.dp,
  )
}

private fun tenKeyLabelRes(key: Korean10KeyKey): Int = when (key) {
  Korean10KeyKey.VERTICAL -> R.string.keyboard_10key_vertical
  Korean10KeyKey.DOT -> R.string.keyboard_10key_dot
  Korean10KeyKey.HORIZONTAL -> R.string.keyboard_10key_horizontal
  Korean10KeyKey.GIYEOK -> R.string.keyboard_10key_giyeok
  Korean10KeyKey.NIEUN -> R.string.keyboard_10key_nieun
  Korean10KeyKey.DIGEUT -> R.string.keyboard_10key_digeut
  Korean10KeyKey.BIEUP -> R.string.keyboard_10key_bieup
  Korean10KeyKey.SIOT -> R.string.keyboard_10key_siot
  Korean10KeyKey.JIEUT -> R.string.keyboard_10key_jieut
  Korean10KeyKey.IEUNG -> R.string.keyboard_10key_ieung
  Korean10KeyKey.NEXT -> R.string.keyboard_10key_next
  Korean10KeyKey.SPACE -> R.string.keyboard_space
}

@Composable
private fun flickHintText(key: Korean10KeyKey): String? = when (val hint = Korean10KeyLayout.flickHint(key)) {
  is Korean10KeyLayout.FlickHint.Fixed -> when (key) {
    Korean10KeyKey.VERTICAL -> stringResource(R.string.keyboard_10key_flick_vertical)
    Korean10KeyKey.DOT -> stringResource(R.string.keyboard_10key_flick_dot)
    else -> stringResource(R.string.keyboard_10key_flick_horizontal)
  }
  is Korean10KeyLayout.FlickHint.Three -> stringResource(R.string.keyboard_10key_flick_three, hint.left, hint.right, hint.down)
  is Korean10KeyLayout.FlickHint.Two -> stringResource(R.string.keyboard_10key_flick_two, hint.left, hint.right)
  Korean10KeyLayout.FlickHint.None -> null
}

/** The key-guide pulse (iOS `.easeInOut(duration: 1).repeatForever(autoreverses: true)`). */
@Composable
private fun rememberGuidePulse(enabled: Boolean): State<Float> {
  if (!enabled) return remember { mutableStateOf(0f) }
  return rememberInfiniteTransition(label = "keyGuidePulse").animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
    label = "keyGuidePulse",
  )
}

/**
 * iOS private `Keycap`: rounded key, accent glow when highlighted (pulsing), 0.95 press scale.
 * Touches are handled by the keyboard's rollover surface; the key only exposes semantics
 * (label, roman hint as state, flick hint, selected when highlighted, click = activate).
 */
@Composable
private fun Keycap(
  action: KeyboardAction,
  label: String,
  tag: String,
  highlighted: Boolean,
  pulse: State<Float>,
  registry: KeyboardTouchRegistry,
  onActivate: (KeyboardAction, Long) -> Unit,
  modifier: Modifier = Modifier,
  title: String? = null,
  icon: ImageVector? = null,
  romanHint: String? = null,
  hint: String? = null,
  stateDescription: String? = null,
  height: Dp = 50.dp,
  overlay: (@Composable () -> Unit)? = null,
) {
  val colors = Piyo.colors
  val metrics = Piyo.metrics
  val pressed = registry.isPressed(action)
  val scale by animateFloatAsState(
    targetValue = if (pressed) 0.95f else 1f,
    animationSpec = tween(60, easing = LinearOutSlowInEasing),
    label = "keyPress",
  )
  val shape = RoundedCornerShape(11.dp)
  val expandedFactor = if (metrics.isExpanded) 1.15f else 1f
  val semanticsState = listOfNotNull(stateDescription, romanHint, hint).joinToString(", ").ifEmpty { null }
  Box(
    modifier
      .height(height * metrics.keyboardScale)
      .registerKey(registry, action)
      .graphicsLayer {
        scaleX = scale
        scaleY = scale
        val p = if (highlighted) pulse.value else 0f
        this.shape = shape
        clip = false
        shadowElevation = (if (highlighted) lerp(3f, 12f, p) else 3f).dp.toPx()
        val shadow = if (highlighted) colors.accent.copy(alpha = lerp(0.32f, 0.72f, p)) else colors.keyShadow
        ambientShadowColor = shadow
        spotShadowColor = shadow
      }
      .background(if (highlighted) colors.accentSoft else colors.key, shape)
      .testTag(tag)
      .clearAndSetSemantics {
        contentDescription = label
        role = Role.Button
        if (semanticsState != null) this.stateDescription = semanticsState
        selected = highlighted
        onClick { onActivate(action, nowUptimeMillis()); true }
      },
    contentAlignment = Alignment.Center,
  ) {
    when {
      title != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (title.isNotEmpty()) {
          Text(
            title,
            color = colors.ink,
            fontSize = Piyo.sp(21f * expandedFactor),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
          )
        }
        if (romanHint != null) {
          Text(
            romanHint,
            color = colors.mutedInk,
            fontSize = Piyo.sp(9f * expandedFactor),
            fontWeight = FontWeight.Medium,
            maxLines = 1,
          )
        }
      }
      icon != null -> Icon(
        icon,
        contentDescription = null,
        tint = if (highlighted) colors.accent else colors.ink,
        modifier = Modifier.size((22f * Piyo.fontScale).dp),
      )
    }
    overlay?.invoke()
  }
}

private fun shiftVector(name: String, filled: Boolean): ImageVector =
  ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
    path(
      fill = if (filled) SolidColor(Color.Black) else null,
      stroke = SolidColor(Color.Black),
      strokeLineWidth = 1.8f,
      strokeLineJoin = StrokeJoin.Round,
      strokeLineCap = StrokeCap.Round,
    ) {
      moveTo(12f, 3.5f)
      lineTo(3.5f, 12.5f)
      lineTo(8.25f, 12.5f)
      lineTo(8.25f, 20f)
      lineTo(15.75f, 20f)
      lineTo(15.75f, 12.5f)
      lineTo(20.5f, 12.5f)
      close()
    }
  }.build()

/** SF Symbol `shift` / `shift.fill` equivalents (Material has no shift glyph). */
private val ShiftIcon: ImageVector by lazy { shiftVector("shift", filled = false) }
private val ShiftFilledIcon: ImageVector by lazy { shiftVector("shift.fill", filled = true) }
