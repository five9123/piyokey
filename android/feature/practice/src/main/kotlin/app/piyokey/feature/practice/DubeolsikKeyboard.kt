package app.piyokey.feature.practice

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.piyokey.feature.practice.KeyboardAction.Backspace
import app.piyokey.feature.practice.KeyboardAction.JamoKey
import app.piyokey.feature.practice.KeyboardAction.Shift
import app.piyokey.feature.practice.KeyboardAction.Space

data class PracticeKeyboardOptions(
  val showsKeyGuide: Boolean = true,
  val showsRomanHints: Boolean = true,
  val hapticsEnabled: Boolean = true,
)

/**
 * App-owned two-beolsik keyboard. Its overlay receives every pointer at the initial pass and sends
 * input on touch-down, so two fingers can roll over independently without waiting for either up.
 */
@Composable
fun DubeolsikKeyboard(
  nextExpectedJamo: Char?,
  onJamo: (Char) -> Unit,
  onBackspace: () -> Unit,
  modifier: Modifier = Modifier,
  options: PracticeKeyboardOptions = PracticeKeyboardOptions(),
) {
  var isShifted by remember { mutableStateOf(false) }
  val shiftLatch = remember { KeyboardShiftLatch() }
  val pressedCounts = remember { mutableStateMapOf<KeyboardAction, Int>() }
  val touchTracker = remember { RolloverTouchTracker() }
  val targetRegistry = remember { KeyboardTouchTargetRegistry() }
  var overlayCoordinates: LayoutCoordinates? by remember { mutableStateOf(null) }
  val touchOutset = with(LocalDensity.current) { 4.dp.toPx() }
  val view = LocalView.current

  val guide = KeyboardGuideResolver.resolve(
    expected = nextExpectedJamo,
    isShifted = isShifted,
    enabled = options.showsKeyGuide,
  )
  val guidePulse by rememberInfiniteTransition(label = "keyboard-guide").animateFloat(
    initialValue = 0.34f,
    targetValue = 0.78f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 1_000),
      repeatMode = RepeatMode.Reverse,
    ),
    label = "keyboard-guide-alpha",
  )

  val latestOnJamo = rememberUpdatedState(onJamo)
  val latestOnBackspace = rememberUpdatedState(onBackspace)
  val latestHapticsEnabled = rememberUpdatedState(options.hapticsEnabled)

  val activationHandler = rememberUpdatedState<(KeyboardAction) -> Unit> { action ->
    when (action) {
      is JamoKey -> {
        val definition = requireNotNull(DubeolsikLayout.definitionFor(action.base))
        latestOnJamo.value(shiftLatch.consume(definition))
        isShifted = shiftLatch.isShifted
      }
      Space -> {
        latestOnJamo.value(' ')
        shiftLatch.consumeSpace()
        isShifted = shiftLatch.isShifted
      }
      Shift -> isShifted = shiftLatch.toggle()
      Backspace -> latestOnBackspace.value()
    }

    if (latestHapticsEnabled.value) {
      view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }
  }

  val keyboardLabel = stringResource(R.string.keyboard_accessibility_label)

  Box(
    modifier = modifier
      .fillMaxWidth()
      .background(KeyboardColors.KeyboardBackground)
      .padding(horizontal = 8.dp, vertical = 8.dp)
      .semantics { contentDescription = keyboardLabel },
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
      KeyboardCharacterRow(
        definitions = DubeolsikLayout.topRow,
        isShifted = isShifted,
        guide = guide,
        guidePulse = guidePulse,
        pressedCounts = pressedCounts,
        targetRegistry = targetRegistry,
        options = options,
        onActivate = activationHandler.value,
      )
      KeyboardCharacterRow(
        definitions = DubeolsikLayout.homeRow,
        isShifted = isShifted,
        guide = guide,
        guidePulse = guidePulse,
        pressedCounts = pressedCounts,
        targetRegistry = targetRegistry,
        options = options,
        onActivate = activationHandler.value,
        modifier = Modifier.padding(horizontal = 12.dp),
      )
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        KeyboardKeycap(
          title = "⇧",
          accessibilityLabel = stringResource(R.string.keyboard_shift),
          action = Shift,
          highlighted = guide.highlightedAction == Shift,
          guidePulse = guidePulse,
          pressed = (pressedCounts[Shift] ?: 0) > 0,
          targetRegistry = targetRegistry,
          onActivate = activationHandler.value,
          modifier = Modifier.weight(1.12f),
          selected = isShifted,
        )
        DubeolsikLayout.bottomRow.forEach { definition ->
          val action = JamoKey(definition.base)
          KeyboardKeycap(
            title = definition.output(isShifted).toString(),
            romanHint = definition.roman.takeIf { options.showsRomanHints },
            accessibilityLabel = keyboardKeyDescription(definition, isShifted, options),
            action = action,
            highlighted = guide.highlightedAction == action,
            guidePulse = guidePulse,
            pressed = (pressedCounts[action] ?: 0) > 0,
            targetRegistry = targetRegistry,
            onActivate = activationHandler.value,
            modifier = Modifier.weight(1f),
          )
        }
        KeyboardKeycap(
          title = "⌫",
          accessibilityLabel = stringResource(R.string.keyboard_backspace),
          action = Backspace,
          highlighted = false,
          guidePulse = guidePulse,
          pressed = (pressedCounts[Backspace] ?: 0) > 0,
          targetRegistry = targetRegistry,
          onActivate = activationHandler.value,
          modifier = Modifier.weight(1.12f),
        )
      }
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
      ) {
        KeyboardKeycap(
          title = "",
          accessibilityLabel = stringResource(R.string.keyboard_space),
          action = Space,
          highlighted = guide.highlightedAction == Space,
          guidePulse = guidePulse,
          pressed = (pressedCounts[Space] ?: 0) > 0,
          targetRegistry = targetRegistry,
          onActivate = activationHandler.value,
          modifier = Modifier
            .fillMaxWidth(0.52f)
            .widthIn(max = 180.dp),
          spaceBar = true,
        )
      }
    }

    // The transparent surface owns raw touch delivery; keycaps retain explicit semantics actions.
    Box(
      modifier = Modifier
        .matchParentSize()
        .onGloballyPositioned { overlayCoordinates = it }
        .pointerInput(touchOutset) {
          try {
            awaitPointerEventScope {
              while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.forEach { change ->
                  val pointerId = change.id.value
                  when {
                    change.changedToDownIgnoreConsumed() -> {
                      val rootPoint = overlayCoordinates?.localToRoot(change.position)
                        ?: return@forEach
                      val action = targetRegistry.resolve(rootPoint, touchOutset)
                        ?: return@forEach
                      if (touchTracker.begin(pointerId, action)) {
                        pressedCounts[action] = touchTracker.pressedCount(action)
                        activationHandler.value(action)
                      }
                      change.consume()
                    }
                    change.changedToUpIgnoreConsumed() || !change.pressed -> {
                      touchTracker.end(pointerId)?.let { action ->
                        val count = touchTracker.pressedCount(action)
                        if (count == 0) pressedCounts.remove(action) else pressedCounts[action] = count
                        change.consume()
                      }
                    }
                  }
                }
              }
            }
          } finally {
            touchTracker.clear()
            pressedCounts.clear()
          }
        },
    )
  }
}

