# Game Center 1.1.1 핫픽스 운영 기록

2026-09-10 JST, Issue #181 / PR #183. 앱 공통 수정, 구형 4개 정리, 필요 시 Flow 초급
1개 교체를 사용자가 명시적으로 승인했다. 계정·점수는 삭제하지 않았다.

## 확인하고 저장한 App Store Connect 변경

앱 `6794853985` / Bundle ID `app.piyokey.Piyokey`.

| 항목 | 저장 후 다시 확인한 상태 |
|---|---|
| 기본 보드 | `piyokey.v4.flow.beginner`의 Default 표시 |
| 구형 Flow 초급 | `piyokey.v3.flow.beginner` — Archive 완료, Unarchive 액션 확인 |
| 구형 Flow 중급 | `piyokey.v3.flow.intermediate` — Archive 완료, Unarchive 액션 확인 |
| 구형 Flow 고급 | `piyokey.v3.flow.advanced` — Archive 완료, Unarchive 액션 확인 |
| 구형 피요컵 | `piyokey.v3.cup.weekly.flow` — Archive 완료, Unarchive 액션 확인 |
| 목록 | 위 네 개 처리 후 현행 16개 / Archived 4개 확인. 아래 v5는 이후 추가됨 |
| 앱 버전 초안 | 1.1.1, Prepare for Submission; Build 미선택·미업로드 |
| 새 기능 안내 | ja, en-US, en-GB, en-AU, en-CA, ko 저장 후 언어 전환·재조회 확인 |
| 앱 출시 설정 | Manually release this version; 즉시 업데이트 배포 옵션 유지; 평점 유지 |
| 공개 앱 | 여전히 1.1 (24), Ready for Distribution |

