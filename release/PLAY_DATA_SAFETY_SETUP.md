# PIYOKEY Google Play Data safety setup

Use this guide for the first analytics-enabled Android release. Recheck the actual Play Console wording at submission time and keep it aligned with the shipped build and the live privacy policy.

## Top-level answers

- Does the app collect or share required user data types? **Yes, collect**.
- Is all collected data encrypted in transit? **Yes** (HTTPS through PostHog/Firebase SDKs; verify on the release build).
- Can users request deletion? **Yes**, through the public support route described in the privacy policy. Explain the no-account/random-identifier limitation without promising deletion of an unidentifiable record.
- Is collection required? **No / optional** for every analytics and diagnostic category.
- Is data shared? Treat PostHog and Firebase as service providers processing on the developer's behalf, not sale or advertising sharing; verify the current Play definition before submitting.
- Ads or cross-app tracking: **No**.

## Data-type matrix

| Play category | Collected when | Purpose | Required | Sold / advertising |
|---|---|---|---|---|
| App activity → App interactions | Anonymous analytics enabled | Analytics | Optional | No |
| App activity → Other actions | Anonymous analytics enabled | Analytics | Optional | No |
| Financial info → Purchase history | Anonymous analytics enabled and a purchase-flow event occurs | Analytics | Optional | No |
| App info and performance → Crash logs | Crash diagnostics enabled | App functionality, Analytics | Optional | No |
| App info and performance → Diagnostics | Crash diagnostics enabled | App functionality, Analytics | Optional | No |
| Device or other IDs | Either option enabled | App functionality, Analytics | Optional | No |

Do not select approximate/precise location, personal information, contacts, messages, photos/videos, audio, files/documents, search history, web browsing, advertising data, health, or user-generated content for this telemetry implementation. `$geoip_disable=true` must be verified in received PostHog events before relying on the no-location answer.

## Console and release checks

1. Publish `release/PRIVACY_POLICY_ANALYTICS_DRAFT.md` at the Play listing Privacy URL.
2. Confirm the policy is public, non-geoblocked, readable without login, and has Japanese, English, and Korean content.
3. Verify analytics-only, diagnostics-only, both-on, and both-off traffic on the exact signed release candidate.
4. Confirm refusing the first notice does not limit the app and the choices remain available in Settings.
5. Confirm no notice appears during a lesson, game, result transition, document import/conflict flow, editor, or billing flow.
6. Save screenshots or exported Play Console answers as release evidence and set `play_data_safety_updated=true` only after the live form matches the build.
