package app.piyokey.core.platform

import android.app.Activity
import app.piyokey.core.data.DeckMakerEntitlementCache
import app.piyokey.core.data.DeckMakerEntitlementCacheEntity
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

const val DECK_MAKER_PRODUCT_ID: String = "app.piyokey.deckmaker.lifetime"

data class DeckMakerProduct(
  val productId: String,
  val displayName: String,
  val formattedPrice: String,
)

enum class DeckMakerPurchaseStatus { PURCHASED, PENDING, UNSPECIFIED }

data class DeckMakerPurchase(
  val productIds: Set<String>,
  val purchaseToken: String,
  val status: DeckMakerPurchaseStatus,
  val acknowledged: Boolean,
)

sealed interface DeckMakerPurchaseOutcome {
  data class Completed(val purchases: List<DeckMakerPurchase>) : DeckMakerPurchaseOutcome
  data object Pending : DeckMakerPurchaseOutcome
  data object Cancelled : DeckMakerPurchaseOutcome
  data class Failed(val responseCode: Int) : DeckMakerPurchaseOutcome
}

interface DeckMakerBillingGateway {
  val purchaseUpdates: Flow<List<DeckMakerPurchase>>
  suspend fun queryProduct(productId: String): DeckMakerProduct?
  suspend fun queryPurchases(): List<DeckMakerPurchase>
  suspend fun launchPurchase(activity: Activity, productId: String): DeckMakerPurchaseOutcome
  suspend fun acknowledge(purchaseToken: String)
  fun close()
}

interface DeckMakerEntitlementCacheStore {
  suspend fun read(productId: String): DeckMakerEntitlementCacheEntity?
  suspend fun write(productId: String, active: Boolean, verifiedAtEpochMillis: Long)
}

class RoomDeckMakerEntitlementCacheStore(
  private val cache: DeckMakerEntitlementCache,
) : DeckMakerEntitlementCacheStore {
  override suspend fun read(productId: String): DeckMakerEntitlementCacheEntity? = cache.read(productId)

  override suspend fun write(productId: String, active: Boolean, verifiedAtEpochMillis: Long) {
    cache.write(productId, active, verifiedAtEpochMillis)
  }
}

enum class DeckMakerBillingActivity { IDLE, LOADING, PURCHASING, RESTORING }

enum class DeckMakerBillingNotice {
  PURCHASE_PENDING,
  PURCHASE_FAILED,
  RESTORE_SUCCEEDED,
  NOTHING_TO_RESTORE,
  RESTORE_FAILED,
  PRODUCT_UNAVAILABLE,
}

data class DeckMakerBillingState(
  val product: DeckMakerProduct? = null,
  val hasAccess: Boolean = false,
  val usingOfflineCache: Boolean = false,
  val activity: DeckMakerBillingActivity = DeckMakerBillingActivity.IDLE,
  val notice: DeckMakerBillingNotice? = null,
)

class DeckMakerAuthorizationException : IllegalStateException("Deck Maker access is required.")

/**
 * Client-only Play entitlement coordinator. A successful query is authoritative;
 * a failed/offline query retains only the last successfully verified one-time purchase.
 */
