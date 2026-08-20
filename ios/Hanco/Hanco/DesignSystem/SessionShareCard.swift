import LinkPresentation
import ImageIO
import Photos
import SwiftUI
import UIKit
import UniformTypeIdentifiers

struct SessionShareCardMetric: Equatable, Identifiable {
  let id: String
  let label: String
  let value: String
  let systemImage: String
}

struct SessionShareCardModel: Equatable {
  let sessionTitle: String
  let sessionSubtitle: String?
  let achievement: String
  let scoreLabel: String
  let scoreValue: String
  let metrics: [SessionShareCardMetric]
  let caption: String
  let mascotName: String

  init(
    sessionTitle: String,
    sessionSubtitle: String? = nil,
    achievement: String,
    scoreLabel: String,
    scoreValue: String,
    metrics: [SessionShareCardMetric],
    caption: String,
    mascotName: String = AppLocalization.string("mascot.default_name")
  ) {
    self.sessionTitle = sessionTitle
    self.sessionSubtitle = sessionSubtitle
    self.achievement = achievement
    self.scoreLabel = scoreLabel
    self.scoreValue = scoreValue
    self.metrics = metrics
    self.caption = caption
    self.mascotName = mascotName
  }
}

enum SessionShareImageSaveResult: Equatable {
  case saved
  case permissionDenied
  case failed
}

enum SessionShareImageSaver {
  static func save(_ pngData: Data) async -> SessionShareImageSaveResult {
    let authorization = await photoLibraryAuthorization()
    guard authorization == .authorized || authorization == .limited else {
      return .permissionDenied
    }

    return await withCheckedContinuation { continuation in
      PHPhotoLibrary.shared().performChanges {
        let request = PHAssetCreationRequest.forAsset()
        let options = PHAssetResourceCreationOptions()
        options.uniformTypeIdentifier = UTType.png.identifier
        request.addResource(with: .photo, data: pngData, options: options)
      } completionHandler: { saved, _ in
        continuation.resume(returning: saved ? .saved : .failed)
      }
    }
  }

  private static func photoLibraryAuthorization() async -> PHAuthorizationStatus {
    let current = PHPhotoLibrary.authorizationStatus(for: .addOnly)
    guard current == .notDetermined else { return current }
    return await withCheckedContinuation { continuation in
      PHPhotoLibrary.requestAuthorization(for: .addOnly) { status in
        continuation.resume(returning: status)
      }
    }
  }
}

enum SessionSharePNGEncoder {
  static func encode(_ image: UIImage) async -> Data? {
    guard let cgImage = image.cgImage else { return nil }
    return await Task.detached(priority: .userInitiated) {
      encode(cgImage)
    }.value
  }

  static func encode(_ cgImage: CGImage) -> Data? {
    let data = NSMutableData()
    guard
      let destination = CGImageDestinationCreateWithData(
        data,
        UTType.png.identifier as CFString,
        1,
        nil
      )
    else { return nil }
    CGImageDestinationAddImage(destination, cgImage, nil)
    guard CGImageDestinationFinalize(destination) else { return nil }
    return data as Data
  }
}

enum SessionSharePreparationPolicy {
  // Give SwiftUI one display pass to reveal progress before the MainActor-only
  // ImageRenderer begins its work.
  static let progressPresentationDelayNanoseconds: UInt64 = 20_000_000
}

struct SessionShareSingleFlightGate: Equatable {
  private(set) var isRunning = false

  mutating func begin() -> Bool {
    guard !isRunning else { return false }
    isRunning = true
    return true
  }

  mutating func finish() {
    isRunning = false
  }
}

@MainActor
enum SessionShareCardRenderer {
  static let logicalSize = CGSize(width: 600, height: 600)
  static let scale: CGFloat = 2

  static func render(_ model: SessionShareCardModel) -> UIImage? {
    let renderer = ImageRenderer(
      content: SessionShareCardView(model: model)
        .frame(width: logicalSize.width, height: logicalSize.height)
        .environment(\.locale, AppLocalization.locale)
    )
    renderer.proposedSize = ProposedViewSize(logicalSize)
    renderer.scale = scale
    renderer.isOpaque = true
    return renderer.uiImage
  }
}

