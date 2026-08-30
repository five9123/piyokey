import AVFoundation
import Foundation
#if DEBUG
  import Combine
#endif

enum SoundPreferenceKeys {
  static let effectsEnabled = "sound.effects_enabled"
  static let typingPreset = "sound.typing_preset"

  static let all = [effectsEnabled, typingPreset]
}

enum TypingSoundPreset: String, CaseIterable, Hashable, Identifiable {
  case system
  case mechanical
  case soft

  var id: String { rawValue }

  static func resolved(from rawValue: String) -> TypingSoundPreset {
    TypingSoundPreset(rawValue: rawValue) ?? .system
  }
}

enum TypingSoundKeyRole: Hashable {
  case character
  case backspace
  case shift
}

enum HancoSoundEvent: Equatable {
  case keyTap(TypingSoundPreset, TypingSoundKeyRole)
  case completion(combo: Int)
  case mistake
  case lifeLost
  case eggKnock
}

struct TypingSoundVariantProfile: Equatable {
  let playbackRate: Double
  let gain: Float
  let peakLimit: Float
}

enum HancoTypingSoundTuning {
  static let variationCount = 3
  static let peakLimit: Float = 0.78

  static func profile(
    preset: TypingSoundPreset,
    role: TypingSoundKeyRole,
    variationIndex: Int
  ) -> TypingSoundVariantProfile {
    let normalizedIndex = ((variationIndex % variationCount) + variationCount) % variationCount
    let variationRates = [0.988, 1.0, 1.012]
    let variationGains: [Float] = [0.96, 1.0, 0.93]

    let presetGain: Float
    switch preset {
    case .system:
      presetGain = 0.34
    case .mechanical:
      presetGain = 0.80
    case .soft:
      presetGain = 0.95
    }

    let roleRate: Double
    let roleGain: Float
    switch role {
    case .character:
      roleRate = 1
      roleGain = 1
    case .backspace:
      roleRate = 0.94
      roleGain = 0.72
    case .shift:
      roleRate = 1.08
      roleGain = 0.52
    }

    return TypingSoundVariantProfile(
      playbackRate: variationRates[normalizedIndex] * roleRate,
      gain: presetGain * variationGains[normalizedIndex] * roleGain,
      peakLimit: peakLimit
    )
  }
}

enum HancoSoundPlaybackLane: Equatable {
  case typing
  case feedback
}

enum HancoSoundPlaybackPolicy {
  static let typingPlayerCount = 6
  static let feedbackPlayerCount = 3
  static let idleShutdownDelay: TimeInterval = 15

  static func lane(for event: HancoSoundEvent) -> HancoSoundPlaybackLane {
    switch event {
    case .keyTap:
      return .typing
    case .completion, .mistake, .lifeLost, .eggKnock:
      return .feedback
    }
  }
}

enum HancoSoundWarmupPolicy {
  /// OS IME does not play the app-owned typing click. Prepare the feedback
  /// engine after real text activity so the first accepted word does not pay
  /// the audio-session and engine cold-start cost.
  static func shouldPrepareForOSIMEInput(
    committedText: String,
    markedText: String?
  ) -> Bool {
    !committedText.isEmpty || !(markedText ?? "").isEmpty
  }

  static func completionCombosToPrepare(after currentCombo: Int) -> [Int] {
    let current = min(max(currentCombo, 0), 20)
    let next = min(current + 1, 20)
    return current == next ? [current] : [current, next]
  }
}

enum SoundWaveform: Equatable {
  case sine
  case triangle
  case noise
}

struct SoundComponent: Equatable {
  let waveform: SoundWaveform
  let startTime: TimeInterval
  let duration: TimeInterval
  let startFrequency: Double
  let endFrequency: Double
  let amplitude: Double
  let attack: TimeInterval
  let release: TimeInterval
}

struct SoundPlan: Equatable {
  let components: [SoundComponent]

  var duration: TimeInterval {
    components.map { $0.startTime + $0.duration }.max() ?? 0
  }
}

