# 다중 디바이스·AI agent 온보딩

이 문서는 같은 PIYOKEY 저장소를 여러 네트워크의 Mac, 개발 기기, 사람,
AI agent가 동시에 다룰 때의 표준 시작 절차다. GitHub가 소스와 작업 상태의
공유 기준이며, iCloud Drive·Dropbox·NAS로 작업 폴더 자체를 동기화하지 않는다.

## 1. 작업 공간 선택

- **다른 물리 디바이스**: 디바이스마다 독립 clone을 사용한다.
- **같은 Mac의 여러 agent**: agent마다 독립 Git worktree를 사용한다.
- **같은 worktree 공유**: 금지한다. 파일 변경과 index가 즉시 섞여 담당 경계가
  사라진다.

Android M7은 재개되어 M1 공용 코어까지 `main`에 반영됐다. 새 Android 작업은
반드시 최신 `main`의 `android/`에서 issue·전용 branch·독립 worktree를 만든 뒤
시작한다. 이전 `hanco` 작업 폴더를 계속 수정하거나 파일을 수동 복사하지 않는다.
웹은 승인된 issue가 생기기 전에는 빈 플랫폼 디렉터리를 선행 생성하지 않는다.

## 2. 새 디바이스 최초 설정

GitHub CLI에 로그인한 다음 비공개 저장소를 clone한다.

```bash
gh auth login
gh repo clone five9123-maker/piyokey
cd piyokey
git config pull.ff only
git config fetch.prune true
python3 tools/workspace_doctor.py
```

macOS 기본 `python3`가 3.11보다 낮다는 진단이 나오면 시스템 Python을
교체하지 않는다. Homebrew·pyenv 등으로 설치한 3.11 이상 실행 파일을
명시해 다시 실행한다(예: `python3.12 tools/workspace_doctor.py`).

iOS 작업을 할 Mac에서는 추가 점검을 실행한다.

```bash
python3 tools/workspace_doctor.py --scope ios
```

Android 작업 환경에서는 JDK 17 이상, Android API 37 SDK, Gradle wrapper와 M1
모듈을 추가 점검한다.

```bash
python3 tools/workspace_doctor.py --scope android
```

점검 도구는 읽기 전용이다. 설정이나 파일을 자동 수정하지 않으며 secret과
인증 토큰을 출력하지 않는다.

## 3. 작업을 먼저 예약하기

코드를 열기 전에 GitHub issue를 만들거나 기존 issue를 자신에게 할당한다.
issue에는 다음을 적는다.

- 완료 결과와 관련 PRD 절
- acceptance criteria와 검증 계획
- 주 담당 사람/agent와 branch 이름
- 수정할 상위 경로
- 단독 소유가 필요한 충돌 위험 파일
- 선행 issue 또는 stacked PR

`project.pbxproj`, `PRD.md`, `DECISIONS.md`, `AGENTS.md`, localization,
카탈로그 index, 생성 음원·manifest는 한 시점에 issue 하나만 소유한다.

## 4. 다른 디바이스에서 branch 만들기

```bash
git switch main
git pull --ff-only origin main
git switch -c codex/123-short-slug
git push -u origin codex/123-short-slug
```

branch를 일찍 push하면 다른 디바이스와 agent가 소유권을 확인할 수 있다.
첫 검토 가능한 변경이 생기면 Draft PR을 열고 issue를 연결한다.

## 5. 같은 Mac에서 agent별 worktree 만들기

기준 clone에서 다음처럼 독립 작업 공간을 만든다.

```bash
git fetch --prune origin
git worktree add ../piyokey-123 -b codex/123-short-slug origin/main
cd ../piyokey-123
python3 tools/workspace_doctor.py
```

worktree 경로와 branch를 issue에 기록한다. agent를 종료한 뒤에도 미병합 변경이
있으면 worktree를 삭제하지 말고 Draft PR에 handoff를 남긴다.

## 6. 작업 중 동기화

원격 변경을 가져올 때 다른 branch의 파일을 수동 복사하지 않는다.

```bash
git fetch --prune origin
git merge --no-edit origin/main
```

원격에 공개한 branch에는 임의 rebase나 force push를 하지 않는다. 정리 목적의
rebase가 꼭 필요한 경우 branch 소유자와 합의하고 `--force-with-lease`만
사용한다.
충돌 위험 파일을 다른 PR이 먼저 변경했다면 그 PR을 병합한 뒤 자신의 branch를
갱신한다.

## 7. AI agent handoff

agent 세션을 끝낼 때 issue 또는 Draft PR에 다음 형식으로 남긴다.

```text
상태: 진행 중 | review 준비 | blocked
branch/worktree: codex/123-short-slug | ../piyokey-123
완료: 실제로 끝난 항목
다음: 바로 실행할 한 단계
검증: 실행한 명령과 결과
미검증: 아직 실행하지 않은 gate와 이유
주의: 충돌 가능 파일, 임시 결정, 외부 상태
```

다음 agent는 이 기록, `AGENTS.md`, 관련 PRD 절, 현재 diff를 읽은 뒤 작업한다.
채팅 기록만을 handoff의 단일 근거로 사용하지 않는다.

## 8. 병합 뒤 다른 디바이스 갱신

```bash
git switch main
git pull --ff-only origin main
git branch --merged main
python3 tools/workspace_doctor.py
```

GitHub의 병합된 `main`이 유일한 공유 기준이다. App Store Connect, TestFlight,
로컬 Xcode archive는 GitHub branch를 대신하지 않으며, 릴리스 시에만 정확한
commit SHA와 build 번호로 연결한다.
