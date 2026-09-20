# Country and usage-context rollout — Issue #180

## Final scope

Usage notice v2 adds standard device/OS/screen/app-distribution/language/time-zone/connection fields and a random session identifier to the existing product events. Geographic enrichment is **country name and code only**. Region, city, postcode, coordinates, accuracy radius, IP-derived time zone, raw IP, person properties, user-assigned device names, advertising identifiers, free text and replay are excluded. Exact fields are in `usage_context.json`; future SDK fields are rejected unless this contract is deliberately updated.

The device time zone is the device setting, not an IP-derived location. Country is inferred from the connection IP and can be wrong for VPN/mobile traffic; never treat it as citizenship or residence. Session IDs are random app-session identifiers and are not linked across companies or to accounts.

## Consent behavior

- New installations: both independent sharing choices default OFF; the existing two-button initial choice remains.
- Existing installations: notice v2 appears once after onboarding/tour on Home. It asks about expanded usage analytics and preserves the diagnostic choice on either button. No repeated notification request or session interruption.
- A manual analytics toggle in Settings uses the updated explanation and records v2. The diagnostic toggle does not advance the analytics notice version.
- Only events captured with v2 usage consent get `piyokey_usage_context_consent_version=2`. Transport rechecks current permission before retaining expanded fields. Older queued events are never upgraded retrospectively.
- Revocation stops new analytics using the existing SDK opt-out path. Already ingested data remains subject to the published retention/deletion process.

## Production configuration

Project: [PIYOKEY Production, EU 258358](https://eu.posthog.com/project/258358).
Transform: [PIYOKEY country and usage context v2](https://eu.posthog.com/project/258358/functions/01a086f4-7fed-0000-6e20-fb1198493205).
Source: `posthog_usage_context.hog`.

1. Keep **Discard client IP data ON** in project privacy settings. The console confirms enrichment can use IP transiently before it is discarded.
2. Place this transformation before built-in GeoIP. Keep `$geoip_disable=true` and `$process_person_profile=false` on the client and in the returned event; never enable general GeoIP as a fallback.
3. The transformation reconstructs iOS/iPadOS properties from the per-event semantic allowlist and the consented SDK allowlist. It performs lookup only with exact consent version 2 and the two privacy control flags. It emits only the two country fields and never logs IP/lookup contents. Non-iOS traffic is returned unchanged.
4. The currently shipped 1.1 client has no new consent marker, so server setup alone does not enable country collection for existing users. The next reviewed app update is required.
5. If rollback is needed, disable this custom transformation. General GeoIP remains blocked and IP discard stays ON. No collection setting needs to be broadened.

## Verification and release checklist

Local unit tests cover old/current/revoked consent, per-event and SDK allowlists, pre-consent queues, diagnostic independence, and privacy manifest. iPhone/iPad UI tests cover initial choice, both upgrade choices, persistence, and ja/en/es/de/fr layout. Python tests keep the client/Hog allowlists synchronized with the semantic contract.

In the PostHog transformation test harness use the official sample IP `89.160.20.129`, artificial IDs and deliberately injected forbidden fields. Verify consent v2 keeps environment and Sweden/SE only; missing/old consent drops environment and all geo; missing/invalid IP keeps permitted context without country; iPad follows the same contract. Test harness output is not proof of storage from a signed app.

Before shipping the next build:

- Publish `release/PRIVACY_POLICY_ANALYTICS_DRAFT.md` at the live privacy URL and verify processor region, retention and deletion handling. The draft is not yet effective.
- Update App Store Privacy with Coarse Location for Analytics; retain the other accurately declared analytics/diagnostic/device categories and Tracking: No. Reassess identity linkage against the final payload and processor settings; an anonymous label alone is not sufficient evidence. Confirm device context classification alongside existing Other Diagnostic Data/Other Usage Data.
- On a signed iPhone and iPad, verify fresh install OFF, initial consent, v1 upgrade accept/decline, diagnostics independence, Settings changes, relaunch and opt-out network silence. Confirm no ATT or location prompt.
- Inspect actual stored events: country only, accepted environment fields, no region/city/postcode/coordinates/IP/person update, no expanded fields before new consent. Do not interpret missing country as an ingestion failure when lookup has no match.
- Firebase remains Crashlytics only. An empty Firebase Analytics dashboard is expected. Complete a consented test crash, relaunch and dSYM symbolication verification separately.
- Close `usage_context_transform_verified`, `usage_context_reconsent_verified` and `usage_context_app_store_privacy_updated` only with the corresponding signed-app/storage/store evidence. Existing unrelated external gates remain open.

## Reference sources

- [PostHog iOS SDK](https://posthog.com/docs/libraries/ios) and pinned `PostHogContext.swift` in SDK 3.69.5 provide the standard environment fields.
- [PostHog GeoIP template](https://github.com/PostHog/posthog/blob/master/nodejs/src/cdp/templates/_transformations/geoip/geoip.template.ts) provides the lookup response shape. Its general enrichment/person writes are intentionally not enabled.
- [Apple App Privacy details](https://developer.apple.com/app-store/app-privacy-details/) defines coarse location, data linkage and required disclosures.
- [Apple User Privacy and Data Use](https://developer.apple.com/app-store/user-privacy-and-data-use/) defines tracking and ATT requirements.

The pinned SDK runs `beforeSend` at capture time and does not purge/stop its queue in `optOut()`. PIYOKEY therefore closes the SDK on withdrawal and installs a consent-checking URLProtocol only on its PostHog session. A request dispatched while usage sharing is OFF completes locally without networking, so stale batches are not retried over the network. Requests already dispatched before withdrawal cannot be recalled; signed-device network verification remains required. Re-enabling sharing configures the SDK again.
