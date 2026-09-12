# PIYOKEY 프로젝트 현황

마지막 갱신: 2026-09-12 JST
기준 저장소: `five9123-maker/piyokey`
확인한 `origin/main`: `602e923e3eddcb32b7ef75c62e3921f28f7a3e33`

제품 계약은 `PRD.md`, 결정은 `DECISIONS.md`, 작업 순서는 `ROADMAP.md`를 따른다.
작업 상태와 남은 검증은 [Linear Typee](https://linear.app/typee/team/TYP/all),
소스·병합·검증 SHA는 GitHub Issue/PR과
[PIYOKEY Development Project](https://github.com/users/five9123-maker/projects/1)에서 연결한다.
Orca 카드는 이 상태의 작업공간별 표시이며 별도 완료 기준이 아니다.
확인 근거와 동기화 내역은 [2026-09-12 점검](docs/audits/tracker-sync-20260912.md)을 참고한다.

## 현재 기준선

| 영역 | 확인 상태 | 남은 확인 |
|---|---|---|
| iOS 공개판 | Apple 공개 lookup을 2026-09-12 직접 조회: **JP/US/KR 모두 1.1.1**, 공개 일시 2026-09-11 09:33:05 JST | 공개 사실과 전체 수동 QA·권리·계정·지역 gate 통과를 구분한다. 세 국가 밖 판매 상태는 이번에 조회하지 않았다 |
| 서명·제출 기록 | #181의 2026-09-11 기록: **1.1.1 (25)**, main `602e923`와 같은 tree의 archive/export/upload·처리·App Review 제출. 단위 449개 PASS. 당시 출시 설정 MANUAL | 공개 lookup에는 build 번호가 없다. build 25 연결은 이전 ASC/아카이브 기록이며, 9월 12일 ASC 상태 자체를 새로 읽지는 않았다 |
| Game Center 핫픽스 | PR #183 병합. 모든 키보드 참여·Flow/Cup 분리·등록 복구·서버 점수 재조회. 구형 v3 4개 archive·Flow 초급 v5 Live 기록 | TYP-120의 동일 서명 기기별 인증·오프라인·최고점 재시도·주간 경계 read-back 증빙 |
| 분석·진단 | PR #182 병합. PostHog 국가·사용 환경 분석과 동의 v2. #181에서 정책 게시·ASC Coarse Location 공시, #180에서 9월 12일 국가 집계 확인 | TYP-121의 재동의·OFF 네트워크·금지 속성 미보관·Crashlytics crash/dSYM |
| 웹 Builder | 별도 `hanco_web` 저장소. schema v2 Builder PR #6 통합 기록, 정책 게시 PR #12는 #181에서 확인 | 모바일 교차 편집·미확인 서비스 gate는 별도. 이 저장소의 `web/`은 analytics 계약 패키지 |
| Android | 과거 포트 참고용 동결 | 개발·의존성·CI·출시 범위 제외. TYP-119는 재개 시 재설계 backlog |
| macOS | #175 / Draft PR #176 로컬 Catalyst 데모 수락 기록, **main 미병합**. TYP-123으로 연결 | 데모와 TYP-115/116 제품·동기화·출시 스파이크는 별도. TYP-116 사용자 중지/Backlog 유지 |
| CI·병합 | Actions 비활성. 현재 head Claude 리뷰·focused 로컬 SHA evidence·최신 main tree·maintainer 수동 승인 정책 | 비활성 CI와 외부 미검증 항목을 성공으로 취급하지 않는다 |

## 현재 우선 작업

| Linear / GitHub | 상태 | 다음 한 단계 |
|---|---|---|
| [TYP-120](https://linear.app/typee/issue/TYP-120) / #181 · PR #183 | In Review / 소스 병합·1.1.1 공개 확인 | 사용자 TestFlight 완료 보고와 별도로 남은 상세 Game Center 기기/서버 증빙 정리. 조사 Draft PR #179는 조사 이력 |
| [TYP-121](https://linear.app/typee/issue/TYP-121) / #180 · PR #182 | In Review / 소스 병합 | 실제 서명 앱의 동의·철회 네트워크·저장 속성 및 Crashlytics 검증 |
| [TYP-122](https://linear.app/typee/issue/TYP-122) / #184 · Draft PR #185 | In Review / 미병합 | head `8831e81`의 Claude 리뷰·maintainer 승인. focused 검증 완료 기록이 있으나 리뷰는 당시 한도로 미실행. **공개 1.1.1에 미포함** |
| [TYP-43](https://linear.app/typee/issue/TYP-43) / #77 | In Review / 공개 후 잔여 증빙 | 오래된 심사 대기·은행 정보 미입력 주장을 현행 확정 사실로 사용하지 않는다. TYP-81·34·78 및 기능별 잔여 확인 |
| [TYP-81](https://linear.app/typee/issue/TYP-81) | Todo / QA | 공개 1.1.1과 실제 설치 build를 기준으로 iPhone/iPad·IAP·계정·권리·지역·미디어 검증 결과 기록 |
| [TYP-34](https://linear.app/typee/issue/TYP-34) | Todo / 검증만 | 구현은 PR #62/#140에 포함. 권한·기존 OFF 보존·시간대 변경·현지 20시 실제 수신 |
| [TYP-78](https://linear.app/typee/issue/TYP-78) / PR #149 | Todo / 의도된 일시정지 | 과거 build 11 미디어와 현재 배포 UI 대조, 사람 검수·저장 재조회. exec 라벨 제거·중지 유지 |
| [TYP-117](https://linear.app/typee/issue/TYP-117) / #186 | In Progress / 상태 동기화 | Linear·Orca 반영 후 현황 문서·release JSON의 검증·PR 리뷰·병합 gate |
| [TYP-123](https://linear.app/typee/issue/TYP-123) / #175 · Draft PR #176 | In Review / 로컬 데모 | 최신 head 리뷰·maintainer 확인. Mac 출시·CloudKit·Universal Purchase 완료로 해석하지 않음 |

## 소스 병합 후 기능별 실기기 검증 대기

아래 Linear 상태는 모두 **In Review**다. PR 병합은 확인했지만 이번 점검에서 새
실기기 테스트를 수행하지 않았다. 과거 build 11~24 표기는 역사적 증빙이며 새 후보가
아니다. 재검증할 때 실제 설치 build·OS·기기를 기록하고 각 티켓의 잔여 조건을 따른다.

| Linear | 소스 | 남은 기능별 확인 범위 |
|---|---|---|
| [TYP-73](https://linear.app/typee/issue/TYP-73) | PR #145 병합 | [Regression][iOS/iPadOS] build 10 OS 한국어 키보드 조합 상태가 단어 전환 후 잔존 |
| [TYP-75](https://linear.app/typee/issue/TYP-75) | PR #146 병합 | [UX][iOS] 설정 화면 정리: 키보드 재구성·섹션 정리·버전 표시·마이페이지 중복 제거 |
| [TYP-77](https://linear.app/typee/issue/TYP-77) | PR #147 병합 | [Bug][iOS/iPadOS] OS 천지인 입력이 영어 키보드로 오판돼 입력 차단(ASCII 가드) |
| [TYP-85](https://linear.app/typee/issue/TYP-85) | PR #161 병합 | [Feature][iOS/iPadOS 1.1] 인앱 천지인(10키) 키보드에 iOS 표준 플릭 제스처 추가 |
| [TYP-88](https://linear.app/typee/issue/TYP-88) | PR #154 병합 | OS 10키 판정기: 받침 경계 중간 상태(겹받침 병합·조립)를 오타로 판정 |
| [TYP-89](https://linear.app/typee/issue/TYP-89) | PR #155 병합 | 설정 화면 정보구조 개편 |
| [TYP-90](https://linear.app/typee/issue/TYP-90) | PR #156 병합 | 키보드 입력 가이드 노출 정책 변경 |
| [TYP-92](https://linear.app/typee/issue/TYP-92) | PR #157 병합 | [Regression][iOS] TYP-90 범위 정정 |
| [TYP-93](https://linear.app/typee/issue/TYP-93) | PR #158 병합 | 세션 설정(덱 플레이·연습) 정보구조 |
| [TYP-97](https://linear.app/typee/issue/TYP-97) | PR #162 병합 | [Bug][iOS] 메인 '랜덤 5' 세션 |
| [TYP-98](https://linear.app/typee/issue/TYP-98) | PR #163 병합 | [Bug][iOS] iPhone OS 키보드 모드에서 '다시 듣기(스피커)' 버튼 탭 불가 |
| [TYP-102](https://linear.app/typee/issue/TYP-102) | PR #167 병합 | [Bug][iOS] 부화 미션 결과 닫힘 후 다음 단계 전환이 배경 커버 상태로 정지 |
| [TYP-103](https://linear.app/typee/issue/TYP-103) | PR #166 병합 | [UI][iOS/iPadOS 1.1] 설정 키보드 카드 |
| [TYP-105](https://linear.app/typee/issue/TYP-105) | PR #168 병합 | [Regression][iOS] build 21 부화 미션 1→2·2→3 결과 전환 경합 잔여 수정 |
| [TYP-112](https://linear.app/typee/issue/TYP-112) | PR #171 병합 | iPad OS 키보드 모드에서 게임 화면에 두벌식 가이드 미표시 + 가이드 off 시 전체화면 미전환 |
| [TYP-113](https://linear.app/typee/issue/TYP-113) | PR #174 병합 | 피요컵이 OS 키보드 설정을 무시하고 인앱 두벌식 키보드 강제 |
| [TYP-114](https://linear.app/typee/issue/TYP-114) | PR #173 병합 | 커리큘럼 스테이지 카드의 '>' 셰브런 제거 |

## 완료·취소와 보류 경계

- 기존 Linear Done: TYP-7·65·66·67·69·71·79·82·83·84·86·94·95·96·111.
  해당 완료 이력을 보존하며 오래된 문서나 Orca 카드 때문에 재개하지 않는다.
- 기존 Canceled: TYP-68·74·101·106. TYP-101/106 프리뷰는 TYP-111 표시 제거로
  대체됐고 플릭 입력 자체는 유지한다. Orca의 종료 열에는 취소/대체 사유를 명시했다.
- TYP-70·76·99·100·104·107~110·118 등 후속 범위의 우선순위·담당·일정은 이번
  동기화에서 변경하지 않았다. TYP-115/116의 Mac 제품 일정·구조는 별도 결정이다.
- 운영 TYP-72·91은 기존 In Progress, TYP-80·87은 Todo다. Orca reviewer·dispatcher는
  활성, capacity watchdog은 비활성임을 확인했으며 자동화 설정은 변경하지 않았다.

## 작업공간과 출시 판단

- 기본 `/Users/jungminoh/Documents/hanco`는 `main`이 아니라 `feat/orca-task-name`
  (`909585d`)이며 원격 main보다 38커밋 뒤, 고유 문서 커밋 1개가 있다. Orca 이름을
  바로잡았다. 현재 기준선과 혼동해 배포하지 않는다.
- 실제 로컬 `main`은 Codex `a1b5/hanco`에 있고 `3aa148f`로 원격보다 1커밋 뒤다.
- `codex/181-separate-cup-flow`의 미커밋 `PROJECT_STATUS.md`, 미병합 PR, 임시·중지
  작업공간과 터미널은 보존했다. 이번 정리로 branch를 일괄 최신화하거나 삭제하지 않았다.
- 소스·자동 테스트·Apple 공개 관찰은 각각 별도 사실이다. 미확인 실기기·계정·권리·IAP·
  전 지역 판매 검증을 완료로 추정하지 않으며 다음 배포 전 strict 기준을 다시 확인한다.
