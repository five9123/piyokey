import Combine
import Foundation
import GameKit
import UIKit

enum GameCenterLeaderboard: String, CaseIterable, Hashable {
  case flowBeginner = "piyokey.v4.flow.beginner"
  case flowIntermediate = "piyokey.v4.flow.intermediate"
  case flowAdvanced = "piyokey.v4.flow.advanced"
  case acidRainBeginner = "piyokey.v3.acid_rain.beginner"
  case acidRainIntermediate = "piyokey.v3.acid_rain.intermediate"
  case acidRainAdvanced = "piyokey.v3.acid_rain.advanced"
  case choseongBeginner = "piyokey.v3.choseong.beginner"
  case choseongIntermediate = "piyokey.v3.choseong.intermediate"
  case choseongAdvanced = "piyokey.v3.choseong.advanced"
  case wordMatchBeginner = "piyokey.v4.word_match.beginner"
  case wordMatchIntermediate = "piyokey.v4.word_match.intermediate"
  case wordMatchAdvanced = "piyokey.v4.word_match.advanced"
  case dictationBeginner = "piyokey.v3.dictation.beginner"
  case dictationIntermediate = "piyokey.v3.dictation.intermediate"
  case dictationAdvanced = "piyokey.v3.dictation.advanced"
  case weeklyPiyoCup = "piyokey.v4.cup.weekly.flow"

  static func leaderboards(for record: GameRecord) -> [GameCenterLeaderboard] {
    guard record.mode == .game,
      let rankedDeck = GameCenterRankedDeck.matching(
        deckID: record.deckId,
        version: record.deckVersion
      )
    else { return [] }

    if record.competition == .weeklyPiyoCup {
      guard rankedDeck.deckID == GameCenterRankedDeck.piyoCupDeckID else { return [] }
      return [.weeklyPiyoCup]
    }
    return [rankedDeck.leaderboard]
  }

  static func leaderboard(for record: GameRecord) -> GameCenterLeaderboard? {
    leaderboards(for: record).first
  }

  static func bestScores(
    from records: [GameRecord],
    asOf date: Date = Date()
  ) -> [GameCenterLeaderboard: Int] {
    records.reduce(into: [:]) { result, record in
      for leaderboard in leaderboards(for: record) {
        if leaderboard == .weeklyPiyoCup,
          !PiyoCupWeek.contains(record.playedAt, inWeekContaining: date)
        {
          continue
        }
        result[leaderboard] = max(result[leaderboard] ?? 0, record.score)
      }
    }
  }
}

/// App Store Connect에서 실제로 Live 상태가 확인된 리더보드만 노출하는 명시적 계약이다.
/// 새 ID는 코드에 추가하는 것만으로 활성화되지 않으며, 운영 확인 뒤 Info.plist 계약에도
/// 추가해야 한다. 키가 없거나 알 수 없는 ID만 있으면 fail-closed로 동작한다.
struct GameCenterAvailabilityContract: Equatable {
  static let infoPlistKey = "PiyokeyGameCenterAvailableLeaderboardIDs"
  static let intendedInfoPlistKey = "PiyokeyGameCenterIntendedLeaderboardIDs"

  let availableLeaderboards: Set<GameCenterLeaderboard>
  let intendedLeaderboards: Set<GameCenterLeaderboard>

  init(
    rawLeaderboardIDs: [String],
    intendedLeaderboardIDs: [String]? = nil
  ) {
    availableLeaderboards = Set(
      rawLeaderboardIDs.compactMap(GameCenterLeaderboard.init(rawValue:))
    )
    let intended = intendedLeaderboardIDs ?? rawLeaderboardIDs
    intendedLeaderboards = Set(
      intended.compactMap(GameCenterLeaderboard.init(rawValue:))
    ).union(availableLeaderboards)
  }

  static func bundled(in bundle: Bundle = .main) -> GameCenterAvailabilityContract {
    let rawIDs = bundle.object(forInfoDictionaryKey: infoPlistKey) as? [String] ?? []
    let intendedIDs = bundle.object(forInfoDictionaryKey: intendedInfoPlistKey) as? [String]
    return GameCenterAvailabilityContract(
      rawLeaderboardIDs: rawIDs,
      intendedLeaderboardIDs: intendedIDs
    )
  }

  func contains(_ leaderboard: GameCenterLeaderboard) -> Bool {
    availableLeaderboards.contains(leaderboard)
  }

  func leaderboards(for record: GameRecord) -> [GameCenterLeaderboard] {
    GameCenterLeaderboard.leaderboards(for: record).filter(contains)
  }

  func intendedLeaderboards(for record: GameRecord) -> [GameCenterLeaderboard] {
    GameCenterLeaderboard.leaderboards(for: record).filter(intendedLeaderboards.contains)
  }

  func rank(
    for record: GameRecord,
    ranks: [GameCenterLeaderboard: Int]
  ) -> Int? {
    leaderboards(for: record).compactMap { ranks[$0] }.min()
  }
}

struct GameCenterRuntimeAvailability: Equatable {
  let baseline: Set<GameCenterLeaderboard>
  let intended: Set<GameCenterLeaderboard>
  private var probedAvailability: Set<GameCenterLeaderboard>?
  private(set) var probeIsInFlight = false
  private(set) var probeIsComplete = false
  private var nextProbeToken = 0
  private var activeProbeToken: Int?

  init(contract: GameCenterAvailabilityContract) {
    baseline = contract.availableLeaderboards
    intended = contract.intendedLeaderboards
  }

  var available: Set<GameCenterLeaderboard> {
    probedAvailability ?? baseline
  }

  mutating func beginProbe() -> Int? {
    guard !probeIsInFlight, !probeIsComplete else { return nil }
    nextProbeToken &+= 1
    activeProbeToken = nextProbeToken
    probeIsInFlight = true
    return nextProbeToken
  }

