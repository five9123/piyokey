#!/usr/bin/env swift

import AppKit
import AVFoundation
import CoreMedia
import CoreVideo
import Foundation
import QuartzCore

private enum ComposeError: Error, CustomStringConvertible {
  case usage
  case missingVideoTrack(String)
  case compositionTrack
  case reader(String)
  case writer(String)
  case append(String)

  var description: String {
    switch self {
    case .usage:
      return "Usage: compose_app_preview.swift <scene-directory> <output.mp4> [clips.json]"
    case .missingVideoTrack(let path):
      return "Missing video track: \(path)"
    case .compositionTrack:
      return "Could not create composition tracks."
    case .reader(let message):
      return "Reader failed: \(message)"
    case .writer(let message):
      return "Writer failed: \(message)"
    case .append(let message):
      return "Could not append video: \(message)"
    }
  }
}

private struct ClipSpec: Codable {
  let filename: String
  let start: Double
  let duration: Double
  let caption: String
  let detail: String?
  let captionAtBottom: Bool
}

private struct PlacedClip {
  let spec: ClipSpec
  let sourceTrack: AVAssetTrack
  let compositionTrack: AVMutableCompositionTrack
  let timeRange: CMTimeRange
  let transform: CGAffineTransform
}

private let targetSize = CGSize(width: 886, height: 1_920)
private let frameDuration = CMTime(value: 1, timescale: 30)
private let transitionDuration = CMTime(seconds: 0.25, preferredTimescale: 600)

private let clips: [ClipSpec] = [
  ClipSpec(
    filename: "practice.mp4",
    start: 28.1,
    duration: 6.25,
    caption: "韓国語を「読める」から「打てる」へ",
    detail: nil,
    captionAtBottom: false
  ),
  ClipSpec(
    filename: "game-hub.mp4",
    start: 11.55,
    duration: 2.85,
    caption: "6つのゲームで、楽しく練習",
    detail: nil,
    captionAtBottom: false
  ),
  ClipSpec(
    filename: "flow-combo.mp4",
    start: 21.05,
    duration: 7.45,
    caption: "打つほど、コンボが続く",
    detail: nil,
    captionAtBottom: false
  ),
  ClipSpec(
    filename: "acid-rain.mp4",
    start: 15.55,
    duration: 4.90,
    caption: "速さも、正確さも。",
    detail: nil,
    captionAtBottom: false
  ),
  ClipSpec(
    filename: "result.mp4",
    start: 30.65,
    duration: 3.85,
    caption: "上達が、ちゃんと見える",
    detail: nil,
    captionAtBottom: false
  ),
  ClipSpec(
    filename: "home-mascot.mp4",
    start: 0,
    duration: 3.00,
    caption: "ピヨキーで、最初の一文字から。",
    detail: "広告なし・登録なし・プロは任意購入",
    captionAtBottom: true
  ),
]

private func orientedTransform(for track: AVAssetTrack) -> CGAffineTransform {
  let naturalSize = track.naturalSize
  let preferred = track.preferredTransform
  let orientedRect = CGRect(origin: .zero, size: naturalSize)
    .applying(preferred)
    .standardized
  let scale = max(targetSize.width / orientedRect.width, targetSize.height / orientedRect.height)
  let scaledSize = CGSize(width: orientedRect.width * scale, height: orientedRect.height * scale)
  let offset = CGPoint(
    x: (targetSize.width - scaledSize.width) / 2,
    y: (targetSize.height - scaledSize.height) / 2
  )

  var transform = preferred
  transform = transform.concatenating(
    CGAffineTransform(translationX: -orientedRect.minX, y: -orientedRect.minY)
  )
  transform = transform.concatenating(CGAffineTransform(scaleX: scale, y: scale))
  transform = transform.concatenating(
    CGAffineTransform(translationX: offset.x, y: offset.y)
  )
  return transform
}

