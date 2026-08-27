import AppKit
import Foundation

enum AssetError: Error, CustomStringConvertible {
  case loadFailed(URL)
  case bitmapFailed(String)
  case writeFailed(URL)

  var description: String {
    switch self {
    case .loadFailed(let url): return "Could not load image: \(url.path)"
    case .bitmapFailed(let name): return "Could not create bitmap: \(name)"
    case .writeFailed(let url): return "Could not write PNG: \(url.path)"
    }
  }
}

let repository = URL(fileURLWithPath: FileManager.default.currentDirectoryPath)
let sourceURL = repository.appendingPathComponent("shared/brand/piyokey_app_icon_source.png")
let assetDirectory = repository.appendingPathComponent("release/google_play/assets/common", isDirectory: true)
let featureSourceURL = assetDirectory.appendingPathComponent("feature-graphic-source.png")
let iconOutputURL = assetDirectory.appendingPathComponent("play-icon-512.png")
let featureOutputURL = assetDirectory.appendingPathComponent("feature-graphic-1024x500.png")

try FileManager.default.createDirectory(at: assetDirectory, withIntermediateDirectories: true)

func load(_ url: URL) throws -> NSImage {
  guard let image = NSImage(contentsOf: url) else { throw AssetError.loadFailed(url) }
  return image
}

func bitmap(width: Int, height: Int, alpha: Bool, name: String) throws -> NSBitmapImageRep {
  guard let value = NSBitmapImageRep(
    bitmapDataPlanes: nil,
    pixelsWide: width,
    pixelsHigh: height,
    bitsPerSample: 8,
    samplesPerPixel: alpha ? 4 : 3,
    hasAlpha: alpha,
    isPlanar: false,
    colorSpaceName: .deviceRGB,
    bytesPerRow: 0,
    bitsPerPixel: 0
  ) else { throw AssetError.bitmapFailed(name) }
  value.size = NSSize(width: width, height: height)
  return value
}

func render(_ rep: NSBitmapImageRep, draw: () -> Void) {
  NSGraphicsContext.saveGraphicsState()
  NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: rep)
  draw()
  NSGraphicsContext.restoreGraphicsState()
}

func writeIfMissing(_ rep: NSBitmapImageRep, to url: URL) throws {
  if FileManager.default.fileExists(atPath: url.path) {
    print("Preserved existing \(url.path)")
    return
  }
  guard let data = rep.representation(using: .png, properties: [.compressionFactor: 0.92]) else {
    throw AssetError.writeFailed(url)
  }
  try data.write(to: url, options: .atomic)
}

let brand = try load(sourceURL)
let icon = try bitmap(width: 512, height: 512, alpha: true, name: "Play icon")
render(icon) {
  NSColor.clear.setFill()
  NSBezierPath(rect: NSRect(x: 0, y: 0, width: 512, height: 512)).fill()
  brand.draw(
    in: NSRect(x: 0, y: 0, width: 512, height: 512),
    from: NSRect(origin: .zero, size: brand.size),
    operation: .copy,
    fraction: 1
  )
}
try writeIfMissing(icon, to: iconOutputURL)

let featureSource = try load(featureSourceURL)
let feature = try bitmap(width: 1024, height: 500, alpha: false, name: "feature graphic")
render(feature) {
  NSColor(calibratedRed: 0.94, green: 0.76, blue: 0.89, alpha: 1).setFill()
  NSBezierPath(rect: NSRect(x: 0, y: 0, width: 1024, height: 500)).fill()
  let sourceRatio = featureSource.size.width / featureSource.size.height
  let destinationRatio = CGFloat(1024.0 / 500.0)
  let sourceRect: NSRect
  if sourceRatio < destinationRatio {
    let height = featureSource.size.width / destinationRatio
    sourceRect = NSRect(
      x: 0,
      y: (featureSource.size.height - height) / 2,
      width: featureSource.size.width,
      height: height
    )
  } else {
    let width = featureSource.size.height * destinationRatio
    sourceRect = NSRect(
      x: (featureSource.size.width - width) / 2,
      y: 0,
      width: width,
      height: featureSource.size.height
    )
  }
  featureSource.draw(
    in: NSRect(x: 0, y: 0, width: 1024, height: 500),
    from: sourceRect,
    operation: .copy,
    fraction: 1
  )
}
try writeIfMissing(feature, to: featureOutputURL)

print("Ready \(iconOutputURL.path)")
print("Ready \(featureOutputURL.path)")