  @discardableResult
  mutating func completeProbe(
    token: Int,
    returned: Set<GameCenterLeaderboard>,
    succeeded: Bool
  ) -> Bool {
    guard activeProbeToken == token else { return false }
    activeProbeToken = nil
    probeIsInFlight = false
    probeIsComplete = true
    if succeeded {
      // A partial GameKit response cannot revoke a board verified Live for this build.
      probedAvailability = baseline.union(returned.intersection(intended))
    }
    return true
  }

  @discardableResult
  mutating func timeoutProbe(token: Int) -> Bool {
    completeProbe(token: token, returned: [], succeeded: false)
  }

  mutating func allowRetry() {
    probeIsComplete = false
  }

  mutating func resetForPlayerChange() {
    probedAvailability = nil
    probeIsInFlight = false
    probeIsComplete = false
    activeProbeToken = nil
  }
}

struct GameCenterPresentationPolicy {
  static func canPresentAuthentication(
    sceneIsActive: Bool,
    dashboardIsBusy: Bool,
    authenticationControllerIsPresented: Bool,
    presenterHasPresentedController: Bool
  ) -> Bool {
    sceneIsActive
      && dashboardIsBusy
      && !authenticationControllerIsPresented
      && !presenterHasPresentedController
  }

  static func canPresentDashboard(
    sceneIsActive: Bool,
    dashboardIsBusy: Bool,
    playerIsAuthenticated: Bool,
    authenticationIsDismissed: Bool,
    accessPointIsPresenting: Bool
  ) -> Bool {
    sceneIsActive
      && dashboardIsBusy
      && playerIsAuthenticated
      && authenticationIsDismissed
      && !accessPointIsPresenting
  }
}

enum PiyoCupWeek {
  private static let jst = TimeZone(identifier: "Asia/Tokyo")
    ?? TimeZone.current

  static func start(containing date: Date) -> Date {
    var calendar = Calendar(identifier: .gregorian)
    calendar.timeZone = jst
    let startOfDay = calendar.startOfDay(for: date)
    let daysSinceMonday = (calendar.component(.weekday, from: startOfDay) + 5) % 7
    return calendar.date(byAdding: .day, value: -daysSinceMonday, to: startOfDay)
      ?? startOfDay
  }

  static func contains(_ date: Date, inWeekContaining referenceDate: Date) -> Bool {
    var calendar = Calendar(identifier: .gregorian)
    calendar.timeZone = jst
    let weekStart = start(containing: referenceDate)
    guard let nextWeekStart = calendar.date(byAdding: .day, value: 7, to: weekStart)
    else { return false }
    return date >= weekStart && date < nextWeekStart
  }
}

struct GameCenterRankedDeck: Equatable {
  let deckID: String
  let version: Int
  let leaderboard: GameCenterLeaderboard

  static let all: [GameCenterRankedDeck] = [
    GameCenterRankedDeck(
      deckID: "flow_topik_beginner",
      version: 3,
      leaderboard: .flowBeginner
    ),
    GameCenterRankedDeck(
      deckID: "flow_topik_intermediate",
      version: 3,
      leaderboard: .flowIntermediate
    ),
    GameCenterRankedDeck(
      deckID: "flow_topik_advanced",
      version: 3,
      leaderboard: .flowAdvanced
    ),
    GameCenterRankedDeck(
      deckID: "acid_rain_topik_beginner",
      version: 3,
      leaderboard: .acidRainBeginner
    ),
    GameCenterRankedDeck(
      deckID: "acid_rain_topik_intermediate",
      version: 3,
      leaderboard: .acidRainIntermediate
    ),
    GameCenterRankedDeck(
      deckID: "acid_rain_topik_advanced",
      version: 3,
      leaderboard: .acidRainAdvanced
    ),
    GameCenterRankedDeck(
      deckID: "choseong_topik_beginner",
      version: 3,
      leaderboard: .choseongBeginner
    ),
    GameCenterRankedDeck(
      deckID: "choseong_topik_intermediate",
      version: 3,
      leaderboard: .choseongIntermediate
    ),
    GameCenterRankedDeck(
      deckID: "choseong_topik_advanced",
      version: 3,
      leaderboard: .choseongAdvanced
    ),
    GameCenterRankedDeck(
      deckID: "word_match_topik_beginner",
      version: 3,
      leaderboard: .wordMatchBeginner
    ),
    GameCenterRankedDeck(
      deckID: "word_match_topik_intermediate",
      version: 3,
      leaderboard: .wordMatchIntermediate
    ),
    GameCenterRankedDeck(
      deckID: "word_match_topik_advanced",
      version: 3,
      leaderboard: .wordMatchAdvanced
    ),
    GameCenterRankedDeck(
      deckID: "dictation_topik_beginner",
      version: 3,
      leaderboard: .dictationBeginner
    ),
    GameCenterRankedDeck(
      deckID: "dictation_topik_intermediate",
      version: 3,
      leaderboard: .dictationIntermediate
    ),
    GameCenterRankedDeck(
      deckID: "dictation_topik_advanced",
      version: 3,
      leaderboard: .dictationAdvanced
    ),
  ]

  static let piyoCupDeckID = "flow_topik_beginner"

  static func matching(deckID: String, version: Int?) -> GameCenterRankedDeck? {
    guard let version else { return nil }
    return all.first { $0.deckID == deckID && $0.version == version }
  }

  static func isEligible(deckID: String, version: Int) -> Bool {
    matching(deckID: deckID, version: version) != nil
  }

  static var piyoCupDeck: GameCenterRankedDeck {
    all.first { $0.deckID == piyoCupDeckID }
      ?? GameCenterRankedDeck(
        deckID: piyoCupDeckID,
        version: 3,
        leaderboard: .flowBeginner
      )
  }
}

struct GameCenterGrowthSnapshot: Equatable {
  let completedChapterCount: Int
  let typedJamoCount: Int
  let longestStreak: Int

  static let empty = GameCenterGrowthSnapshot(
    completedChapterCount: 0,
    typedJamoCount: 0,
    longestStreak: 0
  )
}

