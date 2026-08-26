# PIYOKEY GitHub Projects 운영 가이드

이 문서는 비개발자 사용자와 개발 agent가 같은 작업 상태를 보고 소통하기
위한 실행 규칙이다. 기능 동작과 acceptance criteria의 단일 원본은
`PRD.md`, PRD 밖 결정은 `DECISIONS.md`, 현재 실행 상태는 GitHub Project다.
Slack·이메일·대화는 요청의 출처이지 완료 상태의 원본이 아니다.

## 추적 대상

다음 작업은 시작 전에 반드시 GitHub Issue를 만든다.

- 코드, 테스트, schema, fixture, 콘텐츠 또는 문서 변경
- 앱·스토어·Game Center·CI·릴리스 설정 변경
- 버그 수정, 기능 구현, QA와 실기기 gate
- 사용자 요청을 검토한 뒤 실제 후속 작업으로 수용한 경우

답변만 필요한 질문, 읽기 전용 상태 확인, 수용되지 않은 아이디어는 Issue 없이
처리할 수 있다. 조사 결과 변경이 필요해지면 구현 전에 Issue로 전환한다.

## 문서 우선순위

충돌할 때는 다음 순서를 따른다.

1. `PRD.md`의 기능·AC
2. `DECISIONS.md`의 승인된 결정
3. GitHub Issue의 작업 범위·상태
4. Pull Request의 구현·검증 증빙
5. Slack·이메일·대화 원문

Issue에 PRD 전체를 복사하지 않는다. 관련 절을 링크하거나 식별하고 이번
작업에서 검증할 AC만 적는다.

## Project 필드

### Status

| 상태 | 의미 | 다음 이동 조건 |
| --- | --- | --- |
| `Inbox` | 원문만 수집된 요청 | 중복·출처·사용자 문제 확인 |
| `Needs Decision` | 범위·UX·우선순위에 사용자 결정 필요 | 사용자 결정과 AC 기록 |
| `Ready` | 구현 가능한 계약이 완성됨 | 담당자와 branch 확정 |
| `In Progress` | 구현·검증 진행 중 | 완료 증빙과 PR 준비 |
| `Verify` | 사용자 또는 수동 gate 확인 대기 | 승인과 필수 gate 완료 |
| `Blocked` | 외부 조건 때문에 진행 불가 | 이유와 해제 조건 충족 |
| `Done` | 구현·검증·필수 승인이 끝남 | 종료 상태 |

`Needs Decision`과 `Verify`는 사용자 소통 게이트다. Agent는 해당 상태로
이동하고 질문·증빙을 남길 수 있지만, 제품 범위·사용자 노출 동작·수동
실기기 gate를 스스로 승인해 다음 상태로 넘기지 않는다.

### Priority

| 우선순위 | 기준 |
| --- | --- |
| `P0` | 데이터 손실, 보안, 출시 중단, 앱 사용 불가 |
| `P1` | 현재 마일스톤이나 출시를 막는 버그·필수 기능 |
| `P2` | 다음 계획에 포함된 일반 기능·개선 |
| `P3` | 검토할 아이디어·장기 개선 |

모든 항목을 P0/P1로 두지 않는다. 우선순위 충돌은 사용자가 결정한다.

### Work Type / Area / Target

- `Work Type`: `Bug`, `Feature`, `QA`, `Release Gate`, `Decision`, `Maintenance`
- `Area`: `iOS`, `Android`, `Shared`, `Content`, `Tools/CI`, `Release/Ops`, `Docs`
- `Target`: 현재 릴리스·마일스톤 또는 `Later`

## Issue 준비 완료 조건

`Ready`로 옮기기 전에 다음 항목을 확인한다.

- 사용자 또는 유지보수 관점의 목표 결과
- 원문 링크와 재현 자료
- 관련 PRD 절 또는 변경이 필요한 PRD 제안
- 검증 가능한 acceptance criteria
- 영향 플랫폼과 제외 범위
- 자동 테스트, 수동 시나리오, 실기기 gate 구분
- 선행 Issue와 충돌 가능 파일

요구사항이 모호한 상태에서는 구현을 시작하지 않는다. 선택에 따라 결과가
달라지는 질문은 `Needs Decision`에서 사용자에게 먼저 확인한다.

## 표준 작업 흐름

1. 요청을 `Inbox` Issue로 수집하고 원문을 연결한다.
2. 사용자와 수용 여부, 우선순위, 범위, AC를 논의한다.
3. 계약이 완성되면 `Ready`로 이동한다.
4. 담당자 배정, `codex/<issue>-<slug>` branch 생성, `In Progress` 이동 후
   시작 댓글을 남긴다.
5. 관련 PRD를 읽고 최소 변경으로 구현하며 관련 테스트를 실행한다.
6. PR·테스트·미실행 gate·스크린샷/영상과 남은 위험을 연결하고 `Verify`로
   이동한다.
7. 사용자 승인과 필수 검증 뒤 PR을 병합하고 Issue를 닫아 `Done` 처리한다.

중단할 때는 Issue에 현재 상태, 완료한 일, 다음 한 단계, 테스트, blocker를
남긴다. 외부 조건이 필요하면 `Blocked`로 이동하고 누가 무엇을 해야 해제되는지
명확히 적는다.

## Agent 댓글 형식

시작할 때:

```markdown
작업 시작
- 범위: ...
- 관련 PRD: ...
- 검증 계획: ...
- 충돌 가능 파일: ...
```

검증을 요청할 때:

```markdown
검증 요청
- 구현 결과: ...
- PR: ...
- 통과한 테스트: ...
- 미실행 수동 gate: ...
- 확인할 사용자 동작: ...
```

차단됐을 때:

```markdown
Blocked
- 이유: ...
- 이미 확인한 내용: ...
- 해제 조건: ...
- 필요한 담당자/외부 작업: ...
```

## 권장 Project 뷰

- `Current`: `Ready`, `In Progress`, `Verify`, `Blocked`를 Status로 그룹화
- `Inbox`: `Inbox`, `Needs Decision`만 표시
- `Release`: `Target`별로 그룹화하고 Priority로 정렬
- `Bugs`: `Work Type`이 `Bug`인 열린 항목만 Priority 순으로 표시

상태와 필드를 이슈 Label로 중복 관리하지 않는다. Label은 `bug`, `feature`
같은 검색·자동화용 분류에만 사용하고 실행 상태는 Project 필드를 사용한다.

## 완료 정의

다음을 모두 만족해야 `Done`이다.

- Issue AC 충족
- 관련 자동 테스트 통과
- 미실행 수동 gate와 이유가 공개됨
- 사용자 노출 변경은 필요한 사용자 확인 완료
- PRD 밖 결정은 `DECISIONS.md` 반영
- PR이 병합되고 Issue가 닫힘

코드가 작성됐다는 사실만으로 완료 처리하지 않는다. 실기기·스토어·외부
계정 gate가 남아 있으면 `Verify` 또는 `Blocked`를 유지한다.
