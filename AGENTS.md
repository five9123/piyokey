# PIYOKEY / ピヨキー — 한국어 타이핑 앱 (내부 프로젝트명 Hanco)

## 문서 체계
- `PRD.md` — 단일 소스 오브 트루스. 모든 기능·AC·연출 스펙이 여기 있다. 작업 전 해당 섹션을 반드시 읽을 것.
- `DECISIONS.md` — PRD가 모호해서 네가 내린 결정을 기록 (날짜, 결정, 근거, 관련 PRD 섹션).
- `shared/test_vectors.json` — 한글 조합 엔진 공용 테스트 벡터 (생성기: `tools/gen_test_vectors.py`). iOS/Android 테스트 모두 이 파일을 읽는다. 수정 금지 — 케이스 추가는 생성기를 고쳐 재생성.
- `docs/GITHUB_PROJECTS_GUIDE.md` — GitHub Issue/Projects 상태, 사용자 승인 게이트, 완료 증빙 규칙. 모든 실행성 작업에서 준수.

## GitHub Issue / Projects 작업 계약
- 코드·콘텐츠·설정·릴리스 상태를 바꾸는 모든 실행성 작업은 시작 전에 GitHub Issue를 가져야 한다. 단순 질문·읽기 전용 조사만으로 끝나는 대화는 제외하되, 후속 변경을 수용하는 즉시 Issue로 전환한다.
- Slack·이메일·대화에서 들어온 요청은 바로 구현하지 않는다. 원문 링크와 사용자 문제를 Issue에 기록하고 Project의 `Inbox` 또는 `Needs Decision`에 둔 뒤 사용자와 범위·우선순위·AC를 확정한다.
- `Ready`는 관련 PRD 절, 검증 가능한 AC, 영향 범위, 검증 계획이 모두 있는 작업만 사용한다. PRD가 모호하면 먼저 사용자 결정을 받고 필요 시 `DECISIONS.md`를 갱신한다.
- 구현 시작 시 Issue를 담당자에게 배정하고 `In Progress`로 이동한다. 기본 branch는 `codex/<issue-number>-<short-slug>`이며 한 Issue는 한 주 담당자/agent와 한 주 branch를 가진다.
- 완료 후보는 PR, 실행한 테스트와 결과, 미실행 수동 gate, UI 변경 증빙을 Issue에 연결하고 `Verify`로 이동한다. 사용자 노출 동작·우선순위·수동 실기기 gate는 사용자가 승인하기 전 `Done`으로 바꾸지 않는다.
- 외부 승인·기기·계정·선행 Issue가 필요하면 `Blocked`와 이유·해제 조건을 기록한다. PR 병합과 필수 검증 완료 뒤에만 Issue를 닫고 `Done`으로 처리한다.
- 세부 상태·필드·우선순위 정의와 agent 댓글 형식은 `docs/GITHUB_PROJECTS_GUIDE.md`를 따른다. Project는 실행 현황의 원본이고 기능 동작의 원본은 계속 `PRD.md`다.

## 마일스톤 (PRD §13 순서 엄수)
M1 조합 엔진+덱 스키마 → M2 키보드 뷰+연습 화면 → M3 덱 발견·다운로드 → M4 게임 모드 → M5 커리큘럼+리텐션 → M6 폴리싱+OS 키보드 모드 → M7 Android 포팅
- iOS 선행. 각 마일스톤 종료 시: 테스트 통과 + DECISIONS.md 갱신.

## 프로젝트 구조 (목표)
```
hanco/
  PRD.md, AGENTS.md, DECISIONS.md
  shared/                  # 플랫폼 공용 스펙·픽스처 (테스트 벡터, 덱 JSON Schema, 목 카탈로그)
  tools/                   # 생성기 스크립트 (Python)
  ios/Hanco/               # Xcode 프로젝트 (SwiftUI, iOS 16+)
    Core/HangulEngine/     # 순수 로직 — UI 의존 금지, 테스트 최우선
    Core/DeckKit/          # 덱 모델, 카탈로그 클라이언트, 검증
    Features/...           # 화면 단위
  android/                 # M7 Kotlin/Compose 앱 (API 26+)
```

