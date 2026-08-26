# iOS Bluetooth·물리 키보드 QA

Issue: [#12](https://github.com/five9123-maker/piyokey/issues/12)

## 지원 계약

- 입력 모드는 설정의 `OS 키보드`를 선택한다. 내장 키보드 모드에 별도 물리 키 이벤트를 주입하지 않는다.
- Bluetooth, USB, Magic Keyboard는 모두 iOS 표준 `UITextField` 경로를 사용한다.
- iOS 입력 소스는 한국어 두벌식을 선택한다. 앱이 하드웨어별 자판을 다시 매핑하지 않는다.
- committed/marked text를 자모 시퀀스로 분해해 목표 prefix와 비교한다. 조합 중 불일치는 확정 전까지 오타로 집계하지 않는다.
- Issue #12는 현재 iPhone 앱과 iPad의 iPhone 호환 실행을 검증한다. Universal iPad 레이아웃은 Issue #10 범위다.

## 자동 게이트

```sh
cd ios/HangulEngine
swift test --filter HangulEngineTests

xcodebuild test -project ios/Hanco/Hanco.xcodeproj -scheme Hanco \
  -destination 'platform=iOS Simulator,name=iPhone 17,OS=26.5' \
  -only-testing:HancoTests/PracticeSessionViewModelTests \
  -only-testing:HancoUITests/HancoUITests/testSessionSwitchesToOSIMEAndKeepsAcceptedJamoProgress \
  -only-testing:HancoUITests/HancoUITests/testOSIMEReturnAndForegroundRestoreFocusWithRecoveryAffordance \
  -only-testing:HancoUITests/HancoUITests/testOSIMEHardwareStyleDeleteAndReturnKeepAcceptedPrefixAligned
```

자동 검증 범위:

- marked → committed 동일 snapshot의 중복 입력 방지
- 목표 문장 Space 수락
- Backspace prefix 되감기와 오타 미집계
- Return 개행 방지와 포커스 유지
- background → foreground 포커스 복구
- 내장 → OS IME 전환 시 진행 보존

## 실기기 매트릭스

| 항목 | iPad Pro 11-inch (3세대) | iPhone 15 Pro | iPhone 16 |
|---|---|---|---|
| OS / build | iPadOS 26.6 / 23G71 | 기록 필요 | 기록 필요 |
| 연결 | USB, paired/available | paired | paired |
| Developer Mode | enabled | 기록 필요 | 기록 필요 |
| 한국어 두벌식 입력 | 대기 | 대기 | 대기 |
| Bluetooth 연결·해제 | 대기 | 대기 | 대기 |
| Backspace / Return / Space | 대기 | 대기 | 대기 |
| 한/영 전환 / 키 반복 | 대기 | 대기 | 대기 |
| 연습 / 흐름 / 초성 / 단어 / 받아쓰기 | 대기 | 대기 | 대기 |
| background 복귀 | 대기 | 대기 | 대기 |

## 수동 절차

1. 기기 설정에서 한국어 두벌식 키보드를 추가하고 하드웨어 키보드 입력 소스를 한국어로 전환한다.
2. Bluetooth 키보드를 연결한 뒤 PIYOKEY 설정에서 기본 입력을 `OS 키보드`로 선택한다.
3. 자유 연습에서 한 음절을 조합 중인 상태와 확정한 상태를 각각 확인한다. marked text가 확정될 때 진행이 두 번 증가하면 실패다.
4. 받침·도깨비 이월 단어, 쌍자음, 복합 모음, Space가 있는 문장을 입력한다.
5. Backspace로 조합 중 자모와 확정 음절을 각각 지운 뒤 다시 입력한다. 삭제만으로 오타 수가 늘면 실패다.
6. Return을 누른 뒤 다음 글자를 입력한다. 개행·제출 없이 같은 문제에 이어 입력되어야 한다.
7. 한/영을 전환해 영문을 확정한 뒤 오타가 정확히 한 번만 집계되고 수락 prefix로 복구되는지 확인한다.
8. 키를 길게 눌러 반복 입력한 뒤 중복·누락·크래시가 없는지 확인한다.
9. 입력 중 Bluetooth 연결을 해제하고 다시 연결한다. 문제·수락 prefix가 유지되고 입력 필드를 한 번 탭하면 계속 입력할 수 있어야 한다.
10. 입력 중 홈으로 나갔다 복귀해 현재 문제·타이머·포커스가 복구되는지 확인한다.

## 현재 실기기 게이트 상태 — 2026-08-26

- 자동 게이트: HangulEngine 11/11, `PracticeSessionViewModelTests` 26/26, 대상 OS IME UI 회귀 3/3 통과.
- `xcrun devicectl list devices`: iPad Pro 11-inch (3세대), iPhone 15 Pro, iPhone 16 모두 paired/available.
- iPad: iPadOS 26.6 (23G71), Developer Mode enabled, USB connected, `ddiServicesAvailable: true`.
- Xcode Apple Account 로그인 뒤 기기 자동 등록·프로비저닝이 성공했고 arm64 Debug 앱 설치·실행을 확인했다.
- 실제 iPad에서 `PracticeSessionViewModelTests` 26/26 통과.
- 실제 iPad의 iPhone 호환 실행에서 대상 OS IME UI 회귀 3/3 통과. Backspace → Return → 이어 입력, 내장→OS IME 전환, background→foreground 포커스 복구를 확인했다.
- 호환 창의 접근성 좌표에서 전체 화면 스와이프가 창 밖으로 전달되던 테스트 하네스를 현재 보이는 `ScrollView` 우선 스와이프와 중앙 좌표 탭 폴백으로 수정했다. 앱 제품 코드는 변경하지 않았다.
- 결과 번들: `Test-Hanco-2026.08.26_23-20-27-+0900.xcresult`, `Test-Hanco-2026.08.26_23-21-27-+0900.xcresult`.
- 실제 Bluetooth/USB/Magic Keyboard의 한국어 두벌식 조합, 연결 해제·재연결, 한/영 전환, 키 반복과 연습·게임 전 모드 수동 매트릭스는 아직 대기다.
