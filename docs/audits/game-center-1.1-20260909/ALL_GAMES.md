# iOS 1.1 (24) 게임 전체 Game Center 조사

조사일: 2026-09-10 JST. 출시 앱 소스 `85ebfadabc434659943ea8cd3edcb93a91a39a71`.
기존 발견 사항 번호 F1~F7은 [원 조사 보고서](REPORT.md)를 따른다.

## 결론과 확인 범위

**Flow에 한정된 문제가 아니다. 다섯 게임의 초·중·고 15개와 주간 피요컵, 총 16개 보드 모두 같은 가용성 판정·제출·순위 조회 서비스를 사용한다. 각 보드에 대해 버튼 차단과 F1~F4를 대역으로 재현했다.**

전수 확인한 것은 출고 archive의 15개 덱·16개 ID·5개 언어 리소스, 현재 화면/기록 경로, 16개 보드별 서비스 동작이다. 실제 기기에서 16개 보드 모두에 게임을 플레이하고 서버 수신을 확인한 것은 아니다. 실기기의 버튼 누락 증거는 사용자가 제공한 Flow 결과 캡처와 세 난이도 재현 진술이며, Apple 서버의 해당 시점 응답은 확보하지 못했다.

| 범위 | 출고 계약 | 정상 응답에서 버튼 조건/전송 | 해당 보드 누락 또는 released 표시 누락 | 최초 인증·재시도·미응답·순위 캐시 |
|---|---|---|---|---|
| Flow 초·중·고 | v4 3개 일치 | 3/3 확인 | 3/3 차단 재현 | 각 보드 F1~F4 재현 |
| 산성비 초·중·고 | v3 3개 일치 | 3/3 확인 | 3/3 차단 재현 | 각 보드 F1~F4 재현 |
| 초성 초·중·고 | v3 3개 일치 | 3/3 확인 | 3/3 차단 재현 | 각 보드 F1~F4 재현 |
| 받아쓰기 초·중·고 | v3 3개 일치 | 3/3 확인 | 3/3 차단 재현 | 각 보드 F1~F4 재현 |
| 단어 퀴즈 초·중·고 | v4 3개 일치 | 3/3 확인 | 3/3 차단 재현 | 각 보드 F1~F4 재현 |
| 주간 피요컵 | v4 1개 일치 | 1/1 확인 | OS 입력 기록의 버튼/제출 차단 재현. 내장 입력의 이중 대상은 아래 참조 | F1~F4 재현 |
| 띄어쓰기 | 원격 보드 없음 | PRD에 따라 랭킹 제외 | 오류로 분류하지 않음 | 로컬 게임 테스트 별도 확인 |

위 표의 전송은 모두 GameKit 대역 호출이며 실제 서버 업로드가 아니다. 정상 응답 대조군에서는 예상 16개 ID 모두에 호출됐고, 구형 ID로 호출되는 경우는 없었다.

## 공통 원인과 게임별 차이

1. **F7: 오류 없는 불완전한 전체 보드 응답이 버튼과 제출을 함께 차단한다.** 16개 ID를 각각 하나씩 제외한 16개 시나리오, 각각의 released 표시를 제거한 16개 시나리오에서 해당 보드만 차단됐다. 정상 응답으로 복구해도 같은 세션의 foreground/prepare/synchronize에서 전체 목록을 다시 읽지 않았다. 성공한 빈 응답 또는 구형 4개만 있는 응답이면 현행 16개 전부 차단됐다. 이 상태에서도 실패 플래그가 false인 경우를 확인했다.
2. **통신 오류/5초 timeout은 동작이 다르다.** 이 경우 출고 baseline으로 돌아가 16개 모두에 전송할 수 있었다. 따라서 단순히 인터넷 연결 유무만으로 버튼 소실을 판정할 수 없다. probe 중에는 전송을 보류하지만 baseline의 버튼 조건 자체는 true다.
3. **F1~F4도 모든 16개에서 재현됐다.** 최초 인증 뒤 대시보드를 닫아야 제출, 실패 최고점이 foreground/낮은 다음 점수에서 보충되지 않음, 제출 callback 보류가 같은 점수 이하 재시도를 차단, 빈 순위 응답 이후 생긴 서버 대역 순위를 다시 읽지 않음이다. 총 64개 보드별 결함 재현이다. 이들은 F7과 구분하며 버튼 소실의 직접 원인으로 혼동하지 않는다.
4. **피요컵의 내장 두벌식은 부분 실패를 숨길 수 있다.** 주간 보드가 차단되어도 Flow 초급 클래식이 남으면 버튼은 보이고 클래식에만 제출한다. 클래식이 차단되면 주간에만 제출한다. 둘 다 차단돼야 버튼이 사라진다. OS 키보드의 피요컵은 주간 하나만 대상이라 주간 차단 시 버튼도 사라진다. 내장 입력에서 버튼이 보인다는 이유로 주간 랭킹 제출 성공을 판단하면 안 된다.
5. **F5 구형 보드 중복은 Flow·피요컵의 목록 혼동을 설명한다.** 일반 게임 결과의 버튼 누락과 초성·받아쓰기·단어 퀴즈의 미등록을 이 설정 하나로 설명할 수 없다.