## 철칙
1. **조합 엔진은 순수 함수 상태 기계** (PRD §6). UI 코드와 섞지 마라. `shared/test_vectors.json` 전 케이스 통과가 M1의 완료 조건.
2. **판정은 자모 시퀀스 기준** (완성 음절 비교 금지) — 도깨비(이월) 케이스 때문. PRD §6.3.
3. **세션(레슨/게임) 도중 어떤 모달·인터럽트도 금지.** 광고는 아직 없지만 구조적으로도 넣을 수 없게 설계 (PRD §11).
4. 사용자 노출 문자열은 일본어 1순위, 전부 로컬라이제이션 리소스로. 하드코딩 금지.
5. 효과음과 목표 발음은 `.playback`; 둘 다 `mixWithOthers` — 기본 ON으로 무음 모드에서도 재생하되 설정에서 즉시 OFF할 수 있어야 하며, 다른 앱 음악을 끊으면 안 됨 (PRD F11, 경쟁 앱 버그 이력).
6. 백그라운드 전환 시 타이머 일시정지 (PRD F4 AC, 경쟁 앱 버그 이력).
7. 카탈로그는 읽기 전용 정적 JSON (PRD §8.3). 쓰기 API·계정 만들지 마라. 개발 중엔 `shared/mock_catalog/` 픽스처 사용.
8. 60fps: 게임 모드 파티클 포함 (PRD F6 AC). Instruments로 검증.
9. **앱 제공 고정 한국어 발음은 앞으로도 gTTS 오프라인 MP3만 사용한다** (PRD F6d, F11). 공식·업데이트 덱, 게임 프리셋, 커리큘럼·데일리, 무료 샘플의 목표 문구를 추가·수정하면 `gTTS==2.5.4`, `lang=ko`, `tld=com`, 보통 속도로 사전 생성하고 `audio/ko_<한국어 UTF-8 SHA-256 앞 20자리>.mp3` canonical 경로를 사용한다. 비덱 고정 문구는 `shared/mock_catalog/pronunciation_prompts.json`에도 등록한 뒤 `python3 tools/gen_gtts_audio.py --prune`과 `python3 tools/release_preflight.py`를 통과시킨다. macOS/Yuna 음원·발음용 CAF·앱 런타임 gTTS/네트워크 호출·새 고정 문구의 기기 TTS 전용 처리는 금지한다. `AVSpeechSynthesizer`는 자산 누락·손상 및 번들에 없는 사용자/비공개 동적 콘텐츠의 최종 폴백으로만 유지한다. 제공자나 고정 설정 변경은 사용자 승인과 PRD·DECISIONS 갱신 없이는 하지 않는다.

## 커맨드
- 테스트 벡터 재생성: `python3 tools/gen_test_vectors.py`
- 앱 제공 발음 생성·정리: `python3 tools/gen_gtts_audio.py --prune`
- 저장소 발음 자산 계약 검증: `python3 tools/release_preflight.py`
- iOS 앱 테스트: `xcodebuild test -project ios/Hanco/Hanco.xcodeproj -scheme Hanco -destination 'platform=iOS Simulator,name=iPhone 17,OS=26.5'`
- M1 패키지 테스트: `cd ios/HangulEngine && swift test`
- Android M1 공용 코어 테스트: `cd android && ./gradlew :core:hangul:jacocoTestCoverageVerification :core:deckkit:test :core:piyodeck:test`
- Android M2 회귀·계측 APK: `cd android && ./gradlew :core:session:test :feature:practice:testDebugUnitTest :feature:practice:assembleDebugAndroidTest :feature:practice:lintDebug :app:lintDebug :app:assembleDebug`
- Android M2 자동 pointer gate: `cd android && ./gradlew :feature:practice:connectedDebugAndroidTest`
- Android M2 물리 입력 gate(API 29+ 실기기): `cd android && ./gradlew :feature:practice:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.piyokeyPhysicalGate=true`
- M4 실기기 게이트: `python3 tools/m4_ios_performance_gate.py inspect-device --device <UDID>` 후 `prepare`·`record` 서브커맨드 사용 (`artifacts/m4/README.md` 참고)