enum GameCenterGrowthAchievement: String, CaseIterable, Hashable {
  case hatching = "piyokey.growth.hatching"
  case chick = "piyokey.growth.chick"
  case rooster = "piyokey.growth.rooster"
  case typedJamo = "piyokey.growth.typed_12000"
  case streak = "piyokey.growth.streak_30"

  func percentComplete(for snapshot: GameCenterGrowthSnapshot) -> Double {
    let progress: Double
    switch self {
    case .hatching:
      progress = Double(snapshot.completedChapterCount)
    case .chick:
      progress = Double(snapshot.completedChapterCount) / 3
    case .rooster:
      progress = Double(snapshot.completedChapterCount) / 6
    case .typedJamo:
      progress = Double(snapshot.typedJamoCount) / 12_000
    case .streak:
      progress = Double(snapshot.longestStreak) / 30
    }
    return min(max(progress * 100, 0), 100)
  }

  static func progress(
    for snapshot: GameCenterGrowthSnapshot
  ) -> [GameCenterGrowthAchievement: Double] {
    Dictionary(uniqueKeysWithValues: allCases.map { ($0, $0.percentComplete(for: snapshot)) })
  }
}

@MainActor
final class GameCenterService: ObservableObject {
  enum ConnectionState: Equatable {
    case idle
    case authenticating
    case signInRequired
    case authenticated(displayName: String)
    case unavailable
  }

  enum SubmissionState: Equatable {
    case pending, submitting, submitted, failed
  }

  struct SubmissionFailure: Equatable {
    let score: Int
    let domain: String
    let code: Int
    let date: Date
  }

  @Published private(set) var submissionStates: [GameCenterLeaderboard: SubmissionState] = [:]
  @Published private(set) var submissionFailures: [GameCenterLeaderboard: SubmissionFailure] = [:]
  @Published private(set) var state: ConnectionState = .idle
  @Published private(set) var ranks: [GameCenterLeaderboard: Int] = [:]
  @Published private(set) var hasSubmittedScore = false
  @Published private(set) var lastSyncFailed = false
  @Published private(set) var isDashboardBusy = false
  @Published private(set) var isSceneActive = true
  @Published private(set) var availableLeaderboards: Set<GameCenterLeaderboard>

  private let requestTimeout: TimeInterval
  private let retryDelay: TimeInterval
  private var nextSubmissionToken = 0
  private var submissionTokens: [GameCenterLeaderboard: Int] = [:]
  private var submissionTimeoutTasks: [GameCenterLeaderboard: Task<Void, Never>] = [:]
  private var submissionRetryTasks: [GameCenterLeaderboard: Task<Void, Never>] = [:]
  private var retryCounts: [GameCenterLeaderboard: Int] = [:]
  private var rankTimeoutTasks: [GameCenterLeaderboard: Task<Void, Never>] = [:]
  private var rankRefreshTasks: [GameCenterLeaderboard: Task<Void, Never>] = [:]
  private let isEnabled: Bool
  private let availabilityContract: GameCenterAvailabilityContract
  private var runtimeAvailability: GameCenterRuntimeAvailability
  private var authenticationAttemptActive = false
  private var authenticationAllowsPresentation = false
  private var authenticationGeneration = 0
  private weak var authenticationController: UIViewController?
  private var authenticationPresentationTask: Task<Void, Never>?
  private var authenticationDismissalTask: Task<Void, Never>?
  private var requestedLeaderboard: GameCenterLeaderboard?
  private var shouldShowAllLeaderboards = false
  private var dashboardRequestGeneration = 0
  private var dashboardPresentationTask: Task<Void, Never>?
  private var synchronizationTask: Task<Void, Never>?
  private var availabilityProbeTimeoutTask: Task<Void, Never>?
  private var records: [GameRecord] = []
  private var growthSnapshot = GameCenterGrowthSnapshot.empty
  private var activePlayerID: String?
  private var weeklyPeriodStart: Date?
  private var submittedScores: [GameCenterLeaderboard: Int] = [:]
  private var submittingScores: [GameCenterLeaderboard: Int] = [:]
  private var rankLoadsInFlight: Set<GameCenterLeaderboard> = []
  private var loadedRankLeaderboards: Set<GameCenterLeaderboard> = []
  private var pendingRankReloads: Set<GameCenterLeaderboard> = []
  private var nextRankLoadToken = 0
  private var rankLoadTokens: [GameCenterLeaderboard: Int] = [:]
  private var reportedAchievements: [GameCenterGrowthAchievement: Double] = [:]
  private var reportingAchievements: [GameCenterGrowthAchievement: Double] = [:]

  init(
    isEnabled: Bool? = nil,
    availabilityContract: GameCenterAvailabilityContract? = nil,
    requestTimeout: TimeInterval = 10,
    retryDelay: TimeInterval = 3
  ) {
    self.requestTimeout = requestTimeout
    self.retryDelay = retryDelay
    self.isEnabled = isEnabled ?? GameCenterService.liveServicesEnabled
    let contract = availabilityContract ?? .bundled()
    self.availabilityContract = contract
    runtimeAvailability = GameCenterRuntimeAvailability(contract: contract)
    availableLeaderboards = contract.availableLeaderboards
  }

  var isAuthenticated: Bool {
    if case .authenticated = state { return true }
    return false
  }

  var bestRank: Int? {
    ranks.values.min()
  }

  var canPresentDashboard: Bool {
    isEnabled && isSceneActive && !isDashboardBusy
  }

  func updateSceneActivity(_ isActive: Bool) {
    guard isSceneActive != isActive else { return }
    isSceneActive = isActive
    if isActive {
      refreshWeeklyPeriod()
      retryCounts.removeAll()
      loadedRankLeaderboards.removeAll()
      runtimeAvailability.allowRetry()
      prepare()
    }
  }

  func updateLocalState(records: [GameRecord], growth: GameCenterGrowthSnapshot) {
    self.records = records
    growthSnapshot = growth
  }

  func isLeaderboardAvailable(for record: GameRecord) -> Bool {
    !effectiveLeaderboards(for: record).isEmpty
  }

