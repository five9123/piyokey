# Android 포팅 재시작 계획 (초안)

- 상태: **A0~A7 소스 구현·자동 검증 완료, A8 외부 gate 대기 (2026-09-30)**. §5 항목은
  §5.1의 잠정 기본값으로 구현했다. Play Console 등록·서명·콘텐츠 권리·실기기 QA는
  `release/GOOGLE_PLAY_RELEASE.md`의 외부 gate로 남아 있으며 출시 완료가 아니다.
- 검증(2026-09-30): JVM 499 · 계측 UI 54 통과, lint error 0, Hangul coverage line 99.85% /
  branch 95.99%, release APK·AAB 빌드, 에뮬레이터(API 35) 신규 사용자 전체 흐름 수동 QA.
- 작성: 2026-09-30, 기준 `main` `1f3027a` (iOS 공개 1.1.1 + 이후 병합분)
- 목표: 현재 iOS/iPadOS 앱을 Android(휴대폰 우선, 태블릿 적응형)로 **동일하게** 포팅한다.
  Android에서 iOS에 없는 기능을 먼저 추가하지 않는다.
- 관계 문서: 제품 계약은 `PRD.md`, 결정 기록은 `DECISIONS.md`
  (2026-09-30 "Android 포트 삭제와 처음부터 재시작"), 공용 계약은 `shared/`.

## 1. 재시작 원칙

1. 과거 포트(2026-08-21~08-30, M1~M6D·R1.1A/B)는 저장소에서 삭제했다. 코드·핸드오프·
   Play 초안이 필요하면 git 이력에서 읽기 전용으로 참고한다.
   - 마지막 Android 소스 commit: `b5362eb` (동결), 삭제 직전 기준: `1f3027a`
   - 참고 경로(이력): `android/`, `docs/ANDROID_M7_*.md`,
     `release/google_play_metadata.json`, `release/google_play_console_declarations.json`,
     `release/PLAY_DATA_SAFETY_SETUP.md`, `release/google_play/assets/`
   - 복사해 붙여 넣지 않는다. 필요한 부분은 새 설계에 맞춰 다시 작성한다.
2. **parity 기준은 iOS 코드와 PRD v6.28**이다. 둘이 어긋나면 iOS 현행 동작을 먼저
   기록하고 PRD 정정을 별도 작업으로 연다 (예: PRD F2a의 "builtin만 Game Center" 문장은
   v6.28 이후 낡은 문장).
3. 공용 계약을 먼저 통과한다: `shared/test_vectors.json`, `shared/schema/`,
   `shared/piyodeck/fixtures/`, `shared/mock_catalog/`, `shared/analytics/events.json`.
   화면 복제보다 순수 코어의 계약 통과가 첫 완료 조건이다 (`docs/ARCHITECTURE.md`).
4. 실기기 gate를 마지막으로 미루지 않는다. 과거 포트는 입력 지연 p95 54ms(기준 50ms)에서
   끝났고 실기기 QA를 한 번도 완료하지 못했다. 각 마일스톤이 자기 실기기 증빙을 남긴다.
5. 디자인 시스템(색·아이콘·다크 모드·큰 글자)은 첫 화면부터 적용한다. 과거 포트는 Material
   기본값으로 시작해 별도 폴리싱을 다시 해야 했다.

## 2. 포팅 대상 기능 목록 (iOS 현행 기준)

체크 표시는 소스 구현과 자동 검증 완료를 뜻한다. 실기기 수치 gate(입력 지연·60fps·IME·알림 수신·결제)는
`release/GOOGLE_PLAY_RELEASE.md` §4에서 별도로 닫는다.

아래는 완료 판정용 parity 체크리스트다. 각 항목의 상세 AC는 괄호의 PRD 절과 iOS 파일을 따른다.
iOS 경로는 `ios/Hanco/Hanco/` 기준이다.

### 2.1 앱 구조·진입

