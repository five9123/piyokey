# typee / ピヨキー App Store Connect values

This file separates the next `1.1` submission contract from the retained `1.0.2`
submission history. Do not edit the historical App Store version record when preparing
`1.1`; create a new version and attach the new in-app purchase to that submission.

## App record — version 1.1

| Field | Value |
|---|---|
| Platforms | iOS |
| Name | Localized; see `release/global_app_store_metadata.json` |
| Primary language | Japanese currently; planned English (U.S.) after the required approved-localization bridge |
| App Store Connect Apple ID | `6794853985` |
| Apple Developer Team | `X44BQNTAH9` |
| Bundle ID | `app.piyokey.Piyokey` |
| SKU | `piyokey-ios-001` |
| User access | Full Access |
| Version | `1.1` (project build 7; increment before archive if that build number is no longer available) |
| Primary category | Education |
| Secondary category | Games / Word |
| Price | Free download with one optional non-consumable in-app purchase |
| Release | Pending separate decision: choose manual or automatic after approval before submission; availability does not determine release timing |
| Availability | **All Countries or Regions**, including future App Store storefronts |
| Made for Kids | No |
| Copyright | 2026 Jungmin Oh |

Use `release/global_app_store_metadata.json` for the 1.1 rollout. App UI and content remain
limited to Japanese, English, and Korean. App Store metadata is prepared for English
(U.S.), English (U.K.), English (Australia), English (Canada), Korean, and Japanese;
other UI and metadata-only localizations are deferred until post-launch demand review.

## URLs

- Marketing: https://hancoweb.vercel.app/
- Privacy policy: https://hancoweb.vercel.app/privacy
- Support: https://hancoweb.vercel.app/support

The Privacy and Support pages offer Japanese, English, and Korean variants. The same links are available from the app's Settings screen.

## typee pro in-app purchase — required for 1.1

Create the product under the existing app record before uploading the final review
screenshot. Product ID and product type cannot be changed after creation, so verify the
following values character for character:

| Field | Value |
|---|---|
| Reference name | `typee pro Lifetime` |
| Product ID | `app.piyokey.deckmaker.lifetime` |
| Type | **Non-Consumable** |
| Availability | **All Countries or Regions**, matching the app and including future storefronts |
| Price | Select the approved launch price in App Store Connect; the app must display StoreKit's localized `displayPrice` |
| Family Sharing | Leave off unless the product owner explicitly approves it as part of the purchase promise |

Add at least these three App Store Connect localizations:

| Locale | Display name | Description |
|---|---|---|
| Japanese | `ピヨキー pro` | `ユーザーデッキ無制限と作成・編集をずっと利用` |
| English (U.S.) | `typee pro` | `Unlimited user decks, creation, and editing.` |
| Korean | `피요키 프로` | `사용자 덱 무제한 보관과 생성·편집을 평생 이용` |

Complete these App Store Connect gates before submission:

1. Make sure the Paid Applications agreement, tax information, and banking information
   are active for team `X44BQNTAH9`.
2. Record whether the account is enrolled in Apple's Small Business Program and confirm
   the expected commission with the Account Holder. Enrollment does not change the
   product ID, type, or client implementation, but its status must not be assumed.
3. Add the product's price, storefront availability, and the appropriate tax category.
   Do not use the DEBUG preview price as production metadata, and do not guess a tax
   category without the account holder's tax review.
4. Upload an App Review screenshot from the 1.1 build showing the typee pro paywall,
   its feature list, the localized StoreKit price, **Restore Purchases**, Terms, and
   Privacy links.
5. Save every required localization, price, availability, tax field, review note, and
   screenshot, then confirm that the product status is **Ready to Submit**. Do not attach
   a product that still shows **Missing Metadata** or another incomplete state.
6. Add the product to **In-App Purchases and Subscriptions → Add for Review** on the
   version `1.1` submission and verify that the final review draft contains both the iOS
   app version and `app.piyokey.deckmaker.lifetime`. Because this is the app's first
   in-app purchase, submit it with the new app version rather than as an independent
   product review.
7. In sandbox/TestFlight, verify purchase success, cancellation, pending/Ask to Buy,
   restore after reinstall, relaunch entitlement, refund/revocation, and a product-load
   failure. Revocation must remove create/edit access without deleting existing decks.

