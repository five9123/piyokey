# PIYOKEY analytics and crash diagnostics

## Scope and privacy boundary

PIYOKEY uses PostHog Cloud EU for anonymous product analytics on iOS/iPadOS, Android, and web. Firebase Crashlytics handles native iOS/iPadOS crashes and Android crashes/ANRs; PostHog Error Tracking handles sanitized web exceptions.

`shared/analytics/events.json` is the only product-event allowlist. Run `python3 tools/gen_analytics_contract.py` after changing it, then commit the generated Swift, Kotlin, and TypeScript files. A platform must reject unknown properties before calling its SDK.

Both `Anonymous usage analytics` and `Crash diagnostics` are off by default and independent. Debug/test builds and builds without service configuration are no-op. Do not add a session-time consent modal.

The first privacy notice is versioned. On iOS, complete onboarding, the three hatch missions, and the app tour, request notification permission once, then show the notice once before Home. The notice has no toggles or provider terminology: **Participate and continue** enables both analytics and diagnostics, while **Continue without sharing** disables both. Refusing does not limit lessons, games, local decks, or purchased features. Settings keeps the two independent toggles and the privacy-policy link; it does not show a separate review button. `PrivacyNoticePolicy.currentVersion` is 2 for expanded usage-context analytics. Existing installations choose usage sharing again once on Home, preserving their independent diagnostics choice. Updating diagnostics alone never marks the usage notice reviewed. No notification permission is requested again for this update.

Never capture typed or composing text, answers, target words, deck or item names/IDs, user-deck content/hash/path/URI, email/support content, receipts, account identifiers, advertising IDs, or free-form properties. Do not call PostHog `identify`, `alias`, `group`, or person-property APIs. Autocapture, page/screen capture, element/click capture, session replay, heatmaps, surveys, performance capture, feature-flag fetching, and mobile PostHog error tracking stay disabled.

Every PostHog payload still sets `$geoip_disable=true`, including semantic product events and sanitized web exceptions. This blocks the built-in GeoIP transform and its person-property writes. Only iOS/iPadOS events captured after notice v2 usage consent carry `piyokey_usage_context_consent_version=2` and the standard SDK fields enumerated in `release/analytics/usage_context.json`. They include device model/type, OS, screen dimensions, app/build/distribution environment, language, time zone, connection type and a random session ID. User-assigned device names, advertising identifiers and arbitrary future SDK properties remain excluded.

A dedicated ingestion transformation extracts only country name and country code. It excludes every subnational region, city, postcode, coordinates, accuracy radius and IP-derived time zone. Device time zone is a standard device setting, not inferred location. These fields depend on database coverage and can be inaccurate with VPNs or mobile networks; they are neither GPS nor proof of residence. The transform strips the raw IP, all client-supplied geo fields, person updates, unknown properties and unconsented context. The project's **Discard client IP data** must remain enabled. A missing or disabled transform produces no geo enrichment. Never retroactively add the consent marker to queued pre-consent events. Revocation strips expanded context from events still passing the client transport.

The shared semantic-event schema stays v1. The consent marker and SDK context are transport fields, and geolocation is server enrichment; their exact contract is `release/analytics/usage_context.json` and `posthog_usage_context.hog`. Web has no marker and retains its current boundary; frozen Android is unchanged. See `release/analytics/USAGE_CONTEXT_ROLLOUT.md` for storage, UI, policy and release verification.

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

- Publish the copy in `release/PRIVACY_POLICY_ANALYTICS_DRAFT.md` at the public Privacy URL before enabling either service. The currently published page must not claim that the app has no analytics SDK or device identifiers. Confirm the named processors, retention periods, opt-out/withdrawal, deletion-request handling, and excluded content against the production projects.
- In App Store Connect answer **Yes, data is collected** and **Tracking: No**. Declare Product Interaction, Other Usage Data, Gameplay Content, Purchase History, Crash Data, Other Diagnostic Data, and Device ID. Mark every type **not linked to the user** and **not used for tracking**. Product/usage/gameplay/purchase events are for Analytics; crash/diagnostic data and device identifiers are for App Functionality and Analytics. Declare **Coarse Location** for Analytics for location-enriched iOS/iPadOS releases; verify identity linkage against the final payload and processor settings. Do not declare precise location, typed content, contacts, or advertising data.
- In Google Play Data safety declare App interactions, Other actions, Purchase history, Crash logs, Diagnostics, and Device or other IDs as optional collection. Mark data as not sold, not used for advertising, and processed for Analytics or App functionality as applicable. Do not declare location while `$geoip_disable` is verified. The form, privacy policy, and shipped SDK configuration must match exactly.
- Verify the notification-permission → one-time notice → Home sequence and Settings re-entry in ja/en/es/de/fr plus the retained Korean source: both notice buttons, both resulting stored values, independent Settings changes, persistence after relaunch, refusal without feature loss, and no appearance during any lesson/game/file/import/editor/paywall flow. Confirm no ATT prompt or IDFA use.
- Set the production PostHog event retention to 12 months and confirm deletion thereafter. Confirm Firebase Crashlytics retention against the current Firebase policy (currently 90 days for crash reports) before publishing. Document how support handles deletion requests without an account and never promise that disabling collection retroactively erases already-sent records.
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
