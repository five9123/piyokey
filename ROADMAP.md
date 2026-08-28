# PIYOKEY 로드맵

로드맵은 기능 목록이 아니라 작업 순서와 WIP 경계다. 상세 제품 계약은 `PRD.md`, 현재 사실은 `PROJECT_STATUS.md`를 따른다.

## Now — R1.1 기준선 안정화

사용자 요청 #73 / PR #74: 관심사 뒤 4단계 레벨 확인과 첫 홈 추천→실제 학습 후 이어하기를 제공한다. 2026-08-29 사용자 진행 승인으로 로컬 5언어 후보 `codex/local-es-de-fr`를 이 작업에 통합하며, 원본 clone은 보존한다. 양 플랫폼 회귀·5언어 UI 검증·CI/리뷰 뒤 main 반영한다. 스토어·카탈로그 게시 gate는 별도다. 반복 체크리스트는 `docs/LANGUAGE_EXPANSION_CHECKLIST.md`를 따른다.

1. 현지 20시 온보딩 리마인더 #58
   - 소스 구현과 iOS/Android 자동 회귀 완료
   - 실제 기기에서 권한 동의 뒤 현지 20시 알림 수신 확인 후 종료
2. iOS 1.1 출시 후보
   - StoreKit·파일 상호운용·1,000항목·스토어 자산 검증
   - 10개 로케일 스토어 미디어 로컬 제작 완료 → 현지어·최종 빌드 일치 검수 후 신규 로케일 필수 메타데이터와 미디어 업로드
3. Android 1.1 출시 후보
   - Play Console·서명·권리·외부 리소스 확정
   - Issue #19 동일 signed AAB 통합 실기기 QA

## Next — 병합된 소스의 외부 gate 완결

- iPad Universal #10 / PR #18
- 세션 설정 크래시 #46 / PR #49
- 물리 키보드 학습 #17 / PR #50
- iOS 물리 키보드 gate #12 / PR #15
- Game Center gate #7

위 PR의 소스 병합은 PR #70에서 완료했다. 남은 실기기·출시 검증만 추적한다.

## Later — 출시 기준선을 막지 않는 탐색

- 자동 발음·게임 힌트 #8 및 관련 Draft PR
- 신규 플랫폼·추가 언어(이번 es/de/fr 확장 제외)·클라우드 동기화

Later 작업은 R1.1 출시 파일이나 공용 충돌 파일을 동시에 소유하지 않는다.

## 운영 제한

- 전체 동시 개발 최대 2개: release-critical 1개와 독립 탐색 1개.
- 플랫폼별 `In Progress` 최대 1개.
- non-draft PR은 검증 완료·병합 준비 상태만 허용.
- `PRD.md`, `DECISIONS.md`, `AGENTS.md`, localization, catalog·audio manifest는 단일 소유.
- 완료는 코드 작성이 아니라 검증 증빙, PR 병합, Issue/Project 종료까지 포함한다.
