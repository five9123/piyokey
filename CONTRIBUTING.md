# PIYOKEY 협업 가이드

이 저장소는 여러 기기와 네트워크에서 사람과 AI agent가 동시에 작업하는
것을 전제로 합니다. 충돌을 줄이는 가장 중요한 단위는 `issue 1개 = branch
1개 = 주 담당자/agent 1명`입니다.

처음 참여하는 디바이스나 agent는 먼저
[다중 디바이스·AI agent 온보딩](docs/DEVICE_SETUP.md)을 따르고
`python3 tools/workspace_doctor.py`를 실행합니다.

## 작업 흐름

1. GitHub issue에 목적, PRD 절, acceptance criteria, 영향 플랫폼을 적습니다.
2. `main`을 최신화하고 독립 clone 또는 worktree에서 branch를 만듭니다.
3. 작업 전에 `AGENTS.md`와 관련 PRD 절을 읽습니다.
4. 구현과 직접 관련된 테스트부터 실행합니다.
5. PR 템플릿을 채워 draft PR을 열고 CI가 통과하면 review를 요청합니다.
6. 승인과 필수 검사를 통과한 PR만 squash merge합니다.

Branch 이름은 기본적으로 다음 형식을 사용합니다.

```text
codex/<issue-number>-<short-slug>
feat/<issue-number>-<short-slug>
fix/<issue-number>-<short-slug>
```

Commit은 검토 가능한 한 가지 의도를 담고, 제목은 명령형으로 작성합니다.
생성 파일을 갱신했다면 생성기 변경과 생성 결과를 같은 PR에 포함합니다.

## 병렬 작업 규칙

- `project.pbxproj`, `PRD.md`, `DECISIONS.md`, 카탈로그 index, localization,
  생성 음원은 동시에 수정하지 않습니다. issue에서 소유권을 먼저 선언합니다.
- 다른 branch의 미완료 변경을 복사하지 않습니다. 필요한 선행 작업은 별도
  PR로 먼저 병합하거나 명시적인 stacked PR로 연결합니다.
- 한 agent가 세션을 마칠 때 issue/PR에 현재 상태, 다음 한 단계, 테스트,
  blocker를 남깁니다.
- branch는 작업 시작 직후 원격에 push하고 issue에 주 담당자, branch,
  수정 경로와 단독 소유 파일을 기록합니다.
- signing key, App Store Connect key, provisioning profile, 개인 `.env`는
  저장소나 agent 프롬프트에 넣지 않습니다.

## 테스트 범위

빠른 기본 검증:

```bash
python3 -m unittest discover -s tools/tests -p 'test_*.py'
python3 tools/release_preflight.py
(cd ios/HangulEngine && swift test)
```

일반 기능 수정은 관련 unit/UI suite만 실행하고, 이미 build가 있으면
`xcodebuild test-without-building`을 우선합니다. 전체 iOS unit/UI 회귀는
마일스톤 종료, release candidate, 공통 기반 대규모 변경에만 실행합니다.

`shared/` 계약을 수정했다면 현재 존재하는 모든 소비자 테스트를 실행합니다.
현재 Swift·Kotlin contract suite가 필수이며, 웹이 추가되면 TypeScript suite도
같은 필수 검증에 포함합니다.

## 완료 조건

- 관련 PRD acceptance criteria를 충족한다.
- 사용자 노출 문자열과 접근성 규칙을 지킨다.
- 관련 테스트가 통과하고 미실행 테스트가 PR에 기록된다.
- 모호한 결정을 했다면 `DECISIONS.md`가 갱신된다.
- generated artifact나 비밀 정보가 diff에 포함되지 않는다.
