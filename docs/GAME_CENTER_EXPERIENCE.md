# Game Center 경험 통합 — Issue #184

2026-09-11 JST. 대상은 iOS/iPadOS이며 앱의 경쟁·성장 규칙은 PRD F6f와 DECISIONS가 기준이다. #181/PR #183의 등록 복구와 Flow/Cup 격리 소스를 재사용한다. 이 문서는 #184의 UX, Apple 기능 평가, 운영 관측, 검증 범위를 연결한다.

## 화면과 데이터 계약

| 화면 | 사용자가 확인하고 할 수 있는 일 |
|---|---|
| 게임 허브 | 게임 이름과 마지막 플레이 난이도의 세계 순위. 이력이 없으면 초급. 플레이 카드와 별개인 44pt 이상 순위 버튼으로 해당 네이티브 보드를 연다. |
| 난이도 선택 | 세 단계 각각의 세계 순위, 서버 최고점/미참여 상태, 마지막 확인 시각, 주변 기록·친구 비교, 새로고침. 다운로드·사용자 덱은 로컬 기록을 유지한다. |
| 피요컵 | 이번 주 로컬 최고점, 이번 주 서버 순위·최고점, 사용자 현지 시간대가 명시된 종료 시각. 플레이와 순위 보기는 다른 터치 영역이다. |
| 게임 결과 | 기존 이번 판 점수·재도전은 유지하고 공통 순위 패널에서 서버 최고점과 등록 대기/전송/확인/완료/실패/미확인 및 재시도를 제공한다. |
| 주변 순위 | 정확한 게임·단계 제목, 세계/친구 전환, 본인 주변 서버 기록과 바로 위 기록에 도달하기 위한 점수 차이. 1위·동점·미참여·빈 보드·친구 기록 없음·조회 실패를 구분한다. 전체 목록은 같은 범위의 Apple UI에서 본다. |
| MY 피요 상세 | 기존 로컬 성장 업적 5개의 진행률과 트로피 조건/영구 해금 상태, 네이티브 업적 화면 접근. 변동 순위로 영구 보상을 회수하지 않는다. |

요약과 주변 기록은 계정·보드·세계/친구 범위·주간 회차를 분리한다. 친구 순위는 결과의 세계 순위나 제출 확인을 덮어쓰지 않는다. 서버가 실제로 돌려준 바로 위 순위만 목표로 사용한다. 점수 차이는 “기록에 도달”을 의미하며 다음 판의 추월이나 순위 상승을 보장하지 않는다. 참가자 수는 점수를 등록한 플레이어 수이며 동시 접속자가 아니다.

조회는 첫 진입, 60초 이상 지난 화면 재진입, 앱 복귀, 제출 후 제한된 재조회, 수동 새로고침을 사용한다. 타이머는 주간 경계의 표시를 갱신하며 초 단위 polling을 하지 않는다. 주변 기록은 상세 진입 시에만 불러오고 최대 3회 조회로 이동 중인 순위 주변 범위를 맞춘다. timeout·토큰으로 늦은 응답을 무효화한다. 조회 실패 시 마지막 성공값과 확인 시각을 유지하며 실패 안내를 함께 표시한다. 성공한 빈 응답도 캐시해 반복 요청을 피한다.

피요컵은 앱의 JST 월요일 00:00 회차뿐 아니라 GameKit이 반환한 `startDate`와 7일 `duration`도 확인한다. 계정 변경·주간 경계에서 이전 캐시와 요청을 제거하고 새 최고점 제출은 해당 보드의 두 상세 범위 캐시를 무효화한다. 이전 보드 점수, 이전 주 점수, 임의로 계산한 순위는 현재 기록으로 표시하지 않는다.

작은 화면의 접근성 글자 크기에서는 주변 기록의 이름·점수를 세로로 배치한다. MY 피요 상세는 720pt 이하 읽기 폭과 세로 프로필을 사용하고 스탬프 7칸은 가로 스크롤로 읽을 수 있다. 업적 카드의 접근성 컨테이너는 진행률과 네이티브 버튼을 독립된 요소로 보존한다.

