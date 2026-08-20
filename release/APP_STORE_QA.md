# typee / ピヨキー App Store 출시 QA

이 문서는 App Store 제출용 Release Candidate(RC)의 단일 체크리스트다. `P0`가 하나라도 열려 있으면 제출하지 않는다.

## 0A. 다음 릴리스 후보 — 1.1 Deck Maker

아래 항목은 다음 업데이트 `1.1`의 제출 게이트다. 이후 `1.0.2 (6)` 체크와 제출 ID는 과거 제출 증적으로 보존하며, `1.1` 완료 근거로 재사용하지 않는다.

- [ ] 미사용 build 번호로 `1.1` Distribution archive 생성·검증
- [ ] Paid Applications 계약, 세금·은행 정보를 활성 상태로 확인
- [ ] Apple Small Business Program 가입/미가입 상태와 예상 수수료를 Account Holder가 확인
- [ ] 비소모성 `app.piyokey.deckmaker.lifetime` 생성 및 ja/en-US/ko 상품명·설명 입력
- [ ] 1.1 앱 UI·콘텐츠 언어를 ja/en/ko로 동결하고 App Store 메타데이터를 en-US/en-GB/en-AU/en-CA/ko/ja로 준비
- [ ] en-GB/en-AU/en-CA의 `copy_from` 초안을 App Store Connect 필수 필드·스크린샷으로 실제 저장하고 재확인
- [ ] 프랑스어·스페인어·중국어 등 추가 UI·metadata-only 현지화가 1.1 제출에 섞이지 않았는지 확인
- [ ] 앱과 Deck Maker IAP 모두 `All Countries or Regions` 및 향후 storefront 자동 포함으로 설정
- [ ] EU DSA, 중국 본토, 베트남의 국가별 App Store 상태를 확인하고 action-required/판매 불가 예외를 제출 기록에 남김
- [ ] availability와 별개로 승인 후 수동 출시 또는 자동 출시 중 하나를 확정하고 제출 기록과 App Store Connect 설정을 일치시킴
- [ ] 출시 가격·스토어프런트·세금 카테고리·Family Sharing 정책 확정
- [ ] 1.1 결제창의 가격·복원·약관·개인정보 링크가 보이는 IAP 심사용 스크린샷 업로드
- [ ] IAP 리뷰 노트 입력 후 상품 상태 **Ready to Submit** 확인
- [ ] 1.1 버전의 **Add for Review**에서 iOS 앱과 첫 IAP가 함께 포함됐는지 확인
- [ ] 로컬 StoreKit에서 성공·취소·pending·복원·refund/revocation 및 무료 `.piyodeck` 경계 확인
- [ ] Sandbox/TestFlight에서 같은 결제 시나리오와 재실행 entitlement를 정확한 1.1 빌드로 확인
- [ ] `release/app_store_submission.json#next_submission`의 수동 게이트를 실제 증거에 맞춰 갱신
- [ ] `python3 tools/release_preflight.py --strict` 성공

## 0. 과거 릴리스 후보 — 1.0.2 (6)

- [x] 최신 소스의 Distribution archive `PIYOKEY-1.0.2-6.xcarchive` 생성·검증
- [x] Cloud Managed Apple Distribution IPA 생성·검증
- [x] App Store Connect에 `1.0.2 (6)` 업로드
- [x] App Store Connect 처리 완료(`Complete / Ready to Submit`) 및 TestFlight 사용자 확인
- [x] 앱 버전 `1.0.2`에 build 6 연결·수동 hold 해제 후 심사 제출

이전 승인 버전은 `1.0.1 (4)`이다. 제출 ID `722d6568-1321-4b44-838f-138f07ef8647`의 build 5 제출은 2026-08-11 취소되어 `Removed / Developer Rejected` 상태다. build 6 제출 ID `45184f9b-494c-431e-a740-a3dde9080f4a`는 2026-08-11 21:15 JST에 전송됐고, iOS 앱 1개와 v4 리더보드 4개가 모두 `Waiting for Review`다. 승인 후 phased release 없이 전 사용자에게 즉시 자동 출시한다.

## 1. 현재 자동 확인 가능한 P0

