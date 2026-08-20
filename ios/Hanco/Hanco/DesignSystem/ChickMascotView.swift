import SwiftUI
import UIKit

private enum MascotPalette {
  static let sunny = Color(red: 1.00, green: 0.76, blue: 0.18)
}

private enum MascotFeedback {
  private static let hapticGenerator = UIImpactFeedbackGenerator(style: .soft)

  static func haptic() {
    guard UserDefaults.standard.object(forKey: KeyboardPreferenceKeys.hapticsEnabled) == nil
      || UserDefaults.standard.bool(forKey: KeyboardPreferenceKeys.hapticsEnabled)
    else { return }
    hapticGenerator.prepare()
    hapticGenerator.impactOccurred(intensity: 0.75)
  }

  static func chirpHappy() {
    HancoSoundEngine.shared.play(.completion(combo: 2))
  }

  static func chirpExcited() {
    HancoSoundEngine.shared.play(.completion(combo: 10))
  }
}

/// The Hanco mascot, ported from the Swift vector companion in HanTap: a chick (병아리) that reacts to typing (PRD §7.1), grows
/// with curriculum progress, and can be petted. Pure vector — no assets.
///
/// Design source: the mascot direction board. Geometry authored in a 200×200
/// design space, scaled by `size`.
public enum MascotMood: Equatable {
    case idle, happy, cheer, oops, love, proud, focus, sleepy
    case sulk, surprise, grit, dizzy, wink, shy, eureka, satisfied
}

/// A small, deterministic policy for idle-screen petting so the gesture layer and
/// its tests agree on when the progressively shyer reaction appears.
enum MascotPettingPolicy {
    static let doubleTapWindowNanoseconds: UInt64 = 280_000_000
    static let tapSeriesResetNanoseconds: UInt64 = 1_800_000_000
    static let happyRevertNanoseconds: UInt64 = 900_000_000
    static let cheerRevertNanoseconds: UInt64 = 1_100_000_000

    static func singleTapMood(consecutiveTapCount: Int) -> MascotMood {
        consecutiveTapCount >= 3 ? .shy : .happy
    }
}

private struct MascotTouchInteractionModifier: ViewModifier {
    let isEnabled: Bool
    let onTap: () -> Void
    let onAccessibilityActivate: () -> Void
    let onLongPress: (() -> Void)?

    @ViewBuilder
    func body(content: Content) -> some View {
        if isEnabled {
            content
                .contentShape(Rectangle())
                .overlay {
                    Rectangle()
                        .fill(Color.clear)
                        .contentShape(Rectangle())
                        .highPriorityGesture(
                            TapGesture(count: 1)
                                .onEnded(onTap)
                        )
                        .simultaneousGesture(
                            LongPressGesture(minimumDuration: 0.5)
                                .onEnded { _ in onLongPress?() }
                        )
                        .accessibilityHidden(true)
                }
                .accessibilityAddTraits(.isButton)
                .accessibilityHint(Text("mascot.interaction_hint"))
                .accessibilityAction {
                    onAccessibilityActivate()
                }
        } else {
            content
        }
    }
}

/// A repeatable, event-driven motion independent from the longer-lived facial mood.
public enum MascotReaction: Equatable {
    case none
    case correctJamo
    case syllableCompleted
    case wordCompleted
    case comboMilestone(Int)
    case mistake
    case perfectSession
    case rhythm(Int)
    case startle
    case growthTransition
    case stretch
    case eggKnock
    case reviewGraduated
    case newBest
    case lessonStreak(Int)
    case eureka
}

/// The front drawing can lean into a three-quarter silhouette for game motion and conversation.
public enum MascotPose: Equatable {
    case front, threeQuarterLeft, threeQuarterRight
}

/// 홈·연습·게임의 작은 슬롯 안에서 실루엣이 사라지지 않게 하는 공통 경계.
/// 표정·날갯짓·점프는 유지하되 90° edge-on 회전과 과도한 이동/확대만 제한한다.
enum MascotMotionPolicy {
    static func horizontalOffset(_ rawValue: CGFloat, size: CGFloat) -> CGFloat {
        min(max(rawValue, -size * 0.045), size * 0.045)
    }

    static func verticalOffset(_ rawValue: CGFloat, size: CGFloat) -> CGFloat {
        min(max(rawValue, -size * 0.085), size * 0.05)
    }

    static func reactionScale(_ rawValue: CGFloat) -> CGFloat {
        min(max(rawValue, 0.72), 1.08)
    }

    static func yawDegrees(pose: MascotPose, isLookingBack: Bool) -> Double {
        guard isLookingBack else {
            switch pose {
            case .front: return 0
            case .threeQuarterLeft: return -12
            case .threeQuarterRight: return 12
            }
        }
        // 180° 회전은 중간에 폭이 0이 되어 캐릭터가 사라진 것처럼 보인다.
        switch pose {
        case .front, .threeQuarterRight: return 24
        case .threeQuarterLeft: return -24
        }
    }

    static func capOffset(_ rawValue: CGFloat, size: CGFloat) -> CGFloat {
        min(max(rawValue, -size * 0.10), size * 0.04)
    }
}

/// Permanent and recent-activity axes layered on top of the chapter-driven stage.
public struct MascotGrowthAppearance: Equatable {
    public let combProgress: CGFloat
    public let bodyScale: CGFloat
    public let featherSheen: CGFloat
    public let crackProgress: Int

    public static let standard = MascotGrowthAppearance(
        combProgress: 0,
        bodyScale: 1,
        featherSheen: 0.35,
        crackProgress: 1
    )

    public init(
        typedJamoCount: Int,
        longestStreak: Int,
        activeDaysInLastWeek: Int,
        completedChapterCount: Int
    ) {
        combProgress = min(max(CGFloat(typedJamoCount) / 12_000, 0), 1)
        bodyScale = 1 + min(max(CGFloat(longestStreak) / 30, 0), 1) * 0.08
        featherSheen = 0.18 + min(max(CGFloat(activeDaysInLastWeek) / 7, 0), 1) * 0.82
        crackProgress = min(max(completedChapterCount + 1, 1), 3)
    }

    public init(
        combProgress: CGFloat,
        bodyScale: CGFloat,
        featherSheen: CGFloat,
        crackProgress: Int
    ) {
        self.combProgress = min(max(combProgress, 0), 1)
        self.bodyScale = min(max(bodyScale, 0.92), 1.12)
        self.featherSheen = min(max(featherSheen, 0), 1)
        self.crackProgress = min(max(crackProgress, 1), 3)
    }
}

public enum MascotFanColor: String, Equatable, CaseIterable {
    case pink, lavender, mint, sky, coral, gold

    public static func forDeckTags(_ tags: [String]) -> MascotFanColor {
        let joined = tags.joined(separator: "|")
        let checksum = joined.unicodeScalars.reduce(0) { ($0 &* 31 &+ Int($1.value)) % allCases.count }
        return allCases[checksum]
    }
}

/// Growth stage, driven by cleared curriculum chapters (커리큘럼 연동).
public enum MascotStage: Equatable, Comparable {
    case egg, cracking, hatching, chick, rooster

    /// VoiceOver가 읽는 성장 단계.
    var l10nKey: String {
        switch self {
        case .egg:      return "a11y.stage.egg"
        case .cracking: return "a11y.stage.cracking"
        case .hatching: return "a11y.stage.hatching"
        case .chick:    return "a11y.stage.chick"
        case .rooster:  return "a11y.stage.rooster"
        }
    }

    public static func forClearedChapters(_ n: Int) -> MascotStage {
        switch n {
        case ..<1: return .cracking
        case 1...2: return .hatching
        case 3...5: return .chick
        default: return .rooster
        }
    }

    /// Ordering for "성장했다" 감지 (egg < … < rooster).
    private var rank: Int {
        switch self {
        case .egg: return 0
        case .cracking: return 1
        case .hatching: return 2
        case .chick: return 3
        case .rooster: return 4
        }
    }
    public static func < (l: MascotStage, r: MascotStage) -> Bool { l.rank < r.rank }
}

/// Hand/head accessory (표정과 자유 조합).
public enum MascotProp: String, Equatable, CaseIterable {
    case none
    case lightstick   // 응원봉 — 추시 덱
    case gradCap      // 졸업모 — TOPIK·検定 덱, 장닭
    case headphones   // 헤드폰 — K-POP·음악 덱
    case travelCase
    case coffeeCup
    case microphone
    case heartBalloon
    case foodPlate
    case ribbon
    case glasses
    case gameCenterTrophy

}

/// 알 무늬 — 온보딩 목표별 개인화 (C5).
public enum MascotEggPattern: String, Equatable, CaseIterable, Identifiable {
    case plain, hearts, stars, polka

    public var id: String { rawValue }

    /// 온보딩 목표 raw value → 무늬.
    public static func forGoal(_ goalRaw: String?) -> MascotEggPattern {
        switch goalRaw {
        case "trends", "oshi": return .hearts
        case "topik", "exam": return .stars
        case "travel": return .polka
        default: return .plain
        }
    }
}

