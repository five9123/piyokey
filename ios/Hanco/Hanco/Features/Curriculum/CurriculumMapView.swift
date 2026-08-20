import DeckKit
import SwiftUI

struct HomeView: View {
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
            VStack(spacing: 14) {
              RetentionHomeView(today: today)
              HomePrimaryActionView(today: today)
            }
          }
          HomeRecommendationsView(catalog: catalog)
        }
        .padding(.horizontal, 18)
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
      .rootSettingsToolbar()
    }
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
  @EnvironmentObject private var progress: CurriculumProgressLibrary
  @EnvironmentObject private var deckLibrary: DeckLibrary
  @EnvironmentObject private var retention: RetentionLibrary
  @EnvironmentObject private var onboarding: OnboardingLibrary

  let today: JSTDay

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
      } else {
        NavigationLink {
          DailyChallengePracticeDestination(challenge: dailyChallenge)
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
          .lineLimit(1)
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

  private var dailyChallenge: DailyChallenge {
    DailyChallengeCatalog.challenge(for: today, goal: onboarding.selectedGoal)
  }

  private var isDailyComplete: Bool {
    retention.completedDailyChallenge(on: today)
  }
}

struct CurriculumMapView: View {
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
          if !isHatchOnboarding {
            freePracticeCard
          }
        }
        .padding(.horizontal, 18)
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
        Text(isHatchOnboarding ? "onboarding.hatch.navigation_title" : "curriculum.navigation_title")
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
            String(
              format: AppLocalization.string("onboarding.hatch.progress_format"),
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
            String(
              format: AppLocalization.string("onboarding.hatch.continue_format"),
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
          String(
            format: AppLocalization.string("curriculum.chapter_number_format"),
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
    let isCurrent = HatchOnboardingPolicy.nextRequiredStage(
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

  private var freePracticeCard: some View {
    NavigationLink {
      PracticeSetupView(
        createsNavigationStack: false,
        catalog: catalog
      )
    } label: {
      HStack(spacing: 13) {
        Image(systemName: "slider.horizontal.3")
          .font(.title2.weight(.bold))
          .foregroundStyle(AppPalette.accent)
          .frame(width: 46, height: 46)
          .background(AppPalette.accentSoft.opacity(0.58), in: RoundedRectangle(cornerRadius: 15))
        VStack(alignment: .leading, spacing: 3) {
          Text("curriculum.free_practice")
            .font(.headline.weight(.bold))
            .foregroundStyle(AppPalette.ink)
          Text("curriculum.free_practice_detail")
            .font(.caption)
            .foregroundStyle(AppPalette.mutedInk)
        }
        Spacer()
        Image(systemName: "chevron.right")
          .foregroundStyle(AppPalette.mutedInk)
      }
      .padding(17)
      .background(AppPalette.card, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
    }
    .buttonStyle(.plain)
    .accessibilityIdentifier("curriculum.free_practice")
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
  @EnvironmentObject private var deckLibrary: DeckLibrary
  @EnvironmentObject private var onboarding: OnboardingLibrary

  let catalog: Catalog?

  @ViewBuilder
  var body: some View {
    let recommendations = DeckRecommendationEngine.homeRecommendations(
      catalog: catalog,
      downloadHistory: deckLibrary.downloadHistory,
      installedDeckIDs: Set(deckLibrary.installedDecks.keys),
      preferredTags: onboarding.preferredTags
    )
    if !recommendations.isEmpty, let catalog {
      VStack(alignment: .leading, spacing: 10) {
        Label("recommendations.home.title", systemImage: "sparkles")
          .font(.headline.weight(.bold))
          .foregroundStyle(AppPalette.ink)
        Text(
          deckLibrary.downloadHistory.isEmpty
            ? "recommendations.home.cold_start_subtitle" : "recommendations.home.subtitle"
        )
        .font(.caption)
        .foregroundStyle(AppPalette.mutedInk)

        ScrollView(.horizontal, showsIndicators: false) {
          HStack(spacing: 12) {
            ForEach(recommendations, id: \.deckId) { deck in
              NavigationLink {
                DeckDetailView(deck: deck, catalogDecks: catalog.decks)
              } label: {
                DeckCardView(deck: deck)
                  .frame(width: 282)
              }
              .buttonStyle(.plain)
              .accessibilityIdentifier("home.recommendation.\(deck.deckId)")
            }
          }
          .padding(.bottom, 8)
        }
      }
      .frame(maxWidth: .infinity, alignment: .leading)
      .accessibilityIdentifier("home.recommendations")
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
      if !isLocked {
        Image(systemName: "chevron.right")
          .foregroundStyle(AppPalette.mutedInk)
      }
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
      allowsOSKeyboard: stage.chapterNumber >= 5
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

private struct HatchMissionSequenceDestination: View {
  @Environment(\.dismiss) private var dismiss
  @EnvironmentObject private var progress: CurriculumProgressLibrary
  @EnvironmentObject private var companion: MascotCompanionLibrary

  let onHatchCompleted: () -> Void
  @State private var activeStageID: String
  @State private var celebratingStage: MascotStage?
  @State private var advancesAfterCelebration = false

  init(initialStage: CurriculumStage, onHatchCompleted: @escaping () -> Void) {
    self.onHatchCompleted = onHatchCompleted
    _activeStageID = State(initialValue: initialStage.id)
  }

  var body: some View {
    Group {
      if let stage = CurriculumCatalog.stage(id: activeStageID) {
        CurriculumPracticeDestination(
          stage: stage,
          onResultFinished: advanceAfterResult,
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
    .onAppear(perform: presentPendingGrowthCelebration)
  }

  private func advanceAfterResult() {
    if let pendingStage = companion.pendingCelebration(current: progress.mascotStage) {
      advancesAfterCelebration = true
      celebratingStage = pendingStage
      return
    }
    advanceImmediately()
  }

  private func presentPendingGrowthCelebration() {
    guard celebratingStage == nil else { return }
    celebratingStage = companion.pendingCelebration(current: progress.mascotStage)
  }

  private func completeDeferredAdvance() {
    guard advancesAfterCelebration else { return }
    advancesAfterCelebration = false
    advanceImmediately()
  }

  private func advanceImmediately() {
    if let nextStage = HatchOnboardingPolicy.nextRequiredStage(
      completedStageIDs: progress.completedStageIDs
    ) {
      activeStageID = nextStage.id
    } else {
      onHatchCompleted()
    }
  }
}
