import Foundation
import HangulEngine
import SwiftUI

struct SessionResultRevealTiming: Equatable {
  let scale: Double

  init(scale: Double = 1) {
    self.scale = max(scale, 0.01)
  }

  static var live: SessionResultRevealTiming {
    #if DEBUG
      if let rawValue = ProcessInfo.processInfo.environment["UITEST_RESULT_ANIMATION_SCALE"],
        let scale = Double(rawValue), scale > 0
      {
        return SessionResultRevealTiming(scale: scale)
      }
    #endif
    return SessionResultRevealTiming()
  }

  var totalDuration: TimeInterval { 2.5 * scale }

  func state(elapsed: TimeInterval, skipped: Bool = false) -> SessionResultRevealState {
    SessionResultRevealState(
      canonicalElapsed: skipped ? 2.5 : max(0, elapsed / scale)
    )
  }
}

struct SessionResultRevealState: Equatable {
  let canonicalElapsed: TimeInterval

  var headerProgress: Double {
    Self.easeOutBack(Self.progress(canonicalElapsed, start: 0, duration: 0.4))
  }

  var headerScale: Double { 0.6 + headerProgress * 0.4 }

  var headerOpacity: Double {
    Self.easeOut(Self.progress(canonicalElapsed, start: 0, duration: 0.2))
  }

  var scoreProgress: Double {
    Self.easeOut(Self.progress(canonicalElapsed, start: 0.4, duration: 0.8))
  }

  var lowerZoneOpacity: Double {
    Self.easeOut(Self.progress(canonicalElapsed, start: 1.8, duration: 0.4))
  }

  var actionsEnabled: Bool { canonicalElapsed >= 2.5 }

  var headerParticleProgress: Double {
    Self.progress(canonicalElapsed, start: 0.05, duration: 0.65)
  }

  var newRecordBurstProgress: Double {
    Self.progress(canonicalElapsed, start: 1.2, duration: 0.65)
  }

  func metricProgress(at index: Int) -> Double {
    Self.easeOut(
      Self.progress(
        canonicalElapsed,
        start: 1.2 + Double(index) * 0.3,
        duration: 0.3
      )
    )
  }

  func starProgress(at index: Int) -> Double {
    Self.easeOutBack(
      Self.progress(
        canonicalElapsed,
        start: Double(index) * 0.1,
        duration: 0.24
      )
    )
  }

  private static func progress(
    _ elapsed: TimeInterval,
    start: TimeInterval,
    duration: TimeInterval
  ) -> Double {
    min(max((elapsed - start) / duration, 0), 1)
  }

  private static func easeOut(_ value: Double) -> Double {
    1 - pow(1 - value, 3)
  }

  private static func easeOutBack(_ value: Double) -> Double {
    let overshoot = 1.70158
    let shifted = value - 1
    return 1 + (overshoot + 1) * pow(shifted, 3) + overshoot * pow(shifted, 2)
  }
}

private struct SessionExitCoverModifier: ViewModifier {
  let isActive: Bool

  func body(content: Content) -> some View {
    content
      .accessibilityHidden(isActive)
      .overlay {
        if isActive {
          LinearGradient(
            colors: [AppPalette.backgroundTop, AppPalette.backgroundBottom],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
          )
          .ignoresSafeArea()
          .allowsHitTesting(true)
          .accessibilityHidden(true)
        }
      }
  }
}

extension View {
  func sessionExitCovered(_ isActive: Bool) -> some View {
    modifier(SessionExitCoverModifier(isActive: isActive))
  }
}

struct SessionResultMetric: View {
  let value: String
  let label: LocalizedStringKey
  let progress: Double
  let identifier: String
  var tint: Color = AppPalette.accent

  var body: some View {
    VStack(spacing: 5) {
      Text(verbatim: value)
        .font(.title3.monospacedDigit().weight(.heavy))
        .foregroundStyle(AppPalette.ink)
        .accessibilityIdentifier(identifier)
      Text(label)
        .font(.caption2)
        .foregroundStyle(AppPalette.mutedInk)
      GeometryReader { geometry in
        ZStack(alignment: .leading) {
          Capsule().fill(AppPalette.accentSoft.opacity(0.34))
          Capsule()
            .fill(tint)
            .frame(width: geometry.size.width * progress)
        }
      }
      .frame(height: 4)
      .accessibilityHidden(true)
    }
    .frame(maxWidth: .infinity)
  }
}

struct SessionResultReviewSection: View {
  let items: [SessionReviewItem]
  var onSeeAll: (() -> Void)?

