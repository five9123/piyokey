package app.piyokey.android.platform.audio

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import app.piyokey.android.Services
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Korean target pronunciation (iOS `TargetSpeechSynthesizer`): declared bundled audio →
 * canonical `audio/ko_<sha>.mp3` → offline `TextToSpeech` (ko-KR). Never uses the network and
 * never requests audio focus. Call [stop] when a session is left or the app is backgrounded.
 *
 * All public functions must be called on the main thread.
 */
object Pronunciation {
  private val main = Handler(Looper.getMainLooper())
  private val speaking = MutableStateFlow(false)

  /** True from [speak] until the clip/utterance finishes or [stop]. */
  val isSpeaking: StateFlow<Boolean> = speaking.asStateFlow()

  /** Read by [SoundEngine] on its thread to mute effects during pronunciation. */
  @Volatile internal var isPlaybackActive: Boolean = false
    private set

  private val speechAttributes: AudioAttributes = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_MEDIA)
    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
    .build()

  private var player: MediaPlayer? = null
  private var pendingCandidates = ArrayDeque<Pair<String, PronunciationBackend>>()
  private var fallbackTarget: String? = null
  private var activeUtteranceId: String? = null

  private var tts: TextToSpeech? = null
  private var ttsReady = false
  private var ttsFailed = false
  private var pendingSynthesis: String? = null

  /** Last backend used, for debugging/tests (iOS `HancoAudioDebugProbe`). */
  var lastBackend: PronunciationBackend? = null
    private set

  fun speak(korean: String, declaredAudio: String? = null) {
    if (korean.isEmpty()) return
    stop()
    SoundEngine.suspendForPronunciationPlayback()
    setActive(true)
    fallbackTarget = korean
    val assets = Services.context.assets
    pendingCandidates = ArrayDeque(
      PronunciationAudio.candidates(korean, declaredAudio) { path ->
        runCatching { assets.openFd(path).close(); true }.getOrDefault(false)
      },
    )
    playNextCandidateOrSynthesize()
  }

  fun stop() {
    player?.let { runCatching { it.stop() }; it.release() }
    player = null
    activeUtteranceId = null
    pendingSynthesis = null
    tts?.stop()
    pendingCandidates.clear()
    fallbackTarget = null
    setActive(false)
  }

  /** Warms the TTS engine so the first fallback utterance does not wait for binding. */
  fun warmUp() {
    ensureTts()
  }

  private fun playNextCandidateOrSynthesize() {
    while (pendingCandidates.isNotEmpty()) {
      val (path, backend) = pendingCandidates.removeFirst()
      val created = runCatching {
        Services.context.assets.openFd(path).use { descriptor ->
          MediaPlayer().apply {
            setAudioAttributes(speechAttributes)
            setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
          }
        }
      }.getOrNull() ?: continue
      player = created
      created.setOnPreparedListener { prepared -> if (prepared === player) prepared.start() }
      created.setOnCompletionListener { finished ->
        if (finished !== player) return@setOnCompletionListener
        finished.release()
        player = null
        pendingCandidates.clear()
        fallbackTarget = null
        setActive(false)
      }
      created.setOnErrorListener { failed, _, _ ->
        if (failed === player) {
          failed.release()
          player = null
          playNextCandidateOrSynthesize()
        }
        true
      }
      val prepared = runCatching { created.prepareAsync() }.isSuccess
      if (prepared) {
        lastBackend = backend
        return
      }
      created.release()
      player = null
    }
    val target = fallbackTarget ?: return setActive(false)
    lastBackend = PronunciationBackend.SYNTHESIZED
    synthesize(target)
  }

  private fun synthesize(target: String) {
    val engine = ensureTts()
    when {
      ttsFailed || engine == null -> finishSynthesis()
      !ttsReady -> pendingSynthesis = target
      else -> speakNow(engine, target)
    }
  }

  private fun speakNow(engine: TextToSpeech, target: String) {
    val id = UUID.randomUUID().toString()
    activeUtteranceId = id
    engine.setSpeechRate(PronunciationAudio.androidSpeechRate)
    engine.setPitch(1f)
    val params = Bundle().apply {
      @Suppress("DEPRECATION")
      putString(TextToSpeech.Engine.KEY_FEATURE_EMBEDDED_SYNTHESIS, "true")
      @Suppress("DEPRECATION")
      putString(TextToSpeech.Engine.KEY_FEATURE_NETWORK_SYNTHESIS, "false")
    }
    if (engine.speak(target, TextToSpeech.QUEUE_FLUSH, params, id) != TextToSpeech.SUCCESS) {
      activeUtteranceId = null
      finishSynthesis()
    }
  }

  private fun ensureTts(): TextToSpeech? {
    tts?.let { return it }
    if (ttsFailed) return null
    val created = runCatching {
      TextToSpeech(Services.context) { status -> main.post { onTtsInit(status) } }
    }.getOrNull()
    if (created == null) {
      ttsFailed = true
      return null
    }
    created.setAudioAttributes(speechAttributes)
    created.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
      override fun onStart(utteranceId: String?) = Unit
      override fun onDone(utteranceId: String?) { main.post { onUtteranceEnded(utteranceId) } }
      @Deprecated("Deprecated in Java")
      override fun onError(utteranceId: String?) { main.post { onUtteranceEnded(utteranceId) } }
      override fun onError(utteranceId: String?, errorCode: Int) { main.post { onUtteranceEnded(utteranceId) } }
      override fun onStop(utteranceId: String?, interrupted: Boolean) { main.post { onUtteranceEnded(utteranceId) } }
    })
    tts = created
    return created
  }

  private fun onTtsInit(status: Int) {
    val engine = tts ?: return
    if (status != TextToSpeech.SUCCESS || !selectOfflineKoreanVoice(engine)) {
      ttsFailed = true
      pendingSynthesis = null
      finishSynthesis()
      return
    }
    ttsReady = true
    pendingSynthesis?.let {
      pendingSynthesis = null
      speakNow(engine, it)
    }
  }

  /** Picks an installed, offline ko voice; fails closed (no speech) rather than going online. */
  private fun selectOfflineKoreanVoice(engine: TextToSpeech): Boolean {
    val voices: Set<Voice> = runCatching { engine.voices }.getOrNull().orEmpty()
    val offline = voices.filter {
      it.locale.language == Locale.KOREAN.language &&
        !it.isNetworkConnectionRequired &&
        TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features.orEmpty()
    }
    val voice = offline.minByOrNull { (if (it.locale.country == "KR") 0 else 1) * 1000 - it.quality }
    if (voice != null) return engine.setVoice(voice) == TextToSpeech.SUCCESS
    val result = engine.setLanguage(Locale.KOREA)
    return result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
  }

  private fun onUtteranceEnded(utteranceId: String?) {
    if (utteranceId == null || utteranceId != activeUtteranceId) return
    activeUtteranceId = null
    finishSynthesis()
  }

  private fun finishSynthesis() {
    pendingCandidates.clear()
    fallbackTarget = null
    setActive(false)
  }

  private fun setActive(active: Boolean) {
    isPlaybackActive = active
    speaking.value = active
  }
}
