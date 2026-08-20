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
