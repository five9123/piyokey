# App Store 출시 준비 검증

## 2026-08-11 PIYOKEY 1.0.2 (6) App Review 제출

- 아카이브: `artifacts/release/PIYOKEY-1.0.2-6.xcarchive`
- Organizer upload export: `artifacts/release/PIYOKEY-1.0.2-6-upload-export/typee.ipa`
- IPA SHA-256: `889b87b5e54055f98e1f31f32cfffff456591954cf62ff69988af60830bfe487`
- 앱 실행 파일 SHA-256: `3e6a62ac575348e5390d7510e8a43327fce492622e0eeaf22eddeee64d705537`
- archive `Info.plist` SHA-256: `a39c4ffcbf22c564705873e688466ab65d79b549751bcd63310c7819683365e6`
- Bundle ID `app.piyokey.Piyokey`, 버전 `1.0.2 (6)`, 팀 `X44BQNTAH9`, arm64, iOS 16+를 확인했다.
- export summary에서 Apple Distribution 서명, App Store 프로비저닝, `get-task-allow=false`, `beta-reports-active=true`, Game Center entitlement를 확인했다.
- `PrivacyInfo.xcprivacy`, Live baseline 12개와 intended 16개를 분리한 Game Center availability 계약이 archive에 포함된 것을 확인했다.
- 빌드 6 소스의 출시 후보 전체 회귀는 321/321 통과했다.
- 2026-08-11 20:57:37 JST에 Organizer 업로드 성공 산출물이 저장됐고 App Store Connect 처리 상태 `Complete / Ready to Submit`을 확인했다.
- TestFlight 사용자가 build 6의 Game Center 실기기 동작을 확인해 `game_center_live_device_check=true`로 닫았다.
- 사용자는 이 확인 뒤 build 6의 즉시 심사 제출을 별도로 승인했다. 완료되지 않은 한국어 IME·TTS·사운드 혼합·iPhone 12 등 수동 게이트는 완료로 바꾸지 않고 그대로 기록한다.
- build 6을 앱 버전 `1.0.2`에 연결하고 2026-08-11 21:15 JST에 iOS 앱 1개와 `piyokey.v4.flow.*` 3개, `piyokey.v4.cup.weekly.flow` 1개를 App Review에 제출했다. 제출 ID는 `45184f9b-494c-431e-a740-a3dde9080f4a`이며 5개 모두 `Waiting for Review`다.
- 수동 hold를 해제했으며 승인 후 phased release 없이 전 사용자에게 즉시 자동 출시하도록 확인했다. 취소된 build 5 제출은 `Removed / Developer Rejected` 이력으로 유지한다.

## 2026-08-10 PIYOKEY 1.0.2 (5) App Store Connect 업로드

