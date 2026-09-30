package app.piyokey.android.platform.audio

import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.os.SystemClock
import app.piyokey.android.R
import app.piyokey.android.Services
import app.piyokey.android.data.settings.BoolPref
import app.piyokey.android.data.settings.StringPref
import java.io.File

/** iOS `SoundPreferenceKeys`. */
object SoundPrefs {
  val effectsEnabled = BoolPref("sound.effects_enabled", true)
  val typingPreset = StringPref("sound.typing_preset", TypingSoundPreset.SYSTEM.raw)

  val preset: TypingSoundPreset get() = TypingSoundPreset.resolved(typingPreset.value)
}

/**
 * Low-latency effect player (iOS `HancoSoundEngine`). Buffers are rendered with the same
 * plans/tuning as iOS ([SoundPlanner], [TypingSoundTuning]), written once as small WAV files and
 * played through a [SoundPool] with `USAGE_GAME`/`CONTENT_TYPE_SONIFICATION`.
 *
 * Audio focus is never requested, so other apps' music keeps playing (iOS `.mixWithOthers`).
 * Effects are skipped while a pronunciation is playing, stop after [SoundPlaybackPolicy.IDLE_SHUTDOWN_DELAY_MILLIS]
 * of inactivity and the pool is released when the app goes to the background.
 */
object SoundEngine {
  private sealed interface CacheKey {
    val fileName: String

    data class Typing(val preset: TypingSoundPreset, val role: TypingSoundKeyRole, val variation: Int) : CacheKey {
      override val fileName get() = "typing_${preset.raw}_${role.name.lowercase()}_$variation.wav"
    }
    data class Completion(val combo: Int) : CacheKey { override val fileName get() = "completion_$combo.wav" }
    data object Mistake : CacheKey { override val fileName get() = "mistake.wav" }
    data object LifeLost : CacheKey { override val fileName get() = "life_lost.wav" }
    data object EggKnock : CacheKey { override val fileName get() = "egg_knock.wav" }
  }

  private const val PENDING_PLAY_WINDOW_MILLIS = 250L
  private const val CACHE_VERSION = 1

  private val thread by lazy {
    HandlerThread("piyokey-sound", Process.THREAD_PRIORITY_URGENT_AUDIO).apply { start() }
  }
  private val handler by lazy { Handler(thread.looper) }

  // Everything below is confined to [handler]'s thread.
  private var pool: SoundPool? = null
  private val soundIds = HashMap<CacheKey, Int>()
  private val loadedIds = HashSet<Int>()
  private val pendingPlays = HashMap<Int, Pair<Float, Long>>()
  private val typingSources = HashMap<TypingSoundPreset, FloatArray>()
  private var nextVariation = 0
  private val idleShutdown = Runnable { pool?.autoPause() }

  private val cacheDir: File by lazy {
    File(Services.context.cacheDir, "sfx/v$CACHE_VERSION").apply { mkdirs() }
  }

  /** Current `sound.effects_enabled` value; setting it also stops playback like iOS `setEnabled(false)`. */
  var isEnabled: Boolean
    get() = SoundPrefs.effectsEnabled.value
    set(value) {
      SoundPrefs.effectsEnabled.value = value
      if (!value) suspendForInactivity()
    }

  var preset: TypingSoundPreset
    get() = SoundPrefs.preset
    set(value) { SoundPrefs.typingPreset.value = value.raw }

  fun keyTap(role: TypingSoundKeyRole = TypingSoundKeyRole.CHARACTER, preset: TypingSoundPreset = SoundPrefs.preset) =
    play(SoundEvent.KeyTap(preset, role))

  fun completion(combo: Int) = play(SoundEvent.Completion(combo))
  fun mistake() = play(SoundEvent.Mistake)
  fun lifeLost() = play(SoundEvent.LifeLost)
  fun eggKnock() = play(SoundEvent.EggKnock)

  /** Pre-renders the current typing preset plus the feedback sounds (iOS `prepareForInputFeedback`). */
  fun warmUp(currentCombo: Int = 0) {
    if (!isEnabled) return
    val preset = SoundPrefs.preset
    handler.post {
      handler.removeCallbacks(idleShutdown)
      val pool = ensurePool()
      for (role in TypingSoundKeyRole.entries) {
        for (variation in 0 until TypingSoundTuning.VARIATION_COUNT) {
          soundId(pool, CacheKey.Typing(preset, role, variation), SoundEvent.KeyTap(preset, role))
        }
      }
      SoundWarmupPolicy.completionCombosToPrepare(currentCombo).forEach {
        soundId(pool, CacheKey.Completion(it), SoundEvent.Completion(it))
      }
      soundId(pool, CacheKey.Mistake, SoundEvent.Mistake)
      scheduleIdleShutdown()
    }
  }

