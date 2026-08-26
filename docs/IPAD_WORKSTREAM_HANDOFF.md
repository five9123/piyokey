# PIYOKEY iPad 작업 인계서

작성일: 2026-08-25  
대상 Issue: [#10 iPad 앱 지원 범위 결정 및 화면 최적화](https://github.com/five9123-maker/piyokey/issues/10)  
저장소: [five9123-maker/piyokey](https://github.com/five9123-maker/piyokey)

## 새 Codex 작업에 전달할 첫 요청

아래 블록을 새 작업의 첫 메시지로 붙여 넣고 이 파일을 함께 첨부한다.

```text
PIYOKEY iPad 지원 작업을 이어서 진행해줘.

먼저 첨부한 docs/IPAD_WORKSTREAM_HANDOFF.md를 끝까지 읽고,
저장소의 AGENTS.md, docs/GITHUB_PROJECTS_GUIDE.md, PRD.md 관련 절,
DECISIONS.md, GitHub Issue #10과 Project 현재 상태를 직접 확인해줘.

이전 대화의 기억을 전제로 구현하지 말고 문서와 GitHub 상태를 원본으로 사용해줘.
Issue #10은 현재 Needs Decision이므로 내가 권고 범위를 승인하기 전에는
코드·PRD·Xcode 설정을 변경하거나 branch/worktree를 만들지 마.

승인 후에는 기존 iOS 앱을 별도 앱으로 복제하지 말고 같은 저장소·Xcode
프로젝트·Bundle ID의 iPhone+iPad Universal 앱으로 확장해줘. 기존 dirty
worktree, Android Issue #13 worktree, 다른 Issue branch는 건드리지 말고
Issue #10 전용 worktree와 codex/10-ipad-universal branch를 사용해줘.

첫 응답에서는 현재 상태를 다시 검증한 결과, 확정이 필요한 지원 범위,
권장 단계와 예상 변경 파일, 테스트·수동 QA 계획만 보고해줘.
```

## 현재 상태

- GitHub Issue #10은 열려 있고 Project 상태는 `Needs Decision`이다.
- Project 필드: Priority `P2`, Area `iOS`, Target `Later`.
- 현재 PRD §2.3은 태블릿 최적화와 가로 모드를 비목표로 둔다. iPad 구현 전
  사용자 승인과 PRD 정정이 필요하다.
- Xcode 프로젝트는 `ios/Hanco/Hanco.xcodeproj`다.
- 현재 `TARGETED_DEVICE_FAMILY = 1`로 iPhone 전용이다.
- `Info.plist`는 iPhone 세로 방향만 선언한다.
- 최소 iOS 버전은 iOS 16+다.
- Bluetooth·물리 키보드 입력 호환성은 Issue #12에서 별도 추적한다.
- Android Issue #13은 다른 Codex 작업이 소유한다. 이 작업에서 `android/`와
  `/private/tmp/piyokey-issue-13`을 읽거나 수정하거나 테스트하지 않는다.

## 승인 대기 중인 권고 범위

권고안은 기존 앱을 별도 iPad 앱으로 복제하지 않고 Universal 앱으로 확장하는
것이다.

- 기존 App Store 앱, Bundle ID, Xcode project와 scheme 유지
- iPhone+iPad Universal target
- 최소 iPadOS 16 유지
- iPad 세로·가로 지원
- full screen, Split View 1/2·1/3, Stage Manager와 resizable window 지원
- 기존 5탭 내비게이션 유지; iPad 전용 sidebar 재설계는 제외
- 콘텐츠는 중앙 최대 폭과 폭별 grid 열 수로 조정
- 내장 두벌식 키보드는 하단 고정, 최대 폭 제한, 좁은 창에서 compact 재사용
- 홈·둘러보기·연습·게임·결과·마이페이지·설정의 핵심 흐름 지원
- 포인터, 키보드 포커스, VoiceOver, Dynamic Type 검증
- iPad 실기기와 App Store iPad 스크린샷은 일괄 수동 QA에 포함

예상 공수는 개발 6~10일, 수동 QA 2~4일이다. 설치 호환만 여는 1~2일
축소안과 iPad 전용 sidebar·별도 내비게이션은 권고 범위가 아니다.

## 사용자에게 받을 결정

다음 한 문장으로 승인을 받아야 한다.

> 기존 5탭을 유지하는 iPhone+iPad Universal 앱으로 전환하고, iPadOS 16+
> 세로·가로·Split View·Stage Manager를 지원하는 권고 범위로 진행한다.

승인 전에는 Issue #10을 `Ready`로 이동하지 않고 구현도 시작하지 않는다.
범위가 달라지면 Issue 본문과 AC를 먼저 갱신한다.

## 승인 후 GitHub 순서

1. Issue #10에 사용자 승인 범위, 관련 PRD 절, AC, 검증 계획과 제외 범위를 기록한다.
2. Project의 Issue #10을 `Needs Decision`에서 `Ready`로 이동한다.
3. 구현 시작 시 담당자를 배정하고 `In Progress`로 이동한다.
4. 아래 시작 댓글을 남긴다.

```markdown
작업 시작
- 범위: 기존 PIYOKEY를 iPhone+iPad Universal 앱으로 확장
- 관련 PRD: §2.3, F2, F2a, F4, F6, F10, §7, §12, §13
- 검증 계획: iPhone 회귀 + iPad 폭·방향별 build/UI + 실기기 일괄 QA
- 충돌 가능 파일: project.pbxproj, Info.plist, 공통 화면 container, 키보드·게임 HUD
```

5. Draft PR을 일찍 만들되 사용자 승인과 필수 수동 gate 전에는 병합하지 않는다.
6. 자동 검증과 UI 증빙을 연결한 뒤 `Verify`로 이동한다.
7. PR 병합, 사용자 승인, 필수 gate 완료 뒤에만 Issue를 닫고 `Done` 처리한다.

## 안전한 Git/worktree 구성

현재 기본 폴더 `/Users/jungminoh/Documents/hanco`에는 다른 Issue의 미커밋 변경이
있다. 새 작업은 이 폴더에서 checkout, rebase, reset 또는 commit하지 않는다.

승인 후 먼저 다음을 확인한다.

```bash
cd /Users/jungminoh/Documents/hanco
git status --short --branch
git worktree list --porcelain
git branch --list 'codex/10-ipad-universal'
git fetch origin
```

branch와 대상 폴더가 없고 `origin/main`이 올바른 기준선임을 확인한 뒤 전용
worktree를 만든다.

```bash
git worktree add \
  -b codex/10-ipad-universal \
  /Users/jungminoh/Documents/hanco-ipad \
  origin/main
```

이미 branch나 worktree가 있으면 새로 만들거나 삭제하지 말고 상태와 소유 작업을
확인한다. 다른 Issue branch의 변경을 가져와야 하면 해당 PR이 먼저 병합됐는지
확인하고, 미병합 변경을 임의로 복사하지 않는다.

## Xcode와 폴더 원칙

새 `.xcodeproj`, 별도 iPad app target, 새 Bundle ID, 새 GitHub 저장소를 만들지
않는다. 다음 기존 구조를 확장한다.

```text
ios/Hanco/Hanco.xcodeproj
ios/Hanco/Hanco/
  Core/
    Layout/                 # 공통 adaptive metrics가 필요할 때만 추가
  Features/
    Home/
    Discover/
    Practice/
    Game/
    MyPage/
    Settings/
ios/Hanco/HancoTests/
ios/Hanco/HancoUITests/
artifacts/ipad/             # 승인된 캡처·QA 증빙
```

화면 전체를 `Features/iPad/`에 복사하지 않는다. 공통 화면은 유지하고 size class,
가용 폭과 geometry에 따른 container·grid·최대 폭만 분리한다. iPad 전용 코드는
공통 컴포넌트로 표현할 수 없는 플랫폼 동작에만 둔다.

## 권장 구현 단계

각 단계는 가능하면 Issue #10의 sub-issue와 작은 PR로 분리한다. 앞 단계가
병합된 후 다음 branch를 최신 `origin/main`에서 만든다.

1. **Universal 기반**
   - target device family, 방향과 window 계약
   - 기존 iPhone build와 launch 회귀
2. **Adaptive layout 기반**
   - 중앙 최대 폭, compact/regular width, grid 정책
   - 공통 safe area와 탭 container
3. **탐색·학습 화면**
   - 홈, 둘러보기, 연습, 마이페이지, 설정
4. **세션·게임 화면**
   - 하단 키보드 고정, 문제 레인, HUD, 결과 화면
   - 세션 중 모달 금지와 60fps 계약 유지
5. **접근성·상호작용·출시 증빙**
   - 포인터, 포커스, VoiceOver, Dynamic Type
   - 실기기·방향·창 크기 매트릭스와 App Store 자료

## Acceptance Criteria 초안

- 같은 앱 설치로 iPhone과 지원 iPad에서 실행된다.
- iPhone 기존 세로 UI와 저장 데이터가 회귀하지 않는다.
- iPad full screen 세로·가로에서 5개 탭의 핵심 흐름을 사용할 수 있다.
- Split View 1/2·1/3과 Stage Manager의 좁은 폭에서도 콘텐츠가 잘리거나
  겹치지 않고 가로 스크롤이 생기지 않는다. 자모 트랙의 의도된 내부 가로
  스크롤은 예외다.
- 내장 키보드는 항상 가용 폭 안에 있고 문제 콘텐츠·홈 인디케이터와 겹치지 않는다.
- 게임 HUD 수치, 닫기 버튼, 문제 카드와 키보드가 서로 겹치지 않는다.
- 방향·창 크기 변경 중 현재 탭, 문제, 입력 진행, 타이머 상태가 보존된다.
- 세션 중 새 모달이나 설정 전환 UI가 나타나지 않는다.
- 포인터와 키보드 포커스가 주요 버튼·입력 영역에서 동작한다.
- VoiceOver와 큰 Dynamic Type에서 핵심 메뉴와 세션 종료 동작에 접근할 수 있다.
- 관련 단위·UI 테스트, Release build와 저장소 preflight가 통과한다.
- 지원 iPad 실기기 또는 사용자가 승인한 동등 gate 증거가 Issue에 연결된다.

## 테스트와 증빙 계획

전체 회귀를 매 단계 실행하지 않는다. 변경한 화면과 공통 layout에 직접 관련된
테스트를 우선하고, Universal 기반 변경·공통 container 변경·완료 후보에서 범위를
확대한다.

```bash
# 기존 iPhone 기준선
xcodebuild test \
  -project ios/Hanco/Hanco.xcodeproj \
  -scheme Hanco \
  -destination 'platform=iOS Simulator,name=iPhone 17,OS=26.5' \
  -only-testing:<관련 테스트>

# 사용 가능한 iPad destination은 현재 Xcode에서 먼저 확인
xcodebuild -showdestinations \
  -project ios/Hanco/Hanco.xcodeproj \
  -scheme Hanco

# 저장소 계약
python3 tools/release_preflight.py
```

UI 변경은 최소 다음 매트릭스의 스크린샷 또는 영상 증거를 `artifacts/ipad/`에
남긴다.

- full screen: 세로, 가로
- Split View 또는 동등한 compact 폭: 1/2, 1/3
- Stage Manager 또는 resizable window: regular, compact
- Dynamic Type: 기본, 접근성 크기 1개
- 핵심 흐름: 홈 → 연습 → 결과, 게임 시작 → 플레이 → 결과, 설정

## 열려 있는 위험

- 현재 PRD의 비목표와 정면으로 충돌하므로 승인된 PRD 변경이 선행돼야 한다.
- `project.pbxproj`와 `Info.plist`는 여러 출시·기기 작업과 충돌 가능성이 높다.
- 공통 화면의 무분별한 최대 폭 적용은 게임 카드 레인·키보드 좌표·파티클 성능을
  깨뜨릴 수 있다.
- iPad 지원을 켜면 App Store 제출 자료와 실기기 검증 범위가 늘어난다.
- Bluetooth·물리 키보드 Issue #12의 결과를 중복 구현하지 않는다.
- Android 작업과 공용 fixture를 이 작업에서 변경하지 않는다.

## 새 작업의 중단 조건

다음 중 하나면 구현하지 말고 사용자에게 보고한다.

- 사용자가 권고 지원 범위를 승인하지 않음
- Issue #10이 `Ready`가 아님
- `codex/10-ipad-universal` branch 또는 worktree를 다른 작업이 사용 중임
- 기준 branch에 필요한 iOS 변경이 아직 미병합임
- 기존 dirty worktree를 건드리지 않고 변경을 격리할 수 없음
- App Store 대상 기기나 Bundle ID 변경을 요구하는 새 범위가 발생함