private func makeCaptionLayer(for clip: PlacedClip) -> CALayer {
  let hasDetail = clip.spec.detail != nil
  let panelHeight: CGFloat = hasDetail ? 182 : 106
  let panelSize = CGSize(width: targetSize.width - 96, height: panelHeight)
  let image = NSImage(size: panelSize)
  image.lockFocus()

  let shadow = NSShadow()
  shadow.shadowColor = NSColor.black.withAlphaComponent(0.20)
  shadow.shadowBlurRadius = 14
  shadow.shadowOffset = CGSize(width: 0, height: -3)
  shadow.set()
  NSColor(
    calibratedRed: 0.105,
    green: 0.090,
    blue: 0.175,
    alpha: 0.90
  ).setFill()
  NSBezierPath(
    roundedRect: CGRect(origin: .zero, size: panelSize).insetBy(dx: 8, dy: 8),
    xRadius: panelHeight / 2,
    yRadius: panelHeight / 2
  ).fill()

  let titleParagraph = NSMutableParagraphStyle()
  titleParagraph.alignment = .center
  var titleFontSize: CGFloat = hasDetail ? 39 : 42
  while (clip.spec.caption as NSString).size(withAttributes: [
    .font: NSFont.systemFont(ofSize: titleFontSize, weight: .bold)
  ]).width > panelSize.width - 64 {
    titleFontSize -= 1
    precondition(titleFontSize >= 26, "Caption too long: \(clip.spec.caption)")
  }
  let title = NSAttributedString(
    string: clip.spec.caption,
    attributes: [
      .font: NSFont.systemFont(ofSize: titleFontSize, weight: .bold),
      .foregroundColor: NSColor.white,
      .paragraphStyle: titleParagraph,
    ]
  )
  title.draw(
    in: CGRect(
      x: 24,
      y: hasDetail ? 104 : 25,
      width: panelSize.width - 48,
      height: 56
    )
  )

  if let detail = clip.spec.detail {
    let detailParagraph = NSMutableParagraphStyle()
    detailParagraph.alignment = .center
    NSAttributedString(
      string: detail,
      attributes: [
        .font: NSFont.systemFont(ofSize: 25, weight: .semibold),
        .foregroundColor: NSColor.white.withAlphaComponent(0.82),
        .paragraphStyle: detailParagraph,
      ]
    ).draw(in: CGRect(x: 28, y: 28, width: panelSize.width - 56, height: 70))
  }
  image.unlockFocus()

  var imageRect = CGRect(origin: .zero, size: panelSize)
  let captionImage = image.cgImage(forProposedRect: &imageRect, context: nil, hints: nil)
  let panel = CALayer()
  panel.frame = CGRect(
    x: 48,
    y: clip.spec.captionAtBottom ? 210 : targetSize.height - 370,
    width: panelSize.width,
    height: panelHeight
  )
  panel.contents = captionImage
  panel.contentsGravity = .resizeAspect
  panel.contentsScale = 1

  let duration = CMTimeGetSeconds(clip.timeRange.duration)
  let opacity = CAKeyframeAnimation(keyPath: "opacity")
  opacity.values = [0, 1, 1, 0]
  opacity.keyTimes = [0, 0.08, 0.90, 1]
  opacity.beginTime = AVCoreAnimationBeginTimeAtZero + CMTimeGetSeconds(clip.timeRange.start)
  opacity.duration = duration
  opacity.fillMode = .both
  opacity.isRemovedOnCompletion = false
  panel.opacity = 0
  panel.add(opacity, forKey: "sceneOpacity")
  return panel
}