The reviewer path is: complete onboarding → open **My Page** → tap **Create deck**, import a fourth distinct user deck, or
choose **Edit** / **Edit a copy** from a deck menu → paywall → purchase. **Restore
Purchases** is on the same paywall. A purchase unlocks unlimited installed user decks,
in-app creation, editing, official-deck copy creation, and saving those changes.

`.piyodeck` is a local document, not a paid content container. Validation and preview,
up to three installed user deck IDs, replacement using the same deck ID, practice/game
use, export, and deletion remain free. A fourth distinct ID requires typee pro, and
deleting a user deck restores a free slot. Documents move only through Files, iCloud Drive, AirDrop, or the
iOS share sheet; the app has no account, upload service, public catalog publishing,
social feed, remote write API, or purchase/license flag inside the file.

### Local StoreKit development checks

The shared `Hanco` scheme selects `Hanco/Resources/DeckMaker.storekit` only for its
Debug **Run** action. Its JPY 1,500 value is test data, not a production pricing
decision. Profile and Archive continue to use App Store Connect products and are not
linked to the local configuration.

Before setting `deck_maker_local_storekit_check=true` in
`release/app_store_submission.json`, run the app from Xcode and verify:

1. The Japanese, English, and Korean product name/description and StoreKit-generated
   price render correctly on the paywall.
2. A normal purchase unlocks create, edit, and edit-a-copy, while cancelling leaves
   those actions locked and does not show a false success state.
3. Enable **Ask to Buy** in the StoreKit configuration settings to exercise `.pending`,
   then approve or decline it from **Debug → StoreKit → Manage Transactions**.
4. Relaunch and restore the non-consumable. A successful restore closes the paywall and
   continues into the requested creator/editor action.
5. Refund the test transaction from **Manage Transactions**. The transaction update
   removes creator/editor access without deleting existing decks.
6. Reconfirm that ordinary `.piyodeck` import, preview, use, export, replacement, and
   deletion stay free in every state above.

Repeat purchase, cancellation, pending, restore, and refund/revocation with an App
Store sandbox account or the exact TestFlight candidate before submission. Local
StoreKit results do not replace those server-backed release gates.

Apple references:

- [Create a consumable or non-consumable in-app purchase](https://developer.apple.com/help/app-store-connect/manage-in-app-purchases/create-consumable-or-non-consumable-in-app-purchases/)
- [Submit an in-app purchase](https://developer.apple.com/help/app-store-connect/manage-submissions-to-app-review/submit-an-in-app-purchase)
- [In-app purchase information](https://developer.apple.com/help/app-store-connect/reference/in-app-purchases-and-subscriptions/in-app-purchase-information/)
- [Set a price](https://developer.apple.com/help/app-store-connect/manage-in-app-purchases/set-a-price-for-an-in-app-purchase/)
- [Set up StoreKit testing in Xcode](https://developer.apple.com/documentation/xcode/setting-up-storekit-testing-in-xcode/)
- [Age-rating values and definitions](https://developer.apple.com/help/app-store-connect/reference/app-information/age-ratings-values-and-definitions/)
- [Set a tax category](https://developer.apple.com/help/app-store-connect/manage-app-information/set-a-tax-category/)

## App Privacy

Choose **Yes, data is collected from this app** and **Tracking: No**. Collection is optional, off by default, and split between anonymous product analytics and crash diagnostics. Enter the following types exactly as represented by `release/app_store_metadata.json` and the bundled privacy manifest:

| Data type | Purpose | Linked to user | Tracking |
|---|---|---|---|
| Product Interaction | Analytics | No | No |
| Other Usage Data | Analytics | No | No |
| Gameplay Content | Analytics | No | No |
| Purchase History | Analytics | No | No |
| Crash Data | App Functionality, Analytics | No | No |
| Other Diagnostic Data | App Functionality, Analytics | No | No |
| Device ID | App Functionality, Analytics | No | No |

Do not declare typed/composing text, answers, searches, deck/item identifiers, user-deck names or contents, names, email addresses, advertising identifiers, recordings, session replay, or location. Product events go only to PostHog Cloud EU with IP geolocation disabled; native crash diagnostics go to Firebase Crashlytics. Firebase Analytics and advertising SDKs are not included.

Before submitting, publish `release/PRIVACY_POLICY_ANALYTICS_DRAFT.md` at the Privacy URL and confirm the live page no longer says that the app has no analytics SDK or device identifiers. Verify that the first-launch choice notice and Settings withdrawal controls match the submitted build. Local learning state and `.piyodeck` documents remain on device. StoreKit purchase processing, optional Game Center processing, and user-initiated support mail are separate platform/user actions described by the privacy policy.

## Age rating

Answer **Frequent** for Contests because the weekly Piyo Cup and Game Center leaderboards let users compete for rankings. Answer **None** for simulated gambling, violence, sexual content, profanity, controlled substances, horror, medical content, and all other frequency descriptors. Answer **No** for unrestricted web access, user-generated content, social media, messaging/chat, advertising, gambling, and loot boxes. App Store Connect currently calculates **13+** under the current rating system and maps it to **12+** on operating systems earlier than version 26 (with regional exceptions).

For version 1.1, answer **No** for public user-generated content: a user can create or
import a local document, but the app does not upload, publish, index, recommend, message,
or share it through a typee / ピヨキー service. If a later version adds server distribution or a
public user catalog, update the age-rating and privacy answers before review and ship
moderation, reporting, blocking, and operator-contact controls with that version.

## Content rights and advertising identifier

- Content rights: **Yes, typee / ピヨキー contains/uses only content it has the necessary rights to use**, after the owner sign-off in `release/CONTENT_RIGHTS.md`.
- Advertising identifier: **No**.
- Digital Services Act trader status: declare and verify it before relying on EU availability. `All Countries or Regions` includes the EU.
- China mainland and Vietnam: keep the global selection, but verify each storefront's compliance status and complete any applicable filing, game registration, or license. Do not treat a selected but action-required storefront as available.

## Export compliance

The app does not implement non-exempt encryption. `ITSAppUsesNonExemptEncryption` is `NO`; networking uses Apple platform HTTPS only.

## Review information

Use `release/APP_REVIEW_NOTES.md`. No demo account is required. Include the typee pro / ピヨキー pro
non-consumable in the same review submission. The direct review phone number remains an
account-only field and must be entered in international format.

## Game Center

Create the leaderboards and achievements exactly as listed in `release/GAME_CENTER_SETUP.md` before selecting the build for review. Acid Rain, Initials Quiz, and Dictation use `piyokey.v3.*`; the faster Flow/Piyo Cup and direct-typing Word Quiz use their respective `piyokey.v4.*` contracts.

## Archive and export

Create the next archive from the `Hanco` scheme with version `1.1` and an unused build
number (the project currently uses build 7). Export a local IPA with
`release/ExportOptions.plist` or upload the same archive with
`release/ExportOptionsUpload.plist`; automatic signing must resolve team `X44BQNTAH9`
and Bundle ID `app.piyokey.Piyokey`. Verify that the app target has the **In-App
Purchase** capability. A validated archive may be uploaded to TestFlight while manual
device gates are still open so the exact build can be tested. Do not submit it until
strict preflight and every 1.1 manual gate, including the in-app purchase gates above,
is complete.

After processing completes in App Store Connect, install that exact build from TestFlight and complete `release/TESTFLIGHT_SMOKE.md`. Record the archive checksum and do not rebuild between the passing TestFlight run and submission.

## Historical 1.0–1.0.2 submission record (retained)

Version `1.0 (1)` was uploaded successfully on 2026-07-26, then superseded by the Game Center 5-game × 3-difficulty contract correction. Build 2 contained the earlier 4-choice Word Quiz scoring contract and was superseded in turn. Build 3 was submitted with App Store version 1.0. Version `1.0.1 (4)` was approved and is Ready for Distribution. The release candidate at the time of the first 1.0.2 snapshot was `1.0.2 (5)`; that submission was removed as Developer Rejected, and its corrected successor `1.0.2 (6)` was submitted under ID `45184f9b-494c-431e-a740-a3dde9080f4a`.

This historical paragraph is evidence for the earlier submission only. Do not overwrite
the existing 1.0.2 submission record or reuse it to introduce the 1.1 in-app purchase.
