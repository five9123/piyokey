# PIYOKEY 로드맵

로드맵은 기능 목록이 아니라 작업 순서와 WIP 경계다. 상세 제품 계약은 `PRD.md`, 현재 사실은 `PROJECT_STATUS.md`를 따른다.

## Now — R1.1 기준선 안정화

기준선은 `origin/main` `c066638`이다. PR #74의 5언어·온보딩, PR #80의 iPad 화면, PR #72의 자유 연습 제거, PR #84의 저장소·화면 감사와 PR #82의 홈 추천 두 영역은 통합 완료다. 업로드된 iOS `1.1 (7)`은 최신 소스보다 오래되므로 최종 RC가 아니다. 상세 검증은 `PROJECT_STATUS.md`와 최신 SHA 증빙, 반복 체크리스트는 `docs/LANGUAGE_EXPANSION_CHECKLIST.md`를 따른다.

1. 연습 챕터 5·6 스테이지 확장 #85 / PR #86
   - iOS·Android 동일한 6챕터·12스테이지와 5언어 학습 콘텐츠·고정 음원 계약 적용
   - 최신 main 통합 뒤 양 플랫폼 관련 회귀를 재검증해 병합
2. 현지 20시 온보딩 리마인더 #58
   - 소스 구현과 iOS/Android 자동 회귀 완료
   - 실제 기기에서 권한 동의 뒤 현지 20시 알림 수신 확인 후 종료
3. iOS 1.1 출시 후보
   - PR #80·#72 및 최종 선택 기능을 포함한 새 build 번호로 archive/TestFlight 생성
   - StoreKit·파일 상호운용·1,000항목·스토어 자산 검증
   - 10개 로케일 스토어 미디어 로컬 제작 완료 → 현지어·최종 빌드 일치 검수 후 신규 로케일 필수 메타데이터와 미디어 업로드
4. Android 1.1 출시 후보
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
- 기본 dirty worktree recovery queue: `typee.app` 링크, 흔들림 애니메이션 수정, 스토어/Pro 자산을 기능별 새 issue·최신 main branch로 재적용
- 신규 플랫폼·추가 언어(이번 es/de/fr 확장 제외)·클라우드 동기화

Later 작업은 R1.1 출시 파일이나 공용 충돌 파일을 동시에 소유하지 않는다.

## 운영 제한

- 전체 동시 개발 최대 2개: release-critical 1개와 독립 탐색 1개.
- 플랫폼별 `In Progress` 최대 1개.
- non-draft PR은 검증 완료·병합 준비 상태만 허용.
- `PRD.md`, `DECISIONS.md`, `AGENTS.md`, localization, catalog·audio manifest는 단일 소유.
- 완료는 코드 작성이 아니라 검증 증빙, PR 병합, Issue/Project 종료까지 포함한다.
