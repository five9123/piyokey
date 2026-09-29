package app.piyokey.android.data.decks

import app.piyokey.android.R
import app.piyokey.android.data.settings.AppSettings
import app.piyokey.android.ui.theme.L
import app.piyokey.core.deckkit.CatalogDeck
import app.piyokey.core.deckkit.CatalogPreviewItem
import app.piyokey.core.deckkit.CatalogTag
import app.piyokey.core.deckkit.Deck
import app.piyokey.core.deckkit.DeckItem

/**
 * Deck content in the in-app language (iOS `Core/Settings` extensions `appName`, `appMeaning`, …).
 * The pure overloads take the language code and fallback text explicitly (testable on the JVM);
 * the extension properties read `AppSettings.currentLanguage` and `L`.
 */
object ContentLocale {
  val currentCode: String get() = AppSettings.currentLanguage.raw

  fun meaning(item: DeckItem, languageCode: String, defaultLocale: String? = null): String? =
    item.localizedMeaning(languageCode, defaultLocale)

  fun reading(item: DeckItem, languageCode: String, defaultLocale: String? = null): String? =
    item.localizedReading(languageCode, defaultLocale)

  fun name(deck: Deck, languageCode: String, unavailable: String): String = deck.localizedName(languageCode) ?: unavailable

  fun authorNickname(deck: Deck, languageCode: String, officialAuthor: String, unavailable: String): String =
    deck.localizedAuthorNickname(languageCode) ?: if (deck.official) officialAuthor else unavailable

  fun tags(deck: Deck, languageCode: String): List<String> = deck.localizedTags(languageCode) ?: emptyList()

  fun name(deck: CatalogDeck, languageCode: String, unavailable: String): String = deck.localizedName(languageCode) ?: unavailable

  fun authorNickname(deck: CatalogDeck, languageCode: String, officialAuthor: String, unavailable: String): String =
    deck.localizedAuthorNickname(languageCode) ?: if (deck.official) officialAuthor else unavailable

  fun tags(deck: CatalogDeck, languageCode: String): List<String> = deck.localizedTags(languageCode) ?: emptyList()
}

/** Meaning in the app language. iOS passes no `defaultLocale` here; see [appMeaning] overload for a deck's items. */
val DeckItem.appMeaning: String? get() = localizedMeaning(ContentLocale.currentCode)
val DeckItem.appReading: String? get() = localizedReading(ContentLocale.currentCode)

/** Item clue using the owning deck's `default_locale` (v2 decks). */
fun DeckItem.appMeaning(deck: Deck): String? = localizedMeaning(ContentLocale.currentCode, deck.defaultLocale)
fun DeckItem.appReading(deck: Deck): String? = localizedReading(ContentLocale.currentCode, deck.defaultLocale)

val Deck.appName: String
  get() = ContentLocale.name(this, ContentLocale.currentCode, L.string(R.string.deck_localized_title_unavailable))

val Deck.appAuthorNickname: String
  get() = ContentLocale.authorNickname(
    this, ContentLocale.currentCode, L.string(R.string.deck_official_author), L.string(R.string.deck_author_unavailable),
  )

val Deck.appTags: List<String> get() = ContentLocale.tags(this, ContentLocale.currentCode)

val CatalogDeck.appName: String
  get() = ContentLocale.name(this, ContentLocale.currentCode, L.string(R.string.deck_localized_title_unavailable))

val CatalogDeck.appAuthorNickname: String
  get() = ContentLocale.authorNickname(
    this, ContentLocale.currentCode, L.string(R.string.deck_official_author), L.string(R.string.deck_author_unavailable),
  )

val CatalogDeck.appTags: List<String> get() = ContentLocale.tags(this, ContentLocale.currentCode)

/** Discover hides decks with no content in the app language. */
val CatalogDeck.isAvailableInCurrentLanguage: Boolean get() = hasLocalization(ContentLocale.currentCode)

val CatalogPreviewItem.appMeaning: String? get() = localizedMeaning(ContentLocale.currentCode)

val CatalogTag.appTag: String? get() = localizedTag(ContentLocale.currentCode)
