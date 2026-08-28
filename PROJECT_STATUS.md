# PIYOKEY 프로젝트 현황

마지막 갱신: 2026-08-29 JST
기준 저장소: `five9123-maker/piyokey`
기준 `main`: `git fetch --prune origin && git rev-parse origin/main`으로 확인
최근 통합 기준선: PR #74, `9af01ef96e06472eb3d842880649b5e697c5877e` — 4단계 온보딩·첫 홈 추천·ja/en/es/de/fr

이 문서는 현재 상태의 단일 현황판이다. 제품 계약은 `PRD.md`, 확정 결정은 `DECISIONS.md`, 작업 순서는 `ROADMAP.md`를 따른다. 상태가 바뀌면 과거 문장을 덧붙이지 말고 해당 표를 현재 사실로 교체한다.

## 출시 기준선

| 트랙 | 소스 상태 | 공개·배포 상태 | 다음 gate |
|---|---|---|---|
| iOS 공개판 | `1.0.2 (6)` | 2026-08-18 공개 확인; 2026-08-28 글로벌 availability 처리 시작 | EU DSA 거래자 상태와 지역별 실제 판매 상태 확인 |
| iOS 1.1 | PR #74 main `9af01ef`, `1.1 (7)` | 서명 archive·Distribution IPA 검증, TestFlight 업로드·Apple 처리 완료, Ready to Submit·1.1 버전에 build 7 연결 저장/재조회 완료; 승인 후 자동·전 사용자 즉시 출시 확정, App Review 미제출 | 최종 빌드 일치·현지어 검수·스토어 미디어 업로드·신규 로케일 필수 메타데이터·IAP 심사 스크린샷, Paid Apps Agreement, Sandbox 결제/복원/환불, Files/iCloud/AirDrop, 1,000항목 |
| Android 1.1 | `1.1.0 (8)` 소스 후보 | Play 미배포 | Play Console·서명·권리·Billing/Play Games와 Issue #19 동일 signed AAB 실기기 통합 QA |

소스 완료는 스토어 제출 완료가 아니다. 외부 gate가 남아 있으면 `Blocked` 또는 `Verify`로 유지한다.

## 활성 작업

| Issue/PR | 상태 | 소유 branch/worktree | 다음 한 단계 |
|---|---|---|---|
| #77 iOS 1.1 심사 제출 | Blocked | codex/77-ios11-submission / /private/tmp/piyokey-issue77-submission | Account Holder 은행·한국/미국 세금 정보 완료 및 나머지 제출 gate 검증 |
| #75 / PR #76 iOS 1.1 (7) 배포 | Verify | main 기록 반영 완료 | TestFlight·App Store 빌드 연결 완료, 후속 심사 제출은 #77 |
| #58 / PR #62 현지 20시 리마인더 | Verify | 구현은 `main` 병합 완료; `/private/tmp/piyokey-issue-58` 보존 | 실제 기기에서 권한 동의·현지 20시 알림 수신 확인 |

## #73 5언어·온보딩 main 통합 완료

