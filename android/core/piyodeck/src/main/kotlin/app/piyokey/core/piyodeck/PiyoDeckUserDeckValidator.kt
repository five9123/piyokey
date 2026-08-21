package app.piyokey.core.piyodeck

import app.piyokey.core.deckkit.Deck

/** Package-only rules layered on top of DeckKit's shared schema and semantics. */
internal object PiyoDeckUserDeckValidator {
  private val userDeckId = Regex("^user_[0-9a-f]{32}$")
  private val userItemId = Regex("^item_[0-9a-f]{32}$")
  private val canonicalTimestamp = Regex(
    "^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$",
  )

  fun validate(deck: Deck): List<PiyoDeckValidationIssue> = buildList {
    if (deck.deckId.startsWith("official_")) {
      issue("reserved_identifier", "deck_id", "official_ is reserved")
    }
    if (!userDeckId.matches(deck.deckId)) {
      issue(
        "user_deck_identifier",
        "deck_id",
        "expected user_ followed by 32 lower-case hex characters",
      )
    }
    if (deck.author.id.startsWith("official_")) {
      issue("reserved_identifier", "author.id", "official_ is reserved")
    }
    if (deck.official) {
      issue("user_deck_official", "official", "user decks must set official=false")
    }
    if (deck.items.size !in 1..PiyoDeckPackageLimits.MAXIMUM_ITEM_COUNT) {
      issue(
        "user_deck_item_limit",
        "items",
        "user decks require 1...${PiyoDeckPackageLimits.MAXIMUM_ITEM_COUNT} items",
      )
    }
    if (!canonicalTimestamp.matches(deck.createdAt.toString())) {
      issue(
        "canonical_timestamp",
        "created_at",
        "must use canonical UTC whole-second form",
      )
    }
    if (!canonicalTimestamp.matches(deck.updatedAt.toString())) {
      issue(
        "canonical_timestamp",
        "updated_at",
        "must use canonical UTC whole-second form",
      )
    }

    val seenIds = mutableSetOf<String>()
    deck.items.forEachIndexed { index, item ->
      val path = "items[$index]"
      if (!seenIds.add(item.id)) {
        issue("duplicate", "$path.id", "duplicate item identifier")
      }
      if (item.id.startsWith("official_")) {
        issue("reserved_identifier", "$path.id", "official_ is reserved")
      }
      if (!userItemId.matches(item.id)) {
        issue(
          "user_item_identifier",
          "$path.id",
          "expected item_ followed by 32 lower-case hex characters",
        )
      }
      if (item.audio != null) {
        issue("user_deck_audio", "$path.audio", ".piyodeck v1 requires null")
      }
    }
  }

  private fun MutableList<PiyoDeckValidationIssue>.issue(
    code: String,
    path: String,
    message: String,
  ) {
    add(PiyoDeckValidationIssue(code, path, message))
  }
}
