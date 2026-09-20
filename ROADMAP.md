# PIYOKEY 로드맵

상세 제품 계약은 `PRD.md`, 현재 사실과 확인 출처는 `PROJECT_STATUS.md`를 따른다.

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
- TYP-123 / #175 / Draft PR #176: 로컬 Mac Catalyst 데모의 최신 head 검토.
  별도 학습·6개 게임 데모 범위이며 제품 출시나 CloudKit 동기화를 포함하지 않는다.
- Dependabot PR #177/#178: 변경 범위의 로컬 검증·리뷰·수동 병합 gate 적용.

Next는 새 구현·자동 병합 승인이나 출시 버전 확정을 뜻하지 않는다.

## Later / 중지된 작업

- TYP-76·99·100·107~110·118의 설정·입력·덱·IAP·성능 후속 작업은 Linear 우선순위를 따른다.
- TYP-115/116 Mac 제품·아키텍처·동기화·출시 계획은 별도 결정이다. TYP-116은
  사용자 중지/Backlog를 유지하고 로컬 데모 완료만으로 재개하지 않는다.
- TYP-72·80·87·91 운영 개선은 현재 상태를 유지한다. 자동화·모델·스케줄은 이번 정리에서 변경하지 않았다.
- Android는 동결. TYP-119를 포함한 재개는 별도 승인된 새 PRD·초기 설계에서만 시작한다.

## 운영 제한

- 전체 동시 개발 최대 2개: release-critical 1개와 독립 탐색 1개. 플랫폼별 구현 In Progress 최대 1개.
- 활성 네이티브 제품 개발 범위는 iOS/iPadOS이며 별도 승인된 데모와 제품 출시를 구분한다.
- 작업마다 Issue·담당·branch·worktree·PR을 하나씩 연결한다. 번호가 같은 GitHub/Linear 이슈를 자동으로 동일시하지 않는다.
- `PRD.md`, `DECISIONS.md`, `AGENTS.md`, localization, catalog·audio manifest는 단일 소유.
- 현재 head Claude 리뷰·SHA evidence·최신 main 대조·maintainer 수동 승인 후에만 병합한다.
- 완료는 필요한 증빙·병합·Issue/Project 정리를 포함한다. 미확인 외부 gate는 그대로 남긴다.
