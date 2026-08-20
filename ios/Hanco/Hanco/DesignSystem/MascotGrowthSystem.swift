import SwiftUI

extension MascotStage: Identifiable {
  public var id: String {
    switch self {
    case .egg: "egg"
    case .cracking: "cracking"
    case .hatching: "hatching"
    case .chick: "chick"
    case .rooster: "rooster"
    }
  }

  var growthRank: Int {
    switch self {
    case .egg: 0
    case .cracking: 1
    case .hatching: 2
    case .chick: 3
    case .rooster: 4
    }
  }

  static func forGrowthRank(_ rank: Int) -> MascotStage {
    switch rank {
    case ...0: .egg
    case 1: .cracking
    case 2: .hatching
    case 3: .chick
    default: .rooster
    }
  }
}

struct MascotSessionAppearance: Equatable {
  let automaticProp = MascotProp.none
}

@MainActor
final class MascotCompanionLibrary: ObservableObject {
  enum Selection: Equatable {
    case automatic
    case none
    case fixed(MascotProp)
  }

  struct UnlockState: Identifiable, Equatable {
    let prop: MascotProp
    let isUnlocked: Bool
    let conditionKey: String

    var id: MascotProp { prop }
  }

  static let nameKey = "mascot.name"
  static let propSelectionKey = "mascot.prop_selection"
  static let celebratedStageKey = "mascot.celebrated_stage"
  static let typedJamoKey = "mascot.typed_jamo_count"
  static let eggPatternKey = "mascot.egg_pattern"
  static let consecutiveLessonKey = "mascot.consecutive_lesson_clears"
  static let lastKnownStreakKey = "mascot.last_known_streak"
  static let streakBreakDayKey = "mascot.streak_break_presented_day"
  static let mistakeCountsKey = "mascot.mistake_counts"
  static let unlockedPropsKey = "mascot.unlocked_props"
  static let allKeys = [
    nameKey,
    propSelectionKey,
    celebratedStageKey,
    typedJamoKey,
    eggPatternKey,
    consecutiveLessonKey,
    lastKnownStreakKey,
    streakBreakDayKey,
    mistakeCountsKey,
    unlockedPropsKey,
  ]

  private let defaults: UserDefaults

  @Published var name: String {
    didSet { defaults.set(name, forKey: Self.nameKey) }
  }

  @Published var selection: Selection {
    didSet { defaults.set(rawSelection, forKey: Self.propSelectionKey) }
  }

  @Published var eggPattern: MascotEggPattern {
    didSet { defaults.set(eggPattern.rawValue, forKey: Self.eggPatternKey) }
  }

  @Published private(set) var typedJamoCount: Int
  @Published private(set) var consecutiveLessonClears: Int
  @Published private(set) var mistakeCounts: [String: Int]
  @Published private(set) var unlockedProps: Set<MascotProp>
  @Published private(set) var event: MascotReaction = .none
  @Published private(set) var eventRevision = 0

  init(defaults: UserDefaults = .standard) {
    self.defaults = defaults
    self.name = defaults.string(forKey: Self.nameKey) ?? ""
    let storedSelection = defaults.string(forKey: Self.propSelectionKey)
    switch storedSelection {
    case "none": selection = .none
    default:
      if let storedSelection, let prop = MascotProp(rawValue: storedSelection), prop != .none {
        selection = .fixed(prop)
      } else {
        selection = .automatic
      }
    }
    eggPattern = MascotEggPattern(
      rawValue: defaults.string(forKey: Self.eggPatternKey) ?? ""
    ) ?? .plain
    typedJamoCount = max(0, defaults.integer(forKey: Self.typedJamoKey))
    consecutiveLessonClears = max(0, defaults.integer(forKey: Self.consecutiveLessonKey))
    mistakeCounts = (defaults.dictionary(forKey: Self.mistakeCountsKey) ?? [:])
      .compactMapValues { ($0 as? NSNumber)?.intValue }
    unlockedProps = Set(
      (defaults.stringArray(forKey: Self.unlockedPropsKey) ?? [])
        .compactMap(MascotProp.init(rawValue:))
        .filter { $0 != .none }
    )
    if case .fixed(let prop) = selection {
      unlockedProps.insert(prop)
    }
    defaults.set(
      unlockedProps.map(\.rawValue).sorted(),
      forKey: Self.unlockedPropsKey
    )
  }

