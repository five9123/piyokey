import AVFoundation
import XCTest
@testable import Hanco

final class HancoSoundEngineTests: XCTestCase {
  func testSoundEffectsIgnoreSilentModeWithoutInterruptingOtherAudio() {
    XCTAssertEqual(HancoAudioSessionPolicy.soundEffects.category, .playback)
    XCTAssertEqual(HancoAudioSessionPolicy.soundEffects.mode, .default)
    XCTAssertTrue(HancoAudioSessionPolicy.soundEffects.options.contains(.mixWithOthers))
  }

  func testPronunciationIgnoresSilentModeWithoutInterruptingOtherAudio() {
    XCTAssertEqual(HancoAudioSessionPolicy.pronunciation.category, .playback)
    XCTAssertEqual(HancoAudioSessionPolicy.pronunciation.mode, .spokenAudio)
    XCTAssertTrue(HancoAudioSessionPolicy.pronunciation.options.contains(.mixWithOthers))
  }

  func testBundledPronunciationUsesCanonicalSHA256MP3Path() {
    XCTAssertEqual(
      BundledPronunciationAudio.relativePath(for: "나"),
      "audio/ko_1098bbe54520ab2a367c.mp3"
    )
  }

  func testBundledPronunciationCandidatesPreferExplicitPathThenCanonicalAndDeduplicate() throws {
    let explicitPath = BundledPronunciationAudio.relativePath(for: "가")
    let canonicalPath = BundledPronunciationAudio.relativePath(for: "나")
    let candidates = BundledPronunciationAudio.candidateURLs(
      for: "나",
      explicitRelativePath: explicitPath
    )

    XCTAssertEqual(
      candidates.map(\.lastPathComponent),
      [
        URL(fileURLWithPath: explicitPath).lastPathComponent,
        URL(fileURLWithPath: canonicalPath).lastPathComponent,
      ]
    )

    let duplicateCandidates = BundledPronunciationAudio.candidateURLs(
      for: "나",
      explicitRelativePath: canonicalPath
    )
    XCTAssertEqual(duplicateCandidates.count, 1)
    XCTAssertEqual(
      duplicateCandidates.first,
      try XCTUnwrap(BundledPronunciationAudio.url(for: "나"))
    )
  }

  func testBundledPronunciationCandidatesIgnoreUnsafeAndMissingExplicitPaths() throws {
    let canonicalURL = try XCTUnwrap(BundledPronunciationAudio.url(for: "나"))
    for invalidPath in [
      "/tmp/voice.mp3",
      "../voice.mp3",
      "audio/../voice.mp3",
      "audio/ko_missing.mp3",
    ] {
      XCTAssertEqual(
        BundledPronunciationAudio.candidateURLs(
          for: "나",
          explicitRelativePath: invalidPath
        ),
        [canonicalURL],
        invalidPath
      )
    }

    XCTAssertTrue(
      BundledPronunciationAudio.candidateURLs(
        for: "__missing_pronunciation_fixture__",
        explicitRelativePath: "audio/ko_missing.mp3"
      ).isEmpty
    )
  }

  func testTypingPresetsHaveDistinctSoundProfiles() {
    let systemFallback = HancoSoundPlanner.plan(for: .keyTap(.system, .character))
    let mechanical = HancoSoundPlanner.plan(for: .keyTap(.mechanical, .character))
    let soft = HancoSoundPlanner.plan(for: .keyTap(.soft, .character))

    XCTAssertTrue(systemFallback.components.contains { $0.waveform == .noise })
    XCTAssertTrue(mechanical.components.contains { $0.waveform == .noise })
    XCTAssertFalse(soft.components.contains { $0.waveform == .noise })
    XCTAssertLessThan(systemFallback.duration, mechanical.duration)
    XCTAssertLessThan(mechanical.duration, soft.duration)
    XCTAssertNotEqual(systemFallback, mechanical)
    XCTAssertNotEqual(mechanical, soft)
  }

  func testDefaultTypingFallbackIsBriefAndCrisp() throws {
    let plan = HancoSoundPlanner.plan(for: .keyTap(.system, .character))
    let tone = try XCTUnwrap(plan.components.first { $0.waveform == .triangle })

    XCTAssertLessThanOrEqual(plan.duration, 0.022)
    XCTAssertGreaterThanOrEqual(tone.startFrequency, 1_900)
    XCTAssertLessThanOrEqual(tone.startFrequency, 2_300)
    XCTAssertLessThan(abs(tone.startFrequency - tone.endFrequency), 300)
  }

