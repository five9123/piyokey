# PIYOKEY deck pronunciation assets

## Current generation

- Generated: 2026-08-15
- Generator: `tools/gen_gtts_audio.py`, `gTTS==2.5.4`
- Settings: `lang=ko`, `tld=com`, normal speed
- Scope: every app-provided fixed Korean target in all 26 base/update catalog decks, all 15 game-preset decks, curriculum/daily content, and the free practice sample
- Format: 581 content-addressed gTTS MP3 assets bundled without transcoding
- Total size: 5,951,040 bytes (5.68 MiB)
- Migration: 184 existing MP3 assets retained, 397 assets generated, and 108 legacy macOS `Yuna` IMA4 CAF assets removed
- Migration command: `python3 tools/gen_gtts_audio.py --prune`
- Full regeneration: `python3 tools/gen_gtts_audio.py --force --prune`

Duplicate Korean prompts share a SHA-256 content-addressed canonical path. The
generator scans catalog and game-preset decks recursively, adds non-deck fixed
targets from `pronunciation_prompts.json`, writes downloads atomically, and
validates every MP3 with macOS CoreAudio before replacement. The iOS app never
calls gTTS or sends prompt text to Google at runtime; all fixed targets play from
the bundle offline.

The runtime resolver tries an item's declared `audio` path, then the target's
canonical hash path, then `AVSpeechSynthesizer(ko-KR)`. The final device fallback
covers missing or damaged assets and user/private dynamic content that has no
bundled canonical file. Entering the background stops active pronunciation;
practice resumes automatic pronunciation only when its setting is enabled, while
dictation replays the current prompt after its foreground countdown.

## Maintenance invariant

Every future app-provided fixed Korean target must use the same pinned gTTS
settings and canonical MP3 path. Add non-deck targets to
`pronunciation_prompts.json`, run `python3 tools/gen_gtts_audio.py --prune`, and
then run `python3 tools/release_preflight.py`. Do not add macOS/Yuna pronunciation,
pronunciation CAF, runtime gTTS, or device-TTS-only fixed content. A provider or
generation-setting change requires explicit product approval plus PRD and
DECISIONS updates. `AGENTS.md` 명령 게이트 기준으로 PR/머지 전 `xcodebuild`는 별도 조건이 아니고, 브랜치 병합 전 최소한 `python3 tools/release_preflight.py`는 통과되어야 합니다.

## Release gate

gTTS is MIT-licensed software, but it uses undocumented Google Translate speech
functionality and is not Google Cloud Text-to-Speech. Distribution and
content-use rights for the expanded 581-file set require review. Keep
`content_rights_confirmed` false until those rights are confirmed; otherwise
regenerate the same canonical assets with an approved TTS provider before App
Store submission.