  var displayName: String {
    let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
    return trimmed.isEmpty ? AppLocalization.string("mascot.default_name") : trimmed
  }

  func resolve(automatic contextProp: MascotProp) -> MascotProp {
    switch selection {
    case .automatic: contextProp
    case .none: .none
    case .fixed(let prop): prop
    }
  }

  func pendingCelebration(current stage: MascotStage) -> MascotStage? {
    let celebratedRank = max(defaults.integer(forKey: Self.celebratedStageKey), 1)
    let nextRank = celebratedRank + 1
    guard stage.growthRank >= nextRank else { return nil }
    return MascotStage.forGrowthRank(nextRank)
  }

  func presentedStage(current stage: MascotStage) -> MascotStage {
    let celebratedRank = max(defaults.integer(forKey: Self.celebratedStageKey), 1)
    return MascotStage.forGrowthRank(min(stage.growthRank, celebratedRank))
  }

  func markCelebrated(_ stage: MascotStage) {
    defaults.set(stage.growthRank, forKey: Self.celebratedStageKey)
    objectWillChange.send()
  }

  func recordTypedJamo(_ count: Int = 1) {
    guard count > 0 else { return }
    typedJamoCount += count
    defaults.set(typedJamoCount, forKey: Self.typedJamoKey)
  }

  @discardableResult
  func recordLessonOutcome(cleared: Bool) -> Int {
    consecutiveLessonClears = cleared ? consecutiveLessonClears + 1 : 0
    defaults.set(consecutiveLessonClears, forKey: Self.consecutiveLessonKey)
    if cleared, consecutiveLessonClears >= 3 {
      publish(.lessonStreak(consecutiveLessonClears))
    }
    return consecutiveLessonClears
  }

  func consumeStreakBreak(currentStreak: Int, asOf day: JSTDay) -> Bool {
    let previous = max(0, defaults.integer(forKey: Self.lastKnownStreakKey))
    defaults.set(currentStreak, forKey: Self.lastKnownStreakKey)
    guard currentStreak == 0, previous > 0,
      defaults.string(forKey: Self.streakBreakDayKey) != day.rawValue
    else { return false }
    defaults.set(day.rawValue, forKey: Self.streakBreakDayKey)
    return true
  }

  func recordMistake(expected: Character) {
    let key = String(expected)
    mistakeCounts[key, default: 0] += 1
    defaults.set(mistakeCounts, forKey: Self.mistakeCountsKey)
  }

  /// Closet rewards are achievements. Once earned, deleting a deck or
  /// breaking a streak must not take the item away again.
  func registerUnlocks(_ states: [UnlockState]) {
    let earned = Set(states.lazy.filter(\.isUnlocked).map(\.prop))
    let merged = unlockedProps.union(earned)
    guard merged != unlockedProps else { return }
    unlockedProps = merged
    defaults.set(
      merged.map(\.rawValue).sorted(),
      forKey: Self.unlockedPropsKey
    )
  }

  func hasUnlocked(_ prop: MascotProp) -> Bool {
    unlockedProps.contains(prop)
  }

  func registerGameCenterScoreSubmission() {
    registerUnlocks([
      UnlockState(
        prop: .gameCenterTrophy,
        isUnlocked: true,
        conditionKey: "closet.cond_game_center"
      )
    ])
  }

  func registerTOPIKSessionScore(_ score: Int, sourceTags: [String]) {
    guard score >= 0,
      sourceTags.contains(where: { $0.localizedCaseInsensitiveContains("TOPIK") })
    else { return }
    registerUnlocks([
      UnlockState(
        prop: .glasses,
        isUnlocked: true,
        conditionKey: "closet.cond_glasses"
      )
    ])
  }