## 테스트 실행 범위
- 일반 기능 수정은 변경한 기능의 단위 테스트와 직접 관련된 UI 시나리오만 `-only-testing:<Target>/<Suite>/<Test>`로 실행한다. 이미 빌드가 끝났으면 `xcodebuild test-without-building`을 우선한다.
- 공통 상태·저장 스키마·조합 엔진처럼 영향 범위가 넓은 변경은 직접 의존하는 복수 스위트까지 넓히되, 무관한 전체 UI 회귀를 매번 실행하지 않는다.
- 전체 iOS 단위·UI 테스트는 마일스톤 종료, 출시 후보 검증, 공통 기반 대규모 변경 또는 사용자의 명시적 요청 때만 실행한다.

## 지금 상태
- M6 최신 이동 카드: 흐름·산성비 단어 카드는 한국어·일본어 뜻·가타카나 발음 중 가장 긴 내용에 맞춰 폭을 줄이고, 긴 항목만 기존 레인 상한까지 확장한다. 뜻 아래에는 대괄호로 감싼 `reading_ja`를 표시하며 장식 아이콘은 넣지 않는다. 긴 자모열은 화면 전체 폭을 확장하지 않는 내부 가로 트랙에서 현재 자모를 자동 추적하고, 내장 키보드는 항상 기기 폭 안에 고정한다.
- M6 최신 게임 UI: 초성 맞추기·단어 맞추기·받아쓰기는 흐름·산성비와 같은 `닫기 | 문제 수·점수·콤보` 상단 HUD, 중앙 문제 레인, 입력 상태, 하단 고정 키보드 구조를 사용한다. 공통 HUD는 아이콘 안전 영역과 수치 영역을 분리하고 긴 남은 시간·점수·콤보·목숨·문제 수를 단계적·가변 축소해 서로 겹치지 않게 한다. 플레이 중 덱 제목·세션 설정·입력 방식 변경 컨트롤은 노출하지 않으며, 설정에서 선택한 기본 입력 방식을 세션 시작 시 확정한다. 세 모드는 모두 일반 연습형 `피요 + 원형 조합 프리뷰 + 入力中` 입력 카드에서 누적 입력과 정타·오타·완료 피드백을 보여 주며 문제 카드 안의 중복 피요는 제거한다. 초성 맞추기는 중앙 초성을 음절 진행에 맞춰 현재·완료 색과 스프링으로 갱신하고 큰 일본어 뜻 워드박스를 사용한다. 일본어 뜻은 기본 ON이고 설정의 게임 표시에서 끌 수 있으며, 정답 완성 시 중앙에 맞춘 한국어 단어를 약 1.35초 노출한 뒤 자동 전환한다. 단어 맞추기는 일본어 뜻을 필수 문제 단서로 표시하고 정답 한글 전체를 직접 입력한 뒤 0.65초에 전환한다. 받아쓰기는 정답·초성·뜻·읽기를 사전 노출하지 않고 0.65초 전환을 유지한다.
- M6 최신 게임 조정: 모든 연습·게임 피요는 옷장의 사용자 선택을 세션 내내 유지하고 `자동`에서도 덱·카드 태그 기반 소품을 장착하지 않는다. TOPIK 안경은 TOPIK 게임에서 0점 이상을 기록하면 영구 해금한 뒤 옷장에서 직접 선택한다. 흐름·산성비의 플레이 피요는 몸체를 정면으로 고정하고 눈동자로만 카드를 추적해 다른 화면과 같은 실루엣 비율을 유지한다. 두 모드는 3목숨을 표시하고 세 번째 카드를 놓치면 60초 전이라도 종료한다. 카드가 바뀔 때마다 플레이 경과 0~50초 동안 흐름은 시작 대비 최대 1.8배, 산성비는 최대 1.5배까지 점진 가속한다. 흐름의 새 점수 조건은 Game Center `piyokey.v4.flow.*`와 `piyokey.v4.cup.weekly.flow` 계약으로 분리했다.
- M6 최신 산성비: 첫 카드가 바닥에 닿기 전 이동 시간의 약 42% 간격으로 다음 카드를 생성해 최대 4장이 3개 레인에서 동시에 낙하한다. 내장 키보드는 현재 입력 중인 가장 위험한 카드를 강조·고정하고 완료·이탈 뒤 진행률이 가장 높은 카드로 이어진다. OS 키보드는 화면에 낙하 중인 단어 중 어떤 것이든 완성 입력과 일치하면 해당 카드를 완료한다. 각 카드는 독립적으로 위험선 도달과 목숨 차감을 판정한다.
- iOS M1 완료: `ios/HangulEngine` Swift 패키지, 콘텐츠 스키마/검증기, 공식 26덱 카탈로그와 별도 게임 프리셋 15덱이 구현·검증됨.
- M2 완료: 연습 전 영속 설정, 3문제 흐름, 내장 두벌식 키보드, 조합 프리뷰, 정타·오타·완료 연출과 초기 마스코트가 구현됨. 초기 펭귄 에셋은 M6에서 Swift 벡터 병아리 성장 시스템으로 교체됨.
- M3 완료: 발견·검색·필터·상세·다운로드·오프라인 내 덱·정적 HTTP/캐시·v2 업데이트, 삭제 후에도 유지되는 다운로드 태그 이력, 홈 추천 3개, 결과의 동일 태그 추천 2개와 1탭 재시작이 구현·검증됨. 기준 이미지 5장과 성공 흐름 영상은 `artifacts/m3/`에 저장됨.
- M4 기능 완료: 설치 덱 선택, 자동 코스, 60초 흐름 모드, 점수·콤보·카드 이탈, 60Hz 카드·파티클, 백그라운드 타이머 정지, 콘텐츠 기반 랭크, GameRecord·DeckProgress·최고 기록, 레슨·게임 공통 F12 결과와 2.5초 연출·안전한 탭 스킵·1탭 재도전이 구현·검증됨. iPhone 15 Pro 보조 검증은 Hanco hang·hitch 0건, 파티클 2회 직후 평균 59.8 FPS·p95 16.7ms. 사용자의 명시적 승인으로 iPhone 12 측정은 M6 종료 전 출시 게이트로 이관됨.
- M5 완료: F4 6챕터 커리큘럼·별점·순차 해금·중단 복구·백그라운드 타이머 정지, F7 복습 자동 수집·3회 졸업·결과 1탭 복습, F8 JST 스트릭·7일 스탬프·로컬 시드 5단어 데일리·기본 OFF 시간 지정 리마인더, F5.6 홈/결과 추천이 구현·검증됨. iOS 95개·SwiftPM 13개 테스트와 Release 빌드 통과, 기준 이미지·성공 영상은 `artifacts/m5/`. 다음은 M6 폴리싱+OS 키보드 모드이며, 온보딩에서 목표 기반 추천·리마인더 제안을 연결하고 M6 종료 전 iPhone 12 성능 게이트를 수행해야 함.
- M6 진행 중: F1 온보딩, F2a OS IME, F9 공유, F10 설정, F11 사운드와 F12 종료 순서가 구현됐다. S5의 모든 문제는 성공 연출 0.65초 뒤 버튼 없이 전환하고 마지막 문제는 결과 화면으로 자동 진입하며, 완료 음절은 초록 키캡·체크로 즉시 표시된다. 이미지 기반 펭귄은 제거되고 `hantap`의 Swift 벡터 병아리와 성장 시스템이 커리큘럼·스트릭·덱 태그·복습·게임 기록에 연결됐다. 외부 캐릭터 시트의 추가 표정·덱 소품·성장축·idle/이벤트 반응을 반영하고 게임 플레이에도 카드 추적 3/4 포즈와 콤보·오타·긴급 시간·개인 최고 반응을 넣었다. 옷장은 자동 컨텍스트 소품·없음·고정 아이템과 최종 모습 미리보기, 조건이 사라져도 유지되는 영구 소품 해금을 제공하며 날짜·계절·기념일 기반 장식은 제거됐다. 사용자 노출 브랜드는 `ピヨキー / PIYOKEY / 피요키`로 확정하고, AppIcon과 앱 내부·공유 카드의 브랜드 로고는 사용자가 선택한 음영이 있는 흰색 `ㅎ` 키캡 병아리 이미지로 통일했다. 성장 캐릭터는 기존 Swift 도형을 유지한다. AppIcon 생성기는 공용 3D 원본을 1024×1024 무알파 PNG로 정규화하고 검정 출력 회귀를 검출한다. 하단 GNB는 홈·둘러보기·연습·게임·마이페이지 순서이며, 홈은 스탬프·하루 3분·온보딩 목표/다운로드 이력 기반 추천만 표시한다. 커리큘럼과 자유 연습은 별도 연습 탭으로 분리했으며 설정은 모든 탭의 우측 상단 44pt 탭 영역 공통 시트로 이동했다. 게임 탭 첫 화면은 덱을 숨긴 `흐름·산성비 / 초성·받아쓰기 / 단어 맞추기·띄어쓰기` 순서의 2열 게임 카드 허브이고, 흐름·산성비·초성·단어 맞추기·받아쓰기는 모두 모드별 TOPIK I 1~2급·TOPIK II 3~4급·TOPIK II 5~6급 기준 100단어 전용 세트 3개와 덱 검색 `+` 카드를 먼저 제공하며 검색에서 받은 덱은 추가 선택지로 표시한다. 플로우는 초기 라이트 레인으로 단순화했고, 산성비는 낙하 중 판독에 맞춰 단계별 단어 길이를 높인다. 초성은 고유 초성열에서 동일 초성 쌍과 긴 전문어로, 단어 맞추기는 친숙한 뜻에서 추상어로 의미·타이핑 난이도를 함께 높이고, 받아쓰기는 명료한 기본어에서 받침·발음 변화어로 난이도를 높인다. 초성 맞추기는 정답 전체 직접 입력·동일 초성 무료 뜻 힌트를, 단어 맞추기는 일본어 뜻 기반 한글 전체 직접 입력을, 받아쓰기는 텍스트 단서 없는 자동 발음·직접 입력을 유지한다. 다섯 게임은 자동 전환·복습 수집·피요 반응·덱별 독립 최고 기록을 지원한다. 게임 시작·재도전은 3·2·1 카운트다운 뒤 타이머가 시작되고, 마이페이지는 성장 기록·타이핑 분석·설정과 기존 내 덱 빈 상태 발견 CTA·3종 정렬을 함께 제공한다. 로컬 JSON은 최신 검증 백업·손상 격리·개별 덱 부분 복구·미래 스키마 보존 정책을 적용한다. 세 언어 로컬라이제이션과 iOS·SwiftPM 테스트, generic Release와 개발 서명 archive를 유지한다. App Store 제출용 Required Reason API 매니페스트와 자동/수동 출시 게이트도 추가됐다. 최신 소스는 App Store Connect에 `1.0.1 (4)`로 업로드되어 처리 중이며, 다음은 TestFlight 새 설치·한국어 IME·TTS·외부 음악 혼합 청취와 이관된 iPhone 12 성능 게이트다.
- M6 출시 상태 정정(2026-08-10): 위 `1.0.1 (4)` 처리 중 기록 이후 `1.0.2 (5)`를 아카이브·업로드하고 iOS 앱 버전과 Game Center 리더보드 4개를 같은 제출로 전송했다. App Store Connect 제출 ID `722d6568-1321-4b44-838f-138f07ef8647`은 `Waiting for Review`이며 승인 후 자동 출시로 설정했다. 사용자가 명시적으로 면제한 TestFlight 실기기 청취·입력 점검과 iPhone 12 성능 게이트는 완료로 간주하지 않고 열린 항목으로 유지하며, 주간 리더보드 기본 지정은 해당 리더보드가 Live가 된 뒤 수행한다.
- M6 출시 상태 정정(2026-08-11): 위 build 5 제출은 Game Center 표시·동기화 경합과 MainActor 저장 병목을 수정하기 위해 심사 시작 전에 취소했으며, 현재 `Removed / Developer Rejected`다. 수정본 `1.0.2 (6)`은 전체 회귀 321/321, Distribution archive·IPA 검증, Organizer 업로드와 TestFlight Game Center 실기기 확인을 완료했다. 제출 ID `45184f9b-494c-431e-a740-a3dde9080f4a`로 iOS 앱과 Flow v4 3개·Weekly Piyo Cup v4를 전송했으며 5개 모두 `Waiting for Review`다. 수동 hold는 해제했고 승인 후 phased release 없이 전 사용자에게 즉시 자동 출시한다.
- 앱 1.1 구현 베이스 완료(2026-08-14, build 7·미제출): `.piyodeck` v1 규격·fixture·Swift/Python reader/writer/validator, 무료 가져오기·연습/게임·내보내기·삭제·충돌 교체·재가져오기, 진행/복습 이력 보존, StoreKit 2 비소모성 `app.piyokey.deckmaker.lifetime` 기반 모바일 생성·편집·공식 덱 사본 저장을 구현했다. 사용자 덱은 로컬-only·무계정·비공개이며 세션 중 파일/충돌/편집/결제 모달을 띄우지 않는다. SwiftPM 36개, 관련 iOS 단위 테스트 94개, 결제 경계·안전 UX UI 회귀 5개와 캡처 시나리오 2개, Python 도구/프리플라이트 13개가 통과했다. App Store Connect 상품 생성·실판매 가격/세금/Family Sharing·심사 스크린샷, Sandbox 결제/복원/환불, TestFlight 실기기 Files·iCloud Drive·AirDrop 및 1,000항목 성능은 R1.1 출시 전 수동 게이트로 남아 있다(PRD F5.9, §8.4, R1.1).
- 앱 1.1 안전 UX 보완(2026-08-14): 단일 활성 편집 초안의 primary/backup 자동 저장·재실행 복구·다른 흐름 시작 전 재개/폐기 선택, 설치 transaction 내부 `base_version` 원자 비교와 원본 변경 시 별도 사본 저장, 현재본/가져온 파일 충돌 비교·내보내기·파괴적 재확인, 삭제 확인·내보낸 뒤 삭제·실패 복구, 첫 검증 오류 자동 포커스·VoiceOver 안내, 1,000항목 접이식 편집, Dynamic Type·좁은 폭·텍스트 대비 대응을 추가했다. Debug/Release Simulator 빌드에서 검증하고 Release 앱 번들에서 Debug 전용 `.storekit`을 제외한다.
- iOS 공개 상태 확인(2026-08-21): Apple 공개 lookup의 일본 storefront에서 버전 `1.0.2`가 2026-08-18 출시된 상태를 확인했다. 저장소의 build 6 심사 대기 기록은 운영 상태보다 오래됐고, 로컬 `1.1 (7)`은 Deck Maker와 `.piyodeck`을 포함한 다음 업데이트용 미제출 기준선으로 유지한다.
- M7 재개·A0 로컬 기반 완료(2026-08-21): 사용자의 명시적 요청으로 Android HOLD를 해제하고 `android/`에 Gradle 9.5 wrapper, AGP 9.3.1, Kotlin/Compose compiler 2.3.21, Compose BOM 2026.08.00, min 26/compile 37/target 36의 Compose 앱 골격과 순수 Kotlin Hangul core를 추가했다. 공용 벡터는 생성기 기준 composition 15종+backspace 10종으로 보강했다. API 37은 기존 수락 license를 Gradle이 확인해 자동 설치됐고 별도 수락 명령은 실행하지 않았다. 대규모 dirty worktree의 baseline commit/tag, 최종 application ID·launcher icon, Android Studio/JDK 17, Play Console 상태는 열린 A0 게이트다.
- M7 M1 공용 코어 완료(2026-08-22): Android 순수 Kotlin `core:hangul`·`core:deckkit`·`core:piyodeck`을 구현하고 공식 26덱, 게임 프리셋 15덱×100항목, v10→v11 전체 snapshot, `.piyodeck` v1 strict ZIP/JSON/사용자 덱 정책을 공용 fixture로 검증했다. Python·Swift·Kotlin writer는 1,109-byte canonical package와 동일하고 세 reader는 pretty package를 수용하며 공유 SHA·Unicode 공격 package를 거부한다. Kotlin 35개, SwiftPM 42개, Python 49개와 release preflight, Android lintDebug·assembleDebug가 통과했으며 Hangul line 99.68%·branch 95.78%다.
- M7 M2 기능·계측 하네스 준비(2026-08-23): 순수 Kotlin `core:session` reducer와 Compose 내장 두벌식 키보드·연습 화면을 구현했다. JVM tests는 전체 55/55, API 35 AVD 자동 instrumented gate는 실제 다중 MotionEvent·frame commit 상관 1 pass이고 물리 gate 1개는 skip됐다. API 29+ 실제 기기에서 warm-up 20·측정 100의 touch-down→frame-commit proxy p95≤50ms와 손가락 2-pointer rollover 누락·중복 0을 JSON으로 증명하기 전에는 M2를 완료 처리하거나 M3를 시작하지 않는다.