  var body: some View {
    if items.isEmpty {
      perfectBanner
    } else {
      mistakeList
    }
  }

  private var perfectBanner: some View {
    HStack(spacing: 12) {
      Image(systemName: "crown.fill")
        .font(.title2)
        .foregroundStyle(Color.yellow)
      VStack(alignment: .leading, spacing: 3) {
        Text("practice.result.perfect")
          .font(.headline.weight(.bold))
          .foregroundStyle(AppPalette.ink)
        Text("practice.result.perfect_detail")
          .font(.caption)
          .foregroundStyle(AppPalette.mutedInk)
      }
      Spacer()
    }
    .padding(17)
    .background(AppPalette.card, in: RoundedRectangle(cornerRadius: 21, style: .continuous))
    .accessibilityIdentifier("result.review.perfect")
  }

  private var mistakeList: some View {
    VStack(alignment: .leading, spacing: 12) {
      Label("result.review.title", systemImage: "arrow.triangle.2.circlepath.circle.fill")
        .font(.headline.weight(.bold))
        .foregroundStyle(AppPalette.ink)
        .accessibilityIdentifier("result.review.list")

      ForEach(items.prefix(5)) { item in
        mistakeRow(item)
      }

      if items.count > 5, let onSeeAll {
        Button("result.review.see_all", action: onSeeAll)
          .font(.subheadline.weight(.bold))
          .foregroundStyle(AppPalette.accent)
          .frame(maxWidth: .infinity, alignment: .trailing)
          .accessibilityIdentifier("result.review.see_all")
      }
    }
    .padding(17)
    .background(AppPalette.card, in: RoundedRectangle(cornerRadius: 21, style: .continuous))
  }

  private func mistakeRow(_ reviewItem: SessionReviewItem) -> some View {
    HStack(alignment: .top, spacing: 11) {
      Image(systemName: "checkmark.seal.fill")
        .foregroundStyle(AppPalette.success)
        .accessibilityLabel(Text("result.review.collected"))

      VStack(alignment: .leading, spacing: 4) {
        emphasizedKoreanText(reviewItem)
        HStack(spacing: 6) {
          if let reading = reviewItem.item.appReading {
            Text(verbatim: reading)
          }
          if reviewItem.item.appReading != nil, reviewItem.item.appMeaning != nil {
            Text(verbatim: "·")
          }
          if let meaning = reviewItem.item.appMeaning {
            Text(verbatim: meaning)
          }
        }
        .font(.caption)
        .foregroundStyle(AppPalette.mutedInk)
      }

      Spacer(minLength: 4)
      Text(
        AppLocalization.format("result.review.mistake_count_format",
          reviewItem.mistakeCount
        )
      )
      .font(.caption.monospacedDigit().weight(.bold))
      .foregroundStyle(AppPalette.error)
    }
    .padding(.vertical, 4)
    .accessibilityElement(children: .combine)
    .accessibilityIdentifier("result.review.item.\(reviewItem.item.id)")
  }

  private func emphasizedKoreanText(_ reviewItem: SessionReviewItem) -> some View {
    HStack(spacing: 0) {
      ForEach(Array(markedCharacters(for: reviewItem).enumerated()), id: \.offset) { _, part in
        Text(verbatim: String(part.character))
          .font(.title3.weight(.heavy))
          .foregroundStyle(AppPalette.ink)
          .underline(part.isMistaken, color: AppPalette.error)
      }
    }
  }

  private func markedCharacters(
    for reviewItem: SessionReviewItem
  ) -> [(character: Character, isMistaken: Bool)] {
    var jamoOffset = 0
    return reviewItem.item.ko.map { character in
      let jamoCount =
        (try? JamoDecomposer.keySequence(for: String(character)).count) ?? 1
      let jamoRange = jamoOffset..<(jamoOffset + jamoCount)
      let isMistaken = reviewItem.mistakenJamoIndices.contains { jamoRange.contains($0) }
      jamoOffset += jamoCount
      return (character, isMistaken)
    }
  }
}

