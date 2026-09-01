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
4. M1 조합 엔진·덱 → M2 키보드·연습 → M3 발견·다운로드 → M4 게임 → M5 리텐션 → M6 폴리싱·OS 키보드 순서를 존중한다.
5. 외부 스토어·실기기·권리 gate는 소스 완료와 구분하며, 통과 전에는 출시 완료로 표현하지 않는다.

## 활성 플랫폼 경계

iOS/iPadOS만 현재 제품 개발·출시 범위다. `android/`는 과거 포트의 참고 자료로
동결하며 일반 기능, 수정, 의존성 갱신, CI, 출시 gate에 포함하지 않는다. Android를
다시 시작하려면 기존 포트의 연속 작업으로 처리하지 않고 사용자가 승인한 새 PRD,
로드맵, Issue와 초기 아키텍처 결정에서 시작한다. 그 전에는 `android/`를 삭제하거나
현행 공용 계약에 맞춰 유지보수하지 않는다.

`main`은 공유 기준선이므로 Issue 소유권을 두지 않는다. 작업 병합 후 clean `main`이 `origin/main`과 같은 때에만 `python3 tools/worktree_owner.py unclaim`으로 해당 worktree의 작업 소유권을 해제한다.

## 검토·병합 gate

GitHub Actions는 저장소 수준에서 비활성 상태다. 모든 PR은 다음 기본 수동
fail-closed gate를 통과한 뒤에만 squash merge한다.

1. `git fetch --prune origin` 뒤 최신 `origin/main`을 작업 branch에 병합한다.
2. 변경 영향에 맞춘 focused 로컬 검증을 그 merged tree의 clean implementation
   commit에서 실행하고, 실제 명령·결과·검증 SHA·미실행 gate를
   `release/evidence/<검증 SHA>.json`과 PR 본문에 연결한다.
3. merge와 evidence commit까지 포함한 현재 PR head를 Claude가 검토해
   `review:passed`를 기록해야 한다. reviewer는 evidence의 검증 SHA가 head의
   ancestor이고 evidence-only commit이 해당 JSON 외 파일을 바꾸지 않았는지
   확인한다. 리뷰 뒤 head가 바뀌면 새 head를 다시 검토한다.
4. 병합 직전 `origin/main`이 전진하지 않았는지 다시 확인한다. 전진했다면 1단계부터
   반복한다. 알려진 실패, 설명 없는 미실행 검증, 해결되지 않은 review finding이
   하나라도 있으면 병합하지 않는다. maintainer가 위 조건을 확인해 PR에 수동 승인을
   기록한 뒤에만 병합한다.

과거 PR의 승인이나 evidence는 재사용하지 않는다. 로컬 검증·리뷰·수동 병합 승인은
실기기, 계정, 콘텐츠 권리, IAP, 스토어, 외부 서비스와 release signing gate를
면제하지 않으며, 열린 gate가 있으면 출시 완료로 표현하지 않는다.

## 프로젝트 구조

```text
shared/                  플랫폼 공용 스키마·fixture·콘텐츠
tools/                   생성기·검증·운영 도구
ios/Hanco/               SwiftUI iOS 앱
ios/HangulEngine/        순수 Swift 조합·DeckKit 패키지
android/                 동결된 과거 Kotlin/Compose 포트(참고 전용)
release/                 스토어 메타데이터와 출시 gate
docs/                    workflow·handoff·운영 문서
```

## 제품 철칙

1. 조합 엔진은 UI에 의존하지 않는 순수 함수 상태 기계다(PRD §6).
2. 판정은 완성 음절이 아니라 자모 시퀀스를 기준으로 한다.
3. 레슨·게임 세션 도중 모달·결제·파일 가져오기 같은 인터럽트를 띄우지 않는다.
4. UI 문자열은 ja/en/es/de/fr localization resource에 두고 하드코딩하지 않는다. 한국어는 학습 대상이며, 기존 학습 문구·뜻·읽기·덱·발음과 콘텐츠의 ko 호환성은 UI 언어에서 분리해 보존한다.
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

일반 변경은 직접 관련된 단위·UI 테스트만 실행한다. 공용 상태·저장 schema·조합 엔진 변경은 활성 소비자인 Python reference, Swift와 웹 계약까지 넓힌다. 동결된 Android는 기본 검증 범위에 넣지 않는다. 전체 회귀는 마일스톤 종료나 release candidate에서 실행한다.
