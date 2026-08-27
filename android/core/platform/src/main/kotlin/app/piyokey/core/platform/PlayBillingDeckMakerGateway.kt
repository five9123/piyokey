package app.piyokey.core.platform

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine

class PlayBillingDeckMakerGateway(context: Context) : DeckMakerBillingGateway {
  private val pendingPurchase = AtomicReference<CompletableDeferred<DeckMakerPurchaseOutcome>?>(null)
  private val mutablePurchaseUpdates = MutableSharedFlow<List<DeckMakerPurchase>>(extraBufferCapacity = 8)
  override val purchaseUpdates: Flow<List<DeckMakerPurchase>> = mutablePurchaseUpdates.asSharedFlow()
  private val billingClient = BillingClient.newBuilder(context.applicationContext)
    .setListener { result, purchases ->
      val mapped = purchases.orEmpty().map(::mapPurchase)
      if (result.responseCode == BillingClient.BillingResponseCode.OK) {
        mutablePurchaseUpdates.tryEmit(mapped)
      }
      pendingPurchase.getAndSet(null)?.complete(result.toOutcome(mapped))
    }
    .enablePendingPurchases(
      PendingPurchasesParams.newBuilder().enableOneTimeProducts().build(),
    )
    .enableAutoServiceReconnection()
    .build()

  override suspend fun queryProduct(productId: String): DeckMakerProduct? {
    val details = queryProductDetails(productId) ?: return null
    val offer = details.oneTimePurchaseOfferDetailsList?.firstOrNull() ?: return null
    return DeckMakerProduct(
      productId = details.productId,
      displayName = details.name,
      formattedPrice = offer.formattedPrice,
    )
  }

  override suspend fun queryPurchases(): List<DeckMakerPurchase> {
    connect()
    return suspendCancellableCoroutine { continuation ->
      val params = QueryPurchasesParams.newBuilder()
        .setProductType(BillingClient.ProductType.INAPP)
        .build()
      billingClient.queryPurchasesAsync(params) { result, purchases ->
        if (!continuation.isActive) return@queryPurchasesAsync
        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
          continuation.resume(purchases.map(::mapPurchase))
        } else {
          continuation.resumeWithException(BillingGatewayException(result.responseCode))
        }
      }
    }
  }

  override suspend fun launchPurchase(
    activity: Activity,
    productId: String,
  ): DeckMakerPurchaseOutcome {
    connect()
    // ProductDetails is intentionally re-queried; Google warns against stale cached instances.
    val details = queryProductDetails(productId)
      ?: return DeckMakerPurchaseOutcome.Failed(BillingClient.BillingResponseCode.ITEM_UNAVAILABLE)
    val offer = details.oneTimePurchaseOfferDetailsList?.firstOrNull()
      ?: return DeckMakerPurchaseOutcome.Failed(BillingClient.BillingResponseCode.ITEM_UNAVAILABLE)
    val productParamsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
      .setProductDetails(details)
    offer.offerToken?.let(productParamsBuilder::setOfferToken)
    val productParams = productParamsBuilder.build()
    val deferred = CompletableDeferred<DeckMakerPurchaseOutcome>()
    if (!pendingPurchase.compareAndSet(null, deferred)) {
      return DeckMakerPurchaseOutcome.Failed(BillingClient.BillingResponseCode.DEVELOPER_ERROR)
    }
    val launchResult = billingClient.launchBillingFlow(
      activity,
      BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(productParams)).build(),
    )
    if (launchResult.responseCode != BillingClient.BillingResponseCode.OK) {
      pendingPurchase.compareAndSet(deferred, null)
      return launchResult.toOutcome(emptyList())
    }
    return try {
      deferred.await()
    } finally {
      pendingPurchase.compareAndSet(deferred, null)
    }
  }

  override suspend fun acknowledge(purchaseToken: String) {
    connect()
    suspendCancellableCoroutine { continuation ->
      val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchaseToken).build()
      billingClient.acknowledgePurchase(params) { result ->
        if (!continuation.isActive) return@acknowledgePurchase
        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
          continuation.resume(Unit)
        } else {
          continuation.resumeWithException(BillingGatewayException(result.responseCode))
        }
      }
    }
  }

  override fun close() {
    pendingPurchase.getAndSet(null)?.cancel()
    billingClient.endConnection()
  }

  private suspend fun queryProductDetails(productId: String): ProductDetails? {
    connect()
    return suspendCancellableCoroutine { continuation ->
      val product = QueryProductDetailsParams.Product.newBuilder()
        .setProductId(productId)
        .setProductType(BillingClient.ProductType.INAPP)
        .build()
      billingClient.queryProductDetailsAsync(
        QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build(),
      ) { result, queryResult ->
        if (!continuation.isActive) return@queryProductDetailsAsync
        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
          continuation.resume(queryResult.productDetailsList.firstOrNull())
        } else {
          continuation.resumeWithException(BillingGatewayException(result.responseCode))
        }
      }
    }
  }

  private suspend fun connect() {
    if (billingClient.isReady) return
    suspendCancellableCoroutine { continuation ->
      billingClient.startConnection(object : BillingClientStateListener {
        override fun onBillingSetupFinished(result: BillingResult) {
          if (!continuation.isActive) return
          if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            continuation.resume(Unit)
          } else {
            continuation.resumeWithException(BillingGatewayException(result.responseCode))
          }
        }

        override fun onBillingServiceDisconnected() {
          // Automatic service reconnection handles subsequent operations.
        }
      })
    }
  }

  private fun mapPurchase(purchase: com.android.billingclient.api.Purchase): DeckMakerPurchase =
    DeckMakerPurchase(
      productIds = purchase.products.toSet(),
      purchaseToken = purchase.purchaseToken,
      status = when (purchase.purchaseState) {
        com.android.billingclient.api.Purchase.PurchaseState.PURCHASED -> DeckMakerPurchaseStatus.PURCHASED
        com.android.billingclient.api.Purchase.PurchaseState.PENDING -> DeckMakerPurchaseStatus.PENDING
        else -> DeckMakerPurchaseStatus.UNSPECIFIED
      },
      acknowledged = purchase.isAcknowledged,
    )

  private fun BillingResult.toOutcome(purchases: List<DeckMakerPurchase>): DeckMakerPurchaseOutcome =
    when (responseCode) {
      BillingClient.BillingResponseCode.OK -> when {
        purchases.any { it.status == DeckMakerPurchaseStatus.PURCHASED } ->
          DeckMakerPurchaseOutcome.Completed(purchases)
        purchases.any { it.status == DeckMakerPurchaseStatus.PENDING } ->
          DeckMakerPurchaseOutcome.Pending
        else -> DeckMakerPurchaseOutcome.Failed(responseCode)
      }
      BillingClient.BillingResponseCode.USER_CANCELED -> DeckMakerPurchaseOutcome.Cancelled
      else -> DeckMakerPurchaseOutcome.Failed(responseCode)
    }
}

data class BillingGatewayException(val responseCode: Int) :
  Exception("Google Play Billing failed with response code $responseCode")
