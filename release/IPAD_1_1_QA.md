# 1.1 iPad 화면·스토어 이미지 최종 QA — #79

검증일: 2026-08-29 JST. 이 기록은 소스/시뮬레이터 검증이며 출시 승인이 아니다.

## 배포 대상과 빌드 구분

- 현재 업로드된 `1.1 (7)`의 `UIDeviceFamily`는 `[1, 2]`다. iPhone과 iPad를 함께 지원하는 Universal 앱이며 iPad 별도 앱을 배포하는 구조가 아니다.
- 빌드 7은 PR #74의 앱 소스 `9af01ef96e06472eb3d842880649b5e697c5877e`다. 이 문서의 iPad 변경은 포함되지 않았다.
- #79 연습 구현 기준: `d3917ea` (앞선 `29e1fa7` 이후 가로 문제/조합 나란히 배치 추가). 후속 `9369b95`는 작은 iPad 가로에서 단어/초성/받아쓰기의 문제·입력 상태를 나란히 배치한다. 병합 후 새 빌드 번호로 RC를 만들고 그 빌드에서 다시 실기기/미디어 일치를 확인한다.
- App Store 업로드·버전 연결 변경·Add for Review·심사 제출은 이 작업에서 하지 않았다.

## 변경 내용

| 화면 | 변경 |
|---|---|
| 두벌식 키보드 | 820pt 폭 상한 제거, 실제 창 폭 활용, 가로/세로에 맞춘 키 높이와 글자 확대, 넓은 스페이스바 |
| 10키 키보드 | 3×4 구조 유지, 최대 600pt 중앙 정렬, 태블릿 키 높이 확대 |
| 연습 세로 | 문제/조합 카드가 남은 세로 공간을 나눠 쓰고 목표 글자·조합·피요 확대 |
| 연습 가로 | 문제와 조합 카드를 나란히 배치해 키보드 위에서 동시에 표시 |
| 단어·초성·받아쓰기 가로 | 문제와 입력 상태를 나란히 배치해 작은 iPad에서도 키보드가 화면 아래로 밀리지 않게 조정 |
| 홈 | 넓은 가로 창의 2열 구성, 추천 카드 확대 |
| 발견 | 추천 덱 카드 확대 |
| 게임 선택 | 최대 3열, 아이콘 영역 확대, 접근성 크기에서는 1열 |
| 마이페이지 | 세로 1열로 비대칭 빈 공간 축소 |
| 공통·설정 | 실제 창/시트 폭을 기준으로 폰트 확대, 사용자 접근성 크기 보존 |

600pt 미만 창에서는 기존 iPhone 밀도를 유지한다. 저장 schema·한글 엔진·판정·발음·결제 로직은 변경하지 않았다.

## 검증 범위

로그/xcresult는 `/private/tmp/piyokey-qa79-results`에 보존한다. iPhone·iPad의 선택 회귀이며 전체 UI suite는 아니다.

- Swift 공용 패키지: 45개 통과.
- Python 도구 테스트: 97개 통과.
- iOS 앱 단위 테스트: `d3917ea`에서 367개, 최종 앱 소스 `9369b95`에서 367개 재통과 (`iphone-quiz-final.xcresult`).
- iPhone 17: `29e1fa7`의 선택 UI 15개 중 14개 통과, 결과 연출 중 종료 테스트 1개는 시간 상태 비교에서 실패했다. `d3917ea`에서 해당 사례와 두벌식·10키·OS IME를 포함한 UI 4개를 재실행해 모두 통과했다. 초기 실패 로그는 보존하며 단일 재실행 통과를 테스트 안정성 보증으로 해석하지 않는다.
- iPad mini (A17 Pro): 최초 선택 UI 8개 중 7개 통과. 네이티브 탭 페이지 이동을 보완한 후 주요 6화면의 세로/가로 이동·스크린샷 및 연습 회전/진행 보존 2개를 최종 소스에서 통과했다. 새 가로 검증은 문제·조합 카드 하단이 키보드 위에 있는지 검사한다.
- iPad Pro 13-inch (M5): 최종 소스의 주요 6화면 세로/가로·연습 회전/진행 보존·접근성 큰 글자 UI 3개 통과.
- 후속 게임 가로 검사에서 iPad mini 단어 퀴즈의 키보드 하단이 화면을 4.5pt 넘는 문제를 발견했다. `9369b95`에서 수정 후 게임 6종을 도는 UI 1개와 6장 시각 검수를 통과했다 (`ipad-games-fixed.xcresult`). 같은 소스의 iPhone 10키 게임·초성/단어 발음 힌트·받아쓰기 UI 4개도 통과했다 (`iphone-quiz-final.xcresult`).
- 위 기기는 모두 iOS 26.5 시뮬레이터다. 13인치/mini의 홈·발견·커리큘럼·게임 선택·마이페이지·설정과 연습 가로/세로 캡처를 직접 시각 검수했다.
- 최종 소스의 arm64 iOS Simulator Release 구성 빌드 통과. 서명된 실기기 archive/IPA 검증을 대신하지 않는다.
- 일반 repository preflight 통과. `--strict`는 기존 열린 출시 gate 38개로 실패하며 이를 면제하거나 완료 처리하지 않는다.

