import Foundation
@main struct Audit {
 @MainActor static func pause() async { try? await Task.sleep(nanoseconds:350_000_000) }
 @MainActor static func service() -> GameCenterService { GameCenterService(isEnabled:true,availabilityContract:.init(rawLeaderboardIDs:GameCenterLeaderboard.allCases.map(\.rawValue))) }
 @MainActor static func main() async {
  let first = service(); let record = GameRecord(score:1234)
  first.updateLocalState(records:[record],growth:.empty)
  first.submitScore(for:record)
  first.showLeaderboard(for:record)
  GKLocalPlayer.local.isAuthenticated = true
  GKLocalPlayer.local.authenticateHandler?(nil,nil)
  await pause()
  print("AUTH_DASHBOARD_OPEN submissionCount=\(GKAccessPoint.shared.submissionCountAtOpen) currentlySubmitted=\(GKLeaderboard.submitted.map{$0.1})")
  precondition(GKAccessPoint.shared.isPresentingGameCenter)
  precondition(GKLeaderboard.submitted.isEmpty)
  GKAccessPoint.shared.isPresentingGameCenter = false
  await pause()
  print("AFTER_DASHBOARD_CLOSE submissions=\(GKLeaderboard.submitted.map{$0.1})")
  precondition(GKLeaderboard.submitted.map{$0.1} == [1234])

  GKLeaderboard.submitted = []
  let retry = service(); retry.prepare(); await pause()
  GKLeaderboard.failSubmission = true
  retry.submitScore(for:GameRecord(score:9000)); await pause()
  precondition(retry.lastSyncFailed)
  GKLeaderboard.failSubmission = false
  retry.updateSceneActivity(false); retry.updateSceneActivity(true); await pause()
  print("FOREGROUND_AFTER_FAILURE submissions=\(GKLeaderboard.submitted.map{$0.1}) failed=\(retry.lastSyncFailed)")
  precondition(GKLeaderboard.submitted.map{$0.1} == [9000])
  retry.submitScore(for:GameRecord(score:3000)); await pause()
  print("NEXT_LOWER_GAME submissions=\(GKLeaderboard.submitted.map{$0.1}) failed=\(retry.lastSyncFailed)")
  precondition(GKLeaderboard.submitted.map{$0.1} == [9000,3000])
  retry.synchronize(); await pause()
  print("EXPLICIT_SYNC submissions=\(GKLeaderboard.submitted.map{$0.1})")
  precondition(GKLeaderboard.submitted.map{$0.1} == [9000,3000,9000])

  GKLeaderboard.submitted = []
  let stuck = service(); stuck.prepare(); await pause()
  GKLeaderboard.holdSubmission = true
  stuck.submitScore(for:GameRecord(score:8000)); await pause()
  stuck.synchronize(); await pause()
  stuck.submitScore(for:GameRecord(score:4000)); await pause()
  print("NO_CALLBACK_RESYNC submissions=\(GKLeaderboard.submitted.map{$0.1}) failed=\(stuck.lastSyncFailed)")
  precondition(GKLeaderboard.submitted.map{$0.1} == [8000])
  GKLeaderboard.holdSubmission = false
  GKLeaderboard.submitted = []
  GKLeaderboard.entries = [:]
  let ranks = service(); ranks.prepare(); await pause()
  let rankedRecord = GameRecord(score:7000)
  ranks.submitScore(for:rankedRecord); await pause()
  precondition(ranks.rank(for:rankedRecord) == nil)
  GKLeaderboard.entries[GameCenterLeaderboard.flowBeginner.rawValue] = .init(rank:7)
  ranks.synchronize(); await pause()
  print("DELAYED_SERVER_ENTRY rankAfterSync=\(String(describing:ranks.rank(for:rankedRecord))) backendRank=7")
  precondition(ranks.rank(for:rankedRecord) == nil)
  print("AUDIT_REPRODUCTIONS_CONFIRMED (mock GameKit; no network submission)")
 }
}
