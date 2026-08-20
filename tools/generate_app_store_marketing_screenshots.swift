#!/usr/bin/env swift

import AppKit
import Foundation

private struct ScreenshotSpec: Codable {
    let output: String
    let source: String
    let title: String
    let subtitle: String
}

private let specs: [ScreenshotSpec] = [
    .init(
        output: "01-keyboard-start-ja.png",
        source: "01-appstore-current-curriculum-mission-ja.png",
        title: "光るキーを追って、\n韓国語を打とう",
        subtitle: "韓国語キーボード未設定でも、すぐ練習"
    ),
    .init(
        output: "02-six-games-ja.png",
        source: "02-appstore-current-flow-ja.png",
        title: "流れる単語を追って、\nコンボをつなごう",
        subtitle: "6つのゲームで、スピードと正確さに挑戦"
    ),
    .init(
        output: "03-curriculum-ja.png",
        source: "03-appstore-current-attendance-home-ja.png",
        title: "今日も達成、\n4日連続",
        subtitle: "スタンプとピヨの応援で、自然に続く"
    ),
    .init(
        output: "04-daily-habit-ja.png",
        source: "04-appstore-current-acid-rain-ja.png",
        title: "落ちてくる単語を、\n地面に着く前に！",
        subtitle: "危ない単語を見きわめて、すばやく入力"
    ),
    .init(
        output: "05-results-ja.png",
        source: "05-appstore-current-word-match-ja.png",
        title: "意味を見て、\n韓国語で答えよう",
        subtitle: "日本語のヒントから、ハングルを直接入力"
    ),
    .init(
        output: "06-flow-game-ja.png",
        source: "06-appstore-current-choseong-ja.png",
        title: "初声をヒントに、\n答えをタイピング",
        subtitle: "考えながら、韓国語の語彙も強くなる"
    ),
    .init(
        output: "07-piyo-growth-ja.png",
        source: "07-appstore-current-daily-mission-ja.png",
        title: "今日の3分を、\nピヨと進めよう",
        subtitle: "毎日のミッションで、少しずつ上達"
    ),
    .init(
        output: "08-streak-rewards-ja.png",
        source: "08-appstore-current-rewards-ja.png",
        title: "あと1日で、\n次のごほうび",
        subtitle: "学ぶほど、ピヨのアイテムが増えていく"
    ),
]

private let canvasWidth = 1_320
private let canvasHeight = 2_868
private let canvasSize = NSSize(width: canvasWidth, height: canvasHeight)
private let screenRect = NSRect(x: 150, y: 650, width: 1_020, height: 2_216)

private func drawCenteredText(
    _ text: String,
    in rect: NSRect,
    font: NSFont,
    color: NSColor,
    lineSpacing: CGFloat = 0,
    kern: CGFloat = 0
) {
    let paragraph = NSMutableParagraphStyle()
    paragraph.alignment = .center
    paragraph.lineBreakMode = .byWordWrapping
    paragraph.lineSpacing = lineSpacing

    let attributes: [NSAttributedString.Key: Any] = [
        .font: font,
        .foregroundColor: color,
        .paragraphStyle: paragraph,
        .kern: kern,
    ]

    (text as NSString).draw(
        with: rect,
        options: [.usesLineFragmentOrigin, .usesFontLeading],
        attributes: attributes
    )
}

private func drawPill(in rect: NSRect) {
    let path = NSBezierPath(roundedRect: rect, xRadius: rect.height / 2, yRadius: rect.height / 2)
    NSColor.white.withAlphaComponent(0.82).setFill()
    path.fill()

    let border = NSColor(calibratedRed: 0.96, green: 0.30, blue: 0.55, alpha: 0.18)
    border.setStroke()
    path.lineWidth = 2
    path.stroke()
}

