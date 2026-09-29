package app.piyokey.android.ui.mascot

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.piyokey.android.R
import app.piyokey.android.Services
import app.piyokey.android.data.mascot.MascotEggPattern
import app.piyokey.android.data.mascot.MascotFanColor
import app.piyokey.android.data.mascot.MascotGrowthAppearance
import app.piyokey.android.data.mascot.MascotMood
import app.piyokey.android.data.mascot.MascotMotionPolicy
import app.piyokey.android.data.mascot.MascotPettingPolicy
import app.piyokey.android.data.mascot.MascotPose
import app.piyokey.android.data.mascot.MascotProp
import app.piyokey.android.data.mascot.MascotReaction
import app.piyokey.android.data.mascot.MascotStage
import app.piyokey.android.data.settings.Prefs
import app.piyokey.android.ui.theme.Piyo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.random.Random

/**
 * Global switch for looping mascot motion (idle variations, breathing, blinking). UI tests set it
 * to `false` so the composition can become idle. Reduce motion is handled separately.
 */
val LocalMascotAnimationsEnabled = staticCompositionLocalOf { true }

/**
 * Sound hooks the platform layer installs (iOS `HancoSoundEngine.shared.play(.completion(combo:))`).
 * `chirp(false)` = combo 2 (happy pet), `chirp(true)` = combo 10 (excited pet / growth).
 */
object MascotFeedbackHooks {
  @Volatile var chirp: ((excited: Boolean) -> Unit)? = null
}

/** System "Remove animations" (animator duration scale 0) — the Android twin of Reduce Motion. */
@Composable
fun rememberMascotReduceMotion(): Boolean {
  val context = LocalContext.current
  return remember(context) {
    runCatching {
      Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }.getOrDefault(false)
  }
}

// MARK: - Animation specs mirroring SwiftUI

private val EaseIn = CubicBezierEasing(0.42f, 0f, 1f, 1f)
private val EaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)
private val EaseInOut = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

/** SwiftUI `.spring(response:dampingFraction:)` → Compose spring (mass 1). */
internal fun swiftSpring(response: Float, damping: Float): SpringSpec<Float> {
  val omega = 2 * PI.toFloat() / response
  return spring(dampingRatio = damping, stiffness = omega * omega)
}

private fun easeIn(seconds: Float) = tween<Float>((seconds * 1000).toInt(), easing = EaseIn)
private fun easeOut(seconds: Float) = tween<Float>((seconds * 1000).toInt(), easing = EaseOut)
private fun easeInOut(seconds: Float) = tween<Float>((seconds * 1000).toInt(), easing = EaseInOut)
private fun linear(seconds: Float) = tween<Float>((seconds * 1000).toInt(), easing = LinearEasing)

/** `size * 0.10` / `size * 0.07` expressed in design units (u = size / 200 * 1.7). */
private const val BOUNCE_U = -0.10f * 200f / 1.7f
private const val HOP_U = -0.07f * 200f / 1.7f

private typealias Anim = Animatable<Float, AnimationVector1D>

@Stable
private class MascotMotion {
  val bounce = Animatable(0f)
  val squash = Animatable(0f)
  val blink = Animatable(0f)
  val waddle = Animatable(0f)
  val shiver = Animatable(0f)
  val stomp = Animatable(0f)
  val idleFlap = Animatable(0f)
  val idleSpin = Animatable(0f)
  val reactionY = Animatable(0f)
  val reactionScale = Animatable(1f)
  val reactionTilt = Animatable(0f)
  val reactionFlap = Animatable(0f)
  val glow = Animatable(0f)
  val propRotation = Animatable(0f)
  val capTossOffset = Animatable(0f)
  val capTossRotation = Animatable(0f)
  val headphoneBeat = Animatable(1f)
  val feather = Animatable(1f)

  var idlePeck by mutableStateOf(false)
  var idleTilt by mutableFloatStateOf(0f)
  var idleHop by mutableStateOf(false)
  var idleGaze by mutableFloatStateOf(0f)
  var idleYawn by mutableStateOf(false)
  var idleOneLeg by mutableStateOf(false)
  var idleBackTurn by mutableStateOf(false)
  var idleExtraCrack by mutableStateOf(false)
  var showFeathers by mutableStateOf(false)
  var featherSeq by mutableIntStateOf(0)
  var displayedReaction by mutableStateOf<MascotReaction>(MascotReaction.None)
  var eventMood by mutableStateOf<MascotMood?>(null)
  var petMood by mutableStateOf<MascotMood?>(null)
  var shyPetCount = 0
  var tapJob: Job? = null
  var tapSeriesJob: Job? = null
  var petResetJob: Job? = null