class DeckMakerBillingManager(
  private val gateway: DeckMakerBillingGateway,
  private val cache: DeckMakerEntitlementCacheStore,
  private val clock: () -> Long = System::currentTimeMillis,
  val productId: String = DECK_MAKER_PRODUCT_ID,
) {
  private val observerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private val operationMutex = Mutex()
  private val prepared = AtomicBoolean(false)
  private val mutableState = MutableStateFlow(DeckMakerBillingState())
  val state: StateFlow<DeckMakerBillingState> = mutableState.asStateFlow()

  init {
    observerScope.launch {
      gateway.purchaseUpdates.collect { purchases ->
        operationMutex.withLock {
          runCatching { applySuccessfulPurchaseQuery(purchases) }
        }
      }
    }
  }

  suspend fun prepare(forceReload: Boolean = false) = operationMutex.withLock {
    if (!prepared.get()) {
      val cached = cache.read(productId)
      if (cached?.isActive == true) {
        mutableState.value = mutableState.value.copy(hasAccess = true, usingOfflineCache = true)
      }
    }
    if (prepared.get() && !forceReload) {
      refreshEntitlement(activity = DeckMakerBillingActivity.LOADING, restore = false)
      return@withLock
    }
    mutableState.value = mutableState.value.copy(activity = DeckMakerBillingActivity.LOADING)
    try {
      val product = gateway.queryProduct(productId)
      mutableState.value = mutableState.value.copy(
        product = product,
        notice = if (product == null && !mutableState.value.hasAccess) {
          DeckMakerBillingNotice.PRODUCT_UNAVAILABLE
        } else null,
      )
      queryAndApplyPurchases()
      prepared.set(true)
    } catch (error: CancellationException) {
      throw error
    } catch (_: Exception) {
      // Offline/cache access remains available; product metadata is not cached.
      if (!mutableState.value.hasAccess) {
        mutableState.value = mutableState.value.copy(notice = DeckMakerBillingNotice.PRODUCT_UNAVAILABLE)
      }
    } finally {
      mutableState.value = mutableState.value.copy(activity = DeckMakerBillingActivity.IDLE)
    }
  }

  suspend fun onForeground() {
    prepare(forceReload = prepared.get())
  }

  suspend fun purchase(activity: Activity): Boolean = operationMutex.withLock {
    if (mutableState.value.hasAccess) return@withLock true
    mutableState.value = mutableState.value.copy(activity = DeckMakerBillingActivity.PURCHASING)
    try {
      when (val outcome = gateway.launchPurchase(activity, productId)) {
        is DeckMakerPurchaseOutcome.Completed -> {
          applySuccessfulPurchaseQuery(outcome.purchases)
          val granted = mutableState.value.hasAccess
          mutableState.value = mutableState.value.copy(
            notice = if (granted) null else DeckMakerBillingNotice.PURCHASE_FAILED,
          )
          granted
        }
        DeckMakerPurchaseOutcome.Pending -> {
          mutableState.value = mutableState.value.copy(notice = DeckMakerBillingNotice.PURCHASE_PENDING)
          false
        }
        DeckMakerPurchaseOutcome.Cancelled -> {
          mutableState.value = mutableState.value.copy(notice = null)
          false
        }
        is DeckMakerPurchaseOutcome.Failed -> {
          mutableState.value = mutableState.value.copy(notice = DeckMakerBillingNotice.PURCHASE_FAILED)
          false
        }
      }
    } catch (error: CancellationException) {
      throw error
    } catch (_: Exception) {
      mutableState.value = mutableState.value.copy(notice = DeckMakerBillingNotice.PURCHASE_FAILED)
      false
    } finally {
      mutableState.value = mutableState.value.copy(activity = DeckMakerBillingActivity.IDLE)
    }
  }

  suspend fun restore(): Boolean = operationMutex.withLock {
    refreshEntitlement(activity = DeckMakerBillingActivity.RESTORING, restore = true)
  }

  fun dismissNotice() {
    mutableState.value = mutableState.value.copy(notice = null)
  }

  fun requireAccess() {
    if (!mutableState.value.hasAccess) throw DeckMakerAuthorizationException()
  }

  fun close() {
    observerScope.cancel()
    gateway.close()
  }

  private suspend fun refreshEntitlement(
    activity: DeckMakerBillingActivity,
    restore: Boolean,
  ): Boolean {
    mutableState.value = mutableState.value.copy(activity = activity)
    return try {
      queryAndApplyPurchases()
      val granted = mutableState.value.hasAccess
      if (restore) {
        mutableState.value = mutableState.value.copy(
          notice = if (granted) DeckMakerBillingNotice.RESTORE_SUCCEEDED
          else DeckMakerBillingNotice.NOTHING_TO_RESTORE,
        )
      }
      granted
    } catch (error: CancellationException) {
      throw error
    } catch (_: Exception) {
      if (restore) {
        mutableState.value = mutableState.value.copy(notice = DeckMakerBillingNotice.RESTORE_FAILED)
      }
      mutableState.value.hasAccess
    } finally {
      mutableState.value = mutableState.value.copy(activity = DeckMakerBillingActivity.IDLE)
    }
  }

  private suspend fun queryAndApplyPurchases() {
    applySuccessfulPurchaseQuery(gateway.queryPurchases())
  }

  private suspend fun applySuccessfulPurchaseQuery(purchases: List<DeckMakerPurchase>) {
    val matching = purchases.filter { productId in it.productIds }
    matching.filter { it.status == DeckMakerPurchaseStatus.PURCHASED && !it.acknowledged }
      .forEach { purchase -> runCatching { gateway.acknowledge(purchase.purchaseToken) } }
    val active = matching.any { it.status == DeckMakerPurchaseStatus.PURCHASED }
    cache.write(productId, active, clock())
    mutableState.value = mutableState.value.copy(hasAccess = active, usingOfflineCache = false)
  }
}
