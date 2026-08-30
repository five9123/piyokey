import Combine
import Foundation
import UserNotifications

struct DailyReminderPreference: Equatable {
  var isEnabled: Bool
  var hour: Int
  var minute: Int
}

enum DailyReminderDefaults {
  static let localWorkdayEndHour = 20
  static let minute = 0
}

struct DailyReminderSettingsStore {
  static let enabledKey = "retention.reminder.enabled"
  static let hourKey = "retention.reminder.hour"
  static let minuteKey = "retention.reminder.minute"

  private let defaults: UserDefaults

  init(defaults: UserDefaults = .standard) {
    self.defaults = defaults
  }

  var hasStoredEnabledPreference: Bool {
    defaults.object(forKey: Self.enabledKey) != nil
  }

  func load() -> DailyReminderPreference {
    DailyReminderPreference(
      isEnabled: defaults.bool(forKey: Self.enabledKey),
      hour: defaults.object(forKey: Self.hourKey) == nil
        ? DailyReminderDefaults.localWorkdayEndHour : defaults.integer(forKey: Self.hourKey),
      minute: defaults.object(forKey: Self.minuteKey) == nil
        ? DailyReminderDefaults.minute : defaults.integer(forKey: Self.minuteKey)
    )
  }

  func save(_ preference: DailyReminderPreference) {
    defaults.set(preference.isEnabled, forKey: Self.enabledKey)
    defaults.set(preference.hour, forKey: Self.hourKey)
    defaults.set(preference.minute, forKey: Self.minuteKey)
  }

  func reset() {
    defaults.removeObject(forKey: Self.enabledKey)
    defaults.removeObject(forKey: Self.hourKey)
    defaults.removeObject(forKey: Self.minuteKey)
  }
}

enum DailyReminderScheduleResult: Equatable {
  case scheduled
  case denied
  case failed
}

enum DailyReminderStatus: Equatable {
  case idle
  case scheduling
  case denied
  case failed
}

enum DailyReminderRequest {
  static let identifier = "hanco.daily-practice-reminder"

  static func dateComponents(hour: Int, minute: Int) -> DateComponents {
    var components = DateComponents()
    components.hour = hour
    components.minute = minute
    return components
  }
}

@MainActor
protocol DailyReminderScheduling: AnyObject {
  func schedule(hour: Int, minute: Int) async -> DailyReminderScheduleResult
  func cancel()
}

@MainActor
final class SystemDailyReminderScheduler: DailyReminderScheduling {
  private let center: UNUserNotificationCenter

  init(center: UNUserNotificationCenter = .current()) {
    self.center = center
  }

  func schedule(hour: Int, minute: Int) async -> DailyReminderScheduleResult {
    let settings = await center.notificationSettings()
    let isAuthorized: Bool
    switch settings.authorizationStatus {
    case .notDetermined:
      do {
        isAuthorized = try await center.requestAuthorization(options: [.alert, .sound])
      } catch {
        return .failed
      }
    case .authorized, .provisional, .ephemeral:
      isAuthorized = true
    case .denied:
      isAuthorized = false
    @unknown default:
      isAuthorized = false
    }
    guard isAuthorized else { return .denied }

    let content = UNMutableNotificationContent()
    content.title = AppLocalization.string("retention.reminder.notification_title")
    content.body = AppLocalization.string("retention.reminder.notification_body")
    content.sound = .default
    let trigger = UNCalendarNotificationTrigger(
      dateMatching: DailyReminderRequest.dateComponents(hour: hour, minute: minute),
      repeats: true
    )
    let request = UNNotificationRequest(
      identifier: DailyReminderRequest.identifier,
      content: content,
      trigger: trigger
    )
    center.removePendingNotificationRequests(withIdentifiers: [DailyReminderRequest.identifier])
    do {
      try await center.add(request)
      return .scheduled
    } catch {
      return .failed
    }
  }

  func cancel() {
    center.removePendingNotificationRequests(withIdentifiers: [DailyReminderRequest.identifier])
  }
}

@MainActor
final class DailyReminderLibrary: ObservableObject {
  @Published private(set) var preference: DailyReminderPreference
  @Published private(set) var status: DailyReminderStatus = .idle

  private let store: DailyReminderSettingsStore
  private let scheduler: DailyReminderScheduling
  private var schedulingTask: Task<Void, Never>?

  var hasStoredEnabledPreference: Bool {
    store.hasStoredEnabledPreference
  }

  init(
    store: DailyReminderSettingsStore = DailyReminderSettingsStore(),
    scheduler: DailyReminderScheduling? = nil
  ) {
    self.store = store
    self.scheduler = scheduler ?? SystemDailyReminderScheduler()
    self.preference = store.load()
    if preference.isEnabled {
      reschedule()
    }
  }

  func setEnabled(_ isEnabled: Bool) {
    schedulingTask?.cancel()
    if !isEnabled {
      scheduler.cancel()
      preference.isEnabled = false
      store.save(preference)
      status = .idle
      return
    }

    status = .scheduling
    schedulingTask = Task { @MainActor in
      let result = await scheduler.schedule(hour: preference.hour, minute: preference.minute)
      guard !Task.isCancelled else { return }
      apply(result)
    }
  }

  @discardableResult
  func enableFromOnboarding() async -> DailyReminderScheduleResult {
    schedulingTask?.cancel()
    status = .scheduling
    let result = await scheduler.schedule(hour: preference.hour, minute: preference.minute)
    guard !Task.isCancelled else { return .failed }
    apply(result)
    return result
  }

  func setTime(hour: Int, minute: Int) {
    guard (0...23).contains(hour), (0...59).contains(minute) else { return }
    preference.hour = hour
    preference.minute = minute
    store.save(preference)
    if preference.isEnabled {
      reschedule()
    }
  }

  /// Refresh scheduled copy after a language change without enabling reminders.
  func refreshLocalizedContent() {
    guard preference.isEnabled else { return }
    reschedule()
  }

  private func reschedule() {
    schedulingTask?.cancel()
    status = .scheduling
    schedulingTask = Task { @MainActor in
      let result = await scheduler.schedule(hour: preference.hour, minute: preference.minute)
      guard !Task.isCancelled else { return }
      apply(result)
    }
  }

  private func apply(_ result: DailyReminderScheduleResult) {
    switch result {
    case .scheduled:
      preference.isEnabled = true
      status = .idle
    case .denied:
      preference.isEnabled = false
      status = .denied
    case .failed:
      preference.isEnabled = false
      status = .failed
    }
    store.save(preference)
  }

  static func appRootDefault() -> DailyReminderLibrary {
    #if DEBUG
      if let rawResult = ProcessInfo.processInfo.environment[
        "UITEST_ONBOARDING_NOTIFICATION_RESULT"
      ] {
        return DailyReminderLibrary(
          scheduler: UITestDailyReminderScheduler(
            result: rawResult == "scheduled" ? .scheduled : .denied
          )
        )
      }
    #endif
    return DailyReminderLibrary()
  }
}

#if DEBUG
  @MainActor
  private final class UITestDailyReminderScheduler: DailyReminderScheduling {
    private let result: DailyReminderScheduleResult

    init(result: DailyReminderScheduleResult) {
      self.result = result
    }

    func schedule(hour: Int, minute: Int) async -> DailyReminderScheduleResult {
      result
    }

    func cancel() {}
  }
#endif
