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
않는다. GitHub Actions도 저장소 수준에서 비활성 상태이며 기존 workflow 정의는 향후
재사용을 위해 보존만 한다. Actions check의 부재나 과거 성공은 현재 PR의 통과 신호가
아니다. 기술적 보호를 사용할 수 있을 때까지 다음 기본 수동 fail-closed gate를 모든
PR에 적용한다.

1. `main`에 직접 push하거나 force push하지 않는다.
2. Claude 리뷰가 현재 PR head의 전체 diff를 검토해 `review:passed`를 기록해야 한다.
   `review:passed` 뒤 commit이 추가되거나 head가 바뀌면 이전 결과는 무효이며 새
   exact head를 다시 검토한다.
3. 담당자는 변경 영향에 맞춘 focused 로컬 검증을 clean implementation commit에서
   실제로 실행한다. 명령·결과·검증 SHA·미실행 gate를
   `release/evidence/<검증 SHA>.json`에 기록하고, evidence-only commit과 PR 본문에서
   검증 대상 SHA를 연결한다. 알려진 실패나 설명 없는 미실행 항목은 병합을 막는다.
4. 병합 직전 `git fetch --prune origin`으로 최신 기준선을 가져와 `origin/main`을
   작업 branch에 병합한다. 최신 `origin/main`이 head의 ancestor인지 확인하고 그
   merged tree에서 영향 검증을 통과시킨다. 기준선이 다시 전진하면 병합·검증·리뷰를
   새 head로 반복한다.
5. 해결되지 않은 review finding과 열린 source gate가 없음을 maintainer가 확인하고
   PR에 수동 fail-closed 승인을 기록한 뒤 squash merge한다.

외부에서 별도로 실행된 check가 있으면 pending·failure·cancelled·timed out·설명 없는
skipped 결과를 무시하거나 로컬 evidence로 덮지 않는다. 과거 PR의 승인·리뷰·evidence를
재사용하지 않으며, 예외는 해당 PR에 기록된 사용자의 명시적 1회 승인으로만 허용한다.
TYP-77 PR #147의 Actions-OFF 예외 기록은 이 기본 정책 이전의 역사적 승인이고 다른
PR의 승인이 아니다.

리뷰와 소스 검증은 실기기, 계정, 콘텐츠 권리, IAP, 스토어 심사·콘솔, 외부 서비스,
archive·distribution signing gate를 면제하지 않는다. Release와 store 제출은 일반
개발 권한과 분리된 별도 책임자가 수행한다. GitHub Pro가 승인되면 이 계약을 required
status checks, conversation 해결, force push와 삭제 금지, PR 전용 변경 규칙으로
기술적으로 강제한다.

### 로컬 검증 적용 범위

Actions 비활성 기간에는 변경 경로별 workflow 대신 아래 focused 로컬 검증을
evidence에 기록한다. 표는 최소 범위이며 실제 diff가 소비자 계약을 넓히면 검증도
넓힌다.

| 변경 경로 | 최소 focused 로컬 검증 |
|---|---|
| `docs/**`, `README.md`, `PROJECT_STATUS.md`, `ROADMAP.md`, `AGENTS.md`만 | `git diff --check`, 문서 review와 직접 참조 정책 일관성 |
| `release/**` | Python tools/content/release contracts |
| `ios/Hanco/**` | Python contracts + iOS simulator build |
| `ios/HangulEngine/**` | Python contracts + Swift contracts + iOS simulator build |
| `android/**` | 없음; Android 포트는 참고용 동결 |
| `shared/**` | Python + Swift + iOS |
| `.github/workflows/**` | Python contracts + 수정한 플랫폼 workflow 정의의 로컬 정적 검증; Actions를 켜지 않음 |

비활성 상태에서는 주 1회 `Scheduled iOS regression`과 `workflow_dispatch`도
실행되지 않는다. release candidate 승인 전 정확한 최신 `origin/main` tree에서 해당
iOS unit regression을 로컬로 실행해 SHA evidence를 남긴다. Dependabot PR도 자동
병합하지 않고 위 범위와 동일한 exact-head review·로컬 evidence·merged-tree·수동
승인 gate를 적용한다.

실기기 설치, TestFlight·Play 배포, archive 또는 distribution bundle 생성 전에는
배포하는 파일 트리가 원격 기준선과 같은지 fail-closed로 확인합니다.

```bash
git fetch --prune origin
python3 tools/workspace_doctor.py --strict --require-origin-main
```

이 검사는 commit SHA가 아니라 저장소 전체 파일 tree를 비교하므로, merge commit처럼
SHA가 달라도 전체 tree가 같은 경우만 허용합니다. 증빙·문서만 추가했더라도
`origin/main`에 병합되기 전에는 실패하며, 실패한 작업공간에서는 배포하지 않습니다.

## 개정 기록

- 2026-09-01, TYP-79: GitHub Actions 비활성 상태의 한시 예외를 기본
  exact-head review·focused local evidence·최신 `origin/main` merged-tree 검증·수동
  fail-closed 승인 정책으로 전환했다. PR #147의 1회 예외 기록은 역사적 근거로만
  유지하며 실기기·계정·권리·IAP·스토어·서명 gate는 변경하지 않았다.
