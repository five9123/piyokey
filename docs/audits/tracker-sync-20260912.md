# 2026-09-12 Linear·Orca 상태 대조

담당: Codex / TYP-117 / GitHub #186. 기준 origin/main: `602e923e3eddcb32b7ef75c62e3921f28f7a3e33`.

## 확인 출처와 범위

- GitHub: 저장소 PR/Issue 전체 조회, #175/#180/#181/#184 및 PR #185의 최신 본문·댓글.
- Linear: TYP 팀 59개 이슈 전수 조회(잘림 없음), 연결 누락 3건을 추가한 뒤 재조회.
- Orca: hanco 카드 42개 전수 조회(잘림 없음), worktree 실제 branch/head/dirty 여부와 terminal 상태.
- 공개 App Store: Apple lookup JP/US/KR, 2026-09-12 직접 조회. 모두 1.1.1,
  `currentVersionReleaseDate = 2026-09-11T00:33:05Z`.
- 이전 서명·제출: #181의 2026-09-11 댓글과 로컬 `release-status.json`을 대조.
  main 602e923, 1.1.1 (25), ASC build `cb235a28-982c-43e8-851d-f22ca96b704b`.
  공개 lookup은 build 번호나 최신 ASC 상태를 제공하지 않는다.

[일본 조회](https://itunes.apple.com/lookup?id=6794853985&country=jp),
[미국 조회](https://itunes.apple.com/lookup?id=6794853985&country=us),
[한국 조회](https://itunes.apple.com/lookup?id=6794853985&country=kr).

## 반영 내용

| 기존 불일치 | 정리 |
|---|---|
| Linear TYP-120 Todo / PR #183 이미 병합 | In Review. 1.1.1 공개와 실기기 상세 검증을 분리 |
| GitHub #180/#184/#175의 Linear 연결 누락 | TYP-121/#180, TYP-122/#184, TYP-123/#175 생성. 모두 기존 작업의 검증·리뷰 연결이며 새 구현 요청 없음 |
| Linear Done/Canceled / Orca 진행·리뷰 중 | Orca 종료 상태와 사유 동기화. Canceled는 취소/대체를 명시 |
| Buzz TYP-112/113/95와 일부 Codex 카드의 연결 누락 | 기존 작업공간에 Linear/GitHub 연결 추가 |
| Orca TYP-85/89의 GitHub 숫자 링크가 다른 이슈를 가리킴 | 잘못된 숫자 링크만 제거, 정확한 Linear 링크 보존 |
| 기본 폴더를 main으로 표시 | 실제 feat/orca-task-name과 뒤처짐 표시. 고유 문서 커밋 보존 |
| TYP-43·QA 목록의 구형 후보/심사 대기 기록 | 1.1.1 공개 관찰·최근 build 25 제출 기록과 잔여 검증으로 정리 |
| 현황 문서·로드맵·release JSON의 build 11~24 또는 제출 전 상태 | 확인된 병합·서명·공개 사실을 반영, 미확인 외부 필드는 이월 자료임을 명시 |

Orca `completed` 열에는 완료와 취소된 작업이 함께 들어가되 카드에 Linear 상태·취소
사유를 명시한다. 실제 완료 의미는 Linear Done/Canceled에서 구분한다.

## 의도적으로 유지한 상태

- TYP-78: 과거 PM 댓글에서 확인한 일시정지/Todo, exec:orca 제거 유지. 미디어 재생성 미실행.
- TYP-116: 사용자 중지/Backlog, 별도 Mac 데모와 분리. Mac 계획·일정 재확정 미실행.
- 상세 기기 테스트 미확인 항목은 In Review/Todo 유지. 기존 Done을 다시 열지 않음.
- GitHub #7/#58/#172는 이미 종료돼 재처리하지 않음. Android는 TYP-119 동결 backlog.
- Orca reviewer/dispatcher 활성, watchdog 비활성. 스케줄·모델·prompt 수정 없음.
- 기본 폴더 고유 커밋, #181의 dirty PROJECT_STATUS.md, 미병합 PR과 임시·중지 작업공간,
  경로가 사라진 과거 검토 등록, 터미널을 보존. 삭제·강제 branch 교체 없음.

## 검증·한계

이번 변경은 제품 코드나 앱 바이너리를 수정하지 않는다. Git diff 검사, Python 도구·
콘텐츠/release 계약, repository preflight 및 상태 재조회를 수행하고 SHA evidence에
실제 결과를 기록한다. strict release readiness는 미확인 QA·계정·권리·IAP gate 때문에
통과로 보고하지 않는다. 소스 리뷰·maintainer 승인과 실기기 검증은 별도다.
