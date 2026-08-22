# Android M7 착수 준비 현황

기준일: 2026-08-22 (JST)

## 결론

M7의 문서상 HOLD는 사용자의 명시적 요청으로 해제했다. Android는 현재 로컬
iOS 1.1 build 7과 PRD v5.8, `shared/` 계약을 포팅 기준으로 사용한다. A0 골격과
M1 공용 코어는 완료했다. 여러 사람이 다음 기능을 병렬 구현하거나
Google Play 외부 상태를 만들기 전에는 현재 대규모 미커밋 작업을 review 가능한
baseline commit/tag로 고정해야 한다.

공개 App Store lookup으로 일본 storefront의 iOS 1.0.2가 2026-08-18에 출시된
상태를 확인했다. 저장소의 제출 문서는 아직 build 6 심사 대기 기록이라 운영
상태 갱신이 필요하고, 로컬 iOS 1.1 build 7은 Deck Maker IAP·콘텐츠 권리·실기기
게이트가 열린 미제출 상태다. Android 기능 기준은 공개 1.0.2보다 최신인 현재
로컬 1.1 소스 계약으로 고정한다.

## 최신 기준선

| 항목 | 현재 기준 |
|---|---|
| 제품 문서 | PRD v5.8, DECISIONS 2026-08-21까지 |
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
| 백스페이스 계약 10종 | 완료 | 생성기 재생성, SwiftPM 36 tests 통과 |
| Python/콘텐츠 계약 | 완료 | 도구 tests 45개와 release preflight 통과 |
| Android 버전 계약 | 완료 | AGP 9.3.1, Gradle 9.5.0, Kotlin/Compose compiler 2.3.21, Compose BOM 2026.08.00 |
| Android SDK | 완료 | `ANDROID_HOME`을 지정한 첫 Gradle 빌드가 기존 수락된 license를 확인하고 API 37.0을 자동 설치함. 별도 license 수락 명령은 실행하지 않음 |
| Java | 부분 완료 | 로컬 JDK 21 존재, source/target 17 고정. CI/Android Studio 기준 JDK 17 설치 필요 |
| Android Studio | 미설치 | CLI와 wrapper로 core 개발 가능, UI·emulator 작업 전 안정 채널 설치 필요 |
| Android 골격 | 완료 | `:app` Compose 준비 화면, `:core:hangul`, Gradle 9.5 wrapper와 version catalog |
| Android M1 검증 | 완료 | Kotlin 34 tests, Hangul line 99.68%·branch 95.78%, lintDebug·assembleDebug 통과 |
| 교차 플랫폼 package | 완료 | canonical 1,109 bytes writer 일치, pretty golden reader 수용, SHA·Unicode malicious golden 거부 |
| application ID | 후보 | `app.piyokey.piyokey`; Play Console 충돌 확인과 사용자 확정 전 외부 사용 금지 |
| source baseline | 차단 | main commit 1개, remote 없음, 대규모 modified/deleted/untracked 상태. 기존 변경을 임의 commit하지 않음 |
| Google Play 상태 | 미착수 | 앱 생성·서명·Play Games·Billing 상품 생성 모두 별도 외부 gate |

## 구현 순서

1. A0 완료: wrapper/version catalog, `:app`, 순수 `:core:hangul`, Android contract CI.
2. M1 완료: Hangul coverage 95% 이상, DeckKit/schema/catalog, `.piyodeck` reader/writer
   cross-platform golden과 malicious fixture.
3. M2: 내장 두벌식 키보드와 연습 화면, 표준 `EditText` 기반 OS IME adapter.
4. M3~M5: 정적 카탈로그·설치/복구, 게임, 커리큘럼·복습·스트릭.
5. M6 parity: ja/en/ko, 581 MP3, 설정·공유·성장·접근성.
6. M7 release: Play Billing/Play Games adapter, API 26/36/37 호환, Pixel 6
   60fps·오디오 혼합·파일 가져오기 실기기 gate.

각 단계는 화면 복제보다 공용 계약 통과를 먼저 완료한다. 세션 중에는 파일,
결제, IME 설정, 랭킹 인증, 오류 모달을 열지 않는다.

이번 작업은 M1에서 종료했다. M2는 중앙 저장소 전용 브랜치로 선별 이전하고
기준선 review가 끝나기 전까지 시작하지 않는다.

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
4. Android Studio와 JDK 17, API 26 최소 OS 기기/에뮬레이터, Pixel 6 성능 기기를
   준비한다.
5. iOS 1.1의 아직 열린 IAP·콘텐츠 권리·실기기 gate를 Android 출시 계획과
   함께 다시 동결한다.
6. shared 3D 원본으로 Android adaptive launcher icon을 생성·검증한다. A0 debug
   앱은 아직 launcher icon을 선언하지 않아 lint warning을 유지한다.
