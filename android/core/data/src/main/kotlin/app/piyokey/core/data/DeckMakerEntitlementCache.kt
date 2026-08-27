package app.piyokey.core.data

class DeckMakerEntitlementCache(
  private val database: PiyokeyDatabase,
) {
  suspend fun read(productId: String): DeckMakerEntitlementCacheEntity? =
    database.dao().deckMakerEntitlementCache(productId)

  suspend fun write(productId: String, isActive: Boolean, verifiedAtEpochMillis: Long) {
    database.dao().upsertDeckMakerEntitlementCache(
      DeckMakerEntitlementCacheEntity(productId, isActive, verifiedAtEpochMillis),
    )
  }
}