private func makeVideoComposition(
  composition: AVMutableComposition,
  clips: [PlacedClip]
) -> AVMutableVideoComposition {
  var instructions: [AVVideoCompositionInstructionProtocol] = []

  for index in clips.indices {
    let clip = clips[index]
    let hasIncoming = index > clips.startIndex
    let hasOutgoing = index < clips.index(before: clips.endIndex)
    let passStart = clip.timeRange.start + (hasIncoming ? transitionDuration : .zero)
    let passEnd = CMTimeRangeGetEnd(clip.timeRange) - (hasOutgoing ? transitionDuration : .zero)
    if passEnd > passStart {
      let instruction = AVMutableVideoCompositionInstruction()
      instruction.timeRange = CMTimeRange(start: passStart, end: passEnd)
      let layer = AVMutableVideoCompositionLayerInstruction(assetTrack: clip.compositionTrack)
      layer.setTransform(clip.transform, at: passStart)
      instruction.layerInstructions = [layer]
      instructions.append(instruction)
    }

    guard hasOutgoing else { continue }
    let next = clips[index + 1]
    let transitionRange = CMTimeRange(
      start: CMTimeRangeGetEnd(clip.timeRange) - transitionDuration,
      duration: transitionDuration
    )
    let instruction = AVMutableVideoCompositionInstruction()
    instruction.timeRange = transitionRange

    let incoming = AVMutableVideoCompositionLayerInstruction(assetTrack: next.compositionTrack)
    incoming.setTransform(next.transform, at: transitionRange.start)
    incoming.setOpacity(0, at: transitionRange.start)
    incoming.setOpacityRamp(
      fromStartOpacity: 0,
      toEndOpacity: 1,
      timeRange: transitionRange
    )

    let outgoing = AVMutableVideoCompositionLayerInstruction(assetTrack: clip.compositionTrack)
    outgoing.setTransform(clip.transform, at: transitionRange.start)
    outgoing.setOpacityRamp(
      fromStartOpacity: 1,
      toEndOpacity: 0,
      timeRange: transitionRange
    )
    instruction.layerInstructions = [incoming, outgoing]
    instructions.append(instruction)
  }

  let parentLayer = CALayer()
  parentLayer.frame = CGRect(origin: .zero, size: targetSize)
  parentLayer.backgroundColor = NSColor.black.cgColor
  let videoLayer = CALayer()
  videoLayer.frame = parentLayer.bounds
  parentLayer.addSublayer(videoLayer)
  for clip in clips {
    parentLayer.addSublayer(makeCaptionLayer(for: clip))
  }

  let videoComposition = AVMutableVideoComposition()
  videoComposition.renderSize = targetSize
  videoComposition.frameDuration = frameDuration
  videoComposition.instructions = instructions.sorted {
    CMTimeCompare($0.timeRange.start, $1.timeRange.start) < 0
  }
  videoComposition.animationTool = AVVideoCompositionCoreAnimationTool(
    postProcessingAsVideoLayer: videoLayer,
    in: parentLayer
  )
  return videoComposition
}