  func submissionState(for record: GameRecord) -> SubmissionState? {
    guard let leaderboard = effectiveLeaderboards(for: record).first else { return nil }
    return submissionStates[leaderboard] ?? .pending
  }

  func retrySubmission(for record: GameRecord) {
    guard isEnabled else { return }
    for leaderboard in effectiveLeaderboards(for: record) {
      retryCounts[leaderboard] = 0
      loadedRankLeaderboards.remove(leaderboard)
    }
    submitScore(for: record)
  }

  /// Initializes local Game Center state without presenting sign-in UI. Authentication
  /// controllers are accepted only during an explicit user dashboard request.
  func prepare() {
    guard isEnabled else {
      state = .unavailable
      return
    }
    guard isSceneActive else { return }
    if GKLocalPlayer.local.isAuthenticated {
      adoptAuthenticatedPlayer()
      probeIntendedLeaderboards()
      synchronize()
      return
    }
    startAuthenticationIfNeeded(allowsPresentation: false)
  }

  func showLeaderboards() {
    guard isEnabled, isSceneActive, !isDashboardBusy,
      !availableLeaderboards.isEmpty
    else { return }
    beginDashboardRequest(leaderboard: nil, showsAllLeaderboards: true)
  }

  func showLeaderboard(for record: GameRecord) {
    guard isEnabled, isSceneActive, !isDashboardBusy else { return }
    guard let leaderboard = effectiveLeaderboards(for: record).first else {
      return
    }
    beginDashboardRequest(leaderboard: leaderboard, showsAllLeaderboards: false)
  }

  func submitScore(for record: GameRecord) {
    guard isEnabled else { return }
    if !records.contains(where: { $0.id == record.id }) {
      records.append(record)
    }
    guard GKLocalPlayer.local.isAuthenticated else { return }
    adoptAuthenticatedPlayer()
    refreshWeeklyPeriod()
    // Always drain retained best scores, including a higher score that previously failed.
    synchronizeBestScores()
    synchronizeGrowthAchievements()
    loadRanks()
  }

  func synchronize() {
    guard isEnabled, synchronizationTask == nil else { return }
    synchronizationTask = Task { @MainActor [weak self] in
      await Task.yield()
      guard let self else { return }
      self.synchronizationTask = nil
      guard GKLocalPlayer.local.isAuthenticated else { return }
      self.adoptAuthenticatedPlayer()
      self.refreshWeeklyPeriod()
      self.synchronizeBestScores()
      self.synchronizeGrowthAchievements()
      self.loadRanks()
    }
  }

  /// SwiftUI body에서 호출해도 상태를 바꾸지 않는 순수 조회다.
  func rank(for record: GameRecord) -> Int? {
    effectiveLeaderboards(for: record).compactMap { ranks[$0] }.min()
  }

  private func beginDashboardRequest(
    leaderboard: GameCenterLeaderboard?,
    showsAllLeaderboards: Bool
  ) {
    requestedLeaderboard = leaderboard
    shouldShowAllLeaderboards = showsAllLeaderboards
    dashboardRequestGeneration &+= 1
    isDashboardBusy = true

    if GKLocalPlayer.local.isAuthenticated {
      adoptAuthenticatedPlayer()
      probeIntendedLeaderboards()
      scheduleDashboardPresentation(afterAuthenticationGeneration: nil)
    } else {
      startAuthenticationIfNeeded(allowsPresentation: true)
    }
  }

  private func startAuthenticationIfNeeded(allowsPresentation: Bool) {
    guard isEnabled, isSceneActive else {
      if allowsPresentation { finishDashboardRequest() }
      return
    }

    if authenticationAttemptActive {
      guard allowsPresentation, !authenticationAllowsPresentation else { return }
      resetAuthenticationForRetry()
    }

    authenticationAttemptActive = true
    authenticationAllowsPresentation = allowsPresentation
    authenticationGeneration &+= 1
    let generation = authenticationGeneration
    state = .authenticating
    GKLocalPlayer.local.authenticateHandler = { [weak self] viewController, error in
      Task { @MainActor in
        self?.handleAuthentication(
          viewController: viewController,
          error: error,
          generation: generation
        )
      }
    }
  }

  private func handleAuthentication(
    viewController: UIViewController?,
    error: Error?,
    generation: Int
  ) {
    guard authenticationGeneration == generation else { return }
    if let viewController {
      guard authenticationAllowsPresentation, isDashboardBusy, isSceneActive else {
        state = .signInRequired
        resetAuthenticationForRetry()
        return
      }
      scheduleAuthenticationPresentation(viewController, generation: generation)
      return
    }

    guard error == nil, GKLocalPlayer.local.isAuthenticated else {
      clearPlayerSession()
      state = error == nil ? .signInRequired : .unavailable
      lastSyncFailed = error != nil
      resetAuthenticationForRetry()
      finishDashboardRequest()
      return
    }

    let completedActiveAttempt = authenticationAttemptActive
    authenticationAttemptActive = false
    authenticationAllowsPresentation = false
    adoptAuthenticatedPlayer()
    probeIntendedLeaderboards()
    lastSyncFailed = false
    if isDashboardBusy {
      if completedActiveAttempt || dashboardPresentationTask == nil {
        scheduleDashboardPresentation(afterAuthenticationGeneration: generation)
      }
    } else {
      synchronize()
    }
  }

