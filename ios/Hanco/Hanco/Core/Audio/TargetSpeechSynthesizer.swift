import AVFoundation
import Combine
import CryptoKit
import Foundation

enum PronunciationAudioBackend {
  case explicitBundled
  case canonicalBundled
  case synthesized
}

enum BundledPronunciationAudio {
  static func relativePath(for target: String) -> String {
    let digest = SHA256.hash(data: Data(target.utf8))
    let prefix = digest.prefix(10).map { String(format: "%02x", Int($0)) }.joined()
    return "audio/ko_\(prefix).mp3"
  }

  static func url(
    for target: String,
    explicitRelativePath: String? = nil,
    in bundle: Bundle = .main
  ) -> URL? {
    candidateURLs(
      for: target,
      explicitRelativePath: explicitRelativePath,
      in: bundle
    ).first
  }

  static func candidateURLs(
    for target: String,
    explicitRelativePath: String? = nil,
    in bundle: Bundle = .main
  ) -> [URL] {
    candidateEntries(for: target, explicitRelativePath: explicitRelativePath, in: bundle).map(\.url)
  }

  static func candidateEntries(
    for target: String,
    explicitRelativePath: String? = nil,
    in bundle: Bundle = .main
  ) -> [(url: URL, backend: PronunciationAudioBackend)] {
    var seenPaths = Set<String>()
    var entries: [(url: URL, backend: PronunciationAudioBackend)] = []

    if let explicitURL = url(forRelativePath: explicitRelativePath, in: bundle),
      seenPaths.insert(explicitURL.path).inserted
    {
      entries.append((explicitURL, .explicitBundled))
    }
    let canonicalPath = relativePath(for: target)
    if let canonicalURL = url(forRelativePath: canonicalPath, in: bundle),
      seenPaths.insert(canonicalURL.path).inserted
    {
      entries.append((canonicalURL, .canonicalBundled))
    }
    return entries
  }

  static func url(forRelativePath relativePath: String?, in bundle: Bundle = .main) -> URL? {
    guard let relativePath, !relativePath.isEmpty,
      !relativePath.hasPrefix("/"),
      !relativePath.split(separator: "/").contains(".."),
      let resourceRoot = bundle.resourceURL
    else { return nil }

    let root = resourceRoot.standardizedFileURL
    let candidate = root.appendingPathComponent(relativePath).standardizedFileURL
    guard candidate.path.hasPrefix(root.path + "/"),
      FileManager.default.fileExists(atPath: candidate.path)
    else { return nil }
    return candidate
  }
}

@MainActor
final class TargetSpeechSynthesizer: NSObject, ObservableObject,
  @preconcurrency AVAudioPlayerDelegate, @preconcurrency AVSpeechSynthesizerDelegate
{
  private let synthesizer = AVSpeechSynthesizer()
  private var audioPlayer: AVAudioPlayer?
  private var pendingBundledCandidates: [(url: URL, backend: PronunciationAudioBackend)] = []
  private var activeUtterance: AVSpeechUtterance?
  private var fallbackTarget: String?
  private var audioSessionToken: UUID?

  override init() {
    super.init()
    synthesizer.usesApplicationAudioSession = true
    synthesizer.delegate = self
  }

  func speak(_ target: String, bundledAudioPath: String? = nil) {
    guard !target.isEmpty else { return }
    stop()

    HancoSoundEngine.shared.suspendForPronunciationPlayback()
    audioSessionToken = HancoAudioSessionController.shared.beginPronunciationPlayback()
    fallbackTarget = target

    pendingBundledCandidates = BundledPronunciationAudio.candidateEntries(
      for: target,
      explicitRelativePath: bundledAudioPath
    )

    playNextBundledCandidateOrSynthesize()
  }

  private func playNextBundledCandidateOrSynthesize() {
    while !pendingBundledCandidates.isEmpty {
      let candidate = pendingBundledCandidates.removeFirst()
      guard let player = try? AVAudioPlayer(contentsOf: candidate.url) else { continue }
      audioPlayer = player
      player.delegate = self
      player.prepareToPlay()
      if player.play() {
        #if DEBUG
          HancoAudioDebugProbe.shared.recordPronunciationBackend(mapBackend(candidate.backend))
        #endif
        return
      }
      audioPlayer = nil
    }

    guard let fallbackTarget else {
      finishPronunciationPlayback()
      return
    }
    #if DEBUG
      HancoAudioDebugProbe.shared.recordPronunciationBackend(.synthesized)
    #endif
    speakSynthesized(fallbackTarget)
  }

  private func speakSynthesized(_ target: String) {
    let utterance = AVSpeechUtterance(string: target)
    utterance.voice = AVSpeechSynthesisVoice(language: "ko-KR")
    utterance.rate = 0.42
    utterance.pitchMultiplier = 1
    activeUtterance = utterance
    synthesizer.speak(utterance)
  }

  func stop() {
    let player = audioPlayer
    audioPlayer = nil
    player?.stop()

    activeUtterance = nil
    synthesizer.stopSpeaking(at: .immediate)
    pendingBundledCandidates.removeAll(keepingCapacity: true)
    fallbackTarget = nil
    finishPronunciationPlayback()
  }

  func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
    guard player === audioPlayer else { return }
    audioPlayer = nil

    if flag {
      pendingBundledCandidates.removeAll(keepingCapacity: true)
      fallbackTarget = nil
      finishPronunciationPlayback()
    } else {
      playNextBundledCandidateOrSynthesize()
    }
  }

  func audioPlayerDecodeErrorDidOccur(_ player: AVAudioPlayer, error: Error?) {
    guard player === audioPlayer else { return }
    audioPlayer = nil
    playNextBundledCandidateOrSynthesize()
  }

  func speechSynthesizer(
    _ synthesizer: AVSpeechSynthesizer,
    didFinish utterance: AVSpeechUtterance
  ) {
    finishSpeechUtteranceIfCurrent(utterance)
  }

  func speechSynthesizer(
    _ synthesizer: AVSpeechSynthesizer,
    didCancel utterance: AVSpeechUtterance
  ) {
    finishSpeechUtteranceIfCurrent(utterance)
  }

  private func finishSpeechUtteranceIfCurrent(_ utterance: AVSpeechUtterance) {
    guard utterance === activeUtterance else { return }
    activeUtterance = nil
    pendingBundledCandidates.removeAll(keepingCapacity: true)
    fallbackTarget = nil
    finishPronunciationPlayback()
  }

  #if DEBUG
    private func mapBackend(
      _ backend: PronunciationAudioBackend
    ) -> HancoAudioDebugProbe.PronunciationBackend {
      switch backend {
      case .explicitBundled:
        return .explicitBundled
      case .canonicalBundled:
        return .canonicalBundled
      case .synthesized:
        return .synthesized
      }
    }
  #endif

  private func finishPronunciationPlayback() {
    guard let token = audioSessionToken else { return }
    audioSessionToken = nil
    HancoAudioSessionController.shared.endPronunciationPlayback(token)
  }
}