  fun showReactionFeathers() {
    featherSeq += 1
    showFeathers = true
  }

  suspend fun resetReactionPose() {
    reactionY.snapTo(0f)
    reactionScale.snapTo(1f)
    reactionTilt.snapTo(0f)
    reactionFlap.snapTo(0f)
    glow.snapTo(0f)
    propRotation.snapTo(0f)
    capTossOffset.snapTo(0f)
    capTossRotation.snapTo(0f)
    headphoneBeat.snapTo(1f)
    displayedReaction = MascotReaction.None
    eventMood = null
    showFeathers = false
  }
}

private fun CoroutineScope.go(anim: Anim, target: Float, spec: AnimationSpec<Float>) {
  launch { anim.animateTo(target, spec) }
}

/**
 * The code-drawn PIYOKEY chick (iOS `MascotView`). Geometry is authored in a 200-unit design
 * space scaled by [size]; the layout box is `size*1.25 × size*1.2` and drawing overflows into a
 * `size*1.9 × size*1.8` canvas exactly like iOS.
 */
@Composable
fun ChickMascot(
  modifier: Modifier = Modifier,
  mood: MascotMood = MascotMood.IDLE,
  stage: MascotStage = MascotStage.CHICK,
  prop: MascotProp = MascotProp.NONE,
  eggPattern: MascotEggPattern = MascotEggPattern.PLAIN,
  gazeX: Float = 0f,
  gazeY: Float = 0f,
  reaction: MascotReaction = MascotReaction.None,
  reactionRevision: Int = 0,
  pose: MascotPose = MascotPose.FRONT,
  intensity: Float = 0f,
  growthAppearance: MascotGrowthAppearance = MascotGrowthAppearance.STANDARD,
  fanColor: MascotFanColor = MascotFanColor.PINK,
  nameTag: String? = null,
  speech: String? = null,
  showsFriend: Boolean = false,
  interactive: Boolean = false,
  onOpenCloset: (() -> Unit)? = null,
  size: Dp = 96.dp,
  calm: Boolean = false,
  animated: Boolean = true,
) {
  val clampedIntensity = intensity.coerceIn(0f, 1f)
  val animationsEnabled = animated && LocalMascotAnimationsEnabled.current
  val reduceMotion = rememberMascotReduceMotion()
  val motionAllowed = animationsEnabled && !reduceMotion
  val motion = remember { MascotMotion() }
  val scope = rememberCoroutineScope()
  val haptics = LocalHapticFeedback.current
  val density = LocalDensity.current
  val sizePx = with(density) { size.toPx() }
  val u = sizePx / 200f * 1.7f

  /** Displayed mood: pet and event reactions briefly override the external mood. */
  val m = motion.eventMood ?: motion.petMood ?: mood
  val currentMood by rememberUpdatedState(m)
  val currentStage by rememberUpdatedState(stage)
  val currentReaction by rememberUpdatedState(reaction)

  // MARK: continuous motion

  val breathe: State<Float> = if (motionAllowed && !calm) {
    rememberInfiniteTransition(label = "breathe").animateFloat(
      initialValue = 0.99f,
      targetValue = 1.03f,
      animationSpec = infiniteRepeatable(tween(1600, easing = EaseInOut), RepeatMode.Reverse),
      label = "breathe",
    )
  } else {
    remember { mutableFloatStateOf(0.99f) }
  }
  val pulseA: State<Float>
  val pulseB: State<Float>
  if (motionAllowed && m == MascotMood.LOVE) {
    val pulse = rememberInfiniteTransition(label = "love")
    pulseA = pulse.animateFloat(0.9f, 1.15f, infiniteRepeatable(tween(900, easing = EaseInOut), RepeatMode.Reverse), label = "a")
    pulseB = pulse.animateFloat(1.15f, 0.9f, infiniteRepeatable(tween(1100, easing = EaseInOut), RepeatMode.Reverse), label = "b")
  } else {
    pulseA = remember { mutableFloatStateOf(0.9f) }
    pulseB = remember { mutableFloatStateOf(1.15f) }
  }

  // MARK: mood-driven values (`.animation(.spring(response: 0.3, dampingFraction: 0.55), value: m)`)

  val moodSpring = remember { swiftSpring(0.3f, 0.55f) }
  val leftWing by animateFloatAsState(leftWingAngle(m, clampedIntensity), moodSpring, label = "wingL")
  val rightWing by animateFloatAsState(
    if (m == MascotMood.OOPS) 34f else leftWingAngle(m, clampedIntensity), moodSpring, label = "wingR",
  )
  val tuftAngle by animateFloatAsState(tuftAngle(m), moodSpring, label = "tuft")
  val proudScale by animateFloatAsState(if (m == MascotMood.PROUD) 1.06f else 1f, moodSpring, label = "proud")
  val bigBeak = m == MascotMood.CHEER || m == MascotMood.SURPRISE || motion.idleYawn
  val beakW by animateFloatAsState(if (m == MascotMood.GRIT) 20f else if (bigBeak) 19f else 15f, moodSpring, label = "beakW")
  val beakH by animateFloatAsState(if (m == MascotMood.GRIT) 8f else if (bigBeak) 15f else 12f, moodSpring, label = "beakH")
  val baseTilt by animateFloatAsState(
    when {
      m == MascotMood.OOPS -> 10f
      motion.idlePeck -> 9f
      else -> motion.idleTilt
    },
    if (motion.idlePeck) swiftSpring(0.16f, 0.5f) else swiftSpring(0.32f, 0.6f),
    label = "tilt",
  )
  val hop by animateFloatAsState(if (motion.idleHop) HOP_U else 0f, swiftSpring(0.24f, 0.48f), label = "hop")
  val gazeAnimX by animateFloatAsState(gazeX, easeOut(0.15f), label = "gazeX")
  val gazeAnimY by animateFloatAsState(gazeY, easeOut(0.15f), label = "gazeY")
  val idleGaze by animateFloatAsState(motion.idleGaze, easeInOut(0.22f), label = "idleGaze")
  val yaw by animateFloatAsState(
    MascotMotionPolicy.yawDegrees(pose, motion.idleBackTurn), easeInOut(0.3f), label = "yaw",
  )
  val footMask by animateFloatAsState(
    if (motion.idleOneLeg) 20f else 40f,
    if (motion.idleOneLeg) swiftSpring(0.3f, 0.7f) else swiftSpring(0.28f, 0.55f),
    label = "foot",
  )

  // MARK: effects

  // Cheer: feathers + jump + landing squash (iOS `onChange(of: m)`).
  val previousMood = remember { mutableStateOf<MascotMood?>(null) }
  LaunchedEffect(m) {
    val previous = previousMood.value
    previousMood.value = m
    if (previous == null || m != MascotMood.CHEER) return@LaunchedEffect
    motion.showReactionFeathers()
    launch {
      delay(900)
      motion.showFeathers = false
    }
    if (reduceMotion) return@LaunchedEffect
    go(motion.bounce, BOUNCE_U, swiftSpring(0.25f, 0.45f))
    delay(350)
    go(motion.bounce, 0f, swiftSpring(0.22f, 0.5f))
    delay(180)
    go(motion.squash, 1f, easeOut(0.08f))
    delay(100)
    go(motion.squash, 0f, swiftSpring(0.22f, 0.5f))
  }

  LaunchedEffect(motion.featherSeq) {
    if (motion.featherSeq == 0) return@LaunchedEffect
    motion.feather.snapTo(0f)
    motion.feather.animateTo(1f, easeIn(0.8f))
  }

  // Reactions (iOS `playReaction`): onAppear when revision > 0, then on every revision change.
  val firstReaction = remember { mutableStateOf(true) }
  LaunchedEffect(reactionRevision) {
    val isFirst = firstReaction.value
    firstReaction.value = false
    if (isFirst && reactionRevision <= 0) return@LaunchedEffect
    playReaction(motion, currentReaction, reduceMotion)
  }

  // Blink: random 2.2–4.2 s, sometimes twice.
  LaunchedEffect(animationsEnabled) {
    if (!animationsEnabled) return@LaunchedEffect
    while (true) {
      delay(Random.nextLong(2_200, 4_201))
      val times = if (Random.nextInt(5) == 0) 2 else 1
      repeat(times) {
        go(motion.blink, 1f, easeIn(0.07f))
        delay(130)
        go(motion.blink, 0f, easeOut(0.10f))
        delay(180)
      }
    }
  }

  // Idle variations: 14 for the chick, 4 for eggs.
  LaunchedEffect(motionAllowed) {
    if (!motionAllowed) return@LaunchedEffect
    var lastPick = -1
    delay(1_500)
    while (true) {
      val mood0 = currentMood
      if (mood0 != MascotMood.IDLE && mood0 != MascotMood.SLEEPY) {
        delay(1_200)
        continue
      }
      val isEgg = currentStage == MascotStage.EGG || currentStage == MascotStage.CRACKING
      val count = if (isEgg) 4 else 14
      var pick = if (mood0 == MascotMood.SLEEPY && !isEgg) 7 else Random.nextInt(count)
      if (pick == lastPick) pick = (pick + 1 + Random.nextInt(count - 1)) % count
      lastPick = pick
      if (isEgg) runEggVariation(motion, pick) else runIdleVariation(motion, pick)
      delay(Random.nextLong(2_200, 4_501))
    }
  }

  // MARK: petting

  fun pet(newMood: MascotMood, excited: Boolean, revertMs: Long) {
    mascotHaptic(haptics)
    MascotFeedbackHooks.chirp?.invoke(excited)
    motion.petMood = newMood
    motion.petResetJob?.cancel()
    motion.petResetJob = scope.launch {
      delay(revertMs)
      if (motion.petMood == newMood) motion.petMood = null
    }
  }

  fun handleSinglePet() {
    motion.shyPetCount += 1
    motion.tapSeriesJob?.cancel()
    motion.tapSeriesJob = scope.launch {
      delay(MascotPettingPolicy.TAP_SERIES_RESET_MS)
      motion.shyPetCount = 0
    }
    pet(MascotPettingPolicy.singleTapMood(motion.shyPetCount), excited = false, MascotPettingPolicy.HAPPY_REVERT_MS)
  }

  fun handlePetTap() {
    val pending = motion.tapJob
    if (pending != null) {
      pending.cancel()
      motion.tapJob = null
      pet(MascotMood.CHEER, excited = true, MascotPettingPolicy.CHEER_REVERT_MS)
      return
    }
    motion.tapJob = scope.launch {
      delay(MascotPettingPolicy.DOUBLE_TAP_WINDOW_MS)
      motion.tapJob = null
      handleSinglePet()
    }
  }

  // MARK: accessibility

  val label = stringResource(R.string.mascot_accessibility_label)
  val stageValue = stringResource(stageStringRes(stage))
  val petValue = when (motion.petMood) {
    MascotMood.HAPPY -> stringResource(R.string.mascot_accessibility_pet_happy)
    MascotMood.CHEER -> stringResource(R.string.mascot_accessibility_pet_cheer)
    MascotMood.SHY -> stringResource(R.string.mascot_accessibility_pet_shy)
    else -> null
  }
  val a11yValue = if (petValue == null) stageValue else "$stageValue, $petValue"
  val hint = stringResource(R.string.mascot_interaction_hint)

  // MARK: drawing inputs

  val colors = Piyo.colors
  val theme = MascotThemeColors(colors.accent, colors.secondary, colors.success, colors.error, colors.mutedInk)
  val textMeasurer = rememberTextMeasurer()
  val grown = stage == MascotStage.CHICK || stage == MascotStage.ROOSTER
  val neededIcons = (MascotIcons.forMood(m) + (if (grown) MascotIcons.forProp(prop) else emptyList()) +
    MascotIcons.forReaction(motion.displayedReaction)).distinct()
  val painters = HashMap<ImageVector, VectorPainter>()
  for (vector in neededIcons) {
    key(vector) { painters[vector] = rememberVectorPainter(vector) }
  }

  var outer = modifier
    .size(size * 1.25f, size * 1.2f)
    .testTag(if (interactive) "mascot.interactive" else "mascot.current")
    .clearAndSetSemantics {
      contentDescription = label
      stateDescription = a11yValue
      if (interactive) {
        role = Role.Button
        onClick(label = hint) {
          handleSinglePet()
          true
        }
        if (onOpenCloset != null) {
          onLongClick(label = hint) {
            mascotHaptic(haptics)
            onOpenCloset()
            true
          }
        }
      }
    }
  if (interactive) {
    outer = outer.pointerInput(onOpenCloset) {
      detectTapGestures(
        onTap = { handlePetTap() },
        onLongPress = {
          onOpenCloset?.let { open ->
            mascotHaptic(haptics)
            open()
          }
        },
      )
    }
  }

  Box(outer, contentAlignment = Alignment.Center) {
    Canvas(
      Modifier
        .requiredSize(size * 1.9f, size * 1.8f)
        .graphicsLayer {
          // `.scaleEffect(y: squash ? 0.90 : (breathe ? 1.03 : 0.99), anchor: .bottom)`
          transformOrigin = TransformOrigin(0.5f, 1f)
          scaleY = lerpF(breathe.value, 0.90f, motion.squash.value)
        }
        .graphicsLayer {
          val s = proudScale * growthAppearance.bodyScale * (1 + clampedIntensity * 0.025f) *
            MascotMotionPolicy.reactionScale(motion.reactionScale.value)
          scaleX = s
          scaleY = s
        }
        .graphicsLayer {
          rotationZ = baseTilt + motion.reactionTilt.value + motion.idleSpin.value
          rotationY = yaw
          cameraDistance = 12f * density.density
          translationX = MascotMotionPolicy.horizontalOffset((motion.waddle.value + motion.shiver.value) * u, sizePx)
          translationY = MascotMotionPolicy.verticalOffset(
            (motion.bounce.value + hop + motion.reactionY.value + motion.stomp.value) * u, sizePx,
          )
        }
        .clipToBounds(),
    ) {
      val feather = motion.feather.value
      drawMascot(
        MascotDrawInput(
          u = u,
          sizePx = sizePx,
          mood = m,
          stage = stage,
          prop = prop,
          eggPattern = eggPattern,
          pose = pose,
          intensity = clampedIntensity,
          growth = growthAppearance,
          fanColor = fanColor,
          nameTag = nameTag,
          speech = speech,
          showsFriend = showsFriend,
          gazeX = gazeAnimX + idleGaze,
          gazeY = gazeAnimY,
          blink = motion.blink.value,
          lovePulseA = pulseA.value,
          lovePulseB = pulseB.value,
          leftWingAngle = leftWing,
          rightWingAngle = rightWing,
          flap = motion.idleFlap.value + motion.reactionFlap.value,
          tuftAngle = tuftAngle,
          beakWidth = beakW,
          beakHeight = beakH,
          beakIsGrit = m == MascotMood.GRIT,
          footMaskWidth = footMask,
          extraCrack = motion.idleExtraCrack,
          glow = motion.glow.value,
          propRotation = motion.propRotation.value,
          capTossOffsetU = motion.capTossOffset.value,
          capTossRotation = motion.capTossRotation.value,
          headphoneBeat = motion.headphoneBeat.value,
          reactionTilt = motion.reactionTilt.value,
          displayedReaction = motion.displayedReaction,
          featherProgress = if (motion.showFeathers && feather < 1f) feather else null,
          theme = theme,
          painters = painters,
          textMeasurer = textMeasurer,
        ),
      )
    }
  }
}

private fun lerpF(a: Float, b: Float, t: Float) = a + (b - a) * t

internal fun stageStringRes(stage: MascotStage): Int = when (stage) {
  MascotStage.EGG -> R.string.a11y_stage_egg
  MascotStage.CRACKING -> R.string.a11y_stage_cracking
  MascotStage.HATCHING -> R.string.a11y_stage_hatching
  MascotStage.CHICK -> R.string.a11y_stage_chick
  MascotStage.ROOSTER -> R.string.a11y_stage_rooster
}

/** iOS `MascotFeedback.haptic()`: soft impact unless `keyboard.haptics_enabled` is off. */
private fun mascotHaptic(haptics: HapticFeedback) {
  val enabled = !Services.isInstalled || Prefs.shared.getBoolean("keyboard.haptics_enabled", true)
  if (enabled) haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
}

private fun leftWingAngle(m: MascotMood, intensity: Float): Float {
  val base = when (m) {
    MascotMood.IDLE, MascotMood.SLEEPY -> 18f
    MascotMood.HAPPY, MascotMood.LOVE -> 42f
    MascotMood.CHEER -> 135f
    MascotMood.OOPS -> 10f
    MascotMood.PROUD -> 60f
    MascotMood.FOCUS -> 26f
    MascotMood.SULK -> 8f
    MascotMood.SURPRISE -> 64f
    MascotMood.GRIT -> 32f
    MascotMood.DIZZY -> 50f
    MascotMood.WINK, MascotMood.SHY -> 38f
    MascotMood.EUREKA -> 88f
    MascotMood.SATISFIED -> 48f
  }
  return base + intensity * (if (m == MascotMood.CHEER) 18f else 10f)
}

