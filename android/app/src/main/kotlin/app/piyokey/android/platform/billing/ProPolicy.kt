package app.piyokey.android.platform.billing

/** iOS `PiyokeyProPolicy` (Core/DeckLibrary/DeckLibrary.swift). */
object PiyokeyProPolicy {
  const val FREE_INSTALLED_USER_DECK_LIMIT = 3

  /** Free users may keep up to [FREE_INSTALLED_USER_DECK_LIMIT] installed user decks. */
  fun canInstallUserDeck(hasAccess: Boolean, installedUserDeckCount: Int) =
    hasAccess || installedUserDeckCount < FREE_INSTALLED_USER_DECK_LIMIT
}

/** iOS `PiyokeyProAccessError` + `DeckMakerAuthorizationError`. */
class ProAccessRequiredException(message: String = "Deck Maker access required") : IllegalStateException(message)

/** iOS `DeckMakerPurchaseConstants`. */
object DeckMakerPurchaseConstants {
  const val LIFETIME_PRODUCT_ID = "app.piyokey.deckmaker.lifetime"
}

/** Play `Purchase.PurchaseState` reduced to what the entitlement rules need. */
enum class OwnedPurchaseState { PURCHASED, PENDING, UNSPECIFIED }

/** Play-independent snapshot of a `Purchase` so the rules are JVM-testable. */
data class OwnedPurchase(
  val productIds: List<String>,
  val state: OwnedPurchaseState,
  val isAcknowledged: Boolean,
  val purchaseToken: String,
)

/**
 * Entitlement rules (Play Billing counterpart of iOS verified-transaction checks):
 * only `PURCHASED` unlocks and is acknowledged; `PENDING` never unlocks; an authoritative query
 * without the product (refund/revoke) relocks; a failed query falls back to the offline cache.
 */
object ProEntitlementPolicy {
  fun unlocks(purchase: OwnedPurchase, productId: String = DeckMakerPurchaseConstants.LIFETIME_PRODUCT_ID) =
    productId in purchase.productIds && purchase.state == OwnedPurchaseState.PURCHASED

  fun needsAcknowledgement(purchase: OwnedPurchase, productId: String = DeckMakerPurchaseConstants.LIFETIME_PRODUCT_ID) =
    unlocks(purchase, productId) && !purchase.isAcknowledged

  fun isPending(purchases: List<OwnedPurchase>, productId: String = DeckMakerPurchaseConstants.LIFETIME_PRODUCT_ID) =
    purchases.any { productId in it.productIds && it.state == OwnedPurchaseState.PENDING }

  /**
   * @param queried owned purchases from an authoritative query, or `null` when the query failed
   *   (offline / billing unavailable).
   * @param cached last known entitlement stored for offline use.
   */
  fun resolveAccess(
    queried: List<OwnedPurchase>?,
    cached: Boolean,
    productId: String = DeckMakerPurchaseConstants.LIFETIME_PRODUCT_ID,
  ): Boolean = queried?.any { unlocks(it, productId) } ?: cached
}

/** iOS `DeckMakerPurchaseActivity`. */
enum class ProPurchaseActivity {
  IDLE, LOADING, PURCHASING, RESTORING;

  val isBusy: Boolean get() = this != IDLE
}