- [x] 루트 게이트: 온보딩 → 부화 미션(챕터1~3, 탭바 없음) → 5탭 (`App/AppRootView.swift`)
- [x] 5탭: 홈·발견·연습·게임·마이페이지. 탭 전환 `feature_viewed`
- [x] 앱 투어 6단계 스포트라이트 → 알림 권한 1회 → 개인정보 안내(두 버튼, notice v2) → 홈 (F1, TYP-71)
- [x] 전역 설정 시트, `.typedeck` 파일 열기 진입과 pending import 재개

### 2.2 온보딩·부화·성장 (F1, §7)

- [x] 4단계 온보딩: 목표(keyboard/travel/topik/trends) → 수준 4카드 → 키보드 선택(내장/기기) → 첫 입력, 6탭 이내 첫 입력
- [x] 부화 미션 3챕터, 재실행 후 이어하기, 결과 → "다음 미션"만
- [x] 피요 마스코트: 코드로 그리는 벡터(egg→cracking→hatching→chick→rooster), 16 표정, 소품·알 무늬, 이름, 옷장, 성장 축하 (`DesignSystem/ChickMascotView.swift`, `MascotGrowthSystem.swift`)

### 2.3 입력 (F2, F2a, F3, §6)

- [x] 조합 엔진: `HangulEngine/HangulComposer`·`JamoSequence` 포팅, 공용 벡터 전부 통과, 커버리지 ≥95%
- [x] 내장 두벌식: Shift, 다음 키 가이드, 로마자, 햅틱, 누름 0.95 스케일, 멀티터치 rollover, 오타 키는 입력하지 않고 miss 1
- [x] 내장 천지인(10키): `Korean10KeyRecipe`·`Korean10KeyInterpreter`, `next` 분리, 탭 recipe + 플릭(24dp·450ms·축 1.15배), **플릭 미리보기 UI 없음**(TYP-111), 챕터1~4는 항상 두벌식, 세션 시작 시 배열 고정
- [x] OS IME 모드: `EditText` 기반, committed/composing 분리 → 순수 `OSIMETextJudge` (matching / composingMismatch / unsupportedASCIIInput / confirmedMismatch), 천지인 중간 상태 허용(TYP-83), 대상 변경 시 잔여 IME 텍스트 무시(TYP-73/82 원칙), 한국어 IME 없음 안내(hard block 금지)
- [x] 물리 키보드: OS IME 경로만. 태블릿에서만 물리 배열 가이드
- [x] `input_mode`: `builtin | builtin_korean_10key | os_ime` (legacy 별칭 읽기 불필요 — 새 설치만 존재)

### 2.4 학습 (F4, F7, F8)

- [x] 커리큘럼 6챕터(ch5 3스테이지, ch6 5스테이지), 80% 클리어, 별 3단계(≥97%·60CPM / ≥90%·40CPM), 순차 해금·ch5+ 자유 선택, 중단 복구 (`Core/Progress/CurriculumProgressStore.swift`, `Resources/KoreanLearningContent.strings`)
- [x] 연습 세션: 자모 순차 판정, 조합 프리뷰·자모 트랙·뜻·읽기·피요 반응·콤보, 세션 설정 오버레이, 일시정지/종료
- [x] 복습 덱: 자동 수집, 3회 연속 무실수 졸업, 수동 추가·제거
- [x] 스트릭·JST 주간 7스탬프·3/5/7 보상·응원 10문장, 데일리 챌린지(목표별 풀, `day.ordinal % pool`, stride 3), 랜덤 5단어(최근 20 회피)
- [x] 매일 로컬 리마인더 기본 20:00, 시간대·DST 변경 뒤 20:00 유지, 업그레이드 개념 없음

### 2.5 발견·덱 (F5)

