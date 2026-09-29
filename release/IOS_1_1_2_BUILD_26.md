# iOS 1.1.2 (26) — Deck Maker draft recovery

2026-09-30 JST. **App Review submitted: Waiting for Review.**
Public release remains subject to Apple approval and the existing manual release setting.

## App change

After a Deck Maker purchase, an incomplete draft could reopen the editor every time
My Page became active, even after the user explicitly closed it. Closing the editor
now records that draft's identity and suppresses automatic reopening across tab
changes and app restarts. The draft remains available through New Deck/Edit.
Explicitly resuming it restores automatic recovery if that editing session is interrupted.
Failed draft persistence does not close or suppress the editor.

This patch does not change purchase entitlements, analytics consent, or collection.
The separately diagnosed language-retagging data loss and Korean composition-input
issues are outside this popup fix; they must not be described as fixed by this release.

## PostHog onboarding issue included in this release record

The previous onboarding funnel could not measure events occurring before analytics
consent and misleadingly appeared to show total onboarding abandonment. The approved
fix was applied directly to the dashboard on 2026-09-30 JST, with no app-code branch.
The saved configuration was read back again for this release:

- [Insight HEPUmeJ9](https://eu.posthog.com/project/258358/insights/HEPUmeJ9)
- Title: 동의 후 레슨 전환 — 앱 진입 → 시작 → 완료.
- Ordered `app_opened → session_started → session_completed`, 14-day window.
- Both session steps filter `session_kind = lesson`.
- Description states that only consenting anonymous devices are measured, existing
  users are included, and this is not an onboarding abandonment rate or same-session
  completion rate. Missing historical events are not backfilled.

## Verification

- iOS full unit suite: 449 passed, 0 failures (2026-09-30 JST).
- iPhone 17: simulated purchase/create/save, purchase/incomplete-save/cancel/resume,
  and restored draft navigation/relaunch/interrupted-session recovery passed.
- iPad Pro 13-inch (M5): purchase/cancel/manual-resume and the final restored-draft
  navigation/relaunch/interrupted-session recovery checks passed.
- UI purchases use the test adapter; they do not attest to a real App Store transaction.
- No new real-device QA or unresolved historical release gate is marked complete.

## Release operation

Public JP lookup and authenticated ASC both confirm 1.1.1, with build 25 as the latest
upload. 1.1.2 (26) was archived, exported, uploaded, processed as VALID, selected, and
submitted for review. Existing store settings and manual release after approval
were preserved. Authentication and version/build allocation were verified. Source signing and upload must use existing team
X44BQNTAH9, bundle `app.piyokey.Piyokey`, and app ID 6794853985.
Historical 1.1.1 (25) submission data is preserved in
`release/history/ios-1.1.1-build-25-submission.json`.

What's New draft:

- ja: デッキ作成画面を閉じたあと、マイページを開くたびに再表示される問題を修正しました。入力途中の内容は保存され、続きから編集できます。
- en: Fixed an issue where the deck editor reopened each time you visited My Page after closing it. Your draft is preserved so you can continue editing later.
- ko: 덱 만들기 화면을 닫은 뒤 마이페이지에 들어갈 때마다 다시 열리던 문제를 수정했습니다. 작성 중인 내용은 보관되며 나중에 이어서 편집할 수 있습니다.

## App preview assets

User-supplied English and Japanese previews: 886×1920, 29.8 seconds, H.264 High
Level 4.0, 30 fps, AAC stereo 48 kHz. Register them for the corresponding English
and Japanese iPhone localizations. Filenames and SHA-256 hashes are recorded in
`app_store_submission.json`. All five localized uploads completed. Japanese
processing completed; English processing was still pending when Apple accepted
the submission. The inherited Japanese preview in the new candidate was replaced;
the published 1.1.1 preview was not changed.

Repository Python validation: 110 tests passed.

## Submission evidence

- Version ID: `08b430da-b73b-48f2-9c68-b2c04966ae83`.
- Build / delivery ID: `99788b18-254b-4e1b-9795-b140156bd18c`.
- [Review submission](https://appstoreconnect.apple.com/apps/6794853985/distribution/reviewsubmissions/details/63c65ee3-cbae-492d-9e73-090c5dca9038): both submission and version read back as **WAITING_FOR_REVIEW**.
- Archive source: `fb1af933cd0589217d4d59727b032d0e1ec50b60`; subsequent changes are release records only.
- Swift shared engine: 67 tests passed. Repository preflight passed.
- This records submission, not App Review approval or public availability.
