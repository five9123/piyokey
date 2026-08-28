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
| #67 / PR #70 스페인어 UI·기존 작업 통합 | Blocked (병합 승인) | `codex/67-spanish-ui` / `/private/tmp/piyokey-issue67-spanish` | 기존 PR 8개를 통합하고 ja/en/es UI·학습 원본 보존 검증 완료. CI/리뷰 또는 이번 병합의 사용자 예외 승인 대기; main 미병합 |
| #68 / PR #69 미커밋 스토어·Pro 작업 보존 | Verify (Draft) | `codex/68-preserve-store-work` / `/private/tmp/piyokey-issue68-preserved` | 원본 dirty 파일 35개와 보존본의 바이트 일치 확인. PR #70에 통합 완료; main 병합 대기 |
| #65 / PR #66 한국어 UI 제거 | Verify (Draft) | `codex/65-remove-korean-ui` / `/private/tmp/piyokey-issue65-remove-korean-ui` | 한국어 UI 제거·기존 ko 설정 영어 전환을 PR #70의 ja/en/es 범위에 통합. 학습 콘텐츠 보존; main 병합 대기 |
| #63 / PR #64 사용자 덱 `.typedeck` 확장자 | Blocked | `codex/63-typedeck` / `/Users/jungminoh/Documents/hanco` | PR #70 통합·회귀 완료; CI·검토 또는 사용자 예외 승인 후 병합 |
| #58 / PR #62 현지 20시 리마인더 | Verify | 구현은 `main` 병합 완료; `/private/tmp/piyokey-issue-58` 보존 | 실제 기기에서 권한 동의·현지 20시 알림 수신 확인 |
| #10 → #46 → #17 / PR #18·#49·#50 iPad 스택 | Blocked (병합 승인) | 전용 iPad·stack worktree | 최신 main 충돌 해결·PR #70 통합·iOS 회귀 완료. 사용자 확인 실기기 증거는 유지; 원격 main 병합 승인 대기 |
| #9 / PR #14 게임 음성 힌트 | Blocked (병합 승인) | `codex/9-game-audio-hints` / `/private/tmp/piyokey-issue9-hints` | 최신 main 충돌 해결·PR #70 통합·힌트 예산 UI 검증 완료; 원격 main 병합 대기 |

플랫폼별 동시 `In Progress`는 하나를 원칙으로 하며, 공용 충돌 파일은 한 작업만 소유한다.

#65 단독 단계의 과거 검증 대상은 `a24ac1eff362cad3218b8a806c0f50078493e1b9`이며 증빙은 `release/evidence/a24ac1eff362cad3218b8a806c0f50078493e1b9.json`이다. Python 82개·iOS 관련 단위/UI 57개(동일 소스 빌드), clean commit 재검증 56개·Android 설정/덱 33개 및 Debug assemble/lint·preflight를 통과했다. 당시 APK에는 한국어 UI locale이 없고 iOS 번들은 ja/en을 제공했다. 현재 #67 통합 APK와 iOS 번들은 ja/en/es를 제공한다. `shared/`·기존 학습 문자열·음원·덱 schema는 기준 #64 대비 변경이 없다. 기존 스토어/Pro 미커밋 변경은 원래 worktree에 그대로 두고 #68에 별도 보존한 뒤 PR #70에 통합했다. 원격 main 병합·실기기 설치·스토어 배포는 하지 않았다.

## 열린 출시 gate

- #7: Game Center 계약 전체 점검.
- #12 / PR #15: Bluetooth·물리 키보드 iOS 실기기 검증은 2026-08-28 사용자 확인으로 완료. 소스 PR의 최신 main 충돌 해결·PR #70 통합·관련 입력 회귀는 완료. CI·검토 또는 사용자 예외 승인에 따른 원격 main 병합은 미완료.
- #19: Android 동일 signed AAB의 입력 지연, rollover, IME, 오디오, 알림, Files, Billing, Play Games, 60fps 통합 QA.
- #58: iOS·Android 실제 기기에서 온보딩 알림 권한 동의 뒤 현지 20시 수신 확인.
- iOS 1.1: `release/APP_STORE_QA.md`의 미완료 수동 gate.
- App Store 글로벌 배포: 175개 국가 또는 지역 선택 완료. 145개 지역은 처리 중이며 EU 29개 지역은 DSA 거래자 상태 입력 전까지 보류. 기본 언어 en-US 전환은 필수 영어 스크린샷 등록 전까지 차단.
- App Store 미디어: 한국어 UI 제거 전 ja/en/ko 촬영 테스트 3/3 통과. 현재 ja/en/es RC와 일치하지 않으므로 ko 스토어는 영어 UI, es-ES 스토어는 스페인어 UI로 다시 촬영해야 한다. `ja`, `en-US`, `ko`, `zh-Hans`, `zh-Hant`, `de-DE`, `fr-FR`, `es-ES`, `pt-BR`, `id` 10개 로케일의 100 PNG·10 MP4 제작 및 전체 재검토 완료(2026-08-28 09:16 JST). 지원 언어 안내 없이 실제 UI·현지어 카피·Pro 구매 안내를 유지했다. 사진·영상 contact sheet, 60개 자막, 체크섬과 총 7,920프레임 디코딩 재검증 통과. 영어 홈 CTA 말줄임은 실제 앱 UI의 후속 개선 항목으로 기록했다. **업로드는 Apple 로그인 만료로 차단**: Chrome 미디어 관리자 진입과 앱 내 브라우저 모두 로그인 화면이며, 기존 자산 삭제·신규 업로드·저장·심사 제출은 하지 않았다. 재로그인 뒤 반영을 재개한다. `release/store-assets/verification-20260828.json`과 `artifacts/store-localization/delivery/index.html` 참조. 현지어 사람 검수·최종 RC 일치·신규 로케일 필수 메타데이터·저장 후 재조회는 미완료. GitHub CLI 인증은 2026-08-28 확인했고 보존 작업 #68/PR #69 및 통합 작업 #67/PR #70을 Issue/Project에 반영했다.
- Android 1.1: `release/GOOGLE_PLAY_QA.md`의 운영자·Play Console·권리·서명 gate.

