# 언어 추가 체크리스트 — es/de/fr 로컬 후보

> 2026-08-29 후속: 사용자 승인으로 #73/PR #74에 5언어·온보딩 통합 중이다. 아래는 로컬 후보 당시의 검수 기록이며 최신 앱 빌드/기기 검증은 `LANGUAGE_COVERAGE.md`와 #73 SHA 증빙을 따른다. 원어민·실기기·스토어/카탈로그 게시 gate는 유지한다.

갱신: 2026-08-28 JST
기준 main: `47371de950dcb28530f69b91ec596f53e2818ab3`
상태: **소스·공용 코어 로컬 검증 완료 / 전체 앱·현지어 사람 검수·원격 등록·출시 gate 미완료**

다음 언어를 추가할 때도 아래 A–H를 한 작업에서 갱신한다. 체크는 이번 es/de/fr 작업에서 확인한 범위만 뜻한다. UI 테스트 코드를 작성한 것과 실제 화면에서 통과한 것은 구분한다.

후속 전체 검수의 수정 사항과 남은 개선은 [언어 검수 결과](LANGUAGE_REVIEW.md)를 참조한다.

## 이번 변경

| 영역 | 로컬 반영 내용 |
|---|---|
| iOS UI | ja/en/es/de/fr 각 1,108개 키. de/fr 전체 번역, es에서 영어로 남아 있던 기본 학습 뜻 37개 보완 |
| Android UI | 8개 모듈의 de/fr 전체 문자열·2개 배열. 언어 선택 자칭 추가 |
| 공식 학습 콘텐츠 | 각 es/de/fr에 627개 고유 `(ko, meaning_ja)` 의미 쌍. 한국어 목표·공통 로마자 유지 |
| 공식 메타데이터 | 덱 이름 41개·태그 42개·공식 작성자, 카탈로그 미리보기·게임 preset·업데이트 |
| 언어 선택·편집 | 지역 변형 인식, 선택 유지, iOS 세로 선택 목록, Android FlowRow, 해당 언어 덱 편집 |
| 저장·가져오기 | Swift/Kotlin/Python/JSON schema 동시 확장, 불완전 번역 검증, 새 언어 공용 `.typedeck` golden |
| 운영 소스 | PRD·DECISIONS·상태·분석 enum·스토어 문구/FAQ 초안·촬영 매핑·회귀 테스트 |

627은 사전의 고유 의미 쌍 수다. 기본 카탈로그의 26개 덱·312개 항목과는 다른 집계다. 신규 한국어 문제나 발음을 추가한 작업이 아니다.

## A. 지원 계약·등록·선택

- [x] PRD·DECISIONS에 지원 UI/콘텐츠 언어와 fallback, 지역 변형 범위를 명시한다.
- [x] iOS enum·CFBundleLocalizations·knownRegions·Localizable/InfoPlist 리소스 등록을 함께 갱신한다.
- [x] Android enum·localeFilters·locales_config·각 모듈 리소스를 함께 갱신한다.
- [x] 언어 이름은 日本語 / English / Español / Deutsch / Français로 표시하고 긴 이름을 작은 segmented picker에 넣지 않는다.
- [x] es-ES/MX/419, de-DE/AT/CH, fr-FR/CA/BE/CH를 기본 코드로 인식하는 코드·회귀를 추가한다. Android 설정 단위 검증 통과; iOS 설정 단위/UI 실행은 아래 gate에 남긴다.
- [x] 명시적 선택 유지, 미지원 언어→en, 과거 ko UI→en 정책을 유지한다. 학습 진행·구매 저장소를 변경하지 않는다.
- [ ] 양 플랫폼에서 첫 설치·기기 언어 변경·명시적 선택·재시작·진행 보존을 실제 화면으로 검증한다.

## B. UI·온보딩·학습 안내

- [x] 탭·설정·빈 화면·로딩·오프라인·오류·성공, 온보딩·튜토리얼, 게임 규칙·힌트·결과·복습을 번역한다.
- [x] 자음·모음·받침·한글 결합·Shift/Space/Backspace·두벌식 설명, 발음 버튼과 실패 안내를 번역한다.
- [x] 알림 권한/제목/본문, Pro 혜택·한도·구매/복원·실패/취소·사진 권한·접근성 문구를 번역한다. 가격/통화의 플랫폼 공급 계약은 유지한다.
- [x] 한국어 목표·키캡·정답·자모 판정·테스트 벡터·고정 MP3를 유지한다. UI 번역만으로 새 TTS를 도입하지 않는다.
- [ ] iOS/iPadOS/Android 실제 현지어 OS 메뉴명, 권한 안내와 알림 수신을 확인한다.
- [ ] VoiceOver/TalkBack 이름·상태·입력 가이드, 결제/복원 흐름을 각 언어로 확인한다.