- PR #74를 사용자 main 병합·배포 요청과 로컬 검증에 근거해 2026-08-29 JST squash merge했다. main SHA는 `9af01ef96e06472eb3d842880649b5e697c5877e`; Issue #73은 Closed, Project는 Done이다. 사용자는 CI·별도 리뷰 없는 예외 병합도 추가로 명시 승인했다. Actions·보호 설정을 변경하지 않았으며 App Store 심사 gate는 면제하지 않는다. 배포는 #75에서 추적한다.
- 사용자 진행 승인으로 `dcc32bf` 로컬 5언어 후보를 #73/PR #74에 통합했다. 원본 언어/리텐션 clone은 보존했다. 새 온보딩은 관심사 뒤 4단계·2열 예시 카드이며 돌아가기 버튼이 없다. 첫 홈은 추천, 실제 학습 뒤 이어하기다.
- ja/en/es/de/fr 모두 iOS 1,124개 키·Android 517개 리소스 누락 0개, 공식 es/de/fr 뜻 627개·덱 이름 41개·태그 42개를 포함한다. 공용 schema/reader/writer·카탈로그 호환 계약을 함께 반영했다.
- 검증 소스 `00fb528782bcceb84875778161fca0f4660fb3c8`, 증빙 `release/evidence/00fb528782bcceb84875778161fca0f4660fb3c8.json`. Python 96개, SwiftPM 45개, Android 관련 단위 63개·Debug 빌드/lint·UI 14개, iOS 앱 단위 366개·관련 UI 5개 통과. 마지막 변경은 배경·결과 마스코트의 성장 전 상태를 함께 검사하도록 테스트만 조정했으며, 동일 앱 소스 `a585771`의 단위·첫 홈 3개·Android 결과 재사용 범위를 증빙에 기록했다.
- 부화 성장 실패는 시뮬레이터의 촬영용 성장 값이 앱 컨테이너 밖에 남아 새 설치 테스트에 유입된 문제였다. DEBUG 초기값과 결과 화면/투어 이후 테스트 범위를 명확히 했다. 별도로 화면 종료 시 미시작 세션이 빈 체크포인트를 다시 저장하던 문제를 수정해 부화→첫 홈→재실행에서 추천을 유지한다. 명시적 재도전의 초기화 저장은 유지한다.
- Android 공유 에뮬레이터 재실행은 테스트 중 앱 삭제로 중단됐다. 기존 에뮬레이터/다른 작업을 중단하지 않고 Issue #73 전용 API35 에뮬레이터에서 UI 14개를 모두 재검증했다.
- GitHub Actions/별도 리뷰는 이번 사용자 승인 예외로 수동 병합했다. 원어민·전체 화면/실기기·새 카탈로그 namespace·최종 RC 촬영·스토어 gate는 별도다. 과거 로컬 환경의 앱 빌드/기기 접근 실패 기록은 역사적 증빙이며 현재 통합 검증과 구분한다.
- 상세: `docs/LANGUAGE_COVERAGE.md`, `docs/LANGUAGE_REVIEW.md`, `docs/LANGUAGE_EXPANSION_CHECKLIST.md`.

## 최근 소스 통합

아래 완료 상태는 이 문서가 포함된 PR #70의 원격 `main` 반영을 기준으로 한다. 2026-08-28 사용자가 이번 작업에 한해 로컬 검증 기반 예외 병합을 명시적으로 승인했다. GitHub Actions 활성화·CI 통과·별도 리뷰 승인을 새로 받은 것으로 기록하지 않는다.

| Issue/PR | 소스 상태 | 검증·보존 범위 |
|---|---|---|
| #67 / PR #70 스페인어 UI·기존 작업 통합 | main 반영 | ja/en/es UI, 지역 언어 인식·전환·저장, iOS·Android 회귀 |
| #68 / PR #69 스토어·Pro 작업 보존 | main 반영 | 원본 dirty 파일 35개와 보존본 바이트 일치; 스토어 업로드는 별도 gate |
| #65 / PR #66 한국어 UI 제거 | main 반영 | 기존 ko 설정 영어 전환·학습 원본 보존 |
| #63 / PR #64 `.typedeck` 확장자 | main 반영 | 공용·iOS·Android 문서 흐름 통합 |
| #10·#46·#17 / PR #18·#49·#50 iPad·입력 스택 | main 반영 | 프로젝트 충돌 해결·회전·큰 글자·세션 입력 회귀; 이전 사용자 실기기 확인 보존 |
| #9 / PR #14 게임 음성 힌트 | main 반영 | 공통 힌트 예산 UI 검증 |
| #12 / PR #15 물리 키보드 | main 반영 | 이전 사용자 실기기 검증 확인·입력 회귀 |

플랫폼별 동시 `In Progress`는 하나를 원칙으로 하며, 공용 충돌 파일은 한 작업만 소유한다.

#65 단독 단계의 과거 검증 대상은 `a24ac1eff362cad3218b8a806c0f50078493e1b9`이며 증빙은 `release/evidence/a24ac1eff362cad3218b8a806c0f50078493e1b9.json`이다. Python 82개·iOS 관련 단위/UI 57개(동일 소스 빌드), clean commit 재검증 56개·Android 설정/덱 33개 및 Debug assemble/lint·preflight를 통과했다. 당시 APK에는 한국어 UI locale이 없고 iOS 번들은 ja/en을 제공했다. PR #67 당시 APK와 iOS 번들은 ja/en/es였으며, 현재 PR #74 main은 ja/en/es/de/fr를 제공한다. `shared/`·기존 학습 문자열·음원·덱 schema는 기준 #64 대비 변경이 없다. 기존 스토어/Pro 미커밋 변경은 원래 worktree에 그대로 두고 #68에 별도 보존한 뒤 PR #70에 통합했다. PR #70으로 원격 main에 반영했으며 이번 작업에서 실기기 설치·스토어 배포는 하지 않았다.

