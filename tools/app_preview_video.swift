#!/usr/bin/env swift

import AppKit
import AVFoundation
import AudioToolbox
import CoreMedia
import Foundation

private enum AppPreviewVideoError: Error, CustomStringConvertible {
  case usage
  case missingVideoTrack
  case invalidDuration
  case couldNotCreateImage
  case couldNotCreateExporter
  case exportFailed(String)
  case validationFailed(String)

  var description: String {
    switch self {
    case .usage:
      return "Usage: app_preview_video.swift inspect <input> | validate <input> | contact-sheet <input> <output.png> | export <input> <output.mp4> <start-seconds> <duration-seconds>"
    case .missingVideoTrack: return "The input has no video track."
    case .invalidDuration: return "The requested duration must be between 15 and 30 seconds and fit inside the source."
    case .couldNotCreateImage: return "Could not render the contact sheet."
    case .couldNotCreateExporter: return "Could not create an AVAsset exporter."
    case .exportFailed(let message): return "Export failed: \(message)"
    case .validationFailed(let message): return "Validation failed: \(message)"
    }
  }
}

private func validateDecode(asset: AVAsset) throws {
  let info = try videoInfo(asset)
  guard info.duration >= 15, info.duration <= 30.1,
    info.size == targetSize, abs(info.track.nominalFrameRate - 30) < 0.01
  else {
    throw AppPreviewVideoError.validationFailed("Expected 15–30 seconds, 886×1920, 30 fps")
  }
  guard let format = info.track.formatDescriptions.first,
    CMFormatDescriptionGetMediaSubType(format as! CMFormatDescription) == kCMVideoCodecType_H264
  else { throw AppPreviewVideoError.validationFailed("Expected H.264 video") }
  let audio = asset.tracks(withMediaType: .audio)
  guard audio.count == 1, audio[0].isEnabled,
    let audioFormat = audio[0].formatDescriptions.first,
    let stream = CMAudioFormatDescriptionGetStreamBasicDescription(audioFormat as! CMAudioFormatDescription)?.pointee,
    stream.mFormatID == kAudioFormatMPEG4AAC,
    stream.mChannelsPerFrame == 2,
    stream.mSampleRate == 48_000 || stream.mSampleRate == 44_100
  else { throw AppPreviewVideoError.validationFailed("Expected enabled stereo AAC at 44.1/48 kHz") }
  print("video_codec=h264")
  print("audio_codec=aac")
  print("audio_channels=\(stream.mChannelsPerFrame)")
  print("audio_sample_rate=\(Int(stream.mSampleRate))")
  let reader = try AVAssetReader(asset: asset)
  let output = AVAssetReaderTrackOutput(
    track: info.track,
    outputSettings: [
      kCVPixelBufferPixelFormatTypeKey as String:
        kCVPixelFormatType_420YpCbCr8BiPlanarVideoRange
    ]
  )
  output.alwaysCopiesSampleData = false
  guard reader.canAdd(output) else {
    throw AppPreviewVideoError.validationFailed("Could not create decode output")
  }
  reader.add(output)
  guard reader.startReading() else {
    throw AppPreviewVideoError.validationFailed(
      reader.error?.localizedDescription ?? "Could not start decoding"
    )
  }

  var frameCount = 0
  var firstPTS: CMTime?
  var previousPTS: CMTime?
  var lastPTS: CMTime?
  while let sample = output.copyNextSampleBuffer() {
    let pts = CMSampleBufferGetPresentationTimeStamp(sample)
    if let previousPTS, CMTimeCompare(pts, previousPTS) <= 0 {
      throw AppPreviewVideoError.validationFailed(
        "Non-monotonic frame timestamp at frame \(frameCount)"
      )
    }
    firstPTS = firstPTS ?? pts
    previousPTS = pts
    lastPTS = pts
    frameCount += 1
  }
  guard reader.status == .completed else {
    throw AppPreviewVideoError.validationFailed(
      reader.error?.localizedDescription ?? "Decode status \(reader.status.rawValue)"
    )
  }
  guard frameCount > 0, let firstPTS, let lastPTS else {
    throw AppPreviewVideoError.validationFailed("No decoded frames")
  }

  let decodedSpan = CMTimeGetSeconds(lastPTS - firstPTS)
  let expectedFrames = Int(round(info.duration * 30))
  guard abs(frameCount - expectedFrames) <= 2 else {
    throw AppPreviewVideoError.validationFailed(
      "Expected about \(expectedFrames) frames, decoded \(frameCount)"
    )
  }
  print("decode_status=completed")
  print("decoded_frames=\(frameCount)")
  print(String(format: "decoded_span=%.3f", decodedSpan))
}

