// Controlled Foundation-only adapters for the unmodified production service.
// No Apple account, network requests, or real leaderboard submissions are used.
import Foundation
@propertyWrapper struct Published<Value> { var wrappedValue: Value }
protocol ObservableObject {}
enum SessionInputMode: CaseIterable { case builtIn, osIME, builtInKorean10Key }
enum GameCompetition { case officialDeck, weeklyPiyoCup }
struct GameRecord {
  enum Mode { case game, lesson }
  var id = UUID()
  var mode = Mode.game
  var deckId = "flow_topik_beginner"
  var deckVersion: Int? = 3
  var competition: GameCompetition? = nil
  var inputMode = SessionInputMode.builtIn
  var score: Int
  var playedAt = Date()
}
class UIView { var window: AnyObject? = NSObject() }
class UIViewController: NSObject {
  var presentingViewController: UIViewController?
  var presentedViewController: UIViewController?
  var isBeingDismissed = false
  var isBeingPresented = false
  var viewIfLoaded: UIView? = UIView()
  func present(_ other: UIViewController, animated: Bool) {
    other.presentingViewController = self
    presentedViewController = other
  }
}
class UINavigationController: UIViewController { var visibleViewController: UIViewController? }
class UITabBarController: UIViewController { var selectedViewController: UIViewController? }
class UIScene {}
class UIWindow { var isKeyWindow = true; var rootViewController: UIViewController? = UIViewController() }
class UIWindowScene: UIScene { var windows = [UIWindow()] }
class UIApplication { static let shared = UIApplication(); var connectedScenes: [UIScene] = [UIWindowScene()] }
class GKPlayer { var gamePlayerID = "delivery-test"; var displayName = "Test" }
class GKLocalPlayer: GKPlayer {
  static let local = GKLocalPlayer()
  var isAuthenticated = true
  var authenticateHandler: ((UIViewController?, Error?) -> Void)?
}
struct GKReleaseState: OptionSet { let rawValue: Int; static let released = Self(rawValue: 1) }
class GKLeaderboard {
  enum PlayerScope: Hashable { case global, friendsOnly }
  enum TimeScope { case allTime }
  struct Entry { let rank: Int; var score: Int = 0; var player: GKPlayer = GKLocalPlayer.local }
  let baseLeaderboardID: String
  let occurrence: Int
  var startDate: Date? = GKLeaderboard.occurrenceStart ?? PiyoCupWeek.start(containing: Date())
  static var occurrenceStart: Date?
  var duration: TimeInterval = 7 * 24 * 60 * 60
  var releaseState = GKReleaseState.released
  init(_ id: String) { baseLeaderboardID = id; occurrence = Self.currentOccurrence }
  static var submitted: [(id: String, score: Int)] = []
  static var error: Error?
  static var storesSubmittedScores = true
  static var currentOccurrence = 0
  static var occurrenceSubmissions = 0
  static var holdWeeklyLookup = false
  static var weeklyLookups: [() -> Void] = []
  static var holdSubmission = false
  static var callbacks: [(Error?) -> Void] = []
  static var holdRank = false
  static var entryCallbacks: [(id: String, callback: (Entry?, [Entry]?, Int, Error?) -> Void)] = []
  static var entries: [String: Entry] = [:]
  static var probeIDs: [String]?
  static var probeReleased = true
  static var entryLoads = 0
  static var readHandler: ((String, PlayerScope, NSRange, @escaping (Entry?, [Entry]?, Int, Error?) -> Void) -> Void)?
  static var loadedRanges: [(String, PlayerScope, NSRange)] = []
  static func loadLeaderboards(IDs: [String]?, completionHandler: @escaping ([GKLeaderboard]?, Error?) -> Void) {
    let boards = (IDs ?? probeIDs ?? GameCenterLeaderboard.allCases.map(\.rawValue)).map(GKLeaderboard.init)
    if IDs == nil, !probeReleased { boards.forEach { $0.releaseState = [] } }
    if holdWeeklyLookup, IDs == [GameCenterLeaderboard.weeklyPiyoCup.rawValue] {
      weeklyLookups.append { completionHandler(boards, nil) }
      return
    }
    completionHandler(boards, nil)
  }
  static func submitScore(_ score: Int, context: Int, player: GKPlayer, leaderboardIDs: [String], completionHandler: @escaping (Error?) -> Void) {
    submitted += leaderboardIDs.map { ($0, score) }
    let submittingPlayerID = player.gamePlayerID
    let finish: (Error?) -> Void = { error in
      if error == nil, storesSubmittedScores, GKLocalPlayer.local.gamePlayerID == submittingPlayerID {
        for id in leaderboardIDs {
          let previous = entries[id]
          entries[id] = Entry(rank: previous?.rank ?? 1, score: max(previous?.score ?? 0, score))
        }
      }
      completionHandler(error)
    }
    callbacks.append(finish)
    if !holdSubmission { finish(error) }
  }
  func submitScore(_ score: Int, context: Int, player: GKPlayer, completionHandler: @escaping (Error?) -> Void) {
    Self.occurrenceSubmissions += 1
    guard occurrence == Self.currentOccurrence else {
      completionHandler(NSError(domain: "ExpiredWeeklyOccurrence", code: 1))
      return
    }
    Self.submitScore(score, context: context, player: player,
      leaderboardIDs: [baseLeaderboardID], completionHandler: completionHandler)
  }
  func loadEntries(for scope: PlayerScope, timeScope: TimeScope, range: NSRange, completionHandler: @escaping (Entry?, [Entry]?, Int, Error?) -> Void) {
    Self.entryLoads += 1
    Self.loadedRanges.append((baseLeaderboardID, scope, range))
    if let handler = Self.readHandler {
      handler(baseLeaderboardID, scope, range, completionHandler)
      return
    }
    Self.entryCallbacks.append((baseLeaderboardID, completionHandler))
    if !Self.holdRank { completionHandler(Self.entries[baseLeaderboardID], [], 0, nil) }
  }
}
class GKAccessPoint {
  enum State { case leaderboards, achievements }
  static let shared = GKAccessPoint()
  var isPresentingGameCenter = false
  var openedBoard: String?
  var openedScope: GKLeaderboard.PlayerScope?
  var openedState: State?
  var submittedAtOpen: [(id: String, score: Int)] = []
  func trigger(leaderboardID: String, playerScope: GKLeaderboard.PlayerScope, timeScope: GKLeaderboard.TimeScope, handler: (() -> Void)?) { openedBoard = leaderboardID; openedScope = playerScope; open() }
  func trigger(state: State, handler: @escaping () -> Void) { openedState = state; open() }
  func open() { submittedAtOpen = GKLeaderboard.submitted; isPresentingGameCenter = true }
}
class GKAchievement {
  init(identifier: String) {}
  var percentComplete = 0.0
  var showsCompletionBanner = false
  static var reportedBanners: [Bool] = []
  static func report(_ list: [GKAchievement], withCompletionHandler completionHandler: @escaping (Error?) -> Void) { reportedBanners += list.map(\.showsCompletionBanner); completionHandler(nil) }
}