## 열린 출시 gate

- #7: Game Center 계약 전체 점검.
- #19: Android 동일 signed AAB의 입력 지연, rollover, IME, 오디오, 알림, Files, Billing, Play Games, 60fps 통합 QA.
- #58: iOS·Android 실제 기기에서 온보딩 알림 권한 동의 뒤 현지 20시 수신 확인.
- iOS 1.1: `release/APP_STORE_QA.md`의 미완료 수동 gate. Free Apps Agreement Active / Paid Apps Agreement Pending User Info로 갱신된 상태를 확인했다. Account Holder의 은행 계좌 및 한국 세금 양식·미국 Tax Questionnaire 입력이 남아 있다. 사용자는 승인 후 자동 출시·단계 배포 없이 전 사용자 즉시 공개를 확정했으며 콘솔 설정과 일치한다. 심사 확인 창은 Continue하지 않고 취소했으며 제출 완료가 아니다. `release/TESTFLIGHT_1_1_SMOKE.md`는 정확한 1.1(7) 실기기 검증 전용이며 아직 미실행이다.
- App Store 글로벌 배포: 175개 국가 또는 지역 선택 완료. 145개 지역은 처리 중이며 EU 29개 지역은 DSA 거래자 상태 입력 전까지 보류. 기본 언어 en-US 전환은 필수 영어 스크린샷 등록 전까지 차단.
- App Store 미디어: 한국어 UI 제거 전 ja/en/ko 촬영 테스트 3/3 통과. 현재 main ja/en/es/de/fr에 일치하지 않으므로 ko 스토어는 영어 UI, es/de/fr 스토어는 각각 해당 UI로 다시 촬영해야 한다. `ja`, `en-US`, `ko`, `zh-Hans`, `zh-Hant`, `de-DE`, `fr-FR`, `es-ES`, `pt-BR`, `id` 10개 로케일의 100 PNG·10 MP4 제작 및 전체 재검토 완료(2026-08-28 09:16 JST). 지원 언어 안내 없이 실제 UI·현지어 카피·Pro 구매 안내를 유지했다. 사진·영상 contact sheet, 60개 자막, 체크섬과 총 7,920프레임 디코딩 재검증 통과. 영어 홈 CTA 말줄임은 실제 앱 UI의 후속 개선 항목으로 기록했다. **2026-08-29 Chrome Apple 로그인 복구 확인**. 기존 일본어 8장·영상 1개가 남아 있으며 이번 실행에서는 스토어 미디어 삭제·업로드·저장·심사 제출을 하지 않았다. 최종 RC 일치와 필수 메타데이터·iPad 미디어를 확인한 뒤 미디어 반영을 진행한다. `release/store-assets/verification-20260828.json`과 `artifacts/store-localization/delivery/index.html` 참조. 현지어 사람 검수·최종 RC 일치·신규 로케일 필수 메타데이터·저장 후 재조회는 미완료. GitHub CLI 인증은 2026-08-28 확인했고 보존 작업 #68/PR #69 및 통합 작업 #67/PR #70을 Issue/Project에 반영했다.
- Android 1.1: `release/GOOGLE_PLAY_QA.md`의 운영자·Play Console·권리·서명 gate.

## 작업공간 현황

`outputs/local-language-expansion`의 `codex/local-es-de-fr` (`dcc32bf`)를 사용자 진행 승인에 따라 #73 작업 후보에 통합했다. 원본 언어 clone과 후속 `outputs/local-retention-improvements`, 기본 dirty 작업공간은 수정하지 않았다. 로컬 후보의 과거 독립 작업 예외는 해당 시점 기록이며 현재 통합 작업은 Issue #73 소유권·PR #74로 관리한다.

2026-08-27 재정비에서 병합 완료·clean worktree 9개와 로컬 branch 8개를 제거했다. 현재 #58, #9, #12, #46, #17, iPad #10, #65와 새 #67·#68 작업공간을 보존한다. 기본 `/Users/jungminoh/Documents/hanco`는 dirty `codex/63-typedeck`이며 원본 파일을 변경하지 않았다. 소유권은 main 병합 후 clean `main`과 `origin/main`이 같을 때만 해제한다. dirty worktree는 확인 없이 삭제·이동하지 않는다.

## 상태 갱신 체크

