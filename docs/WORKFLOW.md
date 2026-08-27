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

`main` 직접 push와 force push를 금지합니다. PR, 승인, conversation 해결,
필수 CI 통과를 요구하고 merge queue 또는 최신 main 반영 정책을 사용합니다.
Release와 store 제출은 일반 개발 권한과 분리된 별도 책임자가 수행합니다.

실기기 설치, TestFlight·Play 배포, archive 또는 distribution bundle 생성 전에는
배포하는 파일 트리가 원격 기준선과 같은지 fail-closed로 확인합니다.

```bash
git fetch --prune origin
python3 tools/workspace_doctor.py --strict --require-origin-main
```

이 검사는 commit SHA가 아니라 파일 tree를 비교하므로, 검증 증빙만 추가한 후속
commit처럼 제품 소스가 같은 경우는 허용합니다. 실패하면 해당 작업공간에서
배포하지 않습니다.
