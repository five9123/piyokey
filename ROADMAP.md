# PIYOKEY 로드맵

로드맵은 기능 목록이 아니라 작업 순서와 WIP 경계다. 상세 제품 계약은 `PRD.md`, 현재 사실은 `PROJECT_STATUS.md`를 따른다.

## Now — R1.1 기준선 안정화

기준선은 `PROJECT_STATUS.md`에 기록한 최신 `origin/main`이다. iPad·5개 UI 언어·schema v2·Pro 덱 언어 retag와 Actions 공급망 보안은 통합 완료다. TYP-85 인앱 천지인 플릭은 `main`에 병합됐다. build 18은 분석 설정 미포함과 Search/연습 UI 결함으로 대체하며, TYP-43 source gate와 병합 뒤 clean `origin/main`에서만 build 19를 다음 후보로 준비한다. 상세 검증은 `PROJECT_STATUS.md`와 최신 SHA 증빙, 반복 체크리스트는 `docs/LANGUAGE_EXPANSION_CHECKLIST.md`를 따른다.

1. iOS 1.1 출시 후보
   - TYP-43의 Search 카드·연습 목록·분석 설정 source 검증, exact-head review와 maintainer 승인·병합
   - 최신 clean `origin/main`과 tree가 같은 `1.1 (19)` archive/TestFlight 생성. build 18은 후보에서 제외
   - StoreKit·파일 상호운용·1,000항목·스토어 자산 검증
   - 10개 로케일 스토어 미디어 로컬 제작 완료 → 현지어·최종 빌드 일치 검수 후 신규 로케일 필수 메타데이터와 미디어 업로드
2. iOS 1.1 외부 출시 gate #77
   - Account Holder 계약·은행·세금, 권리·개인정보, IAP와 정확한 build 19 실기기 QA
   - PostHog 동의 ON 수신·OFF 무전송과 Crashlytics 테스트 크래시·dSYM symbolication
   - TYP-85 전체 방향표·롱프레스·취소·동시 입력, p95≤50ms, 지원 게임 60fps와 VoiceOver의 iPhone·iPad 실기기 검증
   - strict preflight 통과 뒤에만 App Review 제출
3. CI 비용·검증 범위 최적화
   - docs-only 변경의 iOS 전체 build를 분리
   - iOS unit test를 정기·수동 release workflow로 보강

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
