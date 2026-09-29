package app.piyokey.android.platform.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/*
 * Pure (JVM-testable) sound rules ported 1:1 from iOS `Core/Audio/HancoSoundEngine.swift`:
 * presets, per-role tuning, synthesized plans, PCM renderer and the typing-variant transform.
 * No Android imports here.
 */

/** iOS `TypingSoundPreset`. Unknown raw values resolve to [SYSTEM]. */
enum class TypingSoundPreset(val raw: String) {
  SYSTEM("system"), MECHANICAL("mechanical"), SOFT("soft");

  companion object {
    fun resolved(raw: String?) = entries.firstOrNull { it.raw == raw } ?: SYSTEM
  }
}

/** iOS `TypingSoundKeyRole`. */
enum class TypingSoundKeyRole { CHARACTER, BACKSPACE, SHIFT }

/** iOS `HancoSoundEvent`. */
sealed interface SoundEvent {
  data class KeyTap(val preset: TypingSoundPreset, val role: TypingSoundKeyRole) : SoundEvent
  data class Completion(val combo: Int) : SoundEvent
  data object Mistake : SoundEvent
  data object LifeLost : SoundEvent
  data object EggKnock : SoundEvent
}

enum class SoundPlaybackLane { TYPING, FEEDBACK }

object SoundPlaybackPolicy {
  const val TYPING_PLAYER_COUNT = 6
  const val FEEDBACK_PLAYER_COUNT = 3
  const val IDLE_SHUTDOWN_DELAY_MILLIS = 15_000L

  fun lane(event: SoundEvent) = if (event is SoundEvent.KeyTap) SoundPlaybackLane.TYPING else SoundPlaybackLane.FEEDBACK
}

object SoundWarmupPolicy {
  fun shouldPrepareForOsImeInput(committedText: String, markedText: String?) =
    committedText.isNotEmpty() || !markedText.isNullOrEmpty()

  fun completionCombosToPrepare(currentCombo: Int): List<Int> {
    val current = currentCombo.coerceIn(0, 20)
    val next = min(current + 1, 20)
    return if (current == next) listOf(current) else listOf(current, next)
  }
}

data class TypingSoundVariantProfile(val playbackRate: Double, val gain: Float, val peakLimit: Float)

object TypingSoundTuning {
  const val VARIATION_COUNT = 3
  const val PEAK_LIMIT = 0.78f

  fun profile(preset: TypingSoundPreset, role: TypingSoundKeyRole, variationIndex: Int): TypingSoundVariantProfile {
    val index = ((variationIndex % VARIATION_COUNT) + VARIATION_COUNT) % VARIATION_COUNT
    val variationRates = doubleArrayOf(0.988, 1.0, 1.012)
    val variationGains = floatArrayOf(0.96f, 1.0f, 0.93f)
    val presetGain = when (preset) {
      TypingSoundPreset.SYSTEM -> 0.34f
      TypingSoundPreset.MECHANICAL -> 0.80f
      TypingSoundPreset.SOFT -> 0.95f
    }
    val (roleRate, roleGain) = when (role) {
      TypingSoundKeyRole.CHARACTER -> 1.0 to 1f
      TypingSoundKeyRole.BACKSPACE -> 0.94 to 0.72f
      TypingSoundKeyRole.SHIFT -> 1.08 to 0.52f
    }
    return TypingSoundVariantProfile(
      playbackRate = variationRates[index] * roleRate,
      gain = presetGain * variationGains[index] * roleGain,
      peakLimit = PEAK_LIMIT,
    )
  }
}

enum class SoundWaveform { SINE, TRIANGLE, NOISE }

data class SoundComponent(
  val waveform: SoundWaveform,
  val startTime: Double,
  val duration: Double,
  val startFrequency: Double,
  val endFrequency: Double,
  val amplitude: Double,
  val attack: Double,
  val release: Double,
)

data class SoundPlan(val components: List<SoundComponent>) {
  val duration: Double get() = components.maxOfOrNull { it.startTime + it.duration } ?: 0.0
}

