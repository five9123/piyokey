# M6 검증 산출물

## F9 결과 공유 이미지

- `f9-share-card-game-ja.png`: `ImageRenderer`가 생성한 1200×1200 PNG 원본. Hanco/ㅎ 키캡 브랜드 락업, Swift 벡터 병아리, 점수·정확도·최대 콤보·스트릭, 일본어 다운로드 CTA를 확인한다.
- `f9-share-sheet-ja.png`: iPhone 17 / iOS 26.5 Simulator에서 결과의 `シェア`를 눌러 시스템 공유 시트와 이미지 미리보기가 표시된 화면.

검증 명령:

```sh
xcodebuild test -project ios/Hanco/Hanco.xcodeproj -scheme Hanco \
  -destination 'platform=iOS Simulator,name=iPhone 17,OS=26.5' \
  -only-testing:HancoTests/SessionShareCardTests

xcodebuild test -project ios/Hanco/Hanco.xcodeproj -scheme Hanco \
  -destination 'platform=iOS Simulator,name=iPhone 17,OS=26.5' \
  -only-testing:HancoUITests/HancoUITests/testM4SuccessFlowCompletesFlawlessCardAndRevealsResult
```

## 외부 캐릭터 시트 기반 펫 시스템 확장

- 표정 8종, 덱 태그 소품 7종, 계절/기념일 장식 6종을 추가했다.
- 누적 입력 자모·최장 스트릭·최근 7일 활동을 볏·몸집·깃털 윤기에 반영하고, 알 무늬를 온보딩/옷장에서 선택·저장한다.
- 게임 플레이 화면에 카드 추적 3/4 포즈 병아리와 정타·5콤보 리듬·오타 놀람·3연속 오타 어지러움·10초 집중·개인 최고 기록 반응을 연결했다.
- 연습/게임 공통으로 누적 정타·자주 틀린 자모·복습 졸업·3회 연속 레슨·백그라운드 복귀를 펫 이벤트에 연결했다.
- 하품·한 발 서기·뒤돌기·깃 고르기·회전·발 구르기·3/4 걸음과 알 추가 균열을 idle 변주로 넣고, 성장 전환은 모프 축하로 표시한다.
- 공유 카드에는 펫 이름표와 친구 병아리가 함께 렌더링된다.

자동 검증 결과(2026-07-19):

- iOS 단위: 115/115
- 게임 병아리 노출·백그라운드 타이머 정지·결과 자동 진입·재도전 성공 흐름 UI: 1/1
- ja/en/ko 로컬라이제이션: 각 469개 키와 포맷 인자 일치
- generic iOS Debug 기기 빌드: 성공
- SwiftPM 회귀: 별도 M1 패키지 게이트 유지

## F11 사운드

- 타건음: 기본 프리셋은 CC0 `Basic Mouse Click UI`의 공식 HQ 스트림을 918 유효 프레임(19.125ms·48kHz·mono) PCM CAF로 변환한 번들 음원이다. 원본에는 런타임 게인 0.34와 0.78 peak 상한을 적용하고, 일반 키 3개 변형을 결정적으로 순환하며 Backspace·Shift는 별도 피치·게인으로 구분한다. 리소스 누락·손상 시에만 기존 18~22ms 합성음을 사용하며 기계식/소프트는 런타임 PCM 합성을 유지한다.
- 오디오 세션: 효과음과 발음 모두 `.playback` + `.mixWithOthers`로 무음 모드에서도 재생한다. 발음 중에는 효과음을 억제하며 OFF 즉시 자체 효과음을 정지하고 세션을 반납한다.
- 재생 안정성: 타건음 6개와 정오답·콤보 3개의 독립 플레이어 풀을 쓰고, 첫 재생 때만 엔진을 시작한다. 마지막 효과음 15초 뒤와 앱 백그라운드 진입 시 세션을 반납하며 통화·Siri·출력 경로 변경·미디어 서비스 리셋 뒤 자동 복구한다.
- 연결: 온보딩·레슨·게임 내장 키 touch-down, 레슨/게임/OS IME의 확정 완성·오타 판정.
- 설정: 설정 화면 최상단에 기본 ON인 효과음 토글을 두고 3종 프리셋(표준/메카니컬/소프트)을 즉시 반영·영속화한다. 신규·알 수 없는 값은 표준이며 기존의 명시적 선택은 유지한다.
- 2026-07-19 iPhone 15 Pro JM에 Debug 빌드를 설치·실행했다. 아래 청취 항목은 사람이 직접 확인한 뒤 M6 출시 체크를 닫는다.

