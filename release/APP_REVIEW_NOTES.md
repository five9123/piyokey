# typee / ピヨキー 1.1 App Review notes

Paste the English block below into **App Review Information → Notes**.

## Review notes

PIYOKEY requires no account, login, subscription, or demo credentials. Reviewers can use every core lesson and game with the built-in Dubeolsik keyboard without a purchase. The iOS Korean system keyboard is an optional input mode and is not required for review.

Version 1.1 adds one optional non-consumable in-app purchase:

- Product ID: `app.piyokey.deckmaker.lifetime`
- Japanese: `ピヨキー プロ`
- English: `typee pro`
- Korean: `피요키 프로`

This lifetime product unlocks installing a fourth distinct user deck, keeping unlimited user decks, creating a new personal deck, editing an imported personal deck, making an editable personal copy of an official deck, and saving those changes. The paywall uses StoreKit's localized price and includes Restore Purchases, the Apple Standard EULA, and the privacy policy. There is no web checkout, account-based license, external activation key, or custom payment server.

The first launch contains a short introduction followed by three guided hatch missions covering consonants, vowels, and syllable composition. Completing these missions opens the five-tab app. This is normal product onboarding and does not require network access.

The app includes six games: Flow, Word Rain, Initials Quiz, Word Quiz, Dictation, and Korean Spacing. The first five offer bundled 100-word sets organized by Korean typing difficulty (Beginner, Intermediate, and Advanced). Korean Spacing uses original bundled passages and does not require a deck.

Daily reminders are off by default. Notification permission is requested only after the reviewer explicitly enables reminders. Game Center is optional. Saving a result image requests add-only Photos permission only after the reviewer chooses the save action.

Korean pronunciation uses bundled pre-generated audio and falls back to the device's ko-KR speech synthesizer if an asset is unavailable. The app never records microphone audio.

The catalog is read-only static content. All launch content is also bundled, so lessons and games remain usable offline if the network catalog is unavailable.

Version 1.1 also recognizes `.typedeck` local document files. Validation and preview are always free, and users may keep up to three distinct user decks installed without purchase. Practicing/playing, exporting, deleting, and replacing the same deck ID remain free. A fourth distinct user deck requires typee pro; deleting one of the three restores a free slot. Purchase status is not written into the document. Files are opened from or exported to Files, iCloud Drive, AirDrop, or the iOS share sheet and are stored locally on device.

Personal decks are not public user-generated content: PIYOKEY has no upload service, public user catalog, search/indexing, social feed, comments, messaging, moderation service, or remote write API. The only network catalog remains first-party, static, and read-only. There is no advertising or cross-company tracking.

After onboarding, the hatch missions, and the app tour, an idle Home screen presents one privacy-choice notice. Both anonymous usage analytics and crash diagnostics are off by default and can be enabled independently. Reviewers may tap **Continue without sharing** and retain access to every lesson, game, local deck feature, and purchased feature. The same choices and the privacy-policy link remain available in Settings. PIYOKEY sends no typed text, answers, searches, user-deck names or content, contact information, advertising ID, recordings, or session replay. Product events use PostHog Cloud EU with IP geolocation disabled; native crash diagnostics use Firebase Crashlytics only when separately enabled.

Privacy policy: https://typee.app/privacy

Support: https://typee.app/support

## Contact and submission fields

- Contact name: Jungmin Oh
- Contact email: contact@typee.app
- Contact phone: +82 10-5091-0203 (App Store Connect private review contact)
- Demo account: not required
- Copyright: 2026 Jungmin Oh

## Reviewer smoke path

1. Launch the app and complete the introductory typing prompt.
2. Complete the three guided hatch missions using the built-in keyboard.
3. Open `れんしゅう` for lessons or free practice.
4. Open `ゲーム` and select any mode and bundled level set.
5. Open Settings from the top-right button to change sound, input mode, language, or open Privacy and Support.

## typee pro and restore smoke path

1. Complete onboarding, open **My Page**, and find the **Import** and **Create deck** actions in the My Decks section.
2. Tap **Create deck**. The typee pro paywall appears outside any lesson/game session.
3. Purchase `app.piyokey.deckmaker.lifetime` with the App Review sandbox account. Confirm that the localized StoreKit price—not a hard-coded price—is shown.
4. Create and save a small deck. Open its menu to edit it, export it as `.typedeck`, practice it, and delete/re-import it.
5. To exercise restore, use **Restore Purchases** on the same paywall after reinstalling or on another test device signed into the same sandbox account. A successful restore closes the paywall and opens the requested creator/editor action.
6. To confirm the free boundary, import three different valid `.typedeck` deck IDs without purchase. Preview a fourth and confirm that typee pro appears only when **Import** is tapped. Confirm that replacing the same ID, practicing, exporting, and deleting remain free, and that deleting one of the three restores one free import slot.

If the purchase is refunded or revoked during testing, unlimited installs and create/edit access are removed after StoreKit's transaction update. Existing personal decks remain installed and usable; typee / ピヨキー does not delete purchased work or local documents when entitlement is lost.