- [x] 번들 카탈로그(26덱) + 원격 정적 카탈로그 선택형(ETag·캐시 fallback, 더 높은 version만 갱신)
- [x] 발견: 검색·태그·정렬(인기·신규·급상승), 카드 1줄 제목 고정 높이, 덱 상세 미리보기 10개·설치/업데이트/플레이·관련 덱·제보 메일
- [x] 홈 추천 2영역(개인화 3 + 다음 단계 3, 중복 없음)
- [x] 로케일별 뜻 조회: ja는 exact/base `ja` → legacy `*_ja`, 그 외는 exact → base → `default_locale` → `en`, 비일본어에서 `*_ja` fallback 금지

### 2.6 게임 (F6~F6f, F12)

- [x] 흐름: 60초, 무실수 +2초, 3목숨, 3-2-1, 50초까지 1.8배, 점수 자모×10×콤보(1.2/1.5/2.0), 코스 자동 선택, 60fps 파티클
- [x] 산성비: 3레인 최대 4카드, 1.5배 상한, 생성 간격 `min(max(1.4, t×0.42), 3.6, t×0.72)`, 내장=가장 위험한 카드 / OS IME=보이는 아무 카드
- [x] 초성·단어 맞추기·받아쓰기: 10라운드, 점수 `max(50, 100 + 속도보너스 + min(combo,10)×10 − 힌트 30)`, 발음 힌트 3회, 받아쓰기 정답 비노출
- [x] 띄어쓰기: 6지문, 경계 판정, 첫 판정 정확도, 랭킹 없음
- [x] 게임 허브: 주간 피요컵 카드 + 5모드 + 띄어쓰기, 모드별 프리셋 3개(v3, 100항목) + 사용자 덱
- [x] 주간 피요컵: `flow_topik_beginner` v3, 월요일 00:00 JST 경계, 일반 흐름과 기록·제출·최고점 완전 분리
- [x] 결과 화면 5존, ~2.5초 연출(스킵 가능, 연출 중 CTA 비활성), S/A/B/C(`shared/tuning/game_rank_tuning.json`), 재도전 1탭
- [x] 공유 이미지 1200×1200 PNG, 로케일 로고, 공유 시트 / 사진 저장

### 2.7 사용자 덱·pro (F5.9, §8.4)

- [x] `.typedeck` container v1 / deck schema v1·v2 reader, 결정적 writer, `shared/piyodeck/fixtures` 21 case 전부 일치
- [x] SAF·VIEW·SEND 가져오기 → 8 MiB 제한 staging → 엄격 파서 → 미리보기 → 충돌 표 → 커밋 직전 재검증
- [x] 무료: 사용자 덱 3개까지 가져오기·연습·내보내기·삭제·같은 ID 교체
- [x] pro(비소모성 1개): 4번째 새 ID, 만들기·편집·공식 덱 사본 편집. 초안 자동 저장·복구·`base_version` 충돌
- [x] 세션 중 모달 금지, 세션 중 도착한 파일은 pending

### 2.8 설정·사운드·개인정보 (F10, F11, §10)

- [x] 설정 순서: 키보드 → 사운드 → 타이핑 표시 → 화면(언어·텍스트) → 연습 알림 → 피요 → 개인정보 → 앱 정보. 키보드 카드: 입력 모드 → 배열 segmented(OS 선택 시 0.45 비활성) → 가이드·로마자·햅틱
- [x] 효과음: CC0 클릭 1개 + 합성음(완료·실수·목숨·알 노크), 프리셋 3종, 다른 앱 음악 중단·duck 금지(audio focus 요청 안 함)
- [x] 발음: 선언 `audio` → canonical `audio/ko_<sha256 앞 20 hex>.mp3` → 오프라인 `TextToSpeech(ko-KR)`, 자동 재생 기본 OFF, 백그라운드 즉시 정지
- [x] 분석: `shared/analytics/events.json` 생성 Kotlin 계약, PostHog EU·기본 OFF·`$geoip_disable`, Crashlytics 동의 전 OFF. notice v2 사용 환경 속성의 Android 적용 여부는 **승인 필요**
- [x] UI 언어 ja/en/es/de/fr, 미지원·`ko`→en, 브랜드 ja=`ピヨキー` / 그 외 `typee`, 복수형·숫자 서식