Game Center 인증·대시보드는 사용자가 명시적으로 선택한 세션 밖 경로에서만 연다. 해당 네이티브 화면을 준비하는 동안 다른 학습 세션으로 진입하지 못하게 하고, 응답 없는 인증 요청은 timeout 후 앱 조작을 돌려준다. 일반 순위 조회에는 화면 전체 로딩을 쓰지 않는다. 지연된 업적 완료 배너는 끄고 로컬 성장 연출과 보상은 보존한다.

## Apple 기능 평가

지원 버전은 이 앱의 최소 iOS 16 및 설치된 Xcode 26.6 GameKit SDK 선언을 기준으로 확인했다. Apple 최신 API가 제공돼도 기존 경쟁 규칙을 자동 확장하지 않는다.

| 기능 | 사용자 가치 | 지원 OS / 현재 구현 | ASC 설정 | 검증 |
|---|---|---|---|---|
| 클래식 리더보드·친구 비교 | 게임과 난이도별 자기 위치 확인 | `loadEntries` iOS 14+, 앱 전 지원 OS. 기존 15개 보드에 요약·주변 기록·친구 범위 추가 | 기존 Live ID 재사용, 신규 설정 없음 | 16개 매핑·계정/범위 격리·빈 응답·실패·늦은 응답의 제어된 회귀; 실제 친구 계정 조회는 외부 gate |
| Recurring leaderboard | 매주 다시 경쟁할 기회 | iOS 14+, 현재 Cup v4; `.allTime`으로 현재 인스턴스의 순위 조회 | 주간 시작·지속·반복 7일 유지 | JST 경계, 반환 회차 검증, Flow 격리; 실제 주간 전환은 외부 gate |
| Access Point / Game Overlay | 익숙한 상세 순위·프로필·업적 경험 | iOS 18+ 정확한 보드·범위로 trigger; iOS 16–17은 기존 전체 보드 fallback. 업적 state 접근은 전 지원 OS | 기존 보드·업적 사용 | 16개 정확한 보드 인자와 친구 범위·업적 경로를 production service + doubles로 확인. 구버전 실제 화면은 미실행 gate |
| 업적 | 학습 성장과 Game Center 기록 연결 | 로컬 챕터 1/3/6, 누적 12,000자모, 최장 스트릭 30일의 5개 진행률 | 기존 Live 업적 5개 유지 | 임계값·상한/하한, 영구 트로피, 네이티브 업적 경로, 배너 없음 |
| 새 Challenges | 친구끼리 한정된 시도/기간으로 경쟁 | 새 API iOS 26+, 이번 작업은 설계까지 | 현재 정의 없음. 신규 정의·최소 앱 버전·보드 연결 필요 | 아래 별도 제출 계약과 두 계정 실기기 시나리오를 충족해야 활성화 |
| Activities·딥링크 | 초대에서 정확한 게임·단계로 복귀 | iOS 26+, 현재 listener/정의 없음. 이번 작업은 설계까지 | activity 정의·보드 연결·properties 필요 | warm/cold launch, 진행 중 세션, 잘못된/종료된 activity, 온보딩·콘텐츠 가드 |

