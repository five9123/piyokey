import Foundation

@main struct AvailabilityAudit {
  static let flowIDs: Set<String> = [
    "piyokey.v4.flow.beginner", "piyokey.v4.flow.intermediate", "piyokey.v4.flow.advanced",
  ]
  static let legacyIDs = [
    "piyokey.v3.flow.beginner", "piyokey.v3.flow.intermediate", "piyokey.v3.flow.advanced",
    "piyokey.v3.cup.weekly.flow",
  ]

  @MainActor static func pause() async {
    try? await Task.sleep(nanoseconds: 350_000_000)
  }

  @MainActor static func fullList() -> [GKLeaderboard] {
    (GameCenterLeaderboard.allCases.map(\.rawValue) + legacyIDs).map(GKLeaderboard.init)
  }

  @MainActor static func scenario(_ name: String, list: [GKLeaderboard], blocked: Bool) async {
    GKLeaderboard.fullListOverride = list
    GKLeaderboard.fullListLoadCount = 0
    GKLeaderboard.submitted = []
    let service = GameCenterService(
      isEnabled: true,
      availabilityContract: .init(rawLeaderboardIDs: GameCenterLeaderboard.allCases.map(\.rawValue))
    )
    let records = zip(["beginner", "intermediate", "advanced"], [464, 478, 220]).map {
      GameRecord(deckId: "flow_topik_\($0.0)", score: $0.1)
    }
    precondition(records.allSatisfy { service.isLeaderboardAvailable(for: $0) })
    service.prepare()
    await pause()
    let buttons = records.map { service.isLeaderboardAvailable(for: $0) }
    precondition(buttons.allSatisfy { $0 == !blocked })
    for record in records { service.submitScore(for: record) }
    await pause()
    let flowSubmissions = GKLeaderboard.submitted.filter { flowIDs.contains($0.0) }.count
    precondition(flowSubmissions == (blocked ? 0 : 3))
    let rain = GameRecord(deckId: "acid_rain_topik_beginner", score: 100)
    precondition(service.isLeaderboardAvailable(for: rain))
    service.submitScore(for: rain)
    await pause()
    precondition(GKLeaderboard.submitted.contains { $0.0 == "piyokey.v3.acid_rain.beginner" })
    print("\(name) returned=\(list.count) flowButtons=\(buttons) flowSubmissions=\(flowSubmissions) rainSubmitted=true")

    if blocked {
      // A later healthy server response will not be requested in this session.
      GKLeaderboard.fullListOverride = fullList()
      service.updateSceneActivity(false)
      service.updateSceneActivity(true)
      service.prepare()
      service.synchronize()
      await pause()
      let afterRecovery = records.map { service.isLeaderboardAvailable(for: $0) }
      precondition(afterRecovery.allSatisfy { !$0 })
      precondition(GKLeaderboard.fullListLoadCount == 1)
      print("\(name)_AFTER_RECOVERY fullListLoads=1 flowButtons=\(afterRecovery)")
    }
  }

  @MainActor static func main() async {
    GKLocalPlayer.local.isAuthenticated = true
    await scenario("ALL_20_RELEASED_CONTROL", list: fullList(), blocked: false)
    await scenario(
      "FLOW_MISSING_IN_SUCCESSFUL_RESPONSE",
      list: fullList().filter { !flowIDs.contains($0.baseLeaderboardID) }, blocked: true
    )
    let list = fullList()
    for board in list where flowIDs.contains(board.baseLeaderboardID) { board.releaseState = [] }
    await scenario("ALL_20_BUT_FLOW_RELEASE_FLAG_ABSENT", list: list, blocked: true)
    print("AVAILABILITY_BLOCKING_REPRODUCED (mock GameKit; device response remains unknown)")
  }
}
