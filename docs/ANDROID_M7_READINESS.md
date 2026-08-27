# Android M7 착수 준비 현황

기준일: 2026-08-25 (JST)

## 결론

M7의 문서상 HOLD는 사용자의 명시적 요청으로 해제했다. Android는 현재 로컬
iOS 1.1 build 7과 PRD v6.3, `shared/` 계약을 포팅 기준으로 사용한다. A0 골격과
M1 공용 코어부터 M5 커리큘럼·리텐션까지 중앙 저장소 `main`에 반영했다. M6A는
온보딩·설정·OS IME 기반을 구현했고 M6B는 나머지 다섯 게임과 번들 콘텐츠 계약을
완성했다. M6C는 오프라인 발음·효과음, 결과 공유, 성장 피요·옷장,
adaptive icon과 접근성 폴리싱을 구현해 API 35 자동 회귀와 Release 빌드를 통과한
상태다. 사용자의 요청에 따라 입력 지연·실제 두 손가락·알림 수신·외부 음악·IME
종류별 확인은 기능 개발을 막지 않고 출시 후보 통합 실기기 QA에 유지한다.

공개 App Store lookup으로 일본 storefront의 iOS 1.0.2가 2026-08-18에 출시된
상태를 확인했다. 저장소의 제출 문서는 아직 build 6 심사 대기 기록이라 운영
상태 갱신이 필요하고, 로컬 iOS 1.1 build 7은 Deck Maker IAP·콘텐츠 권리·실기기
게이트가 열린 미제출 상태다. Android 기능 기준은 공개 1.0.2보다 최신인 현재
로컬 1.1 소스 계약으로 고정한다.

## 최신 기준선

| 항목 | 현재 기준 |
|---|---|
| 제품 문서 | PRD v6.3, DECISIONS 2026-08-27까지 |
| 공개 iOS | App Store 1.0.2, 2026-08-18 출시 확인 |
| iOS 구현 기준 | 1.1 build 7, SwiftUI, iOS 16+ |
| 공용 콘텐츠 | 공식 26덱 + 게임 프리셋 15덱, 총 1,812항목 |
| 발음 | canonical gTTS MP3 581개, CAF 0개 |
| UI 로케일 | `ja` / `en` / `ko`, 미지원 언어는 `en` fallback |
| 사용자 문서 | `.piyodeck` v1, 로컬-only·무계정 |
| Deck Maker | 가져오기·학습·내보내기 무료, 생성·편집만 일회성 상품 |
| 공용 조합 벡터 | composition 15종 + backspace 10종 |

## A0 게이트

| 게이트 | 상태 | 근거 / 다음 작업 |
|---|---|---|
| M7 재개 결정 | 완료 | PRD §13·§14와 DECISIONS 갱신 |
| 백스페이스 계약 10종 | 완료 | 생성기 재생성, SwiftPM 42 tests 통과 |
| Python/콘텐츠 계약 | 완료 | 도구 tests 49개와 release preflight 통과 |
| Android 버전 계약 | 완료 | AGP 9.3.1, Gradle 9.5.0, Kotlin/Compose compiler 2.3.21, Compose BOM 2026.08.00 |
| Android SDK | 완료 | `ANDROID_HOME`을 지정한 첫 Gradle 빌드가 기존 수락된 license를 확인하고 API 37.0을 자동 설치함. 별도 license 수락 명령은 실행하지 않음 |
| Java | 완료 | Homebrew OpenJDK 17로 Gradle·계측·R8 Release 빌드 실행, source/target 17 고정 |
| Android Studio | 미설치 | CLI와 wrapper, 기존 API 35 AVD로 M2 구현·화면 검증 가능. 장기 개발 환경에는 안정 채널 설치 필요 |
| Android 골격 | 완료 | `:app`, 순수 core 4개, `:feature:practice`, Gradle 9.5 wrapper와 version catalog |
| Android M1 검증 | 완료 | Kotlin 35 tests, Hangul line 99.68%·branch 95.78%, lintDebug·assembleDebug 통과 |
| Android M2 기능 베이스 | 자동 완료·실기기 gate 이관 | session/keyboard 자동 회귀와 API 35 MotionEvent/frame-commit 통과. 정량 물리 gate는 출시 후보 QA에 유지 |
| Android M3~M5 | 완료 | 정적 카탈로그·원자 복구·발견, 흐름 게임·공통 결과, 커리큘럼·복습·스트릭·데일리·리마인더 |
| Android M6A | 구현·자동 검증 완료 | F1 온보딩 내장/기기 선택·첫 `가`·챕터1~4 유지·물리 두벌식 가이드, F2a 연습 OS IME, F10 설정, ja/en/ko, API 35 온보딩 3/3·설정 2/2, lint, Debug/Release APK |
| 초기 기기 키보드 parity | 자동 완료·실기기 gate 유지 | iOS와 동일한 입력 선택·QWERTY 가이드·설정 영속화를 구현. Galaxy의 실제 IME/키보드 수동 입력은 통합 QA에서 검증 |
| Android M6B | 구현·자동 검증 완료 | F6~F6e 여섯 게임, 15×100 프리셋, 받아쓰기 MP3, 띄어쓰기 6글, 게임 OS IME, API 35 회귀 |
| Android M6C | 구현·자동 검증 완료 | F8~F12 오디오·공유 PNG·성장 피요/옷장·adaptive icon·접근성, API 35 회귀와 Release APK |
| 교차 플랫폼 package | 완료 | canonical 1,109 bytes writer 일치, pretty golden reader 수용, SHA·Unicode malicious golden 거부 |
| application ID | 후보 | `app.piyokey.piyokey`; Play Console 충돌 확인과 사용자 확정 전 외부 사용 금지 |
| source baseline | 완료 | private GitHub 원격, Issue/Project/PR/CI와 Issue별 `codex/` 브랜치 운용. 사용자 원본 dirty worktree는 별도 보존 |
| Google Play 상태 | 미착수 | 앱 생성·서명·Play Games·Billing 상품 생성 모두 별도 외부 gate |