private func writeVideo(
  composition: AVMutableComposition,
  videoComposition: AVVideoComposition,
  outputURL: URL
) throws {
  try? FileManager.default.removeItem(at: outputURL)
  let reader: AVAssetReader
  do {
    reader = try AVAssetReader(asset: composition)
  } catch {
    throw ComposeError.reader(error.localizedDescription)
  }

  let videoOutput = AVAssetReaderVideoCompositionOutput(
    videoTracks: composition.tracks(withMediaType: .video),
    videoSettings: [
      kCVPixelBufferPixelFormatTypeKey as String:
        kCVPixelFormatType_420YpCbCr8BiPlanarVideoRange
    ]
  )
  videoOutput.videoComposition = videoComposition
  videoOutput.alwaysCopiesSampleData = false
  guard reader.canAdd(videoOutput) else {
    throw ComposeError.reader("Cannot add video composition output")
  }
  reader.add(videoOutput)

  let writer: AVAssetWriter
  do {
    writer = try AVAssetWriter(outputURL: outputURL, fileType: .mp4)
  } catch {
    throw ComposeError.writer(error.localizedDescription)
  }

  let compression: [String: Any] = [
    AVVideoAverageBitRateKey: 12_000_000,
    AVVideoProfileLevelKey: AVVideoProfileLevelH264High40,
    AVVideoExpectedSourceFrameRateKey: 30,
    AVVideoMaxKeyFrameIntervalKey: 60,
  ]
  let settings: [String: Any] = [
    AVVideoCodecKey: AVVideoCodecType.h264,
    AVVideoWidthKey: Int(targetSize.width),
    AVVideoHeightKey: Int(targetSize.height),
    AVVideoCompressionPropertiesKey: compression,
    AVVideoColorPropertiesKey: [
      AVVideoColorPrimariesKey: AVVideoColorPrimaries_ITU_R_709_2,
      AVVideoTransferFunctionKey: AVVideoTransferFunction_ITU_R_709_2,
      AVVideoYCbCrMatrixKey: AVVideoYCbCrMatrix_ITU_R_709_2,
    ],
  ]
  let input = AVAssetWriterInput(mediaType: .video, outputSettings: settings)
  input.expectsMediaDataInRealTime = false
  guard writer.canAdd(input) else {
    throw ComposeError.writer("Cannot add H.264 video input")
  }
  writer.add(input)

  guard writer.startWriting() else {
    throw ComposeError.writer(writer.error?.localizedDescription ?? "startWriting failed")
  }
  guard reader.startReading() else {
    throw ComposeError.reader(reader.error?.localizedDescription ?? "startReading failed")
  }
  writer.startSession(atSourceTime: .zero)

  let queue = DispatchQueue(label: "app-preview.video-writer")
  let finished = DispatchSemaphore(value: 0)
  var appendError: String?
  input.requestMediaDataWhenReady(on: queue) {
    while input.isReadyForMoreMediaData {
      guard let sample = videoOutput.copyNextSampleBuffer() else {
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
    throw ComposeError.append(appendError)
  }
  guard reader.status == .completed else {
    throw ComposeError.reader(reader.error?.localizedDescription ?? "status \(reader.status.rawValue)")
  }
  guard writer.status == .completed else {
    throw ComposeError.writer(writer.error?.localizedDescription ?? "status \(writer.status.rawValue)")
  }
}

do {
  let arguments = CommandLine.arguments
  guard arguments.count == 3 || arguments.count == 4 else { throw ComposeError.usage }
  let selectedClips = arguments.count == 4
    ? try JSONDecoder().decode([ClipSpec].self, from: Data(contentsOf: URL(fileURLWithPath: arguments[3]))) : clips
  let sceneDirectory = URL(fileURLWithPath: arguments[1], isDirectory: true)
  let outputURL = URL(fileURLWithPath: arguments[2])
  let composition = AVMutableComposition()
  guard
    let trackA = composition.addMutableTrack(
      withMediaType: .video,
      preferredTrackID: kCMPersistentTrackID_Invalid
    ),
    let trackB = composition.addMutableTrack(
      withMediaType: .video,
      preferredTrackID: kCMPersistentTrackID_Invalid
    )
  else {
    throw ComposeError.compositionTrack
  }

  var placed: [PlacedClip] = []
  var cursor = CMTime.zero
  for (index, spec) in selectedClips.enumerated() {
    let url = sceneDirectory.appendingPathComponent(spec.filename)
    let asset = AVURLAsset(url: url)
    guard let sourceTrack = asset.tracks(withMediaType: .video).first else {
      throw ComposeError.missingVideoTrack(url.path)
    }
    let sourceRange = CMTimeRange(
      start: CMTime(seconds: spec.start, preferredTimescale: 600),
      duration: CMTime(seconds: spec.duration, preferredTimescale: 600)
    )
    let targetTrack = index.isMultiple(of: 2) ? trackA : trackB
    try targetTrack.insertTimeRange(sourceRange, of: sourceTrack, at: cursor)
    let placedRange = CMTimeRange(start: cursor, duration: sourceRange.duration)
    placed.append(
      PlacedClip(
        spec: spec,
        sourceTrack: sourceTrack,
        compositionTrack: targetTrack,
        timeRange: placedRange,
        transform: orientedTransform(for: sourceTrack)
      )
    )
    cursor = CMTimeRangeGetEnd(placedRange) - transitionDuration
  }

  let duration = CMTimeGetSeconds(composition.duration)
  guard duration >= 15, duration <= 30 else {
    throw ComposeError.writer("Invalid App Preview duration \(duration)")
  }
  let videoComposition = makeVideoComposition(composition: composition, clips: placed)
  try writeVideo(
    composition: composition,
    videoComposition: videoComposition,
    outputURL: outputURL
  )
  print(String(format: "Wrote %@ (%.3fs)", outputURL.path, duration))
} catch {
  fputs("\(error)\n", stderr)
  exit(1)
}