## C. 공식 단어·예문·덱

- [x] 기본 레슨 뜻과 공식 콘텐츠를 번역한다. 사전 627쌍 전체·중복·빈 값·미리보기·업데이트 누락 검사를 둔다.
- [x] 덱 이름·태그·공식 작성자를 번역한다. 내부 정규 태그와 기존 ja/en/ko 값·ID·순서는 보존한다.
- [x] 공통 로마자를 유지하고 자모 조합·한국어 원문은 번역하지 않는다.
- [x] 공식 콘텐츠 영어 fallback을 번역 완료로 취급하지 않는다. 사용자 덱의 미제공 번역은 영어 fallback이며 원문을 자동 번역하지 않는다.
- [ ] 원어민이 스페인어 말투·인칭, 독일어 명사/관사, 프랑스어 성/수·관사, 동음이의어와 학습 맥락을 검수한다. 현재 번역은 사람 검수를 받지 않은 초안이다.
- [ ] 검색·태그 추천·필터·게임의 실제 표시를 각 언어로 확인한다.

## D. schema·저장·파일·배포 호환

- [x] Swift/Kotlin/Python/JSON schema를 같이 갱신하고 en/es/de/fr 지원 메타데이터 선언 시 전체 항목·미리보기를 요구한다. 기존 ko 호환 규칙은 유지한다.
- [x] 편집 언어 매핑과 다른 언어/ID 보존 회귀를 추가한다. Android 편집 코어 통과; iOS 앱 편집 테스트는 실행 gate가 남는다.
- [x] 기존 ja/en/ko 파일과 새 번역 파일의 native reader/writer를 검사한다. Python·Swift·Kotlin이 같은 `localized.typedeck` golden을 읽고 동일 바이트로 쓴다.
- [x] 생성기로 카탈로그·업데이트·size_bytes를 재생성한다. `.typedeck`는 manifest SHA-256도 검증한다. 카탈로그 자체에 없는 SHA 필드를 있다고 가정하지 않는다.
- [x] 구버전 호환 정책을 정한다: **새 es/de/fr 카탈로그를 기존 URL에 덮어쓰지 않는다.** 별도 완전한 namespace(후보 `catalog/v2/`)에 게시하고 새 앱에만 연결한다.
- [x] package format/schema version 1을 유지한다. 새 앱은 기존 파일을 읽지만 새 locale가 있는 파일을 받는 구버전 앱은 업데이트가 필요하다.
- [x] 공식 카탈로그 덱 버전은 증가시킨다. 게임 preset v3는 한국어 목표/ID/점수·랭킹 조건이 같아 유지하고 새 번들/namespace에만 배포한다.
- [ ] 새 namespace 게시·접근·구버전 URL 보존·신규 앱 연결을 검증한다. 현재 원격 카탈로그를 게시하거나 앱 URL을 바꾸지 않았다.
- [ ] iOS↔Android 실제 저장→재시작→편집→Files/공유→가져오기와 업데이트·진행 무손실을 확인한다.

## E. 서식·접근성·레이아웃

- [x] 문자열 키·빈 값·`%@`/`%d`/`%1$s` 타입·순서·배열 길이를 검사한다. 요일 배열은 값 중복과 무관하게 위치를 유지한다.
- [x] Apple strings 파싱·중복 키와 Android AAPT2 리소스 컴파일을 검사한다.
- [x] iOS 주요 수량 17개 키의 stringsdict와 선택 locale 서식, Android 항목/복습/주간 스트릭 plurals를 적용한다. 실제 Foundation 340개 수량 렌더링과 5개 언어 소수점 검사를 통과했다.
- [ ] 두 수량이 있는 통계·공유 문구와 게임 힌트의 복수형을 확장한다. 이번 주요 수량 검증으로 전체 문법 검수를 대신하지 않는다.
- [ ] 0/1/2/여러 개, 복수형·숫자·백분율·시간·날짜·실제 가격 표시를 사람이 확인한다.
- [ ] 독일어 장문·프랑스어 아포스트로피/악센트·스페인어 부호, 작은 iPhone·iPad 회전/분할·좁은 Android·최대 글자 크기에서 잘림/겹침을 검사한다.
- [ ] 언어별 첫 실행→첫 입력→연습→6개 게임→결과→덱 생성/복원 스모크와 세션 무중단을 확인한다.

## F. 분석·개인정보·지원

- [x] 기존 opt-in 분석의 locale/value_bucket enum과 생성 Swift/Kotlin/TypeScript·runtime allowlist를 함께 갱신한다. 새 이벤트·개인정보·동의 기본값은 추가하지 않는다.
- [x] es/de/fr 지원 FAQ(언어 변경·키보드·fallback·구매/복원·파일 공유) 초안을 준비한다.
- [ ] 지원/개인정보 공개 페이지의 언어·브랜드·연락처, 실제 링크를 검수한다. 법률 문구 확정/번역 검토는 별도 담당 gate다.

