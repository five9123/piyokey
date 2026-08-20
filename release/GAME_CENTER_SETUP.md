# PIYOKEY Game Center 설정

앱 코드는 아래 ID를 고정 계약으로 사용한다. App Store Connect에서 ID를 만든 뒤에는 수정할 수 없으므로 철자와 점(`.`), 밑줄(`_`)을 그대로 입력한다.

## 1. 출시 App ID와 기능 활성화

1. 최종 Bundle ID를 확정하고 Apple Developer의 해당 App ID에서 **Game Center**를 활성화한다.
2. Xcode의 Hanco 타깃에는 `GameKit.framework`, `com.apple.developer.game-center` entitlement가 이미 추가되어 있다.
3. App Store Connect의 앱 버전에서 **Game Center**를 활성화한다.

최종 Bundle ID는 `app.piyokey.Piyokey`다. 이 ID의 App ID에서 Game Center를 활성화하고 Distribution archive의 프로비저닝 프로파일도 같은 ID로 생성한다.

### 런타임 availability 계약

앱은 `Info.plist`의 두 배열로 운영 계약을 분리한다.

- `PiyokeyGameCenterIntendedLeaderboardIDs`: 이 빌드가 지원할 수 있는 전체 ID
- `PiyokeyGameCenterAvailableLeaderboardIDs`: 출고 시점에 이미 Live가 확인된 baseline ID

enum이나 intended 목록에 ID가 있다는 이유만으로 제출·순위 조회·CTA를 열지 않는다. iOS 26+의 인증된 세션에서는 `GKLeaderboard.loadLeaderboards(IDs: nil)`로 전체 목록을 한 번 로드하고, `releaseState`가 `.released`인 항목과 intended의 교집합을 실제 availability로 사용한다. 성공 응답은 baseline보다 우선하며, probe 오류 또는 5초 무응답 때는 baseline으로 fallback한다. 각 probe는 generation token을 사용하므로 timeout 뒤 도착한 callback은 무시한다. 플레이어가 바뀌면 동적 결과를 폐기하고 다시 한 번 확인한다. 따라서 sandbox의 `.prereleased` 및 미Live flow v4/weekly는 dashboard/submit에서 차단되고, Live가 된 뒤에는 build 6에서도 새 바이너리 없이 활성화된다. 키가 없거나 알 수 없는 ID면 fail-closed다.

`GKLeaderboard.releaseState`는 iOS 26+ API다. iOS 16~25는 baseline만 사용한다. 2026-08-20 현재 flow v4 3개와 weekly v4를 포함한 16개 운영 ID가 모두 Live로 확인되어 다음 출고 baseline에도 전부 포함한다. 따라서 iOS 16~25에서도 정확한 번들 덱·버전·내장 키보드 기록은 해당 보드에 제출할 수 있다.

ID 추가 순서는 다음과 같다.

1. 새 계약은 먼저 `PiyokeyGameCenterIntendedLeaderboardIDs`에만 추가한다.
2. App Store Connect에서 리더보드가 **Live**인지 확인한다.
3. 출시 계정과 분리한 Game Center 계정으로 TestFlight 실기기 제출·순위 조회·대시보드 재진입을 확인한다.
4. 다음 출고 baseline에도 포함하려면 `PiyokeyGameCenterAvailableLeaderboardIDs`에 추가하고 `python3 tools/release_preflight.py`를 통과시킨다.
5. 동일 RC로 아래 실기기 체크를 다시 통과한 뒤 `game_center_live_device_check`를 `true`로 기록한다.

심사 대기·시작일 대기·비활성 상태의 ID는 baseline 배열에 넣지 않는다. 날짜나 앱 버전으로 자동 활성화하지 않으므로, 서버 상태가 예정보다 늦어져도 미가용 ID의 dashboard/submit을 호출하지 않는다. 현재 16개보다 새 계약을 추가할 때도 먼저 intended에만 넣고 Live·실기기 확인 뒤 baseline으로 승격한다.

## 2. 게임×난이도 클래식 리더보드 15개

