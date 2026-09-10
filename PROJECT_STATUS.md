# PIYOKEY 프로젝트 현황

마지막 갱신: 2026-09-10 JST
기준 저장소: `five9123-maker/piyokey`
기준 `main`: `git fetch --prune origin && git rev-parse origin/main`으로 확인

이 문서는 현재 열린 작업과 출시 gate만 유지한다. 제품 계약은 `PRD.md`, 확정
결정은 `DECISIONS.md`, 작업 순서는 `ROADMAP.md`, 실행 상태는
[PIYOKEY Development Project](https://github.com/users/five9123-maker/projects/1)를
기준으로 한다. 완료 작업의 상세 증빙은 해당 Issue·PR과 Git 기록에 남긴다.

## 현재 기준선

| 영역 | 현재 상태 | 다음 gate |
|---|---|---|
| iOS 공개판 | 2026-09-09 ASC 직접 조회: `1.1 (24)` Ready for Distribution, iPhone 15 Pro JM 설치본도 `1.1 (24)` | PostHog 수신은 2026-09-10 사용자 확인. 지역별 판매·Crashlytics 최초 크래시 수신 및 symbolication·기존 외부 gate는 별도 확인. 출시 후 Game Center 재발은 TYP-120으로 추적 |
| iOS 1.1 | build 24는 TYP-112·113·114를 포함한 `main` `85ebfadabc434659943ea8cd3edcb93a91a39a71`에서 생성됐고 ASC 선택 빌드·archive·iPhone 설치 버전이 일치 | Game Center 현행 16개 Live·entitlement·주간 주기는 확인됐으나 제출/재시도/순위 갱신 결함은 1.1.1 후보에서 수정. 2026-09-10 기본 보드 v4 전환·구형 4개 archive 완료, Flow 초급 v5는 별도 심사 대기. [조사 PR #179](https://github.com/five9123-maker/piyokey/pull/179) |
| Android | 기존 Kotlin/Compose 포트는 참고용 동결. 현재 제품·유지보수·CI·Play 출시 범위에서 제외 | 재개하지 않음. 사용자가 별도 승인한 새 PRD·초기 설계가 생길 때만 신규 작업으로 시작 |
| 웹 Builder | 별도 [`hanco_web`](https://github.com/five9123-maker/hanco_web) 저장소의 schema-v2 Builder PR #6 병합·배포 검증 완료 | 모바일과 교차 편집 회귀 유지. 이 저장소의 `web/`은 analytics 계약 패키지이며 웹 앱 본체가 아님 |
| CI·병합 | GitHub Actions 비활성. `docs/WORKFLOW.md`의 기본 수동 fail-closed 정책에 따라 모든 PR이 exact-head Claude review, focused local evidence, 최신 `origin/main` merged-tree 검증과 maintainer 승인을 요구 | 비활성 CI는 성공으로 간주하지 않으며 full HancoTests와 외부 gate는 focused evidence로 닫지 않음 |

## 열린 작업

| Issue | Project 상태 | 다음 한 단계 |
|---|---|---|
| [#181 Game Center 핫픽스](https://github.com/five9123-maker/piyokey/issues/181) | In Progress / `codex/181-separate-cup-flow` | 1.1.1 (25) 후보: 모든 키보드·피요컵 분리·공통 복구 구현 및 e074ed1 검증 후 서버 score 재조회·미확인 복구·주간 회차 고정 추가 검증 중. ASC 1.1.1 초안·6개 로케일 변경 안내 저장, 구형 4개 archive·기본 v4 전환 완료. Flow 초급 v5 심사 대기 → Live 확인 후 앱 전환. Claude 리뷰는 직전 API 429로 미완료이며 maintainer·서명·실기기 gate도 열림. 공개판은 1.1 (24) |
| TYP-120 Game Center 전수 조사 | [Draft PR #179](https://github.com/five9123-maker/piyokey/pull/179) | 16개 현행 보드 공통 availability·제출 재시도·순위 갱신 결함의 수정을 #181 / PR #183 핫픽스에 통합. 조사와 실기기 실패 응답 확보를 구분 |
| TYP-113 피요컵 OS 키보드·주간 랭킹 | Merged / PR #174 → main `85ebfad` / In Review | build 24에 포함. 정확한 실기기 주간 제출 확인과 TYP-120 공통 Game Center 재발 수정은 열린 상태로 유지 |
| #180 PostHog 국가·기기 사용 환경 분석·재동의 | Verify / PR #182 / 소스 구현·focused 검증 완료 | 국가만 남기는 운영 변환과 고지 v2·진단 선택 보존·철회 시 전송 차단을 구현하고 iPhone/iPad·Release 검증을 통과했다. 사용 분석은 PostHog, Firebase는 Crashlytics 전용으로 유지한다. 정책 게시·App Store Privacy·서명된 앱 수신/OFF 네트워크·Crashlytics crash/dSYM 확인 전에는 Done 처리하지 않으며 현재 1.1의 국가 수집이 시작된 것으로 표현하지 않음 |
| TYP-114 커리큘럼 카드 셰브런 제거 | Merged / PR #173 → main `060775a54a76e60e8da04ef75a83bd7e49328346` / In Review | 공유 stage row의 장식만 제거하고 Spacer·별·RESUME·레슨 탭 동작을 유지. build 24 iPad에서 최종 화면 확인 전 Done 처리하지 않음 |
| TYP-112 iPad OS 키보드 가이드·게임 확장 | Merged / PR #171 → main `dc42e19128bf7fc54971a68f30b89e6e21b54cb5` / In Review | focused iPad 테스트 128/128와 최신 merged-tree 검증 PASS. build 24 실제 iPad·물리 키보드에서 가이드 ON/OFF, OS 입력 스트립과 게임 영역을 확인하기 전 Done 처리하지 않음 |
| TYP-111 플릭 프리뷰 표시 제거 | In Review 준비 / `codex/111-hide-flick-preview` | 표시 전용 overlay와 dead code 제거, full package·HancoTests 및 focused iPhone·iPad UI evidence 뒤 exact-head Claude review를 받고, 병합 전 maintainer 수동 승인을 대기 |
| TYP-106 플릭 팝업 키캡 겹침·앵커 이탈 | Merged / PR #169 → main `71f088a` / build 22 검증 | build 22 실기기에서 확인된 프리뷰 시각 품질 미달은 TYP-111 표시 제거로 대체하되 기존 방향 입력·p95·60fps·VoiceOver gate는 유지 |
| TYP-105 build 21 부화 결과 전환 경합 | Merged / PR #168 → main `c81df6d` | build 22 실제 iPhone에서 1→2·2→3·3→축하→Home과 mission 2 및 final 즉시/10초 종료·재실행을 확인 |
| TYP-101 인앱 천지인 플릭 방향 미리보기 | Merged / PR #165 → main `1a9715b`; 표시 계약은 TYP-111로 대체 | 프리뷰 실기기 gate는 TYP-111 표시 제거 결정으로 폐기하고, 기존 방향 매핑·입력·VoiceOver와 p95·60fps gate는 유지 |
| TYP-102 마지막 부화 3/3 완료 전환 | Merged / PR #167 → main `4e0b969` / build 21 target | 최종 RC build 21 iPhone에서 결과 닫기 → 성장 축하 1회 → 홈, 10초 대기와 즉시 종료 각각의 재실행이 모두 홈을 유지하는지 확인하기 전 Done 처리하지 않음 |
| TYP-43 iOS/iPadOS 1.1 출시 | Linear In Review / ASC `1.1 (24)` Ready for Distribution 직접 확인 | 출시 전 문서·트래커의 오래된 상태는 TYP-117에서 정리. TYP-120 Game Center 재발과 지역·계정·권리·IAP 등 미확인 gate를 별도로 추적 |
| TYP-103 Settings 키보드 카드 순서 | Merged / PR #166 → main `504df3e` / build 21 target | build 22 iPhone·iPad에서 입력 모드→내장 배열 순서와 OS 모드의 배열 비활성·dim을 재확인 |
| TYP-85 인앱 천지인 방향 플릭 | Merged / PR #161 → main `0a0182b` / iOS 1.1 승인 | build 19 iPhone·iPad 실기기 전체 매핑·롱프레스·취소·동시 입력·p95·60fps·VoiceOver 전에는 Done 처리하지 않음 |
| TYP-98 연습 발음 버튼 OS IME 터치 차단 | Merged / PR #163 → main `4a27d6b` | build 19 실기기 iPhone에서 덱 연습·커리큘럼 레슨의 발음·OS IME 재포커스와 내장 두벌식·한국어 10키를 smoke하고, iPad 기존 동작을 재확인하기 전 Done 처리하지 않음 |
| TYP-97 Random 5 입력 중 세로 이동 | Merged / PR #162 → main `1f669cf` | build 19 iPhone 내장 두벌식·한국어 10키·OS IME와 iPad 레이아웃을 실기기로 확인하고, CI·계정·권리·store gate가 열린 동안 Done 처리하지 않음 |
| TYP-95 OS 10키 겹받침 진행 보존 | Merged / PR #160 → main `2812559` | build 19 iPhone·iPad 실기기에서 연습·지원 게임의 OS 천지인 겹받침 진행을 확인하기 전 Done 처리하지 않음 |
| TYP-94 iPhone 게임 레이아웃 미확장 | Merged / PR #159 → main `586f00b` | 철회된 build 16 검증은 다음 후보를 대체하지 않는다. build 19 iPhone 실기기에서 게임·연습·레슨·온보딩의 OS 키보드 레이아웃과 TYP-92 포커스 복구를 확인하고 iPad 기존 레이아웃을 재확인하기 전 Done 처리하지 않음 |
| TYP-93 세션 설정 정보구조 | Merged / PR #158 → main `fe05bd2` | build 14 TestFlight의 iPhone 실기기 덱 플레이·연습 확인 전 Done 처리하지 않음 |
| TYP-92 iPhone 입력 보조 UI 회귀 | Merged / PR #157 → main `855bf27` | build 14 실제 iPhone·iPad에서 OS 입력 패널·물리 참조 배열·포커스·다음 키 강조를 확인하기 전 Done 처리하지 않음. 실수로 실행한 전체 `HancoTests` target pass는 evidence에서 제외 |
| TYP-90 다음 키 가이드 오식별 정정 | In Review / PR #156 → main `fdce1d3` | TYP-92에서 iPhone 포함 기존 다음 키 강조·토글을 복구하고 OS 입력 패널 및 물리 참조 배열을 기기별로 정정한 뒤 build 14 실기기 gate까지 In Review 유지 |
| TYP-89 Settings 정보구조·내장 배열 선택 시인성 | Merged / PR #155 → main `18d1521` | Settings 순서와 segmented 배열 선택은 TYP-90에서 보존. build 13 TestFlight iPhone·iPad 실기기 설정 화면 확인은 OPEN |
| TYP-88 OS 10키 받침 경계 중간 상태 | Merged / PR #154 → main `93eef46` | Class A/B와 정확한 동일-key 경계 순환 Class C, Unicode scalar별 dot 확장 Class D 및 negative matrix를 exact source SHA에서 검증 완료. DEBUG-only probe는 진단 자산으로 유지하지만 standalone 입력 요청은 종료됐으며, build 12 snapshot·dangling 관찰과 정확한 build 13 iPhone·iPad corpus·TYP-81 로그는 OPEN |
| TYP-83 OS 천지인 복합모음 committed 중간 상태 | Merged / PR #152 → main `0ed4777` | iPhone 15 Pro row-13 원문(`돼`·`과`·`웨`·`의`) 완료. iPad row-13, post-fix iPhone·iPad practice/lesson/Flow/Dictation, full HancoTests와 TestFlight·release gate는 OPEN |
| TYP-86 일본어 콘텐츠 현지화 fallback | Merged / PR #151 → main `f46336c` | 후속 TestFlight build의 ja/en/es/de/fr 덱 연습·레슨·게임 표본과 일본어 iPhone·iPad 덱·7개 게임/연습 화면 증빙, full HancoTests·계정·권리·store/release gate는 OPEN |
| TYP-78 iOS 1.1 스토어 미디어 | In Review / PR #149 / `codex/78-store-media` | build 11 소스 `e6d714d5`에서 일본어 iPad 13형 PNG 10장·iPhone App Preview 3편을 로컬 생성·검증했다. PR review 뒤 현지어 사람 검수와 App Store Connect 업로드·저장 후 재조회는 별도 `gate:store`로 유지 |
| TYP-77 OS 천지인 ASCII guard 오판 | Merged / PR #147 → main `e6d714d` | marked ASCII를 확정 입력원 경고에서 제외하는 defensive hardening 병합 완료. Practice 배너 0회, 5개 직접 입력 게임×5단어, 일본어 로마자 IME 체감, 정확한 후속 TestFlight build의 iPhone·iPad 증빙은 `gate:device`로 유지 |
| TYP-73 iOS/iPadOS OS 한국어 키보드 단어 전환 조합 잔존 | Merged / PR #145 → main `43d65b3` / build 11 source | 최신 `origin/main`으로 build 11을 만들고 Practice+5개 게임, iPhone·iPad × 두벌식·천지인 × 연속 10단어를 확인하며 천지인 `대형`·`쇼파`를 포함 |
| TYP-84 게임 OS IME 입력 chrome | Merged / PR #153 → main `2ef04ed` | TestFlight build 12의 iPhone·iPad 실기기 확인 전 Done 금지 |
| TYP-82 OS 한국어 키보드 단어 전환 flicker | Merged / PR #150 → main `b2079c3` | build 12 TestFlight의 iPhone·iPad × 두벌식·천지인 실기기 증빙과 full HancoTests는 OPEN |
| TYP-71 iOS 온보딩 개인정보 문구 | Merged / PR #140 → main `17fb367` | build 10 TestFlight에서 iPhone·iPad 온보딩(알림 권한 1회 → 두 버튼 안내) 실기기 smoke |
| #125 iPad 게임 재도전 마지막 단어 잔존 | Verify / PR #128 | 소스·자동 회귀 통합 후 iOS 26.5 simulator 접근성 runtime 장애와 분리해 iPad 실기기에서 재도전 countdown의 시각·VoiceOver 상태 확인 |
| TYP-68 게임 OS 키보드 전환 후 IME 입력 잔존 | Review / PR #139 (#138 대체) | PR review·병합 후 iPhone·iPad 실기기에서 OS 한국어 키보드(두벌식·천지인)로 5개 직접 입력 게임 연속 10단어 전환 확인 |
| #77 iOS 1.1 심사 제출 | Blocked | Account Holder가 Paid Apps 계약·은행·세금 정보를 완료한 뒤 나머지 제출 gate 진행 |
| #75 기존 iOS 1.1 (7)/(8) TestFlight | Verify | build 7·8·9를 RC로 사용하지 않고 최신 `main`의 build 10으로 대체 |
| #7 Game Center 전체 점검 | Verify | 실제 App Store Connect 계약과 인증·제출·리더보드를 실기기에서 확인 |
| #58 현지 20시 리마인더 | Verify | iOS 실제 기기에서 권한 동의 뒤 현지 20시 수신 확인 |
| #123 iPad 물리 키보드 영문 입력 안내 | Verify | iPad Bluetooth 1차 동작 확인 완료. 반복 영문 입력의 흔들림·색 강조 후 한국어 두벌식 전환 → 현재 문제 완료를 재확인 |
| #8 자동 발음 재생 검토 | Verify / Later | 기존 수동 발음과 차이·재생 시점·기본값을 사용자와 확정하기 전 구현하지 않음 |
| #122 iPad 완료 시 연습 카드 이동 | Review | PR review 뒤 iPad Split View·실기기에서 완료 전환과 Reduce Motion을 최종 확인 |

## 즉시 작업 순서

1. #181 / PR #183에서 모든 키보드 Game Center 참여, 피요컵/일반 Flow 분리와 TYP-120 등록 복구를 검증한다. 16개 보드×3개 입력 방식과 응답 지연·실패·로그인·foreground를 포함한다.
2. 최신 main 병합 → clean 구현 SHA의 focused 검증·evidence → 새 head Claude review → maintainer gate를 통과한다. 기존 분리 PR의 8483f7b 리뷰는 확장된 핫픽스에 재사용하지 않는다.
3. ASC 기본 보드 v4 전환·구형 Flow v3 3개/weekly v3 archive를 완료했다. Flow 초급 v5 단독 심사 제출(acb65714-82c5-477e-96ca-e39b320280c3)을 추적하고, Live 확인 후 앱 ID·설정·테스트를 함께 전환한다. 전환 검증 전에는 현재 v4를 유지한다. 상세 사실과 순서는 `release/GAME_CENTER_HOTFIX_20260910.md`를 따른다.
4. 승인·병합한 최신 main에서 1.1.1 (25)를 서명·업로드한다. 실제 iPhone/iPad Game Center 제출·조회, 전체 iOS 단위 회귀와 기존 출시 gate를 확인하고 App Review 결과를 확인한 단계까지만 완료 처리한다.

## 출시 완료 판단

- 소스·CI 성공은 TestFlight·App Review·App Store 또는 Google Play 출시 완료가 아니다.
- iOS 새 RC는 `workspace_doctor.py --strict --require-origin-main`을 통과한 최신
  `origin/main` tree에서 생성하고, build 7·8의 과거 검증을 재사용하지 않는다.
- Account Holder의 법적·세금·은행 선언, 콘텐츠 권리 승인과 실제 기기 확인은
  agent가 대신 완료 처리하지 않는다.
