package app.piyokey.android.platform.billing

import android.app.Activity
import androidx.annotation.StringRes
import app.piyokey.android.R
import app.piyokey.android.Services
import app.piyokey.android.data.settings.BoolPref
import app.piyokey.android.platform.analytics.AnalyticsEvent
import app.piyokey.android.platform.analytics.AnalyticsProperty
import app.piyokey.android.platform.analytics.Telemetry
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlin.coroutines.resume
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** iOS `DeckMakerProductDetails`. */
data class ProProduct(val productId: String, val displayName: String, val displayPrice: String)

/** iOS `DeckMakerPurchaseNotice` with Android string resources. */
enum class ProPurchaseNotice(@param:StringRes val titleRes: Int, @param:StringRes val messageRes: Int) {
  PURCHASE_PENDING(R.string.deck_maker_purchase_pending_title, R.string.deck_maker_purchase_pending_message),
  PURCHASE_FAILED(R.string.deck_maker_purchase_failed_title, R.string.deck_maker_purchase_failed_message),
  RESTORE_SUCCEEDED(R.string.deck_maker_restore_succeeded_title, R.string.deck_maker_restore_succeeded_message),
  NOTHING_TO_RESTORE(R.string.deck_maker_restore_empty_title, R.string.deck_maker_restore_empty_message),
  RESTORE_FAILED(R.string.deck_maker_restore_failed_title, R.string.deck_maker_restore_failed_message),
  PRODUCT_UNAVAILABLE(R.string.deck_maker_product_unavailable_title, R.string.deck_maker_product_unavailable_message),
}

/**
 * Deck Maker lifetime entitlement via Play Billing (iOS `DeckMakerPurchaseStore`).
 *
 * - Only `PURCHASED` unlocks, and it is acknowledged; `PENDING` shows a notice but never unlocks.
 * - Purchases are re-queried on [prepare] (start), [refreshOnForeground] and [restore].
 * - The last authoritative result is cached for offline launches; a later query without the
 *   product (refund/revocation) relocks creation. Existing user decks stay usable elsewhere.
 * - Without Play (emulator, sideload) the store reports [billingAvailable] = false and stays locked
 *   unless an earlier authoritative query cached access.
 */
object ProStore {
  private val cachedEntitlement = BoolPref("deck_maker.android_cached_entitlement", false)

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private val connectMutex = Mutex()

  private val accessState by lazy { MutableStateFlow(cachedEntitlement.value) }
  private val productState = MutableStateFlow<ProProduct?>(null)
  private val activityState = MutableStateFlow(ProPurchaseActivity.IDLE)
  private val noticeState = MutableStateFlow<ProPurchaseNotice?>(null)
  private val availableState = MutableStateFlow(true)

  val productId: String = DeckMakerPurchaseConstants.LIFETIME_PRODUCT_ID
  val hasAccess: StateFlow<Boolean> get() = accessState.asStateFlow()
  val product: StateFlow<ProProduct?> = productState.asStateFlow()
  val activity: StateFlow<ProPurchaseActivity> = activityState.asStateFlow()
  val notice: StateFlow<ProPurchaseNotice?> = noticeState.asStateFlow()
  val billingAvailable: StateFlow<Boolean> = availableState.asStateFlow()

  val displayPrice: String? get() = productState.value?.displayPrice
  val isBusy: Boolean get() = activityState.value.isBusy

  private var client: BillingClient? = null
  private var productDetails: ProductDetails? = null
  private var hasPrepared = false
  private var purchaseResult: CompletableDeferred<Pair<BillingResult, List<Purchase>?>>? = null

  private val purchasesListener = PurchasesUpdatedListener { result, purchases ->
    val pending = purchaseResult
    if (pending != null && !pending.isCompleted) {
      pending.complete(result to purchases)
    } else if (result.responseCode == BillingClient.BillingResponseCode.OK) {
      // Out-of-flow update (pending purchase completed, purchase from Play Store, family change).
      scope.launch { handleOutOfFlowUpdate(purchases.orEmpty()) }
    }
  }

  /** Throws unless the latest entitlement grants access. Gate only paid create/edit mutations. */
  fun requireAccess() {
    if (!accessState.value) throw ProAccessRequiredException()
  }

  fun dismissNotice() {
    noticeState.value = null
  }

  /** Loads product details and entitlement (iOS `prepare(forceReload:)`). */
  suspend fun prepare(forceReload: Boolean = false) {
    if (isBusy) return
    if (!forceReload && hasPrepared) {
      refreshEntitlement()
      return
    }
    activityState.value = ProPurchaseActivity.LOADING
    try {
      val details = loadProduct()
      productDetails = details
      productState.value = details?.toProduct()
      if (details == null) {
        noticeState.value = ProPurchaseNotice.PRODUCT_UNAVAILABLE
      } else if (noticeState.value == ProPurchaseNotice.PRODUCT_UNAVAILABLE) {
        noticeState.value = null
      }
      refreshEntitlement()
      if (accessState.value && noticeState.value == ProPurchaseNotice.PRODUCT_UNAVAILABLE) noticeState.value = null
      hasPrepared = true
    } finally {
      activityState.value = ProPurchaseActivity.IDLE
    }
  }