모두 **Classic**, **Best Score**, **High to Low**, 정수 점수, 단위 `点 / point(s) / 점`, 범위 0~1,000,000으로 만든다. 대상은 아래 각 게임의 번들 전용 100단어 복합 난이도 세트 v3이며 내장 키보드 기록만 제출한다. 다운로드 덱·사용자 덱·OS IME 기록은 로컬 최고 기록만 사용한다.

| Leaderboard ID | 고정 콘텐츠 | 일본어 표시명 | 영어 표시명 | 한국어 표시명 |
|---|---|---|---|---|
| `piyokey.v4.flow.beginner` | `flow_topik_beginner` v3 | フロー・初級 | Flow · Beginner | 흐름 · 초급 |
| `piyokey.v4.flow.intermediate` | `flow_topik_intermediate` v3 | フロー・中級 | Flow · Intermediate | 흐름 · 중급 |
| `piyokey.v4.flow.advanced` | `flow_topik_advanced` v3 | フロー・上級 | Flow · Advanced | 흐름 · 고급 |
| `piyokey.v3.acid_rain.beginner` | `acid_rain_topik_beginner` v3 | 単語の雨・初級 | Word Rain · Beginner | 산성비 · 초급 |
| `piyokey.v3.acid_rain.intermediate` | `acid_rain_topik_intermediate` v3 | 単語の雨・中級 | Word Rain · Intermediate | 산성비 · 중급 |
| `piyokey.v3.acid_rain.advanced` | `acid_rain_topik_advanced` v3 | 単語の雨・上級 | Word Rain · Advanced | 산성비 · 고급 |
| `piyokey.v3.choseong.beginner` | `choseong_topik_beginner` v3 | 初声クイズ・初級 | Initials Quiz · Beginner | 초성 맞추기 · 초급 |
| `piyokey.v3.choseong.intermediate` | `choseong_topik_intermediate` v3 | 初声クイズ・中級 | Initials Quiz · Intermediate | 초성 맞추기 · 중급 |
| `piyokey.v3.choseong.advanced` | `choseong_topik_advanced` v3 | 初声クイズ・上級 | Initials Quiz · Advanced | 초성 맞추기 · 고급 |
| `piyokey.v4.word_match.beginner` | `word_match_topik_beginner` v3 | 単語クイズ・初級 | Word Quiz · Beginner | 단어 맞추기 · 초급 |
| `piyokey.v4.word_match.intermediate` | `word_match_topik_intermediate` v3 | 単語クイズ・中級 | Word Quiz · Intermediate | 단어 맞추기 · 중급 |
| `piyokey.v4.word_match.advanced` | `word_match_topik_advanced` v3 | 単語クイズ・上級 | Word Quiz · Advanced | 단어 맞추기 · 고급 |
| `piyokey.v3.dictation.beginner` | `dictation_topik_beginner` v3 | 書き取り・初級 | Dictation · Beginner | 받아쓰기 · 초급 |
| `piyokey.v3.dictation.intermediate` | `dictation_topik_intermediate` v3 | 書き取り・中級 | Dictation · Intermediate | 받아쓰기 · 중급 |
| `piyokey.v3.dictation.advanced` | `dictation_topik_advanced` v3 | 書き取り・上級 | Dictation · Advanced | 받아쓰기 · 고급 |

게임 종류나 난이도가 다르면 점수를 섞지 않는다. 흐름은 가속 상한이 1.5배에서 1.8배로 바뀌어 `piyokey.v4.flow.*`, 단어 퀴즈는 4지선다에서 직접 타이핑으로 채점 조건이 바뀌어 `piyokey.v4.word_match.*` 보드로 분리한다. 이후 콘텐츠나 채점 조건이 바뀐 다음 버전도 기존 보드에 제출하지 않고 새 계약 ID를 만든다.

## 3. 주간 피요컵 recurring 리더보드 1개

| Leaderboard ID | 종류 | 일본어 표시명 | 영어 표시명 | 한국어 표시명 |
|---|---|---|---|---|
| `piyokey.v4.cup.weekly.flow` | Recurring | 週間ピヨカップ | Weekly Piyo Cup | 주간 피요컵 |

다음 값으로 설정한다.

