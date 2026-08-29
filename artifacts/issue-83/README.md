# Issue #83 저장소·주요 화면 감사

- 감사 시각: 2026-08-29 JST
- 기준 원격: `five9123-maker/piyokey`
- 기준 소스: `origin/main` `2b25a7d9aaa9ec2df3a09a428f3cb6bdea7fdc3c`
- 캡처 기기: iPhone 17 Simulator / iOS 26.5
- 캡처 앱 소스: 기준 소스와 동일

## 결론

로컬 branch의 많은 “ahead” 커밋은 GitHub의 squash merge 때문에 `main`의 조상이 아니며, 그 자체로 누락을 뜻하지 않는다. 최근 통합 기준선에는 PR #74(5언어·온보딩), #80(iPad), #72(자유 연습 제거)가 포함된다. 감사 시점의 실제 열린 PR은 #82 하나이며 최신 `main`과 충돌한다.

기본 `/Users/jungminoh/Documents/hanco` worktree는 닫힌 Issue #63 branch 위에 44개 tracked 변경과 7개 untracked 파일이 남은 보존 작업공간이다. 현재 `main`보다 오래된 tree와 새 변경이 섞여 있으므로 release 소스나 일괄 병합 대상으로 취급하지 않는다. stash 1개(35파일, 1,214 insertions, 155 deletions)도 apply/drop하지 않았다.

| 항목 | 수량·상태 |
|---|---|
| worktree | 18개 |
| dirty worktree | 1개: `/Users/jungminoh/Documents/hanco` |
| clean worktree | 17개 |
| local branch | 30개 |
| origin branch | `origin/HEAD` 제외 11개 |
| stash | 1개 |
| 열린 PR | #82 하나, `CONFLICTING` |
| #82 변경 | main 대비 15파일, +422/-35 |

## 실제 미통합·후속 범위

- PR #82: 홈의 `나를 위한 추천`·`다음 단계` 두 행. 최신 main 충돌 해결과 회귀가 필요하다.
- 기본 dirty worktree: 챕터5/6 스테이지 확장, `typee.app` 지원 링크, 흔들림 애니메이션 수정, 스토어/Pro 문구·미디어 도구가 섞여 있다. 각각 새 issue/branch로 분리한다.
- iOS: 업로드된 `1.1 (7)`은 PR #80·#72보다 오래되므로 새 build 번호 RC가 필요하다.
- Android: Issue #19 동일 signed AAB 실기기 통합 QA 전까지 배포 완료가 아니다.
- 외부: Game Center #7, 리마인더 #58, Paid Apps/IAP/Sandbox/Files/iCloud/AirDrop, 운영 카탈로그·콘텐츠 권리 gate가 남아 있다.

이번 감사에서는 stash 적용/삭제, branch/worktree 삭제, dirty 파일 덮어쓰기, PR #82 병합을 하지 않았다.

## 화면 캡처

PRD S1~S10을 기준으로 24장을 새 빌드에서 캡처했다. 원본 PNG와 두 `xcresult` bundle은 `.gitignore`가 적용되는 이 디렉터리에 로컬 증빙으로 보존하고, 대화에도 24장을 직접 첨부했다.

| 번호 | 화면 | 번호 | 화면 |
|---|---|---|---|
| 01 | 온보딩 레벨 | 13 | 플로우 모드 |
| 02 | 홈·출석 | 14 | 단어의 비 |
| 03 | MY 피요·보상 | 15 | 초성 퀴즈 |
| 04 | 발견·검색 | 16 | 단어 맞추기 |
| 05 | 덱 상세 | 17 | 게임 결과 |
| 06 | 커리큘럼 맵 | 18 | 게임 공유 시트 |
| 07 | 커리큘럼 하단 | 19 | 내 덱 액션 |
| 08 | 커리큘럼 미션 | 20 | 설정 |
| 09 | 두벌식 긴 자모 연습 | 21 | `.typedeck` 가져오기 |
| 10 | 10키 연습 | 22 | 새 덱 편집기 |
| 11 | 연습 결과 | 23 | 덱 항목 편집기 |
| 12 | 게임 허브 | 24 | 데일리 연습 |

검증 결과는 선택 UI 테스트 10개, 총 0 failures다. 첫 bundle은 7개 테스트에서 19장, 추가 bundle은 3개 테스트에서 5장을 생성했다.