- [x] AppIcon 1024×1024, alpha 없음
- [x] `ja/en/ko` 앱 표시명: `ピヨキー / typee / typee`
- [x] 세 언어 `Localizable.strings` 키·서식 인자 일치
- [x] iPhone 전용, iOS 16+, 세로 방향 설정
- [x] `PrivacyInfo.xcprivacy` 번들 포함
- [x] Required Reason API: `UserDefaults / CA92.1`, `ActiveKeyboards / 54BD.1`
- [x] 추적·수집 데이터 없음 선언
- [x] 비면제 자체 암호화 미사용 선언(Apple SDK의 HTTPS만 사용)
- [x] 최종 Bundle ID `app.piyokey.Piyokey` 확정
- [x] App Store Connect App ID `6794853985` 및 명시적 Bundle ID 등록
- [x] 마케팅 버전·빌드 번호 `1.0.2 (6)` 확정
- [x] Release archive 생성·검증, Cloud Managed Apple Distribution IPA 검증 및 빌드 `1.0.2 (6)` 업로드 통과

자동 점검:

```sh
python3 tools/release_preflight.py
python3 tools/release_preflight.py --strict
```

첫 명령은 소스·번들 설정 회귀를 검사한다. `--strict`는 이 문서의 수동 게이트와 `release/app_store_submission.json`, 스토어 스크린샷까지 모두 닫혀야 성공한다.

## 2. 기능 회귀 P0

### 설치·온보딩·홈

- [ ] TestFlight 새 설치에서 크래시 없이 온보딩 진입
- [ ] 건너뛰기/완료 모두 홈 진입, 재실행 때 온보딩이 중복 노출되지 않음
- [ ] 목표 선택이 홈 추천에 반영됨
- [ ] 스탬프 카드, 하루 3분, 추천 카드가 일본어 기본 화면에서 정상 표시됨
- [ ] 앱 업데이트 설치 시 기존 덱·기록·스트릭·피요 이름/옷장이 유지됨
- [ ] 삭제 후 재설치는 로컬 기록이 초기화되는 현재 정책과 일치함

### 연습·입력

- [ ] 내장 두벌식: 기본/복합 모음·종성·도깨비 이월·Shift 자모·자모 단위 백스페이스
- [ ] 완료 음절 초록 체크, 단어 완료 0.65초 후 자동 다음 문제 전환
- [ ] 마지막 문제 완료 후 결과 자동 진입, `Next target`/`결과 보기` 버튼 없음
- [ ] 결과 우측 상단 `완료`가 연출 중/후 모두 즉시 종료
- [ ] 한국어 OS IME: 설치됨/없음 안내, 조합 중간 상태, 확정, 삭제, 붙여넣기
- [ ] TTS가 현재 목표를 `ko-KR`로 읽고 연속 탭에도 중복·끊김 문제가 없음
- [ ] 세션 설정의 키보드·사운드·햅틱 변경이 즉시 반영됨

### 게임 6종

- [ ] 흐름·산성비·초성 맞추기·단어 맞추기·받아쓰기 각각 난이도/덱 선택→카운트다운→플레이→결과
- [ ] 띄어쓰기 문제 선택→직접 입력→결과
- [ ] 카운트다운 중 타이머/카드/낙하가 시작되지 않음
- [ ] 앱 백그라운드에서 타이머·카드·낙하 정지, 복귀 후 정상 재개
- [ ] 마지막 문제/시간 종료 시 결과 자동 진입, 점수·정확도·콤보·최고 기록 정확
- [ ] 재도전에서 이전 세션 상태가 섞이지 않음
- [ ] 오타가 복습 덱에 수집되고 노미스 3회 후 졸업함
- [ ] Game Center 계약 대상 결과만 CTA 노출, 연타·인증 취소/재시도·백그라운드 복귀에도 단일 대시보드 유지
- [ ] App Store Connect Live baseline과 `PiyokeyGameCenterAvailableLeaderboardIDs`가 일치하고 intended probe의 전부 성공/부분 실패를 TestFlight에서 확인

### 콘텐츠·저장·공유

- [ ] 발견 검색/필터/상세/설치/업데이트/삭제/재설치
- [ ] 비행기 모드에서 번들 및 설치 덱 사용, 실패 UI와 재시도 정상
- [ ] 손상된 주 JSON은 백업 복구, 한 덱 손상이 다른 덱·기록을 삭제하지 않음
- [ ] 공유 이미지에 실제 세션 수치·현재 피요 이름/모습이 표시됨
- [ ] 공유 취소/실패 후 앱 상태가 유지됨
- [ ] 리마인더 기본 OFF, 권한 허용/거부/설정 변경/시간 변경/중복 예약 없음

### 피요 성장 시스템

