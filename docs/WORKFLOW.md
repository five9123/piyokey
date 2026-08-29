# 사람·AI agent 작업 운영

## 권장 단위

작업을 0.5~2일 안에 review 가능한 issue로 나눕니다. issue에는 최소한 다음을
적습니다.

- 사용자 또는 유지보수 관점의 결과
- 관련 PRD 절과 acceptance criteria
- 수정 예상 경로와 영향 플랫폼
- 필요한 테스트와 수동 gate
- 다른 issue와의 선행 관계

모든 작업자는 자기 clone 또는 worktree와 자기 branch를 사용합니다. 같은
worktree를 두 agent가 동시에 수정하지 않습니다.

branch를 만든 직후 소유권을 Git metadata에 기록하고 strict 진단을 통과시킵니다.

```bash
python3 tools/worktree_owner.py claim --issue 123 --owner '담당자 또는 agent task ID'
python3 tools/workspace_doctor.py --strict
```

소유권 파일은 commit 대상이 아닙니다. handoff 시 새 담당자가 `--force`로
교체하고 Issue 또는 Draft PR에도 담당 변경을 남깁니다.

PR 병합 뒤에는 해당 worktree를 clean 최신 `main`으로 전환한 다음 소유권을
해제합니다. `unclaim`은 main, clean, `origin/main` 동일 tree 세 조건을 모두
확인하므로 진행 중 변경을 실수로 무주 상태로 만들지 않습니다.

```bash
git fetch --prune origin
git switch main
git pull --ff-only origin main
python3 tools/worktree_owner.py unclaim
```

새 디바이스와 worktree 생성 명령은 [DEVICE_SETUP.md](DEVICE_SETUP.md)를
기준으로 합니다. 작업 시작 직후 branch를 원격에 push해 다른 작업자가
소유권을 확인할 수 있게 합니다.

## Handoff

작업을 넘길 때 issue 또는 draft PR에 아래 내용을 남깁니다.

```text
상태: 진행 중 | review 준비 | blocked
완료: 실제로 끝난 항목
다음: 바로 실행할 한 단계
검증: 실행한 명령과 결과
미검증: 아직 실행하지 않은 gate와 이유
주의: 충돌 가능 파일, 임시 결정, 외부 상태
```

코드만 있고 이 기록이 없으면 handoff가 완료된 것으로 보지 않습니다.

로컬 검증 결과는 검증한 commit에 묶어 `release/evidence/<SHA>.json`으로
기록합니다. 먼저 검증 대상 변경을 commit하고 clean 상태에서 다음처럼
생성합니다.

```bash
python3 tools/verification_evidence.py \
  --scope 'Issue #123 관련 회귀' \
  --check 'unit::pass::실제로 실행한 명령' \
  --manual-gate '실기기 확인은 Issue #123 Verify에서 수행'
```

생성 파일은 별도 commit으로 추가하고 PR 본문에서 검증 대상 SHA와 파일을
연결합니다. 이 기록은 명령을 대신 실행하지 않으며 거짓 `pass` 입력을
검출하는 장치가 아니므로, 실제 출력과 수동 gate를 PR에서도 확인합니다.

다른 디바이스에서 작업을 재개할 때는 채팅 요약만 믿지 않고 issue/PR handoff,
`AGENTS.md`, 관련 PRD 절, 원격 branch의 최신 diff를 함께 확인합니다.

## 충돌이 잦은 파일

다음 파일은 issue 담당자를 한 명만 지정하고 순차 병합합니다.

- `ios/Hanco/Hanco.xcodeproj/project.pbxproj`
- `PRD.md`, `DECISIONS.md`, `AGENTS.md`
- `shared/mock_catalog/catalog.json`과 update index
- localization string 파일
- 생성 음원과 이를 참조하는 manifest

공용 계약 PR은 작게 유지하고 먼저 병합합니다. 플랫폼별 적용은 그 뒤 독립
PR로 병렬화할 수 있습니다.

## GitHub 보호 규칙

저장소는 private이며 현재 GitHub 요금제에서는 branch protection과 ruleset API가
제공되지 않는다. GitHub Pro 도입 또는 공개 전환을 사용자 승인 없이 수행하지
않는다. 기술적 보호를 사용할 수 있을 때까지 다음 수동 fail-closed gate를 모든
PR에 적용한다.

1. `main`에 직접 push하거나 force push하지 않는다.
2. PR에 표시된 check가 pending 또는 in progress인 동안 병합하지 않는다.
3. 적용되는 모든 check가 성공해야 하며 failure·cancelled·timed out·설명 없는
   skipped 결과는 병합을 막는다.
4. 성공 확인 뒤 head commit이 바뀌면 새 결과를 다시 기다린다.
5. 조건을 확인한 뒤 squash merge한다.

예외는 해당 PR에 기록된 사용자의 명시적 승인이 있을 때만 한 번 적용한다. 과거
예외를 재사용하지 않고 실기기·스토어·계정·서명 gate를 면제하지 않는다. GitHub
Pro가 승인되면 이 계약을 required status checks, conversation 해결, force push와
삭제 금지, PR 전용 변경 규칙으로 기술적으로 강제한다. Release와 store 제출은
일반 개발 권한과 분리된 별도 책임자가 수행한다.

### CI 적용 범위

PR check는 변경 경로에 따라 다음처럼 선택된다. 경로 필터로 표시되지 않은 workflow는
성공으로 간주하는 check가 아니라 해당 PR에 적용되지 않는 check다.

| 변경 경로 | 적용 workflow |
|---|---|
| `docs/**`, `README.md`, `PROJECT_STATUS.md`, `ROADMAP.md`만 | 없음; 문서 review와 수동 병합 gate |
| `release/**` | Python tools/content/release contracts |
| `ios/Hanco/**` | Python contracts + iOS simulator build |
| `ios/HangulEngine/**` | Python contracts + Swift contracts + iOS simulator build |
| `android/**` | 없음; Android 포트는 참고용 동결 |
| `shared/**` | Python + Swift + iOS |
| `.github/workflows/**` | Python contracts + 수정한 플랫폼 workflow 자체 |

주 1회 `Scheduled iOS regression`은 iOS unit test를 실행하며
`workflow_dispatch`로도 시작할 수 있다. 정기 회귀가 실패하거나 완료되지
않으면 release candidate를 승인하지 않는다. Dependabot PR도 자동 병합하지 않고
위 경로 범위와 동일한 fail-closed 판정을 적용한다.

실기기 설치, TestFlight·Play 배포, archive 또는 distribution bundle 생성 전에는
배포하는 파일 트리가 원격 기준선과 같은지 fail-closed로 확인합니다.

```bash
git fetch --prune origin
python3 tools/workspace_doctor.py --strict --require-origin-main
```

이 검사는 commit SHA가 아니라 저장소 전체 파일 tree를 비교하므로, merge commit처럼
SHA가 달라도 전체 tree가 같은 경우만 허용합니다. 증빙·문서만 추가했더라도
`origin/main`에 병합되기 전에는 실패하며, 실패한 작업공간에서는 배포하지 않습니다.
