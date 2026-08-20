import Combine
import Foundation
import QuartzCore
import SwiftUI
import UIKit

#if DEBUG
  struct GameFrameRateSnapshot: Equatable {
    let sampleCount: Int
    let averageFPS: Double
    let p95FrameDurationMilliseconds: Double
    let worstFrameDurationMilliseconds: Double
    let overBudgetFrameCount: Int
  }

  enum GameFrameRateCalculator {
    static func snapshot(timestamps: [CFTimeInterval]) -> GameFrameRateSnapshot? {
      guard timestamps.count >= 2 else { return nil }

      let intervals = zip(timestamps, timestamps.dropFirst())
        .map { $1 - $0 }
        .filter { $0 > 0 }
      guard !intervals.isEmpty else { return nil }

      let sortedIntervals = intervals.sorted()
      let p95Index = max(0, Int(ceil(Double(sortedIntervals.count) * 0.95)) - 1)
      let totalDuration = intervals.reduce(0, +)

      return GameFrameRateSnapshot(
        sampleCount: intervals.count,
        averageFPS: Double(intervals.count) / totalDuration,
        p95FrameDurationMilliseconds: sortedIntervals[p95Index] * 1_000,
        worstFrameDurationMilliseconds: (sortedIntervals.last ?? 0) * 1_000,
        overBudgetFrameCount: intervals.count(where: { $0 > 0.020 })
      )
    }
  }

  @MainActor
  final class GameFrameRateMonitor: ObservableObject {
    @Published private(set) var snapshot: GameFrameRateSnapshot?

    private var timestamps: [CFTimeInterval] = []
    private var framesSincePublish = 0
    private let sampleLimit: Int

    init(sampleLimit: Int = 600) {
      self.sampleLimit = max(120, sampleLimit)
    }

    func record(timestamp: CFTimeInterval) {
      timestamps.append(timestamp)
      if timestamps.count > sampleLimit + 1 {
        timestamps.removeFirst(timestamps.count - sampleLimit - 1)
      }

      framesSincePublish += 1
      guard timestamps.count >= 121, framesSincePublish >= 30 else { return }

      snapshot = GameFrameRateCalculator.snapshot(timestamps: timestamps)
      framesSincePublish = 0
    }

    func reset() {
      timestamps.removeAll(keepingCapacity: true)
      framesSincePublish = 0
      snapshot = nil
    }
  }

  struct GameFrameRateProbe: UIViewRepresentable {
    let onFrame: (CFTimeInterval) -> Void

    func makeCoordinator() -> Coordinator {
      Coordinator(onFrame: onFrame)
    }

    func makeUIView(context: Context) -> UIView {
      let view = UIView(frame: .zero)
      view.isUserInteractionEnabled = false
      view.isAccessibilityElement = false
      context.coordinator.start()
      return view
    }

    func updateUIView(_ uiView: UIView, context: Context) {
      context.coordinator.onFrame = onFrame
    }

    static func dismantleUIView(_ uiView: UIView, coordinator: Coordinator) {
      coordinator.invalidate()
    }

    @MainActor
    final class Coordinator: NSObject {
      var onFrame: (CFTimeInterval) -> Void

      private var displayLink: CADisplayLink?

      init(onFrame: @escaping (CFTimeInterval) -> Void) {
        self.onFrame = onFrame
      }

      func start() {
        guard displayLink == nil else { return }

        let displayLink = CADisplayLink(target: self, selector: #selector(frameDisplayed))
        displayLink.preferredFrameRateRange = CAFrameRateRange(
          minimum: 60,
          maximum: 60,
          preferred: 60
        )
        self.displayLink = displayLink
        displayLink.add(to: .main, forMode: .common)
      }

      func invalidate() {
        displayLink?.invalidate()
        displayLink = nil
      }

      @objc private func frameDisplayed(_ displayLink: CADisplayLink) {
        onFrame(displayLink.timestamp)
      }
    }
  }
#endif