enum HancoSoundPlanner {
  static func plan(for event: HancoSoundEvent) -> SoundPlan {
    switch event {
    case .keyTap(.system, _):
      return SoundPlan(
        components: [
          SoundComponent(
            waveform: .noise,
            startTime: 0,
            duration: 0.018,
            startFrequency: 0,
            endFrequency: 0,
            amplitude: 0.10,
            attack: 0.0005,
            release: 0.014
          ),
          SoundComponent(
            waveform: .triangle,
            startTime: 0,
            duration: 0.021,
            startFrequency: 2_150,
            endFrequency: 2_000,
            amplitude: 0.075,
            attack: 0.0005,
            release: 0.017
          ),
        ]
      )

    case .keyTap(.mechanical, _):
      return SoundPlan(
        components: [
          SoundComponent(
            waveform: .noise,
            startTime: 0,
            duration: 0.028,
            startFrequency: 0,
            endFrequency: 0,
            amplitude: 0.15,
            attack: 0.001,
            release: 0.022
          ),
          SoundComponent(
            waveform: .triangle,
            startTime: 0,
            duration: 0.042,
            startFrequency: 1_650,
            endFrequency: 980,
            amplitude: 0.12,
            attack: 0.001,
            release: 0.034
          ),
        ]
      )

    case .keyTap(.soft, _):
      return SoundPlan(
        components: [
          SoundComponent(
            waveform: .sine,
            startTime: 0,
            duration: 0.062,
            startFrequency: 540,
            endFrequency: 420,
            amplitude: 0.14,
            attack: 0.004,
            release: 0.048
          )
        ]
      )

    case .completion(let combo):
      let clampedCombo = min(max(combo, 0), 20)
      let baseFrequency = 660 * pow(2, Double(clampedCombo) / 30)
      return SoundPlan(
        components: [
          SoundComponent(
            waveform: .sine,
            startTime: 0,
            duration: 0.11,
            startFrequency: baseFrequency,
            endFrequency: baseFrequency,
            amplitude: 0.17,
            attack: 0.005,
            release: 0.07
          ),
          SoundComponent(
            waveform: .sine,
            startTime: 0.065,
            duration: 0.15,
            startFrequency: baseFrequency * 1.25,
            endFrequency: baseFrequency * 1.25,
            amplitude: 0.15,
            attack: 0.006,
            release: 0.1
          ),
        ]
      )

    case .mistake:
      return SoundPlan(
        components: [
          SoundComponent(
            waveform: .sine,
            startTime: 0,
            duration: 0.14,
            startFrequency: 210,
            endFrequency: 170,
            amplitude: 0.11,
            attack: 0.008,
            release: 0.1
          )
        ]
      )

    case .lifeLost:
      return SoundPlan(
        components: [
          SoundComponent(
            waveform: .triangle,
            startTime: 0,
            duration: 0.24,
            startFrequency: 260,
            endFrequency: 105,
            amplitude: 0.20,
            attack: 0.004,
            release: 0.16
          ),
          SoundComponent(
            waveform: .noise,
            startTime: 0,
            duration: 0.075,
            startFrequency: 0,
            endFrequency: 0,
            amplitude: 0.09,
            attack: 0.002,
            release: 0.06
          ),
        ]
      )

    case .eggKnock:
      return SoundPlan(
        components: [
          SoundComponent(
            waveform: .triangle,
            startTime: 0,
            duration: 0.07,
            startFrequency: 430,
            endFrequency: 360,
            amplitude: 0.10,
            attack: 0.003,
            release: 0.055
          ),
          SoundComponent(
            waveform: .triangle,
            startTime: 0.13,
            duration: 0.08,
            startFrequency: 500,
            endFrequency: 390,
            amplitude: 0.09,
            attack: 0.003,
            release: 0.06
          ),
        ]
      )
    }
  }
}

