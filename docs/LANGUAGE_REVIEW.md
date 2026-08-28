# 언어 검수 결과 — 로컬 후보

> 2026-08-29 후속: 사용자 승인으로 #73/PR #74에 5언어·온보딩 통합 중이다. 아래는 로컬 후보 당시의 검수 기록이며 최신 앱 빌드/기기 검증은 `LANGUAGE_COVERAGE.md`와 #73 SHA 증빙을 따른다. 원어민·실기기·스토어/카탈로그 게시 gate는 유지한다.

검수 기준: `dcbc5dc`의 es/de/fr 확장 후보. 2026-08-28, Codex 단독 검수.

## 범위와 판정

ja/en/es/de/fr UI 리소스, 공용 콘텐츠 사전 627개 의미 쌍, 공식 덱·카탈로그·업데이트, locale 선택/저장, 검색·편집·가져오기, 숫자/복수형, 알림, 브랜드·스토어 매핑을 점검했다. 구조 검사는 전체 대상에 적용했고 번역의 문맥 오류를 추가로 찾아 수정했다. **원어민 사람 검수나 전체 앱 화면 QA를 완료한 것은 아니다.**

기존 root의 dirty 작업은 보존했다. 모든 수정은 `outputs/local-language-expansion`, `codex/local-es-de-fr`에만 있으며 GitHub·카탈로그·스토어 게시를 하지 않았다.

## 수정한 문제

| 우선순위 | 발견 사항 | 수정과 검증 |
|---|---|---|
| P1 | iOS 덱 상세와 파일 가져오기 화면의 `Items`가 es=`Objetos`, de/fr=`Accessoires`로 번역되어 학습 항목과 꾸미기 소품이 섞임 | 각 키를 `Elementos` / `Einträge` / `Éléments`로 분리. 같은 영어 값이라도 키의 문맥을 구분하는 회귀 추가 |
| P1 | Android 검색은 악센트가 다르면 `cafe`로 `café`를 찾지 못함 | 라틴 문자 악센트·ß/œ/æ를 검색에서만 정규화. 일본어 탁점과 한국어 원문은 보존. 실제 카탈로그의 es/de/fr 검색·정규 태그 회귀 통과 |
| P1 | 프랑스어가 추가된 뒤에도 Android 발견 테스트가 `fr`를 영어 fallback 언어로 가정 | 미지원 `ar`로 해당 테스트를 바로잡고 `fr-CA`의 프랑스어 검색을 별도 검증 |
| P2 | iOS 일부 숫자가 선택 언어와 무관한 기본 포맷으로 표시됨 | `AppLocalization.format`으로 현지화된 printf 경로를 통합. 소수점·통계 숫자를 앱 locale로 포맷하고 플랫폼이 제공한 결제 가격 문자열은 재계산하지 않음 |
| P2 | `1 éléments`, `1 días` 등 단수에도 복수형 사용 | iOS 주요 개수·스트릭 17개 키에 native stringsdict, Android 항목/복습 개수·주간 스트릭에 native plurals 추가. 실제 Foundation의 340개 수량별 렌더링과 5개 언어 소수점 검사 통과 |
| P2 | iOS 언어를 변경해도 이미 예약된 반복 알림 본문이 갱신되지 않음 | 활성 알림만 같은 시간에 다시 예약하는 refresh를 AppRoot 언어 변경에 연결. 꺼진 알림을 켜지 않는 회귀 추가. 앱 테스트 실행은 아래 gate에 남음 |
| P2 | 일부 개인정보 안내·알림·공유·메일·덱 오류에 이전 `PIYOKEY` 브랜드 노출 | 활성 ja는 `ピヨキー`, en/es/de/fr은 `typee`로 정리. 내부 SDK·파일 format identifier와 기존 ko 콘텐츠는 유지 |
| P2 | 독일어의 재회 질문이 “자기 자신을 다시 만나기”로 읽힘. 프랑스어 일부 구어체 뜻도 학습자에게 더 명확하게 표현할 여지가 있음 | 독일어 2개·프랑스어 3개 의미를 문맥에 맞게 수정하고 생성기로 원본/카탈로그/업데이트를 일치시킴 |
| P2 | 출시 gate 문서가 프랑스어를 미지원으로 분류하고 새 미디어 재촬영 필요 상태를 놓침 | de/fr 브랜드와 지원 언어 범위·콘텐츠 검증·재촬영 gate를 바로잡음. 원격 저장·업로드 상태는 완료로 바꾸지 않음 |

## 남은 개선·확인 사항

