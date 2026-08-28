# 언어 커버리지 점검 — 2026-08-29

사용자가 지정한 `codex/local-es-de-fr`는 기본 저장소의 branch 목록이 아니라 **별도 clone** `/Users/jungminoh/Documents/hanco/outputs/local-language-expansion`에 있다. 실제 HEAD `dcc32bf`와 main `47371de`, 온보딩 Issue #73의 변경을 구분해 점검했다. 로컬 언어 후보는 수정·병합·push하지 않았다.

## 실제 지원 범위

| 범위 | main / #73 기준 | 로컬 `codex/local-es-de-fr` |
|---|---|---|
| 선택 가능한 앱 UI | ja/en/es 3개 | **ja/en/es/de/fr 5개**, iOS·Android 모두 |
| iOS 문자열 | main 언어당 1,106개; #73 1,119개 | 언어당 **1,108개**, 영어 대비 5개 언어 모두 키 누락 0개 |
| iOS 복수형 | 해당 확장 전 | 언어당 **17개 stringsdict 키** |
| Android 번역 리소스 | #73 509개 | **8개 모듈, 언어당 499개**: string 494, string-array 2, plurals 3. 영어 대비 누락 0개 |
| 공식 덱·뜻 | 스페인어 콘텐츠는 영어 fallback | es/de/fr 각각 **고유 학습 뜻 627개, 덱 이름 41개, 태그 42개, 공식 작성자** 번역 |
| 기본 카탈로그 | 26개 덱 | 26개 덱·312개 항목과 별도 게임 preset 15개를 생성·검증 |
| 언어 선택·지역 코드 | ja/en/es | es-MX/419, de-AT/CH, fr-CA/BE/CH 등을 기본 언어로 인식하고 선택 저장 |
| 한국어 | 학습 대상·보존 콘텐츠 | 동일. `ko.lproj` 존재는 한국어 UI 지원을 뜻하지 않으며 선택 목록에는 없음 |

627개는 `(ko, meaning_ja)` 사전의 고유 쌍 수이며, 기본 카탈로그 항목 수와 다르다. 사용자 제작 덱의 미제공 번역은 영어 fallback이다. 키 누락 0개가 번역 자연스러움이나 전체 화면 검수 완료를 뜻하지 않는다.

## 이번 레벨·첫 홈 추천 변경과의 관계

- #73/PR #74는 main을 기준으로 관심사 → 4단계 레벨 선택 → 입력 설정 → 첫 입력과 첫 홈 추천 → 실제 학습 후 이어하기를 구현했다.
- 신규 문구는 iOS 13개 키, Android 15개 리소스로 ja/en/es 및 비활성 ko 보존 리소스에 반영했다. 기존 홈 투어 설명도 추천 → 이어하기에 맞췄다.
- **de/fr은 #73에 아직 통합되지 않았다.** 반대로 언어 확장 후보에는 새 레벨·추천 기능이 없다. 두 branch가 합쳐졌다고 간주하지 않는다.
- 통합 시 de/fr 신규 문구와 기존 투어 설명을 번역하고 5개 언어의 키·서식 parity 및 레벨 화면/선택 저장/첫 홈 추천 덱 표시를 재검증해야 한다. 단순 cherry-pick만으로 전체 커버리지를 보장할 수 없다.
- 언어 후보는 공용 schema·콘텐츠 reader/writer·카탈로그도 바꾼다. UI 리소스만 복사하거나 기존 원격 카탈로그에 덮어쓰지 않는다. 후보의 별도 namespace 호환 계약을 유지한다.

## 이번에 직접 재실행한 확인

`outputs/local-language-expansion`의 clean `dcc32bf`에서:

- `PYTHONDONTWRITEBYTECODE=1 python3.12 -m unittest discover -s tools/tests`: **96개 통과**. UI 언어/서식·배열·복수형, 콘텐츠 생성 snapshot 등 포함.
- `PYTHONDONTWRITEBYTECODE=1 python3.12 tools/release_preflight.py`: **통과**.
- 각 언어의 문자열/리소스 수·키 누락, es/de/fr 의미 쌍 627개·빈 번역 0개·덱 이름/태그 수를 파일에서 다시 집계했다.
- iOS enum·knownRegions·CFBundleLocalizations, Android enum·localeFilters·locales_config의 5개 언어 등록을 확인했다.

언어 후보의 이전 Swift 코어 45개·Kotlin 코어 63개·Foundation plural 340개 통과는 후보의 `docs/LANGUAGE_REVIEW.md`와 `artifacts/language-review/README.md`에 기록된 **이전 작업의 증빙**이다. 이번 감사에서 앱 빌드나 기기 검증을 새로 수행한 것으로 기록하지 않는다.

## 남은 gate / 스토어와의 구분

- 언어 후보의 전체 iOS/Android 앱 빌드·UI/계측, 좁은 화면·큰 글자·접근성·선택 언어 알림 전달 검증이 남아 있다. 이전 시뮬레이터/ADB 실행 시도는 권한 문제로 실패했다.
- es/de/fr 원어민 검수, 복합 수량 문법, 지역별 숫자/날짜 포맷을 확인해야 한다.
- es/de/fr 스토어 설명·FAQ와 촬영 매핑은 초안이다. 새 카탈로그 namespace 게시, 최종 RC 촬영, 스토어 저장·업로드·재조회는 미완료다.
- 스토어 미디어의 10개 로케일(ja/en-US/ko/zh-Hans/zh-Hant/de-DE/fr-FR/es-ES/pt-BR/id)은 앱 UI 언어 수와 다르다. **중국어 간체·번체, 포르투갈어, 인도네시아어는 이 후보에도 앱 UI로 추가되지 않았다.**
- 기존 dirty 기본 작업공간과 후속 `outputs/local-retention-improvements`는 보존했다. 원격 main의 3개 언어와 로컬 후보의 5개 언어를 배포 완료 상태로 혼동하지 않는다.