실기기 청취 체크:

1. Spotify 등에서 음악을 먼저 재생한다.
2. 설정을 열자마자 효과음 토글이 스크롤 없이 보이고 기본 ON인지 확인한다. OFF 즉시 모든 타건음·정오답음이 멈추는지 확인한다.
3. Hanco의 `フリープラクティス`에서 신규 기본값이 `標準`인지 확인하고, 벨소리/무음 스위치가 무음인 상태에서도 기본·`メカニカル`·`ソフト` 타건음이 각각 들리는지 확인한다.
4. 일부러 틀린 키를 눌러 낮은 오타음, 한 단어를 완성해 완성음을 확인한다.
5. 목표 발음 버튼과 문제 자동 발음을 각각 확인하고, 무음 모드에서도 들리는지 확인한다. 발음 중에는 타건음·효과음이 겹치지 않아야 한다.
6. 게임에서 연속 완성해 콤보가 오를수록 피치가 높아지는지 확인한다.
7. 위 과정 내내 외부 음악이 멈추거나 볼륨이 강제로 낮아지지 않는지 확인한다.
8. 효과음을 OFF로 바꾼 뒤 앱을 재실행해도 OFF가 유지되는지 확인한다.
9. 빠르게 타이핑하면서 단어 완성·오타음을 연속 발생시켜 성공음과 오타음이 중간에 잘리지 않는지 확인한다.
10. 일반 키·Backspace·Shift의 크기와 높이가 과하지 않게 구분되고 기본 클릭이 다른 효과음보다 튀지 않는지 확인한다.
11. 통화 또는 Siri를 열었다 닫고, AirPods/Bluetooth 출력을 연결·해제한 뒤 다음 첫 입력부터 효과음이 복구되는지 확인한다.
12. 마지막 효과음 후 15초 이상 기다렸다가 다시 입력해 첫 소리가 누락되지 않는지 확인한다.

자동 검증:

```sh
xcodebuild test -project ios/Hanco/Hanco.xcodeproj -scheme Hanco \
  -destination 'platform=iOS Simulator,name=iPhone 17,OS=26.5' \
  -only-testing:HancoTests/HancoSoundEngineTests

xcodebuild test -project ios/Hanco/Hanco.xcodeproj -scheme Hanco \
  -destination 'platform=iOS Simulator,name=iPhone 17,OS=26.5' \
  -only-testing:HancoUITests/HancoUITests/testSoundSettingAndPresetPersistAcrossRelaunch
```

## F10 설정과 로컬라이제이션

- 다섯 번째 `設定` 탭에서 글자 크기 3단계, 라이트/다크, ja/en/ko, 키 가이드, 영문 힌트, 햅틱, 기본 입력 모드, 효과음과 타건 프리셋을 즉시 변경한다.
- 글자 크기는 문제·조합 프리뷰·게임 카드·온보딩 문제와 키캡에 적용된다.
- 앱 내부 언어 전환은 탭과 현재 설정 화면을 즉시 다시 그리며, 재실행 뒤에도 선택과 나머지 설정이 유지된다.
- 세 언어는 각 442개 키를 가지며 포맷 인자 목록까지 단위 테스트로 비교한다.

자동 검증 결과(2026-07-19):

- SwiftPM: 17/17
- iOS 단위: 100/100
- F10 설정 UI: 1/1
- 연습·리텐션·발견·게임·온보딩 일본어 대표 UI: 5/5
- generic iOS Simulator Release: 성공
- iPhone 15 Pro JM: 최신 F10 Debug 빌드 설치·실행 성공

검증 명령:

```sh
xcodebuild test -project ios/Hanco/Hanco.xcodeproj -scheme Hanco \
  -destination 'platform=iOS Simulator,name=iPhone 17,OS=26.5' \
  -only-testing:HancoTests/AppSettingsTests

xcodebuild test -project ios/Hanco/Hanco.xcodeproj -scheme Hanco \
  -destination 'platform=iOS Simulator,name=iPhone 17,OS=26.5' \
  -only-testing:HancoUITests/HancoUITests/testF10SettingsApplyImmediatelyAndPersistAcrossRelaunch
```