object SoundPlanner {
  fun plan(event: SoundEvent): SoundPlan = when (event) {
    is SoundEvent.KeyTap -> when (event.preset) {
      TypingSoundPreset.SYSTEM -> SoundPlan(listOf(
        SoundComponent(SoundWaveform.NOISE, 0.0, 0.018, 0.0, 0.0, 0.10, 0.0005, 0.014),
        SoundComponent(SoundWaveform.TRIANGLE, 0.0, 0.021, 2_150.0, 2_000.0, 0.075, 0.0005, 0.017),
      ))
      TypingSoundPreset.MECHANICAL -> SoundPlan(listOf(
        SoundComponent(SoundWaveform.NOISE, 0.0, 0.028, 0.0, 0.0, 0.15, 0.001, 0.022),
        SoundComponent(SoundWaveform.TRIANGLE, 0.0, 0.042, 1_650.0, 980.0, 0.12, 0.001, 0.034),
      ))
      TypingSoundPreset.SOFT -> SoundPlan(listOf(
        SoundComponent(SoundWaveform.SINE, 0.0, 0.062, 540.0, 420.0, 0.14, 0.004, 0.048),
      ))
    }
    is SoundEvent.Completion -> {
      val clamped = event.combo.coerceIn(0, 20)
      val base = 660 * 2.0.pow(clamped / 30.0)
      SoundPlan(listOf(
        SoundComponent(SoundWaveform.SINE, 0.0, 0.11, base, base, 0.17, 0.005, 0.07),
        SoundComponent(SoundWaveform.SINE, 0.065, 0.15, base * 1.25, base * 1.25, 0.15, 0.006, 0.1),
      ))
    }
    SoundEvent.Mistake -> SoundPlan(listOf(
      SoundComponent(SoundWaveform.SINE, 0.0, 0.14, 210.0, 170.0, 0.11, 0.008, 0.1),
    ))
    SoundEvent.LifeLost -> SoundPlan(listOf(
      SoundComponent(SoundWaveform.TRIANGLE, 0.0, 0.24, 260.0, 105.0, 0.20, 0.004, 0.16),
      SoundComponent(SoundWaveform.NOISE, 0.0, 0.075, 0.0, 0.0, 0.09, 0.002, 0.06),
    ))
    SoundEvent.EggKnock -> SoundPlan(listOf(
      SoundComponent(SoundWaveform.TRIANGLE, 0.0, 0.07, 430.0, 360.0, 0.10, 0.003, 0.055),
      SoundComponent(SoundWaveform.TRIANGLE, 0.13, 0.08, 500.0, 390.0, 0.09, 0.003, 0.06),
    ))
  }
}

/** Mono float PCM renderer and transforms (iOS `render`/`componentSample`/`typingVariant`). */
object SoundRenderer {
  const val SAMPLE_RATE = 48_000

  fun render(plan: SoundPlan, sampleRate: Int = SAMPLE_RATE): FloatArray {
    val frameCount = max(1, ceil(plan.duration * sampleRate).toInt())
    val out = FloatArray(frameCount)
    for (frame in 0 until frameCount) {
      val time = frame.toDouble() / sampleRate
      var sample = 0.0
      plan.components.forEachIndexed { index, component -> sample += componentSample(component, time, frame, index) }
      out[frame] = sample.coerceIn(-0.9, 0.9).toFloat()
    }
    return out
  }