private fun tuftAngle(m: MascotMood): Float = when (m) {
  MascotMood.OOPS -> 24f
  MascotMood.SLEEPY -> 14f
  MascotMood.SURPRISE, MascotMood.EUREKA -> -12f
  MascotMood.SULK -> 20f
  else -> 0f
}

// MARK: - Egg variations (3 + extra crack)

private suspend fun CoroutineScope.runEggVariation(motion: MascotMotion, pick: Int) {
  when (pick) {
    0 -> for (deg in floatArrayOf(-5f, 5f, -3f, 0f)) {
      motion.idleTilt = deg
      delay(170)
    }
    1 -> {
      for (i in 0 until 5) {
        go(motion.shiver, if (i % 2 == 0) 2f else -2f, linear(0.05f))
        delay(52)
      }
      go(motion.shiver, 0f, easeOut(0.09f))
    }
    2 -> repeat(2) {
      motion.idleHop = true
      delay(170)
      motion.idleHop = false
      delay(200)
    }
    else -> {
      motion.idleExtraCrack = true
      go(motion.glow, 1f, easeOut(0.16f))
      go(motion.reactionScale, 1.035f, easeOut(0.16f))
      delay(260)
      motion.idleExtraCrack = false
      go(motion.glow, 0f, swiftSpring(0.22f, 0.65f))
      go(motion.reactionScale, 1f, swiftSpring(0.22f, 0.65f))
    }
  }
}