최초 iPad UI 테스트에서 새 카드 식별자가 자식 접근성을 가리는 문제를 발견해 `children: .contain`으로 수정했다. 작은 iPad의 네이티브 탭은 페이지로 나뉘므로, 테스트가 가려진 마이페이지를 바로 누르지 않고 페이지를 먼저 넘기도록 수정했다. 촬영 도구는 iPad 상단 탭과 화면 회전 후 원본 픽셀 방향을 지원하며 실패한 캡처는 최종 세트에 포함하지 않는다.

## 스토어 이미지 계약

- 기존 iPhone과 같은 배경·브랜드·마케팅 문구를 사용하고, 수정된 실제 iPad 화면을 비율 왜곡 없이 배치한다.
- ja/en/es/de/fr 실제 UI를 촬영해 기존 10개 시장용 문구와 결합한다. ko/zh-Hans/zh-Hant/pt-BR/id는 기존 방침대로 영어 UI를 사용한다.
- 시장당 세로 10장: `2064×2752`, RGB PNG. 가로 키보드 대체본 1장: `2752×2064`.
- [Apple 스크린샷 규격](https://developer.apple.com/help/app-store-connect/reference/app-information/screenshot-specifications/)의 iPad 13-inch 슬롯을 사용한다. 대체본은 10장에 더하는 것이 아니라 한 장을 교체하는 용도다.
- 유료 덱 편집 화면에는 기존 Pro 필요 문구를 유지한다. 촬영용 DEBUG 진행 상태는 실제 앱 UI이며 실제 구매 성공 증빙은 아니다.
- 원본·배치 설정·출력 SHA-256·소스 commit·미업로드 상태를 manifest에 기록한다. 이미지 생성/추출 실패, 크기/색상 형식 오류가 있으면 제작 도구가 중단한다.

### 제작·검수 결과

- ja/en/es/de/fr 촬영 UI 테스트 5개 모두 통과. 실제 원본 55장으로 10개 시장의 세로 100장·가로 대체본 10장을 제작했다.
- 110장 전체 RGB/해상도/PNG decode 검증 및 원본 포함 165개 SHA-256 재검증 통과. 10개 시장 contact sheet와 전체 가로 대체본, 일본어 대표 세로·가로·편집 화면을 직접 시각 검수했다. 일본어 최종 출력은 먼저 검수한 이미지와 픽셀이 동일하다.
- 전달 폴더: `/Users/jungminoh/Documents/hanco/outputs/ipad-1.1-store-20260829` (`index.html`, 시장별 `screenshots/`, `alternate/`, `manifest.json`, `qa/`).
- 촬영 앱 소스는 `d3917ea`, simulator executable SHA-256은 `201f36096bd6e35001a76276886a9deb0be6ed7f0d53022d97d40f2e9231b089`다. `9369b95`의 후속 변경은 퀴즈 가로 구성이며 세로 스토어 장면과 연습 가로 대체본은 그 변경 대상이 아니다. 촬영 소스와 최신 후보를 manifest에서 분리 기록했고, 새 RC 이미지 대조는 여전히 필수다.

### 재현

13인치 iPad 시뮬레이터를 준비하고 상태바를 촬영용으로 설정한 뒤 실행한다. 출력 폴더는 새 경로를 사용하며 기존 결과를 덮어쓰지 않는다. 이미지 제작 단계의 Python에는 Pillow가 필요하다.

```sh
xcodebuild build-for-testing -project ios/Hanco/Hanco.xcodeproj -scheme Hanco -destination 'generic/platform=iOS Simulator' -derivedDataPath artifacts/ipad-store/DerivedData
python3 tools/capture_global_store_assets.py --screenshots-only --derived-data artifacts/ipad-store/DerivedData --device <iPad-13-UDID> --languages ja en es de fr --output artifacts/ipad-store/capture
swiftc tools/generate_app_store_marketing_screenshots.swift -o artifacts/ipad-store/renderer
python3 tools/build_ipad_store_assets.py --capture artifacts/ipad-store/capture --output artifacts/ipad-store/delivery --renderer artifacts/ipad-store/renderer --capture-source-ref <촬영한-앱-소스-SHA>
```

## 배포 전 남은 항목 (우선순위)

1. #79 병합 및 새 RC: CI/리뷰 정책을 유지한다. 과거 PR의 일회성 예외 승인을 재사용하지 않는다.
2. 새 TestFlight 빌드를 실제 iPhone/iPad에 업데이트 설치해 진행·덱·설정·구매 권한 보존, 두벌식/10키/OS IME/물리 키보드, 가로/세로·분할 창을 확인한다.
3. Account Holder의 은행·세금 정보와 Paid Apps Agreement, IAP 심사 이미지/상태/버전 연결, Sandbox 구매·취소·pending·복원·환불 gate를 완료한다.
4. 최종 빌드의 오디오 혼합/무음/백그라운드, Files/iCloud/AirDrop·1,000항목, Game Center·60fps·알림, 개인정보/권리 gate를 확인한다.
5. 현지어 사람 검수와 최종 RC 화면 일치 후 스토어 미디어를 업로드하고 저장 후 재조회한다.

시뮬레이터의 선택 회귀를 전체 UI 회귀, 실제 기기의 Split View/Stage Manager 검증, 결제 검증, 출시 완료로 확장해 해석하지 않는다.