  var mostMissedJamo: String? {
    mistakeCounts.max { lhs, rhs in
      lhs.value == rhs.value ? lhs.key > rhs.key : lhs.value < rhs.value
    }?.key
  }

  func publish(_ event: MascotReaction) {
    self.event = event
    eventRevision &+= 1
  }

  /// The choseong game is a later surface; keeping the event here lets that mode
  /// reuse the pet contract without coupling the mascot to its quiz engine.
  func recordChoseongSolved(usedPass: Bool) {
    guard !usedPass else { return }
    publish(.eureka)
  }

  static func unlockStates(
    streak: Int,
    chaptersCleared: Int,
    decksInstalled: Int
  ) -> [UnlockState] {
    [
      UnlockState(
        prop: .lightstick,
        isUnlocked: streak >= 3,
        conditionKey: "closet.cond_lightstick"
      ),
      UnlockState(
        prop: .gradCap,
        isUnlocked: chaptersCleared >= 3,
        conditionKey: "closet.cond_gradcap"
      ),
      UnlockState(
        prop: .headphones,
        isUnlocked: decksInstalled >= 7,
        conditionKey: "closet.cond_headphones"
      ),
    ]
  }

  static func contextualUnlockStates(installedTags: Set<String>) -> [UnlockState] {
    let rules: [(MascotProp, [String], String)] = [
      (.travelCase, ["旅行", "travel"], "closet.cond_travel"),
      (.coffeeCup, ["カフェ", "cafe"], "closet.cond_cafe"),
      (.microphone, ["韓ドラ", "セリフ"], "closet.cond_microphone"),
      (.heartBalloon, ["恋愛", "love"], "closet.cond_balloon"),
      (.foodPlate, ["グルメ", "料理", "食"], "closet.cond_food"),
      (.ribbon, ["基本", "あいさつ"], "closet.cond_ribbon"),
    ]
    return rules.map { prop, needles, condition in
      UnlockState(
        prop: prop,
        isUnlocked: installedTags.contains { tag in
          needles.contains { tag.localizedCaseInsensitiveContains($0) }
        },
        conditionKey: condition
      )
    }
  }

  static var gameCenterUnlockState: UnlockState {
    UnlockState(
      prop: .gameCenterTrophy,
      isUnlocked: false,
      conditionKey: "closet.cond_game_center"
    )
  }

  static var topikSessionUnlockState: UnlockState {
    UnlockState(
      prop: .glasses,
      isUnlocked: false,
      conditionKey: "closet.cond_glasses"
    )
  }

  private var rawSelection: String {
    switch selection {
    case .automatic: "auto"
    case .none: "none"
    case .fixed(let prop): prop.rawValue
    }
  }
}

extension CurriculumProgressLibrary {
  var completedChapterCount: Int {
    CurriculumCatalog.chapters.filter { chapter in
      CurriculumChapterCompletionPolicy.isCompleted(
        chapter,
        completedStageIDs: completedStageIDs
      )
    }.count
  }

  var mascotStage: MascotStage {
    MascotStage.forClearedChapters(completedChapterCount)
  }
}

struct GrowingMascotView: View {
  @EnvironmentObject private var progress: CurriculumProgressLibrary
  @EnvironmentObject private var companion: MascotCompanionLibrary
  @EnvironmentObject private var retention: RetentionLibrary

  var mood: MascotMood = .idle
  var reaction: MascotReaction = .none
  var reactionRevision = 0
  var automaticProp: MascotProp = .none
  var sourceTags: [String] = []
  var gazeX: CGFloat = 0
  var gazeY: CGFloat = 0
  var pose: MascotPose = .front
  var intensity: CGFloat = 0
  var showsNameTag = false
  var speech: String?
  var showsFriend = false
  var usesHomeTimeMood = false
  var interactive = false
  var onOpenCloset: (() -> Void)?
  var size: CGFloat = 96
  var calm = false

  @State private var showsStreakBreakMood = false

