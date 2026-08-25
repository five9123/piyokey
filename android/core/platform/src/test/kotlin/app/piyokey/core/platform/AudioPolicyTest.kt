package app.piyokey.core.platform

import app.piyokey.core.settings.KeySoundStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AudioPolicyTest {
  @Test
  fun resolverTriesDeclaredThenCanonicalAndNeverUsesTtsForMissingFixedContent() {
    val target = "안녕하세요"
    val canonical = PronunciationResolver.canonicalPath(target)
    assertEquals("audio/ko_2c68318e352971113645.mp3", canonical)
    assertEquals(PronunciationSource.Asset("audio/custom.mp3"), PronunciationResolver.resolve("audio/custom.mp3", target, true) { it == "audio/custom.mp3" })
    assertEquals(PronunciationSource.Asset(canonical), PronunciationResolver.resolve("audio/missing.mp3", target, true) { it == canonical })
    assertIs<PronunciationSource.MissingFixedAsset>(PronunciationResolver.resolve(null, target, true) { false })
    assertEquals(PronunciationSource.DynamicLocalTts(target), PronunciationResolver.resolve(null, target, false) { false })
  }

  @Test
  fun synthesizedCuesAreShort48kMonoPcmWithValidWaveHeader() {
    SoundCue.entries.forEach { cue ->
      KeySoundStyle.entries.forEach { style ->
        val sound = SoundSynthesizer.synthesize(cue, style)
        assertEquals(48_000, sound.sampleRate)
        assertTrue(sound.samples.isNotEmpty())
        assertTrue(sound.samples.size <= 48_000 * 120 / 1_000)
        assertEquals("RIFF", sound.wavBytes().copyOfRange(0, 4).toString(Charsets.US_ASCII))
      }
    }
  }
}