- 아카이브: `artifacts/release/PIYOKEY-1.0.2-5.xcarchive`
- App Store export: `artifacts/release/export-build5-1.0.2/Hanco.ipa`
- IPA SHA-256: `cfa99a591eba31936a782065d5cb36c4384e6e135ffd07a86ba6d6654edd402f`
- 앱 실행 파일 SHA-256: `54fde8a801edcd586d0e8b0186aece0a8976f43b15766c6470da114a9c7e4657`
- archive `Info.plist` SHA-256: `39405ac9ec52b0947b023b834c2c3e539ec7aa9eead6e14cc53856238d675631`
- Bundle ID `app.piyokey.Piyokey`, 버전 `1.0.2 (5)`, 팀 `X44BQNTAH9`, arm64, iOS 16+를 확인했다.
- Cloud Managed Apple Distribution 서명, App Store 프로비저닝, `get-task-allow=false`, `beta-reports-active=true`, Game Center entitlement를 확인했다.
- `PrivacyInfo.xcprivacy`와 플로우·주간 피요컵·단어 맞추기의 `piyokey.v4.*` 계약이 IPA에 포함된 것을 확인했다.
- SwiftPM 21개 및 iOS 단위·UI 289개 시나리오를 통과했다. 최초 UI 1건은 0.9초 상태를 1초 폴링이 놓친 테스트 결함이었고 폴링 간격 보정 뒤 해당 테스트와 같은 헬퍼 사용 시나리오를 각각 재실행해 통과했다.
- 2026-08-10 02:01:37 JST에 App Store Connect가 `Upload succeeded`를 반환했다. 이후 처리 완료, Binary State `Validated`, 비면제 암호화 `No`, 내부 QA 그룹 자동 연결을 확인했다.
- `piyokey.v4.flow.*` 3개 클래식 보드와 `piyokey.v4.cup.weekly.flow` recurring 보드를 만들고 ja/en-US/ko 현지화를 저장했다. 주간 보드는 2026-08-17 00:00 JST 시작, 7일 기간·7일 재시작이다.
- App Store 버전 `1.0.2`에 빌드 5, 일본어 릴리스 노트, 기존 스크린샷·App Preview·프로모션 문구, 자동 출시·즉시 전체 배포·기존 평점 유지 설정을 저장했다.
- 앱 버전 1개와 v4 리더보드 4개, 총 5개를 2026-08-10 07:55 JST에 App Review로 제출했다. 제출 ID는 `722d6568-1321-4b44-838f-138f07ef8647`이다. 이 제출은 Game Center 안정성 수정본으로 교체하기 위해 2026-08-11 심사 시작 전에 취소했으며, 현재 `Removed / Developer Rejected`다.
- TestFlight 새 설치·한국어 OS IME·TTS·무음 모드·외부 음악 혼합과 이관된 iPhone 12 성능 게이트는 사용자 승인으로 이번 제출에 한해 면제했으며 완료로 기록하지 않는다.
- 새 recurring 보드는 심사 전에는 기본 보드로 지정할 수 없어 Live 전환 뒤 후속 설정이 필요하다.

## 2026-08-03 PIYOKEY 1.0.1 (4) App Store Connect 업로드

- 아카이브: `artifacts/release/PIYOKEY-1.0.1-4.xcarchive`
- App Store export: `artifacts/release/export-build4-1.0.1/Hanco.ipa`
- IPA SHA-256: `33fcb29becf752e07a276b2105b11fffc61cc278e5591b2f7d7d6c63f1572eff`
- 앱 실행 파일 SHA-256: `3ed714ed0fdd004d2be64cbfe772a8cc203a9339228d23f9b956e01fdca9c014`
- archive `Info.plist` SHA-256: `c2bc3b5d497f885d69c83058125c0b1d02f9355c656e8c9ef6ba7698d6f50e80`
- Bundle ID `app.piyokey.Piyokey`, 버전 `1.0.1 (4)`, 팀 `X44BQNTAH9`, arm64, iOS 16+를 확인했다.
- Cloud Managed Apple Distribution 서명, App Store 프로비저닝, `get-task-allow=false`, `beta-reports-active=true`, Game Center entitlement를 확인했다.
- `PrivacyInfo.xcprivacy`와 플로우·주간 피요컵·단어 맞추기의 `piyokey.v4.*` 계약이 아카이브에 포함된 것을 확인했다.
- 2026-08-03 01:33:31 JST에 App Store Connect가 `Upload succeeded`를 반환했으며 현재 패키지 처리 중이다.
- 최초 `1.0 (4)` 시도는 승인된 `1.0` 트레인이 종료되어 Apple 검증 오류 90186·90062로 거절됐고 수신되지 않았다. 소스 변경 없이 마케팅 버전만 `1.0.1`로 올려 다시 아카이브했다.
- 이번 작업 범위는 빌드 업로드까지다. 새 버전 연결과 App Review 제출은 실행하지 않았고, TestFlight 실기기 스모크·한국어 IME·TTS·외부 음악 혼합·iPhone 12 성능 게이트와 플로우 v4 Game Center 구성은 열린 상태다.

## 2026-07-27 PIYOKEY 1.0 (3) App Review 제출본

