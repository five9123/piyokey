# Android M7 M3 handoff

기준일: 2026-08-25 JST

## 범위

M3는 PRD F5.1~F5.7의 읽기 전용 정적 카탈로그, 발견·상세, 설치·업데이트·삭제,
오프라인 내 덱, 홈·결과 추천을 Android에 포팅한다. 계정, 쓰기 API, F5.8 지원
메일, F5.9 Deck Maker/Billing, 게임·커리큘럼·OS IME는 포함하지 않는다.

주요 모듈은 다음과 같다.

- `core:data`: Room schema v1, 복구 journal, 원자적 catalog/deck payload,
  primary/backup/quarantine, HTTPS 조건부 GET, 발견·추천 순수 규칙
- `feature:discover`: 검색·필터·정렬, 섹션형 발견, 상세·미리보기,
  홈 추천, 내 덱, 연습 결과 추천
- `app`: 홈·둘러보기·연습·게임·마이페이지 5탭 경계와 M3 navigation
- `feature:practice`: 설치 덱 target 주입과 결과 callback

번들 asset은 복제하지 않고 `shared/mock_catalog/`와 `shared/schema/`를 Android
asset source로 직접 사용한다. 운영 주소는 `PIYOKEY_CATALOG_URL` Gradle property로
주입한 절대 HTTPS catalog URL만 허용하고, 덱 URL은 같은 scheme·host·port와 content
root의 `decks/` 아래로 제한한다.

## 저장·복구 계약

Room은 설치 metadata, 삭제 후에도 남는 download tag history, catalog validator와
persistent journal을 저장한다. 실제 JSON은 앱 전용 파일에 저장하며 설치와 cache
갱신은 아래 순서를 사용한다.

1. schema·의미 검증을 통과한 bytes를 같은 content root에 stage
2. journal을 Room transaction으로 영속화
3. 현재 payload를 backup으로 옮기고 stage를 target으로 atomic move
4. 새 metadata/history와 journal 삭제를 하나의 Room transaction으로 commit
5. 더 오래된 backup만 정리

시작 시 journal을 재생해 이전 또는 새 pair로 수렴한다. 현재 payload hash·JSON이
손상되면 직전 검증 backup을 복원하고, backup도 유효하지 않으면 해당 덱만 격리한다.
catalog는 두 cache가 모두 유효하지 않을 때 번들 26덱으로 fallback한다.

## 검증

```sh
cd android
./gradlew \
  :core:data:testDebugUnitTest \
  :core:data:connectedDebugAndroidTest \
  :app:connectedDebugAndroidTest \
  :feature:discover:lintDebug \
  :app:lintDebug \
  :app:assembleDebug
```

- JVM: discovery/search/filter/sort/recommendation/URL/atomic store 7개 통과
- API 35 `hantap_test`: Room install/delete/history, 격리·backup 복구,
  v10→v11 조건부 cache 5개 통과
- API 35 `hantap_test`: 발견→번들 덱 상세→다운로드→오프라인 연습 진입
  Compose UI 1개 통과
- `feature:discover`와 `app` lint error 0, debug APK 성공

`artifacts/m7/m3/`의 PNG·MP4는 ignored local evidence이며 release device 증거가
아니다. 실제 기기 의존 항목은 GitHub Issue #19에 유지한다.

## 후속

M4는 순수 monotonic game reducer와 Compose frame rendering을 분리해 게임 hub와
흐름 게임·공통 결과를 구현하고, 나머지 게임은 M6에서 같은 기반으로 확장한다. M3 Room schema는
M4 GameRecord·DeckProgress가 migration으로 확장할 기준선이다. 실제 기기 입력
p95와 rollover, 60초 성능·오디오·IME는 출시 후보 통합 QA 전까지 완료로 표시하지
않는다.