- [ ] 챕터·스트릭·덱·게임 기록에 따른 성장/해금 조건 정확
- [ ] 이름·알 무늬·소품 선택이 재실행 후 유지됨
- [ ] 날짜·계절·기념일에 따른 장식(밀짚모자·목도리·산타 모자 등)이 어느 화면에도 표시되지 않음
- [ ] 홈·연습·게임·결과에서 병아리/소품이 할당 프레임 밖으로 이탈하지 않음
- [ ] Reduce Motion에서 큰 이동 대신 정적 피드백을 사용함

## 3. 기기·성능·접근성 P0

- [ ] iPhone 12: 파티클 포함 60fps 게이트, Hanco hang/hitch 0건
- [ ] iPhone SE급 작은 화면 + iOS 16: 잘림·겹침·키보드 오터치 없음
- [ ] 최신 대화면 iPhone + iOS 26: Safe Area/Dynamic Island/하단 키보드 정상
- [ ] iPad의 iPhone 호환 모드에서 실행·회전·설정 시트가 깨지지 않음
- [ ] 메모리 경고, 저전력, 발열 상태에서 강제 종료/진행 손실 없음
- [ ] VoiceOver로 메뉴와 공통 설정 이동 가능
- [ ] Dynamic Type 최대 크기에서 핵심 텍스트·버튼 접근 가능
- [ ] 모든 작은 아이콘 실제 탭 영역 44×44pt 이상
- [ ] Light/Dark, 고대비, Reduce Motion에서 정보가 색상/애니메이션에만 의존하지 않음
- [ ] Spotify 등 외부 음악 재생 중 효과음/TTS 사용 시 음악이 중단·덕킹되지 않음

## 4. 로컬라이제이션·콘텐츠 권리 P0

- [ ] 일본어 전체 화면의 번역, 줄바꿈, 조사의 자연스러움 원어민 검수
- [ ] 영어·한국어에서 키 노출, 잘림, `%@/%d` 같은 포맷 문자열 노출 없음
- [ ] `es-MX`·`fr-FR`·`zh-Hant`·`ar-SA` 기기 언어 새 설치가 영어 UI·영어 콘텐츠·`typee`로 시작하고 일본어 뜻·가타카나를 노출하지 않음
- [ ] 덱의 모든 `ko` 입력·일본어 뜻·초성 힌트 교차 검수
- [x] 출시 카탈로그에 실제 아티스트·그룹·곡·프로그램·캐릭터명과 실제 가사/대사 없음 확인
- [x] 앱 아이콘·스크린샷·캐릭터·사운드·덱 문구의 제작/사용 권리 증빙 보관
- [x] 1.0의 gTTS 및 macOS 합성 음원 배포 관련 운영자 위험 승인 기록
- [ ] 1.1의 581개 gTTS MP3 확장 자산에 대한 운영자 권리 재검토·승인
- [ ] 정적 큐레이션 덱을 실제 사용자 커뮤니티/UGC처럼 오인시키는 문구 제거

## 5. 과거 1.0.2 App Store Connect P0 증거

- [x] 앱 이름/부제/설명/키워드: 일본어 우선, 실제 제공 기능만 기재
- [x] 카테고리 `Education` + `Games/Word`, 최신 연령 등급 질문 완료(Contests Frequent, 산출 13+·레거시 12+)
- [x] Support URL과 개인정보처리방침 HTTPS URL 등록
- [x] 앱 설정 안에서도 개인정보처리방침과 지원 경로에 접근 가능
- [x] App Privacy `Data Not Collected` 답변 게시 및 RC의 SDK·네트워크 동작과 대조
- [x] Content Rights: 타사 콘텐츠가 포함되며 필요한 권리를 보유한다고 응답·저장
- [x] 수출 규정: `ITSAppUsesNonExemptEncryption=NO`, 업로드 빌드의 Missing Compliance 없음 확인
- [x] 국가/지역(1.0.2 historical): 일본만 Available, 기타 국가/지역 Not Available
- [x] App Review 연락처 입력
- [x] 리뷰 노트에 로그인 없음, 내장 키보드로 전 기능 검토 가능, OS 한국어 키보드는 선택 사항, 로컬 알림/TTS/정적 카탈로그 동작을 설명
- [x] 심사 중 필요한 정적 카탈로그는 번들 콘텐츠와 검증된 캐시 fallback으로 동작
- [x] Game Center 심사 초안: 리더보드 16개(단어퀴즈는 `piyokey.v4.word_match.*`)와 기존 업적 5개
- [x] 플로우 1.8배 규칙용 `piyokey.v4.flow.*` 3개와 `piyokey.v4.cup.weekly.flow` 생성·심사 초안 포함
- [x] 이전 앱 버전 `1.0 (3)` + 리더보드 16개 + 업적 5개, 총 22개를 App Review에 제출
- [x] 이전 제출 ID `1d46dc56-d5bb-4f60-8a47-406436d82541` 기록 보존
- [x] 앱 버전 `1.0.2 (5)` + v4 리더보드 4개 제출 이력 보존(2026-08-11 취소)
- [x] build 6과 v4 리더보드 4개를 제출 ID `45184f9b-494c-431e-a740-a3dde9080f4a`로 제출, 5개 모두 `Waiting for Review`