Apple은 게임 메뉴의 맥락에 맞는 진입점과 자체 버튼에서 Game Center UI를 여는 구성을 지원한다. 본 앱은 화면 구석의 상시 아이콘 대신 게임·난이도 카드의 명확한 버튼을 사용한다. [Apple HIG](https://developer.apple.com/design/human-interface-guidelines/game-center), [Apple 엔지니어의 custom button 안내](https://developer.apple.com/forums/thread/814455).

Apple News Puzzles는 퍼즐 종류와 Sudoku 난이도별 순위를 구분한다. 이를 게임×단계 분리에 참고했다. Wurdweb의 공식 설명은 일간·주간·월간 경쟁을 소개한다. 본 앱에서는 기존 주간 피요컵의 반복 참여에 적용하며 일간·월간 보드는 추가하지 않는다. 두 사례의 모든 화면을 직접 사용했다고 주장하지 않는다. [Apple News 안내](https://support.apple.com/en-gb/guide/iphone/iph4883822da/26/ios/26), [Wurdweb 공식 사이트](https://wurdweb.com/), [Type Flash 공식 App Store 설명](https://apps.apple.com/id/app/type-flash-typing-game/id1481017082).

## 2026-09-11 운영 관측 — 읽기 전용

App Store Connect의 앱 6794853985에서 직접 조회했다. 설정·점수·심사 제출은 변경하지 않았다.

- 목록에는 Live 보드 **17개**가 있다. 현행 앱의 intended/baseline은 그중 **16개**다: Flow 초급 v5, Flow 중·고급 v4, Word Match 3단계 v4, Acid Rain/Choseong/Dictation 9개 v3, Weekly Cup v4. 이전 Flow 초급 v4도 Live이며 **Default**를 유지한다.
- Flow 초급 v5 상세는 Live, Integer, Best Score, High to Low, 0–1,000,000, Hidden No다. v4 서버 최고점을 v5로 복사하지 않는다.
- Cup v4 상세는 Live, 시작 2026-08-17 00:00 GMT+9, Duration 7일, Restart Interval 7일, Best Score/High to Low, 0–1,000,000, Hidden No다.
- 성장 업적 5개 모두 Live. Challenges·Activities에는 정의 목록이 없고 Add 버튼만 있다. v5/Cup 상세의 activity/challenge 연결도 선택 전이다. Legacy Challenges는 Deprecated이며 Turn On 상태다.
- 콘솔 사이드바의 앱 1.1은 Ready for Distribution, 1.1.1은 Waiting for Review로 표시된다. #184 변경이 그 제출 빌드에 포함됐다는 뜻은 아니다.
- v5/Cup의 콘솔 현지화 목록은 en-US/ja/ko다. 인앱 5개 UI 언어와 네이티브 보드 표시 언어는 구분해야 한다. es/de/fr 콘솔 현지화는 후속 운영 변경 검토 항목이며 이 작업에서 저장하지 않았다.

[Game Center 목록](https://appstoreconnect.apple.com/apps/6794853985/distribution/gamecenter), [Flow v5](https://appstoreconnect.apple.com/apps/6794853985/distribution/gamecenter/leaderboards/b36cf5f3-75b2-41fe-9101-fd241621a4f9), [Weekly Cup](https://appstoreconnect.apple.com/apps/6794853985/distribution/gamecenter/leaderboards/b98569fe-dde9-44fb-a8cd-4a2a88c76e31). 이전 이행 근거는 [핫픽스 기록](../release/GAME_CENTER_HOTFIX_20260910.md)에 보존한다.

## 후속 확장 결정안

추천 순서는 **Activities 경로와 친구 도전용 시도 제출 계약을 함께 확정 → 한 게임·한 단계로 샌드박스 검증 → 범위 확대**다. Apple의 새 Challenges는 Best Score 보드에서도 동작하지만, 각 시도의 최신 점수를 제출해야 하며 제출 한 번이 도전 시도를 소비한다. 현행 서비스는 실패 복구·앱 시작·메뉴에서 저장된 최고점을 다시 제출하므로, 현재 보드에 Challenge만 연결하면 잘못된 시도가 기록될 수 있다. [Apple 제출 계약](https://developer.apple.com/documentation/gamekit/creating-engaging-challenges-from-leaderboards).

| 사용자 결정 | 구체적 제안 | 영향 |
|---|---|---|
| 첫 도전 범위 | Flow 초급의 현행 60초·3목숨 규칙, 모든 지원 입력 방식 유지. 친구끼리 반복 가능한 도전부터 검증 | 초대 대상·시도 수·기간은 ASC에서 지원하는 옵션과 제품 경쟁 규칙을 함께 확정 |
| 기존 보드 재사용 여부 | 기존 최고점 복구와 분리한 새 도전용 보드를 우선 검토. 기존 v5를 재사용하려면 전 클라이언트의 시도 제출 계약 전환과 최소 버전 설정이 선행 | 신규 ID/ASC 설정은 승인 후 생성. 현재 16개 데이터와 공개판을 오염시키지 않음 |
| 제출 정책 | 게임 종료와 조기 종료를 시도 완료로 처리하고 그 시도의 점수만 전달. GameKit의 오프라인 처리를 사용하며 startup의 최고점 재전송 경로와 분리 | 취소·강제 종료·중복 콜백·재접속에서 실제 시도 수를 두 계정으로 검증. 앱 자체 outbox를 그대로 재사용하지 않음 |
| Activities 연결 | 논리 경로 `game + difficulty + competition + contentVersion`의 allowlist를 정의와 보드 properties에 연결 | 임의 덱 ID나 외부 URL로 라우팅하지 않으며 유효하지 않은 정의는 안내 후 게임 허브로 복귀 |
| 콘솔 다국어 | 현행 보드·업적의 es/de/fr 이름·설명·점수 단위를 현지화한 리뷰용 변경표 준비 후 별도 적용 | 인앱 resource와 Apple 기본 UI의 언어 일관성 보강 |

후속 UX는 `친구 초대 수락 → activity 수신 → 세션·온보딩·콘텐츠 자격 확인 → 정확한 게임/단계의 준비 화면 → 플레이 → 해당 시도 점수 제출 → Apple 도전 결과 → 재도전`이다. 진행 중인 학습 세션에는 요청을 보관하고 완료 뒤에만 준비 화면을 연다. 시작 전 튜토리얼이 필요하면 대상 도전 문맥을 유지한다. warm/cold launch 모두 같은 route 검증을 통과해야 하며, 종료된 회차를 현재 주로 임의 바꾸지 않는다. [Activities 공식 문서](https://developer.apple.com/documentation/gamekit/creating-activities-for-your-game), [WWDC25 Games app](https://developer.apple.com/videos/play/wwdc2025/215/).

## 검증과 완료 경계

실제 실행 명령·결과·clean 구현 SHA는 PR 본문과 `release/evidence/<SHA>.json`에 기록한다. 제어된 framework doubles는 production service의 코드를 컴파일하며 네트워크·계정·Apple 서버를 대체한다. DEBUG의 `UITEST_GAME_CENTER_RANKINGS=1`은 표시 전용 fixture이며 Game Center API 제출/열기는 비활성이다. 이를 실서버 순위나 로그인 성공 증거로 사용하지 않는다.

필수 focused 범위는 Python contracts/preflight, GameCenterServiceTests·GameProgressStoreTests, 제어된 delivery 시나리오, 신규 허브/난이도/상세/성장 UI와 기존 결과·키보드 회귀다. 작은 iPhone SE와 iPad의 큰 글자 화면을 검사한다. 스크린샷은 xcresult 첨부로 남긴다. 버전별 실제 Game Center UI, 두 계정의 친구 비교·로그인 취소·오프라인 복구, 회차 전환, VoiceOver 실제 읽기 순서, Reduce Motion과 60fps·오디오 공존은 후속 실기기 gate다. 시뮬레이터 UI 성공을 이 gate의 완료로 표현하지 않는다.

#184의 완료 범위는 필수 소스·실행 가능한 검증·문서·트래커·현재 head Claude 리뷰·검증 근거가 연결된 PR 준비다. 병합은 maintainer의 기존 수동 gate, 출시와 서명·배포는 별도 절차를 따른다. #181의 외부 gate와 다른 열린 이슈는 이 작업으로 종료하지 않는다.