## F12 결과 화면 종료 회귀

- 결과의 `完了`는 2.5초 연출 중에도 즉시 동작하고, Z5 재도전·복습·공유 CTA만 기존처럼 연출 종료 또는 스킵 전까지 비활성화된다.
- 연습 결과는 결과 destination을 닫은 뒤 연습 진입 화면으로, 게임 결과는 게임 선택 화면으로 복귀한다.
- 결과 표시 상태는 연습·게임 세션이 각각 소유하며, 게임의 기존 읽기 전용 Binding을 제거했다.

자동 검증 결과(2026-07-19):

- SwiftPM: 17/17
- iOS 단위: 100/100
- 연출 시간을 10초로 확대한 연습·게임 조기 `完了` UI: 1/1
- 데일리 챌린지 `Practice Results → Done → 커리큘럼 홈` UI: 1/1
- 기존 연습·게임 1탭 재도전 UI: 2/2
- iPhone 15 Pro JM: 수정 Debug 빌드 덮어설치·재실행 성공

검증 명령:

```sh
xcodebuild test -quiet -project ios/Hanco/Hanco.xcodeproj -scheme Hanco \
  -destination 'platform=iOS Simulator,name=iPhone 17,OS=26.5' \
  -parallel-testing-enabled NO \
  -only-testing:HancoUITests/HancoUITests/testDoneExitsPracticeAndGameResultsEvenDuringReveal

xcodebuild test -quiet -project ios/Hanco/Hanco.xcodeproj -scheme Hanco \
  -destination 'platform=iOS Simulator,name=iPhone 17,OS=26.5' \
  -parallel-testing-enabled NO \
  -only-testing:HancoUITests/HancoUITests/testDailyChallengeCompletesTodaysStampAndReminderDefaultsOff
```

## S5 문제·세션 완료 자동 전환

- 모든 단어 완성 후 성공 피드백을 0.65초 표시한다.
- 중간 단어는 다음 단어로, 마지막 단어는 결과 화면으로 자동 전환한다.
- `Next target`과 `View results` 버튼은 모두 제거했다.
- 백그라운드·종료·재시도에서는 예약을 취소하고, 완료 상태 포그라운드 복귀 시 다시 예약한다.

자동 검증 결과(2026-07-19):

- iOS 단위: 104/104
- 다문항 커리큘럼·데일리 챌린지·다운로드 덱·결과 조기 종료·오타 복습 연결의 자동 결과 진입 UI: 5/5
- 다섯 경로 모두 `practice.show_result` 버튼 부재 확인
- 최종 실기기 덮어설치는 iPhone 15 Pro JM 재연결 뒤 수행한다.

## 세션 TTS와 실행 중 설정

- 연습 목표 카드의 `Target` 레이블을 현재 목표를 한국어로 읽어 주는 `듣기` 버튼으로 교체했다.
- 연습·복습·데일리·게임의 우측 상단 초기화 버튼을 제거하고 키보드·사운드·햅틱을 즉시 바꾸는 공통 메뉴를 배치했다.
- TTS는 `ko-KR` 음성을 사용하고 `.playback + .mixWithOthers` 정책을 적용해 무음 모드에서도 들린다. 발음 중 효과음을 억제하고 종료 즉시 효과음용 `.playback` 기본 모드로 복원한다.
- 결과 화면의 `다시 하기`는 세션을 끝낸 뒤의 명시적 동작이라 유지한다.

자동 검증 결과(2026-07-19):

- 목표 TTS·연습 세션 설정 메뉴, 게임 세션 설정 메뉴·결과 재도전 UI: 2/2
- 로컬라이제이션 키·포맷 인자와 설정 단위: 4/4
- iOS 단위: 100/100, generic iOS Simulator Release 빌드 성공
- iPhone 15 Pro JM: TTS·세션 설정 Debug 빌드 덮어설치·재실행 성공

## Swift 벡터 병아리 성장 시스템

