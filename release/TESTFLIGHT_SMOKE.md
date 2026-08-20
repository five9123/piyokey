# PIYOKEY 1.0.2 (6) TestFlight smoke record

Run this checklist against the exact build selected for App Review. Record the tester, device, OS, date, build number, and archive SHA-256 at the top of the completed copy.

## Build identity

- Version/build: `1.0.2 (6)`
- Bundle ID: `app.piyokey.Piyokey`
- Tester:
- Device / OS:
- TestFlight build installed at:
- Archive SHA-256:

## Clean install

- [ ] Build 6 업로드·처리 후 기존 PIYOKEY를 삭제하고 TestFlight에서 build 6을 설치한다.
- [ ] Launch without a crash and complete or skip onboarding.
- [ ] Confirm Japanese is the default UI on a Japanese-language device.
- [ ] Complete one guided mission with the built-in keyboard.
- [ ] Open Home, Discover, Practice, Games, and My Page.
- [ ] Open Settings → Privacy Policy and Support.

## Core offline path

- [ ] Enable airplane mode and relaunch.
- [ ] Start a bundled practice deck and finish all three items.
- [ ] Start Flow with a bundled difficulty set and reach the result screen.
- [ ] Play one bundled pronunciation clip or confirm the `ko-KR` fallback.
- [ ] Confirm catalog failure/retry UI does not block bundled content.

## Permission boundaries

- [ ] Reminders remain OFF and no notification prompt appears before opt-in.
- [ ] Enable reminders, deny permission, and confirm the app remains usable.
- [ ] Save a result image, deny add-only Photos permission, and confirm the app remains usable.
- [ ] Confirm the app never requests microphone, camera, contacts, or tracking permission.

## Update retention

- [ ] Install the previous internal build, create a record, download a deck, name Piyo, and select an item.
- [ ] Update to build 6 through TestFlight without deleting the app.
- [ ] Confirm the deck, record, streak, Piyo name, unlock, and selected item remain.

## Device-only audio and input

- [ ] Korean OS IME: marked composition, confirmation, deletion, and paste behave correctly.
- [ ] Silent mode: pronunciation and enabled effects play; both stop immediately when disabled in Settings.
- [ ] Play external music, then use effects and pronunciation; the music continues without interruption or ducking.
- [ ] Rapidly tap pronunciation across multiple targets; stale or overlapping speech does not continue.

## Game Center physical-device gate

- [ ] baseline `PiyokeyGameCenterAvailableLeaderboardIDs`의 모든 ID가 App Store Connect에서 Live인지 대조한다.
- [ ] iOS 26+에서 전체 load 결과 중 `.released ∩ intended`만 활성화되고 `.prereleased`는 CTA/submit/dashboard에서 제외되는지 확인한다.
- [ ] probe 오류는 baseline으로 fallback하고, iOS 16~25는 baseline만 유지하는지 확인한다.
- [ ] Game Center 응답이 지연되거나 끊겨도 5초 안에 CTA loading이 풀리고 baseline으로 재시도 가능한지 확인한다.
- [ ] Game Center 로그아웃 상태에서 계약 대상 결과 CTA를 빠르게 여러 번 탭해 인증 화면이 하나만 나타나는지 확인한다.
- [ ] 인증을 취소한 뒤 CTA가 다시 활성화되고, 두 번째 탭으로 로그인과 리더보드 진입이 가능한지 확인한다.
- [ ] 로그인 성공 시 인증 화면이 완전히 닫힌 뒤 리더보드가 한 번만 열리는지 확인한다.
- [ ] 리더보드 표시 중 CTA 연타, 홈 전환, 백그라운드→복귀 후 중복 모달·강제 종료·고정 로딩이 없는지 확인한다.
- [ ] 비행기 모드에서 탭 후 앱이 유지되고, 온라인 복귀 뒤 재시도할 수 있는지 확인한다.
- [ ] 계약 대상 번들 덱+내장 키보드 점수가 한 번 제출되고 순위가 갱신되는지 확인한다.
- [ ] 다운로드/사용자 덱, OS IME, 계약 미포함 리더보드 결과에는 Game Center CTA가 없는지 확인한다.
- [ ] Console/Organizer에서 UIKit presentation warning, SwiftUI `Publishing changes from within view updates`, GameKit 관련 crash가 없는지 확인한다.

## Result

- [ ] PASS — set `testflight_clean_install_smoke`, `game_center_live_device_check`, and the completed device gates in `release/app_store_submission.json` to `true`.
- [ ] FAIL — record the issue and do not select this build for review.

Notes:
