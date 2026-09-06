import DeckKit
import SwiftUI

struct HomeView: View {
  @Environment(\.dynamicTypeSize) private var dynamicTypeSize
  @Environment(\.hancoAdaptiveMetrics) private var adaptiveMetrics
  @EnvironmentObject private var gameProgress: GameProgressLibrary
  @EnvironmentObject private var reviewDeck: ReviewDeckLibrary
  @EnvironmentObject private var curriculumProgress: CurriculumProgressLibrary
  @EnvironmentObject private var retention: RetentionLibrary
  @EnvironmentObject private var onboarding: OnboardingLibrary

  let catalog: Catalog?

  var body: some View {
    NavigationStack {
      ScrollView {
        VStack(spacing: 20) {
          if hasPersistenceFailure {
            PersistenceRecoveryBanner(onRetry: retryFailedSaves)
          }
          TimelineView(.periodic(from: .now, by: 60)) { _ in
            let today = JSTDay(date: RetentionClock.now())
            let dashboard = usesLandscapeDashboard
              ? AnyLayout(HStackLayout(alignment: .top, spacing: 24))
              : AnyLayout(VStackLayout(spacing: 14))
            dashboard {
              RetentionHomeView(today: today)
              VStack(spacing: 14) {
                HomePrimaryActionView(today: today, catalog: catalog)
                HomeQuickActionsView(catalog: catalog)
              }
            }
          }
          HomeRecommendationsView(catalog: catalog)
        }
        .frame(maxWidth: usesLandscapeDashboard ? adaptiveMetrics.hubContentMaxWidth : adaptiveMetrics.readableContentMaxWidth)
        .frame(maxWidth: .infinity)
        .padding(.horizontal, adaptiveMetrics.horizontalPadding)
        .padding(.vertical, 16)
      }
      .background(
        LinearGradient(
          colors: [AppPalette.backgroundTop, AppPalette.backgroundBottom],
          startPoint: .topLeading,
          endPoint: .bottomTrailing
        )
        .ignoresSafeArea()
      )
      .navigationTitle(Text("home.navigation_title"))
      .navigationBarTitleDisplayMode(.inline)
      .accessibilityIdentifier("home.screen")
      .hancoAdaptiveDebugValue(adaptiveMetrics)
      .rootSettingsToolbar()
    }
  }

  private var usesLandscapeDashboard: Bool {
    adaptiveMetrics.availableWidth >= 1_100 && !adaptiveMetrics.isTall
      && !dynamicTypeSize.isAccessibilitySize
  }

  private var hasPersistenceFailure: Bool {
    gameProgress.saveFailed
      || reviewDeck.saveFailed
      || curriculumProgress.saveFailed
      || retention.saveFailed
      || onboarding.saveFailed
  }

  private func retryFailedSaves() {
    if gameProgress.saveFailed { gameProgress.retryLastSave() }
    if reviewDeck.saveFailed { reviewDeck.retryLastSave() }
    if curriculumProgress.saveFailed { curriculumProgress.retryLastSave() }
    if retention.saveFailed { retention.retryLastSave() }
    if onboarding.saveFailed { onboarding.retryLastSave() }
  }
}

private struct HomeQuickActionsView: View {
  @Environment(\.dynamicTypeSize) private var dynamicTypeSize
  @EnvironmentObject private var deckLibrary: DeckLibrary
  @EnvironmentObject private var onboarding: OnboardingLibrary

  let catalog: Catalog?
  @State private var randomSession: RandomWordPracticeSession?
  @State private var showsRandomPractice = false
  private let piyoCupDeck = PiyoCupDeckLoader.load()
  private let history = RandomWordPracticeHistory()

  var body: some View {
    Group {
      if dynamicTypeSize.isAccessibilitySize {
        VStack(spacing: 12) {
          piyoCupAction
          randomPracticeAction
        }
      } else {
        HStack(spacing: 12) {
          piyoCupAction
          randomPracticeAction
        }
      }
    }
    .navigationDestination(isPresented: $showsRandomPractice) {
      if let randomSession {
        RandomWordPracticeDestination(
          initialSession: randomSession,
          catalog: catalog,
          history: history
        )
      }
    }
  }

  @ViewBuilder
  private var piyoCupAction: some View {
    if let piyoCupDeck {
      NavigationLink {
        FlowGameView(deck: piyoCupDeck, competition: .weeklyPiyoCup)
      } label: {
        quickActionCard(
          title: "home.quick.piyo_cup.title",
          detail: "home.quick.piyo_cup.detail",
          systemImage: "crown.fill",
          tint: .orange
        )
      }
      .buttonStyle(.plain)
      .accessibilityIdentifier("home.quick.piyo_cup")
    } else {
      quickActionCard(
        title: "home.quick.piyo_cup.title",
        detail: "piyo_cup.unavailable",
        systemImage: "exclamationmark.circle.fill",
        tint: .orange
      )
      .opacity(0.55)
      .accessibilityIdentifier("home.quick.piyo_cup.unavailable")
    }
  }

  private var randomPracticeAction: some View {
    Button(action: startRandomPractice) {
      quickActionCard(
        title: "home.quick.random.title",
        detail: "home.quick.random.detail",
        systemImage: "shuffle",
        tint: AppPalette.secondary
      )
    }
    .buttonStyle(.plain)
    .accessibilityIdentifier("home.quick.random")
  }

