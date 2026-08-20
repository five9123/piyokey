import AppKit

@main
struct PiyokeyAppIconRenderer {
  static func main() throws {
    guard CommandLine.arguments.count == 2 || CommandLine.arguments.count == 3 else {
      throw RenderError.usage
    }

    let sourcePath = CommandLine.arguments.count == 3
      ? CommandLine.arguments[1]
      : "shared/brand/piyokey_app_icon_source.png"
    let outputPath = CommandLine.arguments.last!
    guard let source = NSImage(contentsOfFile: sourcePath) else {
      throw RenderError.sourceUnreadable
    }

    var proposedRect = CGRect(origin: .zero, size: source.size)
    guard
      source.size.width > 0,
      source.size.height > 0,
      let sourceImage = source.cgImage(forProposedRect: &proposedRect, context: nil, hints: nil)
    else {
      throw RenderError.sourceUnreadable
    }

    let isOpaque: Bool
    switch sourceImage.alphaInfo {
    case .none, .noneSkipFirst, .noneSkipLast:
      isOpaque = true
    default:
      isOpaque = false
    }

    if sourceImage.width == 1_024, sourceImage.height == 1_024, isOpaque {
      let bitmap = NSBitmapImageRep(cgImage: sourceImage)
      guard
        let sample = bitmap.colorAt(x: 64, y: 64)?.usingColorSpace(.sRGB),
        sample.redComponent + sample.greenComponent + sample.blueComponent > 0.5
      else {
        throw RenderError.unexpectedBlackOutput
      }

      let sourceData = try Data(contentsOf: URL(fileURLWithPath: sourcePath))
      try sourceData.write(to: URL(fileURLWithPath: outputPath), options: .atomic)
      return
    }

    let side: CGFloat = 1_024
    let colorSpace = CGColorSpace(name: CGColorSpace.sRGB) ?? CGColorSpaceCreateDeviceRGB()
    let bitmapInfo = CGBitmapInfo.byteOrder32Big.rawValue
      | CGImageAlphaInfo.noneSkipLast.rawValue
    guard let context = CGContext(
      data: nil,
      width: Int(side),
      height: Int(side),
      bitsPerComponent: 8,
      bytesPerRow: Int(side) * 4,
      space: colorSpace,
      bitmapInfo: bitmapInfo
    ) else { throw RenderError.encoding }

    context.interpolationQuality = .high
    context.setFillColor(NSColor(red: 1.0, green: 0.25, blue: 0.48, alpha: 1).cgColor)
    context.fill(CGRect(x: 0, y: 0, width: side, height: side))

    let sourceAspect = CGFloat(sourceImage.width) / CGFloat(sourceImage.height)
    let drawRect: CGRect
    if sourceAspect > 1 {
      let width = side * sourceAspect
      drawRect = CGRect(x: (side - width) / 2, y: 0, width: width, height: side)
    } else {
      let height = side / sourceAspect
      drawRect = CGRect(x: 0, y: (side - height) / 2, width: side, height: height)
    }
    context.draw(sourceImage, in: drawRect)

    guard let flattenedImage = context.makeImage() else { throw RenderError.encoding }
    let bitmap = NSBitmapImageRep(cgImage: flattenedImage)
    guard
      let sample = bitmap.colorAt(x: 64, y: 64)?.usingColorSpace(.sRGB),
      sample.redComponent + sample.greenComponent + sample.blueComponent > 0.5
    else {
      throw RenderError.unexpectedBlackOutput
    }

    guard let png = bitmap.representation(using: .png, properties: [:]) else {
      throw RenderError.encoding
    }

    try png.write(to: URL(fileURLWithPath: outputPath), options: .atomic)
  }

  enum RenderError: Error {
    case usage
    case sourceUnreadable
    case encoding
    case unexpectedBlackOutput
  }
}