public struct MascotView: View {
    public var mood: MascotMood
    public var stage: MascotStage
    public var prop: MascotProp
    public var eggPattern: MascotEggPattern
    /// 시선 (-1 왼쪽 … +1 오른쪽) — 게임에서 흐르는 카드를 눈으로 따라감.
    public var gazeX: CGFloat
    /// 시선 (-1 위 … +1 아래) — 오타 뒤 다음 키 가이드를 바라봄.
    public var gazeY: CGFloat
    /// 같은 표정이 연속되어도 다시 재생되는 짧은 학습 이벤트 리액션.
    public var reaction: MascotReaction
    public var reactionRevision: Int
    public var pose: MascotPose
    public var intensity: CGFloat
    public var growthAppearance: MascotGrowthAppearance
    public var fanColor: MascotFanColor
    public var nameTag: String?
    public var speech: String?
    public var showsFriend: Bool
    /// 쓰다듬기 허용 (홈): 탭=기쁨, 더블탭=신남, 길게=옷장.
    public var interactive: Bool
    public var onOpenCloset: (() -> Void)?
    public var size: CGFloat
    /// 타이핑 화면용 차분 모드: 상시 숨쉬기(위아래 반복)만 끈다 —
    /// 아이들 변주(쪼기·뒤뚱·파닥…)·깜빡임·무드 리액션은 유지.
    public var calm: Bool

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @State private var breathe = false
    @State private var bounce = false
    @State private var squash = false
    @State private var blink = false
    @State private var lovePulse = false
    @State private var petMood: MascotMood?
    @State private var eventMood: MascotMood?
    @State private var idlePeck = false
    @State private var idleWaddle: CGFloat = 0
    @State private var idleTilt: Double = 0        // 갸웃 (도)
    @State private var idleHop = false             // 폴짝
    @State private var idleGaze: CGFloat = 0       // 두리번 (gazeX에 가산)
    @State private var idleFlap: Double = 0        // 날개 파닥 (가산 각)
    @State private var idleShiver: CGFloat = 0     // 부르르 (x 흔들림)
    @State private var featherSeq = 0
    @State private var showFeathers = false
    @State private var reactionYOffset: CGFloat = 0
    @State private var reactionScale: CGFloat = 1
    @State private var reactionTilt: Double = 0
    @State private var reactionFlap: Double = 0
    @State private var reactionGlow = false
    @State private var propRotation: Double = 0
    @State private var capTossOffset: CGFloat = 0
    @State private var capTossRotation: Double = 0
    @State private var headphoneBeat: CGFloat = 1
    @State private var displayedReaction: MascotReaction = .none
    @State private var reactionTask: Task<Void, Never>?
    @State private var idleYawn = false
    @State private var idleOneLeg = false
    @State private var idleBackTurn = false
    @State private var idlePreen = false
    @State private var idleSpin: Double = 0
    @State private var idleStomp: CGFloat = 0
    @State private var idleExtraCrack = false
    @State private var shyPetCount = 0
    @State private var tapRecognitionTask: Task<Void, Never>?
    @State private var tapSeriesResetTask: Task<Void, Never>?
    @State private var petMoodResetTask: Task<Void, Never>?

    public init(mood: MascotMood = .idle, stage: MascotStage = .chick,
                prop: MascotProp = .none,
                eggPattern: MascotEggPattern = .plain,
                gazeX: CGFloat = 0, gazeY: CGFloat = 0,
                reaction: MascotReaction = .none, reactionRevision: Int = 0,
                pose: MascotPose = .front, intensity: CGFloat = 0,
                growthAppearance: MascotGrowthAppearance = .standard,
                fanColor: MascotFanColor = .pink,
                nameTag: String? = nil, speech: String? = nil,
                showsFriend: Bool = false,
                interactive: Bool = false,
                onOpenCloset: (() -> Void)? = nil, size: CGFloat = 96,
                calm: Bool = false) {
        self.calm = calm
        self.mood = mood
        self.stage = stage
        self.prop = prop
        self.eggPattern = eggPattern
        self.gazeX = gazeX
        self.gazeY = gazeY
        self.reaction = reaction
        self.reactionRevision = reactionRevision
        self.pose = pose
        self.intensity = min(max(intensity, 0), 1)
        self.growthAppearance = growthAppearance
        self.fanColor = fanColor
        self.nameTag = nameTag
        self.speech = speech
        self.showsFriend = showsFriend
        self.interactive = interactive
        self.onOpenCloset = onOpenCloset
        self.size = size
    }

    // MARK: - 알 단계 변주 안무 (3종)

    /// 알이 이따금 살아있음을 내비친다 — 부화 기대(온보딩 서사)의 유지 장치.
    private func runEggVariation(_ pick: Int) async {
        func nap(_ ms: UInt64) async { try? await Task.sleep(nanoseconds: ms * 1_000_000) }
        switch pick {
        case 0:
            // 꿈틀 — 좌우로 갸우뚱갸우뚱.
            for deg in [-5.0, 5.0, -3.0, 0.0] {
                withAnimation(.easeInOut(duration: 0.16)) { idleTilt = deg }
                await nap(170)
            }
        case 1:
            // 부르르 — 안에서 몸부림.
            for i in 0..<5 {
                withAnimation(.linear(duration: 0.05)) {
                    idleShiver = (i.isMultiple(of: 2) ? 2.0 : -2.0) * u
                }
                await nap(52)
            }
            withAnimation(.easeOut(duration: 0.09)) { idleShiver = 0 }
        case 2:
            // 톡톡 — 안에서 두 번 치는 듯 작게 튀어오름.
            for _ in 0..<2 {
                idleHop = true
                await nap(170)
                idleHop = false
                await nap(200)
            }
        default:
            // 금이 하나 더 — 새 균열이 잠깐 보이고 글로우와 함께 사라진다.
            withAnimation(.easeOut(duration: 0.16)) {
                idleExtraCrack = true
                reactionGlow = true
                reactionScale = 1.035
            }
            await nap(260)
            withAnimation(.spring(response: 0.22, dampingFraction: 0.65)) {
                idleExtraCrack = false
                reactionGlow = false
                reactionScale = 1
            }
        }
    }

    // MARK: - Idle 변주 안무 (7종)

    /// 각 안무는 짧게(0.3~1.2초) 끝나고 원상 복귀한다 — 살아있는 펫 감각의
    /// 핵심은 빈도·다양성이지 길이가 아니다 (§7.1).
    private func runIdleVariation(_ pick: Int) async {
        func nap(_ ms: UInt64) async { try? await Task.sleep(nanoseconds: ms * 1_000_000) }
        switch pick {
        case 0:
            // 모이 쪼기 — 절반 확률로 두 번 연속 꾸벅.
            let times = Bool.random() ? 2 : 1
            for _ in 0..<times {
                idlePeck = true
                await nap(200)
                idlePeck = false
                await nap(160)
            }
        case 1:
            // 뒤뚱뒤뚱 두 스텝.
            for dx in [4.0, -4.0, 0.0] {
                withAnimation(.easeInOut(duration: 0.14)) { idleWaddle = dx * u }
                await nap(150)
            }
        case 2:
            // 폴짝 — 착지 스쿼시까지.
            idleHop = true
            await nap(240)
            idleHop = false
            await nap(160)
            withAnimation(.easeOut(duration: 0.07)) { squash = true }
            await nap(90)
            withAnimation(.spring(response: 0.2, dampingFraction: 0.5)) { squash = false }
        case 3:
            // 갸웃 — 잠시 머물다 복귀.
            idleTilt = Bool.random() ? -8 : 8
            await nap(620)
            idleTilt = 0
        case 4:
            // 두리번 — 왼쪽·오른쪽 보고 정면.
            let first: CGFloat = Bool.random() ? -1 : 1
            idleGaze = first
            await nap(420)
            idleGaze = -first
            await nap(420)
            idleGaze = 0
        case 5:
            // 날개 파닥파닥 두 번.
            for _ in 0..<2 {
                withAnimation(.spring(response: 0.14, dampingFraction: 0.5)) { idleFlap = 46 }
                await nap(140)
                withAnimation(.spring(response: 0.18, dampingFraction: 0.55)) { idleFlap = 0 }
                await nap(160)
            }
        case 6:
            // 부르르 — 깃털 털기 (빠른 좌우 흔들림).
            for i in 0..<5 {
                withAnimation(.linear(duration: 0.05)) {
                    idleShiver = (i.isMultiple(of: 2) ? 2.2 : -2.2) * u
                }
                await nap(52)
            }
            withAnimation(.easeOut(duration: 0.09)) { idleShiver = 0 }
        case 7:
            // 하품 — 밤의 sleepy 표정에서는 입과 날개가 함께 늘어진다.
            withAnimation(.easeOut(duration: 0.18)) {
                idleYawn = true
                reactionScale = 1.035
            }
            await nap(520)
            withAnimation(.easeInOut(duration: 0.18)) {
                idleYawn = false
                reactionScale = 1
            }
        case 8:
            // 한 발 서기 — 잠시 균형을 잡다가 원래 자세로 돌아온다.
            withAnimation(.spring(response: 0.3, dampingFraction: 0.7)) { idleOneLeg = true }
            await nap(1_350)
            withAnimation(.spring(response: 0.28, dampingFraction: 0.55)) {
                idleOneLeg = false
                idleTilt = Bool.random() ? -5 : 5
            }
            await nap(220)
            idleTilt = 0
        case 9:
            // 뒤돌아보기 — edge-on으로 사라지지 않는 3/4 각도까지만 돌아본다.
            withAnimation(.easeInOut(duration: 0.3)) { idleBackTurn = true }
            await nap(560)
            withAnimation(.easeInOut(duration: 0.3)) { idleBackTurn = false }
        case 10:
            // 부리로 깃 고르기.
            withAnimation(.easeInOut(duration: 0.22)) {
                idlePreen = true
                idleTilt = -24
                idleFlap = 28
            }
            await nap(620)
            withAnimation(.spring(response: 0.28, dampingFraction: 0.62)) {
                idlePreen = false
                idleTilt = 0
                idleFlap = 0
            }
        case 11:
            // 제자리 한 바퀴.
            withAnimation(.easeInOut(duration: 0.72)) { idleSpin += 360 }
            await nap(760)
        case 12:
            // 발 구르기 두 번 콩콩.
            for _ in 0..<2 {
                withAnimation(.easeOut(duration: 0.08)) { idleStomp = 4 * u }
                await nap(90)
                withAnimation(.spring(response: 0.15, dampingFraction: 0.5)) { idleStomp = 0 }
                await nap(130)
            }
        default:
            // 3/4 측면으로 한 걸음 걸어 나왔다가 정면으로 복귀한다.
            withAnimation(.easeInOut(duration: 0.22)) { idleWaddle = 7 * u }
            await nap(320)
            withAnimation(.spring(response: 0.24, dampingFraction: 0.62)) { idleWaddle = 0 }
        }
    }

    /// 실제 표시 무드 — 쓰다듬기 반응이 외부 무드를 잠시 덮는다.
    private var m: MascotMood { eventMood ?? petMood ?? mood }

    private var u: CGFloat { size / 200 * 1.7 }

    // MARK: - Palette