  private func scheduleAuthenticationPresentation(
    _ viewController: UIViewController,
    generation: Int
  ) {
    authenticationPresentationTask?.cancel()
    authenticationPresentationTask = Task { @MainActor [weak self] in
      guard let self else { return }
      for _ in 0..<30 {
        guard !Task.isCancelled,
          self.authenticationGeneration == generation,
          self.authenticationAttemptActive,
          self.isDashboardBusy
        else { return }

        if let activeController = self.authenticationController,
          activeController.presentingViewController != nil || activeController.isBeingDismissed
        {
          if activeController === viewController { return }
          try? await Task.sleep(nanoseconds: 100_000_000)
          continue
        }

        if self.isSceneActive,
          viewController.presentingViewController == nil,
          let presenter = Self.topViewController(),
          GameCenterPresentationPolicy.canPresentAuthentication(
            sceneIsActive: self.isSceneActive,
            dashboardIsBusy: self.isDashboardBusy,
            authenticationControllerIsPresented: false,
            presenterHasPresentedController: presenter.presentedViewController != nil
          ),
          presenter.viewIfLoaded?.window != nil,
          !presenter.isBeingDismissed,
          !presenter.isBeingPresented
        {
          self.authenticationController = viewController
          presenter.present(viewController, animated: true)
          self.authenticationPresentationTask = nil
          self.monitorAuthenticationDismissal(viewController, generation: generation)
          return
        }
        try? await Task.sleep(nanoseconds: 100_000_000)
      }

      guard self.authenticationGeneration == generation else { return }
      self.state = .signInRequired
      self.resetAuthenticationForRetry()
      self.finishDashboardRequest()
    }
  }

  private func monitorAuthenticationDismissal(
    _ viewController: UIViewController,
    generation: Int
  ) {
    authenticationDismissalTask?.cancel()
    authenticationDismissalTask = Task { @MainActor [weak self] in
      guard let self else { return }
      var wasPresented = false
      for _ in 0..<3_600 {
        guard !Task.isCancelled,
          self.authenticationGeneration == generation,
          self.authenticationAttemptActive
        else { return }
        if viewController.presentingViewController != nil {
          wasPresented = true
        } else if wasPresented {
          try? await Task.sleep(nanoseconds: 300_000_000)
          guard self.authenticationGeneration == generation,
            self.authenticationAttemptActive
          else { return }
          self.authenticationDismissalTask = nil
          if GKLocalPlayer.local.isAuthenticated {
            self.authenticationAttemptActive = false
            self.authenticationAllowsPresentation = false
            self.adoptAuthenticatedPlayer()
            self.probeIntendedLeaderboards()
            self.scheduleDashboardPresentation(afterAuthenticationGeneration: generation)
          } else {
            self.state = .signInRequired
            self.resetAuthenticationForRetry()
            self.finishDashboardRequest()
          }
          return
        }
        try? await Task.sleep(nanoseconds: 100_000_000)
      }

      guard self.authenticationGeneration == generation,
        self.authenticationAttemptActive
      else { return }
      self.authenticationDismissalTask = nil
      self.state = .signInRequired
      self.resetAuthenticationForRetry()
      self.finishDashboardRequest()
    }
  }

