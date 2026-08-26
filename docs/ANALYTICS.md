# PIYOKEY analytics and crash diagnostics

## Scope and privacy boundary

PIYOKEY uses PostHog Cloud EU for anonymous product analytics on iOS/iPadOS, Android, and web. Firebase Crashlytics handles native iOS/iPadOS crashes and Android crashes/ANRs; PostHog Error Tracking handles sanitized web exceptions.

`shared/analytics/events.json` is the only product-event allowlist. Run `python3 tools/gen_analytics_contract.py` after changing it, then commit the generated Swift, Kotlin, and TypeScript files. A platform must reject unknown properties before calling its SDK.

Both `Anonymous usage analytics` and `Crash diagnostics` are off by default and independent. Debug/test builds and builds without service configuration are no-op. Do not add a session-time consent modal.

Never capture typed or composing text, answers, target words, deck or item names/IDs, user-deck content/hash/path/URI, email/support content, receipts, account identifiers, advertising IDs, or free-form properties. Do not call PostHog `identify`, `alias`, `group`, or person-property APIs. Autocapture, page/screen capture, element/click capture, session replay, heatmaps, surveys, performance capture, feature-flag fetching, and mobile PostHog error tracking stay disabled.

## Environment configuration

Use one PostHog EU project for production and do not place secret personal API keys in apps. Project tokens are injected at build/deploy time.

- iOS/iPadOS: set `PIYOKEY_POSTHOG_PROJECT_TOKEN` in the Release xcconfig/build settings and add the Firebase Console-generated `ios/Hanco/Hanco/Resources/GoogleService-Info.plist` outside version control. Register bundle ID `app.piyokey.Piyokey` without enabling Google Analytics.
- Android: pass `PIYOKEY_POSTHOG_PROJECT_TOKEN`; keep `PIYOKEY_POSTHOG_HOST=https://eu.i.posthog.com`; place the Firebase-generated file at `android/app/google-services.json` outside version control. Register the final Play application ID without enabling Google Analytics.
- Web: provide the project token to `@piyokey/web-analytics` only in a production client build. The adapter is standalone because this repository has no web application source yet.

Firebase Analytics is not a dependency and must remain disabled. Firebase Crashlytics collection is explicitly false in both native manifests until the user enables diagnostics.

## Event and dashboard definitions

Create these PostHog dashboards from the semantic events:

1. Activation funnel: `app_opened` → `onboarding_step_completed(onboarding_step=first_input)` → `session_completed(session_kind=lesson)` → `onboarding_step_completed(onboarding_step=hatch_3)`.
2. Feature preference: unique anonymous devices and event counts for `feature_viewed.feature`, with platform and locale breakdowns.
3. Learning health: `session_started` to `session_completed`, completion rate by session kind, deck source, input mode, and duration/item buckets. `session_abandoned.reason` is a separate trend.
4. Games: `game_result` count and completion/result/score buckets by game mode and difficulty. Never send exact score or deck ID.
5. Deck and creation: `deck_downloaded` by category/source; `deck_maker_action` by action; `purchase_flow` funnel from viewed → started → completed/restored.

Retention uses an anonymous device-scoped ID only. Define D1/D7 return as a later `session_started` after the first completed session; do not create person profiles or merge devices.

Create alerts for:

- Firebase crash-free sessions below 99.5% on either native platform.
- New fatal issue or ANR affecting at least two events in 30 minutes.
- PostHog web exception spike above the trailing seven-day hourly baseline.

Stay within PostHog/Firebase free plans. Configure PostHog billing limits/notifications so usage does not silently become paid; enabling paid overage requires explicit user approval.

## Release gates

- Update the public privacy policy before enabling either service. It must name PostHog EU and Firebase Crashlytics, explain optional anonymous device identifiers, retention, opt-out, and the excluded content above.
- Update App Store Privacy to Product Interaction, Crash Data, and Device ID; all are not linked, not used for tracking, and collected only with consent.
- Update Google Play Data safety consistently and provide deletion/retention wording even though there is no account.
- Archive iOS with the real Firebase plist, force a consented test crash, verify the issue and dSYM symbolication, and confirm an opted-out install sends nothing.
- Build Android Release with the real Firebase JSON and Crashlytics Gradle plugin, force a consented test crash and ANR, verify R8 deobfuscation, and confirm an opted-out install sends nothing.
- Build the web app with production source maps, upload them to PostHog for the exact release, remove public `.map` assets, test a sanitized exception, and confirm analytics-only/diagnostics-only/off combinations.
- Record completion in `release/analytics_release_state.json`. `python3 tools/release_preflight.py --strict` remains blocked while any gate is false.

## Verification commands

```sh
python3 tools/gen_analytics_contract.py --check
python3 -m unittest tools.tests.test_analytics_contract
cd web/analytics && npm test
cd android && ./gradlew :core:analytics:test :core:settings:test :app:lintDebug :app:assembleRelease
xcodebuild test -project ios/Hanco/Hanco.xcodeproj -scheme Hanco -destination 'platform=iOS Simulator,name=iPhone 17,OS=26.5' -only-testing:HancoTests/AppSettingsTests
python3 tools/release_preflight.py
```