    private let yellowTop = Color(red: 1.00, green: 0.88, blue: 0.48)
    private let yellowBottom = Color(red: 1.00, green: 0.75, blue: 0.26)
    private let line = Color(red: 0.91, green: 0.59, blue: 0.05)
    private let deep = Color(red: 0.91, green: 0.45, blue: 0.05)
    private let beakColor = Color(red: 1.00, green: 0.54, blue: 0.24)
    private let blushColor = Color(red: 1.00, green: 0.42, blue: 0.29)
    private let cream = Color(red: 1.00, green: 0.95, blue: 0.77)
    private let shell = Color(red: 1.00, green: 0.97, blue: 0.90)
    private let ink = Color(red: 0.23, green: 0.14, blue: 0.19)
    private let charcoal = Color(red: 0.29, green: 0.27, blue: 0.38)
    private let sweatBlue = Color(red: 0.43, green: 0.78, blue: 0.94)
    private let mintDark = Color(red: 0.12, green: 0.64, blue: 0.49)

    private var bodyGradient: LinearGradient {
        LinearGradient(colors: [yellowTop, yellowBottom], startPoint: .top, endPoint: .bottom)
    }

    // MARK: - Body

    public var body: some View {
        ZStack {
            if showsFriend, stage == .chick || stage == .rooster { friendView }
            floatingBits

            switch stage {
            case .egg: eggView(cracked: false)
            case .cracking: eggView(cracked: true)
            case .hatching: hatchingView
            case .chick, .rooster: chickView
            }

            if stage == .chick || stage == .rooster {
                propView
            }

            if showFeathers {
                FeatherBurst(u: u, color: yellowBottom).id(featherSeq)
            }

            reactionBadge
            speechBubble
            nameTagView
        }
        // E1: Metal 래스터화. drawingGroup은 경계 밖을 클리핑하므로, 소품(졸업모·
        // 응원봉·말풍선 문자)이 들어가도록 래스터 캔버스를 먼저 넓혀 둔다.
        .frame(width: size * 1.9, height: size * 1.8)
        .drawingGroup()
        .rotationEffect(.degrees((m == .oops ? 10 : (idlePeck ? 9 : idleTilt)) + reactionTilt + idleSpin))
        .rotation3DEffect(
            .degrees(MascotMotionPolicy.yawDegrees(pose: pose, isLookingBack: idleBackTurn)),
            axis: (x: 0, y: 1, z: 0),
            perspective: 0.45
        )
        .offset(
            x: MascotMotionPolicy.horizontalOffset(idleWaddle + idleShiver, size: size),
            y: MascotMotionPolicy.verticalOffset(
                (bounce ? -size * 0.10 : 0) + (idleHop ? -size * 0.07 : 0)
                    + reactionYOffset + idleStomp,
                size: size
            )
        )
        .scaleEffect(m == .proud ? 1.06 : 1.0)
        .scaleEffect(growthAppearance.bodyScale)
        .scaleEffect(1 + intensity * 0.025)
        .scaleEffect(MascotMotionPolicy.reactionScale(reactionScale))
        .scaleEffect(y: squash ? 0.90 : (breathe ? 1.03 : 0.99), anchor: .bottom)
        .animation(.spring(response: 0.3, dampingFraction: 0.55), value: m)
        .animation(.spring(response: 0.16, dampingFraction: 0.5), value: idlePeck)
        .animation(.spring(response: 0.32, dampingFraction: 0.6), value: idleTilt)
        .animation(.spring(response: 0.24, dampingFraction: 0.48), value: idleHop)
        .animation(.easeInOut(duration: 1.6).repeatForever(autoreverses: true), value: breathe)
        // 수십 개의 도형이 각각 VoiceOver 요소로 잡히면 화면을 훑을 수 없다.
        // 캐릭터 하나로 합치고, 성장 단계를 값으로 읽어 준다 (§F8 성장 보상).
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: AppLocalization.string("mascot.accessibility_label")))
        .accessibilityValue(Text(verbatim: mascotAccessibilityValue))
        .accessibilityIdentifier(interactive ? "mascot.interactive" : "mascot.current")
        .onAppear {
            if !reduceMotion, !calm {   // E2: Reduce Motion·calm이면 상시 모션 없음
                breathe = true
            }
            if !reduceMotion { lovePulse = true }
            if reactionRevision > 0 { playReaction() }
        }
        .onChange(of: m) { newMood in
            guard newMood == .cheer else { return }
            featherSeq += 1
            showFeathers = true
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.9) { showFeathers = false }
            guard !reduceMotion else { return }
            withAnimation(.spring(response: 0.25, dampingFraction: 0.45)) { bounce = true }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) {
                withAnimation(.spring(response: 0.22, dampingFraction: 0.5)) { bounce = false }
                // 착지 스쿼시 (A4): 바닥 앵커로 납작 눌렸다 복귀.
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.18) {
                    withAnimation(.easeOut(duration: 0.08)) { squash = true }
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.10) {
                        withAnimation(.spring(response: 0.22, dampingFraction: 0.5)) { squash = false }
                    }
                }
            }
        }
        .onChange(of: reactionRevision) { _ in
            playReaction()
        }
        // 깜빡임 (idle 계열 dot-eye) — 랜덤 주기, 가끔 두 번 연속.
        .task {
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: UInt64.random(in: 2_200...4_200) * 1_000_000)
                let times = Int.random(in: 0..<5) == 0 ? 2 : 1
                for _ in 0..<times {
                    withAnimation(.easeIn(duration: 0.07)) { blink = true }
                    try? await Task.sleep(nanoseconds: 130_000_000)
                    withAnimation(.easeOut(duration: 0.10)) { blink = false }
                    try? await Task.sleep(nanoseconds: 180_000_000)
                }
            }
        }
        // 아이들 변주 (A3): 병아리 7종(쪼기·뒤뚱·폴짝·갸웃·두리번·파닥·부르르),
        // 알 단계 3종(꿈틀·부르르·톡톡) — 알도 살아있어야 부화 기대가 생긴다.
        .task {
            guard !reduceMotion else { return }
            var lastPick = -1
            try? await Task.sleep(nanoseconds: 1_500_000_000)   // 첫 변주는 이르게
            while !Task.isCancelled {
                guard m == .idle || m == .sleepy else {
                    try? await Task.sleep(nanoseconds: 1_200_000_000)
                    continue
                }
                let isEgg = stage == .egg || stage == .cracking
                let count = isEgg ? 4 : 14
                // 같은 동작 연속 반복 방지.
                var pick = m == .sleepy && !isEgg ? 7 : Int.random(in: 0..<count)
                if pick == lastPick { pick = (pick + 1 + Int.random(in: 0..<(count - 1))) % count }
                lastPick = pick
                if isEgg {
                    await runEggVariation(pick)
                } else {
                    await runIdleVariation(pick)
                }
                try? await Task.sleep(nanoseconds: UInt64.random(in: 2_200...4_500) * 1_000_000)
            }
        }
        .frame(width: size * 1.25, height: size * 1.2)
        // 쓰다듬기 (B1) — idle 화면에서 명시적으로 interactive를 켠 경우만.
        .modifier(
            MascotTouchInteractionModifier(
                isEnabled: interactive,
                onTap: handlePetTap,
                onAccessibilityActivate: handleSinglePet,
                onLongPress: onOpenCloset.map { openCloset in
                    {
                        MascotFeedback.haptic()
                        openCloset()
                    }
                }
            )
        )
        .onDisappear {
            reactionTask?.cancel()
            reactionTask = nil
            tapRecognitionTask?.cancel()
            tapRecognitionTask = nil
            tapSeriesResetTask?.cancel()
            tapSeriesResetTask = nil
            petMoodResetTask?.cancel()
            petMoodResetTask = nil
        }
    }

    private var mascotAccessibilityValue: String {
        let stageValue = AppLocalization.string(stage.l10nKey)
        let reactionKey: String?
        switch petMood {
        case .happy: reactionKey = "mascot.accessibility_pet_happy"
        case .cheer: reactionKey = "mascot.accessibility_pet_cheer"
        case .shy: reactionKey = "mascot.accessibility_pet_shy"
        default: reactionKey = nil
        }
        guard let reactionKey else { return stageValue }
        return "\(stageValue), \(AppLocalization.string(reactionKey))"
    }

    private func handleSinglePet() {
        shyPetCount += 1
        tapSeriesResetTask?.cancel()
        tapSeriesResetTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: MascotPettingPolicy.tapSeriesResetNanoseconds)
            guard !Task.isCancelled else { return }
            shyPetCount = 0
        }
        pet(
            MascotPettingPolicy.singleTapMood(consecutiveTapCount: shyPetCount),
            chirp: MascotFeedback.chirpHappy,
            revertNanoseconds: MascotPettingPolicy.happyRevertNanoseconds
        )
    }

    /// Wait briefly before resolving a single pet so two physical taps can
    /// become one excited reaction without emitting the happy sound first.
    private func handlePetTap() {
        if let tapRecognitionTask {
            tapRecognitionTask.cancel()
            self.tapRecognitionTask = nil
            handleDoublePet()
            return
        }

        tapRecognitionTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: MascotPettingPolicy.doubleTapWindowNanoseconds)
            guard !Task.isCancelled else { return }
            tapRecognitionTask = nil
            handleSinglePet()
        }
    }

    private func handleDoublePet() {
        pet(
            .cheer,
            chirp: MascotFeedback.chirpExcited,
            revertNanoseconds: MascotPettingPolicy.cheerRevertNanoseconds
        )
    }

    private func pet(
        _ newMood: MascotMood,
        chirp: () -> Void,
        revertNanoseconds: UInt64
    ) {
        MascotFeedback.haptic()
        chirp()
        petMood = newMood
        petMoodResetTask?.cancel()
        petMoodResetTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: revertNanoseconds)
            guard !Task.isCancelled, petMood == newMood else { return }
            petMood = nil
        }
    }

    private func playReaction() {
        reactionTask?.cancel()
        resetReactionPose()
        let event = reaction

        reactionTask = Task { @MainActor in
            guard event != .none else { return }
            displayedReaction = event

            if reduceMotion {
                if event != .mistake && event != .correctJamo {
                    withAnimation(.easeOut(duration: 0.14)) { reactionGlow = true }
                }
                guard await pauseReaction(milliseconds: 420) else { return }
                resetReactionPose()
                return
            }

            switch event {
            case .none:
                return

            case .correctJamo:
                withAnimation(.easeOut(duration: 0.08)) {
                    reactionYOffset = 2.5 * u
                    reactionScale = 0.98
                }
                guard await pauseReaction(milliseconds: 90) else { return }
                withAnimation(.spring(response: 0.18, dampingFraction: 0.62)) {
                    reactionYOffset = 0
                    reactionScale = 1
                }
                guard await pauseReaction(milliseconds: 210) else { return }

            case .syllableCompleted:
                withAnimation(.spring(response: 0.14, dampingFraction: 0.5)) {
                    reactionFlap = 58
                    reactionYOffset = -3 * u
                    reactionScale = 1.035
                    reactionGlow = true
                    headphoneBeat = 1.045
                }
                guard await pauseReaction(milliseconds: 150) else { return }
                withAnimation(.spring(response: 0.2, dampingFraction: 0.62)) {
                    reactionFlap = 0
                    reactionYOffset = 0
                    reactionScale = 1
                    reactionGlow = false
                    headphoneBeat = 1
                }
                guard await pauseReaction(milliseconds: 260) else { return }

            case .wordCompleted:
                showReactionFeathers()
                withAnimation(.spring(response: 0.2, dampingFraction: 0.46)) {
                    reactionYOffset = -12 * u
                    reactionFlap = 96
                    reactionScale = 1.07
                    reactionGlow = true
                    propRotation = -22
                    headphoneBeat = 1.08
                }
                guard await pauseReaction(milliseconds: 230) else { return }
                withAnimation(.spring(response: 0.24, dampingFraction: 0.58)) {
                    reactionYOffset = 0
                    reactionFlap = 0
                    reactionScale = 0.96
                    propRotation = 12
                    headphoneBeat = 0.97
                }
                guard await pauseReaction(milliseconds: 110) else { return }
                withAnimation(.spring(response: 0.2, dampingFraction: 0.64)) {
                    reactionScale = 1
                    reactionGlow = false
                    propRotation = 0
                    headphoneBeat = 1
                }
                guard await pauseReaction(milliseconds: 240) else { return }

            case .comboMilestone(let count):
                await playComboReaction(count)
                guard !Task.isCancelled else { return }

            case .mistake:
                withAnimation(.easeOut(duration: 0.09)) { reactionTilt = -16 }
                guard await pauseReaction(milliseconds: 100) else { return }
                withAnimation(.easeInOut(duration: 0.1)) { reactionTilt = 7 }
                guard await pauseReaction(milliseconds: 110) else { return }
                withAnimation(.spring(response: 0.2, dampingFraction: 0.65)) {
                    reactionTilt = 0
                }
                guard await pauseReaction(milliseconds: 250) else { return }

            case .perfectSession:
                showReactionFeathers()
                withAnimation(.spring(response: 0.22, dampingFraction: 0.42)) {
                    reactionYOffset = -18 * u
                    reactionFlap = 122
                    reactionScale = 1.12
                    reactionGlow = true
                    propRotation = -34
                    capTossOffset = -54 * u
                    capTossRotation = -22
                    headphoneBeat = 1.13
                }
                guard await pauseReaction(milliseconds: 270) else { return }
                withAnimation(.spring(response: 0.28, dampingFraction: 0.58)) {
                    reactionYOffset = 0
                    reactionFlap = 0
                    reactionScale = 0.95
                    propRotation = 16
                    capTossOffset = 0
                    capTossRotation = 12
                    headphoneBeat = 0.96
                }
                guard await pauseReaction(milliseconds: 140) else { return }
                withAnimation(.spring(response: 0.22, dampingFraction: 0.62)) {
                    reactionScale = 1
                    reactionGlow = false
                    propRotation = 0
                    capTossRotation = 0
                    headphoneBeat = 1
                }
                guard await pauseReaction(milliseconds: 260) else { return }

            case .rhythm(let count):
                eventMood = count >= 15 ? .focus : .happy
                let beats = max(1, min(count / 5, 4))
                for beat in 0..<beats {
                    withAnimation(.easeInOut(duration: 0.11)) {
                        reactionTilt = beat.isMultiple(of: 2) ? -8 : 8
                        reactionYOffset = -3 * u
                        headphoneBeat = 1.08
                        propRotation = beat.isMultiple(of: 2) ? -12 : 12
                    }
                    guard await pauseReaction(milliseconds: 120) else { return }
                }
                withAnimation(.spring(response: 0.2, dampingFraction: 0.64)) {
                    reactionTilt = 0
                    reactionYOffset = 0
                    headphoneBeat = 1
                    propRotation = 0
                }
                guard await pauseReaction(milliseconds: 220) else { return }

            case .startle:
                eventMood = .surprise
                withAnimation(.spring(response: 0.13, dampingFraction: 0.42)) {
                    reactionYOffset = -7 * u
                    reactionScale = 1.12
                    reactionFlap = 40
                }
                guard await pauseReaction(milliseconds: 150) else { return }
                withAnimation(.spring(response: 0.24, dampingFraction: 0.68)) {
                    reactionYOffset = 0
                    reactionScale = 1
                    reactionFlap = 0
                }
                guard await pauseReaction(milliseconds: 300) else { return }

            case .growthTransition:
                eventMood = .eureka
                showReactionFeathers()
                reactionScale = 0.55
                withAnimation(.spring(response: 0.48, dampingFraction: 0.48)) {
                    reactionScale = 1.16
                    reactionGlow = true
                    reactionFlap = 126
                    idleSpin += 360
                }
                guard await pauseReaction(milliseconds: 480) else { return }
                withAnimation(.spring(response: 0.28, dampingFraction: 0.62)) {
                    reactionScale = 1
                    reactionGlow = false
                    reactionFlap = 0
                }
                guard await pauseReaction(milliseconds: 300) else { return }

            case .stretch:
                eventMood = .satisfied
                withAnimation(.easeOut(duration: 0.22)) {
                    reactionScale = 1.08
                    reactionYOffset = -4 * u
                    reactionFlap = 82
                }
                guard await pauseReaction(milliseconds: 420) else { return }
                withAnimation(.spring(response: 0.28, dampingFraction: 0.62)) {
                    reactionScale = 1
                    reactionYOffset = 0
                    reactionFlap = 0
                }
                guard await pauseReaction(milliseconds: 260) else { return }

            case .eggKnock:
                for direction in [-1.0, 1.0, -1.0, 0.0] {
                    withAnimation(.easeInOut(duration: 0.12)) {
                        reactionTilt = direction * 7
                        reactionYOffset = direction == 0 ? 0 : -2 * u
                    }
                    guard await pauseReaction(milliseconds: 130) else { return }
                }
                reactionGlow = true
                guard await pauseReaction(milliseconds: 180) else { return }

            case .reviewGraduated:
                eventMood = .wink
                withAnimation(.spring(response: 0.2, dampingFraction: 0.46)) {
                    reactionScale = 1.1
                    reactionTilt = -8
                    reactionGlow = true
                }
                guard await pauseReaction(milliseconds: 520) else { return }

            case .newBest:
                eventMood = .surprise
                withAnimation(.spring(response: 0.18, dampingFraction: 0.42)) {
                    reactionYOffset = -10 * u
                    reactionScale = 1.13
                    reactionGlow = true
                }
                guard await pauseReaction(milliseconds: 260) else { return }
                eventMood = .proud
                showReactionFeathers()
                guard await pauseReaction(milliseconds: 500) else { return }

            case .lessonStreak(let count):
                eventMood = count >= 3 ? .satisfied : .happy
                withAnimation(.spring(response: 0.22, dampingFraction: 0.48)) {
                    reactionYOffset = -8 * u
                    reactionScale = 1.08
                    reactionGlow = true
                }
                guard await pauseReaction(milliseconds: 480) else { return }

            case .eureka:
                eventMood = .eureka
                withAnimation(.spring(response: 0.2, dampingFraction: 0.46)) {
                    reactionYOffset = -7 * u
                    reactionScale = 1.1
                    reactionGlow = true
                }
                guard await pauseReaction(milliseconds: 520) else { return }
            }

            resetReactionPose()
        }
    }

    @MainActor
    private func playComboReaction(_ count: Int) async {
        showReactionFeathers()
        let tier = count >= 20 ? 3 : (count >= 10 ? 2 : 1)
        let hopCount = tier == 3 ? 2 : 1

        for _ in 0..<hopCount {
            withAnimation(.spring(response: 0.18, dampingFraction: 0.44)) {
                reactionYOffset = CGFloat(-4 - tier * 3) * u
                reactionFlap = Double(48 + tier * 22)
                reactionScale = 1 + CGFloat(tier) * 0.025
                reactionGlow = true
                propRotation = Double(-10 - tier * 7)
                headphoneBeat = 1 + CGFloat(tier) * 0.035
            }
            guard await pauseReaction(milliseconds: 180) else { return }
            withAnimation(.spring(response: 0.2, dampingFraction: 0.58)) {
                reactionYOffset = 0
                reactionFlap = 0
                reactionScale = 1
                propRotation = Double(5 + tier * 3)
                headphoneBeat = 1
            }
            guard await pauseReaction(milliseconds: 140) else { return }
        }

        withAnimation(.easeOut(duration: 0.16)) {
            reactionGlow = false
            propRotation = 0
        }
        _ = await pauseReaction(milliseconds: 180)
    }

    private func showReactionFeathers() {
        featherSeq += 1
        showFeathers = true
    }

    private func pauseReaction(milliseconds: UInt64) async -> Bool {
        do {
            try await Task.sleep(nanoseconds: milliseconds * 1_000_000)
            return !Task.isCancelled
        } catch {
            return false
        }
    }

    private func resetReactionPose() {
        var transaction = Transaction()
        transaction.disablesAnimations = true
        withTransaction(transaction) {
            reactionYOffset = 0
            reactionScale = 1
            reactionTilt = 0
            reactionFlap = 0
            reactionGlow = false
            propRotation = 0
            capTossOffset = 0
            capTossRotation = 0
            headphoneBeat = 1
            displayedReaction = .none
            eventMood = nil
            showFeathers = false
        }
    }

    @ViewBuilder
    private var reactionBadge: some View {
        switch displayedReaction {
        case .none:
            EmptyView()
        case .correctJamo:
            reactionSymbol("checkmark", color: AppPalette.success, scale: 0.76)
        case .syllableCompleted:
            reactionSymbol("checkmark.seal.fill", color: AppPalette.success)
        case .wordCompleted:
            reactionSymbol("star.fill", color: MascotPalette.sunny, scale: 1.08)
        case .comboMilestone(let count):
            Text(verbatim: "\(count)")
                .font(.system(size: 18 * u, weight: .black, design: .rounded))
                .foregroundStyle(.white)
                .padding(6 * u)
                .background(AppPalette.accent, in: Capsule())
                .shadow(color: AppPalette.accent.opacity(0.35), radius: 5 * u, y: 2 * u)
                .offset(x: -50 * u, y: -58 * u)
                .accessibilityHidden(true)
        case .mistake:
            reactionSymbol("arrow.down.circle.fill", color: AppPalette.secondary, scale: 0.88)
        case .perfectSession:
            reactionSymbol("crown.fill", color: MascotPalette.sunny, scale: 1.18)
        case .rhythm(let count):
            reactionSymbol(count >= 15 ? "bolt.fill" : "music.note", color: AppPalette.secondary)
        case .startle:
            reactionSymbol("exclamationmark", color: AppPalette.error, scale: 1.12)
        case .growthTransition:
            reactionSymbol("sparkles", color: MascotPalette.sunny, scale: 1.2)
        case .stretch:
            reactionSymbol("sun.max.fill", color: Color.orange, scale: 0.92)
        case .eggKnock:
            reactionSymbol("waveform", color: AppPalette.secondary, scale: 0.9)
        case .reviewGraduated:
            reactionSymbol("checkmark.seal.fill", color: AppPalette.success, scale: 1.12)
        case .newBest:
            reactionSymbol("trophy.fill", color: MascotPalette.sunny, scale: 1.16)
        case .lessonStreak(let count):
            reactionSymbol(count >= 3 ? "3.circle.fill" : "star.fill", color: AppPalette.accent)
        case .eureka:
            reactionSymbol("lightbulb.fill", color: MascotPalette.sunny, scale: 1.15)
        }
    }

    private func reactionSymbol(
        _ name: String,
        color: Color,
        scale: CGFloat = 1
    ) -> some View {
        Image(systemName: name)
            .font(.system(size: 18 * u, weight: .black))
            .foregroundStyle(color)
            .scaleEffect(scale)
            .shadow(color: color.opacity(0.3), radius: 4 * u, y: 2 * u)
            .offset(x: -50 * u, y: -58 * u)
            .accessibilityHidden(true)
    }

    // MARK: - Ambient bits per mood

    @ViewBuilder
    private var floatingBits: some View {
        switch m {
        case .cheer:
            sparkle(size: 16, at: CGPoint(x: -58, y: -52))
            sparkle(size: 11, at: CGPoint(x: 58, y: -38))
        case .proud:
            sparkle(size: 13, at: CGPoint(x: 50, y: -46))
        case .love:
            HeartShape().fill(AppPalette.accent.opacity(0.85))
                .frame(width: 15 * u, height: 13 * u)
                .scaleEffect(lovePulse ? 1.15 : 0.9)
                .animation(.easeInOut(duration: 0.9).repeatForever(autoreverses: true), value: lovePulse)
                .offset(x: -56 * u, y: -46 * u)
            HeartShape().fill(AppPalette.accent.opacity(0.6))
                .frame(width: 10 * u, height: 9 * u)
                .scaleEffect(lovePulse ? 0.9 : 1.15)
                .animation(.easeInOut(duration: 1.1).repeatForever(autoreverses: true), value: lovePulse)
                .offset(x: 56 * u, y: -32 * u)
        case .oops:
            Text("?")
                .font(.system(size: 24 * u, weight: .black, design: .rounded))
                .foregroundStyle(AppPalette.secondary)
                .offset(x: 52 * u, y: -56 * u)
        case .sleepy:
            Text("z")
                .font(.system(size: 18 * u, weight: .black, design: .rounded))
                .foregroundStyle(AppPalette.secondary)
                .offset(x: 48 * u, y: -52 * u)
            Text("z")
                .font(.system(size: 12 * u, weight: .black, design: .rounded))
                .foregroundStyle(AppPalette.secondary.opacity(0.7))
                .offset(x: 62 * u, y: -66 * u)
        case .focus:
            SweatDropShape()
                .fill(sweatBlue)
                .frame(width: 9 * u, height: 13 * u)
                .offset(x: 48 * u, y: -34 * u)
        case .sulk:
            Text("…")
                .font(.system(size: 18 * u, weight: .black, design: .rounded))
                .foregroundStyle(AppPalette.mutedInk)
                .offset(x: 48 * u, y: -50 * u)
        case .surprise:
            Text("!")
                .font(.system(size: 25 * u, weight: .black, design: .rounded))
                .foregroundStyle(AppPalette.error)
                .offset(x: 50 * u, y: -56 * u)
        case .grit:
            Image(systemName: "flame.fill")
                .font(.system(size: 16 * u, weight: .black))
                .foregroundStyle(Color.orange)
                .offset(x: 50 * u, y: -48 * u)
        case .dizzy:
            Image(systemName: "tornado")
                .font(.system(size: 18 * u, weight: .black))
                .foregroundStyle(AppPalette.secondary)
                .offset(x: 48 * u, y: -50 * u)
        case .wink:
            HeartShape().fill(AppPalette.accent)
                .frame(width: 12 * u, height: 11 * u)
                .offset(x: 50 * u, y: -48 * u)
        case .shy:
            sparkle(size: 9, at: CGPoint(x: -50, y: -42))
        case .eureka:
            Image(systemName: "lightbulb.fill")
                .font(.system(size: 18 * u, weight: .black))
                .foregroundStyle(MascotPalette.sunny)
                .offset(x: 48 * u, y: -54 * u)
        case .satisfied:
            Image(systemName: "checkmark.circle.fill")
                .font(.system(size: 16 * u, weight: .black))
                .foregroundStyle(AppPalette.success)
                .offset(x: 48 * u, y: -50 * u)
        default:
            EmptyView()
        }
    }

    // MARK: - Chick

    private var chickView: some View {
        ZStack {
            tuftOrComb
            wings
            feet
            Ellipse()
                .fill(bodyGradient)
                .overlay {
                    Ellipse()
                        .fill(
                            LinearGradient(
                                colors: [.white.opacity(0.34 * growthAppearance.featherSheen), .clear],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                        )
                }
                .overlay(Ellipse().strokeBorder(line, lineWidth: 3.4 * u))
                .frame(width: (pose == .front ? 108 : 98) * u, height: 108 * u)
            Ellipse()
                .fill(cream.opacity(0.55 + 0.35 * growthAppearance.featherSheen))
                .frame(width: 28 * u, height: 17 * u)
                .offset(x: (-24 + faceOffsetX * 0.35) * u, y: -22 * u)
            face.offset(x: faceOffsetX * u)
        }
    }

    @ViewBuilder
    private var tuftOrComb: some View {
        if stage == .rooster {
            CombShape()
                .fill(
                    Color(
                        red: 1.0,
                        green: 0.42 - 0.18 * Double(growthAppearance.combProgress),
                        blue: 0.29 - 0.12 * Double(growthAppearance.combProgress)
                    )
                )
                .overlay(CombShape().stroke(deep, lineWidth: 2.2 * u))
                .frame(
                    width: (36 + 8 * growthAppearance.combProgress) * u,
                    height: (17 + 7 * growthAppearance.combProgress) * u
                )
                .offset(y: -62 * u)
        } else if prop != .gradCap {
            TuftShape()
                .stroke(line, style: StrokeStyle(lineWidth: 3.2 * u, lineCap: .round))
                .frame(width: 14 * u, height: 22 * u)
                .rotationEffect(.degrees(tuftAngle), anchor: .bottom)
                .offset(x: 0, y: -64 * u)
        }
    }

    private var tuftAngle: Double {
        switch m {
        case .oops: return 24
        case .sleepy: return 14
        case .surprise, .eureka: return -12
        case .sulk: return 20
        default: return 0
        }
    }

    private var wings: some View {
        ZStack {
            wing.rotationEffect(.degrees(-(leftWingAngle + idleFlap + reactionFlap)), anchor: .top)
                .offset(x: -50 * u, y: 4 * u)
            wing.rotationEffect(.degrees(rightWingAngle + idleFlap + reactionFlap), anchor: .top)
                .offset(x: 50 * u, y: 4 * u)
        }
    }

    private var wing: some View {
        Ellipse()
            .fill(yellowBottom)
            .overlay(Ellipse().strokeBorder(line, lineWidth: 2.8 * u))
            .frame(width: 20 * u, height: 38 * u)
    }

    private var leftWingAngle: Double {
        let base: Double
        switch m {
        case .idle, .sleepy: base = 18
        case .happy, .love: base = 42
        case .cheer: base = 135
        case .oops: base = 10
        case .proud: base = 60
        case .focus: base = 26
        case .sulk: base = 8
        case .surprise: base = 64
        case .grit: base = 32
        case .dizzy: base = 50
        case .wink, .shy: base = 38
        case .eureka: base = 88
        case .satisfied: base = 48
        }
        return base + Double(intensity) * (m == .cheer ? 18 : 10)
    }
    private var rightWingAngle: Double { m == .oops ? 34 : leftWingAngle }

    private var feet: some View {
        FeetShape()
            .stroke(deep, style: StrokeStyle(lineWidth: 3 * u, lineCap: .round))
            .frame(width: 40 * u, height: 14 * u)
            .mask(alignment: .leading) {
                Rectangle().frame(width: idleOneLeg ? 20 * u : 40 * u)
            }
            .offset(y: 62 * u)
    }

    private var faceOffsetX: CGFloat {
        switch pose {
        case .front: 0
        case .threeQuarterLeft: -9
        case .threeQuarterRight: 9
        }
    }

    // MARK: - Face

    private var face: some View {
        ZStack {
            eyes
            if m == .focus || m == .grit { brows }
            beak
            blush(at: -30)
            blush(at: 30)
        }
    }

    @ViewBuilder
    private var eyes: some View {
        let dx: CGFloat = 16 * u
        let dy: CGFloat = -8 * u
        switch m {
        case .idle:
            dotEye(1.0).offset(x: -dx, y: dy)
            dotEye(1.0).offset(x: dx, y: dy)
        case .focus:
            dotEye(0.9).offset(x: -dx, y: dy + 1 * u)
            dotEye(0.9).offset(x: dx, y: dy + 1 * u)
        case .oops:
            dotEye(1.0).offset(x: -dx, y: dy)
            dotEye(0.65).offset(x: dx, y: dy)
        case .happy, .cheer:
            happyEye.offset(x: -dx, y: dy)
            happyEye.offset(x: dx, y: dy)
        case .sleepy:
            closedEye.offset(x: -dx, y: dy)
            closedEye.offset(x: dx, y: dy)
        case .love:
            HeartShape().fill(AppPalette.accent)
                .frame(width: 15 * u, height: 13 * u).offset(x: -dx, y: dy)
            HeartShape().fill(AppPalette.accent)
                .frame(width: 15 * u, height: 13 * u).offset(x: dx, y: dy)
        case .proud:
            StarburstShape().fill(ink)
                .frame(width: 13 * u, height: 13 * u).offset(x: -dx, y: dy)
            StarburstShape().fill(ink)
                .frame(width: 13 * u, height: 13 * u).offset(x: dx, y: dy)
        case .sulk:
            closedEye.offset(x: -dx, y: dy + 2 * u)
            dotEye(0.72).offset(x: dx, y: dy + 2 * u)
        case .surprise:
            dotEye(1.32).offset(x: -dx, y: dy)
            dotEye(1.32).offset(x: dx, y: dy)
        case .grit:
            dotEye(0.82).offset(x: -dx, y: dy + 2 * u)
            dotEye(0.82).offset(x: dx, y: dy + 2 * u)
        case .dizzy:
            xEye.offset(x: -dx, y: dy)
            xEye.offset(x: dx, y: dy)
        case .wink:
            happyEye.offset(x: -dx, y: dy)
            dotEye(1.0).offset(x: dx, y: dy)
        case .shy:
            dotEye(0.72).offset(x: -dx, y: dy + 3 * u)
            dotEye(0.72).offset(x: dx, y: dy + 3 * u)
        case .eureka:
            StarburstShape().fill(ink)
                .frame(width: 12 * u, height: 12 * u).offset(x: -dx, y: dy)
            dotEye(1.08).offset(x: dx, y: dy)
        case .satisfied:
            happyEye.offset(x: -dx, y: dy)
            happyEye.offset(x: dx, y: dy)
        }
    }

    /// dot-eye — 시선(gazeX)을 따라 동공이 살짝 움직인다 (A2).
    private func dotEye(_ scale: CGFloat) -> some View {
        let expressiveScale = scale * (1 + intensity * 0.08)
        return Circle().fill(ink)
            .frame(width: 9 * u * expressiveScale, height: 9 * u * expressiveScale)
            .scaleEffect(y: blink ? 0.15 : 1.0)
            .offset(
                x: max(-1, min(1, gazeX + idleGaze)) * 3 * u,
                y: max(-1, min(1, gazeY)) * 2.5 * u
            )
            .animation(.easeOut(duration: 0.15), value: gazeX)
            .animation(.easeOut(duration: 0.15), value: gazeY)
            .animation(.easeInOut(duration: 0.22), value: idleGaze)
            .frame(width: 9 * u, height: 9 * u)
    }

    private var happyEye: some View {
        ArcShape(startDegrees: 200, endDegrees: 340)
            .stroke(ink, style: StrokeStyle(lineWidth: 3.4 * u, lineCap: .round))
            .frame(width: 13 * u, height: 9 * u)
    }

    private var closedEye: some View {
        ArcShape(startDegrees: 20, endDegrees: 160)
            .stroke(ink, style: StrokeStyle(lineWidth: 3.4 * u, lineCap: .round))
            .frame(width: 13 * u, height: 9 * u)
    }

    private var xEye: some View {
        ZStack {
            Capsule().fill(ink).frame(width: 12 * u, height: 2.8 * u)
                .rotationEffect(.degrees(45))
            Capsule().fill(ink).frame(width: 12 * u, height: 2.8 * u)
                .rotationEffect(.degrees(-45))
        }
    }

    private var brows: some View {
        ZStack {
            BrowShape().stroke(ink, style: StrokeStyle(lineWidth: 3.2 * u, lineCap: .round))
                .frame(width: 14 * u, height: 5 * u)
                .offset(x: -16 * u, y: -18 * u)
            BrowShape().stroke(ink, style: StrokeStyle(lineWidth: 3.2 * u, lineCap: .round))
                .frame(width: 14 * u, height: 5 * u)
                .scaleEffect(x: -1)
                .offset(x: 16 * u, y: -18 * u)
        }
    }

    private var beak: some View {
        let big = (m == .cheer || m == .surprise || idleYawn)
        return DiamondShape()
            .fill(m == .grit ? Color.white : beakColor)
            .overlay(DiamondShape().stroke(deep, lineWidth: 1.8 * u))
            .frame(
                width: (m == .grit ? 20 : (big ? 19 : 15)) * u,
                height: (m == .grit ? 8 : (big ? 15 : 12)) * u
            )
            .offset(y: 7 * u)
    }

    private func blush(at x: CGFloat) -> some View {
        Ellipse()
            .fill(blushColor.opacity(m == .shy ? 0.72 : (m == .love || m == .happy || m == .cheer ? 0.5 : 0.4)))
            .frame(width: 15 * u, height: 9 * u)
            .offset(x: x * u, y: 6 * u)
    }

    // MARK: - Growth stages

    private func eggView(cracked: Bool) -> some View {
        ZStack {
            if cracked { feet.offset(y: 4 * u) }
            EggShape()
                .fill(LinearGradient(colors: [.white, shell],
                                     startPoint: .top, endPoint: .bottom))
                .overlay(eggPatternOverlay.clipShape(EggShape()))
                .overlay(EggShape().stroke(line, lineWidth: 3.4 * u))
                .frame(width: 80 * u, height: 120 * u)
            Ellipse()
                .fill(.white.opacity(0.85))
                .frame(width: 22 * u, height: 30 * u)
                .offset(x: -18 * u, y: -34 * u)
            if cracked || idleExtraCrack {
                CrackShape()
                    .stroke(line, style: StrokeStyle(lineWidth: 2.8 * u,
                                                     lineCap: .round, lineJoin: .round))
                    .frame(width: 34 * u, height: 34 * u)
                    .offset(x: -4 * u, y: -4 * u)
                if growthAppearance.crackProgress >= 2 {
                    CrackShape()
                        .stroke(line.opacity(0.86), style: StrokeStyle(
                            lineWidth: 2.3 * u,
                            lineCap: .round,
                            lineJoin: .round
                        ))
                        .frame(width: 24 * u, height: 26 * u)
                        .scaleEffect(x: -1)
                        .offset(x: 19 * u, y: 20 * u)
                }
                if growthAppearance.crackProgress >= 3 {
                    CrackShape()
                        .stroke(line.opacity(0.72), style: StrokeStyle(
                            lineWidth: 2 * u,
                            lineCap: .round,
                            lineJoin: .round
                        ))
                        .frame(width: 19 * u, height: 20 * u)
                        .rotationEffect(.degrees(90))
                        .offset(x: -22 * u, y: -29 * u)
                }
            }
        }
    }

    /// 알 무늬 (C5): 온보딩 목표별 개인화.
    @ViewBuilder
    private var eggPatternOverlay: some View {
        switch eggPattern {
        case .plain:
            EmptyView()
        case .hearts:
            ZStack {
                HeartShape().fill(AppPalette.accent.opacity(0.25))
                    .frame(width: 16 * u, height: 14 * u).offset(x: 14 * u, y: -18 * u)
                HeartShape().fill(AppPalette.accent.opacity(0.18))
                    .frame(width: 11 * u, height: 10 * u).offset(x: -14 * u, y: 16 * u)
                HeartShape().fill(AppPalette.accent.opacity(0.2))
                    .frame(width: 9 * u, height: 8 * u).offset(x: 18 * u, y: 30 * u)
            }
        case .stars:
            ZStack {
                StarburstShape().fill(MascotPalette.sunny.opacity(0.5))
                    .frame(width: 13 * u, height: 13 * u).offset(x: 14 * u, y: -16 * u)
                StarburstShape().fill(MascotPalette.sunny.opacity(0.38))
                    .frame(width: 9 * u, height: 9 * u).offset(x: -14 * u, y: 14 * u)
                StarburstShape().fill(MascotPalette.sunny.opacity(0.42))
                    .frame(width: 7 * u, height: 7 * u).offset(x: 16 * u, y: 32 * u)
            }
        case .polka:
            ZStack {
                Circle().fill(AppPalette.success.opacity(0.3))
                    .frame(width: 10 * u).offset(x: 14 * u, y: -18 * u)
                Circle().fill(AppPalette.secondary.opacity(0.28))
                    .frame(width: 8 * u).offset(x: -15 * u, y: 10 * u)
                Circle().fill(AppPalette.success.opacity(0.25))
                    .frame(width: 7 * u).offset(x: 12 * u, y: 30 * u)
            }
        }
    }

    private var hatchingView: some View {
        ZStack {
            Circle()
                .fill(bodyGradient)
                .overlay(Circle().strokeBorder(line, lineWidth: 3.4 * u))
                .frame(width: 94 * u, height: 94 * u)
                .offset(y: -8 * u)
            ZStack {
                dotEye(1.0).offset(x: -14 * u, y: -16 * u)
                dotEye(1.0).offset(x: 14 * u, y: -16 * u)
                DiamondShape().fill(beakColor)
                    .overlay(DiamondShape().stroke(deep, lineWidth: 1.8 * u))
                    .frame(width: 14 * u, height: 11 * u).offset(y: -4 * u)
                blush(at: -26).offset(y: -10 * u)
                blush(at: 26).offset(y: -10 * u)
            }
            ShellCupShape()
                .fill(LinearGradient(colors: [.white, shell],
                                     startPoint: .top, endPoint: .bottom))
                .overlay(ShellCupShape().stroke(line, style: StrokeStyle(
                    lineWidth: 3.2 * u, lineJoin: .round)))
                .frame(width: 88 * u, height: 52 * u)
                .offset(y: 34 * u)
        }
    }

    // MARK: - Props & decorations

    @ViewBuilder
    private var propView: some View {
        switch prop {
        case .none: EmptyView()
        case .lightstick: lightstick
        case .gradCap: gradCap
        case .headphones: headphones
        case .travelCase: travelCase
        case .coffeeCup: coffeeCup
        case .microphone: microphone
        case .heartBalloon: heartBalloon
        case .foodPlate: foodPlate
        case .ribbon: ribbon
        case .glasses: glasses
        case .gameCenterTrophy: gameCenterTrophy
        }
    }

    private var fanAccent: Color {
        switch fanColor {
        case .pink: AppPalette.accent
        case .lavender: AppPalette.secondary
        case .mint: AppPalette.success
        case .sky: Color(red: 0.36, green: 0.68, blue: 0.96)
        case .coral: Color(red: 1.0, green: 0.42, blue: 0.34)
        case .gold: MascotPalette.sunny
        }
    }

    private var lightstick: some View {
        ZStack {
            Circle().fill(fanAccent.opacity(reactionGlow ? 0.58 : 0.22))
                .frame(width: 34 * u, height: 34 * u)
                .shadow(
                    color: fanAccent.opacity(reactionGlow ? 0.8 : 0),
                    radius: reactionGlow ? 10 * u : 0
                )
            HeartShape().fill(fanAccent)
                .overlay(HeartShape().stroke(Color(red: 0.88, green: 0.23, blue: 0.46),
                                             lineWidth: 1.8 * u))
                .frame(width: 20 * u, height: 18 * u)
            RoundedRectangle(cornerRadius: 3 * u)
                .fill(charcoal)
                .frame(width: 6 * u, height: 26 * u)
                .offset(y: 22 * u)
        }
        .offset(x: 58 * u, y: -46 * u)
        .rotationEffect(.degrees((m == .cheer ? -14 : 8) + propRotation))
        .scaleEffect(reactionGlow ? 1.14 : 1)
    }

    private var gradCap: some View {
        ZStack {
            Path { p in
                p.move(to: CGPoint(x: 0, y: 0))
                p.addLine(to: CGPoint(x: 22 * u, y: 9 * u))
                p.addLine(to: CGPoint(x: 22 * u, y: 20 * u))
            }
            .stroke(MascotPalette.sunny, style: StrokeStyle(lineWidth: 2.6 * u, lineCap: .round))
            .frame(width: 22 * u, height: 20 * u)
            .offset(x: 18 * u, y: -58 * u)
            Circle().fill(MascotPalette.sunny)
                .frame(width: 7 * u, height: 7 * u)
                .offset(x: 30 * u, y: -46 * u)
            RoundedRectangle(cornerRadius: 4 * u)
                .fill(charcoal)
                .frame(width: 30 * u, height: 12 * u)
                .offset(y: -56 * u)
            DiamondShape()
                .fill(charcoal)
                .overlay(DiamondShape().stroke(Color(red: 0.21, green: 0.19, blue: 0.29),
                                               lineWidth: 2 * u))
                .frame(width: 62 * u, height: 26 * u)
                .offset(y: -64 * u)
        }
        .offset(y: MascotMotionPolicy.capOffset(capTossOffset, size: size))
        .rotationEffect(.degrees(capTossRotation))
    }

    private var headphones: some View {
        ZStack {
            ArcShape(startDegrees: 195, endDegrees: 345)
                .stroke(charcoal, style: StrokeStyle(lineWidth: 5.5 * u, lineCap: .round))
                .frame(width: 112 * u, height: 96 * u)
                .offset(y: -12 * u)
            headphoneCup.offset(x: -55 * u, y: -8 * u)
            headphoneCup.offset(x: 55 * u, y: -8 * u)
            Text("♪")
                .font(.system(size: 17 * u, weight: .black, design: .rounded))
                .foregroundStyle(AppPalette.secondary)
                .offset(x: 56 * u, y: -54 * u)
        }
        .scaleEffect(headphoneBeat)
    }

    private var headphoneCup: some View {
        RoundedRectangle(cornerRadius: 8 * u)
            .fill(AppPalette.success)
            .overlay(RoundedRectangle(cornerRadius: 8 * u)
                .strokeBorder(mintDark, lineWidth: 2.4 * u))
            .frame(width: 16 * u, height: 26 * u)
    }

    private var travelCase: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 7 * u)
                .fill(Color(red: 0.45, green: 0.72, blue: 0.96))
                .overlay(RoundedRectangle(cornerRadius: 7 * u).stroke(charcoal, lineWidth: 2 * u))
                .frame(width: 35 * u, height: 43 * u)
            RoundedRectangle(cornerRadius: 2 * u)
                .stroke(charcoal, lineWidth: 2 * u)
                .frame(width: 15 * u, height: 13 * u)
                .offset(y: -27 * u)
            Circle().fill(charcoal).frame(width: 5 * u).offset(x: -10 * u, y: 24 * u)
            Circle().fill(charcoal).frame(width: 5 * u).offset(x: 10 * u, y: 24 * u)
            Image(systemName: "airplane")
                .font(.system(size: 12 * u, weight: .black))
                .foregroundStyle(.white)
        }
        .offset(x: -58 * u, y: 36 * u)
        .rotationEffect(.degrees(reactionTilt * 0.25))
    }

    private var coffeeCup: some View {
        Image(systemName: "cup.and.saucer.fill")
            .font(.system(size: 32 * u, weight: .black))
            .foregroundStyle(Color(red: 0.66, green: 0.42, blue: 0.28))
            .offset(x: 57 * u, y: 38 * u)
            .rotationEffect(.degrees(propRotation * 0.35))
    }

    private var microphone: some View {
        Image(systemName: "microphone.fill")
            .font(.system(size: 36 * u, weight: .black))
            .foregroundStyle(AppPalette.secondary)
            .offset(x: 57 * u, y: -6 * u)
            .rotationEffect(.degrees(-18 + propRotation))
    }

    private var heartBalloon: some View {
        ZStack {
            Path { path in
                path.move(to: CGPoint(x: 22 * u, y: 26 * u))
                path.addQuadCurve(
                    to: CGPoint(x: 0, y: 74 * u),
                    control: CGPoint(x: 34 * u, y: 54 * u)
                )
            }
            .stroke(charcoal.opacity(0.55), lineWidth: 1.8 * u)
            HeartShape().fill(AppPalette.accent)
                .overlay(HeartShape().stroke(Color.white.opacity(0.7), lineWidth: 1.4 * u))
                .frame(width: 38 * u, height: 34 * u)
        }
        .offset(x: 58 * u, y: -54 * u)
        .rotationEffect(.degrees(propRotation * 0.4), anchor: .bottom)
    }

    private var foodPlate: some View {
        ZStack {
            Ellipse().fill(.white)
                .overlay(Ellipse().stroke(charcoal.opacity(0.35), lineWidth: 2 * u))
                .frame(width: 48 * u, height: 24 * u)
            RoundedRectangle(cornerRadius: 4 * u)
                .fill(Color(red: 0.89, green: 0.19, blue: 0.15))
                .frame(width: 27 * u, height: 11 * u)
            Image(systemName: "fork.knife")
                .font(.system(size: 11 * u, weight: .black))
                .foregroundStyle(charcoal)
                .offset(y: -15 * u)
        }
        .offset(x: 56 * u, y: 42 * u)
        .rotationEffect(.degrees(propRotation * 0.25))
    }

    private var ribbon: some View {
        Image(systemName: "rosette")
            .font(.system(size: 31 * u, weight: .black))
            .foregroundStyle(AppPalette.accent)
            .offset(y: 42 * u)
            .scaleEffect(reactionGlow ? 1.14 : 1)
    }

    private var glasses: some View {
        Image(systemName: "eyeglasses")
            .font(.system(size: 44 * u, weight: .bold))
            .foregroundStyle(charcoal)
            .offset(x: faceOffsetX * u, y: -8 * u)
            .rotationEffect(.degrees(reactionTilt * 0.18))
    }

    private var gameCenterTrophy: some View {
        ZStack {
            Image(systemName: "trophy.fill")
                .font(.system(size: 31 * u, weight: .black))
                .foregroundStyle(MascotPalette.sunny)
                .overlay {
                    Image(systemName: "trophy")
                        .font(.system(size: 31 * u, weight: .black))
                        .foregroundStyle(charcoal.opacity(0.65))
                }
            Image(systemName: "star.fill")
                .font(.system(size: 8 * u, weight: .black))
                .foregroundStyle(.white)
                .offset(y: -4 * u)
        }
        .offset(x: -57 * u, y: 34 * u)
        .rotationEffect(.degrees(reactionTilt * 0.22))
        .scaleEffect(reactionGlow ? 1.13 : 1)
    }

    private var friendView: some View {
        ZStack {
            Circle()
                .fill(
                    LinearGradient(
                        colors: [Color(red: 0.86, green: 0.78, blue: 1.0), AppPalette.secondary],
                        startPoint: .top,
                        endPoint: .bottom
                    )
                )
                .overlay(Circle().stroke(AppPalette.secondary.opacity(0.75), lineWidth: 2 * u))
                .frame(width: 65 * u, height: 65 * u)
            Circle().fill(ink).frame(width: 6 * u).offset(x: -10 * u, y: -6 * u)
            Circle().fill(ink).frame(width: 6 * u).offset(x: 10 * u, y: -6 * u)
            DiamondShape().fill(beakColor).frame(width: 11 * u, height: 8 * u).offset(y: 7 * u)
        }
        .offset(x: -74 * u, y: 30 * u)
        .scaleEffect(0.76)
        .accessibilityHidden(true)
    }

    @ViewBuilder
    private var speechBubble: some View {
        if let speech, !speech.isEmpty {
            Text(verbatim: speech)
                .font(.system(size: 10 * u, weight: .bold, design: .rounded))
                .foregroundStyle(charcoal)
                .lineLimit(2)
                .minimumScaleFactor(0.7)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 8 * u)
                .padding(.vertical, 6 * u)
                .frame(maxWidth: 92 * u)
                .background(Color.white.opacity(0.94), in: RoundedRectangle(cornerRadius: 10 * u))
                .overlay {
                    RoundedRectangle(cornerRadius: 10 * u)
                        .stroke(AppPalette.secondary.opacity(0.32), lineWidth: 1.4 * u)
                }
                .offset(x: 58 * u, y: -73 * u)
        }
    }

    @ViewBuilder
    private var nameTagView: some View {
        if let nameTag, !nameTag.isEmpty {
            Text(verbatim: nameTag)
                .font(.system(size: 10 * u, weight: .black, design: .rounded))
                .foregroundStyle(charcoal)
                .padding(.horizontal, 9 * u)
                .padding(.vertical, 4 * u)
                .background(cream.opacity(0.96), in: Capsule())
                .overlay(Capsule().stroke(line.opacity(0.55), lineWidth: 1.4 * u))
                .offset(y: 75 * u)
        }
    }

    private func sparkle(size s: CGFloat, at point: CGPoint) -> some View {
        StarburstShape()
            .fill(MascotPalette.sunny)
            .frame(width: s * u, height: s * u)
            .offset(x: point.x * u, y: point.y * u)
    }
}

