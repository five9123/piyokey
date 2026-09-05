# PIYOKEY 프로젝트 현황

마지막 갱신: 2026-09-05 JST
기준 저장소: `five9123-maker/piyokey`
기준 `main`: `git fetch --prune origin && git rev-parse origin/main`으로 확인

이 문서는 현재 열린 작업과 출시 gate만 유지한다. 제품 계약은 `PRD.md`, 확정
결정은 `DECISIONS.md`, 작업 순서는 `ROADMAP.md`, 실행 상태는
[PIYOKEY Development Project](https://github.com/users/five9123-maker/projects/1)를
기준으로 한다. 완료 작업의 상세 증빙은 해당 Issue·PR과 Git 기록에 남긴다.

## 현재 기준선

| 영역 | 현재 상태 | 다음 gate |
|---|---|---|
| iOS 공개판 | `1.0.2 (6)` 공개 상태 | EU DSA 거래자 상태와 지역별 실제 판매 상태 확인 |
| iOS 1.1 | `main` `0a0182b`에 TYP-85까지 병합됐다. build 18은 PostHog 토큰·Firebase plist 미포함과 Search 카드/연습 목록 UI 결함으로 대체하며 다음 후보는 build 19다 | TYP-43에서 분석 설정과 두 UI 결함을 병합한 최신 clean `origin/main`으로만 build 19를 만든다. 동의 ON/OFF 네트워크, Crashlytics dSYM, iPhone·iPad 회귀와 기존 계정·권리·IAP·store gate 전에는 출시 완료로 표현하지 않음 |
| Android | 기존 Kotlin/Compose 포트는 참고용 동결. 현재 제품·유지보수·CI·Play 출시 범위에서 제외 | 재개하지 않음. 사용자가 별도 승인한 새 PRD·초기 설계가 생길 때만 신규 작업으로 시작 |
| 웹 Builder | 별도 [`hanco_web`](https://github.com/five9123-maker/hanco_web) 저장소의 schema-v2 Builder PR #6 병합·배포 검증 완료 | 모바일과 교차 편집 회귀 유지. 이 저장소의 `web/`은 analytics 계약 패키지이며 웹 앱 본체가 아님 |
| CI·병합 | GitHub Actions 비활성. `docs/WORKFLOW.md`의 기본 수동 fail-closed 정책에 따라 모든 PR이 exact-head Claude review, focused local evidence, 최신 `origin/main` merged-tree 검증과 maintainer 승인을 요구 | 비활성 CI는 성공으로 간주하지 않으며 full HancoTests와 외부 gate는 focused evidence로 닫지 않음 |

## 열린 작업

| Issue | Project 상태 | 다음 한 단계 |
|---|---|---|
| TYP-102 마지막 부화 3/3 완료 전환 | In Progress / Draft PR #167 / `codex/102-hatch-completion` | 소스·simulator 회귀와 exact-head review 뒤 In Review로 전환한다. build 20 iPhone에서 결과 닫기 → 성장 축하 1회 → 홈, 10초 대기와 즉시 종료 각각의 재실행이 모두 홈을 유지하는지 확인하기 전 Done 처리하지 않음 |
| TYP-43 iOS/iPadOS 1.1 출시 | In Progress / `codex/43-ios-1-1-release` | build 19의 Search 카드 1줄·고정 높이와 연습 목록 수평 overscroll 수정, PostHog/Firebase 실설정 배선을 source PR로 병합한다. 이후 clean `origin/main` archive에서 동의 ON/OFF·Crashlytics dSYM·실기기·계정·권리·IAP·store gate를 순서대로 닫음 |
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

1. TYP-102 source PR의 final hatch 3/3 완료 전환과 내구 저장을 검증·review한 뒤 병합하고, build 20 iPhone에서 성장 축하 1회·홈 도착·종료 후 재실행을 확인한다.
2. TYP-43 source PR에서 build 19의 Search 카드·연습 목록 수정과 Firebase config embed를 검증하고 exact-head review·maintainer 승인을 받는다.
3. 병합 뒤 clean `origin/main`에서만 PostHog token과 Firebase plist를 주입한 build 19 archive를 만든다.
4. build 19에서 동의 ON 이벤트 수신·동의 OFF 무전송·Crashlytics 테스트 크래시와 dSYM을 먼저 확인하고, TYP-85·94·95·97·98 및 기존 TYP-73·77·82·83·88·93 회귀를 iPhone·iPad 실기기로 확인한다.
5. #7·#58과 #77의 계정·권리·IAP·store gate를 처리한다.
6. Dependabot PR을 변경 범위별로 검토하고 성공한 check 없이 자동 병합하지 않음. #8은 Later 유지.

## 출시 완료 판단

- 소스·CI 성공은 TestFlight·App Review·App Store 또는 Google Play 출시 완료가 아니다.
- iOS 새 RC는 `workspace_doctor.py --strict --require-origin-main`을 통과한 최신
  `origin/main` tree에서 생성하고, build 7·8의 과거 검증을 재사용하지 않는다.
- Account Holder의 법적·세금·은행 선언, 콘텐츠 권리 승인과 실제 기기 확인은
  agent가 대신 완료 처리하지 않는다.