- `mascot-onboarding-egg-ja.png`: 첫 온보딩에서 목표별 무늬를 받을 알과 성장 안내.
- `mascot-closet-ja.png`: 이름, 1·3·6챕터 성장 조건, 스트릭·챕터·설치 덱 기반 소품 옷장.
- `mascot-hatching-ja.png`: 첫 챕터 완료 뒤 세션이 완전히 종료된 다음 표시되는 부화·이름 짓기.
- 기존 `PenguinMascot.imageset`과 `PenguinMascotView`는 제거했으며 공유 카드 산출물도 병아리 버전으로 재생성했다.
- 성장 단계는 기존 `CurriculumProgressLibrary`의 완료 챕터 수에서 계산하고 이름·장착·축하 이력만 UserDefaults에 저장한다.
- 성장 축하는 연습·게임 도중 나타나지 않고 커리큘럼 화면 복귀 후 0.55초 뒤 한 번만 표시한다.

자동 검증 결과(2026-07-19):

- iOS 단위: 104/104
- 성장 단계·1회 축하·이름/소품 저장·해금 규칙: 4/4
- 온보딩 알·옷장·첫 챕터 부화/이름 짓기·대표 연습 화면 UI: 4/4
- ja/en/ko 로컬라이제이션: 각 441개 키와 포맷 인자 일치
- generic iOS Simulator Release 빌드 성공
- 최종 실기기 덮어설치는 iPhone 15 Pro JM이 `unavailable`로 전환되어 재연결 뒤 수행한다. 이전 TTS·세션 설정 Debug 빌드의 설치·실행 기록은 유효하다.

## S5 음절 성공 표시와 이벤트 기반 병아리 반응

- `practice-syllable-success-ja.png`: `사랑해요`의 첫 음절을 완성한 직후 화면. 완료 음절의 초록색 키캡·체크 배지, 입력 중 자모 칩, 성장 단계 병아리가 함께 유지되는지 확인한다.
- 목표 음절은 기대 자모 범위가 전부 승인됐을 때만 완료 처리하며, 부분 입력은 분홍색 진행 상태로 구분한다.
- 병아리는 정타 자모·음절 완료·단어 완료·5/10/20 연속 정타·오타·무오타 세션 완료 이벤트에 각각 끄덕임·날갯짓·점프·콤보·다음 키 응시·축하로 반응한다.
- 장착된 응원봉·졸업모·헤드폰도 이벤트에 맞춰 빛나거나 튀어 오르고, Reduce Motion에서는 정적 배지·글로우로 대체한다.

자동 검증 결과(2026-07-19):

- iOS 단위: 106/106
- 새 음절 성공 표시 UI: 1/1
- 자동 다음 단어 전환·자동 결과 진입·결과 `完了` 종료 회귀 UI: 2/2
- ja/en/ko 로컬라이제이션: 각 442개 키와 포맷 인자 일치
- generic iOS Simulator Release 빌드 성공

검증 명령:

```sh
xcodebuild test -project ios/Hanco/Hanco.xcodeproj -scheme Hanco \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro,OS=26.5' \
  -only-testing:HancoUITests/HancoUITests/testCompletedSyllableShowsSuccessStateWhileTyping

xcodebuild test -project ios/Hanco/Hanco.xcodeproj -scheme Hanco \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro,OS=26.5' \
  -only-testing:HancoUITests/HancoUITests/testCompletingDokkaebiTargetAdvancesToNextProblem \
  -only-testing:HancoUITests/HancoUITests/testDoneExitsPracticeAndGameResultsEvenDuringReveal
```

## PIYOKEY 브랜드와 AppIcon

- 사용자 노출명: 일본어 `ピヨキー`, 영어 `PIYOKEY`, 한국어 `피요키`.
- 기본 캐릭터 애칭: 일본어 `ピヨちゃん`, 영어 `Piyo`, 한국어 `피요`. 사용자가 입력한 기존 이름은 그대로 우선한다.
- `PiyokeyLogoMark`는 공유 카드 등 앱 내부의 작은 영역에서도 AppIcon과 동일한 래스터 브랜드 이미지를 사용한다.
- 홈 화면 AppIcon과 앱 내부 로고는 음영이 있는 흰색 `ㅎ` 키캡을 든 병아리 원본 `shared/brand/piyokey_app_icon_source.png`를 함께 사용한다.
- `tools/render_piyokey_app_icon.swift`는 공용 원본을 sRGB 1024×1024 무알파 PNG로 정규화해 `AppIcon.appiconset`에 출력한다.
- `piyokey-share-card.png`: 새 로고·영문 브랜드 락업과 기존 성장 병아리가 함께 표시되는 공유 카드 시각 검수본.
- 공식 목 카탈로그 작성자도 `ピヨキー 公式`로 재생성했다.