Archive는 표시명 변경과 다른 실제 보관 처리다. Apple 안내상 Game Center 화면과
리더보드 조회에서 빠지고 되돌릴 수 있으며, 반영에는 최대 24시간이 걸릴 수 있다.
보관 ID를 사용하는 과거 앱은 그 보드를 조회할 수 없게 된다. 공개 1.1 (24)와 이번
후보의 16개 ID에는 보관한 네 ID가 없다. 구형 점수 관리 화면에는 보관된 항목도
남아 있으므로 해당 화면의 개수를 현재 공개 보드 개수로 해석하지 않는다.
[Apple 관리 문서](https://developer.apple.com/help/app-store-connect/configure-game-center/manage-leaderboards)

## Flow 초급 한 개를 교체하는 근거

ASC 점수 관리에서 일반 Flow 초급 v4의 상위 50개와 현재 피요컵 7개를 읽었다.
두 목록에서 같은 사용자·같은 점수·같은 제출 날짜인 기록 3건이 일치했다:
10,075점 (9월 7일), 1,929점과 1,812점 (9월 9일). 공개 앱의 이중 제출 코드와
부합하는 혼합 정황이다. 개별 게임의 출처를 이 일치만으로 확정하지는 않는다.
조회된 Score Context는 모두 0이어서 일반 Flow와 피요컵을 서버 값으로 구분할 수
없다. 점수를 선택적으로 지우거나 옮기지 않고 초급 보드 한 개를 교체한다.

## 생성·심사 제출한 교체 보드

- ID: `piyokey.v5.flow.beginner`
- Reference Name: Flow Beginner v5
- ASC 리소스: `b36cf5f3-75b2-41fe-9101-fd241621a4f9`
- Classic, Integer, Best Score, High to Low, 0–1,000,000, Hidden = No.
- en-US / ja / ko 표시명·설명·점수 단위 저장. 설명은 일반 Flow 전용·모든 키보드
  참여·피요컵 별도 집계를 명시한다. 콘텐츠와 채점은 `flow_topik_beginner` v3 그대로다.
- 2026-09-10 오전 JST, 이 보드 한 개만 iOS 플랫폼 단독 심사 제출.
  제출 완료 창의 `1 Item Submitted`와 보드의 **Waiting for Review**를 확인했다.
- [보드](https://appstoreconnect.apple.com/apps/6794853985/distribution/gamecenter/leaderboards/b36cf5f3-75b2-41fe-9101-fd241621a4f9)
- [심사 제출 acb65714-82c5-477e-96ca-e39b320280c3](https://appstoreconnect.apple.com/apps/6794853985/distribution/reviewsubmissions/details/acb65714-82c5-477e-96ca-e39b320280c3)

후속 Game Center 항목은 앱 새 바이너리 없이 별도 심사할 수 있다.
이번 제출은 앱 1.1.1의 심사 제출·승인·배포를 의미하지 않는다.
[Apple 제출 문서](https://developer.apple.com/help/app-store-connect/manage-submissions-to-app-review/submit-game-center-components)

## 승인 후 앱 전환 순서 — 아직 미실행

1. v5가 ASC에서 Live인지 다시 확인한다. Waiting for Review를 Live로 간주하지 않는다.
2. `GameCenterLeaderboard.flowBeginner.rawValue`를 v5로 변경하고 Info.plist의 intended
   계약과 ID 단위 테스트를 함께 바꾼다. iOS 26 검증 후보는 intended만 사용하여
   released 응답을 확인한다. 서명 후보를 만들기 전에 해당 소스의 리뷰·병합 gate를 통과한다.
3. 출시 계정과 분리한 테스트 계정으로 정상 Flow → 피요컵 → 정상 Flow를 확인한다.
   v5에는 일반 Flow만, 기존 weekly v4에는 피요컵만 들어가야 한다. 세 입력 방식,
   낮은 결과의 CTA, 인증·오프라인 복귀·수동 재시도도 확인한다.
4. Live·실기기 확인을 기록한 후 baseline의 Flow 초급도 v5로 바꾸고 최신 main의
   최종 RC를 검증한다. iOS 16–25는 baseline만 사용하므로 이 단계를 빼지 않는다.
5. v5를 사용하는 새 앱의 배포·조회·제출 확인 후 기본 보드를 v5로 바꾸고 기존
   Flow 초급 v4를 보관한다. 그전에는 v4를 유지한다. 최종 현행 구성은 기존과 같은
   일반 15개 + 주간 1개다. 중급·고급·다른 게임 12개·weekly ID는 바꾸지 않는다.

기존 앱의 v4 서버 최고점을 v5로 복사하지 않는다. 새 일반 Flow 기록과 기기에
남아 있는 일반 Flow 원본만 보충 제출한다. 피요컵 원본과 출처를 알 수 없는
`legacy_mixed_progress`는 v5로 옮기지 않는다. 과거 원본이 압축되어 사라진 경우
이전 최고점을 복구했다고 표현하지 않는다. 현재 source/baseline은 계속 v4이며
미승인 v5를 사용하는 바이너리는 만들지 않았다.

## 앱 공통 수정과 남은 gate

- 구현 검증 SHA `e074ed10bfafe1ddb5e5b324ec9f67e8f1f92bfe`: 모든 키보드 참여,
  피요컵/일반 Flow 분리, Live baseline 유지, 등록 상태·timeout·재시도·순위 재조회.
- 같은 SHA에서 Python 110, Swift 67, iOS 단위 133, iPhone UI 2, iPad UI 2 통과.
  실제 서비스의 controlled adapter 검증은 서버 수신 증빙을 대신하지 않는다.
- 정확한 PR head의 Claude 리뷰가 필요하다. 직전 시도는 API 429 / usage credits
  exhausted로 시작되지 않았고 이전 review:passed를 재사용하지 않는다.
- maintainer 승인·병합, 최신 main 전체 iOS 회귀, 서명·같은 빌드의 실제 Game Center
  제출·조회와 App Review는 미완료다. #180 분석의 개인정보·동의·수신 gate도 별도다.
- 과거 앱 1.1의 artifact 및 외부 gate는 새 1.1.1 (25)의 증빙으로 재사용하지 않는다.

## 같은 날 후속 서버 확인 보강

사용자가 버튼과 실제 서버 기록을 각각 확인한 핫픽스 제출을 재요청했다. 전송 콜백
성공 후 서버 score를 읽어 보관된 최고점 이상인지 확인하는 단계와 미확인·재시도 상태,
피요컵 회차 인스턴스 제출을 추가했다. 이 변경은 위 e074ed1 이후이므로 새 SHA의
검증과 exact-head 리뷰가 필요하다. 실제 v5 승인은 여전히 Waiting for Review이며
iPhone은 미연결, 연결된 iPad는 잠겨 있어 새 빌드 실제 서버 검증은 아직 못 했다.

스크린샷 확인에서 기존 하단 액션의 랭킹 버튼이 고정 재도전 영역에 일부 가려지는
경우도 발견했다. 모든 랭킹 게임이 공유하는 점수 카드 안으로 버튼·등록 상태를
옮기고, 결과 연출 완료 조건은 유지했다. UI 테스트는 단순 존재/전체 화면 경계 대신
상단 Done 아래와 고정 Retry 위의 실제 표시 위치를 검증하도록 보강했다.