### 2.9 태블릿 (§12.2 대응)

- [x] 가용 폭 기준 compact <600dp / medium 600–900 / wide ≥900, 최대 폭 720/1120, 두벌식 전체 폭·10키 600dp 상한
- [x] 회전·창 크기 변경 시 세션 상태 보존, 문제 재추첨·타이머 재시작 금지

## 3. 초기 아키텍처 (제안)

### 3.1 모듈

```text
android/
  app/                    진입점, navigation, DI 조립
  core/hangul/            순수 Kotlin(JVM): 조합 엔진, 자모 판정, 10키 recipe, OSIMETextJudge
  core/deckkit/           순수 Kotlin: 덱·카탈로그 모델, strict JSON, schema 검증, .typedeck reader/writer
  core/domain/            순수 Kotlin: 연습·게임 reducer, 점수, 커리큘럼·스트릭·복습·추천 규칙
  core/data/              파일 저장소(원자 쓰기·backup·quarantine), DataStore 설정, 번들 자산
  core/platform/          오디오, TTS, 알림, SAF, Billing, Play Games, 분석, 공유
  core/designsystem/      테마·색·타이포·아이콘·마스코트 Canvas·공통 컴포넌트
  feature/{onboarding,home,discover,practice,game,library,settings}/
```

- `core:hangul`·`core:deckkit`·`core:domain`에는 Android·Compose import를 금지한다.
- 과거 포트의 18모듈보다 줄였다. 기능 모듈 경계는 iOS `Features/` 폴더와 1:1로 맞춘다.

### 3.2 기술 선택

| 영역 | 제안 | 비고 |
|---|---|---|
| 언어·UI | Kotlin + Jetpack Compose, 단일 Activity | 버전은 A0에서 최신 안정판을 조사해 version catalog에 고정 |
| 최소/목표 SDK | min 26, target = 착수 시점 Play 요구 수준 | **승인 필요** |
| 저장 | iOS와 같은 **JSON 파일 저장소 + 원자 쓰기·backup·quarantine**, 설정은 DataStore | 과거 포트는 Room(v6까지 마이그레이션). iOS와 schema가 같으면 parity 검증과 이해가 쉽다 |
| 게임 렌더링 | Compose Canvas + frame clock, 판정은 monotonic 순수 reducer | Pixel급 60Hz 60초 stress 평균 ≥59fps, p95 ≤16.7ms, jank ≤1% |
| OS IME | `EditText`를 `AndroidView`로 감쌈 | 과거 포트에서 유효했던 결정 유지 |
| 오디오 | `SoundPool`(효과음) + 별도 MP3 player, audio focus 요청 안 함 | 과거 결정 유지 |
| 결제 | Play Billing one-time product, `PURCHASED`만 해제·acknowledge, `PENDING` 미해제 | 상품 ID는 iOS와 같은 `app.piyokey.deckmaker.lifetime` 제안 |
| 랭킹 | Play Games Services v2 선택형 어댑터, 로컬 기록이 원본 | 주간 피요컵 처리 **승인 필요** (§5) |
| 분석·진단 | PostHog Android, Firebase Crashlytics(Analytics 비활성) | 비밀값은 저장소 밖 |

### 3.3 공용 계약 연결

- 번들 자산: 빌드 시 `shared/mock_catalog/{catalog.json,decks,updates,audio}`,
  `shared/tuning/`, `shared/schema/deck.schema.json`을 assets로 복사(심볼릭·복제 금지, Gradle task).