자동 검증 결과(2026-07-20):

- iOS 단위: 115/115
- 브랜드명·기본 애칭·공유 카드 집중 회귀: 5/5
- ja/en/ko Localizable: 각 465개 키와 포맷 인자 일치
- ja/en/ko `InfoPlist.strings`와 AppIcon의 실기기 Asset Catalog 컴파일 성공
- AppIcon Asset Catalog 원본: 1024×1024, alpha 없음
- iPhone 15 Pro JM에 최신 Debug 빌드 덮어설치 및 실행 성공

## AppIcon 검정 출력 및 병아리 프레임 이탈 회귀

- 기존 AppIcon 생성 과정에서 불투명 비트맵으로 평탄화할 때 색상 원본이 복사되지 않아 1024×1024 PNG가 검정으로 채워지는 문제를 수정했다.
- 생성기는 sRGB Core Graphics 컨텍스트에 브랜드 원본을 고품질 합성하며, 출력 표본이 검정이면 실패하도록 회귀 가드를 둔다.
- `idleBackTurn`의 180° 회전은 캐릭터 폭이 0이 되는 구간이 있어 ±24°의 3/4 시선 전환으로 바꿨다. 좌우·상하 이동, 반응 배율, 졸업모 이동도 캐릭터의 배치 슬롯 안으로 제한한다.
- Reduce Motion의 기존 정적 피드백과 점프·끄덕임·날갯짓 등 프레임 내부 반응은 유지한다.

자동 검증 결과(2026-07-20):

- AppIcon 원본 및 실기기 컴파일 아이콘: 색상 정상, alpha 없음
- iOS 단위: 115/115; 모션 정책 경계 테스트 포함
- 성장/옷장 및 게임 성공 흐름 UI: 2/2
- generic iOS 기기 빌드와 iPhone 15 Pro JM 실기기 빌드: 성공
- 최신 Debug 빌드 실기기 덮어설치 성공; 기기 잠금 상태로 자동 실행만 보류

## 게임·설정 탭 직접 콘텐츠 진입과 병아리 디자인 3안

- 게임 선택 상단의 모드 설명·병아리 브랜드 카드와 설정 상단의 병아리 설명 카드를 제거했다.
- 게임은 설치 덱 목록/빈 상태로 바로 시작한다. 설정은 하단 탭에서 빠지고 모든 콘텐츠 탭의 우측 상단 기어 버튼으로 여는 공통 시트가 되며, 글자 크기·테마·언어 카드로 바로 시작한다.
- `piyokey-mascot-concepts.png`: 외부 이미지 없이 SwiftUI 도형으로 렌더링한 A 말랑 키캡 피요, B 키캡 후드 피요, C 콩알 피요 비교본. 세 안은 사용자 검토에서 선택되지 않았으며 런타임 성장 캐릭터 본체에는 적용하지 않는다. AppIcon은 HanTap과 공유하는 별도 원본을 사용한다.
- 재생성: `xcrun swiftc -parse-as-library -module-cache-path /tmp/piyokey-swift-module-cache tools/render_piyokey_mascot_concepts.swift -o /tmp/render_piyokey_mascot_concepts` 후 `/tmp/render_piyokey_mascot_concepts artifacts/m6/piyokey-mascot-concepts.png`.

자동 검증 결과(2026-07-20):

- iOS 단위: 115/115; ja/en/ko 각 465개 키·포맷 인자 일치
- 게임 직접 콘텐츠 진입, 설정 즉시 반영·영속화, 기존 덱 선택→게임→결과 흐름 UI: 3/3
- iPhone 15 Pro JM 실기기 빌드·덮어설치·실행 성공

## 홈·연습 분리와 공통 설정 시트

- 하단 GNB는 `ホーム | さがす | 練習 | マイデッキ | ゲーム`으로 재편했다. 설정 탭은 제거하고 다섯 탭의 우측 상단에 동일한 작은 기어 버튼을 둔다.
- 홈은 7일 스탬프 카드, `毎日3分` 데일리 챌린지, 사용자 추천 덱만 표시한다. 커리큘럼과 자유 연습은 별도 연습 탭에서 시작한다.
- 연습 알림은 홈에서 설정 시트로 이동했다. 삭제된 커리큘럼 병아리 소개 카드의 이름·성장·아이템 관리 기능은 설정의 `ピヨの設定`으로 보존했다.
- 언어 변경으로 탭이 다시 그려져도 공통 설정 시트의 표시 상태가 유지되도록 설정 제시는 AppRoot가 소유한다.