  private var today: JSTDay { JSTDay(date: RetentionClock.now()) }
  private var currentStreak: RetentionStreak { retention.streak(asOf: today) }
  private var recentActiveDays: Int {
    RetentionCalendar.days(endingAt: today, count: 7).filter { retention.isStamped($0) }.count
  }
  private var presentedStage: MascotStage {
    companion.presentedStage(current: progress.mascotStage)
  }
  private var effectiveMood: MascotMood {
    if showsStreakBreakMood { return .sulk }
    if usesHomeTimeMood, mood == .idle, companion.consecutiveLessonClears >= 3 {
      return .satisfied
    }
    guard usesHomeTimeMood, mood == .idle else { return mood }
    let hour = RetentionCalendar.jst.component(.hour, from: RetentionClock.now())
    return hour >= 21 || hour < 5 ? .sleepy : mood
  }

  var body: some View {
    MascotView(
      mood: effectiveMood,
      stage: presentedStage,
      prop: companion.resolve(
        automatic: presentedStage == .rooster ? .gradCap : automaticProp
      ),
      eggPattern: companion.eggPattern,
      gazeX: gazeX,
      gazeY: gazeY,
      reaction: reaction == .none ? companion.event : reaction,
      reactionRevision: reaction == .none ? companion.eventRevision : reactionRevision,
      pose: pose,
      intensity: intensity,
      growthAppearance: MascotGrowthAppearance(
        typedJamoCount: companion.typedJamoCount,
        longestStreak: currentStreak.longest,
        activeDaysInLastWeek: recentActiveDays,
        completedChapterCount: progress.completedChapterCount
      ),
      fanColor: MascotFanColor.forDeckTags(sourceTags),
      nameTag: showsNameTag ? companion.displayName : nil,
      speech: speech,
      showsFriend: showsFriend,
      interactive: interactive,
      onOpenCloset: onOpenCloset,
      size: size,
      calm: calm
    )
    .onAppear {
      if usesHomeTimeMood {
        showsStreakBreakMood = companion.consumeStreakBreak(
          currentStreak: currentStreak.current,
          asOf: today
        )
      }
    }
  }
}

struct MascotClosetView: View {
  @Environment(\.dismiss) private var dismiss
  @EnvironmentObject private var companion: MascotCompanionLibrary
  @EnvironmentObject private var progress: CurriculumProgressLibrary
  @EnvironmentObject private var retention: RetentionLibrary
  @EnvironmentObject private var deckLibrary: DeckLibrary

  private var evaluatedUnlocks: [MascotCompanionLibrary.UnlockState] {
    MascotCompanionLibrary.unlockStates(
      streak: retention.streak(asOf: JSTDay(date: RetentionClock.now())).current,
      chaptersCleared: progress.completedChapterCount,
      decksInstalled: deckLibrary.installedDecks.count
    )
      + MascotCompanionLibrary.contextualUnlockStates(
        installedTags: Set(deckLibrary.installedDecks.values.flatMap(\.tags))
      )
      + [
        MascotCompanionLibrary.topikSessionUnlockState,
        MascotCompanionLibrary.gameCenterUnlockState,
      ]
  }

  private var unlocks: [MascotCompanionLibrary.UnlockState] {
    evaluatedUnlocks.map { state in
      MascotCompanionLibrary.UnlockState(
        prop: state.prop,
        isUnlocked: state.isUnlocked || companion.hasUnlocked(state.prop),
        conditionKey: state.conditionKey
      )
    }
  }

  private var unlockFingerprint: String {
    let streak = retention.streak(asOf: JSTDay(date: RetentionClock.now())).current
    let tags = deckLibrary.installedDecks.values.flatMap(\.tags).sorted().joined(separator: "|")
    return "\(streak):\(progress.completedChapterCount):\(deckLibrary.installedDecks.count):\(tags)"
  }