enum HancoAudioBufferTransformer {
  static func typingVariant(
    from source: AVAudioPCMBuffer,
    profile: TypingSoundVariantProfile
  ) -> AVAudioPCMBuffer? {
    guard profile.playbackRate > 0,
      source.frameLength > 0,
      let sourceChannel = source.floatChannelData?[0]
    else { return nil }

    let outputFrameCount = max(
      1,
      AVAudioFrameCount(ceil(Double(source.frameLength) / profile.playbackRate))
    )
    guard let output = AVAudioPCMBuffer(
      pcmFormat: source.format,
      frameCapacity: outputFrameCount
    ), let outputChannel = output.floatChannelData?[0]
    else { return nil }
    output.frameLength = outputFrameCount

    let lastSourceFrame = max(0, Int(source.frameLength) - 1)
    for outputFrame in 0..<Int(outputFrameCount) {
      let sourcePosition = min(
        Double(outputFrame) * profile.playbackRate,
        Double(lastSourceFrame)
      )
      let lowerFrame = Int(sourcePosition)
      let upperFrame = min(lowerFrame + 1, lastSourceFrame)
      let interpolation = Float(sourcePosition - Double(lowerFrame))
      let interpolated = sourceChannel[lowerFrame]
        + (sourceChannel[upperFrame] - sourceChannel[lowerFrame]) * interpolation
      outputChannel[outputFrame] = min(
        max(interpolated * profile.gain, -profile.peakLimit),
        profile.peakLimit
      )
    }
    return output
  }
}

struct HancoAudioSessionConfiguration: Equatable {
  let category: AVAudioSession.Category
  let mode: AVAudioSession.Mode
  let options: AVAudioSession.CategoryOptions
}

enum HancoAudioSessionPolicy {
  static let soundEffects = HancoAudioSessionConfiguration(
    category: .playback,
    mode: .default,
    options: [.mixWithOthers]
  )

  static let pronunciation = HancoAudioSessionConfiguration(
    category: .playback,
    mode: .spokenAudio,
    options: [.mixWithOthers]
  )
}

#if DEBUG
  @MainActor
  final class HancoAudioDebugProbe: ObservableObject {
    enum PronunciationBackend: String, CaseIterable {
      case explicitBundled = "explicit"
      case canonicalBundled = "canonical"
      case synthesized = "synthesized"
    }

    static let shared = HancoAudioDebugProbe()

    @Published private(set) var pronunciationPlaybackStartCount = 0
    @Published private(set) var pronunciationPlaybackExplicitBundledCount = 0
    @Published private(set) var pronunciationPlaybackCanonicalBundledCount = 0
    @Published private(set) var pronunciationPlaybackSynthesizedFallbackCount = 0
    @Published private(set) var effectPlaybackStartCount = 0
    @Published private(set) var effectSchedulingP95Milliseconds: Double?

    private var effectSchedulingSamples: [Double] = []

    private init() {}

    func recordPronunciationPlaybackStart() {
      pronunciationPlaybackStartCount += 1
    }

    func recordPronunciationBackend(_ backend: PronunciationBackend) {
      switch backend {
      case .explicitBundled:
        pronunciationPlaybackExplicitBundledCount += 1
      case .canonicalBundled:
        pronunciationPlaybackCanonicalBundledCount += 1
      case .synthesized:
        pronunciationPlaybackSynthesizedFallbackCount += 1
      }
    }

    func recordEffectPlaybackStart(requestedAt: TimeInterval, startedAt: TimeInterval) {
      effectPlaybackStartCount += 1
      effectSchedulingSamples.append(max(0, startedAt - requestedAt) * 1_000)
      if effectSchedulingSamples.count > 120 {
        effectSchedulingSamples.removeFirst(effectSchedulingSamples.count - 120)
      }
      let sorted = effectSchedulingSamples.sorted()
      let percentileIndex = max(0, Int(ceil(Double(sorted.count) * 0.95)) - 1)
      effectSchedulingP95Milliseconds = sorted[percentileIndex]
    }
  }
#endif

enum HancoAudioSessionEvent: Equatable {
  case interruptionBegan
  case interruptionEnded(shouldResume: Bool)
  case routeChanged
  case mediaServicesLost
  case mediaServicesReset
  case pronunciationEnded
}

final class HancoAudioSessionController {
  static let shared = HancoAudioSessionController()