struct SessionShareButton: View {
  private enum Work: Equatable {
    case save
    case share
  }

  let model: SessionShareCardModel
  let accessibilityIdentifier: String

  @State private var artifact: SessionShareArtifact?
  @State private var cachedArtifact: SessionShareArtifact?
  @State private var renderingFailed = false
  @State private var saveResult: SessionShareImageSaveResult?
  @State private var activeWork: Work?
  @State private var preparationGate = SessionShareSingleFlightGate()
  @State private var preparationTask: Task<Void, Never>?

  var body: some View {
    VStack(spacing: 7) {
      HStack(spacing: 10) {
        Button { begin(.save) } label: {
          Label(
            activeWork == .save ? "result.share.saving" : "result.share.save_image",
            systemImage: activeWork == .save ? "hourglass" : "square.and.arrow.down"
          )
          .font(.subheadline.weight(.bold))
          .foregroundStyle(AppPalette.accent)
          .frame(maxWidth: .infinity)
          .padding(.vertical, 13)
          .background(
            AppPalette.accentSoft.opacity(0.55),
            in: RoundedRectangle(cornerRadius: 17, style: .continuous)
          )
        }
        .disabled(activeWork != nil)
        .accessibilityIdentifier("\(accessibilityIdentifier).save_image")

        Button { begin(.share) } label: {
          Label("result.share.action", systemImage: "square.and.arrow.up.fill")
            .font(.subheadline.weight(.bold))
            .foregroundStyle(AppPalette.secondary)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 13)
            .background(
              AppPalette.secondary.opacity(0.12),
              in: RoundedRectangle(cornerRadius: 17, style: .continuous)
            )
        }
        .disabled(activeWork != nil)
        .accessibilityIdentifier(accessibilityIdentifier)
      }

      if let activeWork {
        HStack(spacing: 7) {
          ProgressView()
            .controlSize(.small)
          Text(activeWork == .save ? "result.share.saving" : "result.share.action")
            .font(.caption.weight(.semibold))
            .foregroundStyle(AppPalette.mutedInk)
        }
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("result.share.progress")
      } else if renderingFailed {
        Text("result.share.failed")
          .font(.caption)
          .foregroundStyle(AppPalette.error)
          .accessibilityIdentifier("result.share.error")
      } else if let saveResult {
        Text(saveMessageKey(for: saveResult))
          .font(.caption.weight(.semibold))
          .foregroundStyle(saveResult == .saved ? AppPalette.success : AppPalette.error)
          .accessibilityIdentifier("result.share.save_status")
      }
    }
    .sheet(item: $artifact) { artifact in
      SessionActivityView(artifact: artifact)
        .ignoresSafeArea()
    }
    .onDisappear {
      preparationTask?.cancel()
      preparationTask = nil
      activeWork = nil
      preparationGate.finish()
    }
  }

  private func begin(_ work: Work) {
    guard activeWork == nil, preparationGate.begin() else { return }
    activeWork = work
    renderingFailed = false
    if work == .save { saveResult = nil }
    preparationTask = Task { @MainActor in
      defer {
        activeWork = nil
        preparationGate.finish()
        preparationTask = nil
      }
      try? await Task.sleep(
        nanoseconds: SessionSharePreparationPolicy.progressPresentationDelayNanoseconds
      )
      guard !Task.isCancelled, let preparedArtifact = await prepareArtifact() else {
        if !Task.isCancelled { renderingFailed = true }
        return
      }
      guard !Task.isCancelled else { return }
      switch work {
      case .save:
        saveResult = await SessionShareImageSaver.save(preparedArtifact.pngData)
      case .share:
        artifact = preparedArtifact
      }
    }
  }

  @MainActor
  private func prepareArtifact() async -> SessionShareArtifact? {
    if let cachedArtifact { return cachedArtifact }
    guard let image = SessionShareCardRenderer.render(model), !Task.isCancelled,
      let pngData = await SessionSharePNGEncoder.encode(image), !Task.isCancelled
    else { return nil }
    let artifact = SessionShareArtifact(
      image: image,
      pngData: pngData,
      caption: model.caption
    )
    cachedArtifact = artifact
    return artifact
  }

  private func saveMessageKey(for result: SessionShareImageSaveResult) -> LocalizedStringKey {
    switch result {
    case .saved: "result.share.saved"
    case .permissionDenied: "result.share.permission_denied"
    case .failed: "result.share.save_failed"
    }
  }
}