- 콘텐츠: 번들 `flow_topik_beginner` v3
- 규칙: 흐름 모드 60초·3목숨·0~50초 1.0→1.8배 점진 가속·내장 키보드 고정
- Score Submission Type: **Best Score**
- Sort Order: **High to Low**
- Duration: **1 week**
- Restarts Interval: **1 week**
- Start Date and Time: 2026-08-17 00:00 JST (2026-08-16 15:00 UTC)

피요컵 점수는 recurring 보드와 `piyokey.v4.flow.beginner`에 동시에 제출한다. App Store Connect는 아직 심사 전인 새 보드에 `Make Default`를 제공하지 않으므로, `piyokey.v4.cup.weekly.flow`이 Live 상태가 된 뒤 기본 리더보드로 지정한다.

## 4. 피요 성장 업적 5개

모두 공개·1회성 업적으로 만들고 총점은 180점으로 설정한다. 앱은 로컬 성장값을 기준으로 진행률을 보고하므로 Game Center가 오프라인 성장의 전제 조건이 되지 않는다.

| Achievement ID | 일본어 표시명 | 완료 조건 | 점수 |
|---|---|---:|---:|
| `piyokey.growth.hatching` | はじめてのふか | 챕터 1개 완료 | 10 |
| `piyokey.growth.chick` | ひよこに成長 | 챕터 3개 완료 | 20 |
| `piyokey.growth.rooster` | タイピングマスター | 챕터 6개 완료 | 50 |
| `piyokey.growth.typed_12000` | 12,000字母の羽 | 누적 12,000자모 입력 | 50 |
| `piyokey.growth.streak_30` | 30日のきずな | 최장 스트릭 30일 | 50 |

Game Center에 점수가 한 번이라도 정상 제출되거나 기존 순위가 확인되면 옷장에 `チャンピオントロフィー`가 영구 해금된다. 순위는 내려갈 수 있으므로 현재 순위를 성장 단계나 영구 해금 조건으로 사용하지 않는다.

## 5. 검증

1. Game Center에 로그인한 개발 기기에서 게임 탭을 열어 인증 배너와 계정명을 확인한다.
2. 덱을 다운로드하지 않은 상태에서 게임 허브의 주간 피요컵이 바로 시작되고 내장 키보드만 표시되는지 확인한다.
3. 피요컵 결과가 recurring 보드와 `flow.beginner` 클래식 보드 양쪽에 제출되는지 확인한다.
4. 다섯 게임의 초급·중급·고급 v3 번들 세트가 각각 대응하는 15개 보드에만 제출되고, 흐름만 v4·산성비/초성/받아쓰기는 v3·단어 퀴즈는 v4 계약을 사용하는지 확인한다. 업데이트 버전·다운로드 덱·OS IME에서는 순위 CTA가 나타나지 않아야 한다.
5. 오프라인에서 기록을 만든 뒤 같은 주에 온라인으로 돌아와 게임 탭을 열고 최고 점수가 보충 제출되는지 확인한다. 다음 JST 월요일 00:00 이후에는 지난주 피요컵 점수가 새 회차에 제출되지 않는지도 확인한다.
6. 피요 옷장에서 챔피언 트로피가 재실행 뒤에도 유지되는지 확인한다.
7. App Store Connect에 등록한 5개 성장 업적의 진행률과 완료 배너를 확인한다.
8. TestFlight 검증에는 출시 계정과 분리한 Game Center 테스트 계정을 사용하고, 제출 전 테스트 점수를 정리한다.
9. 로그아웃 상태에서 CTA를 연타하고 인증을 취소한 뒤 다시 탭해 재인증할 수 있는지 확인한다. 인증 시트가 완전히 닫히기 전 리더보드가 겹쳐 열리면 실패다.
10. 리더보드 표시 중 재탭, 앱 백그라운드→복귀, 오프라인→온라인 복구에서 중복 대시보드·강제 종료·무한 로딩이 없는지 확인한다.
11. 다운로드/사용자 덱과 OS IME 결과에는 CTA가 없고, 계약에 포함된 번들+내장 키보드 결과에만 CTA가 있는지 확인한다.