  private func quickActionCard(
    title: LocalizedStringKey,
    detail: LocalizedStringKey,
    systemImage: String,
    tint: Color
  ) -> some View {
    VStack(alignment: .leading, spacing: 8) {
      Image(systemName: systemImage)
        .font(.title2.weight(.black))
        .foregroundStyle(tint)
        .frame(width: 42, height: 42)
        .background(tint.opacity(0.13), in: RoundedRectangle(cornerRadius: 14))
      Text(title)
        .font(.headline.weight(.heavy))
        .foregroundStyle(AppPalette.ink)
        .lineLimit(2)
        .fixedSize(horizontal: false, vertical: true)
      Text(detail)
        .font(.caption)
        .foregroundStyle(AppPalette.mutedInk)
        .lineLimit(2)
        .fixedSize(horizontal: false, vertical: true)
      Spacer(minLength: 0)
    }
    .frame(maxWidth: .infinity, minHeight: 128, alignment: .topLeading)
    .padding(15)
    .background(AppPalette.card, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
    .overlay {
      RoundedRectangle(cornerRadius: 22, style: .continuous)
        .strokeBorder(tint.opacity(0.18), lineWidth: 1)
    }
  }

  private func startRandomPractice() {
    let fallbackDecks = RandomWordPracticeCatalog.bundledFallbackDecks(
      goal: onboarding.selectedGoal,
      catalog: catalog
    )
    guard
      let session = RandomWordPracticeCatalog.makeSession(
        installedDecks: deckLibrary.installed,
        fallbackDecks: fallbackDecks,
        preferredTags: onboarding.preferredTags,
        recentWordKeys: history.recentWordKeys
      )
    else { return }
    history.record(session)
    randomSession = session
    showsRandomPractice = true
  }
}

private struct PersistenceRecoveryBanner: View {
  let onRetry: () -> Void

  var body: some View {
    HStack(spacing: 12) {
      Image(systemName: "exclamationmark.arrow.triangle.2.circlepath")
        .font(.title3.weight(.bold))
        .foregroundStyle(Color.orange)
      VStack(alignment: .leading, spacing: 3) {
        Text("persistence.failure.title")
          .font(.subheadline.weight(.bold))
          .foregroundStyle(AppPalette.ink)
        Text("persistence.failure.detail")
          .font(.caption)
          .foregroundStyle(AppPalette.mutedInk)
          .fixedSize(horizontal: false, vertical: true)
      }
      Spacer(minLength: 4)
      Button("persistence.failure.retry", action: onRetry)
        .font(.subheadline.weight(.bold))
        .foregroundStyle(AppPalette.accent)
        .frame(minWidth: 44, minHeight: 44)
    }
    .padding(14)
    .background(Color.orange.opacity(0.11), in: RoundedRectangle(cornerRadius: 18))
    .overlay {
      RoundedRectangle(cornerRadius: 18)
        .strokeBorder(Color.orange.opacity(0.3), lineWidth: 1)
    }
    .accessibilityElement(children: .contain)
    .accessibilityIdentifier("persistence.failure.banner")
  }
}

private struct HomePrimaryActionView: View {
  @AppStorage(OnboardingStore.homeLearningStartedKey) private var hasStartedLearning = false
  @EnvironmentObject private var progress: CurriculumProgressLibrary
  @EnvironmentObject private var deckLibrary: DeckLibrary
  @EnvironmentObject private var retention: RetentionLibrary
  @EnvironmentObject private var onboarding: OnboardingLibrary

  let today: JSTDay
  let catalog: Catalog?

