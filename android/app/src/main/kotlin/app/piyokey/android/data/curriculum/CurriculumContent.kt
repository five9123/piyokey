package app.piyokey.android.data.curriculum

import android.content.res.Resources
import app.piyokey.android.Services
import app.piyokey.android.data.settings.AppLanguage
import app.piyokey.android.data.settings.AppSettings
import app.piyokey.android.ui.theme.L
import app.piyokey.core.deckkit.DeckItem
import app.piyokey.core.deckkit.DeckItemLocalization
import app.piyokey.core.domain.CurriculumChapter
import app.piyokey.core.domain.CurriculumItem
import app.piyokey.core.domain.CurriculumStage
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves `core/domain` localization keys (iOS keys such as `curriculum.chapter_1.title`,
 * `mascot.daily_encouragement.3`, `retention.rewards.5.title`) to text without
 * `Resources.getIdentifier` (release shrinking keeps only referenced ids, see [DomainStringIds]).
 */
object DomainText {
  private val resourcesByLanguage = ConcurrentHashMap<AppLanguage, Resources>()

  fun resources(language: AppLanguage): Resources =
    resourcesByLanguage.getOrPut(language) { L.localizedContext(Services.context, language).resources }

  /** Drop cached configuration contexts (e.g. after a configuration change). */
  fun clearCache() = resourcesByLanguage.clear()

  /** UI-localized text for [key]; the key itself when unknown (iOS `AppLocalization` behaviour). */
  fun string(key: String, language: AppLanguage = AppSettings.currentLanguage): String =
    DomainStringIds.ui(key)?.let { resources(language).getString(it) } ?: key

  /** Korean learning value (`KoreanLearningContent.strings`) for [key]. */
  fun korean(key: String): String = DomainStringIds.korean(key)?.let { Services.context.getString(it) } ?: key

  /** Resource id for Compose `stringResource`, or `null` if the key is not mapped. */
  fun id(key: String): Int? = DomainStringIds.ui(key)
}

/** App-side views of the pure curriculum catalog (iOS `CurriculumItem.deckItem`, `localizedTitle`). */
object CurriculumContent {
  fun title(stage: CurriculumStage): String = DomainText.string(stage.titleKey)
  fun detail(stage: CurriculumStage): String = DomainText.string(stage.detailKey)
  fun title(chapter: CurriculumChapter): String = DomainText.string(chapter.titleKey)

  /**
   * The practice [DeckItem] for [item]: Japanese base fields, one localization per other app
   * language (meaning in that language, English reading), plus Korean learning values under `ko`.
   * Audio points at the bundled pronunciation.
   */
  fun deckItem(item: CurriculumItem): DeckItem {
    val localizations = LinkedHashMap<String, DeckItemLocalization>()
    val englishReading = DomainText.string(item.readingKey, AppLanguage.ENGLISH)
    for (language in AppLanguage.entries) {
      if (language == AppLanguage.JAPANESE) continue
      localizations[language.raw] = DeckItemLocalization(meaning = DomainText.string(item.meaningKey, language), reading = englishReading)
    }
    localizations.putIfAbsent("ko", DeckItemLocalization(DomainText.korean(item.meaningKey), DomainText.korean(item.readingKey)))
    return DeckItem(
      id = item.id,
      ko = item.ko,
      readingJa = DomainText.string(item.readingKey, AppLanguage.JAPANESE),
      meaningJa = DomainText.string(item.meaningKey, AppLanguage.JAPANESE),
      audio = item.audioRelativePath,
      localizations = localizations,
    )
  }

  fun deckItems(stage: CurriculumStage): List<DeckItem> = stage.items.map(::deckItem)
}