  private let lock = NSLock()
  private let session = AVAudioSession.sharedInstance()
  private let notificationQueue = DispatchQueue(
    label: "app.hanco.audio-session-notifications",
    qos: .userInitiated
  )
  private var pronunciationTokens: Set<UUID> = []
  private var currentConfiguration: HancoAudioSessionConfiguration?
  private var isSessionActive = false
  private var eventHandler: ((HancoAudioSessionEvent) -> Void)?
  private var notificationObservers: [NSObjectProtocol] = []

  private init() {
    installNotificationObservers()
  }

  deinit {
    notificationObservers.forEach(NotificationCenter.default.removeObserver)
  }

  func setEventHandler(_ handler: @escaping (HancoAudioSessionEvent) -> Void) {
    lock.lock()
    eventHandler = handler
    lock.unlock()
  }

  func beginPronunciationPlayback() -> UUID {
    lock.lock()
    defer { lock.unlock() }

    let token = UUID()
    pronunciationTokens.insert(token)
    #if DEBUG
      Task { @MainActor in
        HancoAudioDebugProbe.shared.recordPronunciationPlaybackStart()
      }
    #endif
    try? apply(HancoAudioSessionPolicy.pronunciation)
    return token
  }

  func endPronunciationPlayback(_ token: UUID) {
    lock.lock()
    guard pronunciationTokens.remove(token) != nil,
      pronunciationTokens.isEmpty
    else {
      lock.unlock()
      return
    }
    try? apply(HancoAudioSessionPolicy.soundEffects)
    let handler = eventHandler
    lock.unlock()
    handler?(.pronunciationEnded)
  }

  func prepareForSoundEffects() throws -> Bool {
    lock.lock()
    defer { lock.unlock() }

    guard pronunciationTokens.isEmpty else { return false }
    try apply(HancoAudioSessionPolicy.soundEffects)
    return true
  }

  var isPronunciationPlaybackActive: Bool {
    lock.lock()
    defer { lock.unlock() }
    return !pronunciationTokens.isEmpty
  }

  func deactivateSoundEffectsIfPossible() {
    lock.lock()
    defer { lock.unlock() }

    guard pronunciationTokens.isEmpty, isSessionActive else { return }
    try? session.setActive(false, options: [.notifyOthersOnDeactivation])
    isSessionActive = false
  }

  private func apply(_ configuration: HancoAudioSessionConfiguration) throws {
    if currentConfiguration != configuration {
      try session.setCategory(
        configuration.category,
        mode: configuration.mode,
        options: configuration.options
      )
      currentConfiguration = configuration
    }
    if !isSessionActive {
      try session.setActive(true)
      isSessionActive = true
    }
  }

  static func routeChangeRequiresRecovery(_ reason: AVAudioSession.RouteChangeReason) -> Bool {
    reason == .newDeviceAvailable
      || reason == .oldDeviceUnavailable
      || reason == .noSuitableRouteForCategory
      || reason == .routeConfigurationChange
  }

  private func installNotificationObservers() {
    let center = NotificationCenter.default
    notificationObservers.append(
      center.addObserver(
        forName: AVAudioSession.interruptionNotification,
        object: session,
        queue: nil
      ) { [weak self] notification in
        self?.notificationQueue.async { [weak self] in
          self?.handleInterruption(notification)
        }
      }
    )
    notificationObservers.append(
      center.addObserver(
        forName: AVAudioSession.routeChangeNotification,
        object: session,
        queue: nil
      ) { [weak self] notification in
        self?.notificationQueue.async { [weak self] in
          self?.handleRouteChange(notification)
        }
      }
    )
    notificationObservers.append(
      center.addObserver(
        forName: AVAudioSession.mediaServicesWereLostNotification,
        object: session,
        queue: nil
      ) { [weak self] _ in
        self?.notificationQueue.async { [weak self] in
          self?.invalidateSessionState(for: .mediaServicesLost, resetsConfiguration: false)
        }
      }
    )
    notificationObservers.append(
      center.addObserver(
        forName: AVAudioSession.mediaServicesWereResetNotification,
        object: session,
        queue: nil
      ) { [weak self] _ in
        self?.notificationQueue.async { [weak self] in
          self?.invalidateSessionState(for: .mediaServicesReset, resetsConfiguration: true)
        }
      }
    )
  }