@Composable
private fun KeyboardCharacterRow(
  definitions: List<JamoKeyDefinition>,
  isShifted: Boolean,
  guide: KeyboardGuideState,
  guidePulse: Float,
  pressedCounts: Map<KeyboardAction, Int>,
  targetRegistry: KeyboardTouchTargetRegistry,
  options: PracticeKeyboardOptions,
  onActivate: (KeyboardAction) -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(5.dp),
  ) {
    definitions.forEach { definition ->
      val action = JamoKey(definition.base)
      KeyboardKeycap(
        title = definition.output(isShifted).toString(),
        romanHint = definition.roman.takeIf { options.showsRomanHints },
        accessibilityLabel = keyboardKeyDescription(definition, isShifted, options),
        action = action,
        highlighted = guide.highlightedAction == action,
        guidePulse = guidePulse,
        pressed = (pressedCounts[action] ?: 0) > 0,
        targetRegistry = targetRegistry,
        onActivate = onActivate,
        modifier = Modifier.weight(1f),
      )
    }
  }
}

@Composable
private fun keyboardKeyDescription(
  definition: JamoKeyDefinition,
  isShifted: Boolean,
  options: PracticeKeyboardOptions,
): String {
  val output = definition.output(isShifted).toString()
  return if (options.showsRomanHints) {
    stringResource(R.string.keyboard_key_with_roman, output, definition.roman)
  } else {
    output
  }
}