## 6. 스토어 이미지와 TestFlight P0

- [x] `release/screenshots/ja-marketing/`에 실제 App Store Connect 등록본과 같은 6.9형 스크린샷 8장
- [x] 이미지 alpha 없음, 실제 RC 화면과 일치, Debug FPS/p95·목 데이터·개인정보 미노출
- [x] 순서 확정: 홈 → 입력 성공 → 커리큘럼 → 게임 허브 → 게임 플레이 → 성장/옷장 → 결과 → 스탬프/보상
- [x] 일본어 스크린샷 8장을 App Store Connect에 업로드하고 위 순서로 정렬
- [x] 일본 1차 출시용 일본어 스크린샷과 ja/en/ko 메타데이터 작성
- [ ] 영어/한국어 전용 스크린샷은 1.1 `All Countries or Regions` 제출 전에 별도 제작
- [ ] TestFlight 내부 QA: 새 설치/업데이트/오프라인/알림 거부 스모크
- [ ] 일본어 사용자 외부 베타에서 입력 이해도·번역·키캡 터치 오류 확인
- [x] TestFlight와 제출용 빌드의 version/build/hash 및 Game Center 실기기 확인 결과 기록
- [x] TestFlight 빌드 `1.0.2 (6)` 업로드
- [x] App Store Connect 처리 완료·TestFlight 사용자 확인 및 App Store 버전 연결

## 7. Go / No-Go

다음을 모두 만족해야 Go다.

빌드 6 소스의 출시 후보 전체 회귀는 321/321 통과했다. 같은 소스로 Distribution archive와 Cloud Managed Apple Distribution IPA를 검증한 뒤 Organizer 업로드에 성공했다.

2026-08-10 사용자가 TestFlight 새 설치, 한국어 OS IME, TTS, 무음 모드·외부 음악 혼합, iPhone 12 성능 게이트를 build 5 제출에 한해 명시적으로 면제한 이력은 build 6에 승계하지 않는다. 2026-08-11에는 build 6의 TestFlight Game Center 확인 뒤 즉시 제출하라는 별도 지시를 받았으며, 아래 열린 수동 게이트는 완료로 오인하지 않도록 유지한다.

- [ ] `python3 tools/release_preflight.py --strict` 성공
- [x] 빌드 6 소스의 전체 회귀 321/321 통과
- [x] 빌드 6 App Store Distribution archive·IPA 검증 및 Organizer 업로드 성공
- [ ] TestFlight RC에서 P0 0건, P1은 명시적 승인만 허용
- [ ] iPhone 12 성능과 한국어 IME/TTS/외부 음악 실기기 게이트 완료
- [x] `game_center_live_device_check` 완료(TestFlight 사용자 실기기 확인)
- [x] 개인정보·콘텐츠 권리·스토어 메타데이터 담당자 승인
- [x] 제출 빌드와 Game Center 실기기 확인 빌드가 `1.0.2 (6)`으로 동일함
- [x] App Store Connect build 6과 v4 Flow 3개·Weekly Piyo Cup 1개의 심사 구성 확인
- [x] 수동 hold 해제 후 2026-08-11 21:15 JST에 `Submit for Review` 실행

취소된 build 5 제출 ID `722d6568-1321-4b44-838f-138f07ef8647`은 `Removed / Developer Rejected`로 보존한다. 현재 제출 ID `45184f9b-494c-431e-a740-a3dde9080f4a`의 5개 항목(iOS App 1.0.2 build 6, Flow Beginner/Intermediate/Advanced v4, Weekly Piyo Cup v4)은 모두 `Waiting for Review`다. `manual_hold=false`이며 승인 후 phased release 없이 전 사용자에게 즉시 자동 출시한다.