private let targetSize = CGSize(width: 886, height: 1_920)

private func videoInfo(_ asset: AVAsset) throws -> (track: AVAssetTrack, duration: Double, size: CGSize) {
  guard let track = asset.tracks(withMediaType: .video).first else {
    throw AppPreviewVideoError.missingVideoTrack
  }
  let duration = CMTimeGetSeconds(asset.duration)
  let transformedRect = CGRect(origin: .zero, size: track.naturalSize)
    .applying(track.preferredTransform)
    .standardized
  return (track, duration, transformedRect.size)
}

private func printInfo(url: URL, asset: AVAsset) throws {
  let info = try videoInfo(asset)
  let nominalFPS = info.track.nominalFrameRate
  let bitrate = Double(info.track.estimatedDataRate) / 1_000_000
  let audioTracks = asset.tracks(withMediaType: .audio).count
  print("path=\(url.path)")
  print(String(format: "duration=%.3f", info.duration))
  print("size=\(Int(info.size.width))x\(Int(info.size.height))")
  print(String(format: "nominal_fps=%.3f", nominalFPS))
  print(String(format: "estimated_mbps=%.3f", bitrate))
  print("audio_tracks=\(audioTracks)")
}

private func renderContactSheet(asset: AVAsset, outputURL: URL) throws {
  let info = try videoInfo(asset)
  let interval = 2.0
  let sampleTimes = stride(from: 0.0, through: info.duration, by: interval).map { value in
    CMTime(seconds: min(value, max(0, info.duration - 0.05)), preferredTimescale: 600)
  }
  let columns = 5
  let cellWidth = 180
  let cellHeight = Int(round(Double(cellWidth) * Double(info.size.height / info.size.width)))
  let labelHeight = 26
  let rows = Int(ceil(Double(sampleTimes.count) / Double(columns)))
  let canvasSize = NSSize(width: columns * cellWidth, height: rows * (cellHeight + labelHeight))

  let generator = AVAssetImageGenerator(asset: asset)
  generator.appliesPreferredTrackTransform = true
  generator.maximumSize = CGSize(width: cellWidth * 2, height: cellHeight * 2)
  generator.requestedTimeToleranceBefore = CMTime(seconds: 0.05, preferredTimescale: 600)
  generator.requestedTimeToleranceAfter = CMTime(seconds: 0.05, preferredTimescale: 600)

  let image = NSImage(size: canvasSize)
  image.lockFocus()
  NSColor.black.setFill()
  NSRect(origin: .zero, size: canvasSize).fill()

  let paragraph = NSMutableParagraphStyle()
  paragraph.alignment = .center
  let labelAttributes: [NSAttributedString.Key: Any] = [
    .font: NSFont.monospacedDigitSystemFont(ofSize: 15, weight: .semibold),
    .foregroundColor: NSColor.white,
    .paragraphStyle: paragraph,
  ]

  for (index, time) in sampleTimes.enumerated() {
    let actual = UnsafeMutablePointer<CMTime>.allocate(capacity: 1)
    defer { actual.deallocate() }
    let frame = try generator.copyCGImage(at: time, actualTime: actual)
    let column = index % columns
    let row = index / columns
    let x = column * cellWidth
    let y = Int(canvasSize.height) - ((row + 1) * (cellHeight + labelHeight))
    let frameRect = NSRect(x: x, y: y + labelHeight, width: cellWidth, height: cellHeight)
    NSImage(cgImage: frame, size: frameRect.size).draw(in: frameRect)

    let seconds = CMTimeGetSeconds(actual.pointee)
    let label = String(format: "%.1fs", seconds)
    (label as NSString).draw(
      in: NSRect(x: x, y: y + 3, width: cellWidth, height: labelHeight - 3),
      withAttributes: labelAttributes
    )
  }

  image.unlockFocus()
  guard
    let tiff = image.tiffRepresentation,
    let bitmap = NSBitmapImageRep(data: tiff),
    let png = bitmap.representation(using: .png, properties: [:])
  else {
    throw AppPreviewVideoError.couldNotCreateImage
  }
  try png.write(to: outputURL, options: .atomic)
}