// MARK: - Idle variations (14)

private suspend fun CoroutineScope.runIdleVariation(motion: MascotMotion, pick: Int) {
  when (pick) {
    0 -> repeat(if (Random.nextBoolean()) 2 else 1) {
      motion.idlePeck = true
      delay(200)
      motion.idlePeck = false
      delay(160)
    }
    1 -> for (dx in floatArrayOf(4f, -4f, 0f)) {
      go(motion.waddle, dx, easeInOut(0.14f))
      delay(150)
    }
    2 -> {
      motion.idleHop = true
      delay(240)
      motion.idleHop = false
      delay(160)
      go(motion.squash, 1f, easeOut(0.07f))
      delay(90)
      go(motion.squash, 0f, swiftSpring(0.2f, 0.5f))
    }
    3 -> {
      motion.idleTilt = if (Random.nextBoolean()) -8f else 8f
      delay(620)
      motion.idleTilt = 0f
    }
    4 -> {
      val first = if (Random.nextBoolean()) -1f else 1f
      motion.idleGaze = first
      delay(420)
      motion.idleGaze = -first
      delay(420)
      motion.idleGaze = 0f
    }
    5 -> repeat(2) {
      go(motion.idleFlap, 46f, swiftSpring(0.14f, 0.5f))
      delay(140)
      go(motion.idleFlap, 0f, swiftSpring(0.18f, 0.55f))
      delay(160)
    }
    6 -> {
      for (i in 0 until 5) {
        go(motion.shiver, if (i % 2 == 0) 2.2f else -2.2f, linear(0.05f))
        delay(52)
      }
      go(motion.shiver, 0f, easeOut(0.09f))
    }
    7 -> {
      motion.idleYawn = true
      go(motion.reactionScale, 1.035f, easeOut(0.18f))
      delay(520)
      motion.idleYawn = false
      go(motion.reactionScale, 1f, easeInOut(0.18f))
    }
    8 -> {
      motion.idleOneLeg = true
      delay(1_350)
      motion.idleOneLeg = false
      motion.idleTilt = if (Random.nextBoolean()) -5f else 5f
      delay(220)
      motion.idleTilt = 0f
    }
    9 -> {
      motion.idleBackTurn = true
      delay(560)
      motion.idleBackTurn = false
    }
    10 -> {
      motion.idleTilt = -24f
      go(motion.idleFlap, 28f, easeInOut(0.22f))
      delay(620)
      motion.idleTilt = 0f
      go(motion.idleFlap, 0f, swiftSpring(0.28f, 0.62f))
    }
    11 -> {
      go(motion.idleSpin, motion.idleSpin.targetValue + 360f, easeInOut(0.72f))
      delay(760)
    }
    12 -> repeat(2) {
      go(motion.stomp, 4f, easeOut(0.08f))
      delay(90)
      go(motion.stomp, 0f, swiftSpring(0.15f, 0.5f))
      delay(130)
    }
    else -> {
      go(motion.waddle, 7f, easeInOut(0.22f))
      delay(320)
      go(motion.waddle, 0f, swiftSpring(0.24f, 0.62f))
    }
  }
}

