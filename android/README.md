# PIYOKEY Android

> 2026-08-30 사용자 결정으로 이 트리는 과거 포트의 참고 자료로 동결됐다. 현재 제품·유지보수·의존성 갱신·CI·Google Play 출시 범위가 아니며, Android 재개 시 이 문서의 연속 작업이 아니라 새 PRD와 초기 설계부터 시작한다.

Android M7의 A0~M6C와 앱 1.1 무료 사용자 덱 문서 수명주기 구현이다. 한글 입력·덱·`.typedeck`·게임 규칙 계약은 순수 Kotlin으로 고정하고, Compose 연습·발견·오프라인 덱·전체 게임·SAF 사용자 덱을 5탭 앱 셸에 연결한다.

## 현재 출시 상태

기능 소스와 로컬 Release 빌드는 완성 단계지만 아직 Google Play에 올릴 수 있는 최종 출시 후보는 아니다. 공용 브랜드 테마·벡터 아이콘·라이트/다크·큰 글자 회귀까지 자동 검증한 뒤에도 다음 외부 gate가 남는다.

- 최종 application ID와 Play Console 앱 소유권
- 공개 운영 카탈로그와 콘텐츠·고정 발음 권리 승인
- upload signing identity, Deck Maker 상품, Play Games 리소스
- 스토어 메타데이터·스크린샷과 동일 서명 AAB 생성
- 그 AAB를 설치한 Galaxy의 Issue #19 통합 실기기 QA

따라서 일반 `assembleRelease`·`bundleRelease` 산출물은 소스 증빙일 뿐 업로드하지 않는다. 모든 외부 입력과 서명이 갖춰진 `bundleDistributionRelease` 산출물만 최종 후보가 될 수 있다.

## 고정 도구 체인

- Gradle Wrapper 9.5.0
- Android Gradle Plugin 9.3.1
- Kotlin 및 Compose Compiler plugin 2.3.21
- Compose BOM 2026.08.00
- `minSdk 26`, `compileSdk 37`, `targetSdk 36`
- Java/Kotlin bytecode 17

AGP 9의 built-in Kotlin을 사용하므로 Android 모듈인 `:app`, `:core:data`, `:feature:practice`, `:feature:discover`에는 `org.jetbrains.kotlin.android` plugin을 적용하지 않는다. 순수 JVM 모듈인 `:core:hangul`, `:core:deckkit`, `:core:piyodeck`, `:core:session`은 `org.jetbrains.kotlin.jvm`을 적용한다.

로컬에는 Homebrew JDK 21을 Gradle 실행용으로 사용할 수 있다.

```sh
cd android
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
ANDROID_HOME="$HOME/Library/Android/sdk" \
./gradlew \
  :core:hangul:jacocoTestReport \
  :core:hangul:jacocoTestCoverageVerification \
  :core:deckkit:test \
  :core:piyodeck:test \
  :core:session:test \
  :core:game:test \
  :feature:practice:testDebugUnitTest \
  :feature:practice:assembleDebugAndroidTest \
  :feature:practice:lintDebug \
  :core:data:testDebugUnitTest \
  :feature:discover:lintDebug \
  :feature:game:lintDebug \
  :app:lintDebug \
  :app:assembleDebug
```

CI의 권장 Gradle 실행 JDK는 17이다. 생성되는 bytecode도 17로 고정한다.

첫 앱 빌드는 SDK 경로가 설정되지 않아 차단됐다. `ANDROID_HOME`을 지정한 검증에서는 기존에 수락되어 있던 라이선스를 Gradle이 발견해 Android API 37을 자동 설치했으며, 별도의 라이선스 수락 명령은 실행하지 않았다. 현재 이 기기에서는 앱 빌드가 통과한다. 새 환경에 API 37 또는 수락된 라이선스가 없으면 빌드가 차단되며, 외부 SDK 라이선스는 개발자 본인이 내용을 확인한 뒤 수락해야 한다.

```sh
cd android
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
ANDROID_HOME="$HOME/Library/Android/sdk" \
./gradlew :app:assembleDebug
```

## 모듈 경계