  private func handleInterruption(_ notification: Notification) {
    guard let rawType = notification.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt,
      let type = AVAudioSession.InterruptionType(rawValue: rawType)
    else { return }

    let event: HancoAudioSessionEvent
    switch type {
    case .began:
      event = .interruptionBegan
    case .ended:
      let rawOptions = notification.userInfo?[AVAudioSessionInterruptionOptionKey] as? UInt ?? 0
      let options = AVAudioSession.InterruptionOptions(rawValue: rawOptions)
      event = .interruptionEnded(shouldResume: options.contains(.shouldResume))
    @unknown default:
      return
    }
    let restoresPronunciation: Bool
    if case .interruptionEnded(let shouldResume) = event {
      restoresPronunciation = shouldResume
    } else {
      restoresPronunciation = false
    }
    invalidateSessionState(
      for: event,
      resetsConfiguration: false,
      restoresPronunciation: restoresPronunciation
    )
  }

  private func handleRouteChange(_ notification: Notification) {
    guard let rawReason = notification.userInfo?[AVAudioSessionRouteChangeReasonKey] as? UInt,
      let reason = AVAudioSession.RouteChangeReason(rawValue: rawReason),
      Self.routeChangeRequiresRecovery(reason)
    else { return }
    invalidateSessionState(for: .routeChanged, resetsConfiguration: false)
  }

  private func invalidateSessionState(
    for event: HancoAudioSessionEvent,
    resetsConfiguration: Bool,
    restoresPronunciation: Bool = false
  ) {
    lock.lock()
    isSessionActive = false
    if resetsConfiguration {
      currentConfiguration = nil
    }
    if restoresPronunciation, !pronunciationTokens.isEmpty {
      try? apply(HancoAudioSessionPolicy.pronunciation)
    }
    let handler = eventHandler
    lock.unlock()
    handler?(event)
  }
}

@MainActor
enum HancoTypingSoundFeedback {
  static func play(
    _ preset: TypingSoundPreset,
    role: TypingSoundKeyRole = .character
  ) {
    guard !HancoAudioSessionController.shared.isPronunciationPlaybackActive else { return }
    HancoSoundEngine.shared.play(.keyTap(preset, role))
  }
}

enum HancoBundledSoundAsset {
  static let defaultTypingSoundResource = "ui_basic_mouse_click_640020"
  static let defaultTypingSoundExtension = "caf"

  static func defaultTypingSoundURL(in bundle: Bundle = .main) -> URL? {
    bundle.url(
      forResource: defaultTypingSoundResource,
      withExtension: defaultTypingSoundExtension
    )
  }
}

final class HancoSoundEngine {
  static let shared = HancoSoundEngine()

  private enum CacheKey: Hashable {
    case typing(TypingSoundPreset, TypingSoundKeyRole, Int)
    case completion(Int)
    case mistake
    case lifeLost
    case eggKnock
  }

  private var engine = AVAudioEngine()
  private var typingPlayers = HancoSoundEngine.makePlayers(
    count: HancoSoundPlaybackPolicy.typingPlayerCount
  )
  private var feedbackPlayers = HancoSoundEngine.makePlayers(
    count: HancoSoundPlaybackPolicy.feedbackPlayerCount
  )
  private let format = AVAudioFormat(standardFormatWithSampleRate: 48_000, channels: 1)!
  private let audioQueue = DispatchQueue(label: "app.hanco.sound-engine", qos: .userInteractive)
  private var buffers: [CacheKey: AVAudioPCMBuffer] = [:]
  private var typingSourceBuffers: [TypingSoundPreset: AVAudioPCMBuffer] = [:]
  private var nextTypingPlayerIndex = 0
  private var nextFeedbackPlayerIndex = 0
  private var nextTypingVariationIndex = 0
  private var graphIsConnected = false
  private var isEnabled: Bool
  private var idleShutdownWorkItem: DispatchWorkItem?
  private var shouldResumeAfterSystemRecovery = false

