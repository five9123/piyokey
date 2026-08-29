# PIYOKEY 프로젝트 현황

마지막 갱신: 2026-08-29 JST
기준 저장소: `five9123-maker/piyokey`
기준 `main`: `git fetch --prune origin && git rev-parse origin/main`으로 확인
최근 통합 기준선: `2b25a7d9aaa9ec2df3a09a428f3cb6bdea7fdc3c` — PR #74 5언어·온보딩, PR #80 iPad 화면, PR #72 자유 연습 제거까지 반영

이 문서는 현재 상태의 단일 현황판이다. 제품 계약은 `PRD.md`, 확정 결정은 `DECISIONS.md`, 작업 순서는 `ROADMAP.md`를 따른다. 상태가 바뀌면 과거 문장을 덧붙이지 말고 해당 표를 현재 사실로 교체한다.

## 출시 기준선

| 트랙 | 소스 상태 | 공개·배포 상태 | 다음 gate |
|---|---|---|---|
| iOS 공개판 | `1.0.2 (6)` | 2026-08-18 공개 확인; 2026-08-28 글로벌 availability 처리 시작 | EU DSA 거래자 상태와 지역별 실제 판매 상태 확인 |
| iOS 1.1 | `main` `2b25a7d`; 업로드된 `1.1 (7)`은 PR #80·#72 이전 | build 7 TestFlight 처리·1.1 연결 완료, App Review 미제출 | PR #80·#72와 최종 선택 기능을 포함한 새 build 번호 RC, 현지어·미디어·IAP·Paid Apps·Sandbox·Files/iCloud/AirDrop·1,000항목 gate |
| Android 1.1 | `1.1.0 (8)` 소스 후보 | Play 미배포 | Play Console·서명·권리·Billing/Play Games와 Issue #19 동일 signed AAB 실기기 통합 QA |

소스 완료는 스토어 제출 완료가 아니다. 외부 gate가 남아 있으면 `Blocked` 또는 `Verify`로 유지한다.

## 활성 작업

| Issue/PR | 상태 | 소유 branch/worktree | 다음 한 단계 |
|---|---|---|---|
| #83 최종 현황·화면 감사 | In Progress | `codex/83-final-status` / `/private/tmp/piyokey-issue83-final-status` | PRD·현황·로드맵과 최신 main 화면 캡처를 검증해 PR로 제출 |
| #81 / PR #82 홈 추천 2행 | Blocked | `codex/81-home-recommendation-rows` / `/private/tmp/piyokey-issue81` | `main`과 15파일 충돌 해결, 최신 기준 회귀·리뷰 후 병합 여부 결정 |
| #77 iOS 1.1 심사 제출 | Blocked | 기록은 `main`; 보존 worktree `/private/tmp/piyokey-issue77-submission` | 새 RC를 만든 뒤 Account Holder 은행·한국 세금 정보와 나머지 제출 gate 검증 |
| #58 / PR #62 현지 20시 리마인더 | Verify | 구현은 `main` 병합 완료; `/private/tmp/piyokey-issue-58` 보존 | 실제 기기에서 권한 동의·현지 20시 알림 수신 확인 |

## 2026-08-29 저장소 감사

`git fetch --prune origin`, GitHub PR/Issue 조회, 모든 로컬 worktree 상태, stash와 `origin/main`을 대조했다. squash merge된 branch의 커밋이 `main`의 조상으로 보이지 않는 현상은 누락으로 계산하지 않고 해당 merged PR과 최종 tree를 기준으로 판정한다.

| 구분 | 확정 결과 | 처리 |
|---|---|---|
| 원격 기준선 | `origin/main` `2b25a7d`; 최근 PR #74·#80·#72 반영 | 새 작업·캡처의 유일한 소스 기준선 |
| 열린 PR | #82 하나; `CONFLICTING`, main 대비 15파일·422삽입/35삭제 | 미통합으로 명시, 이번 기준 화면에서 제외 |
| worktree | 총 18개; 기본 `/Users/jungminoh/Documents/hanco`만 dirty, 나머지 17개 clean | clean 보존본은 역사·검증 자료, 곧바로 삭제하지 않음 |
| 기본 dirty worktree | 닫힌 #63 branch 위 tracked 44개·untracked 7개; 현재 main보다 오래된 기반 | release 기준이 아님. 새 branch에서 기능별 재적용·검증 전까지 보존 |
| stash | 1개, 35파일·1,214삽입/155삭제; 스토어 미디어·Pro·지원 URL 계열 | apply/drop하지 않음; 복구 원본으로만 유지 |
| dirty에서 확인된 별도 후보 | 챕터5/6 확장, `typee.app` 링크, 흔들림 애니메이션 수정, 스토어 자산·Pro 문구 | 구현 완료로 표시하지 않고 후속 issue로 분리할 recovery queue |
| 과거 local ahead 커밋 | 다수는 squash 통합 PR #44·#62·#70·#74·#80 등 원본 | PR이 merged이면 누락 아님; branch 삭제는 별도 정리 작업 |

감사 상세와 최신 main에서 캡처한 24장 화면 목록은 `artifacts/issue-83/README.md`에 둔다. 이 감사에서 stash 적용, dirty 파일 덮어쓰기, branch/worktree 삭제, PR #82 병합은 수행하지 않았다.

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

- #79 iPad 후속 수정은 업로드된 1.1(7)에 포함되지 않는다. 병합 후 새 빌드 번호로 RC를 만들고 해당 빌드의 기기 QA·스토어 이미지 일치를 확인해야 한다.

