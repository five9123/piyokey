# Android M7 M4 handoff

기준일: 2026-08-25 JST

## 범위

M4는 PRD F6의 흐름 게임과 F12 공통 결과의 Android 기반을 완성한다. 산성비,
초성 맞추기, 단어 맞추기, 받아쓰기, 띄어쓰기는 게임 허브의 계약된 순서와 비활성
안내만 유지하고 M6에서 같은 기반 위에 포팅한다. OS IME, 오디오, 공유, Play Games,
캐릭터 성장은 M6 범위다.

모듈 경계는 다음과 같다.

- `core:game`: Android/Compose에 의존하지 않는 monotonic 흐름 상태·reducer, 점수,
  콤보, 3목숨, 가속, pause/resume, CPM·랭크 계약
- `core:data`: Room v1→v2 migration, append-only `GameRecord`, 덱/게임/입력 방식별
  `DeckProgress`, 3개 흐름 프리셋과 공용 rank tuning loader
- `feature:game`: 게임 허브, 흐름 덱 선택, 카운트다운·레인·HUD·자모 트랙·고정
  두벌식 키보드·파티클, 결과·재도전
- `app`: 5탭 셸에서 발견/설치 덱과 게임 navigation·기록 저장을 연결

## 규칙 계약

- 3·2·1이 끝난 뒤에만 60초와 카드 이동을 시작한다.
- background에서는 rule clock을 멈추고 복귀 후 새 3·2·1을 센다.
- 노미스 완료는 +2초, 오타는 시간 차감 없이 콤보를 0으로 만든다.
- 카드 이탈은 목숨 1개를 잃고 세 번째 이탈에서 즉시 결과로 간다.
- 새 카드는 플레이 0~50초에 1.0→1.8배로 선형 가속한다. 진행 중 카드의
  속도는 바꾸지 않는다.
- 점수는 `자모 수 × 10 × 완료 시 콤보 배율`이며 배율은 1.0, 5콤보 1.2,
  10콤보 1.5, 20콤보 2.0이다.
- 번들 세 단계는 각 100단어이고 단계 내부와 단계 사이 중복이 없다. 테스트만
  명시적 seed를 사용한다.

Compose frame clock은 첫 프레임을 Android monotonic 세션 origin에 매핑한다.
프레임은 `Tick` cadence만 제공하며 제한시간·카드 이탈·점수 판정은 전부
`core:game` reducer가 계산한다.

## 저장·결과

Room v2 migration은 M3의 설치 덱, 다운로드 이력, catalog state와 recovery journal을
그대로 보존한다. 게임 결과는 `GameRecord`에 매번 추가하고 같은 transaction에서
`DeckProgress`의 plays, best score, best accuracy, last played를 갱신한다. M4 기록의
mode는 `flow`, input mode는 `builtin`이다.

등급은 `shared/tuning/game_rank_tuning.json`을 검증해 iOS와 같은 정확도 60%,
완료 자모 CPM 40%, 120 CPM cap, S/A/B 90/75/55를 사용한다.

## 검증

```sh
cd android
./gradlew \
  :core:game:test \
  :core:data:connectedDebugAndroidTest \
  :app:connectedDebugAndroidTest \
  :feature:game:lintDebug \
  :app:lintDebug \
  :app:assembleDebug
```

- JVM `core:game`: 11개 통과
- API 35 `hantap_test`: Room migration·프리셋·rank·기록과 M3 복구 8개 통과
- API 35 `hantap_test`: 기존 발견 흐름 1개 + 게임 허브→프리셋→카운트다운/고정
  키보드와 주간 피요컵→3목숨 종료→공통 결과 2개, 총 3개 통과
- `feature:game`/`app` lint error 0, debug APK 성공

연결된 Galaxy는 이번 검증에 사용하지 않았다. 60초 frame 평균/p95/jank와 M2
물리 입력 수치는 GitHub Issue #19의 출시 후보 통합 QA에서 수행한다.

## 후속

M5는 순수 curriculum/daily/review/streak 상태와 영속 schema를 먼저 만들고 홈·연습·
마이페이지에 연결한다. M6는 나머지 게임, OS IME, 설정·오디오·공유·온보딩·캐릭터,
Play Games 선택 어댑터와 출시 자동 gate를 완성한다.
