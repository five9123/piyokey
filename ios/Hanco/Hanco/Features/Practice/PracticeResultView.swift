import DeckKit
import SwiftUI

enum PracticeCompletionPersistenceState: Equatable {
  case saving
  case saved
  case failed
}

struct PracticeResultView: View {
  @Environment(\.dismiss) private var dismiss
  @EnvironmentObject private var deckLibrary: DeckLibrary
  @EnvironmentObject private var retention: RetentionLibrary
  @EnvironmentObject private var companion: MascotCompanionLibrary
  @State private var showsReviewDeck = false

  let sessionTitle: String
  let accuracyPercent: Double
  let mistakeCount: Int
  let completedItemCount: Int
  let earnedStarsOverride: Int?
  let reviewItems: [SessionReviewItem]
  let recommendations: [CatalogDeck]
  let catalogDecks: [CatalogDeck]
  let showsRetry: Bool
  let retryTitle: LocalizedStringKey
  let retrySystemImage: String
  let finishTitle: LocalizedStringKey
  let finishSystemImage: String
  let finishAccessibilityIdentifier: String
  let finishIsPrimary: Bool
  let finishIsEnabled: Bool
  let showsSecondaryActions: Bool
  let showsToolbarFinish: Bool
  let completionPersistenceState: PracticeCompletionPersistenceState?
  let onRetry: () -> Void
  let onPersistenceRetry: () -> Void
  let onPersistenceRecoveryExit: () -> Void
  let onFinish: () -> Void

  var body: some View {
    SessionResultView(
      navigationTitle: "practice.result.navigation_title",
      onFinish: onFinish,
      finishTitle: finishTitle,
      finishSystemImage: finishSystemImage,
      finishAccessibilityIdentifier: finishAccessibilityIdentifier,
      finishIsPrimary: finishIsPrimary,
      finishIsEnabled: finishIsEnabled,
      showsToolbarFinish: showsToolbarFinish,
      header: clearCard,
      score: completionScoreCard,
      metrics: metricsCard,
      review: { _ in
        SessionResultReviewSection(
          items: reviewItems,
          onSeeAll: { showsReviewDeck = true }
        )
      },
      actions: { _ in
        if showsSecondaryActions {
          nextActionsSection
        }
      },
      bottomBar: { _ in
        VStack(spacing: 10) {
          persistenceStatus
          if showsRetry {
            retryBar
          }
        }
      }
    )
    .navigationDestination(isPresented: $showsReviewDeck) {
      ReviewDeckView()
    }
  }

  @ViewBuilder
  private var persistenceStatus: some View {
    switch completionPersistenceState {
    case .saving:
      HStack(spacing: 9) {
        ProgressView()
        Text("persistence.saving")
          .font(.subheadline.weight(.semibold))
          .foregroundStyle(AppPalette.mutedInk)
      }
      .frame(maxWidth: .infinity)
      .padding(.vertical, 8)
      .accessibilityElement(children: .combine)
      .accessibilityIdentifier("onboarding.hatch.persistence.saving")

    case .failed:
      VStack(alignment: .leading, spacing: 9) {
        Label("persistence.failure.title", systemImage: "exclamationmark.triangle.fill")
          .font(.subheadline.weight(.bold))
          .foregroundStyle(Color.orange)
          .accessibilityIdentifier("onboarding.hatch.persistence.failure")
        Text("persistence.failure.detail")
          .font(.caption)
          .foregroundStyle(AppPalette.mutedInk)
          .fixedSize(horizontal: false, vertical: true)
        HStack(spacing: 10) {
          Button("persistence.failure.retry", action: onPersistenceRetry)
            .buttonStyle(.borderedProminent)
            .tint(AppPalette.accent)
            .frame(minHeight: 44)
            .accessibilityIdentifier("onboarding.hatch.persistence.retry")
          Button("result.back", action: onPersistenceRecoveryExit)
            .buttonStyle(.bordered)
            .frame(minHeight: 44)
            .accessibilityIdentifier("onboarding.hatch.persistence.back")
        }
      }
      .frame(maxWidth: .infinity, alignment: .leading)
      .padding(13)
      .background(Color.orange.opacity(0.11), in: RoundedRectangle(cornerRadius: 17))
      .overlay {
        RoundedRectangle(cornerRadius: 17)
          .strokeBorder(Color.orange.opacity(0.3), lineWidth: 1)
      }

    case .saved, .none:
      EmptyView()
    }
  }

