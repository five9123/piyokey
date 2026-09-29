package app.piyokey.android.platform.audio

import java.security.MessageDigest

/** Which backend produced a pronunciation (iOS `PronunciationAudioBackend`). */
enum class PronunciationBackend { EXPLICIT_BUNDLED, CANONICAL_BUNDLED, SYNTHESIZED }

/**
 * Pure path rules for bundled pronunciation audio (iOS `BundledPronunciationAudio`).
 * Relative paths are catalog-relative (`audio/ko_….mp3`); the app build copies
 * `shared/mock_catalog/audio` to assets `catalog/audio`.
 */
object PronunciationAudio {
  const val ASSET_ROOT = "catalog"

  /** `audio/ko_<first 20 hex of sha256(utf8)>.mp3`. */
  fun relativePath(target: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(target.toByteArray(Charsets.UTF_8))
    val prefix = digest.take(10).joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    return "audio/ko_$prefix.mp3"
  }

  /** Rejects empty, absolute and parent-traversing paths like iOS `url(forRelativePath:)`. */
  fun isSafeRelativePath(path: String?): Boolean {
    if (path.isNullOrEmpty() || path.startsWith("/") || path.contains('\\')) return false
    return path.split('/').none { it == ".." || it.isEmpty() || it == "." }
  }

  fun assetPath(relativePath: String) = "$ASSET_ROOT/$relativePath"

  /**
   * Ordered, de-duplicated candidates: the declared path first, then the canonical hash path.
   * [exists] checks an asset path (so tests can run without Android).
   */
  fun candidates(
    target: String,
    declared: String?,
    exists: (assetPath: String) -> Boolean,
  ): List<Pair<String, PronunciationBackend>> {
    val result = mutableListOf<Pair<String, PronunciationBackend>>()
    if (declared != null && isSafeRelativePath(declared)) {
      val asset = assetPath(declared)
      if (exists(asset)) result += asset to PronunciationBackend.EXPLICIT_BUNDLED
    }
    val canonical = assetPath(relativePath(target))
    if (result.none { it.first == canonical } && exists(canonical)) {
      result += canonical to PronunciationBackend.CANONICAL_BUNDLED
    }
    return result
  }

  /** iOS `AVSpeechUtterance.rate = 0.42` (default 0.5) → Android multiplier around 1.0 = normal. */
  const val IOS_RATE = 0.42f
  const val IOS_DEFAULT_RATE = 0.5f
  val androidSpeechRate: Float get() = IOS_RATE / IOS_DEFAULT_RATE
}
