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
  enum PlayerScope { case global }
  enum TimeScope { case allTime }
  struct Entry { let rank: Int }
  let baseLeaderboardID: String
  var releaseState = GKReleaseState.released
  init(_ id: String) { baseLeaderboardID = id }
  static var submitted: [(id: String, score: Int)] = []
  static var error: Error?
  static var holdSubmission = false
  static var callbacks: [(Error?) -> Void] = []
  static var holdRank = false
  static var entryCallbacks: [(id: String, callback: (Entry?, [Entry]?, Int, Error?) -> Void)] = []
  static var entries: [String: Entry] = [:]
  static var probeIDs: [String]?
  static var probeReleased = true
  static var entryLoads = 0
  static func loadLeaderboards(IDs: [String]?, completionHandler: @escaping ([GKLeaderboard]?, Error?) -> Void) {
    let boards = (IDs ?? probeIDs ?? GameCenterLeaderboard.allCases.map(\.rawValue)).map(GKLeaderboard.init)
    if IDs == nil, !probeReleased { boards.forEach { $0.releaseState = [] } }
    completionHandler(boards, nil)
  }
  static func submitScore(_ score: Int, context: Int, player: GKPlayer, leaderboardIDs: [String], completionHandler: @escaping (Error?) -> Void) {
    submitted += leaderboardIDs.map { ($0, score) }
    callbacks.append(completionHandler)
    if !holdSubmission { completionHandler(error) }
  }
  func loadEntries(for scope: PlayerScope, timeScope: TimeScope, range: NSRange, completionHandler: @escaping (Entry?, [Entry]?, Int, Error?) -> Void) {
    Self.entryLoads += 1
    Self.entryCallbacks.append((baseLeaderboardID, completionHandler))
    if !Self.holdRank { completionHandler(Self.entries[baseLeaderboardID], [], 0, nil) }
  }
}
class GKAccessPoint {
  enum State { case leaderboards }
  static let shared = GKAccessPoint()
  var isPresentingGameCenter = false
  var submittedAtOpen: [(id: String, score: Int)] = []
  func trigger(leaderboardID: String, playerScope: GKLeaderboard.PlayerScope, timeScope: GKLeaderboard.TimeScope, handler: (() -> Void)?) { open() }
  func trigger(state: State, handler: @escaping () -> Void) { open() }
  func open() { submittedAtOpen = GKLeaderboard.submitted; isPresentingGameCenter = true }
}
class GKAchievement {
  init(identifier: String) {}
  var percentComplete = 0.0
  var showsCompletionBanner = false
  static func report(_ list: [GKAchievement], withCompletionHandler completionHandler: @escaping (Error?) -> Void) { completionHandler(nil) }
}