private func exportPreview(
  asset: AVAsset,
  outputURL: URL,
  start: Double,
  duration: Double
) throws {
  let info = try videoInfo(asset)
  guard duration >= 15, duration <= 30, start >= 0, start + duration <= info.duration + 0.05 else {
    throw AppPreviewVideoError.invalidDuration
  }

  guard let exporter = AVAssetExportSession(
    asset: asset,
    presetName: AVAssetExportPresetHighestQuality
  ) else {
    throw AppPreviewVideoError.couldNotCreateExporter
  }

  let instruction = AVMutableVideoCompositionInstruction()
  instruction.timeRange = CMTimeRange(
    start: CMTime(seconds: start, preferredTimescale: 600),
    duration: CMTime(seconds: duration, preferredTimescale: 600)
  )
  let layerInstruction = AVMutableVideoCompositionLayerInstruction(assetTrack: info.track)

  let sourceRect = CGRect(origin: .zero, size: info.track.naturalSize)
    .applying(info.track.preferredTransform)
    .standardized
  let scale = max(targetSize.width / sourceRect.width, targetSize.height / sourceRect.height)
  let scaledSize = CGSize(width: sourceRect.width * scale, height: sourceRect.height * scale)
  let offset = CGPoint(
    x: (targetSize.width - scaledSize.width) / 2,
    y: (targetSize.height - scaledSize.height) / 2
  )

  var transform = info.track.preferredTransform
  transform = transform.concatenating(
    CGAffineTransform(translationX: -sourceRect.minX, y: -sourceRect.minY)
  )
  transform = transform.concatenating(CGAffineTransform(scaleX: scale, y: scale))
  transform = transform.concatenating(
    CGAffineTransform(translationX: offset.x, y: offset.y)
  )
  layerInstruction.setTransform(transform, at: .zero)
  instruction.layerInstructions = [layerInstruction]

  let composition = AVMutableVideoComposition()
  composition.renderSize = targetSize
  composition.frameDuration = CMTime(value: 1, timescale: 30)
  composition.instructions = [instruction]

  try? FileManager.default.removeItem(at: outputURL)
  exporter.outputURL = outputURL
  exporter.outputFileType = .mp4
  exporter.timeRange = CMTimeRange(
    start: CMTime(seconds: start, preferredTimescale: 600),
    duration: CMTime(seconds: duration, preferredTimescale: 600)
  )
  exporter.videoComposition = composition
  exporter.shouldOptimizeForNetworkUse = true

  let semaphore = DispatchSemaphore(value: 0)
  exporter.exportAsynchronously { semaphore.signal() }
  semaphore.wait()

  guard exporter.status == .completed else {
    throw AppPreviewVideoError.exportFailed(exporter.error?.localizedDescription ?? "unknown error")
  }
}

do {
  let arguments = CommandLine.arguments
  guard arguments.count >= 3 else { throw AppPreviewVideoError.usage }
  let command = arguments[1]
  let inputURL = URL(fileURLWithPath: arguments[2])
  let asset = AVURLAsset(url: inputURL)

  switch command {
  case "inspect":
    try printInfo(url: inputURL, asset: asset)
  case "validate":
    try printInfo(url: inputURL, asset: asset)
    try validateDecode(asset: asset)
  case "contact-sheet":
    guard arguments.count == 4 else { throw AppPreviewVideoError.usage }
    let outputURL = URL(fileURLWithPath: arguments[3])
    try renderContactSheet(asset: asset, outputURL: outputURL)
    print("Wrote \(outputURL.path)")
  case "export":
    guard arguments.count == 6,
      let start = Double(arguments[4]),
      let duration = Double(arguments[5])
    else { throw AppPreviewVideoError.usage }
    let outputURL = URL(fileURLWithPath: arguments[3])
    try exportPreview(asset: asset, outputURL: outputURL, start: start, duration: duration)
    try printInfo(url: outputURL, asset: AVURLAsset(url: outputURL))
  default:
    throw AppPreviewVideoError.usage
  }
} catch {
  fputs("\(error)\n", stderr)
  exit(1)
}