  func testDefaultTypingAssetIsBundledAsNineteenMillisecondMonoPCM() throws {
    let url = try XCTUnwrap(HancoBundledSoundAsset.defaultTypingSoundURL())
    let file = try AVAudioFile(forReading: url)

    XCTAssertEqual(file.processingFormat.channelCount, 1)
    XCTAssertEqual(file.processingFormat.sampleRate, 48_000)
    XCTAssertEqual(file.length, 918)
    XCTAssertEqual(
      Double(file.length) / file.processingFormat.sampleRate,
      0.019125,
      accuracy: 0.000_001
    )
  }

  func testDefaultTypingVariantHasComfortableHeadroomAndPeakLimit() throws {
    let url = try XCTUnwrap(HancoBundledSoundAsset.defaultTypingSoundURL())
    let file = try AVAudioFile(forReading: url)
    let source = try XCTUnwrap(
      AVAudioPCMBuffer(
        pcmFormat: file.processingFormat,
        frameCapacity: AVAudioFrameCount(file.length)
      )
    )
    try file.read(into: source)
    let profile = HancoTypingSoundTuning.profile(
      preset: .system,
      role: .character,
      variationIndex: 1
    )
    let variant = try XCTUnwrap(
      HancoAudioBufferTransformer.typingVariant(from: source, profile: profile)
    )

    let sourcePeak = try peakAmplitude(of: source)
    let variantPeak = try peakAmplitude(of: variant)
    XCTAssertGreaterThan(sourcePeak, 0.9)
    XCTAssertLessThan(variantPeak, 0.4)
    XCTAssertLessThanOrEqual(variantPeak, profile.peakLimit)
  }

  func testTypingVariationsAndSpecialKeysStayDistinct() {
    let variants = (0..<HancoTypingSoundTuning.variationCount).map {
      HancoTypingSoundTuning.profile(
        preset: .system,
        role: .character,
        variationIndex: $0
      )
    }
    XCTAssertEqual(Set(variants.map(\.playbackRate)).count, 3)
    XCTAssertEqual(Set(variants.map(\.gain)).count, 3)

    let character = HancoTypingSoundTuning.profile(
      preset: .system,
      role: .character,
      variationIndex: 1
    )
    let backspace = HancoTypingSoundTuning.profile(
      preset: .system,
      role: .backspace,
      variationIndex: 1
    )
    let shift = HancoTypingSoundTuning.profile(
      preset: .system,
      role: .shift,
      variationIndex: 1
    )
    XCTAssertLessThan(backspace.playbackRate, character.playbackRate)
    XCTAssertLessThan(backspace.gain, character.gain)
    XCTAssertGreaterThan(shift.playbackRate, character.playbackRate)
    XCTAssertLessThan(shift.gain, backspace.gain)
  }

  func testTypingAndFeedbackUseIndependentPlayerPools() {
    XCTAssertEqual(
      HancoSoundPlaybackPolicy.lane(for: .keyTap(.system, .character)),
      .typing
    )
    XCTAssertEqual(HancoSoundPlaybackPolicy.lane(for: .completion(combo: 10)), .feedback)
    XCTAssertEqual(HancoSoundPlaybackPolicy.lane(for: .mistake), .feedback)
    XCTAssertEqual(HancoSoundPlaybackPolicy.lane(for: .lifeLost), .feedback)
    XCTAssertGreaterThan(
      HancoSoundPlaybackPolicy.typingPlayerCount,
      HancoSoundPlaybackPolicy.feedbackPlayerCount
    )
  }

  func testAudioRouteRecoveryCoversDeviceChangesButIgnoresCategoryChanges() {
    XCTAssertTrue(
      HancoAudioSessionController.routeChangeRequiresRecovery(.newDeviceAvailable)
    )
    XCTAssertTrue(
      HancoAudioSessionController.routeChangeRequiresRecovery(.oldDeviceUnavailable)
    )
    XCTAssertTrue(
      HancoAudioSessionController.routeChangeRequiresRecovery(.routeConfigurationChange)
    )
    XCTAssertFalse(
      HancoAudioSessionController.routeChangeRequiresRecovery(.categoryChange)
    )
  }

  func testSoundEngineUsesDeferredIdleShutdown() {
    XCTAssertGreaterThanOrEqual(HancoSoundPlaybackPolicy.idleShutdownDelay, 10)
    XCTAssertLessThanOrEqual(HancoSoundPlaybackPolicy.idleShutdownDelay, 30)
  }