## 구현 순서

1. A0 완료: wrapper/version catalog, `:app`, 순수 `:core:hangul`, Android contract CI.
2. M1 완료: Hangul coverage 95% 이상, DeckKit/schema/catalog, `.piyodeck` reader/writer
   cross-platform golden과 malicious fixture.
3. M2 기능 베이스 구현: 순수 session reducer, 내장 두벌식 키보드와 연습 화면.
   test-only 계측 하네스까지 준비했으며 실제 기기에서 warm-up 20·측정 100의
   frame-commit p95와 동시 2-pointer gate를 통과한 뒤 완료로 닫는다. OS IME adapter는
   PRD §13 순서대로 M6에 구현한다.
4. M3~M5 완료: 정적 카탈로그·설치/복구, 흐름 게임, 커리큘럼·복습·스트릭.
5. M6 기능 구현 완료: M6A 온보딩·설정·연습 OS IME, M6B 여섯 게임·게임 OS IME,
   M6C 효과음·발음·공유·성장/옷장·접근성·브랜딩을 자동 검증했다.
6. M7 release: Play Billing/Play Games adapter, API 26/36/37 호환, Pixel 6
   60fps·오디오 혼합·파일 가져오기 실기기 gate.

각 단계는 화면 복제보다 공용 계약 통과를 먼저 완료한다. 세션 중에는 파일,
결제, IME 설정, 랭킹 인증, 오류 모달을 열지 않는다.

M6A 뒤에도 기기 수동 QA를 요구하지 않는 기능 묶음을 연속 구현한다. 기능·자동
출시 검증이 모두 끝난 뒤에만 통합 실기기 체크리스트를 사용자에게 전달한다.

## 확정한 Android 경계

- 순수 core에는 Android/Compose import를 허용하지 않는다.
- Room은 설치 메타데이터·진행·기록, private files는 payload·backup·draft·staging,
  Preferences DataStore는 작은 설정만 소유한다.
- SAF/`ContentResolver`로 `.piyodeck`을 private staging에 제한 복사하며 저장소
  전체 권한을 요청하지 않는다.
- 고정 목표는 동일한 581개 MP3를 사용하고, 동적 문구만 오프라인 한국어 기기
  TTS로 fallback한다.
- 효과음/발음은 audio-focus gain을 요청하지 않아 다른 앱 음악을 멈추거나
  duck하지 않는다.
- Play Games는 클래식 15개만 원격 미러링한다. JST 월요일 기준 피요컵은
  Google recurring reset과 일치하지 않아 Android에서는 로컬 주간 기록으로 둔다.
- Play Billing은 `PURCHASED`만 제작 권한을 열고 `PENDING`에는 열지 않는다.

## 다음 수동 결정

1. 현재 iOS/shared 변경을 어떤 commit/tag로 기준선화할지 review한다.
2. 새 개발·CI 환경에서는 SDK license 내용을 직접 확인한 뒤 API 37.0을 설치한다.
   현재 기기는 API 37.0 설치와 debug build를 완료했다.
3. `app.piyokey.piyokey` 사용 가능 여부를 Play Console에서 확인한 뒤 최종 ID를
   확정한다.
4. 출시 후보 단계에 API 26 최소 OS, target API, 실제 Galaxy 입력·IME·오디오·알림,
   60fps 성능을 하나의 통합 QA로 실행하고 원시 증거를 보존한다.
5. iOS 1.1의 아직 열린 IAP·콘텐츠 권리·실기기 gate를 Android 출시 계획과
   함께 다시 동결한다.
6. Android launcher/adaptive icon은 공용 1024 RGB 원본을 빌드 생성 리소스로
   사용한다. Play Console 등록 전 실제 런처 마스크별 시각 확인만 통합 QA에 남긴다.