Apple 문서에서 [releaseState](https://developer.apple.com/documentation/gamekit/gkleaderboard/releasestate)는 App Store Connect의 릴리스 상태이며, [GKReleaseState.released](https://developer.apple.com/documentation/gamekit/gkreleasestate)는 출시 버전과 연결된 리소스임을 뜻한다. 사용자의 실제 응답에서 이 값이 누락됐다고 확인한 것은 아니다. 대역의 누락/상태 변형은 앱의 의존성과 복구 동작을 검증하기 위한 제어 조건이다.

## 화면·기록·출고 자산 대조

- `GameDeckSelectionView.swift` 644~657행: Flow·산성비는 `FlowGameView`, 초성·단어 퀴즈·받아쓰기는 `ChoseongTypingView`로 연결된다. 과거 선택형 `ChoseongQuizView`는 현재 앱의 호출 지점이 없으며 활성 경로로 세지 않았다.
- `FlowGameView.swift` 206~221행/1227~1250행, `ChoseongQuizView.swift` 1617~1628행/2354~2375행: 게임 종료 시 덱 ID·버전·입력 모드를 기록하고 모두 `FlowGameResultView`로 전달한다. 종료 이유·신기록 여부·언어에 따른 랭킹 제외 분기는 없다.
- `FlowGameResultView.swift` 366~369행: 기록이 있고 `isLeaderboardAvailable(for:)`가 true일 때만 버튼을 만든다. 같은 서비스 판정이 `submitScore(for:)` 499행에서도 전송을 막는다. 비로그인 자체는 버튼을 숨기는 조건이 아니다.
- 실제 build 24 archive의 모든 게임 preset은 정확한 ID, version 3, 100개 항목이다. available/intended 목록은 예상 16개와 정확히 일치했다. [재현 가능한 자산 점검기](archive_inventory.py), [전체 점검값 및 파일 SHA-256](archive_inventory.json).
- ja/en/es/de/fr 모두 랭킹 버튼과 순위·입력 모드 문구가 존재하며 비어 있지 않다. 언어별 덱/리더보드 ID 분기는 없었다. 실제 기기의 언어를 바꿔 가며 GameKit 응답을 비교한 것은 아니다.
- 등록 정책 111건을 검사했다. 공식 15개 × 입력 3종, 버전 누락/2/4, 레슨 제외, 원격·사용자·띄어쓰기 ID 제외, 피요컵 입력 3종 모두 현재 PRD와 일치했다. 일반 OS/10키 제외와 피요컵 OS 허용을 결함으로 분류하지 않는다.

## 실행 검증

서비스 파일 SHA-256: `f37e33744f345f6a7d91e5287427b274ed50438e09cff6e5ccf9edb54dcb2c43`. 실행기는 이를 고정하고 framework import 3개만 제거해 원본 서비스를 GameKit/UI 대역과 컴파일한다.

- `python3.12 docs/audits/game-center-1.1-20260909/all_boards.py`: **111개 등록 정책 판정, 37개 가용성 시나리오, 64개 F1~F4 재현, 3개 피요컵 부분 가용성 시나리오**의 감사 assertion 일치. [시나리오](all_boards.swift), [전체 출력](all_boards.log). 종료 코드 0은 알려진 결함을 재현했다는 뜻을 포함하며 제품 정상 판정을 뜻하지 않는다.
- iOS focused XCTest: **116/116 PASS**. `GameCenterServiceTests` 20, `GameProgressStoreTests` 16, `FlowGameViewModelTests` 42, `FlowGameRankTuningTests` 3, `ChoseongQuizViewModelTests` 28, `SpacingGameEngineTests` 5, `SpacingGameViewModelTests` 2.
- 첫 xcodebuild의 `SpacingGameTests` 선택자는 파일명이고 실제 클래스명이 아니어서 109개만 실행됐다. 이를 확인한 뒤 정확한 두 클래스만 추가 실행하여 7/7 통과했다. 테스트 선택 성공만으로 실행됐다고 세지 않았다.
- Xcode 결과: `/tmp/piyokey-allgame-audit-20260910.xcresult`, `/tmp/piyokey-spacing-audit-20260910.xcresult`. 로그는 같은 이름의 `.log`. [실행 명령·결과 기록](all_games_verification.json).
- 기존 Game Center 테스트는 부분 응답이 baseline을 대체하고 재조회하지 않는 현행 정책을 통과 조건으로 삼고 있다. 따라서 116개 PASS를 버튼 복구 검증으로 해석하지 않는다.

## 실제 기기 확인과 남은 범위

- 01:17 JST 연결된 iPhone 15 Pro JM에서 `--include-default-apps --bundle-id app.piyokey.Piyokey`로 **typee 1.1 (24)**를 재확인했다.
- 같은 기기의 앱 데이터 컨테이너 `Library/Application Support` 목록 열람은 `CoreDevice.ActionError 3`으로 실패했다. 기록 파일과 가용 보드 캐시/실제 GameKit 응답을 확보하지 못했다. 다른 앱/기기의 설치나 사용자 데이터는 변경하지 않았다.
- 사용자의 Flow 결과는 실제 증거다. 다른 게임의 현재 기기별 결과/Apple 서버 수신/응답 필드는 아직 미확인이다. 이를 확인하려면 실행 중 진단값 또는 동일 계정·조건의 진단 빌드가 필요하다. 현재 서비스는 필요한 보드별 오류/응답 상태를 남기지 않아 기존 캡처만으로 그 값까지 복원할 수 없다.
- 다음 수정 범위는 공통 서비스 F7 및 F1~F4·진단 표시이며, Flow만 별도로 우회하면 안 된다. 출시 확인된 보드의 일시적 조회 문제와 실제 미출시·제한 상태를 구분하고, 버튼·재시도·제출 경로를 복구해야 한다. 공개판 코드나 App Store Connect 설정은 이번 조사에서 수정하지 않았다.
