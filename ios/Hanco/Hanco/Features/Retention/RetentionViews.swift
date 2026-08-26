import SwiftUI

struct DailyChallenge: Equatable, Identifiable {
  let day: JSTDay
  let items: [CurriculumItem]

  var id: JSTDay { day }
}

enum DailyChallengeCatalog {
  static func challenge(for day: JSTDay, goal: OnboardingGoal? = nil) -> DailyChallenge {
    let stageID: String
    switch goal {
    case .keyboard: stageID = "chapter_3_syllable_building"
    case .travel, .trends: stageID = "chapter_6_sentences"
    case .topik, .none: stageID = "chapter_5_words"
    }
    guard let pool = CurriculumCatalog.stage(id: stageID)?.items,
      pool.count >= 5
    else { preconditionFailure("Daily challenge word pool is missing") }

    let start = positiveModulo(day.ordinal, pool.count)
    let items = (0..<5).map { offset in
      pool[(start + offset * 3) % pool.count]
    }
    return DailyChallenge(day: day, items: items)
  }

  private static func positiveModulo(_ value: Int, _ divisor: Int) -> Int {
    let remainder = value % divisor
    return remainder >= 0 ? remainder : remainder + divisor
  }
}

struct MascotEncouragementBubble: View {
  let localizationKey: String

  var body: some View {
    Text(LocalizedStringKey(localizationKey))
      .font(.caption.weight(.semibold))
      .foregroundStyle(AppPalette.ink.opacity(0.78))
      .multilineTextAlignment(.leading)
      .fixedSize(horizontal: false, vertical: true)
      .padding(.horizontal, 12)
      .padding(.vertical, 9)
      .background(bubbleColor, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
      .overlay(alignment: .leading) {
        RoundedRectangle(cornerRadius: 2, style: .continuous)
          .fill(bubbleColor)
          .frame(width: 10, height: 10)
          .rotationEffect(.degrees(45))
          .offset(x: -4)
      }
      .padding(.leading, 5)
  }

  private var bubbleColor: Color {
    AppPalette.accentSoft.opacity(0.56)
  }
}

struct RetentionHomeView: View {
  @EnvironmentObject private var retention: RetentionLibrary
  @EnvironmentObject private var companion: MascotCompanionLibrary
  @EnvironmentObject private var progress: CurriculumProgressLibrary

  let today: JSTDay

  var body: some View {
    NavigationLink {
      MyPiyoDetailView(today: today)
    } label: {
      myPiyoStampCard
    }
    .buttonStyle(.plain)
    .onAppear(perform: registerEarnedRewards)
    .onChange(of: recentStampCount) { _ in registerEarnedRewards() }
  }

  private var myPiyoStampCard: some View {
    VStack(alignment: .leading, spacing: 15) {
      HStack(spacing: 14) {
        GrowingMascotView(
          mood: .happy,
          showsNameTag: false,
          size: 58,
          calm: true
        )
        .frame(width: 82, height: 92)
        .clipped()

        VStack(alignment: .leading, spacing: 5) {
          Text("my_page.profile_eyebrow")
            .font(.caption.weight(.black))
            .foregroundStyle(AppPalette.accent)
          Text(verbatim: companion.displayName)
            .font(.system(.title3, design: .rounded, weight: .heavy))
            .foregroundStyle(AppPalette.ink)
            .lineLimit(1)
          MascotEncouragementBubble(localizationKey: dailyEncouragementKey)
        }

        Spacer(minLength: 0)
        Image(systemName: "chevron.right")
          .font(.subheadline.weight(.bold))
          .foregroundStyle(AppPalette.mutedInk)
      }

      Divider().opacity(0.55)

      StampWeekHeader(
        days: calendarDays,
        stampedDayCount: recentStampCount,
        streak: streak
      )

      SevenDayStampRow(today: today)
    }
    .padding(18)
    .background(AppPalette.card, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
    .shadow(color: AppPalette.keyShadow, radius: 12, y: 7)
    .accessibilityElement(children: .ignore)
    .accessibilityLabel(Text("my_page.profile_eyebrow"))
    .accessibilityValue(Text(verbatim: homeCardAccessibilityValue))
    .accessibilityHint(Text("retention.streak.open_detail_hint"))
    .accessibilityIdentifier("home.my_piyo_card")
  }

  private var streak: RetentionStreak {
    retention.streak(asOf: today)
  }

  private var calendarDays: [JSTDay] {
    RetentionCalendar.stampCardDays(asOf: today)
  }

  private var recentStampCount: Int {
    calendarDays.filter(retention.isStamped).count
  }

  private var homeCardAccessibilityValue: String {
    let encouragement = AppLocalization.string(
      dailyEncouragementKey
    )
    let streakText = String(
      format: AppLocalization.string("retention.streak.current_format"),
      streak.current
    )
    let daySummary = calendarDays.map { day in
      let state = RetentionStampDayState(
        day: day,
        today: today,
        isStamped: retention.isStamped(day)
      )
      return "\(day.rawValue), \(AppLocalization.string(state.localizationKey))"
    }.joined(separator: ", ")
    return "\(encouragement), \(streakText), \(recentStampCount) / 7, \(daySummary)"
  }

  private var dailyEncouragementKey: String {
    MascotDailyEncouragement.localizationKey(
      for: today,
      completedDays: retention.completedDays
    )
  }

  private func registerEarnedRewards() {
    companion.registerUnlocks(
      StampReward.allCases.map { reward in
        MascotCompanionLibrary.UnlockState(
          prop: reward.prop,
          isUnlocked: recentStampCount >= reward.requiredDays,
          conditionKey: reward.conditionKey
        )
      }
    )
  }
}

private struct SevenDayStampRow: View {
  @EnvironmentObject private var retention: RetentionLibrary