## 작업공간 현황

2026-08-27 재정비에서 병합 완료·clean worktree 9개와 로컬 branch 8개를 제거했다. 현재 #58, #9, #12, #46, #17, iPad #10, #65와 새 #67·#68 작업공간을 보존한다. 기본 `/Users/jungminoh/Documents/hanco`는 dirty `codex/63-typedeck`이며 원본 파일을 변경하지 않았다. 소유권은 main 병합 후 clean `main`과 `origin/main`이 같을 때만 해제한다. dirty worktree는 확인 없이 삭제·이동하지 않는다.

## 상태 갱신 체크

1. `git fetch --prune origin` 후 main SHA를 확인한다.
2. GitHub Project의 Status·Priority·Area·Target·Work Type을 확인한다.
3. 병합된 PR의 Issue가 닫혔는지 확인한다.
4. 검증 증빙의 commit SHA가 PR HEAD와 일치하는지 확인한다.
5. 기기·스토어 gate는 실제 증거가 있을 때만 완료로 바꾼다.

## 기존 작업 병합 점검 — 2026-08-28

- #63은 스토어 미디어·지원 URL·별도 Pro 변경과 분리했다. 검증 대상 `d788a78659831188542a31208bcbe1af540129db`, 증빙 `release/evidence/d788a78659831188542a31208bcbe1af540129db.json`: Python 78개, SwiftPM 42개, iOS 문서 흐름 8개, Android 관련 단위 테스트·앱 Kotlin 컴파일, repository preflight·fixture 재생성 통과.
- 저장소 Actions 권한 조회 결과 `enabled=false`. 기존 CI에는 결제 실패/사용 한도 오류도 기록되어 있다. 비활성화 상태의 재실행은 CI 통과 증거가 아니며, 설정 변경이나 CI 우회 병합을 하지 않았다. 활성화·계정 상태 확인 후 최신 PR HEAD에서 검증한다.
- 사용자가 대화에서 iPad·물리 키보드 실기기 검증 완료를 확인했다(2026-08-28). #10/#12/#46/#17의 해당 수동 검증은 사용자 확인 완료로 반영하며, 에이전트가 새로 수행한 테스트로 기록하지 않는다. #18 → #49 → #50 및 #15는 최신 main 충돌 해결·PR #70 통합·로컬 회귀를 마쳤으며, CI/리뷰 또는 사용자 예외 승인 뒤 병합한다. 이 확인을 Android #19, 리마인더 #58의 현지 20시 수신, 결제·스토어·권리 gate 완료로 확대하지 않는다. PR #14의 최신 main 충돌을 해결했고 기존 checkpoint의 오래된 StoreKit·운영 문서는 최신 main 기준을 유지했다.

## #67 통합 검증

검증 소스: `0d273c341c65b073a8ae5d3ec8b74617e59a82ee`. 증빙: `release/evidence/0d273c341c65b073a8ae5d3ec8b74617e59a82ee.json`. 이후 커밋은 현황·증빙만 변경하며 이전 앱 소스의 테스트 재사용 범위도 증빙에 명시했다.

- 기존 PR #14·#15·#18·#49·#50·#64·#66·#69의 원격 HEAD가 모두 PR #70 통합 HEAD의 ancestor임을 확인했다. 원격 `main`은 `7dc2d5562f09c82729fb618f1e2b9feac8a156e6` 그대로다.
- Python 88개·repository preflight, SwiftPM 43개, Android 관련 단위 66개·Debug assemble/lint·앱/연습 instrumentation 소스 컴파일을 통과했다. Android 기기 instrumentation 실행은 하지 않았다.
- iPhone 통합 앱 단위 358개와 관련 UI 6개를 통과했다. 마지막 앱 변경 `b5ebf09`에서 단위 358개·언어/세션 UI 2개를 재검증했다. 이후 변경은 UI 테스트의 iPad 스크롤 범위 판정뿐이며 iPhone·iPad 스페인어 전환/저장 재검증을 통과했다. iPad 회전·세션 보존·접근성 큰 글자 UI 3개도 통과했다.
- iOS 번들·Android APK에 ja/en/es 리소스가 포함됨을 확인했다. `shared/`·Swift 공용 엔진·Android DeckKit은 #66 대비 변경이 없다. 스페인어 UI 학습 뜻·읽기·덱 편집은 기존 영어를 사용한다.
- Actions `enabled=false`이며 CI/리뷰 규칙·보호 설정을 변경하지 않았다. PR #70은 Draft로 유지하며 이번 요청의 예외 병합 승인을 기다린다. 스토어/실기기/결제/권리 gate는 별도로 남아 있다.
