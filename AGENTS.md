# PIYOKEY / ピヨキー — 한국어 타이핑 앱

## 먼저 읽을 문서

- `PRD.md`: 제품 기능과 acceptance criteria의 단일 기준.
- `PROJECT_STATUS.md`: 현재 main, 활성 작업, 출시 gate의 단일 현황판.
- `ROADMAP.md`: 지금 할 일과 Later 범위.
- `DECISIONS.md`: 확정된 제품·기술 결정과 근거.
- `docs/WORKFLOW.md`, `docs/DEVICE_SETUP.md`: Issue·branch·worktree·handoff 절차.
- `shared/test_vectors.json`: 한글 조합 공용 테스트 벡터. 직접 수정하지 말고 `tools/gen_test_vectors.py`로 생성한다.

작업 전 관련 PRD 절과 `PROJECT_STATUS.md`를 읽는다. 완료 후 상태 변화는 `PROJECT_STATUS.md`와 GitHub Issue/Project에 반영하고, 시간에 따라 변하는 진행 상황을 이 파일에 누적하지 않는다.

## 작업 소유와 순서

1. Issue 1개, 담당자 1명, branch 1개, worktree 1개, PR 1개를 사용한다.
2. 같은 worktree를 여러 작업이 공유하지 않는다. 시작 시 `python3 tools/worktree_owner.py claim --issue <번호> --owner '<담당자>'`로 소유권을 기록하고 `python3 tools/workspace_doctor.py --strict`를 실행한다.
3. `PRD.md`, `DECISIONS.md`, `AGENTS.md`, localization, catalog index, 생성 음원·manifest는 한 시점에 작업 하나만 소유한다.
4. M1 조합 엔진·덱 → M2 키보드·연습 → M3 발견·다운로드 → M4 게임 → M5 리텐션 → M6 폴리싱·OS 키보드 → M7 Android 순서를 존중한다.
5. 외부 스토어·실기기·권리 gate는 소스 완료와 구분하며, 통과 전에는 출시 완료로 표현하지 않는다.

`main`은 공유 기준선이므로 Issue 소유권을 두지 않는다. 작업 병합 후 clean `main`이 `origin/main`과 같은 때에만 `python3 tools/worktree_owner.py unclaim`으로 해당 worktree의 작업 소유권을 해제한다.

## 프로젝트 구조

```text
shared/                  플랫폼 공용 스키마·fixture·콘텐츠
tools/                   생성기·검증·운영 도구
ios/Hanco/               SwiftUI iOS 앱
ios/HangulEngine/        순수 Swift 조합·DeckKit 패키지
android/                 Kotlin/Compose Android 앱
release/                 스토어 메타데이터와 출시 gate
docs/                    workflow·handoff·운영 문서
```

## 제품 철칙

1. 조합 엔진은 UI에 의존하지 않는 순수 함수 상태 기계다(PRD §6).
2. 판정은 완성 음절이 아니라 자모 시퀀스를 기준으로 한다.
3. 레슨·게임 세션 도중 모달·결제·파일 가져오기 같은 인터럽트를 띄우지 않는다.
4. 사용자 문자열은 ja/en/ko localization resource에 두고 하드코딩하지 않는다.
5. 효과음과 발음은 `.playback`과 `mixWithOthers` 계약을 유지하고 다른 앱 음악을 끊지 않는다.
6. 백그라운드 전환 시 타이머와 진행 중 발음을 제품 계약대로 정지한다.
7. 카탈로그는 읽기 전용 정적 JSON이며 계정·쓰기 API를 추가하지 않는다.
8. 게임은 파티클을 포함해 60fps 실기기 gate를 유지한다.
9. 앱 제공 고정 한국어 발음은 `gTTS==2.5.4`, `lang=ko`, `tld=com`, 보통 속도의 오프라인 MP3만 사용한다. 새 고정 문구는 선언 파일과 canonical `audio/ko_<SHA-256 앞 20자리>.mp3`를 추가하고 `python3 tools/gen_gtts_audio.py --prune` 및 `python3 tools/release_preflight.py`를 통과시킨다. 제공자 변경은 사용자 승인과 PRD·DECISIONS 갱신 없이는 금지한다.

## 핵심 명령

- 작업공간 점검: `python3 tools/workspace_doctor.py`
- 작업공간 소유권: `python3 tools/worktree_owner.py claim --issue <번호> --owner '<담당자>'`
- 작업 종료 소유권 해제: `python3 tools/worktree_owner.py unclaim`
- SHA 검증 증빙: `python3 tools/verification_evidence.py --scope '<범위>' --check '<이름>::pass::<명령>'`
- 배포 소스 점검: `python3 tools/workspace_doctor.py --strict --require-origin-main`
- 테스트 벡터 생성: `python3 tools/gen_test_vectors.py`
- 발음 생성·정리: `python3 tools/gen_gtts_audio.py --prune`
- 저장소 preflight: `python3 tools/release_preflight.py`
- Swift 공용 패키지: `cd ios/HangulEngine && swift test`
- iOS 앱: `xcodebuild test -project ios/Hanco/Hanco.xcodeproj -scheme Hanco -destination 'platform=iOS Simulator,name=iPhone 17,OS=26.5'`
- Android 공용 코어: `cd android && ./gradlew :core:hangul:jacocoTestCoverageVerification :core:deckkit:test :core:piyodeck:test`
- Android 앱 회귀: `cd android && ./gradlew :app:lintDebug :app:assembleDebug`

일반 변경은 직접 관련된 단위·UI 테스트만 실행한다. 공용 상태·저장 schema·조합 엔진 변경은 직접 소비하는 양 플랫폼까지 넓힌다. 전체 회귀는 마일스톤 종료나 release candidate에서 실행한다.