- 테스트: 순수 모듈 테스트가 `shared/test_vectors.json`과 `shared/piyodeck/fixtures`를 직접 읽는다.
- 분석 계약: `tools/gen_analytics_contract.py`에 Kotlin 출력 경로를 다시 추가한다(현재 제거됨).
- 학습 문자열: iOS `KoreanLearningContent.strings`, `Localizable.strings`를 Android 리소스로
  변환하는 생성기를 `tools/`에 둔다(수작업 복제 금지).

## 4. 마일스톤

각 단계 종료 조건: 해당 단위·UI 테스트 통과 + 실기기 증빙(해당 시) + `release/evidence/<SHA>.json`
+ `DECISIONS.md` 갱신 + Issue/PR 1:1.

| 단계 | 범위 | 종료 조건 |
|---|---|---|
| **A0 골격** | Gradle wrapper·version catalog, 모듈 골격, 디자인 토큰, 자산 복사 task, `workspace_doctor --scope android` 복구, 로컬 검증 명령 | 빈 앱 debug 빌드·실기기 설치, 계약 파일 로드 테스트 |
| **A1 순수 코어** | `core:hangul`, `core:deckkit`(.typedeck 포함), analytics Kotlin 생성 | 벡터·schema·piyodeck fixture 전부 통과, hangul 커버리지 ≥95% |
| **A2 입력·연습** | 두벌식·천지인(플릭)·OS IME, 연습 세션·결과 | **실기기** 입력 지연 p95 ≤50ms, 2-pointer rollover 누락·중복 0, 삼성·Gboard IME 조합 확인 |
| **A3 데이터·발견** | 파일 저장소·복구, 번들/원격 카탈로그, 발견·상세·설치, 내 덱 | 손상 파일 복구·오프라인 플레이 테스트 |
| **A4 게임** | 흐름·산성비·초성·단어·받아쓰기·띄어쓰기, 결과·공유 | **실기기** 60fps gate |
| **A5 학습·리텐션** | 온보딩·부화·투어, 커리큘럼, 복습, 스트릭·데일리·랜덤5·리마인더, 홈 추천, 마스코트 | 부화 흐름·JST 경계·리마인더 실기기 수신 |
| **A6 사용자 덱·pro** | SAF 가져오기·내보내기, Deck Maker, Billing | 라이선스 테스터 구매·pending·환불 |
| **A7 플랫폼 연동** | 설정 전체, 사운드·발음, Play Games, 분석·동의, 태블릿 적응형 | 타 앱 음악 공존, 동의 OFF 무네트워크 |
| **A8 출시 후보** | 로컬라이제이션 검수, 접근성, 스토어 자산·Data safety, 서명 | 동일 서명 후보의 통합 실기기 QA |

동시 진행 제한(ROADMAP 운영 제한)상 Android 구현 In Progress는 한 번에 1개다.

## 5. 승인 필요 항목 (착수 전 사용자 결정)

1. **applicationId**: 과거 후보 `app.piyokey.piyokey` 재사용 여부. Play Console 등록 후 변경 불가.
2. **최소 SDK·지원 기기**: min 26 유지 여부, 태블릿을 1차 출시에 포함할지.
3. **주간 피요컵 랭킹**: Play Games 리더보드의 주간 경계가 JST 월요일 00:00과 다르다.
   (a) 로컬 주간 기록만 제공, (b) 주간 기간 리더보드를 쓰고 경계 차이를 수용, (c) 자체 서버 — 중 선택. 무계정·쓰기 API 금지 철칙상 (c)는 PRD 변경이 필요하다.
4. **분석 notice v2**: iOS 전용인 국가·기기 사용 환경 속성을 Android에도 적용할지.
5. **콘텐츠·발음 권리**: 582개 gTTS 음원의 `content_rights_confirmed` gate는 iOS와 공통으로 열려 있다. Play 배포 전 판단 필요.
6. **저장 방식**: JSON 파일 저장소(제안) vs Room.
7. **추적**: GitHub Issue·Linear 티켓(예: 기존 TYP-119 재사용 여부)과 Orca worktree 배정.