private func drawBrandLockup(mark: NSImage) {
    let pillRect = NSRect(x: 516, y: 94, width: 378, height: 84)
    drawPill(in: pillRect)

    let markRect = NSRect(x: 426, y: 76, width: 112, height: 112)
    let markPath = NSBezierPath(roundedRect: markRect, xRadius: 28, yRadius: 28)
    let shadow = NSShadow()
    shadow.shadowColor = NSColor(calibratedRed: 0.23, green: 0.18, blue: 0.42, alpha: 0.18)
    shadow.shadowBlurRadius = 18
    shadow.shadowOffset = NSSize(width: 0, height: 7)

    NSGraphicsContext.saveGraphicsState()
    shadow.set()
    NSColor.white.setFill()
    markPath.fill()
    NSGraphicsContext.restoreGraphicsState()

    NSGraphicsContext.saveGraphicsState()
    markPath.addClip()
    mark.draw(
        in: markRect,
        from: NSRect(origin: .zero, size: mark.size),
        operation: .sourceOver,
        fraction: 1,
        respectFlipped: true,
        hints: [.interpolation: NSImageInterpolation.high]
    )
    NSGraphicsContext.restoreGraphicsState()

    NSColor.white.withAlphaComponent(0.9).setStroke()
    markPath.lineWidth = 3
    markPath.stroke()

    drawCenteredText(
        "PIYOKEY",
        in: NSRect(x: 550, y: 116, width: 316, height: 45),
        font: .systemFont(ofSize: 31, weight: .bold),
        color: NSColor(calibratedRed: 0.96, green: 0.28, blue: 0.54, alpha: 1),
        kern: 6
    )
}

private func render(
    spec: ScreenshotSpec,
    sourceDirectory: URL,
    outputDirectory: URL,
    background: NSImage,
    brandMark: NSImage
) throws {
    let sourceURL = sourceDirectory.appendingPathComponent(spec.source)
    guard let screenshot = NSImage(contentsOf: sourceURL) else {
        throw NSError(domain: "PiyokeyScreenshots", code: 1, userInfo: [
            NSLocalizedDescriptionKey: "Could not load \(sourceURL.path)"
        ])
    }

    let colorSpace = CGColorSpaceCreateDeviceRGB()
    guard let bitmapContext = CGContext(
        data: nil,
        width: canvasWidth,
        height: canvasHeight,
        bitsPerComponent: 8,
        bytesPerRow: canvasWidth * 4,
        space: colorSpace,
        bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue
    ) else {
        throw NSError(domain: "PiyokeyScreenshots", code: 2, userInfo: [
            NSLocalizedDescriptionKey: "Could not create bitmap context"
        ])
    }

    NSGraphicsContext.saveGraphicsState()
    NSGraphicsContext.current = NSGraphicsContext(cgContext: bitmapContext, flipped: true)

    background.draw(
        in: NSRect(origin: .zero, size: canvasSize),
        from: NSRect(origin: .zero, size: background.size),
        operation: .copy,
        fraction: 1,
        respectFlipped: true,
        hints: [.interpolation: NSImageInterpolation.high]
    )

    // Use live-progress source captures with a coherent growth state and the
    // actual in-app chick visible. The lockup reinforces the brand without
    // replacing or fabricating the mascot shown in the captured UI.
    drawBrandLockup(mark: brandMark)

    drawCenteredText(
        spec.title,
        in: NSRect(x: 92, y: 212, width: 1_136, height: 222),
        font: .systemFont(ofSize: 76, weight: .bold),
        color: NSColor(calibratedRed: 0.13, green: 0.10, blue: 0.22, alpha: 1),
        lineSpacing: 9
    )

    drawCenteredText(
        spec.subtitle,
        in: NSRect(x: 90, y: 493, width: 1_140, height: 58),
        font: .systemFont(ofSize: 37, weight: .medium),
        color: NSColor(calibratedRed: 0.38, green: 0.35, blue: 0.48, alpha: 1)
    )

    let shadowPath = NSBezierPath(roundedRect: screenRect, xRadius: 68, yRadius: 68)
    let shadow = NSShadow()
    shadow.shadowColor = NSColor(calibratedRed: 0.23, green: 0.18, blue: 0.42, alpha: 0.18)
    shadow.shadowBlurRadius = 38
    shadow.shadowOffset = NSSize(width: 0, height: 16)

    NSGraphicsContext.saveGraphicsState()
    shadow.set()
    NSColor.white.setFill()
    shadowPath.fill()
    NSGraphicsContext.restoreGraphicsState()

    NSGraphicsContext.saveGraphicsState()
    shadowPath.addClip()
    screenshot.draw(
        in: screenRect,
        from: NSRect(origin: .zero, size: screenshot.size),
        operation: .sourceOver,
        fraction: 1,
        respectFlipped: true,
        hints: [.interpolation: NSImageInterpolation.high]
    )
    NSGraphicsContext.restoreGraphicsState()

    NSColor.white.withAlphaComponent(0.88).setStroke()
    shadowPath.lineWidth = 5
    shadowPath.stroke()

    NSGraphicsContext.restoreGraphicsState()

    guard let rawImage = bitmapContext.makeImage() else {
        throw NSError(domain: "PiyokeyScreenshots", code: 3, userInfo: [
            NSLocalizedDescriptionKey: "Could not create final image"
        ])
    }

    // AppKit's flipped drawing coordinates are vertically inverted when the
    // raw CGContext pixels are encoded directly. Flip only the vertical axis;
    // a 180-degree rotation would also mirror all UI and Japanese text.
    guard let exportContext = CGContext(
        data: nil,
        width: canvasWidth,
        height: canvasHeight,
        bitsPerComponent: 8,
        bytesPerRow: canvasWidth * 4,
        space: colorSpace,
        bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue
    ) else {
        throw NSError(domain: "PiyokeyScreenshots", code: 4, userInfo: [
            NSLocalizedDescriptionKey: "Could not create export context"
        ])
    }

    exportContext.translateBy(x: 0, y: CGFloat(canvasHeight))
    exportContext.scaleBy(x: 1, y: -1)
    exportContext.draw(
        rawImage,
        in: CGRect(x: 0, y: 0, width: canvasWidth, height: canvasHeight)
    )

    guard let cgImage = exportContext.makeImage() else {
        throw NSError(domain: "PiyokeyScreenshots", code: 5, userInfo: [
            NSLocalizedDescriptionKey: "Could not create oriented image"
        ])
    }

    let representation = NSBitmapImageRep(cgImage: cgImage)
    guard let pngData = representation.representation(using: .png, properties: [:]) else {
        throw NSError(domain: "PiyokeyScreenshots", code: 6, userInfo: [
            NSLocalizedDescriptionKey: "Could not encode final PNG"
        ])
    }

    let outputURL = outputDirectory.appendingPathComponent(spec.output)
    try pngData.write(to: outputURL, options: .atomic)
    print("Wrote \(outputURL.path)")
}