- #7: Game Center 계약 전체 점검.
- #19: Android 동일 signed AAB의 입력 지연, rollover, IME, 오디오, 알림, Files, Billing, Play Games, 60fps 통합 QA.
- #58: iOS·Android 실제 기기에서 온보딩 알림 권한 동의 뒤 현지 20시 수신 확인.
- iOS 1.1: `release/APP_STORE_QA.md`의 미완료 수동 gate. Free Apps Agreement Active / Paid Apps Agreement Pending User Info로 갱신된 상태를 확인했다. Account Holder의 은행 계좌 및 한국 세금 양식 입력이 남아 있다. 2026-08-29 미국 Foreign Status·W-8BEN Active를 확인했다. 사용자는 승인 후 자동 출시·단계 배포 없이 전 사용자 즉시 공개를 확정했으며 콘솔 설정과 일치한다. 심사 확인 창은 Continue하지 않고 취소했으며 제출 완료가 아니다. `release/TESTFLIGHT_1_1_SMOKE.md`는 정확한 1.1(7) 실기기 검증 전용이며 아직 미실행이다.
- App Store 글로벌 배포: 175개 국가 또는 지역 선택 완료. 145개 지역은 처리 중이며 EU 29개 지역은 DSA 거래자 상태 입력 전까지 보류. 기본 언어 en-US 전환은 필수 영어 스크린샷 등록 전까지 차단.
- App Store 미디어: iPhone 기존 10시장 100 PNG·10 MP4 결과는 보존하며 최종 RC 일치 재확인 전이다 (`release/store-assets/verification-20260828.json`). #79 iPad는 최종 앱 f674b57과 동일한 촬영 소스로 **모든 기본 이미지 가로 2752×2064, 10시장 100장, 대체본 0장**을 제작했다. 5 UI 언어 촬영·160개 SHA-256·ZIP 10개·전체 contact sheet 검수 완료. 전달: `/Users/jungminoh/Documents/hanco/outputs/ipad-1.1-landscape-store-20260829/index.html`. Chrome 로그인은 유지되지만 영어(미국) iPad 파일 업로드가 `-32000 Not allowed`로 실패했고 슬롯 재조회 `0 of 10`이다. 이번 작업에서 업로드/저장/심사 제출 완료 없음; 기존 iPhone 미디어·공개 메타데이터는 변경하지 않았다. 현재 일본어·영어 4지역·한국어만 등록돼 있으며 7시장 신규 로케일 메타데이터, 오래된 지원 언어 안내 수정 확인, 현지어 사람 검수·최종 RC 일치·업로드 권한/저장 후 재조회가 남아 있다. 상세 `release/IPAD_1_1_QA.md`, 증빙 `release/evidence/1c7dc566e0ebd3302f7a35a2a563735ebc21a6dc.json`.
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

## #71 프리프랙티스 제거 검증

- iOS·Android 연습 탭 하단 카드와 전용 준비/선택 화면을 제거했다. 커리큘럼·일반 덱 연습·공통 설정과 기존 기록 schema는 유지한다. PR #72 병합은 소스 반영만 의미하며 실기기 설치·스토어 배포 완료로 확대하지 않는다.
- 기존 검증 대상 `b07c59f969c56e1f4641e7714e90acf82f1fc8f9`, 증빙 `release/evidence/b07c59f969c56e1f4641e7714e90acf82f1fc8f9.json`. iPhone 관련 UI 8개·ja/en/es resource 1개, iPad 하단 지도·큰 글자 UI 2개, Android retention 단위 13개·앱/기기 테스트 Kotlin 컴파일, preflight·strict doctor 통과.
- iPhone 최초 9개 선택 실행은 테스트 탐색 순서·동일 이름 옵션 때문에 2개 실패했다. 두 테스트를 공통 설정 순서·사운드 picker 범위에 맞춰 각각 재실행해 통과했다. 전체 9개를 한 번에 재실행한 결과로 표현하지 않는다. iOS 제품 소스는 `693a7ca`, Android 소스는 `10ee7db`와 동일하며 이후 변경은 해당 테스트와 검증 문서뿐이다.
- 2026-08-29 최신 main `a678e0f`를 병합하고 새 de/fr UI 리소스에서도 제거된 진입 문구를 정리했다. repository preflight와 UI 언어 범위 8개, Android retention 단위·앱/기기 테스트 Kotlin 컴파일, iPad 커리큘럼 하단 미노출·큰 글자 접근성 UI 2개를 통과했다.
- iPhone 관련 UI 5개 중 3개는 첫 선택 실행에서 통과했고, 2개는 simulator runner/AX 종료 뒤 각각 새 simulator에서 재실행해 통과했다. 5개 전체가 한 번에 통과한 결과로 표현하지 않는다.
- 최신 main 통합 소스는 `f370f5e2e64b3083be497f4ebd1f00a0ce163c02`, 증빙은 `release/evidence/f370f5e2e64b3083be497f4ebd1f00a0ce163c02.json`이다.
- Android 기기 instrumentation은 미실행. 저장소 Actions `enabled=false`와 승인 리뷰 0건을 재확인했다. 사용자가 2026-08-29 대화에서 PR #72 병합을 명시 승인했으므로 이번 PR에 한해 CI·별도 리뷰 없는 예외로 squash merge하고 Issue #71·Project를 완료 처리한다. 보호 설정은 변경하지 않으며 이 예외를 이후 PR이나 출시 gate로 확대하지 않는다. 기존 기본 작업폴더의 dirty 파일은 보존했다.