// MARK: - Feather burst (A5: cheer 깃털)

/// Three little feathers scatter and fall on cheer. One-shot; parent re-ids
/// per burst.
struct FeatherBurst: View {
    let u: CGFloat
    let color: Color
    @State private var fly = false

    private let seeds: [(x: CGFloat, spin: Double)] = [(-44, -160), (50, 200), (-12, 140)]

    var body: some View {
        ZStack {
            ForEach(0..<3, id: \.self) { i in
                Ellipse()
                    .fill(color)
                    .frame(width: 10 * u, height: 5 * u)
                    .rotationEffect(.degrees(fly ? seeds[i].spin : 0))
                    .offset(x: seeds[i].x * u * (fly ? 1.3 : 0.4),
                            y: fly ? 42 * u : -30 * u)
                    .opacity(fly ? 0 : 0.95)
            }
        }
        .onAppear {
            withAnimation(.easeIn(duration: 0.8)) { fly = true }
        }
        .allowsHitTesting(false)
    }
}

// MARK: - Shapes (200-space geometry, normalized to their frames)

struct TuftShape: Shape {
    func path(in rect: CGRect) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: rect.width * 0.45, y: rect.maxY))
        p.addQuadCurve(to: CGPoint(x: rect.width * 0.55, y: rect.minY),
                       control: CGPoint(x: rect.width * 0.05, y: rect.height * 0.25))
        p.move(to: CGPoint(x: rect.width * 0.55, y: rect.minY))
        p.addQuadCurve(to: CGPoint(x: rect.width * 0.95, y: rect.height * 0.6),
                       control: CGPoint(x: rect.width * 0.35, y: rect.height * 0.35))
        return p
    }
}