let fileManager = FileManager.default
let repository = URL(fileURLWithPath: fileManager.currentDirectoryPath, isDirectory: true)
let sourceDirectory = repository.appendingPathComponent("release/screenshots/ja", isDirectory: true)
let outputDirectory = repository.appendingPathComponent("release/screenshots/ja-marketing", isDirectory: true)
let backgroundURL = repository.appendingPathComponent(
    "release/screenshots/marketing-assets/piyokey-marketing-background.png"
)
let brandMarkURL = repository.appendingPathComponent(
    "shared/brand/piyokey_app_icon_source.png"
)

try fileManager.createDirectory(at: outputDirectory, withIntermediateDirectories: true)

guard let background = NSImage(contentsOf: backgroundURL) else {
    fatalError("Could not load marketing background at \(backgroundURL.path)")
}
guard let brandMark = NSImage(contentsOf: brandMarkURL) else {
    fatalError("Could not load PIYOKEY brand mark at \(brandMarkURL.path)")
}

for spec in specs {
    try render(
        spec: spec,
        sourceDirectory: sourceDirectory,
        outputDirectory: outputDirectory,
        background: background,
        brandMark: brandMark
    )
}

let encoder = JSONEncoder()
encoder.outputFormatting = [.prettyPrinted, .sortedKeys, .withoutEscapingSlashes]
let manifest = try encoder.encode(specs)
try manifest.write(
    to: outputDirectory.appendingPathComponent("manifest.json"),
    options: .atomic
)
