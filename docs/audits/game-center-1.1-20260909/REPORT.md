# iOS 1.1 (24) Game Center 누락 조사

조사: 2026-09-09 23시대~09-10 JST. 사용자 확인: **앱 내 두벌식**으로 게임을 마친 뒤에도 기록이 누락됨.
후속 수정: [TYP-120](https://linear.app/typee/issue/TYP-120), Todo / High. 조사 작업은 #7에 기록한다.
소스: `85ebfadabc434659943ea8cd3edcb93a91a39a71` (`origin/main`과 동일).
범위: iOS/iPadOS 게임별 기록 생성, 저장·보충 제출, 인증·대시보드, 순위 조회, 출고 archive, App Store Connect 전체 리더보드.

## 결론

**현행 리더보드의 미공개나 출고 entitlement 누락은 발견되지 않았다. 앱에 제출·재시도·순위 갱신 결함이 있고, 스토어에는 구형 보드가 기본값으로 노출되는 문제가 남아 있다.**

실제 서비스 구현을 로컬 GameKit 대역에 연결하여 아래 F1~F4를 재현했다. 이 검증은 제어된 응답 순서에 대한 코드 결함 증거다. 사용자 기기에서 발생했던 개별 실패의 GameKit 오류·서버 수신 기록은 확보하지 못했으므로, 이번 신고의 단일 원인을 확정한 것은 아니다. 앱 코드와 운영 설정의 수정·배포는 이 조사에 포함하지 않았다.

**9월 10일 추가 캡처로 신고 증상이 구체화됐다.** 사용자는 영어 설정·내장 두벌식으로 Flow 초급·중급·고급을 완료했으나 **결과의 랭킹 버튼 자체가 없음**을 확인했다. 아래 F7의 availability 차단을 이번 증상의 최우선 조사 대상으로 올린다. 구형 보드 혼동(F5)이나 순위 숫자 갱신(F4)만으로 이 버튼 누락을 설명할 수 없다.

## 배포본·스토어 직접 확인

- [App Store Connect 버전](https://appstoreconnect.apple.com/apps/6794853985/distribution/ios/version/deliverable): **1.1 Ready for Distribution**, 선택 build **24**, Game Center 체크 ON.
- [build 24 메타데이터](https://appstoreconnect.apple.com/apps/6794853985/testflight/ios/9dbf7ab8-b018-4525-a087-8cd4234d26c0/metadata): Validated, 2026-09-07 00:15 JST 업로드, Bundle ID `app.piyokey.Piyokey`, `com.apple.developer.game-center=true`, `get-task-allow=false`.
- 연결된 **iPhone 15 Pro JM**의 설치 앱을 `devicectl`로 조회: `typee`, **1.1 (24)**, `builtByDeveloper=false`.
- TYP-43의 build 24 제출 증빙이 소스 `85ebfad…`와 ASC build ID `9dbf7ab8-…`를 연결한다. 해당 archive의 `Info.plist`를 직접 읽어 version/build와 available/intended 16개 ID가 소스와 일치함을 확인했다.
- archive: `/Users/jungminoh/orca/workspaces/hanco/TYP-43-ios-1-1-release/outputs/appstore-1.1-24-20260907/PIYOKEY-1.1-24.xcarchive`.
- [Game Center 목록](https://appstoreconnect.apple.com/apps/6794853985/distribution/gamecenter)과 **20개 개별 상세 화면 전부**를 읽었다. 현행 16개 + 구형 4개 모두 Live. 각 보드의 저장값은 Integer, 0~1,000,000, Best Score, High to Low, Hidden=No였다.
- 현재 [주간 피요컵 v4](https://appstoreconnect.apple.com/apps/6794853985/distribution/gamecenter/leaderboards/b98569fe-dde9-44fb-a8cd-4a2a88c76e31): 2026-08-17 00:00 GMT+9 시작, 기간 7일, 재시작 7일. 앱의 JST 월요일 00:00 구분과 일치한다.
- 업적 5개도 모두 Live. 이번 조사는 업적의 실기기 달성 여부를 검증하지 않았다.

| 대상 | 점검한 ID | 개수 | 현재 앱 제출 대상 |
|---|---|---:|---|
| 흐름 | `piyokey.v4.flow.beginner/intermediate/advanced` | 3 | 예 |
| 산성비 | `piyokey.v3.acid_rain.beginner/intermediate/advanced` | 3 | 예 |
| 초성 | `piyokey.v3.choseong.beginner/intermediate/advanced` | 3 | 예 |
| 받아쓰기 | `piyokey.v3.dictation.beginner/intermediate/advanced` | 3 | 예 |
| 단어 퀴즈 | `piyokey.v4.word_match.beginner/intermediate/advanced` | 3 | 예 |
| 주간 피요컵 | `piyokey.v4.cup.weekly.flow` | 1 | 예 |
| 구형 흐름 | `piyokey.v3.flow.beginner/intermediate/advanced` | 3 | 아니요 |
| 구형 주간 | `piyokey.v3.cup.weekly.flow` | 1 | 아니요 |

슬래시 표기는 개별 ID 3개를 축약한 것이다. 위 20개를 모두 개별 확인했으며 일부 표본에서 전체를 추정하지 않았다.

## 발견 사항

### F1 · P1 · 로그인 직후 랭킹 화면보다 점수 제출이 늦게 실행됨

근거: [GameCenterService.swift](../../../ios/Hanco/Hanco/Core/GameCenter/GameCenterService.swift), `handleAuthentication` 607~619행, probe 완료 870~906행, dashboard 종료 832~837행.

비로그인 상태에서 게임 결과가 저장되면 `submitScore(for:)`는 로컬 기록만 보관하고 반환한다. 결과에서 랭킹을 눌러 로그인하면 `isDashboardBusy=true`라서 인증 완료와 availability probe 완료 양쪽에서 `synchronize()`가 생략된다. 대시보드는 제출 완료를 기다리지 않고 열리고, **대시보드를 닫은 뒤**에야 보충 제출한다.

재현: 1,234점 결과 → 랭킹 버튼 → 인증 성공. 첫 랭킹 화면이 열릴 때 전송 호출 **0회**, 닫은 뒤 **1,234점 1회**. 첫 화면에는 신규 기록이 없거나 이전 기록이 보일 수 있다. 화면 표시 자체가 실패한 분기도 명시적 동기화 없이 끝날 수 있다.

권고: 인증/probe가 준비되면 dashboard 표시 여부와 독립적으로 제출 큐를 처리한다. 결과에서는 제출 상태를 표시하고, 요청한 보드의 제출·조회 완료 또는 명시적 timeout 후 랭킹으로 진입하도록 순서를 정한다. 플레이 도중 인증 UI를 표시하지 않는 기존 계약은 유지한다.

### F2 · P1 · 실패한 최고점이 복귀·다음 게임에서 보충되지 않음

근거: 서비스 `updateSceneActivity` 444~450행, `submitScore(for:)` 494~511행, 제출 callback 939~964행; [AppRootView.swift](../../../ios/Hanco/Hanco/App/AppRootView.swift) 284~304행.

제출 오류 때 오류 플래그만 세우고 재시도를 예약하지 않는다. foreground 복귀는 주간 경계만 갱신한다. 기록 변경은 로컬 캐시만 바꾼다. 다음 결과 제출은 해당 결과 점수만 사용하므로 **실패한 더 높은 점수**를 우선 제출하지 않는다. 자동 network 복구 재시도도 없다.

재현: 9,000점 전송 실패 → 네트워크 복구를 가정하고 background/foreground → 재전송 없음 → 다음 게임 3,000점 성공. 전송 이력은 `[9000 실패, 3000 성공]`이고 실패 플래그는 false로 바뀐다. 명시적으로 `synchronize()`를 호출해야 9,000점이 다시 전송된다.

권고: 로컬 기록에서 보드별 최고점을 선정하고, 미확인 최고점을 유지하는 보드별 제출 상태를 둔다. foreground/명시적 재시도/연결 복구에서 bounded backoff로 처리한다. 성공한 다른 요청이 미해결 최고점의 실패 상태를 지우지 않도록 한다.

### F3 · P2 · 제출 callback 지연·미도착 시 같은 점수 이하를 계속 차단

근거: 서비스 `submit` 918~940행. `submittingScores[board]`는 callback 또는 플레이어 변경 때 해제된다. 제출에는 availability probe와 달리 timeout과 요청 generation이 없다.

제어된 미응답 재현: 8,000점 callback을 보류한 뒤 synchronize 및 4,000점 결과를 호출해도 실제 제출 호출은 `[8000]` 한 번뿐. 서비스 수명 동안 같은 값·낮은 값의 재시도가 막히며 `lastSyncFailed`도 false다.

Apple callback이 실제 사용자 환경에서 영구 미도착했다고 확인한 것은 아니다. 장시간 지연을 회복할 앱 측 장치가 없다는 결함이다. timeout으로 in-flight를 해제하고 뒤늦은 callback을 generation으로 구분해야 한다.

### F4 · P2 · 늦게 반영된 서버 순위를 다시 읽지 않음

근거: 서비스 `loadRanks` 1000~1104행. `localEntry=nil`, `entryError=nil`도 `loadedRankLeaderboards`에 넣는다. 이후 일반 synchronize는 이미 읽은 보드를 제외한다. 같은/낮은 점수는 제출 캐시에서 건너뛰므로 성공 제출에 따른 강제 조회도 발생하지 않는다.

재현: 7,000점 제출 성공 → 즉시 순위 조회는 nil → 서버 대역에 7위 엔트리 추가 → synchronize. 결과 화면의 순위는 여전히 **nil**. 이는 원격 기록 저장 실패와 별개의 **앱 표시 갱신 실패**다.

권고: 제출 뒤 제한된 재조회, 결과/대시보드 복귀 시 freshness 기준의 refresh를 적용한다. 아직 local entry가 없는 응답을 세션 전체의 조회 완료로 캐시하지 않는다.

### F5 · P2 · 구형 흐름 보드가 기본값이고 현행과 같은 이름으로 노출됨

[구형 흐름 초급 v3](https://appstoreconnect.apple.com/apps/6794853985/distribution/gamecenter/leaderboards/d52c02ce-9c0c-459c-9ae2-59c0d63df775)가 **Default**다. 구형 흐름 3개와 주간 1개도 Hidden=No다. v3/v4 흐름 표시명은 각 난이도별로 동일하며(예: `フロー・初級`, `Flow · Beginner`, `흐름 · 초급`), 구형/현행 주간도 동일한 `週間ピヨカップ` 이름이다.

앱 1.1은 흐름 v4에만 쓰므로, Game Center 전체 목록에서 구형 보드를 열면 최신 점수가 없다. 구형 iOS의 전체 보드 fallback도 영향을 받는다. iOS 18+ 결과 버튼은 현행 ID를 직접 지정하므로 **이 설정 하나로 모든 게임의 누락을 설명할 수는 없다**.

권고: 운영 기본 보드를 현행 보드로 지정하고, 구형 보드의 노출/표시명을 정리한다. 기존 점수 보존과 구버전 앱 동작을 확인한 뒤 변경한다. 이번 조사에서는 저장값을 바꾸지 않았다.

### F6 · 진단 공백 · 실패 원인과 대상 보드를 사용자·운영자가 구분할 수 없음

`lastSyncFailed`는 전역 Bool이고 `NSError` domain/code, board ID, 점수, attempt, 재시도 상태를 남기지 않는다. 한 보드/업적의 성공이 다른 요청의 실패를 덮을 수 있다. [FlowGameResultView.swift](../../../ios/Hanco/Hanco/Features/Game/FlowGameResultView.swift) 404~440행은 인증/제출/실패/대기 상태를 구분하지 않고 순위 또는 랭킹 버튼만 보여 준다. 앱 전체에서 `lastSyncFailed`를 읽는 소비자도 없다.

권고: 보드별 pending/submitting/submitted/failed 상태, 명시적 재시도, 개인정보를 제외한 로컬 구조화 오류 로그를 추가한다. 로그를 외부 분석으로 보내는 것은 별도 동의 계약에 맞춰 검토한다.

### F7 · P1 · 가용 보드 조회가 Flow 버튼과 제출을 함께 차단할 수 있음

사용자 증거: `IMG_2269.PNG`(중급 478점, Personal best 1,070점), `IMG_2272.PNG`(초급 464점, Personal best 588점), `IMG_2271.PNG`(고급 220점, NEW RECORD), `IMG_2270.PNG`(중급 결과 하단, 복습 목록 다음에 랭킹 없이 Save Image/Share가 나옴). 세 난이도 완료·버튼 누락과 영어·내장 두벌식은 사용자 확인이며, 하단 버튼 영역을 직접 보여 주는 사진은 중급이다. 사용자 이미지 원본은 저장소에 복사하지 않았다.

코드와 대조해 확인한 사항:

- `FlowGameResultView` 264~271행의 `Built-in keyboard`는 `recordOutcome.record.inputMode`를 읽어 표시한다. `Personal best`/`NEW RECORD` 역시 `recordOutcome`가 있을 때만 표시한다. 따라서 이 결과들에서 `recordOutcome == nil` 때문에 버튼이 빠졌다는 설명은 맞지 않는다. 이는 기록 객체의 존재를 입증하며 디스크 영속화 성공까지 단정하는 근거는 아니다.
- Beginner/Intermediate/Advanced 표시는 고정 preset 덱 ID와의 일치로 결정된다. 실제 build 24 archive의 세 Flow JSON도 고정 ID·version 3·100개 항목이고, Info.plist baseline에는 현행 Flow v4 세 ID가 모두 들어 있다. 게임 preset 로더는 version 3을 검사하며 언어에 따라 덱을 교체하지 않는다.
- 결과 버튼은 366~369행에서 `isLeaderboardAvailable(for:)`가 false면 렌더링하지 않는다. 같은 조건이 서비스 499행의 점수 제출도 막는다. 목숨 소진, 낮은 점수, 신기록 여부, 로그인 여부는 버튼을 숨기는 직접 조건이 아니다.
- iOS 26의 전체 목록 조회는 `.released`인 보드만 남긴다(서비스 887~896행). **오류 없는 부분 목록 또는 공개 상태 플래그가 빠진 응답**은 baseline보다 우선한다(149~159행). `probeIsComplete`가 true이므로 같은 플레이어 세션에서는 prepare/foreground/synchronize를 반복해도 전체 목록을 다시 조회하지 않는다.

추가 대역 재현은 실제 서비스 소스를 수정하지 않고 GameKit 응답만 바꿨다. 버튼 표시식이 사용하는 서비스 판정과 실제 제출 호출 횟수를 검사했으며 SwiftUI 화면을 실기기에서 자동 재현한 것은 아니다.

| 제어된 GameKit 응답 | Flow 초·중·고 버튼 조건 | Flow 전송 호출 | 산성비 초급 전송 |
|---|---|---:|---|
| 현행 16 + 구형 4, 모두 released | 모두 true | 3회 | 성공 |
| 정상 응답이지만 Flow 현행 3개 누락 | 모두 false | 0회 | 성공 |
| 20개를 모두 반환하되 Flow 현행 3개의 released 표시 없음 | 모두 false | 0회 | 성공 |

후자의 두 경우에 서버 대역을 정상 20개 응답으로 바꾸고 foreground/prepare/synchronize를 실행해도 전체 조회 횟수는 1회, Flow 버튼은 모두 false로 유지됐다. [재현 실행기](availability_probe.py), [시나리오](availability_probe.swift), [출력](availability_probe.log). 실행: `python3.12 docs/audits/game-center-1.1-20260909/availability_probe.py`.

**확정 범위:** 이 차단 경로가 버튼 누락과 실제 미제출을 동시에 만들고 세션 중 회복하지 못함은 재현했다. **미확정 범위:** 사용자 기기의 GameKit 응답·releaseState·availableLeaderboards는 아직 읽지 못했다. 빈/부분 응답 또는 플래그 누락을 실제 Apple 서버 장애로 확정하지 않는다. Games 앱에서 20개가 보인다는 사실은 우리 앱의 해당 호출에서 받은 응답과 동일함을 보증하지 않는다.

수정 방향: 출시 확인된 baseline과 런타임 조회 실패·불완전 응답을 구분해 일시적인 응답이 정당한 기록의 제출 경로와 복구 버튼을 영구 차단하지 않도록 한다. 의도적으로 미출시/제한된 새 보드는 계속 차단해야 한다. 화면에 자격 제외와 확인 실패를 구분하고, 한정된 재조회·명시적 재시도 및 보드 ID/상태 진단을 추가한다. 실제 기기의 실패 세션을 진단하고 같은 빌드의 대조 실험을 수행하기 전에는 이번 개별 사건의 원인 확정이나 수정 완료로 표시하지 않는다.

## 정상 계약과 추가 확인 필요 항목

- **Best Score**이므로 매 게임 이력을 쌓는 것이 아니라 최고점만 갱신한다. 최고점 이하 결과가 리더보드 값을 바꾸지 않는 것은 정상이다.
- 공식 고정 v3 덱의 내장 두벌식만 클래식 15개 대상이다. OS/앱 내 천지인·사용자/다운로드 덱·띄어쓰기는 일반 랭킹 제외. 이번 사용자는 내장 두벌식을 확인했으므로 키보드 제외 정책으로 이번 신고를 종결하면 안 된다.
- 피요컵 두벌식은 주간+흐름 초급, OS 키보드는 주간만 제출한다. 지난주 점수를 새 주간 회차로 보충하지 않는 것은 정상이다. [Apple recurring 설명](https://developer.apple.com/documentation/gamekit/creating-recurring-leaderboards)과 운영 주기를 대조했다.
- iOS 26 availability 차단의 추가 재현과 사용자 캡처의 대조는 F7 참조. 실제 기기의 응답은 여전히 미관찰이며 F1~F4의 기존 재현에서는 정상 16개 released 응답을 사용했다.
- `GameProgressStore`는 2,048건으로 압축할 때 클래식 최고점과 현재 주간 최고점 대표 기록을 보존한다. 이에 대한 기존 단위 검증은 통과했다.
- `release/GAME_CENTER_SETUP.md`와 smoke 문서에는 피요컵 내장 키보드 고정/OS 제외라는 과거 문구가 남아 있고, `PROJECT_STATUS.md`도 build 24 출시 전 상태였다. 현재 PRD와 배포 상태가 우선이며 오래된 문서를 검증 완료 증거로 재사용하면 안 된다.

## 실행 검증과 재현 자료

- `python3.12 tools/workspace_doctor.py --strict`: PASS (네트워크/인증 접근이 허용된 실행). 시스템 Python 3.9.6의 버전 실패는 3.12.13을 사용해 해결했다.
- 현재 소스의 `GameCenterServiceTests` **20/20**, `GameProgressStoreTests` **16/16**, 총 **36/36 PASS**. Game Center 기존 테스트는 매핑/정책 중심이며 실제 서비스 callback 순서를 다루지 않는다.
- 명령: `xcodebuild test -project ios/Hanco/Hanco.xcodeproj -scheme Hanco -destination 'platform=iOS Simulator,id=85F886ED-1D0C-43A9-B72C-44EBBF0FBECB' -derivedDataPath /tmp/piyokey-gc-audit-dd -resultBundlePath /tmp/piyokey-gc-audit-tests.xcresult -only-testing:HancoTests/GameCenterServiceTests -only-testing:HancoTests/GameProgressStoreTests -parallel-testing-enabled NO CODE_SIGNING_ALLOWED=NO`.
- 로컬 전체 출력: `/tmp/piyokey-gc-audit-tests.log`, `/tmp/piyokey-gc-audit-tests.xcresult`.
- [재현 실행기](reproduce.py), [GameKit/UI 대역](stubs.swift), [실행 시나리오](main.swift), [재현 출력](reproduction.log). 실행: `python3.12 docs/audits/game-center-1.1-20260909/reproduce.py`.
- 실행기는 원본 서비스 SHA-256을 확인하고 **framework import 3개만 제거**해 대역과 함께 컴파일한다. 네트워크·실제 Game Center 계정을 사용하지 않는다. assertion은 이 감사 시점의 결함 재현을 확인한다. 수정 후 제품 회귀 테스트를 대신할 테스트가 아니다.

## 남은 실기기 검증과 수정 순서

1. 버튼 자체 누락을 설명하는 F7 availability 차단을 먼저 진단·수정하고 F1/F2 및 F3/F4와 보드별 오류 상태를 함께 회귀 검증한다. 공개판 갱신에는 새 앱 버전이 필요하다.
2. 별도로 App Store Connect의 기본 보드·구형 보드 표시를 정리한다. 이는 앱의 재시도 결함을 해결하지 않는다.
3. 수정된 동일 빌드의 iPhone·iPad에서 15개 클래식+주간을 내장 두벌식으로 확인한다. 인증 후 첫 진입, offline→online, 낮은 다음 점수, 결과에서 즉시 닫기·종료, 순위 지연, 주간 경계, 로그아웃·계정 전환을 포함한다.
4. 사용자 개별 실패를 판별할 오류 로그와 서버 조회 결과를 확보한다. 이번 iPhone 배포 앱 데이터 컨테이너 열람은 CoreDevice 오류로 불가했고 iPad는 잠금으로 조회하지 못했다. 사용자 데이터·기기 설치본·실제 서버 점수는 변경하지 않았다.

과거 #7/TYP-7의 Done은 보존하되 이번 배포 후 재발은 별도 후속 결함으로 추적한다. 위 미실행 검증을 PASS로 처리하거나 수정 완료로 표시하지 않는다.