struct SessionResultView<Header, Score, Metrics, Review, Actions, BottomBar>: View
where
  Header: View,
  Score: View,
  Metrics: View,
  Review: View,
  Actions: View,
  BottomBar: View
{
  @Environment(\.accessibilityReduceMotion) private var reduceMotion
  @Environment(\.hancoAdaptiveMetrics) private var adaptiveMetrics

  let navigationTitle: LocalizedStringKey
  let onFinish: () -> Void
  let finishTitle: LocalizedStringKey
  let finishSystemImage: String
  let finishAccessibilityIdentifier: String
  let finishIsPrimary: Bool
  let finishIsEnabled: Bool
  let showsToolbarFinish: Bool
  let celebratesNewRecord: Bool
  let timing: SessionResultRevealTiming
  private let header: (SessionResultRevealState) -> Header
  private let score: (SessionResultRevealState) -> Score
  private let metrics: (SessionResultRevealState) -> Metrics
  private let review: (SessionResultRevealState) -> Review
  private let actions: (SessionResultRevealState) -> Actions
  private let bottomBar: (Bool) -> BottomBar

  @State private var startedAt: Date?
  @State private var isSkipped = false
  @State private var actionsEnabled = false

  init(
    navigationTitle: LocalizedStringKey,
    onFinish: @escaping () -> Void,
    finishTitle: LocalizedStringKey = "result.back",
    finishSystemImage: String = "chevron.backward",
    finishAccessibilityIdentifier: String = "result.done.bottom",
    finishIsPrimary: Bool = false,
    finishIsEnabled: Bool = true,
    showsToolbarFinish: Bool = true,
    celebratesNewRecord: Bool = false,
    timing: SessionResultRevealTiming = .live,
    @ViewBuilder header: @escaping (SessionResultRevealState) -> Header,
    @ViewBuilder score: @escaping (SessionResultRevealState) -> Score,
    @ViewBuilder metrics: @escaping (SessionResultRevealState) -> Metrics,
    @ViewBuilder review: @escaping (SessionResultRevealState) -> Review,
    @ViewBuilder actions: @escaping (SessionResultRevealState) -> Actions,
    @ViewBuilder bottomBar: @escaping (Bool) -> BottomBar
  ) {
    self.navigationTitle = navigationTitle
    self.onFinish = onFinish
    self.finishTitle = finishTitle
    self.finishSystemImage = finishSystemImage
    self.finishAccessibilityIdentifier = finishAccessibilityIdentifier
    self.finishIsPrimary = finishIsPrimary
    self.finishIsEnabled = finishIsEnabled
    self.showsToolbarFinish = showsToolbarFinish
    self.celebratesNewRecord = celebratesNewRecord
    self.timing = timing
    self.header = header
    self.score = score
    self.metrics = metrics
    self.review = review
    self.actions = actions
    self.bottomBar = bottomBar
  }

  var body: some View {
    TimelineView(.animation(minimumInterval: 1.0 / 60.0, paused: actionsEnabled)) { timeline in
      let elapsed = startedAt.map { timeline.date.timeIntervalSince($0) } ?? 0
      let reveal = timing.state(elapsed: elapsed, skipped: isSkipped)

      ZStack {
        ScrollView {
          VStack(spacing: 16) {
            header(reveal)
              .scaleEffect(CGFloat(reveal.headerScale))
              .opacity(reveal.headerOpacity)

            score(reveal)
              .opacity(reveal.scoreProgress)
              .offset(y: CGFloat((1 - reveal.scoreProgress) * 10))

            metrics(reveal)
              .opacity(reveal.metricProgress(at: 0))

            review(reveal)
              .opacity(reveal.lowerZoneOpacity)

            actions(reveal)
              .opacity(reveal.lowerZoneOpacity)
              .disabled(!actionsEnabled)

            Color.clear
              .frame(height: 24)
              .accessibilityHidden(true)
          }
          .padding(18)
          .padding(.bottom, 82)
          .hancoCenteredContent(maxWidth: adaptiveMetrics.resultContentMaxWidth)
        }

        SessionResultCelebrationCanvas(
          reveal: reveal,
          celebratesNewRecord: celebratesNewRecord
        )
        .allowsHitTesting(false)
        .accessibilityHidden(true)
      }
      .safeAreaInset(edge: .bottom) {
        VStack(spacing: 8) {
          bottomBar(actionsEnabled)
          finishButton
        }
        .padding(.horizontal, 18)
        .padding(.vertical, 12)
        .hancoCenteredContent(maxWidth: adaptiveMetrics.resultContentMaxWidth)
        .background(.ultraThinMaterial)
        .opacity(reveal.lowerZoneOpacity)
        .disabled(!actionsEnabled)
      }
      .contentShape(Rectangle())
      .simultaneousGesture(
        TapGesture().onEnded {
          guard !actionsEnabled else { return }
          skipAnimation()
        }
      )
    }
    .background(
      LinearGradient(
        colors: [AppPalette.backgroundTop, AppPalette.backgroundBottom],
        startPoint: .topLeading,
        endPoint: .bottomTrailing
      )
      .ignoresSafeArea()
    )
    .navigationTitle(Text(navigationTitle))
    .navigationBarTitleDisplayMode(.inline)
    .navigationBarBackButtonHidden(true)
    .toolbar {
      ToolbarItem(placement: .topBarTrailing) {
        if showsToolbarFinish {
          Button("result.done", action: onFinish)
            .disabled(!finishIsEnabled)
            .accessibilityIdentifier("result.done")
        }
      }
    }
    .overlay(alignment: .topLeading) {
      Text(actionsEnabled ? "result.animation.ready" : "result.animation.playing")
        .font(.system(size: 1))
        .opacity(0.01)
        .accessibilityIdentifier("result.animation.state")
    }
    .overlay(alignment: .bottom) {
      if !actionsEnabled {
        Text("result.animation.skip_hint")
          .font(.caption.weight(.bold))
          .foregroundStyle(AppPalette.mutedInk)
          .padding(.horizontal, 12)
          .padding(.vertical, 7)
          .background(.ultraThinMaterial, in: Capsule())
          .padding(.bottom, 78)
          .allowsHitTesting(false)
      }
    }
    .onAppear {
      guard startedAt == nil else { return }
      startedAt = Date()
      if reduceMotion {
        skipAnimation()
      }
    }
    .task(id: startedAt) {
      guard startedAt != nil, !actionsEnabled else { return }
      let nanoseconds = UInt64(timing.totalDuration * 1_000_000_000)
      try? await Task.sleep(nanoseconds: nanoseconds)
      guard !Task.isCancelled, !isSkipped else { return }
      withAnimation(.easeOut(duration: 0.18)) {
        actionsEnabled = true
      }
    }
  }

  private var finishButton: some View {
    Button(action: onFinish) {
      Label(finishTitle, systemImage: finishSystemImage)
        .font(.headline.weight(.bold))
        .foregroundStyle(finishIsPrimary ? Color.white : AppPalette.accent)
        .frame(maxWidth: .infinity)
        .padding(.vertical, 14)
        .background(
          finishIsPrimary ? AppPalette.accent : AppPalette.card,
          in: RoundedRectangle(cornerRadius: 17)
        )
        .overlay {
          if !finishIsPrimary {
            RoundedRectangle(cornerRadius: 17)
              .strokeBorder(AppPalette.accent.opacity(0.28), lineWidth: 1)
          }
        }
    }
    .disabled(!finishIsEnabled)
    .accessibilityIdentifier(finishAccessibilityIdentifier)
  }

  private func skipAnimation() {
    withAnimation(.easeOut(duration: 0.16)) {
      isSkipped = true
      actionsEnabled = true
    }
  }
}

