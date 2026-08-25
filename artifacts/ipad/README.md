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

## 남은 필수 수동 게이트

- Jungmin’s iPad에서 새 설치·실행, 세로/가로, Split View 1/2·1/3, Stage Manager 창 크기 변경
- 온보딩·홈·찾기·연습·게임·결과·마이페이지·설정·Deck Maker의 대표 폭 시각 검수
- 세션 진행 중 회전·리사이즈 후 문제, 입력, 점수, 타이머 보존
- Pointer/trackpad hover·클릭, Full Keyboard Access, VoiceOver, Increase Contrast, Reduce Motion
- Issue #12의 ANSI/JIS Bluetooth 키보드 100회 입력·동시 입력 계측
- Issue #17의 물리 키보드 학습 모드 구현·검증
- App Store Connect의 iPad 스크린샷·현지화 메타데이터와 TestFlight 새 설치 검수

## 현재 실기기 차단 조건

`Jungmin’s iPad`(iPad Pro 11-inch 3rd generation, iPadOS 26.6 build 23G71)는 2026-08-25 현재 paired/available이고 Developer Mode도 enabled다. 그러나 CoreDevice가 `ddiServicesAvailable: false`와 `The developer disk image could not be mounted on this device`를 반환해 설치·실행 자동화가 불가능하다.

현재 Xcode의 후보 이미지를 기존 이미지를 지우지 않는 `xcrun devicectl manage ddis update --no-clean`으로 다시 등록했지만 설치 전후 이미지 집합이 동일했다. 이어서 해당 UDID를 지정한 서명 Debug build도 destination 대기 시간 초과와 같은 DDI 마운트 오류로 종료되어, 앱 코드·서명 이전의 로컬 개발 이미지 호환 문제임을 확인했다.

해제 조건은 현재 Xcode에서 해당 iPadOS 빌드용 Developer Disk Image를 사용할 수 있게 한 뒤(지원 Xcode/플랫폼 구성 확인, 필요 시 USB 재연결·기기 잠금 해제) 동일 기기에서 위 수동 게이트를 수행하는 것이다.