- `:app`: Compose single-activity 진입점, 5탭 셸과 feature 간 navigation을 연결한다.
- `:core:hangul`: Android 및 Compose에 의존하지 않는 순수 Kotlin 상태 기계. `shared/test_vectors.json`의 15개 조합, 10개 백스페이스 계약을 직접 읽어 검증한다.
- `:core:deckkit`: 덱·카탈로그 모델, strict codec, 공용 JSON Schema와 의미·bundle 검증. 공식 26덱, 게임 프리셋 15덱, v10→v11 snapshot을 검증한다.
- `:core:piyodeck`: STORE-only ZIP, strict UTF-8 JSON, manifest/hash/metadata, 사용자 덱 정책과 결정적 writer. `core:deckkit`에만 단방향 의존한다.
- `:core:session`: Android 및 Compose에 의존하지 않는 불변 연습 상태와 순수 reducer. 자모 판정, 오타 무시, 되감기, 정확도와 token 기반 650ms 자동 전환을 소유한다.
- `:core:game`: Android 및 Compose에 의존하지 않는 흐름 게임 상태와 monotonic reducer. 카운트다운·제한시간·점수·콤보·목숨·가속·pause/resume·결과 지표를 소유한다.
- `:core:data`: Room 설치 메타데이터·다운로드 이력·복구 journal과 앱 전용 catalog/deck payload, `GameRecord`·`DeckProgress`를 소유한다. `journal → atomic move → Room transaction` 순서와 primary/backup/quarantine 복구를 유지한다.
- `:feature:practice`: Compose 내장 두벌식 키보드와 연습 화면. 키보드 제스처·연출·현지화만 소유하고 저장·오디오·OS IME·결과 화면은 소유하지 않는다.
- `:feature:discover`: 발견 검색·필터·정렬, 덱 상세·미리보기, 홈 추천, 내 덱과 M3 결과 추천 화면을 소유한다.
- `:feature:game`: 게임 허브, 흐름 프리셋/설치 덱 선택, 카운트다운·레인·HUD·자모 트랙·파티클·결과/재도전 화면을 소유한다.

후보 `applicationId`와 namespace는 `app.piyokey.piyokey`다. Google Play Console에서 패키지 소유권과 최종 식별자를 확인하기 전까지 출시 계약으로 확정하지 않는다.

로컬-only·비공개 사용자 덱이 플랫폼 자동 백업으로 복제되지 않도록 A0 앱은 `android:allowBackup="false"`로 시작한다. 사용자 데이터의 백업 및 명시적 내보내기 정책은 M1 데이터 설계에서 경로별로 확정한다.

## M1 검증 결과

- Kotlin tests: Hangul 11, DeckKit 8, PiyoDeck 16, 실패 0
- Hangul JaCoCo: line 99.68%, branch 95.78%
- cross-platform golden: canonical 1,109 bytes, SHA-256 `025efa7a0584509fd892221a01c4b3cdf828472c3ffeb13a7eec420102061c31`
- 순수 core의 Android/Compose import: 0

## M2 구현 및 검증 결과

- 순수 session reducer 12 tests, 두벌식 layout·동작 8 tests, 실패·스킵 0
- 3행 두벌식, one-shot Shift, touch-down 입력, multi-pointer tracker, 2초 다음 키 guide와 로마자 힌트
- 자모 진행·조합 프리뷰, 오타 220ms, 조합 합류 175ms, 완료 후 650ms 자동 다음 문제
- API 35의 320×640 에뮬레이터에서 일본어 UI, 글자 배율 1.5, 실제 9자모 입력 후 자동 전환 확인
- API 35 에뮬레이터에서 실제 Compose 다중 `MotionEvent`로 `ㄱ down → ㅏ down/up → ㄱ up`과 두 입력의 frame-commit 상관관계 확인
- `feature:practice` lint issue 0, `app` lint error 0, debug APK 조립 성공

### M2 실기기 입력 gate

계측 하네스는 production Kotlin을 바꾸지 않고 `feature:practice/src/androidTest`에만 둔다. 일반 에뮬레이터 실행은 자동 rollover 테스트 1개를 통과하고 물리 gate를 skip한다.

