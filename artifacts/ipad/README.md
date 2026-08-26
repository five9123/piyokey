# iPad Universal 검증 산출물

Issue [#10](https://github.com/five9123-maker/piyokey/issues/10)의 자동 검증과 남은 수동 출시 게이트를 기록한다. 기능 기준은 `PRD.md` §12.2이며, 실기기·접근성·스토어 게이트가 닫히기 전에는 출시 완료로 간주하지 않는다.

## 2026-08-25 자동 검증

환경:

- Xcode 26.6 (17F113), iOS 26.5 Simulator
- iPad Pro 13-inch (M5), iPhone 17
- Universal target family `1,2`, iOS/iPadOS 16+

통과:

- iPad 홈 세로↔가로 회전 시 실제 폭 등급 재계산과 선택 탭 보존: 1/1
- iPad 연습 중 입력 후 회전 시 문제·입력·오타 상태 보존, 내장 키보드 820pt 상한·중앙 정렬: 1/1
- Accessibility XXXL에서 설정 완료 버튼, 연습 시작, 내장 키 접근 가능: 1/1
- iPhone 대표 회귀(연습 초기 화면, 5탭 설정 접근, 앱 투어): 3/3
- `AppSettingsTests`: 15/15
- `python3 tools/release_preflight.py`: PASS
- generic iOS Release, `CODE_SIGNING_ALLOWED=NO`: BUILD SUCCEEDED

증빙 이미지:

- `ipad-home-portrait-ja.png`: 13-inch iPad 세로 홈과 상단 5탭 구조
- `ipad-settings-accessibility-xxxl-ja.png`: Accessibility XXXL 설정 시트

## 2026-08-26 실제 iPad 검증

환경:

- Jungmin’s iPad, iPad Pro 11-inch (3세대), iPadOS 26.6 (23G71)
- Xcode 26.6 (17F113), iOS SDK 26.5
- USB 연결, paired, Developer Mode enabled, `ddiServicesAvailable: true`
- 자동 등록된 개발 기기·프로비저닝 프로파일로 arm64 Debug 앱 서명·설치·실행 성공

통과:

- 세로 홈 `medium` → 가로 홈 `wide` → 세로 홈 `medium` 폭 등급 재계산과 선택 탭 보존: 1/1
- 연습에서 `ㅅ` 입력 후 가로↔세로 회전 시 목표·진행(1/9)·오타 보존과 내장 키보드 중앙 정렬: 1/1
- Accessibility XXXL에서 설정 완료 버튼과 연습 시작·키 접근 가능: 1/1
- Universal iPad의 OS 키보드 전환 후 진행 보존, Return 개행 방지, 홈 복귀 포커스 복구와 이어 입력 완료: 2/2
- `PracticeSessionViewModelTests`: 실제 iPad에서 26/26 통과. marked/committed 중복, Backspace 되감기, Space와 도깨비 이월 상태 포함

실기기 테스트 결과 번들:

- 레이아웃·회전·접근성: `Test-Hanco-2026.08.26_23-04-40-+0900.xcresult`
- OS 키보드: `Test-Hanco-2026.08.26_23-10-37-+0900.xcresult`

추가 증빙 이미지:

- `ipad-device-home-portrait-ja.png`: 실제 iPad 세로 홈
- `ipad-device-home-landscape-ja.png`: 실제 iPad 가로 홈
- `ipad-device-practice-landscape-active-ja.png`: 입력 진행을 보존한 실제 iPad 가로 연습
- `ipad-device-settings-accessibility-xxxl-ja.png`: 실제 iPad Accessibility XXXL 설정
- `ipad-device-os-ime-session-complete-ja.png`: 실제 iPad OS 키보드 전환·이어 입력

## 남은 필수 수동 게이트

- Jungmin’s iPad에서 Split View 1/2·1/3과 Stage Manager 창 크기 변경
- 온보딩·홈·찾기·연습·게임·결과·마이페이지·설정·Deck Maker의 대표 폭 시각 검수
- 세션 진행 중 회전·리사이즈 후 문제, 입력, 점수, 타이머 보존
- Pointer/trackpad hover·클릭, Full Keyboard Access, VoiceOver, Increase Contrast, Reduce Motion
- Issue #12의 ANSI/JIS Bluetooth 키보드 100회 입력·동시 입력 계측
- Issue #17의 실제 Bluetooth/USB 키보드 온보딩 첫 입력·부화 미션·설정 반복 수동 확인
- App Store Connect의 iPad 스크린샷·현지화 메타데이터와 TestFlight 새 설치 검수

## 해제된 실기기 연결 차단

2026-08-25에는 CoreDevice가 `ddiServicesAvailable: false`와 `The developer disk image could not be mounted on this device`를 반환했다. 2026-08-26 USB 재연결과 기기 잠금 해제 후 DDI 서비스가 활성화됐고, Xcode Apple Account 로그인 뒤 기기 등록과 자동 프로비저닝도 성공했다.

동일한 iPadOS 26.6 기기에서 iOS 26.5 SDK 기반 Debug 앱의 빌드·설치·실행과 위 실기기 UI 테스트가 성공했으므로 DDI 차단은 해제됐다. 재현 시 우선 USB 연결, 잠금 해제, `ddiServicesAvailable`, Xcode 계정과 기기 등록 상태를 확인한다.

Split View·Stage Manager·포인터·VoiceOver와 실제 Bluetooth/USB 키보드 수동 입력은 별도의 남은 출시 게이트다.

## 2026-08-26 OS IME 입력 중 세션 설정 크래시 회귀

실제 iPad에서 Bluetooth 키보드 입력 중 세션 설정을 누르면 연속으로 종료되는 현상을 두 번 재현했다. 두 crash report 모두 `SIGABRT`이며, iPadOS 키보드 keyplane 변경 중 SwiftUI context menu가 AttributeGraph를 재진입한 경로를 가리켰다.

수정 계약:

- 설정 표시 전에 OS IME `UITextField`의 first responder를 해제한다.
- 포커스 해제와 설정 패널 표시를 서로 다른 main run-loop cycle로 분리한다.
- 설정을 닫으면 OS IME 포커스를 자동 복구한다.
- 입력 중인 자모, 현재 문제, 오타와 세션 시간은 보존한다.
- Android M7 M2에는 아직 OS IME와 세션 설정 진입점이 없어 동일 런타임 경로는 없다. 향후 F2a/F10 구현은 같은 포커스 격리 계약을 적용한다.

자동 회귀는 `사` 입력 후 설정 열기·닫기를 3회 반복하고 `랑해요`를 이어 입력해 다음 문제로 자동 전환되는지 검증한다. iPhone 17 / iOS 26.5 Simulator의 관련 UI 회귀 7/7과 Jungmin’s iPad / iPadOS 26.6의 신규 회귀 1/1이 통과했다. 실기기 결과 번들은 `Test-Hanco-2026.08.26_23-52-49-+0900.xcresult`다. 실제 Bluetooth 키보드에서 동일 반복 동작과 새 crash report 부재 확인은 Issue #46의 Verify gate로 남긴다.

Android M7 M2는 소스 감사에서 OS IME `EditText`·세션 설정 UI가 아직 없음을 확인했다. 따라서 동일 크래시는 현재 적용 대상이 아니며, 이번 환경에는 JDK가 없어 변경 없는 Android Gradle 회귀는 재실행하지 못했다.

## 2026-08-27 초기 온보딩 기기 키보드 학습 경로

두벌식 소개의 세 번째 상호작용에서 `내장 키보드로 시작` 또는 `기기 키보드로 시작`을 선택할 수 있게 했다. 기기 키보드를 선택하면 첫 `가` 입력부터 OS 한국어 IME를 사용하고, 선택한 입력 방식과 앱 소유 QWERTY 두벌식 배열 가이드는 챕터1~3 부화 미션과 챕터4에도 이어진다. 배열은 다음 자모에 대응하는 라틴 키, 권장 손·손가락, 겹자음·겹모음의 반대 손 Shift를 안내하되 실제 운지는 채점하지 않는다.

자동 검증:

- iPhone 17 / iOS 26.5 Simulator에서 `기기 키보드 선택 → 가 입력 → 부화 미션1 OS IME·배열 가이드 유지`: 1/1
- 기존 내장 키보드의 네 번째 상호작용 첫 입력과 부화 미션 강제 경로: 1/1
- OS IME 입력 중 설정 열기·닫기 3회 후 진행 보존·포커스 복구: 1/1
- 두벌식 base/Shift/Space/home-position 매핑, 전체 내장 자모 coverage, ja/en/ko 키·format parity: 3/3
- 실제 iPad용 arm64 Debug 서명 빌드와 설치: 성공
- Jungmin’s iPad / iPadOS 26.6에서 `기기 키보드 선택 → 가 입력 → 부화 미션1 OS IME·배열 가이드 유지` UI 자동 테스트: 1/1 (`/tmp/piyokey-issue17-ipad-onboarding-final.xcresult`)

실기기 자동화 입력은 실제 Bluetooth 키 이벤트를 대신하지 않으므로, 설치된 앱에서 Bluetooth 키보드로 새 온보딩의 `기기 키보드로 시작`을 선택해 `ㄱ`, `ㅏ`를 입력하고 부화 미션에 같은 입력 방식이 유지되는지, 입력 도중 설정을 반복해도 종료되지 않는지 확인하는 수동 Verify gate가 남아 있다.
