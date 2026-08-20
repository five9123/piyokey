#!/usr/bin/env swift

import AVFoundation
import AudioToolbox
import Foundation

private enum PreviewAudioError: Error, CustomStringConvertible {
  case usage
  case missingVideoTrack
  case couldNotCreateCompositionTrack
  case couldNotCreateExporter
  case readerFailed(String)
  case writerFailed(String)
  case exportFailed(String)

  var description: String {
    switch self {
    case .usage:
      return "Usage: add_silent_app_preview_audio.swift <input.mp4> <output.mp4>"
    case .missingVideoTrack:
      return "The input has no video track."
    case .couldNotCreateCompositionTrack:
      return "Could not create the temporary composition tracks."
    case .couldNotCreateExporter:
      return "Could not create the App Preview exporter."
    case .readerFailed(let message):
      return "Audio reader failed: \(message)"
    case .writerFailed(let message):
      return "Audio writer failed: \(message)"
    case .exportFailed(let message):
      return "Export failed: \(message)"
    }
  }
}

private func encodeConstantBitRateAAC(inputURL: URL, outputURL: URL) throws {
  try? FileManager.default.removeItem(at: outputURL)
  let asset = AVURLAsset(url: inputURL)
  guard let track = asset.tracks(withMediaType: .audio).first else {
    throw PreviewAudioError.readerFailed("The generated PCM file has no audio track")
  }

  let reader = try AVAssetReader(asset: asset)
  let output = AVAssetReaderTrackOutput(
    track: track,
    outputSettings: [
      AVFormatIDKey: kAudioFormatLinearPCM,
      AVLinearPCMBitDepthKey: 32,
      AVLinearPCMIsFloatKey: true,
      AVLinearPCMIsBigEndianKey: false,
      AVLinearPCMIsNonInterleaved: false,
    ]
  )
  guard reader.canAdd(output) else {
    throw PreviewAudioError.readerFailed("Could not add the PCM reader output")
  }
  reader.add(output)

  let writer = try AVAssetWriter(outputURL: outputURL, fileType: .m4a)
  var channelLayout = AudioChannelLayout()
  channelLayout.mChannelLayoutTag = kAudioChannelLayoutTag_Stereo
  let channelLayoutData = Data(
    bytes: &channelLayout,
    count: MemoryLayout<AudioChannelLayout>.size
  )
  let input = AVAssetWriterInput(
    mediaType: .audio,
    outputSettings: [
      AVFormatIDKey: kAudioFormatMPEG4AAC,
      AVSampleRateKey: sampleRate,
      AVNumberOfChannelsKey: Int(channelCount),
      AVEncoderBitRateKey: 256_000,
      AVEncoderBitRateStrategyKey: AVAudioBitRateStrategy_Constant,
      AVChannelLayoutKey: channelLayoutData,
    ]
  )
  input.expectsMediaDataInRealTime = false
  guard writer.canAdd(input) else {
    throw PreviewAudioError.writerFailed("Could not add the AAC writer input")
  }
  writer.add(input)
  writer.shouldOptimizeForNetworkUse = true

  guard writer.startWriting() else {
    throw PreviewAudioError.writerFailed(writer.error?.localizedDescription ?? "startWriting failed")
  }
  guard reader.startReading() else {
    throw PreviewAudioError.readerFailed(reader.error?.localizedDescription ?? "startReading failed")
  }
  writer.startSession(atSourceTime: .zero)

  let queue = DispatchQueue(label: "app-preview.silent-aac-writer")
  let finished = DispatchSemaphore(value: 0)
  var appendError: String?
  input.requestMediaDataWhenReady(on: queue) {
    while input.isReadyForMoreMediaData {
      guard let sample = output.copyNextSampleBuffer() else {
        input.markAsFinished()
        writer.finishWriting { finished.signal() }
        return
      }
      if !input.append(sample) {
        appendError = writer.error?.localizedDescription ?? "append failed"
        input.markAsFinished()
        reader.cancelReading()
        writer.cancelWriting()
        finished.signal()
        return
      }
    }
  }
  finished.wait()

  if let appendError {
    throw PreviewAudioError.writerFailed(appendError)
  }
  guard reader.status == .completed else {
    throw PreviewAudioError.readerFailed(reader.error?.localizedDescription ?? "decode failed")
  }
  guard writer.status == .completed else {
    throw PreviewAudioError.writerFailed(writer.error?.localizedDescription ?? "encode failed")
  }
}

private let sampleRate = 48_000.0
private let channelCount: AVAudioChannelCount = 2