@Composable
private fun KeyboardKeycap(
  title: String,
  accessibilityLabel: String,
  action: KeyboardAction,
  highlighted: Boolean,
  guidePulse: Float,
  pressed: Boolean,
  targetRegistry: KeyboardTouchTargetRegistry,
  onActivate: (KeyboardAction) -> Unit,
  modifier: Modifier = Modifier,
  selected: Boolean = false,
  romanHint: String? = null,
  spaceBar: Boolean = false,
) {
  val keycapHeight = KeyboardKeycapMetrics.heightDp(LocalDensity.current.fontScale).dp
  val scale by animateFloatAsState(
    targetValue = if (pressed) 0.95f else 1f,
    animationSpec = tween(durationMillis = 60),
    label = "key-press",
  )
  val elevation by animateDpAsState(
    targetValue = if (highlighted) (5 + guidePulse * 7).dp else 2.dp,
    animationSpec = tween(durationMillis = 120),
    label = "key-guide-elevation",
  )
  val shape = RoundedCornerShape(11.dp)
  val color = when {
    highlighted -> KeyboardColors.GuideSoft
    selected -> KeyboardColors.SelectedKey
    else -> KeyboardColors.Key
  }

  Surface(
    modifier = modifier
      .height(keycapHeight)
      .scale(scale)
      .shadow(
        elevation = elevation,
        shape = shape,
        ambientColor = if (highlighted) KeyboardColors.Guide.copy(alpha = guidePulse) else Color.Black,
        spotColor = if (highlighted) KeyboardColors.Guide.copy(alpha = guidePulse) else Color.Black,
      )
      .onGloballyPositioned { targetRegistry.register(action, it.boundsInRoot()) }
      .semantics {
        contentDescription = accessibilityLabel
        role = Role.Button
        onClick {
          onActivate(action)
          true
        }
      },
    shape = shape,
    color = color,
  ) {
    Box(
      modifier = Modifier.fillMaxSize(),
      contentAlignment = Alignment.Center,
    ) {
      if (spaceBar) {
        Box(
          Modifier
            .fillMaxWidth(0.34f)
            .height(4.dp)
            .background(KeyboardColors.MutedInk.copy(alpha = 0.28f), RoundedCornerShape(99.dp)),
        )
      } else {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text(
            text = title,
            color = KeyboardColors.Ink,
            fontSize = 21.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = KeyboardKeycapMetrics.MainLineHeightSp.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
          )
          if (romanHint != null) {
            Text(
              text = romanHint,
              color = KeyboardColors.MutedInk,
              fontSize = 9.sp,
              fontWeight = FontWeight.Medium,
              lineHeight = KeyboardKeycapMetrics.RomanLineHeightSp.sp,
              maxLines = 1,
              softWrap = false,
              overflow = TextOverflow.Clip,
            )
          }
        }
      }
    }
  }
}

private class KeyboardTouchTargetRegistry {
  private val boundsByAction = linkedMapOf<KeyboardAction, Rect>()

  fun register(action: KeyboardAction, bounds: Rect) {
    boundsByAction[action] = bounds
  }

  fun resolve(point: Offset, touchOutset: Float): KeyboardAction? {
    var bestAction: KeyboardAction? = null
    var bestEdgeDistance = Float.POSITIVE_INFINITY
    var bestCenterDistance = Float.POSITIVE_INFINITY

    boundsByAction.forEach { (action, bounds) ->
      val expanded = Rect(
        left = bounds.left - touchOutset,
        top = bounds.top - touchOutset,
        right = bounds.right + touchOutset,
        bottom = bounds.bottom + touchOutset,
      )
      if (!expanded.contains(point)) return@forEach

      val edgeDistance = squaredDistanceToRect(point, bounds)
      val centerDistance = (point - bounds.center).getDistanceSquared()
      if (
        edgeDistance < bestEdgeDistance ||
        (edgeDistance == bestEdgeDistance && centerDistance < bestCenterDistance)
      ) {
        bestAction = action
        bestEdgeDistance = edgeDistance
        bestCenterDistance = centerDistance
      }
    }
    return bestAction
  }

  private fun squaredDistanceToRect(point: Offset, rect: Rect): Float {
    val dx = maxOf(rect.left - point.x, 0f, point.x - rect.right)
    val dy = maxOf(rect.top - point.y, 0f, point.y - rect.bottom)
    return dx * dx + dy * dy
  }
}

internal object KeyboardColors {
  val KeyboardBackground = Color(0xFFF8F4FF)
  val Key = Color(0xFFFFFFFF)
  val SelectedKey = Color(0xFFE9E1FF)
  val Guide = Color(0xFF7557FF)
  val GuideSoft = Color(0xFFECE7FF)
  val Ink = Color(0xFF242034)
  val MutedInk = Color(0xFF716B7C)
}