struct DiamondShape: Shape {
    func path(in rect: CGRect) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: rect.midX, y: rect.minY))
        p.addLine(to: CGPoint(x: rect.maxX, y: rect.midY))
        p.addLine(to: CGPoint(x: rect.midX, y: rect.maxY))
        p.addLine(to: CGPoint(x: rect.minX, y: rect.midY))
        p.closeSubpath()
        return p
    }
}

struct HeartShape: Shape {
    func path(in rect: CGRect) -> Path {
        let w = rect.width, h = rect.height
        var p = Path()
        p.move(to: CGPoint(x: w * 0.5, y: h * 0.28))
        p.addCurve(to: CGPoint(x: w * 0.06, y: h * 0.42),
                   control1: CGPoint(x: w * 0.36, y: -h * 0.10),
                   control2: CGPoint(x: -w * 0.04, y: h * 0.10))
        p.addCurve(to: CGPoint(x: w * 0.5, y: h),
                   control1: CGPoint(x: w * 0.12, y: h * 0.72),
                   control2: CGPoint(x: w * 0.38, y: h * 0.86))
        p.addCurve(to: CGPoint(x: w * 0.94, y: h * 0.42),
                   control1: CGPoint(x: w * 0.62, y: h * 0.86),
                   control2: CGPoint(x: w * 0.88, y: h * 0.72))
        p.addCurve(to: CGPoint(x: w * 0.5, y: h * 0.28),
                   control1: CGPoint(x: w * 1.04, y: h * 0.10),
                   control2: CGPoint(x: w * 0.64, y: -h * 0.10))
        p.closeSubpath()
        return p
    }
}