1. `git fetch --prune origin` 후 main SHA를 확인한다.
2. GitHub Project의 Status·Priority·Area·Target·Work Type을 확인한다.
3. 병합된 PR의 Issue가 닫혔는지 확인한다.
4. 검증 증빙의 commit SHA가 PR HEAD와 일치하는지 확인한다.
5. 기기·스토어 gate는 실제 증거가 있을 때만 완료로 바꾼다.

## 기존 작업 병합 점검 — 2026-08-28

- #63은 스토어 미디어·지원 URL·별도 Pro 변경과 분리했다. 검증 대상 `d788a78659831188542a31208bcbe1af540129db`, 증빙 `release/evidence/d788a78659831188542a31208bcbe1af540129db.json`: Python 78개, SwiftPM 42개, iOS 문서 흐름 8개, Android 관련 단위 테스트·앱 Kotlin 컴파일, repository preflight·fixture 재생성 통과.
- 저장소 Actions 권한 조회 결과 `enabled=false`. 기존 CI에는 결제 실패/사용 한도 오류도 기록되어 있다. 비활성화 상태의 재실행은 CI 통과 증거가 아니다. 사용자가 2026-08-28 이번 통합의 예외 병합을 승인했으며, 설정·보호 규칙은 변경하지 않았다. 이후 작업의 CI 요건은 그대로 유지한다.
- 사용자가 대화에서 iPad·물리 키보드 실기기 검증 완료를 확인했다(2026-08-28). #10/#12/#46/#17의 해당 수동 검증은 사용자 확인 완료로 반영하며, 에이전트가 새로 수행한 테스트로 기록하지 않는다. #18 → #49 → #50 및 #15는 최신 main 충돌 해결·로컬 회귀를 마치고 사용자 예외 승인에 따라 PR #70으로 main에 반영했다. 이 확인을 Android #19, 리마인더 #58의 현지 20시 수신, 결제·스토어·권리 gate 완료로 확대하지 않는다. PR #14의 최신 main 충돌을 해결했고 기존 checkpoint의 오래된 StoreKit·운영 문서는 최신 main 기준을 유지했다.

## #67 통합 검증

검증 소스: `0d273c341c65b073a8ae5d3ec8b74617e59a82ee`. 증빙: `release/evidence/0d273c341c65b073a8ae5d3ec8b74617e59a82ee.json`. 이후 커밋은 현황·증빙만 변경하며 이전 앱 소스의 테스트 재사용 범위도 증빙에 명시했다.

- 기존 PR #14·#15·#18·#49·#50·#64·#66·#69의 원격 HEAD가 모두 PR #70 통합 HEAD의 ancestor임을 확인했다. 기준 `main` `7dc2d5562f09c82729fb618f1e2b9feac8a156e6` 위에 검증된 통합 tree를 squash merge로 반영한다. 저장소는 merge commit을 허용하지 않으므로 기존 커밋은 원래 브랜치에 보존하고 선행 PR은 #70으로 통합 완료 처리한다. 최종 merge SHA는 PR #70 및 `git rev-parse origin/main`에서 확인한다.
- Python 88개·repository preflight, SwiftPM 43개, Android 관련 단위 66개·Debug assemble/lint·앱/연습 instrumentation 소스 컴파일을 통과했다. Android 기기 instrumentation 실행은 하지 않았다.
- iPhone 통합 앱 단위 358개와 관련 UI 6개를 통과했다. 마지막 앱 변경 `b5ebf09`에서 단위 358개·언어/세션 UI 2개를 재검증했다. 이후 변경은 UI 테스트의 iPad 스크롤 범위 판정뿐이며 iPhone·iPad 스페인어 전환/저장 재검증을 통과했다. iPad 회전·세션 보존·접근성 큰 글자 UI 3개도 통과했다.
- iOS 번들·Android APK에 ja/en/es 리소스가 포함됨을 확인했다. `shared/`·Swift 공용 엔진·Android DeckKit은 #66 대비 변경이 없다. 스페인어 UI 학습 뜻·읽기·덱 편집은 기존 영어를 사용한다.
- Actions `enabled=false`이며 CI/리뷰 규칙·보호 설정을 변경하지 않았다. 사용자의 이번 요청에 대한 명시적 예외 승인으로 PR #70을 병합한다. 이 예외를 이후 PR이나 출시 gate에 확대하지 않는다. 스토어/실기기/결제/권리 gate는 별도로 남아 있다.
