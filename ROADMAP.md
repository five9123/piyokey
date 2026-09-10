# PIYOKEY 로드맵

로드맵은 기능 목록이 아니라 작업 순서와 WIP 경계다. 상세 제품 계약은 `PRD.md`, 현재 사실은 `PROJECT_STATUS.md`를 따른다.

## Now — 공개판 안정화와 Game Center 경험 통합

기준선은 `PROJECT_STATUS.md`의 최신 `origin/main`이다. 공개판 1.1과 심사 대기 중인 1.1.1, 아직 배포하지 않은 #184 소스를 구분한다. 시간에 따라 변하는 ASC·실기기 현황은 현황판과 각 Issue의 근거를 따른다.

1. #181/PR #183 핫픽스의 남은 계정·실기기·심사 gate 확인. 완료된 복구·Flow/Cup 격리·모든 입력 방식·v5 소스는 재사용한다.
2. #184의 게임별·난이도별 순위 접근, 주간 경쟁 요약, 결과·재도전, 주변/친구 기록, 성장 업적 연계 구현 → focused 검증 → clean SHA evidence → 현재 head Claude review → 리뷰 가능한 PR 준비.
3. 기존 외부 gate #77·#7: 계정·권리·IAP·정확한 서명 빌드의 실기기·성능·VoiceOver·오디오·스토어 검증을 소스 완료와 분리한다.
4. Challenges·Activities와 콘솔 es/de/fr 현지화는 `docs/GAME_CENTER_EXPERIENCE.md`의 구체적 결정안에 따라 별도 범위를 확정한다. 필수 #184 개발을 기다리게 하지 않는다.

## Next — 병합된 소스의 외부 gate 완결

- iPad Universal #10 / PR #18
- 세션 설정 크래시 #46 / PR #49
- 물리 키보드 학습 #17 / PR #50
- iOS 물리 키보드 gate #12 / PR #15
- Game Center gate #7

위 PR의 소스 병합은 PR #70에서 완료했다. 남은 실기기·출시 검증만 추적한다.

## Later — 출시 기준선을 막지 않는 탐색

- 자동 발음·게임 힌트 #8 및 관련 Draft PR
- 기본 dirty worktree recovery queue: `typee.app` 링크, 흔들림 애니메이션 수정, 스토어/Pro 자산을 기능별 새 issue·최신 main branch로 재적용
- 신규 플랫폼·추가 언어(이번 es/de/fr 확장 제외)·클라우드 동기화
- Android 재개는 기존 포트 backlog가 아니라 별도 승인된 새 PRD·초기 설계로만 검토

Later 작업은 R1.1 출시 파일이나 공용 충돌 파일을 동시에 소유하지 않는다.

## 운영 제한

- 전체 동시 개발 최대 2개: release-critical 1개와 독립 탐색 1개.
- 플랫폼별 `In Progress` 최대 1개.
- 활성 네이티브 플랫폼은 iOS/iPadOS뿐이며 `android/`는 참고용 동결 상태다.
- non-draft PR은 검증 완료·병합 준비 상태만 허용.
- `PRD.md`, `DECISIONS.md`, `AGENTS.md`, localization, catalog·audio manifest는 단일 소유.
- 완료는 코드 작성이 아니라 검증 증빙, PR 병합, Issue/Project 종료까지 포함한다.