private struct SessionShareCardView: View {
  let model: SessionShareCardModel

  var body: some View {
    ZStack {
      LinearGradient(
        colors: [AppPalette.backgroundTop, AppPalette.backgroundBottom],
        startPoint: .topLeading,
        endPoint: .bottomTrailing
      )

      Circle()
        .fill(AppPalette.accentSoft.opacity(0.52))
        .frame(width: 300, height: 300)
        .offset(x: 250, y: -245)
      Circle()
        .fill(AppPalette.secondary.opacity(0.11))
        .frame(width: 270, height: 270)
        .offset(x: -260, y: 250)

      VStack(spacing: 22) {
        HStack {
          brandLockup
          Spacer()
          Text(verbatim: model.achievement)
            .font(.system(size: 18, weight: .black, design: .rounded))
            .foregroundStyle(.white)
            .padding(.horizontal, 15)
            .padding(.vertical, 9)
            .background(AppPalette.accent, in: Capsule())
        }

        VStack(alignment: .leading, spacing: 5) {
          Text(verbatim: AppLocalization.string("result.share.eyebrow"))
            .font(.system(size: 16, weight: .bold, design: .rounded))
            .foregroundStyle(AppPalette.secondary)
          HStack(alignment: .firstTextBaseline, spacing: 10) {
            Text(verbatim: model.sessionTitle)
              .font(.system(size: 31, weight: .black, design: .rounded))
              .foregroundStyle(AppPalette.ink)
              .lineLimit(1)
              .minimumScaleFactor(0.66)
              .layoutPriority(1)
            if let sessionSubtitle = model.sessionSubtitle {
              Text(verbatim: sessionSubtitle)
                .font(.system(size: 15, weight: .black, design: .rounded))
                .foregroundStyle(AppPalette.accent)
                .lineLimit(1)
                .minimumScaleFactor(0.68)
                .padding(.horizontal, 11)
                .padding(.vertical, 5)
                .background(AppPalette.accentSoft.opacity(0.68), in: Capsule())
            }
            Spacer(minLength: 0)
          }
        }

        HStack(spacing: 8) {
          VStack(alignment: .leading, spacing: 1) {
            Text(verbatim: model.scoreLabel)
              .font(.system(size: 16, weight: .bold, design: .rounded))
              .foregroundStyle(AppPalette.mutedInk)
            Text(verbatim: model.scoreValue)
              .font(.system(size: 70, weight: .black, design: .rounded))
              .foregroundStyle(AppPalette.accent)
              .monospacedDigit()
              .minimumScaleFactor(0.65)
              .lineLimit(1)
          }
          .frame(maxWidth: .infinity, alignment: .leading)

          SessionShareStaticMascot(name: model.mascotName)
            .frame(width: 150, height: 155)
            .accessibilityHidden(true)
        }
        .frame(height: 155)

        HStack(spacing: 10) {
          ForEach(model.metrics.prefix(3)) { metric in
            metricCard(metric)
          }
        }

        HStack(spacing: 12) {
          Image(systemName: "iphone.gen3")
            .font(.system(size: 21, weight: .bold))
            .foregroundStyle(AppPalette.accent)
          VStack(alignment: .leading, spacing: 2) {
            Text(verbatim: AppLocalization.string("result.share.download_cta"))
              .font(.system(size: 17, weight: .black, design: .rounded))
              .foregroundStyle(AppPalette.ink)
            Text(verbatim: AppLocalization.string("result.share.download_detail"))
              .font(.system(size: 12, weight: .semibold, design: .rounded))
              .foregroundStyle(AppPalette.mutedInk)
          }
          Spacer()
        }
        .padding(.horizontal, 18)
        .padding(.vertical, 13)
        .background(.white.opacity(0.86), in: RoundedRectangle(cornerRadius: 19))
      }
      .padding(34)
    }
    .environment(\.colorScheme, .light)
  }

