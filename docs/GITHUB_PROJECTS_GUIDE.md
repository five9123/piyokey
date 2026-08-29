# PIYOKEY GitHub Projects 운영 가이드

[PIYOKEY Development](https://github.com/users/five9123-maker/projects/1)는 사용자와
개발 agent가 공유하는 실행 상태의 원본이다. 기능·acceptance criteria는
`PRD.md`, 확정 결정은 `DECISIONS.md`, 현재 출시 기준선은 `PROJECT_STATUS.md`를
사용한다. 대화·Slack·이메일은 요청 출처이며 완료 상태의 원본이 아니다.

## 추적 대상

다음 작업은 시작 전에 Issue와 Project 항목을 만든다.

- 코드, 테스트, schema, fixture, 콘텐츠 또는 문서 변경
- 앱·스토어·Game Center·CI·릴리스 설정 변경
- 버그 수정, 기능 구현, QA와 실기기·외부 gate
- 검토한 사용자 요청을 실제 후속 작업으로 수용한 경우

답변만 필요한 질문이나 읽기 전용 상태 확인은 Issue 없이 처리할 수 있다. 조사
결과 변경이 필요해지면 구현 전에 Issue로 전환한다.

## Project 필드

### Status

| 상태 | 의미 | 다음 이동 조건 |
|---|---|---|
| `Inbox` | 원문만 수집된 요청 | 중복·출처·사용자 문제 확인 |
| `Needs Decision` | 범위·UX·우선순위 결정 필요 | 사용자 결정과 AC 기록 |
| `Ready` | 구현 가능한 계약 완성 | 담당자와 branch 확정 |
| `In Progress` | 구현·자동 검증 진행 | PR과 검증 증빙 준비 |
| `Verify` | 사용자·실기기·수동 gate 대기 | 필요한 확인 완료 |
| `Blocked` | 외부 조건 때문에 진행 불가 | 명시된 해제 조건 충족 |
| `Done` | PR 병합과 필수 검증 완료 | 종료 상태 |

`Needs Decision`과 `Verify`는 사용자 소통 gate다. Agent가 제품 범위, 법적·계정
선언 또는 실기기 gate를 스스로 승인해 다음 상태로 넘기지 않는다.

### Priority

| 우선순위 | 기준 |
|---|---|
| `P0` | 데이터 손실, 보안, 출시 중단, 앱 사용 불가 |
| `P1` | 현재 마일스톤·출시의 필수 기능이나 문서 |
| `P2` | 계획된 일반 기능·품질·비용 개선 |
| `P3` | 장기 정리·아이디어 |

- `Work Type`: `Bug`, `Feature`, `QA`, `Release Gate`, `Decision`, `Maintenance`
- `Area`: `iOS`, `Android`, `Shared`, `Content`, `Tools/CI`, `Release/Ops`, `Docs`
- `Target`: 현재 릴리스·마일스톤 또는 `Later`

상태를 Label로 중복 관리하지 않는다. Label은 검색·자동화용 분류에만 사용한다.

## Issue 준비 완료 조건

`Ready`로 옮기기 전에 다음을 기록한다.

- 사용자 또는 유지보수 관점의 목표 결과
- 요청 출처와 관련 PRD 절
- 검증 가능한 acceptance criteria와 제외 범위
- 영향 플랫폼과 공용 계약
- 자동 테스트, 수동 시나리오, 실기기·스토어 gate의 구분
- 선행 Issue와 충돌 가능 파일

선택에 따라 사용자 결과가 달라지면 `Needs Decision`에서 먼저 확인한다.

## 표준 작업 흐름

1. Issue를 만들고 Priority, Work Type, Area, Target과 초기 Status를 설정한다.
2. 계약이 완성되면 `Ready`로 이동한다.
3. Issue 하나에 담당자 하나, branch 하나, worktree 하나를 배정한다.
4. `tools/worktree_owner.py claim`과 `tools/workspace_doctor.py --strict`를 통과하고
   `In Progress`로 이동한다.
5. 관련 테스트와 preflight를 실행하고 PR에 실행 결과·미실행 gate를 기록한다.
6. PR head의 pending·in-progress check가 없어지고 적용되는 모든 check가 성공할
   때까지 병합하지 않는다. 실패·취소·타임아웃·설명 없는 skip은 차단한다.
7. head가 바뀌지 않았음을 확인한 뒤 squash merge한다.
8. Issue를 닫고 Project를 `Done`으로 바꾼다. 외부 gate가 남으면 Issue는
   `Verify` 또는 `Blocked`로 유지한다.

private 저장소의 현재 요금제에서는 branch protection/ruleset을 사용할 수 없어
6~7단계를 수동 fail-closed gate로 적용한다. GitHub Pro나 공개 전환은 Account
Owner 승인 없이 수행하지 않는다. 자세한 계약은 `docs/WORKFLOW.md`와
`docs/REPOSITORY_POLICY.md`를 따른다.

## Handoff 댓글

중단하거나 담당자를 바꿀 때 Issue 또는 Draft PR에 다음을 남긴다.

```text
상태: 진행 중 | review 준비 | blocked
완료: 실제로 끝난 항목
다음: 바로 실행할 한 단계
검증: 실행한 명령과 결과
미검증: 아직 실행하지 않은 gate와 이유
주의: 충돌 가능 파일, 임시 결정, 외부 상태
```

## 권장 뷰

- `Current`: `Ready`, `In Progress`, `Verify`, `Blocked`
- `Inbox`: `Inbox`, `Needs Decision`
- `Release`: `Target`별 그룹, Priority 정렬
- `Bugs`: 열린 `Bug` 항목, Priority 정렬

## 완료 정의

- Issue AC 충족
- 관련 로컬·GitHub 자동 테스트 성공
- 미실행 수동 gate와 이유 공개
- 필요한 사용자 확인과 PRD 밖 결정 반영
- PR 병합 및 Issue 종료

코드가 작성됐다는 사실만으로 `Done` 처리하지 않는다. 실기기·스토어·계정 gate가
남아 있으면 `Verify` 또는 `Blocked`를 유지한다.