자동 검증 결과(2026-07-20):

- iOS 단위: 116/116; ja/en/ko 각 465개 키·포맷 인자 일치
- 홈·연습 분리, 다섯 탭 공통 설정 버튼, 리마인더 이동, 옷장 이동, 설정 언어·영속성 대표 UI 회귀: 6/6
- iPhone 15 Pro JM 실기기 빌드·덮어설치·실행 성공

## 옷장 수동 선택과 계절 장식 우선순위

- 7~8월의 밀짚모자를 포함한 계절 장식은 `おまかせ（季節アイテムあり）`에서만 표시한다.
- `なし` 또는 고정 아이템을 고르면 계절 장식을 즉시 숨겨 사용자의 수동 선택이 우선한다.
- 자동·고정·없음 세 경로의 밀짚모자 해제 회귀 테스트를 추가했다.

## 1차 사용성 개선 배치

- 흐름 게임 시작·재도전에 `3 → 2 → 1` 준비 카운트다운을 추가했다. 카운트다운 중에는 60초 타이머와 카드가 움직이지 않으며, 백그라운드 복귀 시 준비 단계를 다시 시작한다.
- 홈 추천이 콜드 스타트에서 온보딩 목표 태그를 사용하도록 연결했다. 다운로드 이력이 쌓이면 기존 행동 기반 태그가 더 높은 가중치를 갖는다.
- 옷장에 성장·알 무늬·장착 소품을 합성한 최종 모습 미리보기를 추가하고, 한 번 획득한 소품은 조건이 사라지거나 앱을 재실행해도 영구 유지한다.
- 내 덱 빈 상태에 `덱 찾기` CTA를 추가하고 최근 사용·이름·설치일 정렬을 제공한다. 공통 설정 기어의 외형은 유지하면서 탭 영역을 44×44pt로 확장했다.

자동 검증 결과(2026-07-20):

- iOS 전체 단위: 118/118
- 게임 성공·옷장 최종 미리보기·내 덱 빈 상태 이동 대표 UI: 3/3
- ja/en/ko Localizable: 각 472개 키와 포맷 인자 일치
- 첫 UI 재검증 중 CoreSimulator `Busy` 사전 실행 오류 2회는 테스트 러너가 실행되기 전의 인프라 오류였으며, 별도 iPhone 17 시뮬레이터 재실행에서 통과했다.
- iPhone 15 Pro JM에 최신 Debug 빌드를 무선 덮어설치했다. 기기 잠금 상태라 자동 실행만 보류됐으며 설치 자체는 성공했다.

## 로컬 저장 데이터 손상 복구

- 설치 덱·카탈로그·게임 기록·커리큘럼·복습·스트릭 JSON은 원자적 주 파일과 최신 검증 백업을 함께 유지한다.
- 주 파일이 깨지거나 의미상 잘못된 값을 포함하면 `.corrupt`로 격리하고 `.backup`에서 자동 복원한다. 미래 스키마는 구버전 앱이 격리하거나 덮어쓰지 않는다.
- 기존 사용자 데이터는 첫 번째 정상 로드 때 백업이 없으면 즉시 검증 백업을 생성한다.
- 설치 덱 하나의 주 파일과 백업이 모두 손상된 경우 해당 덱만 제거하고 다른 오프라인 덱과 다운로드 태그 이력은 보존한다.
- 온보딩 완료·목표 상태도 UserDefaults 안에 백업과 손상 증거를 유지해 첫 실행 화면이 다시 나타나는 위험을 줄였다.

자동 검증 결과(2026-07-20):

- iOS 전체 단위: 128/128
- 손상·의미 검증·백업 복원·기존 데이터 백업 이관·개별 덱 격리·미래 스키마 보존 신규 회귀: 10/10
- ja/en/ko Localizable: 각 472개 키와 포맷 인자 일치
- iPhone 15 Pro JM에 최신 Debug 빌드를 무선 덮어설치했다. 기기 잠금 상태라 자동 실행만 보류됐다.