  @ViewBuilder
  var body: some View {
    Group {
      if let stage = resumableStage {
        NavigationLink {
          CurriculumPracticeDestination(stage: stage)
        } label: {
          primaryCard(
            eyebrow: "home.primary.resume_eyebrow",
            title: stage.localizedTitle,
            detail: "home.primary.resume_detail",
            systemImage: "arrow.clockwise.circle.fill",
            completed: false
          )
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("home.primary.resume_curriculum")
      } else if let deck = recentPlayedDeck {
        NavigationLink {
          DeckPracticeDestination(deck: deck)
        } label: {
          primaryCard(
            eyebrow: "home.primary.resume_eyebrow",
            title: deck.appName,
            detail: "home.primary.deck_detail",
            systemImage: "play.circle.fill",
            completed: false
          )
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("home.primary.resume_deck")
      } else if let deck = starterRecommendation, let catalog {
        NavigationLink {
          DeckDetailView(deck: deck, catalogDecks: catalog.decks)
        } label: {
          primaryCard(
            eyebrow: "home.primary.recommend_eyebrow",
            title: deck.appName,
            detail: "home.primary.recommend_detail",
            systemImage: "sparkles",
            completed: false
          )
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("home.primary.recommend_deck")
      } else {
        NavigationLink {
          DailyChallengePracticeDestination(challenge: dailyChallenge)
            .onAppear { hasStartedLearning = true }
        } label: {
          primaryCard(
            eyebrow: "retention.daily.eyebrow",
            titleKey: isDailyComplete ? "retention.daily.completed" : "retention.daily.title",
            detail: "home.primary.daily_personalized",
            systemImage: isDailyComplete ? "checkmark.seal.fill" : "bolt.fill",
            completed: isDailyComplete
          )
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("retention.daily_challenge")
      }
    }
    .appTourTarget(.homePrimary)
    .onAppear { rememberLearningHistory() }
    .onChange(of: resumableStage?.id) { _ in rememberLearningHistory() }
    .onChange(of: recentPlayedDeck?.deckId) { _ in rememberLearningHistory() }
    .onChange(of: hasPriorHomeActivity) { _ in rememberLearningHistory() }
  }

  private func primaryCard(
    eyebrow: LocalizedStringKey,
    title: String,
    detail: LocalizedStringKey,
    systemImage: String,
    completed: Bool
  ) -> some View {
    primaryCard(
      eyebrow: eyebrow,
      titleView: Text(verbatim: title),
      detail: detail,
      systemImage: systemImage,
      completed: completed
    )
  }

  private func primaryCard(
    eyebrow: LocalizedStringKey,
    titleKey: LocalizedStringKey,
    detail: LocalizedStringKey,
    systemImage: String,
    completed: Bool
  ) -> some View {
    primaryCard(
      eyebrow: eyebrow,
      titleView: Text(titleKey),
      detail: detail,
      systemImage: systemImage,
      completed: completed
    )
  }

  private func primaryCard(
    eyebrow: LocalizedStringKey,
    titleView: Text,
    detail: LocalizedStringKey,
    systemImage: String,
    completed: Bool
  ) -> some View {
    HStack(spacing: 15) {
      ZStack {
        RoundedRectangle(cornerRadius: 18, style: .continuous)
          .fill(Color.white.opacity(0.18))
        Image(systemName: systemImage)
          .font(.title2.weight(.black))
          .foregroundStyle(.white)
      }
      .frame(width: 58, height: 58)

      VStack(alignment: .leading, spacing: 4) {
        Text(eyebrow)
          .font(.caption.weight(.black))
          .foregroundStyle(Color.white.opacity(0.8))
        titleView
          .font(.title3.weight(.heavy))
          .foregroundStyle(.white)
          .fixedSize(horizontal: false, vertical: true)
        Text(detail)
          .font(.caption)
          .foregroundStyle(Color.white.opacity(0.86))
          .lineLimit(2)
      }
      Spacer(minLength: 0)
      Image(systemName: "arrow.right.circle.fill")
        .font(.title2)
        .foregroundStyle(.white)
    }
    .padding(18)
    .background(
      completed ? AppPalette.success : AppPalette.accent,
      in: RoundedRectangle(cornerRadius: 26, style: .continuous)
    )
    .shadow(color: AppPalette.accent.opacity(0.25), radius: 14, y: 8)
  }

  private var resumableStage: CurriculumStage? {
    guard let stageID = progress.activeSession?.stageId else { return nil }
    return CurriculumCatalog.stage(id: stageID)
  }

  private var recentPlayedDeck: Deck? {
    deckLibrary.installed.first { deck in
      deckLibrary.records[deck.deckId]?.lastPlayedAt != nil
    }
  }

  private var starterRecommendation: CatalogDeck? {
    // Hatch missions are onboarding, not a previous home learning session.
    guard !hasStartedLearning, !hasPriorHomeActivity else { return nil }
    return DeckRecommendationEngine.starterRecommendations(
      catalog: catalog, preferredTags: onboarding.preferredTags,
      level: onboarding.selectedLevel, limit: 1
    ).first
  }

  private var hasPriorHomeActivity: Bool {
    progress.completedStageIDs.contains { (CurriculumCatalog.stage(id: $0)?.chapterNumber ?? 0) > 3 }
      || retention.records.values.contains { $0.activities.contains { $0 != .curriculum } }
  }

  private func rememberLearningHistory() {
    if resumableStage != nil || recentPlayedDeck != nil || hasPriorHomeActivity {
      hasStartedLearning = true
    }
  }

  private var dailyChallenge: DailyChallenge {
    DailyChallengeCatalog.challenge(for: today, goal: onboarding.selectedGoal)
  }

  private var isDailyComplete: Bool {
    retention.completedDailyChallenge(on: today)
  }
}

struct CurriculumMapView: View {
  @Environment(\.hancoAdaptiveMetrics) private var adaptiveMetrics
  @EnvironmentObject private var progress: CurriculumProgressLibrary
  @EnvironmentObject private var companion: MascotCompanionLibrary

  @State private var celebratingStage: MascotStage?
  @State private var growthPresentationTask: Task<Void, Never>?

  let catalog: Catalog?
  var isHatchOnboarding = false
  var onHatchCompleted: () -> Void = {}

  var body: some View {
    NavigationStack {
      ScrollView {
        VStack(spacing: 20) {
          if isHatchOnboarding {
            hatchMissionHeader
            if progress.saveFailed {
              PersistenceRecoveryBanner(onRetry: progress.retryLastSave)
            }
          }

          ForEach(visibleChapters) { chapter in
            chapterSection(chapter)
          }
        }
        .frame(maxWidth: adaptiveMetrics.readableContentMaxWidth)
        .padding(.horizontal, adaptiveMetrics.horizontalPadding)
        .frame(maxWidth: .infinity)
        .padding(.vertical, 16)
      }
      .background(
        LinearGradient(
          colors: [AppPalette.backgroundTop, AppPalette.backgroundBottom],
          startPoint: .topLeading,
          endPoint: .bottomTrailing
        )
        .ignoresSafeArea()
      )
      .navigationTitle(
        Text(
          isHatchOnboarding ? "onboarding.hatch.navigation_title" : "curriculum.navigation_title")
      )
      .navigationBarTitleDisplayMode(.inline)
      .accessibilityIdentifier(
        isHatchOnboarding ? "onboarding.hatch.screen" : "curriculum.map.screen"
      )
      .rootSettingsToolbar()
      .fullScreenCover(
        item: $celebratingStage,
        onDismiss: schedulePendingGrowthCelebration
      ) { stage in
        GrowthCelebrationView(newStage: stage) {
          companion.markCelebrated(stage)
          celebratingStage = nil
        }
      }
      .onAppear(perform: schedulePendingGrowthCelebration)
      .onDisappear {
        growthPresentationTask?.cancel()
        growthPresentationTask = nil
      }
    }
  }

  private var visibleChapters: [CurriculumChapter] {
    isHatchOnboarding
      ? Array(CurriculumCatalog.chapters.prefix(HatchOnboardingPolicy.requiredChapterCount))
      : CurriculumCatalog.chapters
  }

  private var completedHatchMissionCount: Int {
    HatchOnboardingPolicy.requiredStages.filter {
      progress.completedStageIDs.contains($0.id)
    }.count
  }

  @ViewBuilder
  private var hatchMissionHeader: some View {
    VStack(spacing: 15) {
      HStack(spacing: 16) {
        GrowingMascotView(
          mood: completedHatchMissionCount == 0 ? .idle : .happy,
          size: 74,
          calm: true
        )
        .frame(width: 96, height: 128)

        VStack(alignment: .leading, spacing: 6) {
          Text("onboarding.hatch.title")
            .font(.system(.title2, design: .rounded, weight: .heavy))
            .foregroundStyle(AppPalette.ink)
          Text("onboarding.hatch.subtitle")
            .font(.subheadline)
            .foregroundStyle(AppPalette.mutedInk)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
      }

      VStack(spacing: 7) {
        HStack {
          Text("onboarding.hatch.progress_label")
            .font(.caption.weight(.bold))
            .foregroundStyle(AppPalette.secondary)
          Spacer()
          Text(
            AppLocalization.format("onboarding.hatch.progress_format",
              completedHatchMissionCount,
              HatchOnboardingPolicy.requiredChapterCount
            )
          )
          .font(.caption.monospacedDigit().weight(.black))
          .foregroundStyle(AppPalette.accent)
        }
        ProgressView(
          value: Double(completedHatchMissionCount),
          total: Double(HatchOnboardingPolicy.requiredChapterCount)
        )
        .tint(AppPalette.accent)
      }

      if let nextStage = HatchOnboardingPolicy.nextRequiredStage(
        completedStageIDs: progress.completedStageIDs
      ) {
        NavigationLink {
          HatchMissionSequenceDestination(
            initialStage: nextStage,
            onHatchCompleted: onHatchCompleted
          )
        } label: {
          Label(
            AppLocalization.format("onboarding.hatch.continue_format",
              nextStage.chapterNumber
            ),
            systemImage: "arrow.right.circle.fill"
          )
          .font(.headline.weight(.bold))
          .foregroundStyle(.white)
          .frame(maxWidth: .infinity)
          .padding(.vertical, 14)
          .background(AppPalette.accent, in: RoundedRectangle(cornerRadius: 17))
        }
        .accessibilityIdentifier("onboarding.hatch.continue")
      } else {
        Button(action: onHatchCompleted) {
          Label("onboarding.hatch.open_app", systemImage: "sparkles")
            .font(.headline.weight(.bold))
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
            .background(AppPalette.success, in: RoundedRectangle(cornerRadius: 17))
        }
        .accessibilityIdentifier("onboarding.hatch.open_app")
      }
    }
    .padding(18)
    .background(AppPalette.card, in: RoundedRectangle(cornerRadius: 28, style: .continuous))
    .shadow(color: AppPalette.keyShadow, radius: 14, y: 8)
  }

  private func chapterSection(_ chapter: CurriculumChapter) -> some View {
    VStack(alignment: .leading, spacing: 12) {
      HStack {
        Text(
          AppLocalization.format("curriculum.chapter_number_format",
            chapter.number
          )
        )
        .font(.caption.monospacedDigit().weight(.black))
        .foregroundStyle(.white)
        .padding(.horizontal, 9)
        .padding(.vertical, 5)
        .background(AppPalette.secondary, in: Capsule())

        Text(verbatim: chapter.localizedTitle)
          .font(.headline.weight(.heavy))
          .foregroundStyle(AppPalette.ink)
        Spacer()
        if chapter.number >= 5 {
          Text("curriculum.free_selection")
            .font(.caption2.weight(.bold))
            .foregroundStyle(AppPalette.secondary)
        }
      }

      ForEach(chapter.stages) { stage in
        stageRow(stage)
      }
    }
    .frame(maxWidth: .infinity, alignment: .leading)
    .appTourTarget(
      .practiceCurriculum,
      enabled: !isHatchOnboarding && chapter.number == 1
    )
  }

  @ViewBuilder
  private func stageRow(_ stage: CurriculumStage) -> some View {
    if isHatchOnboarding {
      hatchStageRow(stage)
    } else {
      regularStageRow(stage)
    }
  }

  @ViewBuilder
  private func regularStageRow(_ stage: CurriculumStage) -> some View {
    let unlocked = CurriculumUnlockPolicy.isUnlocked(
      stage,
      completedStageIDs: progress.completedStageIDs
    )
    if unlocked {
      NavigationLink {
        CurriculumPracticeDestination(stage: stage)
      } label: {
        CurriculumStageRow(
          stage: stage,
          stageProgress: progress.progress(for: stage.id),
          isResumable: progress.activeSession?.stageId == stage.id,
          isLocked: false
        )
      }
      .buttonStyle(.plain)
      .accessibilityIdentifier("curriculum.stage.\(stage.id)")
    } else {
      CurriculumStageRow(
        stage: stage,
        stageProgress: nil,
        isResumable: false,
        isLocked: true
      )
      .accessibilityElement(children: .combine)
      .accessibilityLabel(Text(verbatim: stage.localizedTitle))
      .accessibilityValue(Text("curriculum.locked"))
      .accessibilityIdentifier("curriculum.stage.\(stage.id).locked")
    }
  }

  private func hatchStageRow(_ stage: CurriculumStage) -> some View {
    let completed = progress.completedStageIDs.contains(stage.id)
    let isCurrent =
      HatchOnboardingPolicy.nextRequiredStage(
        completedStageIDs: progress.completedStageIDs
      )?.id == stage.id
    return CurriculumStageRow(
      stage: stage,
      stageProgress: progress.progress(for: stage.id),
      isResumable: progress.activeSession?.stageId == stage.id,
      isLocked: !completed && !isCurrent,
      isHighlighted: isCurrent
    )
    .accessibilityElement(children: .combine)
    .accessibilityIdentifier("onboarding.hatch.stage.\(stage.id)")
  }

  private func schedulePendingGrowthCelebration() {
    growthPresentationTask?.cancel()
    growthPresentationTask = Task { @MainActor in
      try? await Task.sleep(nanoseconds: 550_000_000)
      guard !Task.isCancelled, celebratingStage == nil else { return }
      celebratingStage = companion.pendingCelebration(current: progress.mascotStage)
      growthPresentationTask = nil
    }
  }
}

private struct HomeRecommendationsView: View {
  @Environment(\.hancoAdaptiveMetrics) private var adaptiveMetrics
  @ScaledMetric(relativeTo: .body) private var compactRecommendationCardHeight: CGFloat =
    DeckCardView.compactMinimumHeight
  @ScaledMetric(relativeTo: .body) private var regularRecommendationCardHeight: CGFloat =
    DeckCardView.regularMinimumHeight
  @EnvironmentObject private var curriculumProgress: CurriculumProgressLibrary
  @EnvironmentObject private var deckLibrary: DeckLibrary
  @EnvironmentObject private var gameProgress: GameProgressLibrary
  @EnvironmentObject private var onboarding: OnboardingLibrary

  let catalog: Catalog?

  @ViewBuilder
  var body: some View {
    let personalRecommendations = DeckRecommendationEngine.homeRecommendations(
      catalog: catalog,
      downloadHistory: deckLibrary.downloadHistory,
      installedDeckIDs: Set(deckLibrary.installedDecks.keys),
      preferredTags: onboarding.preferredTags
    )
    if !personalRecommendations.isEmpty, let catalog {
      let recent = mostRecentLearningSignal(in: catalog)
      let nextStepRecommendations = DeckRecommendationEngine.nextStepRecommendations(
        catalog: catalog,
        installedDeckIDs: Set(deckLibrary.installedDecks.keys),
        excludingDeckIDs: Set(personalRecommendations.map(\.deckId)),
        preferredLevel: onboarding.selectedLevel,
        recentDeckID: recent?.deckID,
        recentAccuracy: recent?.accuracy
      )
      VStack(alignment: .leading, spacing: 22) {
        recommendationSection(
          title: "recommendations.home.title",
          subtitle: deckLibrary.downloadHistory.isEmpty
            ? "recommendations.home.cold_start_subtitle"
            : "recommendations.home.subtitle",
          systemImage: "sparkles",
          decks: personalRecommendations,
          catalog: catalog,
          identifier: "home.recommendations.personal",
          itemIdentifierPrefix: "home.recommendation"
        )
        if !nextStepRecommendations.isEmpty {
          recommendationSection(
            title: "recommendations.home.next_step.title",
            subtitle: recent == nil
              ? "recommendations.home.next_step.cold_start_subtitle"
              : "recommendations.home.next_step.subtitle",
            systemImage: "arrow.up.right.circle.fill",
            decks: nextStepRecommendations,
            catalog: catalog,
            identifier: "home.recommendations.next_step",
            itemIdentifierPrefix: "home.next_step"
          )
        }
      }
      .frame(maxWidth: .infinity, alignment: .leading)
      .accessibilityElement(children: .contain)
      .accessibilityIdentifier("home.recommendations")
    }
  }

  @ViewBuilder
  private func recommendationSection(
    title: LocalizedStringKey,
    subtitle: LocalizedStringKey,
    systemImage: String,
    decks: [CatalogDeck],
    catalog: Catalog,
    identifier: String,
    itemIdentifierPrefix: String
  ) -> some View {
    VStack(alignment: .leading, spacing: 10) {
      Label(title, systemImage: systemImage)
        .font(.headline.weight(.bold))
        .foregroundStyle(AppPalette.ink)
      Text(subtitle)
        .font(.caption)
        .foregroundStyle(AppPalette.mutedInk)

      if adaptiveMetrics.isExpanded {
        LazyVGrid(
          columns: Array(
            repeating: GridItem(.flexible(), spacing: 12, alignment: .top),
            count: 3
          ),
          alignment: .leading,
          spacing: 12
        ) {
          ForEach(decks, id: \.deckId) { deck in
            recommendationLink(
              deck: deck,
              catalog: catalog,
              compact: true,
              identifier: "\(itemIdentifierPrefix).\(deck.deckId)"
            )
          }
        }
      } else {
        ScrollView(.horizontal, showsIndicators: false) {
          LazyHStack(spacing: 12) {
            ForEach(decks, id: \.deckId) { deck in
              recommendationLink(
                deck: deck,
                catalog: catalog,
                compact: false,
                identifier: "\(itemIdentifierPrefix).\(deck.deckId)"
              )
              .frame(width: 282)
            }
          }
          .padding(.bottom, 8)
        }
      }
    }
    .accessibilityElement(children: .contain)
    .accessibilityIdentifier(identifier)
  }

  private func recommendationLink(
    deck: CatalogDeck,
    catalog: Catalog,
    compact: Bool,
    identifier: String
  ) -> some View {
    NavigationLink {
      DeckDetailView(deck: deck, catalogDecks: catalog.decks)
    } label: {
      DeckCardView(
        deck: deck,
        compact: compact,
        fixedHeight: recommendationCardHeight(compact: compact),
        limitsTitleToOneLine: true
      )
    }
    .buttonStyle(.plain)
    .accessibilityIdentifier(identifier)
  }

  private func recommendationCardHeight(compact: Bool) -> CGFloat {
    compact ? compactRecommendationCardHeight : regularRecommendationCardHeight
  }

  private struct LearningSignal {
    let deckID: String
    let accuracy: Double
    let date: Date
  }

  private func mostRecentLearningSignal(in catalog: Catalog) -> LearningSignal? {
    let catalogDeckIDs = Set(catalog.decks.map(\.deckId))
    let catalogSignal = gameProgress.records
      .filter { catalogDeckIDs.contains($0.deckId) }
      .max { $0.playedAt < $1.playedAt }
      .map {
        LearningSignal(deckID: $0.deckId, accuracy: $0.accuracy, date: $0.playedAt)
      }
    let curriculumSignal = curriculumProgress.stageProgress.values
      .max { $0.completedAt < $1.completedAt }
      .flatMap { progress -> LearningSignal? in
        guard
          let stage = CurriculumCatalog.stage(id: progress.stageId),
          let deckID = DeckRecommendationEngine.learningPathDeckID(
            forCurriculumChapter: stage.chapterNumber
          )
        else { return nil }
        return LearningSignal(
          deckID: deckID,
          accuracy: progress.bestAccuracy,
          date: progress.completedAt
        )
      }

    switch (catalogSignal, curriculumSignal) {
    case let (catalog?, curriculum?):
      return catalog.date >= curriculum.date ? catalog : curriculum
    case let (catalog?, nil):
      return catalog
    case let (nil, curriculum?):
      return curriculum
    case (nil, nil):
      return nil
    }
  }
}

private struct CurriculumStageRow: View {
  let stage: CurriculumStage
  let stageProgress: CurriculumStageProgress?
  let isResumable: Bool
  let isLocked: Bool
  var isHighlighted = false

  var body: some View {
    HStack(spacing: 14) {
      CurriculumStageStamp(
        symbol: isLocked ? "lock.fill" : stage.symbol,
        isCompleted: stageProgress != nil,
        isLocked: isLocked
      )

      VStack(alignment: .leading, spacing: 5) {
        Text(verbatim: stage.localizedTitle)
          .font(.headline.weight(.bold))
          .foregroundStyle(isLocked ? AppPalette.mutedInk : AppPalette.ink)
        Text(verbatim: stage.localizedDetail)
          .font(.caption)
          .foregroundStyle(AppPalette.mutedInk)
          .lineLimit(2)
        HStack(spacing: 4) {
          ForEach(0..<3, id: \.self) { index in
            Image(systemName: index < (stageProgress?.stars ?? 0) ? "star.fill" : "star")
              .foregroundStyle(
                index < (stageProgress?.stars ?? 0)
                  ? Color.yellow : AppPalette.mutedInk.opacity(0.28))
          }
          if isResumable {
            Text("curriculum.resume")
              .font(.caption2.weight(.black))
              .foregroundStyle(AppPalette.accent)
              .padding(.leading, 5)
          }
        }
        .font(.caption)
      }
      Spacer()
    }
    .padding(16)
    .background(
      (isLocked ? AppPalette.card.opacity(0.62) : AppPalette.card),
      in: RoundedRectangle(cornerRadius: 22, style: .continuous)
    )
    .overlay {
      RoundedRectangle(cornerRadius: 22, style: .continuous)
        .stroke(
          isResumable || isHighlighted ? AppPalette.accent.opacity(0.55) : Color.clear,
          lineWidth: 2
        )
    }
  }
}

private struct CurriculumStageStamp: View {
  let symbol: String
  let isCompleted: Bool
  let isLocked: Bool
  @State private var stampScale = 0.72

  var body: some View {
    ZStack {
      Circle()
        .fill(
          isCompleted
            ? AppPalette.accentSoft.opacity(0.8)
            : AppPalette.backgroundBottom.opacity(isLocked ? 0.45 : 0.9)
        )
      Circle()
        .strokeBorder(
          isCompleted ? AppPalette.accent : AppPalette.mutedInk.opacity(0.25),
          style: StrokeStyle(lineWidth: 2, dash: isCompleted ? [] : [4, 4])
        )
      Image(systemName: isCompleted ? "seal.fill" : symbol)
        .font(.title2.weight(.black))
        .foregroundStyle(
          isCompleted ? AppPalette.accent : AppPalette.mutedInk.opacity(isLocked ? 0.45 : 0.8)
        )
    }
    .frame(width: 58, height: 58)
    .scaleEffect(stampScale)
    .onAppear {
      withAnimation(.spring(response: 0.34, dampingFraction: 0.58)) {
        stampScale = 1
      }
    }
  }
}

private struct CurriculumPracticeDestination: View {
  @EnvironmentObject private var progress: CurriculumProgressLibrary
  @EnvironmentObject private var retention: RetentionLibrary

  let stage: CurriculumStage
  var onResultFinished: (() -> Void)? = nil
  var onPersistenceFailureExit: (() -> Void)? = nil
  var chainsHatchMissions = false
  var isFinalHatchMission = false
  @State private var session = RetentionSessionContext()

  var body: some View {
    PracticeView(
      targets: practiceItems.map(\.ko),
      sessionTitle: stage.localizedTitle,
      reviewSources: practiceItems.map {
        PracticeReviewSource(item: $0.deckItem, sourceDeckId: stage.sourceDeckId)
      },
      curriculumStageID: stage.id,
      checkpoint: progress.checkpoint(for: stage.id),
      onCheckpoint: { checkpoint in
        progress.save(stageId: stage.id, checkpoint: checkpoint)
      },
      onCheckpointFlush: {
        _ = await progress.flushAndWait()
      },
      onCurriculumCompletion: { accuracy, _, stars in
        let saved = await progress.finishAndWait(
          stageId: stage.id,
          stars: stars,
          accuracy: accuracy
        )
        if saved, stars > 0 {
          retention.record(.curriculum, session: session)
          if chainsHatchMissions,
            let index = HatchOnboardingPolicy.requiredStages.firstIndex(where: {
              $0.id == stage.id
            })
          {
            TelemetryService.shared.capture(
              .onboardingStepCompleted,
              properties: [.onboardingStep: "hatch_\(index + 1)"]
            )
          }
        }
        return saved
      },
      onSessionRestart: {
        session = RetentionSessionContext()
      },
      onResultFinished: onResultFinished,
      onPersistenceFailureExit: onPersistenceFailureExit,
      chainsHatchMissions: chainsHatchMissions,
      isFinalHatchMission: isFinalHatchMission,
      allowsOSKeyboard: true,
      allowsKorean10Key: false,
      analyticsSessionKind: "lesson",
      analyticsDeckSource: "curriculum"
    )
  }

  private var practiceItems: [CurriculumItem] {
    #if DEBUG
      if let rawLimit = ProcessInfo.processInfo.environment["UITEST_CURRICULUM_ITEM_LIMIT"],
        let limit = Int(rawLimit), limit > 0
      {
        return Array(stage.items.prefix(limit))
      }
    #endif
    return stage.items
  }
}

struct HatchMissionTransitionCoordinator: Equatable {
  enum Destination: Equatable {
    case mission(String)
    case home
  }

  enum State: Equatable {
    case awaitingResultDismissal(String)
    case waitingForPresentationTransition(
      completedStageID: String,
      destination: Destination,
      pendingCelebration: MascotStage?
    )
    case celebrating(
      completedStageID: String,
      destination: Destination,
      stage: MascotStage
    )
    case advancing(completedStageID: String, destination: Destination)
    case completed
  }

  enum Action: Equatable {
    case none
    case waitForPresentationTransition
    case presentCelebration(MascotStage)
    case advanceToMission(String)
    case completeHatch
  }

  static let presentationSettlementNanoseconds: UInt64 = 650_000_000

  private(set) var state: State

  init(activeStageID: String) {
    state = .awaitingResultDismissal(activeStageID)
  }

  var isWaitingForPresentationTransition: Bool {
    if case .waitingForPresentationTransition = state { return true }
    return false
  }

  func ownsCelebration(for stageID: String) -> Bool {
    switch state {
    case .waitingForPresentationTransition(let completedStageID, _, _),
      .celebrating(let completedStageID, _, _):
      return completedStageID == stageID
    default:
      return false
    }
  }

  mutating func resultDidDismiss(
    completedStageID: String,
    nextStageID: String?,
    pendingCelebration: MascotStage?
  ) -> Action {
    guard case .awaitingResultDismissal(let activeStageID) = state,
      activeStageID == completedStageID
    else { return .none }
    let destination = nextStageID.map(Destination.mission) ?? .home
    state = .waitingForPresentationTransition(
      completedStageID: completedStageID,
      destination: destination,
      pendingCelebration: pendingCelebration
    )
    return .waitForPresentationTransition
  }

  mutating func presentationTransitionDidFinish() -> Action {
    guard case .waitingForPresentationTransition(
      let completedStageID,
      let destination,
      let pendingCelebration
    ) = state else {
      return .none
    }
    if let pendingCelebration {
      state = .celebrating(
        completedStageID: completedStageID,
        destination: destination,
        stage: pendingCelebration
      )
      return .presentCelebration(pendingCelebration)
    }
    return beginAdvance(completedStageID: completedStageID, destination: destination)
  }

  mutating func celebrationDidDismiss() -> Action {
    guard case .celebrating(let completedStageID, let destination, _) = state else {
      return .none
    }
    return beginAdvance(completedStageID: completedStageID, destination: destination)
  }

  mutating func missionDidActivate(_ stageID: String) -> Bool {
    guard case .advancing(_, .mission(let expectedStageID)) = state,
      stageID == expectedStageID
    else { return false }
    state = .awaitingResultDismissal(stageID)
    return true
  }

  private mutating func beginAdvance(
    completedStageID: String,
    destination: Destination
  ) -> Action {
    switch destination {
    case .mission(let stageID):
      state = .advancing(completedStageID: completedStageID, destination: destination)
      return .advanceToMission(stageID)
    case .home:
      state = .completed
      return .completeHatch
    }
  }
}

private struct HatchMissionSequenceDestination: View {
  @Environment(\.dismiss) private var dismiss
  @Environment(\.scenePhase) private var scenePhase
  @EnvironmentObject private var progress: CurriculumProgressLibrary
  @EnvironmentObject private var companion: MascotCompanionLibrary

  let onHatchCompleted: () -> Void
  @State private var activeStageID: String
  @State private var celebratingStage: MascotStage?
  @State private var transition: HatchMissionTransitionCoordinator
  @State private var transitionTask: Task<Void, Never>?

  init(initialStage: CurriculumStage, onHatchCompleted: @escaping () -> Void) {
    self.onHatchCompleted = onHatchCompleted
    _activeStageID = State(initialValue: initialStage.id)
    _transition = State(
      initialValue: HatchMissionTransitionCoordinator(activeStageID: initialStage.id)
    )
  }

  var body: some View {
    Group {
      if let stage = CurriculumCatalog.stage(id: activeStageID) {
        CurriculumPracticeDestination(
          stage: stage,
          onResultFinished: { settleAfterResult(for: stage.id) },
          onPersistenceFailureExit: dismiss.callAsFunction,
          chainsHatchMissions: true,
          isFinalHatchMission: stage.id == HatchOnboardingPolicy.requiredStages.last?.id
        )
        .id(stage.id)
      }
    }
    .fullScreenCover(
      item: $celebratingStage,
      onDismiss: completeDeferredAdvance
    ) { stage in
      GrowthCelebrationView(newStage: stage) {
        companion.markCelebrated(stage)
        celebratingStage = nil
      }
    }
    .onAppear {
      presentPendingGrowthCelebration()
      schedulePresentationTransitionIfNeeded()
    }
    .onChange(of: scenePhase) { phase in
      if phase == .active {
        schedulePresentationTransitionIfNeeded()
      } else {
        transitionTask?.cancel()
        transitionTask = nil
      }
    }
    .onDisappear {
      transitionTask?.cancel()
      transitionTask = nil
    }
  }

  private func settleAfterResult(for completedStageID: String) {
    let nextStageID = HatchOnboardingPolicy.nextRequiredStage(
      completedStageIDs: progress.completedStageIDs
    )?.id
    performTransitionAction(
      transition.resultDidDismiss(
        completedStageID: completedStageID,
        nextStageID: nextStageID,
        pendingCelebration: companion.pendingCelebration(current: progress.mascotStage)
      )
    )
  }

  private func presentPendingGrowthCelebration() {
    guard celebratingStage == nil else { return }
    // Result dismissal owns any pending celebration while its navigation pop settles.
    // Reappearing under that pop must not race it with a second full-screen cover.
    if transition.ownsCelebration(for: activeStageID) { return }
    celebratingStage = companion.pendingCelebration(current: progress.mascotStage)
  }

  private func completeDeferredAdvance() {
    performTransitionAction(transition.celebrationDidDismiss())
  }

  private func performTransitionAction(
    _ action: HatchMissionTransitionCoordinator.Action
  ) {
    switch action {
    case .none:
      break
    case .waitForPresentationTransition:
      schedulePresentationTransitionIfNeeded()
    case .presentCelebration(let stage):
      celebratingStage = stage
    case .advanceToMission(let stageID):
      activeStageID = stageID
      _ = transition.missionDidActivate(stageID)
    case .completeHatch:
      onHatchCompleted()
    }
  }

  private func schedulePresentationTransitionIfNeeded() {
    guard transition.isWaitingForPresentationTransition, scenePhase == .active
    else { return }
    transitionTask?.cancel()
    transitionTask = Task { @MainActor in
      do {
        try await Task.sleep(
          nanoseconds: HatchMissionTransitionCoordinator.presentationSettlementNanoseconds
        )
      } catch {
        return
      }
      guard !Task.isCancelled, scenePhase == .active else { return }
      transitionTask = nil
      performTransitionAction(transition.presentationTransitionDidFinish())
    }
  }
}
