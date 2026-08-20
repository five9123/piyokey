# PIYOKEY content and media rights record

This is the release evidence index for App Store Connect's **Content Rights** answer. Keep it with the exact RC source and archive checksum.

## First-party material

- **Learning decks and game text:** generated from the first-party source banks in `tools/gen_mock_catalog.py`. The launch catalog contains no artist, group, song, program, or character names and no copied lyrics or drama dialogue. K-pop- and K-drama-themed material is explicitly original, genre-inspired practice copy.
- **Spacing passages and UI copy:** authored for PIYOKEY and stored in the app localization resources.
- **Piyo character and UI illustrations:** rendered from the project's Swift drawing code. No third-party character sheet or runtime image is shipped.
- **App icon:** the developer-provided master is retained at `shared/brand/piyokey_app_icon_source.png`; the release preflight requires the shipped Asset Catalog file to match it byte-for-byte.
- **Store screenshots:** captured from the release-candidate app. They contain no third-party artwork, personal information, debug overlay, or device data.

## Licensed sound effect

`ios/Hanco/Hanco/Resources/Sounds/README.md` records the source URL, CC0 1.0 license URL, original checksum, conversion, and shipped checksum for the only externally sourced sound file. CC0 permits copying, modification, and commercial distribution without attribution.

## Korean pronunciation audio

- Korean prompts are first-party text.
- 581 bundled MP3 files (5,951,040 bytes) were generated with `gTTS 2.5.4` for every fixed pronunciation target provided by the app: official decks, all game presets, curriculum/daily practice, and the built-in free-practice sample. No Korean-pronunciation CAF files remain in the bundle.
- The app does not call gTTS or send prompt text to Google at runtime. It plays the reviewed files offline; private user-deck text remains on device and uses Apple's speech synthesizer only when no matching bundled file exists.
- gTTS itself is MIT-licensed, but its maintainers state that it uses undocumented Google Translate speech functionality and is not Google Cloud Text-to-Speech. The MIT license covers the client code, not a separate promise about Google service access or audio-output distribution.
- No celebrity, artist, actor, cloned, or user-provided voice is used. The files speak only the first-party Korean prompts and are used only as pronunciation aids inside PIYOKEY.
- The runtime fallback is Apple's on-device `AVSpeechSynthesizer(ko-KR)`. If the operator does not accept the remaining service-terms risk for pre-generated files, the release-safe contingency is to omit the bundled pronunciation files and use that runtime fallback, or regenerate them with a provider whose commercial output terms are explicit.

## Owner sign-off

Before setting `content_rights_confirmed` to `true`, the operator confirms all of the following:

- The icon master and character design may be commercially distributed as part of PIYOKEY.
- The generated deck and spacing text is original and has passed the no-real-lyrics/dialogue review.
- The CC0 sound provenance record is retained with the RC.
- The operator accepts the documented gTTS output terms risk for the exact release-candidate asset set, or replaces those files using the contingency above.

This record is operational evidence, not legal advice.

### Version 1.0 approval

- **Date:** 2026-07-26 (Asia/Tokyo)
- **Decision:** The operator confirmed that PIYOKEY holds the necessary rights for version 1.0 and accepts the documented gTTS/macOS synthesized-audio terms risk.
- **App Store Connect answer:** `Yes, it contains, shows, or accesses third-party content, and I have the necessary rights.`

### Version 1.1 expanded-audio review

- **Date generated:** 2026-08-15 (Asia/Tokyo)
- **Scope change:** 397 MP3 files were added or replaced and 108 legacy macOS-voice CAF files were removed, producing the 581-file set documented above.
- **Status:** Pending operator review and listening QA. The version 1.0 approval is retained as historical evidence and does not by itself approve this expanded asset set.
