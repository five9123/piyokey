import Combine
import Foundation
import StoreKit

enum DeckMakerPurchaseConstants {
  static let lifetimeProductID = "app.piyokey.deckmaker.lifetime"
}

struct DeckMakerProductDetails: Equatable, Sendable {
  let productID: String
  let displayName: String
  let displayPrice: String
}

struct DeckMakerEntitlement: Equatable, Sendable {
  let productID: String
  let isActive: Bool
}

enum DeckMakerPurchaseResult: Equatable, Sendable {
  case purchased(DeckMakerEntitlement)
  case pending
  case userCancelled
}

enum DeckMakerStorefrontError: Error, Equatable {
  case productUnavailable
  case failedVerification
  case unexpectedPurchaseResult
}

enum DeckMakerAuthorizationError: Error, Equatable {
  case accessRequired
}

/// StoreKit is kept behind this protocol so entitlement and restore behavior can be
/// tested without an App Store account or a StoreKit configuration file.
@MainActor
protocol DeckMakerStorefront: AnyObject {
  func loadProduct(productID: String) async throws -> DeckMakerProductDetails?
  func currentEntitlements(productID: String) async -> [DeckMakerEntitlement]
  func purchase(productID: String) async throws -> DeckMakerPurchaseResult
  func synchronize() async throws
  func entitlementUpdates(productID: String) -> AsyncStream<DeckMakerEntitlement>
}

@MainActor
final class StoreKitDeckMakerStorefront: DeckMakerStorefront {
  private var productsByID: [String: StoreKit.Product] = [:]

  func loadProduct(productID: String) async throws -> DeckMakerProductDetails? {
    let products = try await StoreKit.Product.products(for: [productID])
    guard let product = products.first(where: { $0.id == productID }) else { return nil }
    productsByID[productID] = product
    return DeckMakerProductDetails(
      productID: product.id,
      displayName: product.displayName,
      displayPrice: product.displayPrice
    )
  }

  func currentEntitlements(productID: String) async -> [DeckMakerEntitlement] {
    var entitlements: [DeckMakerEntitlement] = []
    for await result in StoreKit.Transaction.currentEntitlements {
      guard case .verified(let transaction) = result,
        transaction.productID == productID
      else { continue }
      entitlements.append(Self.entitlement(from: transaction))
    }
    return entitlements
  }

  func purchase(productID: String) async throws -> DeckMakerPurchaseResult {
    let product: StoreKit.Product
    if let cached = productsByID[productID] {
      product = cached
    } else {
      let products = try await StoreKit.Product.products(for: [productID])
      guard let loaded = products.first(where: { $0.id == productID })
      else { throw DeckMakerStorefrontError.productUnavailable }
      productsByID[productID] = loaded
      product = loaded
    }

    switch try await product.purchase() {
    case .success(.verified(let transaction)):
      guard transaction.productID == productID else {
        await transaction.finish()
        throw DeckMakerStorefrontError.unexpectedPurchaseResult
      }
      let entitlement = Self.entitlement(from: transaction)
      await transaction.finish()
      return .purchased(entitlement)
    case .success(.unverified):
      throw DeckMakerStorefrontError.failedVerification
    case .pending:
      return .pending
    case .userCancelled:
      return .userCancelled
    @unknown default:
      throw DeckMakerStorefrontError.unexpectedPurchaseResult
    }
  }

  func synchronize() async throws {
    try await AppStore.sync()
  }

  func entitlementUpdates(productID: String) -> AsyncStream<DeckMakerEntitlement> {
    AsyncStream { continuation in
      let observer = Task { @MainActor in
        for await result in StoreKit.Transaction.updates {
          guard !Task.isCancelled else { break }
          guard case .verified(let transaction) = result,
            transaction.productID == productID
          else { continue }

          let entitlement = Self.entitlement(from: transaction)
          await transaction.finish()
          continuation.yield(entitlement)
        }
        continuation.finish()
      }
      continuation.onTermination = { @Sendable _ in
        observer.cancel()
      }
    }
  }

