# PIYOKEY 프로젝트 현황

마지막 갱신: 2026-08-31 JST
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
| iOS 1.1 | 최신 `main`에 iPad·5언어·schema v2·Pro 덱 언어 retag까지 통합. TestFlight `1.1 (8)`도 Pro 덱 언어 retag 이전 소스이며 프로젝트 후보는 `1.1 (10)` (build 9는 TYP-71 미포함으로 폐기) | build 10 archive·TestFlight 처리 후 정확한 후보로 실기기·IAP·미디어·현지어·출시 gate 검증 |
| Android | 기존 Kotlin/Compose 포트는 참고용 동결. 현재 제품·유지보수·CI·Play 출시 범위에서 제외 | 재개하지 않음. 사용자가 별도 승인한 새 PRD·초기 설계가 생길 때만 신규 작업으로 시작 |
| 웹 Builder | 별도 [`hanco_web`](https://github.com/five9123-maker/hanco_web) 저장소의 schema-v2 Builder PR #6 병합·배포 검증 완료 | 모바일과 교차 편집 회귀 유지. 이 저장소의 `web/`은 analytics 계약 패키지이며 웹 앱 본체가 아님 |
| CI·병합 | GitHub Actions 활성. 경로별 Python·Swift·iOS workflow와 주간/수동 iOS 회귀를 분리. Android는 CI·Dependabot 범위에서 제외. GitHub-owned action만 허용하고 action SHA pinning·Dependabot alerts/security updates를 적용. private 저장소의 현재 요금제에서는 branch protection/ruleset 사용 불가 | 적용 경로의 모든 표시 PR check 성공 후에만 squash merge하고 정기 회귀 실패 시 출시 gate를 닫는 수동 fail-closed gate 유지 |

## 열린 작업

| Issue | Project 상태 | 다음 한 단계 |
|---|---|---|
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

1. TYP-71 PR #140 병합 완료(`17fb367`). 이 기준선에서 build 10 archive·TestFlight 업로드.
2. iOS 1.1 build 10의 정확한 TestFlight 실기기 QA와 #77 외부 gate 진행.
3. #7·#58의 iOS 실기기 gate 처리.
4. Dependabot PR을 변경 범위별로 검토하고 성공한 check 없이 자동 병합하지 않음. #8은 Later 유지.

## 출시 완료 판단

- 소스·CI 성공은 TestFlight·App Review·App Store 또는 Google Play 출시 완료가 아니다.
- iOS 새 RC는 `workspace_doctor.py --strict --require-origin-main`을 통과한 최신
  `origin/main` tree에서 생성하고, build 7·8의 과거 검증을 재사용하지 않는다.
- Account Holder의 법적·세금·은행 선언, 콘텐츠 권리 승인과 실제 기기 확인은
  agent가 대신 완료 처리하지 않는다.