struct StarburstShape: Shape {
    func path(in rect: CGRect) -> Path {
        let w = rect.width, h = rect.height
        let inset: CGFloat = 0.32
        var p = Path()
        p.move(to: CGPoint(x: w * 0.5, y: 0))
        p.addLine(to: CGPoint(x: w * (0.5 + inset / 2), y: h * (0.5 - inset / 2)))
        p.addLine(to: CGPoint(x: w, y: h * 0.5))
        p.addLine(to: CGPoint(x: w * (0.5 + inset / 2), y: h * (0.5 + inset / 2)))
        p.addLine(to: CGPoint(x: w * 0.5, y: h))
        p.addLine(to: CGPoint(x: w * (0.5 - inset / 2), y: h * (0.5 + inset / 2)))
        p.addLine(to: CGPoint(x: 0, y: h * 0.5))
        p.addLine(to: CGPoint(x: w * (0.5 - inset / 2), y: h * (0.5 - inset / 2)))
        p.closeSubpath()
        return p
    }
}

struct FeetShape: Shape {
    func path(in rect: CGRect) -> Path {
        var p = Path()
        for cx in [rect.width * 0.3, rect.width * 0.7] {
            p.move(to: CGPoint(x: cx, y: 0))
            p.addLine(to: CGPoint(x: cx, y: rect.height * 0.62))
            p.move(to: CGPoint(x: cx - rect.width * 0.10, y: rect.maxY))
            p.addLine(to: CGPoint(x: cx, y: rect.height * 0.62))
            p.addLine(to: CGPoint(x: cx + rect.width * 0.10, y: rect.maxY))
        }
        return p
    }
}