  /** Re-query when the app returns to the foreground (refunds, pending purchases completing). */
  fun refreshOnForeground() {
    if (!Services.isInstalled) return
    scope.launch { if (!isBusy) refreshEntitlement() }
  }

  /** Launches the Play purchase sheet. Returns true once access is granted. */
  suspend fun purchase(activity: Activity): Boolean {
    if (isBusy) return false
    if (accessState.value) return true
    if (productDetails == null) {
      productDetails = loadProduct()
      productState.value = productDetails?.toProduct()
    }
    val details = productDetails ?: run {
      noticeState.value = ProPurchaseNotice.PRODUCT_UNAVAILABLE
      return false
    }
    val billing = connectedClient() ?: run {
      noticeState.value = ProPurchaseNotice.PRODUCT_UNAVAILABLE
      return false
    }

    capturePurchaseState("started")
    activityState.value = ProPurchaseActivity.PURCHASING
    try {
      val offerToken = details.oneTimePurchaseOfferDetails?.offerToken
      val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
        .setProductDetails(details)
        .apply { if (offerToken != null) setOfferToken(offerToken) }
        .build()
      val deferred = CompletableDeferred<Pair<BillingResult, List<Purchase>?>>()
      purchaseResult = deferred
      val launch = billing.launchBillingFlow(
        activity,
        BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(productParams)).build(),
      )
      if (launch.responseCode != BillingClient.BillingResponseCode.OK) {
        purchaseResult = null
        return handleLaunchFailure(launch.responseCode)
      }
      val (result, purchases) = deferred.await()
      purchaseResult = null
      return when (result.responseCode) {
        BillingClient.BillingResponseCode.OK -> handlePurchased(purchases.orEmpty().map { it.toOwned() })
        BillingClient.BillingResponseCode.USER_CANCELED -> {
          noticeState.value = null
          capturePurchaseState("cancelled")
          false
        }
        BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
          refreshEntitlement()
          if (!accessState.value) noticeState.value = ProPurchaseNotice.PURCHASE_FAILED
          capturePurchaseState(if (accessState.value) "completed" else "failed")
          accessState.value
        }
        else -> {
          noticeState.value = ProPurchaseNotice.PURCHASE_FAILED
          capturePurchaseState("failed")
          false
        }
      }
    } finally {
      purchaseResult = null
      activityState.value = ProPurchaseActivity.IDLE
    }
  }

  /** Re-queries Play (iOS `AppStore.sync()` + entitlement refresh). */
  suspend fun restore(): Boolean {
    if (isBusy) return false
    activityState.value = ProPurchaseActivity.RESTORING
    try {
      val queried = queryOwned()
      if (queried == null) {
        noticeState.value = ProPurchaseNotice.RESTORE_FAILED
        capturePurchaseState("failed")
        return false
      }
      applyQueried(queried)
      noticeState.value = if (accessState.value) ProPurchaseNotice.RESTORE_SUCCEEDED else ProPurchaseNotice.NOTHING_TO_RESTORE
      if (accessState.value) capturePurchaseState("restored")
      return accessState.value
    } finally {
      activityState.value = ProPurchaseActivity.IDLE
    }
  }

  // Internals -------------------------------------------------------------------------------

  private suspend fun handlePurchased(purchases: List<OwnedPurchase>): Boolean {
    val owned = purchases.filter { productId in it.productIds }
    if (owned.any { ProEntitlementPolicy.unlocks(it, productId) }) {
      owned.filter { ProEntitlementPolicy.needsAcknowledgement(it, productId) }.forEach { acknowledge(it) }
      setAccess(true)
      noticeState.value = null
      capturePurchaseState("completed")
      return true
    }
    if (ProEntitlementPolicy.isPending(owned, productId)) {
      noticeState.value = ProPurchaseNotice.PURCHASE_PENDING
      capturePurchaseState("pending")
      return false
    }
    refreshEntitlement()
    if (!accessState.value) noticeState.value = ProPurchaseNotice.PURCHASE_FAILED
    capturePurchaseState(if (accessState.value) "completed" else "failed")
    return accessState.value
  }

  private suspend fun handleOutOfFlowUpdate(purchases: List<Purchase>) {
    val owned = purchases.map { it.toOwned() }.filter { productId in it.productIds }
    if (owned.any { ProEntitlementPolicy.unlocks(it, productId) }) {
      owned.filter { ProEntitlementPolicy.needsAcknowledgement(it, productId) }.forEach { acknowledge(it) }
      setAccess(true)
      if (noticeState.value == ProPurchaseNotice.PURCHASE_PENDING) noticeState.value = null
    } else {
      refreshEntitlement()
    }
  }

  private fun handleLaunchFailure(code: Int): Boolean {
    noticeState.value = when (code) {
      BillingClient.BillingResponseCode.USER_CANCELED -> null
      BillingClient.BillingResponseCode.BILLING_UNAVAILABLE,
      BillingClient.BillingResponseCode.ITEM_UNAVAILABLE,
      BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED,
      -> ProPurchaseNotice.PRODUCT_UNAVAILABLE
      else -> ProPurchaseNotice.PURCHASE_FAILED
    }
    capturePurchaseState(if (code == BillingClient.BillingResponseCode.USER_CANCELED) "cancelled" else "failed")
    return false
  }

  private suspend fun refreshEntitlement() {
    applyQueried(queryOwned())
  }

  private suspend fun applyQueried(queried: List<OwnedPurchase>?) {
    queried?.filter { ProEntitlementPolicy.needsAcknowledgement(it, productId) }?.forEach { acknowledge(it) }
    val access = ProEntitlementPolicy.resolveAccess(queried, cachedEntitlement.value, productId)
    if (queried != null) cachedEntitlement.value = access
    accessState.value = access
  }

  private fun setAccess(access: Boolean) {
    cachedEntitlement.value = access
    accessState.value = access
  }

  private suspend fun acknowledge(purchase: OwnedPurchase) {
    val billing = connectedClient() ?: return
    val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
    suspendCancellableCoroutine { continuation ->
      billing.acknowledgePurchase(params) { continuation.resume(Unit) }
    }
  }

  /** Owned INAPP purchases, or null when the query could not be made. */
  private suspend fun queryOwned(): List<OwnedPurchase>? {
    val billing = connectedClient() ?: return null
    val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
    return suspendCancellableCoroutine { continuation ->
      billing.queryPurchasesAsync(params) { result, purchases ->
        continuation.resume(
          if (result.responseCode == BillingClient.BillingResponseCode.OK) purchases.map { it.toOwned() } else null,
        )
      }
    }
  }

  private suspend fun loadProduct(): ProductDetails? {
    val billing = connectedClient() ?: return null
    val params = QueryProductDetailsParams.newBuilder()
      .setProductList(
        listOf(
          QueryProductDetailsParams.Product.newBuilder()
            .setProductId(productId)
            .setProductType(BillingClient.ProductType.INAPP)
            .build(),
        ),
      )
      .build()
    return suspendCancellableCoroutine { continuation ->
      billing.queryProductDetailsAsync(params) { result, details ->
        continuation.resume(
          if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            details.productDetailsList.firstOrNull { it.productId == productId }
          } else {
            null
          },
        )
      }
    }
  }

  private suspend fun connectedClient(): BillingClient? = connectMutex.withLock {
    if (!Services.isInstalled) return@withLock null
    val existing = client
    if (existing != null && existing.connectionState == BillingClient.ConnectionState.CONNECTED) return@withLock existing
    val billing = existing ?: runCatching {
      BillingClient.newBuilder(Services.context)
        .setListener(purchasesListener)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()
    }.getOrNull()?.also { client = it }
    if (billing == null) {
      availableState.value = false
      return@withLock null
    }
    val connected = suspendCancellableCoroutine { continuation ->
      var resumed = false
      billing.startConnection(object : BillingClientStateListener {
        override fun onBillingSetupFinished(result: BillingResult) {
          if (resumed) return
          resumed = true
          continuation.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
        }

        override fun onBillingServiceDisconnected() {
          if (resumed) return
          resumed = true
          continuation.resume(false)
        }
      })
    }
    availableState.value = connected
    if (connected) billing else null
  }

  private fun capturePurchaseState(state: String) {
    Telemetry.capture(AnalyticsEvent.PURCHASE_FLOW, mapOf(AnalyticsProperty.PURCHASE_STATE to state))
  }

  private fun ProductDetails.toProduct() = ProProduct(
    productId = productId,
    displayName = name,
    displayPrice = oneTimePurchaseOfferDetails?.formattedPrice.orEmpty(),
  )

  private fun Purchase.toOwned() = OwnedPurchase(
    productIds = products,
    state = when (purchaseState) {
      Purchase.PurchaseState.PURCHASED -> OwnedPurchaseState.PURCHASED
      Purchase.PurchaseState.PENDING -> OwnedPurchaseState.PENDING
      else -> OwnedPurchaseState.UNSPECIFIED
    },
    isAcknowledged = isAcknowledged,
    purchaseToken = purchaseToken,
  )
}
