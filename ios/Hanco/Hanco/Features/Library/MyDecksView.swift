import DeckKit
import SwiftUI

private enum MyDeckSortOrder: String, CaseIterable, Identifiable {
  case recent
  case name
  case installed

  var id: Self { self }

  var labelKey: LocalizedStringKey {
    switch self {
    case .recent: "my_decks.sort.recent"
    case .name: "my_decks.sort.name"
    case .installed: "my_decks.sort.installed"
    }
  }
}

private enum DeckMakerAction {
  case create
  case edit(Deck)
  case copyOfficial(Deck)

  var flow: DeckMakerDraftFlow {
    switch self {
    case .create: .new
    case .edit(let deck): .editing(deckID: deck.deckId)
    case .copyOfficial(let deck): .officialCopy(sourceDeckID: deck.deckId)
    }
  }
}

enum DeckMakerDraftFlow: Equatable {
  case new
  case editing(deckID: String)
  case officialCopy(sourceDeckID: String)

  init(draft: UserDeckDraft) {
    switch draft.origin {
    case .new:
      self = .new
    case .editing:
      self = .editing(deckID: draft.deckID)
    case .officialCopy(let sourceDeckID):
      self = .officialCopy(sourceDeckID: sourceDeckID)
    }
  }

  func matches(_ draft: UserDeckDraft) -> Bool {
    self == DeckMakerDraftFlow(draft: draft)
  }
}

enum UserDeckEditCommitError: Error, Equatable {
  case sourceChanged
}

func requireUnchangedUserDeckSource(
  for draft: UserDeckDraft,
  installedVersion: Int?
) throws {
  guard case .editing = draft.origin else { return }
  guard installedVersion == draft.baseVersion else {
    throw UserDeckEditCommitError.sourceChanged
  }
}

private struct DeckEditorPresentation: Identifiable {
  let id = UUID()
  let draftID: String
  let draft: UserDeckDraft
  let source: InstalledDeckSource
  let derivedFromDeckID: String?
  let deletesDeckID: String?
}

private struct DeckDeletionPresentation: Identifiable {
  let id = UUID()
  let deck: Deck
  let didOfferExport: Bool
}

private struct DeckMakerDraftConflict: Identifiable {
  let id = UUID()
  let requestedAction: DeckMakerAction
  let activeDraft: ActiveUserDeckDraft
}

private struct DeckDeletionFailure: Identifiable {
  let id = UUID()
  let deckName: String
}

struct MyPageView: View {
  @Environment(\.hancoAdaptiveMetrics) private var adaptiveMetrics
  @Environment(\.openRootSettings) private var openSettings
  @EnvironmentObject private var deckLibrary: DeckLibrary
  @EnvironmentObject private var gameProgress: GameProgressLibrary
  @EnvironmentObject private var reviewDeck: ReviewDeckLibrary
  @EnvironmentObject private var curriculumProgress: CurriculumProgressLibrary
  @EnvironmentObject private var retention: RetentionLibrary
  @EnvironmentObject private var companion: MascotCompanionLibrary
  @EnvironmentObject private var purchaseStore: DeckMakerPurchaseStore
  @EnvironmentObject private var documentCoordinator: PiyoDeckDocumentCoordinator
  @State private var sortOrder: MyDeckSortOrder = .recent
  @State private var insightPeriod: LearningInsightPeriod = .week
  @State private var showsMascotCloset = false
  @State private var showsDocumentImporter = false
  @State private var showsDeckMakerPaywall = false
  @State private var pendingDeckMakerAction: DeckMakerAction?
  @State private var paywallGrantedAccess = false
  @State private var editorPresentation: DeckEditorPresentation?
  @State private var draftConflict: DeckMakerDraftConflict?
  @State private var deletionPresentation: DeckDeletionPresentation?
  @State private var deckPendingDeletionAfterExport: Deck?
  @State private var deletionFailure: DeckDeletionFailure?
  @State private var deletingDeckID: String?
  @State private var draftRecoveryFailure = false
  @State private var isPageVisible = false
  let catalog: Catalog?
  let isActive: Bool
  let onFindDecks: () -> Void