  private func scheduleDashboardPresentation(afterAuthenticationGeneration: Int?) {
    let requestGeneration = dashboardRequestGeneration
    dashboardPresentationTask?.cancel()
    dashboardPresentationTask = Task { @MainActor [weak self] in
      guard let self else { return }
      var stableDismissalPasses = 0
      for _ in 0..<100 {
        guard !Task.isCancelled,
          self.dashboardRequestGeneration == requestGeneration,
          self.isDashboardBusy,
          GKLocalPlayer.local.isAuthenticated
        else {
          self.finishDashboardRequest()
          return
        }
        if let expectedGeneration = afterAuthenticationGeneration,
          expectedGeneration != self.authenticationGeneration
        {
          self.finishDashboardRequest()
          return
        }

        let authenticationIsGone: Bool
        if let controller = self.authenticationController {
          authenticationIsGone = controller.presentingViewController == nil
            && !controller.isBeingDismissed
          if authenticationIsGone { self.authenticationController = nil }
        } else {
          authenticationIsGone = true
        }

        if !self.runtimeAvailability.probeIsInFlight,
          GameCenterPresentationPolicy.canPresentDashboard(
            sceneIsActive: self.isSceneActive,
            dashboardIsBusy: self.isDashboardBusy,
            playerIsAuthenticated: GKLocalPlayer.local.isAuthenticated,
            authenticationIsDismissed: authenticationIsGone,
            accessPointIsPresenting: GKAccessPoint.shared.isPresentingGameCenter
          )
        {
          stableDismissalPasses += 1
          if stableDismissalPasses >= 2 { break }
        } else {
          stableDismissalPasses = 0
        }
        try? await Task.sleep(nanoseconds: 100_000_000)
      }

      guard !Task.isCancelled,
        self.dashboardRequestGeneration == requestGeneration,
        !self.runtimeAvailability.probeIsInFlight,
        GameCenterPresentationPolicy.canPresentDashboard(
          sceneIsActive: self.isSceneActive,
          dashboardIsBusy: self.isDashboardBusy,
          playerIsAuthenticated: GKLocalPlayer.local.isAuthenticated,
          authenticationIsDismissed: self.authenticationController == nil,
          accessPointIsPresenting: GKAccessPoint.shared.isPresentingGameCenter
        )
      else {
        self.finishDashboardRequest()
        return
      }

      // Sign-in may have just completed. Submit before opening the first dashboard.
      self.synchronizeBestScores()
      self.synchronizeGrowthAchievements()
      self.loadRanks(force: true)
      for _ in 0..<Int(self.requestTimeout * 10) {
        guard !Task.isCancelled, self.dashboardRequestGeneration == requestGeneration,
          self.isSceneActive, GKLocalPlayer.local.isAuthenticated
        else {
          self.finishDashboardRequest()
          return
        }
        let waiting = self.requestedLeaderboard.map { self.submittingScores[$0] != nil }
          ?? !self.submittingScores.isEmpty
        if !waiting { break }
        try? await Task.sleep(nanoseconds: 100_000_000)
      }
      guard !Task.isCancelled, self.dashboardRequestGeneration == requestGeneration,
        self.isSceneActive, GKLocalPlayer.local.isAuthenticated
      else {
        self.finishDashboardRequest()
        return
      }
      let accessPoint = GKAccessPoint.shared
      if let leaderboard = self.requestedLeaderboard,
        self.availableLeaderboards.contains(leaderboard)
      {
        if #available(iOS 18.0, *) {
          accessPoint.trigger(
            leaderboardID: leaderboard.rawValue,
            playerScope: .global,
            timeScope: .allTime,
            handler: nil
          )
        } else {
          accessPoint.trigger(state: .leaderboards) {}
        }
      } else if self.shouldShowAllLeaderboards {
        accessPoint.trigger(state: .leaderboards) {}
      } else {
        self.finishDashboardRequest()
        return
      }

      var observedPresentation = false
      for _ in 0..<50 {
        guard !Task.isCancelled,
          self.dashboardRequestGeneration == requestGeneration,
          self.isDashboardBusy
        else { return }
        if GKAccessPoint.shared.isPresentingGameCenter {
          observedPresentation = true
          break
        }
        try? await Task.sleep(nanoseconds: 100_000_000)
      }

      guard observedPresentation else {
        self.dashboardPresentationTask = nil
        self.finishDashboardRequest()
        return
      }

      for _ in 0..<3_600 {
        guard !Task.isCancelled,
          self.dashboardRequestGeneration == requestGeneration,
          self.isDashboardBusy
        else { return }
        if !GKAccessPoint.shared.isPresentingGameCenter, self.isSceneActive { break }
        try? await Task.sleep(nanoseconds: 100_000_000)
      }

      let dashboardWasDismissed = !GKAccessPoint.shared.isPresentingGameCenter
        && self.isSceneActive
      self.dashboardPresentationTask = nil
      self.finishDashboardRequest()
      if dashboardWasDismissed {
        self.loadedRankLeaderboards.removeAll()
        self.synchronize()
      }
    }
  }

  private func finishDashboardRequest() {
    requestedLeaderboard = nil
    shouldShowAllLeaderboards = false
    isDashboardBusy = false
  }

  private func resetAuthenticationForRetry() {
    authenticationGeneration &+= 1
    authenticationAttemptActive = false
    authenticationAllowsPresentation = false
    authenticationController = nil
    authenticationPresentationTask?.cancel()
    authenticationPresentationTask = nil
    authenticationDismissalTask?.cancel()
    authenticationDismissalTask = nil
    if isEnabled { GKLocalPlayer.local.authenticateHandler = nil }
  }

  private func effectiveLeaderboards(for record: GameRecord) -> [GameCenterLeaderboard] {
    GameCenterLeaderboard.leaderboards(for: record).filter(availableLeaderboards.contains)
  }

  @discardableResult
  private func probeIntendedLeaderboards() -> Bool {
    guard #available(iOS 26.0, *) else { return false }
    guard GKLocalPlayer.local.isAuthenticated,
      let probeToken = runtimeAvailability.beginProbe()
    else { return false }
    let playerID = activePlayerID
    availabilityProbeTimeoutTask?.cancel()
    availabilityProbeTimeoutTask = Task { @MainActor [weak self] in
      try? await Task.sleep(nanoseconds: 5_000_000_000)
      guard !Task.isCancelled,
        let self,
        self.activePlayerID == playerID,
        self.runtimeAvailability.timeoutProbe(token: probeToken)
      else { return }
      self.availabilityProbeTimeoutTask = nil
      self.availableLeaderboards = self.runtimeAvailability.available
      self.lastSyncFailed = true
      self.synchronize()
    }
    GKLeaderboard.loadLeaderboards(IDs: nil) { [weak self] leaderboards, error in
      Task { @MainActor in
        guard let self, self.activePlayerID == playerID else { return }
        let returned: Set<GameCenterLeaderboard> = Set(
          (leaderboards ?? []).compactMap { leaderboard -> GameCenterLeaderboard? in
            guard leaderboard.releaseState.contains(.released) else { return nil }
            return GameCenterLeaderboard(rawValue: leaderboard.baseLeaderboardID)
          }
        )
        guard self.runtimeAvailability.completeProbe(
          token: probeToken,
          returned: returned,
          succeeded: error == nil
        ) else { return }
        self.availabilityProbeTimeoutTask?.cancel()
        self.availabilityProbeTimeoutTask = nil
        self.availableLeaderboards = self.runtimeAvailability.available
        if error != nil { self.lastSyncFailed = true }
        self.synchronize()
      }
    }
    return true
  }

  private func synchronizeBestScores() {
    for (leaderboard, score) in GameCenterLeaderboard.bestScores(from: records) where
      availableLeaderboards.contains(leaderboard)
    {
      submit(score: score, to: leaderboard)
    }
  }

  private func submit(score: Int, to leaderboard: GameCenterLeaderboard) {
    guard availableLeaderboards.contains(leaderboard) else { return }
    refreshWeeklyPeriod()
    guard score >= 0,
      score > max(submittedScores[leaderboard] ?? -1, submittingScores[leaderboard] ?? -1)
    else { return }
    submittingScores[leaderboard] = score
    submissionStates[leaderboard] = .submitting
    nextSubmissionToken &+= 1
    let token = nextSubmissionToken
    submissionTokens[leaderboard] = token
    let playerID = activePlayerID
    let submissionWeekStart = leaderboard == .weeklyPiyoCup ? weeklyPeriodStart : nil
    submissionTimeoutTasks[leaderboard]?.cancel()
    submissionTimeoutTasks[leaderboard] = Task { @MainActor [weak self] in
      guard let delay = self?.requestTimeout else { return }
      try? await Task.sleep(nanoseconds: UInt64(delay * 1_000_000_000))
      guard !Task.isCancelled else { return }
      self?.finishSubmission(score: score, leaderboard: leaderboard, token: token,
        playerID: playerID, weekStart: submissionWeekStart,
        error: NSError(domain: NSURLErrorDomain, code: NSURLErrorTimedOut))
    }
    GKLeaderboard.submitScore(score, context: 0, player: GKLocalPlayer.local,
      leaderboardIDs: [leaderboard.rawValue]
    ) { [weak self] error in
      Task { @MainActor in
        self?.finishSubmission(score: score, leaderboard: leaderboard, token: token,
          playerID: playerID, weekStart: submissionWeekStart, error: error)
      }
    }
  }

  private func finishSubmission(
    score: Int, leaderboard: GameCenterLeaderboard, token: Int,
    playerID: String?, weekStart: Date?, error: Error?
  ) {
    guard activePlayerID == playerID, GKLocalPlayer.local.isAuthenticated,
      submissionTokens[leaderboard] == token
    else { return }
    refreshWeeklyPeriod()
    guard leaderboard != .weeklyPiyoCup || weeklyPeriodStart == weekStart else { return }
    submissionTokens[leaderboard] = nil
    submissionTimeoutTasks.removeValue(forKey: leaderboard)?.cancel()
    submittingScores[leaderboard] = nil
    if let error = error as NSError? {
      submissionStates[leaderboard] = .failed
      submissionFailures[leaderboard] = SubmissionFailure(score: score,
        domain: error.domain, code: error.code, date: Date())
      lastSyncFailed = true
      scheduleSubmissionRetry(leaderboard)
    } else {
      submittedScores[leaderboard] = max(submittedScores[leaderboard] ?? 0, score)
      submissionStates[leaderboard] = .submitted
      submissionFailures[leaderboard] = nil
      submissionRetryTasks.removeValue(forKey: leaderboard)?.cancel()
      retryCounts[leaderboard] = 0
      hasSubmittedScore = true
      lastSyncFailed = !submissionFailures.isEmpty
      loadRanks(identifiers: [leaderboard], force: true)
      scheduleRankRefresh(leaderboard)
    }
  }

  private func scheduleSubmissionRetry(_ leaderboard: GameCenterLeaderboard) {
    guard submissionRetryTasks[leaderboard] == nil,
      retryCounts[leaderboard, default: 0] < 2
    else { return }
    retryCounts[leaderboard, default: 0] += 1
    let attempt = retryCounts[leaderboard, default: 1]
    submissionRetryTasks[leaderboard] = Task { @MainActor [weak self] in
      guard let delay = self?.retryDelay else { return }
      try? await Task.sleep(nanoseconds: UInt64(delay * Double(attempt) * 1_000_000_000))
      guard !Task.isCancelled, let self else { return }
      self.submissionRetryTasks[leaderboard] = nil
      guard self.isSceneActive, GKLocalPlayer.local.isAuthenticated else { return }
      self.synchronize()
    }
  }

  private func scheduleRankRefresh(_ leaderboard: GameCenterLeaderboard) {
    rankRefreshTasks[leaderboard]?.cancel()
    rankRefreshTasks[leaderboard] = Task { @MainActor [weak self] in
      for multiplier in [1, 2, 3] {
        guard let delay = self?.retryDelay else { return }
        try? await Task.sleep(nanoseconds: UInt64(delay * Double(multiplier) * 1_000_000_000))
        guard !Task.isCancelled, let self else { return }
        guard self.isSceneActive, GKLocalPlayer.local.isAuthenticated else { break }
        self.loadRanks(identifiers: [leaderboard], force: true)
      }
      self?.rankRefreshTasks[leaderboard] = nil
    }
  }

  private func synchronizeGrowthAchievements() {
    let progress = GameCenterGrowthAchievement.progress(for: growthSnapshot)
    let changed = progress.filter { achievement, percent in
      percent > 0
        && percent > max(
          reportedAchievements[achievement] ?? -1,
          reportingAchievements[achievement] ?? -1
        )
    }
    guard !changed.isEmpty else { return }

    let gameKitAchievements = changed.map { achievement, percent -> GKAchievement in
      reportingAchievements[achievement] = percent
      let value = GKAchievement(identifier: achievement.rawValue)
      value.percentComplete = percent
      value.showsCompletionBanner = percent >= 100
      return value
    }
    let playerID = activePlayerID
    GKAchievement.report(gameKitAchievements) { [weak self] error in
      Task { @MainActor in
        guard let self, self.activePlayerID == playerID else { return }
        changed.forEach { achievement, percent in
          if self.reportingAchievements[achievement] == percent {
            self.reportingAchievements[achievement] = nil
          }
        }
        if error == nil {
          changed.forEach { self.reportedAchievements[$0.key] = $0.value }
          self.lastSyncFailed = !self.submissionFailures.isEmpty
        } else {
          self.lastSyncFailed = true
        }
      }
    }
  }

  private func loadRanks(
    identifiers: [GameCenterLeaderboard] = GameCenterLeaderboard.allCases,
    force: Bool = false
  ) {
    guard GKLocalPlayer.local.isAuthenticated else { return }
    let requested = Set(identifiers.filter(availableLeaderboards.contains))
    if force {
      pendingRankReloads.formUnion(requested.intersection(rankLoadsInFlight))
    }
    let pending = Set(requested.filter {
      !rankLoadsInFlight.contains($0) && (force || !loadedRankLeaderboards.contains($0))
    })
    guard !pending.isEmpty else { return }

    let playerID = activePlayerID
    let weekStart = weeklyPeriodStart
    var tokens: [GameCenterLeaderboard: Int] = [:]
    for leaderboard in pending {
      nextRankLoadToken &+= 1
      tokens[leaderboard] = nextRankLoadToken
      rankLoadTokens[leaderboard] = nextRankLoadToken
      rankLoadsInFlight.insert(leaderboard)
      let token = nextRankLoadToken
      rankTimeoutTasks[leaderboard]?.cancel()
      rankTimeoutTasks[leaderboard] = Task { @MainActor [weak self] in
        guard let delay = self?.requestTimeout else { return }
        try? await Task.sleep(nanoseconds: UInt64(delay * 1_000_000_000))
        guard !Task.isCancelled, let self else { return }
        self.finishRankLoad(leaderboard, token: token, markLoaded: false)
      }
    }
    GKLeaderboard.loadLeaderboards(IDs: pending.map(\.rawValue)) { [weak self] leaderboards, error in
      Task { @MainActor in
        guard let self, self.activePlayerID == playerID else { return }
        guard error == nil, let leaderboards else {
          self.lastSyncFailed = true
          pending.forEach {
            self.finishRankLoad($0, token: tokens[$0], markLoaded: false)
          }
          return
        }

        let returned = Set(leaderboards.compactMap {
          GameCenterLeaderboard(rawValue: $0.baseLeaderboardID)
        })
        let missing = pending.subtracting(returned)
        if !missing.isEmpty { self.lastSyncFailed = true }
        missing.forEach {
          self.finishRankLoad($0, token: tokens[$0], markLoaded: false)
        }

        for leaderboard in leaderboards {
          guard let mapped = GameCenterLeaderboard(rawValue: leaderboard.baseLeaderboardID),
            let token = tokens[mapped], self.rankLoadTokens[mapped] == token
          else { continue }
          leaderboard.loadEntries(
            for: .global,
            timeScope: .allTime,
            range: NSRange(location: 1, length: 1)
          ) { [weak self] localEntry, _, _, entryError in
            Task { @MainActor in
              guard let self, self.activePlayerID == playerID,
                self.rankLoadTokens[mapped] == token
              else { return }
              if mapped == .weeklyPiyoCup, self.weeklyPeriodStart != weekStart {
                self.finishRankLoad(mapped, token: token, markLoaded: false)
                return
              }
              if let localEntry {
                self.ranks[mapped] = localEntry.rank
                self.hasSubmittedScore = true
              }
              if entryError != nil { self.lastSyncFailed = true }
              self.finishRankLoad(mapped, token: token,
                markLoaded: entryError == nil && localEntry != nil)
            }
          }
        }
      }
    }
  }

  private func finishRankLoad(
    _ leaderboard: GameCenterLeaderboard,
    token: Int?,
    markLoaded: Bool
  ) {
    guard let token, rankLoadTokens[leaderboard] == token else { return }
    rankLoadTokens[leaderboard] = nil
    rankTimeoutTasks.removeValue(forKey: leaderboard)?.cancel()
    rankLoadsInFlight.remove(leaderboard)
    if markLoaded { loadedRankLeaderboards.insert(leaderboard) }
    guard pendingRankReloads.remove(leaderboard) != nil else { return }
    loadedRankLeaderboards.remove(leaderboard)
    loadRanks(identifiers: [leaderboard], force: true)
  }

  private func adoptAuthenticatedPlayer() {
    let playerID = GKLocalPlayer.local.gamePlayerID
    if activePlayerID != playerID {
      cancelPendingRequests()
      ranks.removeAll()
      submittedScores.removeAll()
      submittingScores.removeAll()
      rankLoadsInFlight.removeAll()
      loadedRankLeaderboards.removeAll()
      pendingRankReloads.removeAll()
      rankLoadTokens.removeAll()
      reportedAchievements.removeAll()
      reportingAchievements.removeAll()
      availabilityProbeTimeoutTask?.cancel()
      availabilityProbeTimeoutTask = nil
      runtimeAvailability.resetForPlayerChange()
      availableLeaderboards = runtimeAvailability.available
      lastSyncFailed = false
      activePlayerID = playerID
    }
    refreshWeeklyPeriod()
    state = .authenticated(displayName: GKLocalPlayer.local.displayName)
  }

  private func refreshWeeklyPeriod(asOf date: Date = Date()) {
    let periodStart = PiyoCupWeek.start(containing: date)
    guard weeklyPeriodStart != periodStart else { return }
    weeklyPeriodStart = periodStart
    cancelPendingRequests(for: .weeklyPiyoCup)
    ranks[.weeklyPiyoCup] = nil
    submittedScores[.weeklyPiyoCup] = nil
    submittingScores[.weeklyPiyoCup] = nil
    rankLoadsInFlight.remove(.weeklyPiyoCup)
    loadedRankLeaderboards.remove(.weeklyPiyoCup)
    pendingRankReloads.remove(.weeklyPiyoCup)
    rankLoadTokens[.weeklyPiyoCup] = nil
  }

  private func clearPlayerSession() {
    cancelPendingRequests()
    activePlayerID = nil
    ranks.removeAll()
    submittedScores.removeAll()
    submittingScores.removeAll()
    rankLoadsInFlight.removeAll()
    loadedRankLeaderboards.removeAll()
    pendingRankReloads.removeAll()
    rankLoadTokens.removeAll()
    reportedAchievements.removeAll()
    reportingAchievements.removeAll()
    availabilityProbeTimeoutTask?.cancel()
    availabilityProbeTimeoutTask = nil
    runtimeAvailability.resetForPlayerChange()
    availableLeaderboards = runtimeAvailability.available
  }

  private func cancelPendingRequests(for leaderboard: GameCenterLeaderboard) {
    submissionTokens[leaderboard] = nil
    submissionTimeoutTasks.removeValue(forKey: leaderboard)?.cancel()
    submissionRetryTasks.removeValue(forKey: leaderboard)?.cancel()
    rankTimeoutTasks.removeValue(forKey: leaderboard)?.cancel()
    rankRefreshTasks.removeValue(forKey: leaderboard)?.cancel()
    retryCounts[leaderboard] = nil
    submissionStates[leaderboard] = nil
    submissionFailures[leaderboard] = nil
  }

  private func cancelPendingRequests() {
    for leaderboard in GameCenterLeaderboard.allCases {
      cancelPendingRequests(for: leaderboard)
    }
  }

  private static var liveServicesEnabled: Bool {
    !ProcessInfo.processInfo.environment.keys.contains { $0.hasPrefix("UITEST_") }
  }

  private static func topViewController() -> UIViewController? {
    let root = UIApplication.shared.connectedScenes
      .compactMap { $0 as? UIWindowScene }
      .flatMap { $0.windows }
      .first { $0.isKeyWindow }?
      .rootViewController
    return topViewController(from: root)
  }

  private static func topViewController(from root: UIViewController?) -> UIViewController? {
    if let presented = root?.presentedViewController {
      return topViewController(from: presented)
    }
    if let navigation = root as? UINavigationController {
      return topViewController(from: navigation.visibleViewController)
    }
    if let tab = root as? UITabBarController {
      return topViewController(from: tab.selectedViewController)
    }
    return root
  }
}