1. **출시 전 필수 — 실제 앱/화면 검증.** 정상 Xcode·Gradle, iOS 설정/편집/알림 테스트와 Android 계측을 실행한다. 독일어 장문, 프랑스어 구두점, 작은 폭·최대 글자 크기, 접근성, 언어 변경·재실행·진행 보존을 각 언어로 확인한다. 이전에 확인한 sandbox/cache/CoreSimulator/socket 제한은 해제하지 않았다.
2. **출시 전 필수 — 원어민 검수.** 여행 상황의 존댓말과 친밀한 대화의 말투, 독일어 단어장의 대소문자·관사, 프랑스어 성/수, 스페인어 지역 어휘를 검수한다. 자동 검사는 번역 자연스러움을 보증하지 않는다.
3. **알림 기기 확인.** Android `DailyReminderReceiver`는 전달받은 Context의 리소스를 사용한다. 특히 앱 내 언어와 시스템 언어가 다른 환경, API 26–32의 백그라운드 전달에서 선택한 언어가 유지되는지 실기기로 확인하고, 다르면 저장한 앱 언어를 적용한 Context로 전환해야 한다. 이번에는 이 Android 동작을 재현하지 못해 수정 완료로 처리하지 않았다.
4. **복합 수량 문구 확장.** 이번 native plural 적용은 주요 단일 수량과 주간 스트릭이다. `my_page.insights.day_activity_format`처럼 두 수량이 있는 문구, 게임 힌트 남은 횟수와 공유 캡션의 복수형도 각 수량을 독립 선택하는 방식으로 확장하면 좋다. 숫자/서식 토큰 보존 검사는 현재도 수행한다.
5. **편집 언어 UX.** 덱 편집은 현재 앱 언어의 실제 값만 보여주며 없는 번역을 영어 값으로 자동 저장하지 않는다. 번역이 없는 가져온 덱은 빈 번역 필드가 나타날 수 있다. 향후 편집 화면에서 콘텐츠 언어를 따로 선택하고 미완성 번역 상태를 안내하면 앱 언어를 바꾸는 번거로움을 줄일 수 있다. 다른 언어 metadata가 불완전해지는 경우 숨기는 현재 정책도 안내가 필요하다.
6. **지역별 포맷.** es-MX/de-CH/fr-CA 등의 UI는 기본 es/de/fr 번역을 사용한다. iOS 숫자/날짜 locale도 현재 es_ES/de_DE/fr_FR로 고정되므로 지역별 숫자·날짜 관습까지 제공하려면 UI 언어와 지역 포맷을 분리해야 한다. 스토어의 실제 구매 가격/통화는 계속 플랫폼 값을 사용한다.
7. **스토어/원격 호환.** 새 namespace·구버전 URL 보존 검증, 지원/개인정보 공개 페이지 검수, 최종 RC 촬영, 필수 metadata 저장·업로드·재조회는 계속 미완료다. 파일을 받는 구버전 앱은 새 locale 키 때문에 업데이트가 필요할 수 있다.

## 로컬 검증

- Python 회귀: 96개 통과. 전체 키·서식·배열·콘텐츠 완전성 외 문맥 혼동/브랜드/plural/store gate 회귀 포함.
- Kotlin 실제 공용/설정·발견 JUnit: 63개 통과. 설치된 compiler/JUnit으로 실행했으며 Gradle 앱 빌드가 아니다.
- Swift 공용 코어: 45개 재실행 통과. 앱 UI/알림 테스트와는 별도다.
- 실제 Apple Foundation: 17개 plural 키 × 5개 언어 × 0/1/2/5 = 340개 출력, 5개 언어 소수점 검사 통과.
- iOS 변경 소스 parse, strings/plist 파싱, Android 8개 모듈 AAPT2 리소스 compile 통과. parse/resource compile은 앱 typecheck나 화면 검사와 같지 않다.
- 기존 한국어 목표·ID·ja/en/ko 값·순서를 보존한 덱 snapshot 67개와 catalog 2개 비교 통과. 기존 MP3·벡터·영어 번역 원본 585개 파일도 byte 일치.

로그와 환경 한정 실행기는 `artifacts/language-review/`에 보존한다. 소스 재현 명령은 `docs/LANGUAGE_EXPANSION_CHECKLIST.md`를 따르고, 새 commit SHA의 증빙과 위 미완료 gate를 함께 인계한다.

## 시뮬레이터·에뮬레이터 실행 요청 — 2026-08-28 21:24 JST

사용자 요청에 따라 `16b0aa1` clean 후보에서 기기 연결을 다시 시도했다. **앱 테스트는 실행하지 못했으며 통과로 처리하지 않는다.**

| 실제 실행한 명령 | 결과 |
|---|---|
| `xcrun simctl list devices available --json` | exit 1. CoreSimulatorService 연결 무효, 로그 접근 `Operation not permitted`, 기기 목록 접근 실패 |
| Android SDK `adb devices -l` | exit 1. ADB 서버의 smartsocket listener 생성 `Operation not permitted` |
| Android SDK `emulator -list-avds` | exit 0. `hantap_test` 등록 확인. 부팅/앱 실행 확인은 아님 |
| Python 3.12 `tools/workspace_doctor.py --strict` | Python gate 통과, 기존 Issue 소유권 미등록 gate 실패. 원격 작업은 하지 않음 |

이 실행 환경은 추가 실행 권한을 요청할 수 없다. CoreSimulator 및 ADB 접근이 허용된 환경이 필요하며, 접근 거부를 다른 실행 도구로 우회하거나 보호 설정을 변경하지 않았다. 실패 증빙은 `release/evidence/16b0aa1aa93481638df361a3ef9d6f9f6977819a.json`이다.

재개 시 기존 iOS `AppSettingsTests`, `RetentionStoreTests`와 UI의 `testSpanishDeviceLanguageSwitchAndPersistence`, `testGermanAndFrenchLanguageSelectionSurvivesRelaunch`, `testRetiredKoreanLanguageShowsEnglishUIAndKeepsKoreanPractice`, `testIPadAccessibilityDynamicTypeKeepsSettingsAndPracticeReachable`를 우선 실행한다. Android는 `M6SettingsInstrumentedTest`, `M3DiscoveryFlowInstrumentedTest`, `R11DeckMakerEditorInstrumentedTest`, `M5RetentionInstrumentedTest`, `AndroidReleasePolishInstrumentedTest`를 사용하되, 이 기존 suite만으로 신규 언어별 화면 검증이 끝나지는 않는다. es/de/fr 각각 선택·재실행·뜻/검색/편집·좁은 화면/큰 글자와 알림 언어를 별도로 확인하고 화면 증거를 남긴다. 실제 하드웨어의 알림 시각·성능·구매 gate는 에뮬레이터 통과와 구분한다.