  let today: JSTDay

  var body: some View {
    HStack(spacing: 7) {
      ForEach(calendarDays) { day in
        let state = stampState(for: day)
        VStack(spacing: 5) {
          Text(verbatim: weekday(for: day))
            .font(.caption2.weight(.bold))
            .foregroundStyle(weekdayColor(for: state))
          ZStack {
            Circle()
              .fill(fillColor(for: state))
            Circle()
              .strokeBorder(
                strokeColor(for: state),
                style: StrokeStyle(
                  lineWidth: state == .todayPending ? 2.5 : 1.5,
                  dash: state == .missed ? [3, 3] : []
                )
              )
            Image(systemName: state == .completed ? "seal.fill" : "circle")
              .font(.caption.weight(.black))
              .foregroundStyle(symbolColor(for: state))
          }
          .aspectRatio(1, contentMode: .fit)
          Text(verbatim: dayNumber(for: day))
            .font(
              .caption2.monospacedDigit().weight(state == .todayPending ? .black : .medium)
            )
            .foregroundStyle(dayNumberColor(for: state))
        }
        .frame(maxWidth: .infinity)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: stampAccessibilityLabel(for: day)))
        .accessibilityIdentifier("retention.stamp_day.\(day.rawValue)")
      }
    }
    .accessibilityElement(children: .contain)
    .accessibilityLabel(Text("retention.streak.title"))
    .accessibilityValue(Text(verbatim: "\(stampedDayCount) / 7"))
    .accessibilityIdentifier("retention.stamp_calendar")
  }

  private var calendarDays: [JSTDay] {
    RetentionCalendar.stampCardDays(asOf: today)
  }

  private var stampedDayCount: Int {
    calendarDays.filter(retention.isStamped).count
  }

  private func weekday(for day: JSTDay) -> String {
    guard let date = RetentionCalendar.date(for: day) else { return "" }
    let formatter = DateFormatter()
    formatter.locale = AppLocalization.locale
    formatter.timeZone = RetentionCalendar.jst.timeZone
    formatter.dateFormat = "E"
    return formatter.string(from: date)
  }

  private func dayNumber(for day: JSTDay) -> String {
    String(Int(day.rawValue.suffix(2)) ?? 0)
  }

  private func stampState(for day: JSTDay) -> RetentionStampDayState {
    RetentionStampDayState(
      day: day,
      today: today,
      isStamped: retention.isStamped(day)
    )
  }

  private func fillColor(for state: RetentionStampDayState) -> Color {
    switch state {
    case .completed: AppPalette.accentSoft
    case .missed: AppPalette.backgroundBottom.opacity(0.75)
    case .todayPending: AppPalette.accentSoft.opacity(0.35)
    case .upcoming: AppPalette.backgroundBottom.opacity(0.28)
    }
  }

  private func strokeColor(for state: RetentionStampDayState) -> Color {
    switch state {
    case .completed, .todayPending: AppPalette.accent
    case .missed: AppPalette.mutedInk.opacity(0.28)
    case .upcoming: AppPalette.mutedInk.opacity(0.1)
    }
  }

  private func symbolColor(for state: RetentionStampDayState) -> Color {
    switch state {
    case .completed: AppPalette.accent
    case .todayPending: AppPalette.accent.opacity(0.42)
    case .missed: AppPalette.mutedInk.opacity(0.28)
    case .upcoming: AppPalette.mutedInk.opacity(0.1)
    }
  }

  private func weekdayColor(for state: RetentionStampDayState) -> Color {
    switch state {
    case .todayPending: AppPalette.accent
    case .upcoming: AppPalette.mutedInk.opacity(0.45)
    case .completed, .missed: AppPalette.mutedInk
    }
  }