  func testOSIMEWarmupStartsOnlyAfterRealTextActivity() {
    XCTAssertFalse(
      HancoSoundWarmupPolicy.shouldPrepareForOSIMEInput(
        committedText: "",
        markedText: nil
      )
    )
    XCTAssertTrue(
      HancoSoundWarmupPolicy.shouldPrepareForOSIMEInput(
        committedText: "ㄱ",
        markedText: nil
      )
    )
    XCTAssertTrue(
      HancoSoundWarmupPolicy.shouldPrepareForOSIMEInput(
        committedText: "",
        markedText: "ㄱ"
      )
    )
    XCTAssertEqual(HancoSoundWarmupPolicy.completionCombosToPrepare(after: -1), [0, 1])
    XCTAssertEqual(HancoSoundWarmupPolicy.completionCombosToPrepare(after: 8), [8, 9])
    XCTAssertEqual(HancoSoundWarmupPolicy.completionCombosToPrepare(after: 20), [20])
  }

  func testCompletionPitchRisesWithComboAndCapsAtTwenty() throws {
    let base = try XCTUnwrap(
      HancoSoundPlanner.plan(for: .completion(combo: 0)).components.first
    )
    let five = try XCTUnwrap(
      HancoSoundPlanner.plan(for: .completion(combo: 5)).components.first
    )
    let ten = try XCTUnwrap(
      HancoSoundPlanner.plan(for: .completion(combo: 10)).components.first
    )
    let twenty = try XCTUnwrap(
      HancoSoundPlanner.plan(for: .completion(combo: 20)).components.first
    )
    let aboveCap = try XCTUnwrap(
      HancoSoundPlanner.plan(for: .completion(combo: 99)).components.first
    )

    XCTAssertLessThan(base.startFrequency, five.startFrequency)
    XCTAssertLessThan(five.startFrequency, ten.startFrequency)
    XCTAssertLessThan(ten.startFrequency, twenty.startFrequency)
    XCTAssertEqual(twenty.startFrequency, aboveCap.startFrequency, accuracy: 0.001)
  }

  func testMistakeToneIsBriefLowAndGentle() throws {
    let plan = HancoSoundPlanner.plan(for: .mistake)
    let tone = try XCTUnwrap(plan.components.first)

    XCTAssertEqual(plan.components.count, 1)
    XCTAssertLessThanOrEqual(tone.startFrequency, 220)
    XCTAssertLessThan(tone.endFrequency, tone.startFrequency)
    XCTAssertLessThanOrEqual(tone.amplitude, 0.12)
    XCTAssertLessThanOrEqual(plan.duration, 0.15)
  }

  func testLifeLostCueIsStrongerAndMoreDistinctThanTypingMistake() throws {
    let mistake = HancoSoundPlanner.plan(for: .mistake)
    let lifeLost = HancoSoundPlanner.plan(for: .lifeLost)
    let fallingTone = try XCTUnwrap(lifeLost.components.first)

    XCTAssertEqual(lifeLost.components.count, 2)
    XCTAssertTrue(lifeLost.components.contains { $0.waveform == .noise })
    XCTAssertGreaterThan(lifeLost.duration, mistake.duration)
    XCTAssertGreaterThan(fallingTone.startFrequency, fallingTone.endFrequency)
    XCTAssertGreaterThan(
      lifeLost.components.map(\.amplitude).max() ?? 0,
      mistake.components.map(\.amplitude).max() ?? 0
    )
  }

  func testUnknownTypingPresetFallsBackToSystem() {
    XCTAssertEqual(TypingSoundPreset.resolved(from: "unknown"), .system)
    XCTAssertEqual(TypingSoundPreset.resolved(from: "system"), .system)
    XCTAssertEqual(TypingSoundPreset.resolved(from: "mechanical"), .mechanical)
    XCTAssertEqual(TypingSoundPreset.resolved(from: "soft"), .soft)
  }

  func testEggKnockIsAQuietTwoTapCue() {
    let plan = HancoSoundPlanner.plan(for: .eggKnock)

    XCTAssertEqual(plan.components.count, 2)
    XCTAssertTrue(plan.components.allSatisfy { $0.waveform == .triangle })
    XCTAssertTrue(plan.components.allSatisfy { $0.amplitude <= 0.10 })
    XCTAssertGreaterThan(plan.components[1].startTime, plan.components[0].startTime)
    XCTAssertLessThanOrEqual(plan.duration, 0.25)
  }

  private func peakAmplitude(of buffer: AVAudioPCMBuffer) throws -> Float {
    let channel = try XCTUnwrap(buffer.floatChannelData?[0])
    var peak: Float = 0
    for frame in 0..<Int(buffer.frameLength) {
      peak = max(peak, abs(channel[frame]))
    }
    return peak
  }
}