  private static func entitlement(
    from transaction: StoreKit.Transaction,
    now: Date = Date()
  ) -> DeckMakerEntitlement {
    let hasExpired = transaction.expirationDate.map { $0 <= now } ?? false
    return DeckMakerEntitlement(
      productID: transaction.productID,
      isActive: transaction.revocationDate == nil && !transaction.isUpgraded && !hasExpired
    )
  }
}

enum DeckMakerPurchaseActivity: Equatable {
  case idle
  case loading
  case purchasing
  case restoring

  var isBusy: Bool { self != .idle }
}

enum DeckMakerPurchaseNotice: String, Identifiable, Equatable {
  case purchasePending
  case purchaseFailed
  case restoreSucceeded
  case nothingToRestore
  case restoreFailed
  case productUnavailable

  var id: String { rawValue }

  var titleLocalizationKey: String {
    switch self {
    case .purchasePending: "deck_maker.purchase.pending.title"
    case .purchaseFailed: "deck_maker.purchase.failed.title"
    case .restoreSucceeded: "deck_maker.restore.succeeded.title"
    case .nothingToRestore: "deck_maker.restore.empty.title"
    case .restoreFailed: "deck_maker.restore.failed.title"
    case .productUnavailable: "deck_maker.product.unavailable.title"
    }
  }

  var messageLocalizationKey: String {
    switch self {
    case .purchasePending: "deck_maker.purchase.pending.message"
    case .purchaseFailed: "deck_maker.purchase.failed.message"
    case .restoreSucceeded: "deck_maker.restore.succeeded.message"
    case .nothingToRestore: "deck_maker.restore.empty.message"
    case .restoreFailed: "deck_maker.restore.failed.message"
    case .productUnavailable: "deck_maker.product.unavailable.message"
    }
  }
}

/// Authoritative in-app state for the non-consumable Deck Maker entitlement.
/// No entitlement is persisted in UserDefaults: verified StoreKit transactions are
/// checked at launch and observed for purchases, family changes, refunds, and revocation.
@MainActor
final class DeckMakerPurchaseStore: ObservableObject {
  @Published private(set) var product: DeckMakerProductDetails?
  @Published private(set) var hasAccess = false
  @Published private(set) var activity: DeckMakerPurchaseActivity = .idle
  @Published private(set) var notice: DeckMakerPurchaseNotice?

  let productID: String

  private let storefront: any DeckMakerStorefront
  private var hasPrepared = false
  private var transactionObserver: Task<Void, Never>?

  init(
    storefront: (any DeckMakerStorefront)? = nil,
    productID: String = DeckMakerPurchaseConstants.lifetimeProductID,
    observesTransactions: Bool = true
  ) {
    self.storefront = storefront ?? StoreKitDeckMakerStorefront()
    self.productID = productID
    if observesTransactions {
      startObservingTransactions()
    }
  }

  deinit {
    transactionObserver?.cancel()
  }

  var displayPrice: String? { product?.displayPrice }
  var isBusy: Bool { activity.isBusy }

  /// Rechecks the in-memory result of the latest verified StoreKit entitlement
  /// immediately before a paid mutation is committed. Free import, export, delete,
  /// practice, and game paths must not call this gate.
  func requireAccess() throws {
    guard hasAccess else { throw DeckMakerAuthorizationError.accessRequired }
  }

  func prepare(forceReload: Bool = false) async {
    guard !isBusy else { return }
    guard forceReload || !hasPrepared else {
      await refreshEntitlement()
      return
    }

    activity = .loading
    defer { activity = .idle }
    do {
      product = try await storefront.loadProduct(productID: productID)
      if product == nil {
        notice = .productUnavailable
      } else if notice == .productUnavailable {
        notice = nil
      }
    } catch {
      product = nil
      notice = .productUnavailable
    }
    await refreshEntitlement()
    if hasAccess, notice == .productUnavailable {
      notice = nil
    }
    hasPrepared = true
  }