  private init(defaults: UserDefaults = .standard) {
    if defaults.object(forKey: SoundPreferenceKeys.effectsEnabled) == nil {
      isEnabled = true
    } else {
      isEnabled = defaults.bool(forKey: SoundPreferenceKeys.effectsEnabled)
    }
    if let defaultTypingSound = loadDefaultTypingSound() {
      typingSourceBuffers[.system] = defaultTypingSound
    }
    HancoAudioSessionController.shared.setEventHandler { [weak self] event in
      self?.handleAudioSessionEvent(event)
    }
  }

  func setEnabled(_ enabled: Bool) {
    audioQueue.async { [weak self] in
      guard let self else { return }
      self.isEnabled = enabled
      guard !enabled else { return }
      self.shouldResumeAfterSystemRecovery = false
      self.cancelIdleShutdown()
      self.stopAllPlayers()
      self.engine.pause()
      HancoAudioSessionController.shared.deactivateSoundEffectsIfPossible()
    }
  }

  func suspendForInactivity() {
    audioQueue.async { [weak self] in
      guard let self else { return }
      self.shouldResumeAfterSystemRecovery = false
      self.cancelIdleShutdown()
      self.stopAllPlayers()
      self.engine.pause()
      HancoAudioSessionController.shared.deactivateSoundEffectsIfPossible()
    }
  }

  func suspendForPronunciationPlayback() {
    audioQueue.async { [weak self] in
      guard let self else { return }
      self.cancelIdleShutdown()
      self.stopAllPlayers()
      self.engine.pause()
    }
  }

  func play(_ event: HancoSoundEvent) {
    #if DEBUG
      let requestedAt = ProcessInfo.processInfo.systemUptime
    #endif
    audioQueue.async { [weak self] in
      guard let self, self.isEnabled else { return }
      do {
        self.cancelIdleShutdown()
        guard !HancoAudioSessionController.shared.isPronunciationPlaybackActive else {
          return
        }
        let buffer = self.buffer(for: event)
        guard try self.startIfNeeded() else { return }
        let player = self.player(for: HancoSoundPlaybackPolicy.lane(for: event))
        player.stop()
        player.scheduleBuffer(buffer, at: nil, options: [])
        player.play()
        #if DEBUG
          let startedAt = ProcessInfo.processInfo.systemUptime
          Task { @MainActor in
            HancoAudioDebugProbe.shared.recordEffectPlaybackStart(
              requestedAt: requestedAt,
              startedAt: startedAt
            )
          }
        #endif
        self.scheduleIdleShutdown()
      } catch {
        self.recoverFromStartFailure()
      }
    }
  }

  func prepareForInputFeedback(currentCombo: Int) {
    audioQueue.async { [weak self] in
      guard let self, self.isEnabled else { return }
      do {
        self.cancelIdleShutdown()
        for combo in HancoSoundWarmupPolicy.completionCombosToPrepare(after: currentCombo) {
          _ = self.buffer(for: .completion(combo: combo))
        }
        _ = self.buffer(for: .mistake)
        guard try self.startIfNeeded() else { return }
        self.scheduleIdleShutdown()
      } catch {
        self.recoverFromStartFailure()
      }
    }
  }

  private func handleAudioSessionEvent(_ event: HancoAudioSessionEvent) {
    audioQueue.async { [weak self] in
      guard let self else { return }
      switch event {
      case .interruptionBegan, .mediaServicesLost:
        self.shouldResumeAfterSystemRecovery = self.engine.isRunning
        self.cancelIdleShutdown()
        self.stopAllPlayers()
        self.engine.pause()

      case .interruptionEnded(let shouldResume):
        let resumes = shouldResume && self.shouldResumeAfterSystemRecovery && self.isEnabled
        self.shouldResumeAfterSystemRecovery = false
        if resumes, (try? self.startIfNeeded()) == true {
          self.scheduleIdleShutdown()
        }

      case .routeChanged:
        let wasRunning = self.engine.isRunning
        self.stopAllPlayers()
        self.engine.pause()
        if wasRunning, self.isEnabled, (try? self.startIfNeeded()) == true {
          self.scheduleIdleShutdown()
        }

      case .mediaServicesReset:
        let resumes = (self.shouldResumeAfterSystemRecovery || self.engine.isRunning)
          && self.isEnabled
        self.rebuildAudioGraph()
        self.shouldResumeAfterSystemRecovery = false
        if resumes, (try? self.startIfNeeded()) == true {
          self.scheduleIdleShutdown()
        }

      case .pronunciationEnded:
        if self.isEnabled {
          do {
            if try self.startIfNeeded() {
              self.scheduleIdleShutdown()
            }
          } catch {
            self.recoverFromStartFailure()
          }
        } else {
          HancoAudioSessionController.shared.deactivateSoundEffectsIfPossible()
        }
      }
    }
  }