private func writeSilentAudio(to url: URL, duration: CMTime) throws {
  try? FileManager.default.removeItem(at: url)
  guard let format = AVAudioFormat(
    commonFormat: .pcmFormatFloat32,
    sampleRate: sampleRate,
    channels: channelCount,
    interleaved: false
  ) else {
    throw PreviewAudioError.exportFailed("Could not create the silent audio format")
  }

  let audioFile = try AVAudioFile(forWriting: url, settings: format.settings)
  let totalFrames = AVAudioFramePosition(ceil(CMTimeGetSeconds(duration) * sampleRate))
  let blockFrames: AVAudioFrameCount = 4_800
  var framesWritten: AVAudioFramePosition = 0

  while framesWritten < totalFrames {
    let remaining = totalFrames - framesWritten
    let frameCount = AVAudioFrameCount(min(AVAudioFramePosition(blockFrames), remaining))
    guard let buffer = AVAudioPCMBuffer(pcmFormat: format, frameCapacity: frameCount) else {
      throw PreviewAudioError.exportFailed("Could not create a silent audio buffer")
    }
    buffer.frameLength = frameCount
    if let channels = buffer.floatChannelData {
      for channel in 0..<Int(channelCount) {
        channels[channel].initialize(repeating: 0, count: Int(frameCount))
      }
    }
    try audioFile.write(from: buffer)
    framesWritten += AVAudioFramePosition(frameCount)
  }
}

private func exportPreview(inputURL: URL, outputURL: URL) throws {
  let videoAsset = AVURLAsset(url: inputURL)
  guard let sourceVideoTrack = videoAsset.tracks(withMediaType: .video).first else {
    throw PreviewAudioError.missingVideoTrack
  }
  let duration = videoAsset.duration

  let temporaryAudioURL = FileManager.default.temporaryDirectory
    .appendingPathComponent("piyokey-app-preview-silence-\(UUID().uuidString).caf")
  let temporaryAACURL = FileManager.default.temporaryDirectory
    .appendingPathComponent("piyokey-app-preview-silence-\(UUID().uuidString).m4a")
  defer {
    try? FileManager.default.removeItem(at: temporaryAudioURL)
    try? FileManager.default.removeItem(at: temporaryAACURL)
  }
  try writeSilentAudio(to: temporaryAudioURL, duration: duration)
  try encodeConstantBitRateAAC(inputURL: temporaryAudioURL, outputURL: temporaryAACURL)

  let audioAsset = AVURLAsset(url: temporaryAACURL)
  guard let sourceAudioTrack = audioAsset.tracks(withMediaType: .audio).first else {
    throw PreviewAudioError.exportFailed("The generated silent audio has no track")
  }

  let composition = AVMutableComposition()
  guard
    let videoTrack = composition.addMutableTrack(
      withMediaType: .video,
      preferredTrackID: kCMPersistentTrackID_Invalid
    ),
    let audioTrack = composition.addMutableTrack(
      withMediaType: .audio,
      preferredTrackID: kCMPersistentTrackID_Invalid
    )
  else {
    throw PreviewAudioError.couldNotCreateCompositionTrack
  }

  let range = CMTimeRange(start: .zero, duration: duration)
  try videoTrack.insertTimeRange(range, of: sourceVideoTrack, at: .zero)
  try audioTrack.insertTimeRange(range, of: sourceAudioTrack, at: .zero)
  videoTrack.preferredTransform = sourceVideoTrack.preferredTransform

  try? FileManager.default.removeItem(at: outputURL)
  guard let exporter = AVAssetExportSession(
    asset: composition,
    presetName: AVAssetExportPresetPassthrough
  ) else {
    throw PreviewAudioError.couldNotCreateExporter
  }
  exporter.outputURL = outputURL
  exporter.outputFileType = .mp4
  exporter.timeRange = range
  exporter.shouldOptimizeForNetworkUse = true

  let semaphore = DispatchSemaphore(value: 0)
  exporter.exportAsynchronously { semaphore.signal() }
  semaphore.wait()

  guard exporter.status == .completed else {
    throw PreviewAudioError.exportFailed(exporter.error?.localizedDescription ?? "unknown error")
  }
}

do {
  let arguments = CommandLine.arguments
  guard arguments.count == 3 else { throw PreviewAudioError.usage }
  let inputURL = URL(fileURLWithPath: arguments[1])
  let outputURL = URL(fileURLWithPath: arguments[2])
  try exportPreview(inputURL: inputURL, outputURL: outputURL)

  let asset = AVURLAsset(url: outputURL)
  let duration = CMTimeGetSeconds(asset.duration)
  let audioTracks = asset.tracks(withMediaType: .audio).count
  print(String(format: "Wrote %@ (%.3fs, %d audio track)", outputURL.path, duration, audioTracks))
} catch {
  fputs("\(error)\n", stderr)
  exit(1)
}
