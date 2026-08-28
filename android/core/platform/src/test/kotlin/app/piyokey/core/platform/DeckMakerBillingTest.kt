package app.piyokey.core.platform

import android.app.Activity
import app.piyokey.core.data.DeckMakerEntitlementCacheEntity
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeckMakerBillingTest {
  @Test
  fun purchasedOnlyGrantsAndAcknowledgesAccess() = runTest {
    val gateway = FakeGateway(
      purchases = listOf(purchase(DeckMakerPurchaseStatus.PURCHASED, acknowledged = false)),
    )
    val manager = DeckMakerBillingManager(gateway, FakeCache(), clock = { 42 })

    manager.prepare()

    assertTrue(manager.state.value.hasAccess)
    assertEquals(listOf("token"), gateway.acknowledged)
    manager.requireAccess()
  }

  @Test
  fun pendingNeverGrantsOrAcknowledges() = runTest {
    val gateway = FakeGateway(purchases = listOf(purchase(DeckMakerPurchaseStatus.PENDING)))
    val manager = DeckMakerBillingManager(gateway, FakeCache())

    manager.prepare()

    assertFalse(manager.state.value.hasAccess)
    assertTrue(gateway.acknowledged.isEmpty())
    assertFailsWith<DeckMakerAuthorizationException> { manager.requireAccess() }
  }

  @Test
  fun offlineStartupUsesLastVerifiedOneTimeEntitlement() = runTest {
    val cache = FakeCache(DeckMakerEntitlementCacheEntity(DECK_MAKER_PRODUCT_ID, true, 10))
    val manager = DeckMakerBillingManager(FakeGateway(failure = true), cache)

    manager.prepare()

    assertTrue(manager.state.value.hasAccess)
    assertTrue(manager.state.value.usingOfflineCache)
  }

  @Test
  fun successfulEmptyQueryRevokesCachedAccessWithoutDeletingUserData() = runTest {
    val cache = FakeCache(DeckMakerEntitlementCacheEntity(DECK_MAKER_PRODUCT_ID, true, 10))
    val manager = DeckMakerBillingManager(FakeGateway(), cache, clock = { 20 })

    manager.prepare()

    assertFalse(manager.state.value.hasAccess)
    assertEquals(false, cache.value?.isActive)
    assertEquals(20, cache.value?.lastVerifiedAtEpochMillis)
  }

  @Test
  fun explicitRestoreRequeriesAndReportsResult() = runTest {
    val gateway = FakeGateway()
    val manager = DeckMakerBillingManager(gateway, FakeCache())
    manager.prepare()
    gateway.purchases = listOf(purchase(DeckMakerPurchaseStatus.PURCHASED))

    assertTrue(manager.restore())
    assertEquals(DeckMakerBillingNotice.RESTORE_SUCCEEDED, manager.state.value.notice)
    assertTrue(gateway.queryCount >= 2)
  }

  private fun purchase(
    status: DeckMakerPurchaseStatus,
    acknowledged: Boolean = true,
  ) = DeckMakerPurchase(setOf(DECK_MAKER_PRODUCT_ID), "token", status, acknowledged)

  private class FakeCache(
    var value: DeckMakerEntitlementCacheEntity? = null,
  ) : DeckMakerEntitlementCacheStore {
    override suspend fun read(productId: String): DeckMakerEntitlementCacheEntity? = value
    override suspend fun write(productId: String, active: Boolean, verifiedAtEpochMillis: Long) {
      value = DeckMakerEntitlementCacheEntity(productId, active, verifiedAtEpochMillis)
    }
  }

  private class FakeGateway(
    var purchases: List<DeckMakerPurchase> = emptyList(),
    private val failure: Boolean = false,
  ) : DeckMakerBillingGateway {
    override val purchaseUpdates: Flow<List<DeckMakerPurchase>> = emptyFlow()
    val acknowledged = mutableListOf<String>()
    var queryCount = 0

    override suspend fun queryProduct(productId: String): DeckMakerProduct? {
      if (failure) error("offline")
      return DeckMakerProduct(productId, "My Deck Maker", "¥1,500")
    }

    override suspend fun queryPurchases(): List<DeckMakerPurchase> {
      queryCount += 1
      if (failure) error("offline")
      return purchases
    }

    override suspend fun launchPurchase(activity: Activity, productId: String): DeckMakerPurchaseOutcome =
      DeckMakerPurchaseOutcome.Completed(purchases)

    override suspend fun acknowledge(purchaseToken: String) {
      acknowledged += purchaseToken
    }

    override fun close() = Unit
  }
}