  private func recoverFromStartFailure() {
    cancelIdleShutdown()
    stopAllPlayers()
    engine.pause()
    HancoAudioSessionController.shared.deactivateSoundEffectsIfPossible()
  }

  private func startIfNeeded() throws -> Bool {
    guard try HancoAudioSessionController.shared.prepareForSoundEffects() else {
      return false
    }

    if !graphIsConnected {
      for player in typingPlayers + feedbackPlayers {
        engine.attach(player)
        engine.connect(player, to: engine.mainMixerNode, format: format)
      }
      graphIsConnected = true
    }

    guard !engine.isRunning else { return true }
    engine.prepare()
    try engine.start()
    return true
  }

  private func buffer(for event: HancoSoundEvent) -> AVAudioPCMBuffer {
    switch event {
    case .keyTap(let preset, let role):
      let variationIndex = nextTypingVariationIndex
      nextTypingVariationIndex = (nextTypingVariationIndex + 1)
        % HancoTypingSoundTuning.variationCount
      let key = CacheKey.typing(preset, role, variationIndex)
      if let cached = buffers[key] { return cached }

      let source: AVAudioPCMBuffer
      if let cachedSource = typingSourceBuffers[preset] {
        source = cachedSource
      } else {
        source = render(HancoSoundPlanner.plan(for: .keyTap(preset, .character)))
        typingSourceBuffers[preset] = source
      }
      let profile = HancoTypingSoundTuning.profile(
        preset: preset,
        role: role,
        variationIndex: variationIndex
      )
      let transformed = HancoAudioBufferTransformer.typingVariant(
        from: source,
        profile: profile
      ) ?? render(HancoSoundPlanner.plan(for: .keyTap(preset, role)))
      buffers[key] = transformed
      return transformed

    case .completion(let combo):
      let key = CacheKey.completion(min(max(combo, 0), 20))
      return cachedOrRenderedBuffer(for: key, event: event)
    case .mistake:
      return cachedOrRenderedBuffer(for: .mistake, event: event)
    case .lifeLost:
      return cachedOrRenderedBuffer(for: .lifeLost, event: event)
    case .eggKnock:
      return cachedOrRenderedBuffer(for: .eggKnock, event: event)
    }
  }

  private func cachedOrRenderedBuffer(
    for key: CacheKey,
    event: HancoSoundEvent
  ) -> AVAudioPCMBuffer {
    if let cached = buffers[key] { return cached }
    let rendered = render(HancoSoundPlanner.plan(for: event))
    buffers[key] = rendered
    return rendered
  }

  private func player(for lane: HancoSoundPlaybackLane) -> AVAudioPlayerNode {
    switch lane {
    case .typing:
      return Self.nextAvailablePlayer(
        from: typingPlayers,
        cursor: &nextTypingPlayerIndex
      )
    case .feedback:
      return Self.nextAvailablePlayer(
        from: feedbackPlayers,
        cursor: &nextFeedbackPlayerIndex
      )
    }
  }

  private static func nextAvailablePlayer(
    from players: [AVAudioPlayerNode],
    cursor: inout Int
  ) -> AVAudioPlayerNode {
    for offset in 0..<players.count {
      let index = (cursor + offset) % players.count
      if !players[index].isPlaying {
        cursor = (index + 1) % players.count
        return players[index]
      }
    }
    let player = players[cursor]
    cursor = (cursor + 1) % players.count
    return player
  }

  private static func makePlayers(count: Int) -> [AVAudioPlayerNode] {
    (0..<count).map { _ in AVAudioPlayerNode() }
  }

  private func stopAllPlayers() {
    (typingPlayers + feedbackPlayers).forEach { $0.stop() }
  }

