// swift-tools-version: 5.10

import PackageDescription

let package = Package(
  name: "HangulEngine",
  platforms: [
    .iOS(.v16),
    .macOS(.v13),
  ],
  products: [
    .library(name: "HangulEngine", targets: ["HangulEngine"]),
    .library(name: "DeckKit", targets: ["DeckKit"]),
  ],
  targets: [
    .target(name: "HangulEngine"),
    .target(name: "DeckKit", dependencies: ["HangulEngine"]),
    .testTarget(name: "HangulEngineTests", dependencies: ["HangulEngine"]),
    .testTarget(name: "DeckKitTests", dependencies: ["DeckKit", "HangulEngine"]),
  ]
)
