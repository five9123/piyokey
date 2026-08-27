# PIYOKEY 로드맵

로드맵은 기능 목록이 아니라 작업 순서와 WIP 경계다. 상세 제품 계약은 `PRD.md`, 현재 사실은 `PROJECT_STATUS.md`를 따른다.

## Now — R1.1 기준선 안정화

1. 프로젝트 운영 재정비 #59
   - 작업공간 소유·dirty 진단
   - SHA 기반 로컬 검증 증빙
   - Issue·PR·Project 상태 정합성
2. 현지 20시 온보딩 리마인더 #58
   - 최신 main 반영
   - iOS/Android 회귀와 실제 기기 수동 gate 분리
3. iOS 1.1 출시 후보
   - StoreKit·파일 상호운용·1,000항목·스토어 자산 검증
4. Android 1.1 출시 후보
   - Play Console·서명·권리·외부 리소스 확정
   - Issue #19 동일 signed AAB 통합 실기기 QA

## Next — 현재 Draft/Verify 작업 완결

- iPad Universal #10 / PR #18
- 세션 설정 크래시 #46 / PR #49
- 물리 키보드 학습 #17 / PR #50
- iOS 물리 키보드 gate #12 / PR #15
- Game Center gate #7

stacked PR은 `#18 → #49 → #50` 순서로만 갱신·검증·병합한다.

## Later — 출시 기준선을 막지 않는 탐색

- 자동 발음·게임 힌트 #8 및 관련 Draft PR
- 신규 플랫폼·언어·클라우드 동기화

Later 작업은 R1.1 출시 파일이나 공용 충돌 파일을 동시에 소유하지 않는다.

## 운영 제한

- 전체 동시 개발 최대 2개: release-critical 1개와 독립 탐색 1개.
- 플랫폼별 `In Progress` 최대 1개.
- non-draft PR은 검증 완료·병합 준비 상태만 허용.
- `PRD.md`, `DECISIONS.md`, `AGENTS.md`, localization, catalog·audio manifest는 단일 소유.
- 완료는 코드 작성이 아니라 검증 증빙, PR 병합, Issue/Project 종료까지 포함한다.