  var body: some View {
    NavigationStack {
      List {
        Section {
          HStack(spacing: 18) {
            GrowingMascotView(
              mood: .idle,
              showsNameTag: true,
              interactive: true,
              size: 76,
              calm: true
            )
            .frame(width: 116, height: 148)

            VStack(alignment: .leading, spacing: 7) {
              Text(verbatim: companion.displayName)
                .font(.system(.title3, design: .rounded, weight: .heavy))
                .foregroundStyle(AppPalette.ink)
              Text("closet.preview_detail")
                .font(.caption)
                .foregroundStyle(AppPalette.mutedInk)
                .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 0)
          }
          .accessibilityElement(children: .combine)
          .accessibilityIdentifier("closet.preview")
        }

        Section("closet.name_section") {
          TextField(
            AppLocalization.string("mascot.default_name"),
            text: $companion.name
          )
          .textInputAutocapitalization(.never)
          .submitLabel(.done)
          .accessibilityIdentifier("mascot.name")
        }

        Section("growth.cond_title") {
          growthRow(stage: .hatching, conditionKey: "growth.cond_hatch", threshold: 1)
          growthRow(stage: .chick, conditionKey: "growth.cond_chick", threshold: 3)
          growthRow(stage: .rooster, conditionKey: "growth.cond_rooster", threshold: 6)
          Text("growth.cond_note")
            .font(.caption)
            .foregroundStyle(AppPalette.mutedInk)
        }

        Section("growth.axis.title") {
          growthAxisRow(
            labelKey: "growth.axis.typed",
            value: companion.typedJamoCount.formatted(),
            progress: min(Double(companion.typedJamoCount) / 12_000, 1),
            tint: AppPalette.accent
          )
          let streak = retention.streak(asOf: JSTDay(date: RetentionClock.now()))
          growthAxisRow(
            labelKey: "growth.axis.streak",
            value: streak.longest.formatted(),
            progress: min(Double(streak.longest) / 30, 1),
            tint: Color.orange
          )
          let activeDays = RetentionCalendar.days(
            endingAt: JSTDay(date: RetentionClock.now()),
            count: 7
          ).filter { retention.isStamped($0) }.count
          growthAxisRow(
            labelKey: "growth.axis.activity",
            value: String(format: AppLocalization.string("growth.axis.days"), activeDays),
            progress: Double(activeDays) / 7,
            tint: AppPalette.success
          )
        }

        Section("closet.prop_section") {
          selectionRow(
            labelKey: "closet.auto",
            selection: .automatic,
            previewProp: .none,
            identifier: "closet.selection.automatic"
          )
          selectionRow(
            labelKey: "closet.bare",
            selection: .none,
            previewProp: .none,
            identifier: "closet.selection.none"
          )
          ForEach(unlocks) { state in
            propRow(state)
          }
        }
      }
      .scrollContentBackground(.hidden)
      .background(AppPalette.backgroundBottom)
      .navigationTitle(Text("closet.title"))
      .onAppear(perform: registerCurrentUnlocks)
      .onChange(of: unlockFingerprint) { _ in registerCurrentUnlocks() }
      .toolbar {
        ToolbarItem(placement: .confirmationAction) {
          Button("closet.done") { dismiss() }
        }
      }
    }
  }

  private func registerCurrentUnlocks() {
    companion.registerUnlocks(evaluatedUnlocks)
  }

  private func growthRow(
    stage: MascotStage,
    conditionKey: String,
    threshold: Int
  ) -> some View {
    let achieved = progress.completedChapterCount >= threshold
    return HStack(spacing: 12) {
      MascotView(
        mood: achieved ? .happy : .idle,
        stage: stage,
        size: 32,
        calm: true
      )
      .frame(width: 44, height: 44)
      Text(LocalizedStringKey(conditionKey))
        .font(.subheadline.weight(.semibold))
        .foregroundStyle(achieved ? AppPalette.ink : AppPalette.mutedInk)
      Spacer()
      Image(systemName: achieved ? "checkmark.seal.fill" : "lock.fill")
        .foregroundStyle(achieved ? AppPalette.success : AppPalette.mutedInk)
    }
  }

