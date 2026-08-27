# PIYOKEY 프로젝트 현황

마지막 갱신: 2026-08-27 JST
기준 저장소: `five9123-maker/piyokey`
기준 `main`: `9ff9cd8` — 개인정보 우선 크로스플랫폼 애널리틱스 PR #44 병합

이 문서는 현재 상태의 단일 현황판이다. 제품 계약은 `PRD.md`, 확정 결정은 `DECISIONS.md`, 작업 순서는 `ROADMAP.md`를 따른다. 상태가 바뀌면 과거 문장을 덧붙이지 말고 해당 표를 현재 사실로 교체한다.

## 출시 기준선

| 트랙 | 소스 상태 | 공개·배포 상태 | 다음 gate |
|---|---|---|---|
| iOS 공개판 | `1.0.2 (6)` | 2026-08-18 공개 확인 | 운영 유지 |
| iOS 1.1 | `1.1 (7)` 소스 기준선 | 미제출 | StoreKit 상품·Sandbox 결제/복원/환불, Files/iCloud/AirDrop, 1,000항목, 제출 자산 |
| Android 1.1 | `1.1.0 (8)` 소스 후보 | Play 미배포 | Play Console·서명·권리·Billing/Play Games와 Issue #19 동일 signed AAB 실기기 통합 QA |

소스 완료는 스토어 제출 완료가 아니다. 외부 gate가 남아 있으면 `Blocked` 또는 `Verify`로 유지한다.

## 활성 작업

| Issue/PR | 상태 | 소유 branch/worktree | 다음 한 단계 |
|---|---|---|---|
| #59 프로젝트 재정비 | In Progress | `codex/59-project-reorganization` / 기본 worktree | 문서·진단·검증 증빙 체계 PR |
| #58 현지 20시 리마인더 | In Progress | `codex/58-onboarding-local-reminder` / `/private/tmp/piyokey-issue-58` | 최신 main 반영 후 관련 회귀와 PR |
| #10 → #46 → #17 iPad 스택 | Draft/Verify | 전용 iPad·stack worktree | #18 → #49 → #50 순서로 기준 main 반영 및 검증 |

플랫폼별 동시 `In Progress`는 하나를 원칙으로 하며, 공용 충돌 파일은 한 작업만 소유한다.

## 열린 출시 gate

- #7: Game Center 계약 전체 점검.
- #12 / PR #15: Bluetooth·물리 키보드 iOS 호환성.
- #19: Android 동일 signed AAB의 입력 지연, rollover, IME, 오디오, 알림, Files, Billing, Play Games, 60fps 통합 QA.
- iOS 1.1: `release/APP_STORE_QA.md`의 미완료 수동 gate.
- Android 1.1: `release/GOOGLE_PLAY_QA.md`의 운영자·Play Console·권리·서명 gate.

## 작업공간 현황

2026-08-27 재정비에서 병합 완료·clean worktree 9개와 로컬 branch 8개를 제거했다. 현재 보존 대상은 #59, #58, #12, #46, #17과 iPad #10 작업공간이다. PR #44의 clean #43 worktree는 해당 task 종료 뒤 정리한다. dirty worktree는 확인 없이 삭제·이동하지 않는다.

## 상태 갱신 체크

1. `git fetch --prune origin` 후 main SHA를 확인한다.
2. GitHub Project의 Status·Priority·Area·Target·Work Type을 확인한다.
3. 병합된 PR의 Issue가 닫혔는지 확인한다.
4. 검증 증빙의 commit SHA가 PR HEAD와 일치하는지 확인한다.
5. 기기·스토어 gate는 실제 증거가 있을 때만 완료로 바꾼다.
