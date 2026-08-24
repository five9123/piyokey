# Android M7 M2 selective-transfer handoff

기준일: 2026-08-23 JST

이 문서는 대규모 dirty worktree를 commit·tag·stage·stash·clean하지 않고 M1의
마지막 `.piyodeck` 오류 순서 보완과 Android M2 증분만 중앙 `piyokey` 저장소의
전용 branch로 선별 이전하기 위한 경계를 기록한다. M3 기능은 포함하지 않는다.

현재 로컬에는 M1-only immutable snapshot이 없고 M1 통합 파일이 M2 내용으로
갱신되어 있다. 따라서 중앙에 이미 검토된 M1 기준선이 있으면 아래 증분만 적용하고,
아직 없다면 `ANDROID_M7_M1_HANDOFF.md`의 전체 경계와 이 문서의 합집합을 현재
내용 그대로 한 번에 이전한다. M1 목록만 현재 내용으로 복사하는 방식은 빌드 가능한
독립 M1 복원이 아니다. 현재 Android source union은 M1 39개 + M2 신규 15개 =
54개다.

## 1. M1 후속 보완 — 2개

```text
android/core/piyodeck/src/main/kotlin/app/piyokey/core/piyodeck/PiyoDeckPackageCodec.kt
android/core/piyodeck/src/test/kotlin/app/piyokey/core/piyodeck/PiyoDeckPackageTest.kt
```

manifest의 `deck_id`, `version`, `items`가 누락되거나 잘못된 타입이어도 deck schema
오류보다 먼저 해당 `ManifestMismatch`로 거부한다. PiyoDeck은 16 tests다.

## 2. M2 신규 모듈·계측 — 15개

```text
android/core/session/build.gradle.kts
android/core/session/src/main/kotlin/app/piyokey/core/session/PracticeSession.kt
android/core/session/src/test/kotlin/app/piyokey/core/session/PracticeSessionReducerTest.kt
android/feature/practice/build.gradle.kts
android/feature/practice/src/main/AndroidManifest.xml
android/feature/practice/src/androidTest/kotlin/app/piyokey/feature/practice/PracticeTouchGateHarness.kt
android/feature/practice/src/androidTest/kotlin/app/piyokey/feature/practice/PracticeTouchGateInstrumentedTest.kt
android/feature/practice/src/main/kotlin/app/piyokey/feature/practice/DubeolsikKeyboard.kt
android/feature/practice/src/main/kotlin/app/piyokey/feature/practice/KeyboardLayout.kt
android/feature/practice/src/main/kotlin/app/piyokey/feature/practice/PracticeMotion.kt
android/feature/practice/src/main/kotlin/app/piyokey/feature/practice/PracticeScreen.kt
android/feature/practice/src/main/res/values-ja/strings.xml
android/feature/practice/src/main/res/values-ko/strings.xml
android/feature/practice/src/main/res/values/strings.xml
android/feature/practice/src/test/kotlin/app/piyokey/feature/practice/KeyboardLayoutTest.kt
```

`core:session`은 Android·Compose 의존이 없는 불변 상태와 순수 reducer다.
`feature:practice`의 production source는 Compose 키보드·화면·연출·현지화만
소유한다. `androidTest` 두 파일은 production Kotlin을 바꾸지 않고 실제 다중
MotionEvent와 물리 실기기 입력 gate를 계측한다.

## 3. M2 통합 변경 — 10개

```text
.github/workflows/source-ci.yml
android/README.md
android/app/build.gradle.kts
android/app/src/main/java/app/piyokey/piyokey/MainActivity.kt
android/app/src/main/res/values-ja/strings.xml
android/app/src/main/res/values-ko/strings.xml
android/app/src/main/res/values/strings.xml
android/build.gradle.kts
android/gradle/libs.versions.toml
android/settings.gradle.kts
```

루트 통합은 Android library plugin, 두 모듈 include, app→practice 연결, M2 버전과
Android CI만 추가한다. application ID 후보, SDK와 toolchain 계약은 바꾸지 않는다.

## 4. 문서·체크섬 변경 — 7개

```text
AGENTS.md
DECISIONS.md
PRD.md
docs/ANDROID_M7_CONTRACTS.sha256
docs/ANDROID_M7_M1_HANDOFF.md
docs/ANDROID_M7_M2_HANDOFF.md
docs/ANDROID_M7_READINESS.md
```

M2에서 `shared/`, Python 도구, iOS 소스와 공용 schema·fixture는 변경하지 않았다.
M1 후속 보완도 Android reader와 해당 Kotlin 회귀 두 파일에 한정된다.

## 5. 로컬 런타임 증거 — 중앙 소스 이전 제외

아래 파일은 현재 저장소 정책에서 ignored local artifact다. 중앙 소스 branch로
선별 이전하지 않고 필요하면 별도 release artifact 보관소에 옮긴다.

```text
artifacts/m7/m2/piyokey_m2_after_first.png
artifacts/m7/m2/piyokey_m2_flow.mp4
artifacts/m7/m2/piyokey_m2_start.png
artifacts/m7/m2/piyokey_m2_start_ja.png
artifacts/m7/m2/piyokey_m2_touch_once.png
```