실기기를 연결한 뒤 아래 명령을 실행하고, 화면 안내에 따라 `ㄱ`을 누른 채 `ㅏ`를 누르는 rollover를 반복한다.

```sh
cd android
./gradlew :feature:practice:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.piyokeyPhysicalGate=true \
  --no-daemon
```

물리 gate는 API 29 이상 하드웨어에서 유효한 warm-up 20입력과 측정 100입력을 요구한다. pointer event uptime부터 해당 입력 revision을 포함하는 첫 `ViewTreeObserver` frame commit까지의 nearest-rank p95가 50ms 이하여야 하며, 측정 50쌍 모두 두 pointer가 동시에 활성 상태에서 `ㄱ`, `ㅏ` 순서로 각 1회 반영되어야 한다. frame commit은 실제 패널 발광 시각이 아니라 Android가 제공하는 GPU/frame-buffer commit proxy이며 timestamp는 millisecond 단위다.

성공·실패 모두 기기/API/주사율, 원시 touch·delivery·frame 표본과 요약을 target app external files의 `m2-touch-gate/` JSON으로 기록하고 Gradle 출력에 절대 경로를 표시한다.

M2 기능 베이스의 코드 차단 이슈는 없으며 물리 정량 gate는 사용자의 결정으로 출시 후보 통합 QA에 이관했다. 설정 영속화·OS IME는 M6 범위다.

## M3 구현 및 검증 결과

- 번들 26덱과 검증된 cache를 즉시 표시하고, 설정된 HTTPS 정적 host만 ETag/If-Modified-Since로 갱신한다.
- Room/files 복구 journal, 원자적 move, 최신/직전 검증 payload와 quarantine으로 설치·업데이트·손상 복구를 보장한다.
- 검색, 타입·레벨·복수 태그·항목 수 필터, 4종 정렬, 상세 10항목 미리보기, 설치·업데이트·삭제, 오프라인 연습을 제공한다.
- 삭제 후에도 다운로드 태그 이력을 유지하고 홈 3개·결과 2개 추천과 1탭 재시작을 제공한다.
- JVM 7 tests, API 35 Room/data instrumented 5 tests, 발견→다운로드→플레이 Compose instrumented 1 test, feature/app lint와 debug APK가 통과했다.
- 실기기 의존 검증은 추가하지 않았으며 출시 후보 통합 QA Issue #19에 유지한다.

M3 증분 파일과 검증은 `docs/ANDROID_M7_M3_HANDOFF.md`에 기록한다.

## M4 구현 및 검증 결과

- 순수 흐름 reducer는 3·2·1, 60초, 노미스 +2초, 3목숨, 점수/콤보, 새 카드 1.8배 가속과 background 정지를 소유한다.
- 흐름 전용 3×100단어와 설치 덱을 선택하고, 밝은 레인·내용 맞춤 카드·내부 자모 추적·화면 폭 고정 키보드·완료 파티클을 제공한다.
- Room v1→v2 migration으로 append-only `GameRecord`와 덱/게임/입력별 `DeckProgress`를 추가하고 공용 rank tuning으로 결과 등급을 계산한다.
- JVM game 11 tests, API 35 data 8 tests와 app 3 tests, feature/app lint와 debug APK가 통과했다.
- 실기기 입력·60초 frame 성능은 출시 후보 통합 QA Issue #19에 유지한다.

M4 증분 파일과 검증은 `docs/ANDROID_M7_M4_HANDOFF.md`에 기록한다.

## M5 구현 및 검증 결과

- `core:retention`은 iOS parity 6챕터·7스테이지, 별/해금, JST 주간·스트릭·보상, 결정적 5문제 데일리와 복습 졸업 정책을 Android 없이 검증한다.
- 연습 checkpoint는 현재 문제·수락 자모·오타 resolution·유효 시간만 저장하며 background 구간을 제외하고 정확히 복구한다.
- Room v2→v3은 M3/M4 데이터를 보존하면서 커리큘럼, 복습, 스트릭 활동, 3/5/7 영구 보상과 리마인더 preference를 추가한다.
- 홈 MY 피요+7일 카드·데일리·추천, 연습 탭 커리큘럼/자유연습, 마이페이지 피요 상세·수동 복습·시간 지정 리마인더를 ja/en/ko로 제공한다.
- 로컬 리마인더는 온보딩에서 Android 13+ 알림 권한을 허용하면 현지 시각 20:00로 자동 활성화한다. exact-alarm 권한 없이 다음 현지 시각을 매일 다시 계산하고 재부팅·시각·시간대 변경 뒤 복원하며, 설정의 ON/OFF·시간 지정은 선택 사항이다.
- JVM session 14·game 11·retention 9·data 7 tests, API 35 data 12 tests와 app M3~M5 6 tests, 관련 lint와 debug APK가 통과했다.
- 실기기 입력·60초 frame·최종 알림 수신은 출시 후보 통합 QA Issue #19에 유지한다.

