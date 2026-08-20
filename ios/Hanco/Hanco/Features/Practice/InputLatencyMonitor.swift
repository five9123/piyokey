import Combine
import Foundation
import QuartzCore
import SwiftUI
import UIKit

#if DEBUG
  @MainActor
  final class InputLatencyMonitor: ObservableObject {
    @Published private(set) var inputRevision = 0
    @Published private(set) var p95Milliseconds: Double?
    @Published private(set) var sampleCount = 0

    private var pendingStarts: [CFTimeInterval] = []
    private var samples: [Double] = []
    private let sampleLimit: Int

    init(sampleLimit: Int = 120) {
      self.sampleLimit = max(1, sampleLimit)
    }

    func beginInput(timestamp: CFTimeInterval = CACurrentMediaTime()) {
      pendingStarts.append(timestamp)
      inputRevision += 1
    }

    func finishPendingInputs(timestamp: CFTimeInterval = CACurrentMediaTime()) {
      guard !pendingStarts.isEmpty else { return }

      samples.append(
        contentsOf: pendingStarts.map { max(0, timestamp - $0) * 1_000 }
      )
      pendingStarts.removeAll(keepingCapacity: true)

      if samples.count > sampleLimit {
        samples.removeFirst(samples.count - sampleLimit)
      }

      sampleCount = samples.count
      let sortedSamples = samples.sorted()
      let percentileIndex = max(0, Int(ceil(Double(sortedSamples.count) * 0.95)) - 1)
      p95Milliseconds = sortedSamples[percentileIndex]
    }

    func reset() {
      pendingStarts.removeAll(keepingCapacity: true)
      samples.removeAll(keepingCapacity: true)
      sampleCount = 0
      p95Milliseconds = nil
    }
  }

  struct DisplayRefreshProbe: UIViewRepresentable {
    let inputRevision: Int
    let onNextFrame: (CFTimeInterval) -> Void

    func makeCoordinator() -> Coordinator {
      Coordinator(onNextFrame: onNextFrame)
    }

    func makeUIView(context: Context) -> UIView {
      let view = UIView(frame: .zero)
      view.isUserInteractionEnabled = false
      view.isAccessibilityElement = false
      return view
    }

    func updateUIView(_ uiView: UIView, context: Context) {
      context.coordinator.onNextFrame = onNextFrame
      context.coordinator.scheduleFrame(for: inputRevision)
    }

    static func dismantleUIView(_ uiView: UIView, coordinator: Coordinator) {
      coordinator.invalidate()
    }

    @MainActor
    final class Coordinator: NSObject {
      var onNextFrame: (CFTimeInterval) -> Void

      private var latestRevision = 0
      private var displayLink: CADisplayLink?

      init(onNextFrame: @escaping (CFTimeInterval) -> Void) {
        self.onNextFrame = onNextFrame
      }

      func scheduleFrame(for revision: Int) {
        guard revision > latestRevision else { return }
        latestRevision = revision
        guard displayLink == nil else { return }

        let displayLink = CADisplayLink(target: self, selector: #selector(frameDisplayed))
        self.displayLink = displayLink
        displayLink.add(to: .main, forMode: .common)
      }

      func invalidate() {
        displayLink?.invalidate()
        displayLink = nil
      }

      @objc private func frameDisplayed() {
        invalidate()
        onNextFrame(CACurrentMediaTime())
      }
    }
  }
#endif