private struct SessionResultCelebrationCanvas: View {
  let reveal: SessionResultRevealState
  let celebratesNewRecord: Bool

  var body: some View {
    Canvas { context, size in
      drawBurst(
        context: &context,
        size: size,
        progress: reveal.headerParticleProgress,
        center: CGPoint(x: size.width * 0.5, y: size.height * 0.19),
        count: 12,
        distance: 82,
        tint: .yellow
      )
      if celebratesNewRecord {
        drawBurst(
          context: &context,
          size: size,
          progress: reveal.newRecordBurstProgress,
          center: CGPoint(x: size.width * 0.5, y: size.height * 0.43),
          count: 20,
          distance: 126,
          tint: AppPalette.accent
        )
      }
    }
    .ignoresSafeArea()
  }

  private func drawBurst(
    context: inout GraphicsContext,
    size: CGSize,
    progress: Double,
    center: CGPoint,
    count: Int,
    distance: Double,
    tint: Color
  ) {
    guard progress > 0, progress < 1 else { return }
    for index in 0..<count {
      let angle = Double(index) / Double(count) * Double.pi * 2 - Double.pi / 2
      let stagger = Double(index % 3) * 0.05
      let particleProgress = min(max((progress - stagger) / (1 - stagger), 0), 1)
      let radius = 14 + particleProgress * distance
      let point = CGPoint(
        x: center.x + CGFloat(cos(angle) * radius),
        y: center.y + CGFloat(sin(angle) * radius)
      )
      var particle = context
      particle.opacity = 1 - particleProgress
      particle.addFilter(
        .colorMultiply(index.isMultiple(of: 2) ? tint : AppPalette.secondary)
      )
      let image = particle.resolve(
        Image(systemName: index.isMultiple(of: 3) ? "heart.fill" : "star.fill")
      )
      particle.draw(image, at: point)
    }
  }
}