  private func clearCard(_ reveal: SessionResultRevealState) -> some View {
    HStack(spacing: 14) {
      GrowingMascotView(
        mood: resultMascotMood,
        reaction: mistakeCount == 0 ? .perfectSession : .wordCompleted,
        reactionRevision: 1,
        showsNameTag: true,
        size: 60
      )
      .frame(width: 78, height: 118)

      VStack(alignment: .leading, spacing: 7) {
        Text(verbatim: sessionTitle)
          .font(.caption.weight(.bold))
          .foregroundStyle(AppPalette.secondary)
          .padding(.horizontal, 10)
          .padding(.vertical, 5)
          .background(AppPalette.accentSoft.opacity(0.52), in: Capsule())

        Text(displayedStars == 0 ? "practice.result.try_again" : "practice.result.clear")
          .font(.system(.title2, design: .rounded, weight: .heavy))
          .foregroundStyle(AppPalette.ink)
          .accessibilityIdentifier("practice.result.screen")

        HStack(spacing: 7) {
          ForEach(0..<3, id: \.self) { index in
            let progress = reveal.starProgress(at: index)
            Image(systemName: index < displayedStars ? "star.fill" : "star")
              .foregroundStyle(
                index < displayedStars ? Color.yellow : AppPalette.mutedInk.opacity(0.35)
              )
              .scaleEffect(CGFloat(0.45 + progress * 0.55))
              .opacity(progress)
          }
        }
        .font(.title2)
      }
      Spacer()
    }
    .padding(20)
    .background(AppPalette.card, in: RoundedRectangle(cornerRadius: 28, style: .continuous))
    .shadow(color: AppPalette.keyShadow, radius: 14, y: 8)
  }