  private func growthAxisRow(
    labelKey: String,
    value: String,
    progress: Double,
    tint: Color
  ) -> some View {
    VStack(alignment: .leading, spacing: 6) {
      HStack {
        Text(LocalizedStringKey(labelKey))
          .font(.subheadline.weight(.semibold))
        Spacer()
        Text(verbatim: value)
          .font(.caption.monospacedDigit().weight(.bold))
          .foregroundStyle(AppPalette.mutedInk)
      }
      ProgressView(value: min(max(progress, 0), 1))
        .tint(tint)
    }
  }

  private func selectionRow(
    labelKey: String,
    selection: MascotCompanionLibrary.Selection,
    previewProp: MascotProp,
    identifier: String
  ) -> some View {
    Button {
      companion.selection = selection
    } label: {
      HStack(spacing: 12) {
        MascotView(
          mood: .happy,
          stage: max(progress.mascotStage, .chick),
          prop: previewProp,
          size: 34,
          calm: true
        )
        .frame(width: 46, height: 46)
        Text(LocalizedStringKey(labelKey))
          .foregroundStyle(AppPalette.ink)
        Spacer()
        if companion.selection == selection {
          Image(systemName: "checkmark.circle.fill")
            .foregroundStyle(AppPalette.accent)
        }
      }
    }
    .buttonStyle(.plain)
    .accessibilityIdentifier(identifier)
  }

  @ViewBuilder
  private func propRow(_ state: MascotCompanionLibrary.UnlockState) -> some View {
    if state.isUnlocked {
      selectionRow(
        labelKey: propLabelKey(state.prop),
        selection: .fixed(state.prop),
        previewProp: state.prop,
        identifier: "closet.selection.\(state.prop.rawValue)"
      )
    } else {
      HStack(spacing: 12) {
        MascotView(
          stage: max(progress.mascotStage, .chick),
          prop: state.prop,
          size: 34,
          calm: true
        )
        .frame(width: 46, height: 46)
        .saturation(0)
        .opacity(0.45)
        VStack(alignment: .leading, spacing: 2) {
          Text(LocalizedStringKey(propLabelKey(state.prop)))
            .foregroundStyle(AppPalette.mutedInk)
          Text(LocalizedStringKey(state.conditionKey))
            .font(.caption2)
            .foregroundStyle(AppPalette.mutedInk)
        }
        Spacer()
        Image(systemName: "lock.fill")
          .foregroundStyle(AppPalette.mutedInk)
      }
    }
  }

  private func propLabelKey(_ prop: MascotProp) -> String {
    switch prop {
    case .none: "closet.bare"
    case .lightstick: "closet.prop_lightstick"
    case .gradCap: "closet.prop_gradcap"
    case .headphones: "closet.prop_headphones"
    case .travelCase: "closet.prop_travel"
    case .coffeeCup: "closet.prop_cafe"
    case .microphone: "closet.prop_microphone"
    case .heartBalloon: "closet.prop_balloon"
    case .foodPlate: "closet.prop_food"
    case .ribbon: "closet.prop_ribbon"
    case .glasses: "closet.prop_glasses"
    case .gameCenterTrophy: "closet.prop_game_center"
    }
  }
}

struct GrowthCelebrationView: View {
  @Environment(\.accessibilityReduceMotion) private var reduceMotion
  @EnvironmentObject private var companion: MascotCompanionLibrary

  let newStage: MascotStage
  let onDone: () -> Void

  @State private var phase = 0
  @State private var shakeAngle: Double = 0
  @State private var draftName = ""

  private var isHatch: Bool { newStage == .hatching }