## G. 스토어·미디어

- [x] UI 언어와 스토어 로케일을 분리한다. `release/store-assets/localizations.json`에서 es-ES→es, de-DE→de, fr-FR→fr, ko→en을 지정한다.
- [x] `release/language_expansion_store_draft.json`에 이름·부제·설명·키워드·Play 짧은 설명·업데이트 노트·Pro·FAQ를 작성하고 저장소의 글자 수 제한을 검사한다.
- [x] 촬영 도구 기본값과 iOS capture 테스트를 ja/en/es/de/fr로 확장한다. 기존 media와 verification은 역사적 증빙으로 보존하고 recapture_required로 표시한다.
- [ ] 현지어 검수 후 초안을 각 운영 metadata에 반영하고 App Store/Play 필수 필드와 실제 상품 가격·기능을 대조한다.
- [ ] 최종 RC와 같은 빌드에서 es/de/fr 스크린샷·영상 재촬영, 자막·잘림·전체 디코딩 검증을 수행한다.
- [ ] Console에 저장·업로드하고 재조회한다. 소스 완료·현지어 검수·스토어 업로드·공개를 각각 기록한다.

## H. 검증·소유권·인계

- [x] 원본 dirty 작업공간은 보존하고 별도 clone `outputs/local-language-expansion`, branch `codex/local-es-de-fr`에서 Codex 단독 작업을 수행한다.
- [x] Python 96개, Swift 공용 코어 45개, Kotlin 공용/설정·발견 코어 63개, 저장소 preflight를 통과한다. Swift/Kotlin은 설치된 컴파일러 + XCTest/JUnit 직접 실행이며 전체 앱 빌드가 아니다.
- [x] 변경 iOS 소스 parse, strings 파싱, Android 8개 모듈 리소스 컴파일을 확인한다.
- [ ] 정상 SwiftPM·Xcode 앱/설정/편집/UI 테스트·Android Gradle lint/assemble·관련 계측을 통과한다. 현재 SwiftPM은 sandbox 생성, Xcode는 cache/CoreSimulator 접근, Gradle은 로컬 socket 권한에서 차단됐다. 보호 설정을 해제하지 않았다.
- [ ] 원격 작업 재개 시 실제 Issue를 생성하고 소유권 claim·workspace doctor·Issue/Project/PR를 갱신한다. 로컬 우선 사용자 요청으로 현재 보류했으며 가짜 Issue는 만들지 않았다. PR #70의 일회성 CI 예외는 새 작업에 적용하지 않는다.

## 수정 경로와 재검증 명령

| 대상 | 단일 변경점/생성물 |
|---|---|
| iOS UI | `Core/Settings/AppSettings.swift`, `Features/Settings/SettingsView.swift`, `Resources/*.lproj`, Xcode project·Info.plist |
| Android UI | `core/settings/.../AppPreferences.kt`, locales_config·localeFilters, 각 모듈 `values-*` |
| 콘텐츠 | `shared/content_localizations/`, `tools/gen_mock_catalog.py`, 생성 `shared/mock_catalog/` |
| 편집·조회 | iOS DeckEditor/CurriculumCatalog, Android MainActivity/DeckMakerScreens/UserDeckDraft/DeckKit Models |
| 계약 | `shared/schema/`, 양 플랫폼 DeckKit validation, `tools/piyodeck_tool.py`, backoffice |
| 분석·출시 | `shared/analytics/events.json`, `tools/gen_analytics_contract.py`, `release/*metadata*`, 스토어 초안·촬영 설정 |

```sh
python3 tools/gen_mock_catalog.py
python3 tools/gen_piyodeck_fixtures.py
python3 tools/gen_analytics_contract.py
python3 -m unittest discover -s tools/tests
python3 tools/release_preflight.py
# 정상 실행 권한이 있는 개발 환경에서 다음 앱 gate를 수행한다.
(cd ios/HangulEngine && swift test)
xcodebuild test -project ios/Hanco/Hanco.xcodeproj -scheme Hanco -destination 'platform=iOS Simulator,name=iPhone 17,OS=26.5'
(cd android && ./gradlew :core:deckkit:test :core:piyodeck:test :core:settings:testDebugUnitTest :core:data:testDebugUnitTest :app:lintDebug :app:assembleDebug)
```

로컬 코어 직접 실행·로그·원본 보존 감사는 `artifacts/language-review/README.md`와 이전 확장의 `artifacts/language-expansion/README.md`를 참조한다. 체크되지 않은 항목은 출시 전 인계 목록이며 로컬 구현 완료와 혼동하지 않는다.