  private func completionScoreCard(_ reveal: SessionResultRevealState) -> some View {
    let displayedItems = Int(Double(completedItemCount) * reveal.scoreProgress)
    return VStack(spacing: 5) {
      Text("practice.result.items")
        .font(.caption.weight(.bold))
        .foregroundStyle(AppPalette.mutedInk)
      Text(verbatim: displayedItems.formatted())
        .font(.system(size: 44, weight: .black, design: .rounded))
        .foregroundStyle(AppPalette.accent)
        .monospacedDigit()
        .accessibilityIdentifier("practice.result.score.value")
    }
    .frame(maxWidth: .infinity)
    .padding(16)
    .background(AppPalette.card, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
  }

  private func metricsCard(_ reveal: SessionResultRevealState) -> some View {
    let accuracyProgress = reveal.metricProgress(at: 0)
    let mistakeProgress = reveal.metricProgress(at: 1)
    let starProgress = reveal.metricProgress(at: 2)
    return HStack(spacing: 0) {
      SessionResultMetric(
        value: String(
          format: AppLocalization.string("practice.result.accuracy_value"),
          accuracyPercent * accuracyProgress
        ),
        label: "practice.result.accuracy",
        progress: accuracyProgress * min(max(accuracyPercent / 100, 0), 1),
        identifier: "practice.result.accuracy.value",
        tint: AppPalette.success
      )
      Divider().frame(height: 64)
      SessionResultMetric(
        value: String(Int(Double(mistakeCount) * mistakeProgress)),
        label: "practice.mistakes",
        progress: mistakeProgress * min(Double(mistakeCount) / 10, 1),
        identifier: "practice.result.mistakes.value",
        tint: mistakeCount == 0 ? AppPalette.success : AppPalette.error
      )
      Divider().frame(height: 64)
      SessionResultMetric(
        value: String(Int(Double(displayedStars) * starProgress)),
        label: "practice.result.stars",
        progress: starProgress * Double(displayedStars) / 3,
        identifier: "practice.result.stars.value",
        tint: .yellow
      )
    }
    .padding(.horizontal, 8)
    .padding(.vertical, 15)
    .background(AppPalette.card, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
  }

  private var nextActionsSection: some View {
    VStack(spacing: 14) {
      SessionShareButton(
        model: shareCardModel,
        accessibilityIdentifier: "practice.result.share"
      )

      if !reviewItems.isEmpty {
        NavigationLink {
          PracticeView(
            targets: reviewItems.map(\.item.ko),
            sessionTitle: AppLocalization.string("review.deck.title"),
            reviewSources: reviewItems.map {
              PracticeReviewSource(item: $0.item, sourceDeckId: $0.sourceDeckId)
            }
          )
        } label: {
          Label("result.review.start", systemImage: "arrow.triangle.2.circlepath")
            .font(.headline.weight(.bold))
            .foregroundStyle(AppPalette.accent)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 13)
            .background(AppPalette.accentSoft.opacity(0.55), in: RoundedRectangle(cornerRadius: 17))
        }
        .accessibilityIdentifier("practice.result.review")
      }

      recommendationSection
    }
  }

  @ViewBuilder
  private var recommendationSection: some View {
    if !recommendations.isEmpty {
      VStack(alignment: .leading, spacing: 11) {
        Label("recommendations.result.title", systemImage: "sparkles.rectangle.stack.fill")
          .font(.headline.weight(.bold))
          .foregroundStyle(AppPalette.ink)
          .accessibilityIdentifier("practice.result.recommendations")
        Text("recommendations.result.subtitle")
          .font(.caption)
          .foregroundStyle(AppPalette.mutedInk)

        ForEach(recommendations, id: \.deckId) { deck in
          NavigationLink {
            DeckDetailView(deck: deck, catalogDecks: catalogDecks)
          } label: {
            DeckCardView(
              deck: deck,
              isInstalled: deckLibrary.isInstalled(deck.deckId),
              updateAvailable: deckLibrary.needsUpdate(deck)
            )
          }
          .buttonStyle(.plain)
          .accessibilityIdentifier("practice.result.recommendation.\(deck.deckId)")
        }
      }
      .frame(maxWidth: .infinity, alignment: .leading)
    }
  }

  private var retryBar: some View {
    Button {
      onRetry()
      dismiss()
    } label: {
      Label(retryTitle, systemImage: retrySystemImage)
        .font(.headline.weight(.bold))
        .foregroundStyle(.white)
        .frame(maxWidth: .infinity)
        .padding(.vertical, 14)
        .background(AppPalette.accent, in: RoundedRectangle(cornerRadius: 17))
    }
    .accessibilityIdentifier("practice.result.retry")
  }

  private var displayedStars: Int {
    if let earnedStarsOverride { return earnedStarsOverride }
    if accuracyPercent >= 95 { return 3 }
    if accuracyPercent >= 80 { return 2 }
    return 1
  }

  private var resultMascotMood: MascotMood {
    if displayedStars == 0 { return .sulk }
    if mistakeCount == 0 { return .proud }
    return displayedStars >= 2 ? .satisfied : .happy
  }

  private var shareCardModel: SessionShareCardModel {
    SessionShareCardModel(
      sessionTitle: sessionTitle,
      achievement: AppLocalization.string("result.share.practice_badge"),
      scoreLabel: AppLocalization.string("result.share.practice_score"),
      scoreValue: String(
        format: AppLocalization.string("result.share.items_value"),
        completedItemCount
      ),
      metrics: [
        SessionShareCardMetric(
          id: "accuracy",
          label: AppLocalization.string("practice.result.accuracy"),
          value: String(
            format: AppLocalization.string("practice.result.accuracy_value"),
            accuracyPercent
          ),
          systemImage: "scope"
        ),
        SessionShareCardMetric(
          id: "stars",
          label: AppLocalization.string("practice.result.stars"),
          value: String(
            format: AppLocalization.string("result.share.stars_value"),
            displayedStars
          ),
          systemImage: "star.fill"
        ),
        SessionShareCardMetric(
          id: "streak",
          label: AppLocalization.string("result.share.streak"),
          value: String(
            format: AppLocalization.string("result.share.streak_value"),
            currentStreak
          ),
          systemImage: "seal.fill"
        ),
      ],
      caption: String(
        format: AppLocalization.string("result.share.practice_caption"),
        sessionTitle,
        completedItemCount
      ),
      mascotName: companion.displayName
    )
  }

  private var currentStreak: Int {
    retention.streak(asOf: JSTDay(date: RetentionClock.now())).current
  }
}
