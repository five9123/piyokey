# PIYOKEY 로드맵

상세 제품 계약은 `PRD.md`, 현재 사실과 확인 출처는 `PROJECT_STATUS.md`를 따른다.

## 2026-09-20 Mac 재개 우선순위

1. TYP-123 / #175 / PR #176: 니모가 기존 작업공간에서 최신 main 통합·문서 충돌 해소·Mac 빌드·공용 코드 회귀를 수행한다. 새 검증 SHA의 독립 Claude 리뷰와 maintainer gate를 거친다.
2. TYP-116: 통합 결과를 바탕으로 Catalyst 제품화 적합성, 공유 저장 경계, CloudKit/KVS·구매 연동의 미검증 항목과 후속 소요를 제시한다. 사용자 재개 지시로 이전 중지를 해제하며, 첫 단계와 병렬 구현하지 않는다.
3. TYP-115: 기존 학습·6개 게임·동기화·Universal Purchase 목표를 유지한다. 제품화 판단과 검증 결과가 나오면 실행 순서·일정을 갱신한다. 과거 출시일은 현재 확약이 아니다.

TYP-117 / PR #187은 병합·정리를 마쳤다. TYP-120 조사 자료는 보관했다.
아래 공개판·외부 gate 목록은 9월 12일 점검 기록으로 보존하며, 새 검증 완료를 뜻하지 않는다.

## Now — 공개 1.1.1 후속 검증

2026-09-12 JP/US/KR App Store 공개 버전은 1.1.1이다. 서명·제출 증빙은 main
`602e923` / 1.1.1 (25)에 연결된다. build 19·21·22·23·24를 다시 만들 다음 후보로
취급하지 않는다. 공개 관찰이 모든 수동 출시 gate의 완료를 뜻하지 않는다.

1. TYP-120 / #181: 병합된 Game Center 핫픽스의 실제 기기·서버 read-back 증빙 정리.
2. TYP-121 / #180: 동의 v2 재선택·OFF 네트워크·금지 속성 미보관·Crashlytics 검증.
3. TYP-43·81·34와 기능별 In Review: 정확한 배포 빌드의 잔여 QA·IAP·권리·계정·지역 확인.
4. TYP-117 / #186: Linear·Orca·GitHub·현황 문서의 상태 동기화와 검토.

TYP-78의 스토어 미디어는 일시정지/Todo를 유지한다. 재개 시 캡처 빌드를 명시하고
기존 미디어의 실제 UI 일치부터 확인한다.

## Next — 이미 구현된 변경의 리뷰

- TYP-122 / #184 / Draft PR #185: 게임별 순위·주간 경쟁·주변 기록·성장 연계.
  head 8831e81의 Claude 리뷰와 maintainer gate가 남았다. 아직 main·공개판에 없다.
- Dependabot PR #177/#178: 변경 범위의 로컬 검증·리뷰·수동 병합 gate 적용.

Next는 새 구현·자동 병합 승인이나 출시 버전 확정을 뜻하지 않는다.

## Later / 중지된 작업

- TYP-76·99·100·107~110·118의 설정·입력·덱·IAP·성능 후속 작업은 Linear 우선순위를 따른다.
- TYP-72·80·87·91 운영 개선은 현재 상태를 유지한다. 자동화·모델·스케줄은 이번 정리에서 변경하지 않았다.
- Android는 동결. TYP-119를 포함한 재개는 별도 승인된 새 PRD·초기 설계에서만 시작한다.

## 운영 제한

- 전체 동시 개발 최대 2개: release-critical 1개와 독립 탐색 1개. 플랫폼별 구현 In Progress 최대 1개.
- iOS/iPadOS와 재개한 Mac 개발을 관리하며, 기존 Mac 데모 통합과 제품 출시 완료를 구분한다.
- 작업마다 Issue·담당·branch·worktree·PR을 하나씩 연결한다. 번호가 같은 GitHub/Linear 이슈를 자동으로 동일시하지 않는다.
- `PRD.md`, `DECISIONS.md`, `AGENTS.md`, localization, catalog·audio manifest는 단일 소유.
- 현재 head Claude 리뷰·SHA evidence·최신 main 대조·maintainer 수동 승인 후에만 병합한다.
- 완료는 필요한 증빙·병합·Issue/Project 정리를 포함한다. 미확인 외부 gate는 그대로 남긴다.
