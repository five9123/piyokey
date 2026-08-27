package app.piyokey.core.platform

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import app.piyokey.core.settings.KeySoundStyle
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

enum class SoundCue { KEY, BACKSPACE, SHIFT, CORRECT, COMBO, MISTAKE, LIFE_LOST }

data class SynthesizedSound(val sampleRate: Int, val samples: ShortArray) {
  fun wavBytes(): ByteArray {
    val payloadSize = samples.size * 2
    return ByteBuffer.allocate(44 + payloadSize).order(ByteOrder.LITTLE_ENDIAN).apply {
      put("RIFF".toByteArray()); putInt(36 + payloadSize); put("WAVEfmt ".toByteArray())
      putInt(16); putShort(1); putShort(1); putInt(sampleRate); putInt(sampleRate * 2)
      putShort(2); putShort(16); put("data".toByteArray()); putInt(payloadSize)
      samples.forEach(::putShort)
    }.array()
  }
}

object SoundSynthesizer {
  fun synthesize(cue: SoundCue, style: KeySoundStyle): SynthesizedSound {
    val durationMillis = when (cue) {
      SoundCue.KEY -> when (style) { KeySoundStyle.DEFAULT -> 20; KeySoundStyle.MECHANICAL -> 28; KeySoundStyle.SOFT -> 34 }
      SoundCue.BACKSPACE -> 34
      SoundCue.SHIFT -> 15
      SoundCue.CORRECT, SoundCue.COMBO -> 90
      SoundCue.MISTAKE, SoundCue.LIFE_LOST -> 110
    }
    val frequency = when (cue) {
      SoundCue.KEY -> when (style) { KeySoundStyle.DEFAULT -> 1_050.0; KeySoundStyle.MECHANICAL -> 720.0; KeySoundStyle.SOFT -> 520.0 }
      SoundCue.BACKSPACE -> 310.0
      SoundCue.SHIFT -> 1_300.0
      SoundCue.CORRECT -> 760.0
      SoundCue.COMBO -> 1_020.0
      SoundCue.MISTAKE -> 220.0
      SoundCue.LIFE_LOST -> 165.0
    }
    val sampleRate = 48_000
    val count = sampleRate * durationMillis / 1_000
    val gain = when (cue) {
      SoundCue.MISTAKE, SoundCue.LIFE_LOST -> .38
      SoundCue.SHIFT -> .26
      else -> .48
    }
    val samples = ShortArray(count) { index ->
      val position = index.toDouble() / sampleRate
      val envelope = (1.0 - index.toDouble() / count).coerceIn(0.0, 1.0)
      val wave = sin(2.0 * PI * frequency * position) + .22 * sin(4.0 * PI * frequency * position)
      (wave * envelope * gain * Short.MAX_VALUE * .78).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
    }
    return SynthesizedSound(sampleRate, samples)
  }
}

/** Lazy SoundPool with synthesized local fallbacks and no audio-focus request. */
class PiyokeySoundEngine(private val context: Context) : AutoCloseable {
  private val handler = Handler(Looper.getMainLooper())
  private var pool: SoundPool? = null
  private val soundIds = mutableMapOf<Pair<SoundCue, KeySoundStyle>, Int>()
  private val loaded = mutableSetOf<Int>()
  private var enabled = true
  private var suppressed = false
  private var releaseGeneration = 0

  fun setEnabled(value: Boolean) {
    enabled = value
    if (!value) release()
  }

  fun setSuppressed(value: Boolean) { suppressed = value }

  fun play(cue: SoundCue, style: KeySoundStyle = KeySoundStyle.DEFAULT) {
    if (!enabled || suppressed) return
    val key = cue to style
    val soundPool = ensurePool()
    val soundId = soundIds[key] ?: load(soundPool, key).also { soundIds[key] = it }
    if (soundId in loaded) soundPool.play(soundId, 1f, 1f, 1, 0, 1f)
    scheduleIdleRelease()
  }

  private fun ensurePool(): SoundPool = pool ?: SoundPool.Builder()
    .setMaxStreams(8)
    .setAudioAttributes(
      AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build(),
    )
    .build()
    .also { created ->
      created.setOnLoadCompleteListener { soundPool, sampleId, status ->
        if (status == 0) {
          loaded += sampleId
          if (enabled && !suppressed) soundPool.play(sampleId, 1f, 1f, 1, 0, 1f)
        }
      }
      pool = created
    }

  private fun load(soundPool: SoundPool, key: Pair<SoundCue, KeySoundStyle>): Int {
    val directory = File(context.cacheDir, "piyokey_sounds").apply { mkdirs() }
    val file = File(directory, "${key.first.name.lowercase()}_${key.second.name.lowercase()}.wav")
    if (!file.exists()) file.writeBytes(SoundSynthesizer.synthesize(key.first, key.second).wavBytes())
    return soundPool.load(file.absolutePath, 1)
  }

  private fun scheduleIdleRelease() {
    val generation = ++releaseGeneration
    handler.postDelayed({ if (generation == releaseGeneration) release() }, 15_000)
  }

  fun release() {
    releaseGeneration++
    pool?.release()
    pool = null
    soundIds.clear()
    loaded.clear()
  }

  override fun close() = release()
}