  var body: some View {
    NavigationStack {
      ScrollView {
        pageContent
          .frame(
            maxWidth: adaptiveMetrics.usesTwoColumnDashboard
              ? adaptiveMetrics.hubContentMaxWidth
              : adaptiveMetrics.readableContentMaxWidth
          )
          .frame(maxWidth: .infinity)
          .padding(.horizontal, adaptiveMetrics.horizontalPadding)
          .padding(.vertical, 18)
      }
      .frame(maxWidth: .infinity, maxHeight: .infinity)
      .onAppear { isPageVisible = true }
      .onDisappear { isPageVisible = false }
      .background(
        LinearGradient(
          colors: [AppPalette.backgroundTop, AppPalette.backgroundBottom],
          startPoint: .topLeading,
          endPoint: .bottomTrailing
        )
        .ignoresSafeArea()
      )
      .accessibilityIdentifier("my_page.screen")
      .navigationTitle(Text("my_page.navigation_title"))
      .navigationBarTitleDisplayMode(.inline)
      .rootSettingsToolbar()
      .sheet(isPresented: $showsMascotCloset) {
        MascotClosetView()
      }
      .fileImporter(
        isPresented: $showsDocumentImporter,
        allowedContentTypes: [.piyoDeck],
        allowsMultipleSelection: false
      ) { result in
        switch result {
        case .success(let urls):
          guard let url = urls.first else { return }
          Task { await documentCoordinator.receive(url) }
        case .failure:
          documentCoordinator.reportReadFailure()
        }
      }
      .sheet(isPresented: importPreviewBinding) {
        if let candidate = documentCoordinator.candidate {
          PiyoDeckImportPreviewView(candidate: candidate)
            .environmentObject(deckLibrary)
            .environmentObject(reviewDeck)
            .environmentObject(purchaseStore)
            .environmentObject(documentCoordinator)
        }
      }
      .sheet(isPresented: $showsDeckMakerPaywall, onDismiss: finishPaywallPresentation) {
        DeckMakerPaywallView(purchaseStore: purchaseStore) {
          paywallGrantedAccess = true
        }
      }
      .sheet(item: $editorPresentation) { presentation in
        DeckEditorView(
          draft: presentation.draft,
          draftID: presentation.draftID,
          onDraftChange: { draft in
            try UserDeckDraftStore.live.save(draft)
          },
          onSave: { deck, latestDraftID in
            try await saveUserDeck(
              deck,
              presentation: presentation,
              draftID: latestDraftID ?? presentation.draftID
            )
          },
          onSaveAsCopy: { deck, latestDraftID in
            try await saveChangedSourceDraftAsCopy(
              deck,
              presentation: presentation,
              draftID: latestDraftID ?? presentation.draftID
            )
          },
          onDelete: presentation.deletesDeckID.map { deckID in
            { latestDraftID in
              try await deckLibrary.removeAndWait(deckID)
              _ = await reviewDeck.markSourceUnavailable(deckId: deckID)
              _ = try? UserDeckDraftStore.live.clear(
                draftID: latestDraftID ?? presentation.draftID
              )
              TelemetryService.shared.capture(
                .deckMakerAction,
                properties: [
                  .action: "deleted",
                  .itemCountBucket: TelemetryService.shared.itemCountBucket(
                    presentation.draft.items.count
                  ),
                  .deckSource: presentation.source == .imported ? "imported" : "created",
                ]
              )
            }
          }
        )
      }
      .sheet(isPresented: exportArtifactBinding) {
        if let artifact = documentCoordinator.exportArtifact {
          PiyoDeckActivityView(artifact: artifact) { completed in
            if completed {
              TelemetryService.shared.capture(
                .deckMakerAction,
                properties: [.action: "exported"]
              )
            }
            Task { @MainActor in finishExportPresentation() }
          }
        }
      }
      .alert(item: documentNoticeBinding) { notice in
        Alert(
          title: Text(AppLocalization.string(notice.titleKey)),
          message: Text(AppLocalization.string(notice.messageKey)),
          dismissButton: .default(Text("deck_maker.alert.ok")) {
            documentCoordinator.dismissNotice()
          }
        )
      }
      .sheet(item: $deletionPresentation) { presentation in
        DeckDeletionConfirmationView(
          presentation: presentation,
          onDelete: { delete(presentation.deck) },
          onExportThenDelete: presentation.deck.official || presentation.didOfferExport
            ? nil
            : { prepareExportBeforeDeletion(presentation.deck) },
          onCancel: { deletionPresentation = nil }
        )
      }
      .alert(item: $deletionFailure) { failure in
        Alert(
          title: Text("my_decks.delete.failure.title"),
          message: Text(
            AppLocalization.format("my_decks.delete.failure.message_format",
              failure.deckName
            )
          ),
          dismissButton: .default(Text("deck_maker.alert.ok"))
        )
      }
      .alert("deck_editor.draft.failure.title", isPresented: $draftRecoveryFailure) {
        Button("deck_maker.alert.ok", role: .cancel) {}
      } message: {
        Text("deck_editor.draft.failure.message")
      }
      .confirmationDialog(
        "deck_editor.draft.conflict.title",
        isPresented: draftConflictBinding,
        titleVisibility: .visible
      ) {
        Button("deck_editor.draft.conflict.resume") {
          resumeConflictingDraft()
        }
        Button("deck_editor.draft.conflict.discard", role: .destructive) {
          discardDraftAndStartRequestedFlow()
        }
        Button("common.cancel", role: .cancel) {
          draftConflict = nil
        }
      } message: {
        Text("deck_editor.draft.conflict.message")
      }
      .task {
        restoreDraftIfAvailable()
      }
      .onChange(of: isActive) { active in
        if active { restoreDraftIfAvailable() }
      }
      .onChange(of: purchaseStore.hasAccess) { hasAccess in
        if hasAccess { restoreDraftIfAvailable() }
      }
      .onChange(of: documentCoordinator.candidate?.id) { candidateID in
        if candidateID == nil { restoreDraftIfAvailable() }
      }
    }
  }

  @ViewBuilder
  private var pageContent: some View {
    if adaptiveMetrics.usesTwoColumnDashboard {
      LazyVStack(spacing: 16) {
        HStack(alignment: .top, spacing: 16) {
          VStack(spacing: 16) {
            profileCard
              .appTourTarget(.myPageProfile)
            growthRecordCard
          }
          .frame(maxWidth: .infinity, alignment: .top)

          VStack(spacing: 16) {
            settingsCard
            learningInsightsCard
          }
          .frame(maxWidth: .infinity, alignment: .top)
        }

        deckLibrarySection
      }
    } else {
      LazyVStack(spacing: 16) {
        profileCard
          .appTourTarget(.myPageProfile)
        growthRecordCard
        learningInsightsCard
        settingsCard
        deckLibrarySection
      }
    }
  }

