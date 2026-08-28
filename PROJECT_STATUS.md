# PIYOKEY 프로젝트 현황

마지막 갱신: 2026-08-28 JST
기준 저장소: `five9123-maker/piyokey`
기준 `main`: `git fetch --prune origin && git rev-parse origin/main`으로 확인
최근 운영 기준선: 프로젝트 재정비 PR #60·#61 병합

이 문서는 현재 상태의 단일 현황판이다. 제품 계약은 `PRD.md`, 확정 결정은 `DECISIONS.md`, 작업 순서는 `ROADMAP.md`를 따른다. 상태가 바뀌면 과거 문장을 덧붙이지 말고 해당 표를 현재 사실로 교체한다.

## 출시 기준선

| 트랙 | 소스 상태 | 공개·배포 상태 | 다음 gate |
|---|---|---|---|
| iOS 공개판 | `1.0.2 (6)` | 2026-08-18 공개 확인; 2026-08-28 글로벌 availability 처리 시작 | EU DSA 거래자 상태와 지역별 실제 판매 상태 확인 |
| iOS 1.1 | `1.1 (7)` 소스 기준선 | 글로벌 메타데이터·typee pro IAP 준비, 10개 로케일 사진 100장·영상 10개 로컬 제작/기술검증 완료, 미제출 | 최종 빌드 일치·현지어 검수·스토어 미디어 업로드·신규 로케일 필수 메타데이터·IAP 심사 스크린샷, Paid Apps Agreement, Sandbox 결제/복원/환불, Files/iCloud/AirDrop, 1,000항목 |
| Android 1.1 | `1.1.0 (8)` 소스 후보 | Play 미배포 | Play Console·서명·권리·Billing/Play Games와 Issue #19 동일 signed AAB 실기기 통합 QA |

소스 완료는 스토어 제출 완료가 아니다. 외부 gate가 남아 있으면 `Blocked` 또는 `Verify`로 유지한다.

## 활성 작업

| Issue/PR | 상태 | 소유 branch/worktree | 다음 한 단계 |
|---|---|---|---|
| #63 / PR #64 사용자 덱 `.typedeck` 확장자 | Blocked | `codex/63-typedeck` / `/Users/jungminoh/Documents/hanco` | 로컬 회귀 통과·Draft PR 생성 완료; GitHub Actions 활성화 및 CI·검토 승인 후 병합 |
| #58 / PR #62 현지 20시 리마인더 | Verify | 구현은 `main` 병합 완료; `/private/tmp/piyokey-issue-58` 보존 | 실제 기기에서 권한 동의·현지 20시 알림 수신 확인 |
| #10 → #46 → #17 iPad 스택 | Blocked (CI·통합) | 전용 iPad·stack worktree | 2026-08-28 사용자 실기기 검증 완료 확인; #18 → #49 → #50 순서로 main 통합·회귀·병합 조건 확인 |

플랫폼별 동시 `In Progress`는 하나를 원칙으로 하며, 공용 충돌 파일은 한 작업만 소유한다.

## 열린 출시 gate