  private func dayNumberColor(for state: RetentionStampDayState) -> Color {
    state == .upcoming ? AppPalette.mutedInk.opacity(0.45) : AppPalette.ink
  }

  private func stampAccessibilityLabel(for day: JSTDay) -> String {
    let state = stampState(for: day)
    return "\(day.rawValue), \(AppLocalization.string(state.localizationKey))"
  }
}

private struct StampWeekHeader: View {
  let days: [JSTDay]
  let stampedDayCount: Int
  let streak: RetentionStreak

  var body: some View {
    HStack(alignment: .top, spacing: 10) {
      VStack(alignment: .leading, spacing: 3) {
        Label("retention.streak.title", systemImage: "flame.fill")
          .font(.headline.weight(.heavy))
          .foregroundStyle(AppPalette.ink)
        Text(verbatim: weekRangeText)
          .font(.caption.monospacedDigit().weight(.semibold))
          .foregroundStyle(AppPalette.mutedInk)
      }
      Spacer(minLength: 6)
      VStack(alignment: .trailing, spacing: 3) {
        Text(
          String(
            format: AppLocalization.string("retention.week.progress_format"),
            stampedDayCount
          )
        )
        .font(.subheadline.monospacedDigit().weight(.black))
        .foregroundStyle(AppPalette.accent)
        Text(
          String(
            format: AppLocalization.string("retention.streak.current_format"),
            streak.current
          )
        )
        .font(.caption.monospacedDigit().weight(.bold))
        .foregroundStyle(streak.current > 0 ? Color.orange : AppPalette.mutedInk)
      }
    }
    .accessibilityElement(children: .combine)
  }

  private var weekRangeText: String {
    guard let first = days.first.flatMap({ RetentionCalendar.date(for: $0) }),
      let last = days.last.flatMap({ RetentionCalendar.date(for: $0) })
    else { return "" }
    let formatter = DateFormatter()
    formatter.locale = AppLocalization.locale
    formatter.timeZone = RetentionCalendar.jst.timeZone
    formatter.setLocalizedDateFormatFromTemplate("Md")
    return "\(formatter.string(from: first))–\(formatter.string(from: last))"
  }
}

enum StampReward: Int, CaseIterable, Identifiable {
  case three = 3
  case five = 5
  case seven = 7

  var id: Int { rawValue }
  var requiredDays: Int { rawValue }

  var prop: MascotProp {
    switch self {
    case .three: .lightstick
    case .five: .ribbon
    case .seven: .headphones
    }
  }

  var conditionKey: String { "retention.rewards.\(rawValue).condition" }
  var titleKey: String { "retention.rewards.\(rawValue).title" }
  var systemImage: String {
    switch self {
    case .three: "wand.and.stars"
    case .five: "rosette"
    case .seven: "headphones"
    }
  }
}

struct MyPiyoDetailView: View {
  @EnvironmentObject private var retention: RetentionLibrary
  @EnvironmentObject private var companion: MascotCompanionLibrary
  @EnvironmentObject private var progress: CurriculumProgressLibrary

  @State private var showsMascotCloset = false

  let today: JSTDay

  var body: some View {
    ScrollView {
      VStack(spacing: 18) {
        profileCard
        stampCard
        rewardsCard
      }
      .padding(18)
    }
    .background(
      LinearGradient(
        colors: [AppPalette.backgroundTop, AppPalette.backgroundBottom],
        startPoint: .topLeading,
        endPoint: .bottomTrailing
      )
      .ignoresSafeArea()
    )
    .navigationTitle(Text("my_page.profile_eyebrow"))
    .navigationBarTitleDisplayMode(.inline)
    .onAppear(perform: registerEarnedRewards)
    .onChange(of: stampedDays) { _ in registerEarnedRewards() }
    .sheet(isPresented: $showsMascotCloset) {
      MascotClosetView()
    }
  }

