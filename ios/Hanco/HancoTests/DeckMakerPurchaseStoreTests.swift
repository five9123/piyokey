import XCTest

@testable import Hanco

@MainActor
final class DeckMakerPurchaseStoreTests: XCTestCase {
  func testLifetimeProductContractIsStable() {
    XCTAssertEqual(
      DeckMakerPurchaseConstants.lifetimeProductID,
      "app.piyokey.deckmaker.lifetime"
    )
  }

  func testPrepareLoadsLocalizedPriceAndCurrentEntitlement() async {
    let storefront = MockDeckMakerStorefront()
    storefront.product = .fixture(displayPrice: "¥1,500")
    storefront.entitlements = [.active]
    let store = DeckMakerPurchaseStore(
      storefront: storefront,
      observesTransactions: false
    )

    await store.prepare()

    XCTAssertEqual(store.displayPrice, "¥1,500")
    XCTAssertTrue(store.hasAccess)
    XCTAssertEqual(store.activity, .idle)
    XCTAssertEqual(storefront.loadedProductIDs, [DeckMakerPurchaseConstants.lifetimeProductID])
    XCTAssertEqual(storefront.entitlementProductIDs, [DeckMakerPurchaseConstants.lifetimeProductID])
  }

  func testPrepareFailsClosedForRevokedAndUnrelatedEntitlements() async {
    let storefront = MockDeckMakerStorefront()
    storefront.entitlements = [
      .revoked,
      DeckMakerEntitlement(productID: "another.product", isActive: true),
    ]
    let store = DeckMakerPurchaseStore(
      storefront: storefront,
      observesTransactions: false
    )

    await store.prepare()

    XCTAssertFalse(store.hasAccess)
    XCTAssertThrowsError(try store.requireAccess()) { error in
      XCTAssertEqual(error as? DeckMakerAuthorizationError, .accessRequired)
    }
  }

  func testVerifiedPurchaseUnlocksDeckMaker() async {
    let storefront = MockDeckMakerStorefront()
    storefront.purchaseResult = .purchased(.active)
    let store = DeckMakerPurchaseStore(
      storefront: storefront,
      observesTransactions: false
    )

    let purchased = await store.purchase()

    XCTAssertTrue(purchased)
    XCTAssertTrue(store.hasAccess)
    XCTAssertNoThrow(try store.requireAccess())
    XCTAssertNil(store.notice)
    XCTAssertEqual(storefront.purchaseProductIDs, [DeckMakerPurchaseConstants.lifetimeProductID])
  }

  func testPendingAndCancelledPurchasesDoNotUnlock() async {
    let pendingStorefront = MockDeckMakerStorefront()
    pendingStorefront.purchaseResult = .pending
    let pendingStore = DeckMakerPurchaseStore(
      storefront: pendingStorefront,
      observesTransactions: false
    )

    let pendingPurchase = await pendingStore.purchase()
    XCTAssertFalse(pendingPurchase)
    XCTAssertFalse(pendingStore.hasAccess)
    XCTAssertEqual(pendingStore.notice, .purchasePending)

    let cancelledStorefront = MockDeckMakerStorefront()
    cancelledStorefront.purchaseResult = .userCancelled
    let cancelledStore = DeckMakerPurchaseStore(
      storefront: cancelledStorefront,
      observesTransactions: false
    )

    let cancelledPurchase = await cancelledStore.purchase()
    XCTAssertFalse(cancelledPurchase)
    XCTAssertFalse(cancelledStore.hasAccess)
    XCTAssertNil(cancelledStore.notice)
  }

  func testPurchaseFailureIsGenericAndFailsClosed() async {
    let storefront = MockDeckMakerStorefront()
    storefront.purchaseError = MockError.expected
    let store = DeckMakerPurchaseStore(
      storefront: storefront,
      observesTransactions: false
    )

    let purchased = await store.purchase()
    XCTAssertFalse(purchased)
    XCTAssertFalse(store.hasAccess)
    XCTAssertEqual(store.notice, .purchaseFailed)
  }

  func testRestoreSynchronizesThenRefreshesCurrentEntitlements() async {
    let storefront = MockDeckMakerStorefront()
    storefront.entitlements = []
    storefront.synchronizeHandler = {
      storefront.entitlements = [.active]
    }
    let store = DeckMakerPurchaseStore(
      storefront: storefront,
      observesTransactions: false
    )

    let restored = await store.restore()

    XCTAssertTrue(restored)
    XCTAssertTrue(store.hasAccess)
    XCTAssertEqual(store.notice, .restoreSucceeded)
    XCTAssertEqual(storefront.synchronizeCallCount, 1)
  }

  func testRestoreWithNoPurchaseStaysLocked() async {
    let storefront = MockDeckMakerStorefront()
    let store = DeckMakerPurchaseStore(
      storefront: storefront,
      observesTransactions: false
    )

    let restored = await store.restore()
    XCTAssertFalse(restored)
    XCTAssertFalse(store.hasAccess)
    XCTAssertEqual(store.notice, .nothingToRestore)
  }

  func testRestoreFailureStaysLocked() async {
    let storefront = MockDeckMakerStorefront()
    storefront.synchronizeError = MockError.expected
    let store = DeckMakerPurchaseStore(
      storefront: storefront,
      observesTransactions: false
    )

    let restored = await store.restore()
    XCTAssertFalse(restored)
    XCTAssertFalse(store.hasAccess)
    XCTAssertEqual(store.notice, .restoreFailed)
  }

