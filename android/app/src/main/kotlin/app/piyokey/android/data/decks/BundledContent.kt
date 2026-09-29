package app.piyokey.android.data.decks

import app.piyokey.android.data.persistence.AssetSource
import app.piyokey.android.data.persistence.BundledAssetPaths
import app.piyokey.core.deckkit.Catalog
import app.piyokey.core.deckkit.CatalogDeck
import app.piyokey.core.deckkit.Deck
import app.piyokey.core.deckkit.DeckJson
import app.piyokey.core.deckkit.DeckValidator
import app.piyokey.core.domain.FlowGameRankTuning
import app.piyokey.core.domain.FlowGameRankTuningException
import app.piyokey.core.domain.FlowGameRankTuningLoader
import app.piyokey.core.domain.GameProgressRules
import app.piyokey.core.domain.OnboardingGoal
import app.piyokey.core.domain.RandomWordPracticeCatalog

/** Read-only bundled files (decks outside the library, schema, tuning). Blocking IO. */
class BundledContent(private val assets: AssetSource) {
  /** Pinned `deck.schema.json` for `.typedeck` read/write. */
  val deckSchema: ByteArray by lazy {
    requireNotNull(assets.read(BundledAssetPaths.DECK_SCHEMA)) { "Missing bundled deck.schema.json" }
  }

  /** Bundled game rank tuning (throws if missing/invalid, like iOS `requiredBundled`). */
  val rankTuning: FlowGameRankTuning by lazy {
    FlowGameRankTuningLoader.decode(
      assets.read(BundledAssetPaths.RANK_TUNING) ?: throw FlowGameRankTuningException.MissingBundledResource,
    )
  }

  /** A validated deck at `catalog/<relativePath>`, or `null` when missing/invalid. */
  fun deck(relativePath: String): Deck? {
    if (relativePath.contains("..") || !relativePath.startsWith("decks/")) return null
    val data = assets.read(BundledAssetPaths.catalogFile(relativePath)) ?: return null
    val deck = runCatching { DeckJson.decodeDeck(data) }.getOrNull() ?: return null
    return deck.takeIf { DeckValidator.validate(it).isEmpty() }
  }

  /** An official catalog deck read straight from the bundle (matching id and version). */
  fun officialDeck(entry: CatalogDeck): Deck? {
    if (!entry.official) return null
    return deck(entry.fileUrl)?.takeIf { it.deckId == entry.deckId && it.version == entry.version }
  }

  /** Weekly Piyo Cup deck `decks/flow/flow_topik_beginner_v3.json` (iOS `PiyoCupDeckLoader`). */
  fun piyoCupDeck(): Deck? = deck("decks/flow/${GameProgressRules.PIYO_CUP_DECK_ID}_v$PIYO_CUP_DECK_VERSION.json")
    ?.takeIf { it.deckId == GameProgressRules.PIYO_CUP_DECK_ID && it.version == PIYO_CUP_DECK_VERSION }

  /** Game preset deck (`decks/<game>/<deckId>_v<version>.json`, e.g. `decks/acid_rain/acid_rain_topik_beginner_v3.json`). */
  fun gamePresetDeck(gameFolder: String, deckId: String, version: Int = PIYO_CUP_DECK_VERSION): Deck? =
    deck("decks/$gameFolder/${deckId}_v$version.json")?.takeIf { it.deckId == deckId && it.version == version }

  /** Random word practice fallbacks: the goal's official deck, then the Piyo Cup deck. */
  fun randomWordFallbackDecks(goal: OnboardingGoal?, catalog: Catalog?): List<Deck> {
    val decks = ArrayList<Deck>()
    val goalDeckId = RandomWordPracticeCatalog.fallbackDeckId(goal)
    catalog?.decks?.firstOrNull { it.deckId == goalDeckId }?.let(::officialDeck)?.let(decks::add)
    piyoCupDeck()?.takeIf { cup -> decks.none { it.deckId == cup.deckId } }?.let(decks::add)
    return decks
  }

  companion object {
    const val PIYO_CUP_DECK_VERSION = 3
  }
}
