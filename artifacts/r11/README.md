# PIYOKEY 1.1 user-deck UI captures

Captured on 2026-08-14 from the real SwiftUI app on an iPhone 17 / iOS 26.5 Simulator.
The app runs in Japanese because Japanese is the product's primary launch locale.

## Screens

1. `screenshots/01-import-preview-ja.png` — free `.piyodeck` validation and preview
2. `screenshots/02-my-decks-actions-ja.png` — free import and Deck Maker creation entry points
3. `screenshots/03-new-deck-editor-ja.png` — paid mobile deck editor
4. `screenshots/04-deck-maker-paywall-ja.png` — lifetime non-consumable purchase screen
5. `screenshots/05-delete-confirmation-ja.png` — safe delete with export-first and history-retention notice
6. `screenshots/06-import-conflict-ja.png` — downgrade warning with Keep Current as the primary action

The displayed JPY 1,500 price comes from the Debug StoreKit configuration and is not the
production App Store price.

## Reproduction

The captures are produced by these UI tests:

- `HancoUITests.testR11ImportAndEditorScreenshotCapture`
- `HancoUITests.testR11PaywallScreenshotCapture`
- `HancoUITests.testR11UserDeckDeleteCancelPreservesDeckAndConfirmationRemovesIt`
- `HancoUITests.testR11DowngradeImportShowsComparisonAndKeepCurrentIsSafePath`

Both capture-only data and entitlement overrides are compiled under `#if DEBUG`; Release
builds continue to rely exclusively on verified StoreKit transactions.