  func testTransactionUpdateUnlocksAndRevocationLocksAgain() async {
    let storefront = MockDeckMakerStorefront()
    let store = DeckMakerPurchaseStore(storefront: storefront)
    await waitForUpdateObserver(storefront)

    storefront.sendUpdate(.active)
    await waitUntil { store.hasAccess }
    XCTAssertTrue(store.hasAccess)

    storefront.sendUpdate(.revoked)
    await waitUntil { !store.hasAccess }
    XCTAssertFalse(store.hasAccess)
    XCTAssertThrowsError(try store.requireAccess())
  }

  func testProductUnavailableCanBeRetried() async {
    let storefront = MockDeckMakerStorefront()
    storefront.product = nil
    let store = DeckMakerPurchaseStore(
      storefront: storefront,
      observesTransactions: false
    )

    await store.prepare()
    XCTAssertEqual(store.notice, .productUnavailable)
    XCTAssertNil(store.product)

    store.dismissNotice()
    storefront.product = .fixture(displayPrice: "$9.99")
    await store.prepare(forceReload: true)

    XCTAssertEqual(store.displayPrice, "$9.99")
    XCTAssertNil(store.notice)
  }

  func testPaywallNoticesAreLocalizedInEverySupportedLanguage() {
    let notices: [DeckMakerPurchaseNotice] = [
      .purchasePending,
      .purchaseFailed,
      .restoreSucceeded,
      .nothingToRestore,
      .restoreFailed,
      .productUnavailable,
    ]

    for language in AppLanguage.allCases {
      for notice in notices {
        XCTAssertNotEqual(
          AppLocalization.string(notice.titleLocalizationKey, language: language),
          notice.titleLocalizationKey
        )
        XCTAssertNotEqual(
          AppLocalization.string(notice.messageLocalizationKey, language: language),
          notice.messageLocalizationKey
        )
      }
    }
  }

  func testPaywallLegalLinksUseHTTPS() {
    XCTAssertEqual(DeckMakerLegalLinks.privacyPolicy, AppReleaseLinks.privacyPolicy)
    XCTAssertEqual(DeckMakerLegalLinks.termsOfUse.scheme, "https")
    XCTAssertEqual(DeckMakerLegalLinks.privacyPolicy.scheme, "https")
  }

  private func waitForUpdateObserver(
    _ storefront: MockDeckMakerStorefront,
    iterations: Int = 100
  ) async {
    for _ in 0..<iterations where !storefront.hasUpdateObserver {
      await Task.yield()
    }
    XCTAssertTrue(storefront.hasUpdateObserver)
  }

  private func waitUntil(
    iterations: Int = 100,
    _ condition: @escaping @MainActor () -> Bool
  ) async {
    for _ in 0..<iterations {
      if condition() { return }
      await Task.yield()
    }
    XCTFail("Condition was not met")
  }
}

@MainActor
private final class MockDeckMakerStorefront: DeckMakerStorefront {
  var product: DeckMakerProductDetails? = .fixture()
  var entitlements: [DeckMakerEntitlement] = []
  var purchaseResult: DeckMakerPurchaseResult = .userCancelled
  var purchaseError: Error?
  var synchronizeError: Error?
  var synchronizeHandler: (() -> Void)?

  private(set) var loadedProductIDs: [String] = []
  private(set) var entitlementProductIDs: [String] = []
  private(set) var purchaseProductIDs: [String] = []
  private(set) var synchronizeCallCount = 0
  private(set) var hasUpdateObserver = false

  private var updateContinuation: AsyncStream<DeckMakerEntitlement>.Continuation?

  func loadProduct(productID: String) async throws -> DeckMakerProductDetails? {
    loadedProductIDs.append(productID)
    return product
  }

  func currentEntitlements(productID: String) async -> [DeckMakerEntitlement] {
    entitlementProductIDs.append(productID)
    return entitlements
  }

  func purchase(productID: String) async throws -> DeckMakerPurchaseResult {
    purchaseProductIDs.append(productID)
    if let purchaseError { throw purchaseError }
    return purchaseResult
  }

  func synchronize() async throws {
    synchronizeCallCount += 1
    if let synchronizeError { throw synchronizeError }
    synchronizeHandler?()
  }

  func entitlementUpdates(productID: String) -> AsyncStream<DeckMakerEntitlement> {
    AsyncStream { continuation in
      updateContinuation = continuation
      hasUpdateObserver = true
    }
  }

  func sendUpdate(_ entitlement: DeckMakerEntitlement) {
    updateContinuation?.yield(entitlement)
  }
}

extension DeckMakerProductDetails {
  fileprivate static func fixture(displayPrice: String = "¥1,500") -> DeckMakerProductDetails {
    DeckMakerProductDetails(
      productID: DeckMakerPurchaseConstants.lifetimeProductID,
      displayName: "Deck Maker",
      displayPrice: displayPrice
    )
  }
}

extension DeckMakerEntitlement {
  fileprivate static let active = DeckMakerEntitlement(
    productID: DeckMakerPurchaseConstants.lifetimeProductID,
    isActive: true
  )
  fileprivate static let revoked = DeckMakerEntitlement(
    productID: DeckMakerPurchaseConstants.lifetimeProductID,
    isActive: false
  )
}

private enum MockError: Error {
  case expected
}