### 5.1 잠정 결정 (구현 기본값, 2026-09-30)

사용자가 "출시 가능한 수준까지 구현·테스트"를 지시해 아래 기본값으로 진행한다. 외부 등록 전
사용자가 바꿀 수 있도록 모두 설정값/빌드 입력으로 분리한다.

1. applicationId: `app.piyokey.piyokey`를 기본값으로 쓰되 `PIYOKEY_APPLICATION_ID`로 바꿀 수 있고,
   배포 AAB는 `PIYOKEY_APPLICATION_ID_CONFIRMED`가 같아야만 만든다. 코드 namespace는 `app.piyokey.android`.
2. minSdk 26, targetSdk 36, compileSdk 37. 휴대폰·태블릿 모두 지원(적응형 레이아웃).
3. 주간 피요컵: 로컬 JST 월요일 주간 기록이 원본. Play Games에는 `cup_weekly_flow` 보드 ID가
   주입된 경우에만 제출하고 Play의 주간 기간 표시를 사용한다(경계 차이 수용, 결과 화면에 로컬 주간 기록 표시).
4. notice v2: iOS와 같은 흐름·버전(2)을 쓰고, 사용 환경 속성은 앱 locale 지역과 기기 형태(phone/tablet)만 보낸다.
5. 저장: iOS와 같은 JSON 파일 저장소 + SharedPreferences(iOS UserDefaults 키 그대로).
6. 모듈: 계획 §3.1의 feature 모듈 대신 `:core:hangul`·`:core:deckkit`·`:core:domain`(순수) + 단일 `:app`
   (패키지 분리)로 단순화했다. 규칙은 `android/README.md`.
7. 추적 Issue·Linear 생성은 외부 작업이므로 사용자 확인 뒤 연결한다.

## 6. 과거 포트에서 가져올 교훈

- 유지할 결정: 순수 코어가 공용 fixture를 직접 읽기, OS IME `EditText`, audio focus 미요청,
  SAF staging 제한 복사와 커밋 전 재검증, Billing `PURCHASED`만 해제, Play Games 선택형 어댑터와
  로컬 원본, 입력 지연 계측 방식(pointer `uptimeMillis` → 해당 revision의 첫
  `registerFrameCommitCallback`, 워밍업 20 + 측정 100, nearest-rank p95), release 빌드의
  fail-closed 서명 입력 검사.
- 피할 것: 실기기 gate를 마지막에 몰기, 에뮬레이터 결과를 실기기 증빙으로 쓰기, 거대한 dirty
  worktree에서 선별 이전, Material 기본 테마로 시작, 게임 규칙을 iOS 스냅샷에 고정해 drift 발생.
- 과거 포트가 놓친 iOS 변경(이번 범위에 포함): 천지인·플릭, OS 천지인 중간 상태, 설정 IA 재편,
  온보딩 끝 알림→개인정보 순서, notice v2, 모든 키보드 랭킹 참여, 피요컵/흐름 분리, 서버 점수 재조회,
  흐름 초급 v5 보드.

## 7. 확인된 불일치 (포팅 전 정리 대상)

- 음원 수: PRD F11/§14는 581개, `shared/mock_catalog/audio/`와 `AUDIO_PROVENANCE.md`는 582개.
- PRD F2a의 "builtin 기록만 Game Center 제출" 문장은 v6.28 결정으로 대체됨.
- PRD §8.4의 "v1 reader는 schema 1만" 문장은 v6.11·`SPEC.md`(1·2 모두 읽음)와 다름.
- iOS `ChoseongQuizView`(객관식)는 호출처가 없는 코드다. 포팅하지 않는다.
- `shared/mock_catalog/spacing_passages.json`은 iOS에 번들되지 않고 Swift에 하드코딩돼 있다.
  Android는 JSON을 원본으로 읽고, iOS 쪽 정리는 별도 작업으로 둔다.