// MARK: - Reactions

/** Offsets are in design units, so the `* u` factors of iOS disappear here. */
private suspend fun CoroutineScope.playReaction(motion: MascotMotion, event: MascotReaction, reduceMotion: Boolean) {
  motion.resetReactionPose()
  if (event == MascotReaction.None) return
  motion.displayedReaction = event

  if (reduceMotion) {
    if (event != MascotReaction.Mistake && event != MascotReaction.CorrectJamo) go(motion.glow, 1f, easeOut(0.14f))
    delay(420)
    motion.resetReactionPose()
    return
  }

  with(motion) {
    when (event) {
      MascotReaction.None -> return
      MascotReaction.CorrectJamo -> {
        go(reactionY, 2.5f, easeOut(0.08f)); go(reactionScale, 0.98f, easeOut(0.08f))
        delay(90)
        val s = swiftSpring(0.18f, 0.62f)
        go(reactionY, 0f, s); go(reactionScale, 1f, s)
        delay(210)
      }
      MascotReaction.SyllableCompleted -> {
        val a = swiftSpring(0.14f, 0.5f)
        go(reactionFlap, 58f, a); go(reactionY, -3f, a); go(reactionScale, 1.035f, a); go(glow, 1f, a); go(headphoneBeat, 1.045f, a)
        delay(150)
        val b = swiftSpring(0.2f, 0.62f)
        go(reactionFlap, 0f, b); go(reactionY, 0f, b); go(reactionScale, 1f, b); go(glow, 0f, b); go(headphoneBeat, 1f, b)
        delay(260)
      }
      MascotReaction.WordCompleted -> {
        showReactionFeathers()
        val a = swiftSpring(0.2f, 0.46f)
        go(reactionY, -12f, a); go(reactionFlap, 96f, a); go(reactionScale, 1.07f, a); go(glow, 1f, a)
        go(propRotation, -22f, a); go(headphoneBeat, 1.08f, a)
        delay(230)
        val b = swiftSpring(0.24f, 0.58f)
        go(reactionY, 0f, b); go(reactionFlap, 0f, b); go(reactionScale, 0.96f, b); go(propRotation, 12f, b); go(headphoneBeat, 0.97f, b)
        delay(110)
        val c = swiftSpring(0.2f, 0.64f)
        go(reactionScale, 1f, c); go(glow, 0f, c); go(propRotation, 0f, c); go(headphoneBeat, 1f, c)
        delay(240)
      }
      is MascotReaction.ComboMilestone -> {
        showReactionFeathers()
        val tier = if (event.count >= 20) 3 else if (event.count >= 10) 2 else 1
        repeat(if (tier == 3) 2 else 1) {
          val a = swiftSpring(0.18f, 0.44f)
          go(reactionY, (-4f - tier * 3), a); go(reactionFlap, (48f + tier * 22), a)
          go(reactionScale, 1f + tier * 0.025f, a); go(glow, 1f, a)
          go(propRotation, (-10f - tier * 7), a); go(headphoneBeat, 1f + tier * 0.035f, a)
          delay(180)
          val b = swiftSpring(0.2f, 0.58f)
          go(reactionY, 0f, b); go(reactionFlap, 0f, b); go(reactionScale, 1f, b)
          go(propRotation, (5f + tier * 3), b); go(headphoneBeat, 1f, b)
          delay(140)
        }
        go(glow, 0f, easeOut(0.16f)); go(propRotation, 0f, easeOut(0.16f))
        delay(180)
      }
      MascotReaction.Mistake -> {
        go(reactionTilt, -16f, easeOut(0.09f))
        delay(100)
        go(reactionTilt, 7f, easeInOut(0.1f))
        delay(110)
        go(reactionTilt, 0f, swiftSpring(0.2f, 0.65f))
        delay(250)
      }
      MascotReaction.PerfectSession -> {
        showReactionFeathers()
        val a = swiftSpring(0.22f, 0.42f)
        go(reactionY, -18f, a); go(reactionFlap, 122f, a); go(reactionScale, 1.12f, a); go(glow, 1f, a)
        go(propRotation, -34f, a); go(capTossOffset, -54f, a); go(capTossRotation, -22f, a); go(headphoneBeat, 1.13f, a)
        delay(270)
        val b = swiftSpring(0.28f, 0.58f)
        go(reactionY, 0f, b); go(reactionFlap, 0f, b); go(reactionScale, 0.95f, b); go(propRotation, 16f, b)
        go(capTossOffset, 0f, b); go(capTossRotation, 12f, b); go(headphoneBeat, 0.96f, b)
        delay(140)
        val c = swiftSpring(0.22f, 0.62f)
        go(reactionScale, 1f, c); go(glow, 0f, c); go(propRotation, 0f, c); go(capTossRotation, 0f, c); go(headphoneBeat, 1f, c)
        delay(260)
      }
      is MascotReaction.Rhythm -> {
        eventMood = if (event.count >= 15) MascotMood.FOCUS else MascotMood.HAPPY
        val beats = (event.count / 5).coerceIn(1, 4)
        for (beat in 0 until beats) {
          val a = easeInOut(0.11f)
          go(reactionTilt, if (beat % 2 == 0) -8f else 8f, a); go(reactionY, -3f, a)
          go(headphoneBeat, 1.08f, a); go(propRotation, if (beat % 2 == 0) -12f else 12f, a)
          delay(120)
        }
        val b = swiftSpring(0.2f, 0.64f)
        go(reactionTilt, 0f, b); go(reactionY, 0f, b); go(headphoneBeat, 1f, b); go(propRotation, 0f, b)
        delay(220)
      }
      MascotReaction.Startle -> {
        eventMood = MascotMood.SURPRISE
        val a = swiftSpring(0.13f, 0.42f)
        go(reactionY, -7f, a); go(reactionScale, 1.12f, a); go(reactionFlap, 40f, a)
        delay(150)
        val b = swiftSpring(0.24f, 0.68f)
        go(reactionY, 0f, b); go(reactionScale, 1f, b); go(reactionFlap, 0f, b)
        delay(300)
      }
      MascotReaction.GrowthTransition -> {
        eventMood = MascotMood.EUREKA
        showReactionFeathers()
        reactionScale.snapTo(0.55f)
        val a = swiftSpring(0.48f, 0.48f)
        go(reactionScale, 1.16f, a); go(glow, 1f, a); go(reactionFlap, 126f, a)
        go(idleSpin, idleSpin.targetValue + 360f, a)
        delay(480)
        val b = swiftSpring(0.28f, 0.62f)
        go(reactionScale, 1f, b); go(glow, 0f, b); go(reactionFlap, 0f, b)
        delay(300)
      }
      MascotReaction.Stretch -> {
        eventMood = MascotMood.SATISFIED
        val a = easeOut(0.22f)
        go(reactionScale, 1.08f, a); go(reactionY, -4f, a); go(reactionFlap, 82f, a)
        delay(420)
        val b = swiftSpring(0.28f, 0.62f)
        go(reactionScale, 1f, b); go(reactionY, 0f, b); go(reactionFlap, 0f, b)
        delay(260)
      }
      MascotReaction.EggKnock -> {
        for (direction in floatArrayOf(-1f, 1f, -1f, 0f)) {
          val a = easeInOut(0.12f)
          go(reactionTilt, direction * 7f, a); go(reactionY, if (direction == 0f) 0f else -2f, a)
          delay(130)
        }
        glow.snapTo(1f)
        delay(180)
      }
      MascotReaction.ReviewGraduated -> {
        eventMood = MascotMood.WINK
        val a = swiftSpring(0.2f, 0.46f)
        go(reactionScale, 1.1f, a); go(reactionTilt, -8f, a); go(glow, 1f, a)
        delay(520)
      }
      MascotReaction.NewBest -> {
        eventMood = MascotMood.SURPRISE
        val a = swiftSpring(0.18f, 0.42f)
        go(reactionY, -10f, a); go(reactionScale, 1.13f, a); go(glow, 1f, a)
        delay(260)
        eventMood = MascotMood.PROUD
        showReactionFeathers()
        delay(500)
      }
      is MascotReaction.LessonStreak -> {
        eventMood = if (event.count >= 3) MascotMood.SATISFIED else MascotMood.HAPPY
        val a = swiftSpring(0.22f, 0.48f)
        go(reactionY, -8f, a); go(reactionScale, 1.08f, a); go(glow, 1f, a)
        delay(480)
      }
      MascotReaction.Eureka -> {
        eventMood = MascotMood.EUREKA
        val a = swiftSpring(0.2f, 0.46f)
        go(reactionY, -7f, a); go(reactionScale, 1.1f, a); go(glow, 1f, a)
        delay(520)
      }
    }
    resetReactionPose()
  }
}
