import Foundation
@propertyWrapper struct Published<Value> { var wrappedValue: Value }
protocol ObservableObject {}
enum SessionInputMode { case builtIn, osIME, builtInKorean10Key }
enum GameCompetition { case officialDeck, weeklyPiyoCup }
struct GameRecord {
 enum Mode { case game, lesson }
 var id = UUID()
 var mode = Mode.game
 var deckId = "flow_topik_beginner"
 var deckVersion: Int? = 3
 var competition: GameCompetition? = .officialDeck
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
 func present(_ other: UIViewController, animated: Bool) { other.presentingViewController = self; presentedViewController = other }
}
class UINavigationController: UIViewController { var visibleViewController: UIViewController? }
class UITabBarController: UIViewController { var selectedViewController: UIViewController? }
class UIScene {}
class UIWindow { var isKeyWindow = true; var rootViewController: UIViewController? = UIViewController() }
class UIWindowScene: UIScene { var windows = [UIWindow()] }
class UIApplication { static let shared = UIApplication(); var connectedScenes: [UIScene] = [UIWindowScene()] }
class GKPlayer { var gamePlayerID = "audit-only-player"; var displayName = "Audit" }
class GKLocalPlayer: GKPlayer {
 static let local = GKLocalPlayer()
 var isAuthenticated = false
 var authenticateHandler: ((UIViewController?, Error?)->Void)?
}
struct GKReleaseState: OptionSet { let rawValue: Int; static let released = Self(rawValue:1) }
class GKLeaderboard {
 enum PlayerScope { case global }
 enum TimeScope { case allTime }
 struct Entry { var rank: Int }
 let baseLeaderboardID: String
 var releaseState = GKReleaseState.released
 init(_ id: String) { baseLeaderboardID = id }
 static var submitted: [(String,Int)] = []
 static var failSubmission = false
 static var holdSubmission = false
 static var entries: [String:Entry] = [:]
 static func loadLeaderboards(IDs: [String]?, completionHandler: @escaping ([GKLeaderboard]?,Error?)->Void) {
  completionHandler((IDs ?? GameCenterLeaderboard.allCases.map(\.rawValue)).map(GKLeaderboard.init),nil)
 }
 static func submitScore(_ score:Int, context:Int, player:GKPlayer, leaderboardIDs:[String], completionHandler:@escaping (Error?)->Void) {
  for id in leaderboardIDs { submitted.append((id,score)) }
  if holdSubmission { return }
  completionHandler(failSubmission ? NSError(domain:"AuditNetwork",code:1) : nil)
 }
 func loadEntries(for scope:PlayerScope,timeScope:TimeScope,range:NSRange,completionHandler:@escaping (Entry?,[Entry]?,Int,Error?)->Void) { completionHandler(Self.entries[baseLeaderboardID],[],0,nil) }
}
class GKAccessPoint {
 enum State { case leaderboards }
 static let shared = GKAccessPoint()
 var isPresentingGameCenter = false
 var submissionCountAtOpen = -1
 func trigger(leaderboardID:String,playerScope:GKLeaderboard.PlayerScope,timeScope:GKLeaderboard.TimeScope,handler:(()->Void)?) { open() }
 func trigger(state:State,handler:@escaping ()->Void) { open() }
 func open() { submissionCountAtOpen = GKLeaderboard.submitted.count; isPresentingGameCenter = true }
}
class GKAchievement {
 init(identifier:String) {}
 var percentComplete = 0.0
 var showsCompletionBanner = false
 static func report(_ list:[GKAchievement],withCompletionHandler completionHandler:@escaping (Error?)->Void) { completionHandler(nil) }
}