최종 화면 근거는 `piyokey_m2_start.png`, `piyokey_m2_start_ja.png`, 실제 입력 후
화면은 `piyokey_m2_touch_once.png`, 자동 전환 녹화는 `piyokey_m2_flow.mp4`다.
`piyokey_m2_after_first.png`는 레이아웃 수정 중간 증거이므로 최종 UI 기준으로
사용하지 않는다.

## 6. 검증 기준과 실측

```sh
cd android
./gradlew \
  :core:hangul:jacocoTestReport \
  :core:hangul:jacocoTestCoverageVerification \
  :core:deckkit:test \
  :core:piyodeck:test \
  :core:session:test \
  :feature:practice:testDebugUnitTest \
  :feature:practice:assembleDebugAndroidTest \
  :feature:practice:lintDebug \
  :app:lintDebug \
  :app:assembleDebug \
  --rerun-tasks \
  --no-daemon
cd ..
shasum -a 256 -c docs/ANDROID_M7_CONTRACTS.sha256
```

2026-08-23 강제 재실행은 Gradle 142 tasks 전부 성공했다. 테스트는 Hangul 11,
DeckKit 8, PiyoDeck 16, session 12, practice 8로 총 55/55이며 실패·오류·스킵은
0이다. Hangul JaCoCo line 99.68%, branch 95.78%, feature/app lint error 0, debug
app APK와 androidTest APK 조립 성공이다.

app lint에는 error가 없고 의도적으로 열린 warning만 남는다: target 36/compile 37
차이, 고정한 Gradle·Kotlin보다 새 버전이 있다는 알림, 아직 없는 launcher icon이다.
버전 알림만으로 계약을 임의 상향하지 않으며 launcher icon은 별도 디자인 gate다.

API 35, 320×640 AVD에서 일본어 UI와 글자 배율 1.5를 확인했고, 로마자 힌트
26개가 유지됐다. 실제 `사랑해요` 9자모를 입력한 뒤 650ms에 두 번째 문제로
자동 전환됐다. 조합 자모는 175ms 동안 오른쪽에서 원형 프리뷰로 합류한다.

API 35 `hantap_test` AVD에서는 아래 기본 계측 실행을 검증했다.

```sh
cd android
./gradlew :feature:practice:connectedDebugAndroidTest --rerun-tasks --no-daemon
```

결과는 instrumented tests 2개 중 자동 다중 pointer·frame-commit 상관 1 pass,
실기기 전용 gate 1 skip, 실패·오류 0이다. 자동 테스트는 한 gesture에서
`ㄱ down → ㅏ down/up → ㄱ up`을 실제 Compose MotionEvent 경로로 주입하고 두
입력이 순서대로 각 1회 수락되며 같은 화면 frame commit에 연결되는지 확인한다.
에뮬레이터 결과를 물리 latency나 손가락 rollover 통과로 사용하지 않는다.

## 7. 완료 판정과 열린 gate

현재 범위의 M2 코드·계측 하네스 차단 이슈는 없다. 다음 두 항목은 에뮬레이터
단위·화면 검증으로 대체하지 않고 API 29+ 실제 기기에서 닫는다.

- pointer event uptime부터 해당 수락 revision을 포함하는 첫 hardware frame commit
  proxy까지 nearest-rank p95 50ms 이하
- 실제 동시 두 손가락의 rollover 입력 누락·중복 0

물리 gate 실행 명령은 다음과 같다.

```sh
cd android
./gradlew :feature:practice:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.piyokeyPhysicalGate=true \
  --no-daemon
```

유효한 warm-up 20입력 뒤 측정 100입력을 사용한다. 측정 50쌍은 모두 첫 pointer를
떼기 전에 두 번째 pointer가 내려간 `ㄱ`, `ㅏ`여야 하고 누락·중복·reject·unknown·
overflow가 0이어야 한다. frame commit은 실제 패널 표시 시각이 아닌 Android의
GPU/frame-buffer commit proxy이고 event timestamp는 millisecond 단위이므로 50ms
경계에는 약 1ms 양자화가 있다.

기기 제조사·모델·fingerprint·API·주사율·build variant, raw touch/delivery/frame
표본과 percentile 요약은 target app external files의 `m2-touch-gate/` JSON에
기록하고 Gradle 출력에 `PIYOKEY_M2_TOUCH_GATE_REPORT=<absolute path>`로 표시한다.
현재 연결된 물리 기기가 없어 이 JSON 최종 증거는 아직 없다.

설정 영속화, F12 결과 화면, 최신 S5의 닫기/설정 헤더와 뜻·읽기 데이터 연동,
OS IME adapter는 의도적인 후속 범위다. API 26 최소 OS와 Pixel 6 성능 게이트도
각 출시 단계에서 별도 검증한다. 이 상태에서 M3는 시작하지 않는다.

## 8. 수행하지 않은 작업

- Git commit, tag, stage, stash, clean, branch 생성
- Git remote 추가 또는 GitHub·Play Console 외부 상태 변경
- M3 덱 발견·다운로드, 저장, 오디오, Billing, Play Games 기능 착수