  private var profileCard: some View {
    HStack(spacing: 16) {
      GrowingMascotView(
        mood: .idle,
        showsNameTag: false,
        interactive: true,
        onOpenCloset: { showsMascotCloset = true },
        size: 78,
        calm: true
      )
      .frame(width: 118, height: 140)
      .clipped()

      VStack(alignment: .leading, spacing: 7) {
        Text("my_page.profile_eyebrow")
          .font(.caption.weight(.black))
          .foregroundStyle(AppPalette.accent)
          .accessibilityIdentifier("my_piyo.detail.screen")
        Text(verbatim: companion.displayName)
          .font(.system(.title2, design: .rounded, weight: .heavy))
          .foregroundStyle(AppPalette.ink)
          .lineLimit(1)
        Label {
          Text(LocalizedStringKey(progress.mascotStage.l10nKey))
        } icon: {
          Image(systemName: "sparkles")
        }
        .font(.subheadline.weight(.semibold))
        .foregroundStyle(AppPalette.mutedInk)

        Button {
          showsMascotCloset = true
        } label: {
          Label("my_page.piyo_settings", systemImage: "hanger")
            .font(.caption.weight(.bold))
            .foregroundStyle(AppPalette.accent)
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(AppPalette.accentSoft.opacity(0.55), in: Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("my_piyo.detail.settings")
      }
      Spacer(minLength: 0)
    }
    .padding(18)
    .background(AppPalette.card, in: RoundedRectangle(cornerRadius: 26, style: .continuous))
    .shadow(color: AppPalette.keyShadow, radius: 12, y: 7)
  }

  private var stampCard: some View {
    VStack(alignment: .leading, spacing: 14) {
      StampWeekHeader(days: days, stampedDayCount: stampedDays, streak: streak)
        .accessibilityIdentifier("my_piyo.detail.stamps")

      SevenDayStampRow(today: today)

      Text(
        String(
          format: AppLocalization.string("retention.streak.longest_format"),
          streak.longest
        )
      )
      .font(.caption)
      .foregroundStyle(AppPalette.mutedInk)
    }
    .padding(18)
    .background(AppPalette.card, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
    .shadow(color: AppPalette.keyShadow, radius: 9, y: 5)
  }

  private var rewardsCard: some View {
    VStack(alignment: .leading, spacing: 12) {
      Label("retention.rewards.navigation_title", systemImage: "gift.fill")
        .font(.headline.weight(.heavy))
        .foregroundStyle(AppPalette.ink)

      ForEach(StampReward.allCases) { reward in
        HStack(spacing: 13) {
          Image(systemName: reward.systemImage)
            .font(.title3.weight(.bold))
            .foregroundStyle(isEarned(reward) ? AppPalette.success : AppPalette.mutedInk)
            .frame(width: 44, height: 44)
            .background(
              (isEarned(reward) ? AppPalette.accentSoft : AppPalette.backgroundBottom),
              in: RoundedRectangle(cornerRadius: 14)
            )
          VStack(alignment: .leading, spacing: 3) {
            Text(LocalizedStringKey(reward.titleKey))
              .font(.headline.weight(.bold))
              .foregroundStyle(AppPalette.ink)
            Text(LocalizedStringKey(reward.conditionKey))
              .font(.caption)
              .foregroundStyle(AppPalette.mutedInk)
          }
          Spacer()
          Image(systemName: isEarned(reward) ? "checkmark.seal.fill" : "lock.fill")
            .foregroundStyle(isEarned(reward) ? AppPalette.success : AppPalette.mutedInk)
        }
        .padding(15)
        .background(
          AppPalette.backgroundBottom.opacity(0.7),
          in: RoundedRectangle(cornerRadius: 20)
        )
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("my_piyo.detail.reward.\(reward.requiredDays)")
      }
    }
    .padding(18)
    .background(AppPalette.card, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
    .shadow(color: AppPalette.keyShadow, radius: 9, y: 5)
    .accessibilityElement(children: .contain)
    .accessibilityLabel(Text("retention.rewards.accessibility"))
  }

  private var days: [JSTDay] {
    RetentionCalendar.stampCardDays(asOf: today)
  }
  private var stampedDays: Int { days.filter(retention.isStamped).count }
  private var streak: RetentionStreak { retention.streak(asOf: today) }
  private func isEarned(_ reward: StampReward) -> Bool {
    stampedDays >= reward.requiredDays || companion.hasUnlocked(reward.prop)
  }
  private func registerEarnedRewards() {
    companion.registerUnlocks(
      StampReward.allCases.map { reward in
        MascotCompanionLibrary.UnlockState(
          prop: reward.prop,
          isUnlocked: stampedDays >= reward.requiredDays,
          conditionKey: reward.conditionKey
        )
      }
    )
  }
}

struct DailyChallengePracticeDestination: View {
  @EnvironmentObject private var retention: RetentionLibrary

  let challenge: DailyChallenge

  var body: some View {
    PracticeView(
      targets: challenge.items.map(\.ko),
      sessionTitle: AppLocalization.string("retention.daily.session_title"),
      reviewSources: challenge.items.map {
        PracticeReviewSource(item: $0.deckItem, sourceDeckId: "official_daily_challenge")
      },
      onPracticeCompletion: { _, _ in
        retention.record(.dailyChallenge, on: challenge.day)
      },
      analyticsSessionKind: "daily",
      analyticsDeckSource: "daily"
    )
  }
}