- 아카이브: `artifacts/release/PIYOKEY-1.0-3.xcarchive`
- 직접 업로드 방식이라 별도 로컬 IPA는 만들지 않았다.
- 앱 실행 파일 SHA-256: `0b0355e84591d28ff62ad1198144d3bfa4b93b9fab4c3dac6d761acbcab6c85d`
- archive `Info.plist` SHA-256: `68f7f5eba7788955fad0d4696c56eb4b73b669a6d26b9953894847bd2cac3494`
- Bundle ID `app.piyokey.Piyokey`, 버전 `1.0 (3)`, 팀 `X44BQNTAH9`, arm64, iOS 16+를 확인했다.
- archive의 배포 기록에서 App Store 업로드 상태 `success`, 업로드 빌드 번호 `3`, 오류·경고 0건을 확인했다.
- App Store Connect에서 빌드 3의 상태가 `Ready to Submit`이며 iOS 버전 1.0에 연결된 것을 확인했다.
- 앱 바이너리에 직접 입력식 단어퀴즈 전용 `piyokey.v4.word_match.beginner/intermediate/advanced` 3개 ID가 포함되고, 이전 4지선다용 v3 ID는 포함되지 않은 것을 확인했다.
- 앱 `1.0 (3)` 1개, 리더보드 16개, 업적 5개로 총 22개를 2026-07-27 03:25 JST에 App Review로 제출했다. 제출 ID는 `1d46dc56-d5bb-4f60-8a47-406436d82541`, 현재 상태는 `Waiting for Review`다.
- 리더보드 16개는 흐름·산성비·초성·받아쓰기의 v3 난이도별 12개, 주간 피요컵 v3 1개, 단어퀴즈 v4 난이도별 3개다. 이전 단어퀴즈 v3 3개는 초안에서 제외했다.
- 업적 5개의 기존 이미지는 2026-07-27 사용자 승인에 따라 변경하지 않았다.
- 빌드 교체 중 의도치 않게 생성된 submission `a2c1fd17-6a3a-4e6a-9da7-2369c8d54296`은 즉시 취소해 App Review 기록이 `Removed`임을 확인했고, 현재 위 22개 새 초안으로 복구했다.
- 사용자 요청에 따라 추가 전체 회귀는 실행하지 않았다. 따라서 TestFlight 새 설치, 한국어 OS IME, TTS, 무음 모드·외부 음악 혼합, iPhone 12 성능과 `full_unit_and_ui_regression` 게이트는 열린 상태다.

## 2026-07-27 PIYOKEY 1.0 (2) 후보 — 빌드 3으로 대체됨

- 아카이브: `artifacts/release/PIYOKEY-1.0-2.xcarchive`
- App Store export: `artifacts/release/export-build2/Hanco.ipa`
- IPA SHA-256: `7a7a0a7e8f2b6bfa2823d5bb68fe6bc5cc9563008de71869c7452cf7301791c9`
- Bundle ID `app.piyokey.Piyokey`, 버전 `1.0 (2)`, 팀 `X44BQNTAH9`
- archive의 배포 기록에서 App Store 업로드 상태 `success`, 업로드 빌드 번호 `2`, 오류·경고 0건을 확인했다.
- export IPA는 Apple Distribution 서명, `get-task-allow=false`, `beta-reports-active=true`, Game Center entitlement 포함 상태를 확인했다.
- 새 음영 키캡 로고는 1024×1024·72ppi·RGB·무알파 PNG로 AppIcon과 인앱/공유 로고에 동일하게 반영했다.
- 로고 원본 및 AppIcon SHA-256: `2d734cbebe771de7e83f54422f4244105d5e4495fabb8fecbfa7ac1a657b2a9c`
- `python3 tools/release_preflight.py`: 통과
- 2026-07-27 외부 HTTPS 확인: Marketing, Privacy Policy, Support URL 모두 HTTP 200 응답
- Support 페이지에서 `contact@typee.app` 연락처와 PIYOKEY/ピヨキー 표기를, Privacy 페이지에서 PIYOKEY/ピヨキー 및 데이터 처리 안내를 확인했다.
- 일본어 스크린샷 8장을 직접 시각 검수했다. 잘림·디버그 오버레이·개인정보·외부 저작물 노출 없이 홈→입력→커리큘럼→게임→성장→결과 흐름을 보여 준다.
- ja/en-US/ko의 앱 이름·부제는 30자 이내, 홍보 문구는 170자 이내, 설명은 4,000자 이내다. 키워드는 각각 ja 97바이트, en-US 63바이트, ko 81바이트로 100바이트 이내다.
- 전체 회귀는 다수 단위·UI 시나리오 통과 후 사용자 요청으로 중단했다. 따라서 `full_unit_and_ui_regression` 게이트는 닫지 않으며, 기존에 승인한 UI 타이밍성 알려진 이슈 2건과 함께 TestFlight 수동 스모크 대상으로 유지한다.
- App Store Connect의 5개 업적 이미지는 사용자 승인에 따라 기존 상태를 유지한다.
- 빌드 2는 4지선다 단어퀴즈용 `piyokey.v3.word_match.*`를 포함해, 직접 입력식 단어퀴즈와 v4 계약을 반영한 빌드 3으로 대체됐다.