  fun play(event: SoundEvent) {
    if (!isEnabled || Pronunciation.isPlaybackActive) return
    val requestedAt = SystemClock.uptimeMillis()
    handler.post {
      if (!isEnabled || Pronunciation.isPlaybackActive) return@post
      handler.removeCallbacks(idleShutdown)
      val pool = ensurePool()
      val (key, volume) = when (event) {
        is SoundEvent.KeyTap -> {
          val variation = nextVariation
          nextVariation = (nextVariation + 1) % TypingSoundTuning.VARIATION_COUNT
          CacheKey.Typing(event.preset, event.role, variation) to 1f
        }
        is SoundEvent.Completion -> CacheKey.Completion(event.combo.coerceIn(0, 20)) to 1f
        SoundEvent.Mistake -> CacheKey.Mistake to 1f
        SoundEvent.LifeLost -> CacheKey.LifeLost to 1f
        SoundEvent.EggKnock -> CacheKey.EggKnock to 1f
      }
      val id = soundId(pool, key, event) ?: return@post
      if (id in loadedIds) {
        pool.play(id, volume, volume, 1, 0, 1f)
      } else {
        pendingPlays[id] = volume to requestedAt
      }
      scheduleIdleShutdown()
    }
  }

  /** Background / disabled: stop everything and release the pool (iOS `suspendForInactivity`). */
  fun suspendForInactivity() {
    handler.post {
      handler.removeCallbacks(idleShutdown)
      releasePool()
    }
  }

  /** Called when a pronunciation starts (iOS `suspendForPronunciationPlayback`). */
  internal fun suspendForPronunciationPlayback() {
    handler.post {
      handler.removeCallbacks(idleShutdown)
      pool?.autoPause()
    }
  }

  private fun ensurePool(): SoundPool = pool ?: SoundPool.Builder()
    .setMaxStreams(SoundPlaybackPolicy.TYPING_PLAYER_COUNT + SoundPlaybackPolicy.FEEDBACK_PLAYER_COUNT)
    .setAudioAttributes(
      AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build(),
    )
    .build()
    .also { created ->
      created.setOnLoadCompleteListener { loadedPool, sampleId, status ->
        handler.post {
          if (loadedPool !== pool) return@post
          if (status == 0) loadedIds += sampleId
          val pending = pendingPlays.remove(sampleId) ?: return@post
          val fresh = SystemClock.uptimeMillis() - pending.second <= PENDING_PLAY_WINDOW_MILLIS
          if (status == 0 && fresh && isEnabled && !Pronunciation.isPlaybackActive) {
            loadedPool.play(sampleId, pending.first, pending.first, 1, 0, 1f)
          }
        }
      }
      pool = created
    }

  private fun releasePool() {
    pool?.release()
    pool = null
    soundIds.clear()
    loadedIds.clear()
    pendingPlays.clear()
  }

  private fun scheduleIdleShutdown() {
    handler.removeCallbacks(idleShutdown)
    handler.postDelayed(idleShutdown, SoundPlaybackPolicy.IDLE_SHUTDOWN_DELAY_MILLIS)
  }

  private fun soundId(pool: SoundPool, key: CacheKey, event: SoundEvent): Int? {
    soundIds[key]?.let { return it }
    val file = File(cacheDir, key.fileName)
    if (!file.isFile || file.length() <= 44) {
      val samples = renderSamples(key, event) ?: return null
      runCatching {
        val temp = File(cacheDir, "${key.fileName}.tmp")
        temp.writeBytes(SoundRenderer.wavBytes(samples))
        if (!temp.renameTo(file)) return null
      }.getOrElse { return null }
    }
    val id = pool.load(file.absolutePath, 1)
    if (id == 0) return null
    soundIds[key] = id
    return id
  }

  private fun renderSamples(key: CacheKey, event: SoundEvent): FloatArray? = when (key) {
    is CacheKey.Typing -> {
      val source = typingSources.getOrPut(key.preset) {
        (if (key.preset == TypingSoundPreset.SYSTEM) loadBundledClick() else null)
          ?: SoundRenderer.render(SoundPlanner.plan(SoundEvent.KeyTap(key.preset, TypingSoundKeyRole.CHARACTER)))
      }
      SoundRenderer.typingVariant(source, TypingSoundTuning.profile(key.preset, key.role, key.variation))
        ?: SoundRenderer.render(SoundPlanner.plan(event))
    }
    else -> SoundRenderer.render(SoundPlanner.plan(event))
  }

  /** The only bundled SFX (iOS `ui_basic_mouse_click_640020.caf`, converted to 48 kHz mono WAV). */
  private fun loadBundledClick(): FloatArray? = runCatching {
    Services.context.resources.openRawResource(R.raw.ui_basic_mouse_click_640020).use { it.readBytes() }
  }.getOrNull()?.let { SoundRenderer.decodeWav(it) }
}