  private var brandLockup: some View {
    HStack(spacing: 10) {
      PiyokeyLogoMark()
        .frame(width: 44, height: 44)
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        .shadow(color: AppPalette.keyShadow, radius: 4, y: 3)
      Text(verbatim: AppLocalization.string("app.name"))
        .font(.system(size: 27, weight: .black, design: .rounded))
        .foregroundStyle(AppPalette.ink)
    }
  }

  private func metricCard(_ metric: SessionShareCardMetric) -> some View {
    VStack(spacing: 7) {
      Image(systemName: metric.systemImage)
        .font(.system(size: 19, weight: .bold))
        .foregroundStyle(AppPalette.secondary)
      Text(verbatim: metric.value)
        .font(.system(size: 23, weight: .black, design: .rounded))
        .foregroundStyle(AppPalette.ink)
        .monospacedDigit()
        .lineLimit(1)
        .minimumScaleFactor(0.7)
      Text(verbatim: metric.label)
        .font(.system(size: 11, weight: .bold, design: .rounded))
        .foregroundStyle(AppPalette.mutedInk)
        .lineLimit(1)
        .minimumScaleFactor(0.7)
    }
    .frame(maxWidth: .infinity)
    .padding(.vertical, 13)
    .background(.white.opacity(0.76), in: RoundedRectangle(cornerRadius: 18))
  }
}

/// ImageRenderer runs on the main actor, so the share card intentionally uses the
/// pre-rendered brand mascot instead of the animated vector mascot's Metal
/// drawing group. This keeps first-tap work bounded while preserving the chosen
/// mascot name in the exported card.
private struct SessionShareStaticMascot: View {
  let name: String

  var body: some View {
    VStack(spacing: 5) {
      ZStack {
        Circle()
          .fill(AppPalette.accentSoft.opacity(0.72))
          .frame(width: 126, height: 126)
        PiyokeyLogoMark()
          .frame(width: 112, height: 112)
          .clipShape(RoundedRectangle(cornerRadius: 28, style: .continuous))
          .shadow(color: AppPalette.keyShadow.opacity(0.7), radius: 5, y: 3)
        Image(systemName: "sparkles")
          .font(.system(size: 20, weight: .black))
          .foregroundStyle(AppPalette.secondary)
          .offset(x: 54, y: -49)
      }

      Text(verbatim: name)
        .font(.system(size: 12, weight: .black, design: .rounded))
        .foregroundStyle(AppPalette.ink)
        .lineLimit(1)
        .minimumScaleFactor(0.7)
        .padding(.horizontal, 10)
        .padding(.vertical, 4)
        .background(.white.opacity(0.9), in: Capsule())
    }
  }
}

struct SessionShareArtifact: Identifiable {
  let id = UUID()
  let image: UIImage
  let pngData: Data
  let caption: String
}

private struct SessionActivityView: UIViewControllerRepresentable {
  let artifact: SessionShareArtifact

  func makeUIViewController(context: Context) -> UIActivityViewController {
    UIActivityViewController(
      activityItems: [
        SessionShareImageSource(image: artifact.image, pngData: artifact.pngData),
        artifact.caption,
      ],
      applicationActivities: nil
    )
  }

  func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}

private final class SessionShareImageSource: NSObject, UIActivityItemSource {
  private let image: UIImage
  private let pngData: Data

  init(image: UIImage, pngData: Data) {
    self.image = image
    self.pngData = pngData
  }

  func activityViewControllerPlaceholderItem(
    _ activityViewController: UIActivityViewController
  ) -> Any {
    image
  }

  func activityViewController(
    _ activityViewController: UIActivityViewController,
    itemForActivityType activityType: UIActivity.ActivityType?
  ) -> Any? {
    pngData
  }

  func activityViewController(
    _ activityViewController: UIActivityViewController,
    dataTypeIdentifierForActivityType activityType: UIActivity.ActivityType?
  ) -> String {
    UTType.png.identifier
  }

  func activityViewControllerLinkMetadata(
    _ activityViewController: UIActivityViewController
  ) -> LPLinkMetadata? {
    let metadata = LPLinkMetadata()
    metadata.title = AppLocalization.string("result.share.preview_title")
    metadata.imageProvider = NSItemProvider(object: image)
    return metadata
  }
}