  @discardableResult
  func purchase() async -> Bool {
    guard !isBusy else { return false }
    if hasAccess { return true }

    if product == nil {
      do {
        product = try await storefront.loadProduct(productID: productID)
      } catch {
        notice = .productUnavailable
        return false
      }
    }
    guard product != nil else {
      notice = .productUnavailable
      return false
    }

    capturePurchaseState("started")
    activity = .purchasing
    defer { activity = .idle }
    do {
      switch try await storefront.purchase(productID: productID) {
      case .purchased(let entitlement):
        guard entitlement.productID == productID, entitlement.isActive else {
          await refreshEntitlement()
          if !hasAccess { notice = .purchaseFailed }
          capturePurchaseState(hasAccess ? "completed" : "failed")
          return hasAccess
        }
        hasAccess = true
        notice = nil
        capturePurchaseState("completed")
        return true
      case .pending:
        notice = .purchasePending
        capturePurchaseState("pending")
        return false
      case .userCancelled:
        notice = nil
        capturePurchaseState("cancelled")
        return false
      }
    } catch {
      notice = .purchaseFailed
      capturePurchaseState("failed")
      return false
    }
  }

  @discardableResult
  func restore() async -> Bool {
    guard !isBusy else { return false }
    activity = .restoring
    defer { activity = .idle }
    do {
      try await storefront.synchronize()
      await refreshEntitlement()
      notice = hasAccess ? .restoreSucceeded : .nothingToRestore
      if hasAccess { capturePurchaseState("restored") }
      return hasAccess
    } catch {
      notice = .restoreFailed
      capturePurchaseState("failed")
      return false
    }
  }

  func dismissNotice() {
    notice = nil
  }

  private func capturePurchaseState(_ state: String) {
    TelemetryService.shared.capture(
      .purchaseFlow,
      properties: [.purchaseState: state]
    )
  }

  private func refreshEntitlement() async {
    let entitlements = await storefront.currentEntitlements(productID: productID)
    hasAccess = entitlements.contains {
      $0.productID == productID && $0.isActive
    }
  }

  private func startObservingTransactions() {
    let storefront = self.storefront
    let productID = self.productID
    transactionObserver = Task { @MainActor [weak self, storefront] in
      for await entitlement in storefront.entitlementUpdates(productID: productID) {
        guard !Task.isCancelled else { return }
        guard let self, entitlement.productID == productID else { continue }
        // An inactive update represents refund/revocation and must immediately
        // remove edit/create access. Existing user decks remain readable elsewhere.
        self.hasAccess = entitlement.isActive
      }
    }
  }
}

#if DEBUG
  extension DeckMakerPurchaseStore {
    /// Preview/test-only helper. Release builds contain no flag, launch argument,
    /// or persisted value that can bypass StoreKit verification.
    static func debugPreview(
      unlocked: Bool,
      displayPrice: String = "¥1,500"
    ) -> DeckMakerPurchaseStore {
      let storefront = DebugDeckMakerStorefront(
        details: DeckMakerProductDetails(
          productID: DeckMakerPurchaseConstants.lifetimeProductID,
          displayName: "Deck Maker",
          displayPrice: displayPrice
        ),
        unlocked: unlocked
      )
      return DeckMakerPurchaseStore(storefront: storefront, observesTransactions: false)
    }
  }

  @MainActor
  private final class DebugDeckMakerStorefront: DeckMakerStorefront {
    private let details: DeckMakerProductDetails
    private var unlocked: Bool

    init(details: DeckMakerProductDetails, unlocked: Bool) {
      self.details = details
      self.unlocked = unlocked
    }

    func loadProduct(productID: String) async throws -> DeckMakerProductDetails? { details }

    func currentEntitlements(productID: String) async -> [DeckMakerEntitlement] {
      [DeckMakerEntitlement(productID: productID, isActive: unlocked)]
    }

    func purchase(productID: String) async throws -> DeckMakerPurchaseResult {
      unlocked = true
      return .purchased(DeckMakerEntitlement(productID: productID, isActive: true))
    }

    func synchronize() async throws {}

    func entitlementUpdates(productID: String) -> AsyncStream<DeckMakerEntitlement> {
      AsyncStream { $0.finish() }
    }
  }
#endif