  private fun componentSample(component: SoundComponent, time: Double, frame: Int, componentIndex: Int): Double {
    val localTime = time - component.startTime
    if (localTime < 0 || localTime >= component.duration) return 0.0
    val attackEnvelope = if (component.attack > 0) min(localTime / component.attack, 1.0) else 1.0
    val remaining = component.duration - localTime
    val releaseEnvelope = if (component.release > 0) min(remaining / component.release, 1.0) else 1.0
    val envelope = max(0.0, min(attackEnvelope, releaseEnvelope))
    val waveform = when (component.waveform) {
      SoundWaveform.SINE, SoundWaveform.TRIANGLE -> {
        val delta = component.endFrequency - component.startFrequency
        val phase = 2 * PI * (component.startFrequency * localTime + 0.5 * delta / component.duration * localTime * localTime)
        val sine = sin(phase)
        if (component.waveform == SoundWaveform.SINE) sine else (2 / PI) * asin(sine)
      }
      SoundWaveform.NOISE -> {
        // Same deterministic UInt32 hash as iOS (wrapping arithmetic).
        var hash = (frame * 1_103_515_245)
        hash += (componentIndex + 1) * 12_345
        hash = hash xor (hash ushr 16)
        (hash and 0xFFFF).toDouble() / 0xFFFF * 2 - 1
      }
    }
    return waveform * component.amplitude * envelope
  }

  /** Linear-interpolated resample + gain + peak clamp (iOS `HancoAudioBufferTransformer.typingVariant`). */
  fun typingVariant(source: FloatArray, profile: TypingSoundVariantProfile): FloatArray? {
    if (profile.playbackRate <= 0 || source.isEmpty()) return null
    val outCount = max(1, ceil(source.size / profile.playbackRate).toInt())
    val last = max(0, source.size - 1)
    return FloatArray(outCount) { frame ->
      val position = min(frame * profile.playbackRate, last.toDouble())
      val lower = position.toInt()
      val upper = min(lower + 1, last)
      val t = (position - lower).toFloat()
      val value = source[lower] + (source[upper] - source[lower]) * t
      (value * profile.gain).coerceIn(-profile.peakLimit, profile.peakLimit)
    }
  }

  /** 16-bit little-endian mono WAV bytes for SoundPool. */
  fun wavBytes(samples: FloatArray, sampleRate: Int = SAMPLE_RATE): ByteArray {
    val dataSize = samples.size * 2
    val buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
    buffer.put("RIFF".toByteArray(Charsets.US_ASCII)).putInt(36 + dataSize)
    buffer.put("WAVE".toByteArray(Charsets.US_ASCII))
    buffer.put("fmt ".toByteArray(Charsets.US_ASCII)).putInt(16).putShort(1).putShort(1)
      .putInt(sampleRate).putInt(sampleRate * 2).putShort(2).putShort(16)
    buffer.put("data".toByteArray(Charsets.US_ASCII)).putInt(dataSize)
    samples.forEach { buffer.putShort((it.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()) }
    return buffer.array()
  }

  /**
   * Decodes a PCM WAV (16-bit int or 32-bit float, mono) into float samples, walking chunks
   * (afconvert adds `FLLR`). Returns null for other formats or sample rates, like iOS which
   * rejects a bundled file that does not match the 48 kHz mono engine format.
   */
  fun decodeWav(bytes: ByteArray, expectedSampleRate: Int = SAMPLE_RATE): FloatArray? {
    if (bytes.size < 12) return null
    val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    fun tag(at: Int) = String(bytes, at, 4, Charsets.US_ASCII)
    if (tag(0) != "RIFF" || tag(8) != "WAVE") return null
    var offset = 12
    var format = -1
    var channels = 0
    var sampleRate = 0
    var bits = 0
    while (offset + 8 <= bytes.size) {
      val id = tag(offset)
      val size = buffer.getInt(offset + 4)
      val body = offset + 8
      if (size < 0 || body + size > bytes.size) return null
      when (id) {
        "fmt " -> {
          format = buffer.getShort(body).toInt()
          channels = buffer.getShort(body + 2).toInt()
          sampleRate = buffer.getInt(body + 4)
          bits = buffer.getShort(body + 14).toInt()
        }
        "data" -> {
          if (channels != 1 || sampleRate != expectedSampleRate) return null
          return when {
            format == 1 && bits == 16 -> FloatArray(size / 2) { buffer.getShort(body + it * 2) / 32768f }
            format == 3 && bits == 32 -> FloatArray(size / 4) { buffer.getFloat(body + it * 4) }
            else -> null
          }?.takeIf { it.isNotEmpty() }
        }
      }
      offset = body + size + (size and 1)
    }
    return null
  }
}