M5 증분 파일과 검증은 `docs/ANDROID_M7_M5_HANDOFF.md`에 기록한다.

## 앱 1.1 R1.1A 무료 사용자 덱 문서

- SAF picker와 `VIEW`/`SEND` 문서 진입은 원본 URI를 8 MiB 제한으로 앱 전용 staging에만 복사하고 strict `.typedeck` parser를 통과한 뒤 미리보기를 연다.
- 이름·제작자·유형·난이도·태그·항목 수·첫 3항목을 설치 전에 표시한다. 동일 package는 무쓰기이며 업데이트·다운그레이드·같은 버전 충돌은 현재본 비교와 별도 교체 확인을 요구한다.
- 가져오기·현재본 내보내기·삭제·내보낸 뒤 삭제·재가져오기는 구매 없이 제공한다. Room v3→v4와 별도 user-deck history가 플레이·게임·복습·설치 이력을 payload 삭제 뒤에도 유지한다.
- 외부 문서는 세션 중 staging/pending으로만 보관하고 화면·오류 모달을 띄우지 않는다. 세션 종료 뒤에만 미리보기를 제안하며 pending은 프로세스 재생성 뒤에도 복구한다.
- API 35에서 data 계측 16개와 앱 전체 23개(세션 보류→미리보기→무료 설치, 손상 staging 즉시 정리 포함)를 통과한다. Files·공유 앱 상호운용 수동 확인은 Issue #19의 출시 후보 통합 QA에서 한 번만 수행한다.

세부 증거와 후속 경계는 `docs/ANDROID_R11A_PIYODECK_HANDOFF.md`에 기록한다.

## Play Games v2 외부 설정

Play Games는 선택 기능이다. 21개 외부 리소스가 모두 주입된 빌드에서만 SDK를 초기화하며, 누락된 로컬·CI 빌드는 로그인 UI 없이 모든 로컬 기능을 그대로 제공한다. Play Console에서 발급한 값은 `release/play_games.properties.example`의 키 이름으로 비공개 Gradle properties 또는 `-P` 인자에 주입한다.

```sh
./gradlew :app:verifyPlayGamesConfiguration
```

클래식 공식 코스의 내장 키보드 결과만 15개 보드에 제출한다. OS IME, 사용자/다운로드 덱, 띄어쓰기, Android 주간컵은 로컬 기록만 유지한다. 최초 로그인·랭킹 UI는 자격 있는 결과 화면의 명시적 버튼에서만 시작된다.

## Android 1.1 Release 빌드

소스 기준 버전은 `1.1.0 (8)`이며 Release는 R8·resource shrink를 적용한다. 일반 로컬/CI는 비밀값 없이 unsigned APK와 AAB를 계속 검증한다.

실제 Play 배포 번들은 아래 전용 task만 사용한다. 최종 application ID 확인, 공개 HTTPS catalog/privacy/support, 콘텐츠 권리 승인, upload signing, Play Games 21개 외부 값 중 하나라도 없거나 형식이 맞지 않으면 AAB 생성 전에 실패한다.

```sh
./gradlew :app:bundleDistributionRelease --no-daemon --no-configuration-cache
```

비공개 입력 이름, 서명 방식, 산출물 검증, Issue #19로 이관된 외부·최종 실기기 gate는 `docs/ANDROID_RELEASE_DISTRIBUTION_HANDOFF.md`에 기록한다. 저장소에는 keystore·password·실제 Play ID를 추가하지 않는다.