  var body: some View {
    ZStack {
      LinearGradient(
        colors: [AppPalette.backgroundTop, AppPalette.backgroundBottom],
        startPoint: .topLeading,
        endPoint: .bottomTrailing
      )
      .ignoresSafeArea()

      Color.clear
        .frame(width: 1, height: 1)
        .accessibilityLabel(Text("growth.hatched"))
        .accessibilityIdentifier("mascot.growth.celebration")

      GrowthConfettiBurst(isActive: phase >= 1)

      VStack(spacing: 22) {
        Spacer()

        Group {
          if phase == 0 {
            MascotView(stage: .cracking, size: 132)
              .rotationEffect(.degrees(shakeAngle))
          } else {
            MascotView(
              mood: .cheer,
              stage: newStage,
              reaction: .growthTransition,
              reactionRevision: phase,
              size: 132
            )
              .transition(.scale(scale: 0.4).combined(with: .opacity))
          }
        }
        .frame(height: 230)

        if phase >= 1 {
          Text(titleKey)
            .font(.system(.largeTitle, design: .rounded, weight: .black))
            .foregroundStyle(AppPalette.accent)
            .multilineTextAlignment(.center)
            .transition(.scale.combined(with: .opacity))
        }

        if phase >= 2 {
          if isHatch {
            VStack(spacing: 10) {
              Text("growth.name_prompt")
                .font(.headline.weight(.bold))
                .foregroundStyle(AppPalette.ink)
              TextField(
                AppLocalization.string("mascot.default_name"),
                text: $draftName
              )
              .textFieldStyle(.roundedBorder)
              .multilineTextAlignment(.center)
              .font(.title3.weight(.semibold))
              .frame(maxWidth: 240)
              .accessibilityIdentifier("mascot.growth.name")
            }
          }

          Button {
            let trimmedName = draftName.trimmingCharacters(in: .whitespacesAndNewlines)
            if isHatch, !trimmedName.isEmpty {
              companion.name = trimmedName
            }
            onDone()
          } label: {
            Text("growth.confirm")
              .font(.headline.weight(.bold))
              .foregroundStyle(.white)
              .padding(.horizontal, 42)
              .padding(.vertical, 14)
              .background(AppPalette.accent, in: Capsule())
          }
          .accessibilityIdentifier("mascot.growth.confirm")
          .transition(.opacity)
        }

        Spacer()
        Spacer()
      }
      .padding(24)
    }
    .interactiveDismissDisabled()
    .task { await runAnimation() }
  }

  private var titleKey: LocalizedStringKey {
    switch newStage {
    case .hatching: "growth.hatched"
    case .rooster: "growth.master"
    case .egg, .cracking, .chick: "growth.advanced"
    }
  }

  private func runAnimation() async {
    if reduceMotion {
      phase = 2
      return
    }
    if isHatch {
      for index in 0..<8 {
        withAnimation(.easeInOut(duration: 0.09)) {
          shakeAngle = index.isMultiple(of: 2) ? 9 : -9
        }
        try? await Task.sleep(nanoseconds: 95_000_000)
      }
      shakeAngle = 0
    }
    HancoSoundEngine.shared.play(.completion(combo: 10))
    withAnimation(.spring(response: 0.4, dampingFraction: 0.55)) {
      phase = 1
    }
    try? await Task.sleep(nanoseconds: 900_000_000)
    withAnimation(.easeOut(duration: 0.3)) {
      phase = 2
    }
  }
}

private struct GrowthConfettiBurst: View {
  let isActive: Bool

  @State private var spreads = false

  private let colors: [Color] = [
    AppPalette.accent,
    AppPalette.secondary,
    AppPalette.success,
    Color.orange,
    Color.yellow,
  ]

  var body: some View {
    ZStack {
      ForEach(0..<18, id: \.self) { index in
        let angle = Double(index) / 18 * Double.pi * 2
        let distance = 92 + CGFloat(index % 4) * 22
        Image(systemName: index.isMultiple(of: 3) ? "star.fill" : "circle.fill")
          .font(.system(size: CGFloat(8 + index % 4) * 1.2))
          .foregroundStyle(colors[index % colors.count])
          .offset(
            x: spreads ? cos(angle) * distance : 0,
            y: spreads ? sin(angle) * distance : 0
          )
          .opacity(spreads ? 0 : 1)
          .rotationEffect(.degrees(spreads ? Double(index * 47) : 0))
      }
    }
    .onChange(of: isActive) { active in
      guard active else {
        spreads = false
        return
      }
      withAnimation(.easeOut(duration: 1.15)) {
        spreads = true
      }
    }
    .allowsHitTesting(false)
    .accessibilityHidden(true)
  }
}