struct EggShape: Shape {
    func path(in rect: CGRect) -> Path {
        let w = rect.width, h = rect.height
        var p = Path()
        p.move(to: CGPoint(x: w * 0.5, y: 0))
        p.addCurve(to: CGPoint(x: w, y: h * 0.55),
                   control1: CGPoint(x: w * 0.78, y: 0),
                   control2: CGPoint(x: w, y: h * 0.25))
        p.addCurve(to: CGPoint(x: w * 0.5, y: h),
                   control1: CGPoint(x: w, y: h * 0.82),
                   control2: CGPoint(x: w * 0.78, y: h))
        p.addCurve(to: CGPoint(x: 0, y: h * 0.55),
                   control1: CGPoint(x: w * 0.22, y: h),
                   control2: CGPoint(x: 0, y: h * 0.82))
        p.addCurve(to: CGPoint(x: w * 0.5, y: 0),
                   control1: CGPoint(x: 0, y: h * 0.25),
                   control2: CGPoint(x: w * 0.22, y: 0))
        p.closeSubpath()
        return p
    }
}

struct CrackShape: Shape {
    func path(in rect: CGRect) -> Path {
        let w = rect.width, h = rect.height
        var p = Path()
        p.move(to: CGPoint(x: 0, y: h * 0.2))
        p.addLine(to: CGPoint(x: w * 0.42, y: h * 0.38))
        p.addLine(to: CGPoint(x: w * 0.16, y: h * 0.6))
        p.addLine(to: CGPoint(x: w * 0.62, y: h * 0.72))
        p.addLine(to: CGPoint(x: w * 0.44, y: h))
        return p
    }
}