  private var profileCard: some View {
    ZStack(alignment: .topTrailing) {
      HStack(spacing: 16) {
        GrowingMascotView(
          mood: .idle,
          showsNameTag: false,
          interactive: true,
          onOpenCloset: { showsMascotCloset = true },
          size: 72,
          calm: true
        )
        .frame(width: 112, height: 132)
        .clipped()

        VStack(alignment: .leading, spacing: 7) {
          Text("my_page.profile_eyebrow")
            .font(.caption.weight(.black))
            .foregroundStyle(AppPalette.accent)
          Text(verbatim: companion.displayName)
            .font(.system(.title2, design: .rounded, weight: .heavy))
            .foregroundStyle(AppPalette.ink)
            .lineLimit(1)
            .padding(.trailing, 48)
          MascotEncouragementBubble(localizationKey: dailyEncouragementKey)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
      }

      Button {
        showsMascotCloset = true
      } label: {
        Image(systemName: "hanger")
          .font(.subheadline.weight(.bold))
          .foregroundStyle(AppPalette.accent)
          .frame(width: 44, height: 44)
          .background(AppPalette.card, in: Circle())
          .overlay {
            Circle().stroke(AppPalette.accent.opacity(0.24), lineWidth: 1.5)
          }
          .shadow(color: AppPalette.keyShadow.opacity(0.7), radius: 5, y: 3)
      }
      .buttonStyle(.plain)
      .accessibilityLabel(Text("closet.title"))
      .accessibilityIdentifier("my_page.piyo_settings")
    }
    .padding(18)
    .background(AppPalette.card, in: RoundedRectangle(cornerRadius: 26, style: .continuous))
    .shadow(color: AppPalette.keyShadow, radius: 12, y: 7)
    .accessibilityIdentifier("my_page.profile")
  }

  private var growthRecordCard: some View {
    VStack(alignment: .leading, spacing: 14) {
      Label("my_page.growth.title", systemImage: "chart.line.uptrend.xyaxis")
        .font(.headline.weight(.heavy))
        .foregroundStyle(AppPalette.ink)

      LazyVGrid(
        columns: [GridItem(.flexible()), GridItem(.flexible())],
        spacing: 10
      ) {
        growthMetric(
          title: "my_page.growth.chapters",
          value: "\(curriculumProgress.completedChapterCount) / 6",
          systemImage: "map.fill",
          tint: AppPalette.accent
        )
        growthMetric(
          title: "my_page.growth.typed",
          value: companion.typedJamoCount.formatted(.number.locale(AppLocalization.locale)),
          systemImage: "keyboard.fill",
          tint: AppPalette.secondary
        )
        growthMetric(
          title: "my_page.growth.streak",
          value: currentStreak.current.formatted(.number.locale(AppLocalization.locale)),
          systemImage: "flame.fill",
          tint: .orange
        )
        growthMetric(
          title: "my_page.growth.activity",
          value: "\(recentActiveDays) / 7",
          systemImage: "calendar.badge.checkmark",
          tint: AppPalette.success
        )
      }
    }
    .padding(18)
    .background(AppPalette.card, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
    .shadow(color: AppPalette.keyShadow, radius: 9, y: 5)
    .accessibilityIdentifier("my_page.growth")
  }

  private var settingsCard: some View {
    VStack(alignment: .leading, spacing: 5) {
      Label("my_page.actions.title", systemImage: "slider.horizontal.3")
        .font(.headline.weight(.heavy))
        .foregroundStyle(AppPalette.ink)
        .padding(.bottom, 7)

      settingsRow(
        title: "my_page.app_settings",
        detail: "my_page.app_settings_detail",
        systemImage: "gearshape.fill",
        identifier: "my_page.settings",
        action: openSettings
      )

      Divider().opacity(0.5)

      settingsRow(
        title: "my_page.piyo_settings",
        detail: "my_page.piyo_settings_detail",
        systemImage: "bird.fill",
        identifier: "my_page.piyo_settings_row"
      ) {
        showsMascotCloset = true
      }
    }
    .padding(18)
    .background(AppPalette.card, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
    .shadow(color: AppPalette.keyShadow, radius: 9, y: 5)
  }

  private var learningInsightsCard: some View {
    let insights = learningInsights
    return VStack(alignment: .leading, spacing: 16) {
      Label("my_page.insights.title", systemImage: "waveform.path.ecg.rectangle.fill")
        .font(.headline.weight(.heavy))
        .foregroundStyle(AppPalette.ink)

      Picker("my_page.insights.period", selection: $insightPeriod) {
        Text("my_page.insights.week").tag(LearningInsightPeriod.week)
        Text("my_page.insights.month").tag(LearningInsightPeriod.month)
      }
      .pickerStyle(.segmented)
      .accessibilityIdentifier("my_page.insights.period")

      activityChart(insights)

      LazyVGrid(
        columns: [GridItem(.flexible()), GridItem(.flexible())],
        spacing: 10
      ) {
        insightMetric(
          title: "my_page.insights.active_days",
          value: AppLocalization.format("my_page.insights.days_format",
            insights.activeDays,
            insightPeriod.rawValue
          ),
          detail: nil,
          systemImage: "calendar.badge.checkmark",
          tint: AppPalette.success
        )
        insightMetric(
          title: "my_page.insights.sessions",
          value: insights.sessionCount.formatted(.number.locale(AppLocalization.locale)),
          detail: AppLocalization.format("my_page.insights.items_format",
            insights.completedItemCount
          ),
          systemImage: "checkmark.circle.fill",
          tint: AppPalette.secondary
        )
        insightMetric(
          title: "my_page.insights.accuracy",
          value: percentageText(insights.averageAccuracy),
          detail: changeText(insights.accuracyChange, fractionDigits: 1),
          systemImage: "scope",
          tint: AppPalette.accent
        )
        insightMetric(
          title: "my_page.insights.speed",
          value: speedText(insights.averageCharactersPerMinute),
          detail: changeText(insights.speedChange, fractionDigits: 0),
          systemImage: "gauge.with.dots.needle.67percent",
          tint: .orange
        )
      }

      if insights.sessionCount == 0 {
        Text("my_page.insights.empty")
          .font(.caption)
          .foregroundStyle(AppPalette.mutedInk)
          .frame(maxWidth: .infinity, alignment: .center)
          .padding(.vertical, 2)
      }

      Divider().opacity(0.55)
      weakJamoSection(insights)

      Divider().opacity(0.55)
      reviewProgressSection(insights)
    }
    .padding(18)
    .background(AppPalette.card, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
    .shadow(color: AppPalette.keyShadow, radius: 9, y: 5)
    .accessibilityElement(children: .contain)
    .accessibilityIdentifier("my_page.insights")
  }

  private func activityChart(_ insights: LearningInsights) -> some View {
    VStack(alignment: .leading, spacing: 9) {
      HStack {
        Text("my_page.insights.activity_chart")
          .font(.caption.weight(.bold))
          .foregroundStyle(AppPalette.mutedInk)
        Spacer()
        Text(verbatim: durationText(insights.totalActiveDuration))
          .font(.caption.monospacedDigit().weight(.bold))
          .foregroundStyle(AppPalette.ink)
      }

      HStack(alignment: .bottom, spacing: insightPeriod == .week ? 8 : 2) {
        ForEach(insights.dailyActivities) { activity in
          RoundedRectangle(cornerRadius: 3, style: .continuous)
            .fill(activity.isActive ? AppPalette.accent : AppPalette.accentSoft.opacity(0.65))
            .frame(height: activityBarHeight(activity, in: insights))
            .frame(maxWidth: .infinity, alignment: .bottom)
            .accessibilityLabel(Text(verbatim: activity.day.rawValue))
            .accessibilityValue(
              Text(
                verbatim: AppLocalization.format("my_page.insights.day_activity_format",
                  activity.sessionCount,
                  Int(activity.activeDuration.rounded())
                )
              )
            )
        }
      }
      .frame(height: 62, alignment: .bottom)

      if let first = insights.dailyActivities.first?.day,
        let last = insights.dailyActivities.last?.day
      {
        HStack {
          Text(verbatim: shortDay(first))
          Spacer()
          Text(verbatim: shortDay(last))
        }
        .font(.caption2.monospacedDigit())
        .foregroundStyle(AppPalette.mutedInk)
      }
    }
    .padding(12)
    .background(AppPalette.backgroundBottom.opacity(0.58), in: RoundedRectangle(cornerRadius: 16))
  }

  private func weakJamoSection(_ insights: LearningInsights) -> some View {
    VStack(alignment: .leading, spacing: 10) {
      Label("my_page.insights.weak_jamo", systemImage: "keyboard.badge.ellipsis")
        .font(.subheadline.weight(.heavy))
        .foregroundStyle(AppPalette.ink)

      if insights.weakJamo.isEmpty {
        Text("my_page.insights.no_weak_jamo")
          .font(.caption)
          .foregroundStyle(AppPalette.mutedInk)
      } else {
        let maximum = max(1, insights.weakJamo.map(\.mistakeCount).max() ?? 1)
        ForEach(insights.weakJamo) { item in
          HStack(spacing: 10) {
            Text(verbatim: item.jamo)
              .font(.system(.headline, design: .rounded, weight: .black))
              .foregroundStyle(AppPalette.ink)
              .frame(width: 36, height: 36)
              .background(AppPalette.error.opacity(0.11), in: RoundedRectangle(cornerRadius: 11))
            GeometryReader { proxy in
              Capsule()
                .fill(AppPalette.error.opacity(0.72))
                .frame(
                  width: max(
                    8,
                    proxy.size.width * CGFloat(item.mistakeCount) / CGFloat(maximum)
                  ),
                  height: 8
                )
                .frame(maxHeight: .infinity, alignment: .center)
            }
            .frame(height: 16)
            Text(
              AppLocalization.format("my_page.insights.mistake_format",
                item.mistakeCount
              )
            )
            .font(.caption.monospacedDigit().weight(.bold))
            .foregroundStyle(AppPalette.mutedInk)
          }
        }
      }
    }
  }

  private func reviewProgressSection(_ insights: LearningInsights) -> some View {
    VStack(alignment: .leading, spacing: 10) {
      Label("my_page.insights.review", systemImage: "arrow.triangle.2.circlepath")
        .font(.subheadline.weight(.heavy))
        .foregroundStyle(AppPalette.ink)

      HStack(spacing: 8) {
        reviewMetric(
          value: insights.activeReviewCount,
          title: "my_page.insights.review_active",
          tint: AppPalette.accent
        )
        reviewMetric(
          value: insights.addedReviewCount,
          title: "my_page.insights.review_added",
          tint: AppPalette.error
        )
        reviewMetric(
          value: insights.graduatedReviewCount,
          title: "my_page.insights.review_graduated",
          tint: AppPalette.success
        )
      }
    }
  }

  private func insightMetric(
    title: LocalizedStringKey,
    value: String,
    detail: String?,
    systemImage: String,
    tint: Color
  ) -> some View {
    VStack(alignment: .leading, spacing: 5) {
      Image(systemName: systemImage)
        .font(.subheadline.weight(.bold))
        .foregroundStyle(tint)
      Text(verbatim: value)
        .font(.headline.monospacedDigit().weight(.black))
        .foregroundStyle(AppPalette.ink)
        .lineLimit(1)
        .minimumScaleFactor(0.78)
      Text(title)
        .font(.caption2.weight(.semibold))
        .foregroundStyle(AppPalette.mutedInk)
      if let detail {
        Text(verbatim: detail)
          .font(.caption2.monospacedDigit().weight(.bold))
          .foregroundStyle(tint)
          .lineLimit(1)
      }
    }
    .padding(11)
    .frame(maxWidth: .infinity, minHeight: 96, alignment: .topLeading)
    .background(AppPalette.backgroundBottom.opacity(0.68), in: RoundedRectangle(cornerRadius: 16))
  }

  private func reviewMetric(
    value: Int,
    title: LocalizedStringKey,
    tint: Color
  ) -> some View {
    VStack(spacing: 4) {
      Text(verbatim: value.formatted(.number.locale(AppLocalization.locale)))
        .font(.headline.monospacedDigit().weight(.black))
        .foregroundStyle(tint)
      Text(title)
        .font(.caption2.weight(.semibold))
        .foregroundStyle(AppPalette.mutedInk)
        .multilineTextAlignment(.center)
        .lineLimit(2)
    }
    .padding(.vertical, 10)
    .frame(maxWidth: .infinity, minHeight: 62)
    .background(tint.opacity(0.09), in: RoundedRectangle(cornerRadius: 14))
  }

  private var learningInsights: LearningInsights {
    LearningInsights.make(
      period: insightPeriod,
      asOf: RetentionClock.now(),
      records: gameProgress.records,
      retentionRecords: retention.records,
      reviewItems: Array(reviewDeck.items.values),
      mistakeCounts: companion.mistakeCounts
    )
  }

  private func activityBarHeight(
    _ activity: DailyLearningActivity,
    in insights: LearningInsights
  ) -> CGFloat {
    let maximum = insights.dailyActivities.map(\.activeDuration).max() ?? 0
    guard maximum > 0 else { return activity.isActive ? 15 : 5 }
    guard activity.activeDuration > 0 else { return activity.isActive ? 12 : 5 }
    return max(8, 58 * CGFloat(activity.activeDuration / maximum))
  }

  private func percentageText(_ value: Double?) -> String {
    guard let value else { return "—" }
    return AppLocalization.format("my_page.insights.percent_format", value)
  }

  private func speedText(_ value: Double?) -> String {
    guard let value else { return "—" }
    return AppLocalization.format("my_page.insights.speed_format",
      Int(value.rounded())
    )
  }

  private func durationText(_ duration: TimeInterval) -> String {
    guard duration >= 60 else { return AppLocalization.string("my_page.insights.less_than_minute") }
    return AppLocalization.format("my_page.insights.minutes_format",
      Int((duration / 60).rounded())
    )
  }

  private func changeText(_ change: Double?, fractionDigits: Int) -> String? {
    guard let change else { return nil }
    let number = String(format: "%+.*f", locale: AppLocalization.locale, fractionDigits, change)
    return AppLocalization.format("my_page.insights.change_format",
      number
    )
  }

  private func shortDay(_ day: JSTDay) -> String {
    let components = day.rawValue.split(separator: "-")
    guard components.count == 3,
      let month = Int(components[1]),
      let date = Int(components[2])
    else { return day.rawValue }
    return "\(month)/\(date)"
  }

  private var deckLibrarySection: some View {
    VStack(alignment: .leading, spacing: 12) {
      HStack {
        Label("my_page.decks.title", systemImage: "square.stack.fill")
          .font(.headline.weight(.heavy))
          .foregroundStyle(AppPalette.ink)
        Spacer()
        Text(
          AppLocalization.format("my_page.decks.count_format",
            deckLibrary.installed.count
          )
        )
        .font(.caption.monospacedDigit().weight(.bold))
        .foregroundStyle(AppPalette.mutedInk)
      }

      deckDocumentActions

      if deckLibrary.installed.isEmpty, reviewDeck.activeItems.isEmpty {
        emptyState
      } else {
        deckList
      }
    }
    .accessibilityElement(children: .contain)
    .accessibilityIdentifier("my_page.decks")
  }

  private var deckDocumentActions: some View {
    ViewThatFits(in: .horizontal) {
      HStack(spacing: 10) {
        importDeckButton
        createDeckButton
      }
      VStack(spacing: 10) {
        importDeckButton
        createDeckButton
      }
    }
  }

  private var importDeckButton: some View {
    Button {
      showsDocumentImporter = true
    } label: {
      HStack(spacing: 7) {
        if documentCoordinator.isReading {
          ProgressView()
        } else {
          Image(systemName: "square.and.arrow.down")
        }
        Text("piyodeck.import.action.open")
      }
      .font(.subheadline.weight(.bold))
      .foregroundStyle(AppPalette.ink)
      .frame(maxWidth: .infinity, minHeight: 48)
      .background(AppPalette.card, in: RoundedRectangle(cornerRadius: 16))
      .overlay {
        RoundedRectangle(cornerRadius: 16)
          .stroke(AppPalette.accent.opacity(0.45), lineWidth: 1.5)
      }
    }
    .buttonStyle(.plain)
    .disabled(documentCoordinator.isReading)
    .accessibilityIdentifier("my_decks.import")
  }

  private var createDeckButton: some View {
    Button {
      requestDeckMaker(.create)
    } label: {
      Label(
        "piyodeck.create.action",
        systemImage: purchaseStore.hasAccess ? "plus.circle.fill" : "lock.fill"
      )
      .font(.subheadline.weight(.bold))
      .foregroundStyle(AppPalette.onAccent)
      .frame(maxWidth: .infinity, minHeight: 48)
      .background(AppPalette.accent, in: RoundedRectangle(cornerRadius: 16))
    }
    .buttonStyle(.plain)
    .accessibilityValue(Text(deckMakerAccessibilityValueKey))
    .accessibilityHint(Text(deckMakerAccessibilityHintKey))
    .accessibilityIdentifier("my_decks.create")
  }

  private var emptyState: some View {
    VStack(spacing: 16) {
      Image(systemName: "square.stack.3d.up.slash")
        .font(.system(size: 34, weight: .bold))
        .foregroundStyle(AppPalette.accent)
        .frame(width: 72, height: 72)
        .background(AppPalette.accentSoft.opacity(0.5), in: Circle())
      Text("my_decks.empty.title")
        .font(.system(.title2, design: .rounded, weight: .bold))
        .foregroundStyle(AppPalette.ink)
      Text("my_decks.empty.message")
        .font(.subheadline)
        .foregroundStyle(AppPalette.mutedInk)
        .multilineTextAlignment(.center)
      Button(action: onFindDecks) {
        Label("my_decks.find_decks", systemImage: "sparkle.magnifyingglass")
          .font(.headline.weight(.bold))
          .foregroundStyle(.white)
          .padding(.horizontal, 22)
          .padding(.vertical, 13)
          .background(AppPalette.accent, in: Capsule())
      }
      .accessibilityIdentifier("my_decks.find_decks")
    }
    .frame(maxWidth: .infinity)
    .padding(24)
    .background(AppPalette.card, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
    .shadow(color: AppPalette.keyShadow, radius: 9, y: 5)
    .accessibilityElement(children: .contain)
    .accessibilityIdentifier("my_decks.empty")
  }

  private var deckList: some View {
    LazyVStack(spacing: 12) {
      HStack {
        Label("my_decks.sort.title", systemImage: "arrow.up.arrow.down")
          .font(.caption.weight(.bold))
          .foregroundStyle(AppPalette.mutedInk)
        Spacer()
        Picker("my_decks.sort.title", selection: $sortOrder) {
          ForEach(MyDeckSortOrder.allCases) { order in
            Text(order.labelKey).tag(order)
          }
        }
        .pickerStyle(.menu)
        .accessibilityIdentifier("my_decks.sort")
      }

      if !reviewDeck.activeItems.isEmpty {
        NavigationLink {
          ReviewDeckView()
        } label: {
          reviewDeckRow
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("my_decks.review_deck")
      }

      ForEach(sortedDecks, id: \.deckId) { deck in
        ZStack(alignment: .topTrailing) {
          NavigationLink {
            DeckPracticeDestination(deck: deck)
          } label: {
            installedDeckRow(deck)
          }
          .buttonStyle(.plain)
          .accessibilityIdentifier("my_decks.deck.\(deck.deckId)")

          Menu {
            NavigationLink {
              DeckPracticeDestination(deck: deck)
            } label: {
              Label("my_decks.play_hint", systemImage: "keyboard.fill")
            }
            if let update = updateEntry(for: deck) {
              Button {
                Task { await deckLibrary.install(update) }
              } label: {
                Label("deck.detail.update", systemImage: "arrow.triangle.2.circlepath")
              }
            }
            if deck.official {
              Button {
                requestDeckMaker(.copyOfficial(deck))
              } label: {
                Label(
                  "piyodeck.action.edit_copy",
                  systemImage: purchaseStore.hasAccess ? "doc.on.doc.fill" : "lock.fill"
                )
              }
              .accessibilityValue(Text(deckMakerAccessibilityValueKey))
              .accessibilityHint(Text(deckMakerAccessibilityHintKey))
            } else {
              Button {
                requestDeckMaker(.edit(deck))
              } label: {
                Label(
                  "piyodeck.action.edit",
                  systemImage: purchaseStore.hasAccess ? "pencil" : "lock.fill"
                )
              }
              .accessibilityValue(Text(deckMakerAccessibilityValueKey))
              .accessibilityHint(Text(deckMakerAccessibilityHintKey))
              Button {
                Task { await documentCoordinator.prepareExport(for: deck) }
              } label: {
                Label("piyodeck.action.export", systemImage: "square.and.arrow.up")
              }
            }
            Button(role: .destructive) {
              deletionPresentation = DeckDeletionPresentation(
                deck: deck,
                didOfferExport: false
              )
            } label: {
              Label("my_decks.delete", systemImage: "trash")
            }
            .disabled(deletingDeckID == deck.deckId)
          } label: {
            Group {
              if deletingDeckID == deck.deckId {
                ProgressView()
              } else {
                Image(systemName: "ellipsis.circle.fill")
              }
            }
            .font(.title3)
            .foregroundStyle(AppPalette.mutedInk)
            .padding(14)
          }
          .disabled(deletingDeckID == deck.deckId)
          .accessibilityLabel(Text("my_decks.actions"))
          .accessibilityIdentifier("my_decks.actions.\(deck.deckId)")
        }
      }
    }
  }

  private var currentStreak: RetentionStreak {
    retention.streak(asOf: currentDay)
  }

  private var recentActiveDays: Int {
    RetentionCalendar.days(endingAt: currentDay, count: 7)
      .filter { retention.isStamped($0) }
      .count
  }

  private var currentDay: JSTDay {
    JSTDay(date: RetentionClock.now())
  }

  private var dailyEncouragementKey: String {
    MascotDailyEncouragement.localizationKey(
      for: currentDay,
      completedDays: retention.completedDays
    )
  }

  private func growthMetric(
    title: LocalizedStringKey,
    value: String,
    systemImage: String,
    tint: Color
  ) -> some View {
    HStack(spacing: 10) {
      Image(systemName: systemImage)
        .font(.headline.weight(.bold))
        .foregroundStyle(tint)
        .frame(width: 34, height: 34)
        .background(tint.opacity(0.13), in: RoundedRectangle(cornerRadius: 11))
      VStack(alignment: .leading, spacing: 2) {
        Text(verbatim: value)
          .font(.headline.monospacedDigit().weight(.black))
          .foregroundStyle(AppPalette.ink)
        Text(title)
          .font(.caption2.weight(.semibold))
          .foregroundStyle(AppPalette.mutedInk)
          .lineLimit(2)
      }
      Spacer(minLength: 0)
    }
    .padding(11)
    .frame(maxWidth: .infinity, minHeight: 68, alignment: .leading)
    .background(AppPalette.backgroundBottom.opacity(0.68), in: RoundedRectangle(cornerRadius: 16))
  }

  private func settingsRow(
    title: LocalizedStringKey,
    detail: LocalizedStringKey,
    systemImage: String,
    identifier: String,
    action: @escaping () -> Void
  ) -> some View {
    Button(action: action) {
      HStack(spacing: 12) {
        Image(systemName: systemImage)
          .font(.system(size: 17, weight: .semibold))
          .foregroundStyle(AppPalette.accent)
          .frame(width: 34, height: 34)
          .background(AppPalette.accentSoft.opacity(0.5), in: RoundedRectangle(cornerRadius: 11))
        VStack(alignment: .leading, spacing: 2) {
          Text(title)
            .font(.subheadline.weight(.semibold))
            .foregroundStyle(AppPalette.ink)
          Text(detail)
            .font(.caption)
            .foregroundStyle(AppPalette.mutedInk)
        }
        Spacer()
        Image(systemName: "chevron.right")
          .font(.caption.weight(.bold))
          .foregroundStyle(AppPalette.mutedInk)
      }
      .padding(.vertical, 8)
      .contentShape(Rectangle())
    }
    .buttonStyle(.plain)
    .accessibilityIdentifier(identifier)
  }

  private var sortedDecks: [Deck] {
    deckLibrary.installed.sorted { lhs, rhs in
      switch sortOrder {
      case .recent:
        let lhsDate =
          deckLibrary.records[lhs.deckId]?.lastPlayedAt
          ?? deckLibrary.records[lhs.deckId]?.installedAt ?? .distantPast
        let rhsDate =
          deckLibrary.records[rhs.deckId]?.lastPlayedAt
          ?? deckLibrary.records[rhs.deckId]?.installedAt ?? .distantPast
        if lhsDate == rhsDate { return lhs.appName < rhs.appName }
        return lhsDate > rhsDate
      case .name:
        return lhs.appName.localizedStandardCompare(rhs.appName) == .orderedAscending
      case .installed:
        let lhsDate = deckLibrary.records[lhs.deckId]?.installedAt ?? .distantPast
        let rhsDate = deckLibrary.records[rhs.deckId]?.installedAt ?? .distantPast
        if lhsDate == rhsDate { return lhs.appName < rhs.appName }
        return lhsDate > rhsDate
      }
    }
  }

  private var reviewDeckRow: some View {
    HStack(spacing: 14) {
      Image(systemName: "bookmark.fill")
        .font(.system(size: 25, weight: .bold))
        .foregroundStyle(.white)
        .frame(width: 74, height: 94)
        .background(AppPalette.accent, in: RoundedRectangle(cornerRadius: 20))

      VStack(alignment: .leading, spacing: 7) {
        Label("review.deck.badge", systemImage: "arrow.triangle.2.circlepath")
          .font(.caption2.weight(.bold))
          .foregroundStyle(AppPalette.secondary)
        Text("review.deck.title")
          .font(.system(.headline, design: .rounded, weight: .bold))
          .foregroundStyle(AppPalette.ink)
        Text(verbatim: reviewPreview)
          .font(.caption)
          .foregroundStyle(AppPalette.mutedInk)
          .lineLimit(1)
        HStack {
          Text(
            AppLocalization.format("review.deck.count_format",
              reviewDeck.activeItems.count
            )
          )
          Text("review.deck.play_hint")
          Image(systemName: "chevron.right")
        }
        .font(.caption.weight(.semibold))
        .foregroundStyle(AppPalette.accent)
      }
      Spacer()
    }
    .padding(14)
    .background(AppPalette.card, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
    .shadow(color: AppPalette.keyShadow, radius: 9, y: 5)
  }

  private var reviewPreview: String {
    reviewDeck.activeItems.prefix(3).map(\.ko).joined(separator: "・")
  }

  private func installedDeckRow(_ deck: Deck) -> some View {
    let updateAvailable = updateEntry(for: deck) != nil
    return HStack(spacing: 14) {
      DeckCoverView(deck: deck, width: 74, height: 94)

      VStack(alignment: .leading, spacing: 7) {
        HStack(spacing: 6) {
          if deck.official {
            Label("deck.badge.official", systemImage: "checkmark.seal.fill")
              .font(.caption2.weight(.bold))
              .foregroundStyle(AppPalette.secondary)
          } else {
            Label("piyodeck.import.user_badge", systemImage: "person.crop.square.fill")
              .font(.caption2.weight(.bold))
              .foregroundStyle(AppPalette.ink)
          }
          Label("deck.badge.offline", systemImage: "arrow.down.circle.fill")
            .font(.caption2.weight(.bold))
            .foregroundStyle(AppPalette.successText)
          if updateAvailable {
            Label("deck.badge.update", systemImage: "arrow.triangle.2.circlepath")
              .font(.caption2.weight(.bold))
              .foregroundStyle(.orange)
          }
        }

        Text(verbatim: deck.appName)
          .font(.system(.headline, design: .rounded, weight: .bold))
          .foregroundStyle(AppPalette.ink)
          .lineLimit(2)

        Text(verbatim: deck.appAuthorNickname)
          .font(.caption)
          .foregroundStyle(AppPalette.mutedInk)

        HStack {
          Text(AppLocalization.format("deck.items.format", deck.items.count))
          Text("my_decks.play_hint")
          Image(systemName: "chevron.right")
        }
        .font(.caption.weight(.semibold))
        .foregroundStyle(AppPalette.ink)
      }
      Spacer()
    }
    .padding(14)
    .background(AppPalette.card, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
    .shadow(color: AppPalette.keyShadow, radius: 9, y: 5)
  }

  private var importPreviewBinding: Binding<Bool> {
    Binding(
      get: {
        canPresentDocumentUI
          && documentCoordinator.exportArtifact == nil
          && documentCoordinator.candidate != nil
      },
      set: { isPresented in
        if !isPresented, canPresentDocumentUI {
          documentCoordinator.dismissCandidate()
        }
      }
    )
  }

  private var exportArtifactBinding: Binding<Bool> {
    Binding(
      get: {
        canPresentDocumentUI && documentCoordinator.exportArtifact != nil
      },
      set: { isPresented in
        if !isPresented, canPresentDocumentUI {
          finishExportPresentation()
        }
      }
    )
  }

  private var documentNoticeBinding: Binding<PiyoDeckDocumentNotice?> {
    Binding(
      get: {
        guard canPresentDocumentUI,
          documentCoordinator.candidate == nil,
          documentCoordinator.exportArtifact == nil
        else { return nil }
        return documentCoordinator.notice
      },
      set: { notice in
        if notice == nil, canPresentDocumentUI {
          documentCoordinator.dismissNotice()
        }
      }
    )
  }

  private var draftConflictBinding: Binding<Bool> {
    Binding(
      get: { draftConflict != nil },
      set: { isPresented in
        if !isPresented { draftConflict = nil }
      }
    )
  }

  private var canPresentDocumentUI: Bool {
    isActive
      && isPageVisible
      && !showsMascotCloset
      && !showsDocumentImporter
      && !showsDeckMakerPaywall
      && editorPresentation == nil
      && draftConflict == nil
      && deletionPresentation == nil
      && deletionFailure == nil
  }

  private var canPresentDraftUI: Bool {
    canPresentDocumentUI
      && documentCoordinator.candidate == nil
      && documentCoordinator.exportArtifact == nil
      && documentCoordinator.notice == nil
      && deckPendingDeletionAfterExport == nil
  }

  private var deckMakerAccessibilityValueKey: LocalizedStringKey {
    purchaseStore.hasAccess
      ? "deck_maker.accessibility.status.unlocked"
      : "deck_maker.accessibility.status.locked"
  }

  private var deckMakerAccessibilityHintKey: LocalizedStringKey {
    purchaseStore.hasAccess
      ? "deck_maker.accessibility.hint.available"
      : "deck_maker.accessibility.hint.locked"
  }

  private func prepareExportBeforeDeletion(_ deck: Deck) {
    deletionPresentation = nil
    deckPendingDeletionAfterExport = deck
    Task { @MainActor in
      await documentCoordinator.prepareExport(for: deck)
      if documentCoordinator.exportArtifact == nil {
        deckPendingDeletionAfterExport = nil
      }
    }
  }

  private func finishExportPresentation() {
    documentCoordinator.finishExport()
    guard let deck = deckPendingDeletionAfterExport else { return }
    deckPendingDeletionAfterExport = nil
    guard deckLibrary.isInstalled(deck.deckId) else { return }
    deletionPresentation = DeckDeletionPresentation(deck: deck, didOfferExport: true)
  }

  private func delete(_ deck: Deck) {
    deletionPresentation = nil
    guard deletingDeckID == nil else { return }
    deletingDeckID = deck.deckId
    let source = deckLibrary.records[deck.deckId]?.source
    Task { @MainActor in
      defer { deletingDeckID = nil }
      do {
        try await deckLibrary.removeAndWait(deck.deckId)
        _ = await reviewDeck.markSourceUnavailable(deckId: deck.deckId)
        if source == .created || source == .imported {
          TelemetryService.shared.capture(
            .deckMakerAction,
            properties: [
              .action: "deleted",
              .itemCountBucket: TelemetryService.shared.itemCountBucket(deck.items.count),
              .deckSource: source == .imported ? "imported" : "created",
            ]
          )
        }
      } catch {
        deletionFailure = DeckDeletionFailure(deckName: deck.appName)
      }
    }
  }

  private func requestDeckMaker(_ action: DeckMakerAction) {
    TelemetryService.shared.capture(.featureViewed, properties: [.feature: "deck_maker"])
    TelemetryService.shared.setCrashContext(feature: "deck_maker")
    if purchaseStore.hasAccess {
      routeDeckMakerAction(action)
    } else {
      pendingDeckMakerAction = action
      paywallGrantedAccess = false
      showsDeckMakerPaywall = true
    }
  }

  private func finishPaywallPresentation() {
    defer {
      pendingDeckMakerAction = nil
      paywallGrantedAccess = false
    }
    guard paywallGrantedAccess, purchaseStore.hasAccess,
      let action = pendingDeckMakerAction
    else { return }
    routeDeckMakerAction(action)
  }

  private func routeDeckMakerAction(_ action: DeckMakerAction) {
    guard canPresentDraftUI else { return }
    do {
      guard let active = try UserDeckDraftStore.live.load() else {
        presentNewDeckEditor(for: action)
        return
      }
      if action.flow.matches(active.draft) {
        presentDeckEditor(active)
      } else {
        draftConflict = DeckMakerDraftConflict(
          requestedAction: action,
          activeDraft: active
        )
      }
    } catch {
      draftRecoveryFailure = true
    }
  }

  private func presentNewDeckEditor(for action: DeckMakerAction) {
    let draft: UserDeckDraft
    let source: InstalledDeckSource
    let derivedFromDeckID: String?
    let deletesDeckID: String?
    switch action {
    case .create:
      draft = UserDeckDraft()
      source = .created
      derivedFromDeckID = nil
      deletesDeckID = nil
    case .edit(let deck):
      let existingSource = deckLibrary.records[deck.deckId]?.source
      draft = UserDeckDraft(editing: deck)
      source = existingSource == .imported ? .imported : .created
      derivedFromDeckID = deckLibrary.records[deck.deckId]?.derivedFromDeckId
      deletesDeckID = deck.deckId
    case .copyOfficial(let deck):
      draft = UserDeckDraft(copyingOfficial: deck)
      source = .created
      derivedFromDeckID = deck.deckId
      deletesDeckID = nil
    }
    do {
      let active = try UserDeckDraftStore.live.save(draft)
      presentDeckEditor(
        active,
        source: source,
        derivedFromDeckID: derivedFromDeckID,
        deletesDeckID: deletesDeckID
      )
    } catch {
      draftRecoveryFailure = true
    }
  }

  private func resumeConflictingDraft() {
    guard let conflict = draftConflict else { return }
    draftConflict = nil
    presentDeckEditor(conflict.activeDraft)
  }

  private func discardDraftAndStartRequestedFlow() {
    guard let conflict = draftConflict else { return }
    draftConflict = nil
    do {
      guard try UserDeckDraftStore.live.clear(draftID: conflict.activeDraft.draftID) else {
        throw UserDeckDraftStoreError.invalidEnvelope
      }
      presentNewDeckEditor(for: conflict.requestedAction)
    } catch {
      draftRecoveryFailure = true
    }
  }

  private func presentDeckEditor(_ active: ActiveUserDeckDraft) {
    let draft = active.draft
    let source: InstalledDeckSource
    let derivedFromDeckID: String?
    let deletesDeckID: String?
    switch draft.origin {
    case .new:
      source = .created
      derivedFromDeckID = nil
      deletesDeckID = nil
    case .editing:
      guard deckLibrary.isInstalled(draft.deckID) else { return }
      source = deckLibrary.records[draft.deckID]?.source == .imported ? .imported : .created
      derivedFromDeckID = deckLibrary.records[draft.deckID]?.derivedFromDeckId
      deletesDeckID = draft.deckID
    case .officialCopy(let sourceDeckID):
      source = .created
      derivedFromDeckID = sourceDeckID
      deletesDeckID = nil
    }
    presentDeckEditor(
      active,
      source: source,
      derivedFromDeckID: derivedFromDeckID,
      deletesDeckID: deletesDeckID
    )
  }

  private func presentDeckEditor(
    _ active: ActiveUserDeckDraft,
    source: InstalledDeckSource,
    derivedFromDeckID: String?,
    deletesDeckID: String?
  ) {
    editorPresentation = DeckEditorPresentation(
      draftID: active.draftID,
      draft: active.draft,
      source: source,
      derivedFromDeckID: derivedFromDeckID,
      deletesDeckID: deletesDeckID
    )
  }

  private func saveUserDeck(
    _ deck: Deck,
    presentation: DeckEditorPresentation,
    draftID: String
  ) async throws {
    try purchaseStore.requireAccess()
    try requireUnchangedUserDeckSource(
      for: presentation.draft,
      installedVersion: deckLibrary.installedDeck(presentation.draft.deckID)?.version
    )
    let schemaData = try PiyoDeckDocumentService.deckSchemaData()
    let package = try await Task.detached {
      try PiyoDeckDocumentService.canonicalPackage(for: deck, schemaData: schemaData)
    }.value
    try purchaseStore.requireAccess()
    try requireUnchangedUserDeckSource(
      for: presentation.draft,
      installedVersion: deckLibrary.installedDeck(presentation.draft.deckID)?.version
    )
    let expectedCurrentVersion: Int?
    if case .editing = presentation.draft.origin {
      expectedCurrentVersion = presentation.draft.baseVersion
    } else {
      expectedCurrentVersion = nil
    }
    let installed: Deck
    do {
      installed = try await deckLibrary.installUserDeck(
        data: package.deckData,
        source: presentation.source,
        contentSHA256: package.contentSHA256,
        packageFormatVersion: package.manifest.formatVersion,
        isLocallyModified: true,
        hasPiyokeyProAccess: purchaseStore.hasAccess,
        derivedFromDeckId: presentation.derivedFromDeckID,
        expectedCurrentVersion: expectedCurrentVersion
      )
    } catch let storeError as DeckInstallationStoreError {
      guard case .sourceChanged = storeError else { throw storeError }
      throw UserDeckEditCommitError.sourceChanged
    }
    _ = reviewDeck.reconcile(with: installed)
    _ = try? UserDeckDraftStore.live.clear(draftID: draftID)
    let action: String
    switch presentation.draft.origin {
    case .new: action = "created"
    case .editing: action = "edited"
    case .officialCopy: action = "copied"
    }
    TelemetryService.shared.capture(
      .deckMakerAction,
      properties: [
        .action: action,
        .itemCountBucket: TelemetryService.shared.itemCountBucket(installed.items.count),
        .deckSource: presentation.source == .imported ? "imported" : "created",
      ]
    )
  }

  private func saveChangedSourceDraftAsCopy(
    _ deck: Deck,
    presentation: DeckEditorPresentation,
    draftID: String
  ) async throws {
    try purchaseStore.requireAccess()
    let copy = PiyoDeckDocumentService.makeUserCopy(of: deck)
    let schemaData = try PiyoDeckDocumentService.deckSchemaData()
    let package = try await Task.detached {
      try PiyoDeckDocumentService.canonicalPackage(for: copy, schemaData: schemaData)
    }.value
    try purchaseStore.requireAccess()
    let installed = try await deckLibrary.installUserDeck(
      data: package.deckData,
      source: .created,
      contentSHA256: package.contentSHA256,
      packageFormatVersion: package.manifest.formatVersion,
      isLocallyModified: true,
      hasPiyokeyProAccess: purchaseStore.hasAccess,
      derivedFromDeckId: presentation.draft.deckID
    )
    _ = reviewDeck.reconcile(with: installed)
    _ = try? UserDeckDraftStore.live.clear(draftID: draftID)
    TelemetryService.shared.capture(
      .deckMakerAction,
      properties: [
        .action: "copied",
        .itemCountBucket: TelemetryService.shared.itemCountBucket(installed.items.count),
        .deckSource: "created",
      ]
    )
  }

  private func restoreDraftIfAvailable() {
    guard purchaseStore.hasAccess,
      canPresentDraftUI,
      pendingDeckMakerAction == nil
    else { return }
    do {
      guard let active = try UserDeckDraftStore.live.load() else { return }
      let draft = active.draft
      let source: InstalledDeckSource
      let derivedFromDeckID: String?
      let deletesDeckID: String?
      switch draft.origin {
      case .new:
        if deckLibrary.isInstalled(draft.deckID) {
          _ = try? UserDeckDraftStore.live.clear(draftID: active.draftID)
          return
        }
        source = .created
        derivedFromDeckID = nil
        deletesDeckID = nil
      case .editing:
        guard deckLibrary.isInstalled(draft.deckID) else {
          // Deletion and entitlement changes never silently discard authored
          // content. The user can explicitly discard this stale draft when
          // starting another flow.
          return
        }
        source = deckLibrary.records[draft.deckID]?.source == .imported ? .imported : .created
        derivedFromDeckID = deckLibrary.records[draft.deckID]?.derivedFromDeckId
        deletesDeckID = draft.deckID
      case .officialCopy(let sourceDeckID):
        if deckLibrary.isInstalled(draft.deckID) {
          _ = try? UserDeckDraftStore.live.clear(draftID: active.draftID)
          return
        }
        source = .created
        derivedFromDeckID = sourceDeckID
        deletesDeckID = nil
      }
      presentDeckEditor(
        active,
        source: source,
        derivedFromDeckID: derivedFromDeckID,
        deletesDeckID: deletesDeckID
      )
    } catch {
      draftRecoveryFailure = true
    }
  }

  private func updateEntry(for deck: Deck) -> CatalogDeck? {
    guard let entry = catalog?.decks.first(where: { $0.deckId == deck.deckId }),
      deckLibrary.needsUpdate(entry)
    else {
      return nil
    }
    return entry
  }
}

private struct DeckDeletionConfirmationView: View {
  let presentation: DeckDeletionPresentation
  let onDelete: () -> Void
  let onExportThenDelete: (() -> Void)?
  let onCancel: () -> Void

  var body: some View {
    NavigationStack {
      ScrollView {
        VStack(spacing: 22) {
          Image(systemName: "trash.circle.fill")
            .font(.system(size: 54, weight: .bold))
            .foregroundStyle(AppPalette.errorText)
            .accessibilityHidden(true)

          VStack(spacing: 10) {
            Text(
              AppLocalization.format("my_decks.delete.confirm.title_format",
                presentation.deck.appName
              )
            )
            .font(.system(.title2, design: .rounded, weight: .heavy))
            .foregroundStyle(AppPalette.ink)
            .multilineTextAlignment(.center)
            .fixedSize(horizontal: false, vertical: true)

            Text("my_decks.delete.confirm.message")
              .font(.subheadline)
              .foregroundStyle(AppPalette.mutedInk)
              .multilineTextAlignment(.center)
              .fixedSize(horizontal: false, vertical: true)
          }

          VStack(spacing: 12) {
            if let onExportThenDelete {
              Button(action: onExportThenDelete) {
                Label("my_decks.delete.export_then_delete", systemImage: "square.and.arrow.up")
                  .font(.headline.weight(.bold))
                  .foregroundStyle(AppPalette.onAccent)
                  .frame(maxWidth: .infinity, minHeight: 52)
                  .background(AppPalette.accent, in: RoundedRectangle(cornerRadius: 17))
              }
              .buttonStyle(.plain)
              .accessibilityIdentifier("my_decks.delete.export_then_delete")
            }

            Button(role: .destructive, action: onDelete) {
              Label("my_decks.delete", systemImage: "trash")
                .font(.headline.weight(.bold))
                .foregroundStyle(AppPalette.errorText)
                .frame(maxWidth: .infinity, minHeight: 52)
                .background(
                  AppPalette.error.opacity(0.1),
                  in: RoundedRectangle(cornerRadius: 17)
                )
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("my_decks.delete.confirm")

            Button("common.cancel", action: onCancel)
              .font(.subheadline.weight(.bold))
              .foregroundStyle(AppPalette.mutedInk)
              .frame(minHeight: 44)
              .accessibilityIdentifier("my_decks.delete.cancel")
          }
        }
        .padding(24)
        .frame(maxWidth: 560)
      }
      .background(AppPalette.backgroundTop.ignoresSafeArea())
      .navigationTitle(Text("my_decks.delete"))
      .navigationBarTitleDisplayMode(.inline)
      .toolbar {
        ToolbarItem(placement: .cancellationAction) {
          Button("common.cancel", action: onCancel)
        }
      }
    }
    .presentationDetents([.medium, .large])
    .interactiveDismissDisabled()
    .accessibilityIdentifier("my_decks.delete.confirmation")
  }
}