  private func rebuildAudioGraph() {
    cancelIdleShutdown()
    engine.stop()
    engine = AVAudioEngine()
    typingPlayers = Self.makePlayers(count: HancoSoundPlaybackPolicy.typingPlayerCount)
    feedbackPlayers = Self.makePlayers(count: HancoSoundPlaybackPolicy.feedbackPlayerCount)
    nextTypingPlayerIndex = 0
    nextFeedbackPlayerIndex = 0
    graphIsConnected = false
  }

  private func scheduleIdleShutdown() {
    cancelIdleShutdown()
    let workItem = DispatchWorkItem { [weak self] in
      guard let self else { return }
      self.stopAllPlayers()
      self.engine.pause()
      HancoAudioSessionController.shared.deactivateSoundEffectsIfPossible()
      self.idleShutdownWorkItem = nil
    }
    idleShutdownWorkItem = workItem
    audioQueue.asyncAfter(
      deadline: .now() + HancoSoundPlaybackPolicy.idleShutdownDelay,
      execute: workItem
    )
  }

  private func cancelIdleShutdown() {
    idleShutdownWorkItem?.cancel()
    idleShutdownWorkItem = nil
  }

  private func loadDefaultTypingSound(bundle: Bundle = .main) -> AVAudioPCMBuffer? {
    guard let url = HancoBundledSoundAsset.defaultTypingSoundURL(in: bundle),
      let file = try? AVAudioFile(forReading: url),
      file.processingFormat.channelCount == format.channelCount,
      file.processingFormat.sampleRate == format.sampleRate,
      file.length > 0,
      let buffer = AVAudioPCMBuffer(
        pcmFormat: file.processingFormat,
        frameCapacity: AVAudioFrameCount(file.length)
      )
    else { return nil }

    do {
      try file.read(into: buffer)
      return buffer.frameLength > 0 ? buffer : nil
    } catch {
      return nil
    }
  }

  private func render(_ plan: SoundPlan) -> AVAudioPCMBuffer {
    let frameCount = max(1, AVAudioFrameCount(ceil(plan.duration * format.sampleRate)))
    guard let buffer = AVAudioPCMBuffer(pcmFormat: format, frameCapacity: frameCount),
      let channel = buffer.floatChannelData?[0]
    else {
      preconditionFailure("Unable to allocate the sound effect buffer")
    }
    buffer.frameLength = frameCount

    for frame in 0..<Int(frameCount) {
      let time = Double(frame) / format.sampleRate
      var sample = 0.0
      for (componentIndex, component) in plan.components.enumerated() {
        sample += componentSample(
          component,
          at: time,
          frame: frame,
          componentIndex: componentIndex
        )
      }
      channel[frame] = Float(min(max(sample, -0.9), 0.9))
    }
    return buffer
  }

  private func componentSample(
    _ component: SoundComponent,
    at time: TimeInterval,
    frame: Int,
    componentIndex: Int
  ) -> Double {
    let localTime = time - component.startTime
    guard localTime >= 0, localTime < component.duration else { return 0 }

    let attackEnvelope =
      component.attack > 0 ? min(localTime / component.attack, 1) : 1
    let remaining = component.duration - localTime
    let releaseEnvelope =
      component.release > 0 ? min(remaining / component.release, 1) : 1
    let envelope = max(0, min(attackEnvelope, releaseEnvelope))

    let waveform: Double
    switch component.waveform {
    case .sine, .triangle:
      let frequencyDelta = component.endFrequency - component.startFrequency
      let phase = 2 * Double.pi
        * (component.startFrequency * localTime
          + 0.5 * frequencyDelta / component.duration * localTime * localTime)
      let sine = sin(phase)
      waveform = component.waveform == .sine ? sine : (2 / Double.pi) * asin(sine)

    case .noise:
      var hash = UInt32(truncatingIfNeeded: frame &* 1_103_515_245)
      hash &+= UInt32(truncatingIfNeeded: (componentIndex + 1) &* 12_345)
      hash ^= hash >> 16
      waveform = Double(hash & 0xFFFF) / Double(UInt16.max) * 2 - 1
    }

    return waveform * component.amplitude * envelope
  }
}
