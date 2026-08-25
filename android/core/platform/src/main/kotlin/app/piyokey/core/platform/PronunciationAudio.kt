package app.piyokey.core.platform

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.security.MessageDigest
import java.util.Locale

sealed interface PronunciationSource {
  data class Asset(val path: String) : PronunciationSource
  data class DynamicLocalTts(val text: String) : PronunciationSource
  data class MissingFixedAsset(val canonicalPath: String) : PronunciationSource
}

object PronunciationResolver {
  fun canonicalPath(text: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
    return "audio/ko_${digest.take(10).joinToString("") { "%02x".format(it.toInt() and 0xff) }}.mp3"
  }

  fun resolve(
    declaredPath: String?,
    target: String,
    isBundledFixedContent: Boolean,
    assetExists: (String) -> Boolean,
  ): PronunciationSource {
    declaredPath?.takeIf(assetExists)?.let { return PronunciationSource.Asset(it) }
    val canonical = canonicalPath(target)
    if (assetExists(canonical)) return PronunciationSource.Asset(canonical)
    return if (isBundledFixedContent) PronunciationSource.MissingFixedAsset(canonical)
    else PronunciationSource.DynamicLocalTts(target)
  }
}

/** Local-only prompt player. It deliberately never asks AudioManager for focus. */
class PronunciationPlayer(
  context: Context,
  private val onPlaybackStateChanged: (Boolean) -> Unit = {},
) : AutoCloseable {
  private val appContext = context.applicationContext
  private var mediaPlayer: MediaPlayer? = null
  private var tts: TextToSpeech? = null
  private var ttsReady = false

  fun play(
    declaredPath: String?,
    target: String,
    isBundledFixedContent: Boolean,
    onMissingFixedAsset: (String) -> Unit = {},
  ) {
    stop()
    if (declaredPath != null && assetExists(declaredPath) && playAsset(declaredPath)) return
    val canonical = PronunciationResolver.canonicalPath(target)
    if (canonical != declaredPath && assetExists(canonical) && playAsset(canonical)) return
    if (isBundledFixedContent) onMissingFixedAsset(canonical) else playDynamicTts(target)
  }

  private fun assetExists(path: String): Boolean = runCatching {
    appContext.assets.openFd(path).close()
  }.isSuccess

  private fun playAsset(path: String): Boolean = runCatching {
      appContext.assets.openFd(path).use { descriptor ->
        mediaPlayer = MediaPlayer().apply {
          setAudioAttributes(
            AudioAttributes.Builder()
              .setUsage(AudioAttributes.USAGE_MEDIA)
              .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
              .build(),
          )
          setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
          setOnCompletionListener { stopMedia() }
          setOnErrorListener { _, _, _ -> stopMedia(); true }
          prepare()
          start()
          onPlaybackStateChanged(true)
        }
      }
    }.isSuccess

  private fun playDynamicTts(text: String) {
    val existing = tts
    if (existing != null && ttsReady) {
      existing.speak(text, TextToSpeech.QUEUE_FLUSH, null, "piyokey-dynamic-prompt")
      return
    }
    tts = TextToSpeech(appContext) { status ->
      val engine = tts ?: return@TextToSpeech
      val languageResult = if (status == TextToSpeech.SUCCESS) engine.setLanguage(Locale.KOREA) else TextToSpeech.LANG_NOT_SUPPORTED
      val offlineKoreanVoice = engine.voices.orEmpty()
        .filter { it.locale.language == Locale.KOREAN.language && !it.isNetworkConnectionRequired }
        .sortedBy { it.name }
        .firstOrNull()
      ttsReady = status == TextToSpeech.SUCCESS && languageResult != TextToSpeech.LANG_MISSING_DATA &&
        languageResult != TextToSpeech.LANG_NOT_SUPPORTED && offlineKoreanVoice != null
      offlineKoreanVoice?.let { engine.voice = it }
      engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = onPlaybackStateChanged(true)
        override fun onDone(utteranceId: String?) = onPlaybackStateChanged(false)
        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) = onPlaybackStateChanged(false)
      })
      if (ttsReady) engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "piyokey-dynamic-prompt")
    }
  }

  fun stop() {
    stopMedia()
    tts?.stop()
    onPlaybackStateChanged(false)
  }

  private fun stopMedia() {
    mediaPlayer?.runCatching { release() }
    mediaPlayer = null
    onPlaybackStateChanged(false)
  }

  override fun close() {
    stop()
    tts?.shutdown()
    tts = null
    ttsReady = false
  }
}