- #7: Game Center 계약 전체 점검.
- #12 / PR #15: Bluetooth·물리 키보드 iOS 실기기 검증은 2026-08-28 사용자 확인으로 완료. 소스 PR의 main 충돌 해결·통합 회귀·CI·병합은 별도 미완료.
- #19: Android 동일 signed AAB의 입력 지연, rollover, IME, 오디오, 알림, Files, Billing, Play Games, 60fps 통합 QA.
- #58: iOS·Android 실제 기기에서 온보딩 알림 권한 동의 뒤 현지 20시 수신 확인.
- iOS 1.1: `release/APP_STORE_QA.md`의 미완료 수동 gate.
- App Store 글로벌 배포: 175개 국가 또는 지역 선택 완료. 145개 지역은 처리 중이며 EU 29개 지역은 DSA 거래자 상태 입력 전까지 보류. 기본 언어 en-US 전환은 필수 영어 스크린샷 등록 전까지 차단.
- App Store 미디어: ja/en/ko 최신 UI 촬영 테스트 3/3 통과. `ja`, `en-US`, `ko`, `zh-Hans`, `zh-Hant`, `de-DE`, `fr-FR`, `es-ES`, `pt-BR`, `id` 10개 로케일의 100 PNG·10 MP4 제작 및 전체 재검토 완료(2026-08-28 09:16 JST). 지원 언어 안내 없이 실제 UI·현지어 카피·Pro 구매 안내를 유지했다. 사진·영상 contact sheet, 60개 자막, 체크섬과 총 7,920프레임 디코딩 재검증 통과. 영어 홈 CTA 말줄임은 실제 앱 UI의 후속 개선 항목으로 기록했다. **업로드는 Apple 로그인 만료로 차단**: Chrome 미디어 관리자 진입과 앱 내 브라우저 모두 로그인 화면이며, 기존 자산 삭제·신규 업로드·저장·심사 제출은 하지 않았다. 재로그인 뒤 반영을 재개한다. `release/store-assets/verification-20260828.json`과 `artifacts/store-localization/delivery/index.html` 참조. 현지어 사람 검수·최종 RC 일치·신규 로케일 필수 메타데이터·저장 후 재조회는 미완료. 현재 GitHub CLI 인증 미확인으로 Issue/Project 반영은 별도 필요.
- Android 1.1: `release/GOOGLE_PLAY_QA.md`의 운영자·Play Console·권리·서명 gate.

## 작업공간 현황

2026-08-27 재정비에서 병합 완료·clean worktree 9개와 로컬 branch 8개를 제거했다. 현재 보존 대상은 #58, #12, #46, #17과 iPad #10 작업공간이다. PR #44의 clean #43 worktree는 해당 task 종료 뒤 정리한다. 기본 worktree는 #59 후속 병합 뒤 clean `main`에서 소유권을 해제한다. dirty worktree는 확인 없이 삭제·이동하지 않는다.

## 상태 갱신 체크

1. `git fetch --prune origin` 후 main SHA를 확인한다.
2. GitHub Project의 Status·Priority·Area·Target·Work Type을 확인한다.
3. 병합된 PR의 Issue가 닫혔는지 확인한다.
4. 검증 증빙의 commit SHA가 PR HEAD와 일치하는지 확인한다.
5. 기기·스토어 gate는 실제 증거가 있을 때만 완료로 바꾼다.

## 기존 작업 병합 점검 — 2026-08-28

- #63은 스토어 미디어·지원 URL·별도 Pro 변경과 분리했다. 검증 대상 `d788a78659831188542a31208bcbe1af540129db`, 증빙 `release/evidence/d788a78659831188542a31208bcbe1af540129db.json`: Python 78개, SwiftPM 42개, iOS 문서 흐름 8개, Android 관련 단위 테스트·앱 Kotlin 컴파일, repository preflight·fixture 재생성 통과.
- 저장소 Actions 권한 조회 결과 `enabled=false`. 기존 CI에는 결제 실패/사용 한도 오류도 기록되어 있다. 비활성화 상태의 재실행은 CI 통과 증거가 아니며, 설정 변경이나 CI 우회 병합을 하지 않았다. 활성화·계정 상태 확인 후 최신 PR HEAD에서 검증한다.
- 사용자가 대화에서 iPad·물리 키보드 실기기 검증 완료를 확인했다(2026-08-28). #10/#12/#46/#17의 해당 수동 검증은 사용자 확인 완료로 반영하며, 에이전트가 새로 수행한 테스트로 기록하지 않는다. #18 → #49 → #50 및 #15는 main 통합·회귀·CI 조건을 확인한 뒤 병합한다. 이 확인을 Android #19, 리마인더 #58의 현지 20시 수신, 결제·스토어·권리 gate 완료로 확대하지 않는다. Later #14는 main과 충돌하며 기존 로컬 ahead 커밋과 worktree는 보존한다.