## 2026-07-26 PIYOKEY 1.0 (1) 후보 검증 — 최종적으로 빌드 3으로 대체됨

- Xcode 26.6, iOS SDK 26.5
- `python3 tools/release_preflight.py`: 통과
- `python3 -m unittest tools.tests.test_release_preflight`: 5/5 통과
- SwiftPM `HangulEngine`·`DeckKit`: 21/21, 실패·스킵 0
- iOS 전체 회귀: 267개 실행, 단위 216개 통과, UI 50개 통과, UI 1개 타이밍 실패, 스킵 0
- 실패한 `testIdlePiyoReactsToTouchAndLongPressOpensCloset`은 단독 재시도 통과
- 전체 실행 시작 후 추가된 개인정보/지원 링크 UI 테스트도 별도 통과
- 비서명 generic iOS Release 빌드는 기존 검증 유지

전체 회귀의 유일한 실패는 홈의 병아리를 길게 눌러 옷장을 여는 UI 대기 결과가 시간 초과된 건이다. 같은 빌드·시뮬레이터의 단독 재실행에서는 15.8초에 통과해 기능 회귀가 아닌 UI 타이밍성 실패로 판정했다. 출시 링크 UI 테스트까지 포함한 재검증 2건은 2/2 통과했다.

## 빌드 1을 현재 App Store RC로 인정하지 않는 이유

- Bundle ID `app.piyokey.Piyokey`, 버전 `1.0 (1)`, App Store Connect Apple ID `6794853985`를 확정했다.
- 팀 `X44BQNTAH9`의 개발 기기·자동 서명 프로필을 만들고 App Store archive와 Cloud Managed Apple Distribution IPA export에 성공했다.
- App Store Connect 서버 분석과 빌드 `1.0 (1)` 업로드가 성공했으며 현재 처리 중이다.
- RC IPA: `artifacts/release/export/Hanco.ipa`
- RC IPA SHA-256: `49a6dfdd3d9c723ef40996e2d350d48d41ab90db3292f0411e694fa2af3c7f2e`
- 개인정보처리방침·Support·Marketing URL과 App Review 연락처를 등록했고 App Privacy `Data Not Collected`를 게시했다.
- 번들 카탈로그의 실제 아티스트·그룹·곡·프로그램·캐릭터명 및 실제 가사·대사는 제거 확인했다.
- 1320×2868, 무알파 일본어 스크린샷 8장을 App Store Connect에 업로드하고 출시 순서로 정렬했다.
- 합성 음원에 대한 운영자 권리 위험 승인을 기록하고 App Store Connect Content Rights에 필요한 권리를 보유한다고 응답했다.
- Game Center 리더보드를 5개 게임 × 3개 난이도로 교정한 소스가 빌드 1 이후 반영되어 빌드 1은 제출 후보에서 제외했다.
- 현재 제출본은 `1.0 (3)`이며 archive·업로드·처리·버전 연결과 22개 심사 항목 제출을 완료했다. TestFlight 새 설치, 한국어 OS IME, TTS, 외부 음악 혼합 청취, iPhone 12 성능 게이트는 열린 상태로 명시적으로 보존했다. 전체 회귀는 사용자 요청으로 중단되어 완료로 기록하지 않는다.

수동·자동 제출 게이트는 `release/APP_STORE_QA.md`와 `release/app_store_submission.json`에서 관리한다. 2026-07-27 사용자가 기존 실기기 테스트와 알려진 이슈 승인을 근거로 최종 제출을 지시해, 열린 수동 게이트는 완료로 오인하지 않도록 그대로 보존한 채 제출했다.