struct ShellCupShape: Shape {
    func path(in rect: CGRect) -> Path {
        let w = rect.width, h = rect.height
        let teeth = 7
        var p = Path()
        p.move(to: CGPoint(x: 0, y: h * 0.3))
        for i in 0..<teeth {
            let x1 = w * (CGFloat(i) + 0.5) / CGFloat(teeth)
            let x2 = w * CGFloat(i + 1) / CGFloat(teeth)
            p.addLine(to: CGPoint(x: x1, y: 0))
            p.addLine(to: CGPoint(x: x2, y: h * 0.3))
        }
        p.addLine(to: CGPoint(x: w, y: h * 0.62))
        p.addCurve(to: CGPoint(x: w * 0.5, y: h),
                   control1: CGPoint(x: w, y: h * 0.9),
                   control2: CGPoint(x: w * 0.78, y: h))
        p.addCurve(to: CGPoint(x: 0, y: h * 0.62),
                   control1: CGPoint(x: w * 0.22, y: h),
                   control2: CGPoint(x: 0, y: h * 0.9))
        p.closeSubpath()
        return p
    }
}

struct CombShape: Shape {
    func path(in rect: CGRect) -> Path {
        let w = rect.width, h = rect.height
        var p = Path()
        p.move(to: CGPoint(x: 0, y: h))
        p.addQuadCurve(to: CGPoint(x: w * 0.3, y: h * 0.85),
                       control: CGPoint(x: w * 0.1, y: -h * 0.2))
        p.addQuadCurve(to: CGPoint(x: w * 0.62, y: h * 0.8),
                       control: CGPoint(x: w * 0.44, y: -h * 0.35))
        p.addQuadCurve(to: CGPoint(x: w, y: h),
                       control: CGPoint(x: w * 0.82, y: -h * 0.1))
        p.closeSubpath()
        return p
    }
}

struct BrowShape: Shape {
    func path(in rect: CGRect) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: 0, y: 0))
        p.addLine(to: CGPoint(x: rect.maxX, y: rect.maxY))
        return p
    }
}

struct SweatDropShape: Shape {
    func path(in rect: CGRect) -> Path {
        let w = rect.width, h = rect.height
        var p = Path()
        p.move(to: CGPoint(x: w * 0.5, y: 0))
        p.addQuadCurve(to: CGPoint(x: w * 0.5, y: h),
                       control: CGPoint(x: w * 1.15, y: h * 0.75))
        p.addQuadCurve(to: CGPoint(x: w * 0.5, y: 0),
                       control: CGPoint(x: -w * 0.15, y: h * 0.75))
        p.closeSubpath()
        return p
    }
}

struct ArcShape: Shape {
    let startDegrees: Double
    let endDegrees: Double
    func path(in rect: CGRect) -> Path {
        var p = Path()
        p.addArc(center: CGPoint(x: rect.midX, y: rect.midY),
                 radius: min(rect.width, rect.height) / 2,
                 startAngle: .degrees(startDegrees),
                 endAngle: .degrees(endDegrees),
                 clockwise: false)
        return p
    }
}
