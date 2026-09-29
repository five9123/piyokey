package app.piyokey.android.ui.mascot

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.piyokey.android.R
import app.piyokey.android.data.mascot.MascotCompanionStore
import app.piyokey.android.data.mascot.MascotMood
import app.piyokey.android.data.mascot.MascotReaction
import app.piyokey.android.data.mascot.MascotStage
import app.piyokey.android.data.mascot.MascotStore
import app.piyokey.android.data.mascot.celebrationTitleKey
import app.piyokey.android.ui.nav.Route
import app.piyokey.android.ui.theme.Piyo
import app.piyokey.android.ui.theme.PiyoType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Full-screen growth celebration (iOS `GrowthCelebrationView`); present on the app navigator. */
class GrowthCelebrationRoute(private val newStage: MascotStage, private val onDone: () -> Unit) : Route {
  @Composable
  override fun Content() = GrowthCelebration(newStage, onDone)
}

@Composable
fun GrowthCelebration(
  newStage: MascotStage,
  onDone: () -> Unit,
  modifier: Modifier = Modifier,
  store: MascotCompanionStore = MascotStore,
) {
  val colors = Piyo.colors
  val reduceMotion = rememberMascotReduceMotion() || !LocalMascotAnimationsEnabled.current
  val isHatch = newStage == MascotStage.HATCHING
  var phase by remember { mutableIntStateOf(0) }
  val shake = remember { Animatable(0f) }
  var draftName by remember { mutableStateOf("") }

  // `.interactiveDismissDisabled()`
  BackHandler(enabled = true) {}

  LaunchedEffect(Unit) {
    if (reduceMotion) {
      phase = 2
      return@LaunchedEffect
    }
    if (isHatch) {
      for (index in 0 until 8) {
        launch { shake.animateTo(if (index % 2 == 0) 9f else -9f, tween(90, easing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f))) }
        delay(95)
      }
      shake.snapTo(0f)
    }
    MascotFeedbackHooks.chirp?.invoke(true)
    phase = 1
    delay(900)
    phase = 2
  }

  Box(
    modifier
      .fillMaxSize()
      .background(Brush.linearGradient(listOf(colors.backgroundTop, colors.backgroundBottom))),
    contentAlignment = Alignment.Center,
  ) {
    val hatchedLabel = stringResource(R.string.growth_hatched)
    Box(Modifier.size(1.dp).testTag("mascot.growth.celebration").clearAndSetSemantics { contentDescription = hatchedLabel })

    GrowthConfettiBurst(isActive = phase >= 1)

    Column(
      Modifier.fillMaxSize().padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
      Spacer(Modifier.weight(1f))
      Box(Modifier.height(230.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
        if (phase == 0) {
          ChickMascot(stage = MascotStage.CRACKING, size = 132.dp, modifier = Modifier.graphicsLayer { rotationZ = shake.value })
        }
        Appear(
          visible = phase >= 1,
          enter = scaleIn(swiftSpring(0.4f, 0.55f), initialScale = 0.4f) + fadeIn(),
        ) {
          ChickMascot(
            mood = MascotMood.CHEER,
            stage = newStage,
            reaction = MascotReaction.GrowthTransition,
            reactionRevision = phase,
            size = 132.dp,
          )
        }
      }

      AnimatedVisibility(visible = phase >= 1, enter = scaleIn() + fadeIn()) {
        Text(
          stringResource(
            when (newStage.celebrationTitleKey()) {
              "growth.hatched" -> R.string.growth_hatched
              "growth.master" -> R.string.growth_master
              else -> R.string.growth_advanced
            },
          ),
          style = PiyoType.largeTitle().copy(fontWeight = FontWeight.Black, color = colors.accent),
          textAlign = TextAlign.Center,
        )
      }

      AnimatedVisibility(visible = phase >= 2, enter = fadeIn(tween(300))) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(22.dp)) {
          if (isHatch) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
              Text(stringResource(R.string.growth_name_prompt), style = PiyoType.headline().copy(fontWeight = FontWeight.Bold))
              OutlinedTextField(
                value = draftName,
                onValueChange = { draftName = it },
                placeholder = {
                  Text(stringResource(R.string.mascot_default_name), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                },
                singleLine = true,
                textStyle = PiyoType.title3().copy(textAlign = TextAlign.Center),
                colors = OutlinedTextFieldDefaults.colors(
                  focusedBorderColor = colors.accent,
                  unfocusedBorderColor = colors.mutedInk.copy(alpha = 0.35f),
                  cursorColor = colors.accent,
                  focusedContainerColor = colors.card,
                  unfocusedContainerColor = colors.card,
                ),
                modifier = Modifier.widthIn(max = 240.dp).testTag("mascot.growth.name"),
              )
            }
          }
          Box(
            Modifier
              .clip(CircleShape)
              .background(colors.accent)
              .clickable(role = Role.Button) {
                val trimmed = draftName.trim()
                if (isHatch && trimmed.isNotEmpty()) store.setName(trimmed)
                onDone()
              }
              .testTag("mascot.growth.confirm")
              .padding(horizontal = 42.dp, vertical = 14.dp),
          ) {
            Text(
              stringResource(R.string.growth_confirm),
              style = PiyoType.headline().copy(fontWeight = FontWeight.Bold, color = Color.White),
            )
          }
        }
      }

      Spacer(Modifier.weight(2f))
    }
  }
}

/** Scope-free [AnimatedVisibility] (avoids the Column/Row scoped overloads). */
@Composable
private fun Appear(visible: Boolean, enter: EnterTransition, content: @Composable () -> Unit) {
  AnimatedVisibility(visible = visible, enter = enter) { content() }
}

/** 18 stars/dots flying outward and fading (iOS `GrowthConfettiBurst`). */
@Composable
private fun GrowthConfettiBurst(isActive: Boolean) {
  val colors = Piyo.colors
  val palette = listOf(colors.accent, colors.secondary, colors.success, MascotColors.systemOrange, MascotColors.systemYellow)
  val spread = remember { Animatable(0f) }
  LaunchedEffect(isActive) {
    if (!isActive) {
      spread.snapTo(0f)
      return@LaunchedEffect
    }
    spread.animateTo(1f, tween(1150, easing = CubicBezierEasing(0f, 0f, 0.58f, 1f)))
  }
  val star = rememberVectorPainter(Icons.Filled.Star)
  Canvas(Modifier.fillMaxSize().clearAndSetSemantics {}) {
    val p = spread.value
    for (index in 0 until 18) {
      val angle = index / 18.0 * PI * 2
      val distance = (92 + (index % 4) * 22).dp.toPx()
      val glyph = ((8 + index % 4) * 1.2f).dp.toPx()
      val cx = center.x + (cos(angle) * distance * p).toFloat()
      val cy = center.y + (sin(angle) * distance * p).toFloat()
      val color = palette[index % palette.size]
      val alpha = 1f - p
      rotate(index * 47f * p, Offset(cx, cy)) {
        if (index % 3 == 0) {
          val box = glyph * 1.2f
          translate(cx - box / 2, cy - box / 2) {
            with(star) { draw(Size(box, box), alpha = alpha, colorFilter = ColorFilter.tint(color)) }
          }
        } else {
          drawCircle(color, radius = glyph * 0.45f, center = Offset(cx, cy), alpha = alpha)
        }
      }
    }
  }
}
